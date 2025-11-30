package tsubota1991tech.github.io.aws_game_manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import tsubota1991tech.github.io.aws_game_manager.discord.DiscordBotListener;

@Service
public class DiscordBotManager {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotManager.class);

    private final DiscordBotListener listener;

    private final Object lock = new Object();

    private JDA jda;

    public DiscordBotManager(DiscordBotListener listener) {
        this.listener = listener;
    }

    public void reload(String token) throws Exception {
        if (!StringUtils.hasText(token)) {
            stop();
            log.warn("Discord Bot Token が設定されていないため、Discord Bot を起動しませんでした。");
            return;
        }

        start(token);
    }

    public void start(String token) throws Exception {
        synchronized (lock) {
            stopInternal();
            try {
                JDA newJda = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                        .addEventListeners(listener)
                        .build();

                newJda.awaitReady();
                jda = newJda;
                log.info("Discord Bot を起動しました。Bot User: {}", jda.getSelfUser().getName());
            } catch (InvalidTokenException ex) {
                log.error("Discord Bot Token が不正です。System Settings のトークンを確認してください。", ex);
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.error("Discord Bot の起動待機中に割り込みが発生しました。", ex);
                throw ex;
            } catch (Exception ex) {
                log.error("Discord Bot の起動に失敗しました。設定やネットワーク環境を確認してください。", ex);
                throw ex;
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            stopInternal();
        }
    }

    private void stopInternal() {
        if (jda != null) {
            log.info("Discord Bot を停止します。");
            jda.shutdownNow();
            jda = null;
        }
    }

    public boolean isRunning() {
        synchronized (lock) {
            return jda != null && jda.getStatus() != JDA.Status.SHUTDOWN;
        }
    }
}
