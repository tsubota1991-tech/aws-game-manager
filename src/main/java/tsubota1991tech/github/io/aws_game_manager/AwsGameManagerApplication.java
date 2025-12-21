package tsubota1991tech.github.io.aws_game_manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AwsGameManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AwsGameManagerApplication.class, args);
	}

}
