package tsubota1991tech.github.io.aws_game_manager.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingException;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.LifecycleState;
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StartInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import tsubota1991tech.github.io.aws_game_manager.aws.AwsClientFactory;
import tsubota1991tech.github.io.aws_game_manager.aws.AwsSsmService;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.exception.GameServerOperationException;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;
import tsubota1991tech.github.io.aws_game_manager.service.SystemSettingService;

@Service
public class GameServerServiceImpl implements GameServerService {

    private static final Logger log = LoggerFactory.getLogger(GameServerServiceImpl.class);

    private final GameServerRepository gameServerRepository;
    private final AwsSsmService awsSsmService;  // SSM 用サービス
    private final SystemSettingService systemSettingService;
    private final AwsClientFactory awsClientFactory;

    // コンストラクタ DI
    public GameServerServiceImpl(
            GameServerRepository gameServerRepository,
            AwsSsmService awsSsmService,
            SystemSettingService systemSettingService,
            AwsClientFactory awsClientFactory
    ) {
        this.gameServerRepository = gameServerRepository;
        this.awsSsmService = awsSsmService;
        this.systemSettingService = systemSettingService;
        this.awsClientFactory = awsClientFactory;
    }

    /**
     * GameServer の読み込み＋設定チェック
     * → おかしければ GameServerOperationException で画面にメッセージを返す。
     */
    private GameServer loadGameServer(Long id) {
        Optional<GameServer> opt = gameServerRepository.findById(id);
        if (opt.isEmpty()) {
            throw new GameServerOperationException("指定されたゲームサーバが見つかりません。(id=" + id + ")");
        }
        GameServer server = opt.get();

        if (server.getCloudAccount() == null) {
            throw new GameServerOperationException(
                    "Cloud Account が設定されていません。ゲームサーバ編集画面で Cloud Account を選択してください。"
            );
        }
        if (!StringUtils.hasText(server.getAutoScalingGroupName())
                && (server.getEc2InstanceId() == null || server.getEc2InstanceId().isBlank())) {
            throw new GameServerOperationException(
                    "EC2 Instance ID が設定されていません。ゲームサーバ編集画面で正しいインスタンスIDを入力してください。"
            );
        }
        return server;
    }

    private void validateSpotOperation(GameServer server) {
        if (!server.isSpotInstance()) {
            return;
        }
        if (!systemSettingService.isSpotOperationEnabled()) {
            throw new GameServerOperationException(
                    "スポットインスタンス運用が無効です。システム設定から有効化してください。"
            );
        }
    }

    private boolean usesAutoScaling(GameServer server) {
        return StringUtils.hasText(server.getAutoScalingGroupName());
    }

    // ==========================
    // 共通：EC2 から Public IP / DNS を取得して GameServer に保存
    // ==========================
    private void updateInstanceAddress(Ec2Client ec2, GameServer server) {
        String instanceId = server.getEc2InstanceId();

        DescribeInstancesResponse res = ec2.describeInstances(
                DescribeInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build()
        );

        if (res.reservations().isEmpty()
                || res.reservations().get(0).instances().isEmpty()) {
            log.warn("Instance not found for id={}", instanceId);
            server.setPublicIp(null);
            server.setPublicDns(null);
            return;
        }

        Instance inst = res.reservations().get(0).instances().get(0);

        String publicIp = inst.publicIpAddress();
        String publicDns = inst.publicDnsName();

        server.setPublicIp(publicIp);
        server.setPublicDns(publicDns);

        log.info("Updated instance address id={} ip={} dns={}",
                instanceId, publicIp, publicDns);
    }

    private AutoScalingGroup fetchAutoScalingGroup(AutoScalingClient autoScaling, GameServer server) {
        DescribeAutoScalingGroupsResponse response = autoScaling.describeAutoScalingGroups(
                DescribeAutoScalingGroupsRequest.builder()
                        .autoScalingGroupNames(server.getAutoScalingGroupName())
                        .build()
        );

        if (response.autoScalingGroups().isEmpty()) {
            throw new GameServerOperationException("指定された Auto Scaling Group が見つかりません: "
                    + server.getAutoScalingGroupName());
        }
        return response.autoScalingGroups().get(0);
    }

    private String waitInstanceInService(AutoScalingClient autoScaling, String asgName) {
        for (int i = 0; i < 10; i++) {
            DescribeAutoScalingGroupsResponse response = autoScaling.describeAutoScalingGroups(
                    DescribeAutoScalingGroupsRequest.builder()
                            .autoScalingGroupNames(asgName)
                            .build()
            );

            for (AutoScalingGroup group : response.autoScalingGroups()) {
                Optional<String> instanceId = group.instances().stream()
                        .filter(inst -> LifecycleState.IN_SERVICE.equals(inst.lifecycleState()))
                        .map(software.amazon.awssdk.services.autoscaling.model.Instance::instanceId)
                        .findFirst();

                if (instanceId.isPresent()) {
                    return instanceId.get();
                }
            }

            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private void updateInstanceAddressWithDelay(Ec2Client ec2, GameServer server) {
        Integer delaySeconds = Optional.ofNullable(server.getAddressRefreshDelaySeconds())
                .filter(v -> v > 0)
                .orElse(0);
        if (delaySeconds > 0) {
            try {
                Thread.sleep(delaySeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for address refresh. instanceId={}", server.getEc2InstanceId());
                return;
            }
        }
        updateInstanceAddress(ec2, server);
    }

    private InstanceStateName fetchCurrentState(Ec2Client ec2, GameServer server) {
        DescribeInstancesResponse res = ec2.describeInstances(
                DescribeInstancesRequest.builder()
                        .instanceIds(server.getEc2InstanceId())
                        .build()
        );

        if (res.reservations().isEmpty() || res.reservations().get(0).instances().isEmpty()) {
            throw new GameServerOperationException("EC2 インスタンスが見つかりません: " + server.getEc2InstanceId());
        }

        return res.reservations().get(0).instances().get(0).state().name();
    }

    private void enforceRestartCooldown(GameServer server) {
        LocalDateTime lastStopped = server.getLastStoppedAt();
        Integer cooldownMinutes = Optional.ofNullable(server.getRestartCooldownMinutes())
                .filter(v -> v > 0)
                .orElse(null);

        if (lastStopped == null || cooldownMinutes == null) {
            return;
        }

        LocalDateTime allowedAt = lastStopped.plusMinutes(cooldownMinutes);
        if (LocalDateTime.now().isBefore(allowedAt)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            throw new GameServerOperationException(
                    "停止直後のため再起動できません。" + cooldownMinutes + "分後 ("
                            + allowedAt.format(formatter) + ") 以降に再実行してください。"
            );
        }
    }

    // ==========================
    // EC2 起動
    // ==========================
    @Override
    @Transactional
    public void startServer(Long gameServerId) {
        GameServer server = loadGameServer(gameServerId);
        CloudAccount account = server.getCloudAccount();

        validateSpotOperation(server);

        log.info("[GameServerService] startServer id={} instanceId={}",
                gameServerId, server.getEc2InstanceId());

        if (usesAutoScaling(server)) {
            startServerWithAutoScaling(server, account);
            return;
        }

        try (Ec2Client ec2 = awsClientFactory.createEc2Client(account)) {
            InstanceStateName currentState = fetchCurrentState(ec2, server);
            if (InstanceStateName.RUNNING.equals(currentState)
                    || InstanceStateName.PENDING.equals(currentState)) {
                server.setLastStatus("START SKIPPED (" + currentState + ")");
                gameServerRepository.save(server);
                throw new GameServerOperationException(
                        "既にインスタンスが起動中のため、起動処理をスキップしました。(現在: "
                                + currentState + ")"
                );
            }

            enforceRestartCooldown(server);

            StartInstancesRequest request = StartInstancesRequest.builder()
                    .instanceIds(server.getEc2InstanceId())
                    .build();

            StartInstancesResponse response = ec2.startInstances(request);

            String currentStateText = response.startingInstances().isEmpty()
                    ? "UNKNOWN"
                    : response.startingInstances().get(0).currentState().nameAsString();

            String statusText = "START " + currentStateText; // 例: "START RUNNING"
            log.info("[GameServerService] startServer result status={}", statusText);

            server.setLastStatus(statusText);
            server.setLastStartedAt(LocalDateTime.now());

            // 起動後に少し待ってから IP / DNS を再取得
            try {
                updateInstanceAddressWithDelay(ec2, server);
            } catch (Ec2Exception e) {
                log.warn("Failed to update instance address after start. instanceId={}",
                        server.getEc2InstanceId(), e);
            }

            gameServerRepository.save(server);

        } catch (Ec2Exception e) {
            log.error("[GameServerService] EC2 start error", e);

            server.setLastStatus("START ERROR");
            gameServerRepository.save(server);

            String msg = buildAwsErrorMessage("起動", e);
            throw new GameServerOperationException(msg, e);
        }
    }

    private void startServerWithAutoScaling(GameServer server, CloudAccount account) {
        enforceRestartCooldown(server);

        try (AutoScalingClient autoScaling = awsClientFactory.createAutoScalingClient(account);
             Ec2Client ec2 = awsClientFactory.createEc2Client(account)) {

            AutoScalingGroup group = fetchAutoScalingGroup(autoScaling, server);
            int desired = Optional.ofNullable(server.getAsgDesiredCapacity())
                    .filter(v -> v > 0)
                    .orElse(1);

            int maxSize = Math.max(group.maxSize(), desired);
            int minSize = Math.max(group.minSize(), 1);

            autoScaling.updateAutoScalingGroup(
                    UpdateAutoScalingGroupRequest.builder()
                            .autoScalingGroupName(server.getAutoScalingGroupName())
                            .desiredCapacity(desired)
                            .minSize(minSize)
                            .maxSize(maxSize)
                            .build()
            );

            server.setLastStatus("START REQUESTED (ASG)");
            server.setLastStartedAt(LocalDateTime.now());
            gameServerRepository.save(server);

            String instanceId = waitInstanceInService(autoScaling, server.getAutoScalingGroupName());
            if (instanceId == null) {
                throw new GameServerOperationException(
                        "ASG 起動指示を送信しましたが、InService のインスタンスが確認できませんでした。"
                                + "数十秒後にステータス更新を実行してください。"
                );
            }

            server.setEc2InstanceId(instanceId);
            try {
                updateInstanceAddressWithDelay(ec2, server);
                server.setLastStatus("START RUNNING (ASG)");
            } catch (Ec2Exception e) {
                log.warn("Failed to update instance address after ASG start. instanceId={}", instanceId, e);
            }
            gameServerRepository.save(server);

        } catch (AutoScalingException e) {
            log.error("[GameServerService] AutoScaling start error", e);
            server.setLastStatus("START ERROR (ASG)");
            gameServerRepository.save(server);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new GameServerOperationException(
                    "Auto Scaling Group の起動に失敗しました。(HTTP "
                            + e.statusCode() + "): " + detail, e);
        }
    }

    // ==========================
    // EC2 停止（SSM バックアップ付き）
    // ==========================
    @Override
    @Transactional
    public void stopServer(Long gameServerId) {
        GameServer server = loadGameServer(gameServerId);
        CloudAccount account = server.getCloudAccount();

        validateSpotOperation(server);

        log.info("[GameServerService] stopServer id={} instanceId={}",
                gameServerId, server.getEc2InstanceId());

        if (usesAutoScaling(server)) {
            stopServerWithAutoScaling(server, account);
            return;
        }

        try (Ec2Client ec2 = awsClientFactory.createEc2Client(account)) {
            InstanceStateName currentState = fetchCurrentState(ec2, server);
            if (InstanceStateName.STOPPED.equals(currentState)
                    || InstanceStateName.STOPPING.equals(currentState)
                    || InstanceStateName.SHUTTING_DOWN.equals(currentState)
                    || InstanceStateName.TERMINATED.equals(currentState)) {
                server.setLastStatus("STOP SKIPPED (" + currentState + ")");
                gameServerRepository.save(server);
                throw new GameServerOperationException(
                        "既にインスタンスが停止中のため、停止処理をスキップしました。(現在: "
                                + currentState + ")"
                );
            }

            runBackupOrThrow(server, account);

            StopInstancesRequest request = StopInstancesRequest.builder()
                    .instanceIds(server.getEc2InstanceId())
                    .build();

            StopInstancesResponse response = ec2.stopInstances(request);

            String currentStateText = response.stoppingInstances().isEmpty()
                    ? "UNKNOWN"
                    : response.stoppingInstances().get(0).currentState().nameAsString();

            String statusText = "STOP " + currentStateText; // 例: "STOP STOPPING"
            log.info("[GameServerService] stopServer result status={}", statusText);

            server.setLastStatus(statusText);
            server.setLastStoppedAt(LocalDateTime.now());

            // 停止後の IP / DNS 更新（無ければ null）
            try {
                updateInstanceAddress(ec2, server);
            } catch (Ec2Exception e) {
                log.warn("Failed to update instance address after stop. instanceId={}",
                        server.getEc2InstanceId(), e);
            }

            gameServerRepository.save(server);

        } catch (Ec2Exception e) {
            log.error("[GameServerService] EC2 stop error", e);

            server.setLastStatus("STOP ERROR");
            gameServerRepository.save(server);

            String msg = buildAwsErrorMessage("停止", e);
            throw new GameServerOperationException(msg, e);
        }
    }

    private void stopServerWithAutoScaling(GameServer server, CloudAccount account) {
        try (AutoScalingClient autoScaling = awsClientFactory.createAutoScalingClient(account);
             Ec2Client ec2 = awsClientFactory.createEc2Client(account)) {

            AutoScalingGroup group = fetchAutoScalingGroup(autoScaling, server);
            Optional<software.amazon.awssdk.services.autoscaling.model.Instance> instanceOpt = group.instances()
                    .stream()
                    .filter(inst -> LifecycleState.IN_SERVICE.equals(inst.lifecycleState()))
                    .findFirst();

            if (instanceOpt.isEmpty()) {
                server.setLastStatus("STOP SKIPPED (ASG EMPTY)");
                gameServerRepository.save(server);
                throw new GameServerOperationException(
                        "既に ASG に稼働中のインスタンスがありません。ステータス更新を確認してください。"
                );
            }

            server.setEc2InstanceId(instanceOpt.get().instanceId());
            runBackupOrThrow(server, account);

            autoScaling.updateAutoScalingGroup(
                    UpdateAutoScalingGroupRequest.builder()
                            .autoScalingGroupName(server.getAutoScalingGroupName())
                            .desiredCapacity(0)
                            .minSize(0)
                            .maxSize(0)
                            .build()
            );

            server.setLastStatus("STOP REQUESTED (ASG)");
            server.setLastStoppedAt(LocalDateTime.now());
            server.setPublicIp(null);
            server.setPublicDns(null);
            server.setAsgStopCheckAt(LocalDateTime.now().plusMinutes(5));

            gameServerRepository.save(server);

        } catch (AutoScalingException e) {
            log.error("[GameServerService] AutoScaling stop error", e);
            server.setLastStatus("STOP ERROR (ASG)");
            gameServerRepository.save(server);
            String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            throw new GameServerOperationException(
                    "Auto Scaling Group の停止に失敗しました。(HTTP "
                            + e.statusCode() + "): " + detail, e);
        }
    }

    private void runBackupOrThrow(GameServer server, CloudAccount account) {
        log.info("[GameServerService] calling AwsSsmService.runShellScriptAndWait ...");

        String backupScriptPath = server.getBackupScriptPath();
        if (!StringUtils.hasText(backupScriptPath)) {
            server.setLastStatus("BACKUP SKIPPED");
            gameServerRepository.save(server);
            log.info("バックアップスクリプト未設定のため停止前バックアップをスキップします。serverId={}", server.getId());
            return;
        }

        if (!StringUtils.hasText(server.getEc2InstanceId())) {
            server.setLastStatus("INSTANCE NOT FOUND FOR BACKUP");
            gameServerRepository.save(server);
            throw new GameServerOperationException("バックアップ対象のEC2インスタンスIDが未設定です。");
        }

        boolean backupOk;
        try {
            backupOk = awsSsmService.runShellScriptAndWait(
                    account.getAwsAccessKeyId(),
                    account.getAwsSecretAccessKey(),
                    account.getDefaultRegion(),
                    server.getEc2InstanceId(),
                    backupScriptPath,
                    "game backup before stop"
            );
        } catch (Exception ex) {
            log.error("[GameServerService] AwsSsmService threw exception", ex);
            backupOk = false;
        }

        log.info("[GameServerService] AwsSsmService result backupOk={}", backupOk);

        if (!backupOk) {
            server.setLastStatus("BACKUP FAILED");
            gameServerRepository.save(server);
            throw new GameServerOperationException(
                    "サーバ停止前バックアップに失敗しました。EC2 インスタンス上のログを確認してください。"
            );
        }

        server.setLastStatus("BACKUP OK");
        gameServerRepository.save(server);
    }

    // ==========================
    // EC2 ステータス確認
    // ==========================
    @Override
    @Transactional
    public void refreshStatus(Long gameServerId) {
        GameServer server = loadGameServer(gameServerId);
        CloudAccount account = server.getCloudAccount();

        log.info("[GameServerService] refreshStatus id={} instanceId={}",
                gameServerId, server.getEc2InstanceId());

        if (usesAutoScaling(server)) {
            refreshStatusWithAutoScaling(server, account);
            return;
        }

        try (Ec2Client ec2 = awsClientFactory.createEc2Client(account)) {
            DescribeInstanceStatusRequest request = DescribeInstanceStatusRequest.builder()
                    .includeAllInstances(true)
                    .instanceIds(server.getEc2InstanceId())
                    .build();

            DescribeInstanceStatusResponse response = ec2.describeInstanceStatus(request);

            String stateText;

            if (response.instanceStatuses().isEmpty()) {
                stateText = "UNKNOWN";
            } else {
                InstanceStateName stateName = response.instanceStatuses()
                        .get(0)
                        .instanceState()
                        .name();
                stateText = stateName.toString(); // 例: RUNNING, STOPPED
            }

            String statusText = "STATUS " + stateText; // 例: "STATUS RUNNING"
            log.info("[GameServerService] refreshStatus result status={}", statusText);

            server.setLastStatus(statusText);
            server.setLastStatusCheckedAt(LocalDateTime.now());

            // Public IP / DNS を最新化
            try {
                updateInstanceAddress(ec2, server);
            } catch (Ec2Exception e) {
                log.warn("Failed to update instance address in refreshStatus. instanceId={}",
                        server.getEc2InstanceId(), e);
            }

            gameServerRepository.save(server);

        } catch (Ec2Exception e) {
            log.error("[GameServerService] EC2 status error", e);

            server.setLastStatus("STATUS ERROR");
            gameServerRepository.save(server);

            String msg = buildAwsErrorMessage("状態確認", e);
            throw new GameServerOperationException(msg, e);
        }
    }

    private void refreshStatusWithAutoScaling(GameServer server, CloudAccount account) {
        try (AutoScalingClient autoScaling = awsClientFactory.createAutoScalingClient(account);
             Ec2Client ec2 = awsClientFactory.createEc2Client(account)) {

            AutoScalingGroup group = fetchAutoScalingGroup(autoScaling, server);
            Optional<software.amazon.awssdk.services.autoscaling.model.Instance> instanceOpt = group.instances()
                    .stream()
                    .filter(inst -> LifecycleState.IN_SERVICE.equals(inst.lifecycleState()))
                    .findFirst();

            if (instanceOpt.isEmpty()) {
                server.setLastStatus("STATUS STOPPED (ASG)");
                server.setPublicIp(null);
                server.setPublicDns(null);
                server.setLastStatusCheckedAt(LocalDateTime.now());
                server.setAsgStopCheckAt(null);
                gameServerRepository.save(server);
                return;
            }

            String instanceId = instanceOpt.get().instanceId();
            server.setEc2InstanceId(instanceId);

            DescribeInstancesResponse res = ec2.describeInstances(
                    DescribeInstancesRequest.builder()
                            .instanceIds(instanceId)
                            .build()
            );

            Instance instance = res.reservations().stream()
                    .flatMap(r -> r.instances().stream())
                    .findFirst()
                    .orElseThrow(() -> new GameServerOperationException("EC2 インスタンスが見つかりません: " + instanceId));

            InstanceStateName stateName = instance.state().name();
            server.setLastStatus("STATUS " + stateName + " (ASG)");
            server.setLastStatusCheckedAt(LocalDateTime.now());
            server.setAsgStopCheckAt(null);

            try {
                updateInstanceAddress(ec2, server);
            } catch (Ec2Exception e) {
                log.warn("Failed to update instance address in refreshStatus (ASG). instanceId={}",
                        server.getEc2InstanceId(), e);
            }

            gameServerRepository.save(server);

        } catch (AutoScalingException | Ec2Exception e) {
            log.error("[GameServerService] ASG status error", e);
            server.setLastStatus("STATUS ERROR (ASG)");
            gameServerRepository.save(server);
            throw new GameServerOperationException("Auto Scaling Group 状態取得に失敗しました: "
                    + e.getMessage(), e);
        }
    }

    // ==========================
    // AWS エラーを人間向けメッセージに変換
    // ==========================
    private String buildAwsErrorMessage(String op, Ec2Exception e) {
        int status = e.statusCode();
        String awsMsg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : "";

        if (status == 401) {
            return "AWS " + op + "に失敗しました: 認証エラーです。"
                    + " Cloud Account に設定した AccessKey / SecretKey が正しいか確認してください。";
        } else if (status == 403) {
            return "AWS " + op + "に失敗しました: 権限エラーです。"
                    + " IAM ユーザに EC2 / SSM の権限が付与されているか確認してください。";
        } else {
            return "AWS " + op + "に失敗しました。(HTTP Status " + status + ") : " + awsMsg;
        }
    }
}
