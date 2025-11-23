package tsubota1991tech.github.io.aws_game_manager.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import tsubota1991tech.github.io.aws_game_manager.domain.AppUser;
import tsubota1991tech.github.io.aws_game_manager.repository.AppUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class ProfileController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/profile")
    public String showProfile(Model model) {
        AppUser currentUser = getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("ログインユーザーが見つかりません"));

        UpdateProfileForm form = new UpdateProfileForm();
        form.setEmail(currentUser.getEmail());
        model.addAttribute("profileForm", form);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute("profileForm") UpdateProfileForm form, Model model) {
        AppUser currentUser = getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("ログインユーザーが見つかりません"));

        List<String> errors = new ArrayList<>();
        boolean updated = false;

        String newEmail = form.getEmail() == null ? "" : form.getEmail().trim();
        if (!StringUtils.hasText(newEmail)) {
            errors.add("メールアドレスを入力してください。");
        } else if (!newEmail.equals(currentUser.getEmail())) {
            appUserRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(currentUser.getId())) {
                    errors.add("すでに利用されているメールアドレスです。");
                }
            });
        }

        if (StringUtils.hasText(form.getNewPassword())) {
            if (!StringUtils.hasText(form.getCurrentPassword()) ||
                    !passwordEncoder.matches(form.getCurrentPassword(), currentUser.getPassword())) {
                errors.add("現在のパスワードが正しくありません。");
            }
            if (!form.getNewPassword().equals(form.getConfirmPassword())) {
                errors.add("新しいパスワードと確認用パスワードが一致しません。");
            }
        }

        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return "profile";
        }

        if (!newEmail.equals(currentUser.getEmail())) {
            currentUser.setEmail(newEmail);
            updated = true;
        }

        if (StringUtils.hasText(form.getNewPassword())) {
            currentUser.setPassword(passwordEncoder.encode(form.getNewPassword()));
            updated = true;
        }

        if (updated) {
            appUserRepository.save(currentUser);
            refreshAuthentication(currentUser);
            model.addAttribute("message", "ユーザー情報を更新しました。");
        } else {
            model.addAttribute("message", "変更はありませんでした。");
        }

        return "profile";
    }

    private Optional<AppUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return appUserRepository.findByEmail(authentication.getName());
    }

    private void refreshAuthentication(AppUser updatedUser) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + updatedUser.getRole()));
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                updatedUser.getEmail(),
                updatedUser.getPassword(),
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    public static class UpdateProfileForm {
        private String email;
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }
}
