/* 個人記録 v6 — ホーム(いま)・すばやく書く(＋)・日記のシート */
'use strict';

/* ================= ホーム ================= */
route('home', () => {
  const up = upcoming(0).filter(([, a]) => a.start && !a.allDay && a.start > NOW)[0];
  const allDay = actsOn(TODAY).filter(([, a]) => a.allDay && !a.hol);
  const hol = actsOn(TODAY).find(([, a]) => a.hol);
  const doing = actsOn(TODAY).find(([, a]) => a.start && a.end && a.start <= NOW && NOW < a.end);
  return `<div class="screen s-home">
    ${head(fmtMDJ(TODAY), { sub: `${dowJ(TODAY)}曜${hol ? ' · ' + esc(hol[1].title) : ''}`, actions: iconBtn('search', 'さがす', "go('#/search')", 'on-card') + iconBtn('pencil', dayOf(TODAY).diary ? '今日の日記を開く' : '今日の日記を書く', `openDiary(${q(TODAY)})`, 'on-card') })}
    <section class="card now-card">
      <div class="now-h"><div class="now-t"><span>いま</span><b class="num">${NOW}</b></div>${doing ? `<a class="now-doing" href="#/a/${doing[0]}">${kindIc(doing[1])}<span>${esc(actTitle(doing[1]))}</span></a>` : ''}<a class="sec-a" href="#/day/${TODAY}">今日のページ</a></div>
      ${skyBand(TODAY)}
      ${allDay.length ? `<div class="ents">${allDay.map(([id, a]) => `<a class="tag plan" href="#/a/${id}">${esc(actTitle(a))}</a>`).join('')}</div>` : ''}
      ${up ? hmNext(up) : `<div class="now-none">${ic('check')}<span>今日の予定は ここまで</span></div>`}
      ${hmFlow()}
    </section>
    <button class="quick" onclick="openCapture()">${ic('plus')}<span>書く・記録する</span><kbd>ランチ 1200</kbd></button>
    ${hmReview()}
    ${hmMoney()}
    ${hmTodo()}
    ${hmRecall()}
    ${hmMemo()}
  </div>`;
}, { tab: 'home', root: 1 });

/** 今日の流れ: 帯の中身を言葉で(時刻順。直近の3件と、これからの予定。前は畳む) */
let HM_ALL = false;
function hmFlow() {
  const items = actsOn(TODAY).filter(([, a]) => !a.hol && !a.allDay && a.start); if (!items.length) return '';
  const past = items.filter(([, a]) => !isPlan(a)), rest = items.filter(([, a]) => isPlan(a));
  const hide = HM_ALL ? 0 : Math.max(0, past.length - 3), shown = [...past.slice(hide), ...rest].sort(byTime);
  const line = ([id, a]) => { const st = isPlan(a) ? (isLate(a) ? 'late' : 'plan') : 'done'; return `<a class="fl ${st}${isSpan(a) ? ' span' : ''}" href="#/a/${id}"><time class="num">${a.start}</time><i></i><b>${esc(actTitle(a))}</b>${isPay(a) && !isPlan(a) ? amt(xOf(id) ? xAmt(id) : payTotal(a)) : a.end ? `<span class="num">–${a.end}</span>` : ''}</a>`; };
  return `<div class="flow">${hide ? `<button class="fl-more" onclick="HM_ALL=true;rerender()">これより前 ${hide}件</button>` : ''}${shown.map(line).join('')}</div>`;
}
/** 次の予定(点線の札)。残り時間を大きく */
function hmNext([id, a]) {
  const left = hm2m(a.start) - hm2m(NOW), tasks = tasksOfAct(id).filter(([, t]) => taskOpen(t));
  return `<div class="next"><a class="hit" href="#/a/${id}" aria-label="次の予定 ${esc(actTitle(a))}"></a>
    <div class="next-l"><span>次</span><b class="num">${a.start}</b></div>
    <div class="next-m"><b>${esc(actTitle(a))}</b><div class="ents">${roleEnts(a, { link: false })}${tasks.length ? `<span class="mini">${ic('task')}${tasks.length}</span>` : ''}</div></div>
    <div class="next-r"><span>あと</span><b>${durJ(left)}</b></div></div>`;
}
/** 確かめる: 承認するだけで片づくもの(記録待ちの予定・読み取ったままの支払い・未精算) */
function hmReview() {
  const pend = pendingPlans(), unread = Object.entries(DB.acts).filter(([, a]) => unreadCount(a)), bals = personBalances();
  const n = pend.length + unread.length + bals.length; if (!n) return '';
  const lend = bals.filter(b => b.amt > 0).reduce((t, b) => t + b.amt, 0), owe = bals.filter(b => b.amt < 0).reduce((t, b) => t - b.amt, 0);
  return `<section>${sec('確かめる', { n })}<div class="rv">
    ${pend.map(([id, a]) => `<div class="rv-i rv-plan"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a><span class="rv-ic">${kindIc(a)}</span><div class="rv-m"><b>${esc(actTitle(a))}</b><span class="num">${a.date === TODAY ? '' : relDay(a.date) + ' '}${timeText(a)}</span></div>${btn('記録にする', `recordPlan(${q(id)})`, 'pri', { sm: 1 })}</div>`).join('')}
    ${unread.map(([id, a]) => `<div class="rv-i rv-read"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a><span class="rv-ic">${ic('receipt')}</span><div class="rv-m"><b>${readMark(esc(actTitle(a)))}</b><span>読み取った値 ${unreadCount(a)}件 · ${yen(payTotal(a))}</span></div>${btn('確かめる', `go('#/a/${id}')`, 'sec', { sm: 1 })}</div>`).join('')}
    ${bals.map(b => `<div class="rv-i rv-owe"><a class="hit" href="#/ledger?v=owe" aria-label="貸し借り"></a><span class="rv-ic">${ic('swap')}</span><div class="rv-m"><b>${esc(whoShort(b.who))}${E(b.who) ? 'さん' : ''}に${b.amt > 0 ? '貸し' : '借り'}</b><span>${b.ids.length}件 · 未精算</span></div><span class="rv-a">${amt(Math.abs(b.amt))}</span>${btn('精算済み', `hmSettle(${q(b.who)})`, 'sec', { sm: 1 })}</div>`).join('')}
  </div>${lend && owe ? `<p class="rv-sum num">貸し ${yen(lend)} · 借り ${yen(owe)} · 差し引き ${lend >= owe ? '+' : '−'}${yen(Math.abs(lend - owe)).slice(1)}</p>` : ''}</section>`;
}
function hmSettle(who) { const b = personBalances().find(x => x.who === who); if (b) done(paySettle(b.ids)); }
/** 今日のお金: 固定費を除く。先週の同じ曜日と、この7日 */
function hmMoney() {
  const t = daySpend(TODAY, { noFixed: 1 }), lw = dAdd(TODAY, -7), l = daySpend(lw, { noFixed: 1 });
  const days = [...Array(7)].map((_, i) => dAdd(TODAY, i - 6)), vals = days.map(d => daySpend(d, { noFixed: 1 })), max = Math.max(1, ...vals);
  const w0 = vals.reduce((s, v) => s + v, 0), w1 = [...Array(7)].reduce((s, _, i) => s + daySpend(dAdd(TODAY, i - 13), { noFixed: 1 }), 0);
  const pct = w1 ? Math.round((w0 - w1) / w1 * 100) : null;
  return `<section>${sec('今日の支出', { act: { label: '家計簿', href: '#/ledger' } })}<div class="card pad money">
    <div class="money-h"><div>${amt(t, { cls: 'hero' })}<span class="tag">固定費を除く</span></div>
      <div class="money-c"><span>先週の${dowJ(lw)}曜 ${yen(l)}</span><b class="${t > l ? 'up' : t < l ? 'dn' : ''}">${t === l ? '±0' : (t > l ? '+' : '−') + yen(Math.abs(t - l)).slice(1)}</b></div></div>
    <div class="wk" role="img" aria-label="この7日の支出">${days.map((d, i) => `<a class="wk-c${d === TODAY ? ' on' : ''}" href="#/day/${d}"><span class="wk-v num">${vals[i] ? (vals[i] >= 10000 ? (vals[i] / 10000).toFixed(1) + '万' : vals[i].toLocaleString()) : ''}</span><i style="height:${vals[i] ? Math.max(4, Math.round(vals[i] / max * 72)) : 0}px"></i><span class="wk-d">${d === TODAY ? '今日' : dowJ(d)}</span></a>`).join('')}</div>
    <div class="money-f"><span>この7日 <b class="num">${yen(w0)}</b></span>${pct == null ? '' : `<span>前の7日より <b class="num">${pct > 0 ? '+' : ''}${pct}%</b></span>`}${btn('支払いを記録', 'openPayEditor()', 'sec', { sm: 1, icon: 'yen' })}</div>
  </div></section>`;
}
function hmTodo() {
  const due = tasksDue().sort((a, b) => a[1].due.localeCompare(b[1].due));
  return `<section>${sec('今日までのToDo', { n: due.length, act: { label: 'すべて', href: '#/todo' } })}${due.length ? rows(due.slice(0, 4).map(([id]) => taskRow(id)).join('') + (due.length > 4 ? row({ t: `ほか ${due.length - 4}件`, href: '#/todo', chev: 1 }) : '')) : `<div class="card">${empty({ icon: 'task', text: '今日までのToDoは ありません', action: { label: 'ToDoを追加', on: 'openTaskEditor()' } })}</div>`}</section>`;
}
/** 思い出す: 1年前・1か月前・先週の同じ日 */
function hmRecall() {
  const r = recallDays(); if (!r.length) return '';
  return `<section>${sec('思い出す')}<div class="recall">${r.map(x => { const dy = dayOf(x.date), acts = actsOn(x.date, { state: 'done' }).filter(([, a]) => !isPay(a) || true); const ph = (dy.photos || [])[0];
    return `<a class="rc" href="#/day/${x.date}" style="--c:${ph ? skyAt(ph.t) : skyAt('12:00')}"><span class="rc-l">${esc(x.label)}</span><b>${esc(dy.diary || acts.slice(0, 2).map(([, a]) => actTitle(a)).join('、') || '記録あり')}</b><span class="rc-d num">${x.date.slice(0, 4)}年${fmtMDJ(x.date)} ${dowJ(x.date)}曜${(dy.photos || []).length ? ` · 写真 ${dy.photos.length}` : ''}</span>${skyBand(x.date, { mini: 1 })}</a>`; }).join('')}</div></section>`;
}
function hmMemo() {
  const ms = memosSorted().slice(0, 2);
  return `<section>${sec('メモ', { act: { label: 'すべて', href: '#/memo' } })}${ms.length ? `<div class="stack">${ms.map(([id]) => memoCard(id, { clamp: 3 })).join('')}</div>` : `<div class="card">${empty({ icon: 'memo', text: 'メモは まだありません', action: { label: 'メモを書く', on: "openCapture('memo')" } })}</div>`}</section>`;
}

/* ================= すばやく書く(＋) =================
   1行書くと種類を推し量り、解釈を見せる(違えば種類を押して直す)。下に、種類ごとの詳しい入力への入口。 */
let CAP = null;
const CAP_TYPES = [['pay', '支払い', 'yen'], ['plan', '予定', 'cal'], ['task', 'ToDo', 'task'], ['memo', 'メモ', 'memo']];
/** o = {text}(書き出しの文字。例: エンティティの名前を入れて開く) */
function openCapture(force, o = {}) {
  CAP = { text: o.text || '', force: force || null };
  openSheet({ id: 'capture', title: '書く・記録する', body: capBody(), dirty: () => !!CAP.text.trim(), primary: { label: '保存', on: capSave, disabled: true } });
  if (CAP.text) capRefresh();
}
function capBody() {
  return `<div class="cap-in">${textarea({ id: 'cap-t', rows: 2, ph: 'ランチ 1200 / 明日 10時 歯医者 / 牛乳 #買い物 / 思いついたこと', oninput: 'capInput(this.value)', auto: 1, label: '書く', value: CAP.text })}</div>
    <div id="cap-pv">${capPreview()}</div>
    <div class="cap-grid">${[['yen', '支払い', 'openPayEditor()'], ['camera', 'レシートを読み取る', 'openReceipt()'], ['flag', '出来事', "openActEditor({state:'done'})"], ['cal', '予定', "openActEditor({state:'plan'})"], ['task', 'ToDo', 'openTaskEditor()'], ['memo', 'メモ', "capForce('memo')"], ['people', 'エンティティ', 'openEntEditor()'], ['pencil', '今日の日記', `openDiary(${q(TODAY)})`]]
      .map(([i, l, on]) => `<button class="cap-g" onclick="capOpen(${q(on)})">${ic(i)}<span>${l}</span></button>`).join('')}</div>`;
}
const capParsed = () => { const p = parseQuick(CAP.text); if (!p) return null; if (CAP.force && CAP.force !== p.type) return capAs(p, CAP.force); return p; };
/** 解釈を別の種類に読み替える */
function capAs(p, type) {
  const raw = CAP.text.trim(), base = { ents: p.ents || [] };
  if (type === 'memo') return { type, body: raw, ...base };
  if (type === 'task') return { type, ...parseTask(raw), ...base };
  if (type === 'plan') return { type, title: p.title || p.text || p.t || raw, date: p.date && p.date >= TODAY ? p.date : (p.due || TODAY), start: p.start && p.type === 'plan' ? p.start : undefined, end: p.end, ...base };
  return { type: 'pay', amt: p.amt || 0, date: TODAY, start: NOW, payee: p.text ? { name: p.text } : null, text: p.text || '', ...base };
}
function capPreview() {
  const p = CAP.text.trim() ? capParsed() : (CAP.force ? { type: CAP.force } : null);
  if (!p) return `<div class="cap-hint">${CAP_TYPES.map(([k, l, i]) => `<span>${ic(i)}${l}</span>`).join('')}</div>`;
  let body = '';
  if (p.type === 'pay') { const cat = catGuess(p.payee, p.text); body = `<div class="slip"><div class="slip-h"><b>${esc(p.payee ? (p.payee.e ? E(p.payee.e).n : p.payee.name) : '支払い')}</b>${amt(p.amt)}</div><div class="slip-s">${catTag(cat)}<span>${relDay(p.date)} ${p.start}</span></div></div>${p.amt ? '' : banner('金額を入れてください(例: ランチ 1200)')}`; }
  else if (p.type === 'plan') body = `<div class="ev plan"><div class="ev-h"><span class="ev-k">${ic('cal')}予定</span><span class="ev-d num">${relDay(p.date)} ${fmtMD(p.date)}(${dowJ(p.date)}) ${p.start ? p.start + (p.end ? '–' + p.end : '') : '終日'}</span></div><b class="ev-t">${esc(p.title || '')}</b>${p.ents.length ? `<div class="ents">${p.ents.map(e => ent(e, { link: false })).join('')}</div>` : ''}</div>`;
  else if (p.type === 'task') body = `<div class="rows"><div class="task">${`<span class="chk"></span>`}<div class="task-m"><b>${p.pri ? '<i class="pri">!</i>' : ''}${esc(p.t || '')}</b><span class="task-s">${p.due ? tag(`${relDay(p.due)} ${fmtMD(p.due)}`, 'me') : ''}<span class="mini">${esc(DB.lists[p.list || Object.keys(DB.lists)[0]].n)}</span>${(p.ents || []).map(e => ent(e, { link: false, short: 1 })).join('')}</span></div></div></div>`;
  else body = `<article class="memo"><p>${esc(p.body || '')}</p><footer><time class="num">${NOW}</time>${(p.ents || []).map(e => ent(e, { link: false, short: 1 })).join('')}</footer></article>`;
  return `<div class="cap-as">${chips(CAP_TYPES.map(([k, l]) => [k, l]), p.type, 'capForce')}</div>${body}`;
}
const CAP_LABEL = { pay: '支払いを記録', plan: '予定を追加', task: 'ToDoを追加', memo: 'メモを保存' };
function capRefresh() { $('#cap-pv').innerHTML = capPreview(); const p = CAP.text.trim() ? capParsed() : null; setSheetPrimary({ label: p ? CAP_LABEL[p.type] : '保存', on: capSave, disabled: !p || (p.type === 'pay' && !p.amt) }); }
function capInput(v) { CAP.text = v; capRefresh(); }
function capForce(k) { CAP.force = k; capRefresh(); const t = $('#cap-t'); t && t.focus(); }
/** 詳しい入力へ(書きかけは引き継ぐ) */
function capOpen(on) { const p = CAP.text.trim() ? capParsed() : null; closeSheet(true); window._capSeed = p; try { (0, eval)(on); } finally { window._capSeed = null; } }
function capSave() {
  const p = capParsed(); if (!p) return; let r, goTo;
  if (p.type === 'pay') { r = paySave({ date: p.date, start: p.start, payee: p.payee, amt: p.amt, exp: { basis: 'items', cat: catGuess(p.payee, p.text) } }); }
  else if (p.type === 'plan') { const roles = (p.ents || []).map(e => ({ r: isUnder(E(e).types[0], 'place') ? '場所' : isUnder(E(e).types[0], 'project') ? 'プロジェクト' : '同席', e })); r = actAdd({ kind: 'event', title: p.title, date: p.date, start: p.start, end: p.end, allDay: p.start ? 0 : 1, state: 'plan', roles }); }
  else if (p.type === 'task') r = taskAdd({ t: p.t, due: p.due, list: p.list, pri: p.pri, ents: p.ents });
  else r = memoAdd(p.body, { ents: p.ents });
  if (!r.ok) return done(r);
  closeSheet(true); rerender();
  const href = p.type === 'memo' ? '#/memo' : p.type === 'task' ? `#/task/${r.id}` : `#/a/${r.id}`;
  toast(r.label, { undo: r.undo, act: { label: '開く', on: () => go(href) } });
}

/* ================= 日記のシート(ホーム・日のページ・＋から同じもの) ================= */
function openDiary(d) {
  const cur = dayOf(d).diary || '';
  openSheet({ id: 'diary', title: d === TODAY ? '今日の日記' : '日記', lead: `<span class="tag">${fmtFull(d)}</span>`, body: textarea({ id: 'diary-t', rows: 9, ph: d === TODAY ? '今日はどんな日だった?' : 'この日のことを書く', value: cur, auto: 1, label: '日記' }),
    dirty: () => val('diary-t') !== cur, primary: { label: '日記を保存', on: () => done(daySetDiary(d, val('diary-t')), { close: 1 }) } });
}
