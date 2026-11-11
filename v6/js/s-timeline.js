/* 個人記録 v6 — アクティビティ(リスト・日・月)と、日のページ */
'use strict';
const TLS = { view: 'list', ym: '', d: '', filter: 'all' };
const TL_H = 52;   /* 日の表示: 1時間の高さ(px) */

route('acts', (id, qs) => {
  if (qs.view) TLS.view = qs.view; if (qs.d && isDate(qs.d)) { TLS.d = qs.d; TLS.ym = ymOf(qs.d); } if (qs.m) TLS.ym = qs.m;
  if (!TLS.ym) TLS.ym = ymOf(TODAY); if (!TLS.d) TLS.d = TODAY;
  const v = TLS.view;
  return `<div class="screen s-acts v-${v}">
    ${head('アクティビティ', { actions: iconBtn('search', 'さがす', "go('#/search')", 'on-card') + iconBtn('plus', '出来事を追加', `openActEditor({state:${v === 'list' ? "'done'" : "'plan'"},date:${q(v === 'list' ? TODAY : TLS.d)}})`, 'pri') })}
    ${seg([['list', 'リスト'], ['day', '日'], ['month', '月']], v, 'tlView')}
    ${v === 'list' ? tlList() : v === 'day' ? tlDay() : tlMonth()}
  </div>`;
}, { tab: 'acts', root: 1 });

function tlView(v) { if (v === 'list') TLS.ym = ymOf(TLS.d); TLS.view = v; TLS.fresh = 1; go('#/acts', { replace: 1 }); rerender(); tlLand(); }
function tlMonthStep(n) { TLS.ym = ymAdd(TLS.ym, n); TLS.d = TLS.ym === ymOf(TODAY) ? TODAY : TLS.ym + '-01'; rerender(); tlLand(); }
function tlDayStep(n) { TLS.d = dAdd(TLS.d, n); TLS.ym = ymOf(TLS.d); rerender(); }
function tlPick(d) { TLS.d = d; TLS.ym = ymOf(d); rerender(); }
function tlFilter(k) { TLS.filter = k; rerender(); }
const tlMatch = k => k === 'all' ? null : (a, id) => { const m = x => k === 'plan' ? isPlan(x) : x.kind === k && !isPlan(x); return m(a) || (!id.includes('@') && descOf(id).some(c => m(DB.acts[c]))); };
/** 月の送り */
const monthNav = (ym, fn, o = {}) => `<div class="mnav"><button class="icon-btn" onclick="${fn}(-1)" aria-label="前の月" ${o.noPrev ? 'disabled' : ''}>${ic('back')}</button><b class="num">${fmtYM(ym)}</b><button class="icon-btn" onclick="${fn}(1)" aria-label="次の月" ${o.noNext ? 'disabled' : ''}>${ic('chev')}</button></div>`;

/* ---------- リスト ---------- */
function tlList() {
  const ym = TLS.ym, cur = ym === ymOf(TODAY), first = ym + '-01', last = `${ym}-${pad2(ymDays(ym))}`;
  const end = cur && dAdd(TODAY, 14) > last ? dAdd(TODAY, 14) : last, f = tlMatch(TLS.filter);
  let days = daysWith(first, end); if (cur && !days.includes(TODAY)) days = [...days, TODAY].sort();
  const kinds = new Set(); Object.values(DB.acts).forEach(a => { if (ymOf(a.date) === ym && !isPlan(a) && !a.rep) kinds.add(a.kind); });
  const chipsL = [['all', 'すべて'], ['plan', '予定'], ...['pay', 'meal', 'talk', 'work', 'move', 'shop', 'play', 'event', 'health', 'trip'].filter(k => kinds.has(k)).map(k => [k, KINDS[k].n])];
  const body = days.map(d => {
    const has = actsOn(d).some(([id, a]) => !a.hol && (!f || f(a, id)));
    if (!has && d !== TODAY) return '';
    return `<section class="tl-day">${dayHead(d)}${has || d === TODAY ? dayAxis(d, { filter: f, memos: !f }) : ''}${d === TODAY && !dayOf(d).diary ? btn('今日の日記を書く', `openDiary(${q(d)})`, 'dash', { icon: 'pencil' }) : ''}</section>`;
  }).join('');
  const later = cur ? upcoming(400).filter(([, , d]) => d > end).length : 0;
  const oldest = Object.values(DB.acts).reduce((m, a) => !a.rep && a.date < m ? a.date : m, TODAY);
  return `<div class="tl-sticky">${monthNav(ym, 'tlMonthStep', { noPrev: ym <= ymOf(oldest), noNext: ym >= ymAdd(ymOf(TODAY), 12) })}${chips(chipsL, TLS.filter, 'tlFilter')}</div>
    ${body || `<div class="card">${empty({ icon: 'axis', text: TLS.filter === 'all' ? 'この月の記録は ありません' : 'あてはまる記録は ありません', action: { label: '出来事を追加', on: "openActEditor({state:'done'})" } })}</div>`}
    ${later ? `<a class="tl-more" href="#/acts?view=month&m=${ymAdd(ymOf(end), 1)}">これより先の予定 <b class="num">${later}</b>件${ic('chev')}</a>` : ''}`;
}
/** 開いた時の位置: 今月は今日(いまの線)へ、ほかの月は先頭へ */
function tlLand() { requestAnimationFrame(() => { const el = TLS.view === 'list' ? $('#d-' + TODAY) : TLS.view === 'day' ? $('.hg-now') || $('.hg-b') : null; if (el) el.scrollIntoView({ block: TLS.view === 'day' ? 'center' : 'start' }); else scroller().scrollTop = 0; }); }
addEventListener('hashchange', () => { if (parseHash().name === 'acts' && !_back) setTimeout(tlLand, 0); });

/* ---------- 日(時間に比例した1日) ---------- */
function tlDay() {
  const d = TLS.d, items = actsOn(d), allDay = items.filter(([, a]) => a.allDay || !a.start), timed = items.filter(([, a]) => a.start && !a.allDay);
  /* 重なる時間は列に分ける */
  const lanes = []; const pos = timed.map(([id, a]) => { const s = hm2m(a.start), e = Math.max(s + (isSpan(a) ? actMins(a) : 30), s + 30); let l = lanes.findIndex(end => end <= s); if (l < 0) { l = lanes.length; lanes.push(0); } lanes[l] = e; return { id, a, s, e, l }; });
  const n = Math.max(1, lanes.length), tasks = Object.entries(DB.tasks).filter(([, t]) => t.due === d);
  return `<div class="tl-sticky"><div class="mnav"><button class="icon-btn" onclick="tlDayStep(-1)" aria-label="前の日">${ic('back')}</button><b>${fmtFull(d)}${d === TODAY ? '<span class="tag me">今日</span>' : `<span class="tag">${relDay(d)}</span>`}</b><button class="icon-btn" onclick="tlDayStep(1)" aria-label="次の日">${ic('chev')}</button></div></div>
    ${allDay.length ? `<div class="hg-all">${allDay.map(([id, a]) => a.hol ? `<span class="hol-t">${ic('flag')}${esc(a.title)}</span>` : `<a class="tag ${isPlan(a) ? 'plan' : 'me'}" href="#/a/${id}">${esc(actTitle(a))}</a>`).join('')}</div>` : ''}
    <div class="card hg-card"><div class="hg" style="height:${24 * TL_H}px">
      <i class="hg-sky"></i>
      ${[...Array(25)].map((_, h) => `<div class="hg-h" style="top:${h * TL_H}px"><span class="num">${h % 3 === 0 ? h + ':00' : ''}</span></div>`).join('')}
      <div class="hg-in">${pos.map(p => { const st = isPlan(p.a) ? (isLate(p.a) ? 'late' : 'plan') : 'done';
        return `<a class="hg-b ${st}${isSpan(p.a) ? '' : ' mo'}${isPay(p.a) ? ' pay' : ''}" href="#/a/${p.id}" style="top:${p.s / 60 * TL_H}px;height:${Math.max(28, (p.e - p.s) / 60 * TL_H - 2)}px;left:${p.l / n * 100}%;width:calc(${100 / n}% - 4px)"><b>${esc(actTitle(p.a))}</b><span class="num">${timeText(p.a)}${isPay(p.a) ? ' · ' + yen(xOf(p.id) ? xAmt(p.id) : payTotal(p.a)) : ''}</span></a>`; }).join('')}
        ${d === TODAY ? `<div class="hg-now" style="top:${hm2m(NOW) / 60 * TL_H}px"><time class="num">${NOW}</time></div>` : ''}</div>
    </div></div>
    ${tasks.length ? `<section>${sec('この日が期限のToDo', { n: tasks.length })}${rows(tasks.map(([id]) => taskRow(id)).join(''))}</section>` : ''}
    <div class="grid2">${btn('予定を追加', `openActEditor({state:'plan',date:${q(d)}})`, 'sec', { icon: 'cal' })}${btn('日のページ', `go('#/day/${d}')`, 'sec', { icon: 'day' })}</div>`;
}

/* ---------- 月 ---------- */
function tlMonth() {
  const ym = TLS.ym, n = ymDays(ym), lead = dDow(ym + '-01'), sel = ymOf(TLS.d) === ym ? TLS.d : '';
  const cells = [...Array(lead)].map(() => '<span></span>').concat([...Array(n)].map((_, i) => {
    const d = `${ym}-${pad2(i + 1)}`, items = actsOn(d), hol = items.some(([, a]) => a.hol);
    const dn = items.filter(([, a]) => !isPlan(a)).length, pl = items.filter(([, a]) => isPlan(a) && !a.hol).length, td = Object.values(DB.tasks).filter(t => t.due === d && !t.done).length;
    const marks = [...Array(Math.min(3, dn)).fill('<i class="d"></i>'), ...Array(Math.min(3, pl)).fill('<i class="p"></i>')].slice(0, 4).join('');
    return `<button class="mc${d === TODAY ? ' today' : ''}${d === sel ? ' sel' : ''}${hol || dDow(d) === 0 ? ' hol' : ''}" onclick="tlPick(${q(d)})" aria-label="${fmtFull(d)} 記録${dn}件 予定${pl}件" aria-pressed="${d === sel}"><b class="num">${i + 1}</b><span class="mc-m">${marks}</span>${td ? `<i class="mc-t" aria-hidden="true"></i>` : ''}</button>`;
  }));
  const tasks = sel ? Object.entries(DB.tasks).filter(([, t]) => t.due === sel) : [];
  return `<div class="card cal">${monthNav(ym, 'tlMonthStep')}<div class="cal-w">${[...DOW].map(w => `<span>${w}</span>`).join('')}</div><div class="cal-g">${cells.join('')}</div>
      <div class="cal-l" aria-hidden="true"><span><i class="d"></i>記録</span><span><i class="p"></i>予定</span><span><i class="mc-t"></i>ToDo</span></div></div>
    ${sel ? `<section class="tl-day">${dayHead(sel)}${actsOn(sel).length ? dayAxis(sel, { memos: 1 }) : `<div class="card">${empty({ icon: 'cal', text: 'この日の予定・記録は ありません', action: { label: '予定を追加', on: `openActEditor({state:'plan',date:${q(sel)}})` } })}</div>`}</section>
      ${tasks.length ? `<section>${sec('この日が期限のToDo', { n: tasks.length })}${rows(tasks.map(([id]) => taskRow(id)).join(''))}</section>` : ''}` : ''}`;
}

/* ================= 日のページ ================= */
route('day', d => {
  if (!isDate(d)) d = TODAY;
  const dy = dayOf(d), items = actsOn(d), done = items.filter(([, a]) => !isPlan(a)), sp = daySpend(d);
  const all = [...items.map(i => i[0]), ...items.flatMap(([id]) => id.includes('@') ? [] : descOf(id))].map(id => [id, A(id)]);
  const ents = [...new Set(all.flatMap(([, a]) => (a.roles || []).filter(r => r.e && r.r !== '支払先').map(r => r.e)))];
  /* 足あと: 場所を時刻の順に(同じ場所が続く時は1つ) */
  const steps = []; all.filter(([, a]) => !isPlan(a) && a.start).sort((x, y) => x[1].start.localeCompare(y[1].start)).forEach(([, a]) => rolesOf(a, '出発', '場所', '支払先', '到着').filter(r => r.r !== '支払先' || (r.e && isUnder(E(r.e).types[0], 'place'))).forEach(r => { const n = roleName(r); if (n && steps[steps.length - 1] !== n) steps.push(n); }));
  const tasks = Object.entries(DB.tasks).filter(([, t]) => t.due === d || t.done === d);
  const known = daysWith(dAdd(d, -400), dAdd(d, 400)).concat(TODAY), prev = known.filter(x => x < d).sort().pop(), next = known.filter(x => x > d).sort()[0];
  return `<div class="screen s-day">
    ${bar(fmtFull(d), { actions: (prev ? iconBtn('up', `前の日 ${fmtMD(prev)}`, `go('#/day/${prev}',{replace:1})`) : '') + (next ? iconBtn('down', `次の日 ${fmtMD(next)}`, `go('#/day/${next}',{replace:1})`) : '') + iconBtn('pencil', '日記を書く', `openDiary(${q(d)})`) })}
    <header class="day-h" data-bar-watch><b class="num">${+d.slice(8)}</b><div><span>${dowJ(d)}曜日${d === TODAY ? '・今日' : ''}</span><small class="num">${d.slice(0, 4)}年${+d.slice(5, 7)}月 · ${relDay(d)}</small></div></header>
    ${skyBand(d)}
    ${dy.diary ? `<button class="diary" onclick="openDiary(${q(d)})"><p>${esc(dy.diary)}</p></button>` : btn(d === TODAY ? '今日の日記を書く' : 'この日のことを書く', `openDiary(${q(d)})`, 'dash', { icon: 'pencil' })}
    ${photoStrip(d, { add: `dayPhoto(${q(d)})`, on: 'dayPhotoMenu' })}
    ${stats([{ v: `${done.length}<small>件</small>`, l: '出来事' }, ...(sp ? [{ v: amt(sp), l: '自分の支出', href: `#/ledger?m=${ymOf(d)}` }] : []), { v: `${(dy.photos || []).length}<small>枚</small>`, l: '写真' }])}
    ${steps.length > 1 ? `<section>${sec('足あと')}<div class="card pad route">${steps.map(s => `<span>${esc(s)}</span>`).join(ic('move'))}</div></section>` : ''}
    ${ents.length ? `<section>${sec('この日に交わった', { n: ents.length })}<div class="card pad ents wide">${ents.map(e => ent(e)).join('')}</div></section>` : ''}
    <section>${sec('この日の流れ')}${items.length || memosOn(d).length ? dayAxis(d, { memos: 1 }) : `<div class="card">${empty({ icon: 'axis', text: 'この日の出来事は まだありません', action: { label: '出来事を追加', on: `openActEditor({state:${d > TODAY ? "'plan'" : "'done'"},date:${q(d)}})` } })}</div>`}
      <div class="grid2">${btn(d > TODAY ? '予定を追加' : '出来事を追加', `openActEditor({state:${d > TODAY ? "'plan'" : "'done'"},date:${q(d)}})`, 'sec', { icon: 'plus' })}${btn('支払いを記録', `openPayEditor({date:${q(d)}})`, 'sec', { icon: 'yen' })}</div></section>
    ${tasks.length ? `<section>${sec('この日のToDo', { n: tasks.length })}${rows(tasks.map(([id]) => taskRow(id)).join(''))}</section>` : ''}
  </div>`;
}, { tab: 'acts' });
function dayPhoto(d) {
  openSheet({ title: '写真を追加', body: banner('この試作では、写真の代わりに ひとことを残します。', 'info') + field('ひとこと', input({ id: 'ph-c', ph: '何の写真?', auto: 1 })), primary: { label: '写真を追加', on: () => done(dayAddPhoto(d, val('ph-c')), { close: 1 }) } });
}
function dayPhotoMenu(d, i) { menu('写真', [{ label: '写真を外す', icon: 'trash', on: () => done(dayRemovePhoto(d, i)) }]); }
