// node v7/tools/model_sync.mjs [--update-baseline]
// データモデルと「文書・検査・画面」のずれを機械で検出する(UIを変えた時にモデルが置き去りになるのを防ぐ)。
//   A. model.js の公開名(_始まり以外)が API.md か MODEL.md に載っているか
//   B. 同じ名前が tools/model_test.mjs で使われているか(検査されているか)
//   C. 画面(js/*.js。model.js と m-*.js はモデル側なので除く)が DB を直接書き換えていないか(書く時は model.js の関数だけ)
//   D. DB の表(model.js の DB の最上位キー)が MODEL.md に載っているか
// 既存のずれは model_sync.baseline.json に退避済み。「新しく増えたずれ」だけを失敗にする。直したら --update-baseline で減らす。
import fs from 'node:fs'; import path from 'node:path'; import vm from 'node:vm'; import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url)), root = path.join(here, '..');
const rd = f => fs.readFileSync(path.join(root, f), 'utf8');
const src = rd('js/model.js'), docs = rd('API.md') + '\n' + rd('MODEL.md'), tests = rd('tools/model_test.mjs');
const has = (text, name) => new RegExp('(?<![\\w$])' + name.replace(/\$/g, '\\$') + '(?![\\w$])').test(text);
const names = [...new Set([...src.matchAll(/^(?:const|let|function|class)\s+([A-Za-z][\w$]*)/gm)].map(m => m[1]))];
const res = { A_docs: names.filter(n => !has(docs, n)).sort(), B_tests: names.filter(n => !has(tests, n)).sort(), C_uiWritesDB: [], D_tables: [] };
// C: 画面ファイルの DB 直接書き込み
const uiFiles = fs.readdirSync(path.join(root, 'js')).filter(f => f.endsWith('.js') && f !== 'model.js' && !f.startsWith('m-'))   // m-*.js はモデル側(受信箱・LLM);
const wr = /\bDB(?:\.[A-Za-z_$][\w$]*|\[[^\]]+\])+\s*(?:=(?!=)|\+=|-=|\+\+|--)|\bDB(?:\.[A-Za-z_$][\w$]*|\[[^\]]+\])*\.(?:push|splice|pop|shift|unshift|sort|reverse)\(|\bdelete\s+DB\b/;
for (const f of uiFiles) rd('js/' + f).split('\n').forEach((l, i) => { if (wr.test(l.replace(/\/\/.*$/, ''))) res.C_uiWritesDB.push(`${f}:${l.trim().slice(0, 80)}`); });
// D: 表
try {
  const store = {}; const ctx = vm.createContext({ localStorage: { getItem: k => store[k] ?? null, setItem: (k, v) => { store[k] = v; } }, location: { search: '' }, console, URLSearchParams, Date });
  vm.runInContext(src, ctx); const keys = vm.runInContext('(load(), Object.keys(DB))', ctx);
  res.D_tables = keys.filter(k => !has(rd('MODEL.md'), k)).sort();
} catch (e) { res.D_tables = ['(model.js を読めない: ' + e.message + ')']; }
const bf = path.join(here, 'model_sync.baseline.json');
if (process.argv.includes('--update-baseline')) { fs.writeFileSync(bf, JSON.stringify(res, null, 1) + '\n'); console.log('baseline を更新:', Object.fromEntries(Object.entries(res).map(([k, v]) => [k, v.length]))); process.exit(0); }
const base = fs.existsSync(bf) ? JSON.parse(fs.readFileSync(bf, 'utf8')) : {};
const why = { A_docs: 'API.md か MODEL.md に書く', B_tests: 'tools/model_test.mjs で検査する', C_uiWritesDB: '画面から DB を直接書かず model.js の関数を使う(無ければ model.js に足す)', D_tables: 'MODEL.md に表の説明を書く' };
let ng = 0;
for (const k of Object.keys(res)) { const fresh = res[k].filter(x => !(base[k] || []).includes(x)); if (fresh.length) { ng = 1; console.log(`NG  ${k}: ${why[k]}`); fresh.forEach(x => console.log('    - ' + x)); } }
const left = Object.values(base).flat().filter(x => !Object.values(res).flat().includes(x)).length;
console.log(ng ? 'NG  model_sync(モデルが文書・検査・画面に追従していない)' : `OK  model_sync(既存のずれ ${Object.values(base).flat().length - left} 件は baseline。直したら --update-baseline)`);
process.exit(ng);
