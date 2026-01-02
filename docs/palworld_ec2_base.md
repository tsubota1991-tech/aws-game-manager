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
    - **補足 (SG との紐付け)**: Gateway 型はセキュリティグループを指定する欄がないため、ここでは SG を触りません。  
  - Interface 型（SSM/EC2Messages/SSMMessages/CloudWatchLogs/ECR api,dkr など、必要に応じて追加）  
    0. 画面最上部「名前タグ - オプション」に `Name=vpce-ssm-palworld` のようなタグを入れておくと一覧で識別しやすい（任意）。  
    1. 同画面でサービス名を検索（例: `ssm`、`ec2messages`、`ssmmessages`、`logs`、`ecr.api`、`ecr.dkr`）を順に作成  
    2. タイプ「Interface」を選択  
    3. VPC: `pal-spot-vpc`  
    4. サブネット: プライベートサブネット 2 つ（`in-apne1a` と `in-apne1c`）をチェック  
    5. セキュリティグループ: `sg-ec2-palworld-spot` を指定（インバウンド 8211/udp・22/tcp を許可）—ここが後述の「どの画面でどの SG を選ぶか」で示す **既存 SG を選択する欄**。  
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
  7. ここまで（1〜6）で SG を **作成済み**。次は「どの画面でどの SG を選ぶか」を、作業ごとに選択欄の位置と理由付きで実施するだけ。上から順にクリックしていけばよい。  
     1) Interface VPC エンドポイントを作成するフロー（例: SSM 用 `com.amazonaws.ap-northeast-1.ssm` を作ると `vpce-xxxxxxxxssm` という名前で `pal-spot-vpc` に紐づく）  
        - 左メニュー「エンドポイント」→「エンドポイントを作成」→ **タイプ=AWS のサービス** → サービス名で `ssm` などを検索・選択。  
        - 入力欄の場所: 画面中央「ネットワーク設定」ブロックにある「VPC」「サブネット」「セキュリティグループ」。ここで **「セキュリティグループ」=`sg-ec2-palworld-spot`** をプルダウン選択（443 を開けているため Interface エンドポイントの https 通信に利用）。  
        - そのまま「エンドポイントを作成」を押す（新しい SG を作る操作は不要）。  
     2) EFS マウントターゲットを設定するフロー  
        - 左メニュー「EFS」→ 対象ファイルシステム → 右上「ネットワーク」タブ（既存なら「マウントターゲットを編集」、新規なら作成ウィザードの「ネットワーク」セクション）。  
        - 入力欄の場所: ネットワークタブ内の「セキュリティグループ」チェックボックス。ここで **`sg-efs-palworld`** をチェック（2049/tcp を受ける役割）。サブネットはプライベート 2 つ（`in-apne1a`/`in-apne1c`）を選択。  
        - 保存/作成を押す（既存 SG を選ぶだけで、追加作成は不要）。  
     3) EC2 起動テンプレート/インスタンス起動のフロー  
        - 左メニュー「起動テンプレート」→「起動テンプレートの作成」（インスタンス直起動も同じ位置）。  
        - 入力欄の場所: 「ネットワーキング」セクションの「セキュリティグループ」行。ここで **`sg-ec2-palworld-spot`** をチェック（8211/udp と 22/tcp を開ける役割）。ASG で複数 AZ を使う場合は LT では「サブネットなし」を選び、ASG 側でサブネット指定。  
        - テンプレートを作成（またはインスタンス起動画面で同じ欄に表示される既存 SG を選択するだけ）。
- **ここまで終わったら、実際に EC2 を作成する流れ**（このあとセクション 3 で詳述）  
  - 左メニュー「インスタンス」→「インスタンスを起動」→ 以降は本書の「## 3. ベースEC2の起動（初回セットアップ）」に記載の 1〜9 を順番に入力。  
  - そこで指定する SG は上で作成した `sg-ec2-palworld-spot`。ユーザーデータやコマンド（EFS マウント、パルワールド導入等）は **インスタンス作成後に実行する** ものなので、まずはインスタンスを作成してからコマンドを実行する。
- **キーペア**: 初期セットアップ/障害対応用に作成（例: `palworld-admin-key`）。必要なら Session Manager 接続ができるので鍵レス運用も可。
  - 手順: 左メニュー「キーペア」→「キーペアを作成」→ 名前入力・ファイル形式 `pem` → 作成してダウンロードを安全に保管。
- **IAM ロール**: EC2 インスタンスプロファイルに `AmazonSSMManagedInstanceCore`、`AmazonElasticFileSystemClientFullAccess`、必要なら S3 読取/CloudWatch Logs 出力を付与。スポット運用に揃える場合は `PalworldSpotInstanceRole` を共用。
  - 手順: 左メニュー「IAM」→「ロール」→「ロールを作成」→ 信頼されたエンティティ= **AWS のサービス**、ユースケース= **EC2** → ポリシー `AmazonSSMManagedInstanceCore`、`AmazonElasticFileSystemClientFullAccess`（必要に応じ CloudWatch/S3 読取）を選択 → ロール名 `PalworldSpotInstanceRole` → 作成。

## 2. EFS の準備（コンソール手順）
- **EFS ファイルシステム作成（添付画面ベースのステップ）**  
  1. 左メニュー「EFS」→「ファイルシステムを作成」。  
  2. ステップ1「ファイルシステムの設定」画面で以下を入力（添付スクリーンショットと同じ配置）。  
     - **名前タグ**: `palworld-efs`（画面上部の「名前タグ - オプション」欄）  
     - **ファイルシステムのタイプ**: 「リージョン」を選択（全AZに展開）  
     - **自動バックアップ**: チェックを入れて有効化（推奨）  
     - **ライフサイクル管理**: 必要に応じて「低頻度アクセス(IA)への移行」を「最後のアクセスから30日」などに設定。不要ならデフォルトのまま。  
     - **標準への移行 / アーカイブ**: 使わない場合は「なし」のまま。  
     - **暗号化**: デフォルトの KMS (aws/elasticfilesystem) のまま。  
     - 右下の「次へ」をクリック。  
  3. ステップ2「ネットワークアクセス」:  
     - VPC: `pal-spot-vpc` を選択。  
     - マウントターゲットのサブネット: `in-apne1a`、`in-apne1c`（プライベート2つ）を選択。  
     - セキュリティグループ: `sg-efs-palworld` を選択（2049/tcp を受ける）。  
  4. ステップ3「ファイルシステムポリシー」はデフォルトのまま（必要に応じて制限）。  
  5. ステップ4「確認して作成」で設定を確認し「作成」。  
- **セキュリティグループ**: EC2 からの `2049/tcp` を許可する SG (`sg-efs-palworld`) をマウントターゲットにアタッチ。
- **ディレクトリ構成 (例)**: `/efs/palworld/{world,save,game}`。

### EC2 につながらないときの確認手順（QA チェックリスト）
1. **鍵ファイルの場所と権限**  
   - ローカルの SSH コマンドで `Warning: Identity file ... not accessible` と出たら、`palworld-spot-admin-key.pem` が存在するディレクトリに移動しているか確認（例: PowerShell なら `cd C:\\Users\\xxxx\\Downloads`）。  
   - ファイル権限を 600 相当にする（Windows は読み取り専用に設定、Linux/macOS は `chmod 600 key.pem`）。  
2. **接続先アドレスの確認**  
   - プライベート IP (`10.0.0.27` など) へ直接 SSH する場合、**踏み台**または **SSM Session Manager** 経由が必要。自宅回線から直接は到達しないので、踏み台 EC2（パブリックサブネット+22番開放）経由か、Session Manager ポートフォワードを利用する。  
   - パブリック IP を付けていない場合は、パブリックサブネット/NAT 環境でも外部から直接はつながらない。  
3. **セキュリティグループの確認**  
   - `sg-ec2-palworld-spot` に 22/tcp で自分のグローバル IP が許可されているか。0.0.0.0/0 を避け、固定/自宅IPを指定。  
   - もし踏み台経由なら、踏み台 SG → ゲーム EC2 SG への 22/tcp を許可。  
4. **OS 側の稼働確認**  
   - AWS コンソールで該当インスタンスのステータスチェックが2/2 か確認。  
   - SSM Agent が有効なら、コンソール「セッションマネージャー」から直接シェル接続してネットワーク疎通を確認。  
5. **ルートとサブネットの確認**  
   - 接続対象インスタンスがプライベートサブネットの場合、外部から直接 SSH はできない設計。VPC 内から踏み台/SSM を使う。  
   - パブリックサブネットで直接接続する場合は「自動割り当てパブリック IPv4 アドレス」が有効か確認。  
6. **想定コマンド例（踏み台を使う場合）**  
   - ローカル → 踏み台: `ssh -i bastion-key.pem ec2-user@<踏み台のPublicIP>`  
   - 踏み台 → ゲームEC2: `ssh -i palworld-spot-admin-key.pem ubuntu@10.0.0.27`（踏み台に鍵を置くか、`ssh -J` の ProxyJump を利用）  

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
