/* 個人記録 v6 — 詳細(記録・予定の詳細 #/a/{id})と、出来事・予定の入力 openActEditor。接頭辞 dt */
'use strict';
/** 仮の回「ひな形@日付」でも、書き込みはひな形へ */
const dtTid = id => String(id).split('@')[0];
const dtGoOf = r => r && r.ok && r.id ? '#/a/' + r.id : undefined;
const dtHM = m => m2hm(Math.max(0, Math.min(1439, m)));

/* ================= 詳細 ================= */
route('a', id => {
  const a = A(id);
  if (!a) return `<div class="screen s-detail">${bar('見つかりません')}<div class="card">${empty({ icon: 'day', text: 'この記録は見つかりません' })}</div>${btn('戻る', 'goBack()', 'sec', { icon: 'back', wide: 1 })}</div>`;
  const more = iconBtn('more', 'その他の操作', `dtMenu(${q(id)})`);
  if (isPay(a) && !isPlan(a)) return `<div class="screen s-detail s-detail-pay">${bar(actTitle(a), { actions: more })}${window.payDetail ? payDetail(id) : paySlip(id, { noLink: 1 })}${dtPhotos(dtTid(id), a)}</div>`;
  const tid = dtTid(id), plan = isPlan(a), kids = kidsOf(id);
  const body = a.hol ? '' : `${dtRoles(tid, a)}${dtNoteBlock(tid, a)}${dtPhotos(tid, a)}${dtRecords(tid, a)}${dtKids(id, a, kids)}${dtTasksMemos(id, tid, a)}`;
  return `<div class="screen s-detail">
    ${bar(actTitle(a), { actions: more })}
    ${dtEnv(ancOf(id), dtHero(id, a))}
    ${plan && !a.hol ? btn(a.virt ? 'この回だけ記録にする' : '記録にする', `dtRecOpen(${q(id)})`, 'pri', { icon: 'check', wide: 1 }) : ''}
    ${body}
  </div>`;
});

/** 親の連なり: 外側から順に封筒のように入れ子にして、いちばん内側に自分 */
function dtEnv(anc, inner) {
  return anc.reduceRight((acc, [pid, p]) => `<div class="dt-env"><a class="dt-env-h" href="#/a/${pid}">${kindIc(p)}<b>${esc(actTitle(p))}</b><span class="num">${timeText(p)}</span>${ic('chev', 'row-chev')}</a>${acc}</div>`, inner);
}
/** 時刻の言葉 */
function dtWhen(a) {
  if (a.allDay || !a.start) return a.date2 ? `${fmtMD(a.date)}–${fmtMD(a.date2)} · ${dDiff(a.date2, a.date) + 1}日間` : '終日';
  if (a.end) return `${a.start}–${a.end} · ${durJ(actMins(a))}`;
  return a.start;
}
/** 空の帯の上で、この記録の場所に付ける枠 */
function dtMark(a) {
  if (a.allDay || !a.start || a.hol) return '';
  const x = (hm2m(a.start) / 14.4).toFixed(2);
  if (isSpan(a)) return `<i class="dt-mark" style="left:calc(${x}% - 3px);width:calc(${Math.max(.6, actMins(a) / 14.4).toFixed(2)}% + 6px)"></i>`;
  return `<i class="dt-mark m" style="left:${x}%"></i>`;
}
function dtHero(id, a) {
  const plan = isPlan(a), late = isLate(a), st = plan ? (late ? 'late' : 'plan') : 'done', k = KINDS[a.kind], title = actTitle(a);
  const rep = repText(A(a.tpl || id)), tags = a.hol ? tag('祝日', 'late') : plan ? tag(late ? '過ぎた予定' : '予定', late ? 'late' : 'plan') : '';
  const nx = a.rep && !a.virt && plan ? repNext(a) : '', diary = dayOf(a.date).diary;
  const rel = plan && !late && !a.hol ? (a.date === TODAY ? (a.start && !a.allDay && a.start > NOW ? 'あと ' + durJ(hm2m(a.start) - hm2m(NOW)) : '') : a.date > TODAY ? relDay(a.date) : '') : '';
  const meta = [nx ? `<span class="mini">${ic('repeat')}次回 ${fmtMD(nx)}(${dowJ(nx)})</span>` : '', rep ? `<span class="mini">${ic('repeat')}${esc(rep)}</span>` : '', a.alarm && plan ? `<span class="mini">${ic('bell')}${esc(a.alarm)}</span>` : ''].join('');
  return `<div class="dt-hero ${st}">
    <div class="dt-k">${kindIc(a)}${title !== k.n ? `<span>${esc(k.n)}</span>` : ''}${tags}</div>
    <h1 class="dt-t" data-bar-watch>${esc(title)}</h1>
    <div class="dt-when num">${esc(dtWhen(a))}${rel ? `<small class="dt-rel">${esc(rel)}</small>` : ''}</div>
    ${a.pay && a.pay.plan ? `<div class="dt-amt">${amt(a.pay.plan.amt, { cls: 'big' })}${catTag(a.pay.plan.cat)}${a.pay.plan.how ? `<span class="mini">${esc(a.pay.plan.how)}</span>` : ''}</div>` : ''}
    ${a.planned ? `<div class="mini num">予定は ${a.planned.start}${a.planned.end ? '–' + a.planned.end : ''}</div>` : ''}
    <a class="dt-date" href="#/day/${a.date}">${ic('cal')}<span>${fmtFull(a.date)}</span>${rel ? '' : `<small>${relDay(a.date)}</small>`}${ic('chev', 'row-chev')}</a>
    ${diary ? `<a class="dt-diary clamp" href="#/day/${a.date}">${esc(diary)}</a>` : ''}
    ${meta ? `<div class="dt-meta">${meta}</div>` : ''}
    <div class="dt-sky">${skyBand(a.date)}${dtMark(a)}</div>
  </div>`;
}

/* ---------- 関わったエンティティ ---------- */
function dtRoles(tid, a) {
  const groups = [];
  (a.roles || []).forEach((r, i) => { let g = groups.find(x => x.r === r.r); if (!g) groups.push(g = { r: r.r, items: [] }); g.items.push([r, i]); });
  if (!groups.length) return `<section>${btn('人・場所を足す', `dtRoleAdd(${q(tid)})`, 'dash', { icon: 'plus' })}</section>`;
  const item = ([r, i]) => `<span class="dt-ro">${ent(r.e, { name: r.name })}${!r.e && r.name ? `<button class="dt-reg" onclick="dtReg(${q(tid)},${i})">登録</button>` : ''}<button class="dt-rx" onclick="dtRoleDel(${q(tid)},${i})" aria-label="${esc(roleName(r))}を外す">${ic('close')}</button></span>`;
  return `<section>${sec('関わった', { act: { label: '足す', on: `dtRoleAdd(${q(tid)})` } })}${rows(groups.map(g => `<div class="prop"><button class="prop-k dt-rk" onclick="dtRoleRename(${q(tid)},[${g.items.map(x => x[1])}])" aria-label="${esc(g.r)}の役割を直す">${esc(g.r)}</button><span class="prop-v">${g.items.map(item).join('')}</span></div>`))}</section>`;
}
/** 役割名だけ選び直す(相手はそのまま) */
function dtRoleRename(tid, idxs) {
  const rs = (DB.acts[tid] || {}).roles || [], cur = (rs[idxs[0]] || {}).r;
  pick({ title: '役割', cur, items: ROLES.map(r => ({ k: r, label: r })), on: r => { if (r === cur) return; done(actUpdate(tid, { roles: rs.map((x, j) => idxs.includes(j) ? { ...x, r } : x) })); } });
}
/** 名前だけの相手を、場所・人物・店として登録(トースト1つで両方取り消せる) */
function dtReg(tid, i) {
  const role = ((DB.acts[tid] || {}).roles || [])[i]; if (!role || role.e || !role.name) return;
  const guess = ['同席', '参加'].includes(role.r) ? 'person' : 'place';
  pick({ title: `「${role.name}」を登録`, cur: guess, items: [['place', '場所', 'place'], ['person', '人物', 'user'], ['shop', '店', 'bag']].map(([k, l, g]) => ({ k, label: l, lead: ic(g) })), on: t => dtRegDo(tid, i, t) });
}
function dtRegDo(tid, i, type) {
  const rs = ((DB.acts[tid] || {}).roles || []), role = rs[i]; if (!role || role.e || !role.name) return;
  const r1 = entAdd({ n: role.name, types: [type] }); if (!r1.ok) return toast(r1.why, { bad: 1 });
  const r2 = actUpdate(tid, { roles: rs.map((x, j) => j === i ? { r: x.r, e: r1.id } : x) });
  if (!r2.ok) { r1.undo(); return done(r2); }
  done({ ok: true, label: `「${role.name}」を登録しました`, undo: () => { r2.undo(); r1.undo(); } });
}
/** 役割 → 相手の順に選ぶ。cb({r, e} か {r, name}) */
function dtRolePick(cb) {
  pick({ title: '役割', items: ROLES.map(r => ({ k: r, label: r })), on: r => dtEntPick(r, cb) });
}
function dtEntPick(r, cb) {
  const root = ['場所', '出発', '到着'].includes(r) ? 'place' : ['同席', '参加'].includes(r) ? 'person' : r === 'プロジェクト' ? 'project' : null;
  const fit = id => root && isUnder(DB.ents[id].types[0], root) ? 1 : 0;
  const ids = Object.keys(DB.ents).sort((x, y) => fit(y) - fit(x));
  pick({
    title: `${r}を選ぶ`, search: true,
    items: ids.map(id => ({ k: id, label: DB.ents[id].n, sub: typePath(DB.ents[id].types[0]), lead: avatar(id) })),
    create: { label: 'を新しく作る', on: t => dtEntNew(r, t, cb) },
    on: k => cb({ r, e: k }),
  });
}
function dtEntNew(r, name, cb) {
  const mk = type => { const res = entAdd({ n: name, types: [type] }); if (!res.ok) return toast(res.why, { bad: 1 }); cb({ r, e: res.id }); };
  menu(`「${name}」`, [
    { label: '人物として作る', icon: 'user', on: () => mk('person') },
    { label: '場所として作る', icon: 'place', on: () => mk('place') },
    { label: '名前だけにする', icon: 'tag', sub: '未登録のまま', on: () => cb({ r, name }) },
  ]);
}
function dtRoleAdd(tid) { dtRolePick(role => done(actUpdate(tid, { roles: [...(DB.acts[tid].roles || []), role] }))); }
function dtRoleDel(tid, i) { done(actUpdate(tid, { roles: (DB.acts[tid].roles || []).filter((_, j) => j !== i) })); }

/* ---------- 記録に付いた写真 ---------- */
function dtPhotos(tid, a) {
  if (!a || a.hol || isPlan(a) || a.virt || typeof photosOfAct !== 'function') return '';
  const ps = photosOfAct(tid);
  if (!ps.length) return `<section>${btn('写真を足す', `dtPhotoAdd(${q(tid)})`, 'dash', { icon: 'camera' })}</section>`;
  return `<section>${sec('写真', { n: ps.length })}<div class="photos">${ps.map(p => `<button class="photo" style="--c:${skyAt(p.t)}" onclick="dtPhotoDel(${q(p.d)},${p.i})" aria-label="${esc(p.cap || '写真')}を外す">${ic('photo')}<span class="dt-pc"><b>${esc(p.cap || '')}</b><time>${p.t}</time></span></button>`).join('')}<button class="photo add" onclick="dtPhotoAdd(${q(tid)})" aria-label="写真を足す">${ic('camera')}<span class="dt-pc">写真を足す</span></button></div></section>`;
}
let DTP = null;
function dtPhotoAdd(tid) {
  DTP = { tid, v: '' };
  openSheet({ title: '写真を足す', body: field('ひとこと', input({ id: 'dtp', ph: 'ひとこと', oninput: 'DTP.v=this.value', auto: 1 })), dirty: () => !!DTP.v, primary: { label: '足す', on: () => done(dayAddPhoto(DB.acts[DTP.tid].date, DTP.v.trim(), DTP.tid), { close: 1 }) } });
}
function dtPhotoDel(d, i) { done(dayRemovePhoto(d, i)); }

/* ---------- 繰り返しの予定: 記録になった回 ---------- */
const DTMORE = {};
function dtRecords(tid, a) {
  const t = DB.acts[tid]; if (!t || !t.rep || typeof recordsOfPlan !== 'function') return '';
  const rs = recordsOfPlan(tid); if (!rs.length) return '';
  const shown = DTMORE[tid] ? rs : rs.slice(0, 5);
  return `<section>${sec('記録になった回', { n: rs.length })}${rows(shown.map(([k, r]) => row({ lead: kindIc(r), t: `${fmtMD(r.date)}(${dowJ(r.date)})`, s: r.start && !r.allDay ? r.start : '', right: isPay(r) ? amt(payTotal(r)) : '', href: '#/a/' + k, chev: 1 })))}${rs.length > shown.length ? btn('もっと見る', `dtMoreRec(${q(tid)})`, 'ghost', { wide: 1 }) : ''}</section>`;
}
function dtMoreRec(tid) { DTMORE[tid] = 1; rerender(); }

/* ---------- メモ書き ---------- */
const dtNoteBlock = (tid, a) => `<section>${a.note ? `<button class="card pad dt-note" onclick="dtNote(${q(tid)})"><p>${esc(a.note)}</p></button>` : btn('メモを書く', `dtNote(${q(tid)})`, 'dash', { icon: 'pencil' })}</section>`;
let DTN = null, DTN0 = '';
function dtNote(tid) {
  DTN = { tid, v: (DB.acts[tid] || {}).note || '' }; DTN0 = DTN.v;
  openSheet({ title: 'メモ書き', body: textarea({ id: 'dtn', value: DTN.v, rows: 7, oninput: 'DTN.v=this.value', auto: 1, label: 'メモ書き' }), dirty: () => DTN.v !== DTN0, primary: { label: '保存', on: () => done(actUpdate(DTN.tid, { note: DTN.v.trim() || null }), { close: 1 }) } });
}

/* ---------- 中のアクティビティ ---------- */
function dtKids(id, a, kids) {
  if (isPlan(a) || a.hol || isPay(a) || !(isSpan(a) || a.allDay || kids.length)) return '';
  return `<section>${kids.length ? `<div class="tl">${kids.map(([k]) => tlAct(k)).join('')}</div>` : ''}${btn('アクティビティを追加', `dtKidAdd(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
}
function dtKidAdd(id) {
  const a = A(id);
  menu('アクティビティを追加', [
    { label: '支払い', icon: 'yen', on: () => window.openPayEditor && openPayEditor({ parent: id, date: a.date, start: a.start }) },
    { label: '出来事(期間・瞬間)', icon: 'flag', on: () => openActEditor({ parent: id, date: a.date, state: 'done', start: a.start }) },
  ]);
}

/* ---------- ToDo・付いたメモ ---------- */
function dtTasksMemos(id, tid, a) {
  const ts = tasksOfAct(id).sort((x, y) => !!x[1].done - !!y[1].done);
  const ms = Object.entries(DB.memos).filter(([, m]) => m.act === id || m.act === tid).sort((x, y) => y[1].at.localeCompare(x[1].at));
  const addT = `dtTaskAdd(${q(tid)})`, addM = `dtMemoAdd(${q(tid)})`;
  const empties = [ts.length ? '' : btn('ToDoを追加', addT, 'dash', { icon: 'task' }), ms.length ? '' : btn('メモを付ける', addM, 'dash', { icon: 'memo' })].filter(Boolean);
  return `${ts.length ? `<section>${sec('ToDo', { n: ts.length, act: { label: '追加', on: addT } })}${rows(ts.map(([k]) => taskRow(k, { noList: 1 })).join(''))}</section>` : ''}
    ${ms.length ? `<section>${sec('付いたメモ', { n: ms.length, act: { label: '追加', on: addM } })}<div class="stack">${ms.map(([k]) => memoCard(k, { clamp: 4 })).join('')}</div></section>` : ''}
    ${empties.length ? `<div class="grid2">${empties.join('')}</div>` : ''}`;
}
let DTT = null;
function dtTaskAdd(tid) {
  if (window.openTaskEditor) return openTaskEditor({ act: tid });
  DTT = { tid, v: '' };
  openSheet({ title: 'ToDoを追加', body: field('やること', input({ id: 'dtt', ph: 'やること', oninput: 'DTT.v=this.value', auto: 1 })), dirty: () => !!DTT.v, primary: { label: '追加', on: () => done(taskAdd({ t: DTT.v, act: DTT.tid }), { close: 1 }) } });
}
let DTM = null;
function dtMemoAdd(tid) {
  DTM = { tid, v: '' };
  openSheet({ title: 'メモを付ける', body: textarea({ id: 'dtm', rows: 6, ph: 'メモ', oninput: 'DTM.v=this.value', auto: 1, label: 'メモ' }), dirty: () => !!DTM.v, primary: { label: '保存', on: () => done(memoAdd(DTM.v, { act: DTM.tid }), { close: 1 }) } });
}

/* ---------- その他の操作 ---------- */
function dtMenu(id) {
  const a = A(id), tid = dtTid(id), many = a.virt || a.rep;
  const items = [{ label: '直す', icon: 'edit', on: () => dtEdit(id) }];
  if (!many && !a.hol) items.push(a.parent ? { label: '活動の外に出す', icon: 'up', on: () => done(actMove(id, null)) } : { label: '活動の中に入れる', icon: 'down', on: () => dtMoveIn(id) });
  if (isPlan(a) && !a.pay && !a.virt && !a.hol && !isPay(a)) items.push({ label: 'ToDoにする', icon: 'task', on: () => { const r = planToTask(id); done(r, { go: r.ok ? '#/task/' + r.id : undefined, replace: 1 }); } });
  if (!isPay(a) && !a.hol) items.push({ label: '複製', sub: '同じ内容で今日に', icon: 'plus', on: () => dtDup(id) });
  items.push({ label: many ? 'すべての回を消す' : '消す', icon: 'trash', on: () => dtRemove(id) });
  menu(actTitle(a), items);
}
function dtEdit(id) { const a = A(id); if (isPay(a) && !isPlan(a)) return window.openPayEditor && openPayEditor({ id }); openActEditor({ id }); }
function dtMoveIn(id) {
  const a = DB.acts[id], ex = [id, ...descOf(id)];
  const c = Object.entries(DB.acts).filter(([k, p]) => !ex.includes(k) && !p.rep && !p.hol && p.kind !== 'pay' && isSpan(p) && p.date === a.date).sort(byTime);
  if (!c.length) return toast('同じ日に入れ先の記録がありません');
  pick({ title: '活動の中に入れる', items: c.map(([k, p]) => ({ k, label: actTitle(p), sub: timeText(p), lead: kindIc(p) })), on: k => done(actMove(id, k)) });
}
function dtDup(id) {
  const a = A(id), o = { kind: a.kind, title: a.title, date: TODAY, state: a.state, roles: (a.roles || []).map(r => ({ ...r })), note: a.note };
  if (a.allDay) { o.allDay = 1; if (a.date2) o.date2 = dAdd(TODAY, dDiff(a.date2, a.date)); } else { o.start = a.start; o.end = a.end; }
  const r = actAdd(o); done(r, { go: dtGoOf(r) });
}
function dtRemove(id) {
  const a = A(id), r = actRemove(dtTid(id));
  done(r, { go: a.parent && DB.acts[a.parent] ? '#/a/' + a.parent : '#/day/' + a.date, replace: 1 });
}

/* ================= 記録にする ================= */
let DTR = null, DTR0 = '';
function dtRecOpen(id) {
  const a = A(id); if (!a || !isPlan(a)) return;
  const pay = isPay(a), pl = (a.pay && a.pay.plan) || {};
  DTR = { id, pay, allDay: !pay && !!a.allDay, start: a.start || (pay ? (a.date === TODAY ? NOW : '09:00') : ''), end: a.end || '', amt: pl.amt || 0, how: pl.how || DB.methods[0] };
  DTR0 = JSON.stringify(DTR);
  const sum = rows(row({ lead: kindIc(a), t: actTitle(a), s: `予定 ${dtWhen(a)}` }));
  const fields = pay
    ? `${field('金額', input({ value: DTR.amt || '', mode: 'numeric', ph: '0', oninput: "dtRIn('amt',this.value)", auto: 1 }))}${field('支払い方法', select({ value: DTR.how, options: DB.methods.map(m => [m, m]), onchange: "dtRIn('how',this.value)" }))}${field('時刻', input({ type: 'time', value: DTR.start, oninput: "dtRIn('start',this.value)" }))}`
    : DTR.allDay ? '' : `<div class="grid2">${field('開始', input({ type: 'time', value: DTR.start, oninput: "dtRIn('start',this.value)" }))}${field('終了', input({ type: 'time', value: DTR.end, oninput: "dtRIn('end',this.value)" }))}</div>`;
  openSheet({ title: a.virt ? 'この回だけ記録にする' : '記録にする', body: `<div class="dt-ed">${sum}${fields}</div>`, dirty: () => JSON.stringify(DTR) !== DTR0, primary: { label: '記録にする', on: dtRecSave } });
}
function dtRIn(k, v) { DTR[k] = k === 'amt' ? +String(v).replace(/\D/g, '') || 0 : v; }
function dtRecSave() {
  const d = DTR, o = {};
  if (d.pay) { if (!d.amt) return toast('金額を入れてください', { bad: 1 }); Object.assign(o, { start: d.start, amt: d.amt, how: d.how }); }
  else if (!d.allDay) { o.start = d.start; o.end = d.end || undefined; }
  const r = planRecord(d.id, o);
  done(r, { close: 1, go: dtGoOf(r), replace: 1 });
}

/* ================= 出来事・予定の入力 ================= */
let DTE = null, DTE0 = '';
const DT_REPS = [['', 'なし'], ['day', '毎日'], ['week', '毎週'], ['month', '毎月']];
const DT_ALARMS = [['', 'なし'], ['10分前', '10分前'], ['30分前', '30分前'], ['1時間前', '1時間前'], ['前日', '前日']];

/** o = {id}(直す)か {state, date, parent, kind, start}(作る) */
function openActEditor(o = {}) {
  const seed = window._capSeed && window._capSeed.type === 'plan' ? window._capSeed : null;
  let d;
  if (o.id) {
    const a = A(o.id); if (!a) return toast('見つかりません', { bad: 1 });
    const tid = dtTid(o.id), t = DB.acts[tid] || a;
    d = { id: tid, tpl: !!(a.virt || t.rep), fixedKind: t.kind === 'pay' || t.kind === 'holiday', kind: t.kind, title: t.title || '', state: t.state, stateSet: 1, date: t.date, multi: !!t.date2, date2: t.date2 || '', allDay: !!t.allDay, start: t.start || '', end: t.end || '', rep: t.rep ? t.rep.u : '', alarm: t.alarm || '', roles: (t.roles || []).map(r => ({ ...r })), parent: t.parent || '', note: t.note || '', amt: t.pay && t.pay.plan ? t.pay.plan.amt : 0 };
  } else {
    const date = o.date || (seed && seed.date) || TODAY, state = o.state || (seed ? 'plan' : date > TODAY ? 'plan' : 'done'), kind = o.kind && KINDS[o.kind] ? o.kind : 'event';
    let s, e, allDay = false;
    if (seed) { s = seed.start || ''; e = seed.end || ''; allDay = !seed.start; }
    else if (o.start) { s = o.start; e = dtHM(hm2m(s) + 60); const p = o.parent && DB.acts[o.parent]; if (p && p.end && e > p.end) e = p.end; }
    else if (state === 'plan') { const nx = (Math.floor(hm2m(NOW) / 60) + 1) * 60; s = nx >= 1440 ? '23:00' : dtHM(nx); e = dtHM(nx >= 1440 ? 1439 : nx + 60); }
    else { s = dtHM(hm2m(NOW) - 60); e = NOW; }
    if (e <= s || KINDS[kind].moment) e = '';
    d = { id: '', kindSet: !!(o.kind && KINDS[o.kind]) || !!seed, tpl: false, fixedKind: false, kind, title: seed ? seed.title || '' : '', state, stateSet: !!(o.state || seed), date, multi: false, date2: '', allDay, start: s, end: e, rep: '', alarm: '', parent: o.parent || '', note: '', amt: 0,
      roles: seed ? (seed.ents || []).map(x => ({ r: isUnder(E(x).types[0], 'place') ? '場所' : isUnder(E(x).types[0], 'project') ? 'プロジェクト' : '同席', e: x })) : [] };
  }
  DTE = d; DTE0 = JSON.stringify(d);
  openSheet({ title: dtETitle(), tall: 1, body: dtEBody(), dirty: () => JSON.stringify(DTE) !== DTE0, primary: { label: dtELabel(), on: dtESave, disabled: dtENone() } });
}
const dtENone = () => !DTE.id && !DTE.title.trim() && DTE.kind === 'event' && !DTE.kindSet;
const dtETitle = () => DTE.id ? (DTE.tpl ? '繰り返しの予定を直す' : DTE.state === 'plan' ? '予定を直す' : '記録を直す') : DTE.state === 'plan' ? '予定を追加' : '出来事を記録';
const dtELabel = () => DTE.id ? '保存' : DTE.state === 'plan' ? '予定を追加' : '記録する';
const dtGroup = (label, html) => `<div class="field"><span class="field-l">${esc(label)}</span>${html}</div>`;
const dtWrap = h => h.replace('class="chips"', 'class="chips wrap"');
function dtParentCands() {
  const ex = DTE.id ? [DTE.id, ...descOf(DTE.id)] : [];
  return Object.entries(DB.acts).filter(([k, p]) => p.date === DTE.date && isSpan(p) && !p.rep && !p.hol && p.kind !== 'pay' && !ex.includes(k)).sort(byTime);
}
function dtEBody() {
  const d = DTE, k = KINDS[d.kind], plan = d.state === 'plan', par = d.parent && DB.acts[d.parent];
  const time = d.allDay
    ? `${rows(row({ t: '複数日', right: toggle(d.multi, 'dtEMulti()', '複数日') }))}${d.multi ? field('終わりの日', input({ type: 'date', value: d.date2, onchange: "dtEIn('date2',this.value)" })) : ''}`
    : `<div class="grid2">${field('開始', input({ type: 'time', value: d.start, oninput: "dtEIn('start',this.value)" }))}<label class="field"><span class="field-l" id="dte-endl">${d.end ? '終了' : '終了なし(瞬間)'}</span>${input({ type: 'time', value: d.end, oninput: "dtEIn('end',this.value)", label: '終了' })}</label></div>`;
  const cands = d.rep ? [] : dtParentCands();
  return `<div class="dt-ed">${d.fixedKind ? '' : dtGroup('種類', dtWrap(chips(Object.keys(KINDS).filter(x => x !== 'pay' && x !== 'holiday').map(x => [x, KINDS[x].n]), d.kind, 'dtEKind')))}
    ${field('題', input({ id: 'dte-t', value: d.title, ph: k.n, cls: 'big', oninput: "dtEIn('title',this.value)" }))}
    ${d.tpl || d.fixedKind ? '' : seg([['done', '記録'], ['plan', '予定']], d.state, 'dtEState')}
    ${field(d.tpl ? '始まりの日' : '日付', input({ type: 'date', value: d.date, onchange: 'dtEDate(this.value)' }))}
    ${rows(row({ t: '終日', right: toggle(d.allDay, 'dtEAll()', '終日') }))}
    ${time}
    ${d.kind === 'pay' ? field('金額', input({ value: d.amt || '', mode: 'numeric', ph: '0', oninput: "dtEIn('amt',this.value)" })) : ''}
    ${plan ? dtGroup('繰り返し', seg(DT_REPS, d.rep, 'dtERep')) + dtGroup('通知', dtWrap(chips(DT_ALARMS, d.alarm, 'dtEAlarm'))) : ''}
    ${dtGroup('関わった', `<div class="stack">${d.roles.length ? rows(d.roles.map((r, i) => `<div class="prop"><button class="prop-k dt-rk" onclick="dtERoleSet(${i})" aria-label="${esc(r.r)}の役割を直す">${esc(r.r)}</button><span class="prop-v"><button class="dt-rp" onclick="dtEPartner(${i})" aria-label="相手を選び直す">${ent(r.e, { name: r.name, link: false })}</button></span><button class="icon-btn" onclick="dtERoleDel(${i})" aria-label="${esc(roleName(r))}を外す">${ic('close')}</button></div>`)) : ''}${btn('足す', 'dtERoleAdd()', 'dash', { icon: 'plus' })}</div>`)}
    ${cands.length || par ? pickField('活動の中', par ? `${kindIc(par)}${esc(actTitle(par))}` : 'なし', 'dtEParent()', { emptyv: !par }) : ''}
    ${field('メモ書き', textarea({ value: d.note, rows: 3, ph: 'メモ', oninput: "dtEIn('note',this.value)" }))}</div>`;
}
const dtERefresh = () => setSheetBody(dtEBody());
const dtEHead = () => { setSheetTitle(dtETitle()); setSheetPrimary({ label: dtELabel(), on: dtESave, disabled: dtENone() }); };
function dtEIn(k, v) {
  const was = dtENone(); DTE[k] = k === 'amt' ? +String(v).replace(/\D/g, '') || 0 : v;
  if (k === 'title' && was !== dtENone()) dtEHead();
  if (k === 'end') { const el = document.getElementById('dte-endl'); if (el) el.textContent = v ? '終了' : '終了なし(瞬間)'; }
}
function dtEKind(k) { DTE.kind = k; DTE.kindSet = 1; dtEHead(); if (KINDS[k].moment) DTE.end = ''; dtERefresh(); }
function dtEState(s) { DTE.state = s; DTE.stateSet = 1; if (s === 'done') { DTE.rep = ''; DTE.alarm = ''; } dtERefresh(); dtEHead(); }
function dtEDate(v) {
  DTE.date = v;
  if (!DTE.stateSet && !DTE.id && isDate(v)) DTE.state = v > TODAY ? 'plan' : 'done';
  if (DTE.multi && isDate(v) && DTE.date2 <= v) DTE.date2 = dAdd(v, 1);
  if (DTE.parent && (!DB.acts[DTE.parent] || DB.acts[DTE.parent].date !== v)) DTE.parent = '';
  dtERefresh(); dtEHead();
}
function dtEAll() { DTE.allDay = !DTE.allDay; if (DTE.allDay) { DTE.start = ''; DTE.end = ''; } else { DTE.multi = false; DTE.date2 = ''; DTE.start = DTE.start || (DTE.date === TODAY ? NOW : '10:00'); } dtERefresh(); }
function dtEMulti() { DTE.multi = !DTE.multi; DTE.date2 = DTE.multi ? (DTE.date2 || dAdd(DTE.date, 1)) : ''; dtERefresh(); }
function dtERep(k) { DTE.rep = k; dtERefresh(); }
function dtEAlarm(k) { DTE.alarm = k; dtERefresh(); }
function dtERoleAdd() { dtRolePick(role => { DTE.roles.push(role); dtERefresh(); }); }
function dtERoleSet(i) { const cur = DTE.roles[i].r; pick({ title: '役割', cur, items: ROLES.map(r => ({ k: r, label: r })), on: r => { DTE.roles[i] = { ...DTE.roles[i], r }; dtERefresh(); } }); }
function dtEPartner(i) { dtEntPick(DTE.roles[i].r, role => { DTE.roles[i] = role; dtERefresh(); }); }
function dtERoleDel(i) { DTE.roles.splice(i, 1); dtERefresh(); }
function dtEParent() {
  pick({ title: '活動の中', cur: DTE.parent || null, none: { label: 'なし' }, items: dtParentCands().map(([k, p]) => ({ k, label: actTitle(p), sub: timeText(p), lead: kindIc(p) })), on: k => { DTE.parent = k || ''; dtERefresh(); } });
}
function dtESave() {
  const d = DTE, plan = d.state === 'plan', o = { kind: d.kind, title: d.kind === 'pay' || d.kind === 'holiday' ? d.title : d.title.trim() || KINDS[d.kind].n, date: d.date, state: d.state, allDay: d.allDay ? 1 : undefined, roles: d.roles, note: d.note.trim() || undefined, parent: d.parent || undefined, rep: plan && d.rep ? { u: d.rep } : undefined, alarm: plan && d.alarm ? d.alarm : undefined };
  if (d.allDay) o.date2 = d.multi && d.date2 ? d.date2 : undefined;
  else { o.start = d.start; o.end = KINDS[d.kind].moment ? undefined : d.end || undefined; o.date2 = undefined; }
  if (d.kind === 'pay' && d.id) { if (!d.amt) return toast('金額を入れてください', { bad: 1 }); const a = DB.acts[d.id]; o.pay = { ...a.pay, plan: { ...(a.pay.plan || {}), amt: d.amt } }; }
  const r = d.id ? actUpdate(d.id, o) : actAdd(o);
  done(r, { close: 1, go: d.id ? undefined : dtGoOf(r) });
}
