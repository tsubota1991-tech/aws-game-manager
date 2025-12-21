package tsubota1991tech.github.io.aws_game_manager.domain;

import java.time.LocalDateTime;

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

    // 基本情報
    private String name;
    private String description;
    private String gameType;
    private String region;
    private Integer port;
    private String ec2InstanceId;

    /**
     * スポットインスタンスとして運用するかどうか
     */
    @Column(nullable = false)
    private boolean spotInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloud_account_id")
    private CloudAccount cloudAccount;

    @Column(length = 100)
    private String lastStatus;

    // // ▼ 既存: ローカルPC側のバッチなどを紐付ける想定
    // @Column(length = 255)
    // private String localStartScriptPath;   // 例: C:\\scripts\\7dtd_start.bat

    // @Column(length = 255)
    // private String localStopScriptPath;    // 例: C:\\scripts\\7dtd_stop.bat

    // ▼ 既存: EC2 の現在の接続情報
    @Column(length = 100)
    private String publicIp;               // 例: 18.xxx.xxx.xxx

    @Column(length = 255)
    private String publicDns;              // 例: ec2-18-xxx-xxx-xxx.ap-northeast-1.compute.amazonaws.com

    // ▼ 追加: EC2 内にあるスクリプトのパス
    @Column(length = 255)
    private String startScriptPath;        // 例: /home/ubuntu/7dtd/startserver.sh

    @Column(length = 255)
    private String backupScriptPath;       // 例: /home/ubuntu/backup_7dtd.sh

    // ▼ 追加: 起動/停止や状態確認のメタ情報
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastStoppedAt;
    private LocalDateTime lastStatusCheckedAt;

    /**
     * 停止後に再起動を許可するまでの待機時間（分）
     */
    @Column
    private Integer restartCooldownMinutes = 5;

    /**
     * 起動中の状態確認を実施する間隔（分）
     */
    @Column
    private Integer statusCheckIntervalMinutes = 180;

    /**
     * 起動直後に Public IP を再取得するまでの待ち時間（秒）
     */
    @Column
    private Integer addressRefreshDelaySeconds = 20;

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

    public boolean isSpotInstance() {
        return spotInstance;
    }

    public void setSpotInstance(boolean spotInstance) {
        this.spotInstance = spotInstance;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    // public String getLocalStartScriptPath() {
    //     return localStartScriptPath;
    // }

    // public void setLocalStartScriptPath(String localStartScriptPath) {
    //     this.localStartScriptPath = localStartScriptPath;
    // }

    // public String getLocalStopScriptPath() {
    //     return localStopScriptPath;
    // }

    // public void setLocalStopScriptPath(String localStopScriptPath) {
    //     this.localStopScriptPath = localStopScriptPath;
    // }

    public String getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public String getPublicDns() {
        return publicDns;
    }

    public void setPublicDns(String publicDns) {
        this.publicDns = publicDns;
    }

    public String getStartScriptPath() {
        return startScriptPath;
    }

    public void setStartScriptPath(String startScriptPath) {
        this.startScriptPath = startScriptPath;
    }

    public String getBackupScriptPath() {
        return backupScriptPath;
    }

    public void setBackupScriptPath(String backupScriptPath) {
        this.backupScriptPath = backupScriptPath;
    }

    public LocalDateTime getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(LocalDateTime lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public LocalDateTime getLastStoppedAt() {
        return lastStoppedAt;
    }

    public void setLastStoppedAt(LocalDateTime lastStoppedAt) {
        this.lastStoppedAt = lastStoppedAt;
    }

    public LocalDateTime getLastStatusCheckedAt() {
        return lastStatusCheckedAt;
    }

    public void setLastStatusCheckedAt(LocalDateTime lastStatusCheckedAt) {
        this.lastStatusCheckedAt = lastStatusCheckedAt;
    }

    public Integer getRestartCooldownMinutes() {
        return restartCooldownMinutes;
    }

    public void setRestartCooldownMinutes(Integer restartCooldownMinutes) {
        this.restartCooldownMinutes = restartCooldownMinutes;
    }

    public Integer getStatusCheckIntervalMinutes() {
        return statusCheckIntervalMinutes;
    }

    public void setStatusCheckIntervalMinutes(Integer statusCheckIntervalMinutes) {
        this.statusCheckIntervalMinutes = statusCheckIntervalMinutes;
    }

    public Integer getAddressRefreshDelaySeconds() {
        return addressRefreshDelaySeconds;
    }

    public void setAddressRefreshDelaySeconds(Integer addressRefreshDelaySeconds) {
        this.addressRefreshDelaySeconds = addressRefreshDelaySeconds;
    }
}
