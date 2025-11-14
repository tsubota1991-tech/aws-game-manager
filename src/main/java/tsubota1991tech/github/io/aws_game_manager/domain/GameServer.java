package tsubota1991tech.github.io.aws_game_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_servers")
public class GameServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloud_account_id", nullable = false)
    private CloudAccount cloudAccount;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "ec2_instance_id", nullable = false, length = 50)
    private String ec2InstanceId;

    @Column(nullable = false)
    private Integer port;

    @Column(length = 500)
    private String description;

    @Column(name = "last_status", length = 50)
    private String lastStatus;

    // getter / setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public CloudAccount getCloudAccount() { return cloudAccount; }
    public void setCloudAccount(CloudAccount cloudAccount) { this.cloudAccount = cloudAccount; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEc2InstanceId() { return ec2InstanceId; }
    public void setEc2InstanceId(String ec2InstanceId) { this.ec2InstanceId = ec2InstanceId; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
}
