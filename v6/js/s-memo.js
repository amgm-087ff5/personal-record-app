/* 個人記録 v6 — メモ(紙)とノート(エンティティごとの長い文)。接頭辞 mm */
'use strict';

/* 画面の状態(描き直しても残す)。書きかけは MMS.body / MMS.ents */
const MMS = { v: 'memo', body: '', ents: [], find: false, q: '', flash: null };

const mmEntsV = ids => ids.map(e => ent(e, { link: false })).join('');
const mmDayLabel = d => d === TODAY ? '今日' : d === dAdd(TODAY, -1) ? '昨日' : fmtFull(d);
/** 自分以外のエンティティを複数選ぶ。on(配列) */
function mmEntPick(cur, on) {
  pick({ title: 'エンティティ', multi: true, search: true, cur, on, items: Object.entries(DB.ents).map(([id, e]) => ({ k: id, label: e.n, sub: e.types && e.types[0] ? typePath(e.types[0]) : '', lead: avatar(id) })) });
}
const mmHit = (q, ...s) => !q || s.some(x => String(x || '').toLowerCase().includes(q));

/* =====================================================================
   #/memo (?v=notes  ?focus=id)
   ===================================================================== */
route('memo', (id, qs) => {
  MMS.v = qs.v === 'notes' ? 'notes' : 'memo';
  const focus = qs.focus || MMS.flash;
  after(() => {
    if (focus && !isRerender()) { const el = $('#mm-' + focus); if (el) { el.scrollIntoView({ block: 'center' }); el.classList.add('mm-flash'); } }
    MMS.flash = null;
  });
  return `<div class="screen s-memo">
    ${head('メモ', { actions: iconBtn('search', 'さがす', 'mmFindToggle()', 'on-card') })}
    ${seg([['memo', 'メモ'], ['notes', 'ノート']], MMS.v, 'mmView')}
    <div id="mm-find">${mmFind()}</div>
    ${MMS.v === 'memo' ? `<div id="mm-comp" class="mm-comp">${mmComposer()}</div>` : `<div>${btn('ノートを書く', 'mmNoteNew()', 'sec', { icon: 'plus', wide: 1 })}</div>`}
    <div id="mm-list" class="mm-list">${MMS.v === 'memo' ? mmMemos() : mmNotes()}</div>
  </div>`;
}, { tab: 'memo', root: 1 });

function mmView(k) { MMS.v = k; go(k === 'notes' ? '#/memo?v=notes' : '#/memo', { replace: 1 }); }
const mmFind = () => MMS.find ? `<input id="mm-q" class="input" type="search" value="${esc(MMS.q)}" placeholder="メモをさがす" oninput="mmQ(this.value)" aria-label="メモをさがす" autocomplete="off">` : '';
function mmFindToggle() {
  MMS.find = !MMS.find; if (!MMS.find) MMS.q = '';
  $('#mm-find').innerHTML = mmFind(); mmRefresh();
  if (MMS.find) { const i = $('#mm-q'); i && i.focus(); }
}
function mmQ(v) { MMS.q = v; mmRefresh(); }
function mmRefresh() { const el = $('#mm-list'); if (el) el.innerHTML = MMS.v === 'memo' ? mmMemos() : mmNotes(); }

/* ---------- 書く欄(角を折った紙) ---------- */
function mmComposer() {
  return `<div class="memo mm-new"><textarea id="mm-t" class="mm-ta" rows="3" placeholder="思いついたことを書く" oninput="mmDraft(this)" aria-label="メモ">${esc(MMS.body)}</textarea>${MMS.ents.length ? `<div class="ents">${mmEntsV(MMS.ents)}</div>` : ''}</div>
    <div class="mm-act">${btn(MMS.ents.length ? 'エンティティを直す' : 'エンティティを付ける', 'mmCompEnts()', 'sec', { icon: 'tag' })}<span></span><button class="btn pri" onclick="mmSave()">保存</button></div>`;
}
function mmDraft(el) { MMS.body = el.value; el.style.height = 'auto'; el.style.height = Math.max(el.scrollHeight, 84) + 'px'; }
function mmCompEnts() { mmEntPick(MMS.ents, l => { MMS.ents = l; $('#mm-comp').innerHTML = mmComposer(); mmFit(); }); }
const mmFit = () => { const el = $('#mm-t'); if (el) { el.style.height = 'auto'; el.style.height = Math.max(el.scrollHeight, 84) + 'px'; } };
function mmSave() {
  const r = memoAdd(MMS.body, { ents: MMS.ents });
  if (!r.ok) { done(r); const el = $('#mm-t'); el && el.focus(); return; }
  MMS.body = ''; MMS.ents = []; MMS.flash = r.id; MMS.q = ''; MMS.find = false;
  done(r);
}

/* ---------- メモの一覧(ピン留め → 日ごと) ---------- */
function mmMemos() {
  const w = MMS.q.trim().toLowerCase();
  const all = memosSorted(), list = all.filter(([, m]) => mmHit(w, m.body, ...(m.ents || []).map(e => E(e) && E(e).n), m.act && actTitle(A(m.act))));
  if (!all.length) return `<div class="card">${empty({ icon: 'memo', text: 'メモは まだありません', action: { label: 'メモを書く', on: "document.getElementById('mm-t').focus()" } })}</div>`;
  if (!list.length) return `<div class="card">${empty({ icon: 'search', text: 'あてはまるメモは ありません' })}</div>`;
  const card = id => `<div class="mm-i" id="mm-${id}">${memoCard(id, { clamp: 5, on: `openMemoEditor(${q(id)})` })}</div>`;
  const pins = list.filter(([, m]) => m.pin), days = [...new Set(list.filter(([, m]) => !m.pin).map(([, m]) => m.at.slice(0, 10)))];
  return (pins.length ? `<section>${sec('ピン留め', { n: pins.length })}<div class="stack">${pins.map(([id]) => card(id)).join('')}</div></section>` : '')
    + days.map(d => { const ms = list.filter(([, m]) => !m.pin && m.at.slice(0, 10) === d); return `<section>${sec(mmDayLabel(d), { n: ms.length })}<div class="stack">${ms.map(([id]) => card(id)).join('')}</div></section>`; }).join('');
}

/* ---------- ノートの一覧(更新の新しい順) ---------- */
function mmNoteText(n) {
  const raw = (n.body.find(b => b[1]) || [])[1] || (n.subs.length ? n.subs.map(s => s[0]).join('、') : '');
  return raw.replace(/\{e:([^}]*)\}/g, (_, k) => (E(k) ? E(k).n : '')).replace(/\{a:[^}]*\}/g, '').trim();
}
function mmNotes() {
  const w = MMS.q.trim().toLowerCase();
  const all = Object.entries(DB.notes).filter(([eid]) => E(eid)).sort((a, b) => b[1].upd.localeCompare(a[1].upd));
  const list = all.filter(([eid, n]) => mmHit(w, E(eid).n, n.body.map(b => b.join(' ')).join(' '), n.subs.map(s => s.join(' ')).join(' ')));
  if (!all.length) return `<div class="card">${empty({ icon: 'book', text: 'ノートは まだありません', action: { label: 'ノートを書く', on: 'mmNoteNew()' } })}</div>`;
  if (!list.length) return `<div class="card">${empty({ icon: 'search', text: 'あてはまるノートは ありません' })}</div>`;
  return rows(list.map(([eid, n]) => { const ex = mmNoteText(n);
    return row({ lead: avatar(eid, null, 'lg'), t: `${E(eid).n}のノート`, sHtml: `${ex ? `<span class="clamp mm-ex" style="-webkit-line-clamp:2">${esc(ex)}</span>` : ''}<span class="num">${n.ver}版 · ${fmtMD(n.upd.slice(0, 10))} 更新</span>`, href: `#/n/${eid}`, chev: 1, cls: 'mm-nr' }); }));
}
/** ノートを書く: エンティティを選んで #/n/id へ */
function mmNoteNew() {
  const items = Object.entries(DB.ents).map(([id, e]) => ({ k: id, label: e.n, has: !!DB.notes[id], sub: [e.types && e.types[0] ? typePath(e.types[0]) : '', DB.notes[id] ? 'ノートあり' : ''].filter(Boolean).join(' · '), lead: avatar(id) })).sort((a, b) => a.has - b.has);
  pick({ title: 'ノートを書く', search: true, items, on: k => k && go('#/n/' + k) });
}

/* =====================================================================
   メモのシート(直す / 新しく書く)
   ===================================================================== */
let MME = null;
function openMemoEditor(id) {
  const m = id && DB.memos[id], sd = !m && window._capSeed && window._capSeed.type === 'memo' ? window._capSeed : null;
  MME = m ? { id, body: m.body, ents: [...(m.ents || [])], act: m.act || '', pin: !!m.pin, o: { body: m.body, ents: [...(m.ents || [])].join(), act: m.act || '', pin: !!m.pin } }
    : { id: null, body: sd ? sd.body || '' : '', ents: sd && sd.ents ? [...sd.ents] : [], act: '', pin: false, o: { body: '', ents: '', act: '', pin: false } };
  const at = m ? m.at : `${TODAY} ${NOW}`;
  openSheet({ id: 'memo-ed', title: m ? 'メモを直す' : 'メモを書く', lead: `<span class="tag num">${at.slice(0, 10) === TODAY ? '今日' : fmtFull(at.slice(0, 10))} ${at.slice(11)}</span>`, body: mmeBody(),
    dirty: mmeDirty, primary: { label: '保存', on: mmeSave }, secondary: m ? { label: '消す', on: mmeDel } : null });
}
const mmeDirty = () => MME.body.trim() !== MME.o.body.trim() || MME.ents.join() !== MME.o.ents || MME.act !== MME.o.act || MME.pin !== MME.o.pin;
/** 未確定のメモの帯(編集シートの上。確定 / 雑記を見る) */
function mmTent(id) {
  const m = id && DB.memos[id]; if (!m || !m.tent) return '';
  return banner(`未確定です。内容を確かめてください<div class="tent-b">${btn('確定', `mmTentOk(${q(id)})`, 'pri', { sm: 1, icon: 'check' })}${btn('雑記を見る', `window.openInbox && openInbox({focus:{t:'memos',k:${q(id)}}})`, 'sec', { sm: 1 })}</div>`, 'info');
}
function mmTentOk(id) { if (window.tentConfirm && done(tentConfirm({ t: 'memos', k: id })) && sheetOpen()) setSheetBody(mmeBody()); }
function mmeBody() {
  const e = MME, a = e.act && A(e.act);
  return `${mmTent(e.id)}${textarea({ id: 'mme-t', rows: 6, ph: '思いついたことを書く', value: e.body, oninput: 'MME.body=this.value', auto: !e.id, label: '本文' })}
    ${pickField('エンティティ', e.ents.length ? mmEntsV(e.ents) : '付ける', 'mmeEnts()', { emptyv: !e.ents.length })}
    ${a ? `<div class="field"><span class="field-l">記録</span><div class="mm-lkr"><a class="mm-lk" href="#/a/${e.act}" onclick="return mmeOpenAct(${q(e.act)})">${kindIc(a)}<b>${esc(actTitle(a))}</b><span class="mini num">${relDay(a.date)} ${timeText(a)}</span></a><button class="btn ghost sm" onclick="mmeAct()">変える</button></div></div>` : pickField('記録', 'なし', 'mmeAct()', { emptyv: 1 })}
    ${e.id ? `<div class="rows">${row({ lead: ic('pin'), t: 'ピン留め', right: toggle(e.pin, 'mmePin()', 'ピン留め') })}</div>` : ''}`;
}
const mmeRedraw = () => setSheetBody(mmeBody());
function mmeEnts() { mmEntPick(MME.ents, l => { MME.ents = l; mmeRedraw(); }); }
function mmePin() { MME.pin = !MME.pin; mmeRedraw(); }
/** 最近7日の記録から選ぶ */
function mmeAct() {
  const items = [], seen = new Set();
  for (let i = 0; i < 7; i++) { const d = dAdd(TODAY, -i); actsOn(d, { state: 'done' }).forEach(([id, a]) => { if (seen.has(id)) return; seen.add(id); items.push({ k: id, label: actTitle(a), sub: `${relDay(d)} ${timeText(a)}`, lead: kindIc(a) }); }); }
  if (MME.act && !seen.has(MME.act) && A(MME.act)) { const a = A(MME.act); items.unshift({ k: MME.act, label: actTitle(a), sub: `${relDay(a.date)} ${timeText(a)}`, lead: kindIc(a) }); }
  pick({ title: '記録', search: true, items, cur: MME.act || null, none: { label: 'なし' }, on: k => { MME.act = k || ''; mmeRedraw(); } });
}
/** 結び付けた記録を開く(書き換え中は先に保存) */
function mmeOpenAct(id) {
  if (mmeDirty()) { toast('保存してから開けます', { bad: 1 }); return false; }
  closeSheet(true); go('#/a/' + id); return false;
}
function mmeSave() {
  const e = MME;
  if (!e.id) return done(memoAdd(e.body, { ents: e.ents, act: e.act || undefined }), { close: 1 });
  const r = memoUpdate(e.id, { body: e.body, ents: e.ents, act: e.act || null });
  if (!r.ok) return done(r);
  let res = r;
  if (e.pin !== e.o.pin) { const p = memoPin(e.id); res = { ok: true, label: r.label, undo() { p.undo(); r.undo(); } }; }
  done(res, { close: 1 });
}
function mmeDel() { done(memoRemove(MME.id), { close: 1 }); }

/* Ctrl+Enter / Cmd+Enter で保存(書く欄とシートの本文) */
document.addEventListener('keydown', ev => {
  if (ev.key !== 'Enter' || !(ev.ctrlKey || ev.metaKey) || ev.isComposing || ev.keyCode === 229) return;
  const id = ev.target && ev.target.id;
  if (id === 'mm-t') { ev.preventDefault(); mmSave(); }
  else if (id === 'mme-t' && MME) { ev.preventDefault(); mmeSave(); }
});
