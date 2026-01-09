package tsubota1991tech.github.io.aws_game_manager.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import tsubota1991tech.github.io.aws_game_manager.domain.AppUser;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;
import tsubota1991tech.github.io.aws_game_manager.domain.GameServer;
import tsubota1991tech.github.io.aws_game_manager.repository.AppUserRepository;
import tsubota1991tech.github.io.aws_game_manager.repository.CloudAccountRepository;
import tsubota1991tech.github.io.aws_game_manager.repository.GameServerRepository;
import tsubota1991tech.github.io.aws_game_manager.service.SystemSettingService;

/**
 * アプリ起動時に初期データを投入するクラス。
 *
 * ・管理者ユーザーが存在しなければ作成
 * ・CloudAccount が1件もなければテスト用を作成
 * ・GameServer が1件もなければテスト用を作成
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudAccountRepository cloudAccountRepository;
    private final GameServerRepository gameServerRepository;
    private final SystemSettingService systemSettingService;

    public DataInitializer(AppUserRepository appUserRepository,
                           PasswordEncoder passwordEncoder,
                           CloudAccountRepository cloudAccountRepository,
                           GameServerRepository gameServerRepository,
                           SystemSettingService systemSettingService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.cloudAccountRepository = cloudAccountRepository;
        this.gameServerRepository = gameServerRepository;
        this.systemSettingService = systemSettingService;
    }

    @Override
    public void run(String... args) throws Exception {
        initAdminUser();
        initCloudAccountsAndGameServers();
        initSystemSettings();
    }

    /** 管理者ユーザが居なければ作成 */
    private void initAdminUser() {
        String adminEmail = "admin@example.com";
        String adminRawPassword = "admin123"; // 開発用。実運用では環境変数などに逃がす

        boolean exists = appUserRepository.findByEmail(adminEmail).isPresent();
        if (!exists) {
            AppUser admin = new AppUser();
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminRawPassword));
            admin.setRole("ADMIN");
            appUserRepository.save(admin);

            System.out.println("=== 初期管理者ユーザーを作成しました ===");
            System.out.println("  email    : " + adminEmail);
            System.out.println("  password : " + adminRawPassword);
        } else {
            System.out.println("初期管理者ユーザーは既に存在します: " + adminEmail);
        }
    }

    /** CloudAccount / GameServer が空ならテストデータを投入 */
    private void initCloudAccountsAndGameServers() {
        if (cloudAccountRepository.count() == 0) {
            CloudAccount mainAws = new CloudAccount();
            mainAws.setName("メインAWSアカウント");
            mainAws.setAwsAccessKeyId("AwsAccessKeyId");
            mainAws.setAwsSecretAccessKey("SecretAccessKey");
            mainAws.setDefaultRegion("ap-northeast-1");
            mainAws.setMemo("ローカル開発用ダミーアカウント情報（本番では差し替え）");

            mainAws = cloudAccountRepository.save(mainAws);

            System.out.println("=== 初期CloudAccountを作成しました ===");
            System.out.println("  id   : " + mainAws.getId());
            System.out.println("  name : " + mainAws.getName());
        } else {
            System.out.println("CloudAccount は既に登録されています。");
        }

        if (gameServerRepository.count() == 0) {
            // どの CloudAccount を使うか（先頭の1件を使う）
            CloudAccount account = cloudAccountRepository.findAll()
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (account == null) {
                System.out.println("GameServer を作成したかったが CloudAccount がありません。");
                return;
            }

            GameServer server = new GameServer();
            server.setName("テストサーバ");
            server.setGameType("テストゲーム");
            server.setCloudAccount(account);
            server.setRegion(account.getDefaultRegion());
            server.setEc2InstanceId("InstanceId");
            server.setAutoScalingGroupName("");
            server.setAsgDesiredCapacity(1);
            server.setPort(26900);
            server.setDescription("ローカル開発用のテストサーバ");
            server.setLastStatus("UNKNOWN");
            server.setSpotInstance(false);
            server.setRestartCooldownMinutes(5);
            server.setStatusCheckIntervalMinutes(180);
            server.setAddressRefreshDelaySeconds(20);

            // ★ ここで EC2 内のスクリプトパスを設定
            server.setStartScriptPath("/home/ubuntu/startserver.sh");
            server.setBackupScriptPath("/home/ubuntu/backup.sh");

            server = gameServerRepository.save(server);


            System.out.println("=== 初期GameServerを作成しました ===");
            System.out.println("  id   : " + server.getId());
            System.out.println("  name : " + server.getName());
        } else {
            System.out.println("GameServer は既に登録されています。");
        }
    }

    private void initSystemSettings() {
        systemSettingService.ensureSettingExists(SystemSettingService.DISCORD_BOT_TOKEN_KEY, "");
        systemSettingService.ensureSettingExists(SystemSettingService.SPOT_OPERATION_ENABLED_KEY, "false");
        systemSettingService.ensureSettingExists(SystemSettingService.AUTO_SCALING_INSTANCE_TYPES_KEY, "");
    }
}
