# 個人記録(personal-record-app)の作業ルール

## 作業を始める前に

1. **Notion の「個人記録セルフホスト計画」を必ず読む。** 現状・決定事項・指示の正本です。
   https://app.notion.com/p/3ec0e054862281b09787cf9d01df81ee
2. **モック・UI・画面の依頼では、新規作成の前に既存を確認する。** 公開モックは GitHub Pages(`gh-pages` ブランチ)にあり、最新は v6 です(`/v6/`)。作業ブランチは `feat/ui-v6`、`feat/ui-v4` などです。
   - 版の一覧と URL は `README.md` の「UI モック(HTML)と開発ブランチ」を参照。
   - 既存の版を無視して新しく作らないこと。

## 作業を終える前に

1. **README.md が古くなっていないか確認する。** 次の項目が今の実装と一致しているかを見ます。
   - DB version(`data/db/AppDatabase.kt`)
   - 下部タブ(`ui/AppRoot.kt`)
   - CI の挙動(`.github/workflows/personal-log.yml`)
   - APK の名前と配布 URL
   - モックの最新版と、開発ブランチ
   古ければ、同じ作業の中で直します。
2. **README には要点だけを書く。** 詳細は Notion か `gh-pages` の `DESIGN.md` に置き、README へ不用意に増やさない。
3. 進捗と決定事項は Notion に記録する。

## 公開リポジトリの注意

- このリポジトリは公開です。個人データ・API キー・署名鍵・keystore をコミットしないこと。
- レシート読取は画像を Anthropic API へ送る。この仕様を変えるときは README も直す。

## Git

- 作業は指定されたブランチで行い、`main` へ直接 push しない。
- Pull Request は、ユーザーが依頼したときだけ作る。
