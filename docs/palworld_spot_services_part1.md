# ② スポットインスタンス運用に必要なサービス準備と設定値（前半）

ここではベース AMI を基にスポット運用するための周辺サービスを整備します。値は ap-northeast-1 を例にしています。

## 0. VPC 作成例（コンソール画面の実値）
- **作成するリソース**: VPC のみ
- **名前タグ**: `pal-spot-vpc`（キー: `Name`, 値: `pal-spot-vpc`）
- **IPv4 CIDR ブロック**: 手動入力 `10.0.0.0/24`
- **IPv6 CIDR ブロック**: なし
- **テナンシー**: デフォルト
- **VPC フローログ**: なし（必要に応じて `Version=V2`, 出力先 CloudWatch Logs を後から有効化）

## 1. IAM とログ・パラメータ管理
- **IAM ロール/インスタンスプロファイル**: `PalworldSpotInstanceRole`
  - コンソール作成例:
    - 信頼されたエンティティタイプ: **AWS のサービス** → **EC2**
    - ロール名: `PalworldSpotInstanceRole`
    - 権限の割り当て: 次のポリシーを選択してアタッチ
      - `AmazonSSMManagedInstanceCore`
      - `AmazonEC2ReadOnlyAccess` (インスタンスメタデータ・タグ参照用最小権限に絞ること推奨)
      - `CloudWatchAgentServerPolicy`（メトリクス送信）
      - `AmazonElasticFileSystemClientFullAccess`
      - 任意: `palworld-backup-prod` バケットのみに `s3:PutObject` を許可するカスタムポリシー（`Resource` を `arn:aws:s3:::palworld-backup-prod/save/*` に限定）
    - タグ: 必要に応じ `Name=pal-spot-role`, `Project=palworld`, `Env=prod`
    - インスタンスプロファイル: 同名で自動作成されるものを EC2 にアタッチ
  - **Parameter Store (任意)**: `palworld/server/AdminPassword` を SecureString、KMS デフォルトキーで暗号化。`Tier=Standard`, `DataType=text`。
  - **CloudWatch Logs**: `/palworld/server` ロググループを事前作成し、`Retention=30 days`。CloudWatch Agent の `logs/metrics` セクションで `palserver.service` の標準出力を `/var/log/journal` から収集。

## 2. ネットワークと EFS
- **サブネット例**: `10.0.0.0/24` VPC 内にプライベートサブネットを 2 つ作成（例: `10.0.0.0/25` in apne1a, `10.0.0.128/25` in apne1c）。ASG は両方に配置し、EFS マウントターゲットも各 AZ に配置。
- **セキュリティグループ**: 
  - EC2 用 SG: `8211/udp`（0.0.0.0/0 または必要クライアント）、`22/tcp`（管理元 IP のみ）をインバウンド許可。アウトバウンドは EFS SG に対して `2049/tcp` を許可。
  - EFS 用 SG: EC2 SG からの `2049/tcp` をインバウンド許可。アウトバウンドはデフォルト許可。
- **EFS 設定例**: `PerformanceMode=General Purpose`, `ThroughputMode=Bursting`, バースト容量不足時のみ `Provisioned` に変更。バックアップを有効化する場合は AWS Backup プランで 1 日 1 回、保持 30 日。

## 3. S3 バックアップ（任意）
- バケット例: `palworld-backup-prod`
- バケットポリシー: インスタンスプロファイルのロールからの `s3:PutObject` を許可。
- 運用案: ライフサイクルルール `Prefix=save/`, `Expiration=30 days` を設定。`/efs/palworld/save` を1時間毎に `aws s3 sync /efs/palworld/save s3://palworld-backup-prod/save/ --delete` で同期する cron または Lambda を用意。

## 4. Launch Template (LT) 作成
- **名前**: `palworld-spot-lt-v1`
- **AMI**: ①で作成したベース AMI
- **インスタンスタイプ**: 複数候補を `InstanceTypesOverrides` で指定（例: `c6i.large`, `m6a.large`, `c5.large`）。
- **キーペア**: 管理用を指定
- **ネットワーク**: 前述のプライベートサブネット/SG を紐付け（例: `subnet-xxxxxxxx_apne1a_private`, `sg-ec2-palworld-spot`）
- **ブロックデバイス**: ルート 30〜50GB gp3
- **IAM インスタンスプロファイル**: `PalworldSpotInstanceRole`
- **詳細監視**: 有効化（`Detailed Monitoring`）
- **タグ例**: `Name=pal-spot-ec2`, `Project=palworld`, `Env=prod`, `Owner=ops-team`（`LaunchTemplate` のタグ伝播を有効化）
- **ユーザーデータ例**: EFS マウント・設定投入（cloud-init）
```bash
#!/bin/bash
set -xe
apt-get update
apt-get install -y amazon-efs-utils awscli
# fstab 追記
if ! grep -q "efs.palworld" /etc/fstab; then
  echo "fs-1234567890abcdef.efs.ap-northeast-1.amazonaws.com:/ /efs efs defaults,_netdev 0 0" >> /etc/fstab
fi
mkdir -p /efs/palworld/{world,save,game}
mount -a
# 管理者パスワードを Parameter Store から取得する例（必要に応じて）
# ADMIN_PASS=$(aws ssm get-parameter --name palworld/server/AdminPassword --with-decryption --query 'Parameter.Value' --output text --region ap-northeast-1)
# sed -i "s/^AdminPassword=.*/AdminPassword=${ADMIN_PASS}/" /efs/palworld/save/Pal/Saved/Config/LinuxServer/PalWorldSettings.ini
systemctl daemon-reload
systemctl enable --now palserver.service
```

## 5. CloudWatch/SSM エージェント
- **CloudWatch Agent**: CPU/メモリ（procstat 監視含む）を送信し、アラームで ASG スケールアウト・中断通知を捕捉。
- **SSM エージェント**: Session Manager で鍵レス接続を行い、スポット置換時のデバッグを容易にする。

---
ここまででスポット用の起動テンプレートと基盤サービスが揃います。次は ASG や中断対応を含む後半設定を行います。
