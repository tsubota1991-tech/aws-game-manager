package tsubota1991tech.github.io.aws_game_manager.service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingException;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingInstancesResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tsubota1991tech.github.io.aws_game_manager.aws.AwsClientFactory;
import tsubota1991tech.github.io.aws_game_manager.aws.SpotInterruptionEvent;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.repository.CloudAccountRepository;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;

@Component
public class SpotInterruptionSqsListener {

    private static final Logger log = LoggerFactory.getLogger(SpotInterruptionSqsListener.class);

    private final AwsClientFactory awsClientFactory;
    private final GameServerRepository gameServerRepository;
    private final CloudAccountRepository cloudAccountRepository;
    private final DiscordBotManager discordBotManager;
    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Instant> dedupCache = new ConcurrentHashMap<>();

    @Value("${spot-interruption.enabled:false}")
    private boolean enabled;

    @Value("${spot-interruption.queue-url:}")
    private String queueUrl;

    @Value("${spot-interruption.region:ap-northeast-1}")
    private String region;

    @Value("${spot-interruption.access-key-id:}")
    private String accessKeyId;

    @Value("${spot-interruption.secret-access-key:}")
    private String secretAccessKey;

    @Value("${spot-interruption.wait-seconds:20}")
    private int waitSeconds;

    @Value("${spot-interruption.dedup-seconds:300}")
    private long dedupSeconds;

    public SpotInterruptionSqsListener(AwsClientFactory awsClientFactory,
                                       GameServerRepository gameServerRepository,
                                       CloudAccountRepository cloudAccountRepository,
                                       DiscordBotManager discordBotManager,
                                       SystemSettingService systemSettingService,
                                       ObjectMapper objectMapper) {
        this.awsClientFactory = awsClientFactory;
        this.gameServerRepository = gameServerRepository;
        this.cloudAccountRepository = cloudAccountRepository;
        this.discordBotManager = discordBotManager;
        this.systemSettingService = systemSettingService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${spot-interruption.poll-interval-ms:10000}")
    public void pollQueue() {
        if (!enabled) {
            return;
        }
        if (!systemSettingService.isSpotOperationEnabled()) {
            return;
        }
        if (!StringUtils.hasText(queueUrl)) {
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }

        try (SqsClient sqsClient = awsClientFactory.createSqsClient(region, accessKeyId, secretAccessKey)) {
            ReceiveMessageResponse response = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .waitTimeSeconds(waitSeconds)
                            .maxNumberOfMessages(5)
                            .build()
            );

            for (Message message : response.messages()) {
                boolean delete = processMessage(sqsClient, message);
                if (delete) {
                    deleteMessage(sqsClient, message);
                }
            }
            cleanupDedup();
        } catch (Exception e) {
            log.warn("スポット中断通知の受信に失敗しました。設定や通信環境を確認してください。", e);
        } finally {
            polling.set(false);
        }
    }

    private boolean processMessage(SqsClient sqsClient, Message message) {
        try {
            SpotInterruptionEvent event = objectMapper.readValue(message.body(), SpotInterruptionEvent.class);

            if (!"aws.ec2".equals(event.getSource())
                    || !"EC2 Spot Instance Interruption Warning".equals(event.getDetailType())) {
                return true;
            }
            if (event.getDetail() == null || !StringUtils.hasText(event.getDetail().getInstanceId())) {
                return true;
            }
            if (isDuplicate(event)) {
                log.debug("Duplicated spot interruption event skipped. instanceId={} time={}",
                        event.getDetail().getInstanceId(), event.getTime());
                return true;
            }

            handleInterruptionEvent(event);
            return true;
        } catch (Exception e) {
            log.warn("SQS メッセージの処理に失敗しました。messageId={}", message.messageId(), e);
            return true;
        }
    }

    private void handleInterruptionEvent(SpotInterruptionEvent event) {
        String instanceId = event.getDetail().getInstanceId();
        GameServer server = resolveGameServer(instanceId);

        StringBuilder message = new StringBuilder();
        message.append("🚨 Spot中断の2分前通知\n");
        if (server != null) {
            message.append("ゲームサーバ: `").append(server.getName()).append("`\n");
            if (StringUtils.hasText(server.getAutoScalingGroupName())) {
                message.append("ASG: `").append(server.getAutoScalingGroupName()).append("`\n");
            }
        }
        message.append("Instance ID: `").append(instanceId).append("`\n");
        if (event.getDetail().getInstanceAction() != null) {
            message.append("Action: `").append(event.getDetail().getInstanceAction()).append("`\n");
        }
        message.append("イベント時刻: ").append(event.getTime());

        if (discordBotManager.isRunning()) {
            discordBotManager.sendMessageToDefaultChannel(message.toString());
        } else {
            log.info("Discord Bot が停止しているため、Spot中断通知をログのみに出力しました: {}", message);
        }
    }

    private boolean isDuplicate(SpotInterruptionEvent event) {
        if (event.getTime() == null || event.getDetail() == null) {
            return false;
        }
        String key = event.getDetail().getInstanceId() + "|" + event.getTime();
        Instant now = Instant.now();

        Instant previous = dedupCache.get(key);
        if (previous != null && previous.plusSeconds(dedupSeconds).isAfter(now)) {
            return true;
        }

        dedupCache.put(key, now);
        return false;
    }

    private void cleanupDedup() {
        Instant now = Instant.now();
        dedupCache.entrySet().removeIf(entry -> entry.getValue().plusSeconds(dedupSeconds).isBefore(now));
    }

    private void deleteMessage(SqsClient sqsClient, Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.warn("SQS メッセージの削除に失敗しました。messageId={}", message.messageId(), e);
        }
    }

    private GameServer resolveGameServer(String instanceId) {
        Optional<GameServer> byInstanceId = gameServerRepository.findByEc2InstanceId(instanceId);
        if (byInstanceId.isPresent()) {
            return byInstanceId.get();
        }

        for (CloudAccount account : cloudAccountRepository.findAll()) {
            try (AutoScalingClient autoScaling = awsClientFactory.createAutoScalingClient(account)) {
                DescribeAutoScalingInstancesResponse response = autoScaling.describeAutoScalingInstances(
                        DescribeAutoScalingInstancesRequest.builder()
                                .instanceIds(instanceId)
                                .build()
                );
                if (response.autoScalingInstances().isEmpty()) {
                    continue;
                }

                String asgName = response.autoScalingInstances().get(0).autoScalingGroupName();
                Optional<GameServer> byAsg = gameServerRepository.findByAutoScalingGroupName(asgName);
                if (byAsg.isPresent()) {
                    GameServer server = byAsg.get();
                    server.setEc2InstanceId(instanceId);
                    gameServerRepository.save(server);
                    return server;
                }
            } catch (AutoScalingException e) {
                log.debug("AutoScaling の問い合わせに失敗しました。account={} instanceId={}", account.getName(), instanceId, e);
            }
        }
        return null;
    }
}
