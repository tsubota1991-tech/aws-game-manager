package tsubota1991tech.github.io.aws_game_manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import tsubota1991tech.github.io.aws_game_manager.service.DiscordBotManager;
import tsubota1991tech.github.io.aws_game_manager.service.SystemSettingService;

@Configuration
public class DiscordBotConfig {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotConfig.class);

    private final SystemSettingService systemSettingService;
    private final DiscordBotManager discordBotManager;

    public DiscordBotConfig(SystemSettingService systemSettingService, DiscordBotManager discordBotManager) {
        this.systemSettingService = systemSettingService;
        this.discordBotManager = discordBotManager;
    }

    @PostConstruct
    public void startBot() {
        String discordToken = systemSettingService.getDiscordBotToken();
        try {
            discordBotManager.reload(discordToken);
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
