/* 個人記録 v6.2 — 入力エリア(担当 C。仕様 INBOX.md §7・§3.4・§7.4)
   状態は IN_ で始まる最上位の変数だけ。旧「すばやく書く」(s-home.js)と同名の capOpen / capAs / _capSeed を残す。 */
'use strict';

/* 部品に無い「マイク」を足す(IC は const のオブジェクト。足すだけ) */

const IN_KEY_DRAFT = 'prmock-v6-inbox-draft';
let IN_DRAFT = '';        /* 書きかけ(閉じても残す。localStorage に写す) */
let IN_FILTER = 'all';    /* 絞り込み: all | jot | tent | add | edit | del */
let IN_LIMIT = 200;       /* 表示する行数(「もっと前」で +200) */
let IN_SEL = null;        /* 選択モードの雑記id の Set。null = 選択モードでない */
let IN_FORCE = null;      /* 種類を決め打ち(openCapture(force) / 解釈の chips) */
let IN_FOCUS = null;      /* 開いた時に合わせる {t, k} */
let IN_NEW = null;        /* 今送った雑記id(吹き出しを出す) */
let IN_STICK = false;     /* 次の描き直しで一番下へ */
let IN_KEEP = false;      /* 次の描き直しで位置を保つ(もっと前) */
let IN_AUX = '';          /* #in-aux の状態: kinds | ip | sel */
let IN_UNDO = null, IN_UNDO_T = 0;
let IN_SHOWN = [];        /* いま描いている雑記id(すべて選ぶ用) */
let IN_MIC_ON = false;

/* ---------- 公開: 開く・描き直す・互換 ---------- */

/** 「入力」を開く。o = {focus: 雑記id か {t,k}, filter, text(書き出し。下書きを置き換える), force(種類)}
    既に開いていれば、その中を描き直す。 */
function openInbox(o = {}) {
  if (inWrap()) {
    if (o.text != null) inSetDraft(String(o.text));
    if ('force' in o) IN_FORCE = o.force || null;
    if (o.filter || o.focus) IN_FILTER = o.filter || 'all';
    if (o.focus) IN_FOCUS = inFocusKey(o.focus);
    IN_SEL = null; IN_LIMIT = Math.max(IN_LIMIT, 200);
    inRedrawAll();
    return;
  }
  IN_FILTER = o.filter || 'all';
  IN_LIMIT = 200; IN_SEL = null; IN_FORCE = o.force || null;
  IN_FOCUS = o.focus ? inFocusKey(o.focus) : null;
  IN_NEW = null; IN_STICK = false; IN_KEEP = false; IN_AUX = '';
  if (o.text != null) { IN_DRAFT = String(o.text); inSaveDraft(); }
  else IN_DRAFT = inLoadDraft();
  const s = openSheet({ id: 'inbox', title: '入力', tall: true, lead: inLead(), body: inBody(), onClose: inClosed });
  inVVBind(true);
  const w = s.el, t = $('#in-t', w);
  if (t) { t.focus({ preventScroll: true }); inGrow(t); }   /* ＋を押した瞬間に入力欄へ */
  const log = $('#in-log', w);
  if (log) { log.scrollTop = log.scrollHeight; inAfterLog(log); }
  return s;
}

/** 開いていれば描き直す(雑記・未確定・履歴が変わった時に呼ぶ。開いていなければ何もしない) */
function inRefresh() {
  const w = inWrap(); if (!w) return;
  $('h2', w).textContent = inTitle();
  $('.sheet-lead', w).innerHTML = inLead();
  $('#in-top', w).innerHTML = inTop();
  const log = $('#in-log', w), y = log.scrollTop, bottom = inAtBottom(log);
  log.classList.toggle('is-sel', IN_SEL != null);
  log.innerHTML = inLogHtml();
  if (IN_STICK || (!IN_KEEP && bottom)) log.scrollTop = log.scrollHeight; else log.scrollTop = y;
  IN_STICK = false; IN_KEEP = false;
  if (IN_SEL) $('#in-aux', w).innerHTML = inSelBar();
  inAfterLog(log);
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
/** 旧 capRefresh の互換: 解釈の部分だけ描き直す */
function capRefresh() { inAux(); }

/* ---------- 枠と状態 ---------- */

function inWrap() { const s = _sheets.find(x => x.o.id === 'inbox'); return s ? s.el : null; }
const inTitle = () => (IN_SEL ? (IN_SEL.size ? `${IN_SEL.size}件を選択` : '雑記を選ぶ') : '入力');
/** ヘッダーの右(選ぶ / 完了) */
const inLead = () => (IN_SEL == null ? btn('選ぶ', 'inToggleSel()', 'ghost', { icon: 'check' }) : btn('完了', 'inToggleSel()', 'sec'));

/** 本体全体(入力欄は一度だけ作る。描き直しは履歴と上の部分だけ) */
function inBody() {
  const top = inTop(), log = inLogHtml(), aux = IN_SEL ? inSelBar() : inAuxHtml();
  return `<div class="in-wrap">
    <div class="in-top" id="in-top">${top}</div>
    <div class="in-log${IN_SEL != null ? ' is-sel' : ''}" id="in-log" role="log" aria-label="履歴">${log}</div>
    <div class="in-dock">
      <div class="in-undo" id="in-undo">${inUndoHtml()}</div>
      <div id="in-aux">${aux}</div>
      ${IN_SEL != null ? '' : inRowHtml()}
    </div></div>`;
}

/** 選ぶ・完了で本体を作り直す(題・本体。入力欄の中身は IN_DRAFT に残っている) */
function inRedrawAll() {
  const w = inWrap(); if (!w) return;
  const log0 = $('#in-log', w), y = log0 ? log0.scrollTop : 0;
  const top = _top();
  if (top && top.el === w) { setSheetTitle(inTitle()); setSheetBody(inBody()); }
  else { $('h2', w).textContent = inTitle(); $('.sheet-b', w).innerHTML = inBody(); }
  $('.sheet-lead', w).innerHTML = inLead();
  const log = $('#in-log', w);
  if (log) { log.scrollTop = y; inAfterLog(log); }
}

function inClosed() {
  inVVBind(false);
  IN_SEL = null; IN_FOCUS = null; IN_NEW = null;
  clearTimeout(IN_UNDO_T); IN_UNDO = null;
  if (IN_MIC_ON) { IN_MIC_ON = false; typeof voiceActive === 'function' && voiceActive() && typeof voiceCancel === 'function' && voiceCancel(); }
}

const inAtBottom = log => log.scrollHeight - log.scrollTop - log.clientHeight < 60;

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

/* ---------- 入力(下) ---------- */

function inRowHtml() {
  return `<div class="in-row">
    <textarea id="in-t" class="in-t" rows="1" aria-label="入力" placeholder="ランチ 980 など" oninput="inInput(this)" onkeydown="inKey(event)">${esc(IN_DRAFT)}</textarea>
    <button class="icon-btn in-mic" id="in-mic" aria-label="音声で入力" aria-pressed="false" onclick="inMic()">${ic('mic')}</button>
    <button class="btn pri in-send" id="in-send" onclick="inSend()" ${IN_DRAFT.trim() ? '' : 'disabled'}><span>送る</span></button>
  </div>`;
}
/** 入力が空: 種類ごとの入口。入力がある: 解釈と「記録する」 */
function inAuxHtml() {
  const c = IN_DRAFT.trim() ? inCand() : null;
  IN_AUX = c ? 'ip' : 'kinds';
  if (!c) return `<div class="in-kinds" role="group" aria-label="種類を選んで入力">${inKinds().map(([i, l, on]) => `<button class="in-k" onclick="${on}">${ic(i)}<span>${esc(l)}</span></button>`).join('')}</div>`;
  const sum = inSum(c), dis = c.type === 'pay' && !c.amt;
  const parts = [sum.ty, ...sum.parts.filter(Boolean)].map(x => `<span>${esc(x)}</span>`).join('');
  return `<div class="in-ip"><span class="in-guess">${parts}</span>${btn('記録する', 'inRecord()', 'sec', { sm: 1, disabled: dis })}</div>${chips(inTypes(c), c.type, 'inForce')}`;
}
function inKinds() {
  return [
    ['yen', '支払い', 'capOpen(&quot;openPayEditor()&quot;)'],
    ['camera', 'レシートを読み取る', 'capOpen(&quot;openReceipt()&quot;)'],
    ['flag', '出来事', `capOpen(${q("openActEditor({state:'done'})")})`],
    ['cal', '予定', `capOpen(${q("openActEditor({state:'plan'})")})`],
    ['task', 'ToDo', 'capOpen(&quot;openTaskEditor()&quot;)'],
    ['memo', 'メモ', `inForce(${q('memo')})`],
    ['people', 'エンティティ', 'capOpen(&quot;openEntEditor()&quot;)'],
    ['pencil', '今日の日記', `capOpen(${q('openDiary(' + q(TODAY) + ')')})`],
  ];
}
function inAux() { const w = inWrap(); if (!w) return; const el = $('#in-aux', w); if (el) el.innerHTML = IN_SEL != null ? inSelBar() : inAuxHtml(); }
function inTypes(c) { const T = [['pay', '支払い'], ['plan', '予定'], ['task', 'ToDo'], ['memo', 'メモ']]; if (c && c.type === 'event') T.splice(1, 0, ['event', '出来事']); return T; }
function inForce(k) { IN_FORCE = k; inAux(); inFocusT(); }
function inFocusT() { const t = $('#in-t', inWrap() || document); t && t.focus({ preventScroll: true }); }

/** 本文を下書きに写す。textarea の値そのものは描き直さない(焦点を失わない) */
function inSetDraft(text) {
  IN_DRAFT = text; inSaveDraft();
  const w = inWrap(); if (!w) return;
  const log = $('#in-log', w), atB = log ? inAtBottom(log) : false;
  const t = $('#in-t', w); if (t && t.value !== text) t.value = text;
  if (t) inGrow(t);
  const send = $('#in-send', w); if (send) send.disabled = !text.trim();
  const mode = text.trim() ? 'ip' : 'kinds';
  if (mode !== IN_AUX || mode === 'ip') inAux();
  if (log && atB) log.scrollTop = log.scrollHeight;
}
function inInput(el) { inSetDraft(el.value); }
function inGrow(t) {
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
function inAppend(t) {
  const s = String(t || '').trim(); if (!s) return;
  const cur = IN_DRAFT.replace(/\s+$/, '');
  inSetDraft(cur ? cur + ' ' + s : s);
  inFocusT(); inMicPaint();
}

/** 送る = 雑記に置く(未確定の記録まで同じ1回の変更で作られる) */
function inSend() {
  const text = IN_DRAFT.trim();
  if (!text) { inFocusT(); return; }
  if (!(typeof jotAdd === 'function')) return;
  const r = jotAdd(text, { src: 'text', convert: true });
  if (!r || !r.ok) { done(r || { ok: false, why: 'できませんでした' }); return; }
  IN_NEW = r.id; IN_STICK = true;
  inSetDraft('');
  inUndoShow(r, text);
  inDone(r, { quiet: 1 });
  inFocusT();
}
function inUndoShow(r, text) {
  clearTimeout(IN_UNDO_T);
  IN_UNDO = { undo: r.undo, text, label: r.label || '雑記に置きました' };
  inUndoPaint();
  IN_UNDO_T = setTimeout(() => { IN_UNDO = null; inUndoPaint(); }, 3000);
}
function inUndoHtml() { return IN_UNDO ? `<div class="in-undo-in"><span>${esc(IN_UNDO.label)}</span>${IN_UNDO.undo ? btn('取り消す', 'inUndo()', 'ghost', { sm: 1 }) : ''}</div>` : ''; }
function inUndoPaint() { const el = $('#in-undo', inWrap() || document); if (el) el.innerHTML = inUndoHtml(); }
function inUndo() {
  const u = IN_UNDO; if (!u) return;
  clearTimeout(IN_UNDO_T); IN_UNDO = null; inUndoPaint();
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
function inMicPaint() { const b = $('#in-mic', inWrap() || document); if (!b) return; const on = !!(typeof voiceActive === 'function' && voiceActive()); b.classList.toggle('is-rec', on); b.setAttribute('aria-pressed', on ? 'true' : 'false'); }

/* ---------- 候補・記録 ---------- */

/** いまの書きかけの解釈(入力が空なら null)。強制の種類があればそれで読み替える */
function inCand() {
  const t = IN_DRAFT.trim(); if (!t) return null;
  if (IN_FORCE && typeof candFromType === 'function') return candFromType(t, IN_FORCE);
  const c = typeof ruleCandidates === 'function' ? (ruleCandidates(t) || [])[0] : null;
  if (c) return c;
  return typeof candFromType === 'function' ? candFromType(t, 'memo') : { type: 'memo', body: t, ents: [] };
}
/** 解釈の札に出す内容(ty と parts) */
function inSum(c) {
  const nm = e => (E(e) || {}).n || '';
  if (c.type === 'pay') return { ty: '支払い', parts: [c.amt ? yen(c.amt) : '金額なし', c.payee ? (c.payee.e ? nm(c.payee.e) : c.payee.name || '') : (c.text || '')] };
  if (c.type === 'event' || c.type === 'plan') return { ty: c.type === 'event' ? '出来事' : '予定', parts: [c.title, `${relDay(c.date)} ${c.start || '終日'}${c.end ? '–' + c.end : ''}`] };
  if (c.type === 'task') return { ty: 'ToDo', parts: [c.t, c.due ? relDay(c.due) : ''] };
  return { ty: 'メモ', parts: [String(c.body || '').slice(0, 30)] };
}
function inRoles(ids) {
  return (ids || []).filter(e => E(e)).map(e => {
    const ty = (E(e).types || [])[0];
    return { r: ty && isUnder(ty, 'place') ? '場所' : ty && isUnder(ty, 'project') ? 'プロジェクト' : '同席', e };
  });
}
/** 候補を確定の記録にする(旧 capSave と同じ。雑記は作らない) */
function inSaveCand(c) {
  const ents = c.ents || [];
  if (c.type === 'pay') return paySave({ date: c.date, start: c.start, payee: c.payee || null, amt: c.amt, exp: { basis: 'items', cat: c.cat || catGuess(c.payee, c.text) } });
  if (c.type === 'event' || c.type === 'plan') return actAdd({ kind: c.kind || 'event', title: c.title, date: c.date, start: c.start, end: c.end, allDay: c.start ? 0 : 1, state: c.type === 'event' ? 'done' : 'plan', roles: inRoles(ents) });
  if (c.type === 'task') return taskAdd({ t: c.t, due: c.due, list: c.list, pri: c.pri, ents });
  if (c.type === 'memo') return memoAdd(c.body, { ents });
  return null;
}
/** 「記録する」: 解釈の種類で確定の記録を作る。雑記は作らない */
function inRecord() {
  const c = inCand(); if (!c || (c.type === 'pay' && !c.amt)) return;
  const r = inSaveCand(c); if (!r) return;
  if (!r.ok) { done(r); return; }
  const href = c.type === 'memo' ? '#/memo' : c.type === 'task' ? `#/task/${r.id}` : `#/a/${r.id}`;
  IN_FORCE = null; IN_STICK = true;
  inSetDraft('');
  inDone(r, { act: { label: '開く', on: () => go(href) } });
  inFocusT();
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
  if (IN_SEL) { const a = document.activeElement; a && a.blur && a.blur(); }
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

/* ---------- キーボードで隠れない(visualViewport) ---------- */

function inVVBind(on) {
  const vv = window.visualViewport; if (!vv) return;
  if (on) { vv.addEventListener('resize', inVV); vv.addEventListener('scroll', inVV); }
  else { vv.removeEventListener('resize', inVV); vv.removeEventListener('scroll', inVV); }
}
/** 表示域が縮んだ(キーボードが出た)時だけ、枠と本体の高さを表示域に合わせる */
function inVV() {
  const w = inWrap(); if (!w) return;
  const vv = window.visualViewport, sh = $('.sheet', w);
  if (vv.height < innerHeight - 120) {
    w.style.top = vv.offsetTop + 'px';
    w.style.height = vv.height + 'px';
    sh.style.height = Math.max(160, vv.height - 40) + 'px';
    sh.style.maxHeight = 'none';
    const t = $('#in-t', w); if (t && document.activeElement === t) t.scrollIntoView({ block: 'nearest' });
  } else { w.style.top = ''; w.style.height = ''; sh.style.height = ''; sh.style.maxHeight = ''; }
}

/* ---------- 下書きの保存 ---------- */
/* inSetDraft / inSend などは上で定義。ここから下は旧 capSave の互換の別名のみ */
