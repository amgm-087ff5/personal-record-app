/* =====================================================================
   個人記録 v6.5 — データモデル(1か所)。画面はここの関数だけでデータを読み書きする。
   仕様の正本は v7/MODEL.md。計算で出せる値は保存しない。保存の鍵は prmock-v7(旧データとの互換は無い)。

   TS(時刻) = 'YYYY-MM-DD'(日だけ = 終日) か 'YYYY-MM-DDTHH:MM'。文字のまま大小を比べられる。
   DB = {
     meta    : { v:7, born, seeded? }
     days    : { 'YYYY-MM-DD': {diary} }
     photos  : { id: {at:TS, cap?, file?, act?, ents?, geo?} }
     acts    : { id: Act }            実際の1回(記録も予定も)
     routines: { id: Routine }        繰り返しの決まり。各回は 'id@日付' という仮の回
     exps    : { 支払いのactId: Exp } 支出 = 家計簿
     tasks lists ents(人・場所・物・会社・プロジェクト・支払い方法。自分は 'me'。名前だけは stub:1) rels types notes memos cats jots log
   }
   Act = { kind, title?, start:TS, end?:TS, state:'plan'|'done', parent?, roles:[{r, e}], note?, alarm?, routine?, planned?:{start,end?},
           unsure?, via?, jot?, pay?:Pay, settle?:Settle }
   Routine = { every, from, until?, skip?:[日], tpl:{kind, title?, at?, endAt?, roles, note?, alarm?, pay?:{amt}, exp?:{cat}} }
   Pay = { amt?, payers:[{who, amt, how?}], items:[{id, n, price|lines, tax, users, memo?, unk?, unsure?}] }
   Exp = { basis, n?, me?, cat, itemCat?:{品目id: カテゴリ}, sp?, fx?, memo? }
   Settle = { who, dir:'in'|'out', amt, how?, pays:[支払いid] }
   Ent = { n, types, stub?, geo?, facts:[{k, v, from?, to?, why?, rare?}] }
   rels = { id: {a, b, role, inv?, from?, to?} } / notes = { eid: {upd, body:[{h,text}], subs:[{id,n,sub,text}]} }
   unsure = true | ['amt','payee',…](項目) / via = 'rule'|'llm'|'ocr'|'gps'
   ===================================================================== */
'use strict';
let DB = null;
const STORE_KEY = 'prmock-v7';

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
/** 実在する日か('2026-02-30' のような月日は偽。Date が繰り上げるので、戻して同じか確かめる) */
const isDate = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(dObj(s)) && dISO(dObj(s)) === s;
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

/* ---------- TS(時刻)の小道具 ---------- */
const isTS = s => typeof s === 'string' && (isDate(s) || (/^\d{4}-\d{2}-\d{2}T([01]\d|2[0-3]):[0-5]\d$/.test(s) && isDate(s.slice(0, 10))));
const dOf = ts => ts ? ts.slice(0, 10) : '';
const tOf = ts => ts && ts.length > 10 ? ts.slice(11, 16) : '';
const hasT = ts => !!ts && ts.length > 10;
const tsOf = (d, t) => t ? `${d}T${t}` : d;
const nowStamp = () => `${TODAY}T${NOW}`;
const tsMin = ts => dDiff(dOf(ts), '1970-01-01') * 1440 + hm2m(tOf(ts));
const aDate = a => dOf(a.start);
const aDate2 = a => { if (!a || !a.end) return null; const e = dOf(a.end); return e !== dOf(a.start) ? e : null; };
const aStart = a => tOf(a.start);
const aEnd = a => a && a.end ? tOf(a.end) : '';
const isAllDay = a => !hasT(a.start);
/** その日 d に掛かる部分 {s, e, from(前日から続く), to(翌日へ続く)}。終日・時刻なしは null */
function spanOn(a, d) {
  if (!a || !hasT(a.start)) return null;
  const sd = dOf(a.start), ed = a.end ? dOf(a.end) : sd;
  if (d < sd || d > ed) return null;
  const s = d === sd ? tOf(a.start) : '00:00';
  if (!a.end) return { s, e: s, from: false, to: false };
  return { s, e: d < ed ? '24:00' : tOf(a.end), from: d > sd, to: d < ed };
}

/* ---------- 種類(アクティビティ)。moment=瞬間が普通 ---------- */
const KINDS = {
  meal: { n: '食事', ic: 'meal' }, talk: { n: '会話', ic: 'talk' }, work: { n: '作業', ic: 'work' }, move: { n: '移動', ic: 'move' },
  shop: { n: '買い物', ic: 'bag' }, play: { n: '遊び', ic: 'spark' }, health: { n: '通院', ic: 'heart' }, trip: { n: '旅行', ic: 'trip' },
  event: { n: '出来事', ic: 'flag' }, sleep: { n: '睡眠', ic: 'moon' },
  pay: { n: '支払い', ic: 'yen', moment: 1 }, settle: { n: '精算', ic: 'swap', moment: 1 },
};
const ROLES = ['場所', '同席', '参加', '出発', '到着', 'プロジェクト', '関連'];
const ROLE_STUB = { '場所': 'place', '出発': 'place', '到着': 'place', '同席': 'person', '参加': 'person', '支払先': 'shop', 'プロジェクト': 'project' };
const TAX_DEFAULT = 10;
const BASIS = { items: '使った分', even: '割り勘', amount: '金額を指定' };

/* =====================================================================
   読む(セレクタ)
   ===================================================================== */
/** 'YYYY-MM-DD' の繰り返し判定 */
function repOn(r, d) {
  if (!r || !isDate(d) || d < r.from || (r.until && d > r.until) || (r.skip || []).includes(d)) return false;
  if (r.every === 'day') return true;
  if (r.every === 'week') return dDow(d) === dDow(r.from);
  if (r.every === 'month') return +d.slice(8) === Math.min(+r.from.slice(8), ymDays(ymOf(d)));
  return false;
}
const repText = r => !r ? '' : r.every === 'day' ? '毎日' : r.every === 'week' ? `毎週${dowJ(r.from)}曜` : `毎月${+r.from.slice(8)}日`;
/** 次の回の日付(from 以降) */
function repNext(r, from = TODAY) { for (let i = 0; i <= 366; i++) { const d = dAdd(from, i); if (repOn(r, d)) return d; } return r.from; }
/** 仮の回(保存しない) */
function _virt(rid, r, d) {
  const t = r.tpl, a = { kind: t.kind, ...(t.title ? { title: t.title } : {}), start: tsOf(d, t.at), state: 'plan', virt: 1, routine: rid, roles: (t.roles || []).map(x => ({ ...x })) };
  if (t.at && t.endAt) { if (t.endAt > t.at) a.end = tsOf(d, t.endAt); else if (t.endAt < t.at) a.end = tsOf(dAdd(d, 1), t.endAt); }
  if (t.note) a.note = t.note; if (t.alarm) a.alarm = t.alarm;
  if (t.pay) { a.kind = 'pay'; a.pay = { amt: t.pay.amt, payers: [], items: [] }; }
  return a;
}
/** Act を返す。'決まりid@日付' は仮の回を作って返す(決まりid だけは null) */
function A(id) {
  if (!id) return null;
  const a = DB.acts[id]; if (a) return a;
  const i = id.indexOf('@'); if (i < 0) return null;
  const rid = id.slice(0, i), d = id.slice(i + 1), r = DB.routines[rid];
  if (!r || !repOn(r, d)) return null;
  return _virt(rid, r, d);
}
const E = id => id ? DB.ents[id] || null : null;
const isStub = id => !!(DB.ents[id] && DB.ents[id].stub);
const isPay = a => !!(a && a.kind === 'pay');
const isSpan = a => !!(a && a.end);
const isPlan = a => !!(a && a.state === 'plan');
const isUnsure = row => !!(row && row.unsure);
/** 過ぎたのに記録になっていない予定 */
function isLate(a) {
  if (!isPlan(a)) return false;
  const e = a.end || a.start;
  return hasT(e) ? e <= nowStamp() : dOf(e) < TODAY;
}
function timeText(a) {
  if (!a) return '';
  if (!hasT(a.start)) { const d2 = aDate2(a); return d2 ? `${fmtMD(aDate(a))}–${fmtMD(d2)}` : '終日'; }
  if (!a.end) return tOf(a.start);
  const d2 = aDate2(a);
  return d2 ? `${fmtMD(aDate(a))} ${tOf(a.start)}–${fmtMD(d2)} ${tOf(a.end)}` : `${tOf(a.start)}–${tOf(a.end)}`;
}
const actMins = a => a && a.end && hasT(a.start) && hasT(a.end) ? Math.max(0, tsMin(a.end) - tsMin(a.start)) : 0;
/** 並び替えの鍵(全体の時系列)。終日は同じ日の時刻ありより前 */
const sortKey = a => a.start;
const byTime = (x, y) => x[1].start.localeCompare(y[1].start) || (isSpan(y[1]) - isSpan(x[1]));
/** その日の中の並び(前日から続くものは 00:00 扱い、終日は先頭) */
const _dayKey = (a, d) => { const s = spanOn(a, d); return s ? s.s : ''; };
const roleName = r => r && r.e && E(r.e) ? E(r.e).n : '';
const rolesOf = (a, ...names) => (a && a.roles || []).filter(r => !names.length || names.includes(r.r));
const placeOf = a => { const r = rolesOf(a, '場所', '支払先', '到着')[0]; return r ? roleName(r) : ''; };
const payeeOf = a => { const r = rolesOf(a, '支払先')[0]; return r ? roleName(r) : ''; };
/** 題。支払いは「支払先」、精算は「花子さんから ¥…」 */
function actTitle(a) {
  if (!a) return '';
  if (a.kind === 'settle' && a.settle && !a.title) { const s = a.settle, w = s.who && E(s.who) ? whoShort(s.who) + 'さん' : ''; return w ? `${w}${s.dir === 'in' ? 'から' : 'へ'} ${yen(s.amt)}` : `精算 ${yen(s.amt)}`; }
  return isPay(a) ? (a.title || payeeOf(a) || '支払い') : (a.title || (KINDS[a.kind] || {}).n || '');
}

/* ---- 繰り返しの記録済み判定 ---- */
let _recIdx = null;
function recIdx() {
  if (_recIdx) return _recIdx; _recIdx = new Set();
  for (const a of Object.values(DB.acts)) if (a.routine) _recIdx.add(a.routine + '@' + dOf((a.planned && a.planned.start) || a.start));
  return _recIdx;
}
/** 決まりの、記録になった回(新しい順)[[id, act]] */
const recordsOfRoutine = rid => Object.entries(DB.acts).filter(([, a]) => a.routine === rid).sort((x, y) => y[1].start.localeCompare(x[1].start));

/** その日の、いちばん外側のもの [[id, act]](時刻順)。繰り返しの仮の回も含む。o.state で絞れる */
function actsOn(d, o = {}) {
  const out = [];
  for (const [id, a] of Object.entries(DB.acts)) {
    if (a.parent) continue;
    if (aDate(a) <= d && d <= (dOf(a.end) || aDate(a))) out.push([id, a]);
  }
  for (const [rid, r] of Object.entries(DB.routines)) if (repOn(r, d) && !recIdx().has(rid + '@' + d)) out.push([rid + '@' + d, _virt(rid, r, d)]);
  return out.filter(([, a]) => !o.state || a.state === o.state).sort((x, y) => _dayKey(x[1], d).localeCompare(_dayKey(y[1], d)) || (isSpan(y[1]) - isSpan(x[1])));
}
/** 子(時刻順) */
const kidsOf = id => Object.entries(DB.acts).filter(([, a]) => a.parent === id).sort(byTime);
function descOf(id) { const out = []; const walk = k => kidsOf(k).forEach(([c]) => { out.push(c); walk(c); }); walk(id); return out; }
function ancOf(id) { const r = []; let a = A(id); while (a && a.parent && DB.acts[a.parent]) { r.unshift([a.parent, DB.acts[a.parent]]); a = DB.acts[a.parent]; } return r; }
/** 記録のある日(昇順)。from〜to */
function daysWith(from, to) {
  const s = new Set();
  for (const a of Object.values(DB.acts)) {
    if (a.parent) continue;
    const e = dOf(a.end) || aDate(a);
    for (let d = aDate(a) > from ? aDate(a) : from; d <= e && d <= to; d = dAdd(d, 1)) s.add(d);
  }
  for (const d of Object.keys(DB.days)) if (d >= from && d <= to && DB.days[d].diary) s.add(d);
  for (const p of Object.values(DB.photos)) { const d = dOf(p.at); if (d >= from && d <= to) s.add(d); }
  for (const [rid, r] of Object.entries(DB.routines)) for (let d = from > r.from ? from : r.from; d <= to; d = dAdd(d, 1)) if (d >= TODAY && repOn(r, d) && !recIdx().has(rid + '@' + d)) s.add(d);
  return [...s].sort();
}
const dayOf = d => DB.days[d] || { diary: '' };
/* ---- 日本の祝日(規則で計算。保存しない。1980〜2099 年に有効。春分・秋分は近似式、ハッピーマンデーは第n月曜) ---- */
const _holCache = {};
/** その年の祝日 {'YYYY-MM-DD': 名前}(振替休日・国民の休日を含む) */
function holidaysOf(y) {
  if (_holCache[y]) return _holCache[y];
  const m = {}, key = (mo, da) => `${y}-${pad2(mo)}-${pad2(da)}`;
  const nthMon = (mo, n) => { const first = dDow(key(mo, 1)); return 1 + ((1 - first + 7) % 7) + 7 * (n - 1); };   /* 第n月曜の日 */
  m[key(1, 1)] = '元日';
  m[key(1, nthMon(1, 2))] = '成人の日';
  m[key(2, 11)] = '建国記念の日';
  m[key(2, 23)] = '天皇誕生日';
  m[key(3, Math.floor(20.8431 + 0.242194 * (y - 1980) - Math.floor((y - 1980) / 4)))] = '春分の日';
  m[key(4, 29)] = '昭和の日';
  m[key(5, 3)] = '憲法記念日'; m[key(5, 4)] = 'みどりの日'; m[key(5, 5)] = 'こどもの日';
  m[key(7, nthMon(7, 3))] = '海の日';
  m[key(8, 11)] = '山の日';
  m[key(9, nthMon(9, 3))] = '敬老の日';
  m[key(9, Math.floor(23.2488 + 0.242194 * (y - 1980) - Math.floor((y - 1980) / 4)))] = '秋分の日';
  m[key(10, nthMon(10, 2))] = 'スポーツの日';
  m[key(11, 3)] = '文化の日'; m[key(11, 23)] = '勤労感謝の日';
  /* 振替休日: 祝日が日曜なら、次の祝日でない日 */
  for (const d of Object.keys(m)) if (dDow(d) === 0) { let s = dAdd(d, 1); while (m[s]) s = dAdd(s, 1); m[s] = '振替休日'; }
  /* 国民の休日: 祝日でない日で、前日と翌日が祝日 */
  for (let d = key(1, 2); d < key(12, 31); d = dAdd(d, 1)) if (!m[d] && m[dAdd(d, -1)] && m[dAdd(d, 1)]) m[d] = '国民の休日';
  return (_holCache[y] = m);
}
/** その日の祝日名(祝日でなければ空) */
function holidayName(d) { if (!isDate(d)) return ''; return holidaysOf(+d.slice(0, 4))[d] || ''; }
/** その日の祝日名か ''(規則の計算。DB には持たない) */
const holOn = d => holidayName(d);
/** その日の写真 [[id, p]](時刻順) */
const photosOn = d => Object.entries(DB.photos).filter(([, p]) => dOf(p.at) === d).sort((x, y) => x[1].at.localeCompare(y[1].at));
const photosOfAct = id => Object.entries(DB.photos).filter(([, p]) => p.act === id).sort((x, y) => x[1].at.localeCompare(y[1].at));
/** 記録待ち(直近 back 日) [[id, act]] */
function pendingPlans(back = 3) {
  const out = [];
  for (let i = back; i >= 0; i--) actsOn(dAdd(TODAY, -i), { state: 'plan' }).forEach(p => { if (isLate(p[1]) && !out.some(q => q[0] === p[0])) out.push(p); });
  return out;
}
/** これからの予定(今日のまだの分〜 days 日先)[[id, act, date]] */
function upcoming(days = 14) {
  const out = [], seen = new Set();
  for (let i = 0; i <= days; i++) { const d = dAdd(TODAY, i); actsOn(d, { state: 'plan' }).forEach(([id, a]) => { if (isLate(a) || seen.has(id)) return; seen.add(id); out.push([id, a, d]); }); }
  return out;
}
/** その人・場所・支払い方法が関わったものの役割 */
function roleIn(a, eid) {
  const r = (a.roles || []).find(r => r.e === eid); if (r) return r.r;
  if (a.pay) {
    if (a.pay.payers.some(p => p.who === eid)) return '支払った';
    if (a.pay.items.some(i => (i.users || []).includes(eid))) return '使った';
    if (a.pay.payers.some(p => p.how === eid)) return '支払い方法';
  }
  if (a.settle && a.settle.who === eid) return '精算';
  return null;
}
function actsOfEnt(eid) {
  const hit = Object.entries(DB.acts).filter(([, a]) => roleIn(a, eid));
  const keys = new Set(hit.map(h => h[0]));
  return hit.filter(([id]) => !ancOf(id).some(([p]) => keys.has(p))).map(([id, a]) => [id, a, roleIn(a, eid)]).sort((x, y) => y[1].start.localeCompare(x[1].start));
}

/* ---- 種類・エンティティ・事実・関係・カテゴリ ---- */
const typeAnc = k => { const r = []; while (k && DB.types[k]) { r.unshift(k); k = DB.types[k].p; } return r; };
const typeKids = k => Object.keys(DB.types).filter(t => (DB.types[t].p || null) === (k || null));
const isUnder = (t, root) => typeAnc(t).includes(root);
const typePath = k => typeAnc(k).map(t => DB.types[t].n).join(' › ');
const propsOf = k => typeAnc(k).flatMap(t => (DB.types[t].props || []).map(p => [p, t]));
/** 全ての ent の id。o.stubs(既定 false)で名前だけの相手も含める */
const entsAll = (o = {}) => Object.keys(DB.ents).filter(id => o.stubs || !DB.ents[id].stub);
const entsOfType = (k, deep = true, o = {}) => entsAll(o).filter(id => DB.ents[id].types.some(t => deep ? isUnder(t, k) : t === k));
const entRoot = id => { const e = E(id); return e && e.types[0] ? typeAnc(e.types[0])[0] : null; };
/** 今の事実 / 前の値(新しい順)/ ある日の値 */
const factsNow = eid => (E(eid) ? E(eid).facts : []).filter(f => !f.to);
const factHist = (eid, k) => (E(eid) ? E(eid).facts : []).filter(f => f.k === k && f.to).sort((x, y) => y.to.localeCompare(x.to) || (y.from || '').localeCompare(x.from || ''));
function factAt(eid, k, d) {
  const rows = (E(eid) ? E(eid).facts : []).filter(f => f.k === k && (!f.from || d.slice(0, f.from.length) >= f.from) && (!f.to || d.slice(0, f.to.length) < f.to));
  rows.sort((x, y) => (y.from || '').localeCompare(x.from || ''));
  return rows.length ? rows[0].v : null;
}
/** 関係 [{id, role, other, from, to}](自分側から見た呼び名)。既定は続いているものだけ。{ended:true} で終わったものも */
function relsOf(id, o = {}) {
  return Object.entries(DB.rels).filter(([, r]) => (r.a === id || r.b === id) && (o.ended || !r.to))
    .map(([rid, r]) => r.a === id ? { id: rid, role: r.role, other: r.b, from: r.from, to: r.to } : { id: rid, role: r.inv || r.role, other: r.a, from: r.from, to: r.to });
}
const catAnc = k => { const r = []; while (k && DB.cats[k]) { r.unshift(k); k = DB.cats[k].p; } return r; };
const catKids = k => Object.keys(DB.cats).filter(c => (DB.cats[c].p || null) === (k || null));
const catName = k => DB.cats[k] ? DB.cats[k].n : '未分類';
const catPath = k => catAnc(k).map(c => DB.cats[c].n).join(' › ') || '未分類';
const catRoot = k => catAnc(k)[0] || k;
const catColor = k => { for (const c of catAnc(k).reverse()) if (DB.cats[c].c) return DB.cats[c].c; return '#8E9BB8'; };
const _inh = (k, f) => { for (const c of catAnc(k).reverse()) if (DB.cats[c][f] !== undefined) return DB.cats[c][f] === 'ask' ? null : DB.cats[c][f]; return null; };
const catFx = k => _inh(k, 'fx'), catSp = k => _inh(k, 'sp');
const CAT_COLORS = ['#2F4F9E', '#5A78BF', '#8EA3D6', '#1F3677', '#B9C6E6', '#54648A', '#C3CBDC', '#8E9BB8', '#B9C2D6'];

/* ---- 支払い方法(種類 method の ent) ---- */
const methodsAll = () => Object.entries(DB.ents).filter(([, e]) => e.types.includes('method'));
const methodDefault = () => { const m = methodsAll()[0]; return m ? m[0] : null; };
const methodName = id => E(id) ? E(id).n : '';

/* =====================================================================
   お金(支払い = 事実 / 支出 = 家計簿。同じことを2度入力しない)
   ===================================================================== */
const lnAmts = lines => { let s = 0; return lines.map((l, j) => { const a = l.k === 'pct' ? (j ? Math.round(s * (l.v || 0) / 100) : 0) : (l.v || 0); s += a; return a; }); };
const itemAmt = it => it.lines && it.lines.length ? lnAmts(it.lines).reduce((s, a) => s + a, 0) : (it.price || 0);
const itemTax = it => (it && it.tax) || TAX_DEFAULT;
const usersOf = it => it && it.users && it.users.length ? it.users : ['me'];
/** 品目(無ければ合計1行として扱う。virtual=1) */
const payItems = a => a.pay.items.length ? a.pay.items : [{ n: '', price: a.pay.amt || 0, users: ['me'], virtual: 1 }];
const payTotal = a => !a || !a.pay ? 0 : a.pay.items.length ? a.pay.items.reduce((s, i) => s + itemAmt(i), 0) : (a.pay.amt || 0);
const paidOf = a => a.pay.payers.length ? a.pay.payers : [{ who: 'me', amt: payTotal(a), how: methodDefault() }];
const myPaid = a => paidOf(a).filter(p => p.who === 'me').reduce((s, p) => s + (p.amt || 0), 0);
const payMethod = a => [...new Set(paidOf(a).filter(p => p.who === 'me').map(p => methodName(p.how)).filter(Boolean))].join('・');
const whoName = w => w && E(w) ? E(w).n : 'ほかの人';
const whoShort = w => w && E(w) ? E(w).n.split(' ').pop() : 'ほかの人';
/** この支払いに関わった人(自分を含む): 使った人・払った人・親の同席者 */
function peopleOf(id) {
  const a = A(id), s = new Set(['me']);
  payItems(a).forEach(i => usersOf(i).forEach(u => s.add(u))); paidOf(a).forEach(p => p.who && s.add(p.who));
  [[id, a], ...ancOf(id)].forEach(([, x]) => rolesOf(x, '同席', '参加').forEach(r => r.e && s.add(r.e)));
  return [...s];
}
/** 支出。仮の回は決まりの exp から */
function xOf(id) {
  if (DB.exps[id]) return DB.exps[id];
  const a = id && id.includes('@') ? A(id) : null;
  if (a && a.virt) { const t = DB.routines[a.routine].tpl; if (t.exp) return { basis: 'items', cat: t.exp.cat }; }
  return null;
}
function xShares(id) {
  const a = A(id), x = xOf(id); if (!a || !a.pay || !x) return [];
  const its = payItems(a), amts = its.map(itemAmt), total = amts.reduce((s, v) => s + v, 0);
  if ((x.basis || 'items') === 'items') return its.map(it => { const u = usersOf(it); return u.includes('me') ? Math.floor(itemAmt(it) / u.length) : 0; });
  const me = x.basis === 'amount' ? Math.max(0, Math.min(x.me || 0, total)) : Math.floor(total / Math.max(1, x.n || 2));
  let left = me;
  return amts.map((v, i) => { const s = i === amts.length - 1 ? left : Math.min(left, total ? Math.floor(v * me / total) : 0); left -= s; return s; });
}
const xAmt = id => xShares(id).reduce((s, v) => s + v, 0);
/** 支出の品目(表示用)[{i, id, n, price, full, how, cat, memo, unk}] */
function xItems(id) {
  const a = A(id), x = xOf(id); if (!a || !x) return [];
  const sh = xShares(id), basis = x.basis || 'items';
  return payItems(a).map((it, i) => {
    const full = itemAmt(it), u = usersOf(it);
    const how = !u.includes('me') && sh[i] > 0 ? '奢り' : basis === 'items' ? (sh[i] === full ? '全額' : `1/${u.length}`) : (sh[i] >= full ? '全額' : '按分');
    return { i, id: it.id, n: it.n || payeeOf(a) || '支払い', price: sh[i], full, how, cat: (it.id && x.itemCat && x.itemCat[it.id]) || x.cat, memo: it.memo, unk: it.unk };
  }).filter(o => o.price > 0);
}
/** カテゴリ配分 [{cat, amt, ids}](既定のカテゴリが先) */
function xCats(id) {
  const x = xOf(id); if (!x) return []; const m = new Map();
  xItems(id).forEach(o => { if (!m.has(o.cat)) m.set(o.cat, { cat: o.cat, amt: 0, ids: [] }); const g = m.get(o.cat); g.amt += o.price; if (o.id) g.ids.push(o.id); });
  if (!m.size) m.set(x.cat, { cat: x.cat, amt: 0, ids: [] });
  return [...m.values()].sort((p, q) => (q.cat === x.cat) - (p.cat === x.cat));
}
const xFx = id => { const x = xOf(id); return !x ? null : x.fx !== undefined ? x.fx : catFx(x.cat); };
const xSp = id => { const x = xOf(id); return !x ? null : x.sp !== undefined ? x.sp : catSp(x.cat); };

/** 貸し借り(相手ごと)[{who, amt(>0 相手が自分に負う / <0 自分が負う), settle(精算の act id か null)}]。手順は MODEL.md 2章 */
function debtsOf(id) {
  const a = DB.acts[id]; if (!a || !a.pay || isPlan(a)) return [];
  const M = xAmt(id), P = myPaid(a), N = P - M; if (N === 0) return [];
  const others = peopleOf(id).filter(p => p !== 'me');
  const burden = new Map(others.map(o => [o, 0])), paid = new Map(others.map(o => [o, 0]));
  paidOf(a).forEach(p => { if (paid.has(p.who)) paid.set(p.who, paid.get(p.who) + (p.amt || 0)); });
  const x = xOf(id) || { basis: 'items' };
  if ((x.basis || 'items') === 'items') payItems(a).forEach(it => { const u = usersOf(it), per = itemAmt(it) / u.length; u.forEach(w => { if (burden.has(w)) burden.set(w, burden.get(w) + per); }); });
  else { const each = (payTotal(a) - M) / Math.max(1, others.length); others.forEach(o => burden.set(o, each)); }
  const w = others.map(o => N > 0 ? burden.get(o) - paid.get(o) : paid.get(o) - burden.get(o));
  let ws = w.map(v => v > 0 ? v : 0), sum = ws.reduce((s, v) => s + v, 0);
  if (!sum && others.length) { ws = others.map(() => 1); sum = others.length; }
  const settleOf = who => { const e = Object.entries(DB.acts).find(([, s]) => s.settle && (s.settle.who || null) === (who || null) && s.settle.pays.includes(id)); return e ? e[0] : null; };
  if (!others.length || !sum) return [{ who: null, amt: N, settle: settleOf(null) }];
  const tot = Math.abs(N), shares = ws.map(v => Math.floor(tot * v / sum));
  shares[ws.findIndex(v => v > 0)] += tot - shares.reduce((s, v) => s + v, 0);
  return others.map((o, i) => ({ who: o, amt: Math.sign(N) * shares[i], settle: settleOf(o) })).filter((d, i) => shares[i] > 0);
}
/** {amt(差 N), who:[相手 id], open, settled, debts} */
function balanceOf(id) {
  const a = DB.acts[id]; if (!a || !a.pay || isPlan(a)) return { amt: 0, who: [], open: false, settled: false, debts: [] };
  const debts = debtsOf(id), amt = debts.reduce((s, d) => s + d.amt, 0);
  return { amt, who: debts.map(d => d.who).filter(Boolean), open: debts.some(d => !d.settle), settled: amt !== 0 && debts.every(d => d.settle), debts };
}
function balanceText(b) {
  if (!b || !b.amt) return '';
  const who = b.who.length ? b.who.map(w => whoShort(w) + (w && E(w) ? 'さん' : '')).join('・') : '相手';
  return `${who}に${b.amt > 0 ? '貸し' : '借り'} ${yen(Math.abs(b.amt))}`;
}
/** 未精算の支払い [[id, act, bal]](新しい順) */
const openBalances = () => Object.entries(DB.acts).filter(([, a]) => a.pay && !isPlan(a)).map(([id, a]) => [id, a, balanceOf(id)]).filter(r => r[2].open).sort((x, y) => y[1].start.localeCompare(x[1].start));
/** 相手ごとの差し引き [{who(id か 'other'), amt(>0 貸し), ids}](未精算の debts の合計) */
function personBalances() {
  const m = new Map();
  openBalances().forEach(([id, , b]) => b.debts.filter(d => !d.settle).forEach(d => { const w = d.who || 'other'; if (!m.has(w)) m.set(w, { who: w, amt: 0, ids: [] }); const g = m.get(w); g.amt += d.amt; if (!g.ids.includes(id)) g.ids.push(id); }));
  return [...m.values()];
}
const settlesOf = pid => Object.entries(DB.acts).filter(([, a]) => a.settle && a.settle.pays.includes(pid));
const isShared = id => { const a = A(id); return !!a && !!a.pay && (peopleOf(id).length > 1 || payTotal(a) !== xAmt(id)); };
/** 未確認の数(支払い: 項目の数 + 未確認の品目の数。true なら 1 + 品目) */
function unsureCount(a) {
  if (!a || !a.unsure) return 0;
  const own = Array.isArray(a.unsure) ? a.unsure.length : 1;
  return own + (a.pay ? a.pay.items.filter(i => i.unsure).length : 0);
}
const basisGuess = items => items.every(i => usersOf(i).length === 1 && usersOf(i)[0] === 'me') ? 'items' : null;

/** 支払先からカテゴリを推し量る(同じ支払先の前回 → 支払先の種類 → 言葉) */
function catGuess(payee, text = '') {
  let pid = payee && payee.e, nm = payee && payee.name;
  if (!pid && nm) pid = Object.keys(DB.ents).find(k => DB.ents[k].n === nm);
  if (pid) { const prev = Object.entries(DB.acts).filter(([id, a]) => a.pay && DB.exps[id] && rolesOf(a, '支払先').some(r => r.e === pid)).sort((x, y) => y[1].start.localeCompare(x[1].start))[0]; if (prev) return DB.exps[prev[0]].cat; }
  const t = (pid && E(pid) ? E(pid).types.map(typePath).join(' ') : '') + ' ' + (nm || (pid && E(pid) ? E(pid).n : '')) + ' ' + text;
  const map = [[/ラーメン|飲食|ランチ|ディナー|定食|そば|寿司|外食|食堂/, 'eatout'], [/カフェ|コーヒー|パン|ベーカリー|ラテ/, 'cafe'], [/スーパー|八百|肉|魚|食材/, 'cook'], [/ドラッグ|薬|洗剤|日用/, 'daily'], [/駅|電車|JR|バス|Suica/, 'train'], [/タクシー/, 'taxi'], [/映画|本|書店|ゲーム/, 'hobby'], [/酒|ビール|居酒屋|バー/, 'alcohol'], [/宿|ホテル|旅/, 'travel']];
  for (const [re, k] of map) if (re.test(t) && DB.cats[k]) return k;
  return DB.cats.eatout ? 'eatout' : Object.keys(DB.cats)[0];
}

/* ---- 家計簿の集計(settle は入れない。exps を持つ支払いだけ) ---- */
function expsIn(ym) {
  return Object.keys(DB.exps).map(id => [id, DB.acts[id]]).filter(([, a]) => a && !isPlan(a) && ymOf(aDate(a)) === ym)
    .map(([id, a]) => [id, a, xAmt(id)]).sort((x, y) => y[1].start.localeCompare(x[1].start));
}
function monthSum(ym) {
  const r = { total: 0, fixed: 0, variable: 0, special: 0, unset: 0, roots: new Map(), n: 0 };
  expsIn(ym).forEach(([id, , amt]) => {
    r.total += amt; r.n++;
    if (xFx(id) === '固定') r.fixed += amt; else if (xFx(id) === '変動') r.variable += amt; else r.unset += amt;
    if (xSp(id) === '非日常') r.special += amt;
    xCats(id).forEach(g => { const k = catRoot(g.cat); r.roots.set(k, (r.roots.get(k) || 0) + g.amt); });
  });
  r.roots = [...r.roots.entries()].map(([cat, amt]) => ({ cat, amt })).sort((a, b) => b.amt - a.amt);
  return r;
}
const catSum = (ym, k) => expsIn(ym).reduce((s, [id]) => s + xCats(id).filter(g => catAnc(g.cat).includes(k)).reduce((t, g) => t + g.amt, 0), 0);
const daySpend = (d, o = {}) => Object.keys(DB.exps).filter(id => DB.acts[id] && aDate(DB.acts[id]) === d && !isPlan(DB.acts[id]) && !(o.noFixed && xFx(id) === '固定')).reduce((s, id) => s + xAmt(id), 0);
function paceOf(ym, upto) {
  const n = upto || ymDays(ym), arr = new Array(n).fill(0);
  expsIn(ym).forEach(([id, a, amt]) => { if (xFx(id) === '固定') return; const i = +aDate(a).slice(8) - 1; if (i < n) arr[i] += amt; });
  for (let i = 1; i < n; i++) arr[i] += arr[i - 1];
  return arr;
}

/* ---- エンティティの数字 ---- */
function spendWith(eid) {
  return actsOfEnt(eid).filter(([, a]) => !isPlan(a)).reduce((s, [id]) => s + [id, ...descOf(id)].filter(k => DB.exps[k]).reduce((t, k) => t + xAmt(k), 0), 0);
}
function entStats(eid) {
  const acts = actsOfEnt(eid).filter(([, a]) => !isPlan(a));
  const y = TODAY.slice(0, 4);
  const weeks = new Array(12).fill(0);
  acts.forEach(([, a]) => { const w = Math.floor(dDiff(TODAY, aDate(a)) / 7); if (w >= 0 && w < 12) weeks[11 - w]++; });
  const bal = personBalances().find(b => b.who === eid);
  return { last: acts[0] ? aDate(acts[0][1]) : null, year: acts.filter(([, a]) => aDate(a).startsWith(y)).length, total: acts.length, spend: spendWith(eid), weeks, bal: bal ? bal.amt : 0, next: upcoming(60).find(([, a]) => roleIn(a, eid)) || null };
}

/* ---- ToDo ---- */
const taskOpen = t => !t.done;
const taskLate = t => taskOpen(t) && t.due && t.due < TODAY;
const tasksDue = () => Object.entries(DB.tasks).filter(([, t]) => taskOpen(t) && t.due && t.due <= TODAY);
const tasksOfAct = id => Object.entries(DB.tasks).filter(([, t]) => t.act === id || (id.includes('@') && t.act === id.split('@')[0]));
const tasksOfEnt = eid => Object.entries(DB.tasks).filter(([, t]) => (t.ents || []).includes(eid));

/* ---- メモ ---- */
const memosSorted = () => Object.entries(DB.memos).sort((a, b) => (b[1].pin ? 1 : 0) - (a[1].pin ? 1 : 0) || b[1].at.localeCompare(a[1].at));
const memosOn = d => Object.entries(DB.memos).filter(([, m]) => dOf(m.at) === d).sort((a, b) => a[1].at.localeCompare(b[1].at));

/* ---- 思い出す ---- */
function recallDays() {
  const out = [], has = d => actsOn(d, { state: 'done' }).length || dayOf(d).diary;
  const y = `${+TODAY.slice(0, 4) - 1}${TODAY.slice(4)}`; if (isDate(y) && has(y)) out.push({ label: '1年前の今日', date: y });
  const m = ymAdd(ymOf(TODAY), -1) + TODAY.slice(7); if (isDate(m) && has(m)) out.push({ label: '1か月前の今日', date: m });
  const w = dAdd(TODAY, -7); if (has(w)) out.push({ label: `先週の${dowJ(w)}曜`, date: w });
  return out;
}

/* ---- さがす ---- */
function searchAll(q) {
  const ws = String(q || '').toLowerCase().split(/\s+/).filter(Boolean); if (!ws.length) return [];
  const hit = s => { const t = String(s || '').toLowerCase(); return ws.every(w => t.includes(w)); };
  const stamp = s => String(s || '').replace('T', ' ');
  const out = [];
  for (const [id, a] of Object.entries(DB.acts)) {
    const names = (a.roles || []).map(roleName).join(' ');
    if (hit(`${actTitle(a)} ${KINDS[a.kind].n} ${names} ${a.note || ''}`)) out.push({ type: 'act', id, title: actTitle(a), sub: `${fmtFull(aDate(a))} ${timeText(a)}`, date: aDate(a), href: `#/a/${id}`, amt: a.pay ? payTotal(a) : a.settle ? a.settle.amt : 0, plan: isPlan(a) });
    if (a.pay) a.pay.items.forEach(it => { if (hit(`${it.n} ${it.memo || ''}`)) out.push({ type: 'item', id: id + '/' + it.id, title: it.n, sub: `${payeeOf(a) || '支払い'} · ${fmtFull(aDate(a))}`, date: aDate(a), href: `#/a/${id}`, amt: itemAmt(it) }); });
  }
  for (const [rid, r] of Object.entries(DB.routines)) {
    const t = r.tpl, names = (t.roles || []).map(roleName).join(' ');
    if (hit(`${t.title || ''} ${KINDS[t.kind].n} ${names} ${t.note || ''}`)) { const d = repNext(r); out.push({ type: 'act', id: rid + '@' + d, title: t.title || names || KINDS[t.kind].n, sub: `${repText(r)}`, date: d, href: `#/a/${rid}@${d}`, amt: t.pay ? t.pay.amt : 0, plan: true }); }
  }
  for (const [id, e] of Object.entries(DB.ents)) if (!e.stub && hit(`${e.n} ${e.types.map(t => DB.types[t] ? DB.types[t].n : '').join(' ')} ${e.facts.map(f => f.v).join(' ')}`)) out.push({ type: 'ent', id, title: e.n, sub: e.types.map(t => DB.types[t] ? DB.types[t].n : '').join('・'), date: '', href: `#/e/${id}` });
  for (const [id, m] of Object.entries(DB.memos)) if (hit(m.body)) out.push({ type: 'memo', id, title: m.body, sub: stamp(m.at), date: dOf(m.at), href: `#/memo?focus=${id}` });
  for (const [id, j] of Object.entries(DB.jots)) if (hit(j.body) && (typeof jotState !== 'function' || jotState(id) === 'raw')) out.push({ type: 'jot', id, title: j.body, sub: `雑記 ${stamp(j.at)}`, date: dOf(j.at), href: '' });
  for (const [id, t] of Object.entries(DB.tasks)) if (hit(`${t.t} ${t.memo || ''}`)) out.push({ type: 'task', id, title: t.t, sub: t.done ? '完了' : t.due ? `期限 ${fmtMD(t.due)}` : '期限なし', date: t.due || '', href: `#/task/${id}`, done: !!t.done });
  for (const [id, n] of Object.entries(DB.notes)) if (E(id) && hit(n.body.map(b => b.h + ' ' + b.text).join(' ') + ' ' + n.subs.map(s => `${s.n} ${s.sub} ${s.text}`).join(' '))) out.push({ type: 'note', id, title: `${E(id).n}のノート`, sub: `更新 ${stamp(n.upd)}`, date: dOf(n.upd), href: `#/n/${id}` });
  for (const [d, o] of Object.entries(DB.days)) if (hit(o.diary)) out.push({ type: 'day', id: d, title: o.diary, sub: `${fmtFull(d)} の日記`, date: d, href: `#/day/${d}` });
  return out.sort((a, b) => (b.date || '').localeCompare(a.date || ''));
}

/* =====================================================================
   書く(変更)。全て commit を通す。戻り値 {ok:true, label, undo(), …} か {ok:false, why}
   取り消しは行単位(後から別の所を変えていても、その変更だけを戻す)
   ===================================================================== */
const TABLES = ['days', 'acts', 'exps', 'tasks', 'lists', 'ents', 'types', 'notes', 'memos', 'cats', 'jots', 'photos', 'routines', 'rels'];
function _snap() { const s = {}; for (const t of TABLES) { s[t] = {}; for (const k in DB[t]) s[t][k] = JSON.stringify(DB[t][k]); } return s; }
class Fail extends Error { }
const fail = why => { throw new Fail(why); };

/* ---- 操作の履歴(commit が自動で書く)。DB.log = 古い順。TABLES には入れない ---- */
const LOG_MAX = 1000;
const LOG_PRI = ['jots', 'acts', 'routines', 'tasks', 'memos', 'days', 'photos', 'ents', 'notes'];
let _cd = 0;                    /* commit の入れ子の深さ。最も外側(0)だけがログを書く */
let _logAt = null;              /* 見本データが履歴の時刻を指定する時だけ使う(TS) */
const _cut = (s, n = 80) => { s = String(s == null ? '' : s).replace(/\s+/g, ' ').trim(); return s.length > n ? s.slice(0, n - 1) + '…' : s; };
function _logWhat(t, k, row) {
  try {
    if (t === 'jots') return { ty: '雑記', s: _cut(row.body) };
    if (t === 'acts') {
      if (row.settle) return { ty: '精算', s: _cut(actTitle(row)) };
      if (row.pay) return { ty: '支払い', s: _cut(`${payeeOf(row) || '支払い'} ${yen(payTotal(row))}`) };
      return { ty: row.state === 'plan' ? '予定' : '出来事', s: _cut(row.title) };
    }
    if (t === 'routines') return { ty: '繰り返し', s: _cut(row.tpl.title || (row.tpl.roles || []).map(roleName).join(' ') || KINDS[row.tpl.kind].n) };
    if (t === 'photos') return { ty: '写真', s: _cut(row.cap || '写真') };
    if (t === 'tasks') return { ty: 'ToDo', s: _cut(row.t) };
    if (t === 'memos') return { ty: 'メモ', s: _cut(row.body) };
    if (t === 'days') return { ty: '日記', s: _cut(row.diary) };
    if (t === 'ents') return { ty: 'エンティティ', s: _cut(row.n) };
    if (t === 'notes') return { ty: 'ノート', s: _cut(`${DB.ents[k] ? DB.ents[k].n : ''}のノート`) };
  } catch (e) { }
  return { ty: '', s: '' };
}
function _logPush(label, patch, before, after) {
  try {
    const by = {}; for (const [t, k] of patch) (by[t] = by[t] || []).push(k);
    let t = LOG_PRI.find(x => by[x]), k;
    if (!t) { if (!by.exps) return null; t = 'acts'; k = by.exps[0]; }
    else { const ks = by[t]; k = ks.find(x => { const row = JSON.parse(after[t][x] || before[t][x] || '{}'); return !(row.parent && ks.includes(row.parent)); }) || ks[0]; }
    const a = after[t][k], b = before[t][k], op = b === undefined ? 'add' : a === undefined ? 'del' : 'edit';
    const w = _logWhat(t, k, JSON.parse(a !== undefined ? a : b));
    const e = { i: uid('l'), at: _logAt || nowStamp(), op, t, k, ty: w.ty, s: w.s, lb: label || '直しました' };
    DB.log.push(e); if (DB.log.length > LOG_MAX) DB.log.splice(0, DB.log.length - LOG_MAX);
    return e;
  } catch (e) { return null; }
}
/** どこからも指されなくなった stub を消す(ノート・事実を持つものは残す) */
function _sweepStubs() {
  const used = new Set(), add = x => { if (x) used.add(x); };
  for (const a of Object.values(DB.acts)) {
    (a.roles || []).forEach(r => add(r.e));
    if (a.pay) { a.pay.payers.forEach(p => add(p.who)); a.pay.items.forEach(i => (i.users || []).forEach(add)); }
    if (a.settle) add(a.settle.who);
  }
  for (const r of Object.values(DB.routines)) (r.tpl.roles || []).forEach(x => add(x.e));
  for (const t of Object.values(DB.tasks)) (t.ents || []).forEach(add);
  for (const m of Object.values(DB.memos)) (m.ents || []).forEach(add);
  for (const p of Object.values(DB.photos)) (p.ents || []).forEach(add);
  for (const r of Object.values(DB.rels)) { add(r.a); add(r.b); }
  for (const l of Object.values(DB.lists)) add(l.shop);
  for (const [id, e] of Object.entries(DB.ents)) if (e.stub && !used.has(id) && !DB.notes[id] && !e.facts.length) delete DB.ents[id];
}
function commit(label, fn) {
  const before = _snap(); let extra; _cd++;
  try { extra = fn() || {}; if (_cd === 1) _sweepStubs(); } catch (e) {
    _cd--;
    if (!(e instanceof Fail)) throw e;
    for (const t of TABLES) { const cur = DB[t]; for (const k of Object.keys(cur)) if (!(k in before[t])) delete cur[k]; for (const k in before[t]) if (JSON.stringify(cur[k]) !== before[t][k]) cur[k] = JSON.parse(before[t][k]); }
    return { ok: false, why: e.message };
  }
  _cd--;
  const after = _snap(), patch = [], touched = new Set();
  /* patch = [表, 鍵, 前の値, 変更後の値](JSON 文字列。無い時は undefined)。取り消しは「今の値が変更後と同じ項目」だけ前に戻す */
  for (const t of TABLES) { for (const k in after[t]) if (before[t][k] !== after[t][k]) { patch.push([t, k, before[t][k], after[t][k]]); touched.add(t); } for (const k in before[t]) if (!(k in after[t])) { patch.push([t, k, before[t][k], undefined]); touched.add(t); } }
  const order = {}; touched.forEach(t => { order[t] = Object.keys(before[t]); });
  const entry = _cd === 0 && patch.length ? _logPush(label, patch, before, after) : null;
  _recIdx = null; const saved = save();
  return {
    ok: true, label, ...(saved ? {} : { warn: '保存できませんでした(端末の空きを確かめてください)' }), ...extra, undo() {
      let skipped = 0;
      for (const [t, k, prev, next] of patch) skipped += _undoEntry(t, k, prev, next);
      for (const t of touched) { const cur = DB[t], cp = { ...cur }; for (const k of Object.keys(cur)) delete cur[k]; for (const k of order[t]) if (k in cp) cur[k] = cp[k]; for (const k in cp) if (!(k in cur)) cur[k] = cp[k]; }
      if (entry && !skipped) DB.log = DB.log.filter(x => x.i !== entry.i);
      _recIdx = null; save();
      return { ok: true, skipped };          /* skipped = 後の変更と重なって戻さなかった項目の数 */
    }
  };
}
/** 1行ぶんの取り消し。行(オブジェクト)なら項目ごとに、追加・削除なら行ごとに、今の値が変更後と同じ所だけ前に戻す。戻さなかった数を返す */
function _undoEntry(t, k, prev, next) {
  const cur = DB[t][k], curS = cur === undefined ? undefined : JSON.stringify(cur);
  const pO = prev === undefined ? undefined : JSON.parse(prev), nO = next === undefined ? undefined : JSON.parse(next);
  const isObj = x => x !== null && typeof x === 'object' && !Array.isArray(x);
  if (curS === next && pO !== undefined) { DB[t][k] = pO; return 0; }                  /* 後の変更が無ければ、行ごと元に戻す(項目の並びも元どおり) */
  if (isObj(pO) && isObj(nO) && isObj(cur)) {
    let skipped = 0;
    for (const f of new Set([...Object.keys(pO), ...Object.keys(nO)])) {
      if (JSON.stringify(pO[f]) === JSON.stringify(nO[f])) continue;        /* この変更で変えていない項目は触らない */
      if (JSON.stringify(cur[f]) !== JSON.stringify(nO[f])) { skipped++; continue; }
      if (f in pO) cur[f] = pO[f]; else delete cur[f];
    }
    return skipped;
  }
  if (curS !== next) return 1;
  if (prev === undefined) delete DB[t][k]; else DB[t][k] = pO;
  return 0;
}
const _str = (v, msg) => { const s = String(v == null ? '' : v).trim(); if (!s && msg) fail(msg); return s; };

/* ---- 参照(人・店・支払い方法は全て ents の id。名前だけなら stub を探すか作る)---- */
/** x = {e} か id 文字列か {name}。名前は 登録済み → stub → 新しい stub の順 */
function _ref(x, ty) {
  if (!x) return null;
  if (typeof x === 'string') return x === 'me' || DB.ents[x] ? x : null;
  if (x.e) return x.e === 'me' || DB.ents[x.e] ? x.e : null;
  const n = String(x.name == null ? '' : x.name).trim(); if (!n) return null;
  const hit = Object.entries(DB.ents).filter(([, e]) => e.n === n);
  const f = hit.find(([, e]) => !e.stub) || hit[0]; if (f) return f[0];
  const id = uid('e'); DB.ents[id] = { n, types: ty ? [ty] : [], stub: 1, facts: [] }; return id;
}
function _refRoles(roles) {
  return (roles || []).map(r => { const e = _ref(r.e ? { e: r.e } : r.name ? { name: r.name } : null, ROLE_STUB[r.r]); return e ? { r: r.r, e } : null; }).filter(Boolean);
}
/** 払った人・使った人(person の stub)。'me'・id・{e}・{name}・{who, name} */
function _refWho(x) { if (x && typeof x === 'object' && x.who !== undefined) return x.who && x.who !== 'other' ? _ref(x.who) : _ref(x.name ? { name: x.name } : null, 'person'); return _ref(x, 'person'); }
const _isMethod = id => !!(DB.ents[id] && DB.ents[id].types.includes('method'));
function _refHow(h) { if (h && _isMethod(h)) return h; const f = methodsAll().find(([, e]) => e.n === h); return f ? f[0] : methodDefault(); }

/* ---- 日・写真 ---- */
const daySetDiary = (d, text) => commit('日記を保存', () => { const t = String(text || '').trim(); if (t) DB.days[d] = { diary: t }; else delete DB.days[d]; });
/** at が無ければ d + 今の時刻 */
const photoAdd = o => commit('写真を追加', () => {
  const at = isTS(o.at) && hasT(o.at) ? o.at : o.d ? tsOf(o.d, NOW) : nowStamp();
  const p = { at, cap: String(o.cap || '') };
  if (o.act && DB.acts[o.act]) p.act = o.act;
  if (o.ents && o.ents.length) p.ents = o.ents.filter(e => DB.ents[e]);
  if (o.geo) p.geo = o.geo; if (o.file) p.file = o.file;
  const id = uid('ph'); DB.photos[id] = p; return { id };
});
const photoUpdate = (id, patch) => commit('写真を直しました', () => {
  const p = DB.photos[id]; if (!p) fail('写真が見つかりません');
  const n = { ...p, ...patch };
  if (!isTS(n.at) || !hasT(n.at)) fail('日時の形が正しくありません');
  if (n.act && !DB.acts[n.act]) delete n.act;
  if (n.ents) n.ents = n.ents.filter(e => DB.ents[e]);
  for (const k of Object.keys(n)) if (n[k] == null || n[k] === '' && k !== 'cap' || (Array.isArray(n[k]) && !n[k].length)) delete n[k];
  DB.photos[id] = n;
});
const photoRemove = id => commit('写真を外した', () => { if (!DB.photos[id]) fail('写真が見つかりません'); delete DB.photos[id]; });

/* ---- 繰り返し(routines) ---- */
function _cleanTpl(t, old) {
  const n = { ...old, ...t };
  if (!KINDS[n.kind]) fail('種類を選んでください');
  if (n.pay) { n.pay = { amt: +n.pay.amt || 0 }; n.kind = 'pay'; }
  if (n.kind !== 'pay') n.title = _str(n.title, '題を入れてください');
  if (n.at != null && n.at !== '' && !isHM(n.at)) fail('時刻を入れてください');
  if (n.endAt != null && n.endAt !== '' && !isHM(n.endAt)) fail('終わりの時刻を入れてください');
  if (!n.at) { delete n.at; delete n.endAt; }
  n.roles = _refRoles(n.roles);
  if (n.exp) n.exp = { cat: DB.cats[n.exp.cat] ? n.exp.cat : Object.keys(DB.cats)[0] };
  for (const k of Object.keys(n)) if (n[k] == null || n[k] === '' || n[k] === false) delete n[k];
  return n;
}
function _cleanRoutine(r) {
  if (!['day', 'week', 'month'].includes(r.every)) fail('繰り返しの単位を選んでください');
  if (!isDate(r.from)) fail('始まりの日を入れてください');
  if (r.until && (!isDate(r.until) || r.until < r.from)) fail('終わりの日は始まりの日より後にしてください');
  const o = { every: r.every, from: r.from }; if (r.until) o.until = r.until; if ((r.skip || []).length) o.skip = [...new Set(r.skip)].sort();
  o.tpl = r.tpl; return o;
}
const routineAdd = o => commit('繰り返しを追加', () => { const id = uid('rt'); DB.routines[id] = _cleanRoutine({ ...o, tpl: _cleanTpl(o.tpl || {}) }); return { id }; });
const routineUpdate = (id, patch) => commit('繰り返しを直しました', () => {
  const r = DB.routines[id]; if (!r) fail('繰り返しが見つかりません');
  const { tpl, ...rest } = patch; DB.routines[id] = _cleanRoutine({ ...r, ...rest, tpl: tpl ? _cleanTpl(tpl, r.tpl) : r.tpl });
});
function _dropRoutineRefs(id) {
  for (const a of Object.values(DB.acts)) if (a.routine === id) delete a.routine;
  for (const t of Object.values(DB.tasks)) if (t.act === id) delete t.act;
}
const routineRemove = id => commit('繰り返しを消しました', () => { if (!DB.routines[id]) fail('繰り返しが見つかりません'); delete DB.routines[id]; _dropRoutineRefs(id); });
function _skip(id, d, on) { const r = DB.routines[id]; if (!r || !isDate(d)) fail('繰り返しが見つかりません'); const s = (r.skip || []).filter(x => x !== d); if (on) s.push(d); if (s.length) r.skip = s.sort(); else delete r.skip; }
const routineSkip = (id, d, on = true) => commit(on ? 'この回をなしにしました' : 'この回を戻しました', () => _skip(id, d, on));

/* ---- アクティビティ(記録・予定) ---- */
function _cleanAct(o, old) {
  const a = { ...old, ...o };
  if (!KINDS[a.kind]) fail('種類を選んでください');
  if (!['pay', 'settle', 'sleep'].includes(a.kind)) a.title = _str(a.title, '題を入れてください');
  if (!isTS(a.start)) fail('日付を入れてください');
  if (a.end) {
    if (!isTS(a.end)) fail('終わりの日時の形が正しくありません');
    if (hasT(a.start) !== hasT(a.end)) fail('始まりが日だけなら、終わりも日だけにしてください');
    if (a.end < a.start) fail('終わりは始まり以降にしてください');
    if (a.end === a.start) delete a.end;
  } else delete a.end;
  if (a.parent && !DB.acts[a.parent]) delete a.parent;
  a.roles = _refRoles(a.roles);
  a.state = a.state === 'plan' ? 'plan' : 'done';
  if (a.title === '') delete a.title;
  for (const k of ['date', 'date2', 'allDay', 'rep', 'hol', 'src', 'tpl', 'virt']) delete a[k];
  for (const k of ['note', 'alarm', 'planned', 'routine', 'unsure', 'via', 'jot', 'parent', 'pay', 'settle']) if (!a[k]) delete a[k];
  return a;
}
/** 繰り返しの回(仮の回)を作る入力から決まりを作る。a は _cleanAct 済み */
function _routineFrom(a, rep, o) {
  const tpl = { kind: a.kind, roles: a.roles }; if (a.title) tpl.title = a.title; if (hasT(a.start)) { tpl.at = tOf(a.start); if (a.end && !aDate2(a)) tpl.endAt = tOf(a.end); else if (a.end) tpl.endAt = tOf(a.end); }
  if (a.note) tpl.note = a.note; if (a.alarm) tpl.alarm = a.alarm;
  if (o && o.pay && o.pay.amt) tpl.pay = { amt: +o.pay.amt }; if (o && o.exp && o.exp.cat) tpl.exp = { cat: o.exp.cat };
  const id = uid('rt'); DB.routines[id] = _cleanRoutine({ every: rep.u || rep.every, from: aDate(a), until: rep.until, tpl: _cleanTpl(tpl) });
  return id;
}
/** 作る。o = {kind, title, start, end?, state, parent?, roles, note…, rep?:{u}}。rep があれば決まりを作り {id:'決まりid@最初の日付', routine} */
const actAdd = o => commit(o.state === 'plan' ? '予定を追加' : '記録を追加', () => {
  if (o.kind === 'settle' && !o.settle) fail('精算は paySettle で記録してください');
  const a = _cleanAct({ roles: [], ...o });
  if (o.rep && (o.rep.u || o.rep.every)) { const rid = _routineFrom(a, o.rep, o); return { id: rid + '@' + aDate(a), routine: rid }; }
  const id = uid('a'); DB.acts[id] = a; return { id };
});
/** 直す(渡した項目だけ。消す項目は null)。'決まりid@日付' は決まり(tpl)を直す。rep:null は決まりをやめて、その日の1回だけの予定にする */
const actUpdate = (id, patch) => commit('直しました', () => {
  const i = id.indexOf('@');
  if (!DB.acts[id] && i > 0) {
    const rid = id.slice(0, i), d = id.slice(i + 1), r = DB.routines[rid], v = A(id); if (!r || !v) fail('見つかりません');
    if (patch.rep === null) {
      const { rep, virt, routine, ...base } = { ...v, ...patch }; if (patch.start === undefined) base.start = v.start;
      const a = _cleanAct({ ...base, state: 'plan' }); const nid = uid('a');
      const x = xOf(id); delete DB.routines[rid]; _dropRoutineRefs(rid); DB.acts[nid] = a; if (x && a.pay) DB.exps[nid] = { ...x }; return { id: nid };
    }
    const t = {}, p = patch;
    for (const k of ['kind', 'title', 'note', 'alarm', 'roles']) if (k in p) t[k] = p[k];
    if ('start' in p) t.at = hasT(p.start) ? tOf(p.start) : (isHM(p.start) ? p.start : null);
    if ('end' in p) t.endAt = p.end ? (hasT(p.end) ? tOf(p.end) : isHM(p.end) ? p.end : null) : null;
    const rest = {}; if (p.rep && p.rep.u) rest.every = p.rep.u;
    DB.routines[rid] = _cleanRoutine({ ...r, ...rest, tpl: _cleanTpl(t, r.tpl) });
    return { id };
  }
  const a = DB.acts[id]; if (!a) fail('見つかりません');
  const { rep, ...p } = patch; DB.acts[id] = _cleanAct(p, a);
});
/** 消す(子・支出・結び付きごと)。'決まりid@日付' はその回だけなし */
const actRemove = id => commit('消しました', () => {
  if (!DB.acts[id] && id.includes('@')) { _skip(id.split('@')[0], id.split('@')[1], true); return { n: 1 }; }
  if (!DB.acts[id]) fail('見つかりません');
  const ids = [id, ...descOf(id)];
  ids.forEach(k => { delete DB.acts[k]; delete DB.exps[k]; });
  for (const [sid, s] of Object.entries(DB.acts)) if (s.settle && s.settle.pays.some(p => ids.includes(p))) { s.settle.pays = s.settle.pays.filter(p => !ids.includes(p)); if (!s.settle.pays.length) delete DB.acts[sid]; }
  for (const t of Object.values(DB.tasks)) { if (ids.includes(t.act)) delete t.act; if (ids.includes(t.payAct)) delete t.payAct; }
  for (const m of Object.values(DB.memos)) if (ids.includes(m.act)) delete m.act;
  for (const p of Object.values(DB.photos)) if (ids.includes(p.act)) delete p.act;
  return { n: ids.length };
});
/** 親を変える(null で外へ出す)。親の日の外なら、親の最初の日へ動かす */
const actMove = (id, parent) => commit('移しました', () => {
  const a = DB.acts[id]; if (!a) fail('見つかりません');
  if (parent) {
    const p = DB.acts[parent]; if (!p || p.kind === 'pay') fail('入れ先が見つかりません'); if (parent === id || descOf(id).includes(parent)) fail('自分の中へは入れられません');
    a.parent = parent;
    const pe = dOf(p.end) || aDate(p);
    if (aDate(a) < aDate(p) || aDate(a) > pe) { const n = dDiff(aDate(p), aDate(a)); a.start = tsOf(dAdd(aDate(a), n), tOf(a.start)); if (a.end) a.end = tsOf(dAdd(dOf(a.end), n), tOf(a.end)); }
  } else delete a.parent;
});
const _planned = (src, base, routine) => (base.start !== src.start || base.end !== src.end) && (hasT(src.start) || routine || dOf(src.start) !== dOf(base.start)) ? { start: src.start, ...(src.end ? { end: src.end } : {}) } : null;
/** 予定を記録にする。id は予定('決まりid@日付' も可)。o = {start?, end?, amt?, how?} → {id}。支払い予定は支払い(payers・品目)になる */
const planRecord = (id, o = {}) => commit('記録にしました', () => {
  const src = A(id); if (!src || !isPlan(src)) fail('予定が見つかりません');
  const base = JSON.parse(JSON.stringify(src)); delete base.virt; delete base.alarm; base.state = 'done';
  const fix = (v, d0) => isHM(v) ? tsOf(d0, v) : v;
  if (o.start) { base.start = fix(o.start, dOf(src.start)); if (o.end === undefined && src.end && hasT(src.end) && hasT(base.start)) { const m = tsMin(base.start) - tsMin(src.start), e = tsMin(src.end) + m; base.end = tsOf(dAdd('1970-01-01', Math.floor(e / 1440)), m2hm(e % 1440 < 0 ? e % 1440 + 1440 : e % 1440)); } }
  if (o.end !== undefined) { if (o.end) base.end = fix(o.end, dOf(base.start)); else delete base.end; }
  const nid = src.virt ? uid('a') : id;
  if (base.pay) {
    const amt = +o.amt || payTotal(src);
    if (!hasT(base.start)) base.start = tsOf(dOf(base.start), '09:00');
    if (base.end) delete base.end;
    base.pay = { ...(base.pay.items.length ? base.pay : { payers: [], items: [] }) };
    if (!base.pay.items.length) base.pay.items = [{ id: uid('i'), n: src.title || payeeOf(src) || '支払い', price: amt, tax: TAX_DEFAULT, users: ['me'] }];
    base.pay.payers = [{ who: 'me', amt: base.pay.items.reduce((s, i) => s + itemAmt(i), 0), how: _refHow(o.how) }];
    delete base.pay.amt;
    if (src.virt) { const x = xOf(id); if (x) DB.exps[nid] = { ...x }; }
  }
  const pl = _planned(src, base, !!src.routine); if (pl) base.planned = pl; else delete base.planned;
  DB.acts[nid] = _cleanAct(base);
  return { id: nid };
});

/* ---- 支払い(事実)と支出(家計簿) ---- */
function _cleanItems(items) {
  return (items || []).map(i => {
    const users = (i.users && i.users.length ? i.users : ['me']).map(u => _refWho(u)).filter(Boolean);
    const r = { id: i.id || uid('i'), n: _str(i.n), tax: i.tax === 8 ? 8 : TAX_DEFAULT, users: users.length ? users : ['me'] };
    const lines = (i.lines || []).map((l, j) => ({ n: j ? _str(l.n) || ((l.v || 0) < 0 ? '割引' : 'オプション') : '', k: l.k === 'pct' ? 'pct' : 'amt', v: +l.v || 0 })).filter((l, j) => j === 0 || l.v !== 0);
    if (lines.length > 1) r.lines = lines; else r.price = lines.length ? lines[0].v : (+i.price || 0);
    if (i.memo) r.memo = String(i.memo); if (i.unk) r.unk = 1; if (i.unsure) r.unsure = 1;
    return r;
  }).filter(i => i.n || itemAmt(i));
}
function _fitPayers(payers, total) {
  let p = (payers || []).map(x => ({ who: _refWho(x), amt: +x.amt || 0, how: _refHow(x.how) })).filter(x => x.who);
  if (!p.length) p = [{ who: 'me', amt: total, how: methodDefault() }];
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
  if (x.itemCat) { const ic = {}, ids = a.pay.items.map(i => i.id); Object.entries(x.itemCat).forEach(([k, v]) => { if (DB.cats[v] && v !== e.cat && ids.includes(k)) ic[k] = v; }); if (Object.keys(ic).length) e.itemCat = ic; }
  if (x.sp && x.sp !== catSp(e.cat)) e.sp = x.sp; if (x.fx && x.fx !== catFx(e.cat)) e.fx = x.fx; if (x.memo) e.memo = String(x.memo);
  DB.exps[id] = e;
  if (xAmt(id) <= 0) delete DB.exps[id];
}
/** 入力の start を TS にする('HH:MM' なら o.date と、日だけなら時刻を足す) */
function _payStart(o, old) {
  let s = o.start || (old && old.start) || nowStamp();
  if (isHM(s)) s = tsOf(o.date || TODAY, s);
  if (isTS(s) && !hasT(s)) s = tsOf(s, s === TODAY ? NOW : '12:00');
  return s;
}
const _unsure = u => Array.isArray(u) ? (u.length ? u.slice() : null) : u ? true : null;
/** 支払いを保存(作る・直す)。o = {id?, start(TS), parent?, payee:{e?|name?}, roles?, items:[{n, price|lines, tax, users, memo?, unk?, unsure?}], amt?, payers?:[{who, amt, how}],
    note?, unsure?, via?, exp:{basis, n?, me?, cat, itemCat?:{品目id:cat}, sp?, fx?, memo?}, doneTasks?} → {id} */
const paySave = o => commit(o.id ? '支払いを保存しました' : '支払いを記録しました', () => {
  const id = o.id || uid('p'), old = DB.acts[id] || {};
  const items = _cleanItems(o.items), total = items.length ? items.reduce((s, i) => s + itemAmt(i), 0) : (+o.amt || 0);
  if (total <= 0) fail('金額を入れてください');
  const roles = [...(o.payee && (o.payee.e || o.payee.name) ? [{ r: '支払先', ...o.payee }] : []), ...(o.roles || []).filter(r => r.r !== '支払先')];
  const pay = { payers: _fitPayers(o.payers, total), items };
  if (!items.length) pay.amt = total;
  const a = { ...old, kind: 'pay', title: '', start: _payStart(o, old), end: null, state: 'done', parent: o.parent || null, roles, note: o.note, pay, unsure: _unsure(o.unsure), via: o.via || old.via };
  DB.acts[id] = _cleanAct(a);
  _fitExp(id, o.exp || DB.exps[id] || { basis: 'items', cat: 'eatout' });
  (o.doneTasks || []).forEach(k => { const t = DB.tasks[k]; if (t && !t.done) { t.done = aDate(DB.acts[id]); t.payAct = id; } });
  return { id };
});
/** 支払い予定(支払いと同じ形の state:'plan')。o = {id?, start, payee, title?, amt, cat, rep?:{u}}。rep があれば決まり → {id:'決まりid@日付', routine} */
const payPlanSave = o => commit(o.id ? '支払い予定を保存しました' : '支払い予定を追加', () => {
  const amt = +o.amt || 0; if (amt <= 0) fail('金額を入れてください');
  const cat = DB.cats[o.cat] ? o.cat : Object.keys(DB.cats)[0];
  const start = isTS(o.start) ? o.start : fail('日付を入れてください');
  const roles = o.payee && (o.payee.e || o.payee.name) ? [{ r: '支払先', ...o.payee }] : [];
  if (o.rep && (o.rep.u || o.rep.every)) {
    const tpl = { kind: 'pay', roles, pay: { amt }, exp: { cat }, ...(o.title ? { title: o.title } : {}), ...(hasT(start) ? { at: tOf(start) } : {}) };
    if (o.id && o.id.includes('@')) { const rid = o.id.split('@')[0]; DB.routines[rid] = _cleanRoutine({ ...DB.routines[rid], every: o.rep.u || o.rep.every, tpl: _cleanTpl(tpl, {}) }); return { id: o.id, routine: rid }; }
    const rid = uid('rt'); DB.routines[rid] = _cleanRoutine({ every: o.rep.u || o.rep.every, from: dOf(start), tpl: _cleanTpl(tpl) }); return { id: rid + '@' + dOf(start), routine: rid };
  }
  const id = o.id && DB.acts[o.id] ? o.id : uid('p');
  DB.acts[id] = _cleanAct({ ...(DB.acts[id] || {}), kind: 'pay', title: o.title || '', start, end: null, state: 'plan', roles, pay: { amt, payers: [], items: [] } });
  DB.exps[id] = { basis: 'items', cat };
  return { id };
});
/** 支出だけ直す(カテゴリ・決め方・特別感・メモ・品目別カテゴリ) */
const expUpdate = (id, patch) => commit('支出を直しました', () => { const a = DB.acts[id]; if (!a || !a.pay) fail('支払いが見つかりません'); _fitExp(id, { ...(DB.exps[id] || { basis: 'items', cat: 'eatout' }), ...patch }); });
/** 品目を消す(最後の1件も可。その時は合計だけ残る) */
const itemRemove = (id, iid) => commit('品目を消しました', () => {
  const a = DB.acts[id]; if (!a || !a.pay || !a.pay.items.some(i => i.id === iid)) fail('品目が見つかりません');
  const oldTotal = payTotal(a); a.pay.items = a.pay.items.filter(i => i.id !== iid);
  const x = DB.exps[id];
  if (x && x.itemCat) { delete x.itemCat[iid]; if (!Object.keys(x.itemCat).length) delete x.itemCat; }
  if (!a.pay.items.length) a.pay.amt = oldTotal;
  else { const t = payTotal(a); if (a.pay.payers.length === 1) a.pay.payers[0].amt = t; else { let diff = oldTotal - t; for (let i = a.pay.payers.length - 1; i >= 0 && diff > 0; i--) { const take = Math.min(diff, a.pay.payers[i].amt); a.pay.payers[i].amt -= take; diff -= take; } } }
  if (x) _fitExp(id, x);
});
/** 精算: on = 未精算の貸し借りを相手ごとにまとめて settle の Act を作る / off = その支払いを settle から外す(空なら settle を消す)。o = {who?, date?, how?} */
const paySettle = (ids, on = true, o = {}) => commit(on ? '精算済みにしました' : '未精算に戻しました', () => {
  ids = [].concat(ids);
  for (const id of ids) { const a = DB.acts[id]; if (!a || !a.pay) fail('支払いが見つかりません'); }
  if (on) {
    const g = new Map();
    for (const id of ids) for (const d of debtsOf(id)) { if (d.settle || (o.who !== undefined && (d.who || null) !== (o.who || null))) continue; const k = d.who || ''; if (!g.has(k)) g.set(k, { who: d.who, amt: 0, pays: [] }); const r = g.get(k); r.amt += d.amt; if (!r.pays.includes(id)) r.pays.push(id); }
    if (!g.size) fail('精算するものがありません');
    const start = isTS(o.date) && hasT(o.date) ? o.date : isDate(o.date) && o.date !== TODAY ? tsOf(o.date, '12:00') : nowStamp();
    for (const r of g.values()) DB.acts[uid('s')] = { kind: 'settle', start, state: 'done', roles: [], settle: { who: r.who || null, dir: r.amt >= 0 ? 'in' : 'out', amt: Math.abs(r.amt), ...(o.how ? { how: _refHow(o.how) } : {}), pays: r.pays } };
  } else {
    for (const [sid, s] of Object.entries(DB.acts)) {
      if (!s.settle || !s.settle.pays.some(p => ids.includes(p)) || (o.who !== undefined && (s.settle.who || null) !== (o.who || null))) continue;
      s.settle.pays = s.settle.pays.filter(p => !ids.includes(p));
      if (!s.settle.pays.length) { delete DB.acts[sid]; continue; }
      const amt = s.settle.pays.reduce((t, p) => t + debtsOf(p).filter(d => (d.who || null) === (s.settle.who || null)).reduce((u, d) => u + d.amt, 0), 0);
      s.settle.amt = Math.abs(amt); s.settle.dir = amt >= 0 ? 'in' : 'out';
    }
  }
});
/** 確かめた(unsure を消す。支払いは品目の unsure も)。rows = [{t:'acts'|'tasks'|'memos', k}] */
const confirmRows = rows => commit('確かめました', () => {
  let n = 0;
  for (const r of [].concat(rows || [])) {
    if (!['acts', 'tasks', 'memos'].includes(r.t)) continue;
    const row = DB[r.t][r.k]; if (!row) continue;
    if (row.unsure) { delete row.unsure; n++; }
    if (r.t === 'acts' && row.pay) row.pay.items.forEach(i => { if (i.unsure) { delete i.unsure; n++; } });
  }
  if (!n) fail('未確認のものがありません');
});
/** 起床: ts より前24時間以内に始まった end の無い睡眠があれば end = ts。無ければ「起床」の睡眠を1件作る → {id} */
const sleepWake = ts => commit('起床を記録', () => {
  if (!isTS(ts) || !hasT(ts)) fail('日時を入れてください');
  const hit = Object.entries(DB.acts).filter(([, a]) => a.kind === 'sleep' && a.state === 'done' && !a.end && hasT(a.start) && a.start < ts && tsMin(ts) - tsMin(a.start) <= 1440).sort((x, y) => y[1].start.localeCompare(x[1].start))[0];
  if (hit) { DB.acts[hit[0]].end = ts; return { id: hit[0] }; }
  const id = uid('a'); DB.acts[id] = { kind: 'sleep', title: '起床', start: ts, state: 'done', roles: [] }; return { id };
});

/* ---- ToDo ---- */
const taskAdd = o => commit('ToDoを追加', () => {
  const id = uid('t'); const t = { t: _str(o.t, 'やることを入れてください'), list: DB.lists[o.list] ? o.list : Object.keys(DB.lists)[0] };
  for (const k of ['due', 'pri', 'act', 'memo', 'rep', 'unsure', 'via', 'jot']) if (o[k]) t[k] = o[k];
  if (o.ents && o.ents.length) t.ents = o.ents.filter(e => DB.ents[e]); if (o.sub && o.sub.length) t.sub = o.sub; DB.tasks[id] = t; return { id };
});
const taskUpdate = (id, patch) => commit('ToDoを直しました', () => { const t = DB.tasks[id]; if (!t) fail('ToDoが見つかりません'); const n = { ...t, ...patch }; n.t = _str(n.t, 'やることを入れてください'); for (const k of Object.keys(n)) if (n[k] == null || n[k] === '' || (Array.isArray(n[k]) && !n[k].length)) delete n[k]; if (!n.list) n.list = t.list; DB.tasks[id] = n; });
/** 完了を切り替える。繰り返しは次の回を作る */
const taskToggle = id => commit('', () => {
  const t = DB.tasks[id]; if (!t) fail('ToDoが見つかりません');
  if (t.done) { delete t.done; return { done: false }; }
  t.done = TODAY;
  if (t.rep && t.due) { const step = t.rep.u === 'day' ? 1 : t.rep.u === 'week' ? 7 : 0; const nd = step ? dAdd(t.due, step) : ymAdd(ymOf(t.due), 1) + t.due.slice(7); const n = { ...JSON.parse(JSON.stringify(t)), due: nd, sub: (t.sub || []).map(s => ({ t: s.t })) }; for (const k of ['done', 'unsure', 'via', 'jot', 'payAct']) delete n[k]; DB.tasks[uid('t')] = n; }
  return { done: true };
});
const taskRemove = id => commit('ToDoを消しました', () => { if (!DB.tasks[id]) fail('ToDoが見つかりません'); delete DB.tasks[id]; });
const listAdd = n => commit(`リスト「${String(n || '').trim()}」を追加しました`, () => { const v = _str(n, 'リストの名前を入れてください'); if (Object.values(DB.lists).some(x => x.n === v)) fail('同じ名前のリストがあります'); const id = uid('l'); DB.lists[id] = { n: v }; return { id }; });
const listRemove = id => commit('リストを消しました', () => { if (Object.keys(DB.lists).length <= 1) fail('リストは1つ以上必要です'); if (Object.values(DB.tasks).some(t => t.list === id && !t.done)) fail('未完了のToDoがあるので先に移してください'); delete DB.lists[id]; const f = Object.keys(DB.lists)[0]; Object.values(DB.tasks).forEach(t => { if (t.list === id) t.list = f; }); });
const listRename = (id, n) => commit('リストの名前を直しました', () => { const l = DB.lists[id]; if (!l) fail('リストが見つかりません'); const v = _str(n, 'リストの名前を入れてください'); if (Object.entries(DB.lists).some(([k, x]) => k !== id && x.n === v)) fail('同じ名前のリストがあります'); l.n = v; });
const listSetShop = (id, eid) => commit('既定の店を直しました', () => { const l = DB.lists[id]; if (!l) fail('リストが見つかりません'); if (eid && DB.ents[eid]) l.shop = eid; else delete l.shop; });
/** 「明日 牛乳 #買い物 !」のような1行を解釈 → {t, due?, list?, pri?} */
function parseTask(s) {
  let t = ' ' + String(s || '').trim() + ' ', due, list, pri;
  t = t.replace(/[!！]/g, () => { pri = 1; return ''; });
  t = t.replace(/[#＃](\S+)/, (_, n) => { const k = Object.keys(DB.lists).find(k => DB.lists[k].n === n); if (k) { list = k; return ''; } return _; });
  const d = parseDateWord(t); if (d) { due = d.date; t = t.replace(d.word, ' '); }
  return { t: t.replace(/\s+/g, ' ').trim(), due, list, pri };
}
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
/** すばやく書く: 1行から種類を推し量る → {type:'pay'|'plan'|'task'|'memo', …}。pay/plan の時刻は TS(start, end) */
function parseQuick(s) {
  const raw = String(s || '').trim(); if (!raw) return null;
  let t = ' ' + raw + ' ', time, time2;
  const d = parseDateWord(t); if (d) t = t.replace(d.word, ' ');
  t = t.replace(/(\d{1,2})[:：](\d{2})(?:\s*[-–〜~]\s*(\d{1,2})[:：](\d{2}))?/, (_, h, mi, h2, m2) => { time = `${pad2(h)}:${mi}`; if (h2) time2 = `${pad2(h2)}:${m2}`; return ' '; });
  t = t.replace(/(\d{1,2})時(半|(\d{1,2})分)?/, (_, h, half, mi) => { if (time) return _; time = `${pad2(h)}:${half === '半' ? '30' : pad2(mi || 0)}`; return ' '; });
  let amt; t = t.replace(/[¥￥]\s?([\d,]+)|([\d,]{3,})\s?円|(?:^|\s)([\d,]{3,})(?=\s|$)/, (_, a, b, c) => { amt = +(a || b || c).replace(/,/g, ''); return ' '; });
  const ents = Object.keys(DB.ents).filter(id => { const e = DB.ents[id]; if (id === 'me' || e.types.includes('method')) return false; const short = e.n.split(' ').pop(); return raw.includes(e.n) || (short.length > 1 && raw.includes(short)); });
  const text = t.replace(/\s+/g, ' ').trim();
  if (amt) { const pe = ents.find(id => !DB.ents[id].stub && isUnder(DB.ents[id].types[0], 'place')); const dd = d && d.date <= TODAY ? d.date : TODAY; return { type: 'pay', amt, start: tsOf(dd, time || NOW), payee: pe ? { e: pe } : text ? { name: text } : null, text, ents }; }
  const date = d ? d.date : TODAY;
  if ((d && date > TODAY && (time || !/[#＃]/.test(raw))) || (time && (date > TODAY || (date === TODAY && time > NOW)))) return { type: 'plan', title: text || '予定', start: tsOf(date, time), ...(time && time2 && time2 > time ? { end: tsOf(date, time2) } : {}), ents };
  if (/[#＃!！]/.test(raw) || (d && date >= TODAY)) { const p = parseTask(raw); return { type: 'task', ...p, ents }; }
  return { type: 'memo', body: raw, ents };
}

/* ---- 買い物リストとの連動・重複・行き来(ToDo ⇄ 予定) ---- */
function shopMatches(items) {
  const names = new Set((items || []).map(i => String(i.n || '').trim()).filter(Boolean));
  return Object.entries(DB.tasks).filter(([, t]) => !t.done && DB.lists[t.list] && DB.lists[t.list].shop && names.has(t.t.trim()));
}
/** 重複の疑い: 同じ写真(o.photo = 写真のハッシュ)、または同じ日・同じ合計・時刻が5分以内の支払い [[id, act]](o = {id?, start(TS か 'HH:MM'+date), total, photo?}) */
function payDup(o) {
  const d = o.date || dOf(o.start), tm = isHM(o.start) ? o.start : tOf(o.start) || NOW;
  return Object.entries(DB.acts).filter(([id, a]) => a.pay && !isPlan(a) && id !== o.id && ((o.photo && a.photo === o.photo) || (aDate(a) === d && payTotal(a) === o.total && o.total > 0 && Math.abs(hm2m(aStart(a)) - hm2m(tm)) <= 5)));
}
/** 支払いに付いたレシート写真のハッシュを覚える(paySave は作り直すので、その後に呼ぶ。act の photo は再保存でも残る) */
const payPhotoSet = (id, hash) => commit('', () => { const a = DB.acts[id]; if (!a || !a.pay) fail('支払いが見つかりません'); if (hash) a.photo = hash; });
const oldestYm = () => ymOf([...Object.values(DB.acts).map(a => aDate(a)), ...Object.keys(DB.days).filter(d => DB.days[d].diary)].reduce((m, d) => d < m ? d : m, TODAY));
const _entRole = e => DB.ents[e] && isUnder(DB.ents[e].types[0], 'place') ? '場所' : DB.ents[e] && isUnder(DB.ents[e].types[0], 'project') ? 'プロジェクト' : '同席';
/** ToDo を予定にする(ToDo は消える)。o = {start, end?}(TS。旧形 {date, allDay, start:'HH:MM', end} も受ける)→ {id}(繰り返しなら {id:'決まりid@日付', routine}) */
const taskToPlan = (tid, o) => commit('予定にしました', () => {
  const t = DB.tasks[tid]; if (!t) fail('ToDoが見つかりません');
  let start = o.start, end = o.end;
  if (o.date) { start = o.allDay || !start ? o.date : isHM(start) ? tsOf(o.date, start) : start; end = o.allDay || !end ? null : isHM(end) ? tsOf(o.date, end) : end; }
  const a = _cleanAct({ kind: 'event', title: t.t, start, end, state: 'plan', roles: (t.ents || []).map(e => ({ r: _entRole(e), e })), note: t.memo });
  delete DB.tasks[tid];
  if (t.rep) { const rid = _routineFrom(a, t.rep); return { id: rid + '@' + aDate(a), routine: rid }; }
  const id = uid('a'); DB.acts[id] = a; return { id };
});
/** 予定を ToDo にする(予定は消える。支払い予定は不可。'決まりid@日付' か決まりid は決まりごと消えて ToDo の rep になる)→ {id} */
const planToTask = aid => commit('ToDoにしました', () => {
  const rid = DB.routines[aid] ? aid : !DB.acts[aid] && aid.includes('@') ? aid.split('@')[0] : null;
  if (rid) {
    const r = DB.routines[rid]; if (!r) fail('予定が見つかりません'); if (r.tpl.pay) fail('支払い予定は ToDo にできません');
    const d = aid.includes('@') ? aid.split('@')[1] : repNext(r), ents = (r.tpl.roles || []).map(x => x.e).filter(Boolean), id = uid('t');
    DB.tasks[id] = { t: r.tpl.title || KINDS[r.tpl.kind].n, due: d, list: Object.keys(DB.lists)[0], ...(ents.length ? { ents } : {}), ...(r.tpl.note ? { memo: r.tpl.note } : {}), rep: { u: r.every } };
    delete DB.routines[rid]; _dropRoutineRefs(rid); return { id };
  }
  const a = DB.acts[aid]; if (!a || !isPlan(a)) fail('予定が見つかりません'); if (a.pay) fail('支払い予定は ToDo にできません');
  const id = uid('t'), ents = (a.roles || []).map(r => r.e).filter(Boolean);
  DB.tasks[id] = { t: a.title, due: aDate(a), list: Object.keys(DB.lists)[0], ...(ents.length ? { ents } : {}), ...(a.note ? { memo: a.note } : {}) };
  for (const t of Object.values(DB.tasks)) if (t.act === aid) delete t.act;
  [aid, ...descOf(aid)].forEach(k => { delete DB.acts[k]; delete DB.exps[k]; });
  for (const p of Object.values(DB.photos)) if (p.act === aid) delete p.act;
  return { id };
});

/* ---- メモ ---- */
const memoAdd = (body, o = {}) => commit('メモを保存しました', () => { const id = uid('m'); const m = { body: _str(body, '本文を入れてください'), at: nowStamp(), ents: (o.ents || []).filter(e => DB.ents[e]) }; if (o.act) m.act = o.act; DB.memos[id] = m; return { id }; });
const memoUpdate = (id, patch) => commit('メモを直しました', () => { const m = DB.memos[id]; if (!m) fail('メモが見つかりません'); if (patch.body !== undefined) m.body = _str(patch.body, '本文を入れてください'); if (patch.ents) m.ents = patch.ents.filter(e => DB.ents[e]); if ('act' in patch) { if (patch.act) m.act = patch.act; else delete m.act; } });
const memoPin = id => commit('', () => { const m = DB.memos[id]; if (!m) fail('メモが見つかりません'); if (m.pin) delete m.pin; else m.pin = 1; return { pin: !!m.pin }; });
const memoRemove = id => commit('メモを消しました', () => { if (!DB.memos[id]) fail('メモが見つかりません'); delete DB.memos[id]; });

/* ---- エンティティ・事実・関係・種類・ノート ---- */
const entAdd = o => commit(`「${String(o.n || '').trim()}」を追加しました`, () => { const n = _str(o.n, '名前を入れてください'); const types = (o.types || []).filter(t => DB.types[t]); if (!types.length) fail('種類を選んでください'); const id = uid('e'); DB.ents[id] = { n, types, facts: _cleanFacts(o.facts) }; return { id }; });
function _cleanFacts(fs) { return (fs || []).filter(f => f.k && String(f.v == null ? '' : f.v).trim()).map(f => ({ k: f.k, v: String(f.v).trim(), ...(f.from ? { from: f.from } : {}), ...(f.to ? { to: f.to } : {}), ...(f.why ? { why: f.why } : {}), ...(f.rare ? { rare: 1 } : {}) })); }
const entUpdate = (id, patch) => commit('直しました', () => {
  const e = DB.ents[id]; if (!e) fail('エンティティが見つかりません');
  if (patch.n !== undefined) e.n = _str(patch.n, '名前を入れてください');
  if (patch.types) { const t = patch.types.filter(k => DB.types[k]); if (!t.length) fail('種類は1つ以上必要です'); e.types = t; }
  if (patch.facts) e.facts = _cleanFacts(patch.facts);
  if ('stub' in patch) { if (patch.stub) e.stub = 1; else delete e.stub; }
  if (!e.stub && !e.types.length) fail('種類を選んでください');
});
/** 事実の値を変える。keepHist なら前の値を残す(今の行に to と why、新しい行に from)。値を空にすると、その項目の行を全て消す(keepHist なら今の行だけ) */
const factSet = (id, k, v, o = {}) => commit('直しました', () => {
  const e = DB.ents[id]; if (!e) fail('エンティティが見つかりません'); const val = _str(v); _coarse(o.since, '始まり');
  const i = e.facts.findIndex(f => f.k === k && !f.to), f = e.facts[i];
  if (!f) { if (val) e.facts.push({ k, v: val, ...(o.since ? { from: o.since } : {}) }); return; }
  if (!val) { e.facts = o.keepHist ? e.facts.filter(x => x !== f) : e.facts.filter(x => x.k !== k); return; }
  if (o.keepHist && f.v !== val) { const at = o.since || ymOf(TODAY); f.to = at; if (o.why) f.why = o.why; e.facts.splice(i, 0, { k, v: val, from: at }); return; }
  f.v = val;
});
/** 消す。支払い(払った人・使った人・精算)から指されていれば stub に戻して名前だけ残す。支払い方法は使われていれば消せない */
const entRemove = id => commit('エンティティを消しました', () => {
  const e = DB.ents[id]; if (!e) fail('エンティティが見つかりません'); if (id === 'me') fail('自分は消せません');
  if (_isMethod(id) && _methodUsed(id)) fail('使われている支払い方法は消せません');
  let keep = false;
  for (const a of Object.values(DB.acts)) {
    a.roles = (a.roles || []).filter(r => r.e !== id);
    if (a.pay && (a.pay.payers.some(p => p.who === id) || a.pay.items.some(i => (i.users || []).includes(id)))) keep = true;
    if (a.settle && a.settle.who === id) keep = true;
  }
  for (const r of Object.values(DB.routines)) r.tpl.roles = (r.tpl.roles || []).filter(x => x.e !== id);
  for (const t of Object.values(DB.tasks)) if (t.ents) { t.ents = t.ents.filter(x => x !== id); if (!t.ents.length) delete t.ents; }
  for (const m of Object.values(DB.memos)) m.ents = (m.ents || []).filter(x => x !== id);
  for (const p of Object.values(DB.photos)) if (p.ents) { p.ents = p.ents.filter(x => x !== id); if (!p.ents.length) delete p.ents; }
  for (const [rid, r] of Object.entries(DB.rels)) if (r.a === id || r.b === id) delete DB.rels[rid];
  for (const l of Object.values(DB.lists)) if (l.shop === id) delete l.shop;
  delete DB.notes[id];
  if (keep) { e.stub = 1; e.types = []; e.facts = []; delete e.geo; } else delete DB.ents[id];
});
/** 月・日の粗い日付 'YYYY' / 'YYYY-MM' / 'YYYY-MM-DD'(月・日が実在する) か */
const isCoarse = s => typeof s === 'string' && (/^\d{4}$/.test(s) || (/^\d{4}-(0[1-9]|1[0-2])$/.test(s)) || isDate(s));
const _coarse = (v, what) => { if (v && !isCoarse(v)) fail(`${what}の日付が正しくありません`); return v; };
const relAdd = o => commit('関係を追加', () => {
  if (!DB.ents[o.a] || !DB.ents[o.b]) fail('相手を選んでください'); if (o.a === o.b) fail('自分自身とは結べません');
  const from = o.from || o.since; _coarse(from, '始まり');
  const id = uid('r'); DB.rels[id] = { a: o.a, b: o.b, role: _str(o.role, '関係の名前を入れてください'), ...(o.inv ? { inv: o.inv } : {}), ...(from ? { from } : {}) }; return { id };
});
/** 関係を直す。relUpdate(relId, patch) か relUpdate(relId, viewerId, {other, role(viewer から見た名), sym, inv(相手から見た名・一方向のみ), since|from}) */
const relUpdate = (id, a2, a3) => commit('関係を直しました', () => {
  const r = DB.rels[id]; if (!r) fail('関係が見つかりません');
  let patch = a2;
  if (typeof a2 === 'string') {
    const v = a2, o = a3 || {};
    if (r.a !== v && r.b !== v) fail('関係が見つかりません');
    if (!DB.ents[o.other]) fail('相手を選んでください'); if (o.other === v) fail('自分自身とは結べません');
    const role = _str(o.role, '関係の名前を入れてください');
    const inv = o.sym ? '' : _str(o.inv, '相手から見た呼び名を入れてください');
    const from = o.from !== undefined ? o.from : o.since;
    patch = v === r.a ? { a: v, b: o.other, role, inv: o.sym ? '' : inv } : { a: o.other, b: v, role: o.sym ? role : inv, inv: o.sym ? '' : role };
    patch.from = from || ''; if (o.to !== undefined) patch.to = o.to;
  }
  const n = { ...r, ...patch }; _coarse(n.from, '始まり'); _coarse(n.to, '終わり');
  n.role = _str(n.role, '関係の名前を入れてください'); for (const k of ['inv', 'from', 'to']) if (!n[k]) delete n[k];
  if (n.from && n.to && n.to < n.from) fail('終わりは始まり以降にしてください');
  DB.rels[id] = n;
});
const relRemove = id => commit('関係を外しました', () => { if (!DB.rels[id]) fail('関係が見つかりません'); delete DB.rels[id]; });
const typeAdd = (n, p) => commit(`種類「${String(n || '').trim()}」を追加しました`, () => { const key = uid('ty'); DB.types[key] = { n: _str(n, '名前を入れてください'), props: [], ...(p && DB.types[p] ? { p } : {}) }; return { key }; });
const typeUpdate = (key, patch) => commit('種類を直しました', () => {
  const t = DB.types[key]; if (!t) fail('種類が見つかりません');
  if (patch.n !== undefined) t.n = _str(patch.n, '名前を入れてください');
  if ('p' in patch) { if (patch.p && (!DB.types[patch.p] || typeAnc(patch.p).includes(key))) fail('自分の中へは移せません'); if (patch.p) t.p = patch.p; else delete t.p; }
});
const typeRemove = key => commit('種類を消しました', () => { if (!DB.types[key]) fail('種類が見つかりません'); if (key === 'method') fail('支払い方法の種類は消せません'); if (typeKids(key).length) fail('中に種類があるので、先に移すか消してください'); if (entsOfType(key, false, { stubs: true }).length) fail('この種類のエンティティがあるので、先に外してください'); delete DB.types[key]; });
const typePropAdd = (key, n) => commit('項目を追加', () => { const t = DB.types[key]; if (!t) fail('種類が見つかりません'); const v = _str(n, '項目の名前を入れてください'); if (propsOf(key).some(p => p[0] === v)) fail('同じ名前の項目があります'); t.props = [...(t.props || []), v]; });
const typePropRename = (key, a, b) => commit('項目の名前を直しました', () => { const t = DB.types[key]; const v = _str(b, '項目の名前を入れてください'); if (!t || !(t.props || []).includes(a)) fail('項目が見つかりません'); if (v !== a && propsOf(key).some(p => p[0] === v)) fail('同じ名前の項目があります'); t.props = t.props.map(p => p === a ? v : p); entsOfType(key, true, { stubs: true }).forEach(id => DB.ents[id].facts.forEach(f => { if (f.k === a) f.k = v; })); });
const typePropRemove = (key, n) => commit('項目を消しました', () => { const t = DB.types[key]; if (!t || !(t.props || []).includes(n)) fail('項目が見つかりません'); t.props = t.props.filter(p => p !== n); });
/** ノート。body = [{h, text}]。subs = [{id, n, sub, text}] */
const _nbody = b => (b || []).map(x => Array.isArray(x) ? { h: String(x[0] || ''), text: String(x[1] || '') } : { h: String(x.h || ''), text: String(x.text || '') }).filter(b => b.h || b.text);
const noteSave = (eid, body) => commit('ノートを保存しました', () => { if (!DB.ents[eid]) fail('エンティティが見つかりません'); const old = DB.notes[eid]; DB.notes[eid] = { upd: nowStamp(), body: _nbody(body), subs: old ? old.subs : [] }; });
const noteSubSave = (eid, sid, sub) => commit('サブページを保存しました', () => {
  const n = DB.notes[eid] || (DB.ents[eid] ? (DB.notes[eid] = { upd: nowStamp(), body: [], subs: [] }) : fail('エンティティが見つかりません'));
  const s = { id: sid || uid('ns'), n: _str(sub.n, '名前を入れてください'), sub: String(sub.sub || ''), text: String(sub.text || '') };
  if (sid && n.subs.some(x => x.id === sid)) n.subs = n.subs.map(x => x.id === sid ? s : x); else n.subs = [...n.subs, s];
  n.upd = nowStamp(); return { id: s.id };
});
const noteSubRemove = (eid, sid) => commit('サブページを消しました', () => { const n = DB.notes[eid]; if (!n || !n.subs.some(x => x.id === sid)) fail('サブページが見つかりません'); n.subs = n.subs.filter(x => x.id !== sid); n.upd = nowStamp(); });

/* ---- カテゴリ・支払い方法 ---- */
const catAdd = (n, p) => commit(`カテゴリ「${String(n || '').trim()}」を追加しました`, () => { const key = uid('c'); const used = new Set(Object.values(DB.cats).map(c => c.c)); DB.cats[key] = { n: _str(n, '名前を入れてください'), ...(p && DB.cats[p] ? { p } : { c: CAT_COLORS.find(c => !used.has(c)) || CAT_COLORS[0], fx: '変動', sp: '日常' }) }; return { key }; });
const catUpdate = (key, patch) => commit('カテゴリを直しました', () => {
  const c = DB.cats[key]; if (!c) fail('カテゴリが見つかりません');
  if (patch.n !== undefined) c.n = _str(patch.n, '名前を入れてください');
  for (const f of ['c', 'fx', 'sp']) if (f in patch) { if (patch[f]) c[f] = patch[f]; else delete c[f]; }
  if ('p' in patch) { if (patch.p && (!DB.cats[patch.p] || catAnc(patch.p).includes(key))) fail('自分の中へは移せません'); if (patch.p) c.p = patch.p; else { delete c.p; if (!c.c) c.c = CAT_COLORS[0]; } }
});
const catRemove = key => commit('カテゴリを消しました', () => { if (!DB.cats[key]) fail('カテゴリが見つかりません'); if (catKids(key).length) fail('中にカテゴリがあるので、先に移すか消してください'); if (Object.values(DB.exps).some(x => x.cat === key || Object.values(x.itemCat || {}).includes(key))) fail('このカテゴリの支出があるので、先に付け替えてください'); if (Object.values(DB.routines).some(r => r.tpl.exp && r.tpl.exp.cat === key)) fail('このカテゴリの繰り返しの支払いがあります'); delete DB.cats[key]; });
const _methodUsed = id => Object.values(DB.acts).some(a => (a.pay && a.pay.payers.some(p => p.how === id)) || (a.settle && a.settle.how === id));
/** 支払い方法を足す → {id} */
const methodAdd = n => commit('支払い方法を追加', () => { const v = _str(n, '名前を入れてください'); if (methodsAll().some(([, e]) => e.n === v)) fail('同じ名前があります'); const id = uid('e'); DB.ents[id] = { n: v, types: ['method'], facts: [] }; return { id }; });
/** 消す(1つ以上必要。使われていれば消さずに理由を返す)。id か名前 */
const methodRemove = k => commit('支払い方法を消しました', () => {
  const id = _isMethod(k) ? k : (methodsAll().find(([, e]) => e.n === k) || [])[0]; if (!id) fail('支払い方法が見つかりません');
  if (methodsAll().length <= 1) fail('1つ以上必要です'); if (_methodUsed(id)) fail('使われている支払い方法は消せません');
  delete DB.ents[id];
});

/* =====================================================================
   保存・読み込み・書き出し
   ===================================================================== */
function save() { try { localStorage.setItem(STORE_KEY, JSON.stringify(DB)); return true; } catch (e) { return false; } }
function dbNormalize(d) { if (!d.meta || typeof d.meta !== 'object') d.meta = { v: 7, born: TODAY }; if (!d.log || !Array.isArray(d.log)) d.log = []; for (const t of ['jots', 'photos', 'routines', 'rels']) if (!d[t] || typeof d[t] !== 'object' || Array.isArray(d[t])) d[t] = {}; delete d.holidays; return d; }
/* 表の形: 'obj' = 鍵から引く表、'arr' = 並びの表。欠けていたら空で補う。型が違えば取り込みを断る(祝日は規則の計算なので表は無い) */
const DB_KIND = { days: 'obj', acts: 'obj', exps: 'obj', tasks: 'obj', lists: 'obj', ents: 'obj', rels: 'obj', types: 'obj', notes: 'obj', memos: 'obj', cats: 'obj', jots: 'obj', photos: 'obj', routines: 'obj', log: 'arr' };
/** 読み込み・取り込みの共通の正規化。d を書き換えて {ok, db} か {ok:false, why} を返す */
function dbFill(d) {
  if (!d || typeof d !== 'object' || Array.isArray(d)) return { ok: false, why: 'このアプリ(v7)の書き出しファイルではありません' };
  if (d.meta !== undefined && (d.meta === null || typeof d.meta !== 'object' || Array.isArray(d.meta))) return { ok: false, why: '保存データの「meta」の形が違うので、読めませんでした' };
  for (const t of Object.keys(DB_KIND)) {
    const want = DB_KIND[t];
    if (d[t] === undefined || d[t] === null) { d[t] = want === 'arr' ? [] : {}; continue; }
    if (want === 'arr' ? !Array.isArray(d[t]) : (typeof d[t] !== 'object' || Array.isArray(d[t]))) return { ok: false, why: `保存データの「${t}」の形が違うので、読めませんでした` };
  }
  /* 自分は必ずいる(欠けた ents でも画面が落ちない) */
  if (!d.ents.me) d.ents.me = { n: '自分', types: ['person'], facts: [] };
  if (!d.types.method) d.types.method = { n: '支払い方法', props: [] };
  if (!d.types.person) d.types.person = { n: '人物', props: [] };
  return { ok: true, db: dbNormalize(d) };
}
/** 見本の雑記と履歴を入れる(まだ入れていない時だけ。seedInbox は m-inbox.js。印は DB.meta.seeded) */
function dbSeedInbox() { if (!DB.meta.seeded && typeof seedInbox === 'function') { try { seedInbox(); DB.meta.seeded = 1; } catch (e) { console.error('seedInbox', e); } } }
const BROKEN_KEY = 'prmock-v7-broken';
/** 読めなかった保存データの退避。_brokenNotice は起動時のお知らせ(boot が1回だけ出す) */
let _brokenNotice = null;
function load() {
  let raw = null; try { raw = localStorage.getItem(STORE_KEY); } catch (e) { }
  if (raw !== null) {
    let s = null; try { s = JSON.parse(raw); } catch (e) { }
    const c = s && s.meta && s.meta.v === 7 && s.acts ? dbFill(s) : { ok: false };
    if (c.ok) { DB = c.db; _recIdx = null; dbSeedInbox(); save(); return; }
    /* 読めない → 壊れた元の文字列を退避してから見本で始める(退避は1件だけ。次の退避で上書き) */
    let kept = false; try { localStorage.setItem(BROKEN_KEY, raw); kept = true; } catch (e) { }
    _brokenNotice = kept ? '保存データを読めなかったので、見本のデータで始めました(壊れた元のデータは残してあります)' : '保存データを読めなかったので、見本のデータで始めました(退避できませんでした)';
  }
  DB = dbFill(seed()).db; _recIdx = null; dbSeedInbox(); save();
}
function resetData() { DB = dbFill(seed()).db; _recIdx = null; dbSeedInbox(); save(); }
const exportJSON = () => JSON.stringify(DB, null, 1);
/** 書き出したファイルを読み込む(中身を確かめてから入れ替える。欠けた表は補い、型が違えば断る) */
function importJSON(text) {
  let s; try { s = JSON.parse(text); } catch (e) { return { ok: false, why: 'ファイルを読めませんでした(JSON ではありません)' }; }
  if (!s || !s.meta || s.meta.v !== 7) return { ok: false, why: 'このアプリ(v7)の書き出しファイルではありません' };
  const c = dbFill(s); if (!c.ok) return { ok: false, why: c.why };
  const prev = JSON.stringify(DB); DB = c.db; DB.meta.seeded = 1; _recIdx = null; save();
  return { ok: true, undo() { DB = JSON.parse(prev); _recIdx = null; save(); } };
}
const dataCounts = () => ({
  acts: Object.values(DB.acts).filter(a => !isPlan(a)).length, plans: Object.values(DB.acts).filter(isPlan).length, tasks: Object.keys(DB.tasks).length, memos: Object.keys(DB.memos).length,
  ents: Object.entries(DB.ents).filter(([id, e]) => id !== 'me' && !e.stub && !e.types.includes('method')).length, photos: Object.keys(DB.photos).length, routines: Object.keys(DB.routines).length, bytes: JSON.stringify(DB).length,
});

/* =====================================================================
   見本データ(架空)。初めて開いた日を「今日」として、そこからの相対で作る
   ===================================================================== */
function seed() {
  const T = TODAY, D = n => dAdd(T, n), at = (d, t) => `${d}T${t}`;
  const db = { meta: { v: 7, born: T }, days: {}, photos: {}, acts: {}, routines: {}, exps: {}, tasks: {}, lists: {}, ents: {}, rels: {}, types: {}, notes: {}, memos: {}, cats: {}, jots: {}, log: [] };
  db.types = {
    person: { n: '人物', props: ['電話', '誕生日', '住所'] }, colleague: { n: '同僚', p: 'person', props: ['部署'] }, split: { n: '割り勘メンバー', p: 'person', props: ['略称'] },
    place: { n: '場所', props: ['住所'] }, shop: { n: '店', p: 'place', props: ['営業時間', '定休日'] }, food: { n: '飲食店', p: 'shop', props: [] }, ramen: { n: 'ラーメン屋', p: 'food', props: [] }, cafe: { n: 'カフェ', p: 'food', props: [] },
    super: { n: 'スーパー', p: 'shop', props: [] }, drug: { n: 'ドラッグストア', p: 'shop', props: [] }, home: { n: '住まい', p: 'place', props: [] }, workplace: { n: '作業場所', p: 'place', props: ['電源'] },
    company: { n: '会社', props: ['所在地'] }, thing: { n: '物', props: ['購入日'] }, doc: { n: '書類', props: ['発行元', '期限'] },
    project: { n: 'プロジェクト', props: ['期間', '状況'] }, life_proj: { n: '生活', p: 'project', props: [] }, method: { n: '支払い方法', props: [] },
  };
  const E_ = db.ents;
  E_.me = { n: '自分', types: ['person'], facts: [{ k: '住所', v: '横浜市青葉区青葉台2-4-6', from: '2023-03' }, { k: '住所', v: '川崎市宮前区鷺沼1-3', from: '2019-04', to: '2023-03', why: '転居' }, { k: '誕生日', v: '1992-04-18' }, { k: '電話', v: '090-0000-1111', rare: 1 }] };
  [['cash', '現金'], ['card', 'クレジットカード ••1234'], ['suica', 'Suica'], ['qr', 'QRコード決済'], ['debit', 'デビットカード'], ['bank', '銀行振込']].forEach(([id, n]) => { E_[id] = { n, types: ['method'], facts: [] }; });
  Object.assign(E_, {
    hanako: { n: '田中 花子', types: ['colleague', 'split'], facts: [
      { k: '姓', v: '田中', from: '2020-04' }, { k: '姓', v: '佐藤', to: '2020-04', why: '結婚' }, { k: '名', v: '花子' }, { k: '電話', v: '090-1234-5678' }, { k: '誕生日', v: '1994-06-12' },
      { k: '住所', v: '横浜市青葉区美しが丘2', from: '2023-03' }, { k: '住所', v: '川崎市宮前区鷺沼1', from: '2020-04', to: '2023-03', why: '転居' }, { k: '住所', v: '世田谷区用賀4', to: '2020-04' },
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
  });
  /* 名前だけの相手(stub)。同じ名前は同じ stub */
  let nst = 0; const st = (n, ty) => { const f = Object.keys(E_).find(k => E_[k].n === n); if (f) return f; const id = 'st' + (++nst); E_[id] = { n, types: ty ? [ty] : [], stub: 1, facts: [] }; return id; };
  const R = (a, b, role, o = {}) => { db.rels['r' + (Object.keys(db.rels).length + 1)] = { a, b, role, ...o }; };
  R('hanako', 'taro', '配偶者', { from: '2020-04' }); R('hanako', 'acorp', '勤務先', { inv: '従業員', from: '2018-04' }); R('hanako', 'ken', '友人'); R('proj1', 'hanako', '一緒に');
  R('ken', 'acorp', '勤務先', { inv: '従業員', from: '2016-04', to: '2022-03' });
  db.cats = {
    house: { n: '住居', c: '#2F4F9E', fx: '固定', sp: '日常' }, rent: { n: '家賃', p: 'house' },
    comm: { n: '通信', c: '#5A78BF', fx: '固定', sp: '日常' }, phone: { n: 'スマホ', p: 'comm' }, sub: { n: 'サブスク', p: 'comm' },
    food: { n: '食費', c: '#8EA3D6', fx: '変動', sp: '日常' }, cook: { n: '自炊', p: 'food' }, eatout: { n: '外食', p: 'food' }, cafe: { n: 'カフェ・軽食', p: 'food' }, alcohol: { n: 'お酒', p: 'food' },
    daily: { n: '日用品', c: '#1F3677', fx: '変動', sp: '日常' },
    trans: { n: '交通費', c: '#B9C6E6', fx: '変動', sp: '日常' }, train: { n: '電車', p: 'trans' }, taxi: { n: 'タクシー', p: 'trans', sp: '非日常' },
    fun: { n: '娯楽', c: '#8E9BB8', fx: '変動', sp: '日常' }, hobby: { n: '趣味', p: 'fun' }, travel: { n: '旅行', p: 'fun', sp: '非日常' },
    gift: { n: '贈り物', c: '#C3CBDC', fx: 'ask', sp: '非日常' },
  };
  db.lists = { inbox: { n: '受信箱' }, life: { n: '生活' }, shop: { n: '買い物', shop: 'super' } };
  const HOW = { '現金': 'cash', 'クレジットカード ••1234': 'card', 'Suica': 'suica', 'QRコード決済': 'qr', '銀行振込': 'bank' };
  /* roles の {name} は stub にする */
  const rl = roles => (roles || []).map(r => r.name ? { r: r.r, e: st(r.name, ROLE_STUB[r.r]) } : r);
  const act = (id, o) => { db.acts[id] = { roles: [], state: 'done', ...o, roles: rl(o.roles) }; };
  /* 支払い。items の itemCat は旧い番号 → 品目 id に直す */
  const pay = (id, o, items, x, payers) => {
    const its = items ? items.map((i, n) => ({ id: `${id}-${n}`, ...i })) : [];
    const total = items ? its.reduce((s, i) => s + itemAmt(i), 0) : o.amt;
    const a = { kind: 'pay', state: 'done', start: o.start, ...(o.parent ? { parent: o.parent } : {}), roles: rl(o.roles), pay: { payers: (payers || [{ who: 'me', amt: total, how: o.how || 'クレジットカード ••1234' }]).map(p => ({ ...p, how: HOW[p.how] || p.how })), items: its, ...(items ? {} : { amt: total }) } };
    if (o.unsure) a.unsure = o.unsure; if (o.via) a.via = o.via; if (o.routine) a.routine = o.routine;
    db.acts[id] = a;
    if (x) { const e = { basis: 'items', ...x }; if (x.itemCat) e.itemCat = Object.fromEntries(Object.entries(x.itemCat).map(([n, c]) => [`${id}-${n}`, c])); db.exps[id] = e; }
  };
  const it = (n, price, users = ['me'], tax = 10, more = {}) => ({ n, price, users, tax, ...more });

  /* --- 昨日: 花子さんとランチ → ミナトで作業(中に会話・支払い)→ 帰宅 --- */
  const y = D(-1), y2 = D(-2);
  db.days[y] = { diary: '花子さんとラーメン。午後はミナトで作業、夜は早めに帰宅。' };
  db.photos.ph1 = { at: at(y, '12:20'), cap: 'らーめん青葉', act: 'l1', ents: ['aoba', 'hanako'] };
  db.photos.ph2 = { at: at(y, '15:30'), cap: '窓側の席', act: 'w2' };
  db.photos.ph3 = { at: at(y, '19:40'), cap: '帰り道の夕焼け', geo: { lat: 35.5434, lng: 139.5521 } };
  /* 睡眠は日をまたぐ1件。昨夜〜今朝もある */
  act('w5', { kind: 'sleep', title: '睡眠', start: at(D(-4), '23:40'), end: at(D(-3), '07:10') });
  act('w4', { kind: 'sleep', title: '睡眠', start: at(D(-3), '23:50'), end: at(y2, '07:20') });
  act('w1', { kind: 'sleep', title: '睡眠', start: at(y2, '23:20'), end: at(y, '07:40') });
  act('w3', { kind: 'sleep', title: '睡眠', start: at(y, '23:30'), end: at(T, '07:05') });
  act('l1', { kind: 'meal', title: '花子さんとランチ', start: at(y, '12:00'), end: at(y, '13:10'), planned: { start: at(y, '12:00'), end: at(y, '13:00') }, roles: [{ r: '場所', e: 'aoba' }, { r: '同席', e: 'hanako' }] });
  pay('p1', { start: at(y, '13:05'), parent: 'l1', roles: [{ r: '支払先', e: 'aoba' }], how: '現金' },
    [{ n: '醤油らーめん', lines: [{ n: '', k: 'amt', v: 1000 }, { n: '大盛り', k: 'amt', v: 100 }], users: ['me'], tax: 10 }, it('塩らーめん', 1200, ['hanako']), it('餃子', 680, ['me', 'hanako']), it('生ビール', 800, ['me'], 10, { memo: '1杯目' }), it('生ビール', 800, ['hanako'])],
    { cat: 'eatout', itemCat: { 3: 'alcohol' }, memo: '花子さんとランチ' });
  act('w2', { kind: 'work', title: 'カフェ ミナトで作業', start: at(y, '13:40'), end: at(y, '19:20'), roles: [{ r: '場所', e: 'minato' }] });
  act('c1', { kind: 'talk', title: '転職の相談', start: at(y, '15:00'), end: at(y, '17:00'), parent: 'w2', roles: [{ r: '参加', e: 'hanako' }], note: 'A社を辞めるか迷っているとのこと。年内に決めたいらしい。' });
  pay('p16', { start: at(y, '15:05'), parent: 'c1', roles: [{ r: '支払先', e: 'minato' }], how: '現金' }, [it('カフェラテ', 550, ['me']), it('カフェラテ', 550, ['hanako'])], { basis: 'amount', me: 1100, cat: 'cafe', memo: '花子さんの分もおごり' });
  act('m1', { kind: 'move', title: '帰宅', start: at(y, '19:30'), end: at(y, '20:05'), roles: [{ r: '出発', e: 'minato' }, { r: '到着', e: 'home' }] });
  /* --- おととい --- */
  db.days[y2] = { diary: '雨。在宅で仕事。夕方に内見。' };
  db.photos.ph4 = { at: at(y2, '14:20'), cap: '内見した部屋', act: 'h1', ents: ['proj1'] };
  act('h1', { kind: 'event', title: '物件の内見', start: at(y2, '14:00'), end: at(y2, '15:30'), roles: [{ r: 'プロジェクト', e: 'proj1' }, { r: '同席', e: 'hanako' }] });
  act('s2', { kind: 'shop', title: '夕食の買い物', start: at(y2, '18:25'), end: at(y2, '18:50'), roles: [{ r: '場所', e: 'super' }] });
  pay('p2', { start: at(y2, '18:40'), parent: 's2', roles: [{ r: '支払先', e: 'super' }] },
    [it('牛乳', 238, ['me'], 8), it('卵', 268, ['me'], 8, { memo: '特売' }), it('卵', 268, ['me'], 8), { n: '鶏もも肉', lines: [{ n: '', k: 'amt', v: 680 }, { n: '値引き', k: 'pct', v: -10 }], users: ['me'], tax: 8 }, it('キッチンペーパー', 398), it('その他', 900, ['me'], 8, { unk: 1 })],
    { cat: 'cook', itemCat: { 4: 'daily' } });
  /* --- 3日前・4日前 --- */
  db.days[D(-3)] = { diary: '朝パンを買って出社。' };
  pay('p3', { start: at(D(-3), '08:20'), roles: [{ r: '支払先', name: 'ベーカリー木漏れ日' }], how: 'Suica' }, [it('クロワッサン', 280, ['me'], 8), it('カレーパン', 340, ['me'], 8)], { cat: 'cafe' });
  pay('p4', { start: at(D(-4), '20:05'), roles: [{ r: '支払先', e: 'drug' }], how: 'QRコード決済' }, [it('洗剤', 680), it('歯ブラシ', 600)], { cat: 'daily' });
  /* --- 健さんが払ったランチ(割り勘で借り)/ 花子さんの誕生日(奢り)/ 名前だけの鈴木さん / 読み取ったままのレシート --- */
  act('l2', { kind: 'meal', title: '健さんとランチ', start: at(D(-9), '12:30'), end: at(D(-9), '13:30'), roles: [{ r: '場所', name: 'イタリアン 木の実' }, { r: '同席', e: 'ken' }] });
  pay('p17', { start: at(D(-9), '13:25'), parent: 'l2', roles: [{ r: '支払先', name: 'イタリアン 木の実' }] }, [it('ランチセット', 2840, ['me']), it('ランチセット', 2840, ['ken'])], { basis: 'even', n: 2, cat: 'eatout' }, [{ who: 'ken', amt: 5680, how: 'cash' }]);
  act('l4', { kind: 'meal', title: '鈴木さんと打ち合わせランチ', start: at(D(-12), '12:10'), end: at(D(-12), '13:00'), roles: [{ r: '場所', name: 'そば処 山の手' }, { r: '同席', name: '鈴木さん' }] });
  act('l3', { kind: 'meal', title: '花子さんの誕生日ディナー', start: at(D(-19), '19:00'), end: at(D(-19), '21:00'), roles: [{ r: '場所', e: 'aoba' }, { r: '同席', e: 'hanako' }] });
  pay('p8', { start: at(D(-19), '20:55'), parent: 'l3', roles: [{ r: '支払先', e: 'aoba' }] }, [it('コース', 3400, ['me']), it('コース', 3400, ['hanako'])], { basis: 'amount', me: 6800, cat: 'eatout', sp: '非日常', memo: '誕生日なのでおごり' });
  pay('p30', { start: at(T, '08:40'), roles: [{ r: '支払先', e: 'minato' }], how: 'Suica' }, [it('カフェラテ', 480)], { cat: 'cafe' });
  pay('p31', { start: at(y, '20:20'), roles: [{ r: '支払先', e: 'drug' }], unsure: ['amt', 'payee'], via: 'ocr' }, [it('シャンプー', 780, ['me'], 10, { unsure: 1 }), it('ハンドソープ', 348, ['me'], 10, { unsure: 1 })], { cat: 'daily' });
  /* --- 固定費(繰り返しの支払い予定)と、過去の各回 --- */
  const fixed = [['f_rent', '家賃', '大家(振込)', 27, 85000, 'rent', '銀行振込'], ['f_phone', 'スマホ料金', 'モバイル通信', 10, 3280, 'phone', 'クレジットカード ••1234'], ['f_sub', '動画サブスク', '動画配信', 15, 990, 'sub', 'クレジットカード ••1234']];
  const ym0 = ymOf(T);
  fixed.forEach(([id, title, payee, dom, amt, cat, how]) => {
    db.routines[id] = { every: 'month', from: `${ymAdd(ym0, -7)}-${pad2(dom)}`, tpl: { kind: 'pay', title, roles: [{ r: '支払先', e: st(payee, 'shop') }], pay: { amt }, exp: { cat } } };
    for (let m = -7; m <= 0; m++) { const d = `${ymAdd(ym0, m)}-${pad2(dom)}`; if (d > T || d < `${ymAdd(ym0, -6)}-01`) continue; pay(`${id}_${d}`, { start: at(d, '09:00'), roles: [{ r: '支払先', e: st(payee, 'shop') }], how, amt, routine: id }, null, { cat }); }
  });
  /* --- 過去6か月の変動費(決まった乱数で作る) --- */
  let sd = 20261111; const rnd = () => (sd = (sd * 1103515245 + 12345) % 2147483648) / 2147483648;
  const pool = [[{ e: 'super' }, 'cook', 1400, 4200, '18:40', 9], [{ e: 'minato' }, 'cafe', 480, 980, '15:10', 7], [{ e: 'drug' }, 'daily', 600, 1800, '19:40', 3], [{ e: 'aoba' }, 'eatout', 980, 1500, '12:30', 3], [{ name: 'そば処 山の手' }, 'eatout', 900, 2200, '12:30', 2],
    [{ name: 'JR 青葉台駅' }, 'train', 500, 1500, '08:10', 3], [{ e: 'cinema' }, 'hobby', 1800, 2000, '15:00', 1], [{ name: '書店 よむよむ' }, 'hobby', 800, 2600, '17:20', 1], [{ name: 'タクシー' }, 'taxi', 1800, 3200, '23:10', 0.4], [{ name: '酒場 みなみ' }, 'alcohol', 2400, 4800, '20:30', 1]];
  const used = new Set(Object.values(db.acts).filter(a => a.kind === 'pay' && a.state === 'done' && !a.routine).map(a => dOf(a.start)));
  for (let n = -5; n >= -185; n--) {
    const d = D(n); if (used.has(d)) continue;
    pool.forEach(([payee, cat, lo, hi, tm, perMonth], pi) => {
      if (rnd() > perMonth / 30) return;
      const amt = Math.round((lo + rnd() * (hi - lo)) / 10) * 10, id = `g${-n}_${pi}`;
      pay(id, { start: at(d, tm), roles: [{ r: '支払先', ...payee }], how: cat === 'train' ? 'Suica' : rnd() < .3 ? '現金' : 'クレジットカード ••1234', amt }, null, { cat });
    });
  }
  pay('p11', { start: at(D(-33), '10:30'), roles: [{ r: '支払先', name: '宿 山の湯' }], amt: 18000 }, null, { cat: 'travel' });
  /* --- 精算済み(2件まとめて1回 / 1件)と、未精算(p1・p17) --- */
  pay('p20', { start: at(D(-16), '19:30'), roles: [{ r: '支払先', e: 'aoba' }] }, [it('お任せ', 5000, ['me', 'hanako'])], { basis: 'even', n: 2, cat: 'eatout' });
  pay('p21', { start: at(D(-15), '17:00'), roles: [{ r: '支払先', name: 'ケーキ屋 ルーチェ' }], how: '現金' }, [it('ケーキ', 1800, ['hanako'])], null);
  act('s1', { kind: 'settle', start: at(D(-12), '18:30'), settle: { who: 'hanako', dir: 'in', amt: 4300, how: 'cash', pays: ['p20', 'p21'] } });
  pay('p22', { start: at(D(-22), '12:40'), roles: [{ r: '支払先', e: 'minato' }] }, [it('ランチ', 1800, ['me']), it('ランチ', 1800, ['ken'])], { basis: 'even', n: 2, cat: 'eatout' }, [{ who: 'ken', amt: 3600, how: 'cash' }]);
  act('s2b', { kind: 'settle', start: at(D(-20), '20:00'), settle: { who: 'ken', dir: 'out', amt: 1800, how: 'bank', pays: ['p22'] } });
  /* --- 1年前の今日(思い出す) --- */
  const ly = `${+T.slice(0, 4) - 1}${T.slice(4)}`;
  if (isDate(ly)) {
    db.days[ly] = { diary: '花子さんと紅葉を見に行った。帰りに温かいそば。' };
    db.photos.ph5 = { at: at(ly, '11:10'), cap: '渓谷の紅葉', act: 'ly1' }; db.photos.ph6 = { at: at(ly, '13:20'), cap: '山のそば屋' };
    act('ly1', { kind: 'play', title: '紅葉を見に行く', start: at(ly, '10:00'), end: at(ly, '15:30'), roles: [{ r: '同席', e: 'hanako' }, { r: '場所', name: '青葉渓谷' }] });
    pay('ly2', { start: at(ly, '13:40'), parent: 'ly1', roles: [{ r: '支払先', name: 'そば処 山の手' }], amt: 2400 }, null, { cat: 'eatout' });
  }
  /* --- 今日とこれからの予定 --- */
  act('e9', { kind: 'work', title: '在宅作業', start: at(T, '10:00'), end: at(T, '12:00'), state: 'plan', roles: [{ r: '場所', e: 'home' }] });
  act('e1', { kind: 'play', title: '花子さんと映画', start: at(T, '15:00'), end: at(T, '17:10'), state: 'plan', roles: [{ r: '場所', e: 'cinema' }, { r: '同席', e: 'hanako' }], note: 'チケットはネットで予約', alarm: '30分前' });
  act('e2', { kind: 'shop', title: '夕食の買い物', start: at(T, '18:30'), end: at(T, '19:00'), state: 'plan', roles: [{ r: '場所', e: 'super' }] });
  act('e3', { kind: 'health', title: '歯医者', start: at(D(2), '10:00'), end: at(D(2), '11:00'), state: 'plan', roles: [{ r: '場所', name: 'さくら歯科' }], alarm: '1時間前' });
  act('e11', { kind: 'event', title: '不動産屋と打ち合わせ', start: at(D(6), '14:00'), end: at(D(6), '15:00'), state: 'plan', roles: [{ r: '場所', name: '不動産ファースト' }, { r: 'プロジェクト', e: 'proj1' }] });
  act('e6', { kind: 'trip', title: '温泉旅行', start: D(10), end: D(11), state: 'plan', roles: [{ r: '場所', name: '宿 山の湯' }, { r: '同席', e: 'hanako' }] });
  db.routines.e12 = { every: 'week', from: D(-7 - ((dDow(T) + 1) % 7)), tpl: { kind: 'event', title: '資源ごみの日', roles: [] } };
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
    q5: { body: '引っ越し業者は3社から見積もりを取る', at: at(T, '09:30'), ents: ['proj1'] },
    q4: { body: '花子さんの誕生日、甘い物が良さそう。駅前のケーキ屋を見ておく', at: at(T, '08:12'), ents: ['hanako'] },
    q3: { body: 'カフェ ミナトは窓側に電源あり。午後は混む', at: at(y, '16:40'), ents: ['minato'], act: 'c1' },
    q2: { body: '年末調整の書類、会社のポータルから出す', at: at(y2, '21:15'), ents: [], pin: 1 },
    q1: { body: '洗剤の詰め替えは大きいサイズの方が安い', at: at(D(-4), '20:10'), ents: ['drug'] },
  };
  db.notes = {
    hanako: { upd: at(D(-3), '21:04'), body: [{ h: '好み', text: '甘い物が好き。辛い物は苦手。お酒はビールなら飲む。' }, { h: '最近のこと', text: '転職を考えている。A社は2018年から。昨日ミナトで話した。' }], subs: [{ id: 'ns1', n: '誕生日プレゼント候補', sub: '更新 ' + fmtMD(D(-20)), text: 'ハンドクリーム\n紅茶のセット\n駅前のケーキ屋の焼き菓子' }, { id: 'ns2', n: '好きな店', sub: '3件', text: 'らーめん青葉\nカフェ ミナト\nイタリアン 木の実' }] },
    aoba: { upd: at(y, '13:02'), body: [{ h: 'メモ', text: '麺は硬めがおいしい。餃子は2人で1皿で足りる。' }], subs: [] },
  };
  return db;
}
