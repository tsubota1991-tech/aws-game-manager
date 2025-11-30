package tsubota1991tech.github.io.aws_game_manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * シンプルなホームコントローラ
 * ルートにアクセスしたら game-servers の一覧へリダイレクト。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String root() {
        return "redirect:/admin/game-servers";
    }
}
