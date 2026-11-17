// UIを変えたコミットが「データモデルを見た」証拠(Data-Model: 行)を持つかを検査する。
//   node v7/tools/model_coupling.mjs --msg <コミットメッセージのファイル>   … 今ステージしている変更が対象(commit-msg フック用)
//   node v7/tools/model_coupling.mjs --range <基点>..<先端>                 … 範囲内の各コミットが対象(CI 用)
// 規則:
//   R1 画面(v7/js/s-*.js parts.js core.js kbd.js voice.js, v7/index.html)を変えた → モデル一式(下記)のどれかも変えるか、`Data-Model: unchanged — 理由`
//   R2 model.js か m-*.js を変えた → MODEL.md か API.md と model_test.mjs も変えるか、`Data-Model: docs-na — 理由`
//   `Data-Model: changed` はモデル一式が実際に変わっている時だけ書ける
//   文書だけ・検査だけのコミットや、画面もモデルも触らないコミットは対象外
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
const git = (...a) => execFileSync('git', a, { encoding: 'utf8' });
const isUI = f => /^v7\/js\/(s-[\w-]+|parts|core|kbd|voice)\.js$/.test(f) || f === 'v7/index.html';
const isModelCode = f => /^v7\/js\/(model|m-[\w-]+)\.js$/.test(f);
const isDoc = f => f === 'v7/MODEL.md' || f === 'v7/API.md';
const isTest = f => f === 'v7/tools/model_test.mjs';
const trailer = msg => { const m = msg.split('\n').map(l => l.match(/^Data-Model:\s*(changed|unchanged|docs-na)\b\s*(?:[—–-]+\s*(.*))?$/i)).filter(Boolean).pop(); return m ? { kind: m[1].toLowerCase(), why: (m[2] || '').trim() } : null; };
function judge(files, msg) {
  const ui = files.filter(isUI), code = files.filter(isModelCode), doc = files.some(isDoc), test = files.some(isTest);
  const t = trailer(msg), modelTouched = code.length > 0 || doc || test;
  const errs = [];
  if (t && t.kind !== 'changed' && !t.why) errs.push(`Data-Model: ${t.kind} には「— 理由」が要ります`);
  if (t && t.kind === 'changed' && !modelTouched) errs.push('Data-Model: changed と書いたが、モデル一式(model.js / MODEL.md / API.md / model_test.mjs)が変わっていない');
  if (ui.length && !modelTouched && !(t && t.kind === 'unchanged' && t.why)) errs.push(`画面(${ui.join(', ')})を変えたのにモデル一式が変わっていない。モデルを見直し、要るなら model.js→MODEL.md→API.md→model_test.mjs を直す。要らないなら「Data-Model: unchanged — 理由」`);
  if (code.length && !(doc && test) && !(t && t.kind === 'docs-na' && t.why)) errs.push(`${code.join(', ')} を変えたのに ${[!doc && 'MODEL.md か API.md', !test && 'tools/model_test.mjs'].filter(Boolean).join(' と ')} が変わっていない。直すか「Data-Model: docs-na — 理由」`);
  return errs;
}
const a = process.argv.slice(2); let bad = 0;
if (a[0] === '--msg') {
  const errs = judge(git('diff', '--cached', '--name-only').split('\n').filter(Boolean), fs.readFileSync(a[1], 'utf8'));
  if (errs.length) { bad = 1; console.error('NG  データモデル追従(tools/MODEL-GUARD.md)'); errs.forEach(e => console.error('  - ' + e)); }
} else if (a[0] === '--range') {
  for (const h of git('rev-list', '--no-merges', a[1]).split('\n').filter(Boolean)) {
    const errs = judge(git('diff-tree', '--no-commit-id', '--name-only', '-r', h).split('\n').filter(Boolean), git('log', '-1', '--format=%B', h));
    if (errs.length) { bad = 1; console.error(`NG  ${h.slice(0, 7)} ${git('log', '-1', '--format=%s', h).trim()}`); errs.forEach(e => console.error('  - ' + e)); }
  }
} else { console.error('使い方: --msg <file> | --range A..B'); process.exit(2); }
if (!bad) console.log('OK  データモデル追従');
process.exit(bad);
