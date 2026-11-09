/* =====================================================================
   画面: アクティビティ(リスト・日・月)と日のページ。担当 S2。関数の接頭辞は tl。
   ルート: #/activities(?view=list / day / month) , #/day/YYYY-MM-DD
   v4 からの進化: 絞り込みは一覧だけ(日のページに漏らさない) / 件数は countKids / 親子は入れ子 /
   日のページは日記・写真の枚数・前後の日 / 全ての一覧に empty()
   ===================================================================== */

let tlFilter = 'all', tlYM = TODAY.slice(0, 7), tlSelDay = TODAY;
let tlP = 0, tlLast = null, tlRaf = 0, tlSettleT = 0, tlSnapRaf = 0, tlHash = null, tlPending = null;
const TL_R = 146;                       /* 上部の縮められる量(px)。札の縮み 90 + 絞り込みの行 56 */
const TL_HOUR = 48, TL_H0 = 6, TL_H1 = 24;   /* 日の時間の軸: 1時間 48px、6:00〜24:00 */
const TL_FILTERS = [['all', 'すべて'], ['予定', '予定'], ['支払い', '支払い'], ['会話', '会話'], ['移動', '移動']].map(([k, label]) => ({ k, label }));
const TL_VIEWS = [['list', 'リスト'], ['day', '日'], ['month', '月']].map(([k, label]) => ({ k, label }));

/* ---------- 作成・日記の入口(別の担当が登録する関数を呼ぶ。未登録なら知らせるだけ) ---------- */
function tlCreate(name, arg) {
  const f = window[name];
  if (typeof f === 'function') return arg === undefined ? f() : f(arg);
  toast('準備中です');
}
/** 日記のシート(home.js 担当が openDiary(日付) を登録する予定。未登録の間は知らせるだけ) */
function tlDiary(d) {
  if (typeof openDiary === 'function') return openDiary(d);
  toast('日記は準備中です');
}

/* ---------- データの小道具(この画面だけ) ---------- */
const tlMin = s => { const [h, m] = String(s || '0:0').split(':').map(Number); return h * 60 + m };
const tlDiaryOf = d => Object.keys(A).find(k => A[k].type === '日記' && A[k].date === d) || null;
const tlDiaryDates = () => Object.values(A).filter(a => a.type === '日記').map(a => a.date);
const tlBody = d => { const id = tlDiaryOf(d); return id && A[id].body || '' };
const tlPlanOn = () => tlFilter === 'all' || tlFilter === '予定';
/** その日の自分の支出の合計(日の直下と活動の中の全部。支払いに支出があるもの) */
/** 自分の支出の日の合計。支払いが2件以上ある日だけ出す(v4 の daySpent と同じ条件) */
const tlSpent = d => { const id = tlDiaryOf(d); if (!id) return 0; const ps = descOf(id).filter(k => A[k] && A[k].x && X[A[k].x]); return ps.length >= 2 ? ps.reduce((s, k) => s + xAmt(X[A[k].x]), 0) : 0 };
/** 予定が過ぎている(未記録): 日付が前か、今日で いまより前 */
const tlLate = (d, e) => d < TODAY || (d === TODAY && !e.all && (e.s || '') <= NOW);
/** これから(今日のいまより後・未来の日) */
const tlFut = (d, e) => d > TODAY || (d === TODAY && !e.all && (e.s || '') > NOW);
/** 1日の項目(記録+未記録の予定)を時刻順に。useF=true なら一覧の絞り込みを使う(日のページは使わない) */
function tlItems(d, useF = true) {
  const f = useF ? tlFilter : 'all';
  const out = [], id = tlDiaryOf(d);
  if (id) kidActs(id).forEach(k => { const a = A[k]; if (f === 'all' || (f !== '予定' && a.type === f)) out.push({ kind: 'rec', id: k, t: a.time || '' }) });
  if (f === 'all' || f === '予定') evOn(d).forEach(([k, e]) => {
    if (e.rec || e.hol) return;               /* 記録になった予定は記録の行で出す */
    out.push({ kind: 'plan', id: k, t: e.all ? '00:00' : (e.s || ''), late: tlLate(d, e), future: tlFut(d, e) });
  });
  return out.sort((x, y) => x.t.localeCompare(y.t));
}

/* ---------- 行の部品(axRow・payShell を使う。同じ意味の部品は作らない) ---------- */
const tlRoles = a => (a.roles || []).filter(r => r[1] && E[r[1]] && r[0] !== '支払先').slice(0, 3).map(r => eref(r[1])).join('');
/** 活動の中の子。支払いは支払いの殻(kid)、ほかは小さい行。子の子は入れ子で(3段まで) */
function tlKid(c, depth) {
  const b = A[c]; if (!b) return '';
  if (b.type === '支払い') return payShell(c, { mode: 'kid' });
  const n = countKids(c), sub = kidActs(c);
  const head = `<a class="tl-kid" href="#/a/${esc(c)}"><span class="tl-kid-t">${esc(b.title || b.type)}</span>${b.time ? `<small class="num">${esc(b.time)}${b.end ? '–' + esc(b.end) : ''}</small>` : ''}${n ? `<span class="tl-kid-n num">${n}</span>` : ''}</a>`;
  const inner = depth < 3 && sub.length ? `<div class="tl-sub">${sub.map(x => tlKid(x, depth + 1)).join('')}</div>` : '';
  return `<div class="tl-kidbox">${head}${inner}</div>`;
}
/** 記録の行。支払い=支払いの殻 / 子がある活動=入れ子 / 子が無い=押すと開く行 */
function tlRecRow(k) {
  const a = A[k];
  const node = a.type === '支払い' ? 'pay' : (a.end ? 'ev' : 'moment');
  if (node === 'pay') return axRow({ time: a.time, end: a.end, node, body: payShell(k, { mode: 'row' }) });
  const kids = kidActs(k);
  const meta = `<span class="tl-meta">${esc(a.type)}</span>${tlRoles(a)}`;
  if (!kids.length) return axRow({ time: a.time, end: a.end, node, title: a.title, meta, href: '#/a/' + k });
  const kh = kids.map(c => tlKid(c, 1)).join('');
  return axRow({ time: a.time, end: a.end, node, body: `<span class="p-ax-c is-raw tl-ac"><span class="tl-ac-h"><a class="tl-atl" href="#/a/${esc(k)}">${esc(a.title)}</a><span class="p-ax-m">${meta}</span></span><span class="tl-kids">${kh}</span></span>` });
}
/** 予定の行。過ぎて未記録は赤(「記録にする?」は予定の詳細で行う) */
function tlPlanRow(it) {
  const e = EV[it.id];
  return axRow({
    time: e.all ? '終日' : e.s, end: e.all ? '' : e.e, node: it.late ? 'late' : 'plan', future: it.future,
    title: e.t, meta: `${it.late ? '<span class="tl-rec">記録にする?</span>' : ''}${esc(placeOf(e))}`, href: '#/ev/' + it.id
  });
}
/** 1日の軸。今日は 過ぎた項目 → いまの線 → これからの項目 */
function tlAxis(d, items) {
  const row = it => it.kind === 'rec' ? tlRecRow(it.id) : tlPlanRow(it);
  if (d !== TODAY) return items.map(row).join('');
  return items.filter(it => !it.future).map(row).join('') + nowLine() + items.filter(it => it.future).map(row).join('');
}
/** 一覧の1日: 日のカード(押すと日のページ)+その日の軸 */
function tlDayCard(d) {
  const sp = tlSpent(d), hol = evOn(d).find(([k, e]) => e.hol);
  return dayCard(d, { sum: sp ? `自分 ${yen(sp)}` : '', diary: tlBody(d) || (hol ? hol[1].t : ''), href: '#/day/' + d });
}
function tlGroup(d) {
  const items = tlItems(d);
  const write = d === TODAY && !tlBody(d) ? `<button class="tl-dwrite" type="button" onclick="tlDiary('${d}')">今日の日記を書く</button>` : '';
  return `<div class="tl-day${d === TODAY ? ' tl-today' : ''}">${tlDayCard(d)}${write}<div class="tl-ax">${tlAxis(d, items)}</div></div>`;
}
/** ToDo の段(その日が期限の ToDo。完了も出す)。0件なら何も出さない */
function tlTodoBlock(d) {
  const ks = Object.keys(TD).filter(k => TD[k].due === d);
  if (!ks.length) return '';
  return sh('ToDo', { count: ks.length }) + `<div class="tl-todos">${ks.map(k => `<div class="tl-todo">${checkbox(!!TD[k].done, { click: `tlTodo('${k}')`, label: TD[k].t })}<a class="tl-todo-t${TD[k].done ? ' is-done' : ''}" href="#/task/${k}">${esc(TD[k].t)}</a></div>`).join('')}</div>`;
}
function tlTodo(k) {
  const r = todoToggle(k, TODAY);
  if (!r || !r.ok) { toast(r && r.why || 'ToDoを変えられません'); return }
  save(); rerender();
  toast(r.msg || 'ToDoを変えました', r.undo ? { undo: () => { undoRun(r.undo); rerender() } } : {});
}

/* ---------- 上部(リスト)のまとめ・月の送り ---------- */
function tlSummary() {
  const ym = tlYM, m = +ym.slice(5);
  const monthN = tlDiaryDates().filter(d => d.startsWith(ym)).reduce((s, d) => s + countKids(tlDiaryOf(d)), 0);
  const evN = evOn(TODAY).filter(([k, e]) => !e.hol && !e.rec).length;
  const todoN = todoDueCount(TODAY);
  const metrics = [{ v: `${monthN}件`, l: `${m}月の記録` }, { v: `${evN}件`, l: '今日の予定', href: '#/activities?view=day' }, { v: `${todoN}件`, l: '今日のToDo', href: '#/todo' }];
  const actions = [{ label: '支払いを記録', icon: 'pay', primary: true, on: "tlCreate('openPayment')" }, { label: '出来事', icon: 'plus', on: "tlCreate('openNewHappening')" }];
  const mini = metrics.map(x => `<span class="tl-mini-n"><b class="num">${esc(x.v)}</b>${esc(x.l)}</span>`).join('');
  return `<div class="tl-sum">${summaryCard({ metrics, actions })}<div class="tl-mini" aria-hidden="true">${mini}</div></div>`;
}
function tlMonthRange() {
  const dates = [...tlDiaryDates(), TODAY].sort();
  return { minM: dates[0].slice(0, 7), maxM: TODAY.slice(0, 7) };
}
function tlMonthShift(n) {
  const [y, m] = tlYM.split('-').map(Number);
  const d = new Date(y, m - 1 + n, 1);
  tlYM = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  tlSelDay = tlYM === TODAY.slice(0, 7) ? TODAY : tlYM + '-01';
  tlPending = tlYM === TODAY.slice(0, 7) ? 'today' : 'bottom';
  rerender();
}
function tlSetFilter(k) { tlFilter = k; rerender() }
function tlSetView(v) {
  const cur = CUR.q.view || 'list';
  if (cur === 'list' && v !== 'list') tlSelDay = tlYM === TODAY.slice(0, 7) ? TODAY : tlYM + '-01';
  if (cur !== 'list' && v === 'list') tlYM = tlSelDay.slice(0, 7);
  go('#/activities?view=' + v);
}
function tlStep(n) { tlSelDay = addDays(tlSelDay, n); tlYM = tlSelDay.slice(0, 7); rerender() }
function tlPick(d) { tlSelDay = d; rerender() }

/* 一覧の本体: 今月の日(記録のある日・今日・予定のある日) + 明日から14日の予定 */
function tlListBody() {
  const isNow = tlYM === TODAY.slice(0, 7), planOn = tlPlanOn();
  const set = new Set();
  tlDiaryDates().forEach(d => { if (d.startsWith(tlYM)) set.add(d) });
  if (planOn) Object.values(EV).forEach(e => { if (!e.rec && !e.hol && e.d.startsWith(tlYM) && e.d < TODAY) set.add(e.d) });
  if (isNow) set.add(TODAY);
  const days = [...set].filter(d => d === TODAY || tlItems(d).length).sort();
  const { minM } = tlMonthRange();
  let h = (tlYM <= minM ? axEdge('これより前の記録 なし', false) : '') + days.map(tlGroup).join('');
  if (isNow && planOn) {
    for (let i = 1; i <= 14; i++) { const d = addDays(TODAY, i); if (tlItems(d).length) h += tlGroup(d) }
    const later = Object.values(EV).filter(e => !e.rec && !e.hol && e.d > addDays(TODAY, 14)).length;
    h += `<a class="tl-edge-a" href="#/activities?view=month">${axEdge(later ? `これより先の予定 ${later}件` : '予定 › 月で見る', true)}</a>`;
  }
  if (!days.length) h += empty({ shape: 'list', text: 'この月の記録はありません', action: { label: '支払いを記録', on: "tlCreate('openPayment')" } });
  if (isNow) h += '<div class="tl-room" aria-hidden="true"></div>';
  return h;
}
function tlListHead() {
  const { minM, maxM } = tlMonthRange();
  const [y, m] = tlYM.split('-').map(Number);
  const nav = periodNav(`${y}年${m}月`, { on: 'tlMonthShift(-1)', dis: tlYM <= minM }, { on: 'tlMonthShift(1)', dis: tlYM >= maxM });
  const tb = `<div class="tl-tb">${iconBtn('pay', '支払いを記録', "tlCreate('openPayment')")}${iconBtn('plus', '出来事を追加', "tlCreate('openNewHappening')")}</div>`;
  return `<header class="tl-top" id="tl-top"><div class="tl-ttl">${largeTitle('アクティビティ')}${tb}</div>`
    + `<div class="tl-seg">${seg(TL_VIEWS, 'list', 'tlSetView')}</div>`
    + tlSummary()
    + `<div class="tl-mn">${nav}</div>`
    + `<div class="tl-chips">${chips(TL_FILTERS, tlFilter, 'tlSetFilter')}</div>`
    + `</header><div class="tl-sp" id="tl-sp" aria-hidden="true"></div>`;
}

/* ---------- 日の表示: 時間の軸(記録=塗り、予定=点線、瞬間=細い) ---------- */
function tlGrid(d) {
  const items = tlItems(d, false), blocks = [], allday = [];
  items.forEach(it => {
    if (it.kind === 'rec') {
      const a = A[it.id];
      const label = a.type === '支払い' ? `${payLabel(a)} ${yen(a.amt)}` : (a.title || a.type);
      if (!a.time) { allday.push(`<a class="tl-ad" href="#/a/${esc(it.id)}">${esc(label)}</a>`); return }
      const s = tlMin(a.time), e = a.end ? tlMin(a.end) : null;
      blocks.push({ s, e: e == null ? s + 35 : e, instant: e == null, cls: a.type === '支払い' ? 'is-pay' : (e == null ? 'is-moment' : 'is-ev'), label, tm: a.time + (a.end ? '–' + a.end : ''), href: '#/a/' + it.id });
    } else {
      const e = EV[it.id];
      if (e.all) { allday.push(`<a class="tl-ad is-plan" href="#/ev/${esc(it.id)}">${esc(e.t)}</a>`); return }
      const s = tlMin(e.s), en = e.e ? tlMin(e.e) : s + 30;
      blocks.push({ s, e: en, instant: false, cls: it.late ? 'is-late' : 'is-plan', label: e.t, tm: e.s + (e.e ? '–' + e.e : ''), href: '#/ev/' + it.id });
    }
  });
  /* 重なりは列に分けて横に並べる(同時の項目が多い時は幅を割る) */
  blocks.sort((x, y) => x.s - y.s || x.e - y.e);
  const ends = [];
  blocks.forEach(b => { let i = ends.findIndex(v => v <= b.s); if (i < 0) { i = ends.length; ends.push(b.e) } else ends[i] = b.e; b.lane = i });
  const n = Math.max(1, ends.length);
  const top = t => (t - TL_H0 * 60) * TL_HOUR / 60;
  const blk = b => `<a class="tl-blk ${b.cls}" href="${esc(b.href)}" style="top:${top(b.s) - (b.instant ? 14 : 0)}px;height:${Math.max(b.instant ? 28 : 30, (b.e - b.s) * TL_HOUR / 60)}px;left:calc(${b.lane * 100 / n}% + 2px);width:calc(${100 / n}% - 4px)"><b>${esc(b.label)}</b><small class="num">${esc(b.tm)}</small></a>`;
  const hours = Array.from({ length: TL_H1 - TL_H0 + 1 }, (_, i) => `<div class="tl-hr" style="top:${i * TL_HOUR}px"><span class="num">${TL_H0 + i}:00</span></div>`).join('');
  const nowL = d === TODAY ? `<div class="tl-nowl" style="top:${top(tlMin(NOW))}px"><span class="num">${NOW}</span></div>` : '';
  const ad = allday.length ? `<div class="tl-allday">${allday.join('')}</div>` : '';
  if (!items.length) return empty({ shape: 'day', text: 'この日の予定・記録はありません', action: { label: '予定を追加', on: `tlCreate('openNewEvent','${d}')` } });
  return ad + `<div class="tl-grid" style="height:${(TL_H1 - TL_H0) * TL_HOUR}px">${hours}<div class="tl-cols">${blocks.map(blk).join('')}</div>${nowL}</div>`;
}
function tlPeriodLabel(d) {
  const [, m, dd] = d.split('-');
  return `${+m}月${+dd}日 ${pDow(d)}曜${d === TODAY ? ' 今日' : ''}`;
}
function tlSubHead(view) {
  const [y, m] = tlYM.split('-').map(Number);
  const nav = view === 'day'
    ? periodNav(tlPeriodLabel(tlSelDay), { on: 'tlStep(-1)', text: '前の日' }, { on: 'tlStep(1)', text: '次の日' })
    : periodNav(`${y}年${m}月`, { on: 'tlMonthShift(-1)', text: '前の月' }, { on: 'tlMonthShift(1)', text: '次の月' });
  const add = iconBtn('plus', '予定を追加', `tlCreate('openNewEvent','${tlSelDay}')`);
  return `<header class="tl-head">${largeTitle('アクティビティ')}<div class="tl-seg">${seg(TL_VIEWS, view, 'tlSetView')}</div><div class="tl-per">${nav}${add}</div></header>`;
}
function tlDayView() {
  return tlSubHead('day') + `<div class="tl-body">${tlDayCard(tlSelDay)}${tlGrid(tlSelDay)}${tlTodoBlock(tlSelDay)}</div>`;
}

/* ---------- 月の表示: 見取り図。選んだ日の軸を下に ---------- */
function tlMonthView() {
  const [y, m] = tlYM.split('-').map(Number);
  const first = new Date(y, m - 1, 1).getDay(), n = new Date(y, m, 0).getDate();
  const cells = Array.from({ length: first }, () => '<span class="tl-cell is-blank" aria-hidden="true"></span>');
  for (let dd = 1; dd <= n; dd++) {
    const d = `${tlYM}-${String(dd).padStart(2, '0')}`;
    const id = tlDiaryOf(d), rc = id ? countKids(id) : 0;
    const pl = evOn(d).filter(([k, e]) => !e.rec && !e.hol && !e.pay).length, pay = evOn(d).filter(([k, e]) => !e.rec && !e.hol && e.pay).length;
    const hol = evOn(d).some(([k, e]) => e.hol);
    const td = Object.keys(TD).filter(k => TD[k].due === d && !TD[k].done).length;
    const recM = Math.min(rc, 4), planM = Math.min(pl, 4 - recM), payM = Math.min(pay, 4 - recM - planM);
    const marks = `<span class="tl-marks">${'<i class="rec"></i>'.repeat(recM)}${'<i class="plan"></i>'.repeat(planM)}${'<i class="pay"></i>'.repeat(payM)}</span>`;
    cells.push(`<button class="tl-cell${d === TODAY ? ' is-today' : ''}${d === tlSelDay ? ' is-sel' : ''}${hol ? ' is-hol' : ''}" type="button" aria-label="${m}月${dd}日" onclick="tlPick('${d}')"><b class="num">${dd}</b>${marks}${td ? `<span class="tl-td num">${icon('check')}${td}</span>` : ''}</button>`);
  }
  const week = '日月火水木金土'.split('').map(w => `<span class="tl-wk">${w}</span>`).join('');
  const d = tlSelDay, items = tlItems(d, false);
  const body = tlDayCard(d)
    + (items.length ? `<div class="tl-ax">${tlAxis(d, items)}</div>` : empty({ shape: 'day', text: 'この日の予定・記録はありません', action: { label: '予定を追加', on: `tlCreate('openNewEvent','${d}')` } }))
    + tlTodoBlock(d);
  return tlSubHead('month') + `<div class="tl-body"><div class="tl-cal"><div class="tl-weeks">${week}</div><div class="tl-grid7">${cells.join('')}</div>`
    + `<div class="tl-lg"><span><i class="plan"></i>予定</span><span><i class="rec"></i>記録</span><span><i class="pay"></i>支払い予定</span><span>${icon('check')}ToDo</span></div></div>${body}</div>`;
}

/* ---------- 日のページ: 日記・写真・その日の記録の軸(絞り込みは使わない) ---------- */
route('activities', (id, q) => {
  const view = ['day', 'month'].includes(q.view) ? q.view : 'list';
  const hash = location.hash, changed = hash !== tlHash;
  tlHash = hash;
  if (view === 'list') {
    const want = changed ? 'auto' : tlPending; tlPending = null;
    if (want) after(() => tlLandSoon(want));
    return tlListHead() + `<div class="tl-body">${tlListBody()}</div>`;
  }
  if (changed) after(() => tlSetY(tlScroller(), 0));
  return view === 'day' ? tlDayView() : tlMonthView();
}, { tab: 'activities' });

route('day', (d) => {
  const id = tlDiaryOf(d), a = id ? A[id] : null;
  const dl = [...new Set([...tlDiaryDates(), TODAY])].sort();
  const prev = dl.filter(x => x < d).pop(), next = dl.find(x => x > d);
  const acts = [
    ...(prev ? [{ icon: 'up', label: '前の日', on: `go('#/day/${prev}')` }] : []),
    ...(next ? [{ icon: 'down', label: '次の日', on: `go('#/day/${next}')` }] : []),
    { icon: 'edit', label: '日記を書く', on: `tlDiary('${d}')` }
  ];
  const w = new Date(d + 'T00:00').getDay();
  const n = a && a.photos || 0;
  const diary = a && a.body
    ? `<p class="tl-dp-text">${esc(a.body)}</p>`
    : `<button class="tl-dp-text is-none" type="button" onclick="tlDiary('${d}')">この日のことを書く…</button>`;
  const pics = n ? `<div class="tl-photos">${Array.from({ length: n }, () => `<span class="tl-photo">${icon('photo')}</span>`).join('')}</div><p class="tl-photo-n num">写真 ${n}枚</p>` : '';
  const cnt = id ? countKids(id) : 0, sp = tlSpent(d);
  const people = new Set();
  if (id) kidActs(id).forEach(k => (A[k].roles || []).forEach(r => { if (r[1] && E[r[1]] && r[0] !== '支払先') people.add(r[1]) }));
  const items = tlItems(d, false);
  const axis = items.length
    ? `<div class="tl-ax">${tlAxis(d, items)}${d === TODAY ? '<div class="tl-room" aria-hidden="true"></div>' : ''}</div>`
    : empty({ shape: 'day', text: 'この日の出来事はまだありません', action: { label: '出来事を追加', on: `tlCreate('openNewHappening','${d}')` } });
  const hash = location.hash, changed = hash !== tlHash;   /* 開いた時だけ位置を合わせる(操作での描き直しは位置を保つ) */
  tlHash = hash;
  if (changed) after(() => {
    if (d !== TODAY) return tlSetY(tlScroller(), 0);
    const nl = document.querySelector('#app .p-now');
    if (nl) window.scrollTo({ top: nl.getBoundingClientRect().top + scrollY - 80, behavior: 'instant' });
  });
  return appbar('', { actions: acts })
    + `<section class="tl-dp"><div class="tl-dp-date"><b class="num">${+d.slice(8)}</b><span>${WD[w]}曜日<small class="num">${d.slice(0, 4)}年${+d.slice(5, 7)}月${d === TODAY ? '・今日' : ''}</small></span></div>${diary}${pics}</section>`
    + `<div class="tl-stats"><div><b class="num">${cnt}件</b><span>出来事</span></div>${sp ? `<div><b class="num">${yen(sp)}</b><span>自分の支出</span></div>` : ''}</div>`
    + (people.size ? sh('この日に交わった縁', { count: people.size }) + `<div class="tl-people">${[...people].map(p => eref(p)).join('')}</div>` : '')
    + sh('この日の時間軸') + axis;
}, { tab: 'activities' });

/* ---------- 上部の縮み(一覧のスクロールに合わせて。transform と opacity だけ) ---------- */
const tlHdr = () => document.getElementById('tl-top');
/** 幅が広い時は左の一覧が枠(pane-l)。それ以外は窓 */
const tlScroller = () => innerWidth >= 1100 ? document.querySelector('#app .pane-l') : null;
const tlY = s => s ? s.scrollTop : scrollY;
const tlMaxY = s => s ? s.scrollHeight - s.clientHeight : document.documentElement.scrollHeight - innerHeight;
const tlSetY = (s, y) => { if (s) s.scrollTop = y; else window.scrollTo({ top: y, behavior: 'instant' }) };
const tlDocTop = (el, s) => s ? el.getBoundingClientRect().top - s.getBoundingClientRect().top + s.scrollTop : el.getBoundingClientRect().top + scrollY;
function tlLayout() {
  const h = tlHdr(); if (!h) return;
  const s = tlScroller(), box = (s || document.getElementById('app')).getBoundingClientRect();
  h.style.left = box.left + 'px'; h.style.width = box.width + 'px'; h.style.top = (s ? box.top : 0) + 'px';
  document.getElementById('tl-sp').style.height = h.offsetHeight + 'px';
  tlApply();
}
/** 縮み具合 tlP(0=広い・1=縮んだ)を CSS 変数へ。e はなめらかな位置、fo/fi は消える・出る途中 */
function tlApply() {
  const h = tlHdr(); if (!h) return;
  const p = tlP, e = p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
  h.style.setProperty('--p', p.toFixed(4));
  h.style.setProperty('--e', e.toFixed(4));
  h.style.setProperty('--fo', Math.max(0, 1 - p / .4).toFixed(4));
  h.style.setProperty('--fi', Math.min(1, Math.max(0, (p - .6) / .4)).toFixed(4));
  h.classList.toggle('is-fold', p > .4);
  h.classList.toggle('is-line', p > .6);
}
function tlOnScroll() {
  tlRaf = 0;
  const h = tlHdr(); if (!h) return;
  const s = tlScroller(), y = tlY(s);
  if (tlLast === null) { tlLast = y; return }
  const dy = y - tlLast; tlLast = y;
  if (y <= 0) tlP = 0; else if (dy) tlP = Math.min(1, Math.max(0, tlP + dy / TL_R));
  tlApply();
  clearTimeout(tlSettleT); tlSettleT = setTimeout(tlSettle, 100);
}
/** 止まったら、中途半端な形を 0 か 1 へ送る(残りの分だけスクロールして、一覧と上部がずれないように) */
function tlSettle() {
  tlSettleT = 0;
  if (!tlHdr() || tlP <= .001 || tlP >= .999) return;
  const s = tlScroller(), y = tlY(s), max = Math.max(0, tlMaxY(s));
  const to = Math.min(max, Math.max(0, tlP < .5 ? y - TL_R * tlP : y + TL_R * (1 - tlP)));
  const dur = matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180, t0 = performance.now();
  tlCancelSnap();
  const step = now => { const k = dur ? Math.min(1, (now - t0) / dur) : 1, ea = 1 - Math.pow(1 - k, 3); tlSetY(s, y + (to - y) * ea); tlSnapRaf = k < 1 ? requestAnimationFrame(step) : 0 };
  tlSnapRaf = requestAnimationFrame(step);
}
function tlCancelSnap() { if (tlSnapRaf) { cancelAnimationFrame(tlSnapRaf); tlSnapRaf = 0 } }
['wheel', 'touchstart', 'pointerdown', 'keydown'].forEach(t => addEventListener(t, tlCancelSnap, { capture: true, passive: true }));
document.addEventListener('scroll', e => {
  const t = e.target;
  if ((t === document || (t.classList && t.classList.contains('pane-l'))) && !tlRaf) tlRaf = requestAnimationFrame(tlOnScroll);
}, { capture: true, passive: true });
addEventListener('resize', () => { if (tlHdr()) tlLayout() });
/** 開いた時の位置: 今月は今日の日のカードを上部のすぐ下(8px)へ、前の月は一番下へ。描き終えてから動かす */
function tlLand(want) {
  const h = tlHdr(); if (!h) return;
  const s = tlScroller();
  tlP = 0; tlLayout();
  if (want === 'bottom') tlSetY(s, tlMaxY(s));
  else {
    const t = document.querySelector('#app .tl-today'); if (!t) return;
    tlSetY(s, Math.max(0, tlDocTop(t, s) - h.offsetHeight - 8));
  }
  tlLast = tlY(s); tlApply();
}
function tlLandSoon(want) {
  const an = document.getAnimations ? document.getAnimations() : [];
  Promise.all(an.map(a => a.finished.catch(() => {}))).then(() => {
    if (!tlHdr()) return;
    tlLand(want === 'auto' ? (tlYM === TODAY.slice(0, 7) ? 'today' : 'bottom') : want);
  });
}
