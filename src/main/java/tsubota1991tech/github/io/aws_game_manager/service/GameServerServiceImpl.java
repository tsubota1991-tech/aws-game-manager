package tsubota1991tech.github.io.aws_game_manager.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
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
import tsubota1991tech.github.io.aws_game_manager.aws.AwsSsmService;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.exception.GameServerOperationException;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;

@Service
public class GameServerServiceImpl implements GameServerService {

    private static final Logger log = LoggerFactory.getLogger(GameServerServiceImpl.class);

    private final GameServerRepository gameServerRepository;
    private final AwsSsmService awsSsmService;  // SSM 用サービス

    // コンストラクタ DI
    public GameServerServiceImpl(
            GameServerRepository gameServerRepository,
            AwsSsmService awsSsmService
    ) {
        this.gameServerRepository = gameServerRepository;
        this.awsSsmService = awsSsmService;
    }

    // ==========================
    // 共通：EC2 クライアント生成
    // ==========================
    private Ec2Client buildEc2Client(CloudAccount account) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAwsAccessKeyId(),      // ★ CloudAccount に合わせる
                account.getAwsSecretAccessKey()   // ★ CloudAccount に合わせる
        );

        Region region = Region.of(account.getDefaultRegion()); // ★ defaultRegion を使用

        return Ec2Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
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
        if (server.getEc2InstanceId() == null || server.getEc2InstanceId().isBlank()) {
            throw new GameServerOperationException(
                    "EC2 Instance ID が設定されていません。ゲームサーバ編集画面で正しいインスタンスIDを入力してください。"
            );
        }
        return server;
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

    // ==========================
    // EC2 起動
    // ==========================
    @Override
    @Transactional
    public void startServer(Long gameServerId) {
        GameServer server = loadGameServer(gameServerId);
        CloudAccount account = server.getCloudAccount();

        log.info("[GameServerService] startServer id={} instanceId={}",
                gameServerId, server.getEc2InstanceId());

        try (Ec2Client ec2 = buildEc2Client(account)) {
            StartInstancesRequest request = StartInstancesRequest.builder()
                    .instanceIds(server.getEc2InstanceId())
                    .build();

            StartInstancesResponse response = ec2.startInstances(request);

            String currentState = response.startingInstances().isEmpty()
                    ? "UNKNOWN"
                    : response.startingInstances().get(0).currentState().nameAsString();

            String statusText = "START " + currentState; // 例: "START RUNNING"
            log.info("[GameServerService] startServer result status={}", statusText);

            server.setLastStatus(statusText);

            // 起動直後でも一応 IP / DNS 更新を試みる
            try {
                updateInstanceAddress(ec2, server);
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

    // ==========================
    // EC2 停止（SSM バックアップ付き）
    // ==========================
    @Override
    @Transactional
    public void stopServer(Long gameServerId) {
        GameServer server = loadGameServer(gameServerId);
        CloudAccount account = server.getCloudAccount();

        log.info("[GameServerService] stopServer id={} instanceId={}",
                gameServerId, server.getEc2InstanceId());

        log.info("[GameServerService] calling AwsSsmService.runShellScriptAndWait ...");

        String backupScriptPath = server.getBackupScriptPath();
        if (backupScriptPath == null || backupScriptPath.isBlank()) {
            server.setLastStatus("BACKUP SCRIPT NOT SET");
            gameServerRepository.save(server);
            throw new GameServerOperationException(
                "バックアップスクリプトのパスが設定されていません。\n" +
                "Game Server 編集画面で backupScriptPath を設定してください。"
            );
        }

        AwsSsmService.SsmCommandResult backupResult;
        try {
            backupResult = awsSsmService.runShellScriptAndWait(
                    account.getAwsAccessKeyId(),       // ★ 修正
                    account.getAwsSecretAccessKey(),   // ★ 修正
                    account.getDefaultRegion(),        // ★ 修正
                    server.getEc2InstanceId(),
                    backupScriptPath,
                    "7dtd backup before stop"
            );
        } catch (Exception ex) {
            log.error("[GameServerService] AwsSsmService threw exception", ex);
            backupResult = new AwsSsmService.SsmCommandResult(
                    false,
                    null,
                    null,
                    null,
                    ex.getMessage(),
                    "sudo " + backupScriptPath
            );
        }

        log.info(
                "[GameServerService] AwsSsmService result success={} commandId={} status={} stdout={} stderr={}",
                backupResult.isSuccess(),
                backupResult.getCommandId(),
                backupResult.getFinalStatus(),
                backupResult.getStandardOutput(),
                backupResult.getStandardError()
        );

        if (!backupResult.isSuccess()) {
            server.setLastStatus("BACKUP FAILED");
            gameServerRepository.save(server);

            StringBuilder msg = new StringBuilder();
            msg.append("サーバ停止前バックアップに失敗しました。ラズパイ側のバッチログを確認してください。");
            msg.append("\n実行コマンド: ").append(backupResult.getExecutedCommand());

            if (backupResult.getCommandId() != null) {
                msg.append("\nAWS Command ID: ").append(backupResult.getCommandId());
            }

            if (backupResult.getStandardOutput() != null && !backupResult.getStandardOutput().isBlank()) {
                msg.append("\n--- stdout ---\n").append(backupResult.getStandardOutput());
            }
            if (backupResult.getStandardError() != null && !backupResult.getStandardError().isBlank()) {
                msg.append("\n--- stderr ---\n").append(backupResult.getStandardError());
            }

            throw new GameServerOperationException(
                    msg.toString()
            );
        }

        server.setLastStatus("BACKUP OK");
        gameServerRepository.save(server);

        // 2. EC2 を停止
        try (Ec2Client ec2 = buildEc2Client(account)) {
            StopInstancesRequest request = StopInstancesRequest.builder()
                    .instanceIds(server.getEc2InstanceId())
                    .build();

            StopInstancesResponse response = ec2.stopInstances(request);

            String currentState = response.stoppingInstances().isEmpty()
                    ? "UNKNOWN"
                    : response.stoppingInstances().get(0).currentState().nameAsString();

            String statusText = "STOP " + currentState; // 例: "STOP STOPPING"
            log.info("[GameServerService] stopServer result status={}", statusText);

            server.setLastStatus(statusText);

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

        try (Ec2Client ec2 = buildEc2Client(account)) {
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
