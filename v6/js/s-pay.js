/* 個人記録 v6 — 支払い: 詳細の中身(payDetail)・記録と編集(openPayEditor)・レシートの読み取り(openReceipt)。接頭辞 py。
   考え方: 支払い = 事実(誰がいくら払い、誰が何を使ったか)/ 支出 = 家計簿(自分の負担の決め方・カテゴリ…)。
   承認するだけ・同じことを2度入力しない: 支払先から前回のカテゴリと支払い方法を引き、活動の中は時刻から決め、
   自分の負担は「ほかの人が関わる時だけ」1回聞く。 */
'use strict';

const PY_BASIS = [['items', '使った分'], ['even', '割り勘'], ['amount', '金額を指定']];
const pyFn = (f, ...a) => `${f}.bind(null${a.map(x => ',' + q(x)).join('')})`;
const pyNum = v => Math.max(0, parseInt(String(v == null ? '' : v).replace(/[^\d]/g, ''), 10) || 0);
const pyOthers = a => payItems(a).some(i => usersOf(i).some(u => u !== 'me')) || paidOf(a).some(p => p.who !== 'me');
const pySign = v => v < 0 ? '−' : '+';
const pyLineTxt = l => `${esc(l.n || (l.v < 0 ? '割引' : 'オプション'))} ${pySign(l.v)}${l.k === 'pct' ? Math.abs(l.v) + '%' : Math.abs(l.v).toLocaleString('ja-JP')}`;
/** 支払いを保存できる形に戻す(品目・払った人・支出をそのまま持つ) */
function pyRebuild(id) {
  const a = DB.acts[id], x = DB.exps[id], pr = rolesOf(a, '支払先')[0], cp = v => v == null ? v : JSON.parse(JSON.stringify(v));
  return { id, date: a.date, start: a.start, parent: a.parent || null, payee: pr ? (pr.e ? { e: pr.e } : { name: pr.name }) : null, roles: cp(a.roles.filter(r => r.r !== '支払先')),
    items: cp(a.pay.items), amt: a.pay.amt || 0, payers: cp(a.pay.payers), settled: a.pay.settled, unread: a.pay.unread ? { ...a.pay.unread } : null, note: a.note, exp: cp(x) };
}

/* =====================================================================
   詳細の中身(route 'a' が bar の下に置く。<section> の並び)
   ===================================================================== */
let PYEXP = new Set();
function payDetail(id) {
  const a = A(id); if (!a || !a.pay) return `<section>${empty({ icon: 'receipt', text: '支払いが見つかりません' })}</section>`;
  if (isPlan(a)) return pyHero(id, a) + pyPlanBody(id, a);
  return pyHero(id, a) + pyReadBox(id, a) + pyMine(id, a) + pyItems(id, a) + pyPaid(id, a);
}
function pyHero(id, a) {
  const par = a.parent && DB.acts[a.parent], un = a.pay.unread || {}, pr = rolesOf(a, '支払先')[0], plan = isPlan(a);
  const nm = pr ? esc(roleName(pr)) : `<span class="py-ph-t">${un.payee ? '読めませんでした' : '支払先なし'}</span>`;
  const mk = un.payee ? readMark(nm) : nm, head = pr && pr.e && E(pr.e) ? `<a class="py-pl" href="#/e/${pr.e}">${mk}${ic('chev')}</a>` : mk;
  const m = plan ? '' : payMethod(a), total = payTotal(a);
  return `<section class="py-hero">
    ${par ? `<a class="py-in" href="#/a/${a.parent}">${kindIc(par)}<span>${esc(actTitle(par))}の中</span></a>` : ''}
    <div class="py-ph">${avatar(pr && pr.e, pr && pr.name, 'lg')}<h1 class="py-payee" data-bar-watch>${head}</h1></div>
    <div class="py-when"><a class="py-day" href="#/day/${a.date}">${fmtFull(a.date)}</a>${a.start && !a.allDay ? `<time class="num">${a.start}</time>` : ''}${m ? `<span class="tag">${esc(m)}</span>` : ''}</div>
    <div class="py-tot"><span>合計</span>${un.amt ? readMark(amt(total, { cls: 'big' })) : amt(total, { cls: 'big' })}</div></section>`;
}
function pyPlanBody(id, a) {
  const pl = a.pay.plan || {};
  return `<section>${rows([prop('金額', amt(pl.amt || 0)), prop('カテゴリ', catTag(pl.cat, { path: 1 })), pl.how ? prop('支払い方法', esc(pl.how)) : ''])}${btn('記録にする', `recordPlan(${q(id)})`, 'pri', { wide: 1, icon: 'check' })}</section>`;
}
/** 読み取ったまま: 主ボタン「確かめました」 */
function pyReadBox(id, a) {
  const n = unreadCount(a); if (!n) return '';
  return `<section><div class="card pad py-read"><div class="py-read-h"><span class="py-read-i">${ic('receipt')}</span><b>読み取ったまま</b>${tag(`未確認 ${n}`, 'read')}</div>${btn('確かめました', `pyConfirm(${q(id)})`, 'pri', { wide: 1, icon: 'check' })}</div></section>`;
}
function pyMine(id, a) {
  const x = xOf(id), total = payTotal(a);
  if (!x) return `<section>${sec('自分の支出')}<div class="card pad py-mine"><div class="py-mine-h"><span class="tag">自分の負担なし</span></div>${btn('支出に入れる', `pyXAdd(${q(id)})`, 'sec', { icon: 'plus' })}</div></section>`;
  const mine = xAmt(id), b = balanceOf(id), others = pyOthers(a), split = total !== mine || b.amt !== 0, cs = xCats(id);
  const sp = xSp(id), fx = xFx(id);
  const basisX = x.basis === 'even' ? `<div class="py-ex"><span>人数</span><div class="py-step"><button onclick="pyN(${q(id)},-1)" aria-label="人数を減らす">−</button><b class="num">${x.n || 2}人</b><button onclick="pyN(${q(id)},1)" aria-label="人数を増やす">＋</button></div></div>`
    : x.basis === 'amount' ? `<div class="py-ex"><span>自分の負担</span>${input({ value: x.me || 0, mode: 'numeric', onchange: `pyMe(${q(id)},this.value)`, cls: 'num', label: '自分の負担' })}</div>` : '';
  const opt = (k, items, cur, fn) => `<div class="py-opt"><span class="prop-k">${k}</span>${seg(items, cur, pyFn(fn, id))}</div>`;
  return `<section>${sec('自分の支出')}
    <div class="card pad py-mine"><div class="py-mine-h"><span class="py-k">自分の負担</span>${b.amt ? (b.open ? tag(balanceText(b), 'owe') : tag('精算済み')) : ''}</div>
      ${amt(mine, { cls: 'hero' })}
      ${split ? `<div class="py-sp">${splitBar(id)}<span class="mini">合計 ${yen(total)} のうち ${Math.round(mine / (total || 1) * 100)}%</span></div>` : ''}
      ${b.amt ? `<div class="py-bal">${b.settled ? `<span class="mini">${esc(balanceText(b))}</span>` : ''}${btn(b.settled ? '未精算に戻す' : '精算済みにする', `pySettle(${q(id)},${!b.settled})`, 'sec', { sm: 1 })}</div>` : ''}</div>
    ${rows([
      others ? `<div class="py-opt"><span class="prop-k">負担の決め方</span>${seg(PY_BASIS, x.basis || 'items', pyFn('pyBasis', id))}${basisX}</div>` : '',
      prop('カテゴリ', catTag(x.cat, { path: 1 }), { on: `pyCatPick(${q(id)})` }),
      opt('特別感', [['', catSp(x.cat) ? `既定(${catSp(x.cat)})` : '既定'], ['日常', '日常'], ['非日常', '非日常']], x.sp || '', 'pySp'),
      opt('固定・変動', [['', catFx(x.cat) ? `既定(${catFx(x.cat)})` : '既定'], ['固定', '固定'], ['変動', '変動']], x.fx || '', 'pyFx'),
      `<label class="py-opt"><span class="prop-k">メモ</span><textarea class="input area" rows="2" placeholder="メモ" onchange="pyMemo(${q(id)},this.value)" aria-label="メモ">${esc(x.memo || '')}</textarea></label>`])}
    ${cs.length >= 2 ? `${sec('カテゴリの内訳')}${rows(cs.map(c => row({ tHtml: catTag(c.cat), right: amt(c.amt) })))}` : ''}</section>`;
}
/** 品目。同じ名前・金額・使った人が続く時は「×2」にまとめる(データはそのまま) */
function pyItems(id, a) {
  const its = a.pay.items, x = xOf(id);
  if (!its.length) return `<section>${sec('品目')}${rows(row({ t: '合計だけ', right: amt(payTotal(a)) }))}${btn('品目に分ける', `pySplit(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
  const sh = x ? xShares(id) : [], key = (it, i) => JSON.stringify([it.n || '', itemAmt(it), usersOf(it).slice().sort(), it.lines || null, itemTax(it), it.memo || '', (x && x.itemCat && x.itemCat[i]) || '', it.unk ? 1 : 0, it.unread ? 1 : 0, sh[i]]);
  const out = [];
  for (let i = 0; i < its.length;) { let n = 1; while (i + n < its.length && key(its[i + n], i + n) === key(its[i], i)) n++; out.push(...pyItemRows(id, a, i, n)); i += n; }
  return `<section>${sec('品目', { n: its.length })}${rows(out)}${btn('品目を足す', `pyItemOpen(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
}
function pyItemRows(id, a, i, n) {
  const open = PYEXP.has(id + ':' + i);
  if (n > 1 && open) return [pyItemRow(id, a, i, n, { head: 1 }), ...[...Array(n)].map((_, j) => pyItemRow(id, a, i + j, 1, { sub: 1 }))];
  return [pyItemRow(id, a, i, n, {})];
}
function pyItemRow(id, a, i, n, o) {
  const it = a.pay.items[i], x = xOf(id), xi = x ? xItems(id).find(r => r.i === i) : null, v = itemAmt(it), unk = !!it.unk, u = usersOf(it);
  const nameH = it.n ? esc(it.n) : `<span class="py-ph-t">${unk ? '読めませんでした' : '名前なし'}</span>`;
  const sub = [];
  (it.lines || []).slice(1).forEach(l => sub.push(`<span class="py-ln num">${pyLineTxt(l)}</span>`));
  if (unk && it.n) sub.push(tag('読めませんでした', 'read'));
  if (itemTax(it) === 8) sub.push(tag('税8%'));
  if (u.some(w => w !== 'me')) sub.push(`<span class="ents">${u.map(w => ent(w, { link: false, short: 1 })).join('')}</span>`);
  if (xi && xi.how !== '全額') sub.push(xi.how === '奢り' ? tag('奢り') : tag(`自分 ${xi.how}`, 'me'));
  if (x && x.itemCat && x.itemCat[i] && x.itemCat[i] !== x.cat) sub.push(catTag(x.itemCat[i]));
  if (it.memo) sub.push(`<span class="mini">${esc(it.memo)}</span>`);
  const price = unk && !v ? `<span class="py-ph-t">金額</span>` : it.unread ? readMark(amt(v * n)) : amt(v * n);
  return row({ tHtml: `${nameH}${n > 1 ? ` <span class="py-x num">×${n}</span>` : ''}`, sHtml: sub.join(''), right: price, on: n > 1 ? `pyGroup(${q(id)},${i})` : `pyItemOpen(${q(id)},${i})`, cls: `py-it${unk ? ' unk' : ''}${o.sub ? ' sub' : ''}` });
}
function pyPaid(id, a) {
  const ps = paidOf(a), me1 = ps.length === 1 && ps[0].who === 'me';
  const body = me1 ? row({ lead: ic('wallet'), t: ps[0].how || '—', s: '自分が払った' })
    : ps.map(p => row({ lead: avatar(p.who, p.name), tHtml: esc(whoName(p.who, p.name)), s: p.how || '', right: amt(p.amt) })).join('');
  return `<section>${sec('払った人', { act: { label: '直す', on: `openPayEditor({id:${q(id)}})` } })}${rows(body)}</section>`;
}
/* ---- 詳細の操作(全部 model の関数。done が取り消しと描き直しを受け持つ) ---- */
const pyConfirm = id => done(payConfirm(id));
const pySettle = (id, on) => done(paySettle(id, on));
function pyXAdd(id) { const a = A(id), r = rolesOf(a, '支払先')[0]; done(expUpdate(id, { basis: 'even', n: Math.max(2, peopleOf(id).length), cat: catGuess(r ? (r.e ? { e: r.e } : { name: r.name }) : null) })); }
function pyBasis(id, k) { const x = xOf(id), p = { basis: k }; if (k === 'even') p.n = x.n || Math.max(2, peopleOf(id).length); if (k === 'amount') p.me = x.me != null ? x.me : xAmt(id); done(expUpdate(id, p)); }
const pyN = (id, d) => done(expUpdate(id, { n: Math.max(2, Math.min(99, (xOf(id).n || 2) + d)) }), { quiet: 1 });
const pyMe = (id, v) => done(expUpdate(id, { me: pyNum(v) }), { quiet: 1 });
const pySp = (id, k) => done(expUpdate(id, { sp: k || null }));
const pyFx = (id, k) => done(expUpdate(id, { fx: k || null }));
function pyMemo(id, v) { const r = expUpdate(id, { memo: v.trim() }); if (!r.ok) done(r); }
const pyCatItems = () => { const out = [], walk = (k, d) => catKids(k).forEach(c => { out.push({ k: c, label: DB.cats[c].n, depth: d, lead: `<i class="py-sw" style="background:${catColor(c)}"></i>` }); walk(c, d + 1); }); walk(null, 0); return out; };
function pyCatPick(id) { pick({ title: 'カテゴリ', items: pyCatItems(), cur: xOf(id).cat, search: true, on: k => done(expUpdate(id, { cat: k })) }); }
function pyGroup(id, i) { const k = id + ':' + i; PYEXP.has(k) ? PYEXP.delete(k) : PYEXP.add(k); rerender(); }
function pySplit(id) { pyItemOpen(id); }

/* =====================================================================
   品目のシート(名前・金額行・税率・使った人・この品目のカテゴリ・メモ・消す)
   ===================================================================== */
let PYI = null, PYI0 = '';
function pyItemOpen(id, ix) {
  const a = A(id), x = xOf(id), it = ix == null ? null : a.pay.items[ix];
  const ls = it ? (it.lines && it.lines.length ? it.lines.map(l => ({ ...l })) : [{ n: '', k: 'amt', v: it.price || 0 }]) : [{ n: '', k: 'amt', v: a.pay.items.length ? 0 : payTotal(a) }];
  PYI = { id, ix: ix == null ? null : ix, n: it ? it.n : '', lines: ls.map((l, j) => j ? { n: l.n, k: l.k, v: Math.abs(l.v), dis: l.v < 0 } : { n: '', k: 'amt', v: l.v }), tax: it ? itemTax(it) : TAX_DEFAULT, users: it ? usersOf(it).slice() : ['me'],
    cat: it && x && x.itemCat && x.itemCat[ix] || null, memo: it && it.memo || '', unk: it && it.unk ? 1 : 0, unread: it && it.unread ? 1 : 0 };
  PYI0 = JSON.stringify(PYI);
  openSheet({ id: 'pay-item', title: it ? '品目を直す' : '品目を足す', body: pyiBody(), dirty: () => JSON.stringify(PYI) !== PYI0, primary: { label: '保存', on: pyiSave }, secondary: it ? { label: '消す', on: pyiDel } : null });
}
const pyiCalc = ls => lnAmts(ls.map(l => ({ k: l.k, v: l.dis ? -l.v : l.v })));
function pyiSum() {
  const I = PYI, am = pyiCalc(I.lines), tot = am.reduce((s, v) => s + v, 0); if (I.lines.length < 2) return '';
  return `<div class="card pad py-sum">${I.lines.map((l, j) => `<div class="py-sum-r"><span>${j ? esc(l.n || (l.dis ? '割引' : 'オプション')) : '本体'}</span><b class="num">${am[j] < 0 ? '−' : j ? '+' : ''}${yen(Math.abs(am[j]))}</b></div>`).join('')}<div class="py-sum-r tot"><span>合計</span><b class="num">${yen(tot)}</b></div></div>`;
}
function pyiBody() {
  const I = PYI, x = xOf(I.id), ppl = peopleOf(I.id);
  const lineRow = (l, j) => `<div class="py-ln-r"><div class="py-ln-n">${input({ value: l.n, ph: l.dis ? '割引の名前' : 'オプションの名前', oninput: `pyiLn(${j},'n',this.value)`, label: 'この行の名前' })}</div>
    <span class="py-sg">${l.dis ? '−' : '+'}</span>${input({ value: l.v || '', ph: '0', mode: 'numeric', oninput: `pyiLn(${j},'v',this.value)`, cls: 'num py-ln-v', label: '値' })}
    ${l.dis ? `<button class="py-unit" onclick="pyiUnit(${j})" aria-label="円と%を切り替える">${l.k === 'pct' ? '%' : '円'}</button>` : `<span class="py-sg">${l.k === 'pct' ? '%' : '円'}</span>`}${iconBtn('close', 'この行を消す', `pyiLnDel(${j})`)}</div>`;
  return `${field('品名', input({ id: 'pyi-n', value: PYI.n, ph: PYI.unk ? '読めませんでした' : '名前', oninput: 'pyiN(this.value)', auto: !PYI.n && !PYI.unk, label: '品名', cls: PYI.unk ? 'py-unread' : '' }))}
    ${field('金額', `<div class="py-amt-f"><i>¥</i>${input({ value: I.lines[0].v || '', ph: '0', mode: 'numeric', oninput: 'pyiV(this.value)', cls: 'big num', label: '金額' })}</div>`)}
    ${I.lines.length > 1 ? `<div class="field"><span class="field-l">オプション・割引</span>${I.lines.slice(1).map((l, j) => lineRow(l, j + 1)).join('')}</div>` : ''}
    <div class="grid2">${btn('オプション', 'pyiAdd(0)', 'dash', { icon: 'plus' })}${btn('割引', 'pyiAdd(1)', 'dash', { icon: 'plus' })}</div>
    <div id="pyi-sum">${pyiSum()}</div>
    <div class="field"><span class="field-l">税率</span>${seg([[10, '税10%'], [8, '税8%']], I.tax, 'pyiTax')}</div>
    ${ppl.length > 1 ? `<div class="field"><span class="field-l">使った人</span>${chips(ppl.map(w => [w, whoShort(w)]), I.users, 'pyiUser').replace('class="chips"', 'class="chips wrap"')}</div>` : ''}
    ${x ? pickField('この品目のカテゴリ', I.cat ? catTag(I.cat) : `既定(${esc(catName(x.cat))})`, 'pyiCat()', { emptyv: !I.cat }) : ''}
    ${field('メモ', input({ value: I.memo, ph: 'メモ', oninput: 'pyiMemo(this.value)', label: 'メモ' }))}`;
}
const pyiRe = () => setSheetBody(pyiBody());
const pyiSumRe = () => { const e = $('#pyi-sum'); if (e) e.innerHTML = pyiSum(); };
const pyiN = v => { PYI.n = v; };
const pyiV = v => { PYI.lines[0].v = pyNum(v); pyiSumRe(); };
const pyiMemo = v => { PYI.memo = v; };
function pyiLn(j, f, v) { PYI.lines[j][f] = f === 'v' ? pyNum(v) : v; pyiSumRe(); }
const pyiUnit = j => { PYI.lines[j].k = PYI.lines[j].k === 'pct' ? 'amt' : 'pct'; pyiRe(); };
const pyiAdd = dis => { PYI.lines.push({ n: '', k: 'amt', v: 0, dis: !!dis }); pyiRe(); };
const pyiLnDel = j => { PYI.lines.splice(j, 1); pyiRe(); };
const pyiTax = k => { PYI.tax = k === 8 ? 8 : 10; pyiRe(); };
function pyiUser(k) { const u = PYI.users, i = u.indexOf(k); if (i >= 0) { if (u.length > 1) u.splice(i, 1); } else u.push(k); pyiRe(); }
function pyiCat() { const x = xOf(PYI.id); pick({ title: 'この品目のカテゴリ', items: pyCatItems(), cur: PYI.cat, search: true, none: { label: `既定と同じ(${catName(x.cat)})` }, on: k => { PYI.cat = k; pyiRe(); } }); }
function pyiSave() {
  const I = PYI, ls = I.lines.map(l => ({ n: l.n, k: l.k, v: l.dis ? -l.v : l.v })), tot = pyiCalc(I.lines).reduce((s, v) => s + v, 0);
  if (!I.n.trim() || tot <= 0) return toast('品名と金額を入れてください', { bad: 1 });
  const o = pyRebuild(I.id), a = DB.acts[I.id], oldTot = payTotal(a);
  const item = { n: I.n.trim(), tax: I.tax, users: I.users.slice(), memo: I.memo.trim(), ...(ls.length > 1 && ls.slice(1).some(l => l.v) ? { lines: ls } : { price: ls[0].v }), ...(I.unread ? { unread: 1 } : {}) };
  if (I.ix == null) o.items.push(item); else o.items[I.ix] = item;
  const ix = I.ix == null ? o.items.length - 1 : I.ix, nt = o.items.reduce((s, i) => s + itemAmt(i), 0);
  o.exp = o.exp || { basis: 'items', cat: catGuess(o.payee) };
  const ic2 = { ...(o.exp.itemCat || {}) }; if (I.cat) ic2[ix] = I.cat; else delete ic2[ix]; o.exp.itemCat = ic2;
  if (o.payers.length > 1 && nt !== oldTot) { let d = nt - oldTot; if (d > 0) o.payers[0].amt += d; else for (let i = o.payers.length - 1; i >= 0 && d < 0; i--) { const t = Math.min(-d, o.payers[i].amt); o.payers[i].amt -= t; d += t; } }
  done(paySave(o), { close: 1, msg: I.ix == null ? '品目を足しました' : '品目を直しました' });
}
function pyiDel() { done(itemRemove(PYI.id, PYI.ix), { close: 1 }); }

/* =====================================================================
   支払いを記録・直す(1画面)
   ===================================================================== */
let PYD = null, PYD0 = '';
const pyAsk = (cat, f) => (f === 'fx' ? catFx(cat) : catSp(cat)) == null;
function pyCands(d) {
  const out = [], walk = (list, depth) => list.forEach(([id, a]) => { if (isPay(a)) return; if (isSpan(a) && !isPlan(a)) out.push({ id, a, depth }); walk(kidsOf(id), depth + 1); });
  walk(actsOn(d, { state: 'done' }), 0); return out;
}
function pyDefParent(d, t) {
  const c = pyCands(d).filter(x => t && x.a.start <= t && t < x.a.end).sort((p, r) => r.depth - p.depth || r.a.start.localeCompare(p.a.start))[0];
  return c ? c.id : null;
}
function pyParentPeople(pid) { return pid && DB.acts[pid] ? [...new Set([[pid, DB.acts[pid]], ...ancOf(pid)].flatMap(([, x]) => rolesOf(x, '同席', '参加').map(r => r.e).filter(e => e && E(e))))] : []; }
function pyPrevMethod(payee) {
  if (!payee) return null;
  const p = Object.values(DB.acts).filter(a => a.pay && !isPlan(a) && rolesOf(a, '支払先').some(r => (payee.e && r.e === payee.e) || (payee.name && r.name === payee.name))).sort((x, y) => (y.date + y.start).localeCompare(x.date + x.start))[0];
  const me = p && paidOf(p).find(z => z.who === 'me'); return me && me.how || null;
}
function pyNorm() { const D = PYD; if (pyAsk(D.cat, 'fx') && !D.fx) D.fx = '変動'; if (pyAsk(D.cat, 'sp') && !D.sp) D.sp = '日常'; }
function pyDraft(o) {
  const seed = window._capSeed && window._capSeed.type === 'pay' ? window._capSeed : null, cp = v => JSON.parse(JSON.stringify(v));
  let D;
  if (o.id) {
    const a = DB.acts[o.id]; if (!a || !a.pay) return null;
    const pr = rolesOf(a, '支払先')[0], x = DB.exps[o.id], items = cp(a.pay.items).map(i => ({ ...i, users: usersOf(i).slice() }));
    const ppl = new Set(rolesOf(a, '同席', '参加').map(r => r.e).filter(Boolean)); items.forEach(i => i.users.forEach(u => u !== 'me' && ppl.add(u))); a.pay.payers.forEach(p => p.who && p.who !== 'me' && ppl.add(p.who));
    const payers = a.pay.payers.length ? cp(a.pay.payers) : [{ who: 'me', amt: payTotal(a), how: DB.methods[0] }], mp = payers.find(p => p.who === 'me');
    D = { id: o.id, edit: 1, date: a.date, start: a.start || NOW, parent: a.parent || null, parentTouched: 1, payee: pr ? (pr.e ? { e: pr.e } : { name: pr.name }) : null, amt: items.length ? 0 : (a.pay.amt || 0), items,
      people: [...ppl], peopleTouched: 1, paySet: payers.map(p => p.who), payAmt: Object.fromEntries(payers.map(p => [p.who, p.amt])), payHow: Object.fromEntries(payers.map(p => [p.who, p.how])), method: mp ? mp.how : DB.methods[0], methodTouched: 1,
      cat: x ? x.cat : catGuess(pr ? (pr.e ? { e: pr.e } : { name: pr.name }) : null), catTouched: 1, basis: x ? x.basis : 'items', n: x && x.n || 0, me: x && x.me || 0, fx: x && x.fx || null, sp: x && x.sp || null, memo: x && x.memo || '', itemCat: x && x.itemCat ? cp(x.itemCat) : {},
      unread: a.pay.unread ? { ...a.pay.unread } : {}, settled: !!a.pay.settled, roles: a.roles.filter(r => r.r !== '支払先' && r.r !== '同席' && r.r !== '参加'), note: a.note };
  } else {
    const rd = o.read, date = o.date || (rd && rd.date) || (seed && seed.date) || TODAY, start = o.start || (rd && rd.start) || (seed && seed.start) || NOW;
    const payee = rd ? rd.payee : seed && seed.payee ? seed.payee : null, parent = o.parent !== undefined ? (o.parent && DB.acts[o.parent] ? o.parent : null) : pyDefParent(date, start);
    const ppl = [...new Set([...(seed && seed.ents || []).filter(e => E(e) && isUnder(E(e).types[0], 'person')), ...pyParentPeople(parent)])];
    const items = rd ? rd.items.map(i => ({ n: i.n || '', price: i.price || 0, tax: i.tax || TAX_DEFAULT, users: ['me'], ...(i.unk ? { unk: 1 } : {}), unread: 1 })) : [];
    D = { id: null, edit: 0, fromRead: rd ? 1 : 0, date, start, parent, parentTouched: o.parent !== undefined, payee, amt: rd && !rd.items.length ? rd.amt : (!rd && (o.amt || seed && seed.amt)) || 0, items,
      people: ppl, peopleTouched: false, paySet: ['me'], payAmt: {}, payHow: {}, method: pyPrevMethod(payee) || DB.methods[0], methodTouched: false,
      cat: catGuess(payee, seed && seed.text || ''), catTouched: false, basis: 'items', n: 0, me: 0, fx: null, sp: null, memo: '', itemCat: {}, unread: rd ? { ...rd.unread } : {}, settled: false, roles: [] };
  }
  PYD = D; pyNorm(); return D;
}
function pyTotal(D = PYD) { return D.items.length ? D.items.reduce((s, i) => s + itemAmt(i), 0) : (+D.amt || 0); }
const pyUsers = (D, i) => i.users.filter(u => u === 'me' || D.people.includes(u));
const pyNeed = (D = PYD) => D.items.some(i => pyUsers(D, i).some(u => u !== 'me')) || D.paySet.some(w => w !== 'me');
function pyCalc(D = PYD) {
  const total = pyTotal(D), need = pyNeed(D), basis = need ? D.basis : 'items'; let mine;
  if (basis === 'even') mine = Math.floor(total / Math.max(2, D.n || D.people.length + 1));
  else if (basis === 'amount') mine = Math.max(0, Math.min(D.me || 0, total));
  else mine = D.items.length ? D.items.reduce((s, i) => { const u = pyUsers(D, i); return s + (u.includes('me') ? Math.floor(itemAmt(i) / u.length) : 0); }, 0) : total;
  const paid = D.paySet.length === 1 ? (D.paySet[0] === 'me' ? total : 0) : (+D.payAmt.me || 0), bal = paid - mine;
  const who = bal > 0 ? [...new Set(D.items.flatMap(i => pyUsers(D, i).filter(u => u !== 'me')))] : D.paySet.filter(w => w !== 'me');
  return { total, mine, bal, who: who.length ? who : D.people.slice() };
}
const pyPaySum = (D = PYD) => D.paySet.length > 1 ? D.paySet.reduce((s, w) => s + (+D.payAmt[w] || 0), 0) : pyTotal(D);
const pyBad = (D = PYD) => pyTotal(D) <= 0 || pyPaySum(D) !== pyTotal(D);
function pyPayDist() { const t = pyTotal(), n = PYD.paySet.length, b = Math.floor(t / n); PYD.payAmt = {}; PYD.paySet.forEach((w, i) => { PYD.payAmt[w] = i ? b : t - b * (n - 1); }); }

function openPayEditor(o = {}) {
  if (!pyDraft(o)) return toast('支払いが見つかりません', { bad: 1 });
  PYD0 = JSON.stringify(PYD);
  openSheet({ id: 'pay-ed', title: o.id ? '支払いを直す' : '支払いを記録', tall: true, lead: o.id ? '' : `<button class="btn sec sm" onclick="pyToReceipt()">${ic('camera')}<span>レシートを読み取る</span></button>`,
    body: pyEdBody(), dirty: () => PYD && (PYD.fromRead || JSON.stringify(PYD) !== PYD0), primary: { label: o.id ? '保存' : '支払いを保存', on: pySaveEd, disabled: pyBad() } });
}
const pyPrimary = () => setSheetPrimary({ label: PYD.edit ? '保存' : '支払いを保存', on: pySaveEd, disabled: pyBad() });
function pyRe() { setSheetBody(pyEdBody()); pyPrimary(); }
function pyEdBody() {
  const D = PYD, total = pyTotal(), hasIt = D.items.length > 0, need = pyNeed(), un = D.unread || {}, pp = D.people, c = pyCalc();
  const payeeV = D.payee ? ent(D.payee.e || null, { link: false, name: D.payee.name }) : (un.payee ? '読めませんでした' : '支払先を選ぶ');
  const par = D.parent && DB.acts[D.parent];
  const amtB = hasIt ? `<div class="py-amt-f"><span id="py-tot">${pyTotHtml()}</span><span class="mini">品目の合計</span></div>`
    : `<div class="py-amt-f"><i>¥</i>${input({ id: 'py-amt', value: D.amt || '', ph: '0', mode: 'numeric', oninput: 'pyAmt(this.value)', cls: `big num${un.amt ? ' py-unread' : ''}`, auto: !D.edit && !D.fromRead && !D.amt, label: '金額' })}</div>`;
  const itemRow = (it, i) => `<div class="py-eit${it.unk ? ' py-unk' : ''}"><div class="py-eit-r">${input({ value: it.n, ph: it.unk ? '読めませんでした' : '品名', oninput: `pyItN(${i},this.value)`, cls: it.unread || it.unk ? 'py-unread' : '', label: '品名' })}
      ${it.lines ? `<span class="py-eit-a">${amt(itemAmt(it))}<small>金額行あり</small></span>` : input({ value: it.price || '', ph: '金額', mode: 'numeric', oninput: `pyItP(${i},this.value)`, cls: `num py-eit-p${it.unread ? ' py-unread' : ''}`, label: '金額' })}${iconBtn('trash', 'この品目を消す', `pyItDel(${i})`)}</div>
      ${pp.length ? chips([['me', '自分'], ...pp.map(w => [w, whoShort(w)])], pyUsers(D, it), pyFn('pyItU', i)).replace('class="chips"', 'class="chips wrap"') : ''}</div>`;
  const askFx = pyAsk(D.cat, 'fx'), askSp = pyAsk(D.cat, 'sp');
  const payers = pp.length ? `<div class="field"><span class="field-l">払った人</span>${chips([['me', '自分'], ...pp.map(w => [w, whoShort(w)])], D.paySet, 'pyPayer').replace('class="chips"', 'class="chips wrap"')}
      ${D.paySet.length > 1 ? D.paySet.map(w => `<div class="py-pr"><span>${ent(w, { link: false, short: 1 })}</span>${input({ id: 'py-pa-' + w, value: D.payAmt[w] || '', mode: 'numeric', ph: '0', oninput: `pyPayAmt(${q(w)},this.value)`, cls: 'num', label: whoShort(w) + 'が払った額' })}</div>`).join('') + `<div id="py-psum">${pyPsum()}</div>` : ''}</div>` : '';
  const split = need ? `<div class="field"><span class="field-l">自分の負担</span><div class="card pad py-split">${seg(PY_BASIS, D.basis, 'pyBasisEd')}
      ${D.basis === 'even' ? `<div class="py-ex"><span>人数</span><div class="py-step"><button onclick="pyNEd(-1)" aria-label="人数を減らす">−</button><b class="num">${D.n || pp.length + 1}人</b><button onclick="pyNEd(1)" aria-label="人数を増やす">＋</button></div></div>` : ''}
      ${D.basis === 'amount' ? `<div class="py-ex"><span>自分の負担</span>${input({ value: D.me || '', ph: '0', mode: 'numeric', oninput: 'pyMeEd(this.value)', cls: 'num', label: '自分の負担' })}</div>` : ''}
      <div id="py-res">${pyRes(c)}</div></div></div>` : '';
  return `<div class="field"><span class="field-l">金額</span>${amtB}</div>
    ${pickField('支払先', un.payee && D.payee ? readMark(payeeV) : payeeV, 'pyPayee()', { emptyv: !D.payee })}
    <div class="grid2">${field('日付', input({ id: 'py-d', type: 'date', value: D.date, onchange: 'pyWhen()', label: '日付' }))}${field('時刻', input({ id: 'py-t', type: 'time', value: D.start, onchange: 'pyWhen()', label: '時刻' }))}</div>
    ${D.paySet.includes('me') ? `<div class="field"><span class="field-l">支払い方法</span>${chips(DB.methods.map(m => [m, m]), D.method, 'pyMethod').replace('class="chips"', 'class="chips wrap"')}</div>` : ''}
    ${pickField('カテゴリ', catTag(D.cat, { path: 1 }), 'pyCatEd()')}
    ${pickField('活動の中', par ? `${kindIc(par)}<span>${esc(actTitle(par))}</span><span class="mini num">${timeText(par)}</span>` : 'なし(日の直下)', 'pyParent()', { emptyv: !par })}
    <div class="field"><span class="field-l">品目</span>${D.items.map(itemRow).join('')}${btn('品目を足す', 'pyItAdd()', 'dash', { icon: 'plus' })}</div>
    ${pickField('一緒の人', pp.length ? `<span class="ents">${pp.map(e => ent(e, { link: false })).join('')}</span>` : '足す', 'pyPeople()', { emptyv: !pp.length })}
    ${payers}${split}
    ${askFx ? `<div class="field"><span class="field-l">固定・変動</span>${seg([['固定', '固定'], ['変動', '変動']], D.fx, 'pyFxEd')}</div>` : ''}
    ${askSp ? `<div class="field"><span class="field-l">特別感</span>${seg([['日常', '日常'], ['非日常', '非日常']], D.sp, 'pySpEd')}</div>` : ''}
    ${field('メモ', textarea({ rows: 2, ph: 'メモ', oninput: 'pyMemoEd(this.value)', value: D.memo, label: 'メモ' }))}`;
}
const pyTotHtml = () => PYD.unread && PYD.unread.amt ? readMark(amt(pyTotal(), { cls: 'big' })) : amt(pyTotal(), { cls: 'big' });
const pyRes = c => `<div class="py-res"><span class="py-k">自分の負担</span>${amt(c.mine, { cls: 'big' })}${c.bal ? tag(balanceText({ amt: c.bal, who: c.who }), 'owe') : ''}</div>`;
function pyPsum() { const t = pyTotal(), s = pyPaySum(), d = t - s; return `<div class="py-psum num"><span>払った額の合計 ${yen(s)} / ${yen(t)}</span>${d ? tag(d > 0 ? `あと ${yen(d)}` : `超過 ${yen(-d)}`) : ''}</div>`; }
/** 入力のたびに、合計・自分の負担・払った額の合計だけを書き換える(フォーカスを失わない) */
function pyRefresh() {
  const t = $('#py-tot'); if (t) t.innerHTML = pyTotHtml(); const r = $('#py-res'); if (r) r.innerHTML = pyRes(pyCalc()); const p = $('#py-psum'); if (p) p.innerHTML = pyPsum();
  const bad = pyBad(); if (PYD._bad !== bad) { PYD._bad = bad; pyPrimary(); }
}
/* ---- 入力 ---- */
function pyAmt(v) { PYD.amt = pyNum(v); delete PYD.unread.amt; if (PYD.paySet.length > 1) pyPayDist(); pyRefresh(); }
function pyItN(i, v) { const it = PYD.items[i]; it.n = v; if (v.trim()) delete it.unk; delete it.unread; }
function pyItP(i, v) { const it = PYD.items[i]; it.price = pyNum(v); delete it.unread; pyRefresh(); }
function pyItU(i, k) { const u = PYD.items[i].users, j = u.indexOf(k); if (j >= 0) { if (u.length > 1) u.splice(j, 1); } else u.push(k); pyRe(); }
function pyItAdd() { const D = PYD; D.items.push({ n: '', price: D.items.length ? 0 : D.amt, tax: TAX_DEFAULT, users: ['me'] }); if (D.items.length === 1) D.amt = 0; pyRe(); }
function pyItDel(i) {
  const D = PYD, it = D.items[i]; D.items.splice(i, 1);
  const ic2 = {}; Object.entries(D.itemCat).forEach(([k, v]) => { const n = +k; if (n !== i) ic2[n > i ? n - 1 : n] = v; }); D.itemCat = ic2;
  if (!D.items.length) D.amt = itemAmt(it); if (D.paySet.length > 1) pyPayDist(); pyRe();
}
const pyMethod = m => { PYD.method = m; PYD.methodTouched = 1; pyRe(); };
const pyBasisEd = k => { PYD.basis = k; if (k === 'even' && !PYD.n) PYD.n = PYD.people.length + 1; pyRe(); };
const pyNEd = d => { PYD.n = Math.max(2, Math.min(99, (PYD.n || PYD.people.length + 1) + d)); pyRe(); };
const pyMeEd = v => { PYD.me = pyNum(v); pyRefresh(); };
const pyFxEd = k => { PYD.fx = k; pyRe(); };
const pySpEd = k => { PYD.sp = k; pyRe(); };
const pyMemoEd = v => { PYD.memo = v; };
function pyPayer(k) {
  const D = PYD, s = D.paySet, i = s.indexOf(k); if (i >= 0) { if (s.length > 1) s.splice(i, 1); } else s.push(k);
  D.paySet = ['me', ...D.people].filter(w => D.paySet.includes(w)); if (D.paySet.length > 1) pyPayDist(); pyRe();
}
function pyPayAmt(w, v) { PYD.payAmt[w] = pyNum(v); pyRefresh(); }
/** 日付・時刻を変えたら、利用者が選んでいなければ活動の中を決め直す */
function pyWhen() {
  const D = PYD; D.date = val('py-d') || D.date; D.start = val('py-t') || D.start;
  if (D.parent && !pyCands(D.date).some(x => x.id === D.parent)) { D.parent = null; D.parentTouched = false; }
  if (!D.parentTouched) { D.parent = pyDefParent(D.date, D.start); if (!D.peopleTouched) pyApplyPeople(pyParentPeople(D.parent)); }
  pyRe();
}
function pyApplyPeople(list) {
  const D = PYD; D.people = list.slice(); const ok = w => w === 'me' || D.people.includes(w);
  D.paySet = D.paySet.filter(ok); if (!D.paySet.length) D.paySet = ['me']; if (D.paySet.length > 1) pyPayDist();
}
/* ---- 選ぶシート ---- */
function pyPayee() {
  const rec = Object.values(DB.acts).filter(a => a.pay && !isPlan(a)).sort((x, y) => (y.date + y.start).localeCompare(x.date + x.start)), ord = new Map(), names = [];
  rec.forEach(a => { const r = rolesOf(a, '支払先')[0]; if (!r) return; if (r.e) { if (!ord.has(r.e)) ord.set(r.e, ord.size); } else if (r.name && !names.includes(r.name)) names.push(r.name); });
  const ents = [...new Set([...entsOfType('place'), ...entsOfType('company')])].sort((x, y) => (ord.has(x) ? ord.get(x) : 999) - (ord.has(y) ? ord.get(y) : 999));
  const items = [...ents.map(id => ({ k: 'e:' + id, label: E(id).n, sub: DB.types[E(id).types[0]].n, lead: avatar(id) })), ...names.slice(0, 8).map(n => ({ k: 'n:' + n, label: n, sub: '最近の支払先', lead: avatar(null, n) }))];
  const D = PYD, cur = D.payee ? (D.payee.e ? 'e:' + D.payee.e : 'n:' + D.payee.name) : null;
  const set = p => {
    D.payee = p; delete D.unread.payee;
    if (!D.catTouched) { const k = catGuess(p); if (k !== D.cat) { D.cat = k; D.fx = D.sp = null; pyNorm(); } }
    if (!D.methodTouched) D.method = pyPrevMethod(p) || D.method; pyRe();
  };
  pick({ title: '支払先', items, cur, search: '店・会社をさがす・新しく入れる', create: { label: 'をそのまま使う', on: t => set({ name: t }) }, on: k => set(k.startsWith('e:') ? { e: k.slice(2) } : { name: k.slice(2) }) });
}
function pyCatEd() {
  pick({ title: 'カテゴリ', items: pyCatItems(), cur: PYD.cat, search: true, on: k => { if (k !== PYD.cat) { PYD.cat = k; PYD.fx = PYD.sp = null; pyNorm(); } PYD.catTouched = 1; pyRe(); } });
}
function pyParent() {
  const D = PYD, cs = pyCands(D.date);
  if (D.parent && DB.acts[D.parent] && !cs.some(x => x.id === D.parent)) cs.unshift({ id: D.parent, a: DB.acts[D.parent], depth: 0 });
  pick({ title: '活動の中', items: cs.map(x => ({ k: x.id, label: actTitle(x.a), sub: timeText(x.a), lead: kindIc(x.a), depth: x.depth })), cur: D.parent || null, none: { label: 'なし(日の直下)' },
    on: k => { D.parent = k; D.parentTouched = 1; if (!D.peopleTouched) pyApplyPeople(pyParentPeople(k)); pyRe(); } });
}
function pyPeople() {
  const D = PYD, all = [...new Set([...entsOfType('person'), ...D.people])].filter(e => E(e));
  pick({ title: '一緒の人', multi: true, items: all.map(e => ({ k: e, label: E(e).n, lead: avatar(e) })), cur: D.people, on: l => { D.peopleTouched = 1; pyApplyPeople(l); pyRe(); } });
}
function pyToReceipt() { openReceipt({ replace: 1, parent: PYD.parent, date: PYD.date }); }
/* ---- 保存 ---- */
function pySaveEd() {
  const D = PYD, total = pyTotal(), need = pyNeed(), mi = DB.methods[0];
  const items = D.items.map(i => ({ n: i.n, ...(i.lines ? { lines: i.lines } : { price: +i.price || 0 }), tax: i.tax, users: pyUsers(D, i), memo: i.memo, unk: i.unk, unread: i.unread }));
  const payers = D.paySet.length === 1 ? [{ who: D.paySet[0], amt: total, how: D.paySet[0] === 'me' ? D.method : (D.payHow[D.paySet[0]] || mi) }] : D.paySet.map(w => ({ who: w, amt: +D.payAmt[w] || 0, how: w === 'me' ? D.method : (D.payHow[w] || mi) }));
  const exp = { basis: need ? D.basis : 'items', cat: D.cat, itemCat: Object.fromEntries(Object.entries(D.itemCat).filter(([k]) => +k < items.length)), sp: D.sp || undefined, fx: D.fx || undefined, memo: D.memo.trim() };
  if (exp.basis === 'even') exp.n = D.n || D.people.length + 1; if (exp.basis === 'amount') exp.me = D.me;
  const r = paySave({ id: D.id || undefined, date: D.date, start: D.start, parent: D.parent, payee: D.payee, roles: [...D.roles, ...D.people.map(e => ({ r: '同席', e }))], items, amt: items.length ? undefined : total, payers,
    settled: D.settled, unread: Object.fromEntries(Object.entries(D.unread).filter(([, v]) => v)), note: D.note, exp });
  if (D.edit) return done(r, { close: 1 });
  if (!done(r, { close: 1, go: '#/a/' + (r.id || ''), quiet: 1 })) return;
  toast(r.label, { undo: r.undo });
  if (D.fromRead) setTimeout(() => toast('続けて読み取れます', { act: { label: '続けて読み取る', on: () => openReceipt() } }), 60);
}

/* =====================================================================
   レシートの読み取り(模擬。見本から選ぶ → 読み取り中 → 値の入った支払いの入力へ。読めなかった値は空のまま)
   ===================================================================== */
const PY_RC = [
  { k: 'super', t: 'スーパー', sub: '6品目・1品が読めない', read: () => ({ payee: { e: 'super' }, date: TODAY, start: '12:41', amt: 2190, unread: { amt: 1, payee: 1 },
    items: [{ n: '牛乳', price: 238, tax: 8 }, { n: '食パン', price: 158, tax: 8 }, { n: '卵', price: 268, tax: 8 }, { n: '鶏むね肉', price: 498, tax: 8 }, { n: '洗濯洗剤', price: 698 }, { n: '', price: 330, unk: 1 }] }) },
  { k: 'cafe', t: 'カフェ', sub: '2品目', read: () => ({ payee: { e: 'minato' }, date: TODAY, start: '09:15', amt: 740, unread: { amt: 1, payee: 1 }, items: [{ n: 'カフェラテ', price: 480 }, { n: '焼き菓子', price: 260 }] }) },
  { k: 'total', t: '合計だけ', sub: '店名と品目は読めない', read: () => ({ payee: null, date: dAdd(TODAY, -1), start: null, amt: 1480, unread: { amt: 1, payee: 1 }, items: [] }) },
];
let PYRC = null;
const pyPaper = r => `<div class="py-paper" aria-hidden="true"><b class="py-paper-h">${r.payee ? esc(E(r.payee.e).n) : '▒▒▒▒▒▒'}</b>
  ${r.items.length ? r.items.map(i => `<div class="py-paper-r"><span>${i.n ? esc(i.n) : '▒▒▒▒'}</span><span class="num">${i.price.toLocaleString('ja-JP')}</span></div>`).join('') : `<div class="py-paper-r"><span>▒▒▒▒▒▒</span><span>▒▒▒</span></div><div class="py-paper-r"><span>▒▒▒▒</span><span>▒▒▒</span></div>`}
  <div class="py-paper-r tot"><span>合計</span><span class="num">${r.amt.toLocaleString('ja-JP')}</span></div></div>`;
function openReceipt(o = {}) {
  PYRC = { o, timer: null };
  openSheet({ id: 'receipt', title: 'レシートを読み取る', tall: true, body: `<div class="py-rcs">${PY_RC.map((s, i) => `<button class="py-rc" onclick="pyRcRead(${i})">${pyPaper(s.read())}<span class="py-rc-l"><b>${esc(s.t)}</b><span>${esc(s.sub)}</span></span></button>`).join('')}</div>`,
    onClose: () => { if (PYRC) clearInterval(PYRC.timer); } });
}
function pyRcRead(i) {
  const s = PY_RC[i], r = s.read(); let p = 0;
  setSheetBody(`<div class="py-scanbox">${pyPaper(r)}<i class="py-scan"></i></div><div id="py-prog">${progress(0, '読み取り中')}</div>`); setSheetPrimary(null);
  PYRC.timer = setInterval(() => {
    p = Math.min(1, p + .09); const e = $('#py-prog'); if (e) e.innerHTML = progress(p, p < 1 ? '読み取り中' : '読み取りました'); if (p < 1) return;
    clearInterval(PYRC.timer); const o = PYRC.o; if (o.replace) closeAllSheets(); else closeSheet(true);
    openPayEditor(o.parent ? { read: r, parent: o.parent } : { read: r });
  }, 90);
}
