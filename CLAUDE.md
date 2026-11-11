# 個人記録アプリ（personal-record-app）

## v6 モックの公開

- 公開版は `gh-pages` ブランチの `v6/` で、URLは https://amgm-087ff5.github.io/personal-record-app/v6/ 。
- ソース（`feat/ui-v6` 系、このブランチの `v6/`）と公開版は別系統。ソースを直したら、公開版にも同じ変更を入れる必要がある。
- v6 の変更を公開するときは、手作業をせずに次のスクリプトを使う。
  `v6/tools/publish.sh <ソースのref> <変更したファイル>...`
  例: `v6/tools/publish.sh HEAD v6/js/parts.js v6/css/parts.css`
- スクリプトは版（`window.BUILD`）を1つ上げ、gh-pages に指定ファイルだけを反映し、公開URLで版と中身を確かめる。確認できなければ失敗で止まるので、そのときは原因を報告する。
- 公開せずに中身だけ確かめたいときは `DRY=1` を付ける。
- 本番への反映は権限で止められることがある。止まったら、理由と残りの作業を伝える。
