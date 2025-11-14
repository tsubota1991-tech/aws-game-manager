package tsubota1991tech.github.io.aws_game_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cloud_accounts")
public class CloudAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "access_key_id", nullable = false, length = 100)
    private String accessKeyId;

    @Column(name = "secret_access_key", nullable = false, length = 200)
    private String secretAccessKey;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(length = 500)
    private String memo;

    // getter / setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
