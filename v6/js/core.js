/* =====================================================================
   個人記録 v6 — 共通の仕組み(ルーター・ナビ・シート・トースト・小道具)
   画面は route() で登録し、HTML 文字列を返す。API は v6/API.md。
   ===================================================================== */
'use strict';
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
/** onclick の中に文字列を安全に入れる: onclick="fn(${q(id)})" */
const q = v => esc(JSON.stringify(v == null ? null : v));
const hapt = (ms = 8) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) { } };
const isWide = () => innerWidth >= 900, isTwoPane = () => innerWidth >= 1180;
const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

/* ---------- テーマ ---------- */
const THEME_KEY = 'prmock-v6-theme';
const getTheme = () => { try { return localStorage.getItem(THEME_KEY) || 'auto'; } catch (e) { return 'auto'; } };
function applyTheme() {
  const t = getTheme(), dark = t === 'dark' || (t === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  const m = $('meta[name=theme-color]'); if (m) m.content = dark ? '#0E1525' : '#EDF1F8';
}
function setTheme(t) { try { localStorage.setItem(THEME_KEY, t); } catch (e) { } applyTheme(); }
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);

/* ---------- ルーター ---------- */
const ROUTES = {};
/** 画面を登録。fn(id, q, rest) → HTML。o.tab = ナビのタブ、o.root = タブの最上位(一覧) */
function route(name, fn, o = {}) { ROUTES[name] = { fn, ...o }; }
function parseHash(h = location.hash) {
  const [path, qs = ''] = h.replace(/^#\/?/, '').split('?');
  const seg = path.split('/').filter(Boolean).map(decodeURIComponent);
  return { name: ROUTES[seg[0]] ? seg[0] : 'home', id: seg[1] || '', rest: seg.slice(2), q: Object.fromEntries(new URLSearchParams(qs)) };
}
let _after = [], _rootHash = '#/home', _curTab = 'home', _depth = 0, _lastHash = '', _back = false;
const _scroll = new Map();
/** 描いた直後に1回だけ呼ぶ処理 */
const after = fn => _after.push(fn);
const currentTab = () => _curTab;
const scroller = el => (isWide() ? (el || $('#main')) : document.scrollingElement);
function go(hash, o = {}) { if (location.hash === hash) return rerender(); if (o.replace) { history.replaceState(null, '', hash); _stack[Math.max(0, _stack.length - 1)] = hash; render(); } else location.hash = hash; }
function goBack() { if (_depth > 0) history.back(); else go(_rootHash || '#/home', { replace: true }); }
function _draw(el, r) {
  const rt = ROUTES[r.name];
  let html; try { html = rt.fn(r.id, r.q, r.rest); } catch (e) { console.error(e); html = `<div class="screen"><div class="empty"><p>表示できませんでした</p><button class="btn sec" onclick="go('#/home')">ホームへ</button></div></div>`; }
  el.innerHTML = html;
}
let _isKeep = false;
/** いまの描画は「描き直し」か(位置合わせは初回だけにする時に使う) */
const isRerender = () => _isKeep;
function render(keep) {
  _isKeep = !!keep;
  const r = parseHash(), rt = ROUTES[r.name], hash = location.hash || '#/home';
  const main = $('#main'), side = $('#side');
  const sy = keep ? scroller().scrollTop : 0, sy2 = keep && side ? side.scrollTop : 0;
  if (!keep && _lastHash) _scroll.set(_lastHash, isTwoPane() && !ROUTES[parseHash(_lastHash).name].root ? side.scrollTop : scroller().scrollTop);
  if (rt.root) { _rootHash = hash; _curTab = rt.tab || _curTab; }
  else if (rt.tab && !ROUTES[parseHash(_rootHash).name]) _curTab = rt.tab;
  _after = [];
  document.body.dataset.route = r.name;
  if (isTwoPane()) {
    document.body.classList.add('two');
    if (rt.root || keep || !main.firstChild) _draw(main, rt.root ? r : parseHash(_rootHash));
    if (rt.root) side.innerHTML = `<div class="side-empty" aria-hidden="true"><span></span><span></span><span></span></div>`; else _draw(side, r);
    $$('#main a[href]').forEach(a => a.classList.toggle('is-open', a.getAttribute('href') === hash));
  } else { document.body.classList.remove('two'); side.innerHTML = ''; _draw(main, r); }
  if (!keep) {
    const tgt = isTwoPane() && !rt.root ? side : scroller();
    tgt.scrollTop = _back ? (_scroll.get(hash) || 0) : 0;
    if (!reduced()) { const el = isTwoPane() && !rt.root ? side : main; el.classList.remove('enter', 'enter-back'); void el.offsetWidth; el.classList.add(_back ? 'enter-back' : 'enter'); }
  } else { scroller().scrollTop = sy; if (side) side.scrollTop = sy2; }
  _lastHash = hash; _back = false;
  renderNav(); wireBars();
  const fns = _after; _after = []; fns.forEach(f => { try { f(); } catch (e) { console.error(e); } });
  try { if (typeof inRefresh === 'function') inRefresh(); } catch (e) { console.error(e); }
}
/** 同じ画面を、スクロール位置を保って描き直す */
const rerender = () => render(true);
const _stack = [];
addEventListener('hashchange', () => {
  const h = location.hash || '#/home';
  if (_stack.length > 1 && _stack[_stack.length - 2] === h) { _stack.pop(); _back = true; } else _stack.push(h);
  _depth = _stack.length - 1;
  for (let i = _sheets.length - 1; i >= 0; i--) { if (_sheets[i].o.dirty && _sheets[i].o.dirty()) break; closeSheet(true); }
  render();
});
let _rsz; addEventListener('resize', () => { clearTimeout(_rsz); _rsz = setTimeout(() => { if (!sheetOpen()) rerender(); }, 150); });

/* 上部バー: 大きい題が流れ去ったら、細いバーに題を出す */
let _barIO;
function wireBars() {
  if (_barIO) _barIO.disconnect();
  _barIO = new IntersectionObserver(es => es.forEach(e => { const bar = e.target.closest('.screen') && $('.bar', e.target.closest('.screen')); if (bar) bar.classList.toggle('is-stuck', !e.isIntersecting); }), { rootMargin: '-52px 0px 0px 0px' });
  $$('.screen [data-bar-watch]').forEach(el => _barIO.observe(el));
}

/* ---------- ナビ(2枚。1枚目=毎日 / 2枚目=整理・さがす。＋は中央で動かない) ---------- */
const NAV = [
  [{ k: 'home', l: 'ホーム', h: '#/home' }, { k: 'acts', l: 'アクティビティ', h: '#/acts' }, null, { k: 'todo', l: 'ToDo', h: '#/todo' }, { k: 'memo', l: 'メモ', h: '#/memo' }],
  [{ k: 'ents', l: 'エンティティ', h: '#/ents' }, { k: 'ledger', l: '家計簿', h: '#/ledger' }, null, { k: 'search', l: 'さがす', h: '#/search' }, { k: 'settings', l: '設定', h: '#/settings' }],
];
let _deck = 0, _navBadge = {};
const deckOf = tab => NAV.findIndex(d => d.some(i => i && i.k === tab));
/** タブに数字の印を付ける(fn は件数を返す) */
const setNavBadge = (tab, fn) => { _navBadge[tab] = fn; };
function renderNav() {
  const d = deckOf(_curTab); if (d >= 0 && !_navDrag) _deck = d;
  const item = (i, di) => { if (!i) return '<span class="nav-gap"></span>'; const n = _navBadge[i.k] ? _navBadge[i.k]() : 0;
    return `<a class="nav-i${i.k === _curTab ? ' is-on' : ''}" href="${i.h}" data-deck="${di}" ${i.k === _curTab ? 'aria-current="page"' : ''} ${di !== _deck ? 'tabindex="-1" aria-hidden="true"' : ''}>${navIc(i.k, i.k === _curTab)}<span>${i.l}</span>${n ? `<b class="nav-badge">${n}</b>` : ''}</a>`; };
  $('#nav').innerHTML = `<div class="nav-bar" data-deck="${_deck}">${NAV.map((dk, di) => `<div class="nav-deck" data-deck="${di}">${dk.map(i => item(i, di)).join('')}</div>`).join('')}</div>
    <button class="nav-plus" onclick="openCapture()" aria-label="入力${plusN() ? `(未処理 ${plusN()}件)` : ''}">${ic('plus')}${plusN() ? `<b class="nav-badge" aria-hidden="true">${plusN() > 9 ? '9+' : plusN()}</b>` : ''}</button>
    <button class="nav-knob${NAV[1 - _deck].some(i => i && _navBadge[i.k] && _navBadge[i.k]()) ? ' has-badge' : ''}" onclick="navToggle()" aria-label="${_deck ? '毎日のタブへ' : '整理のタブへ'}${NAV[1 - _deck].some(i => i && _navBadge[i.k] && _navBadge[i.k]()) ? '(お知らせあり)' : ''}"><i class="${_deck === 0 ? 'on' : ''}"></i><i class="${_deck === 1 ? 'on' : ''}"></i></button>`;
  $('#rail').innerHTML = `<div class="rail-logo">${ic('sky')}</div>${NAV.flat().filter(Boolean).map(i => `<a class="rail-i${i.k === _curTab ? ' is-on' : ''}" href="${i.h}">${navIc(i.k, i.k === _curTab)}<span>${i.l}</span>${_navBadge[i.k] && _navBadge[i.k]() ? `<b class="nav-badge">${_navBadge[i.k]()}</b>` : ''}</a>`).join('')}<button class="rail-plus" onclick="openCapture()" aria-label="入力${plusN() ? `(未処理 ${plusN()}件)` : ''}">${ic('plus')}<span>入力</span>${plusN() ? `<b class="nav-badge" aria-hidden="true">${plusN() > 9 ? '9+' : plusN()}</b>` : ''}</button>`;
  _navApply(_deck, null, false);
}
/** ＋の印: 未処理 = 雑記のまま + 未確定の記録(m-inbox.js の inboxCount) */
const plusN = () => (typeof inboxCount === 'function' ? inboxCount() : 0);
/* p = 0(1枚目)〜1(2枚目)。指に近い窓から先に動く */
function _navApply(p, fx, anim) {
  const bar = $('.nav-bar'); if (!bar) return; const w = bar.offsetWidth || 1, bx = bar.getBoundingClientRect().left;
  $$('.nav-i', bar).forEach(el => {
    const cx = el.offsetLeft + el.offsetWidth / 2, dist = fx == null ? cx / w : Math.min(1, Math.abs(cx + bx - fx) / w);
    const pi = anim || p === 0 || p === 1 ? p : Math.max(0, Math.min(1, p * 1.6 - dist * .6)), d1 = el.dataset.deck === '1';
    el.style.transition = anim ? `transform .34s var(--spring) ${Math.round(dist * 110)}ms, opacity .2s ${Math.round(dist * 110)}ms` : 'none';
    el.style.transform = `translateY(${d1 ? (1 - pi) * 115 : -pi * 115}%)`;
    el.style.opacity = d1 ? pi : 1 - pi; el.style.pointerEvents = (d1 ? pi : 1 - pi) > .5 ? '' : 'none';
  });
}
function navToggle(to, fx) {
  _deck = to == null ? 1 - _deck : to; hapt();
  _navApply(_deck, fx == null ? null : fx, !reduced());
  $$('.nav-knob i').forEach((i, n) => i.classList.toggle('on', n === _deck));
  $$('.nav-i').forEach(el => { const on = +el.dataset.deck === _deck; el.tabIndex = on ? 0 : -1; el.setAttribute('aria-hidden', !on); });
}
let _navDrag = null;
function navGestures() {
  const nav = $('#nav');
  nav.addEventListener('pointerdown', e => { if (e.target.closest('.nav-plus')) return; _navDrag = { x: e.clientX, y: e.clientY, mode: '', from: _deck }; });
  addEventListener('pointermove', e => {
    const g = _navDrag; if (!g) return; const dx = e.clientX - g.x, dy = e.clientY - g.y;
    if (!g.mode) { if (Math.abs(dy) > 8 && Math.abs(dy) > Math.abs(dx)) g.mode = 'v'; else if (Math.abs(dx) > 14 && Math.abs(dx) > Math.abs(dy) * 1.4) g.mode = 'h'; else return; nav.setPointerCapture && e.pointerId != null && nav.setPointerCapture(e.pointerId); }
    const t = g.mode === 'v' ? -dy / 64 : -dx / 150;            /* 上へ・左へ = 2枚目へ */
    g.p = Math.max(0, Math.min(1, g.from + (g.from === 0 ? Math.max(0, t) : Math.min(0, t))));
    _navApply(g.p, e.clientX, false); g.fx = e.clientX; g.moved = true;
  });
  const end = () => { const g = _navDrag; _navDrag = null; if (!g || !g.moved) return; const to = Math.abs(g.p - g.from) > .3 ? 1 - g.from : g.from; navToggle(to, g.fx); g.block = true; setTimeout(() => { _navBlock = false; }, 60); _navBlock = true; };
  addEventListener('pointerup', end); addEventListener('pointercancel', end);
  nav.addEventListener('click', e => { if (_navBlock) { e.preventDefault(); e.stopPropagation(); } }, true);
}
let _navBlock = false;

/* ---------- シート(下から出る面。重ねられる) ---------- */
const _sheets = [];
const sheetOpen = () => _sheets.length > 0;
/** o = {title, body, primary:{label, on, disabled}, secondary:{label, on}, dirty:()=>bool, onClose, tall, id, lead(HTML: 題の右)} */
function openSheet(o) {
  const el = document.createElement('div'); el.className = 'sheet-wrap'; el.dataset.id = o.id || '';
  el.innerHTML = `<div class="scrim"></div><section class="sheet${o.tall ? ' tall' : ''}" role="dialog" aria-modal="true" aria-label="${esc(o.title || '')}">
    <header class="sheet-h"><i class="sheet-grab"></i><button class="icon-btn" data-close aria-label="閉じる">${ic('close')}</button><h2>${esc(o.title || '')}</h2><div class="sheet-lead">${o.lead || ''}</div></header>
    <div class="sheet-b"></div><footer class="sheet-f"></footer><div class="sheet-ask" hidden><p>入力を破棄しますか?</p><button class="btn sec" data-keep>続ける</button><button class="btn pri" data-drop>破棄する</button></div></section>`;
  $('#sheets').appendChild(el);
  const s = { el, o, body: $('.sheet-b', el) }; _sheets.push(s);
  s.body.innerHTML = o.body || ''; _sheetFoot(s);
  $('.scrim', el).onclick = () => closeSheet(); $('[data-close]', el).onclick = () => closeSheet();
  $('[data-keep]', el).onclick = () => { $('.sheet-ask', el).hidden = true; };
  $('[data-drop]', el).onclick = () => closeSheet(true);
  _sheetDrag(s);
  document.body.classList.add('has-sheet');
  requestAnimationFrame(() => el.classList.add('is-open'));
  const f = $('[autofocus]', el); if (f) setTimeout(() => f.focus({ preventScroll: true }), 260);
  return s;
}
function _sheetFoot(s) {
  const o = s.o, f = $('.sheet-f', s.el);
  f.innerHTML = (o.secondary ? `<button class="btn sec" data-sec>${esc(o.secondary.label)}</button>` : '') + (o.primary ? `<button class="btn pri wide" data-pri ${o.primary.disabled ? 'disabled' : ''}>${esc(o.primary.label)}</button>` : '');
  f.hidden = !o.primary && !o.secondary;
  if (o.primary) $('[data-pri]', f).onclick = () => o.primary.on();
  if (o.secondary) $('[data-sec]', f).onclick = () => o.secondary.on();
}
const _top = () => _sheets[_sheets.length - 1];
/** いちばん上のシートの中身を差し替える(スクロール位置を保つ) */
function setSheetBody(html) { const s = _top(); if (!s) return; const y = s.body.scrollTop; s.body.innerHTML = html; s.body.scrollTop = y; }
function setSheetPrimary(p) { const s = _top(); if (!s) return; s.o.primary = p; _sheetFoot(s); }
function setSheetTitle(t) { const s = _top(); if (s) $('h2', s.el).textContent = t; }
function closeSheet(force) {
  const s = _top(); if (!s) return;
  if (!force && s.o.dirty && s.o.dirty()) { const a = $('.sheet-ask', s.el); a.hidden = false; $('[data-keep]', a).focus(); return; }
  _sheets.pop(); s.el.classList.remove('is-open'); if (!_sheets.length) document.body.classList.remove('has-sheet');
  setTimeout(() => s.el.remove(), 260); if (s.o.onClose) s.o.onClose();
}
const closeAllSheets = () => { while (_sheets.length) closeSheet(true); };
function _sheetDrag(s) {
  const sh = $('.sheet', s.el), h = $('.sheet-h', s.el); let g = null;
  h.addEventListener('pointerdown', e => { if (e.target.closest('button,a,input')) return; g = { y: e.clientY, t: Date.now() }; h.setPointerCapture(e.pointerId); sh.style.transition = 'none'; });
  h.addEventListener('pointermove', e => { if (!g) return; g.d = Math.max(0, e.clientY - g.y); sh.style.transform = `translateY(${g.d}px)`; });
  const end = () => { if (!g) return; const v = (g.d || 0) / Math.max(1, Date.now() - g.t); sh.style.transition = ''; sh.style.transform = ''; if ((g.d || 0) > 110 || v > .7) closeSheet(); g = null; };
  h.addEventListener('pointerup', end); h.addEventListener('pointercancel', end);
}
addEventListener('keydown', e => { if (e.key === 'Escape' && sheetOpen()) closeSheet(); });

/** 取り消せない操作だけの確認。o = {title, body(HTML), ok, on} */
function confirmSheet(o) { openSheet({ title: o.title, body: `<div class="confirm-b">${o.body || ''}</div>`, primary: { label: o.ok || '実行する', on: () => { closeSheet(true); o.on(); } }, secondary: { label: 'やめる', on: () => closeSheet(true) } }); }
/** 操作の一覧。items = [{label, icon, on, sub}] */
function menu(title, items) {
  window._menu = items.filter(Boolean);
  openSheet({ title, body: `<div class="rows">${window._menu.map((m, i) => `<button class="row" onclick="_menuRun(${i})"><span class="row-lead">${ic(m.icon || 'chev')}</span><span class="row-m"><span class="row-t">${esc(m.label)}</span>${m.sub ? `<span class="row-s">${esc(m.sub)}</span>` : ''}</span></button>`).join('')}</div>` });
}
function _menuRun(i) { const m = window._menu[i]; closeSheet(true); m.on(); }
/** 選ぶシート。o = {title, items:[{k, label, sub, lead(HTML), depth}], cur(k か配列), multi, on(k か配列), search, create:{label, on(text)}, none:{label}} */
function pick(o) {
  const st = { cur: o.multi ? [...(o.cur || [])] : o.cur, q: '' }; window._pick = { o, st };
  const list = () => { const ws = st.q.toLowerCase(); const its = o.items.filter(i => !ws || (i.label + ' ' + (i.sub || '')).toLowerCase().includes(ws));
    return `${o.none ? `<button class="row pick-i" role="option" aria-selected="${o.multi ? !st.cur.length : st.cur == null}" onclick="_pickTap(null)"><span class="row-m"><span class="row-t">${esc(o.none.label)}</span></span><span class="pick-mark">${ic('check')}</span></button>` : ''}${its.map(i => { const on = o.multi ? st.cur.includes(i.k) : st.cur === i.k;
      return `<button class="row pick-i" role="option" aria-selected="${on}" style="--depth:${i.depth || 0}" onclick="_pickTap(${q(i.k)})">${i.lead ? `<span class="row-lead">${i.lead}</span>` : ''}<span class="row-m"><span class="row-t">${esc(i.label)}</span>${i.sub ? `<span class="row-s">${esc(i.sub)}</span>` : ''}</span><span class="pick-mark">${ic('check')}</span></button>`; }).join('')}${!its.length && !o.create ? `<p class="pick-none">見つかりません</p>` : ''}${o.create && st.q.trim() && !o.items.some(i => i.label === st.q.trim()) ? `<button class="row pick-new" onclick="_pickNew()"><span class="row-lead">${ic('plus')}</span><span class="row-m"><span class="row-t">「${esc(st.q.trim())}」${esc(o.create.label)}</span></span></button>` : ''}`; };
  st.list = list;
  openSheet({ title: o.title, tall: o.items.length > 7 || o.search, body: `${o.search || o.create ? `<div class="pick-q"><input class="input" type="search" placeholder="${esc(o.search === true || !o.search ? (o.create ? 'さがす・新しく入れる' : 'さがす') : o.search)}" oninput="_pickQ(this.value)" aria-label="さがす"></div>` : ''}<div class="rows pick-list" role="listbox">${list()}</div>`,
    primary: o.multi ? { label: '決定', on: () => { closeSheet(true); o.on(st.cur); } } : null });
}
function _pickQ(v) { const p = window._pick; p.st.q = v; $('.pick-list', _top().el).innerHTML = p.st.list(); }
function _pickTap(k) { const { o, st } = window._pick; if (o.multi) { if (k == null) st.cur = []; else st.cur = st.cur.includes(k) ? st.cur.filter(x => x !== k) : [...st.cur, k]; $('.pick-list', _top().el).innerHTML = st.list(); } else { closeSheet(true); o.on(k); } }
function _pickNew() { const { o, st } = window._pick; const t = st.q.trim(); closeSheet(true); o.create.on(t); }

/* ---------- トースト(結果と「取り消す」。前の取り消しを消さない) ---------- */
const _toastQ = [];
function toast(msg, o = {}) {
  if ($('#toasts').children.length >= 3) { _toastQ.push([msg, o]); return; }
  const el = document.createElement('div'); el.className = 'toast' + (o.bad ? ' bad' : ''); el.setAttribute('role', 'status');
  el.innerHTML = `<span class="toast-ic">${ic(o.bad ? 'warn' : 'check')}</span><span class="toast-m">${esc(msg)}</span>${o.act ? `<button class="toast-u" data-act>${esc(o.act.label)}</button>` : ''}${o.undo ? `<button class="toast-u" data-undo>取り消す</button>` : ''}`;
  let dead = false;
  const kill = () => { if (dead) return; dead = true; el.classList.remove('in'); setTimeout(() => { el.remove(); const n = _toastQ.shift(); if (n) toast(n[0], n[1]); }, 220); };
  if (o.undo) $('[data-undo]', el).onclick = () => { o.undo(); kill(); rerender(); toast('取り消しました'); };
  if (o.act) $('[data-act]', el).onclick = () => { kill(); o.act.on(); };
  $('#toasts').appendChild(el); requestAnimationFrame(() => el.classList.add('in'));
  setTimeout(kill, o.undo || o.act ? 6000 : o.bad ? 3600 : 2400);
}
/** 変更の結果を受ける定型: 失敗は理由を出す。成功はトースト(取り消し付き)と描き直し。
    o = {msg, close(シートを閉じる), go(移動先), quiet, noUndo} → 成功なら true */
function done(res, o = {}) {
  if (!res || !res.ok) { toast((res && res.why) || 'できませんでした', { bad: 1 }); hapt(30); return false; }
  if (o.close) closeSheet(true);
  if (o.go) { if (location.hash === o.go) rerender(); else go(o.go, { replace: o.replace }); } else rerender();
  const msg = o.msg || res.label; if (msg && !o.quiet) toast(msg, { undo: o.noUndo ? null : res.undo, act: o.act });
  if (res.warn) toast(res.warn, { bad: 1 });
  hapt(); return true;
}

/* ---------- 入力の小道具 ---------- */
const val = (id, root) => { const el = (root || document).querySelector('#' + id); return el ? (el.type === 'checkbox' ? el.checked : el.value) : ''; };
/** ファイルを書き出す */
function downloadText(name, text, type = 'application/json') { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type })); a.download = name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500); }
/** 横スワイプで行の操作を出す(ほのめかし付き)。.swipe > .swipe-in + .swipe-act */
function wireSwipe(root = document) {
  $$('.swipe', root).forEach(row => {
    if (row._w) return; row._w = 1; const inn = $('.swipe-in', row); let g = null;
    row.addEventListener('pointerdown', e => { if (e.pointerType === 'mouse') return; g = { x: e.clientX, y: e.clientY, on: false }; });
    row.addEventListener('pointermove', e => { if (!g) return; const dx = e.clientX - g.x, dy = e.clientY - g.y; if (!g.on) { if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy) * 1.5) { g.on = true; inn.style.transition = 'none'; } else if (Math.abs(dy) > 12) { g = null; return; } else return; } g.d = Math.max(-120, Math.min(0, dx)); inn.style.transform = `translateX(${g.d}px)`; row.classList.toggle('armed', g.d < -72); });
    const end = () => { if (!g) return; inn.style.transition = ''; inn.style.transform = ''; const fire = g.on && g.d < -72; if (g.on) { row._block = true; setTimeout(() => row._block = false, 80); } g = null; row.classList.remove('armed'); if (fire) { const b = $('.swipe-act', row); b && b.click(); } };
    row.addEventListener('pointerup', end); row.addEventListener('pointercancel', end);
    row.addEventListener('click', e => { if (row._block && !e.target.closest('.swipe-act')) { e.preventDefault(); e.stopPropagation(); } }, true);
  });
}

/* ---------- 起動 ---------- */
function boot() {
  applyTheme(); load(); navGestures();
  if (!location.hash) history.replaceState(null, '', '#/home');
  _stack.push(location.hash);
  render();
  setInterval(() => { const t = NOW, d = TODAY; clockRead(); if ((t !== NOW || d !== TODAY) && !sheetOpen() && !(document.activeElement && /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName))) rerender(); }, 20000);
  document.documentElement.classList.add('ready');
}
