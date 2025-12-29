# ① パルワールド用ベースEC2 (Ubuntu) 構築手順

パルワールドをスポットインスタンスで運用する前提として、オンデマンドで1台構築し、EFS上にワールド/セーブ/ゲームデータを配置した状態をAMI化します。以下は構築時の推奨設定例です。

## 1. ネットワーク・基本設定
- **VPC/サブネット**: コンソール作成例では VPC 名タグ `pal-spot-vpc`、IPv4 CIDR `10.0.0.0/24`（IPv6 なし）。`10.0.0.0/25`（apne1a）と `10.0.0.128/25`（apne1c）のプライベートサブネットを用意し、NAT Gateway や VPC エンドポイントを配置して SteamCMD ダウンロードやパッケージ取得を許可。
- **セキュリティグループ (SG)**: 
  - `8211/udp` (パルワールドデフォルト) をインターネット/必要クライアントに開放。
  - `22/tcp` (管理用) は管理端末からのみ許可。
  - EFS マウント用に、EFS 側 SG からの `2049/tcp` を許可。
- **キーペア**: 初期セットアップ/障害対応用に作成。
- **IAM ロール**: EC2 インスタンスプロファイルに `AmazonSSMManagedInstanceCore`、`AmazonElasticFileSystemClientFullAccess`、必要なら S3 読取/CloudWatch Logs 出力を付与。

## 2. EFS の準備
- **EFS ファイルシステム**: パフォーマンス `General Purpose`、スループット `Bursting` (負荷が高い場合は `Provisioned` も検討)。
- **マウントターゲット**: 利用する各 AZ のプライベートサブネットに作成。
- **セキュリティグループ**: EC2 からの `2049/tcp` を許可する SG をアタッチ。
- **ディレクトリ構成 (例)**: `/efs/palworld/{world,save,game}`。

### EC2 でのマウント例
```bash
sudo apt update
sudo apt install -y amazon-efs-utils
sudo mkdir -p /efs/palworld/{world,save,game}
# ファイルシステムID を置換
echo "fs-1234567890abcdef.efs.ap-northeast-1.amazonaws.com:/ /efs efs defaults,_netdev 0 0" | sudo tee -a /etc/fstab
sudo mount -a
```

## 3. パルワールドサーバー導入 (EFS 利用)
- **前提パッケージ**: `curl`, `steamcmd`, `lib32gcc-s1` 等。
- **SteamCMD インストール例**:
```bash
sudo apt install -y software-properties-common
sudo add-apt-repository multiverse
sudo dpkg --add-architecture i386
sudo apt update
sudo apt install -y steamcmd
```
- **ゲームサーバー配置**: EFS 上の `/efs/palworld/game` をワークディレクトリにし、`steamcmd +login anonymous +app_update 2394010 validate +quit` で展開。`/efs/palworld/save` に `Saved` ディレクトリを配置し、`GameUserSettings.ini` など設定ファイルを EFS へ置く。
- **シンボリックリンク**: 必要に応じ、`/home/ubuntu/PalServer/Saved` などローカルパスを EFS の `/efs/palworld/save` へリンク。

## 4. 自動起動設定例 (systemd)
`/etc/systemd/system/palserver.service`:
```ini
[Unit]
Description=Palworld Dedicated Server
After=network.target nss-lookup.target
Requires=network.target

[Service]
Type=simple
WorkingDirectory=/efs/palworld/game
ExecStart=/usr/games/steamcmd +login anonymous +app_update 2394010 validate +quit && \\
  /efs/palworld/game/PalServer.sh -useperfthreads -NoAsyncLoadingThread -UseMultithreadForDS -EpicApp=PalServer -port=8211 -queryport=27015
Environment="SteamAppId=2394010"
Restart=always
RestartSec=5
User=ubuntu

[Install]
WantedBy=multi-user.target
```
有効化:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now palserver
```

## 5. 動作確認と AMI 化
1. EFS マウント状態でサーバーが起動し、クライアントから接続できることを確認。
2. `Saved` データが EFS に蓄積されることを確認。
3. 不要ファイルを削除し、`/var/log/cloud-init.log` など機微を必要に応じて整理。
4. この EC2 を停止し、AMI を作成 (以降の Launch Template で参照)。

---
このベース AMI がスポット環境の起動元になります。EFS にデータを置くことで、スポット停止・再取得時もワールド/セーブ/ゲームファイルを継続利用できます。
