/* =====================================================================
   ホーム(#/home) — 接頭辞 hm_(関数)/ hm-(CSS)。見た目は css/screens/home.css。
   上から: 日付の大見出し / 今日の流れ(塗りのカード) / 記録待ち / 支出 / ToDo / 未精算 / すぐメモ
   v4 から進化: 比較は「先週の◯曜」(曜日を計算)、終日の予定を出す、棒は7日、0円と少額を区別、
   すぐメモはピン留めを先に(編集・ピン留め・削除は memo.js の mm_ を使う)、日記は今日の日記の登録と同じシート。
   ===================================================================== */

const hmMin = s => { const [h, m] = String(s).split(':').map(Number); return h * 60 + m };
/** 「あと 1時間40分」(次の予定の札) */
const hmLeft = s => { const df = hmMin(s) - hmMin(NOW); return 'あと ' + (df < 60 ? df + '分' : Math.floor(df / 60) + '時間' + (df % 60 ? (df % 60) + '分' : '')) };
/** 日付を n 日ずらす(ローカル時刻で計算。UTC のずれを避ける) */
const hmAdd = (d, n) => { const t = new Date(d + 'T00:00'); t.setDate(t.getDate() + n); const p = x => String(x).padStart(2, '0'); return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())}` };
/** その日の日記のキー(無ければ null) */
const hmDayKey = d => Object.keys(A).find(k => A[k].type === '日記' && A[k].date === d) || null;
/** 日記を探し、無ければ作る(記録にする・日記の保存で使う) */
function hmDayFor(d) {
  let k = hmDayKey(d);
  if (!k) { k = uid('d'); A[k] = {type: '日記', date: d, dd: String(+d.slice(8)), dw: dowJ(d), body: '', kids: []} }
  return k;
}
/** 予定が記録になっていて、その記録の日が d か */
const hmRecorded = (e, d) => !!(e.rec && A[e.rec] && dateOf(A[e.rec]) === d);
/** 今日の予定(休日の札と記録済みは除く) */
const hmPlans = () => evOn(TODAY).filter(([k, e]) => !e.hol && !hmRecorded(e, TODAY));
/** いまより後で一番近い時刻のある予定(次の予定の大きな札) */
const hmNext = () => hmPlans().filter(([k, e]) => !e.all && e.s > NOW).sort((a, b) => a[1].s.localeCompare(b[1].s))[0] || null;
/** 終わった時刻が いま より前の予定(記録待ち) */
const hmPast = () => hmPlans().filter(([k, e]) => !e.all && e.e && e.e <= NOW);
/** 今日までの未完了の ToDo(期限の古い順) */
const hmTodoKeys = () => Object.keys(TD).filter(k => !TD[k].done && TD[k].due && TD[k].due <= TODAY).sort((a, b) => TD[a].due.localeCompare(TD[b].due));

/* ---------- 1. 見出し: 大きな日付 + 日記の導線 ---------- */
function hmHead() {
  const dk = hmDayKey(TODAY), has = !!(dk && A[dk].body);
  const pill = has
    ? `<a class="hm-pill" href="#/day/${TODAY}">日記を開く</a>`
    : `<button class="hm-pill" type="button" onclick="hmDiary(TODAY)">日記を書く</button>`;
  return `<header class="hm-head"><div><h1 class="hm-date num">${+TODAY.slice(5, 7)}月${+TODAY.slice(8, 10)}日</h1>`
    + `<p class="hm-head-wd">${dowJ(TODAY)}曜</p></div>${pill}</header>`;
}

/* ---------- 2. 今日の流れ(塗りのカード。上=過去・下=未来) ---------- */
/** 記録の行。過去は塗り、これからは中抜き */
function hmRecRow(k, a, past) {
  const t = a.type === '支払い' ? payLabel(a) : (a.title || '記録');
  return `<a class="hm-tr ${past ? 'is-past' : 'is-fut'}" href="#/a/${esc(k)}">`
    + `<span class="hm-tc num">${esc(a.time)}</span><span class="hm-rl" aria-hidden="true"><i class="hm-nd"></i></span>`
    + `<span class="hm-c"><span class="hm-ct">${esc(t)}</span>${a.end ? `<span class="hm-cs num">${esc(timeText(a))}</span>` : ''}</span></a>`;
}
/** 終日の予定(時刻の列は「終日」) */
function hmAllDayRow(k, e) {
  return `<a class="hm-tr is-plan" href="#/ev/${esc(k)}"><span class="hm-tc num">終日</span>`
    + `<span class="hm-rl" aria-hidden="true"><i class="hm-nd is-plan"></i></span>`
    + `<span class="hm-c"><span class="hm-ct">${esc(e.t)}</span></span></a>`;
}
/** いまの線(時刻の列に「いま」、横の赤い線) */
const hmNowRow = () => `<div class="hm-tr hm-now" role="separator" aria-label="いま ${esc(NOW)}">`
  + `<span class="hm-tc num"><b class="hm-nt">いま</b>${esc(NOW)}</span><span class="hm-rl" aria-hidden="true"></span>`
  + `<span class="hm-c"><span class="hm-line" aria-hidden="true"></span></span></div>`;
/** これからの予定(小さい行・点線の軸)。開始を過ぎて終わる前なら「進行中」 */
function hmPlanRow(k, e) {
  const ing = e.s <= NOW && e.e > NOW;
  return `<a class="hm-tr is-plan" href="#/ev/${esc(k)}"><span class="hm-tc num">${esc(e.s)}</span>`
    + `<span class="hm-rl" aria-hidden="true"><i class="hm-nd is-plan"></i></span>`
    + `<span class="hm-c"><span class="hm-ct is-soft">${esc(e.t)}</span>`
    + `<span class="hm-cs num">${esc(e.s)}–${esc(e.e)}${ing ? '<b class="hm-ing">進行中</b>' : ''}</span></span></a>`;
}
/** 次の予定: 白い半透明の札(点線の枠)。場所と同席者も出す */
function hmNextRow(k, e) {
  const ppl = (e.who || []).filter(w => E[w]);
  const place = placeOf(e);
  const meta = place || ppl.length
    ? `<span class="hm-nx-meta">${place ? `<span class="hm-mi">${icon('place')}${esc(place)}</span>` : ''}${ppl.map(w => `<span class="hm-mi"><i class="hm-dot" aria-hidden="true"></i>${esc(E[w].n)}</span>`).join('')}</span>`
    : '';
  return `<div class="hm-tr is-plan hm-next-row"><span class="hm-tc num">${esc(e.s)}</span>`
    + `<span class="hm-rl" aria-hidden="true"><i class="hm-nd is-plan"></i></span>`
    + `<span class="hm-c"><a class="hm-next" href="#/ev/${esc(k)}">`
    + `<span class="hm-nx-top"><span class="hm-lbl">次の予定</span><b class="hm-left num">${esc(hmLeft(e.s))}</b></span>`
    + `<span class="hm-nx-t">${esc(e.t)}</span><span class="hm-nx-time num">${esc(e.s)}–${esc(e.e)}</span>${meta}`
    + `</a></span></div>`;
}
function hmTimeline() {
  const dk = hmDayKey(TODAY);
  const recs = dk ? kidActs(dk).map(k => [k, A[k]]).filter(([k, a]) => a.time).sort((x, y) => x[1].time.localeCompare(y[1].time)) : [];
  const past = recs.filter(([k, a]) => a.time <= NOW), fut = recs.filter(([k, a]) => a.time > NOW);
  const allDay = hmPlans().filter(([k, e]) => e.all);
  const plans = hmPlans().filter(([k, e]) => !e.all && e.s && e.e > NOW).sort((x, y) => x[1].s.localeCompare(y[1].s));
  if (!past.length && !fut.length && !plans.length && !allDay.length) return empty({shape: 'day', text: '今日の予定 なし'});
  const nk = (hmNext() || [null])[0];
  const rows = allDay.map(([k, e]) => hmAllDayRow(k, e));
  const more = past.length - 3;
  if (more > 0) rows.push(`<div class="hm-tr hm-more-r"><span class="hm-tc"></span><span class="hm-rl" aria-hidden="true"></span><a class="hm-more-l" href="#/day/${TODAY}">これより前 ${more}件</a></div>`);
  past.slice(-3).forEach(([k, a]) => rows.push(hmRecRow(k, a, true)));
  rows.push(hmNowRow());
  /* いまより後は、記録と予定を開始時刻の順に1本へ混ぜる(記録だけ先に出さない) */
  const later = [
    ...fut.map(([k, a]) => ({t: a.time, h: hmRecRow(k, a, false)})),
    ...plans.map(([k, e]) => ({t: e.s, h: k === nk ? hmNextRow(k, e) : hmPlanRow(k, e)})),
  ].sort((x, y) => x.t.localeCompare(y.t));
  later.forEach(x => rows.push(x.h));
  return `<div class="hm-tl">${rows.join('')}</div>`;
}
function hmMain() {
  return `<section class="hm-main"><div class="hm-hd"><h2 class="hm-hh">今日の流れ</h2><a class="hm-more" href="#/activities">アクティビティ ›</a></div>${hmTimeline()}</section>`;
}

/* ---------- 3. 記録待ち(終わったのに記録になっていない予定) ---------- */
function hmPending() {
  const xs = hmPast();
  if (!xs.length) return '';
  return `<section class="hm-sec">${sh('記録待ち', {count: xs.length + '件'})}<div class="hm-pend-l">`
    + xs.map(([k, e]) => `<div class="hm-pr"><span class="hm-pr-cap" aria-hidden="true"></span>`
      + `<span class="hm-pr-b"><span class="hm-pr-t">${esc(e.t)}</span><span class="hm-pr-s num">${esc(e.s)}–${esc(e.e)}</span></span>`
      + `<button class="p-btn p-btn-fill" type="button" onclick="hmRecord('${esc(k)}')">記録にする</button></div>`).join('')
    + `</div></section>`;
}
/** 予定を記録にする(v4 と同じ作り方。支払いの予定は支払いと自分の支出にする) */
function hmRecord(id) {
  const e = EV[id];
  if (!e) return;
  if (e.rec && A[e.rec]) return go('#/a/' + e.rec);
  const day = hmDayFor(e.d);
  const placeId = e.place ? e.place[0] : null;
  const placeName = e.place ? (e.place[0] ? E[e.place[0]].n : e.place[1]) : '';
  let aid;
  if (e.pay) {
    aid = makePayment({parent: day, time: e.s || '09:00', items: [{n: e.t, price: e.pay.amt, users: ['me'], tax: 10}],
      payee: [placeId, placeName || e.t], roles: (e.who || []).map(w => ['同席', w]), how: 'クレジットカード ••1234', cat: e.pay.cat});
  } else {
    aid = uid('c');
    const roles = (e.who || []).map(w => ['参加', w]);
    if (e.place) roles.push(['場所', placeId, placeName]);
    const span = !e.all && e.s && e.e ? {time: e.s, end: e.e} : {time: e.s || '09:00'};
    A[aid] = {type: '出来事', parent: day, ...span, title: e.t, roles, body: '', kids: []};
    A[day].kids.push(aid);
  }
  e.rec = aid;
  save();
  go('#/a/' + aid);
  toast('記録にしました');
}

/* ---------- 4. 支出(今日の額・先週の同じ曜日との差・7日の棒・その場で登録) ---------- */
/** 1日の支出(自分の分)。固定費(家賃・通信・サブスクなど)は除く */
const hmSpendOn = d => Object.values(X).reduce((s, x) => {
  const a = A[x.pay];
  if (!a || xFx(x) === '固定') return s;
  return dateOf(a) === d ? s + xAmt(x) : s;
}, 0);
const hmSpendRange = (from, to) => { let s = 0; for (let d = from; d <= to; d = hmAdd(d, 1)) s += hmSpendOn(d); return s };
function hmMoney() {
  const days = []; for (let i = -6; i <= 0; i++) days.push(hmAdd(TODAY, i));
  const vals = days.map(hmSpendOn);
  const max = Math.max(...vals, 1);
  const cols = days.map((d, i) => {
    const h = vals[i] > 0 ? Math.max(4, Math.round(vals[i] / max * 96)) : 0;
    const isT = i === days.length - 1;
    return `<div class="hm-col${isT ? ' is-today' : ''}" role="img" aria-label="${esc(fmtMDW(d))} ${esc(yen(vals[i]))}">`
      + `<span class="hm-bar" style="height:${h}px"></span><span class="hm-cd">${isT ? '今日' : dowJ(d)}</span></div>`;
  }).join('');
  const today = vals[vals.length - 1];
  const sameDay = hmAdd(TODAY, -7), pastSame = hmSpendOn(sameDay), diff = today - pastSame;
  const diffTxt = diff === 0 ? '±0' : (diff > 0 ? '+' : '−') + yen(Math.abs(diff));
  const thisW = hmSpendRange(hmAdd(TODAY, -6), TODAY), prevW = hmSpendRange(hmAdd(TODAY, -13), hmAdd(TODAY, -7));
  const rate = prevW ? Math.round((thisW - prevW) / prevW * 100) : null;
  return `<section class="hm-sec hm-money"><div class="hm-mh"><h2 class="hm-mh-t">支出</h2>`
    + `<span class="hm-excl">固定費(家賃・通信・サブスク)を除く</span><a class="hm-more" href="#/ledger">家計簿 ›</a></div>`
    + `<div class="hm-today"><div><span class="hm-k">今日</span><b class="hm-big num">${esc(yen(today))}</b></div>`
    + `<div class="hm-cmp"><span class="hm-sub num">先週の${dowJ(sameDay)}曜 ${esc(yen(pastSame))}</span>${tag(diffTxt)}</div></div>`
    + `<div class="hm-chart" role="group" aria-label="この7日間の支出">${cols}</div>`
    + `<div class="hm-wk"><div><span class="hm-k">この7日間 ${md(hmAdd(TODAY, -6))}〜${md(TODAY)}</span><b class="hm-amt num">${esc(yen(thisW))}</b></div>`
    + `<div class="hm-cmp"><span class="hm-sub num">前の7日間 ${esc(yen(prevW))}</span>${rate == null ? '' : tag(`${rate >= 0 ? '+' : '−'}${Math.abs(rate)}%`)}</div></div>`
    + `<form class="hm-qe" onsubmit="hmQeGo();return false"><input id="hm-qe-amt" class="p-input num" inputmode="numeric" placeholder="¥ 金額" aria-label="金額" autocomplete="off">`
    + `<button class="p-btn p-btn-fill" type="submit" aria-label="支払いを記録へ">次へ</button></form>`
    + `</section>`;
}
/** 入力した金額を持って「支払いを記録」へ。金額が空でもシートは開く */
function hmQeGo() {
  const el = document.getElementById('hm-qe-amt');
  const v = String(el ? el.value : '').replace(/[^\d]/g, '');
  const c = CREATES.find(x => x.label === '支払いを記録');
  if (!c || !c.run) return toast('支払いを記録を開けませんでした');
  c.run({amt: v && +v > 0 ? +v : 0, stay: true});
}

/* ---------- 5. ToDo(今日までの未完了。最大3件・件数は全件) ---------- */
function hmTodoDone(k) {
  const r = todoToggle(k);
  if (!r.ok) return toast(r.why);
  toast('完了にしました', {undo: () => undoRun(r.undo)});
  rerender();
}
function hmTodo() {
  const ks = hmTodoKeys(), n = todoDueCount(TODAY);
  const rows = ks.slice(0, 3).map(k => {
    const t = TD[k], over = t.due < TODAY, linked = t.ev && EV[t.ev] ? EV[t.ev].t : '';
    return `<div class="hm-td">${checkbox(false, {click: `hmTodoDone('${esc(k)}')`, label: `${t.t}を完了にする`})}`
      + `<a class="hm-td-b" href="#/task/${esc(k)}"><span class="hm-td-t">${esc(t.t)}${t.pri ? '<b class="hm-pri" aria-label="優先">!</b>' : ''}</span>`
      + `<span class="hm-td-s">${over ? tag('期限切れ', {kind: 'late'}) : ''}<span>${esc(relDay(t.due))}</span>${linked ? `<span class="ell">${esc(linked)}</span>` : ''}</span></a></div>`;
  }).join('');
  return `<section class="hm-sec">${sh('ToDo', {count: n + '件', act: {label: 'すべて ›', on: "go('#/todo')"}})}`
    + `<div class="hm-panel">${rows || empty({shape: 'todo', text: '今日までのToDo なし'})}</div></section>`;
}

/* ---------- 6. 未精算(丸ごと押せて家計簿へ) ---------- */
function hmOwe() {
  const ob = openBalances();
  const lend = ob.filter(([, , b]) => b.amt > 0), borrow = ob.filter(([, , b]) => b.amt < 0);
  const part = (xs, label) => {
    const sum = xs.reduce((s, [, , b]) => s + Math.abs(b.amt), 0);
    const ppl = [...new Set(xs.flatMap(([, , b]) => b.with))];
    /* 金額は本文の太さ(強さの順位3位)。琥珀は「貸し/借り」のタグだけ */
    return `<span class="hm-ow-l">${tag(label, {kind: 'owe'})}<b class="hm-ow-amt num">${esc(yen(sum))}</b>`
      + (ppl.length ? `<span class="hm-ow-p">${ppl.map(w => `<span class="p-ref"><span class="p-dot" aria-hidden="true"></span>${esc(E[w].n)}</span>`).join('')}</span>` : '')
      + `</span>`;
  };
  return `<section class="hm-sec">${sh('未精算')}<a class="p-obj hm-owe" href="#/ledger">`
    + (ob.length ? '' : `<span class="hm-none-l">なし</span>`)
    + (lend.length ? part(lend, '貸し') : '') + (borrow.length ? part(borrow, '借り') : '')
    + `<span class="p-chev">${icon('chev')}</span></a></section>`;
}

/* ---------- 7. すぐメモ(入力+保存。最新1件。ピン留めを先に) ---------- */
function hmMemoSave() {
  const el = document.getElementById('hm-memo');
  const body = (el ? el.value : '').trim();
  if (!body) { toast('メモを入れてください'); return }
  const id = memoAdd(body);
  save();
  rerender();
  toast('メモを保存しました', {undo: () => { memoRemove(id) }});
}
function hmMemo() {
  const top = memosSorted()[0];
  return `<section class="hm-sec">${sh('すぐメモ', {act: {label: 'すべて ›', on: "go('#/memo')"}})}`
    + `<form class="hm-memo-in" onsubmit="hmMemoSave();return false"><input id="hm-memo" class="p-input" placeholder="思いついたことを書く" aria-label="すぐメモ" autocomplete="off">`
    + `<button class="p-btn p-btn-fill" type="submit">保存</button></form>`
    + (top ? mmCard(top[0], {lines: 2, withDate: true}) : '')
    + `</section>`;
}

/* ---------- 日記(今日の日記・日の日記の保存) ---------- */
let hmDy = null;   /* 日記のシートの作業中の値 {d, body, orig} */
function hmDiary(d) {
  const k = hmDayKey(d), orig = k ? (A[k].body || '') : '';
  hmDy = {d, body: orig, orig};
  openSheet({
    title: d === TODAY ? '今日の日記' : '日記',
    body: `<p class="hm-dy-d num">${esc(fmtMDW(d))}</p>`
      + `<div class="hm-dy"><textarea class="hm-dy-t" rows="8" aria-label="日記" placeholder="今日はどんな日だった?" oninput="hmDy.body=this.value">${esc(orig)}</textarea></div>`,
    primary: {label: '日記を保存', on: () => hmDiarySave()},
    dirty: () => !!hmDy && hmDy.body !== hmDy.orig,
  });
}
function hmDiarySave() {
  const id = hmDayFor(hmDy.d);
  A[id].body = hmDy.body;
  hmDy = null;
  save();
  closeSheet(true);
  rerender();
  toast('日記を保存しました');
}

/* 作成(＋)の登録: 今日の日記 */
addCreate({key: 'diary', label: '今日の日記', icon: 'diary', run: () => hmDiary(TODAY)});

route('home', () => `<div class="hm-root">`
  + hmHead() + hmMain() + hmPending() + hmMoney() + hmTodo() + hmOwe() + hmMemo()
  + `</div>`, {tab: 'home'});
