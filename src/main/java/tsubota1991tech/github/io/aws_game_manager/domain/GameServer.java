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

/**
 * GameServer エンティティ
 * 管理対象のゲームサーバ情報（EC2インスタンスIDなど）を保持する。
 */
@Entity
@Table(name = "game_servers")
public class GameServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** サーバ表示名（例：7DTD本番、Minecraftテスト など） */
    @Column(nullable = false, length = 100)
    private String name;

    /** ゲーム種別（例：7dtd, minecraft など） */
    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    /** 利用するクラウドアカウント（AWS） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloud_account_id", nullable = false)
    private CloudAccount cloudAccount;

    /** リージョン（CloudAccount と同じでもよいが、個別指定も許可） */
    @Column(nullable = false, length = 50)
    private String region;

    /** EC2 インスタンスID（例：i-0123456789abcdef0） */
    @Column(name = "ec2_instance_id", nullable = false, length = 50)
    private String ec2InstanceId;

    /** 接続ポート番号（例：26900） */
    @Column(nullable = false)
    private Integer port;

    /** 説明・メモ */
    @Column(length = 500)
    private String description;

    /** 最終状態（起動中 / 停止中 など） */
    @Column(name = "last_status", length = 50)
    private String lastStatus;

    // ===== getter / setter =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public CloudAccount getCloudAccount() {
        return cloudAccount;
    }

    public void setCloudAccount(CloudAccount cloudAccount) {
        this.cloudAccount = cloudAccount;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEc2InstanceId() {
        return ec2InstanceId;
    }

    public void setEc2InstanceId(String ec2InstanceId) {
        this.ec2InstanceId = ec2InstanceId;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }
}
