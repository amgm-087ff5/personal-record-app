# このリポジトリで作業する人・AIへ(必読)

## UI(v7/js の画面・index.html)を変える前に
1. `v7/MODEL.md`(データモデル。5つの約束と「0.5 UIを変える時」)と `v7/API.md`(画面が呼べる関数)を読む。
2. 既存の型(ents・facts・routines・acts・tasks・memos・photos)で足りるか先に調べる。新しい表は最後の手段。
3. 画面は `model.js` の関数だけでデータを読み書きする。`DB` を直接書き換えない。
4. 新しいデータ・意味が要るなら、**画面より先に** `model.js` → `MODEL.md` → `API.md` → `v7/tools/model_test.mjs` を直す。
5. コミットメッセージに `Data-Model:` の行を書く。
   - `Data-Model: changed` … モデル一式を直した
   - `Data-Model: unchanged — 理由` … 見直したが不要(画面だけの変更)
   - `Data-Model: docs-na — 理由` … model.js を変えたが文書・検査は不要(色の定数など)
6. `sh v7/tools/check_all.sh`(中に model_sync を含む)が通ること。

## 仕組み(詳細は `tools/MODEL-GUARD.md`)
- Claude Code: `.claude/settings.json` のフックが、画面の編集前にこの確認を毎回差し込む。
- git: `sh tools/setup_hooks.sh` で `.githooks`(commit-msg / pre-commit)を有効にする。
- CI: `.github/workflows/model-sync.yml` が push / PR で同じ検査を行う。
