// node v7/tools/model_test.mjs — model.js(v6.5)の検査。vm で読み込み、assert で確かめる
import vm from 'node:vm'; import fs from 'node:fs'; import assert from 'node:assert/strict'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const src = fs.readFileSync(path.join(here, '../js/model.js'), 'utf8');
const store = {};
const ctx = vm.createContext({ localStorage: { getItem: k => store[k] ?? null, setItem: (k, v) => { store[k] = v; } }, location: { search: '?now=2026-11-11T13:20' }, console, URLSearchParams, Date });
vm.runInContext(src, ctx);
const names = [...new Set([...src.matchAll(/^(?:const|let|function|class)\s+([A-Za-z_$][\w$]*)/gm)].map(m => m[1]))];
const M = vm.runInContext(`({ ${names.join(', ')}, get DB() { return DB; }, set DB(v) { DB = v; } })`, ctx);
M.load();
let n = 0; const T = (name, fn) => { try { fn(); n++; console.log('ok  ', name); } catch (e) { console.log('FAIL', name, '\n', e.stack.split('\n').slice(0, 4).join('\n')); process.exitCode = 1; } };
const deq = (a, b, m) => assert.equal(JSON.stringify(a), JSON.stringify(b), m);
const ok = r => { assert.ok(r.ok, r.why); return r; };
const snap = () => JSON.stringify(M.DB);
const undoCheck = (name, fn) => T('undo: ' + name, () => { const b = snap(), r = ok(fn()); assert.notEqual(snap(), b, 'changed'); r.undo(); assert.equal(snap(), b); });
const { DB } = M; const TODAY = M.TODAY;
assert.equal(TODAY, '2026-11-11');

T('参照が全て実在する', () => {
  const D = M.DB, has = id => id === 'me' || !!D.ents[id], bad = [];
  const chk = (c, m) => { if (!c) bad.push(m); };
  for (const [id, a] of Object.entries(D.acts)) {
    (a.roles || []).forEach(r => chk(has(r.e), `${id} role ${r.e}`));
    if (a.parent) chk(D.acts[a.parent], `${id} parent`);
    if (a.routine) chk(D.routines[a.routine], `${id} routine`);
    if (a.pay) { a.pay.payers.forEach(p => { chk(has(p.who), `${id} payer`); chk(D.ents[p.how] && D.ents[p.how].types.includes('method'), `${id} how ${p.how}`); }); a.pay.items.forEach(i => { chk(i.id, `${id} item id`); (i.users || []).forEach(u => chk(has(u), `${id} user ${u}`)); }); }
    if (a.settle) { chk(a.settle.who === null || has(a.settle.who), `${id} settle.who`); a.settle.pays.forEach(p => chk(D.acts[p] && D.acts[p].pay, `${id} settle pay ${p}`)); if (a.settle.how) chk(D.ents[a.settle.how], 'settle how'); }
    chk(M.isTS(a.start), `${id} start`);
  }
  for (const [id, x] of Object.entries(D.exps)) { chk(D.acts[id] && D.acts[id].pay, `exp ${id}`); chk(D.cats[x.cat], `exp cat ${id}`); const ids = D.acts[id].pay.items.map(i => i.id); Object.keys(x.itemCat || {}).forEach(k => chk(ids.includes(k), `itemCat ${id} ${k}`)); }
  for (const [id, r] of Object.entries(D.routines)) (r.tpl.roles || []).forEach(x => chk(has(x.e), `routine ${id} role`));
  for (const [id, p] of Object.entries(D.photos)) { if (p.act) chk(D.acts[p.act], `photo ${id} act`); (p.ents || []).forEach(e => chk(has(e), 'photo ent')); }
  for (const [id, t] of Object.entries(D.tasks)) { if (t.act) chk(D.acts[t.act] || D.routines[t.act], `task ${id} act`); chk(D.lists[t.list], 'task list'); (t.ents || []).forEach(e => chk(has(e), 'task ent')); if (t.payAct) chk(D.acts[t.payAct], 'payAct'); }
  for (const [id, m] of Object.entries(D.memos)) { if (m.act) chk(D.acts[m.act], `memo ${id} act`); m.ents.forEach(e => chk(has(e), 'memo ent')); }
  for (const [id, r] of Object.entries(D.rels)) { chk(has(r.a) && has(r.b), `rel ${id}`); }
  for (const id of Object.keys(D.notes)) chk(has(id), 'note ent');
  deq(bad, []);
  assert.ok(D.ents.me && M.E('me').n === '自分' && D.meta.v === 7);
});
T('seed: 旧 id が引ける', () => {
  for (const id of ['l1', 'w2', 'c1', 'w1', 'm1', 'e1', 'e9', 'e6', 'p1', 'p16', 'p17', 'p2', 'p31', 'p3', 'f_rent@2026-11-27', 'f_rent_2026-10-27']) assert.ok(M.A(id), id);
  assert.equal(M.A('f_rent'), null);
  for (const id of ['t1', 't2', 't5']) assert.ok(M.DB.tasks[id]);
  for (const id of ['hanako', 'aoba', 'minato', 'acorp', 'proj1', 'ken']) assert.ok(M.E(id));
  assert.ok(M.DB.notes.hanako && M.DB.notes.ken === undefined);
  assert.equal(M.holOn('2026-11-23'), '勤労感謝の日'); assert.ok(!Object.values(M.DB.acts).some(a => a.kind === 'holiday'));
});
T('seed: 追加分(me の事実・stub・終わった関係・精算・unsure)', () => {
  assert.ok(M.factHist('me', '住所').length === 1 && M.factsNow('me').some(f => f.k === '住所'));
  assert.ok(M.entsAll({ stubs: true }).some(id => M.isStub(id)));
  assert.ok(M.relsOf('ken', { ended: true }).length > M.relsOf('ken').length);
  assert.equal(Object.values(M.DB.acts).filter(a => a.settle).length, 2);
  assert.ok(M.DB.acts.s1.settle.pays.length === 2);
  assert.ok(M.balanceOf('p20').settled && M.balanceOf('p1').open && M.balanceOf('p17').open);
  assert.ok(M.unsureCount(M.A('p31')) === 4 && M.isUnsure(M.A('p31')) && M.A('p31').via === 'ocr');
  assert.ok(Object.values(M.DB.acts).filter(a => a.kind === 'sleep' && M.aDate2(a)).length >= 4);
});

T('時刻の小道具', () => {
  assert.equal(M.dOf('2026-11-11T13:20'), '2026-11-11'); assert.equal(M.tOf('2026-11-11'), ''); assert.equal(M.tOf('2026-11-11T13:20'), '13:20');
  assert.equal(M.tsOf('2026-11-11', ''), '2026-11-11'); assert.equal(M.tsOf('2026-11-11', '09:00'), '2026-11-11T09:00');
  assert.equal(M.nowStamp(), '2026-11-11T13:20');
  assert.ok(M.isTS('2026-11-11') && M.isTS('2026-11-11T09:00') && !M.isTS('2026-11-11 09:00') && !M.isTS('2026-11-11T9:00'));
});
T('日またぎの睡眠', () => {
  const a = M.A('w3'); // 昨夜 23:30 → 今朝 07:05
  assert.equal(M.actMins(a), 7 * 60 + 35); assert.equal(M.isSpan(a), true);
  assert.equal(M.aDate(a), '2026-11-10'); assert.equal(M.aDate2(a), '2026-11-11'); assert.equal(M.aStart(a), '23:30'); assert.equal(M.aEnd(a), '07:05');
  assert.equal(M.timeText(a), '11/10 23:30–11/11 07:05');
  assert.ok(M.actsOn('2026-11-10').some(([id]) => id === 'w3')); assert.ok(M.actsOn('2026-11-11').some(([id]) => id === 'w3'));
  assert.ok(!M.actsOn('2026-11-12').some(([id]) => id === 'w3'));
  deq(M.spanOn(a, '2026-11-10'), { s: '23:30', e: '24:00', from: false, to: true });
  deq(M.spanOn(a, '2026-11-11'), { s: '00:00', e: '07:05', from: true, to: false });
  assert.equal(M.spanOn(a, '2026-11-12'), null);
  const d = M.actsOn('2026-11-11').map(([id]) => id); assert.equal(d[0] === 'w3' || M.A(d[0]).start < '2026-11-11', true);
  const x = { start: '2026-11-14T23:30', end: '2026-11-15T07:00' }; assert.equal(M.actMins(x), 450); assert.equal(M.timeText(x), '11/14 23:30–11/15 07:00');
  assert.equal(M.timeText({ start: '2026-11-11T12:10' }), '12:10'); assert.equal(M.timeText({ start: '2026-11-11T12:10', end: '2026-11-11T13:00' }), '12:10–13:00');
  assert.equal(M.timeText({ start: '2026-11-11' }), '終日'); assert.equal(M.timeText(M.A('e6')), `${M.fmtMD(M.A('e6').start)}–${M.fmtMD(M.A('e6').end)}`);
  assert.equal(M.actMins(M.A('e6')), 0); assert.equal(M.spanOn(M.A('e6'), M.A('e6').start), null);
  assert.ok(M.actsOn(M.dAdd(TODAY, 11)).some(([id]) => id === 'e6'));
});
T('actAdd/actUpdate の検査', () => {
  for (const bad of [{ start: '2026-11-11 10:00' }, { start: '2026-11-11T10:00', end: '2026-11-11T09:00' }, { start: '2026-11-11', end: '2026-11-12T10:00' }, {}]) assert.equal(M.actAdd({ kind: 'event', title: 'x', ...bad }).ok, false);
  const r = ok(M.actAdd({ kind: 'event', title: 'x', start: '2026-11-12', end: '2026-11-14' })); assert.equal(M.aDate2(M.A(r.id)), '2026-11-14');
  ok(M.actUpdate(r.id, { end: null })); assert.equal(M.isSpan(M.A(r.id)), false); assert.equal(M.actUpdate(r.id, { start: 'ばか' }).ok, false);
});
T('isLate', () => {
  assert.equal(M.isLate({ state: 'plan', start: '2026-11-11T10:00', end: '2026-11-11T12:00' }), true);
  assert.equal(M.isLate({ state: 'plan', start: '2026-11-11T15:00' }), false);
  assert.equal(M.isLate({ state: 'plan', start: '2026-11-11' }), false); assert.equal(M.isLate({ state: 'plan', start: '2026-11-10' }), true);
  assert.equal(M.isLate({ state: 'done', start: '2026-11-01' }), false);
  assert.ok(M.pendingPlans().some(([id]) => id === 'e9'));
});

T('繰り返し: 仮の回・planRecord・skip・rep:null', () => {
  const v = M.A('f_rent@2026-11-27'); assert.equal(v.virt, 1); assert.equal(v.state, 'plan'); assert.equal(v.kind, 'pay'); assert.equal(M.payTotal(v), 85000); assert.equal(M.xOf('f_rent@2026-11-27').cat, 'rent'); assert.equal(M.xAmt('f_rent@2026-11-27'), 85000);
  assert.equal(M.repText(M.DB.routines.f_rent), '毎月27日'); assert.equal(M.repNext(M.DB.routines.f_rent), '2026-11-27');
  assert.ok(M.actsOn('2026-11-27').some(([id]) => id === 'f_rent@2026-11-27'));
  assert.ok(!M.actsOn('2026-10-27').some(([id]) => id === 'f_rent@2026-10-27'), '記録済みの回は仮の回に出ない');
  const r = ok(M.planRecord('f_rent@2026-11-27', { how: 'bank' }));
  assert.ok(!M.actsOn('2026-11-27').some(([id]) => id.startsWith('f_rent@'))); const a = M.DB.acts[r.id];
  assert.equal(a.routine, 'f_rent'); assert.equal(a.state, 'done'); assert.equal(a.pay.payers[0].how, 'bank'); assert.equal(M.xAmt(r.id), 85000); assert.ok(M.recordsOfRoutine('f_rent').some(([id]) => id === r.id));
  assert.ok(M.actsOn('2026-11-27').some(([id]) => id === r.id));
  r.undo(); assert.ok(M.actsOn('2026-11-27').some(([id]) => id === 'f_rent@2026-11-27'));
  ok(M.actRemove('f_rent@2026-11-27')); assert.ok(!M.actsOn('2026-11-27').some(([id]) => id.startsWith('f_rent@'))); assert.equal(M.A('f_rent@2026-11-27'), null);
  ok(M.routineSkip('f_rent', '2026-11-27', false)); assert.ok(M.A('f_rent@2026-11-27'));
  // 追加・更新
  const g = ok(M.actAdd({ kind: 'health', title: 'ジム', start: '2026-11-12T19:00', end: '2026-11-12T20:30', state: 'plan', roles: [{ r: '場所', name: 'ジムX' }], rep: { u: 'week' } }));
  assert.equal(g.id, g.routine + '@2026-11-12'); assert.ok(M.A(g.id)); assert.equal(M.A(g.id).end, '2026-11-12T20:30'); assert.ok(M.actsOn('2026-11-19').some(([id]) => id === g.routine + '@2026-11-19'));
  ok(M.actUpdate(g.id, { start: '2026-11-12T18:00', title: 'ジム2' })); assert.equal(M.A(g.id).start, '2026-11-12T18:00'); assert.equal(M.DB.routines[g.routine].tpl.title, 'ジム2');
  ok(M.actUpdate(g.id, { rep: { u: 'day' } })); assert.equal(M.DB.routines[g.routine].every, 'day');
  const rr = ok(M.planRecord(g.routine + '@2026-11-12', { start: '2026-11-13T18:30' })); // 別の日に記録
  assert.equal(M.DB.acts[rr.id].planned.start, '2026-11-12T18:00'); assert.ok(!M.actsOn('2026-11-12').some(([id]) => id.startsWith(g.routine)));
  const one = ok(M.actUpdate(g.routine + '@2026-11-15', { rep: null })); assert.equal(M.DB.routines[g.routine], undefined); assert.equal(M.DB.acts[one.id].state, 'plan'); assert.equal(M.aDate(M.DB.acts[one.id]), '2026-11-15'); assert.equal(M.DB.acts[rr.id].routine, undefined);
  ok(M.routineAdd({ every: 'month', from: '2026-01-31', tpl: { kind: 'event', title: 'm' } })); const rid = Object.keys(M.DB.routines).pop(); assert.ok(M.repOn(M.DB.routines[rid], '2026-02-28')); ok(M.routineUpdate(rid, { until: '2026-03-01', tpl: { title: 'm2' } })); assert.equal(M.DB.routines[rid].tpl.kind, 'event'); assert.ok(!M.repOn(M.DB.routines[rid], '2026-03-31'));
  ok(M.routineRemove(rid)); assert.equal(M.DB.routines[rid], undefined);
  assert.ok(M.tasksOfAct('f_rent@2026-11-27') !== undefined);
});

T('名前だけの相手 = stub', () => {
  const r1 = ok(M.actAdd({ kind: 'meal', title: 'a', start: '2026-11-11T12:00', roles: [{ r: '同席', name: '佐々木さん' }] }));
  const r2 = ok(M.actAdd({ kind: 'meal', title: 'b', start: '2026-11-11T13:00', roles: [{ r: '同席', name: '佐々木さん' }] }));
  const e1 = M.DB.acts[r1.id].roles[0].e, e2 = M.DB.acts[r2.id].roles[0].e; assert.equal(e1, e2); assert.ok(M.isStub(e1)); deq(M.E(e1).types, ['person']);
  assert.ok(!M.entsOfType('person').includes(e1)); assert.ok(M.entsOfType('person', true, { stubs: true }).includes(e1)); assert.ok(!M.entsAll().includes(e1));
  ok(M.actRemove(r1.id)); assert.ok(M.E(e1)); ok(M.actRemove(r2.id)); assert.equal(M.E(e1), null, '参照が無くなると消える');
  const hana = ok(M.actAdd({ kind: 'meal', title: 'c', start: '2026-11-11T12:00', roles: [{ r: '同席', name: '田中 花子' }] })); assert.equal(M.DB.acts[hana.id].roles[0].e, 'hanako'); const stubsBefore = M.entsAll({ stubs: true }).length;
  const st = ok(M.actAdd({ kind: 'meal', title: 'd', start: '2026-11-11T12:00', roles: [{ r: '同席', name: '新人さん' }, { r: '場所', name: '新店' }] })); const sid = M.DB.acts[st.id].roles[0].e; assert.equal(M.entsAll({ stubs: true }).length, stubsBefore + 2);
  ok(M.entUpdate(sid, { stub: null, types: ['person'] })); assert.ok(!M.isStub(sid)); ok(M.actRemove(st.id)); assert.ok(M.E(sid), '登録済みは残る'); assert.equal(M.entsAll({ stubs: true }).length, stubsBefore + 1);
  const sn = ok(M.actAdd({ kind: 'meal', title: 'e', start: '2026-11-11T12:00', roles: [{ r: '同席', name: 'ノート持ち' }] })); const nid = M.DB.acts[sn.id].roles[0].e; ok(M.noteSave(nid, [{ h: 'a', text: 'b' }])); ok(M.actRemove(sn.id)); assert.ok(M.E(nid), 'ノートのある stub は残る');
  assert.equal(M.entRemove('me').ok, false);
  const p = ok(M.paySave({ start: '2026-11-11T12:00', payee: { name: '新しい店X' }, items: [{ n: 'a', price: 500, users: [{ name: '友A' }] }], payers: [{ who: 'me', amt: 500 }], exp: { cat: 'eatout' } })); assert.ok(M.isStub(M.DB.acts[p.id].pay.items[0].users[0])); ok(M.actRemove(p.id)); assert.ok(!M.entsAll({ stubs: true }).some(id => M.E(id).n === '友A' || M.E(id).n === '新しい店X'));
});

const mk = (items, payers, exp, extra = {}) => ok(M.paySave({ start: '2026-11-10T12:00', payee: { e: 'aoba' }, items, payers, exp, ...extra }));
T('お金: 割り勘・奢り・借り', () => {
  // 現行の見本と同じ結果
  assert.equal(M.xAmt('p1'), 2240); assert.equal(M.balanceOf('p1').amt, 2340); assert.equal(M.balanceText(M.balanceOf('p1')), '花子さんに貸し ¥2,340');
  assert.equal(M.xAmt('p16'), 1100); assert.equal(M.balanceOf('p16').amt, 0); // 奢り(差なし)
  assert.equal(M.xAmt('p17'), 2840); assert.equal(M.balanceOf('p17').amt, -2840); assert.equal(M.balanceText(M.balanceOf('p17')), '健さんに借り ¥2,840');
  assert.equal(M.xAmt('p8'), 6800);
  const x = M.xItems('p1'); assert.ok(x.every(o => o.id && typeof o.i === 'number')); assert.equal(x.find(o => o.id === 'p1-3').cat, 'alcohol');
  const c = M.xCats('p1'); assert.ok(c.some(g => g.cat === 'alcohol' && g.ids.includes('p1-3')));
  assert.equal(M.payMethod(M.A('p1')), '現金'); assert.equal(M.payMethod(M.A('p3')), 'Suica');
  // 割り勘(even)
  const e = mk([{ n: 'x', price: 3000, users: ['me', 'hanako'] }], [{ who: 'me', amt: 3000, how: 'card' }], { basis: 'even', n: 2, cat: 'eatout' });
  assert.equal(M.xAmt(e.id), 1500); assert.equal(M.balanceOf(e.id).amt, 1500); deq(M.debtsOf(e.id).map(d => [d.who, d.amt]), [['hanako', 1500]]);
  // 奢り(全額自分が払い、相手分は自分の負担にしない)
  const g = mk([{ n: 'x', price: 2000, users: ['hanako'] }], null, { basis: 'items', cat: 'gift' }); assert.equal(M.xAmt(g.id), 0); assert.equal(M.DB.exps[g.id], undefined); assert.equal(M.balanceOf(g.id).amt, 2000);
  // 3人
  const t3 = mk([{ n: 'a', price: 3000, users: ['me', 'hanako', 'ken'] }, { n: 'b', price: 1000, users: ['ken'] }], [{ who: 'me', amt: 4000, how: 'cash' }], { basis: 'items', cat: 'eatout' });
  const d3 = M.debtsOf(t3.id); assert.equal(d3.reduce((s, d) => s + d.amt, 0), M.balanceOf(t3.id).amt); assert.equal(M.balanceOf(t3.id).amt, 4000 - 1000);
  const t4 = mk([{ n: 'a', price: 1000, users: ['me', 'hanako', 'ken'] }], [{ who: 'me', amt: 400, how: 'cash' }, { who: 'hanako', amt: 300, how: 'cash' }, { who: 'ken', amt: 300, how: 'cash' }], { basis: 'items', cat: 'eatout' });
  const d4 = M.debtsOf(t4.id); assert.equal(d4.reduce((s, d) => s + d.amt, 0), M.balanceOf(t4.id).amt, '3人の合計 = 差'); assert.equal(M.balanceOf(t4.id).amt, 400 - 333);
  const t5 = mk([{ n: 'a', price: 1000, users: ['me', 'hanako', 'ken'] }], [{ who: 'hanako', amt: 700, how: 'cash' }, { who: 'ken', amt: 300, how: 'cash' }], { basis: 'items', cat: 'eatout' });
  const d5 = M.debtsOf(t5.id); assert.equal(d5.reduce((s, d) => s + d.amt, 0), -333); assert.ok(d5.every(d => d.amt < 0) && d5.length === 1 && d5[0].who === 'hanako');
  // 相手不明
  const u = mk([{ n: 'a', price: 1000 }], [{ who: 'me', amt: 1000 }], { basis: 'even', n: 2, cat: 'eatout' }); deq(M.debtsOf(u.id).map(d => [d.who, d.amt]), [[null, 500]]);
  assert.equal(M.balanceOf('f_rent@2026-11-27').amt, 0);
});
T('お金: paySettle(まとめて)・off', () => {
  const a = mk([{ n: 'x', price: 2000, users: ['me', 'hanako'] }], [{ who: 'me', amt: 2000 }], { basis: 'items', cat: 'eatout' }), b = mk([{ n: 'y', price: 600, users: ['me', 'hanako'] }], [{ who: 'me', amt: 600 }], { basis: 'items', cat: 'eatout' });
  const before = Object.values(M.DB.acts).filter(x => x.settle).length;
  const r = ok(M.paySettle([a.id, b.id]));
  const ss = Object.entries(M.DB.acts).filter(([, x]) => x.settle); assert.equal(ss.length, before + 1);
  const s = M.settlesOf(a.id)[0][1].settle; assert.equal(s.who, 'hanako'); assert.equal(s.dir, 'in'); assert.equal(s.amt, 1300); deq(s.pays.sort(), [a.id, b.id].sort());
  assert.equal(M.balanceOf(a.id).open, false); assert.equal(M.balanceOf(a.id).settled, true); assert.equal(M.actTitle(M.settlesOf(a.id)[0][1]), '花子さんから ¥1,300');
  assert.ok(!M.personBalances().some(p => p.ids.includes(a.id)));
  assert.equal(M.paySettle(a.id).ok, false);
  ok(M.paySettle(a.id, false)); assert.equal(M.balanceOf(a.id).open, true); assert.equal(M.settlesOf(b.id)[0][1].settle.amt, 300);
  ok(M.paySettle(b.id, false)); assert.equal(M.settlesOf(b.id).length, 0); assert.equal(Object.values(M.DB.acts).filter(x => x.settle).length, before);
  r.undo();
  // 借りの精算 → out
  const c = ok(M.paySettle('p17', true, { how: 'bank', date: '2026-11-10' })); const sc = M.settlesOf('p17')[0][1]; assert.equal(sc.settle.dir, 'out'); assert.equal(sc.settle.amt, 2840); assert.equal(M.actTitle(sc), '山本 健'.split(' ').pop() + 'さんへ ¥2,840'); assert.equal(sc.start, '2026-11-10T12:00');
  c.undo();
  // 支払いを消すと settle から外れる
  const pr = M.settlesOf('p20')[0][0]; ok(M.actRemove('p20')); assert.deepEqual(JSON.stringify(M.DB.acts.s1.settle.pays), '["p21"]');
  // 家計簿に settle は入らない
  assert.ok(!M.expsIn(M.ymOf(TODAY)).some(([id]) => M.DB.acts[id].settle));
  assert.equal(M.settlesOf('p21').length, 1);
});
T('お金: 支払い予定 → planRecord', () => {
  const r = ok(M.payPlanSave({ start: '2026-11-20', payee: { name: '大家X' }, title: '駐車場', amt: 12000, cat: 'house' }));
  const a = M.A(r.id); assert.equal(a.state, 'plan'); assert.equal(a.pay.amt, 12000); assert.equal(M.payTotal(a), 12000); assert.equal(M.xAmt(r.id), 12000); assert.equal(M.balanceOf(r.id).amt, 0); assert.ok(!('plan' in a.pay));
  assert.ok(M.upcoming(30).some(([id]) => id === r.id)); assert.equal(M.expsIn('2026-11').some(([id]) => id === r.id), false);
  const rr = ok(M.planRecord(r.id, { amt: 12500, how: 'bank' })); const b = M.DB.acts[r.id]; assert.equal(b.state, 'done'); assert.equal(b.pay.payers[0].how, 'bank'); assert.equal(M.payTotal(b), 12500); assert.equal(b.pay.items.length, 1); assert.ok(b.pay.items[0].id); assert.equal(M.DB.exps[r.id].cat, 'house'); assert.ok(M.hasT(b.start));
  assert.equal(M.expsIn('2026-11').some(([id]) => id === r.id), true);
  const rep = ok(M.payPlanSave({ start: '2026-12-05', payee: { name: '保険X' }, amt: 5000, cat: 'comm', rep: { u: 'month' } })); assert.ok(rep.routine && rep.id.endsWith('@2026-12-05')); assert.equal(M.xOf(rep.id).cat, 'comm');
  assert.equal(M.payPlanSave({ start: '2026-11-20', amt: 0, cat: 'house' }).ok, false);
});
T('お金: 集計が動く・支出の形', () => {
  const m = M.monthSum('2026-10'); assert.ok(m.total > 0 && m.fixed >= 85000 + 3280 + 990); assert.ok(m.roots.length);
  assert.ok(M.expsIn('2026-10').some(([id]) => id === 'f_rent_2026-10-27'));
  assert.ok(M.catSum('2026-10', 'food') >= 0); assert.ok(M.paceOf('2026-10').length === 31); assert.equal(typeof M.daySpend('2026-11-10'), 'number');
  assert.equal(M.catGuess({ e: 'aoba' }), 'eatout'); assert.equal(M.catGuess({ name: 'ドラッグ桜' }), 'daily');
  assert.ok(M.isShared('p1') && !M.isShared('p3'));
  const st = M.entStats('hanako'); assert.ok(st.total > 0 && st.weeks.length === 12 && st.bal > 0); assert.ok(M.spendWith('hanako') > 0);
  assert.ok(M.roleIn(M.A('p1'), 'cash') === '支払い方法' && M.roleIn(M.A('s1'), 'hanako') === '精算'); assert.ok(M.actsOfEnt('cash').length > 0);
  assert.equal(M.whoName('me'), '自分'); assert.equal(M.whoShort('hanako'), '花子'); assert.equal(M.roleName({ r: '場所', e: 'aoba' }), 'らーめん青葉');
  assert.equal(M.itemRemove('p2', 'nope').ok, false); const r = ok(M.itemRemove('p2', 'p2-4')); assert.equal(M.DB.exps.p2.itemCat, undefined); assert.equal(M.DB.acts.p2.pay.items.length, 5); r.undo();
});
T('支払い方法 = ent', () => {
  deq(M.methodsAll().map(([id]) => id), ['cash', 'card', 'suica', 'qr', 'debit', 'bank']); assert.equal(M.methodDefault(), 'cash'); assert.equal(M.methodName('suica'), 'Suica');
  const r = ok(M.methodAdd('PayX')); assert.equal(M.methodName(r.id), 'PayX'); assert.equal(M.methodAdd('PayX').ok, false); assert.equal(M.methodRemove('cash').ok, false, '使われている');
  ok(M.methodRemove(r.id)); assert.equal(M.E(r.id), null); assert.ok(!M.entsAll({ stubs: true }).some(id => M.E(id).n === 'PayX'));
  assert.equal(M.DB.methods, undefined);
});
T('unsure', () => {
  assert.equal(M.unsureCount(M.A('p31')), 4);
  const p = mk([{ n: 'a', price: 100, unsure: 1 }, { n: 'b', price: 100 }], null, { basis: 'items', cat: 'daily' }, { unsure: ['amt'], via: 'ocr' }); assert.equal(M.unsureCount(M.A(p.id)), 2); assert.equal(M.A(p.id).via, 'ocr');
  const t = ok(M.taskAdd({ t: 'x', unsure: true, via: 'rule' })); assert.equal(M.unsureCount(M.DB.tasks[t.id]), 1);
  const r = ok(M.confirmRows([{ t: 'acts', k: p.id }, { t: 'acts', k: 'p31' }, { t: 'tasks', k: t.id }]));
  assert.equal(M.isUnsure(M.A(p.id)), false); assert.equal(M.unsureCount(M.A('p31')), 0); assert.ok(!M.DB.acts.p31.pay.items.some(i => i.unsure)); assert.equal(M.isUnsure(M.DB.tasks[t.id]), false); assert.equal(M.DB.acts.p31.via, 'ocr');
  assert.equal(M.confirmRows([{ t: 'acts', k: 'p31' }]).ok, false); r.undo(); assert.equal(M.unsureCount(M.A('p31')), 4);
  const e = ok(M.paySave({ id: p.id, start: M.A(p.id).start, payee: { e: 'aoba' }, items: [{ n: 'a', price: 100 }], exp: { basis: 'items', cat: 'daily' } })); assert.equal(M.isUnsure(M.A(p.id)), false);
});
T('事実・関係', () => {
  ok(M.entAdd({ n: 'テスト人', types: ['person'], facts: [{ k: '住所', v: 'A町' }] })); const id = Object.keys(M.DB.ents).pop();
  ok(M.factSet(id, '住所', 'B町', { keepHist: true, why: '転居', since: '2024-05' }));
  deq(M.factsNow(id).map(f => f.v), ['B町']); const h = M.factHist(id, '住所'); assert.equal(h.length, 1); assert.equal(h[0].v, 'A町'); assert.equal(h[0].to, '2024-05'); assert.equal(h[0].why, '転居'); assert.equal(M.factsNow(id)[0].from, '2024-05');
  assert.equal(M.factAt(id, '住所', '2024-04-30'), 'A町'); assert.equal(M.factAt(id, '住所', '2024-05-01'), 'B町'); assert.equal(M.factAt(id, '住所', '2030-01-01'), 'B町');
  ok(M.factSet(id, '住所', 'B町2', {})); assert.equal(M.factsNow(id)[0].v, 'B町2'); assert.equal(M.factHist(id, '住所').length, 1);
  ok(M.factSet(id, '住所', '')); assert.equal(M.E(id).facts.length, 0);
  assert.equal(M.factAt('hanako', '住所', '2022-01-01'), '川崎市宮前区鷺沼1'); assert.equal(M.factAt('hanako', '姓', '2019-01-01'), '佐藤');
  const n0 = M.relsOf('hanako').length, r = ok(M.relAdd({ a: 'hanako', b: id, role: '同僚', from: '2024-01' })); assert.equal(M.relsOf('hanako').length, n0 + 1); assert.equal(M.relsOf(id)[0].role, '同僚'); assert.equal(M.relsOf(id)[0].other, 'hanako');
  ok(M.relUpdate(r.id, { to: '2025-01' })); assert.equal(M.relsOf('hanako').length, n0); assert.equal(M.relsOf('hanako', { ended: true }).length, n0 + 1); assert.equal(M.relsOf('hanako', { ended: true }).find(x => x.id === r.id).to, '2025-01');
  assert.equal(M.relsOf('acorp')[0].role, '従業員'); assert.equal(M.relAdd({ a: 'hanako', b: 'hanako', role: 'x' }).ok, false);
  ok(M.relRemove(r.id)); assert.equal(M.DB.rels[r.id], undefined); ok(M.entRemove(id));
});
T('写真・ノート・メモ・日記', () => {
  assert.equal(M.photosOn(M.dAdd(TODAY, -1)).length, 3); deq(M.photosOn(M.dAdd(TODAY, -1)).map(([id]) => id), ['ph1', 'ph2', 'ph3']); deq(M.photosOfAct('l1').map(([id]) => id), ['ph1']); deq(M.dayOf('2026-11-10'), { diary: '花子さんとラーメン。午後はミナトで作業、夜は早めに帰宅。' }); deq(M.dayOf('2020-01-01'), { diary: '' });
  const p = ok(M.photoAdd({ d: '2026-11-10', cap: 'x', act: 'l1' })); assert.ok(M.DB.photos[p.id].at.startsWith('2026-11-10T')); ok(M.photoUpdate(p.id, { cap: 'y', at: '2026-11-10T09:00' })); assert.equal(M.photosOn('2026-11-10')[0][0], p.id);
  const p2 = ok(M.photoAdd({ at: '2026-11-10T08:00', cap: 'z' })); assert.equal(M.photosOn('2026-11-10')[0][0], p2.id);
  ok(M.actRemove('l1')); assert.equal(M.DB.photos.ph1.act, undefined); assert.equal(M.DB.photos[p.id].act, undefined); ok(M.photoRemove(p.id)); assert.equal(M.DB.photos[p.id], undefined);
  ok(M.noteSave('hanako', [{ h: 'あ', text: 'い' }])); assert.equal(M.DB.notes.hanako.subs.length, 2); assert.equal(M.DB.notes.hanako.body[0].h, 'あ'); assert.equal(M.DB.notes.hanako.ver, undefined);
  const s = ok(M.noteSubSave('hanako', null, { n: '新', sub: '', text: 't' })); assert.ok(s.id); ok(M.noteSubSave('hanako', s.id, { n: '新2', sub: '', text: 't2' })); assert.equal(M.DB.notes.hanako.subs.find(x => x.id === s.id).n, '新2'); ok(M.noteSubRemove('hanako', s.id)); assert.equal(M.DB.notes.hanako.subs.length, 2);
  assert.ok(M.memosOn(TODAY).length >= 2 && /T/.test(M.memosSorted()[0][1].at)); const m = ok(M.memoAdd('abc', { ents: ['hanako'] })); assert.equal(M.DB.memos[m.id].at, '2026-11-11T13:20');
  ok(M.daySetDiary('2026-11-09', '日記')); assert.equal(M.dayOf('2026-11-09').diary, '日記'); assert.ok(M.daysWith('2026-11-01', '2026-11-30').includes('2026-11-09'));
});
T('ToDo ⇄ 予定・parse・その他', () => {
  const t = ok(M.taskAdd({ t: '掃除', due: '2026-11-14', rep: { u: 'week' }, ents: ['hanako'] })); const r = ok(M.taskToPlan(t.id, { start: '2026-11-14T10:00', end: '2026-11-14T11:00' })); assert.ok(r.routine && M.A(r.id).roles[0].e === 'hanako'); assert.equal(M.DB.tasks[t.id], undefined);
  const b = ok(M.planToTask(r.id)); assert.equal(M.DB.tasks[b.id].rep.u, 'week'); assert.equal(M.DB.routines[r.routine], undefined);
  const t2 = ok(M.taskAdd({ t: 'x' })); const r2 = ok(M.taskToPlan(t2.id, { start: '2026-11-14' })); assert.equal(M.DB.acts[r2.id].start, '2026-11-14'); const b2 = ok(M.planToTask(r2.id)); assert.equal(M.DB.tasks[b2.id].due, '2026-11-14');
  const q = M.parseQuick('ラーメン 980円'); assert.equal(q.type, 'pay'); assert.equal(q.start, '2026-11-11T13:20'); const p = M.parseQuick('明日 15:00-16:00 歯医者'); assert.equal(p.type, 'plan'); assert.equal(p.start, '2026-11-12T15:00'); assert.equal(p.end, '2026-11-12T16:00'); assert.equal(M.parseQuick('牛乳 #買い物').type, 'task'); assert.equal(M.parseQuick('疲れた').type, 'memo');
  assert.equal(M.tasksOfAct('e1').length, 1); assert.ok(M.shopMatches([{ n: '牛乳' }]).length === 1);
  assert.ok(M.payDup({ start: '2026-11-11T08:42', total: 480 }).some(([id]) => id === 'p30')); assert.equal(M.payDup({ id: 'p30', start: '2026-11-11T08:42', total: 480 }).length, 0);
  const w = ok(M.sleepWake('2026-11-11T07:30')); assert.ok(M.DB.acts[w.id].start.startsWith('2026-11-11T07:30')); assert.equal(M.DB.acts[w.id].title, '起床');
  const s = ok(M.actAdd({ kind: 'sleep', start: '2026-11-08T23:30', state: 'done' })); const w2 = ok(M.sleepWake('2026-11-09T07:00')); assert.equal(w2.id, s.id); assert.equal(M.DB.acts[s.id].end, '2026-11-09T07:00');
  assert.equal(M.oldestYm() <= '2026-05', true);
});
T('集計・検索・書き出し・読み込み', () => {
  assert.ok(M.expsIn('2026-11').length > 0 && M.monthSum('2026-11').total > 0);
  const S = q => M.searchAll(q).map(r => r.type + ':' + r.href).join(' '); for (const q of ['らーめん', '家賃', '卵', '転職']) assert.ok(M.searchAll(q).length, q);
  assert.ok(M.searchAll('らーめん').some(r => r.type === 'act') && M.searchAll('らーめん').some(r => r.type === 'ent') && M.searchAll('家賃').some(r => r.href.startsWith('#/a/f_rent@')) && M.searchAll('卵').some(r => r.type === 'item') && M.searchAll('麺は硬め').some(r => r.type === 'note') && M.searchAll('日記').length >= 0);
  assert.ok(M.recallDays().length >= 1); assert.ok(M.daysWith('2026-11-01', '2026-11-30').length > 5); const dc = M.dataCounts(); assert.ok(dc.acts > 100 && dc.ents >= 11 && dc.bytes > 0);
  const j = M.exportJSON(); const before = snap(); const r = M.importJSON(j); assert.ok(r.ok); assert.ok(snap().replace(',"seeded":1', '') === before.replace(',"seeded":1', ''), 'import/export 往復'); r.undo();
  assert.equal(M.importJSON(j.replace('"v": 7', '"v": 6')).ok, false); assert.equal(M.importJSON('x').ok, false);
  M.resetData(); assert.ok(M.DB.meta.v === 7 && M.DB.acts.l1); assert.equal(JSON.parse(store['prmock-v7']).meta.v, 7);
  assert.ok(M.DB.log.length >= 0);
});
T('commit 失敗で巻き戻る(stub も)', () => {
  const b = snap(); const r = M.actAdd({ kind: 'meal', title: 'x', start: 'bad', roles: [{ r: '同席', name: '巻き戻る人' }] }); assert.equal(r.ok, false); assert.equal(snap(), b);
  const r2 = M.paySave({ start: '2026-11-10T12:00', payee: { name: '巻き戻る店' }, items: [{ n: 'a', price: 100 }], payers: [{ who: 'me', amt: 1 }, { who: 'hanako', amt: 1 }], exp: { cat: 'eatout' } }); assert.equal(r2.ok, false); assert.equal(snap(), b);
});
// undo(新しい見本で)
M.resetData();
undoCheck('actAdd', () => M.actAdd({ kind: 'event', title: 'u', start: '2026-11-12T10:00', roles: [{ r: '同席', name: '取消の人' }] }));
undoCheck('actUpdate', () => M.actUpdate('e3', { title: 'z', end: null, roles: [{ r: '場所', name: 'さくら歯科' }, { r: '同席', e: 'ken' }] }));
undoCheck('actRemove(親・子・写真・精算)', () => M.actRemove('p22'));
undoCheck('actRemove(l1)', () => M.actRemove('l1'));
undoCheck('actRemove(仮の回)', () => M.actRemove('f_rent@2026-11-27'));
undoCheck('actMove', () => M.actMove('p3', 'l2'));
undoCheck('planRecord(仮の回)', () => M.planRecord('f_rent@2026-11-27'));
undoCheck('paySave', () => M.paySave({ start: '2026-11-10T12:00', payee: { name: '店ZZ' }, amt: 800, exp: { cat: 'cafe' } }));
undoCheck('paySettle', () => M.paySettle(['p1']));
undoCheck('paySettle off', () => M.paySettle(['p20'], false));
undoCheck('entRemove(stub 化)', () => M.entRemove('ken'));
undoCheck('entRemove(hanako)', () => M.entRemove('hanako'));
undoCheck('methodRemove', () => M.methodRemove('debit'));
undoCheck('factSet keepHist', () => M.factSet('me', '住所', '新住所', { keepHist: true, since: '2026-10', why: '転居' }));
undoCheck('relUpdate', () => M.relUpdate(Object.keys(M.DB.rels)[0], { to: '2026-01' }));
undoCheck('photoRemove', () => M.photoRemove('ph2'));
undoCheck('noteSave', () => M.noteSave('aoba', [{ h: 'x', text: 'y' }]));
undoCheck('confirmRows', () => M.confirmRows([{ t: 'acts', k: 'p31' }]));
undoCheck('routineSkip', () => M.routineSkip('e12', '2026-11-14'));
undoCheck('actUpdate(rep:null)', () => M.actUpdate('f_sub@2026-11-15', { rep: null }));
undoCheck('typeRemove 失敗でないもの', () => M.typeAdd('tmp', 'person'));

/* ---- v6.5.0 の修正の移植(土台・祝日・取り消しの競合・重複・関係・不正な月日) ---- */
T('祝日: 規則で計算(DB に表は無い・先の年も・振替休日・国民の休日)', () => {
  assert.equal(M.DB.holidays, undefined);
  const KNOWN = { '2027-01-11': '成人の日', '2027-03-21': '春分の日', '2027-05-03': '憲法記念日', '2027-05-05': 'こどもの日', '2027-07-19': '海の日', '2027-08-11': '山の日', '2027-09-20': '敬老の日', '2027-09-23': '秋分の日', '2027-10-11': 'スポーツの日', '2027-11-23': '勤労感謝の日', '2030-05-06': '振替休日', '2026-03-20': '春分の日', '2026-09-23': '秋分の日', '2026-10-12': 'スポーツの日', '2026-11-03': '文化の日' };
  for (const [d, nm] of Object.entries(KNOWN)) { assert.equal(M.holidayName(d), nm, d); assert.equal(M.holOn(d), nm, d); }
  assert.equal(M.holOn('2026-11-04'), ''); assert.equal(M.holOn('2026-02-30'), ''); assert.ok(!M.isDate('2026-02-30') && !M.isDate('2026-13-01') && M.isDate('2028-02-29') && !M.isTS('2026-02-31T10:00')); assert.equal(M.holOn('xx'), '');
  let kokumin = 0;
  for (let y = 2026; y <= 2040; y++) for (const [d, nm] of Object.entries(M.holidaysOf(y))) if (nm === '国民の休日') { kokumin++; assert.ok(M.holOn(M.dAdd(d, -1)) && M.holOn(M.dAdd(d, 1)), d); }
  assert.ok(kokumin > 0);
  assert.ok(!M.actsOn('2027-05-03').some(([, a]) => a.kind === 'holiday'));
});
T('undo: 後の変更と同じ項目は戻さず skipped を返す', () => {
  const id = Object.keys(M.DB.tasks)[0], t0 = M.DB.tasks[id].t;
  const r1 = ok(M.taskUpdate(id, { t: 'A1' })); ok(M.taskUpdate(id, { t: 'B2' }));
  const u = r1.undo(); assert.equal(u.skipped, 1); assert.equal(M.DB.tasks[id].t, 'B2');
  // 別の項目への後の変更は消さない
  const r3 = ok(M.taskUpdate(id, { t: 'D4' })); ok(M.taskUpdate(id, { pri: 1 }));
  const u3 = r3.undo(); assert.equal(u3.skipped, 0); assert.equal(M.DB.tasks[id].pri, 1); assert.notEqual(M.DB.tasks[id].t, 'D4');
  // 単独は元どおり
  const id2 = Object.keys(M.DB.tasks)[1], b2 = M.DB.tasks[id2].t; const r4 = ok(M.taskUpdate(id2, { t: 'E5' })); const u4 = r4.undo();
  assert.equal(u4.skipped, 0); assert.equal(M.DB.tasks[id2].t, b2);
  // 追加した行を後で直されていたら、消さない
  const r5 = ok(M.taskAdd({ t: '追加テスト' })); ok(M.taskUpdate(r5.id, { t: '追加テスト2' })); assert.equal(r5.undo().skipped, 1); assert.ok(M.DB.tasks[r5.id]);
  M.taskRemove(r5.id);
});
T('listAdd: 同じ名前は断る', () => {
  const nm = Object.values(M.DB.lists)[0].n; const r = M.listAdd(nm); assert.equal(r.ok, false); assert.match(r.why, /同じ名前/);
  const r2 = ok(M.listAdd('新しいリスト')); assert.equal(M.listAdd('新しいリスト').ok, false); r2.undo();
});
T('payDup / payPhotoSet: 同じ写真は重複の疑い', () => {
  const r = ok(M.paySave({ start: '2026-11-03T09:00', payee: { name: '写真テスト店' }, items: [{ n: 'x', price: 777, users: ['me'], tax: 10 }], exp: { cat: 'food' } }));
  assert.equal(M.payDup({ start: '2026-11-04T10:00', total: 1, photo: 'h1' }).length, 0);
  ok(M.payPhotoSet(r.id, 'h1')); assert.equal(M.A(r.id).photo, 'h1');
  deq(M.payDup({ start: '2026-11-04T10:00', total: 1, photo: 'h1' }).map(x => x[0]), [r.id]);
  assert.equal(M.payDup({ start: '2026-11-04T10:00', total: 1, photo: 'h2' }).length, 0);
  assert.equal(M.payPhotoSet('nothing', 'h').ok, false);
  assert.equal(M.payPhotoSet(Object.keys(M.DB.acts).find(i => !M.DB.acts[i].pay), 'h').ok, false);
  ok(M.paySave({ id: r.id, start: '2026-11-03T09:00', payee: { name: '写真テスト店' }, items: [{ n: 'x', price: 777, users: ['me'], tax: 10 }], exp: { cat: 'food' } }));
});
T('relUpdate: 見ている側の形(a 側・b 側・双方向・一方向)と不正な月日', () => {
  const x = ok(M.relAdd({ a: 'hanako', b: 'ken', role: '同僚' })).id;
  ok(M.relUpdate(x, 'hanako', { other: 'taro', role: '上司', sym: false, inv: '部下', since: '2024-05' }));
  deq([M.DB.rels[x].a, M.DB.rels[x].b, M.DB.rels[x].role, M.DB.rels[x].inv, M.DB.rels[x].from], ['hanako', 'taro', '上司', '部下', '2024-05']);
  ok(M.relUpdate(x, 'taro', { other: 'hanako', role: '部下', sym: false, inv: '上司' }));       /* b 側から見て直す: 保存は a=hanako のまま */
  deq([M.DB.rels[x].a, M.DB.rels[x].b, M.DB.rels[x].role, M.DB.rels[x].inv], ['hanako', 'taro', '上司', '部下']);
  ok(M.relUpdate(x, 'taro', { other: 'hanako', role: '友人', sym: true })); assert.equal(M.DB.rels[x].inv, undefined); assert.equal(M.DB.rels[x].role, '友人');
  assert.equal(M.relUpdate(x, 'hanako', { other: 'hanako', role: 'a', sym: true }).ok, false);
  assert.equal(M.relUpdate(x, 'hanako', { other: 'ken', role: '', sym: true }).ok, false);
  assert.equal(M.relUpdate(x, 'ken', { other: 'taro', role: 'a', sym: true }).ok, false);
  assert.equal(M.relUpdate(x, 'hanako', { other: 'ken', role: 'a', sym: false }).ok, false);          /* 一方向は相手から見た名が要る */
  for (const bad of ['2026-13', '2026-00', '2026-02-30', '26-01', 'abc']) { assert.equal(M.relUpdate(x, { from: bad }).ok, false, bad); assert.equal(M.relAdd({ a: 'ken', b: 'taro', role: 'r', from: bad }).ok, false, bad); assert.equal(M.factSet('hanako', '電話', '1', { since: bad }).ok, false, bad); }
  assert.ok(M.isCoarse('2026') && M.isCoarse('2026-02') && M.isCoarse('2028-02-29') && !M.isCoarse('2027-02-29'));
  M.relRemove(x);
});
T('dbFill / load / importJSON: 欠けた表・型違い・壊れた保存の退避', () => {
  const c = M.dbFill({ meta: { v: 7 }, acts: {}, ents: {}, cats: {} });
  assert.ok(c.ok); for (const t of ['days', 'tasks', 'lists', 'rels', 'photos', 'routines', 'jots', 'memos', 'notes', 'types', 'exps']) if (t !== 'types') deq(c.db[t], {}, t);
  assert.ok(Array.isArray(c.db.log) && c.db.ents.me && c.db.types.method);
  for (const bad of [{ meta: { v: 7 }, acts: [] }, { meta: { v: 7 }, tasks: 'x' }, { meta: { v: 7 }, rels: [] }, { meta: 5 }, [], null]) assert.equal(M.dbFill(bad).ok, false, JSON.stringify(bad));
  const keep = snap();
  assert.ok(M.importJSON(JSON.stringify({ meta: { v: 7 }, acts: {}, ents: {}, cats: {} })).ok);
  deq(Object.keys(M.DB.acts), []); assert.ok(M.DB.tasks && M.DB.lists && Array.isArray(M.DB.log) && M.DB.meta.seeded);
  for (const bad of ['{"meta":{"v":7},"acts":[],"ents":{}}', '{"meta":{"v":7},"tasks":"x"}', '{"meta":{"v":7},"rels":[]}', '{"v":6}', 'nope']) assert.equal(M.importJSON(bad).ok, false, bad);
  // 壊れた保存データは退避され、見本で始まる
  store['prmock-v7'] = '{broken'; M.load();
  assert.equal(store['prmock-v7-broken'], '{broken'); assert.match(vm.runInContext('_brokenNotice', ctx), /読めなかった/); assert.ok(M.DB.acts.l1);
  assert.equal(JSON.parse(store['prmock-v7']).meta.v, 7);
  // 型が違う保存データも退避
  store['prmock-v7'] = JSON.stringify({ meta: { v: 7 }, acts: {}, tasks: [] }); M.load(); assert.ok(store['prmock-v7-broken'].includes('"tasks":[]')); assert.ok(M.DB.acts.l1);
  // 表が欠けた保存データは補って読む(退避しない)
  vm.runInContext('_brokenNotice = null', ctx); delete store['prmock-v7-broken'];
  store['prmock-v7'] = JSON.stringify({ meta: { v: 7, born: '2026-01-01', seeded: 1 }, acts: {}, ents: { me: { n: '自分', types: ['person'], facts: [] } }, cats: {} }); M.load();
  assert.equal(store['prmock-v7-broken'], undefined); assert.equal(vm.runInContext('_brokenNotice', ctx), null); deq(M.DB.tasks, {}); assert.ok(Array.isArray(M.DB.log));
  M.resetData(); assert.ok(M.DB.acts.l1);
});

console.log(`\n${n} tests passed${process.exitCode ? ' (with failures)' : ''}`);
