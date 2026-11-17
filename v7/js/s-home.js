/* 個人記録 v7 — ホーム(いま)・日記のシート */
'use strict';

/* ================= ホーム ================= */
route('home', () => {
  const up = upcoming(0).filter(([, a]) => hasT(a.start) && tOf(a.start) > NOW)[0];
  const allDay = actsOn(TODAY).filter(([, a]) => isAllDay(a));
  const hol = holOn(TODAY), ts = nowStamp(), face = skyFace(NOW);
  const doing = actsOn(TODAY).find(([, a]) => hasT(a.start) && hasT(a.end) && a.start <= ts && ts < a.end);
  return `<div class="screen s-home">
    ${head(fmtMDJ(TODAY), { sub: `${dowJ(TODAY)}曜${hol ? ' · ' + esc(hol) : ''}`, actions: iconBtn('search', 'さがす', "go('#/search')", 'on-card') + iconBtn('pencil', dayOf(TODAY).diary ? '今日の日記を開く' : '今日の日記を書く', `openDiary(${q(TODAY)})`, 'on-card') })}
    <section class="card now-card lift">
      <div class="now-sky ink-${face.ink}" style="${face.style}">
        <div class="now-h"><div class="now-t"><span>いま</span><b class="num">${NOW}</b></div>${doing ? `<a class="now-doing" href="#/a/${doing[0]}">${kindIc(doing[1])}<span>${esc(actTitle(doing[1]))}</span></a>` : ''}<a class="now-pg" href="#/day/${TODAY}">今日のページ${ic('chev')}</a></div>
        ${skyBand(TODAY)}
      </div>
      <div class="now-body">
        ${allDay.length ? `<div class="ents">${allDay.map(([id, a]) => `<a class="tag plan" href="#/a/${id}">${esc(actTitle(a))}</a>`).join('')}</div>` : ''}
        ${up ? hmNext(up) : `<div class="now-none">${ic('check')}<span>今日の予定は ここまで</span></div>`}
        ${hmFlow()}
      </div>
    </section>
    <button class="quick" onclick="openCapture()">${ic('plus')}<span>入力する</span><kbd>ランチ 1200</kbd></button>
    ${hmGoals()}
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
  const items = actsOn(TODAY).filter(([, a]) => hasT(a.start)); if (!items.length) return '';
  const past = items.filter(([, a]) => !isPlan(a)), rest = items.filter(([, a]) => isPlan(a));
  const hide = HM_ALL ? 0 : Math.max(0, past.length - 3), shown = [...past.slice(hide), ...rest].sort(byTime);
  const line = ([id, a]) => { const st = isPlan(a) ? (isLate(a) ? 'late' : 'plan') : 'done'; return `<a class="fl ${st}${isSpan(a) ? ' span' : ''}" href="#/a/${id}"><time class="num">${spanOn(a, TODAY).from ? '前日' : tOf(a.start)}</time><i></i><b>${esc(actTitle(a))}</b>${isPay(a) && !isPlan(a) ? amt(xOf(id) ? xAmt(id) : payTotal(a)) : hasT(a.end) ? `<span class="num">–${edgeText(a.end, TODAY)}</span>` : ''}</a>`; };
  return `<div class="flow">${hide ? `<button class="fl-more" onclick="HM_ALL=true;rerender()">これより前 ${hide}件</button>` : ''}${shown.map(line).join('')}</div>`;
}
/** 次の予定(点線の札)。残り時間を大きく */
function hmNext([id, a]) {
  const left = hm2m(tOf(a.start)) - hm2m(NOW), tasks = tasksOfAct(id).filter(([, t]) => taskOpen(t));
  return `<div class="next"><a class="hit" href="#/a/${id}" aria-label="次の予定 ${esc(actTitle(a))}"></a>
    <div class="next-l"><span>次</span><b class="num">${tOf(a.start)}</b></div>
    <div class="next-m"><b>${esc(actTitle(a))}</b><div class="ents">${roleEnts(a, { link: false })}${tasks.length ? `<span class="mini">${ic('task')}${tasks.length}</span>` : ''}</div></div>
    <div class="next-r"><span>あと</span><b>${durJ(left)}</b></div></div>`;
}
/** 取り組み中(目標): 進行中のプロジェクトを1件1枚。期日・連続・今日の回(未記録は「記録にする」) */
function hmGoals() {
  const ids = goalsNow(); if (!ids.length) return '';
  const cards = ids.map(id => goalCard(id)).filter(Boolean).map(hmGoalCard).join('');
  return `<section>${sec('取り組み中', { n: ids.length })}<div class="stack">${cards}</div></section>`;
}
function hmGoalCard(g) {
  const d = g.daysLeft;
  const left = d == null ? '' : d < 0 ? `<span class="gl-over"><b class="num">${-d}</b>日 過ぎた</span>`
    : d === 0 ? `<span class="gl-near">今日まで</span>`
    : `<span class="${d <= 7 ? 'gl-near' : ''}">あと<b class="num">${d}</b>日</span>`;
  const st = g.streak && g.streak.n > 0 ? `<span><b class="num">${g.streak.n}</b>${esc(g.streak.unit)} 連続</span>` : '';
  const meta = left || st ? `<div class="gl-meta">${left}${st}</div>` : '';
  const today = g.today.length ? `<div class="gl-today">${g.today.map(t => t.done
    ? `<div class="gl-t done">${ic('check')}<span>${esc(t.title)}</span></div>`
    : `<div class="gl-t"><span>${esc(t.title)}</span>${btn('記録にする', `recordPlan(${q(t.id)})`, 'pri', { sm: 1 })}</div>`).join('')}</div>` : '';
  return `<article class="card gl-card"><div class="gl-h"><a class="gl-name" href="#/e/${esc(g.id)}">${esc(g.name)}</a>${meta}</div>${today}</article>`;
}
/** 確かめる: 承認するだけで片づくもの(記録待ちの予定・未確認の行・未精算) */
function hmTitle(x) { const r = x.row; return x.t === 'acts' ? actTitle(r) : x.t === 'tasks' ? r.t : String(r.body || '').slice(0, 24); }
function hmReview() {
  const pend = pendingPlans(), unsure = window.unsureRows ? unsureRows() : [], bals = personBalances();
  const n = pend.length + unsure.length + bals.length; if (!n) return '';
  const lend = bals.filter(b => b.amt > 0).reduce((t, b) => t + b.amt, 0), owe = bals.filter(b => b.amt < 0).reduce((t, b) => t - b.amt, 0);
  return `<section>${sec('確かめる', { n })}<div class="rv">
    ${pend.map(([id, a]) => `<div class="rv-i rv-plan"><a class="hit" href="#/a/${id}" aria-label="${esc(actTitle(a))}"></a><span class="rv-ic">${kindIc(a)}</span><div class="rv-m"><b>${esc(actTitle(a))}</b><span class="num">${aDate(a) === TODAY ? '' : relDay(aDate(a)) + ' '}${esc(timeText(a))}</span></div>${btn('記録にする', `recordPlan(${q(id)})`, 'pri', { sm: 1 })}</div>`).join('')}
    ${unsure.map(x => {
      const a = x.t === 'acts' ? A(x.k) : null, pay = a && isPay(a), rc = a ? (pay ? ic('receipt') : kindIc(a)) : ic(x.t === 'tasks' ? 'task' : 'memo');
      const ttl = window.recTitle ? recTitle(x.t, x.row) : hmTitle(x), d = a ? aDate(a) : x.t === 'tasks' ? (x.row.due || '') : x.t === 'memos' ? dOf(x.row.at) : '';
      const sub = pay ? `未確認 ${unsureCount(a)}件 · ${yen(payTotal(a))}` : `未確認${d ? ` · ${esc(relDay(d))}` : ''}`;
      return `<div class="rv-i rv-read"><button class="hit" onclick="${pay ? `go('#/a/${x.k}')` : `window.openInbox && openInbox({focus:{t:${q(x.t)},k:${q(x.k)}}})`}" aria-label="${esc(ttl)}"></button><span class="rv-ic">${rc}</span><div class="rv-m"><b>${readMark(esc(ttl))}</b><span>${sub}</span></div>${btn('確かめる', pay ? `go('#/a/${x.k}')` : `done(confirmRows([{t:${q(x.t)},k:${q(x.k)}}]))`, 'sec', { sm: 1 })}</div>`;
    }).join('')}
    ${bals.map(b => `<div class="rv-i rv-owe"><a class="hit" href="#/ledger?v=owe" aria-label="貸し借り"></a><span class="rv-ic">${ic('swap')}</span><div class="rv-m"><b>${esc(whoShort(b.who))}${E(b.who) && !isStub(b.who) ? 'さん' : ''}に${b.amt > 0 ? '貸し' : '借り'}</b><span>${b.ids.length}件 · 未精算</span></div><span class="rv-a">${amt(Math.abs(b.amt))}</span>${btn('精算済み', `hmSettle(${q(b.who)})`, 'sec', { sm: 1 })}</div>`).join('')}
  </div>${lend && owe ? `<p class="rv-sum num">貸し ${yen(lend)} · 借り ${yen(owe)} · 差し引き ${lend >= owe ? '+' : '−'}${yen(Math.abs(lend - owe))}</p>` : ''}</section>`;
}
function hmSettle(who) { const b = personBalances().find(x => x.who === who); if (b) done(paySettle(b.ids, true, { who: who === 'other' ? null : who })); }
/** 今日のお金: 固定費を除く。先週の同じ曜日と、この7日 */
function hmMoney() {
  const t = daySpend(TODAY, { noFixed: 1 }), lw = dAdd(TODAY, -7), l = daySpend(lw, { noFixed: 1 });
  const days = [...Array(7)].map((_, i) => dAdd(TODAY, i - 6)), vals = days.map(d => daySpend(d, { noFixed: 1 })), max = Math.max(1, ...vals);
  const w0 = vals.reduce((s, v) => s + v, 0), w1 = [...Array(7)].reduce((s, _, i) => s + daySpend(dAdd(TODAY, i - 13), { noFixed: 1 }), 0);
  const pct = w1 ? Math.round((w0 - w1) / w1 * 100) : null;
  return `<section>${sec('今日の支出', { act: { label: '家計簿', href: '#/ledger' } })}<div class="card pad money">
    <div class="money-h"><div>${amt(t, { cls: 'hero', count: 1 })}<span class="tag">固定費を除く</span></div>
      <div class="money-c"><span>先週の${dowJ(lw)}曜 ${yen(l)}</span><b class="${t > l ? 'up' : t < l ? 'dn' : ''}">${t === l ? '±0' : (t > l ? '+' : '−') + yen(Math.abs(t - l)).slice(1)}</b></div></div>
    <div class="wk" role="img" aria-label="この7日の支出">${days.map((d, i) => `<a class="wk-c${d === TODAY ? ' on' : ''}" href="#/day/${d}"><span class="wk-v num">${vals[i] ? (vals[i] >= 10000 ? (vals[i] / 10000).toFixed(1) + '万' : vals[i].toLocaleString()) : ''}</span><i style="height:${vals[i] ? Math.max(4, Math.round(vals[i] / max * 72)) : 0}px"></i><span class="wk-d">${d === TODAY ? '今日' : dowJ(d)}</span></a>`).join('')}</div>
    <div class="money-f"><span>この7日 <b class="num">${yen(w0)}</b></span>${pct == null ? '' : `<span>前の7日より <b class="num">${pct > 0 ? '+' : ''}${pct}%</b></span>`}</div>
    <div class="hm-qe"><label class="field"><span class="field-l">金額</span><input id="hm-qe" class="input num" type="text" inputmode="numeric" placeholder="例: 1280" aria-label="金額"></label>${btn('支払いを記録', 'hmPayQuick()', 'sec', { sm: 1, icon: 'yen' })}</div>
  </div></section>`;
}
function hmTodo() {
  const due = tasksDue().sort((a, b) => a[1].due.localeCompare(b[1].due));
  return `<section>${sec('今日までのToDo', { n: due.length, act: { label: 'すべて', href: '#/todo' } })}${due.length ? rows(due.slice(0, 4).map(([id]) => taskRow(id)).join('') + (due.length > 4 ? row({ t: `ほか ${due.length - 4}件`, href: '#/todo', chev: 1 }) : '')) : `<div class="card">${empty({ icon: 'task', text: '今日までのToDoは ありません', action: { label: 'ToDoを追加', on: 'openTaskEditor()' } })}</div>`}</section>`;
}
/** 思い出す: 1年前・1か月前・先週の同じ日 */
function hmRecall() {
  const r = recallDays(); if (!r.length) return '';
  return `<section>${sec('思い出す')}<div class="recall">${r.map(x => { const dy = dayOf(x.date), acts = actsOn(x.date, { state: 'done' }), phs = photosOn(x.date), ph = phs[0] && phs[0][1];
    return `<a class="rc" href="#/day/${x.date}" style="--c:${ph ? skyAt(tOf(ph.at) || '12:00') : skyAt('12:00')}"><span class="rc-l">${esc(x.label)}</span><b>${esc(dy.diary || acts.slice(0, 2).map(([, a]) => actTitle(a)).join('、') || '記録あり')}</b><span class="rc-d num">${x.date.slice(0, 4)}年${fmtMDJ(x.date)} ${dowJ(x.date)}曜${phs.length ? ` · 写真 ${phs.length}` : ''}</span>${skyBand(x.date, { mini: 1 })}</a>`; }).join('')}</div></section>`;
}
function hmMemo() {
  const ms = memosSorted().slice(0, 2);
  const quick = `<div class="card pad hm-quick">${textarea({ id: 'hm-memo', rows: 2, ph: 'いま思ったこと', label: 'すぐメモ' })}<div class="hm-quick-f">${btn('メモを保存', 'hmMemoSave()', 'sec', { sm: 1, icon: 'memo' })}</div></div>`;
  return `<section>${sec('メモ', { act: { label: 'すべて', href: '#/memo' } })}${quick}${ms.length ? `<div class="stack">${ms.map(([id]) => memoCard(id, { clamp: 3 })).join('')}</div>` : `<div class="card">${empty({ icon: 'memo', text: 'メモは まだありません', action: { label: 'メモを書く', on: "openCapture('memo')" } })}</div>`}</section>`;
}

/** すぐメモ: 入力欄の文を保存(取り消し付き)。空なら理由を出して欄に残す */
function hmMemoSave() {
  const v = val('hm-memo'); if (!v.trim()) return toast('本文を入れてください', { bad: 1 });
  done(memoAdd(v), { msg: 'メモを保存しました' });
}
/** 支払いを記録(ホーム): 金額欄の値を渡して開く。金額が空なら金額なしで開く */
function hmPayQuick() {
  const n = Number(String(val('hm-qe')).replace(/[,，\s]/g, ''));
  openPayEditor(n > 0 ? { amt: Math.round(n) } : {});
}

/* ================= 日記のシート(ホーム・日のページ・＋から同じもの) ================= */
function openDiary(d) {
  const cur = dayOf(d).diary || '';
  openSheet({ id: 'diary', title: d === TODAY ? '今日の日記' : '日記', lead: `<span class="tag">${fmtFull(d)}</span>`, body: textarea({ id: 'diary-t', rows: 9, ph: d === TODAY ? '今日はどんな日だった?' : 'この日のことを書く', value: cur, auto: 1, label: '日記' }),
    dirty: () => val('diary-t') !== cur, primary: { label: '日記を保存', on: () => done(daySetDiary(d, val('diary-t')), { close: 1 }) } });
}
