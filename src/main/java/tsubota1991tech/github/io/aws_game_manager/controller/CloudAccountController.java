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
import tsubota1991tech.github.io.aws_game_manager.repository.CloudAccountRepository;

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
                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/cloud_accounts/form";
        }
        cloudAccountRepository.save(cloudAccount);
        return "redirect:/admin/cloud-accounts";
    }

    /** 編集フォーム表示 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        CloudAccount account = cloudAccountRepository.findById(id)
                .orElse(null);

        if (account == null) {
            redirectAttributes.addFlashAttribute("message", "Cloud Account が見つかりません (id=" + id + ")");
            return "redirect:/admin/cloud-accounts";
        }

        model.addAttribute("cloudAccount", account);
        return "admin/cloud_accounts/form";
    }

    /** 更新処理 */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("cloudAccount") CloudAccount form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "admin/cloud_accounts/form";
        }

        CloudAccount account = cloudAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CloudAccount not found: " + id));

        // 必要な項目を上書き
        account.setName(form.getName());
        account.setAwsAccessKeyId(form.getAwsAccessKeyId());
        account.setAwsSecretAccessKey(form.getAwsSecretAccessKey());
        account.setDefaultRegion(form.getDefaultRegion());
        account.setMemo(form.getMemo());

        cloudAccountRepository.save(account);

        redirectAttributes.addFlashAttribute("message", "Cloud Account を更新しました。");
        return "redirect:/admin/cloud-accounts";
    }

    /** 削除 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        cloudAccountRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Cloud Account を削除しました。");
        return "redirect:/admin/cloud-accounts";
    }
}
