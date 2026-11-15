/* 個人記録 v6 — 詳細(記録・予定の詳細 #/a/{id})と、出来事・予定の入力 openActEditor。接頭辞 dt */
'use strict';
const dtGoOf = r => r && r.ok && r.id ? '#/a/' + r.id : undefined;
const dtHM = m => m2hm(Math.max(0, Math.min(1439, m)));
const dtM = t => t === '24:00' ? 1440 : hm2m(t);
/** 時刻 TS の短い表記(終日 = 日だけ) */
const dtTs = ts => hasT(ts) ? `${fmtMD(dOf(ts))} ${tOf(ts)}` : fmtMD(dOf(ts));
/** 取り消しをまとめる(後の操作が失敗したら前の操作も戻す) */
function dtChain(label, ...steps) {
  const done_ = [];
  for (const f of steps) { const r = f(); if (!r || !r.ok) { done_.reverse().forEach(x => x.undo && x.undo()); return r || { ok: false, why: '失敗しました' }; } done_.push(r); }
  const last = done_[done_.length - 1];
  return { ok: true, label, id: last.id, undo: () => done_.slice().reverse().forEach(x => x.undo && x.undo()) };
}

/* ================= 詳細 ================= */
route('a', id => {
  const a = A(id);
  if (!a) return `<div class="screen s-detail">${bar('見つかりません')}<div class="card">${empty({ icon: 'day', text: 'この記録は見つかりません' })}</div>${btn('戻る', 'goBack()', 'sec', { icon: 'back', wide: 1 })}</div>`;
  const more = iconBtn('more', 'その他の操作', `dtMenu(${q(id)})`);
  if (isPay(a) && !isPlan(a)) return `<div class="screen s-detail s-detail-pay">${bar(actTitle(a), { actions: more })}${dtUnsure(id, a)}${window.payDetail ? payDetail(id) : paySlip(id, { noLink: 1 })}${dtPhotos(id, a)}</div>`;
  if (a.kind === 'settle' && a.settle) return `<div class="screen s-detail">${bar(actTitle(a), { actions: more })}${dtEnv(ancOf(id), dtSettle(id, a))}${dtPays(a)}${dtNoteBlock(id, a)}</div>`;
  const plan = isPlan(a), kids = kidsOf(id);
  return `<div class="screen s-detail">
    ${bar(actTitle(a), { actions: more })}
    ${dtUnsure(id, a)}
    ${dtEnv(ancOf(id), dtHero(id, a))}
    ${plan ? btn(a.virt ? 'この回だけ記録にする' : '記録にする', `dtRecOpen(${q(id)})`, 'pri', { icon: 'check', wide: 1 }) : ''}
    ${dtRoles(id, a)}${dtNoteBlock(id, a)}${dtPhotos(id, a)}${dtRecords(id, a)}${dtKids(id, a, kids)}${dtTasksMemos(id, a)}
  </div>`;
});

/** 未確認の帯(確かめる / 雑記を見る) */
function dtUnsure(id, a) {
  if (!a || a.virt || !isUnsure(a)) return '';
  return banner(`未確認です。内容を確かめてください<div class="dt-cf">${btn('確かめる', `done(confirmRows([{t:'acts',k:${q(id)}}]))`, 'pri', { sm: 1, icon: 'check' })}${btn('雑記を見る', `window.openInbox && openInbox({focus:{t:'acts',k:${q(id)}}})`, 'sec', { sm: 1 })}</div>`, 'info');
}
/** 親の連なり: 外側から順に封筒のように入れ子にして、いちばん内側に自分 */
function dtEnv(anc, inner) {
  return anc.reduceRight((acc, [pid, p]) => `<div class="dt-env"><a class="dt-env-h" href="#/a/${pid}">${kindIc(p)}<b>${esc(actTitle(p))}</b><span class="num">${timeText(p)}</span>${ic('chev', 'row-chev')}</a>${acc}</div>`, inner);
}
/** 時刻の言葉 */
function dtWhen(a) {
  const d = aDate(a), d2 = aDate2(a);
  if (isAllDay(a)) return d2 ? `${fmtMD(d)}–${fmtMD(d2)} · ${dDiff(d2, d) + 1}日間` : '終日';
  if (!a.end) return aStart(a);
  return `${d2 ? fmtMD(d) + ' ' : ''}${aStart(a)}–${d2 ? fmtMD(d2) + ' ' : ''}${aEnd(a)} · ${durJ(actMins(a))}`;
}
/** 空の帯の上で、この記録の場所に付ける枠 */
function dtMark(a) {
  const sp = spanOn(a, aDate(a)); if (!sp) return '';
  const x = (dtM(sp.s) / 14.4).toFixed(2);
  if (a.end && dtM(sp.e) > dtM(sp.s)) return `<i class="dt-mark" style="left:calc(${x}% - 3px);width:calc(${Math.max(.6, (dtM(sp.e) - dtM(sp.s)) / 14.4).toFixed(2)}% + 6px)"></i>`;
  return `<i class="dt-mark m" style="left:${x}%"></i>`;
}
function dtHero(id, a) {
  const plan = isPlan(a), late = isLate(a), st = plan ? (late ? 'late' : 'plan') : 'done', k = KINDS[a.kind], title = actTitle(a), d = aDate(a);
  const r = a.routine ? DB.routines[a.routine] : null, rep = repText(r), tags = plan ? tag(late ? '過ぎた予定' : '予定', late ? 'miss' : 'plan') : '';
  const nx = r ? repNext(r, d >= TODAY ? dAdd(d, 1) : TODAY) : '', diary = dayOf(d).diary;
  const rel = plan && !late ? (d === TODAY ? (hasT(a.start) && aStart(a) > NOW ? 'あと ' + durJ(hm2m(aStart(a)) - hm2m(NOW)) : '') : d > TODAY ? relDay(d) : '') : '';
  const meta = [nx && plan ? `<span class="mini">${ic('repeat')}次回 ${fmtMD(nx)}(${dowJ(nx)})</span>` : '', rep ? `<span class="mini">${ic('repeat')}${esc(rep)}</span>` : '', a.alarm && plan ? `<span class="mini">${ic('bell')}${esc(a.alarm)}</span>` : ''].join('');
  const x = a.pay && plan ? xOf(id) : null;
  const pl = a.planned && !plan ? `<div class="mini num">予定は ${esc(dtTs(a.planned.start))}${a.planned.end ? '–' + esc(dtTs(a.planned.end)) : ''}</div>` : '';
  const wake = a.kind === 'sleep' && !a.end && !plan ? btn('起きた', `dtWake(${q(id)})`, 'pri', { icon: 'sun', wide: 1 }) : '';
  return `<div class="dt-hero ${st}">
    <div class="dt-k">${kindIc(a)}${title !== k.n ? `<span>${esc(k.n)}</span>` : ''}${tags}</div>
    <h1 class="dt-t" data-bar-watch>${esc(title)}</h1>
    <div class="dt-when num">${esc(dtWhen(a))}${rel ? `<small class="dt-rel">${esc(rel)}</small>` : ''}</div>
    ${a.pay && plan ? `<div class="dt-amt">${amt(a.pay.amt || 0, { cls: 'big' })}${x && x.cat ? catTag(x.cat) : ''}</div>` : ''}
    ${pl}${wake}
    <a class="dt-date" href="#/day/${d}">${ic('cal')}<span>${fmtFull(d)}</span>${rel ? '' : `<small>${relDay(d)}</small>`}${ic('chev', 'row-chev')}</a>
    ${diary ? `<a class="dt-diary clamp" href="#/day/${d}">${esc(diary)}</a>` : ''}
    ${meta ? `<div class="dt-meta">${meta}</div>` : ''}
    <div class="dt-sky">${skyBand(d)}${dtMark(a)}</div>
  </div>`;
}
function dtWake(id) { done(actUpdate(id, { end: nowStamp() })); }

/* ---------- 精算 ---------- */
function dtSettle(id, a) {
  const s = a.settle, d = aDate(a), who = s.who && E(s.who) ? ent(s.who) : '';
  return `<div class="dt-hero done">
    <div class="dt-k">${kindIc(a)}<span>${esc(KINDS.settle.n)}</span>${tag(s.dir === 'in' ? '受け取った' : '返した')}</div>
    <h1 class="dt-t" data-bar-watch>${esc(actTitle(a))}</h1>
    <div class="dt-amt">${amt(s.amt, { cls: 'big' })}</div>
    <div class="dt-when num">${esc(dtWhen(a))}</div>
    ${rows([who ? prop('相手', who) : '', s.how ? prop('方法', esc(methodName(s.how))) : ''].join(''))}
    <a class="dt-date" href="#/day/${d}">${ic('cal')}<span>${fmtFull(d)}</span><small>${relDay(d)}</small>${ic('chev', 'row-chev')}</a>
  </div>`;
}
/** 精算の対象の支払い(押すとその支払いへ) */
function dtPays(a) {
  const ps = (a.settle.pays || []).filter(p => A(p));
  if (!ps.length) return '';
  return `<section>${sec('対象の支払い', { n: ps.length })}<div class="stack">${ps.map(p => paySlip(p)).join('')}</div></section>`;
}

/* ---------- 関わったエンティティ ---------- */
const dtRolesOf = id => ((A(id) || {}).roles || []);
function dtRoles(id, a) {
  const groups = [];
  (a.roles || []).forEach((r, i) => { let g = groups.find(x => x.r === r.r); if (!g) groups.push(g = { r: r.r, items: [] }); g.items.push([r, i]); });
  if (!groups.length) return `<section>${btn('人・場所を足す', `dtRoleAdd(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
  const item = ([r, i]) => `<span class="dt-ro">${ent(r.e)}${isStub(r.e) ? `<button class="dt-reg" onclick="dtReg(${q(id)},${i})">登録する</button>` : ''}<button class="dt-rx" onclick="dtRoleDel(${q(id)},${i})" aria-label="${esc(roleName(r))}を外す">${ic('close')}</button></span>`;
  return `<section>${sec('関わった', { act: { label: '足す', on: `dtRoleAdd(${q(id)})` } })}${rows(groups.map(g => `<div class="prop"><button class="prop-k dt-rk" onclick="dtRoleRename(${q(id)},[${g.items.map(x => x[1])}])" aria-label="${esc(g.r)}の役割を直す">${esc(g.r)}</button><span class="prop-v">${g.items.map(item).join('')}</span></div>`))}</section>`;
}
/** 役割名だけ選び直す(相手はそのまま) */
function dtRoleRename(id, idxs) {
  const rs = dtRolesOf(id), cur = (rs[idxs[0]] || {}).r;
  pick({ title: '役割', cur, items: ROLES.map(r => ({ k: r, label: r })), on: r => { if (r === cur) return; done(actUpdate(id, { roles: rs.map((x, j) => idxs.includes(j) ? { ...x, r } : x) })); } });
}
/** 名前だけの相手を、場所・人物・店として登録する(エンティティの stub を外す) */
function dtReg(id, i) {
  const role = dtRolesOf(id)[i]; if (!role || !isStub(role.e)) return;
  const e = E(role.e);
  if (e.types && e.types.length) return dtRegDo(role.e, e.types[0]);
  const guess = ['同席', '参加'].includes(role.r) ? 'person' : 'place';
  pick({ title: `「${e.n}」を登録`, cur: guess, items: [['place', '場所', 'place'], ['person', '人物', 'user'], ['shop', '店', 'bag']].filter(([k]) => DB.types[k]).map(([k, l, g]) => ({ k, label: l, lead: ic(g) })), on: t => dtRegDo(role.e, t) });
}
function dtRegDo(eid, type) {
  const e = E(eid); if (!e || !e.stub) return;
  done(entUpdate(eid, { types: e.types && e.types.length ? e.types : [type], stub: null }), { msg: `「${e.n}」を登録しました`, act: window.openEntEditor ? { label: '詳しく直す', on: `openEntEditor({id:${q(eid)}})` } : undefined });
}
/** 役割 → 相手の順に選ぶ。cb({r, e} か {r, name}) */
function dtRolePick(cb) {
  pick({ title: '役割', items: ROLES.map(r => ({ k: r, label: r })), on: r => dtEntPick(r, cb) });
}
function dtEntPick(r, cb) {
  const root = ['場所', '出発', '到着'].includes(r) ? 'place' : ['同席', '参加'].includes(r) ? 'person' : r === 'プロジェクト' ? 'project' : null;
  const fit = id => root && DB.ents[id].types[0] && isUnder(DB.ents[id].types[0], root) ? 1 : 0;
  const ids = entsAll({ stubs: true }).filter(id => !DB.ents[id].types.includes('method')).sort((x, y) => fit(y) - fit(x));
  pick({
    title: `${r}を選ぶ`, search: true,
    items: ids.map(id => ({ k: id, label: DB.ents[id].n, sub: DB.ents[id].stub ? '未登録' : typePath(DB.ents[id].types[0]), lead: avatar(id) })),
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
function dtRoleAdd(id) { dtRolePick(role => done(actUpdate(id, { roles: [...dtRolesOf(id), role] }))); }
function dtRoleDel(id, i) { done(actUpdate(id, { roles: dtRolesOf(id).filter((_, j) => j !== i) })); }

/* ---------- 記録に付いた写真 ---------- */
function dtPhotos(id, a) {
  if (!a || isPlan(a) || a.virt || a.kind === 'settle') return '';
  const ps = photosOfAct(id);
  if (!ps.length) return `<section>${btn('写真を足す', `dtPhotoAdd(${q(id)})`, 'dash', { icon: 'camera' })}</section>`;
  return `<section>${sec('写真', { n: ps.length })}<div class="photos">${ps.map(([pid, p]) => `<button class="photo" style="--c:${skyAt(tOf(p.at))}" onclick="dtPhotoDel(${q(pid)})" aria-label="${esc(p.cap || '写真')}を外す">${ic('photo')}<span class="dt-pc"><b>${esc(p.cap || '')}</b><time>${tOf(p.at)}</time></span></button>`).join('')}<button class="photo add" onclick="dtPhotoAdd(${q(id)})" aria-label="写真を足す">${ic('camera')}<span class="dt-pc">写真を足す</span></button></div></section>`;
}
let DTP = null;
function dtPhotoAdd(id) {
  DTP = { id, v: '' };
  openSheet({ title: '写真を足す', body: field('ひとこと', input({ id: 'dtp', ph: 'ひとこと', oninput: 'DTP.v=this.value', auto: 1 })), dirty: () => !!DTP.v, primary: { label: '足す', on: () => { const a = DB.acts[DTP.id]; if (!a) return; done(photoAdd({ act: DTP.id, cap: DTP.v.trim(), ...(hasT(a.start) ? { at: a.start } : { d: aDate(a) }) }), { close: 1 }); } } });
}
function dtPhotoDel(pid) { done(photoRemove(pid)); }

/* ---------- 繰り返しの予定: 記録になった回 ---------- */
const DTMORE = {};
function dtRecords(id, a) {
  const rid = a.routine; if (!rid || !DB.routines[rid]) return '';
  const rs = recordsOfRoutine(rid).filter(([k]) => k !== id); if (!rs.length) return '';
  const shown = DTMORE[rid] ? rs : rs.slice(0, 5);
  return `<section>${sec('記録になった回', { n: rs.length })}${rows(shown.map(([k, r]) => row({ lead: kindIc(r), t: `${fmtMD(aDate(r))}(${dowJ(aDate(r))})`, s: hasT(r.start) ? aStart(r) : '', right: isPay(r) ? amt(payTotal(r)) : '', href: '#/a/' + k, chev: 1 })))}${rs.length > shown.length ? btn('もっと見る', `dtMoreRec(${q(rid)})`, 'ghost', { wide: 1 }) : ''}</section>`;
}
function dtMoreRec(rid) { DTMORE[rid] = 1; rerender(); }

/* ---------- メモ書き ---------- */
const dtNoteBlock = (id, a) => `<section>${a.note ? `<button class="card pad dt-note" onclick="dtNote(${q(id)})"><p>${esc(a.note)}</p></button>` : btn('メモを書く', `dtNote(${q(id)})`, 'dash', { icon: 'pencil' })}</section>`;
let DTN = null, DTN0 = '';
function dtNote(id) {
  DTN = { id, v: (A(id) || {}).note || '' }; DTN0 = DTN.v;
  openSheet({ title: 'メモ書き', body: textarea({ id: 'dtn', value: DTN.v, rows: 7, oninput: 'DTN.v=this.value', auto: 1, label: 'メモ書き' }), dirty: () => DTN.v !== DTN0, primary: { label: '保存', on: () => done(actUpdate(DTN.id, { note: DTN.v.trim() || null }), { close: 1 }) } });
}

/* ---------- 中のアクティビティ ---------- */
function dtKids(id, a, kids) {
  if (isPlan(a) || isPay(a) || ['sleep', 'settle'].includes(a.kind) || !(isSpan(a) || isAllDay(a) || kids.length)) return '';
  return `<section>${kids.length ? `<div class="tl">${kids.map(([k]) => tlAct(k)).join('')}</div>` : ''}${btn('アクティビティを追加', `dtKidAdd(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
}
function dtKidAdd(id) {
  const a = A(id);
  menu('アクティビティを追加', [
    { label: '支払い', icon: 'yen', on: () => window.openPayEditor && openPayEditor({ parent: id, date: aDate(a), start: a.start }) },
    { label: '出来事(期間・瞬間)', icon: 'flag', on: () => openActEditor({ parent: id, state: 'done', start: a.start }) },
  ]);
}

/* ---------- ToDo・付いたメモ(仮の回は決まりに付ける) ---------- */
function dtTasksMemos(id, a) {
  const tid = a.virt ? a.routine : id;
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
  const a = A(id), items = [{ label: '直す', icon: 'edit', on: () => dtEdit(id) }], settle = a.kind === 'settle';
  if (!a.virt && !settle) items.push(a.parent ? { label: '活動の外に出す', icon: 'up', on: () => done(actMove(id, null)) } : { label: '活動の中に入れる', icon: 'down', on: () => dtMoveIn(id) });
  if (isPlan(a) && !isPay(a)) items.push({ label: 'ToDoにする', sub: a.virt ? '繰り返しごと' : undefined, icon: 'task', on: () => { const r = planToTask(id); done(r, { go: r.ok ? '#/task/' + r.id : undefined, replace: 1 }); } });
  if (!isPay(a) && !settle) items.push({ label: '複製', sub: '同じ内容で今日に', icon: 'plus', on: () => dtDup(id) });
  if (a.virt) {
    items.push({ label: 'この回だけ無しにする', icon: 'close', on: () => dtRemove(id) });
    items.push({ label: '繰り返しごと消す', icon: 'trash', on: () => dtRemoveAll(id) });
  } else items.push({ label: '消す', icon: 'trash', on: () => dtRemove(id) });
  menu(actTitle(a), items);
}
function dtEdit(id) { const a = A(id); if (isPay(a) && !isPlan(a)) return window.openPayEditor && openPayEditor({ id }); openActEditor({ id }); }
/** 入れ先の候補: 親の日に掛かる期間の記録 */
function dtMoveIn(id) {
  const a = DB.acts[id], ex = [id, ...descOf(id)], d = aDate(a);
  const c = Object.entries(DB.acts).filter(([k, p]) => !ex.includes(k) && !isPay(p) && !['sleep', 'settle'].includes(p.kind) && isSpan(p) && aDate(p) <= d && d <= dOf(p.end)).sort(byTime);
  if (!c.length) return toast('同じ日に入れ先の記録がありません');
  pick({ title: '活動の中に入れる', items: c.map(([k, p]) => ({ k, label: actTitle(p), sub: timeText(p), lead: kindIc(p) })), on: k => done(actMove(id, k)) });
}
function dtDup(id) {
  const a = A(id), o = { kind: a.kind, title: a.title, state: a.state, roles: (a.roles || []).map(r => ({ ...r })), note: a.note };
  const span = a.end ? dDiff(dOf(a.end), aDate(a)) : 0;
  o.start = hasT(a.start) ? tsOf(TODAY, aStart(a)) : TODAY;
  if (a.end) o.end = hasT(a.end) ? tsOf(dAdd(TODAY, span), aEnd(a)) : dAdd(TODAY, span);
  const r = actAdd(o); done(r, { go: dtGoOf(r) });
}
function dtGoAfter(a) { return a.parent && DB.acts[a.parent] ? '#/a/' + a.parent : '#/day/' + aDate(a); }
/** 消す(仮の回はその回だけ無し) */
function dtRemove(id) { const a = A(id); done(actRemove(id), { go: dtGoAfter(a), replace: 1 }); }
/** 繰り返しごと消す */
function dtRemoveAll(id) { const a = A(id); done(routineRemove(a.routine), { go: '#/day/' + aDate(a), replace: 1 }); }

/* ================= 記録にする ================= */
let DTR = null, DTR0 = '';
function dtRecOpen(id) {
  const a = A(id); if (!a || !isPlan(a)) return;
  const pay = isPay(a), d = aDate(a);
  DTR = { id, pay, allDay: !pay && isAllDay(a), sd: d, st: hasT(a.start) ? aStart(a) : (pay ? (d === TODAY ? NOW : '09:00') : ''), et: aEnd(a), amt: pay ? (a.pay.amt || 0) : 0, how: methodDefault() };
  DTR.et0 = DTR.et; DTR.st0 = DTR.st;
  DTR0 = JSON.stringify(DTR);
  const sum = rows(row({ lead: kindIc(a), t: actTitle(a), s: `予定 ${dtWhen(a)}` }));
  const fields = pay
    ? `${field('金額', input({ value: DTR.amt || '', mode: 'numeric', ph: '0', oninput: "dtRIn('amt',this.value)", auto: 1 }))}${field('支払い方法', select({ value: DTR.how, options: methodsAll().map(([k, e]) => [k, e.n]), onchange: "dtRIn('how',this.value)" }))}${field('時刻', input({ type: 'time', value: DTR.st, oninput: "dtRIn('st',this.value)" }))}`
    : DTR.allDay ? '' : `<div class="grid2">${field('開始', input({ type: 'time', value: DTR.st, oninput: "dtRIn('st',this.value)" }))}${field('終了', input({ type: 'time', value: DTR.et, oninput: "dtRIn('et',this.value)" }))}</div>`;
  openSheet({ title: a.virt ? 'この回だけ記録にする' : '記録にする', body: `<div class="dt-ed">${sum}${fields}</div>`, dirty: () => JSON.stringify(DTR) !== DTR0, primary: { label: '記録にする', on: dtRecSave } });
}
function dtRIn(k, v) { DTR[k] = k === 'amt' ? +String(v).replace(/\D/g, '') || 0 : v; }
function dtRecSave() {
  const d = DTR, o = {};
  if (d.pay) { if (!d.amt) return toast('金額を入れてください', { bad: 1 }); Object.assign(o, { start: d.st, amt: d.amt, how: d.how }); }
  else if (!d.allDay) {
    if (!d.st) return toast('開始の時刻を入れてください', { bad: 1 });
    o.start = tsOf(d.sd, d.st);
    if (d.et !== d.et0) o.end = d.et ? tsOf(d.et < d.st ? dAdd(d.sd, 1) : d.sd, d.et) : '';
  }
  const r = planRecord(d.id, o);
  done(r, { close: 1, go: dtGoOf(r), replace: 1 });
}

/* ================= 出来事・予定の入力 ================= */
let DTE = null, DTE0 = '';
const DT_REPS = [['', 'なし'], ['day', '毎日'], ['week', '毎週'], ['month', '毎月']];
const DT_ALARMS = [['', 'なし'], ['10分前', '10分前'], ['30分前', '30分前'], ['1時間前', '1時間前'], ['前日', '前日']];
const DT_FIXED = ['pay', 'settle'];
/** 分を足した TS('YYYY-MM-DDTHH:MM') */
function dtAddMin(ts, m) { const t = tsMin(ts) + m, dd = Math.floor(t / 1440), r = t - dd * 1440; return tsOf(dAdd('1970-01-01', dd), m2hm(r)); }
/** 入力 o の start/end('HH:MM' か TS)と date から 日・時刻を取り出す */
function dtSplit(v, d0) { return isTS(v) ? { d: dOf(v), t: tOf(v) } : isHM(v) ? { d: d0, t: v } : { d: d0, t: '' }; }

/** o = {id}(直す)か {state, date, parent, kind, start, end}(作る。start/end は TS か 'HH:MM') */
function openActEditor(o = {}) {
  const seed = window._capSeed && window._capSeed.type === 'plan' ? window._capSeed : null;
  let d;
  if (o.id) {
    const a = A(o.id); if (!a) return toast('見つかりません', { bad: 1 });
    const r = a.virt ? DB.routines[a.routine] : null, sd = r ? r.from : aDate(a), allDay = isAllDay(a), d2 = aDate2(a);
    d = { id: o.id, tpl: !!r, kind: a.kind, title: a.title || '', state: a.state, stateSet: 1, sd, allDay, st: aStart(a), et: aEnd(a), ed: allDay ? d2 || '' : (a.end && d2 ? d2 : ''), multi: allDay && !!d2, rep: r ? r.every : '', alarm: a.alarm || '', roles: (a.roles || []).map(x => ({ r: x.r, e: x.e })), parent: a.parent || '', note: a.note || '', amt: a.pay ? a.pay.amt || 0 : 0, real: !a.virt && !a.routine };
    if (r) d.fixedRep = a.routine;
  } else {
    const s0 = o.start || (seed && seed.start), e0 = o.end || (seed && seed.end);
    const date = o.date || (s0 && isTS(s0) ? dOf(s0) : TODAY), state = o.state || (seed ? 'plan' : date > TODAY ? 'plan' : 'done'), kind = o.kind && KINDS[o.kind] && !DT_FIXED.includes(o.kind) ? o.kind : 'event';
    let sp = dtSplit(s0, date), ep = dtSplit(e0, sp.d), allDay = false, st, et, ed = '';
    if (seed) { st = sp.t; et = ep.t; allDay = !sp.t; if (ep.d !== sp.d) ed = ep.d; }
    else if (sp.t) {
      st = sp.t; et = ep.t || tOf(dtAddMin(tsOf(sp.d, sp.t), 60)); if (!ep.t && dOf(dtAddMin(tsOf(sp.d, sp.t), 60)) !== sp.d) et = '';
      const p = o.parent && DB.acts[o.parent]; if (p && p.end && hasT(p.end) && et && tsOf(sp.d, et) > p.end) et = tOf(p.end);
      if (ep.t && ep.d !== sp.d) ed = ep.d;
    }
    else if (state === 'plan') { const nx = (Math.floor(hm2m(NOW) / 60) + 1) * 60; st = nx >= 1440 ? '23:00' : dtHM(nx); et = dtHM(nx >= 1440 ? 1439 : nx + 60); }
    else { st = dtHM(hm2m(NOW) - 60); et = NOW; }
    if ((KINDS[kind] || {}).moment || (!ed && et && et <= st && !(e0 && ep.t))) et = '';
    d = { id: '', kindSet: !!(o.kind && KINDS[o.kind]) || !!seed, tpl: false, kind, title: seed ? seed.title || '' : '', state, stateSet: !!(o.state || seed), sd: sp.d, allDay, st: allDay ? '' : st, et: allDay ? '' : et, ed, multi: false, rep: '', alarm: '', parent: o.parent || '', note: '', amt: 0, real: false,
      roles: seed ? (seed.ents || []).map(x => ({ r: isUnder(E(x).types[0], 'place') ? '場所' : isUnder(E(x).types[0], 'project') ? 'プロジェクト' : '同席', e: x })) : [] };
  }
  DTE = d; DTE0 = JSON.stringify(d);
  openSheet({ title: dtETitle(), tall: 1, body: dtEBody(), dirty: () => JSON.stringify(DTE) !== DTE0, primary: { label: dtELabel(), on: dtESave, disabled: dtENone() } });
}
const dtFixed = () => DT_FIXED.includes(DTE.kind);
const dtENone = () => !DTE.id && !DTE.title.trim() && DTE.kind === 'event' && !DTE.kindSet;
const dtETitle = () => DTE.id ? (DTE.tpl ? '繰り返しの予定を直す' : DTE.state === 'plan' ? '予定を直す' : '記録を直す') : DTE.state === 'plan' ? '予定を追加' : '出来事を記録';
const dtELabel = () => DTE.id ? '保存' : DTE.state === 'plan' ? '予定を追加' : '記録する';
const dtGroup = (label, html) => `<div class="field"><span class="field-l">${esc(label)}</span>${html}</div>`;
const dtWrap = h => h.replace('class="chips"', 'class="chips wrap"');
/** 終わりの日(時刻ありの時)。指定が無ければ、始まりと同じ日。終わりの時刻が始まりより前なら翌日 */
function dtEffEd() {
  const d = DTE; if (!d.et) return '';
  if (d.ed) return d.ed;
  return d.st && d.et < d.st ? dAdd(d.sd, 1) : d.sd;
}
function dtParentCands() {
  const ex = DTE.id ? [DTE.id, ...descOf(DTE.id)] : [];
  return Object.entries(DB.acts).filter(([k, p]) => isSpan(p) && aDate(p) <= DTE.sd && DTE.sd <= dOf(p.end) && !isPay(p) && !['sleep', 'settle'].includes(p.kind) && !ex.includes(k)).sort(byTime);
}
function dtEBody() {
  const d = DTE, k = KINDS[d.kind] || KINDS.event, plan = d.state === 'plan', par = d.parent && DB.acts[d.parent], fx = dtFixed(), moment = k.moment;
  const eff = dtEffEd(), nx = eff && eff !== d.sd;
  const time = d.allDay
    ? `${rows(row({ t: '複数日', right: toggle(d.multi, 'dtEMulti()', '複数日') }))}${d.multi ? field('終わりの日', input({ type: 'date', value: d.ed, onchange: "dtEIn('ed',this.value)" })) : ''}`
    : `<div class="grid2">${field('開始', input({ type: 'time', value: d.st, oninput: "dtEIn('st',this.value)" }))}${moment ? '' : `<label class="field"><span class="field-l" id="dte-endl">${d.et ? '終了' : '終了なし(瞬間)'}</span>${input({ type: 'time', value: d.et, oninput: "dtEIn('et',this.value)", label: '終了' })}</label>`}</div>
      ${moment || d.tpl ? '' : `<div class="field" id="dte-edw"${d.et ? '' : ' style="display:none"'}><span class="field-l" id="dte-edl">終わりの日${nx ? ' · 日をまたぐ' : ''}</span>${input({ id: 'dte-ed', type: 'date', value: eff, onchange: "dtEIn('edv',this.value)", label: '終わりの日' })}</div>`}`;
  const cands = d.tpl || d.rep ? [] : dtParentCands();
  return `<div class="dt-ed">${fx ? '' : dtGroup('種類', dtWrap(chips(Object.keys(KINDS).filter(x => !DT_FIXED.includes(x)).map(x => [x, KINDS[x].n]), d.kind, 'dtEKind')))}
    ${fx && d.kind === 'settle' ? '' : field('題', input({ id: 'dte-t', value: d.title, ph: k.n, cls: 'big', oninput: "dtEIn('title',this.value)" }))}
    ${d.tpl || fx ? '' : seg([['done', '記録'], ['plan', '予定']], d.state, 'dtEState')}
    ${field(d.tpl ? '始まりの日' : '日付', input({ type: 'date', value: d.sd, onchange: 'dtEDate(this.value)' }))}
    ${rows(row({ t: '終日', right: toggle(d.allDay, 'dtEAll()', '終日') }))}
    ${time}
    ${d.kind === 'pay' ? field('金額', input({ value: d.amt || '', mode: 'numeric', ph: '0', oninput: "dtEIn('amt',this.value)" })) : ''}
    ${plan && (d.tpl || !d.id || d.real) && !fx ? dtGroup('繰り返し', seg(DT_REPS, d.rep, 'dtERep')) : ''}${plan && !fx ? dtGroup('通知', dtWrap(chips(DT_ALARMS, d.alarm, 'dtEAlarm'))) : ''}
    ${d.kind === 'settle' ? '' : dtGroup('関わった', `<div class="stack">${d.roles.length ? rows(d.roles.map((r, i) => `<div class="prop"><button class="prop-k dt-rk" onclick="dtERoleSet(${i})" aria-label="${esc(r.r)}の役割を直す">${esc(r.r)}</button><span class="prop-v"><button class="dt-rp" onclick="dtEPartner(${i})" aria-label="相手を選び直す">${ent(r.e, { name: r.name, link: false })}</button></span><button class="icon-btn" onclick="dtERoleDel(${i})" aria-label="${esc(roleName(r) || r.name || r.r)}を外す">${ic('close')}</button></div>`)) : ''}${btn('足す', 'dtERoleAdd()', 'dash', { icon: 'plus' })}</div>`)}
    ${cands.length || par ? pickField('活動の中', par ? `${kindIc(par)}${esc(actTitle(par))}` : 'なし', 'dtEParent()', { emptyv: !par }) : ''}
    ${field('メモ書き', textarea({ value: d.note, rows: 3, ph: 'メモ', oninput: "dtEIn('note',this.value)" }))}</div>`;
}
const dtERefresh = () => setSheetBody(dtEBody());
const dtEHead = () => { setSheetTitle(dtETitle()); setSheetPrimary({ label: dtELabel(), on: dtESave, disabled: dtENone() }); };
/** 終わりの時刻・始まりの時刻を打った時、終わりの日の欄だけ描き直す(入力欄は作り直さない) */
function dtESyncEnd() {
  const d = DTE, w = document.getElementById('dte-edw'), el = document.getElementById('dte-ed'), l = document.getElementById('dte-edl'), el2 = document.getElementById('dte-endl'), eff = dtEffEd();
  if (w) w.style.display = d.et ? '' : 'none';
  if (el) el.value = eff;
  if (l) l.textContent = '終わりの日' + (eff && eff !== d.sd ? ' · 日をまたぐ' : '');
  if (el2) el2.textContent = d.et ? '終了' : '終了なし(瞬間)';
}
function dtEIn(k, v) {
  const was = dtENone();
  if (k === 'edv') { DTE.ed = isDate(v) ? v : ''; return dtESyncEnd(); }
  DTE[k] = k === 'amt' ? +String(v).replace(/\D/g, '') || 0 : v;
  if (k === 'title' && was !== dtENone()) dtEHead();
  if (k === 'st' || k === 'et') dtESyncEnd();
}
function dtEKind(k) { DTE.kind = k; DTE.kindSet = 1; dtEHead(); if (KINDS[k].moment) { DTE.et = ''; DTE.ed = ''; } dtERefresh(); }
function dtEState(s) { DTE.state = s; DTE.stateSet = 1; if (s === 'done') { DTE.rep = ''; DTE.alarm = ''; } dtERefresh(); dtEHead(); }
function dtEDate(v) {
  if (!isDate(v)) return;
  const old = DTE.sd; DTE.sd = v;
  if (!DTE.stateSet && !DTE.id && isDate(v)) DTE.state = v > TODAY ? 'plan' : 'done';
  if (DTE.ed && isDate(old)) DTE.ed = dAdd(DTE.ed, dDiff(v, old));
  if (DTE.parent && (!DB.acts[DTE.parent] || !(aDate(DB.acts[DTE.parent]) <= v && v <= (dOf(DB.acts[DTE.parent].end) || aDate(DB.acts[DTE.parent]))))) DTE.parent = '';
  dtERefresh(); dtEHead();
}
function dtEAll() {
  const d = DTE;
  if (!d.allDay) { const eff = dtEffEd(); d.allDay = true; d.multi = !!eff && eff !== d.sd; d.ed = d.multi ? eff : ''; d.st = ''; d.et = ''; }
  else { d.allDay = false; d.st = d.st || (d.sd === TODAY ? NOW : '10:00'); d.et = ''; d.ed = ''; d.multi = false; }
  dtERefresh();
}
function dtEMulti() { DTE.multi = !DTE.multi; DTE.ed = DTE.multi ? (DTE.ed || dAdd(DTE.sd, 1)) : ''; dtERefresh(); }
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
  const d = DTE, plan = d.state === 'plan', moment = (KINDS[d.kind] || {}).moment;
  let start, end;
  if (d.allDay) { start = d.sd; end = d.multi && d.ed ? d.ed : undefined; }
  else {
    if (!d.st) return toast('開始の時刻を入れてください', { bad: 1 });
    start = tsOf(d.sd, d.st); end = !moment && d.et && !d.tpl ? tsOf(dtEffEd(), d.et) : !moment && d.et ? tsOf(d.et < d.st ? dAdd(d.sd, 1) : d.sd, d.et) : undefined;
  }
  const o = { kind: d.kind, title: DT_FIXED.includes(d.kind) ? d.title : d.title.trim() || (d.kind === 'sleep' ? '' : KINDS[d.kind].n), start, end, state: d.state, roles: d.roles, note: d.note.trim() || undefined, parent: d.parent || undefined, alarm: plan && d.alarm ? d.alarm : undefined };
  if (d.kind === 'pay' && d.id && !d.amt) return toast('金額を入れてください', { bad: 1 });
  /* 作る */
  if (!d.id) { const r = actAdd({ ...o, rep: plan && d.rep ? { u: d.rep } : undefined }); return done(r, { close: 1, go: dtGoOf(r) }); }
  const a = A(d.id); if (!a) return toast('見つかりません', { bad: 1 });
  /* 繰り返しの仮の回 = 決まりを直す */
  if (d.tpl) {
    const rid = d.fixedRep, r0 = DB.routines[rid], steps = [];
    if (!d.rep) steps.push(() => actUpdate(d.id, { ...o, rep: null }));
    else {
      if (d.kind === 'pay' && a.pay && d.amt !== a.pay.amt) steps.push(() => routineUpdate(rid, { tpl: { pay: { amt: d.amt } } }));
      steps.push(() => actUpdate(d.id, { ...o, rep: { u: d.rep } }));
      if (d.sd !== r0.from) steps.push(() => routineUpdate(rid, { from: d.sd }));
    }
    const res = dtChain('直しました', ...steps);
    let go;
    if (res.ok) { const nr = DB.routines[rid]; go = !d.rep ? dtGoOf(res) : nr && !repOn(nr, dOf(a.start)) ? '#/a/' + rid + '@' + repNext(nr, TODAY) : undefined; }
    return done(res, { close: 1, go, replace: go ? 1 : undefined });
  }
  /* 予定に繰り返しを付ける = 決まりを作って、1回きりの予定は置き換える */
  if (plan && d.rep && d.real) {
    const ex = d.kind === 'pay' ? xOf(d.id) : null;
    const res = dtChain('繰り返しにしました', () => actAdd({ ...o, rep: { u: d.rep }, ...(d.kind === 'pay' ? { pay: { amt: d.amt }, exp: ex ? { cat: ex.cat } : undefined } : {}) }), () => actRemove(d.id));
    return done(res, { close: 1, go: dtGoOf(res) ? '#/a/' + res.id : undefined, replace: 1 });
  }
  const patch = { ...o };
  if (d.kind === 'pay') patch.pay = { ...a.pay, amt: d.amt };
  done(actUpdate(d.id, patch), { close: 1 });
}
