package tsubota1991tech.github.io.aws_game_manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AWSなどのクラウドアカウント情報
 */
@Entity
@Table(name = "cloud_accounts")
public class CloudAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任意の表示名（例: メインAWSアカウント） */
    @Column(nullable = false, length = 100)
    private String name;

    /** AWS Access Key ID */
    @Column(name = "aws_access_key_id", length = 100)
    private String awsAccessKeyId;

    /** AWS Secret Access Key */
    @Column(name = "aws_secret_access_key", length = 200)
    private String awsSecretAccessKey;

    /** デフォルトリージョン (例: ap-northeast-1) */
    @Column(name = "default_region", length = 50)
    private String defaultRegion;

    /** メモ */
    @Column(length = 255)
    private String memo;

    // ====== getter / setter ======

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

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public void setAwsAccessKeyId(String awsAccessKeyId) {
        this.awsAccessKeyId = awsAccessKeyId;
    }

    public String getAwsSecretAccessKey() {
        return awsSecretAccessKey;
    }

    public void setAwsSecretAccessKey(String awsSecretAccessKey) {
        this.awsSecretAccessKey = awsSecretAccessKey;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
