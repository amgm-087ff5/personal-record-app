/* =====================================================================
   画面: エンティティ(縁)・種類 — v5/DESIGN.md の意味表に従う
   ルート: #/entities(一覧・種類ごと) / #/e/{id}(詳細・フォルダのタブ+縁の色の面+時間軸) / #/types(種類の木)
   作成: 「エンティティ」(primaryOn:['entities'])。メモ(#/notes, #/n/{id})は S1 の担当。ここはリンクだけ張る。
   接頭辞: en(画面の状態・操作) / en_(この画面の中の小道具)
   ===================================================================== */

/* ---------- 小道具 ---------- */
const en_ALL = () => Object.entries(E).filter(([k]) => k !== 'me');
const en_roots = () => Object.keys(TYPES).filter(k => !TYPES[k].p);
const en_count = t => en_ALL().filter(([, e]) => e.t.some(x => isUnder(x, t))).length;
const en_path = t => typeAnc(t).map(a => TYPES[a].n).join(' › ');
const en_root = id => typeAnc(E[id].t[0])[0];
const en_dot = '<span class="p-dot" aria-hidden="true"></span>';
/** 最後に交わった日(関わった記録の日付の最大) */
const en_lastMet = id => {
  const ds = actsOf(id).map(([, a]) => dateOf(a)).filter(Boolean).sort();
  return ds.length ? ds[ds.length - 1] : null;
};
const en_ago = d => {
  if (!d) return 'まだなし';
  const n = Math.round((new Date(TODAY + 'T00:00') - new Date(d + 'T00:00')) / 864e5);
  return n === 0 ? '今日' : n === 1 ? '昨日' : n < 7 ? `${n}日前` : n < 35 ? `${Math.round(n / 7)}週間前` : `${Math.round(n / 30)}か月前`;
};
/** 最近12週の出会い(週ごとの登場数の棒) */
const en_strip = id => {
  const end = new Date(TODAY + 'T00:00');
  const c = Array(12).fill(0);
  actsOf(id).forEach(([, a]) => {
    const d = dateOf(a);
    if (!d) return;
    const w = Math.floor((end - new Date(d + 'T00:00')) / (7 * 864e5));
    if (w >= 0 && w < 12) c[11 - w]++;
  });
  return `<span class="en-strip" aria-label="最近12週の登場">${c.map(n => `<i class="${n >= 3 ? 'h3' : n === 2 ? 'h2' : n === 1 ? 'h1' : ''}"></i>`).join('')}</span>`;
};
const en_ymPrev = ym => { const [y, m] = ym.split('-').map(Number); const d = new Date(y, m - 2, 1); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') };
const en_evKey = ev => (ev.rep ? nextOn(ev) : ev.d) + ' ' + (ev.all ? '00:00' : (ev.s || ''));
const en_pastAll = new Set();
function en_pastAllOn(id) { en_pastAll.add(id); rerender() }

/* ---------- 一覧: 種類ごと・最後に交わった順 ---------- */
let enKind = 'all';
let enQ = '';
const en_matches = (id, q) => {
  if (!q) return true;
  const e = E[id];
  const low = q.toLowerCase();
  const hit = s => String(s == null ? '' : s).toLowerCase().includes(low);
  return hit(e.n)
    || e.t.some(t => typeAnc(t).some(a => hit(TYPES[a].n)))
    || (e.facts || []).some(f => hit(f.v));
};
const en_group = e => {
  const t = e.t.find(x => isUnder(x, enKind)) || e.t[0];
  return enKind === 'all' ? typeAnc(t)[0] : (typeAnc(t).find(a => TYPES[a].p === enKind) || t);
};
const en_row = (id, first) => {
  const e = E[id];
  return `<a class="p-obj en-row" href="#/e/${esc(id)}"><span class="p-row-lead">${en_dot}</span>`
    + `<span class="p-row-body"><span class="p-row-t">${esc(e.n)}</span><span class="p-row-s">${esc(en_path(e.t[0]))}</span></span>`
    + `<span class="en-side">${first ? '<small class="en-ago-k">最後に交わった</small>' : ''}<span class="en-ago">${esc(en_ago(en_lastMet(id)))}</span>${en_strip(id)}</span>`
    + `<span class="p-chev">${icon('chev')}</span></a>`;
};
/** 一覧の中身(検索の入力では、この部分だけ描き直す) */
function en_list() {
  const q = enQ.trim();
  const shown = en_ALL()
    .filter(([k, e]) => (enKind === 'all' || e.t.some(t => isUnder(t, enKind))) && en_matches(k, q))
    .map(([k, e]) => ({k, e, d: en_lastMet(k), g: en_group(e)}))
    .sort((a, b) => (b.d || '').localeCompare(a.d || '') || a.e.n.localeCompare(b.e.n));
  if (!shown.length) {
    return q || enKind !== 'all'
      ? empty({shape: 'entity', text: '一致する エンティティ なし'})
      : empty({shape: 'entity', text: 'エンティティ なし', action: {label: 'エンティティを追加', on: 'en_createSheet()'}});
  }
  const order = Object.keys(TYPES);
  const keys = [...new Set(shown.map(x => x.g))].sort((a, b) => order.indexOf(a) - order.indexOf(b));
  let firstDone = false;   /* 「最後に交わった」の見出しは一覧の先頭の行だけに付ける */
  return keys.map(g => {
    const rs = shown.filter(x => x.g === g);
    const rows = rs.map(x => { const f = !firstDone; firstDone = true; return en_row(x.k, f) }).join('');
    return `<section class="en-g"><div class="en-gh">${tag(TYPES[g].n)}<span class="en-gn num">${rs.length}件</span></div>${rows}</section>`;
  }).join('');
}
function enSearch(v) {
  enQ = v;
  const el = document.getElementById('en-list');
  if (el) el.innerHTML = en_list();
}
function enKindSet(k) { enKind = k; rerender() }

route('entities', () => {
  const all = en_ALL().length;
  const items = [{k: 'all', label: `すべて ${all}`}, ...en_roots().filter(r => en_count(r) > 0).map(r => ({k: r, label: `${TYPES[r].n} ${en_count(r)}`}))];
  return largeTitle('エンティティ', {actions: [
      {label: '追加', icon: 'plus', primary: true, on: 'en_createSheet()'},
      {label: 'エンティティのメモ', icon: 'lines', on: "go('#/notes')"},
      {label: '種類', icon: 'type', on: "go('#/types')"}]})
    + `<div class="en-search">${icon('search')}<input class="p-input" type="search" value="${esc(enQ)}" placeholder="名前・種類・項目の値で探す" aria-label="エンティティを探す" oninput="enSearch(this.value)"></div>`
    + `<div class="en-chips">${chips(items, enKind, 'enKindSet')}</div>`
    + `<div id="en-list">${en_list()}</div>`;
}, {tab: 'entities'});

/* ---------- 詳細: その縁の糸 ---------- */
/** 事実・関係の変化を時間軸の点にする(事実は版で残る) */
function en_factEvents(id) {
  const e = E[id];
  const out = [];
  const since = s => { const m = (s || '').match(/(\d{4}-\d{2})〜/); return m ? m[1] : null };
  (e.facts || []).forEach(f => {
    if (!f.hist) return;
    const chain = [[f.v, since(f.ver)], ...f.hist.map(h => [h[0], since(h[1])])];
    for (let i = 0; i < chain.length - 1; i++) {
      const when = chain[i][1];
      const nv = chain[i][0], ov = chain[i + 1][0];
      const reason = f.hist[i] && f.hist[i][2];
      if (when) out.push({ym: when, key: when + '-' + f.k, title: `${f.k}が変わった`, meta: `${esc(ov)} → ${esc(nv)}${reason ? `(${esc(reason)})` : ''}`});
    }
  });
  relsOf(id).forEach(([rel, other, period]) => {
    const m = (period || '').match(/(\d{4}-\d{2})/);
    if (m && E[other]) out.push({ym: m[1], key: m[1] + '-' + rel, title: `${rel}: ${E[other].n}`, meta: '関係が始まった'});
  });
  return out;
}
/** 記録の行(時間軸)。支払いは殻(payShell)。ほかは題・役割・場所・ほかの縁 */
function en_actRow(x, id) {
  const a = x.a;
  const time = {d: +x.d.slice(8), t: a.time || ''};
  const role = x.role ? tag(x.role) : '';
  if (a.type === '支払い') return axRow({time, node: 'pay', body: `<span class="en-role">${role}</span>${payShell(x.k, {mode: 'row'})}`});
  const others = (a.roles || []).filter(r => r[1] && r[1] !== id && r[1] !== 'me' && E[r[1]] && !['支払先', '場所', '到着', '出発'].includes(r[0])).map(r => eref(r[1])).join('');
  const place = (a.roles || []).find(r => ['支払先', '場所', '到着'].includes(r[0]));
  const placeName = place ? (place[1] && E[place[1]] ? E[place[1]].n : (place[2] || '')) : '';
  return axRow({time, end: a.end, node: a.end ? 'ev' : 'moment', title: a.title || '', href: '#/a/' + x.k,
    meta: `${role}${placeName ? `<span class="ell">${esc(placeName)}</span>` : ''}${others}`});
}
/** 予定の行(点線のカプセル。未来) */
function en_planRow(k, ev, id) {
  const od = ev.rep ? nextOn(ev) : ev.d;
  const tm = ev.all ? '終日' : (ev.s || '');
  const meta = `${placeOf(ev) ? `<span class="ell">${esc(placeOf(ev))}</span>` : ''}${(ev.who || []).filter(i => E[i] && i !== id).map(i => eref(i)).join('')}`;
  return axRow({time: {d: +od.slice(8), t: tm}, node: 'plan', title: ev.t, meta, href: '#/ev/' + k, future: true});
}

route('e', id => {
  const e = E[id];
  if (!e) return appbar('') + empty({shape: 'entity', text: 'エンティティ なし'});
  const root = en_root(id);
  const acts = actsOf(id).map(([k, a, role]) => ({k, a, role, d: dateOf(a) || null})).filter(x => x.d);
  const yearN = acts.filter(x => x.d.startsWith(TODAY.slice(0, 4))).length;
  const money = spendWith(id);
  const ob = openBalances().filter(([, , b]) => b.with.includes(id));
  const lend = ob.filter(([, , b]) => b.amt > 0).reduce((s, [, , b]) => s + b.amt, 0);
  const borrow = ob.filter(([, , b]) => b.amt < 0).reduce((s, [, , b]) => s - b.amt, 0);

  /* 数字の帯 */
  const stats = `<div class="en-stats"><div><b>${esc(en_ago(en_lastMet(id)))}</b><span>最後に交わった</span></div>`
    + `<div><b>${yearN}回</b><span>今年</span></div>`
    + (money ? `<div><b>${yen(money)}</b><span>${root === 'person' ? '一緒の時の自分の支出' : 'ここでの自分の支出'}</span></div>` : '')
    + (lend ? `<div><b class="en-owe">${yen(lend)}</b><span>貸し・未精算</span></div>` : '')
    + (borrow ? `<div><b class="en-owe">${yen(borrow)}</b><span>借り・未精算</span></div>` : '')
    + '</div>';

  /* 事実・関係の行 */
  const sinceOf = (s, re) => { const m = (s || '').match(re); return m ? `${m[1]}年${+m[2]}月から` : '' };
  const factRow = (k, vHtml, s) => `<div class="p-prop en-fact"><span class="p-prop-k">${esc(k)}</span><span class="p-prop-v">${vHtml}${s ? `<small class="en-since">${esc(s)}</small>` : ''}</span></div>`;
  const facts = (e.facts || []).filter(f => !f.rare);
  const rare = (e.facts || []).filter(f => f.rare);
  const factsHTML = facts.map(f => factRow(f.k, esc(f.v), sinceOf(f.ver, /(\d{4})-(\d{2})〜/)))
    .concat(relsOf(id).map(([rel, other, period, sym]) => factRow(rel,
      `<span class="en-dir" title="${sym ? 'お互い' : '一方向'}">${sym ? '⟷' : '→'}</span>${eref(other)}`,
      sinceOf(period, /(\d{4})-(\d{2})/)))).join('');

  /* 持ち主の種類のパンくず(フォルダのタブ) と 縁の色の面 */
  const tabHTML = typeAnc(e.t[0]).map(a => `<a href="#/types">${esc(TYPES[a].n)}</a>`).join('<span class="en-sep">›</span>');
  const hero = `<div class="en-tab">${tabHTML}</div>`
    + `<section class="en-hero"><h1>${esc(e.n)}</h1><div class="en-kinds">${e.t.map(t => tag(TYPES[t].n)).join('')}`
    + `<button class="p-chip" type="button" onclick="en_editSheet('${id}')">＋種類</button></div></section>`;

  /* エンティティのメモ(角を折った紙)。書くのは #/n/{id} の側 */
  const note = NOTES[id];
  const noteHTML = note
    ? `<a class="en-paper" href="#/n/${id}"><span class="en-paper-h">${icon('lines')}<b>エンティティのメモ</b><span class="p-grow"></span><small class="num">更新 ${esc(en_dt(note.upd))}</small></span>`
      + (note.body && note.body[0] ? `<p>${esc(note.body[0][1].replace(/\{[ea]:\w+\}/g, ''))}</p>` : '') + '</a>'
    : `<a class="en-paper en-empty" href="#/n/${id}"><span class="en-paper-h">${icon('lines')}<b>エンティティのメモを書く</b></span></a>`;

  /* ToDo: このエンティティに結び付いた未完了(期限の近い順) */
  const todos = Object.keys(TD).filter(k => tdOpen(k) && (TD[k].ent || []).includes(id))
    .sort((a, b) => (TD[a].due || '9999').localeCompare(TD[b].due || '9999'));
  const todoHTML = todos.length
    ? sh('ToDo', {count: todos.length}) + `<div class="p-list">${todos.map(k => en_todoRow(k)).join('')}</div>`
    : '';

  /* 時間軸: 最近2か月(前の月と今月)+ これより前は押して出す。上=過去・下=未来 */
  const cur = TODAY.slice(0, 7), from = en_ymPrev(cur);
  const evs = Object.entries(EV)
    .filter(([, ev]) => !ev.rec && !ev.hol && ev.d >= TODAY && ((ev.who || []).includes(id) || (ev.place && ev.place[0] === id) || (ev.ent || []).includes(id)))
    .sort((a, b) => en_evKey(a[1]).localeCompare(en_evKey(b[1])));
  const pastAll = [
    ...acts.map(x => ({ym: x.d.slice(0, 7), key: x.d + ' ' + (x.a.time || ''), html: en_actRow(x, id)})),
    ...en_factEvents(id).map(f => ({ym: f.ym, key: f.key, html: axRow({time: '', node: 'fact', title: f.title, meta: f.meta})})),
  ].sort((a, b) => a.key.localeCompare(b.key));
  const futs = evs.map(([k, ev]) => ({ym: en_evKey(ev).slice(0, 7), key: en_evKey(ev), html: en_planRow(k, ev, id)}));
  const past = en_pastAll.has(id) ? pastAll : pastAll.filter(x => x.ym >= from);
  const yms = [...new Set([...past.map(x => x.ym), ...futs.map(x => x.ym), cur])].sort();
  const faces = yms.map(ym => {
    const p = past.filter(x => x.ym === ym).map(x => x.html).join('');
    const f = futs.filter(x => x.ym === ym).map(x => x.html).join('');
    return monthCard(ym) + (ym > cur ? p + f : p + (ym === cur ? nowLine() : '') + f);
  }).join('');
  const older = pastAll.length - past.length;
  const top = !pastAll.length ? axEdge('記録 なし', false)
    : older ? `<div class="p-axedge is-past en-edge"><button class="en-more-btn" type="button" onclick="en_pastAllOn('${id}')">これより前を見る ›</button></div>`
    : axEdge('これより前の記録 なし', false);

  return appbar('', {actions: [{icon: 'edit', label: '縁を編集', on: `en_editSheet('${id}')`}]})
    + `<div class="en-detail">${hero}${stats}`
    + (factsHTML ? `<div class="p-list en-facts">${factsHTML}</div>` : '')
    + (rare.length ? `<details class="en-more"><summary>ほかの情報 ${rare.length}件</summary><div class="p-list en-facts">${rare.map(f => factRow(f.k, esc(f.v), '')).join('')}</div></details>` : '')
    + noteHTML + todoHTML
    + sh('このエンティティの時間軸')
    + `<div class="en-thread">${top}${faces}</div></div>`;
}, {tab: 'entities'});

/** 日時の表記(メモ・一覧と揃える: 2026/10/28 21:04) */
const en_dt = s => String(s || '').replace(/-/g, '/');
/** 詳細のToDo の1行: 左チェック・題・優先の青い「!」(ホームと同じ印)・期限。行全体で ToDo を開く */
const en_todoRow = k => {
  const t = TD[k];
  return `<div class="p-row en-todo"><span class="p-row-lead">${checkbox(false, {label: '完了にする', click: `en_todo('${k}')`})}</span>`
    + `<a class="p-row-body en-todo-a" href="#/task/${k}"><span class="p-row-t">${esc(t.t)}${t.pri ? '<b class="en-pri" aria-label="優先">!</b>' : ''}</span>`
    + `${t.due ? `<span class="p-row-s">${esc(relDay(t.due))}</span>` : ''}</a>`
    + `<span class="p-chev">${icon('chev')}</span></div>`;
}
/** 詳細のToDo: チェックで完了(取り消せる) */
function en_todo(k) {
  const was = !!TD[k].done;
  const r = todoToggle(k, TODAY);
  if (!r.ok) return toast(r.why);
  rerender();
  toast(was ? '完了を取り消しました' : '完了にしました', {undo: () => undoRun(r.undo)});
}

/* ---------- 編集・作成(同じ項目。作成は保存前の下書き) ---------- */
let en_ed = null;
const en_cloneFact = f => { const c = {...f}; if (f.hist) c.hist = f.hist.map(h => h.slice()); return c };
const en_relDraft = r => { const m = (r[2] || '').match(/(\d{4}-\d{2})/); return [r[0] || '', r[1] || '', m ? m[1] : '', r[3] ? 1 : 0] };
const en_draftOf = e => e
  ? {n: e.n, t: e.t.slice(), facts: (e.facts || []).map(en_cloneFact), rels: (e.rels || []).map(en_relDraft)}
  : {n: '', t: [], facts: [], rels: []};
function en_createSheet() { en_open('new', null) }
function en_editSheet(id) { en_open('edit', id) }
function en_open(mode, id) {
  const e = mode === 'edit' ? E[id] : null;
  en_ed = {mode, id, orig: en_draftOf(e), d: en_draftOf(e)};
  en_ed.base = JSON.stringify(en_ed.d);
  openSheet({
    title: mode === 'edit' ? 'エンティティを編集' : 'エンティティを追加',
    body: en_sheetBody(),
    primary: {label: mode === 'edit' ? '保存' : '追加', on: en_save},
    dirty: () => !!en_ed && JSON.stringify(en_ed.d) !== en_ed.base,
    onClose: () => { en_ed = null },
  });
}
function en_refresh() { if (en_ed) setSheetBody(en_sheetBody()) }
function en_sheetBody() {
  const d = en_ed.d;
  const keys = [];
  d.t.forEach(t => propsOf(t).forEach(([p]) => { if (!keys.includes(p)) keys.push(p) }));
  d.facts.forEach(f => { if (!keys.includes(f.k)) keys.push(f.k) });
  const fields = keys.map(k => {
    const f = d.facts.find(x => x.k === k);
    return field(k, input({value: f ? f.v : '', oninput: `en_fact('${encodeURIComponent(k)}',this.value)`}));
  }).join('');
  const typeList = d.t.map((t, i) => `<div class="p-row"><span class="p-row-body"><span class="p-row-t">${esc(TYPES[t].n)}</span><span class="p-row-s">${esc(en_path(t))}</span></span>`
    + (d.t.length > 1 ? `<button class="p-btn p-btn-tonal en-mini" type="button" onclick="en_typeDrop(${i})">外す</button>` : '') + '</div>');
  const addable = Object.keys(TYPES).filter(k => !d.t.includes(k));
  typeList.push(`<div class="p-row"><select id="en-t-add" class="p-input p-select" aria-label="種類を選ぶ"><option value="">種類を選ぶ</option>${addable.map(k => `<option value="${k}">${esc(en_path(k))}</option>`).join('')}</select>`
    + `<button class="p-btn p-btn-tonal en-mini" type="button" onclick="en_typeAdd()">種類を足す</button></div>`);
  const entOpts = sel => '<option value="">相手を選ぶ</option>' + en_ALL().filter(([k]) => k !== en_ed.id)
    .map(([k, e]) => `<option value="${k}" ${k === sel ? 'selected' : ''}>${esc(e.n)}</option>`).join('');
  const rels = d.rels.map((r, i) => `<div class="en-rel">`
    + `<input class="p-input" value="${esc(r[0])}" placeholder="関係の名前(例: 配偶者)" aria-label="関係の名前" oninput="en_rel(${i},0,this.value)">`
    + `<select class="p-input p-select" aria-label="相手" onchange="en_rel(${i},1,this.value)">${entOpts(r[1])}</select>`
    + `<div class="p-seg">${[[1, 'お互い'], [0, '一方向']].map(([v, l]) => `<button class="p-seg-i" type="button" aria-pressed="${+r[3] === v ? 'true' : 'false'}" onclick="en_relSym(${i},${v})">${l}</button>`).join('')}</div>`
    + `<input class="p-input" type="month" value="${esc(r[2])}" aria-label="開始の年月" onchange="en_rel(${i},2,this.value)">`
    + `<button class="p-btn p-btn-tonal en-mini" type="button" onclick="en_relDrop(${i})">外す</button></div>`).join('');
  return field('名前', input({value: d.n, ph: '例: 田中 花子', oninput: "en_set('n',this.value)"}))
    + sh('種類', {count: d.t.length}) + list(typeList)
    + sh('項目') + (fields ? `<div class="en-fields">${fields}</div>` : '<p class="en-none">項目 なし</p>')
    + sh('関係', {count: d.rels.length}) + rels
    + `<div class="en-foot"><button class="p-btn p-btn-tonal" type="button" onclick="en_relAdd()">関係を足す</button></div>`
    + (en_ed.mode === 'edit' ? `<div class="en-foot"><button class="p-btn p-btn-tonal" type="button" onclick="en_remove()">この縁を消す</button></div>` : '');
}
function en_set(k, v) { if (en_ed) en_ed.d[k] = v }
function en_fact(enc, v) {
  if (!en_ed) return;
  const k = decodeURIComponent(enc);
  const f = en_ed.d.facts.find(x => x.k === k);
  if (f) f.v = v; else en_ed.d.facts.push({k, v});
}
function en_rel(i, which, v) { if (en_ed && en_ed.d.rels[i]) en_ed.d.rels[i][which] = v }
function en_relSym(i, v) { if (en_ed && en_ed.d.rels[i]) { en_ed.d.rels[i][3] = v; en_refresh() } }
function en_relAdd() { if (en_ed) { en_ed.d.rels.push(['', '', TODAY.slice(0, 7), 1]); en_refresh() } }
function en_relDrop(i) { if (en_ed) { en_ed.d.rels.splice(i, 1); en_refresh() } }
function en_typeAdd() {
  const v = document.getElementById('en-t-add').value;
  if (!v || !en_ed) return;
  en_ed.d.t.push(v);
  en_refresh();
}
function en_typeDrop(i) {
  if (!en_ed) return;
  if (en_ed.d.t.length <= 1) return toast('種類は1つ以上必要です');
  en_ed.d.t.splice(i, 1);
  en_refresh();
}
/** 版: 値を変えたら、旧い値を履歴(hist)に残し、版を進める(開始月は今月) */
function en_factSave(f) {
  const v = String(f.v).trim();
  const orig = en_ed.orig.facts.find(o => o.k === f.k);
  if (!orig) return {k: f.k, v};
  if (orig.v === v) return en_cloneFact(orig);
  const ym = TODAY.slice(0, 7);
  const m = (orig.ver || '').match(/版(\d+)/), sm = (orig.ver || '').match(/(\d{4}-\d{2})〜/);
  const n = m ? +m[1] : 1;
  const out = {k: f.k, v, ver: `版${n + 1} · ${ym}〜`, hist: [[orig.v, `${sm ? sm[1] : ''}〜${ym}`, ''], ...(orig.hist || []).map(h => h.slice())]};
  if (orig.rare) out.rare = true;
  return out;
}
function en_save() {
  if (!en_ed) return;
  const d = en_ed.d, name = d.n.trim();
  if (!name) return toast('名前を入れてください');
  if (!d.t.length) return toast('種類を選んでください');
  if (d.rels.some(r => !r[0].trim() || !r[1])) return toast('関係の名前と相手を入れてください');
  const facts = d.facts.filter(f => String(f.v || '').trim()).map(en_factSave);
  const rels = d.rels.map(r => [r[0].trim(), r[1], r[2] ? r[2] + '〜' : '', +r[3] ? 1 : 0]);
  if (en_ed.mode === 'new') {
    const r = entAdd({n: name, t: d.t, facts, rels});
    if (!r.ok) return toast(r.why);
    closeSheet(true);
    go('#/e/' + r.id);
    toast(`「${name}」を追加しました`, {undo: () => undoRun(r.undo)});
  } else {
    const r = entUpdate(en_ed.id, {n: name, t: d.t, facts, rels});
    if (!r.ok) return toast(r.why);
    closeSheet(true);
    rerender();
    toast(`「${name}」を保存しました`, {undo: () => undoRun(r.undo)});
  }
}
function en_remove() {
  if (!en_ed) return;
  const id = en_ed.id, name = E[id] ? E[id].n : '';
  const r = entRemove(id);
  if (!r.ok) return toast(r.why);
  closeSheet(true);
  go('#/entities');
  toast(`「${name}」を消しました`, {undo: () => undoRun(r.undo)});
}
addCreate({key: 'entity', label: 'エンティティ', icon: 'entity', primaryOn: ['entities'], run: () => en_createSheet()});

/* ---------- 種類の木 ---------- */
const en_flipped = new Set();
const en_isOpen = (k, d) => (d < 2) !== en_flipped.has(k);
function en_toggle(k) { en_flipped.has(k) ? en_flipped.delete(k) : en_flipped.add(k); rerender() }
function en_treeHTML() {
  const out = [];
  const walk = (k, d) => {
    const kids = Object.keys(TYPES).filter(c => TYPES[c].p === k);
    const open = kids.length > 0 && en_isOpen(k, d);
    const props = propsOf(k).map(p => p[0]).join('・') || 'なし';
    const guides = Array.from({length: d}, (_, i) => `<i class="en-guide" style="--gi:${i}"></i>`).join('');
    const tg = kids.length
      ? `<button class="en-tg" type="button" aria-label="${open ? '閉じる' : '開く'}" aria-expanded="${open}" onclick="en_toggle('${k}')">${icon(open ? 'down' : 'chev')}</button>`
      : '<span class="en-tg-none"></span>';
    out.push(`<div class="p-row en-tr" style="--d:${d}">${guides}${tg}${d === 0 ? en_dot : ''}`
      + `<button class="en-tr-main" type="button" onclick="en_editType('${k}')"><span class="en-tr-t">${tag(TYPES[k].n)}</span><span class="en-tr-s">項目 ${esc(props)}</span></button>`
      + `<span class="en-tr-n num">${en_count(k)}</span></div>`);
    if (open) kids.forEach(c => walk(c, d + 1));
  };
  en_roots().forEach(r => walk(r, 0));
  return out.join('');
}
route('types', () => appbar('種類', {actions: [{icon: 'plus', label: '種類を作成', on: "en_newType('')"}]})
  + (Object.keys(TYPES).length
    ? `<div class="p-list en-tree">${en_treeHTML()}</div>`
    : empty({shape: 'type', text: '種類 なし', action: {label: '種類を作成', on: "en_newType('')"}})), {tab: 'entities'});

function en_desc(k) {
  const r = [k];
  let g = true;
  while (g) { g = false; Object.keys(TYPES).forEach(c => { if (!r.includes(c) && r.includes(TYPES[c].p)) { r.push(c); g = true } }) }
  return r;
}
/** 種類の編集シートの中身。form があれば名前・親の入力を引き継ぐ(項目の操作の後に描き直す) */
function en_typeBody(k, form) {
  const t = TYPES[k];
  const own = TYPE_PROPS[k] || [];
  const inh = propsOf(k).filter(([, from]) => from !== k);
  const kids = Object.keys(TYPES).filter(c => TYPES[c].p === k);
  const used = en_count(k);
  const why = kids.length ? '子の種類があるので消せません' : used ? `${used}件の縁で使われているので消せません` : '';
  const ban = en_desc(k);
  const pVal = form ? form.p : (t.p || '');
  const propRows = own.map((p, i) => `<div class="p-row"><input class="p-input en-pin" value="${esc(p)}" aria-label="項目の名前" onchange="en_propRename('${k}',${i},this.value)">`
    + `<button class="p-btn p-btn-tonal en-mini" type="button" onclick="en_propDrop('${k}',${i})">消す</button></div>`);
  const inhRows = inh.map(([p, from]) => `<div class="p-row"><span class="p-row-body"><span class="p-row-t">${esc(p)}</span><span class="p-row-s">${esc(TYPES[from].n)}から</span></span></div>`);
  return field('名前', input({id: 'en-tn', value: form ? form.n : t.n}))
    + field('親', `<select id="en-tp" class="p-input p-select"><option value="">いちばん上</option>${Object.keys(TYPES).filter(c => !ban.includes(c))
      .map(c => `<option value="${c}" ${c === pVal ? 'selected' : ''}>${esc(en_path(c))}</option>`).join('')}</select>`)
    + sh('項目', {count: own.length + inh.length})
    + list([...propRows, ...inhRows, `<div class="p-row"><input id="en-pa" class="p-input en-pin" placeholder="項目の名前" aria-label="項目の名前"><button class="p-btn p-btn-tonal en-mini" type="button" onclick="en_propAdd('${k}')">項目を足す</button></div>`])
    + sh('子の種類', {count: kids.length})
    + list([...kids.map(c => `<button class="p-row" type="button" onclick="en_editType('${c}')"><span class="p-row-body"><span class="p-row-t">${esc(TYPES[c].n)}</span></span></button>`),
      `<div class="p-row"><button class="p-btn p-btn-tonal" type="button" onclick="en_newType('${k}')">子の種類を足す</button></div>`])
    + (why ? banner('info', esc(why)) : '')
    + `<div class="en-foot"><button class="p-btn p-btn-tonal" type="button" ${why ? 'disabled' : ''} onclick="en_typeDelete('${k}')">この種類を消す</button></div>`;
}
function en_typeRefresh(k) {
  const n = document.getElementById('en-tn'), p = document.getElementById('en-tp');
  setSheetBody(en_typeBody(k, {n: n ? n.value : TYPES[k].n, p: p ? p.value : (TYPES[k].p || '')}));
}
function en_editType(k) {
  openSheet({title: '種類を編集', body: en_typeBody(k), primary: {label: '保存', on: () => en_typeSave(k)}});
}
function en_typeSave(k) {
  const n = document.getElementById('en-tn').value.trim();
  const p = document.getElementById('en-tp').value || '';
  if (!n) return toast('名前を入れてください');
  if (p && en_desc(k).includes(p)) return toast('自分の中へは移せません');
  const log = [];
  if (n !== TYPES[k].n) { const r = typeRename(k, n); if (!r.ok) return toast(r.why); log.push(...r.undo.log) }
  if (p !== (TYPES[k].p || '')) { const r = typeMove(k, p || null); if (!r.ok) return toast(r.why); log.push(...r.undo.log) }
  closeSheet(true);
  rerender();
  if (log.length) toast('種類を保存しました', {undo: () => undoRun({label: '種類を直した', log})});
}
function en_propAdd(k) {
  const v = document.getElementById('en-pa').value;
  const r = typePropAdd(k, v);
  if (!r.ok) return toast(r.why);
  en_typeRefresh(k);
  toast('項目を足しました', {undo: () => { undoRun(r.undo) }});
}
function en_propRename(k, i, v) {
  const old = (TYPE_PROPS[k] || [])[i];
  const r = typePropRename(k, old, v);
  en_typeRefresh(k);
  if (!r.ok) return toast(r.why);
  toast('項目の名前を直しました', {undo: () => { undoRun(r.undo) }});
}
function en_propDrop(k, i) {
  const name = (TYPE_PROPS[k] || [])[i];
  const r = typePropRemove(k, name);
  if (!r.ok) return toast(r.why);
  en_typeRefresh(k);
  toast('項目を消しました', {undo: () => { undoRun(r.undo) }});
}
function en_newType(parent) {
  openSheet({title: '種類を作成',
    body: field('名前', input({id: 'en-tnew', ph: '例: 居酒屋'}))
      + field('親', `<select id="en-tpnew" class="p-input p-select"><option value="">いちばん上</option>${Object.keys(TYPES).map(c => `<option value="${c}" ${c === parent ? 'selected' : ''}>${esc(en_path(c))}</option>`).join('')}</select>`),
    primary: {label: '作成', on: en_createType}});
}
function en_createType() {
  const n = document.getElementById('en-tnew').value;
  const p = document.getElementById('en-tpnew').value || '';
  const r = typeAdd(n, p || undefined);
  if (!r.ok) return toast(r.why);
  closeSheet(true);
  rerender();
  toast(`「${n.trim()}」を作成しました`, {undo: () => { undoRun(r.undo) }});
}
function en_typeDelete(k) {
  const name = TYPES[k].n;
  const r = typeRemove(k);
  if (!r.ok) return toast(r.why);
  closeSheet(true);
  rerender();
  toast(`「${name}」を消しました`, {undo: () => { undoRun(r.undo) }});
}
