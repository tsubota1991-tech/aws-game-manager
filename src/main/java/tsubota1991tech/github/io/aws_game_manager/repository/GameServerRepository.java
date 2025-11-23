package tsubota1991tech.github.io.aws_game_manager.repository;

import java.util.List;
import java.util.Optional;   // ★ 必須！

import org.springframework.data.jpa.repository.JpaRepository;

import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;

public interface GameServerRepository extends JpaRepository<GameServer, Long> {

    List<GameServer> findByGameType(String gameType);

    Optional<GameServer> findByName(String name);  // ★ これでOK
}
