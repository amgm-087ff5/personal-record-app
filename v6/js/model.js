/* =====================================================================
   個人記録 v6 — データモデル(1か所)。画面はここの関数だけでデータを読み書きする。
   仕様の正本は v6/DESIGN.md 2章。計算で出せる値は保存しない。

   DB = {
     days  : { 'YYYY-MM-DD': {diary, photos:[{t:'HH:MM', cap, act?}]} }        日 = 記録ではなく「鍵」。日記と写真を持つ
     acts  : { id: Act }                                                         時間の上のもの全部(記録も予定も同じ表)
     exps  : { 支払いのactId: Exp }                                              支出 = 家計簿のエントリ(支払い1件に0か1件)
     tasks : { id: Task }                                                        ToDo
     lists : { id: {n, shop?} }                                                  ToDo のリスト
     ents  : { id: {n, types:[種類], facts:[{k,v,since?,hist:[{v,span,why}],rare?}]} }
     rels  : [ {a, b, role, inv?, since?, sym?} ]                                関係 = 辺の表(a から見て b は role。b から見ると inv。sym=同じ呼び名)
     types : { key: {n, p?, props:[項目名]} }                                    種類の木
     notes : { エンティティid: {upd, ver, body:[[見出し,本文]], subs:[[名前,補足,本文]]} }
     memos : { id: {body, at:'YYYY-MM-DD HH:MM', ents:[], act?, pin?} }
     cats  : { key: {n, p?, c?, fx?, sp?} }                                      カテゴリの木(fx=固定|変動|ask、sp=日常|非日常)
     methods: [支払い方法]
   }
   Act = { kind, title, date, date2?, start?, end?, allDay?, state:'done'|'plan', parent?, roles:[{r, e?, name?}],
           note?, rep?:{u:'day'|'week'|'month'}, alarm?, hol?, src?(元の繰り返し予定), planned?:{start,end}(予定の時の時刻),
           pay?: {amt?, payers:[{who,amt,how,name?}], items:[{n, price?, lines?:[{n,k:'amt'|'pct',v}], tax, users:[who], memo?, unk?, unread?}],
                  settled?, unread?:{amt?,payee?}, plan?:{amt,cat}} }
     - 予定と記録は同じもの。state が違うだけ(「記録にする」= state を done にする)。
     - 瞬間 = end が無い / 期間 = end がある。
     - 支払い = kind:'pay' で pay を持つ Act(事実: 誰がいくら払い、誰が何を使ったか)。
     - 繰り返しの予定はひな形。各回は id を「ひな形id@日付」と書く仮の回(保存しない)。記録すると src 付きの Act になる。
   Exp = { basis:'items'|'even'|'amount', n?, me?, cat, itemCat?:{番号:カテゴリ}, sp?, fx?, memo? }
   Task = { t, due?, done?(日付), list, pri?, act?(予定・記録のid), ents?:[], rep?, memo?, sub?:[{t,d}], payAct? }
   ===================================================================== */
'use strict';
let DB = null;
const STORE_KEY = 'prmock-v6';

/* ---------- 時計(本物の今。?now=2026-11-11T13:20 で固定できる) ---------- */
let TODAY = '', NOW = '';
const pad2 = n => String(n).padStart(2, '0');
const dISO = dt => `${dt.getFullYear()}-${pad2(dt.getMonth() + 1)}-${pad2(dt.getDate())}`;
function clockRead() {
  const q = new URLSearchParams(location.search).get('now');
  const dt = q ? new Date(q) : new Date();
  TODAY = dISO(dt); NOW = `${pad2(dt.getHours())}:${pad2(dt.getMinutes())}`;
}
clockRead();

/* ---------- 日付と時刻の小道具 ---------- */
const dObj = d => new Date(d + 'T00:00:00');
const dAdd = (d, n) => { const t = dObj(d); t.setDate(t.getDate() + n); return dISO(t); };
const dDiff = (a, b) => Math.round((dObj(a) - dObj(b)) / 864e5);           /* a - b(日) */
const dDow = d => dObj(d).getDay();
const DOW = '日月火水木金土';
const dowJ = d => DOW[dDow(d)];
const ymOf = d => d.slice(0, 7);
const ymAdd = (ym, n) => { const t = new Date(+ym.slice(0, 4), +ym.slice(5, 7) - 1 + n, 1); return `${t.getFullYear()}-${pad2(t.getMonth() + 1)}`; };
const ymDays = ym => new Date(+ym.slice(0, 4), +ym.slice(5, 7), 0).getDate();
const hm2m = t => t ? (+t.slice(0, 2)) * 60 + (+t.slice(3, 5)) : 0;
const m2hm = m => `${pad2(Math.floor(m / 60) % 24)}:${pad2(m % 60)}`;
const isHM = s => typeof s === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(s);
const isDate = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(dObj(s));
const fmtMD = d => `${+d.slice(5, 7)}/${+d.slice(8)}`;
const fmtMDJ = d => `${+d.slice(5, 7)}月${+d.slice(8)}日`;
const fmtFull = d => `${fmtMDJ(d)} ${dowJ(d)}曜`;
const fmtYM = ym => `${+ym.slice(0, 4)}年${+ym.slice(5, 7)}月`;
/** 今日・昨日・明日・n日前… */
function relDay(d) {
  const n = dDiff(d, TODAY);
  if (n === 0) return '今日'; if (n === -1) return '昨日'; if (n === 1) return '明日'; if (n === 2) return 'あさって';
  if (n > 0) return n < 7 ? `${n}日後` : n < 31 ? `${Math.round(n / 7)}週間後` : n < 365 ? `${Math.round(n / 30)}か月後` : `${Math.round(n / 365)}年後`;
  const m = -n; return m < 7 ? `${m}日前` : m < 31 ? `${Math.round(m / 7)}週間前` : m < 365 ? `${Math.round(m / 30)}か月前` : `${Math.round(m / 365)}年前`;
}
/** 分 → 「1時間10分」 */
const durJ = m => m <= 0 ? '' : m < 60 ? `${m}分` : m % 60 ? `${Math.floor(m / 60)}時間${m % 60}分` : `${m / 60}時間`;
const yen = n => '¥' + Math.round(n || 0).toLocaleString('ja-JP');
let _uid = 0;
const uid = p => `${p}${Date.now().toString(36)}${(_uid++).toString(36)}`;

/* ---------- 種類(アクティビティ)。span=期間が普通 / moment=瞬間が普通 ---------- */
const KINDS = {
  meal: { n: '食事', ic: 'meal' }, talk: { n: '会話', ic: 'talk' }, work: { n: '作業', ic: 'work' }, move: { n: '移動', ic: 'move' },
  shop: { n: '買い物', ic: 'bag' }, play: { n: '遊び', ic: 'spark' }, health: { n: '通院', ic: 'heart' }, trip: { n: '旅行', ic: 'trip' },
  event: { n: '出来事', ic: 'flag' }, wake: { n: '起床', ic: 'sun', moment: 1 }, sleep: { n: '就寝', ic: 'moon', moment: 1 },
  pay: { n: '支払い', ic: 'yen', moment: 1 }, holiday: { n: '祝日', ic: 'flag' },
};
const ROLES = ['場所', '同席', '参加', '出発', '到着', 'プロジェクト', '関連'];
const TAX_DEFAULT = 10;
const BASIS = { items: '使った分', even: '割り勘', amount: '金額を指定' };

/* =====================================================================
   読む(セレクタ)
   ===================================================================== */
/** Act を返す。「ひな形id@日付」は仮の回を作って返す */
function A(id) {
  if (!id) return null;
  const a = DB.acts[id]; if (a) return a;
  const i = id.indexOf('@'); if (i < 0) return null;
  const tpl = DB.acts[id.slice(0, i)]; if (!tpl) return null;
  return { ...tpl, date: id.slice(i + 1), virt: 1, tpl: id.slice(0, i) };
}
const E = id => id === 'me' ? { n: '自分', types: ['person'], facts: [] } : DB.ents[id] || null;
const isPay = a => !!(a && a.kind === 'pay');
const isSpan = a => !!(a && a.end);
const isPlan = a => !!(a && a.state === 'plan');
/** 過ぎたのに記録になっていない予定 */
function isLate(a) {
  if (!isPlan(a) || a.hol) return false;
  const d2 = a.date2 || a.date;
  if (d2 < TODAY) return true; if (d2 > TODAY) return false;
  return a.allDay || !a.start ? false : (a.end || a.start) <= NOW;
}
const timeText = a => !a ? '' : a.allDay || !a.start ? (a.date2 ? `${fmtMD(a.date)}–${fmtMD(a.date2)}` : '終日') : a.end ? `${a.start}–${a.end}` : a.start;
const actMins = a => isSpan(a) ? Math.max(0, hm2m(a.end) - hm2m(a.start)) : 0;
const sortKey = a => (a.allDay || !a.start ? '00:00' : a.start);
const byTime = (x, y) => sortKey(x[1]).localeCompare(sortKey(y[1])) || (isSpan(y[1]) - isSpan(x[1]));
/** 役割の相手の名前 */
const roleName = r => r.e && E(r.e) ? E(r.e).n : (r.name || '');
const rolesOf = (a, ...names) => (a && a.roles || []).filter(r => !names.length || names.includes(r.r));
const placeOf = a => { const r = rolesOf(a, '場所', '支払先', '到着')[0]; return r ? roleName(r) : ''; };
/** 支払先の名前 */
const payeeOf = a => { const r = rolesOf(a, '支払先')[0]; return r ? roleName(r) : ''; };
/** 題。支払いは「支払先」(無ければ「支払い」) */
const actTitle = a => !a ? '' : isPay(a) ? (payeeOf(a) || a.title || '支払い') : (a.title || KINDS[a.kind].n);

/* ---- 繰り返し ---- */
function repOn(t, d) {
  if (!t.rep || d < t.date) return false;
  if (t.rep.u === 'day') return true;
  if (t.rep.u === 'week') return dDow(d) === dDow(t.date);
  if (t.rep.u === 'month') return +d.slice(8) === Math.min(+t.date.slice(8), ymDays(ymOf(d)));
  return false;
}
const repText = t => !t || !t.rep ? '' : t.rep.u === 'day' ? '毎日' : t.rep.u === 'week' ? `毎週${dowJ(t.date)}曜` : `毎月${+t.date.slice(8)}日`;
/** 次の回の日付(今日以降) */
function repNext(t, from = TODAY) { for (let i = 0; i <= 366; i++) { const d = dAdd(from, i); if (repOn(t, d)) return d; } return t.date; }
let _recIdx = null;       /* 「ひな形id@日付」→ 記録済みか */
function recIdx() {
  if (_recIdx) return _recIdx; _recIdx = new Set();
  for (const a of Object.values(DB.acts)) if (a.src) _recIdx.add(a.src + '@' + a.date);
  return _recIdx;
}

/** その日の、いちばん外側のもの [[id, act]](時刻順)。繰り返しの仮の回も含む。o.state で絞れる */
function actsOn(d, o = {}) {
  const out = [];
  for (const [id, a] of Object.entries(DB.acts)) {
    if (a.parent) continue;
    if (a.rep) { if (repOn(a, d) && !recIdx().has(id + '@' + d)) out.push([id + '@' + d, A(id + '@' + d)]); continue; }
    if (a.date === d || (a.date2 && a.date <= d && d <= a.date2)) out.push([id, a]);
  }
  return out.filter(([, a]) => !o.state || a.state === o.state).sort(byTime);
}
/** 子(時刻順) */
const kidsOf = id => Object.entries(DB.acts).filter(([, a]) => a.parent === id).sort(byTime);
/** 子孫の id */
function descOf(id) { const out = []; const walk = k => kidsOf(k).forEach(([c]) => { out.push(c); walk(c); }); walk(id); return out; }
/** 祖先(外側が先)[[id, act]] */
function ancOf(id) { const r = []; let a = A(id); while (a && a.parent && DB.acts[a.parent]) { r.unshift([a.parent, DB.acts[a.parent]]); a = DB.acts[a.parent]; } return r; }
/** 記録のある日(新しい順ではなく昇順)。from〜to */
function daysWith(from, to) {
  const s = new Set();
  for (const a of Object.values(DB.acts)) { if (a.parent || a.rep) continue; if (a.date >= from && a.date <= to) s.add(a.date); if (a.date2) for (let d = a.date; d <= a.date2 && d <= to; d = dAdd(d, 1)) if (d >= from) s.add(d); }
  for (const d of Object.keys(DB.days)) if (d >= from && d <= to && (DB.days[d].diary || (DB.days[d].photos || []).length)) s.add(d);
  for (const [id, a] of Object.entries(DB.acts)) if (a.rep) for (let d = from > a.date ? from : a.date; d <= to; d = dAdd(d, 1)) if (repOn(a, d) && !recIdx().has(id + '@' + d) && d >= TODAY) s.add(d);
  return [...s].sort();
}
const dayOf = d => DB.days[d] || { diary: '', photos: [] };
/** 記録待ち: 過ぎたのに記録になっていない予定(直近 back 日) [[id, act]] */
function pendingPlans(back = 3) {
  const out = [];
  for (let i = back; i >= 0; i--) actsOn(dAdd(TODAY, -i), { state: 'plan' }).forEach(p => { if (isLate(p[1]) && !out.some(q => q[0] === p[0])) out.push(p); });
  return out;
}
/** これからの予定(今日のまだの分〜 days 日先)[[id, act, date]] */
function upcoming(days = 14) {
  const out = [], seen = new Set();
  for (let i = 0; i <= days; i++) { const d = dAdd(TODAY, i); actsOn(d, { state: 'plan' }).forEach(([id, a]) => { if (isLate(a) || a.hol || seen.has(id)) return; seen.add(id); out.push([id, a, d]); }); }
  return out;
}
/** その人・場所が関わったもの(親も関わっていれば親にまとめる)[[id, act, 役割]] */
function roleIn(a, eid) {
  const r = (a.roles || []).find(r => r.e === eid); if (r) return r.r;
  if (a.pay) { if (a.pay.payers.some(p => p.who === eid)) return '支払った'; if (a.pay.items.some(i => (i.users || []).includes(eid))) return '使った'; }
  return null;
}
function actsOfEnt(eid) {
  const hit = Object.entries(DB.acts).filter(([, a]) => !a.rep && roleIn(a, eid));
  const keys = new Set(hit.map(h => h[0]));
  return hit.filter(([id]) => !ancOf(id).some(([p]) => keys.has(p))).map(([id, a]) => [id, a, roleIn(a, eid)]).sort((x, y) => (y[1].date + sortKey(y[1])).localeCompare(x[1].date + sortKey(x[1])));
}

/* ---- 種類・関係・カテゴリ ---- */
const typeAnc = k => { const r = []; while (k && DB.types[k]) { r.unshift(k); k = DB.types[k].p; } return r; };
const typeKids = k => Object.keys(DB.types).filter(t => (DB.types[t].p || null) === (k || null));
const isUnder = (t, root) => typeAnc(t).includes(root);
const typePath = k => typeAnc(k).map(t => DB.types[t].n).join(' › ');
const propsOf = k => typeAnc(k).flatMap(t => (DB.types[t].props || []).map(p => [p, t]));
const entsOfType = (k, deep = true) => Object.keys(DB.ents).filter(id => DB.ents[id].types.some(t => deep ? isUnder(t, k) : t === k));
/** 主な種類の根(人物・場所…)。一覧のグループ分けに使う */
const entRoot = id => { const e = E(id); return e && e.types[0] ? typeAnc(e.types[0])[0] : null; };
/** 関係 [{role, other, since, i(rels の番号)}](自分側から見た呼び名) */
const relsOf = id => DB.rels.map((r, i) => r.a === id ? { role: r.role, other: r.b, since: r.since, i } : r.b === id ? { role: r.sym ? r.role : (r.inv || r.role), other: r.a, since: r.since, i } : null).filter(Boolean);
const catAnc = k => { const r = []; while (k && DB.cats[k]) { r.unshift(k); k = DB.cats[k].p; } return r; };
const catKids = k => Object.keys(DB.cats).filter(c => (DB.cats[c].p || null) === (k || null));
const catName = k => DB.cats[k] ? DB.cats[k].n : '未分類';
const catPath = k => catAnc(k).map(c => DB.cats[c].n).join(' › ') || '未分類';
const catRoot = k => catAnc(k)[0] || k;
const catColor = k => { for (const c of catAnc(k).reverse()) if (DB.cats[c].c) return DB.cats[c].c; return '#9AA6BC'; };
const _inh = (k, f) => { for (const c of catAnc(k).reverse()) if (DB.cats[c][f] !== undefined) return DB.cats[c][f] === 'ask' ? null : DB.cats[c][f]; return null; };
const catFx = k => _inh(k, 'fx'), catSp = k => _inh(k, 'sp');
const CAT_COLORS = ['#8A9AC0', '#6FB3B8', '#E08FA6', '#8CC49A', '#7FAAE3', '#A9D3EA', '#F0BBCB', '#E3B36B', '#A9B4C6'];

/* =====================================================================
   お金(支払い = 事実 / 支出 = 家計簿。同じことを2度入力しない)
   ===================================================================== */
const lnAmts = lines => { let s = 0; return lines.map((l, j) => { const a = l.k === 'pct' ? (j ? Math.round(s * (l.v || 0) / 100) : 0) : (l.v || 0); s += a; return a; }); };
/** 品目の金額(金額行があれば行の計算) */
const itemAmt = it => it.lines && it.lines.length ? lnAmts(it.lines).reduce((s, a) => s + a, 0) : (it.price || 0);
const itemTax = it => (it && it.tax) || TAX_DEFAULT;
const usersOf = it => it && it.users && it.users.length ? it.users : ['me'];
/** 品目(無ければ合計1行として扱う。virtual=1) */
const payItems = a => a.pay.items.length ? a.pay.items : [{ n: '', price: a.pay.amt || 0, users: ['me'], virtual: 1 }];
const payTotal = a => !a || !a.pay ? 0 : a.pay.items.length ? a.pay.items.reduce((s, i) => s + itemAmt(i), 0) : (a.pay.amt || (a.pay.plan ? a.pay.plan.amt : 0) || 0);
const paidOf = a => a.pay.payers.length ? a.pay.payers : [{ who: 'me', amt: payTotal(a), how: DB.methods[0] }];
const myPaid = a => paidOf(a).filter(p => p.who === 'me').reduce((s, p) => s + (p.amt || 0), 0);
const payMethod = a => [...new Set(paidOf(a).filter(p => p.who === 'me').map(p => p.how).filter(Boolean))].join('・');
const whoName = (w, name) => w === 'me' ? '自分' : w && E(w) ? E(w).n : (name || 'ほかの人');
const whoShort = (w, name) => w === 'me' ? '自分' : w && E(w) ? E(w).n.split(' ').pop() : (name || 'ほかの人');
/** この支払いに関わった人(自分を含む): 使った人・払った人・親の同席者 */
function peopleOf(id) {
  const a = A(id), s = new Set(['me']);
  payItems(a).forEach(i => usersOf(i).forEach(u => s.add(u))); paidOf(a).forEach(p => p.who && s.add(p.who));
  [[id, a], ...ancOf(id)].forEach(([, x]) => rolesOf(x, '同席', '参加').forEach(r => r.e && s.add(r.e)));
  return [...s];
}
const xOf = id => DB.exps[id] || null;
/** 品目ごとの自分の額 */
function xShares(id) {
  const a = A(id), x = xOf(id); if (!a || !a.pay || !x) return [];
  const its = payItems(a), amts = its.map(itemAmt), total = amts.reduce((s, v) => s + v, 0);
  if ((x.basis || 'items') === 'items') return its.map(it => { const u = usersOf(it); return u.includes('me') ? Math.floor(itemAmt(it) / u.length) : 0; });
  const me = x.basis === 'amount' ? Math.max(0, Math.min(x.me || 0, total)) : Math.floor(total / Math.max(1, x.n || 2));
  let left = me;
  return amts.map((v, i) => { const s = i === amts.length - 1 ? left : Math.min(left, total ? Math.floor(v * me / total) : 0); left -= s; return s; });
}
/** 自分の負担(支出が無ければ 0) */
const xAmt = id => xShares(id).reduce((s, v) => s + v, 0);
/** 支出の品目(表示用)[{i, n, price(自分の額), full, how:'全額'|'1/2'|'按分'|'奢り', cat, memo, unk}] */
function xItems(id) {
  const a = A(id), x = xOf(id); if (!a || !x) return [];
  const sh = xShares(id), basis = x.basis || 'items';
  return payItems(a).map((it, i) => {
    const full = itemAmt(it), u = usersOf(it);
    const how = !u.includes('me') && sh[i] > 0 ? '奢り' : basis === 'items' ? (sh[i] === full ? '全額' : `1/${u.length}`) : (sh[i] >= full ? '全額' : '按分');
    return { i, n: it.n || payeeOf(a) || '支払い', price: sh[i], full, how, cat: (x.itemCat && x.itemCat[i]) || x.cat, memo: it.memo, unk: it.unk };
  }).filter(o => o.price > 0);
}
/** カテゴリ配分 [{cat, amt, idx}](既定のカテゴリが先) */
function xCats(id) {
  const x = xOf(id); if (!x) return []; const m = new Map();
  xItems(id).forEach(o => { if (!m.has(o.cat)) m.set(o.cat, { cat: o.cat, amt: 0, idx: [] }); const g = m.get(o.cat); g.amt += o.price; g.idx.push(o.i); });
  if (!m.size) m.set(x.cat, { cat: x.cat, amt: 0, idx: [] });
  return [...m.values()].sort((p, q) => (q.cat === x.cat) - (p.cat === x.cat));
}
const xFx = id => { const x = xOf(id); return !x ? null : x.fx !== undefined ? x.fx : catFx(x.cat); };
const xSp = id => { const x = xOf(id); return !x ? null : x.sp !== undefined ? x.sp : catSp(x.cat); };
/** 貸し借り: amt>0 = 貸し(自分が多く払った) / <0 = 借り。who = 相手の id */
function balanceOf(id) {
  const a = A(id); if (!a || !a.pay || isPlan(a)) return { amt: 0, who: [], open: false, settled: false };
  const amt = myPaid(a) - xAmt(id);
  let w;
  if (amt > 0) { const u = new Set(); payItems(a).forEach(i => usersOf(i).forEach(x => x !== 'me' && u.add(x))); w = [...u]; if (!w.length) w = peopleOf(id).filter(p => p !== 'me'); }
  else w = paidOf(a).filter(p => p.who !== 'me').map(p => p.who);
  return { amt, who: w, settled: !!a.pay.settled, open: amt !== 0 && !a.pay.settled };
}
/** 「花子さんに貸し ¥2,340」 */
function balanceText(b) {
  if (!b || !b.amt) return '';
  const who = b.who.length ? b.who.map(w => whoShort(w) + (w && E(w) ? 'さん' : '')).join('・') : '相手';
  return `${who}に${b.amt > 0 ? '貸し' : '借り'} ${yen(Math.abs(b.amt))}`;
}
/** 未精算の支払い [[id, act, bal]](新しい順) */
const openBalances = () => Object.entries(DB.acts).filter(([, a]) => a.pay && !isPlan(a)).map(([id, a]) => [id, a, balanceOf(id)]).filter(r => r[2].open).sort((x, y) => y[1].date.localeCompare(x[1].date));
/** 相手ごとの差し引き [{who, amt(>0 貸し), ids}] */
function personBalances() {
  const m = new Map();
  openBalances().forEach(([id, , b]) => { const w = b.who[0] || 'other'; if (!m.has(w)) m.set(w, { who: w, amt: 0, ids: [] }); const g = m.get(w); g.amt += b.amt; g.ids.push(id); });
  return [...m.values()];
}
/** 支払いは割り勘や立替を含むか(棒を出すかどうか) */
const isShared = id => { const a = A(id); return !!a && !!a.pay && (peopleOf(id).length > 1 || payTotal(a) !== xAmt(id)); };
/** 読み取りの未確認の数 */
const unreadCount = a => !a || !a.pay ? 0 : (a.pay.unread ? Object.keys(a.pay.unread).length : 0) + a.pay.items.filter(i => i.unread).length;
/** 自分の負担の既定: 全部自分が使ったなら items。ほかの人がいれば null(1回だけ聞く) */
const basisGuess = items => items.every(i => usersOf(i).length === 1 && usersOf(i)[0] === 'me') ? 'items' : null;

/** 支払先からカテゴリを推し量る(同じ支払先の前回 → 支払先の種類 → 言葉)。同じことを2度入力しない */
function catGuess(payee, text = '') {
  if (payee) { const prev = Object.entries(DB.acts).filter(([id, a]) => a.pay && DB.exps[id] && rolesOf(a, '支払先').some(r => (payee.e && r.e === payee.e) || (payee.name && r.name === payee.name))).sort((x, y) => y[1].date.localeCompare(x[1].date))[0]; if (prev) return DB.exps[prev[0]].cat; }
  const t = (payee && payee.e && E(payee.e) ? E(payee.e).types.map(typePath).join(' ') : '') + ' ' + ((payee && payee.name) || '') + ' ' + text;
  const map = [[/ラーメン|飲食|ランチ|ディナー|定食|そば|寿司|外食|食堂/, 'eatout'], [/カフェ|コーヒー|パン|ベーカリー|ラテ/, 'cafe'], [/スーパー|八百|肉|魚|食材/, 'cook'], [/ドラッグ|薬|洗剤|日用/, 'daily'], [/駅|電車|JR|バス|Suica/, 'train'], [/タクシー/, 'taxi'], [/映画|本|書店|ゲーム/, 'hobby'], [/酒|ビール|居酒屋|バー/, 'alcohol'], [/宿|ホテル|旅/, 'travel']];
  for (const [re, k] of map) if (re.test(t) && DB.cats[k]) return k;
  return DB.cats.eatout ? 'eatout' : Object.keys(DB.cats)[0];
}

/* ---- 家計簿の集計 ---- */
/** その月の支出 [[actId, act, 自分の額]](新しい順) */
function expsIn(ym) {
  return Object.keys(DB.exps).map(id => [id, DB.acts[id]]).filter(([, a]) => a && !isPlan(a) && ymOf(a.date) === ym)
    .map(([id, a]) => [id, a, xAmt(id)]).sort((x, y) => (y[1].date + sortKey(y[1])).localeCompare(x[1].date + sortKey(x[1])));
}
/** 月のまとめ {total, fixed, variable, special, roots:[{cat, amt}], n} */
function monthSum(ym) {
  const r = { total: 0, fixed: 0, variable: 0, special: 0, roots: new Map(), n: 0 };
  expsIn(ym).forEach(([id, , amt]) => {
    r.total += amt; r.n++;
    if (xFx(id) === '固定') r.fixed += amt; else r.variable += amt;
    if (xSp(id) === '非日常') r.special += amt;
    xCats(id).forEach(g => { const k = catRoot(g.cat); r.roots.set(k, (r.roots.get(k) || 0) + g.amt); });
  });
  r.roots = [...r.roots.entries()].map(([cat, amt]) => ({ cat, amt })).sort((a, b) => b.amt - a.amt);
  return r;
}
/** カテゴリ k(子を含む)にその月に計上した額 */
const catSum = (ym, k) => expsIn(ym).reduce((s, [id]) => s + xCats(id).filter(g => catAnc(g.cat).includes(k)).reduce((t, g) => t + g.amt, 0), 0);
/** その日の自分の支出。o.noFixed で固定費を除く */
const daySpend = (d, o = {}) => Object.keys(DB.exps).filter(id => DB.acts[id] && DB.acts[id].date === d && !isPlan(DB.acts[id]) && !(o.noFixed && xFx(id) === '固定')).reduce((s, id) => s + xAmt(id), 0);
/** 変動費の累計(月の1日〜各日)[n1, n2, …] */
function paceOf(ym, upto) {
  const n = upto || ymDays(ym), arr = new Array(n).fill(0);
  expsIn(ym).forEach(([id, a, amt]) => { if (xFx(id) === '固定') return; const i = +a.date.slice(8) - 1; if (i < n) arr[i] += amt; });
  for (let i = 1; i < n; i++) arr[i] += arr[i - 1];
  return arr;
}

/* ---- エンティティの数字 ---- */
/** 一緒の時の自分の支出(関わった記録とその子孫の支払い。二重に数えない) */
function spendWith(eid) {
  return actsOfEnt(eid).filter(([, a]) => !isPlan(a)).reduce((s, [id]) => s + [id, ...descOf(id)].filter(k => DB.exps[k]).reduce((t, k) => t + xAmt(k), 0), 0);
}
function entStats(eid) {
  const acts = actsOfEnt(eid).filter(([, a]) => !isPlan(a));
  const y = TODAY.slice(0, 4);
  const weeks = new Array(12).fill(0);
  acts.forEach(([, a]) => { const w = Math.floor(dDiff(TODAY, a.date) / 7); if (w >= 0 && w < 12) weeks[11 - w]++; });
  const bal = personBalances().find(b => b.who === eid);
  return { last: acts[0] ? acts[0][1].date : null, year: acts.filter(([, a]) => a.date.startsWith(y)).length, total: acts.length, spend: spendWith(eid), weeks, bal: bal ? bal.amt : 0, next: upcoming(60).find(([, a]) => roleIn(a, eid)) || null };
}

/* ---- ToDo ---- */
const taskOpen = t => !t.done;
const taskLate = t => taskOpen(t) && t.due && t.due < TODAY;
/** 今日まで(期限が今日以前の未完了)。件数はどこでもこれで数える */
const tasksDue = () => Object.entries(DB.tasks).filter(([, t]) => taskOpen(t) && t.due && t.due <= TODAY);
const tasksOfAct = id => Object.entries(DB.tasks).filter(([, t]) => t.act === id || (id.includes('@') && t.act === id.split('@')[0]));
const tasksOfEnt = eid => Object.entries(DB.tasks).filter(([, t]) => (t.ents || []).includes(eid));

/* ---- メモ ---- */
const memosSorted = () => Object.entries(DB.memos).sort((a, b) => (b[1].pin ? 1 : 0) - (a[1].pin ? 1 : 0) || b[1].at.localeCompare(a[1].at));
const memosOn = d => Object.entries(DB.memos).filter(([, m]) => m.at.slice(0, 10) === d).sort((a, b) => a[1].at.localeCompare(b[1].at));
const nowStamp = () => `${TODAY} ${NOW}`;

/* ---- 思い出す: 同じ月日の過去(1年前・1か月前・1週間前)[{label, date}](記録のある日だけ) ---- */
function recallDays() {
  const out = [], has = d => actsOn(d, { state: 'done' }).length || dayOf(d).diary;
  const y = `${+TODAY.slice(0, 4) - 1}${TODAY.slice(4)}`; if (isDate(y) && has(y)) out.push({ label: '1年前の今日', date: y });
  const m = ymAdd(ymOf(TODAY), -1) + TODAY.slice(7); if (isDate(m) && has(m)) out.push({ label: '1か月前の今日', date: m });
  const w = dAdd(TODAY, -7); if (has(w)) out.push({ label: `先週の${dowJ(w)}曜`, date: w });
  return out;
}

/* ---- さがす: 全部を横断 [{type:'act'|'ent'|'memo'|'task'|'note'|'item'|'day', id, title, sub, date, href}] ---- */
function searchAll(q) {
  const ws = String(q || '').toLowerCase().split(/\s+/).filter(Boolean); if (!ws.length) return [];
  const hit = s => { const t = String(s || '').toLowerCase(); return ws.every(w => t.includes(w)); };
  const out = [];
  for (const [id, a] of Object.entries(DB.acts)) {
    const names = (a.roles || []).map(roleName).join(' ');
    if (hit(`${actTitle(a)} ${KINDS[a.kind].n} ${names} ${a.note || ''}`)) out.push({ type: 'act', id, title: actTitle(a), sub: `${fmtFull(a.date)} ${timeText(a)}`, date: a.date, href: `#/a/${id}`, amt: a.pay ? payTotal(a) : 0, plan: isPlan(a) });
    if (a.pay) a.pay.items.forEach((it, i) => { if (hit(`${it.n} ${it.memo || ''}`)) out.push({ type: 'item', id: id + '/' + i, title: it.n, sub: `${payeeOf(a) || '支払い'} · ${fmtFull(a.date)}`, date: a.date, href: `#/a/${id}`, amt: itemAmt(it) }); });
  }
  for (const [id, e] of Object.entries(DB.ents)) if (hit(`${e.n} ${e.types.map(t => DB.types[t] ? DB.types[t].n : '').join(' ')} ${e.facts.map(f => f.v).join(' ')}`)) out.push({ type: 'ent', id, title: e.n, sub: e.types.map(t => DB.types[t].n).join('・'), date: '', href: `#/e/${id}` });
  for (const [id, m] of Object.entries(DB.memos)) if (hit(m.body)) out.push({ type: 'memo', id, title: m.body, sub: m.at, date: m.at.slice(0, 10), href: `#/memo?focus=${id}` });
  for (const [id, t] of Object.entries(DB.tasks)) if (hit(`${t.t} ${t.memo || ''}`)) out.push({ type: 'task', id, title: t.t, sub: t.done ? '完了' : t.due ? `期限 ${fmtMD(t.due)}` : '期限なし', date: t.due || '', href: `#/task/${id}`, done: !!t.done });
  for (const [id, n] of Object.entries(DB.notes)) if (E(id) && hit(n.body.map(b => b.join(' ')).join(' ') + ' ' + n.subs.map(s => s.join(' ')).join(' '))) out.push({ type: 'note', id, title: `${E(id).n}のノート`, sub: `更新 ${n.upd}`, date: n.upd.slice(0, 10), href: `#/n/${id}` });
  for (const [d, o] of Object.entries(DB.days)) if (hit(o.diary)) out.push({ type: 'day', id: d, title: o.diary, sub: `${fmtFull(d)} の日記`, date: d, href: `#/day/${d}` });
  return out.sort((a, b) => (b.date || '').localeCompare(a.date || ''));
}

/* =====================================================================
   書く(変更)。全て commit を通す。戻り値 {ok:true, label, undo(), …} か {ok:false, why}
   取り消しは行単位(後から別の所を変えていても、その変更だけを戻す)
   ===================================================================== */
const TABLES = ['days', 'acts', 'exps', 'tasks', 'lists', 'ents', 'types', 'notes', 'memos', 'cats'];
function _snap() { const s = {}; for (const t of TABLES) { s[t] = {}; for (const k in DB[t]) s[t][k] = JSON.stringify(DB[t][k]); } s._rels = JSON.stringify(DB.rels); s._methods = JSON.stringify(DB.methods); return s; }
class Fail extends Error { }
const fail = why => { throw new Fail(why); };
function commit(label, fn) {
  const before = _snap(); let extra;
  try { extra = fn() || {}; } catch (e) {
    if (!(e instanceof Fail)) throw e;
    for (const t of TABLES) { const cur = DB[t]; for (const k of Object.keys(cur)) if (!(k in before[t])) delete cur[k]; for (const k in before[t]) if (JSON.stringify(cur[k]) !== before[t][k]) cur[k] = JSON.parse(before[t][k]); }
    DB.rels = JSON.parse(before._rels); DB.methods = JSON.parse(before._methods);
    return { ok: false, why: e.message };
  }
  const after = _snap(), patch = [];
  for (const t of TABLES) { for (const k in after[t]) if (before[t][k] !== after[t][k]) patch.push([t, k, before[t][k]]); for (const k in before[t]) if (!(k in after[t])) patch.push([t, k, before[t][k]]); }
  const relsB = before._rels !== after._rels ? before._rels : null, metB = before._methods !== after._methods ? before._methods : null;
  _recIdx = null; save();
  return {
    ok: true, label, ...extra, undo() {
      for (const [t, k, prev] of patch) { if (prev === undefined) delete DB[t][k]; else DB[t][k] = JSON.parse(prev); }
      if (relsB) DB.rels = JSON.parse(relsB); if (metB) DB.methods = JSON.parse(metB);
      _recIdx = null; save();
    }
  };
}
const _str = (v, msg) => { const s = String(v == null ? '' : v).trim(); if (!s && msg) fail(msg); return s; };

/* ---- 日 ---- */
const daySetDiary = (d, text) => commit('日記を保存', () => { DB.days[d] = { photos: [], ...DB.days[d], diary: String(text || '').trim() }; });
const dayAddPhoto = (d, cap) => commit('写真を追加', () => { const o = DB.days[d] = { diary: '', photos: [], ...DB.days[d] }; o.photos = [...(o.photos || []), { t: d === TODAY ? NOW : '12:00', cap: String(cap || '') }]; });
const dayRemovePhoto = (d, i) => commit('写真を外した', () => { const o = DB.days[d]; if (!o || !o.photos[i]) fail('写真が見つかりません'); o.photos = o.photos.filter((_, j) => j !== i); });

/* ---- アクティビティ(記録・予定) ---- */
function _cleanAct(o, old) {
  const a = { ...old, ...o };
  if (!KINDS[a.kind]) fail('種類を選んでください');
  if (a.kind !== 'pay') a.title = _str(a.title, '題を入れてください');
  if (!isDate(a.date)) fail('日付を入れてください');
  if (a.date2 && (!isDate(a.date2) || a.date2 < a.date)) fail('終わりの日は始まりの日より後にしてください');
  if (a.date2 === a.date) delete a.date2;
  if (a.allDay) { delete a.start; delete a.end; }
  else {
    if (!isHM(a.start)) fail('時刻を入れてください');
    if (a.end) { if (!isHM(a.end)) fail('終わりの時刻を入れてください'); if (a.end <= a.start) fail('終わりは始まりより後にしてください'); } else delete a.end;
  }
  if (a.parent && !DB.acts[a.parent]) delete a.parent;
  a.roles = (a.roles || []).filter(r => r.e || r.name).map(r => r.e ? { r: r.r, e: r.e } : { r: r.r, name: String(r.name).trim() });
  a.state = a.state === 'plan' ? 'plan' : 'done';
  for (const k of ['note', 'alarm', 'rep', 'hol', 'allDay', 'planned', 'src']) if (!a[k]) delete a[k];
  return a;
}
/** 作る。o = Act の項目(kind, title, date, start, end?, state, parent?, roles, note…)。→ {id} */
const actAdd = o => commit(o.state === 'plan' ? '予定を追加' : '記録を追加', () => { const id = uid('a'); DB.acts[id] = _cleanAct({ roles: [], ...o }); return { id }; });
/** 直す(渡した項目だけ) */
const actUpdate = (id, patch) => commit('直しました', () => { const a = DB.acts[id]; if (!a) fail('見つかりません'); DB.acts[id] = _cleanAct(patch, a); });
/** 消す(子・支出・結び付きごと) */
const actRemove = id => commit('消しました', () => {
  if (!DB.acts[id]) fail('見つかりません');
  const ids = [id, ...descOf(id)];
  ids.forEach(k => { delete DB.acts[k]; delete DB.exps[k]; });
  for (const t of Object.values(DB.tasks)) { if (ids.includes(t.act)) delete t.act; if (ids.includes(t.payAct)) delete t.payAct; }
  for (const m of Object.values(DB.memos)) if (ids.includes(m.act)) delete m.act;
  return { n: ids.length };
});
/** 親を変える(null で外へ出す)。自分の中へは入れられない */
const actMove = (id, parent) => commit('移しました', () => {
  const a = DB.acts[id]; if (!a) fail('見つかりません');
  if (parent) { const p = DB.acts[parent]; if (!p || p.kind === 'pay') fail('入れ先が見つかりません'); if (parent === id || descOf(id).includes(parent)) fail('自分の中へは入れられません'); a.parent = parent; a.date = p.date; }
  else delete a.parent;
});
/** 予定を記録にする。id は予定(仮の回「ひな形@日付」も可)。o で時刻などを直せる → {id} */
const planRecord = (id, o = {}) => commit('記録にしました', () => {
  const src = A(id); if (!src || !isPlan(src)) fail('予定が見つかりません');
  const planned = src.allDay || !src.start ? null : { start: src.start, end: src.end };
  const base = { ...src, ...o, state: 'done' }; delete base.virt; delete base.rep; delete base.alarm;
  if (base.allDay && !o.start && src.kind !== 'pay') { /* 終日はそのまま */ }
  if (planned && (planned.start !== base.start || planned.end !== base.end)) base.planned = planned;
  let nid = id;
  if (src.virt) { nid = uid('a'); base.src = src.tpl; delete base.tpl; }
  if (base.pay && base.pay.plan) {            /* 支払い予定 → 支払い + 支出 */
    const pl = base.pay.plan, amt = o.amt || pl.amt;
    base.pay = { payers: [{ who: 'me', amt, how: o.how || pl.how || DB.methods[0] }], items: [], amt };
    if (base.allDay) { delete base.allDay; base.start = o.start || '09:00'; }
    DB.exps[nid] = { basis: 'items', cat: pl.cat };
  }
  DB.acts[nid] = _cleanAct(base);
  if (src.virt) for (const t of Object.values(DB.tasks)) if (t.act === src.tpl && !t.done) { /* ひな形の ToDo はそのまま */ }
  return { id: nid };
});

/* ---- 支払い(事実)と支出(家計簿) ---- */
function _cleanItems(items) {
  return (items || []).map(i => {
    const r = { n: _str(i.n), tax: i.tax === 8 ? 8 : TAX_DEFAULT, users: (i.users && i.users.length ? i.users : ['me']).slice() };
    const lines = (i.lines || []).map((l, j) => ({ n: j ? _str(l.n) || ((l.v || 0) < 0 ? '割引' : 'オプション') : '', k: l.k === 'pct' ? 'pct' : 'amt', v: +l.v || 0 })).filter((l, j) => j === 0 || l.v !== 0);
    if (lines.length > 1) r.lines = lines; else r.price = lines.length ? lines[0].v : (+i.price || 0);
    if (i.memo) r.memo = String(i.memo); if (i.unk) r.unk = 1; if (i.unread) r.unread = 1;
    return r;
  }).filter(i => i.n || itemAmt(i));
}
function _fitPayers(payers, total) {
  let p = (payers || []).filter(x => x.who).map(x => ({ who: x.who, amt: +x.amt || 0, how: x.how || DB.methods[0], ...(x.name ? { name: x.name } : {}) }));
  if (!p.length) p = [{ who: 'me', amt: total, how: DB.methods[0] }];
  if (p.length === 1) p[0].amt = total;
  else if (p.reduce((s, x) => s + x.amt, 0) !== total) fail(`払った額の合計を ${yen(total)} に合わせてください`);
  return p;
}
function _fitExp(id, x) {
  const a = DB.acts[id]; if (!a || !a.pay) return;
  if (!x) { delete DB.exps[id]; return; }
  const e = { basis: BASIS[x.basis] ? x.basis : 'items', cat: DB.cats[x.cat] ? x.cat : Object.keys(DB.cats)[0] };
  if (e.basis === 'even') e.n = Math.max(2, +x.n || peopleOf(id).length || 2);
  if (e.basis === 'amount') e.me = Math.max(0, +x.me || 0);
  if (x.itemCat) { const ic = {}; Object.entries(x.itemCat).forEach(([k, v]) => { if (DB.cats[v] && v !== e.cat && +k < a.pay.items.length) ic[k] = v; }); if (Object.keys(ic).length) e.itemCat = ic; }
  if (x.sp && x.sp !== catSp(e.cat)) e.sp = x.sp; if (x.fx && x.fx !== catFx(e.cat)) e.fx = x.fx; if (x.memo) e.memo = String(x.memo);
  DB.exps[id] = e;
  if (xAmt(id) <= 0) delete DB.exps[id];     /* 自分の負担が 0 なら支出は無い */
}
/** 支払いを保存(作る・直す)。o = {id?, date, start, parent?, payee:{e?|name?}, roles?, items:[…], amt?(品目なしの合計), payers?, settled?, unread?, note?,
    exp:{basis, n?, me?, cat, itemCat?, sp?, fx?, memo?}} → {id} */
const paySave = o => commit(o.id ? '支払いを保存しました' : '支払いを記録しました', () => {
  const id = o.id || uid('p'), old = DB.acts[id] || {};
  const items = _cleanItems(o.items), total = items.length ? items.reduce((s, i) => s + itemAmt(i), 0) : (+o.amt || 0);
  if (total <= 0) fail('金額を入れてください');
  const roles = [...(o.payee && (o.payee.e || o.payee.name) ? [{ r: '支払先', ...o.payee }] : []), ...(o.roles || []).filter(r => r.r !== '支払先')];
  const pay = { payers: _fitPayers(o.payers, total), items };
  if (!items.length) pay.amt = total; if (o.settled) pay.settled = 1;
  if (o.unread && Object.keys(o.unread).length) pay.unread = { ...o.unread };
  DB.acts[id] = _cleanAct({ ...old, kind: 'pay', title: '', date: o.date, start: o.start || NOW, end: null, allDay: 0, state: 'done', parent: o.parent || null, roles, note: o.note, pay });
  if (!DB.acts[id].parent) delete DB.acts[id].parent;
  _fitExp(id, o.exp || DB.exps[id] || { basis: 'items', cat: 'eatout' });
  return { id };
});
/** 支出だけ直す(カテゴリ・決め方・特別感・メモ・品目別カテゴリ) */
const expUpdate = (id, patch) => commit('支出を直しました', () => { const a = DB.acts[id]; if (!a || !a.pay) fail('支払いが見つかりません'); _fitExp(id, { ...(DB.exps[id] || { basis: 'items', cat: 'eatout' }), ...patch }); });
/** 品目を消す(最後の1件も可。その時は合計だけ残る) */
const itemRemove = (id, ix) => commit('品目を消しました', () => {
  const a = DB.acts[id]; if (!a || !a.pay || !a.pay.items[ix]) fail('品目が見つかりません');
  const oldTotal = payTotal(a); a.pay.items = a.pay.items.filter((_, i) => i !== ix);
  const x = DB.exps[id];
  if (x && x.itemCat) { const ic = {}; Object.entries(x.itemCat).forEach(([k, v]) => { const n = +k; if (n !== ix) ic[n > ix ? n - 1 : n] = v; }); if (Object.keys(ic).length) x.itemCat = ic; else delete x.itemCat; }
  if (!a.pay.items.length) a.pay.amt = oldTotal;
  else { const t = payTotal(a); if (a.pay.payers.length === 1) a.pay.payers[0].amt = t; else { let diff = oldTotal - t; for (let i = a.pay.payers.length - 1; i >= 0 && diff > 0; i--) { const take = Math.min(diff, a.pay.payers[i].amt); a.pay.payers[i].amt -= take; diff -= take; } } }
  if (x) _fitExp(id, x);
});
/** 精算済みにする / 戻す */
const paySettle = (ids, on = true) => commit(on ? '精算済みにしました' : '未精算に戻しました', () => { [].concat(ids).forEach(id => { const a = DB.acts[id]; if (!a || !a.pay) fail('支払いが見つかりません'); if (on) a.pay.settled = 1; else delete a.pay.settled; }); });
/** 読み取りを確かめた(未確認を全部消す) */
const payConfirm = id => commit('確かめました', () => { const a = DB.acts[id]; if (!a || !a.pay) fail('支払いが見つかりません'); delete a.pay.unread; a.pay.items.forEach(i => delete i.unread); });

/* ---- ToDo ---- */
const taskAdd = o => commit('ToDoを追加', () => { const id = uid('t'); const t = { t: _str(o.t, 'やることを入れてください'), list: DB.lists[o.list] ? o.list : Object.keys(DB.lists)[0] }; for (const k of ['due', 'pri', 'act', 'memo', 'rep']) if (o[k]) t[k] = o[k]; if (o.ents && o.ents.length) t.ents = o.ents.slice(); if (o.sub && o.sub.length) t.sub = o.sub; DB.tasks[id] = t; return { id }; });
const taskUpdate = (id, patch) => commit('ToDoを直しました', () => { const t = DB.tasks[id]; if (!t) fail('ToDoが見つかりません'); const n = { ...t, ...patch }; n.t = _str(n.t, 'やることを入れてください'); for (const k of Object.keys(n)) if (n[k] == null || n[k] === '' || (Array.isArray(n[k]) && !n[k].length)) delete n[k]; if (!n.list) n.list = t.list; DB.tasks[id] = n; });
/** 完了を切り替える。繰り返しは次の回を作る */
const taskToggle = id => commit('', () => {
  const t = DB.tasks[id]; if (!t) fail('ToDoが見つかりません');
  if (t.done) { delete t.done; return { done: false }; }
  t.done = TODAY;
  if (t.rep && t.due) { const step = t.rep.u === 'day' ? 1 : t.rep.u === 'week' ? 7 : 0; const nd = step ? dAdd(t.due, step) : ymAdd(ymOf(t.due), 1) + t.due.slice(7); DB.tasks[uid('t')] = { ...JSON.parse(JSON.stringify(t)), due: nd, done: undefined, sub: (t.sub || []).map(s => ({ t: s.t })) }; }
  return { done: true };
});
const taskRemove = id => commit('ToDoを消しました', () => { if (!DB.tasks[id]) fail('ToDoが見つかりません'); delete DB.tasks[id]; });
const listAdd = n => commit('リストを追加', () => { const id = uid('l'); DB.lists[id] = { n: _str(n, 'リストの名前を入れてください') }; return { id }; });
const listRemove = id => commit('リストを消しました', () => { if (Object.keys(DB.lists).length <= 1) fail('リストは1つ以上必要です'); if (Object.values(DB.tasks).some(t => t.list === id && !t.done)) fail('未完了のToDoがあるので先に移してください'); delete DB.lists[id]; const f = Object.keys(DB.lists)[0]; Object.values(DB.tasks).forEach(t => { if (t.list === id) t.list = f; }); });
/** 「明日 牛乳 #買い物 !」のような1行を解釈 → {t, due?, list?, pri?} */
function parseTask(s) {
  let t = ' ' + String(s || '').trim() + ' ', due, list, pri;
  t = t.replace(/[!！]/g, () => { pri = 1; return ''; });
  t = t.replace(/[#＃](\S+)/, (_, n) => { const k = Object.keys(DB.lists).find(k => DB.lists[k].n === n); if (k) { list = k; return ''; } return _; });
  const d = parseDateWord(t); if (d) { due = d.date; t = t.replace(d.word, ' '); }
  return { t: t.replace(/\s+/g, ' ').trim(), due, list, pri };
}
/** 文の中の日付の言葉 → {date, word} */
function parseDateWord(t) {
  let m;
  if ((m = t.match(/今日|きょう/))) return { date: TODAY, word: m[0] };
  if ((m = t.match(/明日|あした/))) return { date: dAdd(TODAY, 1), word: m[0] };
  if ((m = t.match(/明後日|あさって/))) return { date: dAdd(TODAY, 2), word: m[0] };
  if ((m = t.match(/昨日|きのう/))) return { date: dAdd(TODAY, -1), word: m[0] };
  if ((m = t.match(/(来週)?([日月火水木金土])曜日?/))) { let n = (DOW.indexOf(m[2]) - dDow(TODAY) + 7) % 7; if (m[1]) n += 7; else if (n === 0) n = 7; return { date: dAdd(TODAY, n), word: m[0] }; }
  if ((m = t.match(/(\d{1,2})[\/月](\d{1,2})日?/))) { let d = `${TODAY.slice(0, 4)}-${pad2(m[1])}-${pad2(m[2])}`; if (isDate(d)) { if (dDiff(d, TODAY) < -180) d = `${+TODAY.slice(0, 4) + 1}${d.slice(4)}`; return { date: d, word: m[0] }; } }
  return null;
}
/** すばやく書く: 1行から種類を推し量る → {type:'pay'|'plan'|'task'|'memo', …}
    金額があれば支払い / これからの日時があれば予定 / 「#リスト」や「〜する」の形は ToDo / それ以外はメモ */
function parseQuick(s) {
  const raw = String(s || '').trim(); if (!raw) return null;
  let t = ' ' + raw + ' ', time, time2;
  const d = parseDateWord(t); if (d) t = t.replace(d.word, ' ');
  t = t.replace(/(\d{1,2})[:：](\d{2})(?:\s*[-–〜~]\s*(\d{1,2})[:：](\d{2}))?/, (_, h, mi, h2, m2) => { time = `${pad2(h)}:${mi}`; if (h2) time2 = `${pad2(h2)}:${m2}`; return ' '; });
  t = t.replace(/(\d{1,2})時(半|(\d{1,2})分)?/, (_, h, half, mi) => { if (time) return _; time = `${pad2(h)}:${half === '半' ? '30' : pad2(mi || 0)}`; return ' '; });
  let amt; t = t.replace(/[¥￥]\s?([\d,]+)|([\d,]{3,})\s?円|(?:^|\s)([\d,]{3,})(?=\s|$)/, (_, a, b, c) => { amt = +(a || b || c).replace(/,/g, ''); return ' '; });
  const ents = Object.keys(DB.ents).filter(id => { const e = DB.ents[id], short = e.n.split(' ').pop(); return raw.includes(e.n) || (short.length > 1 && raw.includes(short)); });
  const text = t.replace(/\s+/g, ' ').trim();
  if (amt) { const pe = ents.find(id => isUnder(DB.ents[id].types[0], 'place')); return { type: 'pay', amt, date: d && d.date <= TODAY ? d.date : TODAY, start: time || NOW, payee: pe ? { e: pe } : text ? { name: text } : null, text, ents }; }
  const date = d ? d.date : TODAY;
  if ((d && date > TODAY && (time || !/[#＃]/.test(raw))) || (time && (date > TODAY || time > NOW))) return { type: 'plan', title: text || '予定', date, start: time, end: time2, ents };
  if (/[#＃!！]/.test(raw) || (d && date >= TODAY)) { const p = parseTask(raw); return { type: 'task', ...p, ents }; }
  return { type: 'memo', body: raw, ents };
}

/* ---- メモ ---- */
const memoAdd = (body, o = {}) => commit('メモを保存しました', () => { const id = uid('m'); const m = { body: _str(body, '本文を入れてください'), at: nowStamp(), ents: (o.ents || []).slice() }; if (o.act) m.act = o.act; DB.memos[id] = m; return { id }; });
const memoUpdate = (id, patch) => commit('メモを直しました', () => { const m = DB.memos[id]; if (!m) fail('メモが見つかりません'); if (patch.body !== undefined) m.body = _str(patch.body, '本文を入れてください'); if (patch.ents) m.ents = patch.ents.slice(); if ('act' in patch) { if (patch.act) m.act = patch.act; else delete m.act; } });
const memoPin = id => commit('', () => { const m = DB.memos[id]; if (!m) fail('メモが見つかりません'); if (m.pin) delete m.pin; else m.pin = 1; return { pin: !!m.pin }; });
const memoRemove = id => commit('メモを消しました', () => { if (!DB.memos[id]) fail('メモが見つかりません'); delete DB.memos[id]; });

/* ---- エンティティ・関係・種類・ノート ---- */
const entAdd = o => commit('エンティティを追加', () => { const n = _str(o.n, '名前を入れてください'); const types = (o.types || []).filter(t => DB.types[t]); if (!types.length) fail('種類を選んでください'); const id = uid('e'); DB.ents[id] = { n, types, facts: (o.facts || []).filter(f => f.k && f.v).map(f => ({ k: f.k, v: f.v })) }; return { id }; });
const entUpdate = (id, patch) => commit('直しました', () => {
  const e = DB.ents[id]; if (!e) fail('エンティティが見つかりません');
  if (patch.n !== undefined) e.n = _str(patch.n, '名前を入れてください');
  if (patch.types) { const t = patch.types.filter(k => DB.types[k]); if (!t.length) fail('種類は1つ以上必要です'); e.types = t; }
  if (patch.facts) e.facts = patch.facts.filter(f => f.k && String(f.v || '').trim());
});
/** 事実の値を変える。keepHist なら前の値を履歴に残す(why = 理由、since = いつから) */
const factSet = (id, k, v, o = {}) => commit('直しました', () => {
  const e = DB.ents[id]; if (!e) fail('エンティティが見つかりません'); const val = _str(v);
  const f = e.facts.find(f => f.k === k);
  if (!f) { if (val) e.facts.push({ k, v: val, ...(o.since ? { since: o.since } : {}) }); return; }
  if (!val) { e.facts = e.facts.filter(x => x !== f); return; }
  if (o.keepHist && f.v !== val) { f.hist = [{ v: f.v, span: `${f.since || ''}〜${o.since || ymOf(TODAY)}`, why: o.why || '' }, ...(f.hist || [])]; f.since = o.since || ymOf(TODAY); }
  f.v = val;
});
/** 消す(参照は名前を残して外す) */
const entRemove = id => commit('エンティティを消しました', () => {
  const e = DB.ents[id]; if (!e) fail('エンティティが見つかりません');
  for (const a of Object.values(DB.acts)) {
    a.roles = (a.roles || []).map(r => r.e === id ? { r: r.r, name: e.n } : r);
    if (a.pay) { a.pay.payers.forEach(p => { if (p.who === id) { p.who = 'other'; p.name = e.n; } }); a.pay.items.forEach(i => { if (i.users) i.users = i.users.map(u => u === id ? 'other' : u); }); }
  }
  for (const t of Object.values(DB.tasks)) if (t.ents) { t.ents = t.ents.filter(x => x !== id); if (!t.ents.length) delete t.ents; }
  for (const m of Object.values(DB.memos)) m.ents = (m.ents || []).filter(x => x !== id);
  DB.rels = DB.rels.filter(r => r.a !== id && r.b !== id);
  delete DB.notes[id]; delete DB.ents[id];
});
const relAdd = o => commit('関係を追加', () => { if (!DB.ents[o.a] && o.a !== 'me') fail('相手を選んでください'); if (!DB.ents[o.b]) fail('相手を選んでください'); if (o.a === o.b) fail('自分自身とは結べません'); DB.rels = [...DB.rels, { a: o.a, b: o.b, role: _str(o.role, '関係の名前を入れてください'), ...(o.inv ? { inv: o.inv } : { sym: 1 }), ...(o.since ? { since: o.since } : {}) }]; });
const relRemove = i => commit('関係を外しました', () => { if (!DB.rels[i]) fail('関係が見つかりません'); DB.rels = DB.rels.filter((_, j) => j !== i); });
const typeAdd = (n, p) => commit('種類を追加', () => { const key = uid('ty'); DB.types[key] = { n: _str(n, '名前を入れてください'), props: [], ...(p && DB.types[p] ? { p } : {}) }; return { key }; });
const typeUpdate = (key, patch) => commit('種類を直しました', () => {
  const t = DB.types[key]; if (!t) fail('種類が見つかりません');
  if (patch.n !== undefined) t.n = _str(patch.n, '名前を入れてください');
  if ('p' in patch) { if (patch.p && (!DB.types[patch.p] || typeAnc(patch.p).includes(key))) fail('自分の中へは移せません'); if (patch.p) t.p = patch.p; else delete t.p; }
});
const typeRemove = key => commit('種類を消しました', () => { if (!DB.types[key]) fail('種類が見つかりません'); if (typeKids(key).length) fail('中に種類があるので、先に移すか消してください'); if (entsOfType(key, false).length) fail('この種類のエンティティがあるので、先に外してください'); delete DB.types[key]; });
const typePropAdd = (key, n) => commit('項目を追加', () => { const t = DB.types[key]; if (!t) fail('種類が見つかりません'); const v = _str(n, '項目の名前を入れてください'); if (propsOf(key).some(p => p[0] === v)) fail('同じ名前の項目があります'); t.props = [...(t.props || []), v]; });
const typePropRename = (key, a, b) => commit('項目の名前を直しました', () => { const t = DB.types[key]; const v = _str(b, '項目の名前を入れてください'); if (!t || !(t.props || []).includes(a)) fail('項目が見つかりません'); if (v !== a && propsOf(key).some(p => p[0] === v)) fail('同じ名前の項目があります'); t.props = t.props.map(p => p === a ? v : p); entsOfType(key).forEach(id => DB.ents[id].facts.forEach(f => { if (f.k === a) f.k = v; })); });
const typePropRemove = (key, n) => commit('項目を消しました', () => { const t = DB.types[key]; if (!t || !(t.props || []).includes(n)) fail('項目が見つかりません'); t.props = t.props.filter(p => p !== n); });
/** ノートを保存(版 +1) */
const noteSave = (eid, body) => commit('ノートを保存しました', () => { if (!DB.ents[eid]) fail('エンティティが見つかりません'); const old = DB.notes[eid]; DB.notes[eid] = { upd: nowStamp(), ver: ((old && old.ver) || 0) + 1, body: (body || []).map(([h, t]) => [String(h || ''), String(t || '')]).filter(b => b[0] || b[1]), subs: old ? old.subs : [] }; });
const noteSubSave = (eid, i, sub) => commit('サブページを保存しました', () => { const n = DB.notes[eid] || (DB.ents[eid] ? (DB.notes[eid] = { upd: nowStamp(), ver: 1, body: [], subs: [] }) : fail('エンティティが見つかりません')); const s = [_str(sub[0], '名前を入れてください'), String(sub[1] || ''), String(sub[2] || '')]; n.subs = i == null || i < 0 ? [...n.subs, s] : n.subs.map((x, j) => j === i ? s : x); n.upd = nowStamp(); });
const noteSubRemove = (eid, i) => commit('サブページを消しました', () => { const n = DB.notes[eid]; if (!n || !n.subs[i]) fail('サブページが見つかりません'); n.subs = n.subs.filter((_, j) => j !== i); });

/* ---- カテゴリ・支払い方法 ---- */
const catAdd = (n, p) => commit('カテゴリを追加', () => { const key = uid('c'); const used = new Set(Object.values(DB.cats).map(c => c.c)); DB.cats[key] = { n: _str(n, '名前を入れてください'), ...(p && DB.cats[p] ? { p } : { c: CAT_COLORS.find(c => !used.has(c)) || CAT_COLORS[0], fx: '変動', sp: '日常' }) }; return { key }; });
const catUpdate = (key, patch) => commit('カテゴリを直しました', () => {
  const c = DB.cats[key]; if (!c) fail('カテゴリが見つかりません');
  if (patch.n !== undefined) c.n = _str(patch.n, '名前を入れてください');
  for (const f of ['c', 'fx', 'sp']) if (f in patch) { if (patch[f]) c[f] = patch[f]; else delete c[f]; }
  if ('p' in patch) { if (patch.p && (!DB.cats[patch.p] || catAnc(patch.p).includes(key))) fail('自分の中へは移せません'); if (patch.p) c.p = patch.p; else { delete c.p; if (!c.c) c.c = CAT_COLORS[0]; } }
});
const catRemove = key => commit('カテゴリを消しました', () => { if (!DB.cats[key]) fail('カテゴリが見つかりません'); if (catKids(key).length) fail('中にカテゴリがあるので、先に移すか消してください'); if (Object.values(DB.exps).some(x => x.cat === key || Object.values(x.itemCat || {}).includes(key))) fail('このカテゴリの支出があるので、先に付け替えてください'); if (Object.values(DB.acts).some(a => a.pay && a.pay.plan && a.pay.plan.cat === key)) fail('このカテゴリの支払い予定があります'); delete DB.cats[key]; });
const methodAdd = n => commit('支払い方法を追加', () => { const v = _str(n, '名前を入れてください'); if (DB.methods.includes(v)) fail('同じ名前があります'); DB.methods = [...DB.methods, v]; });
const methodRemove = n => commit('支払い方法を消しました', () => { if (DB.methods.length <= 1) fail('1つ以上必要です'); DB.methods = DB.methods.filter(m => m !== n); });

/* =====================================================================
   保存・読み込み・書き出し
   ===================================================================== */
function save() { try { localStorage.setItem(STORE_KEY, JSON.stringify(DB)); return true; } catch (e) { return false; } }
function load() {
  try { const s = JSON.parse(localStorage.getItem(STORE_KEY) || 'null'); if (s && s.v === 6 && s.acts) { DB = s; _recIdx = null; return; } } catch (e) { }
  DB = seed(); _recIdx = null; save();
}
function resetData() { DB = seed(); _recIdx = null; save(); }
const exportJSON = () => JSON.stringify(DB, null, 1);
/** 書き出したファイルを読み込む(中身を確かめてから入れ替える) */
function importJSON(text) {
  let s; try { s = JSON.parse(text); } catch (e) { return { ok: false, why: 'ファイルを読めませんでした(JSON ではありません)' }; }
  if (!s || s.v !== 6 || !s.acts || !s.ents || !s.cats) return { ok: false, why: 'このアプリ(v6)の書き出しファイルではありません' };
  const prev = JSON.stringify(DB); DB = s; _recIdx = null; save();
  return { ok: true, undo() { DB = JSON.parse(prev); _recIdx = null; save(); } };
}
const dataCounts = () => ({ acts: Object.values(DB.acts).filter(a => !isPlan(a)).length, plans: Object.values(DB.acts).filter(isPlan).length, tasks: Object.keys(DB.tasks).length, memos: Object.keys(DB.memos).length, ents: Object.keys(DB.ents).length, bytes: JSON.stringify(DB).length });

/* =====================================================================
   見本データ(架空)。初めて開いた日を「今日」として、そこからの相対で作る
   ===================================================================== */
function seed() {
  const T = TODAY, D = n => dAdd(T, n);
  const db = { v: 6, born: T, days: {}, acts: {}, exps: {}, tasks: {}, lists: {}, ents: {}, rels: [], types: {}, notes: {}, memos: {}, cats: {}, methods: ['現金', 'クレジットカード ••1234', 'Suica', 'QRコード決済', 'デビットカード', '銀行振込'] };
  db.types = {
    person: { n: '人物', props: ['電話', '誕生日', '住所'] }, colleague: { n: '同僚', p: 'person', props: ['部署'] }, split: { n: '割り勘メンバー', p: 'person', props: ['略称'] },
    place: { n: '場所', props: ['住所'] }, shop: { n: '店', p: 'place', props: ['営業時間', '定休日'] }, food: { n: '飲食店', p: 'shop', props: [] }, ramen: { n: 'ラーメン屋', p: 'food', props: [] }, cafe: { n: 'カフェ', p: 'food', props: [] },
    super: { n: 'スーパー', p: 'shop', props: [] }, drug: { n: 'ドラッグストア', p: 'shop', props: [] }, home: { n: '住まい', p: 'place', props: [] }, workplace: { n: '作業場所', p: 'place', props: ['電源'] },
    company: { n: '会社', props: ['所在地'] }, thing: { n: '物', props: ['購入日'] }, doc: { n: '書類', props: ['発行元', '期限'] },
    project: { n: 'プロジェクト', props: ['期間', '状況'] }, life_proj: { n: '生活', p: 'project', props: [] },
  };
  db.ents = {
    hanako: { n: '田中 花子', types: ['colleague', 'split'], facts: [
      { k: '姓', v: '田中', since: '2020-04', hist: [{ v: '佐藤', span: '〜2020-04', why: '結婚' }] }, { k: '名', v: '花子' }, { k: '電話', v: '090-1234-5678' }, { k: '誕生日', v: '1994-06-12' },
      { k: '住所', v: '横浜市青葉区美しが丘2', since: '2023-03', hist: [{ v: '川崎市宮前区鷺沼1', span: '2020-04〜2023-03', why: '転居' }, { v: '世田谷区用賀4', span: '〜2020-04', why: '' }] },
      { k: '部署', v: '企画部' }, { k: 'メール', v: 'hanako@example.com', rare: 1 }, { k: '略称', v: '花', rare: 1 }] },
    taro: { n: '田中 太郎', types: ['person'], facts: [{ k: '電話', v: '080-2222-3333' }] },
    ken: { n: '山本 健', types: ['person', 'split'], facts: [{ k: '電話', v: '070-4444-5555' }, { k: '略称', v: '健' }] },
    acorp: { n: 'A社', types: ['company'], facts: [{ k: '所在地', v: '東京都渋谷区神南1' }] },
    aoba: { n: 'らーめん青葉', types: ['ramen'], facts: [{ k: '住所', v: '青葉区青葉台1-2' }, { k: '営業時間', v: '11:00–21:00' }, { k: '定休日', v: '水曜', rare: 1 }] },
    minato: { n: 'カフェ ミナト', types: ['cafe', 'workplace'], facts: [{ k: '住所', v: '青葉区美しが丘3' }, { k: '電源', v: 'あり(窓側)' }] },
    super: { n: 'スーパー青葉台', types: ['super'], facts: [{ k: '営業時間', v: '9:00–23:00' }] },
    drug: { n: 'ドラッグ桜', types: ['drug'], facts: [{ k: '住所', v: '青葉区桜台5' }] },
    home: { n: '自宅', types: ['home'], facts: [{ k: '住所', v: '青葉区青葉台2' }] },
    cinema: { n: 'シネマ青葉', types: ['place'], facts: [{ k: '住所', v: '青葉区青葉台1-7' }] },
    proj1: { n: '引っ越し準備', types: ['life_proj'], facts: [{ k: '期間', v: `${ymOf(D(-20))}〜${ymOf(D(60))}` }, { k: '状況', v: '進行中' }] },
  };
  db.rels = [{ a: 'hanako', b: 'taro', role: '配偶者', sym: 1, since: '2020-04' }, { a: 'hanako', b: 'acorp', role: '勤務先', inv: '従業員', since: '2018-04' }, { a: 'hanako', b: 'ken', role: '友人', sym: 1 }, { a: 'proj1', b: 'hanako', role: '一緒に', sym: 1 }];
  db.cats = {
    house: { n: '住居', c: '#8A9AC0', fx: '固定', sp: '日常' }, rent: { n: '家賃', p: 'house' },
    comm: { n: '通信', c: '#6FB3B8', fx: '固定', sp: '日常' }, phone: { n: 'スマホ', p: 'comm' }, sub: { n: 'サブスク', p: 'comm' },
    food: { n: '食費', c: '#E08FA6', fx: '変動', sp: '日常' }, cook: { n: '自炊', p: 'food' }, eatout: { n: '外食', p: 'food' }, cafe: { n: 'カフェ・軽食', p: 'food' }, alcohol: { n: 'お酒', p: 'food' },
    daily: { n: '日用品', c: '#8CC49A', fx: '変動', sp: '日常' },
    trans: { n: '交通費', c: '#7FAAE3', fx: '変動', sp: '日常' }, train: { n: '電車', p: 'trans' }, taxi: { n: 'タクシー', p: 'trans', sp: '非日常' },
    fun: { n: '娯楽', c: '#E3B36B', fx: '変動', sp: '日常' }, hobby: { n: '趣味', p: 'fun' }, travel: { n: '旅行', p: 'fun', sp: '非日常' },
    gift: { n: '贈り物', c: '#F0BBCB', fx: 'ask', sp: '非日常' },
  };
  db.lists = { inbox: { n: '受信箱' }, life: { n: '生活' }, shop: { n: '買い物', shop: 'super' } };
  const act = (id, o) => { db.acts[id] = { roles: [], state: 'done', ...o }; };
  const pay = (id, o, items, x, payers) => {
    const total = items ? items.reduce((s, i) => s + itemAmt(i), 0) : o.amt;
    db.acts[id] = { kind: 'pay', title: '', state: 'done', date: o.date, start: o.start, ...(o.parent ? { parent: o.parent } : {}), roles: o.roles, pay: { payers: payers || [{ who: 'me', amt: total, how: o.how || 'クレジットカード ••1234' }], items: items || [], ...(items ? {} : { amt: total }), ...(o.unread ? { unread: o.unread } : {}) } };
    if (x) db.exps[id] = { basis: 'items', ...x };
  };
  const it = (n, price, users = ['me'], tax = 10, more = {}) => ({ n, price, users, tax, ...more });

  /* --- 昨日: 花子さんとランチ → ミナトで作業(中に会話・支払い)→ 帰宅 --- */
  const y = D(-1);
  db.days[y] = { diary: '花子さんとラーメン。午後はミナトで作業、夜は早めに帰宅。', photos: [{ t: '12:20', cap: 'らーめん青葉', act: 'l1' }, { t: '15:30', cap: '窓側の席', act: 'w2' }, { t: '19:40', cap: '帰り道の夕焼け' }] };
  act('w1', { kind: 'wake', title: '起床', date: y, start: '07:40' });
  act('l1', { kind: 'meal', title: '花子さんとランチ', date: y, start: '12:00', end: '13:10', planned: { start: '12:00', end: '13:00' }, roles: [{ r: '場所', e: 'aoba' }, { r: '同席', e: 'hanako' }] });
  pay('p1', { date: y, start: '13:05', parent: 'l1', roles: [{ r: '支払先', e: 'aoba' }], how: '現金' },
    [{ n: '醤油らーめん', lines: [{ n: '', k: 'amt', v: 1000 }, { n: '大盛り', k: 'amt', v: 100 }], users: ['me'], tax: 10 }, it('塩らーめん', 1200, ['hanako']), it('餃子', 680, ['me', 'hanako']), it('生ビール', 800, ['me'], 10, { memo: '1杯目' }), it('生ビール', 800, ['hanako'])],
    { cat: 'eatout', itemCat: { 3: 'alcohol' }, memo: '花子さんとランチ' });
  act('w2', { kind: 'work', title: 'カフェ ミナトで作業', date: y, start: '13:40', end: '19:20', roles: [{ r: '場所', e: 'minato' }] });
  act('c1', { kind: 'talk', title: '転職の相談', date: y, start: '15:00', end: '17:00', parent: 'w2', roles: [{ r: '参加', e: 'hanako' }], note: 'A社を辞めるか迷っているとのこと。年内に決めたいらしい。' });
  pay('p16', { date: y, start: '15:05', parent: 'c1', roles: [{ r: '支払先', e: 'minato' }], how: '現金' }, [it('カフェラテ', 550, ['me']), it('カフェラテ', 550, ['hanako'])], { basis: 'amount', me: 1100, cat: 'cafe', memo: '花子さんの分もおごり' });
  act('m1', { kind: 'move', title: '帰宅', date: y, start: '19:30', end: '20:05', roles: [{ r: '出発', e: 'minato' }, { r: '到着', e: 'home' }] });
  /* --- おととい --- */
  const y2 = D(-2);
  db.days[y2] = { diary: '雨。在宅で仕事。夕方に内見。', photos: [{ t: '14:20', cap: '内見した部屋', act: 'h1' }] };
  act('h1', { kind: 'event', title: '物件の内見', date: y2, start: '14:00', end: '15:30', roles: [{ r: 'プロジェクト', e: 'proj1' }, { r: '同席', e: 'hanako' }] });
  act('s2', { kind: 'shop', title: '夕食の買い物', date: y2, start: '18:25', end: '18:50', roles: [{ r: '場所', e: 'super' }] });
  pay('p2', { date: y2, start: '18:40', parent: 's2', roles: [{ r: '支払先', e: 'super' }] },
    [it('牛乳', 238, ['me'], 8), it('卵', 268, ['me'], 8, { memo: '特売' }), it('卵', 268, ['me'], 8), { n: '鶏もも肉', lines: [{ n: '', k: 'amt', v: 680 }, { n: '値引き', k: 'pct', v: -10 }], users: ['me'], tax: 8 }, it('キッチンペーパー', 398), it('その他', 900, ['me'], 8, { unk: 1 })],
    { cat: 'cook', itemCat: { 4: 'daily' } });
  /* --- 3日前・4日前 --- */
  db.days[D(-3)] = { diary: '朝パンを買って出社。', photos: [] };
  pay('p3', { date: D(-3), start: '08:20', roles: [{ r: '支払先', name: 'ベーカリー木漏れ日' }], how: 'Suica' }, [it('クロワッサン', 280, ['me'], 8), it('カレーパン', 340, ['me'], 8)], { cat: 'cafe' });
  pay('p4', { date: D(-4), start: '20:05', roles: [{ r: '支払先', e: 'drug' }], how: 'QRコード決済' }, [it('洗剤', 680), it('歯ブラシ', 600)], { cat: 'daily' });
  /* --- 健さんが払ったランチ(割り勘で借り)/ 花子さんの誕生日(奢り)/ 読み取ったままのレシート --- */
  act('l2', { kind: 'meal', title: '健さんとランチ', date: D(-9), start: '12:30', end: '13:30', roles: [{ r: '場所', name: 'イタリアン 木の実' }, { r: '同席', e: 'ken' }] });
  pay('p17', { date: D(-9), start: '13:25', parent: 'l2', roles: [{ r: '支払先', name: 'イタリアン 木の実' }] }, [it('ランチセット', 2840, ['me']), it('ランチセット', 2840, ['ken'])], { basis: 'even', n: 2, cat: 'eatout' }, [{ who: 'ken', amt: 5680, how: '現金' }]);
  act('l3', { kind: 'meal', title: '花子さんの誕生日ディナー', date: D(-19), start: '19:00', end: '21:00', roles: [{ r: '場所', e: 'aoba' }, { r: '同席', e: 'hanako' }] });
  pay('p8', { date: D(-19), start: '20:55', parent: 'l3', roles: [{ r: '支払先', e: 'aoba' }] }, [it('コース', 3400, ['me']), it('コース', 3400, ['hanako'])], { basis: 'amount', me: 6800, cat: 'eatout', sp: '非日常', memo: '誕生日なのでおごり' });
  pay('p30', { date: T, start: '08:40', roles: [{ r: '支払先', e: 'minato' }], how: 'Suica' }, [it('カフェラテ', 480)], { cat: 'cafe' });
  pay('p31', { date: D(-1), start: '20:20', roles: [{ r: '支払先', e: 'drug' }], unread: { amt: 1, payee: 1 } }, [it('シャンプー', 780, ['me'], 10, { unread: 1 }), it('ハンドソープ', 348, ['me'], 10, { unread: 1 }), it('', 0, ['me'], 10, { unk: 1, unread: 1, price: 0 })].slice(0, 2), { cat: 'daily' });
  /* --- 固定費(繰り返しの支払い予定)と、過去の各回 --- */
  const fixed = [['f_rent', '家賃', '大家(振込)', 27, 85000, 'rent', '銀行振込'], ['f_phone', 'スマホ料金', 'モバイル通信', 10, 3280, 'phone', 'クレジットカード ••1234'], ['f_sub', '動画サブスク', '動画配信', 15, 990, 'sub', 'クレジットカード ••1234']];
  const ym0 = ymOf(T);
  fixed.forEach(([id, title, payee, dom, amt, cat, how]) => {
    act(id, { kind: 'pay', title, date: `${ymAdd(ym0, -7)}-${pad2(dom)}`, allDay: 1, state: 'plan', rep: { u: 'month' }, roles: [{ r: '支払先', name: payee }], pay: { payers: [], items: [], plan: { amt, cat, how } } });
    for (let m = -7; m <= 0; m++) { const d = `${ymAdd(ym0, m)}-${pad2(dom)}`; if (d > T || d < `${ymAdd(ym0, -6)}-01`) continue; const pid = `${id}_${d}`; pay(pid, { date: d, start: '09:00', roles: [{ r: '支払先', name: payee }], how, amt }, null, { cat }); db.acts[pid].src = id; }
  });
  /* --- 過去6か月の変動費(決まった乱数で作る) --- */
  let sd = 20261111; const rnd = () => (sd = (sd * 1103515245 + 12345) % 2147483648) / 2147483648;
  const pool = [[{ e: 'super' }, 'cook', 1400, 4200, '18:40', 9], [{ e: 'minato' }, 'cafe', 480, 980, '15:10', 7], [{ e: 'drug' }, 'daily', 600, 1800, '19:40', 3], [{ e: 'aoba' }, 'eatout', 980, 1500, '12:30', 3], [{ name: 'そば処 山の手' }, 'eatout', 900, 2200, '12:30', 2],
    [{ name: 'JR 青葉台駅' }, 'train', 500, 1500, '08:10', 3], [{ e: 'cinema' }, 'hobby', 1800, 2000, '15:00', 1], [{ name: '書店 よむよむ' }, 'hobby', 800, 2600, '17:20', 1], [{ name: 'タクシー' }, 'taxi', 1800, 3200, '23:10', 0.4], [{ name: '酒場 みなみ' }, 'alcohol', 2400, 4800, '20:30', 1]];
  const used = new Set(Object.values(db.acts).filter(a => a.kind === 'pay' && a.state === 'done' && !a.src).map(a => a.date));
  for (let n = -5; n >= -185; n--) {
    const d = D(n); if (used.has(d)) continue;
    pool.forEach(([payee, cat, lo, hi, tm, perMonth], pi) => {
      if (rnd() > perMonth / 30) return;
      const amt = Math.round((lo + rnd() * (hi - lo)) / 10) * 10, id = `g${-n}_${pi}`;
      pay(id, { date: d, start: tm, roles: [{ r: '支払先', ...payee }], how: cat === 'train' ? 'Suica' : rnd() < .3 ? '現金' : 'クレジットカード ••1234', amt }, null, { cat });
    });
  }
  pay('p11', { date: D(-33), start: '10:30', roles: [{ r: '支払先', name: '宿 山の湯' }], amt: 18000 }, null, { cat: 'travel' });
  /* --- 1年前の今日(思い出す) --- */
  const ly = `${+T.slice(0, 4) - 1}${T.slice(4)}`;
  if (isDate(ly)) {
    db.days[ly] = { diary: '花子さんと紅葉を見に行った。帰りに温かいそば。', photos: [{ t: '11:10', cap: '渓谷の紅葉', act: 'ly1' }, { t: '13:20', cap: '山のそば屋' }] };
    act('ly1', { kind: 'play', title: '紅葉を見に行く', date: ly, start: '10:00', end: '15:30', roles: [{ r: '同席', e: 'hanako' }, { r: '場所', name: '青葉渓谷' }] });
    pay('ly2', { date: ly, start: '13:40', parent: 'ly1', roles: [{ r: '支払先', name: 'そば処 山の手' }], amt: 2400 }, null, { cat: 'eatout' });
  }
  /* --- 今日とこれからの予定 --- */
  act('e9', { kind: 'work', title: '在宅作業', date: T, start: '10:00', end: '12:00', state: 'plan', roles: [{ r: '場所', e: 'home' }] });
  act('e1', { kind: 'play', title: '花子さんと映画', date: T, start: '15:00', end: '17:10', state: 'plan', roles: [{ r: '場所', e: 'cinema' }, { r: '同席', e: 'hanako' }], note: 'チケットはネットで予約', alarm: '30分前' });
  act('e2', { kind: 'shop', title: '夕食の買い物', date: T, start: '18:30', end: '19:00', state: 'plan', roles: [{ r: '場所', e: 'super' }] });
  act('e3', { kind: 'health', title: '歯医者', date: D(2), start: '10:00', end: '11:00', state: 'plan', roles: [{ r: '場所', name: 'さくら歯科' }], alarm: '1時間前' });
  act('e11', { kind: 'event', title: '不動産屋と打ち合わせ', date: D(6), start: '14:00', end: '15:00', state: 'plan', roles: [{ r: '場所', name: '不動産ファースト' }, { r: 'プロジェクト', e: 'proj1' }] });
  act('e6', { kind: 'trip', title: '温泉旅行', date: D(10), date2: D(11), allDay: 1, state: 'plan', roles: [{ r: '場所', name: '宿 山の湯' }, { r: '同席', e: 'hanako' }] });
  act('e12', { kind: 'event', title: '資源ごみの日', date: D(-7 - ((dDow(T) + 1) % 7)), allDay: 1, state: 'plan', rep: { u: 'week' } });
  const HOL = { '2026-11-03': '文化の日', '2026-11-23': '勤労感謝の日', '2027-01-01': '元日', '2027-01-11': '成人の日', '2027-02-11': '建国記念の日', '2027-02-23': '天皇誕生日', '2027-03-21': '春分の日', '2027-04-29': '昭和の日', '2027-05-03': '憲法記念日', '2027-05-04': 'みどりの日', '2027-05-05': 'こどもの日' };
  Object.entries(HOL).forEach(([d, n]) => { if (dDiff(d, T) > -40 && dDiff(d, T) < 90) act('hol' + d, { kind: 'holiday', title: n, date: d, allDay: 1, state: 'plan', hol: 1 }); });
  /* --- ToDo --- */
  db.tasks = {
    t2: { t: '映画のチケットを予約', due: T, list: 'inbox', act: 'e1', pri: 1 },
    t7: { t: 'ドラッグ桜のポイントを交換', due: D(-2), list: 'inbox', ents: ['drug'] },
    t3: { t: '歯医者の問診票を書く', due: D(1), list: 'life', act: 'e3' },
    t1: { t: '花子さんの誕生日プレゼントを選ぶ', due: D(5), list: 'life', pri: 1, ents: ['hanako'], sub: [{ t: '候補を3つ挙げる', d: 1 }, { t: '予算を決める' }, { t: '注文する' }], memo: 'ノートの「誕生日プレゼント候補」を見る' },
    t8: { t: '年末調整の書類を出す', due: D(14), list: 'life' },
    t4: { t: '牛乳', list: 'shop', act: 'e2' }, t6: { t: '食器用洗剤', list: 'shop', act: 'e2' }, t5: { t: '卵', list: 'shop', act: 'e2', done: D(-2), payAct: 'p2' },
    t10: { t: 'スマホの料金プランを見直す', list: 'inbox', memo: '通信費(固定費)を下げたい' },
    t11: { t: '不動産屋に連絡', due: D(2), list: 'life', ents: ['proj1'] }, t12: { t: '転居の届け出を出す', due: D(30), list: 'life', ents: ['proj1'] },
    t9: { t: '部屋の掃除', due: D(3), list: 'life', rep: { u: 'week' } },
  };
  /* --- メモとノート --- */
  db.memos = {
    q5: { body: '引っ越し業者は3社から見積もりを取る', at: `${T} 09:30`, ents: ['proj1'] },
    q4: { body: '花子さんの誕生日、甘い物が良さそう。駅前のケーキ屋を見ておく', at: `${T} 08:12`, ents: ['hanako'] },
    q3: { body: 'カフェ ミナトは窓側に電源あり。午後は混む', at: `${y} 16:40`, ents: ['minato'], act: 'c1' },
    q2: { body: '年末調整の書類、会社のポータルから出す', at: `${y2} 21:15`, ents: [], pin: 1 },
    q1: { body: '洗剤の詰め替えは大きいサイズの方が安い', at: `${D(-4)} 20:10`, ents: ['drug'] },
  };
  db.notes = {
    hanako: { upd: `${D(-3)} 21:04`, ver: 7, body: [['好み', '甘い物が好き。辛い物は苦手。お酒はビールなら飲む。'], ['最近のこと', '転職を考えている。A社は2018年から。昨日ミナトで話した。']], subs: [['誕生日プレゼント候補', '更新 ' + fmtMD(D(-20)), 'ハンドクリーム\n紅茶のセット\n駅前のケーキ屋の焼き菓子'], ['好きな店', '3件', 'らーめん青葉\nカフェ ミナト\nイタリアン 木の実']] },
    aoba: { upd: `${y} 13:02`, ver: 2, body: [['メモ', '麺は硬めがおいしい。餃子は2人で1皿で足りる。']], subs: [] },
  };
  return db;
}
