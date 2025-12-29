# ② スポットインスタンス運用に必要なサービス準備と設定値（前半）

ここではベース AMI を基にスポット運用するための周辺サービスを整備します。値は ap-northeast-1 を例にしています。

## 1. IAM とログ・パラメータ管理
- **IAM ロール/インスタンスプロファイル**: `PalworldSpotInstanceRole`
  - `AmazonSSMManagedInstanceCore`
  - `AmazonEC2ReadOnlyAccess` (インスタンスメタデータ・タグ参照用最小権限に絞ること推奨)
  - `CloudWatchAgentServerPolicy`（メトリクス送信）
  - `AmazonElasticFileSystemClientFullAccess`
  - 必要に応じて S3 バックアップ用の限定ポリシー（`s3:PutObject` をバケットプレフィックスに限定）。
- **Parameter Store (任意)**: `palworld/server/AdminPassword` などを SecureString で管理し、ユーザーデータで参照。
- **CloudWatch Logs**: `/palworld/server` ロググループを作成し、`palserver.service` の標準出力をジャーナル経由で転送する設定を CloudWatch Agent で実施。

## 2. ネットワークと EFS
- **サブネット**: 最低2 AZ のプライベートサブネットを ASG 用に指定し、マウントターゲットが同一 AZ に存在することを確認。
- **セキュリティグループ**: 
  - EC2 用 SG: `8211/udp`・`22/tcp`（管理時のみ）を許可。EFS SG への `2049/tcp` をアウトバウンド許可。
  - EFS 用 SG: EC2 SG からの `2049/tcp` をインバウンド許可。

## 3. S3 バックアップ（任意）
- バケット例: `palworld-backup-prod`
- バケットポリシー: インスタンスプロファイルのロールからの `s3:PutObject` を許可。
- 運用案: ライフサイクルで世代管理（例: 30日保持）。`/efs/palworld/save` を1時間毎に同期する cron または Lambda を用意。

## 4. Launch Template (LT) 作成
- **名前**: `palworld-spot-lt-v1`
- **AMI**: ①で作成したベース AMI
- **インスタンスタイプ**: 複数候補を `InstanceTypesOverrides` で指定（例: `c6i.large`, `m6a.large`, `c5.large`）。
- **キーペア**: 管理用を指定
- **ネットワーク**: 前述のプライベートサブネット/SG を紐付け
- **ブロックデバイス**: ルート 30〜50GB gp3
- **IAM インスタンスプロファイル**: `PalworldSpotInstanceRole`
- **詳細監視**: 有効化（`Detailed Monitoring`）
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
