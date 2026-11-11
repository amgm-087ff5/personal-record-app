/* 個人記録 v6 — エンティティ(一覧・詳細・種類の木・ノート)。接頭辞 en */
'use strict';

/* 画面をまたいで持つ状態(描き直しても残す) */
const ENS = { q: '', root: 'all' };                 /* 一覧: さがす文字・大分類 */
const ENX = { rare: {}, rec: {}, sub: {}, tree: {} };   /* 詳細: まれな事実を開く・記録の件数・サブページの開閉 / 種類の木の開閉(鍵 → true=開く) */

/* ---------- 小道具 ---------- */
/** 種類の木を深さ優先で並べる [{k, depth}] */
const enTree = (p = null, depth = 0) => typeKids(p).flatMap(k => [{ k, depth }, ...enTree(k, depth + 1)]);
const enTypeName = k => (DB.types[k] || {}).n || '';
/** いちばん細かい種類の名前 */
const enKind = id => { const e = E(id); if (!e || !e.types.length) return ''; return enTypeName([...e.types].sort((a, b) => typeAnc(b).length - typeAnc(a).length)[0]); };
const enIsPlace = id => (E(id).types || []).some(t => isUnder(t, 'place'));
const enIsPerson = id => (E(id).types || []).some(t => isUnder(t, 'person'));
/** 'YYYY-MM-DD HH:MM' → 「11/8 21:04」(今日は時刻だけ) */
const enStamp = s => !s ? '' : (s.slice(0, 10) === TODAY ? '今日 ' : fmtMD(s.slice(0, 10)) + ' ') + s.slice(11);
/** 履歴の期間「2020-04〜2023-03」→「2020年4月〜2023年3月」 */
const enSpan = s => String(s || '').replace(/(\d{4})-(\d{2})/g, (_, y, m) => `${y}年${+m}月`);
/** いちばん細かい種類の道「場所 › 店 › 飲食店 › ラーメン屋」。長い時は末尾2段 */
const enKindPath = id => {
  const e = E(id); if (!e || !e.types.length) return '';
  const t = [...e.types].sort((a, b) => typeAnc(b).length - typeAnc(a).length)[0], names = typeAnc(t).map(enTypeName);
  return names.join(' › ').length > 20 && names.length > 2 ? names.slice(-2).join(' › ') : names.join(' › ');
};
/** ノート本文の参照。{e:エンティティid} → 相手へのリンク、{a:記録id} → 記録へのリンク。esc した後で置き換える。plain = 文字だけ */
function enRef(text, plain) {
  return esc(text).replace(/\{([ea]):([^}]*)\}/g, (_, k, id) => {
    if (k === 'e') { const e = id !== 'me' && E(id); return e ? (plain ? esc(e.n) : ent(id)) : '<span class="en-gone">名前なし</span>'; }
    const a = A(id); return a ? (plain ? esc(actTitle(a)) : `<a class="en-aref" href="#/a/${esc(id)}">${ic('cal')}<span>${esc(actTitle(a))}</span></a>`) : '<span class="en-gone">名前なし</span>';
  });
}
const enTypePick = o => pick({
  title: o.title, none: o.none, cur: o.cur, multi: o.multi, search: false, on: o.on,
  items: enTree().filter(t => !o.exclude || !o.exclude(t.k)).map(t => ({ k: t.k, label: enTypeName(t.k), depth: t.depth })),
});
const enSheetIs = id => { const s = _sheets[_sheets.length - 1]; return !!s && s.el.dataset.id === id; };

/* ====================================================================
   一覧  #/ents
   ==================================================================== */
route('ents', () => {
  const all = Object.keys(DB.ents);
  return `<div class="screen s-ents">
    ${head('エンティティ', { actions: iconBtn('tree', '種類の木', "go('#/types')", 'on-card') + iconBtn('plus', 'エンティティを足す', 'openEntEditor()', 'pri') })}
    ${all.length ? `<div class="en-q">${ic('search')}${input({ id: 'en-q', type: 'search', value: ENS.q, ph: '名前・種類・値でさがす', oninput: 'enQ(this.value)', label: 'エンティティをさがす' })}</div>
    <div id="en-chips">${enChips()}</div>` : ''}
    <div id="en-res" class="en-res">${enRes()}</div>
  </div>`;
}, { tab: 'ents', root: 1 });

function enChips() {
  const items = [['all', 'すべて', Object.keys(DB.ents).length], ...typeKids(null).map(k => [k, enTypeName(k), entsOfType(k).length]).filter(c => c[2] > 0)];
  if (!items.some(c => c[0] === ENS.root)) ENS.root = 'all';
  return chips(items, ENS.root, 'enRoot');
}
function enRoot(k) {
  ENS.root = k;
  const box = $('#en-chips'), sl = box && $('.chips', box) ? $('.chips', box).scrollLeft : 0;
  if (box) { box.innerHTML = enChips(); const c = $('.chips', box); if (c) c.scrollLeft = sl; }
  const r = $('#en-res'); if (r) r.innerHTML = enRes();
}
function enQ(v) { ENS.q = v; const r = $('#en-res'); if (r) r.innerHTML = enRes(); }

/** 一覧の行のための数字(最後に交わった・次の予定・貸し借り) */
function enMeta() {
  const m = {};
  Object.keys(DB.ents).forEach(id => { const st = entStats(id); m[id] = { last: st.last, next: st.next ? st.next[2] : null, bal: st.bal || 0, weeks: st.weeks }; });
  return m;
}
/** 最近12週の交わりの小さな棒(0 は細い線。1件 淡・2件 中・3件以上 濃) */
const enBars = w => `<span class="en-bars" role="img" aria-label="最近12週の交わり">${w.map(n => `<i class="${n ? 'c' + Math.min(3, n) : 'z'}"></i>`).join('')}</span>`;
const enHay = id => { const e = DB.ents[id]; return [e.n, ...e.types.map(typePath), ...e.facts.flatMap(f => [f.v, ...(f.hist || []).map(h => h.v)])].join(' ').toLowerCase(); };
/** 節の鍵: すべて = 大分類。大分類を選んだ時 = その直下の種類 */
function enGroupKey(id) {
  const e = DB.ents[id];
  if (ENS.root === 'all') return entRoot(id);
  const t = e.types.find(t => isUnder(t, ENS.root)), a = typeAnc(t);
  return a[a.indexOf(ENS.root) + 1] || ENS.root;
}
function enRes() {
  const all = Object.keys(DB.ents);
  if (!all.length) return `<div class="card">${empty({ icon: 'people', text: 'エンティティは まだありません', action: { label: 'エンティティを足す', on: 'openEntEditor()' } })}</div>`;
  const w = ENS.q.trim().toLowerCase(), meta = enMeta();
  let ids = all.filter(id => ENS.root === 'all' || DB.ents[id].types.some(t => isUnder(t, ENS.root)));
  if (w) ids = ids.filter(id => enHay(id).includes(w));
  if (!ids.length) return `<div class="card">${empty({ icon: 'search', text: 'あてはまるエンティティは ありません' })}</div>`;
  const order = ENS.root === 'all' ? typeKids(null) : [ENS.root, ...typeKids(ENS.root)];
  const groups = new Map(order.map(k => [k, []]));
  ids.forEach(id => { const k = enGroupKey(id); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(id); });
  const byLast = (a, b) => (meta[b].last || '').localeCompare(meta[a].last || '') || DB.ents[a].n.localeCompare(DB.ents[b].n, 'ja');
  return [...groups].filter(([, g]) => g.length).map(([k, g]) => `<section class="en-g">${sec(enTypeName(k), { n: g.length })}${rows(g.sort(byLast).map(id => enRow(id, meta[id])).join(''))}</section>`).join('');
}
function enRow(id, m) {
  const e = DB.ents[id];
  const when = `<span class="en-when">${m.last ? `<b>${relDay(m.last)}</b>` : '<b class="none">まだなし</b>'}${m.next ? `<small>次 ${fmtMD(m.next)}</small>` : ''}</span>`;
  return row({ cls: 'en-row', href: `#/e/${id}`, lead: avatar(id, '', 'lg'), t: e.n, sHtml: `<span>${esc(enKindPath(id))}</span>${m.bal ? tag(`${m.bal > 0 ? '貸し' : '借り'} ${yen(Math.abs(m.bal))}`, 'owe') : ''}`, right: `${enBars(m.weeks)}${when}` });
}

/* ====================================================================
   詳細  #/e/{id}
   ==================================================================== */
route('e', id => {
  const e = id !== 'me' && DB.ents[id];
  if (!e) return `<div class="screen s-ents">${bar('エンティティ')}<div class="card">${empty({ icon: 'people', text: 'エンティティが見つかりません' })}</div></div>`;
  const st = entStats(id);
  return `<div class="screen s-ents s-ent">
    ${bar(e.n, { actions: iconBtn('edit', '直す', `openEntEditor({id:${q(id)}})`) + iconBtn('more', 'その他', `enMore(${q(id)})`) })}
    ${enHero(id, e)}
    ${enNums(id, st)}
    ${enWeeks(st)}
    ${enFacts(id, e)}
    ${enRels(id)}
    ${enNote(id)}
    ${enTodos(id)}
    ${enMemos(id)}
    ${enAxis(id)}
  </div>`;
}, { tab: 'ents' });

function enHero(id, e) {
  const multi = e.types.length > 1;
  return `<header class="en-hero">${avatar(id, '', 'xl')}<div class="en-hero-m"><h1 data-bar-watch>${esc(e.n)}</h1>
    <div class="en-types">${e.types.map(t => `<span class="en-ty"><a class="en-ty-n" href="#/types" title="${esc(typePath(t))}" aria-label="種類 ${esc(typePath(t))}">${esc(enTypeName(t))}</a>${multi ? `<button class="en-ty-x" onclick="enTypeDrop(${q(id)},${q(t)})" aria-label="種類を外す ${esc(enTypeName(t))}">${ic('close')}</button>` : ''}</span>`).join('')}
      <button class="en-ty add" onclick="enTypeAdd(${q(id)})">${ic('plus')}種類を足す</button></div></div></header>`;
}
function enTypeAdd(id) {
  const e = E(id);
  enTypePick({ title: '種類を足す', exclude: k => e.types.includes(k), on: k => k && done(entUpdate(id, { types: [...e.types, k] })) });
}
function enTypeDrop(id, t) {
  const e = E(id); if (e.types.length < 2) return toast('種類は1つ以上必要です', { bad: 1 });
  done(entUpdate(id, { types: e.types.filter(x => x !== t) }));
}
function enMore(id) {
  const e = E(id);
  menu(e.n, [
    window.openTaskEditor && { label: 'ToDoを足す', icon: 'task', on: () => openTaskEditor({ ents: [id] }) },
    window.openCapture && { label: 'メモを書く', icon: 'memo', on: () => enMemoNew(id) },
    { label: '消す', icon: 'trash', on: () => enRemove(id) },
  ]);
}
function enMemoNew(id) {
  openCapture('memo');
  try { CAP.text = E(id).n + ' '; const t = $('#cap-t'); if (t) t.value = CAP.text; capRefresh(); } catch (err) { }
}
function enRemove(id) { done(entRemove(id), { go: '#/ents', replace: 1 }); }

/** 一緒の時・ここでの支出の内訳(場所では 回数とよく頼む品目) */
function enSpendInfo(id) {
  const ex = new Set();
  actsOfEnt(id).filter(([, a]) => !isPlan(a)).forEach(([k]) => [k, ...descOf(k)].forEach(x => { if (DB.exps[x]) ex.add(x); }));
  const cnt = new Map();
  [...ex].map(x => DB.acts[x]).filter(a => a && a.pay && (a.roles || []).some(r => r.r === '支払先' && r.e === id)).forEach(a => a.pay.items.forEach(i => { if (i.n && usersOf(i).includes('me')) cnt.set(i.n, (cnt.get(i.n) || 0) + 1); }));
  const fav = [...cnt].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'ja')).slice(0, 3);
  return { n: ex.size, fav };
}
function enNums(id, st) {
  const place = enIsPlace(id), person = enIsPerson(id) && !place;
  const sp = enSpendInfo(id);
  const items = [
    { v: st.last ? relDay(st.last) : '<span class="en-none">まだなし</span>', l: '最後に交わった' },
    { v: `${st.year}<small>回</small>`, l: '今年' },
    { v: amt(st.spend, { cls: st.spend ? '' : 'mute' }), l: person ? '一緒の時の支出' : 'ここでの支出' },
  ];
  const nx = st.next;
  return `<section class="en-nums">${stats(items)}
    ${place && (sp.n || sp.fav.length) ? `<div class="en-fav">${sp.n ? `<span class="mini num">支払い ${sp.n}回</span>` : ''}${sp.fav.length ? `<span class="en-fav-l">よく頼む</span>${sp.fav.map(([n, c]) => tag(c > 1 ? `${n} ×${c}` : n)).join('')}` : ''}</div>` : ''}
    ${st.bal ? `<a class="en-owe" href="#/ledger?v=owe">${ic('swap')}<b>${st.bal > 0 ? '貸し' : '借り'} ${yen(Math.abs(st.bal))} · 未精算</b>${ic('chev', 'row-chev')}</a>` : ''}
    ${nx ? `<div class="en-next"><a class="hit" href="#/a/${nx[0]}" aria-label="次の予定 ${esc(actTitle(nx[1]))}"></a><span class="en-next-k">次の予定</span><b>${esc(actTitle(nx[1]))}</b><span class="mini num">${fmtMD(nx[2])} ${dowJ(nx[2])}曜 ${esc(timeText(nx[1]))} · ${relDay(nx[2])}</span></div>` : ''}
  </section>`;
}
function enWeeks(st) {
  const w = st.weeks, mx = Math.max(1, ...w), sum = w.reduce((s, n) => s + n, 0); if (!sum) return '';
  return `<section>${sec('12週の交わり', { n: sum })}<div class="card pad">
    <div class="en-wk" role="img" aria-label="12週前から今週までの回数">${w.map((n, i) => `<span class="en-c${i === 11 ? ' now' : ''}"><b class="num">${n || ''}</b><i class="${n ? '' : 'z'}" style="height:${n ? Math.max(10, Math.round(n / mx * 52)) : 2}px"></i></span>`).join('')}</div>
    <div class="en-wk-x"><span>12週前</span><span>今週</span></div></div></section>`;
}

/* ---------- 事実 ---------- */
const enFv = f => `${esc(f.v)}${f.since ? `<span class="mini">${fmtYM(f.since)}から</span>` : ''}${(f.hist || []).length ? `<span class="mini en-chg">${f.hist.length}回 変わった</span>` : ''}`;
function enFacts(id, e) {
  const main = e.facts.filter(f => !f.rare), rare = e.facts.filter(f => f.rare), open = !!ENX.rare[id];
  const defined = [...new Set(e.types.flatMap(t => propsOf(t).map(p => p[0])))], missing = defined.filter(k => !e.facts.some(f => f.k === k));
  const fr = f => prop(f.k, enFv(f), { on: `enFactEdit(${q(id)},${q(f.k)})` });
  const list = [...main.map(fr), ...(open ? rare.map(fr) : []), ...missing.map(k => `<button class="prop en-add" onclick="enFactEdit(${q(id)},${q(k)})"><span class="prop-k">${esc(k)}</span><span class="prop-v">${ic('plus')}足す</span></button>`),
    ...(rare.length ? [`<button class="prop en-more${open ? ' is-open' : ''}" onclick="enRare(${q(id)})"><span class="prop-k">${open ? 'たたむ' : `ほか ${rare.length}件`}</span>${ic('down', 'row-chev')}</button>`] : [])];
  return `<section>${sec('事実', { act: { label: '項目を足す', on: `enFactNew(${q(id)})` } })}${list.length ? rows(list) : `<div class="card">${empty({ icon: 'tag', text: '事実は まだありません', action: { label: '項目を足す', on: `enFactNew(${q(id)})` } })}</div>`}</section>`;
}
function enRare(id) { ENX.rare[id] = !ENX.rare[id]; rerender(); }

/* 事実を直す(履歴の確認・値を空にして消す・前の値を履歴に残す) */
let ENF = null;
function enFactEdit(id, k) {
  const f = E(id).facts.find(x => x.k === k);
  enFactOpen({ id, k, free: false, exists: !!f, v: f ? f.v : '', orig: f ? f.v : '', hist: f && f.hist ? f.hist : [] });
}
function enFactNew(id) { enFactOpen({ id, k: '', free: true, exists: false, v: '', orig: '', hist: [] }); }
function enFactOpen(o) {
  ENF = { keep: !!(o.exists && String(o.v || '').trim()), since: ymOf(TODAY), why: '', em: false, ...o }; ENF.keep0 = ENF.keep;
  openSheet({ id: 'enfact', title: ENF.free ? '項目を足す' : ENF.k, body: enfBody(), dirty: () => ENF.v !== ENF.orig || ENF.keep !== ENF.keep0 || !!(ENF.free && ENF.k.trim()), primary: enfPrimary() });
}
const enfEm = () => ENF.exists && !ENF.v.trim();
const enfPrimary = () => ({ label: enfEm() ? '項目を消す' : ENF.exists ? '保存' : '足す', on: enfSave, disabled: !ENF.exists && !ENF.v.trim() });
const enfWarn = () => enfEm() ? banner(ENF.hist.length ? `履歴 ${ENF.hist.length}件も消えます` : 'この項目を消します') : '';
function enfBody() {
  const F = ENF;
  return `${F.free ? field('項目の名前', input({ id: 'enf-k', value: F.k, ph: '例: 好きな食べ物', oninput: 'ENF.k=this.value', auto: 1 })) : ''}
    ${field(F.free ? '値' : F.k, input({ id: 'enf-v', value: F.v, ph: F.exists ? '' : '値', oninput: 'enfInput(this.value)', auto: !F.free }))}
    <div id="enf-warn">${enfWarn()}</div>
    ${F.exists ? `<div class="en-tg"><span>前の値を履歴に残す</span>${toggle(F.keep, 'enfKeep()', '前の値を履歴に残す')}</div>` : ''}
    ${F.exists && F.keep ? `<div class="grid2">${field('いつから', input({ id: 'enf-s', type: 'month', value: F.since, oninput: 'ENF.since=this.value' }))}${field('理由', input({ id: 'enf-w', value: F.why, ph: '例: 転居', oninput: 'ENF.why=this.value' }))}</div>` : ''}
    ${F.hist.length ? `<section>${sec('履歴', { n: F.hist.length })}${rows(F.hist.map(h => row({ t: h.v, s: `${enSpan(h.span)}${h.why ? ' · ' + h.why : ''}` })))}</section>` : ''}`;
}
function enfInput(v) {
  ENF.v = v; $('#enf-warn').innerHTML = enfWarn();
  const p = enfPrimary(), cur = _sheets[_sheets.length - 1].o.primary;
  if (p.label !== cur.label || p.disabled !== cur.disabled) setSheetPrimary(p);
}
function enfKeep() { ENF.keep = !ENF.keep; setSheetBody(enfBody()); }
function enfSave() {
  const F = ENF, k = F.free ? F.k.trim() : F.k, v = F.v.trim();
  if (!k) return toast('項目の名前を入れてください', { bad: 1 });
  if (!F.exists && !v) return toast('値を入れてください', { bad: 1 });
  const keep = F.exists && F.keep && !!v;
  if (keep && !/^\d{4}-\d{2}$/.test(F.since)) return toast('いつからを入れてください', { bad: 1 });
  const r = factSet(F.id, k, v, { keepHist: keep, since: keep ? F.since : undefined, why: F.why.trim() });
  done(r, { close: 1, msg: F.exists && !v ? '項目を消しました' : F.exists ? '直しました' : '項目を足しました' });
}

/* ---------- 関係 ---------- */
function enRels(id) {
  const rs = relsOf(id);
  return `<section>${sec('関係', { n: rs.length || undefined, act: rs.length ? { label: '足す', on: `enRelNew(${q(id)})` } : null })}${rs.length
    ? rows(rs.map(r => `<div class="prop"><span class="prop-k">${esc(r.role)}</span><span class="prop-v">${ent(r.other)}${r.since ? `<span class="mini">${fmtYM(r.since)}から</span>` : ''}${enDir(r)}</span><button class="icon-btn en-x" onclick="enRelDel(${r.i})" aria-label="外す ${esc(r.role)}">${ic('close')}</button></div>`))
    : btn('関係を足す', `enRelNew(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
}
/** 関係の向きの印(お互い = swap / 一方向 = move) */
const enDir = r => { const sym = !!(DB.rels[r.i] || {}).sym; return `<span class="en-dir" role="img" aria-label="${sym ? 'お互い' : '一方向'}">${ic(sym ? 'swap' : 'move')}</span>`; };
function enRelDel(i) { done(relRemove(i)); }
const EN_ROLES = [['配偶者', ''], ['友人', ''], ['家族', ''], ['勤務先', '従業員'], ['一緒に', '']];
let ENR = null;
function enRelNew(id) {
  ENR = { id, role: '', inv: '', dir: 'sym', other: null, since: ymOf(TODAY) };
  openSheet({ id: 'enrel', title: '関係を足す', body: enrBody(), dirty: () => !!(ENR.role || ENR.other), primary: { label: '足す', on: enrSave, disabled: true } });
}
function enrBody() {
  const R = ENR, cands = EN_ROLES.map(([r, v]) => [r, r + (v ? '/' + v : '')]);
  return `${field('関係の名前', input({ id: 'enr-role', value: R.role, ph: '例: 配偶者', oninput: 'enrRole(this.value)', auto: 1 }))}
    ${chips(cands, EN_ROLES.some(([r]) => r === R.role) ? R.role : null, 'enrCand')}
    ${pickField('相手', R.other ? ent(R.other, { link: false }) : '選ぶ', 'enrOther()', { emptyv: !R.other })}
    ${seg([['sym', 'お互い'], ['one', '一方向']], R.dir, 'enrDir')}
    ${R.dir === 'one' ? field('相手から見た呼び名', input({ id: 'enr-inv', value: R.inv, ph: '例: 従業員', oninput: 'enrInv(this.value)' })) : ''}
    ${field('いつから', input({ id: 'enr-s', type: 'month', value: R.since, oninput: 'ENR.since=this.value' }))}`;
}
const enrOk = () => !!(ENR.role.trim() && ENR.other && (ENR.dir !== 'one' || ENR.inv.trim()));
function enrDir(k) { ENR.dir = k; setSheetBody(enrBody()); setSheetPrimary({ label: '足す', on: enrSave, disabled: !enrOk() }); }
function enrInv(v) { ENR.inv = v; setSheetPrimary({ label: '足す', on: enrSave, disabled: !enrOk() }); }
function enrRole(v) { ENR.role = v; setSheetPrimary({ label: '足す', on: enrSave, disabled: !enrOk() }); }
function enrCand(r) { ENR.role = r; ENR.inv = (EN_ROLES.find(x => x[0] === r) || [0, ''])[1]; ENR.dir = ENR.inv ? 'one' : 'sym'; setSheetBody(enrBody()); setSheetPrimary({ label: '足す', on: enrSave, disabled: !enrOk() }); }
function enrOther() {
  pick({ title: '相手を選ぶ', search: true, cur: ENR.other, items: Object.keys(DB.ents).filter(k => k !== ENR.id).map(k => ({ k, label: DB.ents[k].n, sub: enKind(k), lead: avatar(k) })), on: k => { if (!k) return; ENR.other = k; setSheetBody(enrBody()); setSheetPrimary({ label: '足す', on: enrSave, disabled: !enrOk() }); } });
}
function enrSave() {
  const R = ENR; if (!enrOk()) return toast(R.dir === 'one' && R.role.trim() && R.other ? '相手から見た呼び名を入れてください' : '関係の名前と相手を入れてください', { bad: 1 });
  done(relAdd({ a: R.id, b: R.other, role: R.role.trim(), inv: R.dir === 'one' ? R.inv.trim() : undefined, since: /^\d{4}-\d{2}$/.test(R.since) ? R.since : undefined }), { close: 1 });
}

/* ---------- ノート・ToDo・メモ ---------- */
function enNote(id) {
  const n = DB.notes[id];
  if (!n) return `<section>${sec('ノート')}${btn('ノートを書く', `go('#/n/${id}')`, 'dash', { icon: 'pencil' })}</section>`;
  const b = (n.body || []).filter(x => x[0] || x[1]), first = b[0];
  return `<section>${sec('ノート', { act: { label: 'ひらく', href: `#/n/${id}` } })}<article class="card en-note"><a class="hit" href="#/n/${id}" aria-label="ノートを開く"></a>
    ${first ? `${first[0] ? `<b>${esc(first[0])}</b>` : ''}<p class="clamp" style="-webkit-line-clamp:3">${enRef(first[1], 1)}</p>` : `<p class="mini">本文なし</p>`}
    <footer class="mini num">版 ${n.ver} · 更新 ${enStamp(n.upd)}</footer></article></section>`;
}
function enTodos(id) {
  const ts = tasksOfEnt(id).filter(([, t]) => taskOpen(t)).sort((a, b) => (a[1].due || '9999').localeCompare(b[1].due || '9999')); if (!ts.length) return '';
  return `<section>${sec('ToDo', { n: ts.length, act: window.openTaskEditor ? { label: '足す', on: `openTaskEditor({ents:[${q(id)}]})` } : null })}${rows(ts.map(([k]) => taskRow(k)).join(''))}</section>`;
}
function enMemos(id) {
  const ms = memosSorted().filter(([, m]) => (m.ents || []).includes(id)); if (!ms.length) return '';
  return `<section>${sec('メモ', { n: ms.length, act: window.openCapture ? { label: '足す', on: `enMemoNew(${q(id)})` } : null })}<div class="stack">${ms.map(([k]) => memoCard(k, { clamp: 3 })).join('')}</div></section>`;
}

/* ---------- 時間の軸 ---------- */
/** 軸に載せるもの: {d(日。月の出来事は 'YYYY-MM-00'), id?, html?}。日ごとにまとめる */
function enDays(list, asc, id) {
  const m = new Map(); list.forEach(x => { if (!m.has(x.d)) m.set(x.d, []); m.get(x.d).push(x); });
  return `<div class="en-days">${[...m].map(([d, g]) => {
    const ev = d.endsWith('-00'), yr = d.slice(0, 4) !== TODAY.slice(0, 4) ? d.slice(0, 4) + '年' : '';
    const items = (asc || ev) ? g : [...g].reverse();
    const hd = ev ? `<div class="en-dh"><b>${fmtYM(d.slice(0, 7))}</b></div>` : `<a class="en-dh" href="#/day/${d}"><b>${yr}${fmtFull(d)}</b><span>· ${relDay(d)}</span></a>`;
    return `<div class="en-day">${hd}<div class="tl${d > TODAY ? ' future' : ''}">${items.map(x => x.html || tlAct(x.id, { roleOf: id })).join('')}</div></div>`;
  }).join('')}</div>`;
}
/** 月の出来事(瞬間の形の小さい行。押せない) */
const enEv = (ym, icn, html) => ({ d: ym + '-00', html: `<div class="tl-row done moment"><i class="tl-n"></i><div class="tl-c"><div class="mo en-ev">${ic(icn)}<span>${html}</span></div></div></div>` });
/** 事実の変化(履歴の終わり = 変わった月)と関係の始まり */
function enMarks(id) {
  const out = [], e = E(id);
  e.facts.forEach(f => (f.hist || []).forEach((h, i) => {
    const ym = String(h.span || '').split('〜')[1]; if (!/^\d{4}-\d{2}$/.test(ym || '')) return;
    const nv = i === 0 ? f.v : f.hist[i - 1].v;
    out.push(enEv(ym, 'edit', `<b>${esc(f.k)}が変わった</b> ${esc(h.v)} → ${esc(nv)}`));
  }));
  relsOf(id).forEach(r => { if (/^\d{4}-\d{2}$/.test(r.since || '')) out.push(enEv(r.since, 'link', `<b>関係が始まった</b> ${esc(r.role)} ${esc((E(r.other) || {}).n || '')}`)); });
  return out;
}
/** 繰り返しの予定の「次の1回」(今日から60日先まで。各ひな形につき1回) */
function enNextReps(id) {
  const seen = new Set(), out = [];
  for (let i = 0; i <= 60; i++) {
    const d = dAdd(TODAY, i);
    actsOn(d).forEach(([k, a]) => { if (!k.includes('@') || seen.has(a.tpl || k.split('@')[0]) || a.hol || isLate(a) || !roleIn(a, id)) return; seen.add(a.tpl || k.split('@')[0]); out.push({ d, id: k }); });
  }
  return out;
}
function enAxis(id) {
  const all = actsOfEnt(id), key = x => x.d + sortKey(A(x.id));
  const mk = ([k, a]) => ({ d: a.date, id: k });
  const marks = enMarks(id), ym = ymOf(TODAY);
  const plans = [...all.filter(([, a]) => isPlan(a)).map(mk), ...enNextReps(id), ...marks.filter(x => x.d.slice(0, 7) > ym)].sort((x, y) => x.id && y.id ? key(x).localeCompare(key(y)) : x.d.localeCompare(y.d));
  const recAll = all.filter(([, a]) => !isPlan(a)).map(mk), n = ENX.rec[id] || 10, shown = recAll.slice(0, n);
  const lim = recAll.length > n ? shown[shown.length - 1].d : '';
  const recs = [...shown, ...marks.filter(x => x.d.slice(0, 7) <= ym && x.d >= lim)].sort((x, y) => y.d.localeCompare(x.d));
  if (!plans.length && !recAll.length && !marks.length) return `<section>${sec('記録')}<div class="card">${empty({ icon: 'axis', text: 'このエンティティの記録は まだありません' })}</div></section>`;
  return `${plans.length ? `<section>${sec('これから', { n: plans.filter(x => x.id).length })}${enDays(plans, true, id)}</section>` : ''}
    <div class="tl en-nowl">${tlNow()}</div>
    ${recs.length ? `<section>${sec('記録', { n: recAll.length })}${enDays(recs, false, id)}${recAll.length > n ? btn(`もっと見る · 残り ${recAll.length - n}件`, `enMoreRec(${q(id)})`, 'dash') : ''}</section>` : ''}`;
}
function enMoreRec(id) { ENX.rec[id] = (ENX.rec[id] || 10) + 20; rerender(); }

/* ====================================================================
   エンティティの編集  openEntEditor({id}) / openEntEditor()
   ==================================================================== */
let ENE = null;
function openEntEditor(o = {}) {
  const e = o.id ? E(o.id) : null; if (o.id && (!e || o.id === 'me')) return toast('エンティティが見つかりません', { bad: 1 });
  const types = e ? [...e.types] : ((o.types || []).filter(t => DB.types[t]).length ? o.types.filter(t => DB.types[t]) : [DB.types.person ? 'person' : Object.keys(DB.types)[0]]);
  ENE = { id: e ? o.id : null, n: e ? e.n : (o.n || ''), types, f: {}, n0: e ? e.n : (o.n || ''), t0: types.join() };
  openSheet({ id: 'enedit', title: e ? 'エンティティを直す' : 'エンティティを足す', body: eneBody(), dirty: () => ENE.n !== ENE.n0 || ENE.types.join() !== ENE.t0 || Object.values(ENE.f).some(v => v), primary: { label: e ? '保存' : '足す', on: eneSave, disabled: !ENE.n.trim() } });
}
const eneProps = () => [...new Set(ENE.types.flatMap(t => propsOf(t).map(p => p[0])))];
function eneBody() {
  return `${field('名前', input({ id: 'ene-n', value: ENE.n, ph: '例: 田中 花子', oninput: 'eneName(this.value)', auto: !ENE.id }))}
    ${pickField('種類', ENE.types.map(t => tag(enTypeName(t), 'kin')).join(''), 'eneTypes()')}
    ${ENE.id ? '' : eneFields()}`;
}
/** 種類の項目を全部(4つ目以降は「ほかの項目」で畳む。入力済みなら開いたまま) */
function eneFields() {
  const ks = eneProps(), fld = k => field(k, input({ value: ENE.f[k] || '', oninput: `ENE.f[${q(k)}]=this.value` })), rest = ks.slice(3);
  const open = ENE.more || rest.some(k => (ENE.f[k] || '').trim());
  return ks.slice(0, 3).map(fld).join('') + (rest.length ? (open ? rest.map(fld).join('') : btn(`ほかの項目 ${rest.length}`, 'eneMore()', 'ghost', { icon: 'down' })) : '');
}
function eneMore() { ENE.more = true; setSheetBody(eneBody()); }
function eneName(v) {
  const was = !!ENE.n.trim(); ENE.n = v;
  if (was !== !!v.trim()) setSheetPrimary({ label: ENE.id ? '保存' : '足す', on: eneSave, disabled: !v.trim() });
}
function eneTypes() {
  enTypePick({ title: '種類', multi: true, cur: ENE.types, on: ts => { if (!ts.length) return toast('種類は1つ以上必要です', { bad: 1 }); ENE.types = ts; setSheetBody(eneBody()); } });
}
function eneSave() {
  const n = ENE.n.trim(); if (!n) return toast('名前を入れてください', { bad: 1 });
  if (ENE.id) return done(entUpdate(ENE.id, { n, types: ENE.types }), { close: 1 });
  const keys = eneProps(), r = entAdd({ n, types: ENE.types, facts: keys.map(k => ({ k, v: (ENE.f[k] || '').trim() })).filter(f => f.v) });
  done(r, { close: 1, go: r.ok ? '#/e/' + r.id : undefined });
}

/* ====================================================================
   種類の木  #/types
   ==================================================================== */
/** 種類の木の開閉(既定: 深さ0と1は開き、2以上は閉じる)。閉じた行の下は隠す */
const enOpen = (k, depth) => ENX.tree[k] != null ? ENX.tree[k] : depth < 2;
function enTreeVis() {
  const out = [], walk = (p, depth) => typeKids(p).forEach(k => { const kids = typeKids(k).length; out.push({ k, depth, kids }); if (kids && enOpen(k, depth)) walk(k, depth + 1); });
  walk(null, 0); return out;
}
function enTreeTog(k) { ENX.tree[k] = !enOpen(k, typeAnc(k).length - 1); rerender(); }
route('types', () => {
  const tree = enTreeVis(), n = enTree().length, total = Object.keys(DB.ents).length;
  return `<div class="screen s-ents">
    ${bar('種類', { actions: iconBtn('plus', '種類を足す', 'enTypeNew()', 'pri') })}
    <header class="en-th"><h1 data-bar-watch>種類</h1><span class="mini num">${n}種類 · ${total}件</span></header>
    ${tree.length ? rows(tree.map(({ k, depth, kids }) => {
      const np = propsOf(k).length, open = enOpen(k, depth);
      return `<div class="row en-tr${depth ? '' : ' root'}" style="--depth:${depth}">${kids ? `<button class="en-tx${open ? ' is-open' : ''}" aria-expanded="${open}" aria-label="${esc(enTypeName(k))}の中を${open ? 'たたむ' : '開く'}" onclick="enTreeTog(${q(k)})">${ic('down')}</button>` : '<span class="en-tx-sp"></span>'}<button class="en-tm" onclick="enTypeOpen(${q(k)})"><span class="row-m"><span class="row-t">${esc(enTypeName(k))}</span><span class="row-s">項目 ${np}</span></span><span class="row-r num">${entsOfType(k).length}</span>${ic('chev', 'row-chev')}</button></div>`;
    })) : `<div class="card">${empty({ icon: 'tree', text: '種類は まだありません', action: { label: '種類を足す', on: 'enTypeNew()' } })}</div>`}
  </div>`;
}, { tab: 'ents' });

let ENT = null, ENP = null, ENY = null;
function enTypeOpen(key) {
  const t = DB.types[key]; if (!t) return;
  ENT = { key, n: t.n, n0: t.n, p: t.p || null, p0: t.p || null, np: '' };
  openSheet({ id: 'entype', title: '種類', body: entBody(), dirty: () => ENT.n !== ENT.n0 || ENT.p !== ENT.p0 || !!ENT.np.trim(), primary: { label: '保存', on: entSave } });
}
function entBody() {
  const k = ENT.key, props = propsOf(k), ents = entsOfType(k);
  return `${field('名前', input({ id: 'ent-n', value: ENT.n, oninput: 'ENT.n=this.value' }))}
    ${pickField('親', ENT.p ? esc(enTypeName(ENT.p)) : 'なし', 'entParent()', { emptyv: !ENT.p })}
    <section>${sec('項目', { n: props.length })}<div class="rows">${props.map(([p, from]) => from === k
      ? `<div class="row"><span class="row-m"><span class="row-t">${esc(p)}</span></span>${btn('改名', `entPropRen(${q(p)})`, 'ghost', { sm: 1 })}${btn('消す', `entPropDel(${q(p)})`, 'ghost', { sm: 1 })}</div>`
      : `<div class="row"><span class="row-m"><span class="row-t">${esc(p)}</span></span><span class="mini">${esc(enTypeName(from))}から</span></div>`).join('')}
      <div class="row en-np"><input class="input" id="ent-np" placeholder="項目を足す" value="${esc(ENT.np)}" oninput="ENT.np=this.value" onkeydown="if(event.key==='Enter'){event.preventDefault();entPropAdd()}" aria-label="項目の名前" autocomplete="off">${btn('足す', 'entPropAdd()', 'sec', { sm: 1 })}</div></div></section>
    ${ents.length ? `<section>${sec('この種類のエンティティ', { n: ents.length })}${rows(ents.map(id => row({ lead: avatar(id), t: DB.ents[id].n, s: enKind(id), on: `enGoEnt(${q(id)})`, chev: 1 })))}</section>` : ''}
    ${btn('この中に種類を足す', `enTypeNew(${q(k)})`, 'dash', { icon: 'plus' })}
    ${btn('この種類を消す', 'entDel()', 'sec', { icon: 'trash' })}`;
}
/** 種類のシートが開いていれば描き直す */
const enTypeRefresh = () => { if (ENT && DB.types[ENT.key] && enSheetIs('entype')) setSheetBody(entBody()); };
/** シートの中の変更: トーストの「取り消す」でシートも戻す */
const enTU = r => toast(r.label, { undo: () => { r.undo(); enTypeRefresh(); } });
function enGoEnt(id) { closeAllSheets(); go('#/e/' + id); }
function entParent() {
  enTypePick({ title: '親の種類', none: { label: 'なし(いちばん上)' }, cur: ENT.p, exclude: k => isUnder(k, ENT.key), on: k => { ENT.p = k || null; setSheetBody(entBody()); } });
}
function entSave() {
  const patch = {}; if (ENT.n !== ENT.n0) patch.n = ENT.n; if (ENT.p !== ENT.p0) patch.p = ENT.p;
  if (!Object.keys(patch).length) return closeSheet(true);
  done(typeUpdate(ENT.key, patch), { close: 1 });
}
function entDel() { done(typeRemove(ENT.key), { close: 1 }); }
function entPropAdd() {
  const r = typePropAdd(ENT.key, ENT.np);
  if (done(r, { quiet: 1 })) { ENT.np = ''; enTU(r); enTypeRefresh(); }
}
function entPropDel(p) { const r = typePropRemove(ENT.key, p); if (done(r, { quiet: 1 })) { enTU(r); enTypeRefresh(); } }
function entPropRen(p) {
  ENP = { a: p, b: p };
  openSheet({ id: 'enprop', title: '項目の名前', body: field('名前', input({ id: 'enp-n', value: p, oninput: 'ENP.b=this.value', auto: 1 })), dirty: () => ENP.b !== ENP.a, primary: { label: '改名', on: entPropRenSave } });
}
function entPropRenSave() {
  const r = typePropRename(ENT.key, ENP.a, ENP.b.trim());
  if (done(r, { close: 1, quiet: 1 })) { enTU(r); enTypeRefresh(); }
}
/** 種類を足す(parent があればその中に) */
function enTypeNew(p) {
  ENY = { n: '', p: p || null };
  openSheet({ id: 'enynew', title: '種類を足す', body: enyBody(), dirty: () => !!ENY.n.trim(), primary: { label: '足す', on: enySave, disabled: true } });
}
const enyBody = () => `${field('名前', input({ id: 'eny-n', value: ENY.n, ph: '例: 居酒屋', oninput: 'enyName(this.value)', auto: 1 }))}${pickField('親', ENY.p ? esc(enTypeName(ENY.p)) : 'なし', 'enyParent()', { emptyv: !ENY.p })}`;
function enyName(v) { const was = !!ENY.n.trim(); ENY.n = v; if (was !== !!v.trim()) setSheetPrimary({ label: '足す', on: enySave, disabled: !v.trim() }); }
function enyParent() { enTypePick({ title: '親の種類', none: { label: 'なし(いちばん上)' }, cur: ENY.p, on: k => { ENY.p = k || null; setSheetBody(enyBody()); setSheetPrimary({ label: '足す', on: enySave, disabled: !ENY.n.trim() }); } }); }
function enySave() {
  const r = typeAdd(ENY.n, ENY.p);
  if (done(r, { close: 1, quiet: 1 })) { enTU(r); enTypeRefresh(); }
}

/* ====================================================================
   ノート  #/n/{エンティティid}
   ==================================================================== */
route('n', id => {
  const e = id !== 'me' && DB.ents[id];
  if (!e) return `<div class="screen s-ents">${bar('ノート')}<div class="card">${empty({ icon: 'book', text: 'エンティティが見つかりません' })}</div></div>`;
  const n = DB.notes[id], title = `${e.n}のノート`;
  return `<div class="screen s-ents">
    ${bar(title, { actions: iconBtn('edit', n ? '直す' : 'ノートを書く', `enNoteEdit(${q(id)})`) })}
    <header class="en-nh"><h1 data-bar-watch>${esc(title)}</h1><div class="en-nm">${ent(id)}${n ? `<span class="mini num">版 ${n.ver} · 更新 ${enStamp(n.upd)}</span>` : ''}</div></header>
    ${n ? enNoteBody(id, n) : `<div class="card">${empty({ icon: 'book', text: 'ノートは まだありません', action: { label: 'ノートを書く', on: `enNoteEdit(${q(id)})` } })}</div>`}
  </div>`;
}, { tab: 'ents' });

function enNoteBody(id, n) {
  const body = (n.body || []).filter(b => b[0] || b[1]), subs = n.subs || [];
  return `${body.length ? `<article class="card pad en-nb">${body.map(([h, t]) => `<div class="en-ns">${h ? `<h3>${esc(h)}</h3>` : ''}${t ? `<p>${enRef(t)}</p>` : ''}</div>`).join('')}</article>` : ''}
    <section>${sec('サブページ', { n: subs.length, act: { label: '足す', on: `enSubEdit(${q(id)},null)` } })}${subs.length ? `<div class="rows">${subs.map(([name, s, t], i) => {
      const key = `${id}:${i}`, open = !!ENX.sub[key];
      return `<div class="en-sub${open ? ' is-open' : ''}"><button class="row" aria-expanded="${open}" onclick="enSubToggle(${q(key)})"><span class="row-m"><span class="row-t">${esc(name)}</span></span>${s ? `<span class="mini">${esc(s)}</span>` : ''}${ic('chev', 'row-chev')}</button>
        ${open ? `<div class="en-sub-b">${t ? `<p>${enRef(t)}</p>` : '<p class="mini">本文なし</p>'}<div class="en-acts">${btn('直す', `enSubEdit(${q(id)},${i})`, 'sec', { sm: 1, icon: 'edit' })}${btn('消す', `enSubDel(${q(id)},${i})`, 'ghost', { sm: 1, icon: 'trash' })}</div></div>` : ''}</div>`;
    }).join('')}</div>` : btn('サブページを足す', `enSubEdit(${q(id)},null)`, 'dash', { icon: 'plus' })}</section>`;
}
function enSubToggle(key) { ENX.sub[key] = !ENX.sub[key]; rerender(); }
function enSubDel(id, i) { done(noteSubRemove(id, i)); }
let ENU = null;
function enSubEdit(id, i) {
  const s = i != null && DB.notes[id] ? DB.notes[id].subs[i] : null;
  ENU = { id, i, n: s ? s[0] : '', s: s ? s[1] : '', t: s ? s[2] : '' }; const o = { ...ENU };
  openSheet({ id: 'ensub', title: s ? 'サブページを直す' : 'サブページを足す', tall: 1,
    body: `${field('名前', input({ id: 'ens-n', value: ENU.n, ph: '例: 好きな店', oninput: 'ENU.n=this.value', auto: !s }))}${field('補足', input({ id: 'ens-s', value: ENU.s, ph: '例: 3件', oninput: 'ENU.s=this.value' }))}${field('本文', textarea({ id: 'ens-t', rows: 9, value: ENU.t, oninput: 'ENU.t=this.value', label: '本文' }))}${btn('エンティティを入れる', "enRefPick('ens-t',v=>ENU.t=v)", 'sec', { icon: 'link' })}`,
    dirty: () => ENU.n !== o.n || ENU.s !== o.s || ENU.t !== o.t, primary: { label: '保存', on: () => done(noteSubSave(ENU.id, ENU.i, [ENU.n, ENU.s, ENU.t]), { close: 1 }) } });
}

/* ノートを直す(節ごとに 見出し・本文) */
let ENN = null;  /* cur = 今フォーカスのある本文の節 */
const ennSig = () => JSON.stringify(ENN.secs.map(s => [s.h.trim(), s.t.trim()]).filter(s => s[0] || s[1]));
function enNoteEdit(id) {
  const n = DB.notes[id];
  ENN = { id, secs: n && n.body.length ? n.body.map(([h, t]) => ({ h, t })) : [{ h: '', t: '' }] }; ENN.orig = ennSig();
  openSheet({ id: 'ennote', title: `${E(id).n}のノート`, tall: 1, body: ennBody(), dirty: () => ennSig() !== ENN.orig, primary: { label: '保存', on: ennSave }, lead: n ? `<span class="tag">版 ${n.ver + 1}</span>` : '' });
}
function ennBody() {
  const last = ENN.secs.length - 1;
  return `${ENN.secs.map((s, i) => `<div class="card pad en-nsec"><div class="en-nsec-h"><span class="mini num">節 ${i + 1}</span><div>${i ? iconBtn('up', '上へ', `ennMove(${i},-1)`) : '<span class="en-sp"></span>'}${i < last ? iconBtn('down', '下へ', `ennMove(${i},1)`) : '<span class="en-sp"></span>'}${iconBtn('trash', '節を消す', `ennDel(${i})`)}</div></div>
      ${input({ value: s.h, ph: '見出し', oninput: `ENN.secs[${i}].h=this.value`, label: `節${i + 1}の見出し` })}${textarea({ id: `enn-t${i}`, rows: 6, value: s.t, ph: '本文', oninput: `ENN.secs[${i}].t=this.value`, label: `節${i + 1}の本文` }).replace('<textarea ', `<textarea onfocus="ENN.cur=${i}" `)}</div>`).join('')}
    ${btn('エンティティを入れる', 'ennRef()', 'sec', { icon: 'link' })}
    ${btn('節を足す', 'ennAdd()', 'dash', { icon: 'plus' })}`;
}
/** 今フォーカスのある本文の末尾に {e:id} を足す */
function ennRef() { const i = Math.min(ENN.cur == null ? ENN.secs.length - 1 : ENN.cur, ENN.secs.length - 1); enRefPick(`enn-t${i}`, v => { ENN.secs[i].t = v; ENN.cur = i; }); }
function enRefPick(tid, set) {
  pick({ title: 'エンティティを入れる', search: true, items: Object.keys(DB.ents).map(k => ({ k, label: DB.ents[k].n, sub: enKindPath(k), lead: avatar(k) })),
    on: k => { if (!k) return; const el = document.getElementById(tid); if (!el) return; const v = el.value, nv = v + (v && !/\s$/.test(v) ? ' ' : '') + `{e:${k}}`; el.value = nv; set(nv); el.focus({ preventScroll: true }); } });
}
function ennMove(i, d) { const s = ENN.secs, j = i + d; if (j < 0 || j >= s.length) return; [s[i], s[j]] = [s[j], s[i]]; setSheetBody(ennBody()); }
function ennDel(i) { ENN.secs.splice(i, 1); if (!ENN.secs.length) ENN.secs.push({ h: '', t: '' }); setSheetBody(ennBody()); }
function ennAdd() { ENN.secs.push({ h: '', t: '' }); setSheetBody(ennBody()); setTimeout(() => { const el = $$('.en-nsec', _sheets[_sheets.length - 1].el).pop(); if (el) { el.scrollIntoView({ block: 'nearest', behavior: 'smooth' }); const f = $('input', el); f && f.focus({ preventScroll: true }); } }, 30); }
function ennSave() {
  const secs = JSON.parse(ennSig()); if (!secs.length) return toast('本文を入れてください', { bad: 1 });
  if (ennSig() === ENN.orig) return closeSheet(true);
  done(noteSave(ENN.id, secs), { close: 1 });
}
