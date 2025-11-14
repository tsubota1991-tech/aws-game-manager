package tsubota1991tech.github.io.aws_game_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;

public interface CloudAccountRepository extends JpaRepository<CloudAccount, Long> {
}
