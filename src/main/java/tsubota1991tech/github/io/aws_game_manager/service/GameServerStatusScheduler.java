package tsubota1991tech.github.io.aws_game_manager.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;

@Component
public class GameServerStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameServerStatusScheduler.class);

    private final GameServerRepository gameServerRepository;
    private final GameServerService gameServerService;
    private final DiscordBotManager discordBotManager;

    public GameServerStatusScheduler(GameServerRepository gameServerRepository,
                                     GameServerService gameServerService,
                                     DiscordBotManager discordBotManager) {
        this.gameServerRepository = gameServerRepository;
        this.gameServerService = gameServerService;
        this.discordBotManager = discordBotManager;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.status-check.delay-ms:60000}")
    public void autoRefreshForRunningServers() {
        for (GameServer server : gameServerRepository.findAll()) {
            if (shouldCheckAfterAsgStop(server)) {
                refreshStatusOnce(server);
                continue;
            }

            Integer intervalMinutes = Optional.ofNullable(server.getStatusCheckIntervalMinutes())
                    .filter(v -> v > 0)
                    .orElse(null);

            if (intervalMinutes == null) {
                continue;
            }

            if (!shouldCheckWhileRunning(server)) {
                continue;
            }

            LocalDateTime baseline = Optional.ofNullable(server.getLastAutoStatusCheckedAt())
                    .orElse(server.getLastStartedAt());

            if (baseline == null) {
                continue;
            }

            LocalDateTime nextCheckAt = baseline.plusMinutes(intervalMinutes);
            if (LocalDateTime.now().isBefore(nextCheckAt)) {
                continue;
            }

            try {
                gameServerService.refreshStatus(server.getId());
                updateLastAutoStatusCheckedAt(server.getId());

                GameServer refreshed = gameServerRepository.findById(server.getId()).orElse(server);
                String ip = refreshed.getPublicIp() != null ? refreshed.getPublicIp() : "不明";
                String status = refreshed.getLastStatus() != null ? refreshed.getLastStatus() : "UNKNOWN";

                if (discordBotManager.isRunning()) {
                    String message = "自動状態確認結果 📡\n"
                            + "サーバー: `" + refreshed.getName() + "`\n"
                            + "状態: `" + status + "`\n"
                            + "IP: `" + ip + "`\n"
                            + "確認時刻: " + LocalDateTime.now();
                    discordBotManager.sendMessageToDefaultChannel(message);
                } else {
                    log.info("Discord Bot 非稼働のため自動通知をスキップしました。serverId={}", server.getId());
                }
            } catch (Exception ex) {
                log.warn("自動状態確認に失敗しました。serverId={} name={}", server.getId(), server.getName(), ex);
                if (discordBotManager.isRunning()) {
                    String errorMessage = "自動状態確認に失敗しました ❌\n"
                            + "サーバー: `" + server.getName() + "`\n"
                            + "エラー: " + ex.getMessage();
                    discordBotManager.sendMessageToDefaultChannel(errorMessage);
                }
            }
        }
    }

    private boolean shouldCheckAfterAsgStop(GameServer server) {
        LocalDateTime checkAt = server.getAsgStopCheckAt();
        if (checkAt == null) {
            return false;
        }
        return !LocalDateTime.now().isBefore(checkAt);
    }

    private void refreshStatusOnce(GameServer server) {
        try {
            gameServerService.refreshStatus(server.getId());
            GameServer refreshed = gameServerRepository.findById(server.getId()).orElse(server);
            refreshed.setAsgStopCheckAt(null);
            gameServerRepository.save(refreshed);

            String status = refreshed.getLastStatus() != null ? refreshed.getLastStatus() : "UNKNOWN";
            if (discordBotManager.isRunning()) {
                String message = "ASG 停止後の状態確認結果 📡\n"
                        + "サーバー: `" + refreshed.getName() + "`\n"
                        + "状態: `" + status + "`\n"
                        + "確認時刻: " + LocalDateTime.now();
                discordBotManager.sendMessageToDefaultChannel(message);
            } else {
                log.info("Discord Bot 非稼働のため停止後確認の通知をスキップしました。serverId={}", server.getId());
            }
        } catch (Exception ex) {
            log.warn("ASG 停止後の状態確認に失敗しました。serverId={} name={}", server.getId(), server.getName(), ex);
        }
    }

    private void updateLastAutoStatusCheckedAt(Long serverId) {
        gameServerRepository.findById(serverId).ifPresent(refreshed -> {
            refreshed.setLastAutoStatusCheckedAt(LocalDateTime.now());
            gameServerRepository.save(refreshed);
        });
    }

    private boolean shouldCheckWhileRunning(GameServer server) {
        LocalDateTime lastStarted = server.getLastStartedAt();
        LocalDateTime lastStopped = server.getLastStoppedAt();

        if (lastStarted == null) {
            return false;
        }

        if (lastStopped != null && !lastStarted.isAfter(lastStopped)) {
            return false;
        }

        String lastStatus = Optional.ofNullable(server.getLastStatus()).orElse("");
        return !lastStatus.contains("STOP");
    }
}
