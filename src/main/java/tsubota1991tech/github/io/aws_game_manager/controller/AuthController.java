package tsubota1991tech.github.io.aws_game_manager.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import tsubota1991tech.github.io.aws_game_manager.domain.AppUser;
import tsubota1991tech.github.io.aws_game_manager.repository.AppUserRepository;

@Controller
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** ログイン画面表示（Spring Security がこのURLを使う） */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** ユーザー登録画面表示 */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("appUser", new AppUser());
        return "register";
    }

    /** ユーザー登録処理 */
    @PostMapping("/register")
    public String register(@ModelAttribute("appUser") AppUser formUser,
                           Model model) {

        // 既に同じメールが登録されていないかチェック
        appUserRepository.findByEmail(formUser.getEmail())
                .ifPresent(u -> {
                    throw new IllegalArgumentException("既に登録済みのメールアドレスです");
                });

        AppUser user = new AppUser();
        user.setEmail(formUser.getEmail());
        user.setPassword(passwordEncoder.encode(formUser.getPassword()));
        user.setRole("USER");

        appUserRepository.save(user);

        // 登録後はログイン画面へ
        return "redirect:/login?registered";
    }
}
