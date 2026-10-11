# 個人記録 (Personal Log)

写真 → 日記 → レシート読取 → 家計簿 をひとつにまとめた個人用アプリ。データは端末内(Room / 内部ストレージ / DataStore)にのみ保存します。Android 先行(minSdk 28)。

- 技術: Kotlin / Jetpack Compose / Material3 / Navigation Compose / Room / Hilt / DataStore / kotlinx-serialization
- applicationId: `com.amgm.personallog`(debug/release 共通)
- タブ: 日記 / レシート / 家計簿 / 設定(Wave1 時点で実装済みは設定画面のみ。他は空の画面)

## ビルドとインストール

ローカルにAndroid SDKがなくても、GitHub Actions(`personal-log` ワークフロー)でビルドされます。`main` ブランチへ push すると、テストと `assembleDebug` を実行し、最新のAPKをプレリリースに公開します。

スマホのブラウザから常に最新版を取得できます:

https://github.com/amgm-masamune/personal-record-app/releases/download/personal-log-debug-latest/personal-log-debug.apk

**このリポジトリは公開です。公開リポジトリのReleaseに置かれるAPKも誰でも取得できます。** 個人の記録データはAPKに含まれず端末内にのみ保存されますが、公開されて困るものをコミットしないでください。

### 署名鍵の登録(初回のみ・必須)

署名鍵(keystore)はリポジトリに置かず、GitHub Actions の Secrets から供給します。Settings → Secrets and variables → Actions に次の2つを登録してください。未登録だとCIは「署名鍵が未設定」で失敗します。

- `DEBUG_KEYSTORE_BASE64`(keystoreを `base64 -w0` した1行)
- `DEBUG_KEYSTORE_PASSWORD`

キーのaliasは app/build.gradle.kts に `personallog` で固定(Secretではない)で、キーパスワードはストアパスワード(`DEBUG_KEYSTORE_PASSWORD`)と同一です(PKCS12のため)。

CIはビルド前にこれらから `app/debug.keystore` を復元します(このファイルは `.gitignore` 済みでコミットされません)。ローカルビルドで `app/debug.keystore` が無ければAGP標準のデバッグ鍵になります。

手元でビルドする場合: `./gradlew testDebugUnitTest assembleDebug`(JDK 17 + Android SDK 35)。

## 更新エラー対策(上書き更新できない問題)

| 対策 | 内容 |
|---|---|
| a. 署名の固定 | 鍵はリポジトリに置かず GitHub Secrets から CI で `app/debug.keystore` を復元し、`app/build.gradle.kts` で debug 署名をこの鍵に固定。CIの使い捨てランナーでも毎回同じ署名になる(`INSTALL_FAILED_UPDATE_INCOMPATIBLE` の原因対策)。Secretsが未設定ならCIは失敗する。 |
| b. versionCode | `(System.currentTimeMillis() / 60000L).toInt()`(エポックからの分)で単調増加。ダウングレードにならない。versionName は `0.1.<GITHUB_RUN_NUMBER or local>`。 |
| c. Room | `exportSchema = true`、スキーマは `app/schemas` にコミット、DB version=3(v1→v2: レシートの画像ハッシュ追加、v2→v3: 品目の税率・税込/税抜・負担割合、レシートの立て替えフラグ)。`fallbackToDestructiveMigration` は使わない。version を上げるときは `data/db/Migrations.kt` に Migration を必ず追加する。 |
| d. バックアップ無効 | `android:allowBackup="false"`。自動バックアップの復元でDBだけ古く戻る事故を避ける。 |
| e. 失敗時の診断 | 起動時にDBを開き、例外が出ても落ちずに原因を画面へ表示する。 |

CIは `apksigner verify --print-certs` で署名証明書の SHA-256 をログとReleaseの本文に出します(Releaseページの本文で確認できます)。設定画面でも端末にインストール済みアプリの署名SHA-256を確認できます。

## 更新エラーが出たとき

- 署名が同じなら、APKをそのまま開けば上書きインストールできます(データは残ります)。PCからは `adb install -r personal-log-debug.apk`。端末の「アプリを更新」でも可。
- 署名の違う旧版(固定前のCIビルドや、鍵ローテーション前の版など)が入っている場合のみ、一度アンインストールが必要です(その端末の記録は消えます)。同じ鍵のビルド同士では不要です。
- **鍵のローテーション**: 以前の署名鍵はリポジトリ履歴(旧リポジトリ由来のコミット)に残っていますが、破棄済みで今後は使いません。現在の鍵はSecretsのみにあり、署名が旧版とは変わるため、旧版が入っている端末では一度アンインストールしてください。
- 起動時にDBエラー画面が出たら、表示された原因を控えてください。
