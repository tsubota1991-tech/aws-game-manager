package tsubota1991tech.github.io.aws_game_manager.aws;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.CommandInvocationStatus;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationResponse;
import software.amazon.awssdk.services.ssm.model.InvocationDoesNotExistException;
import software.amazon.awssdk.services.ssm.model.SendCommandRequest;
import software.amazon.awssdk.services.ssm.model.SendCommandResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * AWS Systems Manager (SSM) 経由で EC2 インスタンス上のシェルスクリプトを実行するユーティリティ。
 *
 * ・AWS-RunShellScript ドキュメントを利用
 * ・指定インスタンスで sudo スクリプト を実行
 * ・完了するまでポーリングし、成功/失敗を boolean で返す
 */
@Component
public class AwsSsmService {

    private static final Logger log = LoggerFactory.getLogger(AwsSsmService.class);

    /**
     * 指定した認証情報＆リージョンで SSM クライアントを生成。
     */
    private SsmClient buildSsmClient(String accessKeyId, String secretAccessKey, String regionName) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        return SsmClient.builder()
                .region(Region.of(regionName))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    /**
     * EC2 インスタンス上でシェルスクリプトを実行し、成功/失敗を返す。
     *
     * @param accessKeyId     CloudAccount に保存している AccessKeyId
     * @param secretAccessKey CloudAccount に保存している SecretAccessKey
     * @param regionName      CloudAccount に保存しているリージョン (ap-northeast-1 等)
     * @param instanceId      対象 EC2 インスタンス ID
     * @param scriptPath      実行するスクリプトのフルパス (/home/ubuntu/backup_7dtd.sh など)
     * @param comment         AWS コンソール上に残すコメント（任意）
     * @return true: 成功 / false: 失敗
     */
    public SsmCommandResult runShellScriptAndWait(
            String accessKeyId,
            String secretAccessKey,
            String regionName,
            String instanceId,
            String scriptPath,
            String comment
    ) {
        String command = "sudo " + scriptPath;

        log.warn("[AwsSsmService] runShellScriptAndWait CALLED instanceId={} command={}", instanceId, command);

        try (SsmClient ssm = buildSsmClient(accessKeyId, secretAccessKey, regionName)) {

            // 1. コマンド送信
            SendCommandRequest sendReq = SendCommandRequest.builder()
                    .documentName("AWS-RunShellScript")
                    .instanceIds(instanceId)
                    .parameters(
                            Map.of(
                                    "commands",
                                    List.of(command)
                            )
                    )
                    .comment(comment)
                    .timeoutSeconds(600) // 最大 10 分
                    .build();

            SendCommandResponse sendRes = ssm.sendCommand(sendReq);
            String commandId = sendRes.command().commandId();

            log.info("[AwsSsmService] SendCommand issued. commandId={} instanceId={}", commandId, instanceId);

            // ★ 重要：すぐに getCommandInvocation を呼ぶと InvocationDoesNotExist になることがあるので
            // 少し待ってからポーリングを開始する
            try {
                Thread.sleep(3000L); // 3秒だけ待機
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 2. 成否が確定するまでポーリング
            return waitCommandResult(ssm, commandId, instanceId, command);

        } catch (SsmException e) {
            log.error("[AwsSsmService] SSM command error: {}", e.getMessage(), e);
            return new SsmCommandResult(false, null, null, null, e.getMessage(), command);
        }
    }

    private SsmCommandResult waitCommandResult(
            SsmClient ssm,
            String commandId,
            String instanceId,
            String executedCommand
    ) {
        int maxAttempts = 60; // 10 秒 × 60 回 = 最大 10 分
        Duration sleep = Duration.ofSeconds(10);

        String latestStdout = "";
        String latestStderr = "";
        CommandInvocationStatus latestStatus = null;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                GetCommandInvocationRequest getReq = GetCommandInvocationRequest.builder()
                        .commandId(commandId)
                        .instanceId(instanceId)
                        .build();

                GetCommandInvocationResponse res = ssm.getCommandInvocation(getReq);
                CommandInvocationStatus status = res.status();

                latestStatus = status;
                latestStdout = res.standardOutputContent();
                latestStderr = res.standardErrorContent();

                log.info("[AwsSsmService] status={} stdout={} stderr={}",
                        status, res.standardOutputContent(), res.standardErrorContent());

                switch (status) {
                    case SUCCESS:
                        return new SsmCommandResult(
                                true,
                                commandId,
                                status,
                                latestStdout,
                                latestStderr,
                                executedCommand
                        );
                    case FAILED:
                    case TIMED_OUT:
                    case CANCELLED:
                        return new SsmCommandResult(
                                false,
                                commandId,
                                status,
                                latestStdout,
                                latestStderr,
                                executedCommand
                        );
                    default:
                        // Pending / InProgress の場合は待機して再試行
                }

                Thread.sleep(sleep.toMillis());

            } catch (InvocationDoesNotExistException e) {
                // ★ ここが今回のポイント
                // まだ SSM 側でインボケーションが作成されていないだけの場合があるので、
                // ログだけ出して「待機 → 再試行」する
                log.warn("[AwsSsmService] InvocationDoesNotExist: commandId={} instanceId={}. Will retry...",
                        commandId, instanceId);
                try {
                    Thread.sleep(sleep.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new SsmCommandResult(false, commandId, latestStatus, latestStdout, "Interrupted", executedCommand);
                }
            } catch (SsmException e) {
                log.error("[AwsSsmService] getCommandInvocation error: {}", e.getMessage(), e);
                return new SsmCommandResult(false, commandId, latestStatus, latestStdout, e.getMessage(), executedCommand);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SsmCommandResult(false, commandId, latestStatus, latestStdout, "Interrupted", executedCommand);
            }
        }

        log.error("[AwsSsmService] SSM command did not finish within timeout.");
        return new SsmCommandResult(false, commandId, latestStatus, latestStdout, latestStderr, executedCommand);
    }

    public static class SsmCommandResult {
        private final boolean success;
        private final String commandId;
        private final CommandInvocationStatus finalStatus;
        private final String standardOutput;
        private final String standardError;
        private final String executedCommand;

        public SsmCommandResult(
                boolean success,
                String commandId,
                CommandInvocationStatus finalStatus,
                String standardOutput,
                String standardError,
                String executedCommand
        ) {
            this.success = success;
            this.commandId = commandId;
            this.finalStatus = finalStatus;
            this.standardOutput = standardOutput;
            this.standardError = standardError;
            this.executedCommand = executedCommand;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getCommandId() {
            return commandId;
        }

        public CommandInvocationStatus getFinalStatus() {
            return finalStatus;
        }

        public String getStandardOutput() {
            return standardOutput;
        }

        public String getStandardError() {
            return standardError;
        }

        public String getExecutedCommand() {
            return executedCommand;
        }
    }

}
