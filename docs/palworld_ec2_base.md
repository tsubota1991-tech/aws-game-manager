# ① パルワールド用ベースEC2 (Ubuntu) 構築手順

パルワールドをスポットインスタンスで運用する前提として、オンデマンドで1台構築し、EFS上にワールド/セーブ/ゲームデータを配置した状態をAMI化します。以下は構築時の推奨設定例です。

## 1. ネットワーク・基本設定（コンソール手順で解説）
- **VPC/サブネットの作成**  
  1. 左メニュー「VPC」→「VPC を作成」→「VPC のみ」を選択し、名前タグ `pal-spot-vpc`、IPv4 CIDR `10.0.0.0/24` を入力（IPv6 なし、テナンシー=デフォルト）。
  2. そのままか、左メニュー「サブネット」→「サブネットを作成」で次を入力して 2AZ 分作成:  
     - AZ: `ap-northeast-1a` / `ap-northeast-1c` を明示選択  
     - VPC: `pal-spot-vpc` をプルダウンで選択  
     - サブネット名: `in-apne1a` / `in-apne1c`  
      - IPv4 サブネット CIDR: 本番 `10.0.0.0/26`（apne1a）、`10.0.0.64/26`（apne1c）／検証なら `/28` に縮小可  
        - 理由: `/26` を使うと残りのアドレス帯にパブリックサブネット（例: `10.0.0.128/27`）を置けるため、重複エラーを回避できる  
     - タグ: `Project=palworld`, `Env=prod`
  3. VPC の設定で「DNS 解決を有効化」「DNS ホスト名を有効化」が ON のままであることを確認。
- **インターネット/NAT の作り方（コンソール手順）**:  
  1. 左メニュー「インターネットゲートウェイ」→「インターネットゲートウェイを作成」→名前タグ `pal-spot-igw` → 作成 → 「VPC にアタッチ」から `pal-spot-vpc` を選択してアタッチ。
  2. 左メニュー「サブネット」→ パブリック用に新規サブネット（例: `10.0.0.128/27`, AZ=apne1a, タグ `Name=pub-apne1a`）。`/26` で確保したプライベートと重ならない CIDR を選ぶ。`/25` のままでは重複するため必ず `/26` 以下に分割してから作成する。  
  3. 左メニュー「NAT ゲートウェイ」→「NAT ゲートウェイを作成」→ サブネットに上記パブリックサブネットを選択 → 「接続タイプ: パブリック」→ 新しい Elastic IP を割り当て → 作成。  
  4. 左メニュー「ルートテーブル」→「ルートテーブルを作成」→ 名前 `pal-spot-public-rt`, VPC=`pal-spot-vpc` → 作成後「ルートを編集」で `0.0.0.0/0 -> igw-xxxx` を追加 → 「サブネットの関連付け」でパブリックサブネットを関連付け。  
  5. 同様に「ルートテーブルを作成」→ 名前 `pal-spot-private-rt` → ルートに `0.0.0.0/0 -> nat-xxxx` を追加 → サブネットの関連付けでプライベートサブネット2つ（apne1a/apne1c）を関連付け。
- **VPC エンドポイント（任意だが推奨）**:
  - Gateway 型 S3（料金ほぼゼロ、強く推奨）  
    1. 左メニュー「エンドポイント」→「エンドポイントを作成」  
    2. サービスカテゴリで「AWS サービス」、検索で `s3` を入力し `com.amazonaws.ap-northeast-1.s3` を選択  
    3. タイプ「Gateway」を選択  
    4. VPC: `pal-spot-vpc` を選択  
    5. ルートテーブル: `pal-spot-private-rt` をチェック  
    6. ポリシーはデフォルト許可のまま → 「エンドポイントを作成」  
  - Interface 型（SSM/EC2Messages/SSMMessages/CloudWatchLogs/ECR api,dkr など、必要に応じて追加）  
    1. 同画面でサービス名を検索（例: `ssm`、`ec2messages`、`ssmmessages`、`logs`、`ecr.api`、`ecr.dkr`）を順に作成  
    2. タイプ「Interface」を選択  
    3. VPC: `pal-spot-vpc`  
    4. サブネット: プライベートサブネット 2 つ（`in-apne1a` と `in-apne1c`）をチェック  
    5. セキュリティグループ: `sg-ec2-palworld-spot` を指定（インバウンド 8211/udp・22/tcp を許可）  
    6. ポリシーはデフォルト許可のまま → 「エンドポイントを作成」  
    7. ルートは自動で作成されるため追加設定不要。作成完了を確認して次のエンドポイントを繰り返し作成する。
- **セキュリティグループ (SG) を画面の入力項目に紐付けて作成**  
  1. 左メニュー「セキュリティグループ」→「セキュリティグループを作成」  
  2. **セキュリティグループ名**: `sg-ec2-palworld-spot`、**説明**: `Palworld EC2`（任意）、**VPC**: `pal-spot-vpc` をプルダウンで選択  
  3. **インバウンドルールを追加**（画面下部の「インバウンドルールを追加」ボタンで行ごとに追加）  
     - 行1: タイプ=「カスタム UDP」、ポート範囲=`8211`、送信元=クライアント CIDR（例: `0.0.0.0/0` または許可したい固定 IP）  
     - 行2: タイプ=「SSH」、ポート範囲=`22`、送信元=管理端末の固定 IP（例: `203.0.113.10/32`）  
     - 備考: EFS 用の 2049/tcp は **インバウンドには追加不要**（アウトバウンドで許可するため）。  
  4. **アウトバウンドルール**  
     - 最も簡単: デフォルトの「すべてのトラフィック 0.0.0.0/0」を残す。  
     - 厳格にする場合: 行1=タイプ「カスタム TCP」、ポート範囲=`2049`、送信先=`sg-efs-palworld`（後述で作成）を追加し、行2=「カスタム TCP」ポート範囲=`443` 送信先=`0.0.0.0/0`（パッチや SSM/エンドポイント向け https 通信用）を残す。  
  5. **タグ**: `Name=sg-ec2-palworld-spot`, `Project=palworld`, `Env=prod` を入力 → 「セキュリティグループを作成」を押下。  
  6. 画面に戻り、同様の手順で **EFS 用 SG** を作成（「セキュリティグループを作成」→下記を入力）  
     - セキュリティグループ名: `sg-efs-palworld`、説明: `Palworld EFS`、VPC: `pal-spot-vpc`  
     - インバウンドルール: 行1=タイプ「NFS」、ポート範囲=`2049`、送信元=`sg-ec2-palworld-spot`（プルダウンで SG を選択）  
     - アウトバウンドルール: デフォルトの「すべてのトラフィック」許可を残す  
     - タグ: `Name=sg-efs-palworld`, `Project=palworld`, `Env=prod` → 作成  
  7. 作成後、**どの画面でどの SG を選ぶか（操作手順＋入力欄の場所と判断材料）**  
     - Interface VPC エンドポイント  
       - 開き方: 左メニュー「エンドポイント」→「エンドポイントを作成」→ **タイプ=AWS のサービス** を選択（添付スクリーンショットの画面）→ サービス名で `ssm` などを検索・選択。  
       - 入力欄の場所と値:  
         - 「VPC」プルダウン: `pal-spot-vpc`  
         - 「サブネット」チェックボックス: プライベート 2 つ（`in-apne1a`/`in-apne1c`）  
         - 「セキュリティグループ」プルダウン: **`sg-ec2-palworld-spot`** を選択（https 443 を許可しているため Interface エンドポイントの 443 通信に使える）。  
       - そのまま「エンドポイントを作成」を押す。  
     - EFS マウントターゲット  
       - 開き方: 左メニュー「EFS」→ 対象ファイルシステムをクリック → 右上「ネットワーク」タブ。既存なら「マウントターゲットを編集」、新規なら作成ウィザードの「ネットワーク」セクション。  
       - 入力欄の場所と値:  
         - 「VPC」: `pal-spot-vpc`  
         - 「サブネット」: プライベート 2 つ（`in-apne1a`/`in-apne1c`）  
         - 「セキュリティグループ」チェックボックス: **`sg-efs-palworld`** を選択（2049/tcp を受ける SG）。  
       - 保存/作成を押す。  
     - EC2 起動/Launch Template  
       - 開き方: 左メニュー「起動テンプレート」→「起動テンプレートの作成」。インスタンスを直接起動する場合も同じ UI 配置。  
       - 入力欄の場所と値（画面「ネットワーキング」セクション）:  
         - 「VPC」: `pal-spot-vpc`  
         - 「サブネット」: `in-apne1a`（ASG で複数 AZ を使う場合は LT 側は「サブネットなし」を選び ASG で指定しても可）  
         - 「セキュリティグループ」チェックボックス: **`sg-ec2-palworld-spot`** を選択（8211/udp,22/tcp を開けるため）。  
       - テンプレートを作成（または起動時に同じ欄で SG を選択）。
- **キーペア**: 初期セットアップ/障害対応用に作成（例: `palworld-admin-key`）。必要なら Session Manager 接続ができるので鍵レス運用も可。
  - 手順: 左メニュー「キーペア」→「キーペアを作成」→ 名前入力・ファイル形式 `pem` → 作成してダウンロードを安全に保管。
- **IAM ロール**: EC2 インスタンスプロファイルに `AmazonSSMManagedInstanceCore`、`AmazonElasticFileSystemClientFullAccess`、必要なら S3 読取/CloudWatch Logs 出力を付与。スポット運用に揃える場合は `PalworldSpotInstanceRole` を共用。
  - 手順: 左メニュー「IAM」→「ロール」→「ロールを作成」→ 信頼されたエンティティ= **AWS のサービス**、ユースケース= **EC2** → ポリシー `AmazonSSMManagedInstanceCore`、`AmazonElasticFileSystemClientFullAccess`（必要に応じ CloudWatch/S3 読取）を選択 → ロール名 `PalworldSpotInstanceRole` → 作成。

## 2. EFS の準備（コンソール手順）
- **EFS ファイルシステム作成**: 左メニュー「EFS」→「ファイルシステムの作成」→ 名前タグ `palworld-efs` → パフォーマンス `General Purpose`、スループット `Bursting` (負荷が高い場合は `Provisioned` も検討) を選択。
- **マウントターゲット設定**: 作成ウィザードの「ネットワーク」セクションで VPC=`pal-spot-vpc` を選択し、サブネットにプライベート2つ（`in-apne1a`, `in-apne1c`）を選択。セキュリティグループは `sg-efs-palworld` を指定。
- **セキュリティグループ**: EC2 からの `2049/tcp` を許可する SG (`sg-efs-palworld`) をアタッチ。
- **バックアップ (任意)**: 作成画面の「自動バックアップ」を ON にして日次バックアップを有効化可能。
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

## 3. ベースEC2の起動（初回セットアップ）
1. 左メニュー「インスタンス」→「インスタンスを起動」。
2. AMI: Ubuntu（後でこのインスタンスを AMI 化する）。
3. インスタンスタイプ: 検証 `t3.large`、本番準備で `c6i.large` など。
4. キーペア: `palworld-admin-key` を選択。
5. ネットワーク設定: VPC=`pal-spot-vpc`、サブネット=`in-apne1a`、自動割り当てパブリック IP=無効。
6. セキュリティグループ: 既存の `sg-ec2-palworld-spot` を選択。
7. IAM ロール: `PalworldSpotInstanceRole` を選択。
8. ストレージ: ルート 30〜50GB gp3 を指定。
9. 起動後、EFS マウント・パルワールド導入を実施し、動作確認が終わったら AMI 化する。

## 4. パルワールドサーバー導入 (EFS 利用)
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

## 5. 自動起動設定例 (systemd)
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

## 6. 動作確認と AMI 化
1. EFS マウント状態でサーバーが起動し、クライアントから接続できることを確認。
2. `Saved` データが EFS に蓄積されることを確認。
3. 不要ファイルを削除し、`/var/log/cloud-init.log` など機微を必要に応じて整理。
4. この EC2 を停止し、AMI を作成 (以降の Launch Template で参照)。

---
このベース AMI がスポット環境の起動元になります。EFS にデータを置くことで、スポット停止・再取得時もワールド/セーブ/ゲームファイルを継続利用できます。
