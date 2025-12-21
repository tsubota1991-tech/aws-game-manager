package tsubota1991tech.github.io.aws_game_manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import tsubota1991tech.github.io.aws_game_manager.service.DiscordBotManager;
import tsubota1991tech.github.io.aws_game_manager.service.SystemSettingService;

@Controller
@RequestMapping("/admin/system-settings")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;
    private final DiscordBotManager discordBotManager;

    public SystemSettingController(SystemSettingService systemSettingService, DiscordBotManager discordBotManager) {
        this.systemSettingService = systemSettingService;
        this.discordBotManager = discordBotManager;
    }

    @GetMapping
    public String showForm(Model model) {
        String discordToken = systemSettingService.getDiscordBotToken();
        model.addAttribute("discordBotToken", discordToken);
        model.addAttribute("spotOperationEnabled", systemSettingService.isSpotOperationEnabled());
        return "admin/system_settings/form";
    }

    @PostMapping
    public String update(
            @RequestParam(name = "discordBotToken", required = false) String discordBotToken,
            @RequestParam(name = "spotOperationEnabled", defaultValue = "false") boolean spotOperationEnabled,
            RedirectAttributes redirectAttributes
    ) {
        systemSettingService.updateSpotOperationEnabled(spotOperationEnabled);
        systemSettingService.updateDiscordBotToken(discordBotToken);
        try {
            discordBotManager.reload(discordBotToken);
            if (discordBotManager.isRunning()) {
                redirectAttributes.addFlashAttribute("message", "システム設定を更新しました。Discord Bot への接続に成功しました。");
            } else {
                redirectAttributes.addFlashAttribute("message", "システム設定を更新しました。Discord Bot はトークン未設定のため停止中です。");
            }
        } catch (InvalidTokenException ex) {
            redirectAttributes.addFlashAttribute("error", "Discord Bot Token が不正です。値を確認してください。");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            redirectAttributes.addFlashAttribute("error", "Discord Bot 起動中に割り込みが発生しました。再度お試しください。");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Discord Bot の起動に失敗しました。ログを確認してください。");
        }
        return "redirect:/admin/system-settings";
    }
}
