package tsubota1991tech.github.io.aws_game_manager.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
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

    private static final List<String> INSTANCE_TYPE_OPTIONS = List.of(
            "c6a.large", "c6i.large", "c7i.large", "m6a.large", "m6i.large",
            "m7i.large", "r6a.large", "r6i.large", "t3.large", "t3.xlarge", "t4g.large"
    );

    private final SystemSettingService systemSettingService;
    private final DiscordBotManager discordBotManager;

    public SystemSettingController(SystemSettingService systemSettingService, DiscordBotManager discordBotManager) {
        this.systemSettingService = systemSettingService;
        this.discordBotManager = discordBotManager;
    }

    @GetMapping
    public String showForm(Model model) {
        String discordToken = systemSettingService.getDiscordBotToken();
        List<String> autoScalingInstanceTypes = systemSettingService.getAutoScalingInstanceTypes();
        model.addAttribute("discordBotToken", discordToken);
        model.addAttribute("autoScalingInstanceTypes", autoScalingInstanceTypes);
        model.addAttribute("availableInstanceTypes", INSTANCE_TYPE_OPTIONS);
        model.addAttribute("autoScalingInstanceTypesJoined", String.join(",", autoScalingInstanceTypes));
        model.addAttribute("spotOperationEnabled", systemSettingService.isSpotOperationEnabled());
        return "admin/system_settings/form";
    }

    @PostMapping
    public String update(
            @RequestParam(name = "discordBotToken", required = false) String discordBotToken,
            @RequestParam(name = "spotOperationEnabled", defaultValue = "false") boolean spotOperationEnabled,
            @RequestParam(name = "autoScalingInstanceTypes", required = false) List<String> autoScalingInstanceTypes,
            @RequestParam(name = "autoScalingInstanceTypesManual", required = false) String autoScalingInstanceTypesManual,
            RedirectAttributes redirectAttributes
    ) {
        systemSettingService.updateSpotOperationEnabled(spotOperationEnabled);
        systemSettingService.updateDiscordBotToken(discordBotToken);
        systemSettingService.updateAutoScalingInstanceTypes(
                mergeInstanceTypes(autoScalingInstanceTypes, autoScalingInstanceTypesManual)
        );
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

    private List<String> mergeInstanceTypes(List<String> selected, String manual) {
        List<String> merged = new ArrayList<>();
        if (selected != null) {
            merged.addAll(selected);
        }
        if (StringUtils.hasText(manual)) {
            Arrays.stream(manual.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(merged::add);
        }
        return merged;
    }
}
