package tsubota1991tech.github.io.aws_game_manager.discord;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;
import tsubota1991tech.github.io.aws_game_manager.service.GameServerService;

@Component
public class DiscordBotListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotListener.class);

    private final GameServerService gameServerService;
    private final GameServerRepository gameServerRepository;

    public DiscordBotListener(
            GameServerService gameServerService,
            GameServerRepository gameServerRepository
    ) {
        this.gameServerService = gameServerService;
        this.gameServerRepository = gameServerRepository;
    }

    // Bot 起動時にスラッシュコマンド登録
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        log.info("Discord Bot Ready. Registering slash commands...");

        List<CommandData> commands = List.of(
                Commands.slash("start", "ゲームサーバーを起動します")
                        .addOptions(new OptionData(OptionType.STRING, "server", "ゲームサーバー名", true)),
                Commands.slash("stop", "ゲームサーバーを停止します（バックアップ付き）")
                        .addOptions(new OptionData(OptionType.STRING, "server", "ゲームサーバー名", true)),
                Commands.slash("status", "ゲームサーバーの状態を確認します")
                        .addOptions(new OptionData(OptionType.STRING, "server", "ゲームサーバー名", true))
        );

        event.getJDA()
                .updateCommands()
                .addCommands(commands)
                .queue(
                        (List<Command> ignored) -> log.info("Slash commands registered."),
                        throwable -> log.error("Failed to register slash commands", throwable)
                );
    }

    // スラッシュコマンド受付
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String name = event.getName();
        String serverName = event.getOption("server") != null
                ? event.getOption("server").getAsString()
                : null;

        if (serverName == null || serverName.isBlank()) {
            event.reply("サーバー名を指定してください。\n例: `/start server: 7DTD テストサーバ`")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Optional<GameServer> opt = gameServerRepository.findByName(serverName);

        if (opt.isEmpty()) {
            event.reply("指定されたゲームサーバーが見つかりません: `" + serverName + "`")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GameServer server = opt.get();

        switch (name) {
            case "start" -> handleStart(event, server);
            case "stop" -> handleStop(event, server);
            case "status" -> handleStatus(event, server);
            default -> event.reply("未知のコマンドです。").setEphemeral(true).queue();
        }
    }

    private void handleStart(SlashCommandInteractionEvent event, GameServer server) {
        try {
            event.deferReply().queue(); // 「考え中…」表示

            gameServerService.startServer(server.getId());
            // 起動後に最新ステータス＆IPを取得
            gameServerService.refreshStatus(server.getId());
            Optional<GameServer> refreshed = gameServerRepository.findById(server.getId());

            GameServer s = refreshed.orElse(server);
            String ip = s.getPublicIp() != null ? s.getPublicIp() : "不明";

            event.getHook()
                    .sendMessage("サーバーを起動しました ✅\n" +
                            "サーバー名: `" + s.getName() + "`\n" +
                            "状態: `" + s.getLastStatus() + "`\n" +
                            "グローバルIP: `" + ip + "`")
                    .queue();
        } catch (Exception ex) {
            log.error("start error", ex);
            event.getHook()
                    .sendMessage("起動に失敗しました ❌\nエラー: " + ex.getMessage())
                    .queue();
        }
    }

    private void handleStop(SlashCommandInteractionEvent event, GameServer server) {
        try {
            event.deferReply().queue();

            gameServerService.stopServer(server.getId());
            gameServerService.refreshStatus(server.getId());
            Optional<GameServer> refreshed = gameServerRepository.findById(server.getId());

            GameServer s = refreshed.orElse(server);
            String ip = s.getPublicIp() != null ? s.getPublicIp() : "不明";

            event.getHook()
                    .sendMessage("サーバーを停止しました 🛑\n" +
                            "サーバー名: `" + s.getName() + "`\n" +
                            "状態: `" + s.getLastStatus() + "`\n" +
                            "停止後 IP: `" + ip + "`")
                    .queue();
        } catch (Exception ex) {
            log.error("stop error", ex);
            event.getHook()
                    .sendMessage("停止に失敗しました ❌\nエラー: " + ex.getMessage())
                    .queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event, GameServer server) {
        try {
            event.deferReply().queue();

            gameServerService.refreshStatus(server.getId());
            Optional<GameServer> refreshed = gameServerRepository.findById(server.getId());

            GameServer s = refreshed.orElse(server);
            String ip = s.getPublicIp() != null ? s.getPublicIp() : "不明";
            String status = s.getLastStatus() != null ? s.getLastStatus() : "UNKNOWN";

            event.getHook()
                    .sendMessage("サーバー状態 📡\n" +
                            "サーバー名: `" + s.getName() + "`\n" +
                            "状態: `" + status + "`\n" +
                            "グローバルIP: `" + ip + "`")
                    .queue();
        } catch (Exception ex) {
            log.error("status error", ex);
            event.getHook()
                    .sendMessage("状態確認に失敗しました ❌\nエラー: " + ex.getMessage())
                    .queue();
        }
    }
}
