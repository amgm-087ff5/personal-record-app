/* =====================================================================
   個人記録 v6 — 共通部品(HTML 文字列を返す関数)。見た目は css/parts.css。
   画面は同じ意味の部品を自作しない。意味は v6/DESIGN.md 4章。
   ===================================================================== */
'use strict';
/* ---------- アイコン(24格子・線)。.f = 選択中に淡く塗る面 ---------- */
const IC = {
  plus: '<path d="M12 5v14M5 12h14"/>', close: '<path d="M6 6l12 12M18 6L6 18"/>', back: '<path d="M15 5l-7 7 7 7"/>', chev: '<path d="M9 5l7 7-7 7"/>',
  down: '<path d="M5 9l7 7 7-7"/>', up: '<path d="M5 15l7-7 7 7"/>', check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  more: '<circle cx="5" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="19" cy="12" r="1.3" fill="currentColor"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4"/>', trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6"/>',
  pin: '<path class="f" d="M9 3h6l-1 6 4 4H6l4-4-1-6z"/><path d="M12 13v8"/>', search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
  sliders: '<path d="M4 7h9M19 7h1M4 17h1M11 17h9"/><circle class="f" cx="16" cy="7" r="2.6"/><circle class="f" cx="8" cy="17" r="2.6"/>',
  cal: '<rect class="f" x="4" y="5" width="16" height="15" rx="3"/><path d="M4 10h16M8 3v4M16 3v4"/>', clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  place: '<path class="f" d="M12 21s-6.5-5.7-6.5-11a6.5 6.5 0 0113 0c0 5.3-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
  yen: '<path d="M7 5l5 7 5-7M12 12v8M8 13h8M8 16.5h8"/>', camera: '<path class="f" d="M4 8h3.2l1.6-2.5h6.4L16.8 8H20v11H4z"/><circle cx="12" cy="13.2" r="3.3"/>',
  photo: '<rect x="4" y="5" width="16" height="14" rx="3"/><path d="M4 15l4.5-4.5 4 4 2.5-2.5 5 5"/><circle cx="15.5" cy="9.5" r="1.2"/>',
  memo: '<path class="f" d="M6 4h9l4 4v12H6z"/><path d="M15 4v4h4M9 12h6M9 16h4"/>', task: '<rect class="f" x="4.5" y="4.5" width="15" height="15" rx="4.5"/><path d="M8.5 12.3l2.5 2.5 4.8-5.3"/>',
  people: '<circle class="f" cx="9" cy="9" r="3.2"/><path d="M3.5 19c.6-3.2 2.8-5 5.5-5s4.9 1.8 5.5 5"/><circle cx="17" cy="10" r="2.4"/><path d="M16.5 14.2c2.2.2 3.6 1.7 4 4.3"/>',
  user: '<circle cx="12" cy="9" r="3.5"/><path d="M5 20c.8-3.8 3.6-6 7-6s6.2 2.2 7 6"/>', wallet: '<path d="M3 20h18"/><path class="f" d="M5 20v-8h3v8zM10.5 20V5h3v15zM16 20v-6h3v6z"/>',
  home: '<path class="f" d="M4 11l8-7 8 7v9h-5.5v-5.5h-5V20H4z"/>', axis: '<path d="M7 3v18"/><rect class="f" x="11" y="4.5" width="9" height="5" rx="2"/><rect class="f" x="11" y="14" width="6.5" height="5" rx="2"/><path d="M5.5 7h3M5.5 16.5h3"/>',
  sky: '<path d="M4 17h16M7 17a5 5 0 0110 0M12 6.5v2M5.8 9.6l1.4 1.4M18.2 9.6L16.8 11"/>', sun: '<circle cx="12" cy="12" r="3.6"/><path d="M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6L18 18M6 18l1.4-1.4M16.6 7.4L18 6"/>',
  moon: '<path d="M19 14.5A8 8 0 019.5 5a7 7 0 109.5 9.5z"/>', meal: '<path d="M7 3v7a2 2 0 004 0V3M9 12v9M17 3c-2 1.5-3 4-3 7h3v11"/>', talk: '<path d="M4 6h16v10H10l-4 4v-4H4z"/>',
  work: '<path d="M5 6h14v9H5zM3 18h18"/>', move: '<path d="M4 12h15M13 6l6 6-6 6"/>', bag: '<path d="M5 8h14l-1 12H6zM9 8V7a3 3 0 016 0v1"/>',
  spark: '<path d="M12 3l2.2 5.6L20 9.5l-4.4 3.9L17 19.5 12 16.3 7 19.5l1.4-6.1L4 9.5l5.8-.9z"/>', heart: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0112 7.4a4.3 4.3 0 017.5 2.4C19.5 15.4 12 20 12 20z"/>',
  trip: '<rect x="5" y="8" width="14" height="12" rx="2.5"/><path d="M9 8V5h6v3M9 12v4M15 12v4"/>', flag: '<path d="M6 21V4M6 5h11l-2.5 4 2.5 4H6"/>',
  repeat: '<path d="M5 9a7 7 0 0112-3l2 2M19 4v4h-4M19 15a7 7 0 01-12 3l-2-2M5 20v-4h4"/>', bell: '<path d="M6 16v-5a6 6 0 0112 0v5l1.5 2h-15zM10 20a2 2 0 004 0"/>',
  link: '<path d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1"/>', warn: '<path d="M12 4l9 16H3zM12 10v5M12 17.6v.4"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v6M12 7.4v.4"/>', download: '<path d="M12 4v11M7 11l5 5 5-5M5 20h14"/>', upload: '<path d="M12 16V5M7 9l5-5 5 5M5 20h14"/>',
  filter: '<path d="M4 6h16M7 12h10M10 18h4"/>', receipt: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6"/>', swap: '<path d="M7 7h11l-3-3M17 17H6l3 3"/>',
  tag: '<path d="M4 4h7l9 9-7 7-9-9zM8 8v.4"/>', book: '<path d="M5 4h11a3 3 0 013 3v13H8a3 3 0 01-3-3zM5 17a3 3 0 013-3h11"/>',
  tree: '<path d="M6 4v12a2 2 0 002 2h4M6 9h6"/><rect x="12" y="6.5" width="8" height="5" rx="1.6"/><rect x="12" y="15.5" width="8" height="5" rx="1.6"/>',
  theme: '<circle cx="12" cy="12" r="8.5"/><path d="M12 3.5a8.5 8.5 0 000 17z" fill="currentColor"/>', undo: '<path d="M8 5L4 9l4 4M4 9h10a6 6 0 010 12h-3"/>', list: '<path d="M9 6h11M9 12h11M9 18h11M4.5 6h.5M4.5 12h.5M4.5 18h.5"/>',
  grid: '<rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="7" height="7" rx="2"/><rect x="13" y="13" width="7" height="7" rx="2"/>',
  hand: '<path d="M4 13l4-4 4 3 4-4 4 4M4 13v5h16v-5"/>', pencil: '<path d="M4 20h4L19 9l-4-4L4 16v4z"/>', day: '<rect x="4" y="5" width="16" height="15" rx="3"/><path d="M4 10h16M9 14h6"/>',
};
const ic = (n, cls = '') => `<svg class="ic ${cls}" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${IC[n] || IC.flag}</svg>`;
const NAV_IC = { home: 'home', acts: 'axis', todo: 'task', memo: 'memo', ents: 'people', ledger: 'wallet', search: 'search', settings: 'sliders' };
const navIc = k => ic(NAV_IC[k]);
/** 言葉なしのボタン(読み上げ名は必須) */
const iconBtn = (name, label, on, cls = '') => `<button class="icon-btn ${cls}" onclick="${on}" aria-label="${esc(label)}">${ic(name)}</button>`;
/** ボタン。kind = pri(主)| sec(副)| ghost(文字だけ)| dash(足す所)。o = {icon, sm, wide, disabled} */
const btn = (label, on, kind = 'sec', o = {}) => `<button class="btn ${kind}${o.sm ? ' sm' : ''}${o.wide ? ' wide' : ''}" onclick="${on}" ${o.disabled ? 'disabled' : ''}>${o.icon ? ic(o.icon) : ''}<span>${esc(label)}</span></button>`;

/* ---------- 画面の頭 ---------- */
/** タブの最上位の頭: 大きい題 + 右の操作(HTML) */
const head = (title, o = {}) => `<header class="top"><div class="top-m"><h1 data-bar-watch>${esc(title)}</h1>${o.sub ? `<p>${o.sub}</p>` : ''}</div><div class="top-a">${o.actions || ''}</div></header>`;
/** 詳細の上のバー: 戻る + (流れたら出る題) + 右の操作(HTML) */
const bar = (title, o = {}) => `<div class="bar"><button class="icon-btn" onclick="goBack()" aria-label="戻る">${ic('back')}</button><b class="bar-t">${esc(title)}</b><div class="bar-a">${o.actions || ''}</div></div>`;
/** 節の見出し。o = {n(件数), act:{label, on|href}} */
const sec = (title, o = {}) => `<div class="sec"><h2>${esc(title)}</h2>${o.n != null ? `<span class="sec-n num">${o.n}</span>` : ''}${o.act ? (o.act.href ? `<a class="sec-a" href="${o.act.href}">${esc(o.act.label)}</a>` : `<button class="sec-a" onclick="${o.act.on}">${esc(o.act.label)}</button>`) : ''}</div>`;

/* ---------- 基本 ---------- */
/** 金額。¥ は小さく。o = {cls, sign(+/− を付ける)} */
const amt = (n, o = {}) => `<span class="amt ${o.cls || ''}">${o.sign ? (n > 0 ? '+' : n < 0 ? '−' : '±') : ''}<i>¥</i>${Math.abs(Math.round(n || 0)).toLocaleString('ja-JP')}</span>`;
/** 札。kind = owe(貸し借り)| late(遅れ)| plan(予定)| me | kin | read(未確認)| ''(ふつう) */
const tag = (text, kind = '') => `<span class="tag ${kind}">${esc(text)}</span>`;
/** カテゴリ(色の小さい四角 + 名前)。o.path で親からの道 */
const catTag = (k, o = {}) => `<span class="cat"><i style="background:${catColor(k)}"></i>${esc(o.path ? catPath(k) : catName(k))}</span>`;
/** エンティティの印(頭文字の角丸四角)。名前だけ(未登録)は点線 */
const avatar = (id, name, cls = '') => { const e = id && E(id); const n = e ? e.n : (name || ''); return `<i class="av ${e ? '' : 'raw'} ${id === 'me' ? 'me' : ''} ${cls}">${esc((n.split(' ').pop() || '?').slice(0, 1))}</i>`; };
/** エンティティの参照。o = {name(未登録の名前), link(既定 true), short} */
function ent(id, o = {}) {
  const e = id && E(id), n = e ? (o.short ? e.n.split(' ').pop() : e.n) : (o.name || '');
  const inner = `${avatar(id, n)}<span>${esc(n)}</span>`;
  return e && id !== 'me' && o.link !== false ? `<a class="ent" href="#/e/${id}">${inner}</a>` : `<span class="ent ${e ? '' : 'raw'}">${inner}</span>`;
}
/** 役割の相手たち(場所・人)を並べる */
const roleEnts = (a, o = {}) => (o.roleOf && roleIn(a, o.roleOf) ? tag(roleIn(a, o.roleOf), 'kin') : '') + (a.roles || []).filter(r => (r.r !== '支払先' || o.payee) && r.e !== o.roleOf).map(r => ent(r.e, { name: r.name, link: o.link, short: o.short })).join('');
/** 切替(1つ選ぶ)。items = [[k, label]]、fn = 関数名(k を渡す) */
const seg = (items, cur, fn) => `<div class="seg" role="tablist">${items.map(([k, l]) => `<button role="tab" aria-selected="${k === cur}" onclick="${fn}(${q(k)})">${esc(l)}</button>`).join('')}</div>`;
/** 絞り込み・選択のピル。items = [[k, label, 件数?]]、cur = k か配列 */
const chips = (items, cur, fn) => `<div class="chips">${items.map(([k, l, n]) => `<button class="chip" aria-pressed="${Array.isArray(cur) ? cur.includes(k) : k === cur}" onclick="${fn}(${q(k)})">${esc(l)}${n != null ? `<i class="num">${n}</i>` : ''}</button>`).join('')}</div>`;
/** 行。o = {lead(HTML), t, s, tHtml, sHtml, right(HTML), href, on, chev, cls} */
function row(o) {
  const inner = `${o.lead ? `<span class="row-lead">${o.lead}</span>` : ''}<span class="row-m"><span class="row-t">${o.tHtml || esc(o.t || '')}</span>${o.s || o.sHtml ? `<span class="row-s">${o.sHtml || esc(o.s)}</span>` : ''}</span>${o.right ? `<span class="row-r">${o.right}</span>` : ''}${o.chev ? ic('chev', 'row-chev') : ''}`;
  return o.href ? `<a class="row ${o.cls || ''}" href="${o.href}">${inner}</a>` : o.on ? `<button class="row ${o.cls || ''}" onclick="${o.on}">${inner}</button>` : `<div class="row ${o.cls || ''}">${inner}</div>`;
}
/** 行の束(1枚の面) */
const rows = (html, cls = '') => `<div class="rows ${cls}">${Array.isArray(html) ? html.join('') : html}</div>`;
/** 属性の行(名前 + 値)。on があれば押して直せる */
const prop = (k, vHtml, o = {}) => `<${o.on ? `button onclick="${o.on}"` : 'div'} class="prop"><span class="prop-k">${esc(k)}</span><span class="prop-v">${vHtml}</span>${o.on ? ic('chev', 'row-chev') : ''}</${o.on ? 'button' : 'div'}>`;
/** 数字の並び(最大3つ)。items = [{v(HTML), l, href, on, kind}] */
const stats = items => `<div class="stats">${items.map(s => { const inner = `<b>${s.v}</b><span>${esc(s.l)}</span>`; return s.href ? `<a class="stat ${s.kind || ''}" href="${s.href}">${inner}</a>` : s.on ? `<button class="stat ${s.kind || ''}" onclick="${s.on}">${inner}</button>` : `<div class="stat ${s.kind || ''}">${inner}</div>`; }).join('')}</div>`;
/** 空の状態: 何もないこと + 最初の一手。o = {icon, text, action:{label, on}} */
const empty = o => `<div class="empty"><span class="empty-ic">${ic(o.icon || 'day')}</span><p>${esc(o.text)}</p>${o.action ? btn(o.action.label, o.action.on, 'sec', { icon: 'plus' }) : ''}</div>`;
/** 注意の面 */
const banner = (html, kind = 'warn') => `<div class="banner ${kind}">${ic(kind === 'warn' ? 'warn' : 'info')}<div>${html}</div></div>`;
/** チェック欄(ToDo)。on(JS)で切替 */
const check = (done, on, o = {}) => `<button class="chk${done ? ' on' : ''}${o.late ? ' late' : ''}" role="checkbox" aria-checked="${!!done}" aria-label="${esc(o.label || '完了')}" onclick="event.preventDefault();event.stopPropagation();${on}">${ic('check')}</button>`;
/** スイッチ */
const toggle = (onv, on, label) => `<button class="tgl" role="switch" aria-checked="${!!onv}" aria-label="${esc(label || '')}" onclick="${on}"><i></i></button>`;
/** 入力欄の枠。control = HTML */
const field = (label, control, o = {}) => `<label class="field ${o.cls || ''}"><span class="field-l">${esc(label)}</span>${control}${o.hint ? `<span class="field-h">${esc(o.hint)}</span>` : ''}</label>`;
const input = o => `<input class="input ${o.cls || ''}" type="${o.type || 'text'}" ${o.id ? `id="${o.id}"` : ''} value="${esc(o.value == null ? '' : o.value)}" ${o.ph ? `placeholder="${esc(o.ph)}"` : ''} ${o.oninput ? `oninput="${o.oninput}"` : ''} ${o.onchange ? `onchange="${o.onchange}"` : ''} ${o.mode ? `inputmode="${o.mode}"` : ''} ${o.auto ? 'autofocus' : ''} ${o.min ? `min="${o.min}"` : ''} ${o.max ? `max="${o.max}"` : ''} ${o.label ? `aria-label="${esc(o.label)}"` : ''} autocomplete="off">`;
const textarea = o => `<textarea class="input area" ${o.id ? `id="${o.id}"` : ''} rows="${o.rows || 4}" ${o.ph ? `placeholder="${esc(o.ph)}"` : ''} ${o.oninput ? `oninput="${o.oninput}"` : ''} ${o.auto ? 'autofocus' : ''} ${o.label ? `aria-label="${esc(o.label)}"` : ''}>${esc(o.value || '')}</textarea>`;
const select = o => `<select class="input" ${o.id ? `id="${o.id}"` : ''} ${o.onchange ? `onchange="${o.onchange}"` : ''} ${o.label ? `aria-label="${esc(o.label)}"` : ''}>${o.options.map(([v, l]) => `<option value="${esc(v)}" ${String(v) === String(o.value) ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
/** 押して選ぶ欄(値を見せ、押すと選ぶシートが開く) */
const pickField = (label, vHtml, on, o = {}) => `<div class="field"><span class="field-l">${esc(label)}</span><button class="input pickf ${o.emptyv ? 'is-empty' : ''}" onclick="${on}"><span>${vHtml}</span>${ic('chev')}</button></div>`;
/** 読み取った値(未確認)の印 */
const readMark = html => `<span class="read">${html}</span>`;
/** 進み具合 */
const progress = (p, label = '') => `<div class="prog" role="progressbar" aria-valuenow="${Math.round(p * 100)}" aria-valuemin="0" aria-valuemax="100"><i style="width:${Math.round(Math.max(0, Math.min(1, p)) * 100)}%"></i>${label ? `<span>${esc(label)}</span>` : ''}</div>`;

/* ---------- 空(時刻の物差し) ---------- */
const SKY = [[0, [26, 34, 69]], [.17, [42, 52, 99]], [.23, [125, 127, 174]], [.27, [243, 191, 166]], [.315, [205, 229, 247]], [.42, [159, 208, 246]], [.54, [134, 194, 244]], [.65, [166, 209, 243]], [.71, [245, 207, 160]], [.75, [231, 155, 139]], [.795, [106, 107, 166]], [.86, [46, 56, 104]], [1, [26, 34, 69]]];
/** その時刻の空の色 */
function skyAt(hm) {
  const p = Math.max(0, Math.min(1, hm2m(hm) / 1440)); let i = 1; while (i < SKY.length - 1 && SKY[i][0] < p) i++;
  const [p0, c0] = SKY[i - 1], [p1, c1] = SKY[i], t = (p - p0) / (p1 - p0 || 1);
  return `rgb(${c0.map((v, k) => Math.round(v + (c1[k] - v) * t)).join(',')})`;
}
const _pct = hm => (hm2m(hm) / 1440 * 100).toFixed(2);
/** 1日の空の帯: 0時〜24時。記録=白い面 / 予定=点線 / 支払い=下の小さな印 / いま=赤い針。o = {mini, link(既定 true)} */
function skyBand(d, o = {}) {
  const items = actsOn(d).filter(([, a]) => !a.hol);
  const blocks = items.filter(([, a]) => a.start && !a.allDay).map(([id, a]) => {
    const w = isSpan(a) ? Math.max(.7, actMins(a) / 14.4) : 0, cls = isPlan(a) ? (isLate(a) ? 'late' : 'plan') : 'done', l = +_pct(a.start);
    if (!w) return isPay(a) ? '' : `<i class="sky-m ${cls}" style="left:${l}%;--c:${skyAt(a.start)}"></i>`;
    /* 起きた期間だけ、その時刻の空の色が現れる(帯の地は淡い) */
    const st = `left:${l}%;width:${w.toFixed(2)}%;background-size:${(10000 / w).toFixed(0)}% 100%;background-position:${(l / (100 - w) * 100).toFixed(2)}% 0`;
    return o.mini || o.link === false ? `<i class="sky-b ${cls}" style="${st}"></i>` : `<a class="sky-b ${cls}" style="${st}" href="#/a/${id}" aria-label="${esc(actTitle(a))} ${timeText(a)}"></a>`;
  }).join('');
  const pays = Object.values(DB.acts).filter(a => a.pay && a.date === d && !isPlan(a) && a.start).map(a => `<i class="sky-p" style="left:${_pct(a.start)}%"></i>`).join('');
  const now = d === TODAY ? `<i class="sky-past" style="clip-path:inset(0 ${(100 - _pct(NOW)).toFixed(2)}% 0 0)"></i><i class="sky-now" style="left:${_pct(NOW)}%"></i>` : d < TODAY ? `<i class="sky-past"></i>` : '';
  return `<div class="sky${o.mini ? ' mini' : ''}" ${o.mini ? 'aria-hidden="true"' : `role="img" aria-label="${fmtMDJ(d)}の流れ"`}><div class="sky-in"><i class="sky-bg"></i>${now}${blocks}${pays}</div>${o.mini ? '' : `<div class="sky-x num" aria-hidden="true"><span>0</span><span>6</span><span>12</span><span>18</span><span>24</span></div>`}</div>`;
}

/* ---------- 日 ---------- */
/** 日の見出し(押すと日のページ)。大きい日 + 曜日 + その日の空の帯 + その日の自分の支出 */
function dayHead(d, o = {}) {
  const sp = daySpend(d), today = d === TODAY, hol = actsOn(d).find(([, a]) => a.hol);
  return `<a class="dh${today ? ' is-today' : ''}${dDow(d) === 0 || hol ? ' is-hol' : ''}" href="#/day/${d}" id="d-${d}">
    <span class="dh-d num">${+d.slice(8)}</span><span class="dh-m"><b>${dowJ(d)}曜${today ? '・今日' : ''}</b><small>${today ? fmtMDJ(d) : `${relDay(d)}${hol ? ' · ' + esc(hol[1].title) : ''}`}</small></span>
    ${sp ? `<span class="dh-r">${amt(sp)}</span>` : ''}${skyBand(d, { mini: 1 })}${dayOf(d).diary ? `<span class="dh-diary">${esc(dayOf(d).diary)}</span>` : ''}</a>`;
}
/** 写真の並び(枠の色 = 撮った時刻の空) */
const photoStrip = (d, o = {}) => { const ph = dayOf(d).photos || []; return ph.length || o.add ? `<div class="photos">${ph.map((p, i) => `<figure class="photo" style="--c:${skyAt(p.t)}" ${o.on ? `onclick="${o.on}(${q(d)},${i})"` : ''}>${ic('photo')}<figcaption><b>${esc(p.cap || '')}</b><time>${p.t}</time></figcaption></figure>`).join('')}${o.add ? `<button class="photo add" onclick="${o.add}" aria-label="写真を追加">${ic('camera')}</button>` : ''}</div>` : ''; };

/* ---------- お金 ---------- */
/** 支払いの棒: 自分の負担(青)/ ほかの人(灰)。未精算の差額は琥珀の斜線 */
function splitBar(id) {
  const a = A(id), total = payTotal(a) || 1, mine = xAmt(id), b = balanceOf(id);
  const owe = b.open ? Math.abs(b.amt) : 0, lend = b.amt > 0;
  const segs = lend ? [[mine, 'me'], [owe, 'owe'], [total - mine - owe, 'other']] : [[mine - owe, 'me'], [owe, 'owe me'], [total - mine, 'other']];
  return `<div class="split" role="img" aria-label="合計 ${yen(total)} のうち自分 ${yen(mine)}">${segs.filter(s => s[0] > 0).map(([v, c]) => `<i class="${c}" style="flex:${v}"></i>`).join('')}</div>`;
}
/** 支払いの中身の一言: 「醤油らーめん ほか4点」 */
const itemsText = a => { const it = a.pay.items; return !it.length ? '' : it.length === 1 ? it[0].n : `${it[0].n || '品目'} ほか${it.length - 1}点`; };
/** 支払いの面(一覧・軸・詳細の子で同じ形)。右の金額 = 自分の支出。o = {noLink, time} */
function paySlip(id, o = {}) {
  const a = A(id); if (!a) return '';
  if (isPlan(a)) { const pl = a.pay.plan || {}; return `<div class="slip plan"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a><div class="slip-h"><b>${esc(a.title || payeeOf(a))}</b>${amt(pl.amt || 0)}</div><div class="slip-s">${ic('repeat')}<span>${esc(repText(A(a.tpl || id)) || '支払い予定')}</span>${catTag(pl.cat)}</div></div>`; }
  const x = xOf(id), mine = xAmt(id), total = payTotal(a), b = balanceOf(id), un = unreadCount(a), shared = isShared(id);
  const part = o.cat && x ? xCats(id).filter(g => catAnc(g.cat).includes(o.cat)).reduce((t, g) => t + g.amt, 0) : null, sp = x ? xSp(id) : null;
  return `<div class="slip${un ? ' unread' : ''}">${o.noLink ? '' : `<a class="hit" href="#/a/${id}" aria-label="支払い ${esc(payeeOf(a))} ${yen(total)}"></a>`}
    <div class="slip-h"><b>${un && a.pay.unread && a.pay.unread.payee ? readMark(esc(actTitle(a))) : esc(actTitle(a))}</b>${amt(part != null ? part : x ? mine : total, { cls: x ? '' : 'mute' })}</div>
    <div class="slip-s">${x ? catTag(x.cat) : tag('自分の負担なし')}${sp === '非日常' ? `<span class="mini sp">${ic('spark')}非日常</span>` : x && sp == null ? tag('特別感 未設定', 'plan') : ''}${part != null && part !== mine ? tag(`支出 ${yen(mine)}`) : ''}${itemsText(a) ? `<span class="slip-i">${esc(itemsText(a))}</span>` : ''}${un ? tag(`未確認 ${un}`, 'read') : ''}</div>
    ${shared ? `<div class="slip-sp">${splitBar(id)}<span class="slip-tot">合計 ${yen(total)}</span>${b.open ? tag(balanceText(b), 'owe') : b.settled && b.amt ? tag('精算済み') : ''}</div>` : ''}</div>`;
}

/* ---------- 時間の軸(上=過去・下=未来) ---------- */
const kindIc = a => ic(KINDS[a.kind].ic);
/** 入れ子の子(活動の中)。3段まで開き、その先は件数 */
function kidRows(id, depth = 1) {
  const ks = kidsOf(id); if (!ks.length) return '';
  return `<div class="kids">${ks.map(([k, a]) => {
    const sub = depth < 3 ? kidRows(k, depth + 1) : (kidsOf(k).length ? `<div class="kid-more">ほか ${kidsOf(k).length}件</div>` : '');
    if (isPay(a)) return `<div class="kid pay"><time>${a.start || ''}</time>${paySlip(k)}</div>`;
    return `<div class="kid${isSpan(a) ? ' span' : ''}"><time>${a.start || ''}</time><div class="kid-c"><a class="hit" href="#/a/${k}" aria-label="${esc(actTitle(a))}"></a><div class="kid-h">${kindIc(a)}<b>${esc(actTitle(a))}</b>${isSpan(a) ? `<span>${durJ(actMins(a))}</span>` : ''}</div>${roleEnts(a, { link: false, short: 1 }) ? `<div class="ents">${roleEnts(a, { link: false, short: 1 })}</div>` : ''}${sub}</div></div>`;
  }).join('')}</div>`;
}
/** 軸の1行。記録・予定・支払い・瞬間を、形で出し分ける。o = {noKids, date(仮の回の日付)} */
function tlAct(id, o = {}) {
  const a = A(id); if (!a) return '';
  const plan = isPlan(a), late = isLate(a), st = plan ? (late ? 'late' : 'plan') : 'done';
  const t = a.allDay || !a.start ? '終日' : a.start;
  let body;
  if (a.hol) return `<div class="tl-row hol"><i class="tl-n"></i><div class="tl-c"><span class="hol-t">${ic('flag')}${esc(a.title)}</span></div></div>`;
  if (isPay(a)) body = paySlip(id);
  else if (!isSpan(a) && !plan && !a.allDay) body = `<a class="mo" href="#/a/${id}">${kindIc(a)}<b>${esc(actTitle(a))}</b>${roleEnts(a, { link: false, short: 1 })}</a>`;
  else {
    const tasks = plan ? tasksOfAct(id).filter(([, t]) => taskOpen(t)).length : 0;
    body = `<div class="ev ${st}"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a>
      <div class="ev-h"><span class="ev-k">${kindIc(a)}${esc(KINDS[a.kind].n)}</span>${a.end ? `<span class="ev-d num">${a.start}–${a.end}${plan ? '' : ' · ' + durJ(actMins(a))}</span>` : a.date2 ? `<span class="ev-d num">${fmtMD(a.date)}–${fmtMD(a.date2)}</span>` : ''}</div>
      <b class="ev-t">${esc(actTitle(a))}</b>
      ${roleEnts(a, { link: false, roleOf: o.roleOf }) || tasks || a.rep || a.tpl ? `<div class="ents">${roleEnts(a, { link: false, roleOf: o.roleOf })}${a.tpl || a.rep ? `<span class="mini">${ic('repeat')}${esc(repText(A(a.tpl || id)))}</span>` : ''}${tasks ? `<span class="mini">${ic('task')}${tasks}</span>` : ''}</div>` : ''}
      ${late ? `<button class="btn sec sm ev-rec" onclick="recordPlan(${q(id)})">${ic('check')}<span>記録にする</span></button>` : ''}
      ${o.noKids ? '' : kidRows(id)}</div>`;
  }
  return `<div class="tl-row ${st}${isSpan(a) || a.allDay ? ' span' : ' moment'}${isPay(a) ? ' pay' : ''}"><i class="tl-n"></i><div class="tl-c"><time class="tl-t num">${t}</time>${body}</div></div>`;
}
/** いまの線(線の上に針の点、右に時刻、その右へ赤い線) */
const tlNow = () => `<div class="tl-now" id="now"><i class="tl-now-d"></i><time class="num">${NOW}</time><i class="tl-now-l"></i></div>`;
/** メモを軸に置く(書いた時刻) */
const tlMemo = (id) => { const m = DB.memos[id]; return `<div class="tl-row moment is-memo"><i class="tl-n"></i><div class="tl-c"><time class="tl-t num">${m.at.slice(11)}</time>${memoCard(id, { clamp: 2, bare: 1, noTime: 1 })}</div></div>`; };
/** その日の軸(記録と予定を時刻順。今日は「いま」の線を挟む)。o = {memos, filter(fn)} */
function dayAxis(d, o = {}) {
  let items = actsOn(d).map(([id, a]) => ({ k: sortKey(a), id, a }));
  if (o.filter) items = items.filter(i => o.filter(i.a, i.id));
  if (o.memos) memosOn(d).filter(([, m]) => !m.act).forEach(([id, m]) => items.push({ k: m.at.slice(11), id, memo: 1 }));
  items.sort((x, y) => x.k.localeCompare(y.k));
  let html = '', nowDone = d !== TODAY;
  for (const i of items) {
    if (!nowDone && !i.memo && (i.a.allDay ? false : (isPlan(i.a) ? !isLate(i.a) : i.k > NOW))) { html += tlNow(); nowDone = true; }
    else if (!nowDone && i.memo && i.k > NOW) { html += tlNow(); nowDone = true; }
    html += i.memo ? tlMemo(i.id) : tlAct(i.id);
  }
  if (!nowDone) html += tlNow();
  return `<div class="tl${d > TODAY ? ' future' : ''}">${html}</div>`;
}

/* ---------- ToDo・メモ ---------- */
/** ToDo の行(チェックはその場で切替、行は詳細へ)。o = {noList} */
function taskRow(id, o = {}) {
  const t = DB.tasks[id]; if (!t) return '';
  const late = taskLate(t), a = t.act && A(t.act), sub = t.sub || [];
  return `<div class="task${t.done ? ' done' : ''}">${check(t.done, `taskCheck(${q(id)})`, { late, label: t.t })}<a class="task-m" href="#/task/${id}"><b>${t.pri && !t.done ? '<i class="pri" aria-label="優先">!</i>' : ''}${esc(t.t)}</b>
    <span class="task-s">${t.due && !t.done ? tag(late ? `${relDay(t.due)}` : t.due === TODAY ? '今日' : `${fmtMD(t.due)} ${dowJ(t.due)}`, late ? 'late' : t.due === TODAY ? 'me' : '') : ''}${t.rep ? `<span class="mini">${ic('repeat')}</span>` : ''}${sub.length ? `<span class="mini num">${ic('list')}${sub.filter(s => s.d).length}/${sub.length}</span>` : ''}${a ? `<span class="mini">${ic('cal')}${esc(actTitle(a))}</span>` : ''}${(t.ents || []).map(e => ent(e, { link: false, short: 1 })).join('')}${!o.noList && DB.lists[t.list] ? `<span class="mini">${esc(DB.lists[t.list].n)}</span>` : ''}</span></a></div>`;
}
/** チェックの切替(どの画面でも同じ) */
function taskCheck(id) { const r = taskToggle(id); if (done(r, { quiet: 1 })) toast(r.done ? '完了にしました' : '未完了に戻しました', { undo: r.undo }); }
/** メモの紙(角を折った紙)。o = {clamp(行数), bare(影なし), href} */
function memoCard(id, o = {}) {
  const m = DB.memos[id]; if (!m) return '';
  const a = m.act && A(m.act);
  return `<article class="memo${m.pin ? ' pin' : ''}${o.bare ? ' bare' : ''}"><a class="hit" href="${o.href || `#/memo?focus=${id}`}" ${o.on ? `onclick="event.preventDefault();${o.on}"` : ''} aria-label="メモを開く"></a><p ${o.clamp ? `style="-webkit-line-clamp:${o.clamp}" class="clamp"` : ''}>${esc(m.body)}</p>
    <footer>${m.pin ? ic('pin', 'memo-pin') : ''}<time class="num" ${o.noTime ? 'hidden' : ''}>${m.at.slice(0, 10) === TODAY ? m.at.slice(11) : `${fmtMD(m.at.slice(0, 10))} ${m.at.slice(11)}`}</time>${(m.ents || []).map(e => ent(e, { link: false, short: 1 })).join('')}${a ? `<span class="mini">${kindIc(a)}${esc(actTitle(a))}</span>` : ''}</footer></article>`;
}
/** 予定を記録にする(どの画面でも同じ)。支払い予定は金額を確かめてから */
function recordPlan(id) {
  const a = A(id); if (!a) return;
  const r = planRecord(id);
  if (done(r, { quiet: 1 })) toast('記録にしました', { undo: r.undo, act: null });
}
