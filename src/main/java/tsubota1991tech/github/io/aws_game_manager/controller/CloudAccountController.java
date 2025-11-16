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
import tsubota1991tech.github.io.aws_game_manager.repository.CloudAccountRepository;

/**
 * CloudAccount 管理用コントローラ
 * /admin/cloud-accounts 配下で一覧＆新規登録を行う。
 */
@Controller
@RequestMapping("/admin/cloud-accounts")
public class CloudAccountController {

    private final CloudAccountRepository cloudAccountRepository;

    public CloudAccountController(CloudAccountRepository cloudAccountRepository) {
        this.cloudAccountRepository = cloudAccountRepository;
    }

    /** 一覧表示 */
    @GetMapping
    public String list(Model model) {
        List<CloudAccount> accounts = cloudAccountRepository.findAll();
        model.addAttribute("accounts", accounts);
        return "admin/cloud_accounts/list";
    }

    /** 新規登録フォーム表示 */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("cloudAccount", new CloudAccount());
        return "admin/cloud_accounts/form";
    }

    /** 新規登録処理 */
    @PostMapping
    public String create(@ModelAttribute("cloudAccount") @Valid CloudAccount cloudAccount,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/cloud_accounts/form";
        }
        cloudAccountRepository.save(cloudAccount);
        return "redirect:/admin/cloud-accounts";
    }
}
