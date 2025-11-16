package tsubota1991tech.github.io.aws_game_manager.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tsubota1991tech.github.io.aws_game_manager.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);
}
