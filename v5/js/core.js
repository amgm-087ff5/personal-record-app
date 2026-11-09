/* =====================================================================
   個人記録 UI v5 — 共通の仕組み(core)
   保存・ルーター・ナビ・シート・トースト・作成の一覧・小道具。
   画面担当は DESIGN.md 2.1 の名前だけを使う。読み込みは index.html の loader が行い、最後に boot() を呼ぶ。
   data.js で定義済みの名前(TODAY・yen・$・md・mdw など)はここで定義しない。
   ===================================================================== */

const NOW = '13:20';            /* モックの「いま」(TODAY と同じ日) */
const EXTRA = {};               /* 画面担当が自由に使う保存領域 */
const SKEY = 'prmock-v5';
const THEME_KEY = 'prmock-theme';

/* ---------- 文字・日付・小道具 ---------- */
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid = p => p + Date.now().toString(36) + Math.random().toString(36).slice(2, 5);
const WD = '日月火水木金土';
const dowJ = d => WD[new Date(d + 'T00:00').getDay()];
const fmtMD = d => { const [, m, dd] = d.split('-'); return `${+m}/${+dd}`; };
const fmtMDW = d => { const [, m, dd] = d.split('-'); return `${+m}月${+dd}日 ${dowJ(d)}曜`; };
const dayDiff = d => Math.round((new Date(d + 'T00:00') - new Date(TODAY + 'T00:00')) / 864e5);
const relDay = d => {
  const n = dayDiff(d);
  if (n === 0) return '今日';
  if (n === -1) return '昨日';
  if (n === 1) return '明日';
  if (n === 2) return '明後日';
  if (n > 0) return `${n}日後`;
  const p = -n;
  if (p < 7) return `${p}日前`;
  if (p < 35) return `${Math.round(p / 7)}週間前`;
  return `${Math.round(p / 30)}か月前`;
};
/** 'HH:MM' の a から b までの分(b − a) */
const minsBetween = (a, b) => { const t = s => { const [h, m] = String(s).split(':').map(Number); return h * 60 + m }; return t(b) - t(a) };
const hapt = () => { try { navigator.vibrate && navigator.vibrate(6) } catch (e) {} };
const currentTab = () => TAB;

/* ---------- 保存 ---------- */
const storeObj = () => ({A, X, E, PLN, TYPES, CATS, NOTES, MEMOS, LISTS, TYPE_PROPS, EXTRA});
function save() {
  try {
    if (typeof plSettle === 'function') plSettle();
    localStorage.setItem(SKEY, JSON.stringify(storeObj()));
  } catch (e) { console.error(e); toast('保存できませんでした') }
}
/** 保存があるキーだけ中身を入れ替える(オブジェクトは作り直さない) */
function load() {
  let s = null;
  try { s = JSON.parse(localStorage.getItem(SKEY) || 'null') } catch (e) { console.error(e); return false }
  if (!s) return false;
  try { if (typeof dataMigrate === 'function') s = dataMigrate(s) || s } catch (e) { console.error(e) }
  const objs = storeObj();
  for (const k of Object.keys(objs)) {
    if (!s[k]) continue;
    const o = objs[k];
    for (const kk of Object.keys(o)) delete o[kk];
    Object.assign(o, s[k]);
  }
  try {
    if (typeof plSettle === 'function') plSettle();
    if (typeof moneyMigrate === 'function') moneyMigrate();
    if (typeof moneyInstall === 'function') moneyInstall();
  } catch (e) { console.error(e) }
  return true;
}
function resetData() {
  try { localStorage.removeItem(SKEY) } catch (e) {}
  location.hash = '#/home';
  location.reload();
}
/** 全データを JSON ファイルで保存先へ。ファイル名を返す */
function exportData() {
  const d = new Date(), p = n => String(n).padStart(2, '0');
  const name = `personal-record-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}.json`;
  const blob = new Blob([JSON.stringify({app: 'personal-record', version: 'v5', exported: d.toISOString(), data: storeObj()}, null, 2)], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return name;
}
/** 書き出したJSONを読み込む(全データを置き換える)。確認は呼び出し側(設定)が confirmIn で行う */
function importData(file) {
  return file.text().then(t => {
    let j;
    try { j = JSON.parse(t) } catch (e) { throw new Error('JSONとして読めません') }
    const d = j && j.data && typeof j.data === 'object' ? j.data : j;
    const keys = ['A','X','E','PLN','TYPES','CATS','NOTES','MEMOS','LISTS','TYPE_PROPS','EXTRA'];
    if (!d || typeof d !== 'object' || !keys.some(k => k in d)) throw new Error('このファイルは読み込めません');
    const full = {};
    keys.forEach(k => { full[k] = d[k] || {} });
    localStorage.setItem(SKEY, JSON.stringify(full));
    load();
    return true;
  });
}

/* ---------- ルーター ---------- */
const ROUTES = {};
const route = (name, fn, opt = {}) => { ROUTES[name] = {fn, ...opt} };
const TAB_ROOT = {home: 'home', activities: 'activities', todo: 'todo', memo: 'memo', entities: 'entities', ledger: 'ledger', settings: 'settings'};
let CUR = {name: 'home', id: null, q: {}};
let TAB = 'home';   /* 今のタブ。詳細はタブを変えない */
let DEPTH = 0;      /* アプリ内で何回移ったか(戻る判定) */
let AFTER = [];
const rootHash = t => '#/' + (TAB_ROOT[t] || 'home');
const parseHash = () => {
  const h = location.hash.replace(/^#\/?/, '') || 'home';
  const [path, qs] = h.split('?');
  const [name, ...rest] = path.split('/');
  return {name, id: rest.join('/') || null, q: Object.fromEntries(new URLSearchParams(qs || ''))};
};
const go = h => { if (location.hash === h) render(true); else location.hash = h };
const goBack = () => { if (DEPTH > 0) history.back(); else go(rootHash(TAB)) };
const after = fn => { AFTER.push(fn) };
const rerender = () => render(true);
const safeRun = (fn, id, q) => {
  try { return fn(id, q) || '' }
  catch (e) {
    console.error(e);
    return `<div class="err" role="alert"><b>表示できません</b><small>${esc(e.message)}</small></div>`;
  }
};
function render(keepScroll) {
  CUR = parseHash();
  const r = ROUTES[CUR.name] || ROUTES.activities;
  if (r.tab) TAB = r.tab;
  const app = document.getElementById('app');
  const y = scrollY;
  const keeps = [...app.querySelectorAll('[data-keep]')].map(e => [e.dataset.keep, e.scrollTop]);
  const rootName = TAB_ROOT[TAB] || 'home';
  const root = ROUTES[rootName] || {fn: () => ''};
  const sameRoot = app.dataset.root === rootName;
  const prevLeft = app.querySelector('.pane-l')?.scrollTop || 0;
  if (WIDE2.matches) {
    const isRoot = CUR.name === rootName;
    const left = safeRun(root.fn, null, isRoot ? CUR.q : {});
    const right = isRoot ? '' : safeRun(r.fn, CUR.id, CUR.q);
    const solo = !right && rootName !== 'activities';
    app.classList.toggle('solo', solo);
    app.innerHTML = `<section class="pane-l">${left}</section>${solo ? '' : `<main class="pane-r screen" id="screen">${right}</main>`}`;
    app.dataset.root = rootName;
    if (sameRoot) app.querySelector('.pane-l').scrollTop = prevLeft;
    [...app.querySelectorAll('.pane-l a')].filter(a => a.getAttribute('href') === location.hash).forEach(a => a.classList.add('sel'));
  } else {
    app.classList.remove('solo');
    delete app.dataset.root;
    app.innerHTML = `<main class="screen" id="screen">${safeRun(r.fn, CUR.id, CUR.q)}</main>`;
  }
  renderNav(TAB);
  scrollTo(0, keepScroll ? y : 0);
  keeps.forEach(([k, t]) => { const e = app.querySelector(`[data-keep="${k}"]`); if (e) e.scrollTop = t });
  const fns = AFTER; AFTER = [];
  fns.forEach(f => { try { f() } catch (e) { console.error(e) } });
  document.dispatchEvent(new Event('rendered'));
  const h = app.querySelector('h1, .appbar .title');
  document.title = ((h && h.textContent) || '個人記録') + ' — 個人記録';
}
const WIDE2 = matchMedia('(min-width:1100px)');
const WIDE_NAV = matchMedia('(min-width:900px)');
const WIDE_SHEET = matchMedia('(min-width:900px)');
const REDUCED = () => { try { return matchMedia('(prefers-reduced-motion: reduce)').matches } catch (e) { return false } };

/* ---------- ナビ ----------
   1枚目(毎日): ホーム・アクティビティ・＋・ToDo・メモ / 2枚目(整理): エンティティ・家計簿・＋・設定・(空き)
   狭い幅: 窓4つ(帯=1枚目と2枚目を縦に積んだもの)。指に近い窓ほど先に動く。幅900以上は左のレール(7つ)。 */
const NAV_IC = {
  home: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path fill-rule="evenodd" d="M12 2.8 2.9 10.1a1 1 0 0 0-.4.8v8.9a1.7 1.7 0 0 0 1.7 1.7h15.6a1.7 1.7 0 0 0 1.7-1.7v-8.9a1 1 0 0 0-.4-.8Z M9.8 21.5v-5a1.2 1.2 0 0 1 1.2-1.2h2a1.2 1.2 0 0 1 1.2 1.2v5Z"/></svg>',
  activities: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><rect x="3" y="2" width="2.6" height="20" rx="1.3"/><rect x="8" y="3.5" width="13" height="6" rx="3"/><rect x="8" y="13" width="8.5" height="6" rx="3"/><circle cx="4.3" cy="19" r="2.4"/></svg>',
  memo: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path fill-rule="evenodd" d="M6 2.5h8.3l5.2 5.2V20a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 20V4A1.5 1.5 0 0 1 6 2.5Z M14.3 2.5v5.2h5.2Z M8.2 11.2h7.6v1.8H8.2Z M8.2 15h4.6v1.8H8.2Z"/></svg>',
  todo: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path fill-rule="evenodd" d="M8 3h8a5 5 0 0 1 5 5v8a5 5 0 0 1-5 5H8a5 5 0 0 1-5-5V8a5 5 0 0 1 5-5Z M6.6 12.4l1.9-1.9 1.8 1.8 5.1-5.1 1.9 1.9-7 7Z"/></svg>',
  entities: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><circle cx="8.5" cy="7.5" r="4"/><path d="M1.5 21.5a7 7 0 0 1 14 0Z"/><path fill-rule="evenodd" d="M18 2.8a4.7 4.7 0 0 1 4.7 4.7c0 3.3-4.7 9.2-4.7 9.2s-4.7-5.9-4.7-9.2A4.7 4.7 0 0 1 18 2.8Zm0 3.1a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2Z"/></svg>',
  ledger: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><rect x="3" y="11" width="4.2" height="9.5" rx="1.2"/><rect x="9.9" y="4" width="4.2" height="16.5" rx="1.2"/><rect x="16.8" y="8" width="4.2" height="12.5" rx="1.2"/><rect x="2" y="20.6" width="20" height="1.6" rx=".8"/></svg>',
  settings: '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path fill-rule="evenodd" d="M19.6 12 22.1 13.44 21.65 15.3 18.77 15.45 18.15 16.47 17.37 17.37 18.12 20.16 16.49 21.16 14.35 19.23 13.19 19.51 12 19.6 10.56 22.1 8.7 21.65 8.55 18.77 7.53 18.15 6.63 17.37 3.84 18.12 2.84 16.49 4.77 14.35 4.49 13.19 4.4 12 1.9 10.56 2.35 8.7 5.23 8.55 5.85 7.53 6.63 6.63 5.88 3.84 7.51 2.84 9.65 4.77 10.81 4.49 12 4.4 13.44 1.9 15.3 2.35 15.45 5.23 16.47 5.85 17.37 6.63 20.16 5.88 21.16 7.51 19.23 9.65 19.51 10.81ZM8.7 12a3.3 3.3 0 1 0 6.6 0a3.3 3.3 0 1 0-6.6 0Z"/></svg>',
  switch: '<svg class="i" viewBox="0 0 52 30" aria-hidden="true" fill="currentColor"><rect class="r1" x="12.5" y="12" width="14" height="6" rx="3"/><rect class="r2" x="29.5" y="12" width="6" height="6" rx="3"/></svg>',
};
const FAB_PLUS = '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>';
const ICON_X = '<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>';
const NAV_PAGES = [
  [['home', 'ホーム'], ['activities', 'アクティビティ'], 'fab', ['todo', 'ToDo'], ['memo', 'メモ']],
  [['entities', 'エンティティ'], ['ledger', '家計簿'], 'fab', ['settings', '設定'], 'blank'],
];
const NAV_ALL = [['home', 'ホーム'], ['activities', 'アクティビティ'], ['todo', 'ToDo'], ['memo', 'メモ'], ['entities', 'エンティティ'], ['ledger', '家計簿'], ['settings', '設定']];
const NAV_SLOTS = [0, 1, 3, 4];   /* 窓の位置(中央の＋は 2) */
const NAV_BADGE = {};
/** 赤いバッジの件数を返す関数を登録する(例: setNavBadge('todo', () => 期限切れの数)) */
const setNavBadge = (tab, fn) => { NAV_BADGE[tab] = fn };
const badgeOf = k => { const f = NAV_BADGE[k]; return f ? Number(f()) || 0 : 0 };
const navItemHTML = (t, cur, hidden) => {
  if (t === 'blank') return '<span class="nav-blank" aria-hidden="true"></span>';
  const [k, label] = t, n = badgeOf(k);
  return `<a href="#/${TAB_ROOT[k]}" data-tab="${k}"${k === cur ? ' aria-current="page"' : ''}${hidden ? ' inert tabindex="-1" aria-hidden="true"' : ''}><span class="pill">${NAV_IC[k]}</span><span class="lbl">${label}</span>${n ? `<span class="badge">${n}</span>` : ''}</a>`;
};
const fabHTML = () => `<div class="fab-slot"><button class="fab" type="button" aria-label="作成" data-act="create">${FAB_PLUS}</button></div>`;
const navCell = () => '<div class="nav-cell"><div class="nav-strip"></div></div>';
let NAV_MODE = '', NAV_PG = 0, NAV_KEY = null, DOT_P = 0, DOT_RAF = 0, LAND_T = 0, SUPPRESS = false, G = null;
const navEl = () => document.getElementById('nav');
const navStrips = () => [...navEl().querySelectorAll('.nav-strip')];
const navPageOf = t => NAV_PAGES.findIndex(p => p.some(x => Array.isArray(x) && x[0] === t));

function renderNav(tab) {
  const nav = navEl();
  if (!nav) return;
  if (WIDE_NAV.matches) {
    nav.innerHTML = `<div class="nav-in rail">${fabHTML()}${NAV_ALL.map(t => navItemHTML(t, tab, false)).join('')}</div>`;
    NAV_MODE = 'rail';
    return;
  }
  const fresh = NAV_MODE !== 'strip';
  const key = CUR.name + '/' + (CUR.id || '');
  const jump = key !== NAV_KEY;
  NAV_KEY = key;
  if (jump) { const p = navPageOf(tab); if (p >= 0) NAV_PG = p }
  if (fresh) {
    nav.innerHTML = `<div class="nav-in">${navCell()}${navCell()}${fabHTML()}${navCell()}${navCell()}</div><button class="nav-sw" type="button" data-act="switch" aria-label="ナビを切り替え">${NAV_IC.switch}</button>`;
    NAV_MODE = 'strip';
  }
  paintStrips(tab);
  if (fresh || jump) placeStrips(NAV_PG, null, true);
}
function paintStrips(tab) {
  navStrips().forEach((s, k) => {
    const sl = NAV_SLOTS[k];
    s.dataset.pg = NAV_PG;
    s.innerHTML = navItemHTML(NAV_PAGES[0][sl], tab, NAV_PG !== 0) + navItemHTML(NAV_PAGES[1][sl], tab, NAV_PG !== 1);
  });
  const sw = navEl().querySelector('.nav-sw');
  if (sw) {
    const other = NAV_PAGES[NAV_PG ? 0 : 1].some(t => Array.isArray(t) && badgeOf(t[0]) > 0);
    sw.classList.toggle('dot', other);
    sw.setAttribute('aria-label', `ナビを切り替え(${NAV_PG + 1}/2)${other ? '(もう一方の枚目にお知らせ)' : ''}`);
  }
}
/** 帯を pg(0/1)へ置く。delays=窓ごとの遅れ(ms)。instant なら動かさず置く */
function placeStrips(pg, delays, instant) {
  const nav = navEl(), H = nav.clientHeight, tgt = pg ? -H : 0;
  if (instant) nav.classList.add('dr');
  navStrips().forEach((s, k) => {
    s.style.transitionDelay = (delays ? delays[k] : 0) + 'ms';
    s.style.transform = `translate3d(0,${tgt}px,0)`;
  });
  if (instant) { void nav.offsetWidth; nav.classList.remove('dr') }
  navDots(pg, !instant);
}
/* 切替のつまみの点: 今の枚目は幅の広い粒、もう一方は細い粒。p=0(1枚目)〜1(2枚目) */
const navEase = t => { const c = 1.1, u = t - 1; return 1 + (c + 1) * u * u * u + c * u * u };
function paintDots(p) {
  const r1 = navEl().querySelector('.nav-sw .r1'), r2 = navEl().querySelector('.nav-sw .r2');
  if (!r1 || !r2) return;
  const w1 = 6 + 8 * (1 - p), w2 = 6 + 8 * p;
  r1.setAttribute('x', (19.5 - w1 / 2).toFixed(2)); r1.setAttribute('width', w1.toFixed(2));
  r2.setAttribute('x', (32.5 - w2 / 2).toFixed(2)); r2.setAttribute('width', w2.toFixed(2));
  r1.style.opacity = (.35 + .65 * (1 - p)).toFixed(3);
  r2.style.opacity = (.35 + .65 * p).toFixed(3);
}
function navDots(pg, anim) {
  cancelAnimationFrame(DOT_RAF);
  if (!anim || REDUCED()) { DOT_P = pg; paintDots(pg); return }
  const from = DOT_P, t0 = performance.now();
  const step = now => {
    const t = Math.min(1, (now - t0) / 340);
    DOT_P = from + (pg - from) * navEase(t);
    paintDots(DOT_P);
    if (t < 1) DOT_RAF = requestAnimationFrame(step);
  };
  DOT_RAF = requestAnimationFrame(step);
}
function landFx() {
  const nav = navEl();
  nav.classList.add('land');
  clearTimeout(LAND_T);
  LAND_T = setTimeout(() => nav.classList.remove('land'), 600);
}
/** 2枚を切り替える(つまみ・横スワイプ)。stagger=左から 0/25/50/75ms */
function navSwitch(stagger = true) {
  const to = NAV_PG ? 0 : 1;
  hapt();
  NAV_PG = to;
  paintStrips(TAB);
  placeStrips(to, stagger ? navStrips().map((_, k) => k * 25) : null, false);
  landFx();
}
/* ---- 指のドラッグ(縦8px以上で開始。離した時は35%以上 or 0.4px/ms で切替) ---- */
const navDist = (c, fx, W, plus) => {
  let px = Math.abs(c - fx);
  if (plus && ((c < plus.c) !== (fx < plus.c))) px += plus.w;
  return Math.min(1, px / W);
};
function navDown(e) {
  if (WIDE_NAV.matches || e.button > 0 || e.target.closest('.nav-sw')) return;
  G = {id: e.pointerId, x0: e.clientX, y0: e.clientY, drag: false, h: false, moved: false, hist: [[e.clientY, e.timeStamp]]};
}
function startDrag(g, sign) {
  const nav = navEl(), H = nav.clientHeight, ss = navStrips();
  const fab = nav.querySelector('.fab-slot')?.getBoundingClientRect();
  g.drag = true; g.moved = true; g.sign = sign;
  g.base = NAV_PG ? -H : 0; g.tgt = g.base; g.off = g.base; g.fx = g.x0;
  g.ss = ss;
  g.c = ss.map(s => { const b = s.parentNode.getBoundingClientRect(); return b.left + b.width / 2 });
  g.plus = fab ? {c: fab.left + fab.width / 2, w: fab.width} : null;
  g.W = nav.clientWidth || 1;
  g.p = ss.map(() => g.base);
  g.t = performance.now();
  nav.classList.add('dr');
  g.raf = requestAnimationFrame(dragLoop);
}
function dragLoop(now) {
  const g = G;
  if (!g || !g.drag) return;
  const dt = Math.min(50, Math.max(0, now - g.t)); g.t = now;
  g.ss.forEach((s, k) => {
    const d = navDist(g.c[k], g.fx, g.W, g.plus);
    const r = .09 + (.012 - .09) * Math.pow(d, .8);
    g.p[k] += (g.tgt - g.p[k]) * (1 - Math.exp(-r * dt));
    s.style.transform = `translate3d(0,${g.p[k].toFixed(1)}px,0)`;
  });
  g.raf = requestAnimationFrame(dragLoop);
}
function navMove(e) {
  const g = G;
  if (!g || e.pointerId !== g.id) return;
  const dx = e.clientX - g.x0, dy = e.clientY - g.y0;
  if (!g.drag) {
    if (g.h) return;
    if (Math.abs(dx) >= 8 && Math.abs(dx) > Math.abs(dy)) { g.h = true; g.moved = true; return }
    if (Math.abs(dy) < 8 || Math.abs(dy) <= Math.abs(dx)) return;
    startDrag(g, Math.sign(dy));
    try { navEl().setPointerCapture(e.pointerId) } catch (err) {}
  }
  const H = navEl().clientHeight, raw = g.base + dy - g.sign * 8;
  g.off = raw > 0 ? raw * .3 : raw < -H ? -H + (raw + H) * .3 : raw;
  g.tgt = g.off; g.fx = e.clientX;
  g.hist.push([e.clientY, e.timeStamp]);
  while (g.hist.length > 2 && e.timeStamp - g.hist[0][1] > 100) g.hist.shift();
}
function navUp(e, cancel) {
  const g = G;
  if (!g || e.pointerId !== g.id) return;
  G = null;
  if (g.moved) { SUPPRESS = true; setTimeout(() => SUPPRESS = false, 300) }
  if (!g.drag) {
    if (!cancel && g.h && Math.abs(e.clientX - g.x0) >= 30) navSwitch(false);
    return;
  }
  cancelAnimationFrame(g.raf);
  const H = navEl().clientHeight, dist = g.off - g.base;
  const h = g.hist.concat([[e.clientY, e.timeStamp]]);
  const dt = h[h.length - 1][1] - h[0][1];
  const v = dt > 0 ? (h[h.length - 1][0] - h[0][0]) / dt : 0;
  let to = NAV_PG;
  if (!cancel) {
    if (NAV_PG === 0 && dist < 0 && (-dist >= .35 * H || v <= -.4)) to = 1;
    else if (NAV_PG === 1 && dist > 0 && (dist >= .35 * H || v >= .4)) to = 0;
  }
  const changed = to !== NAV_PG;
  if (changed) hapt();
  NAV_PG = to;
  paintStrips(TAB);
  navEl().classList.remove('dr');
  const delays = g.c.map(c => Math.round(navDist(c, e.clientX, g.W, g.plus) * 110));
  placeStrips(to, delays, false);
  if (changed) landFx();
}

/* ---------- シート(重ねられる。下へドラッグで閉じる。入力中は確認) ---------- */
const SHEETS = [];
let SHEET_SEQ = 0, SHEET_DRAG = null;
const topSheet = () => SHEETS[SHEETS.length - 1] || null;
/** o = {title, body(HTML), primary:{label,on,disabled}, secondary:{label,on}, dirty:()=>bool, onClose, full, id}。戻り値 = 内部の項目(el を持つ) */
function openSheet(o = {}) {
  const el = document.createElement('section');
  const id = o.id || ('s' + (++SHEET_SEQ));
  el.className = 'sheet' + (o.full ? ' full' : '');
  el.setAttribute('role', 'dialog');
  el.setAttribute('aria-modal', 'true');
  el.setAttribute('aria-label', o.title || '');
  el.tabIndex = -1;
  el.innerHTML = `<div class="sheet-grab" data-drag aria-hidden="true"></div>`
    + `<header class="sheet-h" data-drag><button class="sheet-x" type="button" aria-label="閉じる" data-act="x">${ICON_X}</button><h2 class="sheet-t">${esc(o.title || '')}</h2><span class="sheet-sp" aria-hidden="true"></span></header>`
    + `<div class="sheet-b">${o.body || ''}</div>`
    + `<footer class="sheet-f">${o.secondary ? `<button class="sheet-s" type="button" data-act="sec">${esc(o.secondary.label)}</button>` : ''}${o.primary ? `<button class="btn block" type="button" data-act="pri"${o.primary.disabled ? ' disabled' : ''}>${esc(o.primary.label)}</button>` : ''}</footer>`
    + `<div class="sheet-ask" hidden></div>`;
  const entry = {id, el, o, pri: o.primary, sec: o.secondary};
  el.addEventListener('click', e => {
    const b = e.target.closest('[data-act]');
    if (!b || b.disabled) return;
    const a = b.dataset.act;
    if (a === 'x') requestClose(entry, false);
    else if (a === 'pri') entry.pri && entry.pri.on && entry.pri.on();
    else if (a === 'sec') (entry.sec && entry.sec.on) ? entry.sec.on() : requestClose(entry, false);
    else if (a === 'stay') hideAsk(entry);
    else if (a === 'discard') removeSheet(entry);
  });
  el.addEventListener('pointerdown', e => sheetDown(e, entry));
  SHEETS.push(entry);
  sheetsHost().appendChild(el);
  restack();
  requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('on')));
  el.focus({preventScroll: true});
  return entry;
}
const sheetsHost = () => document.getElementById('sheets');
function restack() {
  SHEETS.forEach((s, i) => {
    const top = i === SHEETS.length - 1;
    s.el.classList.toggle('under', !top);
    if (top) s.el.removeAttribute('inert'); else s.el.setAttribute('inert', '');
    s.el.setAttribute('aria-hidden', String(!top));
  });
  const open = SHEETS.length > 0;
  document.getElementById('scrim').classList.toggle('on', open);
  document.body.classList.toggle('sheet-open', open);
}
/** 閉じる。dirty() が true なら、シートの中に「入力を破棄しますか?」を出す。force=true は確認なし */
function requestClose(entry, force) {
  if (!SHEETS.includes(entry)) return;
  if (!force && entry.o.dirty && entry.o.dirty() === true) return showAsk(entry);
  removeSheet(entry);
}
function removeSheet(entry) {
  const i = SHEETS.indexOf(entry);
  if (i < 0) return;
  SHEETS.splice(i, 1);
  const el = entry.el;
  el.classList.remove('dragging', 'on');
  el.style.transform = '';
  el.setAttribute('inert', '');
  setTimeout(() => el.remove(), 300);
  restack();
  if (entry.o.onClose) { try { entry.o.onClose() } catch (e) { console.error(e) } }
}
function showAsk(entry) {
  const ask = entry.el.querySelector('.sheet-ask');
  ask.innerHTML = `<div class="ask-b"><p class="ask-t">入力を破棄しますか?</p></div><footer class="sheet-f"><button class="btn block" type="button" data-act="stay">続ける</button><button class="sheet-s" type="button" data-act="discard">破棄する</button></footer>`;
  ask.hidden = false;
  entry.el.classList.add('asking');
  entry.el.style.transform = '';
}
function hideAsk(entry) {
  entry.el.querySelector('.sheet-ask').hidden = true;
  entry.el.classList.remove('asking');
}
/** 上のシートを閉じる。force=true は確認なし(保存したあとなど) */
function closeSheet(force) {
  const t = topSheet();
  if (t) requestClose(t, !!force);
}
/** 開いているか。id を渡すとそのシートが開いているか */
const sheetOpen = id => id ? SHEETS.some(s => s.id === id) : SHEETS.length > 0;
/** 上のシートの中身だけ差し替える(スクロール位置を保つ) */
function setSheetBody(html) {
  const t = topSheet();
  const b = t && t.el.querySelector('.sheet-b');
  if (!b) return;
  const y = b.scrollTop;
  b.innerHTML = html;
  b.scrollTop = y;
}
/** 上のシートの主ボタンを変える(文言・無効)。例: 入力が揃ったら有効にする */
function setSheetPrimary(p) {
  const t = topSheet();
  if (!t || !t.pri) return;
  Object.assign(t.pri, p);
  const b = t.el.querySelector('[data-act="pri"]');
  if (!b) return;
  if (p.label !== undefined) b.textContent = p.label;
  if (p.disabled !== undefined) b.disabled = !!p.disabled;
}
function sheetDown(e, entry) {
  if (e.button > 0 || WIDE_SHEET.matches) return;
  if (e.target.closest('button,input,select,textarea,a')) return;
  if (!e.target.closest('[data-drag]')) return;
  if (entry !== topSheet() || entry.el.classList.contains('asking')) return;
  SHEET_DRAG = {entry, id: e.pointerId, y0: e.clientY, hist: [[e.clientY, e.timeStamp]], dy: 0};
  entry.el.classList.add('dragging');
}
function sheetMove(e) {
  const d = SHEET_DRAG;
  if (!d || e.pointerId !== d.id) return;
  d.dy = Math.max(0, e.clientY - d.y0);
  d.entry.el.style.transform = `translate3d(0,${d.dy}px,0)`;
  d.hist.push([e.clientY, e.timeStamp]);
  while (d.hist.length > 2 && e.timeStamp - d.hist[0][1] > 100) d.hist.shift();
}
function sheetUp(e, cancel) {
  const d = SHEET_DRAG;
  if (!d || e.pointerId !== d.id) return;
  SHEET_DRAG = null;
  const el = d.entry.el;
  el.classList.remove('dragging');
  const h = d.hist.concat([[e.clientY, e.timeStamp]]);
  const dt = h[h.length - 1][1] - h[0][1];
  const v = dt > 0 ? (h[h.length - 1][0] - h[0][0]) / dt : 0;   /* px/ms。下向きが正 */
  const close = !cancel && d.dy > 0 && (d.dy >= .3 * el.offsetHeight || v >= .5);
  if (close) requestClose(d.entry, false);
  if (SHEETS.includes(d.entry)) el.style.transform = '';
}
/** 閉じた時、残っている(入力中の)シートは残す。それ以外を閉じる */
function closeUnsavedFree() {
  [...SHEETS].reverse().forEach(s => { if (!(s.o.dirty && s.o.dirty() === true)) removeSheet(s) });
}

/* ---------- 確認(取り消せない操作だけ。シートの中に出す) ---------- */
/** o = {title, body(HTML), ok:{label}, on}。danger は付けない(赤は いま・遅れ だけ) */
function confirmIn(o) {
  openSheet({
    title: o.title || '',
    body: o.body || '',
    primary: {label: (o.ok && o.ok.label) || '実行', on: () => { closeSheet(true); o.on && o.on() }},
    secondary: {label: 'やめる', on: () => closeSheet(true)},
  });
}

/* ---------- トースト(積み重ねる。取り消し付きは6秒、なしは2.6秒) ---------- */
const TOASTS = [], TOAST_Q = [], TOAST_MAX = 3;
let TOAST_SEQ = 0;
function toast(msg, o = {}) {
  const item = {msg, undo: o.undo || null, id: ++TOAST_SEQ, timer: 0, el: null};
  if (TOASTS.length >= TOAST_MAX) TOAST_Q.push(item);
  else showToast(item);
}
function showToast(item) {
  TOASTS.push(item);
  const el = document.createElement('div');
  el.className = 'toast';
  el.setAttribute('role', 'status');
  el.innerHTML = `<span class="toast-m">${esc(item.msg)}</span>${item.undo ? '<button class="toast-u" type="button">取り消す</button>' : ''}`;
  document.getElementById('toasts').appendChild(el);
  item.el = el;
  requestAnimationFrame(() => el.classList.add('on'));
  item.timer = setTimeout(() => dropToast(item), item.undo ? 6000 : 2600);
  const u = el.querySelector('.toast-u');
  if (u) u.addEventListener('click', () => {
    clearTimeout(item.timer);
    try { item.undo() } catch (e) { console.error(e) }
    save();
    dropToast(item);
    rerender();
  });
}
function dropToast(item) {
  const i = TOASTS.indexOf(item);
  if (i < 0) return;
  TOASTS.splice(i, 1);
  item.el.classList.remove('on');
  setTimeout(() => item.el.remove(), 260);
  if (TOAST_Q.length) showToast(TOAST_Q.shift());
}

/* ---------- 作成の一覧(＋) ---------- */
const CREATES = [];
/** 作成の一覧に登録。{key, label, icon, primaryOn:[tab], run} */
function addCreate(o) {
  const i = CREATES.findIndex(c => c.key === o.key);
  if (i >= 0) CREATES[i] = o; else CREATES.push(o);
}
function openCreate() {
  const tab = TAB;
  const items = [...CREATES].sort((a, b) => (b.primaryOn || []).includes(tab) - (a.primaryOn || []).includes(tab));
  const row = c => `<button class="cr-item${(c.primaryOn || []).includes(tab) ? ' primary' : ''}" type="button" data-cr="${esc(c.key)}">${typeof icon === 'function' ? icon(c.icon) : ''}<span class="cr-t">${esc(c.label)}</span></button>`;
  const entry = openSheet({title: '作成', body: `<div class="cr-list">${items.map(row).join('')}</div>`});
  entry.el.querySelectorAll('[data-cr]').forEach(b => b.addEventListener('click', () => {
    const c = CREATES.find(x => x.key === b.dataset.cr);
    closeSheet(true);
    setTimeout(() => { c && c.run && c.run() }, 120);
  }));
}

/* ---------- テーマ ---------- */
const getTheme = () => { try { return localStorage.getItem(THEME_KEY) || '' } catch (e) { return '' } };
function setTheme(v) {
  if (v) document.documentElement.dataset.theme = v; else delete document.documentElement.dataset.theme;
  try { localStorage.setItem(THEME_KEY, v || '') } catch (e) {}
}

/* ---------- 起動(index.html の loader の後に呼ぶ) ---------- */
function boot() {
  load();
  if (!history.state || typeof history.state.d !== 'number') history.replaceState({d: 0}, '', location.href);
  DEPTH = history.state.d;
  if (!location.hash) history.replaceState({d: 0}, '', '#/home');
  const nav = navEl();
  nav.addEventListener('pointerdown', navDown);
  nav.addEventListener('dragstart', e => e.preventDefault());
  nav.addEventListener('click', e => {
    if (SUPPRESS) { e.preventDefault(); e.stopPropagation(); SUPPRESS = false }
  }, true);
  nav.addEventListener('click', e => {
    if (e.target.closest('[data-act="switch"]')) return navSwitch();
    if (e.target.closest('[data-act="create"]')) return openCreate();
  });
  document.getElementById('scrim').addEventListener('click', () => closeSheet(false));
  WIDE2.addEventListener('change', () => render(true));
  WIDE_NAV.addEventListener('change', () => renderNav(TAB));
  render();
}

/* ---------- 全体のイベント ---------- */
addEventListener('hashchange', () => {
  const d = history.state && typeof history.state.d === 'number' ? history.state.d : null;
  if (d === null) { DEPTH += 1; history.replaceState({d: DEPTH}, '', location.href) } else DEPTH = d;
  closeUnsavedFree();
  render();
});
addEventListener('pointermove', e => { navMove(e); sheetMove(e) });
addEventListener('pointerup', e => { navUp(e, false); sheetUp(e, false) });
addEventListener('pointercancel', e => { navUp(e, true); sheetUp(e, true) });
addEventListener('keydown', e => {
  if (e.key !== 'Escape' || !SHEETS.length) return;
  const t = topSheet();
  if (t.el.classList.contains('asking')) hideAsk(t); else requestClose(t, false);
});
addEventListener('scroll', () => {
  const b = document.querySelector('.appbar');
  if (b) b.classList.toggle('scrolled', scrollY > 4);
}, {passive: true});
