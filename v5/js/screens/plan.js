/* =====================================================================
   画面: ToDo(#/todo)・ToDo の詳細(#/task/{id})・予定と ToDo の作成(接頭辞 pl)
   データは PLN(plans.js の planUpdate・planRemove・todoToggle・todoDueCount・todoToEvent・eventToTodo)。
   取り消せる操作は toast の「取り消す」で戻す。赤は期限切れだけ。
   ===================================================================== */

/* ---------- 状態(画面をまたいで残す) ---------- */
let PL_VIEW = 'today';   /* 絞り込み: today / linked / all / list:キー */
let PL_DONE = false;     /* 完了の一覧を開いているか */
let PL_PICK = null;      /* 選ぶシートの決まった後の処理(関数) */
let PL_CONV = null;      /* ToDo→予定の入力 */
let PL_NT = null;        /* ToDo 作成の入力 */
let PL_EV = null;        /* 予定 作成の入力 */
let PL_EV0 = '', PL_NT0 = '';

/* ---------- 共通 ---------- */
/** 期限が過ぎた未完了の数(ナビの赤い印にも使う) */
function plOverdue() { return Object.values(TD).filter(t => !t.done && t.due && t.due < TODAY).length }
setNavBadge('todo', plOverdue);
/** 絞り込みの述語(完了は含めない判定は呼び手が行う) */
function plPickFn(v) {
  if (v.startsWith('list:')) { const l = v.slice(5); return k => TD[k].list === l }
  if (v === 'linked') return k => !!TD[k].ev;
  if (v === 'today') return k => !!TD[k].due && TD[k].due <= TODAY;
  return () => true;
}
function plPick(v) { PL_VIEW = v; rerender() }
function plDoneToggle() { PL_DONE = !PL_DONE; rerender() }
/** 軸のない日付の表示(期限・完了日) */
function plDue(d) { return fmtMDW(d) }
/** 押せる所の属性の小さい文字(12px 以上) */
const plM = s => `<span class="pl_m">${s}</span>`;
/** 繰り返し(毎日・毎週○曜・毎月○日)が日 d に当たるか。d は e.d より後の日だけ(e.d 当日は含めない) */
function plRepOn(e, d) {
  if (!e.rep || !(d > e.d)) return false;
  if (e.rep === '毎日') return true;
  if (e.rep.startsWith('毎週')) return dowJ(d) === e.rep.charAt(2);
  if (e.rep.startsWith('毎月')) { const m = e.rep.match(/(\d+)日/); return +d.slice(8) === (m ? +m[1] : +e.d.slice(8)) }
  return false;
}
/** 次に起きる日。繰り返しが無ければ e.d。まだ始まっていない繰り返しは最初の日、始まった後は今日より後の最初の日 */
function plNextOn(e) {
  if (!e.rep || e.d > TODAY) return e.d;
  for (let i = 1; i <= 366; i++) { const d = addDays(TODAY, i); if (plRepOn(e, d)) return d }
  return e.d;
}
/** 今日の絞りで期限なしを作った時は、一覧に出るよう「すべて」へ */
function plShowNew(due) { if (!due && PL_VIEW === 'today') PL_VIEW = 'all' }

/** ToDo の行: 左=チェック欄、右=詳細への押し面。期限切れは赤のタグ */
function plRow(k) {
  const t = TD[k], over = t.due && t.due < TODAY;
  const ev = t.ev && EV[t.ev];
  const meta = [
    t.due ? (over ? tag(relDay(t.due), {kind: 'late'}) : plM(esc(relDay(t.due)))) : '',
    LISTS[t.list] ? plM(esc(LISTS[t.list].n)) : '',
    ev ? plM(esc(ev.t)) : '',
    t.sub && t.sub.length ? plM(`<span class="num">${t.sub.filter(x => x[1]).length}/${t.sub.length}</span>`) : '',
  ].filter(Boolean).join('');
  return `<div class="p-obj pl_task${t.done ? ' is-done' : ''}">`
    + `<span class="pl_task-c">${checkbox(!!t.done, {click: `event.stopPropagation();plToggle('${k}')`, label: t.t})}</span>`
    + `<a class="pl_task-a" href="#/task/${k}"><span class="pl_task-b"><span class="pl_task-t${t.done ? ' pl_done' : ''}">${esc(t.t)}${t.pri ? '<span class="pl_pri">!</span>' : ''}</span>`
    + (meta ? `<span class="pl_meta">${meta}</span>` : '') + `</span>${icon('chev')}</a></div>`;
}

/** 完了・未完了の切り替え。取り消しで戻す */
function plToggle(k) {
  const was = !!TD[k].done;
  const r = todoToggle(k);
  if (!r.ok) { toast(r.why || '変えられませんでした'); return }
  rerender();
  toast(was ? '未完了に戻しました' : '完了にしました', {undo: () => undoRun(r.undo)});
}

/** 一覧の下の入力欄から追加(「明日 牛乳 #買い物」)。日付の語は単語として読む */
function plParse(v, defList) {
  let due, lst = defList || 'inbox';
  const m0 = v.match(/(^|\s)(明後日|明日|今日)(?=\s|$)/);
  if (m0) { due = addDays(TODAY, {明後日: 2, 明日: 1, 今日: 0}[m0[2]]); v = v.replace(m0[0], ' ') }
  const m = v.match(/#(\S+)/);
  if (m) { const hit = Object.entries(LISTS).find(([k, L]) => L.n === m[1]); if (hit) { lst = hit[0]; v = v.replace(m[0], ' ') } }
  return {t: v.replace(/\s+/g, ' ').trim(), due, list: lst};
}
function plQuickAdd() {
  const el = document.getElementById('pl-qa');
  const p = plParse(el ? el.value : '', PL_VIEW.startsWith('list:') ? PL_VIEW.slice(5) : 'inbox');
  if (!p.t) { toast('ToDoの名前を入力してください'); return }
  const id = plAddTask({t: p.t, list: p.list, ...(p.due ? {due: p.due} : {})});
  plShowNew(p.due);
  rerender();
  toast('ToDoを追加しました', {undo: () => { delete PLN[id] }});
}
/** ToDo を1つ足す(キーを返す) */
function plAddTask(o) { const id = uid('t'); TD[id] = o; save(); return id }

/* ---------- ToDo の一覧(#/todo) ---------- */
route('todo', () => {
  const open = Object.keys(TD).filter(k => !TD[k].done);
  const sel = Object.keys(TD).filter(plPickFn(PL_VIEW));
  const openSel = sel.filter(k => !TD[k].done), doneSel = sel.filter(k => TD[k].done);
  const cnt = v => open.filter(plPickFn(v)).length;
  const today = todoDueCount(TODAY);
  const items = [
    {k: 'today', label: `今日まで ${today}`},
    {k: 'linked', label: `予定あり ${cnt('linked')}`},
    {k: 'all', label: `すべて ${cnt('all')}`},
    ...Object.entries(LISTS).map(([l, L]) => ({k: 'list:' + l, label: `${L.n} ${cnt('list:' + l)}`})),
  ];
  /* 期限切れは行の赤いタグだけで示す(見出しは普通の色) */
  const secs = [
    ['期限切れ', k => TD[k].due && TD[k].due < TODAY],
    ['今日', k => TD[k].due === TODAY],
    ['これから', k => TD[k].due && TD[k].due > TODAY],
    ['期限なし', k => !TD[k].due],
  ];
  let body = '';
  for (const [title, f] of secs) {
    const xs = openSel.filter(f);
    if (xs.length) body += sh(title, {count: xs.length}) + xs.map(plRow).join('');
  }
  if (doneSel.length) body += sh('完了', {count: doneSel.length, act: {label: PL_DONE ? '閉じる' : '表示', on: 'plDoneToggle()'}})
    + (PL_DONE ? doneSel.map(plRow).join('') : '');
  if (!openSel.length && !doneSel.length) body += empty({shape: 'todo', text: 'ToDo なし', action: {label: 'ToDoを追加', on: 'plOpenNewTask()'}});
  return largeTitle('ToDo')
    + summaryCard({
      metrics: [
        {v: `${plOverdue()}件`, l: '期限切れ', on: "plPick('today')"},
        {v: `${today}件`, l: '今日まで', on: "plPick('today')"},
        {v: `${open.length}件`, l: '全部の未完了', on: "plPick('all')"},
      ],
      actions: [{label: 'ToDoを追加', icon: 'plus', primary: true, on: 'plOpenNewTask()'}],
    })
    + `<div class="pl_qa"><input id="pl-qa" class="p-input" placeholder="例: 明日 牛乳 #買い物" aria-label="ToDoを追加" onkeydown="if(event.key==='Enter')plQuickAdd()"><button class="p-btn p-btn-tonal" type="button" onclick="plQuickAdd()">追加</button></div>`
    + chips(items, PL_VIEW, 'plPick')
    + body;
}, {tab: 'todo'});

/* ---------- ToDo の詳細(#/task/{id}) ---------- */
/** 詳細の上の帯(戻る=ToDoの一覧・削除) */
function plBar(id, del) {
  return `<header class="p-appbar">${iconBtn('back', 'ToDoへ戻る', "go('#/todo')")}<h2 class="p-ab-t">ToDo</h2><span class="p-grow"></span>${del ? iconBtn('trash', 'ToDoを削除', `plTaskDel('${id}')`) : ''}</header>`;
}
/** 値を1つ直す(空にすると項目を消す)。失敗は理由を出す */
function plTaskSet(k, f, v) {
  const val = typeof v === 'string' ? v.trim() : v;
  const r = planUpdate(k, {[f]: val === '' || val == null ? null : val});
  if (!r.ok) toast(r.why);
  rerender();
}
/** ToDo を消す(取り消せる)。一覧へ戻る */
function plTaskDel(k) {
  const r = planRemove(k);
  if (!r.ok) { toast(r.why); return }
  go('#/todo');
  toast('ToDoを消しました', {undo: () => undoRun(r.undo)});
}
/** 手順(sub)の項目 [文字, 0|1] */
function plSubSave(k, sub, msg) {
  const r = planUpdate(k, {sub: sub.length ? sub : null});
  if (!r.ok) { toast(r.why); return }
  rerender();
  if (msg) toast(msg, {undo: () => undoRun(r.undo)});
}
function plSubAdd(k) {
  const el = document.getElementById('pl-sub');
  const s = (el && el.value || '').trim();
  if (!s) { toast('手順を入れてください'); return }
  plSubSave(k, [...(TD[k].sub || []), [s, 0]], '手順を追加しました');
}
function plSubToggle(k, i) {
  plSubSave(k, (TD[k].sub || []).map((x, j) => j === i ? [x[0], x[1] ? 0 : 1] : x));
}
function plSubDel(k, i) {
  plSubSave(k, (TD[k].sub || []).filter((x, j) => j !== i), '手順を削除しました');
}
/** 選ぶシート: items=[{k,t,s}]。選ぶと onPick(k) を呼ぶ */
function plPickSheet(title, items, onPick) {
  PL_PICK = onPick;
  const body = items.length
    ? `<div class="p-list">${items.map(it => `<button class="p-row pl_pick-i" type="button" onclick="plPickDo('${it.k}')"><span class="p-row-body"><span class="p-row-t">${esc(it.t)}</span>${it.s ? `<span class="pl_meta">${esc(it.s)}</span>` : ''}</span></button>`).join('')}</div>`
    : empty({shape: 'list', text: '選べるものがありません'});
  openSheet({title, body});
}
function plPickDo(k) {
  const f = PL_PICK; PL_PICK = null;
  closeSheet(true);
  f && f(k);
}
/** 縁を足す・外す / 予定をつなぐ・外す */
function plTaskPickEnt(k) {
  const have = TD[k].ent || [];
  plPickSheet('縁を追加', Object.keys(E).filter(x => !have.includes(x)).map(x => ({
    k: x, t: E[x].n, s: E[x].t.map(y => TYPES[y] ? TYPES[y].n : y).join('・'),
  })), id => {
    const r = planUpdate(k, {ent: [...have, id]});
    if (!r.ok) toast(r.why); rerender();
  });
}
function plEntDel(k, id) {
  const rest = (TD[k].ent || []).filter(x => x !== id);
  const r = planUpdate(k, {ent: rest.length ? rest : null});
  rerender();
  if (r.ok) toast('縁を外しました', {undo: () => undoRun(r.undo)});
}
function plTaskPickEv(k) {
  const items = Object.keys(EV).map(x => ({k: x, t: EV[x].t, d: plNextOn(EV[x])}))
    .sort((a, b) => (a.d || '').localeCompare(b.d || ''))
    .map(x => ({k: x.k, t: x.t, s: fmtMDW(x.d)}));
  plPickSheet('予定につなぐ', items, id => {
    const r = planUpdate(k, {ev: id});
    if (!r.ok) toast(r.why); rerender();
  });
}
/** ToDo を予定に変える(同じキーのまま。取り消しで戻す) */
function plSnap(k) {
  const p = PLN[k];
  const before = JSON.parse(JSON.stringify(p));
  return () => { for (const kk of Object.keys(p)) delete p[kk]; Object.assign(p, before); plSettle(); save() };
}
function plTaskToEvent(k) {
  const t = TD[k];
  PL_CONV = {k, d: t.due || TODAY, all: true, s: '10:00', e: '11:00'};
  openSheet({title: '予定にする', body: plConvBody(), primary: {label: '予定にする', on: plConvSave}});
}
function plConvBody() {
  const c = PL_CONV;
  return `<div class="p-list">`
    + field('日付', input({type: 'date', value: c.d, oninput: 'PL_CONV.d=this.value'}))
    + `<div class="p-row"><span class="p-row-body"><span class="p-row-t">終日</span></span>${toggle(c.all, {click: 'plConvAll()', label: '終日'})}</div>`
    + (c.all ? '' : field('開始', input({type: 'time', value: c.s, oninput: 'PL_CONV.s=this.value'}))
      + field('終了', input({type: 'time', value: c.e, oninput: 'PL_CONV.e=this.value'})))
    + `</div>`;
}
function plConvAll() { PL_CONV.all = !PL_CONV.all; setSheetBody(plConvBody()) }
function plConvSave() {
  const c = PL_CONV;
  if (!c.d) { toast('日付を入れてください'); return }
  if (!c.all && (!c.s || !c.e)) { toast('時刻を入れてください'); return }
  if (!c.all && c.e < c.s) { toast('終わりは始まりより後にしてください'); return }
  const snap = plSnap(c.k);
  todoToEvent(c.k, c.all ? {d: c.d} : {d: c.d, s: c.s, e: c.e});
  closeSheet(true);
  go('#/todo');
  toast('予定にしました', {undo: () => snap()});
}
/** 予定 → ToDo(予定の詳細の操作から呼ぶ。取り消しで戻す) */
function plEvToTodo(k) {
  const snap = plSnap(k);
  try { eventToTodo(k, {list: 'inbox'}) } catch (e) { toast(e.message); return }
  go('#/todo');
  toast('ToDoにしました', {undo: () => snap()});
}
/** 買い物の ToDo を支払いで完了にする(支払いの詳細から呼ぶ)。名前が一致する未完了の買い物ToDoのキー、無ければ null */
function plShopTodoFor(name) {
  const n = String(name || '').trim();
  if (!n) return null;
  return Object.keys(TD).find(k => !TD[k].done && TD[k].list === 'shop' && TD[k].t === n) || null;
}
/** 支払い aid で買い物の ToDo k を完了にする。戻り値は取り消し用({ok, undo}) */
function plShopDone(k, aid) {
  const log = [];
  logSet(log, PLN[k], 'done', TODAY);
  logSet(log, PLN[k], 'payRec', aid);
  save();
  return okUndo(log, '完了にした');
}

route('task', id => {
  const t = TD[id];
  if (!t) return plBar(id, false) + empty({shape: 'todo', text: 'ToDo なし'});
  const ev = t.ev && EV[t.ev];
  const ents = (t.ent || []).filter(x => E[x]);
  const subs = t.sub || [];
  const doneN = subs.filter(x => x[1]).length;
  const pay = t.payRec && A[t.payRec];
  const meta = [
    t.due ? (t.due < TODAY && !t.done ? tag(`期限 ${plDue(t.due)}`, {kind: 'late'}) : `<span>期限 ${esc(plDue(t.due))}</span>`) : '<span>期限なし</span>',
    t.done ? `<span>完了 ${esc(fmtMD(t.done))}</span>` : '',
  ].join('');
  const listOpts = Object.entries(LISTS).map(([k, L]) => ({v: k, l: L.n}));
  const shop = LISTS.shop && LISTS.shop.shop && E[LISTS.shop.shop];
  return plBar(id, true)
    + `<section class="pl_card"><div class="pl_tk">${checkbox(!!t.done, {click: `plToggle('${id}')`, label: '完了'}).replace('class="p-check', 'class="p-check pl_big')}`
    + `<input class="pl_tk-in${t.done ? ' is-done' : ''}" aria-label="ToDoの題" value="${esc(t.t)}" onchange="plTaskSet('${id}','t',this.value)"></div>`
    + `<div class="pl_tk-m">${meta}</div></section>`
    + `<div class="mt"></div>`
    + list([
      prop('期限', `<input class="p-input" type="date" aria-label="期限" value="${esc(t.due || '')}" onchange="plTaskSet('${id}','due',this.value)">`, {html: true}),
      prop('リスト', `<select class="p-input p-select" aria-label="リスト" onchange="plTaskSet('${id}','list',this.value)">${listOpts.map(o => `<option value="${esc(o.v)}"${o.v === t.list ? ' selected' : ''}>${esc(o.l)}</option>`).join('')}</select>`, {html: true}),
      ...(t.list === 'shop' ? [prop('既定の店', shop ? shop.n : '未設定')] : []),
      prop('繰り返し', t.rep ? esc(`${t.rep} · 次回 ${fmtMDW(plNextOn({d: t.due || TODAY, rep: t.rep}))}`) : 'なし'),
      prop('優先', toggle(!!t.pri, {click: `plTaskSet('${id}','pri',${t.pri ? 'null' : 1})`, label: '優先'}), {html: true}),
    ])
    + sh('予定', {})
    + `<div class="p-list">`
    + (ev
      ? `<div class="p-row pl_link"><a class="pl_link-a" href="#/ev/${esc(t.ev)}">${esc(ev.t)}${plM(esc(fmtMDW(plNextOn(ev))))}</a><button class="p-sh-act" type="button" onclick="plTaskSet('${id}','ev',null)">外す</button></div>`
      : `<button class="p-row pl_pick-i" type="button" onclick="plTaskPickEv('${id}')"><span class="p-row-body"><span class="p-row-t">予定につなぐ</span></span>${icon('chev')}</button>`)
    + `<button class="p-row pl_pick-i" type="button" onclick="plTaskToEvent('${id}')"><span class="p-row-body"><span class="p-row-t">予定にする</span></span>${icon('chev')}</button></div>`
    + (ents.length
      ? sh('縁', {count: ents.length, act: {label: '縁を追加', on: `plTaskPickEnt('${id}')`}}) + `<div class="p-list">${ents.map(x => `<div class="p-row pl_link">${eref(x)}<span class="p-grow"></span><button class="p-sh-act" type="button" onclick="plEntDel('${id}','${x}')">外す</button></div>`).join('')}</div>`
      : `<div class="p-list"><button class="p-row pl_pick-i" type="button" onclick="plTaskPickEnt('${id}')"><span class="p-row-body"><span class="p-row-t">縁を追加</span></span>${icon('plus')}</button></div>`)
    + (subs.length ? sh('手順', {count: `${doneN}/${subs.length}`}) + `<div class="p-list">${subs.map((x, i) => `<div class="p-row pl_sub">${checkbox(!!x[1], {click: `plSubToggle('${id}',${i})`, label: x[0]})}<span class="p-row-body"><span class="p-row-t${x[1] ? ' pl_done' : ''}">${esc(x[0])}</span></span><button class="p-ibtn" type="button" aria-label="手順を削除" onclick="plSubDel('${id}',${i})">${icon('trash')}</button></div>`).join('')}</div>` : '')
    + `<div class="pl_qa"><input id="pl-sub" class="p-input" placeholder="手順を入れる" aria-label="手順を入れる" onkeydown="if(event.key==='Enter')plSubAdd('${id}')"><button class="p-btn p-btn-tonal" type="button" onclick="plSubAdd('${id}')">追加</button></div>`
    + sh('メモ')
    + `<div class="p-list"><textarea class="pl_memo" aria-label="メモ" onchange="plTaskSet('${id}','memo',this.value)">${esc(t.memo || '')}</textarea></div>`
    + (pay ? sh('支払い') + `<div class="p-list">${row({href: '#/a/' + t.payRec, t: payLabel(pay), right: yen(pay.amt)})}</div>` : '')
    + `<div class="pl_end"></div>`;
}, {tab: 'todo'});

/* ---------- ToDo の作成(＋「ToDo」・ToDo の一覧の「ToDoを追加」) ---------- */
function plNtNew() { return {t: '', due: '', list: 'inbox', pri: false, ent: []} }
function plOpenNewTask() {
  PL_NT = plNtNew(); PL_NT0 = JSON.stringify(PL_NT);
  openSheet({
    title: 'ToDoを追加',
    body: plNtBody(),
    primary: {label: 'ToDoを追加', on: plNtSave, disabled: true},
    dirty: () => JSON.stringify(PL_NT) !== PL_NT0,
  });
}
function plNtBody() {
  const f = PL_NT;
  const shop = f.list === 'shop' && LISTS.shop.shop && E[LISTS.shop.shop];
  const persons = Object.keys(E).filter(k => E[k].t.some(x => isUnder(x, 'person')));
  return `<div class="p-list">`
    + field('タイトル', input({value: f.t, ph: '例: 牛乳を買う', oninput: 'PL_NT.t=this.value;plNtSync()'}))
    + field('期限', input({type: 'date', value: f.due, oninput: 'PL_NT.due=this.value'}))
    + field('リスト', select({options: Object.entries(LISTS).map(([k, L]) => ({v: k, l: L.n})), value: f.list, onchange: 'PL_NT.list=this.value;setSheetBody(plNtBody())'}))
    + (shop ? `<div class="p-prop"><span class="p-prop-k">既定の店</span><span class="p-prop-v">${esc(shop.n)}</span></div>` : '')
    + `<div class="p-row"><span class="p-row-body"><span class="p-row-t">優先</span></span>${toggle(f.pri, {click: 'PL_NT.pri=!PL_NT.pri;setSheetBody(plNtBody())', label: '優先'})}</div>`
    + `</div>`
    + sh('関係する人')
    + `<div class="p-chips" role="group">${persons.map(k => `<button class="p-chip" type="button" aria-pressed="${f.ent.includes(k)}" onclick="plNtWho('${k}')">${esc(E[k].n)}</button>`).join('')}</div>`;
}
function plNtSync() { setSheetPrimary({disabled: !PL_NT.t.trim()}) }
function plNtWho(k) {
  PL_NT.ent = PL_NT.ent.includes(k) ? PL_NT.ent.filter(x => x !== k) : [...PL_NT.ent, k];
  setSheetBody(plNtBody());
}
function plNtSave() {
  const f = PL_NT, t = f.t.trim();
  if (!t) { toast('タイトルを入れてください'); return }
  const id = plAddTask({t, list: f.list || 'inbox', ...(f.due ? {due: f.due} : {}), ...(f.pri ? {pri: 1} : {}), ...(f.ent.length ? {ent: [...f.ent]} : {})});
  plShowNew(f.due);
  closeSheet(true);
  rerender();
  toast('ToDoを追加しました', {undo: () => { delete PLN[id] }});
}

/* ---------- 予定の作成(＋「予定」) ---------- */
function plEvNew() { return {t: '', all: false, d: TODAY, s: '10:00', e: '11:00', place: null, who: [], rep: 'なし', alarm: 'なし', memo: ''} }
function plOpenNewEvent(d) {
  PL_EV = plEvNew(); if (d && typeof d === 'string') PL_EV.d = d; PL_EV0 = JSON.stringify(PL_EV);
  openSheet({
    title: '予定を追加',
    body: plEvBody(),
    primary: {label: '予定を追加', on: plEvSave, disabled: true},
    dirty: () => JSON.stringify(PL_EV) !== PL_EV0,
  });
}
/** 場所の候補(場所の種類の縁。文字で絞る) */
function plPlaceSug(q) {
  const s = (q || '').trim();
  const hits = Object.keys(E).filter(k => E[k].t.some(x => isUnder(x, 'place')) && E[k].n !== s && (!s || E[k].n.includes(s))).slice(0, 8);
  return hits.map(k => `<button class="p-chip" type="button" onmousedown="event.preventDefault()" onclick="plEvPickPlace('${k}')">${esc(E[k].n)}</button>`).join('');
}
function plEvBody() {
  const f = PL_EV, pname = f.place ? f.place[1] : '';
  const persons = Object.keys(E).filter(k => E[k].t.some(x => isUnder(x, 'person')));
  return `<div class="p-list">`
    + field('タイトル', input({value: f.t, ph: '例: 花子さんとカフェ', oninput: 'PL_EV.t=this.value;plEvSync()'}))
    + `<div class="p-row"><span class="p-row-body"><span class="p-row-t">終日</span></span>${toggle(f.all, {click: 'plEvAll()', label: '終日'})}</div>`
    + field('日付', input({type: 'date', value: f.d, oninput: 'PL_EV.d=this.value'}))
    + (f.all ? '' : field('開始', input({type: 'time', value: f.s, oninput: 'PL_EV.s=this.value'}))
      + field('終了', input({type: 'time', value: f.e, oninput: 'PL_EV.e=this.value'})))
    + field('場所', input({value: pname, ph: '場所を入力', oninput: 'plEvPlace(this.value)'}))
    + `<div class="pl_sug" id="pl-ev-sug">${plPlaceSug(pname)}</div>`
    + field('繰り返し', select({options: ['なし', '毎日', '毎週', '毎月'], value: f.rep, onchange: 'PL_EV.rep=this.value'}))
    + field('通知', select({options: ['なし', '10分前', '30分前', '1時間前', '前日'], value: f.alarm, onchange: 'PL_EV.alarm=this.value'}))
    + field('メモ', `<textarea class="pl_memo" oninput="PL_EV.memo=this.value">${esc(f.memo)}</textarea>`)
    + `</div>`
    + sh('参加者')
    + `<div class="p-chips" role="group">${persons.map(k => `<button class="p-chip" type="button" aria-pressed="${f.who.includes(k)}" onclick="plEvWho('${k}')">${esc(E[k].n)}</button>`).join('')}</div>`;
}
function plEvSync() { setSheetPrimary({disabled: !PL_EV.t.trim()}) }
function plEvAll() { PL_EV.all = !PL_EV.all; setSheetBody(plEvBody()) }
function plEvWho(k) {
  PL_EV.who = PL_EV.who.includes(k) ? PL_EV.who.filter(x => x !== k) : [...PL_EV.who, k];
  setSheetBody(plEvBody());
}
/** 場所の文字: 縁の名前と一致すれば縁につなぐ。違えば名前だけ */
function plEvPlace(v) {
  const name = v.trim();
  if (!name) PL_EV.place = null;
  else {
    const hit = Object.keys(E).find(k => E[k].n === name);
    PL_EV.place = [hit || null, name];
  }
  const el = document.getElementById('pl-ev-sug');
  if (el) el.innerHTML = plPlaceSug(name);
}
function plEvPickPlace(k) { PL_EV.place = [k, E[k].n]; setSheetBody(plEvBody()) }
function plEvSave() {
  const f = PL_EV, t = f.t.trim();
  if (!t) { toast('タイトルを入れてください'); return }
  if (!f.d) { toast('日付を入れてください'); return }
  if (!f.all && (!f.s || !f.e)) { toast('時刻を入れてください'); return }
  if (!f.all && f.e < f.s) { toast('終わりは始まりより後にしてください'); return }
  const id = uid('ev');
  const o = {t, d: f.d};
  if (f.all) o.all = 1; else { o.s = f.s; o.e = f.e }
  if (f.place) o.place = f.place;
  if (f.who.length) o.who = [...f.who];
  if (f.rep !== 'なし') o.rep = f.rep === '毎月' ? `毎月${+f.d.slice(8)}日` : f.rep === '毎週' ? `毎週${dowJ(f.d)}曜` : '毎日';
  if (f.alarm !== 'なし') o.alarm = f.alarm;
  if (f.memo.trim()) o.memo = f.memo.trim();
  EV[id] = o;
  closeSheet(true);
  rerender();
  toast('予定を追加しました', {undo: () => { delete PLN[id] }});
}

/* ---------- 作成の登録 ---------- */
addCreate({key: 'plan', label: '予定', icon: 'schedule', primaryOn: ['activities'], run: () => plOpenNewEvent()});
addCreate({key: 'task', label: 'ToDo', icon: 'todo', primaryOn: ['todo'], run: () => plOpenNewTask()});
