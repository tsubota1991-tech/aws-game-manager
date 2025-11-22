package tsubota1991tech.github.io.aws_game_manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import tsubota1991tech.github.io.aws_game_manager.service.SystemSettingService;

@Controller
@RequestMapping("/admin/system-settings")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    public SystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public String showForm(Model model) {
        String discordToken = systemSettingService.getDiscordBotToken();
        model.addAttribute("discordBotToken", discordToken);
        return "admin/system_settings/form";
    }

    @PostMapping
    public String update(
            @RequestParam(name = "discordBotToken", required = false) String discordBotToken,
            RedirectAttributes redirectAttributes
    ) {
        systemSettingService.updateDiscordBotToken(discordBotToken);
        redirectAttributes.addFlashAttribute("message", "システム設定を更新しました。");
        return "redirect:/admin/system-settings";
    }
}
