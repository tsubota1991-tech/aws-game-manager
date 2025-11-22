package tsubota1991tech.github.io.aws_game_manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import tsubota1991tech.github.io.aws_game_manager.discord.DiscordBotListener;

@Configuration
public class DiscordBotConfig {

    @Value("${discord.bot.api-token}")
    private String discordToken;

    @Bean
    public JDA jda(DiscordBotListener listener) throws Exception {
        return JDABuilder.createDefault(discordToken)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(listener)
                .build();
    }
}
