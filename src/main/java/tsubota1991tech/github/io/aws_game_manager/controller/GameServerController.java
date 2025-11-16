package tsubota1991tech.github.io.aws_game_manager.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.repository.CloudAccountRepository;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;

/**
 * GameServer 管理用コントローラ
 * /admin/game-servers 配下で一覧＆新規登録を行う。
 */
@Controller
@RequestMapping("/admin/game-servers")
public class GameServerController {

    private final GameServerRepository gameServerRepository;
    private final CloudAccountRepository cloudAccountRepository;

    public GameServerController(GameServerRepository gameServerRepository,
                                CloudAccountRepository cloudAccountRepository) {
        this.gameServerRepository = gameServerRepository;
        this.cloudAccountRepository = cloudAccountRepository;
    }

    /** 一覧表示 */
    @GetMapping
    public String list(Model model) {
        List<GameServer> servers = gameServerRepository.findAll();
        model.addAttribute("servers", servers);
        return "admin/game_servers/list";
    }

    /** 新規登録フォーム表示 */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        GameServer gameServer = new GameServer();
        model.addAttribute("gameServer", gameServer);

        List<CloudAccount> accounts = cloudAccountRepository.findAll();
        model.addAttribute("accounts", accounts);

        return "admin/game_servers/form";
    }

    /** 新規登録処理 */
    @PostMapping
    public String create(@ModelAttribute("gameServer") @Valid GameServer gameServer,
                         BindingResult bindingResult,
                         Model model) {

        if (bindingResult.hasErrors()) {
            List<CloudAccount> accounts = cloudAccountRepository.findAll();
            model.addAttribute("accounts", accounts);
            return "admin/game_servers/form";
        }

        gameServerRepository.save(gameServer);
        return "redirect:/admin/game-servers";
    }
}
