# データモデル追従ガード

**目的**: UIを直すたびにデータモデル(`v7/MODEL.md` `v7/js/model.js` `v7/API.md` `v7/tools/model_test.mjs`)が置き去りになるのを、気合ではなく仕組みで防ぐ。

## 3つの層
| 層 | 何をする | どこ |
|---|---|---|
| 気づかせる | 画面を編集する直前に「モデルを先に確認」を差し込む。model.js 編集後はずれを返す | `CLAUDE.md` / `.claude/settings.json` + `tools/hook_model.mjs` |
| ずれを見つける | model.js の公開名が文書・検査にあるか、画面が DB を直接書いていないか、表が MODEL.md にあるか | `v7/tools/model_sync.mjs`(既存のずれは `model_sync.baseline.json`。新しいずれだけ失敗) |
| 証拠を求める | 画面を変えたコミットに `Data-Model:` 行(changed / unchanged — 理由 / docs-na — 理由)を要求 | `v7/tools/model_coupling.mjs` ← `.githooks/commit-msg`、CI |

## 使い方
- 初回: `sh tools/setup_hooks.sh`(git フック有効化)。
- 全検査: `sh v7/tools/check_all.sh`(model_sync を含む)。
- 既存のずれを直したら: `node v7/tools/model_sync.mjs --update-baseline`(baseline は減らす方向にだけ使う)。
- CI: `.github/workflows/model-sync.yml`(push / PR)。

## 限界
意味の妥当性(新しい表が本当に要るか)は機械では判定できない。フックは「見直した」記録を強制し、判断は MODEL.md の「0.5 UIを変える時」に従う。
