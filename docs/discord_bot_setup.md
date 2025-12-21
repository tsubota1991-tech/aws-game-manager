# Discord Bot 設定マニュアル

本マニュアルは、当システムで利用する Discord Bot の作成手順、権限設定、システム内の設定箇所をまとめたものです。Bot 作成からトークン登録までを順番に実施してください。

## 1. Bot の作成手順（Discord Developer Portal）
1. https://discord.com/developers/applications にアクセスし、「New Application」を作成する。
2. 作成したアプリケーションの「Bot」タブを開き、「Add Bot」を押下して Bot ユーザーを作成する。
3. 「Reset Token」で表示される Bot Token を控える（システム設定で使用）。トークンは第三者に知られないよう管理すること。
4. 「Privileged Gateway Intents」で以下を有効化する。
   - **MESSAGE CONTENT INTENT**（メッセージ本文を扱うため必須）
   - 必要に応じて **SERVER MEMBERS INTENT** などを有効化（メンバー関連の機能を使う場合）。

## 2. 権限と招待リンク設定
1. 「OAuth2 > URL Generator」で以下を指定して招待 URL を作成する。
   - Scopes: `bot`（必要に応じて `applications.commands`）
   - Bot Permissions（最低限）:
     - Send Messages
     - Embed Links
     - Read Message History
     - Manage Messages（Bot が自動削除等を行う場合）
2. 生成した URL で対象サーバー（ギルド）に Bot を招待する。
3. 招待後、Bot が参加しているチャンネルで必要な権限が付与されていることを確認する。

## 3. 当システムでの設定箇所
1. 管理画面「システム設定」ページ（`/admin/system-settings`）にアクセスする。
2. 「Discord Bot Token」入力欄に、手順 1 で取得した Bot Token を貼り付けて保存する。保存時に Bot が再起動され、トークンが正しい場合は接続が確立される。
3. Bot Token はデータベースに保存され、再起動時はシステム設定から読み込まれる。環境変数 `DISCORD_BOT_TOKEN` を利用する運用も可能だが、通常は管理画面での登録を推奨する。
4. Bot が動作しない場合は、ログに「Discord Bot Token が不正です」等のエラーが出ていないか確認し、トークンや権限設定を見直す。

## 4. 運用時のポイント
- トークンは流出防止のため、権限のないユーザーに共有しない。
- Bot を別環境へ移行する際は、新しい環境でも同じトークンを設定するか、必要に応じて再発行する。
- メッセージ本文を扱う機能を利用する場合、必ず MESSAGE CONTENT INTENT を有効化してから招待し直す。

以上で Discord Bot の設定は完了です。Bot が接続済みであれば、当システムからの通知やコマンド連携を利用できます。
