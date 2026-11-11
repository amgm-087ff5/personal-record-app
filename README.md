# 個人記録 (Personal Log)

写真付きの日記、レシートの読取、家計簿、割り勘をまとめた個人用の Android アプリです。データは端末内(Room / 内部ストレージ / DataStore)にのみ保存します。

- 技術: Kotlin / Jetpack Compose / Material3 / Navigation Compose / Room / Hilt / DataStore / kotlinx-serialization
- applicationId: `com.amgm.personallog`(debug / release 共通)
- minSdk 28 / targetSdk 35
- DB version: 17(`data/db/AppDatabase.kt`)
- 下部タブ: 日記 / 家計簿 / 設定。レシートは家計簿から開きます。

## 外部への送信と公開範囲

- **レシートの読取は、画像を Anthropic API(`api.anthropic.com`)へ送信します**(`receipt/ClaudeApi.kt`)。それ以外のデータは端末内にのみ保存します。
- **このリポジトリは公開です。** Release の APK も誰でも取得できます。個人データ・API キー・署名鍵はコミットしないでください。

## 最新版の入手

スマホのブラウザから、最新の release ビルドを取得できます:

https://github.com/amgm-087ff5/personal-record-app/releases/download/personal-log-debug-latest/personal-log-release.apk

`main` への push ごとに、CI が APK を prerelease `personal-log-debug-latest` に差し替えます。

## ビルドとテスト

- ローカル: `./gradlew testDebugUnitTest assembleDebug`(JDK 17 + Android SDK 35)
- CI: `.github/workflows/personal-log.yml`。`main` と `feat/**` への push で、テストと release ビルドと署名検証を行います。配布の公開は `main` のみです。
- 署名鍵は Secrets から復元します。Settings → Secrets and variables → Actions に次の2つを登録してください。未登録だと CI は「署名鍵が未設定」で失敗します。
  - `DEBUG_KEYSTORE_BASE64`(keystore を `base64 -w0` した1行)
  - `DEBUG_KEYSTORE_PASSWORD`
- キーの alias は `personallog` で固定しています(Secret ではありません)。キーパスワードはストアパスワードと同一です(PKCS12 のため)。
- ローカルで `app/debug.keystore` が無い場合は、AGP 標準のデバッグ鍵でビルドされます。
- CI は Room のスキーマ(`app/schemas`)を生成し、`main` に自動コミットします。

## DB とマイグレーション

- version を上げるときは、`data/db/Migrations.kt` に Migration を必ず追加します。
- `fallbackToDestructiveMigration` は使いません。
- 生成されたスキーマ JSON は `app/schemas` にコミットします。
- 移行の連続性は `MigrationV*` のテストで確認します。

## 更新で困ったとき

- 署名が同じなら、APK をそのまま開けば上書きインストールできます(データは残ります)。PC からは `adb install -r personal-log-release.apk`。
- 署名の違う旧版が入っている場合だけ、一度アンインストールが必要です(その端末の記録は消えます)。
- 起動時に DB エラー画面が出たら、表示された原因を控えてください。
- `android:allowBackup="false"` にしています。自動バックアップの復元で DB だけ古く戻る事故を避けるためです。

## UI モック(HTML)と開発ブランチ

UI のモックは GitHub Pages(`gh-pages` ブランチ)で公開しています。

| 版 | URL | 備考 |
|---|---|---|
| v6「そら」 | https://amgm-087ff5.github.io/personal-record-app/v6/ | 最新。ブランチ `feat/ui-v6`。ユーザー確認待ち |
| v5 | https://amgm-087ff5.github.io/personal-record-app/v5/ | |
| v4 | https://amgm-087ff5.github.io/personal-record-app/v4/ | ブランチ `feat/ui-v4` |
| v3 / v2 | https://amgm-087ff5.github.io/personal-record-app/v3/ ・ https://amgm-087ff5.github.io/personal-record-app/ | |

- `feat/app-v4`: 別アプリ `:appv4`(`com.amgm.personallog.v4`)の実装です(PR #21)。`main` には未マージです。

## 設計と進捗

- 設計・進捗・決定事項は Notion の「個人記録セルフホスト計画」に記録しています。
- 作業時のルールは `CLAUDE.md` にあります。
