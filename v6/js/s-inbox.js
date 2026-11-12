/* 個人記録 v6.3 — 入力(担当 B。仕様 INPUT2.md §2・INBOX.md §7)
   2つのモード: 記録モード(.in-wrap.is-rec = #in-t に焦点がある間)と履歴モード(焦点が無い間)。
   #in-t はシートが開いている間 1度も作り直さない。描き直しは履歴・帯・札・道具・操作だけ。
   旧「すばやく書く」(s-home.js)と同名の capOpen / capAs / _capSeed を残す。 */
'use strict';

const IN_KEY_DRAFT = 'prmock-v6-inbox-draft';
let IN_DRAFT = '';        /* 書きかけ(閉じても残す。localStorage に写す) */
let IN_FILTER = 'all';    /* 絞り込み: all | jot | tent | add | edit | del */
let IN_LIMIT = 200;       /* 表示する行数(「もっと前」で +200) */
let IN_SEL = null;        /* 選択モードの雑記id の Set。null = 選択モードでない */
let IN_FORCE = null;      /* 種類の決め打ち: null(自動) | pay | plan | event | task | memo | diary */
let IN_FOCUS = null;      /* 開いた時に合わせる {t, k} */
let IN_NEW = null;        /* 今送った雑記id(吹き出しを出す) */
let IN_STICK = false;     /* 次の描き直しで一番下へ */
let IN_KEEP = false;      /* 次の描き直しで位置を保つ(もっと前) */
let IN_UNDO = null, IN_UNDO_T = 0;
let IN_SHOWN = [];        /* いま描いている雑記id(すべて選ぶ用) */
let IN_MIC_ON = false;
let IN_OFF = new Set();   /* 読みから取り消した役: date | time | amt | type */
let IN_BLUR_T = 0;        /* 焦点が外れてから履歴モードへ切り替えるまでの待ち */
const IN_KINDS = [[null, '自動'], ['pay', '支払い'], ['plan', '予定'], ['event', '出来事'], ['task', 'ToDo'], ['memo', 'メモ'], ['diary', '日記']];
const IN_TOOLS = '<button class="in-tl" onclick="inTool(\'today\')">今日</button>'
  + '<button class="in-tl" onclick="inTool(\'tomorrow\')">明日</button>'
  + '<button class="in-tl" onclick="inTool(\'time\')">時刻</button>'
  + '<button class="in-tl" onclick="inTool(\'yen\')">円</button>'
  + '<button class="in-tl" onclick="inTool(\'nl\')">改行</button>'
  + '<button class="icon-btn in-mic in-tl-mic" aria-label="音声で入力" aria-pressed="false" onclick="inMic()">' + ic('mic') + '</button>';

/* ---------- 公開: 開く・描き直す・互換 ---------- */

/** 「入力」を開く。o = {focus: 雑記id か {t,k}, filter, text(書き出し。下書きを置き換える), force(種類)}
    既に開いていれば、その中を描き直す。開いた直後は記録モード(入力欄に焦点)。 */
function openInbox(o = {}) {
  if (inWrap()) {
    if (o.text != null) inSetDraft(String(o.text));
    if ('force' in o) IN_FORCE = o.force || null;
    if (o.filter || o.focus) IN_FILTER = o.filter || 'all';
    if (o.focus) IN_FOCUS = inFocusKey(o.focus);
    IN_SEL = null; IN_LIMIT = Math.max(IN_LIMIT, 200);
    inRedrawAll();
    if (o.text != null || 'force' in o) inFocusT();
    return;
  }
  IN_FILTER = o.filter || 'all';
  IN_LIMIT = 200; IN_SEL = null; IN_FORCE = o.force || null;
  IN_FOCUS = o.focus ? inFocusKey(o.focus) : null;
  IN_NEW = null; IN_STICK = false; IN_KEEP = false;
  if (o.text != null) { IN_DRAFT = String(o.text); inSaveDraft(); }
  else IN_DRAFT = inLoadDraft();
  const s = openSheet({ id: 'inbox', title: '記録', tall: true, body: inBody(), onClose: inClosed });
  inWire(s.el);
  inRefresh();
  const t = $('#in-t', s.el);
  if (t) { t.focus({ preventScroll: true }); inGrow(t); }   /* ＋を押した瞬間に入力欄へ(記録モード) */
  return s;
}

/** 開いていれば描き直す(雑記・未確定・履歴が変わった時に呼ぶ。開いていなければ何もしない)。#in-t には触れない */
function inRefresh() {
  const w = inWrap(); if (!w) return;
  inPaintSel(w);
  inPaintHead(w);
  inPaintTop(w);
  inPaintLog(w);
  inPaintRec(w);
  inPaintUndo(w);
}

/** ＋(従来の呼び出しの互換)。openCapture(force, {text}) = openInbox({force, text}) */
function openCapture(force, o = {}) {
  openInbox({ force: force || null, text: o && o.text != null ? o.text : undefined });
}

/** 旧「すばやく書く」の互換: 書きかけの解釈を window._capSeed に入れて、詳しい入力を開く */
function capOpen(on) {
  const c = IN_DRAFT.trim() ? inCand() : null;
  const w = inWrap();
  if (w && _top() && _top().el === w) closeSheet(true);
  window._capSeed = c;
  try { (0, eval)(on); } finally { window._capSeed = null; }
}
/** 解釈を別の種類で読み替える(旧 capAs) */
function capAs(p, type) {
  const raw = IN_DRAFT.trim();
  return typeof candFromType === 'function' ? candFromType(raw, type) : null;
}
/** 旧 capSave の互換: 「記録する」(確定で作る。雑記は作らない) */
function capSave() { inRecord(); }
/** 旧 capRefresh の互換: 札と操作の部分だけ描き直す */
function capRefresh() { inAux(); }

/* ---------- 枠・モード・焦点 ---------- */

function inWrap() { const s = _sheets.find(x => x.o.id === 'inbox'); return s ? s.el : null; }
const inIsRec = w => { const wrap = w && $('.in-wrap', w); return !!(wrap && wrap.classList.contains('is-rec')); };
const inTitle = () => (IN_SEL ? (IN_SEL.size ? `${IN_SEL.size}件を選択` : '雑記を選ぶ') : (inIsRec(inWrap()) ? '記録' : '入力'));
/** ヘッダーの右(選ぶ / 完了) */
const inLead = () => (IN_SEL == null ? btn('選ぶ', 'inToggleSel()', 'ghost', { icon: 'check' }) : btn('完了', 'inToggleSel()', 'sec'));
const inboxCountSafe = () => (typeof inboxCount === 'function' ? inboxCount() : 0);

/** 本体の骨組み。一度だけ作る(#in-t はここで作ったきり)。中身は inRefresh が描く */
function inBody() {
  return `<div class="in-wrap">
    <div class="in-top" id="in-top"></div>
    <div class="in-log" id="in-log" role="log" aria-label="履歴"></div>
    <div class="in-dock">
      <div class="in-kinds" id="in-kinds" role="group" aria-label="種類"></div>
      <div class="in-row">
        <textarea id="in-t" class="in-t" rows="1" aria-label="入力" placeholder="ランチ 980 など" oninput="inInput(this)" onkeydown="inKey(event)">${esc(IN_DRAFT)}</textarea>
        <button class="icon-btn in-mic" id="in-mic" aria-label="音声で入力" aria-pressed="false" onclick="inMic()">${ic('mic')}</button>
        <button class="btn pri in-send-h" id="in-send" onclick="inSend()"><span>送る</span></button>
      </div>
      <div class="in-undo" id="in-undo"></div>
      <div class="in-cards" id="in-cards" aria-live="polite" hidden></div>
      <div class="in-tools" id="in-tools">${IN_TOOLS}</div>
      <div class="in-ops" id="in-ops"></div>
      <div class="in-aux" id="in-aux"></div>
    </div></div>`;
}

/** 入力欄に焦点がある間は記録モード、外れたら(150〜200ms 待って)履歴モード */
function inWire(w) {
  const t = $('#in-t', w); if (!t) return;
  t.addEventListener('focus', () => { clearTimeout(IN_BLUR_T); inRecOn(w, true); });
  t.addEventListener('blur', () => {
    clearTimeout(IN_BLUR_T);
    IN_BLUR_T = setTimeout(() => { if (document.activeElement !== t) inRecOn(w, false); }, 180);
  });
  /* 記録モードのボタンは押しても焦点を奪わない(iOS で押す前に切り替わって click が消えるのを防ぐ) */
  w.addEventListener('pointerdown', e => inGuard(e, w), true);
  w.addEventListener('mousedown', e => inGuard(e, w), true);
  /* 全画面のシートは下に引いて閉じない(ヘッダーの掴みを止める) */
  w.addEventListener('pointerdown', e => { if (e.target.closest && e.target.closest('.sheet-h')) e.stopPropagation(); }, true);
}
function inGuard(e, w) {
  const wrap = $('.in-wrap', w);
  if (!wrap || !wrap.classList.contains('is-rec') || !e.target.closest) return;
  if (e.target.closest('button')) e.preventDefault();
}
/** 記録モード ⇄ 履歴モード(class の付け外しだけ。入力欄は作り直さない) */
function inRecOn(w, on) {
  const wrap = w && $('.in-wrap', w); if (!wrap) return;
  if (on && IN_SEL != null) return;
  if (wrap.classList.contains('is-rec') === on) return;
  wrap.classList.toggle('is-rec', on);
  const t = $('#in-t', w);
  if (on) { if (t) t.style.height = ''; inPaintRec(w); }
  else { const log = $('#in-log', w); if (log) log.scrollTop = log.scrollHeight; if (t) inGrow(t); }
  inPaintHead(w);
}
/** 履歴モードへ(見出しの「履歴」) */
function inToHist() {
  const w = inWrap(); if (!w) return;
  const t = $('#in-t', w);
  inRecOn(w, false);
  if (t && document.activeElement === t) t.blur();
}

const inAtBottom = log => log.scrollHeight - log.scrollTop - log.clientHeight < 60;

function inClosed() {
  IN_SEL = null; IN_FOCUS = null; IN_NEW = null; IN_OFF = new Set();
  clearTimeout(IN_UNDO_T); IN_UNDO = null; clearTimeout(IN_BLUR_T);
  if (IN_MIC_ON) { IN_MIC_ON = false; typeof voiceActive === 'function' && voiceActive() && typeof voiceCancel === 'function' && voiceCancel(); }
}

/* ---------- 描き方(部分ごと) ---------- */

function inPaintSel(w) {
  const wrap = $('.in-wrap', w);
  if (IN_SEL != null) wrap.classList.remove('is-rec');
  wrap.classList.toggle('is-sel', IN_SEL != null);
  const aux = $('#in-aux', w); if (aux) aux.innerHTML = IN_SEL != null ? inSelBar() : '';
}
function inPaintHead(w) {
  w = w || inWrap(); if (!w) return;
  const h = $('h2', w); if (h) h.textContent = inTitle();
  const lead = $('.sheet-lead', w);
  if (lead) lead.innerHTML = inIsRec(w) && IN_SEL == null ? btn(`履歴 ${inboxCountSafe()}`, 'inToHist()', 'ghost') : inLead();
}
function inPaintTop(w) { const el = $('#in-top', w); if (el) el.innerHTML = inTop(); }
function inPaintLog(w) {
  const log = $('#in-log', w); if (!log) return;
  const y = log.scrollTop, bottom = inAtBottom(log);
  log.classList.toggle('is-sel', IN_SEL != null);
  log.innerHTML = inLogHtml();
  if (IN_STICK || (!IN_KEEP && bottom)) log.scrollTop = log.scrollHeight; else log.scrollTop = y;
  IN_STICK = false; IN_KEEP = false;
  inAfterLog(log);
}
function inPaintUndo(w) { const el = $('#in-undo', w || inWrap() || document); if (el) el.innerHTML = inUndoHtml(); }
/** 記録モードの札・種類・操作(入力欄は触らない) */
function inPaintRec(w) {
  w = w || inWrap(); if (!w) return;
  const R = inRead();
  const kinds = $('#in-kinds', w); if (kinds) kinds.innerHTML = inKindsHtml(R);
  const cards = $('#in-cards', w);
  if (cards) { cards.hidden = !R; cards.innerHTML = R ? inCardsHtml(R) : ''; }
  const ops = $('#in-ops', w); if (ops) ops.innerHTML = inOpsHtml(R);
  const send = $('#in-send', w); if (send) send.disabled = !IN_DRAFT.trim();
}
/** 描き直しの一括(互換) */
function inRedrawAll() { inRefresh(); }
function inAux() { const w = inWrap(); if (w) inPaintRec(w); }

/** 下書きの保存と読み込み(閉じても残す) */
function inSaveDraft() { try { IN_DRAFT ? localStorage.setItem(IN_KEY_DRAFT, IN_DRAFT) : localStorage.removeItem(IN_KEY_DRAFT); } catch (e) { } }
function inLoadDraft() { try { return localStorage.getItem(IN_KEY_DRAFT) || ''; } catch (e) { return ''; } }

/** 開いた時に合わせる対象。記録は、そのもとの雑記に直す */
function inFocusKey(x) {
  if (typeof x === 'string') return { t: 'jots', k: x };
  if (x.t === 'jots') return { t: 'jots', k: x.k };
  const rec = (DB[x.t] || {})[x.k];
  return rec && rec.jot ? { t: 'jots', k: rec.jot } : { t: x.t, k: x.k };
}

/* ---------- 上(絞り込み・未確定の帯) ---------- */

function inTop() {
  const jn = typeof jotsSorted === 'function' ? jotsSorted().length : Object.keys(DB.jots || {}).length;
  const tc = typeof tentCount === 'function' ? tentCount() : 0;
  const items = [['all', 'すべて'], ['jot', '雑記', jn], ['tent', '未確定', tc], ['add', '作った'], ['edit', '直した'], ['del', '消した']];
  return chips(items, IN_FILTER, 'inFilter') +
    (tc ? `<div class="in-band"><b>${tag('未確定', 'read')}<span class="num">${tc}件</span></b>${btn('まとめて確定', 'inConfirmAll()', 'sec', { sm: 1 })}</div>` : '');
}
function inFilter(k) { IN_FILTER = k; IN_LIMIT = 200; IN_STICK = true; inRefresh(); }
function inMore() { const log = $('#in-log'), y = log ? log.scrollTop : 0, h0 = log ? log.scrollHeight : 0; IN_LIMIT += 200; IN_KEEP = true; inRefresh(); const l2 = $('#in-log'); if (l2) l2.scrollTop = y + (l2.scrollHeight - h0); }

/* ---------- 履歴 ---------- */

function inView() { return typeof logView === 'function' ? (logView({ filter: IN_FILTER, q: '' }) || []) : []; }

function inLogHtml() {
  const all = inView();
  if (IN_FOCUS) { const idx = all.findIndex(r => r.t === IN_FOCUS.t && r.k === IN_FOCUS.k); if (idx >= 0 && all.length - idx > IN_LIMIT) IN_LIMIT = all.length - idx + 20; }
  const list = all.length > IN_LIMIT ? all.slice(all.length - IN_LIMIT) : all;
  IN_SHOWN = list.filter(r => r.t === 'jots' && r.alive !== false).map(r => r.k);
  if (!list.length) return empty({ icon: 'pencil', text: 'ここに入力' });
  const more = all.length > list.length ? `<button class="btn dash in-more" onclick="inMore()">もっと前</button>` : '';
  return more + inGroup(list).map(g => `<div class="in-day${g.d === TODAY ? ' is-today' : ''}"><span>${esc(inDayLabel(g.d))}</span></div>${g.items.map(inItemHtml).join('')}`).join('');
}
function inGroup(list) {
  if (typeof logDays === 'function') return logDays(list);
  const m = new Map(); list.forEach(r => { const d = (r.at || '').slice(0, 10); if (!m.has(d)) m.set(d, []); m.get(d).push(r); });
  return [...m].map(([d, items]) => ({ d, items }));
}
const inDayLabel = d => (d === TODAY ? '今日' : d === dAdd(TODAY, -1) ? '昨日' : fmtFull(d));

function inItemHtml(r) {
  if (r.t === 'jots') return r.alive === false ? inLine(r, true) : inBubble(r.k, r);
  if (r.op === 'del' || r.alive === false) return inLine(r, true);
  return inLine(r, false);
}

/** 作成・編集・削除の行(面なし・小さい字・左寄せ)。消した物は薄く、押せない */
function inLine(r, gone) {
  const V = { add: ['作った', 'plus'], edit: ['直した', 'pencil'], del: ['消した', 'trash'] }[r.op] || ['', 'more'];
  const key = `${r.t}:${r.k}`;
  const inner = `<span class="in-ev-ic">${ic(V[1])}</span><span class="in-ev-v">${V[0]}</span><span class="in-ev-ty">${esc(r.ty || '')}</span><b>${esc(r.s || '')}</b><time class="num">${esc((r.at || '').slice(11, 16))}</time>`;
  return gone ? `<div class="in-ev is-gone" data-key="${esc(key)}">${inner}</div>`
    : `<button class="in-ev" data-key="${esc(key)}" onclick="inGo(${q(r.t)},${q(r.k)})">${inner}</button>`;
}
/** 対象を開く(閉じてから移る) */
function inGo(t, k) { const href = inHref(t, k); if (!href) return; closeSheet(true); go(href); }
function inHref(t, k) {
  const m = { acts: '#/a/', tasks: '#/task/', memos: '#/memo?focus=', days: '#/day/', ents: '#/e/', notes: '#/n/' };
  return m[t] ? m[t] + encodeURIComponent(k) : '';
}

/** 雑記 = 自分の吹き出し(右寄せの面)。できた記録は中に入れ子で出す */
function inBubble(jid, r) {
  const j = (DB.jots || {})[jid]; if (!j) return inLine(r, true);
  const recs = typeof recsOfJot === 'function' ? recsOfJot(jid) : [];
  const st = typeof jotState === 'function' ? jotState(jid) : (recs.length ? 'tent' : 'raw');
  const L = j.llm || null, busy = !!L && (L.st === 'queued' || L.st === 'running');
  const selecting = IN_SEL != null, on = selecting && IN_SEL.has(jid);
  return `<div class="in-msg${on ? ' is-sel' : ''}" data-key="${esc('jots:' + jid)}"${selecting ? ` onclick="inTogSel(${q(jid)})"` : ''}>
    ${selecting ? check(on, `inTogSel(${q(jid)})`, { label: '選ぶ' }) : ''}
    <article class="card in-bub">
      <div class="in-bub-h"><time class="num">${esc((j.at || '').slice(11, 16))}</time>${j.src === 'voice' ? `<span class="in-ico">${ic('mic')}</span>` : ''}</div>
      <p class="in-bub-t">${esc(j.body)}</p>
      ${busy || (L && L.st === 'error') ? inLlmHtml(jid, L) : ''}
      ${recs.map(x => inRecHtml(jid, x)).join('')}
      ${st === 'raw' && !busy ? inRawActs(jid) : ''}
    </article></div>`;
}
function inRecHtml(jid, x) {
  const s = (typeof recSummary === 'function' && recSummary(x.t, x.row)) || { ty: '', title: '', facts: [] };
  const tent = !!(x.row && x.row.tent);
  /* 札は記録の via だけで決める(設定を変えても過去の由来は変わらない)。rule は札なし */
  const via = x.row && x.row.via === 'llm' ? tag('LLM', 'read') : x.row && x.row.via === 'mock' ? tag('模擬', 'read') : '';
  return `<div class="in-rec${tent ? ' is-tent' : ''}" data-key="${esc(x.t + ':' + x.k)}">
    <div class="in-rec-h">${tent ? tag('未確定', 'read') : ''}${via}<span>${esc(s.ty || '')}</span></div>
    <b class="in-rec-t">${esc(s.title || '')}</b>
    ${(s.facts || []).length ? `<div class="in-rec-f">${s.facts.map(f => `<span class="num">${esc(f)}</span>`).join('')}</div>` : ''}
    <div class="in-act">${tent ? btn('確定', `inConfirmRec(${q(x.t)},${q(x.k)})`, 'pri', { sm: 1 }) : ''}${btn('直す', `inConv(${q(jid)},${q(x.t)},${q(x.k)})`, 'sec', { sm: 1 })}</div>
  </div>`;
}
function inRawActs(jid) {
  return `<div class="in-act">${btn('変換する', `inConv(${q(jid)})`, 'sec', { sm: 1 })}${btn('メモにする', `inMemo(${q(jid)})`, 'ghost', { sm: 1 })}${btn('LLMで変換', `inLlm(${q(jid)})`, 'ghost', { sm: 1 })}</div>`;
}
function inLlmHtml(jid, L) {
  if (L.st === 'queued') return `<div class="in-llm"><span>順番待ち</span>${btn('やめる', `inLlmCancel(${q(jid)})`, 'ghost', { sm: 1 })}</div>`;
  if (L.st === 'running') return `<div class="in-llm is-run"><span>LLMで変換中</span><i class="in-dots" aria-hidden="true"><i></i><i></i><i></i></i></div>`;
  return `<div class="in-llm is-err"><span>できませんでした${L.why ? `(${esc(L.why)})` : ''}</span>${btn('もう一度', `inLlm(${q(jid)})`, 'sec', { sm: 1 })}</div>`;
}

/** 描いた直後の印(出たばかりの吹き出し・開いた対象)。CSS の動きは一度だけ */
function inAfterLog(log) {
  if (IN_FOCUS) {
    const el = inKeyEl(log, IN_FOCUS.t + ':' + IN_FOCUS.k);
    if (el) { log.scrollTop = Math.max(0, el.offsetTop - log.clientHeight / 2 + el.offsetHeight / 2); inFlash(el, 'is-focus'); }
    IN_FOCUS = null;
  }
  if (IN_NEW) { const el = inKeyEl(log, 'jots:' + IN_NEW); IN_NEW = null; if (el) inFlash(el, 'is-new'); }
}
const inKeyEl = (log, key) => [...log.querySelectorAll('[data-key]')].find(e => e.dataset.key === key) || null;
function inFlash(el, cls) { el.classList.add(cls); setTimeout(() => el.classList.remove(cls), 1400); }

/* ---------- 読み(記録モードの札。読みは readText / readBlocks の契約どおり) ---------- */

/** 入力を空行で分けた文(1文 = 1記録)。readBlocks が無ければ空行で分ける */
function inBlocks() {
  const t = IN_DRAFT.trim(); if (!t) return [];
  if (typeof readBlocks === 'function') { const b = readBlocks(t) || []; return b.length ? b : [t]; }
  return t.split(/\n\s*\n/).map(s => s.trim()).filter(Boolean);
}
/** 1文の読み。A が未完成の時は今の読み(強い時だけ候補) */
function inReadOne(text, o) {
  if (IN_FORCE === 'diary') return { cand: null, conf: 'none', marks: [], off: [], hint: 'diary', hintDate: TODAY };
  if (typeof readText === 'function') {
    const r = readText(text, { date: TODAY, time: NOW }, o) || {};
    const cand = r.cand || null;
    return { cand, conf: r.conf || (cand ? 'strong' : 'none'), marks: r.marks || [], off: r.off || [], hint: r.hint || null, hintDate: r.hint === 'diary' ? (r.hintDate || TODAY) : null };
  }
  let cand = null;
  if (o.type && typeof candFromType === 'function') cand = candFromType(text, o.type);
  else if (typeof ruleCandidates === 'function') cand = (ruleCandidates(text) || [])[0] || null;
  return { cand, conf: cand ? 'strong' : 'none', marks: [], off: [], hint: null, hintDate: null };
}
/** 入力全体の読み {items:[{text, r}], multi}。入力が空なら null */
function inRead() {
  const list = inBlocks(); if (!list.length) return null;
  const type = IN_FORCE && IN_FORCE !== 'diary' ? IN_FORCE : null, off = [...IN_OFF];
  const items = list.map(text => ({ text, r: inReadOne(text, { type, off }) }));
  return { items, multi: items.length > 1 };
}
/** いまの書きかけの候補(旧 inCand の互換。無ければ雑記の候補) */
function inCand() {
  const R = inRead(); const it = R && R.items[0]; if (!it) return null;
  if (it.r.cand) return it.r.cand;
  return typeof candFromType === 'function' ? candFromType(it.text, 'memo') : null;
}
/** 記録できる文か(金額の無い支払いは不可。感想の文は候補が無い) */
const inRecOk = r => !!r.cand && !(r.cand.type === 'pay' && !(+r.cand.amt > 0));
const inCanRecord = R => !!R && R.items.some(it => inRecOk(it.r));
/** 読み取った種類(自動の時に光らせる) */
const inReadKind = r => (r.hint === 'diary' ? 'diary' : r.cand ? r.cand.type : null);
/** 札に出す中身 {ty, cls, title, facts} */
function inCardOf(c) {
  const nm = e => (E(e) || {}).n || '';
  if (c.type === 'pay') return { ty: '支払い', cls: '', title: (c.payee ? (c.payee.e ? nm(c.payee.e) : c.payee.name) : c.text) || '支払い', facts: [c.amt ? yen(c.amt) : '金額なし', relDay(c.date)] };
  if (c.type === 'event' || c.type === 'plan') return { ty: c.type === 'event' ? '出来事' : '予定', cls: c.type === 'plan' ? ' is-plan' : '', title: c.title || '', facts: [`${relDay(c.date)} ${c.start || '終日'}${c.end ? '–' + c.end : ''}`] };
  if (c.type === 'task') return { ty: 'ToDo', cls: '', title: c.t || '', facts: c.due ? [relDay(c.due)] : [] };
  return { ty: 'メモ', cls: '', title: String(c.body || '').split('\n')[0].slice(0, 40), facts: [] };
}

/* ---------- 記録モードの描き方(種類・札・操作) ---------- */

function inKindsHtml(R) {
  const rd = R && !R.multi ? inReadKind(R.items[0].r) : null;
  const chips = IN_KINDS.map(([k, l]) => {
    const on = (IN_FORCE || null) === k, read = !IN_FORCE && !!k && k === rd;
    return `<button class="in-k${on ? ' is-on' : ''}${read ? ' is-read' : ''}" aria-pressed="${on}" onclick="inForce(${q(k)})">${esc(l)}</button>`;
  }).join('');
  return chips + `<button class="in-k" onclick="inKindsMenu()">ほか</button>`;
}
function inCardsHtml(R) {
  if (R.multi) return inMultiCard(R);
  const it = R.items[0], r = it.r;
  if (!r.cand) {
    if (r.hint === 'diary') return `<section class="in-card is-diary"><p class="in-tt clamp">${esc(it.text)}</p><div class="in-facts"><span>${esc(relDay(r.hintDate || TODAY))}</span></div><div class="in-act">${btn('日記に書く', 'inDiaryAt(0)', 'sec', { wide: 1 })}</div></section>`;
    return `<section class="in-card is-none"><span>雑記のまま置きます</span></section>`;
  }
  const k = inCardOf(r.cand);
  return `<section class="in-card${r.conf === 'weak' ? ' is-weak' : ''}">
    <div class="in-card-h">こう記録します</div>
    <div class="in-card-b"><span class="in-ty${k.cls}">${esc(k.ty)}</span><b class="in-tt">${esc(k.title)}</b></div>
    ${k.facts.length ? `<div class="in-facts num">${k.facts.map(f => `<span>${esc(f)}</span>`).join('')}</div>` : ''}
    ${inMarksHtml(r)}
  </section>`;
}
/** 読んだ語の小札。押すと取り消す(取り消した語は押すと戻る)。文の一部と判断して採らなかった語は押せない */
function inMarksHtml(r) {
  const on = (r.marks || []).map(m => `<button class="in-mk" onclick="inOffToggle(${q(m.role)})" aria-label="${esc(m.label)}を取り消す"><span class="in-mk-c">${esc(m.label)}${ic('close')}</span></button>`);
  const off = (r.off || []).map(m => IN_OFF.has(m.role)
    ? `<button class="in-mk is-off" onclick="inOffToggle(${q(m.role)})" aria-label="${esc(m.label)}を戻す"><span class="in-mk-c">${esc(m.label)}${ic('undo')}</span></button>`
    : `<span class="in-mk is-dim"><span class="in-mk-c">${esc(m.label)}</span></span>`);
  const all = [...on, ...off];
  return all.length ? `<div class="in-mks">${all.join('')}</div>` : '';
}
function inMultiCard(R) {
  const rows = R.items.map((it, i) => {
    const r = it.r;
    if (inRecOk(r)) { const k = inCardOf(r.cand); return `<div class="in-row2"><span class="in-ty${k.cls}">${esc(k.ty)}</span><b class="in-tt">${esc(k.title)}</b><span class="in-facts num">${esc(k.facts[0] || '')}</span></div>`; }
    if (r.hint === 'diary') return `<div class="in-row2"><span class="in-ty">日記</span><span class="in-tt clamp">${esc(it.text)}</span>${btn('日記に書く', `inDiaryAt(${i})`, 'sec')}</div>`;
    return `<div class="in-row2 is-raw"><span class="in-ty">雑記</span><span class="in-tt clamp">${esc(it.text)}</span></div>`;
  }).join('');
  return `<section class="in-card is-multi"><div class="in-card-h">こう記録します<span class="num">${R.items.length}件</span></div>${rows}</section>`;
}
/** 操作の列: 雑記に置く(副)・記録する(主) */
function inOpsHtml(R) {
  const can = inCanRecord(R), n = R && R.multi ? R.items.filter(it => inRecOk(it.r)).length : 0;
  const label = R && R.multi ? `記録する ${n}件` : '記録する';
  return btn('雑記に置く', 'inSend()', 'sec', { wide: 1, disabled: !IN_DRAFT.trim() }) + btn(label, 'inRecord()', 'pri', { wide: 1, disabled: !can });
}
/** ほか: 詳しい入力(今までの入口)。閉じて開く */
function inKindsMenu() {
  menu('ほかの入力', [
    { label: '支払い(詳しく)', icon: 'yen', on: () => capOpen('openPayEditor()') },
    { label: 'レシートを読み取る', icon: 'camera', on: () => capOpen('openReceipt()') },
    { label: '予定(詳しく)', icon: 'cal', on: () => capOpen("openActEditor({state:'plan'})") },
    { label: '出来事(詳しく)', icon: 'flag', on: () => capOpen("openActEditor({state:'done'})") },
    { label: 'ToDo(詳しく)', icon: 'task', on: () => capOpen('openTaskEditor()') },
    { label: 'エンティティ', icon: 'people', on: () => capOpen('openEntEditor()') },
    { label: '今日の日記を開く', icon: 'pencil', on: () => capOpen('openDiary(' + JSON.stringify(TODAY) + ')') },
  ]);
}
/** 種類の決め打ち(自動 = null) */
function inForce(k) { IN_FORCE = k || null; const w = inWrap(); if (w) inPaintRec(w); inFocusT(); }
/** 取り消す役を切り替える(読みを取り直す) */
function inOffToggle(role) { if (IN_OFF.has(role)) IN_OFF.delete(role); else IN_OFF.add(role); inPaintRec(); }
/** 道具の列: カーソル位置に区切って差し込む */
function inTool(k) {
  const t = $('#in-t', inWrap() || document); if (!t) return;
  const v = t.value, a = t.selectionStart != null ? t.selectionStart : v.length, b = t.selectionEnd != null ? t.selectionEnd : a;
  const before = v.slice(0, a), after = v.slice(b);
  let s;
  if (k === 'yen') { if (!/\d$/.test(before)) { inFocusT(); return; } s = '円'; }
  else if (k === 'nl') s = '\n';
  else {
    s = k === 'today' ? '今日' : k === 'tomorrow' ? '明日' : NOW;
    if (before && !/\s$/.test(before)) s = ' ' + s;
    if (after && !/^\s/.test(after)) s = s + ' ';
  }
  const next = before + s + after, p = before.length + s.length;
  t.value = next; t.setSelectionRange(p, p);
  inSetDraft(next);
  inFocusT();
}

/* ---------- 入力(下) ---------- */

function inFocusT() { const t = $('#in-t', inWrap() || document); t && t.focus({ preventScroll: true }); }

/** 本文を下書きに写す。textarea の値そのものは描き直さない(焦点を失わない) */
function inSetDraft(text) {
  IN_DRAFT = text; inSaveDraft();
  if (!String(text).trim()) IN_OFF = new Set();
  const w = inWrap(); if (!w) return;
  const log = $('#in-log', w), atB = log ? inAtBottom(log) : false;
  const t = $('#in-t', w); if (t && t.value !== text) t.value = text;
  if (t) inGrow(t);
  inPaintRec(w);
  if (log && atB) log.scrollTop = log.scrollHeight;
}
function inInput(el) { inSetDraft(el.value); }
function inGrow(t) {
  if (t.closest('.is-rec')) { t.style.height = ''; return; }   /* 記録モードは残りの高さを使う(CSS) */
  const cs = getComputedStyle(t), lh = parseFloat(cs.lineHeight) || 24, max = lh * 5 + parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom);
  t.style.height = 'auto';
  t.style.height = Math.min(t.scrollHeight, max) + 'px';
  t.style.overflowY = t.scrollHeight > max ? 'auto' : 'hidden';
}
/** Enter: PC(細かいポインタ)では送る、Shift+Enter で改行。タッチでは改行。Ctrl/Cmd+Enter は常に送る。変換中の Enter は除く */
function inKey(e) {
  if (e.key !== 'Enter' || e.isComposing || e.keyCode === 229) return;
  const fine = typeof matchMedia === 'function' && matchMedia('(pointer: fine)').matches;
  if (e.metaKey || e.ctrlKey || (fine && !e.shiftKey)) { e.preventDefault(); inSend(); }
}
/** 音声などの確定文を足す。前が空なら何も挟まない。前が空でなければ空白を挟む(前が改行なら挟まない)。改行だけが来たら改行を足す */
function inAppend(t) {
  const raw = String(t == null ? '' : t), s = raw.trim();
  const cur = IN_DRAFT.replace(/[ 　]+$/, '');
  if (!s) { if (cur && /\n/.test(raw)) inSetDraft(cur + raw.replace(/[^\n]/g, '')); }
  else inSetDraft(cur ? cur + (/\n$/.test(cur) ? '' : ' ') + s : s);
  inFocusT(); inMicPaint();
}

/** 雑記に置く。空行で分かれていれば文ごとに、まとめて1回の変更で置く */
function inSend() {
  const text = IN_DRAFT.trim();
  if (!text) { inFocusT(); return; }
  const list = inBlocks();
  const r = list.length > 1 ? inJotsAdd(list) : (typeof jotAdd === 'function' ? jotAdd(text, { src: 'text', convert: true }) : null);
  if (!r || !r.ok) { done(r || { ok: false, why: 'できませんでした' }); return; }
  IN_NEW = r.id || (r.ids && r.ids[0]) || null; IN_STICK = true;
  inSetDraft('');
  inDone(r, { quiet: 1 });
  inUndoShow(r, text);
  inFocusT();
}
/** 複数の雑記を置く(jotAddMany が無ければ1文ずつ。取り消しは全部まとめて1回) */
function inJotsAdd(list) {
  if (typeof jotAddMany === 'function') return jotAddMany(list, { src: 'text', convert: true });
  const undos = []; let ok = true; const ids = [];
  for (const t of list) { const r = jotAdd(t, { src: 'text', convert: true }); if (!r || !r.ok) { ok = false; break; } ids.push(r.id); if (r.undo) undos.push(r.undo); }
  return { ok, ids, undo: () => undos.reverse().forEach(u => u()), label: `${list.length}件を雑記に置きました` };
}
function inUndoShow(r, text) {
  clearTimeout(IN_UNDO_T);
  IN_UNDO = { undo: r.undo, text, label: r.label || '置きました' };
  inPaintUndo();
  IN_UNDO_T = setTimeout(() => { IN_UNDO = null; inPaintUndo(); }, 3000);
}
function inUndoHtml() { return IN_UNDO ? `<div class="in-undo-in"><span>${esc(IN_UNDO.label)}</span>${IN_UNDO.undo ? btn('取り消す', 'inUndo()', 'ghost', { sm: 1 }) : ''}</div>` : ''; }
function inUndo() {
  const u = IN_UNDO; if (!u) return;
  clearTimeout(IN_UNDO_T); IN_UNDO = null; inPaintUndo();
  if (u.undo) u.undo();
  if (!IN_DRAFT.trim()) inSetDraft(u.text);
  inRefresh(); rerender(); toast('取り消しました');
}

/** 音声: 押すと始まり、もう一度押すと終わる。終わった文は下書きに足す */
function inMic() {
  if (!(typeof voiceStart === 'function')) return;
  if (typeof voiceActive === 'function' && voiceActive()) { IN_MIC_ON = false; typeof voiceFinish === 'function' && voiceFinish(); }
  else { IN_MIC_ON = true; voiceStart({ from: 'composer', onText: inAppend }); }
  inMicPaint();
}
/** 録音中の印(入力欄の下の 🎤 も道具の列の 🎤 も同じ) */
function inMicPaint() {
  const on = !!(typeof voiceActive === 'function' && voiceActive());
  document.querySelectorAll('.in-mic').forEach(b => { b.classList.toggle('is-rec', on); b.setAttribute('aria-pressed', on ? 'true' : 'false'); });
}

/* ---------- 記録する・日記に書く ---------- */

/** 「記録する」: 札の候補を確定の記録にする(雑記は作らない)。文が複数なら文ごとに記録し、読めない文は雑記に置く */
function inRecord() {
  const R = inRead(); if (!R || !inCanRecord(R)) return;
  const text = IN_DRAFT.trim();
  if (!R.multi) {
    const r = inSaveCand(R.items[0].r.cand);
    if (!r) return;
    if (!r.ok) { done(r); return; }
    IN_FORCE = null;
    inSetDraft('');
    inDone(r, { quiet: 1 });
    inUndoShow(r, text);
    inFocusT();
    return;
  }
  const undos = []; let n = 0; const raw = []; let bad = '';
  R.items.forEach(it => {
    const x = inRecOk(it.r) ? inSaveCand(it.r.cand) : null;
    if (x && x.ok) { n++; if (x.undo) undos.push(x.undo); }
    else { raw.push(it.text); if (x && x.why) bad = x.why; }
  });
  let rawOk = false;
  if (raw.length) { const j = inJotsAdd(raw); if (j && j.ok) { rawOk = true; if (j.undo) undos.push(j.undo); } }
  if (!n && !rawOk) { toast(bad || 'できませんでした', { bad: 1 }); return; }
  const label = rawOk ? `${n}件を記録・${raw.length}件は雑記に置きました` : `${n}件を記録しました`;
  const r = { ok: true, label, undo: () => undos.slice().reverse().forEach(u => u()) };
  IN_FORCE = null;
  inSetDraft('');
  inDone(r, { quiet: 1 });
  inUndoShow(r, text);
  inFocusT();
}
/** 日記に書く(感想の文)。i = 文の番号。文が1つなら入力を空にし、複数なら、その文だけ外す */
function inDiaryAt(i) {
  const R = inRead(), it = R && R.items[i];
  if (!it || typeof diaryAppend !== 'function') return;
  const text = it.text, r = diaryAppend(it.r.hintDate || TODAY, text);
  if (!r || !r.ok) { done(r || { ok: false, why: 'できませんでした' }); return; }
  if (R.multi) inDropBlock(i); else inSetDraft('');
  inDone(r, { quiet: 1 });
  inUndoShow(r, text);
  inFocusT();
}
/** 文の一つを入力から外す(空行で分けて残す) */
function inDropBlock(i) { const b = inBlocks(); b.splice(i, 1); inSetDraft(b.join('\n\n')); }

/** 候補を確定の記録にする(旧 capSave と同じ。雑記は作らない) */
function inSaveCand(c) {
  if (!c) return null;
  const ents = c.ents || [];
  if (c.type === 'pay') return paySave({ date: c.date, start: c.start, payee: c.payee || null, amt: c.amt, exp: { basis: 'items', cat: c.cat || catGuess(c.payee, c.text) } });
  if (c.type === 'event' || c.type === 'plan') return actAdd({ kind: c.kind || 'event', title: c.title, date: c.date, start: c.start, end: c.end, allDay: c.start ? 0 : 1, state: c.type === 'event' ? 'done' : 'plan', roles: inRoles(ents) });
  if (c.type === 'task') return taskAdd({ t: c.t, due: c.due, list: c.list, pri: c.pri, ents });
  if (c.type === 'memo') return memoAdd(c.body, { ents });
  return null;
}
function inRoles(ids) {
  return (ids || []).filter(e => E(e)).map(e => {
    const ty = (E(e).types || [])[0];
    return { r: ty && isUnder(ty, 'place') ? '場所' : ty && isUnder(ty, 'project') ? 'プロジェクト' : '同席', e };
  });
}

/** 結果を受ける: done の取り消しの後にも「入力」を描き直す */
function inDone(r, o = {}) {
  if (!r) return false;
  const u = r.undo;
  const ok = done(Object.assign({}, r, { undo: u ? () => { u(); inRefresh(); } : undefined }), o);
  inRefresh();
  return ok;
}

/* ---------- 雑記の操作 ---------- */

function inConv(jid, t, k) { if (!(typeof openConv === 'function')) return; openConv(jid, t ? { rec: { t, k } } : {}); }
function inConfirmRec(t, k) { if (typeof tentConfirm === 'function') inDone(tentConfirm([{ t, k }])); }
function inConfirmAll() { if (typeof tentConfirmAll === 'function') inDone(tentConfirmAll()); }
function inMemo(jid) {
  const j = (DB.jots || {})[jid]; if (!j || typeof jotConvert !== 'function' || typeof candFromType !== 'function') return;
  inDone(jotConvert(jid, candFromType(j.body, 'memo'), { confirm: true }));
}
function inLlm(jid) {
  if (!(typeof llmSubmit === 'function')) return;
  const r = llmSubmit([jid]);
  if (r && r.ok) { inRefresh(); done(r, { msg: 'LLMで変換します', noUndo: 1 }); } else done(r || { ok: false, why: 'できませんでした' });
  inRefresh();
}
function inLlmCancel(jid) { typeof llmCancel === 'function' && llmCancel(jid); inRefresh(); }

/* ---------- 選択モード ---------- */

function inToggleSel() {
  IN_SEL = IN_SEL ? null : new Set();
  if (IN_SEL) { const a = document.activeElement; a && a.blur && a.blur(); const w = inWrap(); w && inRecOn(w, false); }
  inRedrawAll();
}
function inTogSel(jid) { if (!IN_SEL) return; IN_SEL.has(jid) ? IN_SEL.delete(jid) : IN_SEL.add(jid); inRefresh(); }
function inSelAll() {
  if (!IN_SEL) return;
  const all = IN_SHOWN.length && IN_SHOWN.every(j => IN_SEL.has(j));
  IN_SEL = all ? new Set() : new Set(IN_SHOWN);
  inRefresh();
}
function inSelInfo() {
  const ids = [...(IN_SEL || [])], tent = [];
  ids.forEach(j => (typeof recsOfJot === 'function' ? recsOfJot(j) : []).forEach(x => { if (x.row && x.row.tent) tent.push({ t: x.t, k: x.k }); }));
  const llm = ids.filter(j => (typeof jotState === 'function' ? jotState(j) !== 'done' : true));
  return { llm, tent };
}
function inSelBar() {
  const info = inSelInfo(), n = IN_SEL ? IN_SEL.size : 0;
  const all = IN_SHOWN.length && IN_SHOWN.every(j => IN_SEL && IN_SEL.has(j));
  return `<div class="in-selbar">${btn(`LLMで変換 ${info.llm.length}`, 'inSelLlm()', 'pri', { sm: 1, disabled: !info.llm.length })}${btn(`確定 ${info.tent.length}`, 'inSelConfirm()', 'sec', { sm: 1, disabled: !info.tent.length })}${btn(`消す ${n}`, 'inSelDel()', 'sec', { sm: 1, disabled: !n })}${btn(all ? '選びを外す' : 'すべて選ぶ', 'inSelAll()', 'ghost', { sm: 1, disabled: !IN_SHOWN.length })}</div>`;
}
function inSelLlm() {
  if (!(typeof llmSubmit === 'function')) return;
  const info = inSelInfo(); IN_SEL = null;
  if (!info.llm.length) { inRedrawAll(); return; }
  const r = llmSubmit(info.llm);
  if (r && r.ok) done(r, { msg: `LLMで変換します ${info.llm.length}件`, noUndo: 1 }); else done(r || { ok: false, why: 'できませんでした' });
  inRedrawAll();
}
function inSelConfirm() {
  const info = inSelInfo(); IN_SEL = null;
  if (info.tent.length && typeof tentConfirm === 'function') inDone(tentConfirm(info.tent));
  inRedrawAll();
}
function inSelDel() {
  if (!IN_SEL || !(typeof jotRemove === 'function')) return;
  const ids = [...IN_SEL], undos = []; let n = 0, bad = '';
  ids.forEach(j => { const r = jotRemove(j, { recs: true }); if (r && r.ok) { n++; if (r.undo) undos.push(r.undo); } else bad = bad || (r && r.why) || ''; });
  IN_SEL = null;
  if (n) inDone({ ok: true, label: `雑記を消しました ${n}件`, undo: undos.length ? () => { undos.reverse().forEach(u => u()); } : null });
  if (bad) toast(bad, { bad: 1 });
  inRedrawAll();
}

/* ---------- 下書きの保存 ---------- */
/* inSetDraft / inSend などは上で定義。ここから下は旧 capSave の互換の別名のみ */
