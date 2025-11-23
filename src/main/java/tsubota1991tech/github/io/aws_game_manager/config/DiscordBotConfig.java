package tsubota1991tech.github.io.aws_game_manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import tsubota1991tech.github.io.aws_game_manager.discord.DiscordBotListener;
import tsubota1991tech.github.io.aws_game_manager.service.SystemSettingService;

@Configuration
public class DiscordBotConfig {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotConfig.class);

    private final SystemSettingService systemSettingService;
    private final DiscordBotListener listener;

    public DiscordBotConfig(SystemSettingService systemSettingService, DiscordBotListener listener) {
        this.systemSettingService = systemSettingService;
        this.listener = listener;
    }

    @PostConstruct
    public void startBot() {
        String discordToken = systemSettingService.getDiscordBotToken();
        if (!StringUtils.hasText(discordToken)) {
            log.warn("Discord Bot Token が設定されていないため、Discord Bot を起動しませんでした。");
            return;
        }

        try {
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(listener)
                    .build();

            jda.awaitReady();
            log.info("Discord Bot を起動しました。Bot User: {}", jda.getSelfUser().getName());
        } catch (InvalidTokenException ex) {
            log.error("Discord Bot Token が不正です。System Settings のトークンを確認してください。", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Discord Bot の起動待機中に割り込みが発生しました。", ex);
        } catch (Exception ex) {
            log.error("Discord Bot の起動に失敗しました。設定やネットワーク環境を確認してください。", ex);
        }
    }
}
