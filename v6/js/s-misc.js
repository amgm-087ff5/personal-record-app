/* 個人記録 v6 — さがす・設定(接頭辞 ms) */
'use strict';

/* ================= さがす =================
   入力欄は描き直さない。入力のたびに #ms-res だけを差し替える(フォーカスと変換中の文字を守る)。 */
const MS = { q: '', kind: 'all', open: {}, seen: null };
const MS_KEY = 'prmock-v6-recent';
const MS_TYPES = [['act', 'アクティビティ'], ['item', '品目'], ['ent', 'エンティティ'], ['memo', 'メモ'], ['jot', '雑記'], ['task', 'ToDo'], ['note', 'ノート'], ['day', '日記']];
const MS_LIMIT = 6;

const msRecent = () => { try { const a = JSON.parse(localStorage.getItem(MS_KEY) || '[]'); return Array.isArray(a) ? a.filter(x => typeof x === 'string' && x).slice(0, 8) : []; } catch (e) { return []; } };
const msRecentSet = a => { try { localStorage.setItem(MS_KEY, JSON.stringify(a.slice(0, 8))); } catch (e) { } };
/** いまの言葉を「最近さがした言葉」の先頭へ */
function msRemember() { const t = MS.q.trim(); if (t) msRecentSet([t, ...msRecent().filter(x => x !== t)]); }
const msWords = s => String(s || '').toLowerCase().split(/\s+/).filter(Boolean);

/** 一致した所を <mark> で目立たせる(文字は escape 済み)。o.cut = 長い文は一致の少し前から見せる */
function msMark(text, ws, o = {}) {
  const s = String(text == null ? '' : text).replace(/\s*\n\s*/g, ' '), low = s.toLowerCase(), rg = [];
  ws.forEach(w => { let i = 0; while ((i = low.indexOf(w, i)) >= 0) { rg.push([i, i + w.length]); i += w.length; } });
  rg.sort((x, y) => x[0] - y[0] || y[1] - x[1]);
  const m = []; rg.forEach(r => { const l = m[m.length - 1]; if (l && r[0] <= l[1]) l[1] = Math.max(l[1], r[1]); else m.push([...r]); });
  let from = 0, pre = '';
  if (o.cut && s.length > o.cut && m.length && m[0][0] > 14) { from = m[0][0] - 10; pre = '…'; }
  let html = '', at = from;
  m.forEach(([a, b]) => { html += esc(s.slice(at, a)) + `<mark>${esc(s.slice(a, b))}</mark>`; at = b; });
  return pre + html + esc(s.slice(at));
}
/** 題に無い言葉が、どこで一致したか(役割の名前・メモ・項目)を短く */
function msWhy(title, cands, ws) {
  const low = String(title || '').toLowerCase(), unc = ws.filter(w => !low.includes(w)); if (!unc.length) return [];
  const out = [];
  for (const c of cands) { const s = String(c || ''); if (s && unc.some(w => s.toLowerCase().includes(w))) out.push(`<span>${msMark(s, unc, { cut: 28 })}</span>`); if (out.length >= 2) break; }
  return out;
}
const msWhen = d => d ? `${esc(relDay(d))}` : '';

/** 結果の1行 */
function msRow(r, ws) {
  const a = r.type === 'act' ? A(r.id) : null;
  const lead = r.type === 'act' ? (a && !isPay(a) ? kindIc(a) : ic('yen')) : r.type === 'item' ? ic('receipt') : r.type === 'ent' ? avatar(r.id)
    : r.type === 'memo' ? ic('memo') : r.type === 'jot' ? ic('pencil') : r.type === 'task' ? ic('task') : r.type === 'note' ? ic('book') : ic('day');
  let subs = [];
  if (r.type === 'act') subs = [...msWhy(r.title, a ? [KINDS[a.kind].n, ...(a.roles || []).map(roleName), a.note] : [], ws), `<span>${esc(r.sub)} · ${msWhen(r.date)}</span>`];
  else if (r.type === 'item') { const [pid, i] = r.id.split('/'), it = A(pid) && A(pid).pay.items[+i]; subs = [...msWhy(r.title, [it && it.memo], ws), `<span>${esc(r.sub)} · ${msWhen(r.date)}</span>`]; }
  else if (r.type === 'ent') { const e = E(r.id); subs = [`<span>${msMark(r.sub, ws)}</span>`, ...msWhy(r.title, e ? e.facts.map(f => `${f.k}: ${f.v}`) : [], ws)]; }
  else if (r.type === 'memo') subs = [`<span>${esc(fmtFull(r.date))} · ${msWhen(r.date)}</span>`];
  else if (r.type === 'jot') subs = [`<span>${esc(r.sub)} · ${msWhen(r.date)}</span>`];
  else if (r.type === 'task') subs = [`<span>${esc(r.sub)}</span>`];
  else if (r.type === 'note') subs = [`<span>更新 ${esc(fmtFull(r.date))} · ${msWhen(r.date)}</span>`];
  else subs = [`<span>${esc(r.sub)} · ${msWhen(r.date)}</span>`];
  /* 雑記は押すと「入力」の履歴の、その雑記へ(href を持たない) */
  return row({ lead, tHtml: `<span class="ms-t">${msMark(r.title, ws, { cut: 48 })}</span>`, sHtml: subs.join(''), right: `${r.plan ? tag('予定', 'plan') : ''}${r.amt ? amt(r.amt) : ''}`, href: r.href, on: r.type === 'jot' ? `window.openInbox && openInbox({focus:${q(r.id)}})` : '' });
}

/** 「前はいくらだった?」: N回・合計(または平均)・最後 */
function msSummary(res) {
  const pays = res.filter(r => r.type === 'act' && r.amt > 0 && !r.plan);
  let parts;
  if (pays.length) {
    const sum = pays.reduce((s, r) => s + r.amt, 0), last = pays.map(r => r.date).filter(d => d <= TODAY).sort().pop();
    parts = [`<b class="num">${pays.length}回</b>`, `<span>合計 ${amt(sum)}</span>`, last ? `<span>最後は ${esc(relDay(last))}</span>` : ''];
  } else {
    const its = res.filter(r => r.type === 'item' && r.amt > 0 && !isPlan(A(r.id.split('/')[0]))); if (!its.length) return '';
    const sum = its.reduce((s, r) => s + r.amt, 0), last = its.map(r => r.date).filter(d => d <= TODAY).sort().pop();
    parts = [`<b class="num">${its.length}回</b>`, `<span>平均 ${amt(sum / its.length)}</span>`, last ? `<span>最後は ${esc(relDay(last))}</span>` : ''];
  }
  return `<div class="ms-sum">${ic('yen')}${parts.filter(Boolean).map(p => p.startsWith('<span') ? p : `<span>${p}</span>`).join('')}</div>`;
}

/** 入力が空の時: 最近さがした言葉・よく交わる・種類 */
function msIdle() {
  const rc = msRecent();
  const freq = Object.keys(DB.ents).map(id => [id, actsOfEnt(id).length]).filter(x => x[1] > 0).sort((x, y) => y[1] - x[1]).slice(0, 6);
  const kinds = Object.entries(KINDS).filter(([k]) => k !== 'holiday').map(([, v]) => [v.n, v.n]);
  return `${rc.length ? `<section>${sec('最近さがした言葉')}${rows(rc.map(w => `<div class="row ms-rc"><button class="ms-rc-b" onclick="msUse(${q(w)})"><span class="row-lead">${ic('clock')}</span><span class="row-t">${esc(w)}</span></button>${iconBtn('close', `${w}を消す`, `msRecentDel(${q(w)})`)}</div>`))}</section>` : ''}
    ${freq.length ? `<section>${sec('よく交わる')}<div class="card pad ms-fq">${freq.map(([id, n]) => `<span class="ms-fq-i">${ent(id)}<span class="mini num">${n}</span></span>`).join('')}</div></section>` : ''}
    <section>${sec('種類でさがす')}${chips(kinds, null, 'msUse').replace('class="chips"', 'class="chips wrap"')}</section>`;
}

/** 結果(または空の状態)の HTML。#ms-res の中身 */
function msRes() {
  const t = MS.q.trim(); if (!t) return msIdle();
  const res = searchAll(t), ws = msWords(t);
  if (!res.length) return `<div class="card">${empty({ icon: 'search', text: '見つかりませんでした' })}</div>`;
  const by = {}; res.forEach(r => (by[r.type] = by[r.type] || []).push(r));
  const kinds = MS_TYPES.filter(([k]) => by[k]);
  const kind = by[MS.kind] ? MS.kind : 'all';
  const secs = kinds.filter(([k]) => kind === 'all' || k === kind).map(([k, name]) => {
    const items = by[k], full = kind !== 'all' || MS.open[k], shown = full ? items : items.slice(0, MS_LIMIT);
    return `<section>${sec(name, { n: items.length })}${rows([...shown.map(r => msRow(r, ws)), items.length > shown.length ? row({ t: 'もっと見る', right: `<span class="mini num">あと ${items.length - shown.length}件</span>`, on: `msMore(${q(k)})`, chev: 1, cls: 'ms-more' }) : ''].join(''))}</section>`;
  }).join('');
  return `${kinds.length > 1 ? chips([['all', 'すべて', res.length], ...kinds.map(([k, n]) => [k, n, by[k].length])], kind, 'msKind') : ''}
    ${kind === 'all' || kind === 'act' || kind === 'item' ? msSummary(res) : ''}${secs}`;
}

function msRefresh() { const el = $('#ms-res'); if (el) el.innerHTML = msRes(); const x = $('#ms-x'); if (x) x.hidden = !MS.q; }
function msInput(v) { MS.q = v; MS.kind = 'all'; MS.open = {}; msRefresh(); }
function msUse(w) { MS.q = w; MS.kind = 'all'; MS.open = {}; const i = $('#ms-q'); if (i) i.value = w; msRefresh(); }
function msClear() { MS.q = ''; MS.kind = 'all'; MS.open = {}; const i = $('#ms-q'); if (i) { i.value = ''; i.focus(); } msRefresh(); }
function msKind(k) { MS.kind = k; msRefresh(); }
function msMore(k) { MS.open[k] = true; msRefresh(); }
function msRecentDel(w) { msRecentSet(msRecent().filter(x => x !== w)); msRefresh(); }
/* 結果を選んで開いた時に、その言葉を覚える */
document.addEventListener('click', e => { const a = e.target.closest && e.target.closest('#ms-res a.row'); if (a) msRemember(); }, true);

route('search', (id, qs) => {
  if (qs.q != null) { if (qs.q !== MS.seen) { MS.q = qs.q; MS.seen = qs.q; MS.kind = 'all'; MS.open = {}; } } else MS.seen = null;
  after(() => {
    const i = $('#ms-q'); if (!i) return;
    i.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.isComposing && e.keyCode !== 229) { msRemember(); i.blur(); } });
    if (!MS.q.trim() && isWide() && !isRerender()) i.focus();
  });
  return `<div class="screen s-search">
    ${head('さがす')}
    <div class="ms-top"><div class="ms-in">${ic('search')}${input({ id: 'ms-q', type: 'search', value: MS.q, ph: '店・人・品目・メモ', oninput: 'msInput(this.value)', label: 'さがす' })}<button class="ms-x" id="ms-x" onclick="msClear()" aria-label="消す" ${MS.q ? '' : 'hidden'}>${ic('close')}</button></div></div>
    <div class="ms-res" id="ms-res">${msRes()}</div>
  </div>`;
}, { tab: 'search', root: 1 });

/* ================= 設定 ================= */
route('settings', () => {
  const c = dataCounts(), kb = Math.max(1, Math.round(c.bytes / 1024));
  const cnt = [[c.acts, '記録', '件'], [c.plans, '予定', '件'], [c.tasks, 'ToDo', '件'], [c.memos, 'メモ', '件'], [c.ents, 'エンティティ', '件'], [kb, '大きさ', 'KB']];
  return `<div class="screen s-settings">
    ${head('設定')}
    <section>${sec('見た目')}<div class="card pad">${seg([['auto', '自動'], ['light', 'ライト'], ['dark', 'ダーク']], getTheme(), 'msTheme')}</div></section>
    <section>${sec('データ')}
      <div class="card ms-stats">${cnt.map(([v, l, u]) => `<div class="stat"><b class="num">${v.toLocaleString('ja-JP')}<small>${u}</small></b><span>${l}</span></div>`).join('')}</div>
      ${rows([
        row({ lead: ic('download'), t: '全てのデータを書き出す', on: 'msExport()' }),
        row({ lead: ic('upload'), t: '書き出したファイルを読み込む', on: 'msPickFile()' }),
        row({ lead: ic('undo'), t: '見本のデータに戻す', on: 'msReset()' })])}
      <input type="file" id="ms-file" accept="application/json,.json" hidden onchange="msFile(this)">
    </section>
    <section>${sec('履歴', { n: DB.log.length })}${rows([
      prop('件数', `<span class="num">${DB.log.length}</span>`),
      row({ lead: ic('trash'), t: '履歴を消す', on: 'msLogClear()' })])}</section>
    <section>${sec('LLM')}${msLlmBody()}</section>
    <section>${sec('支払い方法', { n: DB.methods.length })}
      ${rows(DB.methods.map(m => `<div class="row ms-m"><span class="row-m"><span class="row-t">${esc(m)}</span></span><span class="row-r">${iconBtn('trash', `${m}を消す`, `msMethodDel(${q(m)})`)}</span></div>`))}
      ${btn('支払い方法を足す', 'msMethodAdd()', 'dash', { icon: 'plus' })}</section>
    <section>${sec('整理')}${rows([
      row({ lead: ic('tag'), t: 'カテゴリ', right: `<span class="mini num">${Object.keys(DB.cats).length}</span>`, href: '#/cats', chev: 1 }),
      row({ lead: ic('tree'), t: '種類', right: `<span class="mini num">${Object.keys(DB.types).length}</span>`, href: '#/types', chev: 1 }),
      row({ lead: ic('list'), t: 'ToDoのリスト', right: `<span class="mini num">${Object.keys(DB.lists).length}</span>`, href: '#/todo', chev: 1 })])}</section>
    <section>${sec('このアプリ')}${rows([prop('版', esc(window.BUILD || '不明')), prop('今日', `<span class="num">${fmtFull(TODAY)} ${NOW}</span>`), prop('文字', 'M PLUS 2(SIL OFL)'), row({ lead: ic('undo'), t: '前の版を開く', sHtml: ['v5', 'v4', 'v3'].map(v => `<a class="ms-old" href="../${v}/">${v}</a>`).join('') })])}
      <p class="ms-note">${ic('info')}<span>データはこの端末の中だけに保存されます</span></p></section>
  </div>`;
}, { tab: 'settings', root: 1 });

function msTheme(k) { setTheme(k); rerender(); }
/* LLM(雑記を記録の候補にする)の設定。既定は模擬。DB には入らない(llmConfig / llmSetConfig) */
const MS_LLM_MODES = [['off', '使わない'], ['mock', '模擬'], ['endpoint', 'エンドポイント']];
function msLlmBody() {
  const c = window.llmConfig ? llmConfig() : { mode: 'off', url: '', timeoutSec: 20 };
  return rows([
    `<div class="row ms-llm">${seg(MS_LLM_MODES, c.mode, 'msLlmMode')}</div>`,
    ...(c.mode === 'endpoint' ? [`<div class="row">${field('エンドポイント', input({ id: 'ms-llm-url', type: 'url', value: c.url || '', ph: 'https://', onchange: 'msLlmSet({url:this.value})', label: 'エンドポイント' }))}</div>`] : []),
    `<div class="row">${field('タイムアウト(秒)', input({ id: 'ms-llm-to', mode: 'numeric', value: c.timeoutSec || 20, onchange: 'msLlmSet({timeoutSec:+this.value||20})', label: 'タイムアウト(秒)' }))}</div>`]);
}
function msLlmMode(k) { if (window.llmSetConfig) llmSetConfig({ mode: k }); rerender(); }
function msLlmSet(patch) { if (window.llmSetConfig) llmSetConfig(patch); }
/** 履歴を消す(取り消せない = 確認を出す) */
function msLogClear() {
  confirmSheet({ title: '履歴を消す', body: `履歴 ${DB.log.length}件を消します。取り消せません。雑記や記録は残ります。`, ok: '消す',
    on: () => { if (window.jtLogClear) jtLogClear(); rerender(); toast('履歴を消しました'); } });
}
function msExport() {
  downloadText(`kojin-kiroku-${TODAY}.json`, exportJSON());
  toast(`書き出しました(${Math.max(1, Math.round(dataCounts().bytes / 1024))}KB)`);
}
function msPickFile() { const f = $('#ms-file'); if (f) f.click(); }
let MS_FILE = '';
/** 選んだファイルを読み、置き換える前に確かめる */
function msFile(inp) {
  const f = inp.files && inp.files[0]; if (!f) return;
  f.text().then(t => {
    MS_FILE = t; inp.value = '';
    const c = dataCounts();
    confirmSheet({ title: '読み込む', body: `今のデータ(記録 ${c.acts}件・予定 ${c.plans}件・ToDo ${c.tasks}件・メモ ${c.memos}件・エンティティ ${c.ents}件)を、ファイルの内容で置き換えます。`, ok: '読み込む', on: msImport });
  }).catch(() => { inp.value = ''; toast('ファイルを読めませんでした', { bad: 1 }); });
}
function msImport() { const t = MS_FILE; MS_FILE = ''; done(importJSON(t), { msg: '読み込みました' }); }
function msReset() {
  confirmSheet({ title: '見本のデータに戻す', body: '全てのデータ(記録・予定・ToDo・メモ・エンティティ・カテゴリ)を消して、見本のデータに戻します。取り消せません。', ok: '見本に戻す',
    on: () => { resetData(); rerender(); toast('見本のデータに戻しました'); } });
}
let MS_NAME = '';
function msMethodAdd() {
  MS_NAME = '';
  openSheet({ title: '支払い方法を足す', body: field('名前', input({ id: 'ms-mn', ph: '例: 交通系IC', oninput: 'msName(this.value)', auto: 1, label: '名前' })),
    dirty: () => !!MS_NAME.trim(), primary: { label: '足す', on: () => done(methodAdd(MS_NAME), { close: 1 }) } });
}
function msName(v) { MS_NAME = v; }
function msMethodDel(n) { done(methodRemove(n)); }
