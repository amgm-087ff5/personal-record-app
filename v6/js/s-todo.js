/* 個人記録 v6 — ToDo(一覧・詳細・追加のシート・リストの管理)。接頭辞 td */
'use strict';
setNavBadge('todo', () => Object.values(DB.tasks).filter(taskLate).length);

/* 画面の状態(描き直しても残す) */
const TDS = { f: 'due', q: '', focus: null, done: (() => { try { return localStorage.getItem('prmock-v6-td-done') === '1'; } catch (e) { return false; } })() };

/* ---------- 小道具 ---------- */
const tdListName = k => (DB.lists[k] || {}).n || '';
const tdOpen = () => Object.entries(DB.tasks).filter(([, t]) => taskOpen(t));
const tdHasAct = t => !!(t.act && A(t.act));
/** 絞り込みの判定(f = due | all | linked | l:リストid) */
function tdMatch(f) {
  if (f === 'due') return t => !!(t.due && t.due <= TODAY);
  if (f === 'linked') return tdHasAct;
  if (f.startsWith('l:')) return t => t.list === f.slice(2);
  return () => true;
}
function tdFix() { if (TDS.f.startsWith('l:') && !DB.lists[TDS.f.slice(2)]) TDS.f = 'due'; }
const tdDefList = () => (TDS.f.startsWith('l:') && DB.lists[TDS.f.slice(2)] ? TDS.f.slice(2) : Object.keys(DB.lists)[0]);
const tdMute = s => `<span class="td-mute">${esc(s)}</span>`;
/** 期限のピルの鍵(今日・明日・来週のどれか) */
const tdPill = d => !d ? '' : d === TODAY ? 'today' : d === dAdd(TODAY, 1) ? 'tm' : d === dAdd(TODAY, 7) ? 'nw' : null;
const tdPillDate = k => k === 'today' ? TODAY : k === 'tm' ? dAdd(TODAY, 1) : k === 'nw' ? dAdd(TODAY, 7) : '';
const TD_PILLS = [['', 'なし'], ['today', '今日'], ['tm', '明日'], ['nw', '来週']];
const TD_REPS = [['day', '毎日'], ['week', '毎週'], ['month', '毎月']];
const tdRepSub = (k, due) => !due ? '' : k === 'week' ? `${dowJ(due)}曜` : k === 'month' ? `${+due.slice(8)}日` : '';
const tdRepText = t => !t.rep ? '' : t.rep.u === 'day' ? '毎日' : t.rep.u === 'week' ? `毎週${t.due ? ' ' + dowJ(t.due) + '曜' : ''}` : `毎月${t.due ? ' ' + +t.due.slice(8) + '日' : ''}`;
/** 予定・記録の表示(札なし) */
const tdActV = id => { const a = A(id); return a ? `${kindIc(a)}<span>${esc(actTitle(a))}</span><span class="mini num">${relDay(a.date)} ${timeText(a)}</span>` : ''; };
const tdEntsV = ids => ids.map(e => ent(e, { link: false })).join('');
const tdEnter = fn => `onkeydown="if(event.key==='Enter'&&!event.isComposing&&event.keyCode!==229){event.preventDefault();${fn}}"`;

/** 予定・記録を選ぶ(これからの予定 + 今日の記録)。on(id か null) */
function tdActPick(cur, on) {
  const seen = new Set(), items = [];
  const add = (id, a, sub) => { if (seen.has(id)) return; seen.add(id); items.push({ k: id, label: actTitle(a), sub, lead: kindIc(a) }); };
  upcoming(60).forEach(([id, a, d]) => add(id, a, `${relDay(d)} ${fmtMD(d)}(${dowJ(d)}) ${timeText(a)}`));
  actsOn(TODAY, { state: 'done' }).forEach(([id, a]) => add(id, a, `今日の記録 ${timeText(a)}`));
  if (cur && !seen.has(cur) && A(cur)) { const a = A(cur); add(cur, a, `${relDay(a.date)} ${timeText(a)}`); }
  pick({ title: '予定・記録', items, cur: cur || null, search: true, none: { label: 'なし' }, on });
}
/** エンティティを選ぶ(複数)。on(配列) */
function tdEntPick(cur, on) {
  pick({ title: 'エンティティ', multi: true, search: true, cur, on, items: Object.entries(DB.ents).map(([id, e]) => ({ k: id, label: e.n, sub: e.types && e.types[0] ? typePath(e.types[0]) : '', lead: avatar(id) })) });
}

/* =====================================================================
   一覧 #/todo
   ===================================================================== */
route('todo', () => {
  tdFix(); after(() => wireSwipe());
  return `<div class="screen s-todo">
    ${head('ToDo', { actions: iconBtn('list', 'リストの管理', 'tdLists()', 'on-card') + iconBtn('plus', 'ToDoを追加', 'tdNew()', 'pri') })}
    <div class="td-quick"><div class="td-q"><input id="td-q" class="input" type="text" value="${esc(TDS.q)}" placeholder="明日 牛乳 #買い物 !" oninput="tdInput(this.value)" ${tdEnter('tdAdd()')} aria-label="すばやく足す" autocomplete="off" enterkeyhint="done"><button class="btn pri" onmousedown="event.preventDefault()" onclick="tdAdd()">${ic('plus')}<span>足す</span></button></div><div id="td-pv" class="td-pv">${tdPv()}</div></div>
    <div id="td-list" class="td-list">${tdBody()}</div>
  </div>`;
}, { tab: 'todo', root: 1 });

/** すばやく足す欄の解釈(期限の札・リスト・優先) */
function tdPv() {
  const raw = TDS.q.trim(); if (!raw) return '';
  const p = parseTask(raw), lk = p.list && DB.lists[p.list] ? p.list : tdDefList();
  return `${p.t ? `<b class="td-pv-t">${esc(p.t)}</b>` : ''}${p.due ? tag(`${relDay(p.due)} ${fmtMD(p.due)}(${dowJ(p.due)})`, p.due < TODAY ? 'late' : p.due === TODAY ? 'me' : '') : ''}<span class="mini">${ic('list')}${esc(tdListName(lk))}</span>${p.pri ? `<span class="tag"><i class="pri">!</i>優先</span>` : ''}`;
}
function tdInput(v) { TDS.q = v; const el = $('#td-pv'); if (el) el.innerHTML = tdPv(); }
function tdNew() { openTaskEditor(TDS.f.startsWith('l:') ? { list: TDS.f.slice(2) } : undefined); }

/** 絞り込み・一覧・完了(この部分だけ差し替える) */
function tdBody() {
  tdFix();
  const open = tdOpen(), f = TDS.f;
  const cnt = fn => open.filter(([, t]) => fn(t)).length;
  const items = [['due', '今日まで', tasksDue().length], ['all', 'すべて', open.length], ['linked', '予定あり', cnt(tdHasAct)], ...Object.keys(DB.lists).map(k => ['l:' + k, DB.lists[k].n, cnt(t => t.list === k)])];
  return `${chips(items, f, 'tdFilter')}${tdSections(open)}${tdDone()}`;
}
const tdSw = id => `<div class="swipe td-sw"><div class="swipe-in">${taskRow(id, { noList: TDS.f.startsWith('l:') })}</div><button class="swipe-act" onclick="taskCheck(${q(id)})">${ic('check')}<span>完了</span></button></div>`;
const tdBlock = (title, ids) => ids.length ? `<section>${sec(title, { n: ids.length })}${rows(ids.map(tdSw))}</section>` : '';
function tdSections(openAll) {
  const f = TDS.f, fn = tdMatch(f), group = f === 'all' || f === 'linked';
  const open = openAll.filter(([, t]) => fn(t)).sort((a, b) => (a[1].due || '9').localeCompare(b[1].due || '9'));
  const loose = group ? open.filter(([, t]) => !tdHasAct(t)) : open, linked = group ? open.filter(([, t]) => tdHasAct(t)) : [];
  const part = g => loose.filter(([, t]) => g(t.due)).map(([id]) => id), d7 = dAdd(TODAY, 7);
  let html = f === 'linked' ? '' : tdBlock('期限切れ', part(d => d && d < TODAY)) + tdBlock('今日', part(d => d === TODAY)) + tdBlock('明日〜7日', part(d => d > TODAY && d <= d7)) + tdBlock('それより先', part(d => d > d7)) + tdBlock('期限なし', part(d => !d));
  if (linked.length) {
    const by = new Map(); linked.forEach(([id, t]) => { (by.get(t.act) || by.set(t.act, []).get(t.act)).push(id); });
    const gs = [...by.entries()].sort((x, y) => (A(x[0]).date + (A(x[0]).start || '')).localeCompare(A(y[0]).date + (A(y[0]).start || ''))).map(([aid, ids]) => {
      const a = A(aid);
      return `<div class="td-g"><a class="td-gh" href="#/a/${aid}">${kindIc(a)}<b>${esc(actTitle(a))}</b><span class="mini num">${relDay(a.date)} ${timeText(a)}</span>${ic('chev', 'row-chev')}</a>${rows(ids.map(tdSw))}</div>`;
    }).join('');
    html += `<section class="td-gs">${f === 'all' ? sec('予定ごと', { n: linked.length }) : ''}${gs}</section>`;
  }
  if (html) return html;
  const text = f === 'due' ? '今日までのToDoは ありません' : f === 'linked' ? '予定に付いたToDoは ありません' : f.startsWith('l:') ? `「${tdListName(f.slice(2))}」のToDoは ありません` : 'ToDoは ありません';
  return `<div class="card">${empty({ icon: 'task', text, action: { label: 'ToDoを追加', on: 'tdNew()' } })}</div>`;
}
/** 完了(最近の20件)。畳んでおき、開いた状態は覚える */
function tdDone() {
  const fn = tdMatch(TDS.f === 'due' ? 'all' : TDS.f), ds = Object.entries(DB.tasks).filter(([, t]) => t.done && fn(t)).sort((a, b) => b[1].done.localeCompare(a[1].done));
  if (!ds.length) return '';
  const noList = TDS.f.startsWith('l:');
  return `<section class="td-done"><button class="td-dn" aria-expanded="${TDS.done}" onclick="tdDoneToggle()"><span>完了</span><b class="num">${ds.length}</b>${ic('chev')}</button>${TDS.done ? rows(ds.slice(0, 20).map(([id, t]) => `<div class="td-dr">${taskRow(id, { noList })}<span class="td-dd num">${fmtMD(t.done)}</span></div>`)) : ''}</section>`;
}
function tdDoneToggle() { TDS.done = !TDS.done; try { localStorage.setItem('prmock-v6-td-done', TDS.done ? '1' : '0'); } catch (e) { } tdRefresh(); }
function tdFilter(k) { TDS.f = k; tdRefresh(); tdInput(TDS.q); }
function tdRefresh() { const el = $('#td-list'); if (!el) return; el.innerHTML = tdBody(); wireSwipe(el); if (typeof renderNav === 'function') renderNav(); }

/** すばやく足す: 解釈して足し、欄にフォーカスを残して続けて足せる */
function tdAdd() {
  const inp = $('#td-q'), p = parseTask(TDS.q);
  const r = taskAdd({ t: p.t, due: p.due, list: p.list || (TDS.f.startsWith('l:') ? TDS.f.slice(2) : undefined), pri: p.pri ? 1 : undefined });
  if (!r.ok) { toast(r.why, { bad: 1 }); hapt(30); inp && inp.focus(); return; }
  if (!tdMatch(TDS.f)(DB.tasks[r.id])) TDS.f = 'all';
  TDS.q = ''; if (inp) { inp.value = ''; inp.focus(); }
  tdInput(''); tdRefresh(); hapt();
  const a = $(`a.task-m[href="#/task/${r.id}"]`); if (a && a.closest('.task')) a.closest('.task').classList.add('td-new');
  toast(`「${p.t}」を足しました`, { undo: r.undo });
}

/* =====================================================================
   ToDo を追加するシート
   ===================================================================== */
let TDE = null;
function openTaskEditor(o = {}) {
  const sd = window._capSeed && window._capSeed.type === 'task' ? window._capSeed : null, keys = Object.keys(DB.lists);
  const l0 = o.list || (sd && sd.list), list = DB.lists[l0] ? l0 : keys[0], ents = [...new Set([...(o.ents || []), ...((sd && sd.ents) || [])])];
  TDE = { raw: sd ? sd.t || '' : '', due: o.due || (sd && sd.due) || '', list, list0: list, pri: !!(sd && sd.pri), rep: '', act: o.act || '', ents, memo: '', more: !!(o.act || ents.length), auto: {} };
  openSheet({ id: 'task-ed', title: 'ToDoを追加', body: tdeBody(), dirty: () => !!(TDE.raw.trim() || TDE.memo.trim()), primary: { label: 'ToDoを追加', on: tdeSave, disabled: !parseTask(TDE.raw).t } });
}
function tdeBody() {
  const e = TDE;
  return `${field('やること', `<input id="tde-t" class="input" type="text" value="${esc(e.raw)}" placeholder="例: 明日 牛乳 #買い物" oninput="tdeInput(this.value)" ${tdEnter('tdeSave()')} autofocus autocomplete="off" aria-label="やること">`)}
    <div id="tde-fx" class="td-fx">${tdeFx()}</div>${tdeMore()}`;
}
function tdeFx() {
  const e = TDE;
  return `<div class="field"><span class="field-l">期限</span>${input({ id: 'tde-due', type: 'date', value: e.due, onchange: 'tdeDue(this.value)', label: '期限' })}${chips(TD_PILLS, tdPill(e.due), 'tdeDuePill')}</div>
    <div class="field"><span class="field-l">リスト</span>${chips(Object.keys(DB.lists).map(k => [k, DB.lists[k].n]), e.list, 'tdeList')}</div>
    ${rows(row({ tHtml: `優先 <i class="td-pri">!</i>`, right: toggle(e.pri, 'tdePri()', '優先') }))}`;
}
function tdeMore() {
  const e = TDE; if (!e.more) return btn('繰り返し・予定・メモを付ける', 'tdeOpenMore()', 'dash', { icon: 'down' });
  return `<div class="field"><span class="field-l">繰り返し</span>${chips([['', 'なし'], ...TD_REPS], e.rep, 'tdeRep')}</div>
    ${pickField('予定', e.act ? tdActV(e.act) : 'なし', 'tdeAct()', { emptyv: !e.act })}
    ${pickField('エンティティ', e.ents.length ? tdEntsV(e.ents) : 'なし', 'tdeEnts()', { emptyv: !e.ents.length })}
    ${field('メモ', textarea({ id: 'tde-m', rows: 3, value: e.memo, oninput: 'TDE.memo=this.value', label: 'メモ' }))}`;
}
const tdeRedraw = () => setSheetBody(tdeBody());
/** 「やること」の入力。「明日」「#買い物」「!」を期限・リスト・優先の欄へ反映する(欄だけ描き直す) */
function tdeInput(v) {
  const e = TDE, p = parseTask(v), a = e.auto; let ch = false; e.raw = v;
  if (p.due !== a.due) { if (p.due) e.due = p.due; else if (e.due === a.due) e.due = ''; a.due = p.due; ch = true; }
  if (p.list !== a.list) { if (p.list) e.list = p.list; else if (e.list === a.list) e.list = e.list0; a.list = p.list; ch = true; }
  if (!!p.pri !== !!a.pri) { if (p.pri) e.pri = true; else if (e.pri) e.pri = false; a.pri = p.pri; ch = true; }
  if (ch) $('#tde-fx').innerHTML = tdeFx();
  setSheetPrimary({ label: 'ToDoを追加', on: tdeSave, disabled: !p.t });
}
function tdeDue(v) { TDE.due = v; $('#tde-fx').innerHTML = tdeFx(); }
function tdeDuePill(k) { TDE.due = tdPillDate(k); $('#tde-fx').innerHTML = tdeFx(); }
function tdeList(k) { TDE.list = k; $('#tde-fx').innerHTML = tdeFx(); }
function tdePri() { TDE.pri = !TDE.pri; $('#tde-fx').innerHTML = tdeFx(); }
function tdeOpenMore() { TDE.more = true; tdeRedraw(); }
function tdeRep(k) { TDE.rep = k; if (k && !TDE.due) { TDE.due = TODAY; } tdeRedraw(); }
function tdeAct() { tdActPick(TDE.act, k => { TDE.act = k || ''; tdeRedraw(); }); }
function tdeEnts() { tdEntPick(TDE.ents, l => { TDE.ents = l; tdeRedraw(); }); }
function tdeSave() {
  const e = TDE, p = parseTask(e.raw);
  const r = taskAdd({ t: p.t, due: e.due || undefined, list: e.list, pri: e.pri ? 1 : undefined, rep: e.rep ? { u: e.rep } : undefined, act: e.act || undefined, ents: e.ents, memo: e.memo.trim() || undefined });
  if (r.ok && !tdMatch(TDS.f)(DB.tasks[r.id])) TDS.f = 'all';
  done(r, { close: 1, msg: `「${p.t}」を足しました` });
}

/* =====================================================================
   詳細 #/task/{id}
   ===================================================================== */
/** 未確定の帯(ToDo。確定 / 雑記を見る) */
function tdTent(id) {
  const t = DB.tasks[id]; if (!t || !t.tent) return '';
  return banner(`未確定です。内容を確かめてください<div class="tent-b">${btn('確定', `window.tentConfirm && done(tentConfirm({t:'tasks',k:${q(id)}}))`, 'pri', { sm: 1, icon: 'check' })}${btn('雑記を見る', `window.openInbox && openInbox({focus:{t:'tasks',k:${q(id)}}})`, 'sec', { sm: 1 })}</div>`, 'info');
}
route('task', id => {
  const t = DB.tasks[id];
  if (!t) return `<div class="screen s-task">${bar('ToDo')}<div class="card">${empty({ icon: 'task', text: 'ToDoが見つかりません' })}</div>${btn('ToDoの一覧へ', "go('#/todo')", 'sec', { wide: 1 })}</div>`;
  const late = taskLate(t), sub = t.sub || [], sd = sub.filter(s => s.d).length, pa = t.payAct && A(t.payAct), I = q(id);
  after(() => { if (TDS.focus === 'sub') { TDS.focus = null; const el = $('#td-sub'); el && el.focus({ preventScroll: true }); } });
  const dueV = !t.due ? tdMute('なし') : `${late ? tag(relDay(t.due), 'late') : t.due === TODAY ? tag('今日', 'me') : ''}<span class="num">${fmtMD(t.due)}</span><span>${dowJ(t.due)}曜</span>${late || t.due === TODAY ? '' : `<span class="mini">${relDay(t.due)}</span>`}`;
  const a = t.act && A(t.act);
  const actRow = a
    ? `<div class="prop"><span class="prop-k">予定・記録</span><span class="prop-v"><a class="td-lk" href="#/a/${t.act}">${tdActV(t.act)}</a></span><button class="btn ghost sm td-chg" onclick="tdActEdit(${I})">変える</button></div>`
    : prop('予定・記録', tdMute('なし'), { on: `tdActEdit(${I})` });
  return `<div class="screen s-task">
    ${bar(t.t, { actions: iconBtn('more', 'その他', `tdTaskMenu(${I})`) })}
    ${tdTent(id)}
    <div class="td-hero${t.done ? ' done' : ''}">${check(t.done, `taskCheck(${I})`, { late, label: t.done ? '未完了に戻す' : '完了' })}<button class="td-title" data-bar-watch onclick="tdTitleEdit(${I})">${esc(t.t)}</button></div>
    ${t.done ? `<div class="td-state">${tag(`完了 ${fmtMD(t.done)}`, 'me')}${btn('未完了に戻す', `taskCheck(${I})`, 'sec', { sm: 1, icon: 'undo' })}</div>` : ''}
    ${rows([
      prop('期限', dueV, { on: `tdDueSheet(${I})` }),
      prop('リスト', esc(tdListName(t.list)), { on: `tdListEdit(${I})` }),
      ...((DB.lists[t.list] || {}).shop && E(DB.lists[t.list].shop) ? [prop('店', ent(DB.lists[t.list].shop))] : []),
      prop('優先', `<span class="tgl" aria-hidden="true" aria-checked="${!!t.pri}"><i></i></span><span>${t.pri ? '優先' : 'ふつう'}</span>`, { on: `tdSet(${I},{pri:${t.pri ? 'null' : 1}})` }),
      prop('繰り返し', t.rep ? `${ic('repeat', 'td-ri')}<span>${esc(tdRepText(t))}</span>` : tdMute('なし'), { on: `tdRepEdit(${I})` }),
      actRow,
      ...(t.done ? [] : [row({ lead: ic('cal'), t: '予定にする', on: `tdPlanOpen(${I})`, chev: 1 })]),
      prop('エンティティ', (t.ents || []).length ? tdEntsV(t.ents) : tdMute('なし'), { on: `tdEntEdit(${I})` }),
      prop('メモ', t.memo ? `<span class="td-memo">${esc(t.memo)}</span>` : tdMute('書く'), { on: `tdMemoEdit(${I})` }),
    ])}
    ${pa ? `<section>${sec('買った支払い')}${paySlip(t.payAct)}</section>` : ''}
    <section>${sec('小さなやること', { n: sub.length ? `${sd}/${sub.length}` : null })}${rows([
      sub.length ? `<div class="td-pg">${progress(sd / sub.length)}</div>` : '',
      ...sub.map((s, i) => `<div class="row td-sub${s.d ? ' done' : ''}">${check(s.d, `tdSubToggle(${I},${i})`, { label: s.t })}<span class="row-m"><span class="row-t">${esc(s.t)}</span></span><button class="icon-btn td-x" onclick="tdSubDel(${I},${i})" aria-label="消す">${ic('trash')}</button></div>`),
      `<div class="row td-add"><span class="row-lead">${ic('plus')}</span><input id="td-sub" class="td-in" type="text" placeholder="小さなやることを足す" ${tdEnter(`tdSubAdd(${I})`)} aria-label="小さなやること" autocomplete="off" enterkeyhint="done"><button class="btn ghost" onmousedown="event.preventDefault()" onclick="tdSubAdd(${I})">足す</button></div>`])}</section>
  </div>`;
}, {});

/** 予定にする: 日付・終日・開始と終了 → taskToPlan */
let TDP = null;
function tdPlanOpen(id) {
  const t = DB.tasks[id]; if (!t) return;
  const h = Math.min(+NOW.slice(0, 2) + 1, 22), s0 = pad2(h) + ':00', e0 = pad2(h + 1) + ':00';
  TDP = { id, date: t.due || TODAY, allDay: true, start: s0, end: e0 };
  TDP.o = JSON.stringify([TDP.date, TDP.allDay, TDP.start, TDP.end]);
  openSheet({ id: 'td-plan', title: '予定にする', body: tdPlanBody(), dirty: () => JSON.stringify([TDP.date, TDP.allDay, TDP.start, TDP.end]) !== TDP.o, primary: { label: '予定にする', on: tdPlanSave } });
}
function tdPlanBody() {
  const e = TDP;
  return `<div class="stack">${field('日付', input({ id: 'tdp-d', type: 'date', value: e.date, oninput: 'TDP.date=this.value', onchange: 'TDP.date=this.value', label: '日付' }))}
    ${rows(row({ t: '終日', right: toggle(e.allDay, 'tdPlanAll()', '終日') }))}
    ${e.allDay ? '' : `<div class="grid2">${field('開始', input({ id: 'tdp-s', type: 'time', value: e.start, oninput: 'TDP.start=this.value', onchange: 'TDP.start=this.value', label: '開始' }))}${field('終了', input({ id: 'tdp-e', type: 'time', value: e.end, oninput: 'TDP.end=this.value', onchange: 'TDP.end=this.value', label: '終了' }))}</div>`}</div>`;
}
function tdPlanAll() { TDP.allDay = !TDP.allDay; setSheetBody(tdPlanBody()); }
function tdPlanSave() {
  const e = TDP; if (!isDate(e.date)) return toast('日付を入れてください', { bad: 1 });
  if (!e.allDay && !isHM(e.start)) return toast('開始の時刻を入れてください', { bad: 1 });
  if (!e.allDay && e.end && isHM(e.end) && e.end < e.start) return toast('終了は開始より後にしてください', { bad: 1 });
  const r = taskToPlan(e.id, e.allDay ? { date: e.date, allDay: true } : { date: e.date, allDay: false, start: e.start, end: isHM(e.end) ? e.end : undefined });
  done(r, { close: 1, go: '#/a/' + r.id, replace: 1 });
}

/** 直して保存(全て taskUpdate → done) */
function tdSet(id, patch, o = {}) { return done(taskUpdate(id, patch), o); }
function tdTitleEdit(id) {
  const t = DB.tasks[id]; if (!t) return;
  openSheet({ id: 'td-title', title: '題を直す', body: `<input id="tdt-t" class="input" type="text" value="${esc(t.t)}" ${tdEnter(`tdTitleSave(${q(id)})`)} autofocus autocomplete="off" aria-label="題">`, dirty: () => val('tdt-t') !== t.t, primary: { label: '直す', on: () => tdTitleSave(id) } });
}
function tdTitleSave(id) { tdSet(id, { t: val('tdt-t') }, { close: 1 }); }
function tdListEdit(id) {
  const t = DB.tasks[id], open = tdOpen();
  pick({ title: 'リスト', cur: t.list, items: Object.keys(DB.lists).map(k => ({ k, label: DB.lists[k].n, sub: `未完了 ${open.filter(([, x]) => x.list === k).length}` })), on: k => k && tdSet(id, { list: k }) });
}
function tdRepEdit(id) {
  const t = DB.tasks[id];
  pick({ title: '繰り返し', cur: t.rep ? t.rep.u : null, none: { label: 'なし' }, items: TD_REPS.map(([k, label]) => ({ k, label, sub: tdRepSub(k, t.due) })), on: k => tdSet(id, k ? { rep: { u: k }, ...(t.due ? {} : { due: TODAY }) } : { rep: null }) });
}
function tdActEdit(id) { tdActPick(DB.tasks[id].act, k => tdSet(id, { act: k || null })); }
function tdEntEdit(id) { tdEntPick(DB.tasks[id].ents || [], l => tdSet(id, { ents: l })); }
function tdMemoEdit(id) {
  const t = DB.tasks[id], cur = t.memo || '';
  openSheet({ id: 'td-memo', title: 'メモ', body: textarea({ id: 'tdm-t', rows: 6, value: cur, auto: 1, label: 'メモ' }), dirty: () => val('tdm-t') !== cur, primary: { label: '保存', on: () => tdSet(id, { memo: val('tdm-t').trim() || null }, { close: 1 }) } });
}
/** 期限: ピルは押すとすぐ決まる。日付は「決める」で */
let TDD = null;
function tdDueSheet(id) {
  const t = DB.tasks[id]; TDD = { id, v: t.due || '', v0: t.due || '' };
  openSheet({ id: 'td-due', title: '期限', body: `${chips(TD_PILLS, tdPill(t.due), 'tdDuePill')}<div class="field"><span class="field-l">日付</span>${input({ id: 'tdd-d', type: 'date', value: TDD.v, oninput: 'TDD.v=this.value', onchange: 'TDD.v=this.value', label: '日付' })}</div>`, dirty: () => TDD.v !== TDD.v0, primary: { label: '決める', on: () => tdSet(id, { due: TDD.v || null }, { close: 1 }) } });
}
function tdDuePill(k) { tdSet(TDD.id, { due: tdPillDate(k) || null }, { close: 1 }); }
/* 小さなやること */
function tdSubAdd(id) {
  const el = $('#td-sub'), v = el ? el.value.trim() : '', t = DB.tasks[id]; if (!t) return;
  if (!v) { toast('やることを入れてください', { bad: 1 }); el && el.focus(); return; }
  TDS.focus = 'sub'; tdSet(id, { sub: [...(t.sub || []), { t: v }] }, { msg: '小さなやることを足しました' });
}
function tdSubToggle(id, i) { const t = DB.tasks[id]; tdSet(id, { sub: t.sub.map((s, j) => j !== i ? s : s.d ? { t: s.t } : { t: s.t, d: 1 }) }, { quiet: 1 }); }
function tdSubDel(id, i) { const t = DB.tasks[id]; tdSet(id, { sub: t.sub.filter((_, j) => j !== i) }, { msg: '小さなやることを消しました' }); }
/* その他 */
function tdTaskMenu(id) { menu('ToDo', [{ label: '複製', icon: 'plus', on: () => tdDup(id) }, { label: '消す', icon: 'trash', on: () => done(taskRemove(id), { go: '#/todo', replace: true }) }]); }
function tdDup(id) {
  const t = DB.tasks[id]; if (!t) return;
  const r = taskAdd({ t: t.t, due: t.due, list: t.list, pri: t.pri, act: t.act, ents: t.ents, rep: t.rep, memo: t.memo, sub: (t.sub || []).map(s => ({ t: s.t })) });
  done(r, { go: `#/task/${r.id}`, msg: '複製しました' });
}

/* =====================================================================
   リストの管理
   ===================================================================== */
function tdLists() { openSheet({ id: 'td-lists', title: 'リスト', body: tdListsBody() }); }
function tdListsBody() {
  const open = tdOpen();
  return `${rows(Object.keys(DB.lists).map(k => { const sh = DB.lists[k].shop, se = sh && E(sh);
    return `<div class="row td-lr"><button class="td-lm" onclick="tdListOpen(${q(k)})" aria-label="${esc(DB.lists[k].n)}を直す"><span class="row-lead">${ic('list')}</span><span class="row-m"><span class="row-t">${esc(DB.lists[k].n)}</span>${se ? `<span class="row-s"><span class="mini">${ic('place')}${esc(se.n)}</span></span>` : ''}</span></button><span class="row-r"><span class="num td-cnt">${open.filter(([, t]) => t.list === k).length}</span>${iconBtn('trash', `${DB.lists[k].n}を消す`, `tdListDel(${q(k)})`)}</span></div>`; }))}
    <div class="td-q"><input id="tdl-in" class="input" type="text" placeholder="リストの名前" ${tdEnter('tdListAdd()')} aria-label="リストの名前" autocomplete="off"><button class="btn pri" onmousedown="event.preventDefault()" onclick="tdListAdd()">${ic('plus')}<span>足す</span></button></div>`;
}
/** リストを直す: 名前と、買い物用の既定の店 */
let TDL = null;
function tdListOpen(k) {
  const l = DB.lists[k]; if (!l) return;
  TDL = { id: k, n: l.n, shop: l.shop || null, n0: l.n, shop0: l.shop || null };
  openSheet({ id: 'td-list', title: 'リストを直す', body: tdListBody(), dirty: () => TDL.n !== TDL.n0 || TDL.shop !== TDL.shop0, primary: { label: '直す', on: tdListSave } });
}
function tdListBody() {
  const e = TDL;
  return `<div class="stack">${field('名前', input({ id: 'tdl-n', value: e.n, oninput: 'TDL.n=this.value', label: '名前' }))}
    ${pickField('既定の店(買い物用)', e.shop && E(e.shop) ? ent(e.shop, { link: false }) : 'なし', 'tdListShop()', { emptyv: !e.shop })}</div>`;
}
function tdListShop() {
  pick({ title: '既定の店', cur: TDL.shop, none: { label: 'なし' }, search: true, items: entsOfType('place').map(id => ({ k: id, label: E(id).n, sub: E(id).types[0] ? typePath(E(id).types[0]) : '', lead: avatar(id) })), on: k => { TDL.shop = k || null; setSheetBody(tdListBody()); } });
}
function tdListSave() {
  const e = TDL, rs = [];
  if (e.n !== e.n0) { const r = listRename(e.id, e.n); if (!r.ok) return done(r); rs.push(r); }
  if (e.shop !== e.shop0) { const r = listSetShop(e.id, e.shop); if (!r.ok) { rs.reverse().forEach(x => x.undo()); return done(r); } rs.push(r); }
  if (!rs.length) return closeSheet(true);
  const res = { ok: true, label: rs[0].label, undo() { rs.slice().reverse().forEach(x => x.undo()); } };
  if (!done(res, { close: 1, quiet: 1 })) return;
  tdFix(); tdListsRefresh();
  toast(res.label, { undo: () => { res.undo(); tdFix(); tdListsRefresh(); } });
}
function tdListsRefresh(focus) { const i = $('#tdl-in'); if (!i) return; i.closest('.sheet-b').innerHTML = tdListsBody(); if (focus) { const n = $('#tdl-in'); n && n.focus({ preventScroll: true }); } }
function tdListDone(res, msg) {
  if (!done(res, { quiet: 1 })) return false;
  tdFix(); tdListsRefresh(true);
  toast(msg, { undo: () => { res.undo(); tdFix(); tdListsRefresh(); } });
  return true;
}
function tdListAdd() { const v = val('tdl-in'); tdListDone(listAdd(v), `「${v.trim()}」を足しました`); }
function tdListDel(k) { const n = tdListName(k); tdListDone(listRemove(k), `「${n}」を消しました`); }
