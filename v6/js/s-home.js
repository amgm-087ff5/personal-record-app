/* 個人記録 v6 — ホーム(いま)・日記のシート */
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
    <button class="quick" onclick="openCapture()">${ic('plus')}<span>入力する</span><kbd>ランチ 1200</kbd></button>
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
  const tents = window.tentAll ? tentAll() : [];
  const n = pend.length + unread.length + bals.length + tents.length; if (!n) return '';
  const lend = bals.filter(b => b.amt > 0).reduce((t, b) => t + b.amt, 0), owe = bals.filter(b => b.amt < 0).reduce((t, b) => t - b.amt, 0);
  return `<section>${sec('確かめる', { n })}<div class="rv">
    ${pend.map(([id, a]) => `<div class="rv-i rv-plan"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a><span class="rv-ic">${kindIc(a)}</span><div class="rv-m"><b>${esc(actTitle(a))}</b><span class="num">${a.date === TODAY ? '' : relDay(a.date) + ' '}${timeText(a)}</span></div>${btn('記録にする', `recordPlan(${q(id)})`, 'pri', { sm: 1 })}</div>`).join('')}
    ${unread.map(([id, a]) => `<div class="rv-i rv-read"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a><span class="rv-ic">${ic('receipt')}</span><div class="rv-m"><b>${readMark(esc(actTitle(a)))}</b><span>読み取った値 ${unreadCount(a)}件 · ${yen(payTotal(a))}</span></div>${btn('確かめる', `go('#/a/${id}')`, 'sec', { sm: 1 })}</div>`).join('')}
    ${tents.map(x => {
      const a = x.t === 'acts' ? A(x.k) : null, rc = a ? (isPay(a) ? ic('yen') : kindIc(a)) : ic(x.t === 'tasks' ? 'task' : 'memo');
      const ttl = window.recTitle ? recTitle(x.t, x.row) : '', d = x.row.date || x.row.due || (x.row.at ? x.row.at.slice(0, 10) : '');
      return `<div class="rv-i rv-tent"><button class="hit" onclick="window.openInbox && openInbox({focus:{t:${q(x.t)},k:${q(x.k)}}})" aria-label="${esc(ttl)}"></button><span class="rv-ic">${rc}</span><div class="rv-m"><b>${readMark(esc(ttl))}</b><span>未確定${d ? ` · ${esc(relDay(d))}` : ''}</span></div>${btn('確定', `window.tentConfirm && done(tentConfirm({t:${q(x.t)},k:${q(x.k)}}))`, 'sec', { sm: 1 })}</div>`;
    }).join('')}
    ${bals.map(b => `<div class="rv-i rv-owe"><a class="hit" href="#/ledger?v=owe" aria-label="貸し借り"></a><span class="rv-ic">${ic('swap')}</span><div class="rv-m"><b>${esc(whoShort(b.who))}${E(b.who) ? 'さん' : ''}に${b.amt > 0 ? '貸し' : '借り'}</b><span>${b.ids.length}件 · 未精算</span></div><span class="rv-a">${amt(Math.abs(b.amt))}</span>${btn('精算済み', `hmSettle(${q(b.who)})`, 'sec', { sm: 1 })}</div>`).join('')}
  </div>${lend && owe ? `<p class="rv-sum num">貸し ${yen(lend)} · 借り ${yen(owe)} · 差し引き ${lend >= owe ? '+' : '−'}${yen(Math.abs(lend - owe))}</p>` : ''}</section>`;
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

/* ================= 日記のシート(ホーム・日のページ・＋から同じもの) ================= */
function openDiary(d) {
  const cur = dayOf(d).diary || '';
  openSheet({ id: 'diary', title: d === TODAY ? '今日の日記' : '日記', lead: `<span class="tag">${fmtFull(d)}</span>`, body: textarea({ id: 'diary-t', rows: 9, ph: d === TODAY ? '今日はどんな日だった?' : 'この日のことを書く', value: cur, auto: 1, label: '日記' }),
    dirty: () => val('diary-t') !== cur, primary: { label: '日記を保存', on: () => done(daySetDiary(d, val('diary-t')), { close: 1 }) } });
}
