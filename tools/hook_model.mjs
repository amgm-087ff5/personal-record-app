// Claude Code フック。画面ファイルを編集しようとした時に、データモデルの存在を毎回思い出させる。
//   PreToolUse(Edit|Write|MultiEdit): 画面 → 「モデルを先に確認」/ model.js・m-*.js → 「MODEL.md・API.md・model_test.mjs も直す」
//   PostToolUse(同上): モデル側を直した後に model_sync を走らせ、ずれを返す
import { execFileSync } from 'node:child_process';
let raw = ''; process.stdin.on('data', d => raw += d).on('end', () => {
  let j = {}; try { j = JSON.parse(raw); } catch {}
  const f = String((j.tool_input && (j.tool_input.file_path || j.tool_input.path)) || '').replace(/\\/g, '/');
  const ui = /\/v7\/(js\/(s-[\w-]+|parts|core|kbd|voice)\.js|index\.html)$/.test(f), model = /\/v7\/js\/(model|m-[\w-]+)\.js$/.test(f);
  if (!ui && !model) process.exit(0);
  const post = j.hook_event_name === 'PostToolUse'; let msg;
  if (post) {
    if (!model) process.exit(0);
    let out = ''; try { out = execFileSync('node', [f.replace(/js\/[^/]+$/, 'tools/model_sync.mjs')], { encoding: 'utf8' }); } catch (e) { out = (e.stdout || '') + (e.stderr || ''); }
    if (/^NG/m.test(out)) msg = 'model.js を変えました。モデルの文書・検査にずれがあります:\n' + out + '\nv7/MODEL.md・v7/API.md・v7/tools/model_test.mjs を直してください(直せない理由があるなら commit に Data-Model: docs-na — 理由)。';
    else process.exit(0);
  } else if (ui) msg = '【データモデル確認】画面を変えようとしています。先に v7/MODEL.md(5つの約束と「0.5 UIを変える時」)と v7/API.md を読み、①既存の型(ents/facts/routines/acts/tasks…)で足りるか ②画面は model.js の関数だけで読み書きしているか(DB を直接書かない)を確かめてください。新しいデータや意味が要るなら、画面より先に model.js → MODEL.md → API.md → tools/model_test.mjs を直します。コミットには「Data-Model: changed|unchanged — 理由|docs-na — 理由」を書きます(tools/MODEL-GUARD.md)。';
  else msg = '【データモデル確認】model.js を変えようとしています。同じ作業の中で v7/MODEL.md・v7/API.md・v7/tools/model_test.mjs も直します(tools/MODEL-GUARD.md)。';
  console.log(JSON.stringify({ hookSpecificOutput: { hookEventName: post ? 'PostToolUse' : 'PreToolUse', additionalContext: msg } }));
});
