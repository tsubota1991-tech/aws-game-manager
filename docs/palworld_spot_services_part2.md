# ③ スポットインスタンス運用に必要なサービス準備と設定値（後半）

前半で作成した Launch Template を用い、ASG と中断時のハンドリングを設定します。

## 1. Auto Scaling Group (ASG) の設定
- **名前**: `palworld-spot-asg`
- **起動テンプレート**: `palworld-spot-lt-v1`、`Version: Latest`
- **サブネット**: `subnet-xxxxxxxx_apne1a_private`, `subnet-yyyyyyyy_apne1c_private` を指定
- **キャパシティ**: `Desired=1`, `Min=0`, `Max=2`（ピークに合わせ調整）
- **Mixed Instances Policy**:
  - `OnDemandPercentageAboveBaseCapacity=0`（全スポット）
  - `SpotAllocationStrategy=capacity-optimized`
  - `SpotInstancePools=3`（確保性を高める）
- **ヘルスチェック**: EC2 + ELB/ターゲットグループ (任意) を有効化、`GracePeriod=300s`。
- **スケーリングポリシー例**: 
  - CPU > 70% が 5 分継続で `+1`。
  - 接続プレイヤー数（カスタムメトリクス）> 20 で `+1`。
- **ライフサイクルフック**: `Terminating:wait` を 120〜180 秒設定し、中断時にセーブ/通知を実行する Lambda または SSM Run Command を呼び出す。
- **その他の入力例（コンソールの値）**:
  - **AZ バランシング**: デフォルト（有効）
  - **インスタンスウォームアップ**: 300 秒
  - **スケールインの保護**: 初期は無効、必要に応じて特定インスタンスに付与
  - **ELB 側のヘルスチェック**: 使用する場合はターゲットグループを紐付け、`HealthCheckType=ELB`
  - **アタッチするターゲットグループ**: 任意 (`palworld-udp-tg` など)、`HealthCheckProtocol=TCP` を指定
  - **タグ**: `Name=pal-spot-asg`, `Project=palworld`, `Env=prod`, `Owner=ops-team`（`PropagateAtLaunch=true`）

## 2. スポット中断通知ハンドリング
- **メタデータ監視**: インスタンス内で 30 秒毎に `http://169.254.169.254/latest/meta-data/spot/instance-action` を監視する systemd サービス/cron を配置。
- **Graceful Shutdown の例 (擬似コード)**:
  - 中断通知を検知 → 管理 API/CLI でプレイヤー通知 → `/efs/palworld/save` を同期（必要なら S3 へ） → `systemctl stop palserver`。
- **CloudWatch Events (EventBridge)**: `EC2 Spot Instance Interruption Warning` イベントで Lambda を起動し、Discord/SNS へ通知。Lambda から SSM Run Command で上記停止スクリプトを実行する構成も有効。

## 3. 監視と復旧
- **CloudWatch Alarm**: 
  - `StatusCheckFailed_Instance` で自動再起動/復旧を ASG 任せにする。
  - `CPUUtilization`、`NetworkIn/Out`、カスタムメトリクス（接続数）を監視。
- **Self-healing**: ASG のヘルスチェック異常時にインスタンスを置換する設定を有効化。
- **タグ例**: `Name=pal-spot-asg`, `Project=palworld`, `Env=prod`, `Owner=ops-team`（`PropagateAtLaunch=true`）
- **AMI ローテーション**: ベース AMI 更新時は LT のバージョンを更新し、ASG でインスタンスリフレッシュを実施。
  - ロールアウト例: `MinHealthyPercentage=90`, `InstanceWarmup=300s`, `CheckpointDelay=0s`

---
# ④ 当システムでの設定

本リポジトリの管理画面/バックエンドでスポット運用を有効化する際のポイントです（AWS 側の準備が完了している前提）。

## 1. システム設定の有効化
- 管理画面のシステム設定で「スポット運用」を ON にする。
- アプリケーション設定で ASG 名/リージョンを保持する場合は、環境変数や設定ファイルに `palworld-spot-asg` などを登録。

## 2. サーバー単位のフラグ
- ゲームサーバー登録/編集画面で対象サーバーに「スポット運用」フラグを設定。
- フラグが ON のサーバーは、起動・停止時に上記 ASG/LT を使用する運用手順に合わせる。

## 3. 運用フロー例
1. システム設定でスポット運用を有効化。
2. 対象サーバーの「スポット運用」フラグを ON。
3. 管理画面から起動指示 → AWS 側では ASG Desired を 1 に変更（または起動 API が LT/ASG を呼び出す実装）。
4. 停止指示 → ASG Desired を 0 に戻す。中断通知を検知した際も停止スクリプトが EFS へセーブ。

## 4. 推奨タグ設計
- `Name=palworld-spot`、`Project=palworld`、`Env=prod`、`Owner=ops-team` 等を LT/ASG/EC2/EFS/S3 に共通付与し、コスト配分と運用可視化を行う。

## 5. テストチェックリスト
- 起動: ASG Desired=1 でインスタンスが起動し、EFS マウント済みでサーバーが自動起動する。
- 接続: クライアントから `8211/udp` で接続できる。
- 中断: Spot 中断通知を模擬 (`aws ec2 send-spot-instance-interruptions`) し、セーブ＆停止が実行される。
- 置換: ASG が新しいスポットインスタンスを補充し、同一 EFS から継続プレイできる。

以上で、AWS 側とシステム側の設定がそろい、パルワールドをスポットインスタンスで安定運用するための手順が整理されます。
