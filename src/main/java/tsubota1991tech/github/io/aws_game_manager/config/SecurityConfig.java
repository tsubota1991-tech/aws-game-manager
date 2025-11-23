package tsubota1991tech.github.io.aws_game_manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import tsubota1991tech.github.io.aws_game_manager.repository.AppUserRepository;

@Configuration
public class SecurityConfig {

    /**
     * パスワードエンコーダ（BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * ユーザー情報の読み込み方法を定義
     * ログイン時に email を username として扱う。
     */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository appUserRepository) {
        return new UserDetailsService() {
            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                // username ＝ フォームで入力されたメールアドレス
                var user = appUserRepository.findByEmail(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

                // AppUser.role には "ADMIN" や "USER" を入れておく想定
                return User.withUsername(user.getEmail())
                        .password(user.getPassword())  // 既にハッシュ済み
                        .roles(user.getRole())         // 例: "ADMIN" -> ROLE_ADMIN
                        .build();
            }
        };
    }

    /**
     * セキュリティのメイン設定
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // H2 コンソール用に CSRF 無効化＆frame許可
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))

                // アクセス制御
                .authorizeHttpRequests(auth -> auth
                        // 誰でもアクセスOK
                        .requestMatchers(
                                "/login",
                                "/h2-console/**",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()
                        // ★ /register は管理者のみ
                        .requestMatchers("/register").hasRole("ADMIN")
                        // それ以外はログイン必須
                        .anyRequest().authenticated()
                )

                // フォームログイン設定
                .formLogin(form -> form
                        .loginPage("/login")                              // ログイン画面のURL
                        .defaultSuccessUrl("/admin/game-servers", true)   // ログイン後の遷移先
                        .permitAll()
                )

                // ログアウト設定
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )

                // HTTP Basic（必要なら）
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
