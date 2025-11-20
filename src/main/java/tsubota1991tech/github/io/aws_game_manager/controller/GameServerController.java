package tsubota1991tech.github.io.aws_game_manager.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.exception.GameServerOperationException;
import tsubota1991tech.github.io.aws_game_manager.repository.CloudAccountRepository;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;
import tsubota1991tech.github.io.aws_game_manager.service.GameServerService;

@Controller
@RequestMapping("/admin/game-servers")  // ★ ここを画面と合わせる（ハイフン）
public class GameServerController {

    private final GameServerRepository gameServerRepository;
    private final CloudAccountRepository cloudAccountRepository;
    private final GameServerService gameServerService;

    public GameServerController(GameServerRepository gameServerRepository,
                                CloudAccountRepository cloudAccountRepository,
                                GameServerService gameServerService) {
        this.gameServerRepository = gameServerRepository;
        this.cloudAccountRepository = cloudAccountRepository;
        this.gameServerService = gameServerService;
    }

    /** 一覧表示 */
    @GetMapping
    public String list(Model model) {
        List<GameServer> servers = gameServerRepository.findAll();
        model.addAttribute("servers", servers);
        return "admin/game_servers/list"; // ★ Thymeleaf テンプレートパス
    }

    /** 新規登録フォーム表示 */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("gameServer", new GameServer());

        List<CloudAccount> accounts = cloudAccountRepository.findAll();
        model.addAttribute("cloudAccounts", accounts);

        return "admin/game_servers/form";
    }

    /** 新規登録処理 */
    @PostMapping
    public String create(@ModelAttribute("gameServer") @Valid GameServer form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            List<CloudAccount> accounts = cloudAccountRepository.findAll();
            model.addAttribute("cloudAccounts", accounts);
            return "admin/game_servers/form";
        }

        // CloudAccount を紐づけ
        GameServer server = form;
        if (form.getCloudAccount() != null && form.getCloudAccount().getId() != null) {
            CloudAccount account = cloudAccountRepository.findById(form.getCloudAccount().getId())
                    .orElse(null);
            server.setCloudAccount(account);

            // region 未入力なら CloudAccount の defaultRegion をコピー
            if (server.getRegion() == null || server.getRegion().isBlank()) {
                if (account != null) {
                    server.setRegion(account.getDefaultRegion());
                }
            }
        }

        gameServerRepository.save(server);
        redirectAttributes.addFlashAttribute("message", "GameServer を登録しました。");
        return "redirect:/admin/game-servers";
    }

    /** 編集フォーム表示 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        GameServer server = gameServerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GameServer not found: " + id));

        model.addAttribute("gameServer", server);

        List<CloudAccount> accounts = cloudAccountRepository.findAll();
        model.addAttribute("cloudAccounts", accounts);

        return "admin/game_servers/form";
    }

    /** 更新処理 */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("gameServer") GameServer form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            List<CloudAccount> accounts = cloudAccountRepository.findAll();
            model.addAttribute("cloudAccounts", accounts);
            return "admin/game_servers/form";
        }

        GameServer server = gameServerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GameServer not found: " + id));

        server.setName(form.getName());
        server.setDescription(form.getDescription());
        server.setGameType(form.getGameType());
        server.setRegion(form.getRegion());
        server.setPort(form.getPort());
        server.setEc2InstanceId(form.getEc2InstanceId());
        server.setStartScriptPath(form.getStartScriptPath());
        server.setBackupScriptPath(form.getBackupScriptPath());

        // CloudAccount の再紐づけ
        if (form.getCloudAccount() != null && form.getCloudAccount().getId() != null) {
            CloudAccount account = cloudAccountRepository.findById(form.getCloudAccount().getId())
                    .orElse(null);
            server.setCloudAccount(account);
        } else {
            server.setCloudAccount(null);
        }

        gameServerRepository.save(server);

        redirectAttributes.addFlashAttribute("message", "GameServer を更新しました。");
        return "redirect:/admin/game-servers";
    }

    /** 削除 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        gameServerRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "GameServer を削除しました。");
        return "redirect:/admin/game-servers";
    }

    /** 起動ボタン */
    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            gameServerService.startServer(id);
            redirectAttributes.addFlashAttribute("message", "サーバを起動しました。");
        } catch (GameServerOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/game-servers";
    }

    /** 停止ボタン */
    @PostMapping("/{id}/stop")
    public String stop(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            gameServerService.stopServer(id);
            redirectAttributes.addFlashAttribute("message", "サーバを停止しました。");
        } catch (GameServerOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/game-servers";
    }

    /** 状態更新ボタン */
    @PostMapping("/{id}/refresh")
    public String refresh(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            gameServerService.refreshStatus(id);
            redirectAttributes.addFlashAttribute("message", "ステータスを更新しました。");
        } catch (GameServerOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/game-servers";
    }
}
