package tsubota1991tech.github.io.aws_game_manager.repository;

import java.util.List;
import java.util.Optional;   // ★ 必須！

import org.springframework.data.jpa.repository.JpaRepository;

import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;

public interface GameServerRepository extends JpaRepository<GameServer, Long> {

    List<GameServer> findByGameType(String gameType);

    Optional<GameServer> findByName(String name);  // ★ これでOK

    Optional<GameServer> findByEc2InstanceId(String ec2InstanceId);

    Optional<GameServer> findByAutoScalingGroupName(String autoScalingGroupName);
}
