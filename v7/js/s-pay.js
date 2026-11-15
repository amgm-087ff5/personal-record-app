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
/** 未確認: unsure は true(全体)か ['amt','payee']。項目 f が未確認か */
const pyUn = (u, f) => u === true || (Array.isArray(u) && u.includes(f));
/** 下書きの中の人: 登録済み・名前だけの相手(stub)は id。まだ保存していない名前だけの相手は '~名前'。保存の入力では id か {name} */
const pyIsName = k => typeof k === 'string' && k[0] === '~';
const pyRef = k => pyIsName(k) ? { name: k.slice(1) } : k;
const pyKey = t => { const n = String(t || '').trim(), hit = Object.keys(DB.ents).find(id => id !== 'me' && DB.ents[id].n === n && !(DB.ents[id].types || []).includes('method')); return hit || '~' + n; };
const pyShort = k => pyIsName(k) ? k.slice(1) : whoShort(k);
const pyEnt = (k, o = {}) => pyIsName(k) ? ent(null, { link: false, name: k.slice(1), ...o }) : ent(k, { link: false, ...o });
const pyBalText = (amt, who) => `${who.length ? who.map(w => pyShort(w) + (pyIsName(w) || E(w) ? 'さん' : '')).join('・') : '相手'}に${amt > 0 ? '貸し' : '借り'} ${yen(Math.abs(amt))}`;
/** 'YYYY-MM-DD' と 'HH:MM' に分ける(TS か 'HH:MM' を受ける) */
const pyTsSplit = (s, d) => isTS(s) ? [dOf(s), tOf(s) || ''] : isHM(s) ? [d || '', s] : [d || '', ''];
/** 支払いを保存できる形に戻す(品目・払った人・支出をそのまま持つ。品目は id を持つ) */
function pyRebuild(id) {
  const a = DB.acts[id], x = DB.exps[id], pr = rolesOf(a, '支払先')[0], cp = v => v == null ? v : JSON.parse(JSON.stringify(v));
  return { id, start: a.start, parent: a.parent || null, payee: pr ? { e: pr.e } : null, roles: cp(a.roles.filter(r => r.r !== '支払先')),
    items: cp(a.pay.items), amt: a.pay.amt || 0, payers: cp(a.pay.payers), unsure: cp(a.unsure), via: a.via, note: a.note, exp: cp(x) };
}

/* =====================================================================
   詳細の中身(route 'a' が bar の下に置く。<section> の並び)
   ===================================================================== */
let PYEXP = new Set();
function payDetail(id) {
  const a = A(id); if (!a || !a.pay) return `<section>${empty({ icon: 'receipt', text: '支払いが見つかりません' })}</section>`;
  if (isPlan(a)) return pyHero(id, a) + pyPlanBody(id, a);
  return pyHero(id, a) + pyReadBox(id, a) + pyMine(id, a) + pyItems(id, a) + pyShopSug(id, a) + pyPaid(id, a) + pyWith(a);
}
/** 品目の名前が買い物リストの未完了 ToDo に一致する時、完了にする案内(編集シートと同じ判定) */
function pyShopSug(id, a) {
  const L = a.pay.items.length ? shopMatches(a.pay.items.map(i => ({ n: i.n }))) : [];
  if (!L.length) return '';
  return `<section><div class="py-shop"><span class="py-shop-i">${ic('bag')}</span><div class="py-shop-m"><b>買い物リストの ${L.length}件を完了にする</b><span class="mini">${L.map(([, t]) => esc(t.t)).join('・')}</span></div></div>${btn('完了にする', `pyShopDone(${q(id)})`, 'sec', { wide: 1, icon: 'check' })}</section>`;
}
const pyShopDone = id => { const a = DB.acts[id]; if (!a || !a.pay) return; const ks = shopMatches(a.pay.items.map(i => ({ n: i.n }))).map(([k]) => k); done(paySave({ ...pyRebuild(id), doneTasks: ks }), { msg: `買い物リストの${ks.length}件を完了にしました` }); };
/** 子の支払いの時、親の活動に関わった人(同席・参加)を「関わった」に出す */
function pyWith(a) {
  const pp = a.parent ? pyParentPeople(a.parent) : []; if (!pp.length) return '';
  return `<section>${sec('関わった', { n: pp.length })}${rows(pp.map(e => row({ lead: avatar(e), t: E(e).n, href: `#/e/${e}`, chev: true })))}</section>`;
}
function pyHero(id, a) {
  const par = a.parent && DB.acts[a.parent], pr = rolesOf(a, '支払先')[0], plan = isPlan(a), pun = pyUn(a.unsure, 'payee'), aun = pyUn(a.unsure, 'amt');
  const nm = pr ? esc(roleName(pr)) : `<span class="py-ph-t">${pun ? '読めませんでした' : '支払先なし'}</span>`;
  const mk = pun ? readMark(nm) : nm, head = pr && E(pr.e) ? `<a class="py-pl" href="#/e/${pr.e}">${mk}${ic('chev')}</a>` : mk;
  const m = plan ? '' : payMethod(a), total = payTotal(a), d = aDate(a);
  return `<section class="py-hero">
    ${par ? `<a class="py-in" href="#/a/${a.parent}">${kindIc(par)}<span>${esc(actTitle(par))}の中</span></a>` : ''}
    <div class="py-ph">${avatar(pr && pr.e)}<h1 class="py-payee" data-bar-watch>${head}</h1></div>
    ${pr && E(pr.e) && E(pr.e).stub && !plan ? `<button class="btn sec sm py-reg" onclick="pyReg(${q(id)})">${ic('plus')}<span>店として登録</span></button>` : ''}
    <div class="py-when"><a class="py-day" href="#/day/${d}">${fmtFull(d)}</a>${!isAllDay(a) ? `<time class="num">${aStart(a)}</time>` : ''}${m ? `<span class="tag">${esc(m)}</span>` : ''}</div>
    <div class="py-tot"><span>合計</span>${aun ? readMark(amt(total, { cls: 'big' })) : amt(total, { cls: 'big' })}</div></section>`;
}
function pyPlanBody(id, a) {
  const x = xOf(id), r = a.routine && DB.routines[a.routine];
  return `<section>${sec('支払い予定', { act: { label: '直す', on: `openPayEditor({id:${q(id)}})` } })}${rows([prop('金額', amt(payTotal(a))), x ? prop('カテゴリ', catTag(x.cat, { path: 1 })) : '', r ? prop('繰り返し', esc(repText(r))) : ''])}${btn('記録にする', `recordPlan(${q(id)})`, 'pri', { wide: 1, icon: 'check' })}</section>`;
}
/** 読み取ったまま(未確認): 主ボタン「確かめました」 */
function pyReadBox(id, a) {
  const n = unsureCount(a); if (!n) return '';
  return `<section><div class="card pad py-read"><div class="py-read-h"><span class="py-read-i">${ic('receipt')}</span><b>読み取ったまま</b>${tag(`未確認 ${n}`, 'read')}</div>${btn('確かめました', `pyConfirm(${q(id)})`, 'pri', { wide: 1, icon: 'check' })}</div></section>`;
}
const pyWhoName = w => w ? whoName(w) + (E(w) ? 'さん' : '') : '相手';
const pySetDate = sid => { const s = A(sid); return s ? fmtMD(aDate(s)) : ''; };
/** 相手ごとの貸し借り。相手が1人なら札と1つのボタン、複数なら相手ごとの行 */
function pyDebts(id, b) {
  const ds = b.debts; if (!ds.length) return '';
  const btnFor = d => btn(d.settle ? '未精算に戻す' : '精算した', `pySettle(${q(id)},${!d.settle},${d.who ? q(d.who) : 'null'})`, 'sec', { sm: 1 });
  const when = d => d.settle ? `<a class="py-sl" href="#/a/${d.settle}">${ic('check')}<span>${pySetDate(d.settle)}に精算</span></a>` : '';
  if (ds.length === 1) { const d = ds[0];
    return `<div class="py-bal">${d.settle ? when(d) : ''}${btnFor(d)}</div>`; }
  return `<div class="py-debts">${ds.map(d => `<div class="py-debt"><div class="py-debt-h">${avatar(d.who)}<span class="py-debt-n">${esc(pyWhoName(d.who))}</span>${tag(d.amt > 0 ? '貸し' : '借り', 'owe')}${amt(Math.abs(d.amt))}</div>
    <div class="py-bal">${d.settle ? when(d) : '<span class="mini">未精算</span>'}${btnFor(d)}</div></div>`).join('')}</div>`;
}
function pyMine(id, a) {
  const x = xOf(id), total = payTotal(a);
  if (!x) return `<section>${sec('自分の支出')}<div class="card pad py-mine"><div class="py-mine-h"><span class="tag">自分の負担なし</span></div>${btn('支出に入れる', `pyXAdd(${q(id)})`, 'sec', { icon: 'plus' })}</div></section>`;
  const mine = xAmt(id), b = balanceOf(id), others = pyOthers(a), split = total !== mine || b.amt !== 0, cs = xCats(id);
  const sp = xSp(id), fx = xFx(id);
  const basisX = x.basis === 'even' ? `<div class="py-ex"><span>人数</span><div class="py-step"><button onclick="pyN(${q(id)},-1)" aria-label="人数を減らす">−</button><b class="num">${x.n || 2}人</b><button onclick="pyN(${q(id)},1)" aria-label="人数を増やす">＋</button></div></div>`
    : x.basis === 'amount' ? `<div class="py-ex"><span>自分の負担</span>${input({ value: x.me || 0, mode: 'numeric', onchange: `pyMe(${q(id)},this.value)`, cls: 'num', label: '自分の負担' })}</div>${myPaid(a) > 0 && myPaid(a) !== (x.me || 0) ? `<div class="py-ex py-fill">${btn(`自分が払った ${yen(myPaid(a))} を入れる`, `pyMe(${q(id)},${myPaid(a)})`, 'sec', { sm: 1 })}</div>` : ''}` : '';
  return `<section>${sec('自分の支出')}
    ${split || b.amt ? `<div class="card pad py-mine"><div class="py-mine-h"><span class="py-k">自分の負担</span>${b.amt ? (b.open ? tag(balanceText(b), 'owe') : tag('精算済み')) : ''}</div>
      ${amt(mine, { cls: 'hero' })}
      ${split ? `<div class="py-sp">${splitBar(id)}<span class="mini">合計 ${yen(total)} のうち ${Math.round(mine / (total || 1) * 100)}%</span></div>` : ''}
      ${pyDebts(id, b)}</div>` : ''}
    ${rows([
      others ? `<div class="py-opt"><span class="prop-k">負担の決め方</span>${seg(PY_BASIS, x.basis || 'items', pyFn('pyBasis', id))}${basisX}</div>` : '',
      prop('カテゴリ', catTag(x.cat, { path: 1 }), { on: `pyCatPick(${q(id)})` }),
      prop('特別感', `${esc(sp || '未設定')}${x.sp ? tag('この支出だけ', 'me') : '<span class="mini">カテゴリの既定</span>'}`, { on: `pyOptPick(${q(id)},'sp')` }),
      prop('固定・変動', `${esc(fx || '未設定')}${x.fx ? tag('この支出だけ', 'me') : '<span class="mini">カテゴリの既定</span>'}`, { on: `pyOptPick(${q(id)},'fx')` }),
      prop('メモ', x.memo ? esc(x.memo) : '<span class="mini">なし</span>', { on: `pyMemoSheet(${q(id)})` })])}
    ${cs.length >= 2 ? `${sec('カテゴリの内訳')}${rows(cs.map(c => row({ tHtml: catTag(c.cat), sHtml: c.ids.length ? `<span class="mini">${c.ids.length}品目</span>` : '', right: amt(c.amt) })))}` : ''}</section>`;
}
/** 品目。同じ名前・金額・使った人が続く時は「×2」にまとめる(データはそのまま) */
function pyItems(id, a) {
  const its = a.pay.items, x = xOf(id);
  if (!its.length) return `<section>${sec('品目')}${rows(row({ t: '合計だけ', right: amt(payTotal(a)) }))}${btn('品目に分ける', `pySplit(${q(id)})`, 'dash', { icon: 'plus' })}</section>`;
  const sh = x ? xShares(id) : [], key = (it, i) => JSON.stringify([it.n || '', itemAmt(it), usersOf(it).slice().sort(), it.lines || null, itemTax(it), it.memo || '', (x && x.itemCat && x.itemCat[it.id]) || '', it.unk ? 1 : 0, it.unsure ? 1 : 0, sh[i]]);
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
  const it = a.pay.items[i], x = xOf(id), xi = x ? xItems(id).find(r => r.id === it.id) : null, v = itemAmt(it), unk = !!it.unk, u = usersOf(it);
  const nameH = it.n ? esc(it.n) : `<span class="py-ph-t">${unk ? '読めませんでした' : '名前なし'}</span>`;
  const sub = [], ic1 = x && x.itemCat && x.itemCat[it.id];
  (it.lines || []).slice(1).forEach(l => sub.push(`<span class="py-ln num">${pyLineTxt(l)}</span>`));
  if (unk && it.n) sub.push(tag('読めませんでした', 'read'));
  if (itemTax(it) === 8) sub.push(tag('税8%'));
  if (u.some(w => w !== 'me')) sub.push(`<span class="ents">${u.map(w => ent(w, { link: false, short: 1 })).join('')}</span>`);
  if (xi && xi.how === '奢り') sub.push(tag('奢り'));
  if (xi && xi.price !== xi.full) sub.push(tag(`自分 ${yen(xi.price * n)}${xi.how === '1/2' || xi.how === '按分' ? ' · ' + xi.how : ''}`, 'me'));
  if (ic1 && ic1 !== x.cat) sub.push(catTag(ic1));
  if (it.memo) sub.push(`<span class="mini">${esc(it.memo)}</span>`);
  const price = unk && !v ? `<span class="py-ph-t">金額</span>` : it.unsure ? readMark(amt(v * n)) : amt(v * n);
  return row({ tHtml: `${nameH}${n > 1 ? ` <span class="py-x num">×${n}</span>` : ''}`, sHtml: sub.join(''), right: price, on: n > 1 ? `pyGroup(${q(id)},${i})` : `pyItemOpen(${q(id)},${q(it.id)})`, cls: `py-it${unk ? ' unk' : ''}${o.sub ? ' sub' : ''}` });
}
function pyPaid(id, a) {
  const ps = paidOf(a), me1 = ps.length === 1 && ps[0].who === 'me';
  const body = me1 ? row({ lead: ic('wallet'), t: methodName(ps[0].how) || '—', s: '自分が払った' })
    : ps.map(p => row({ lead: avatar(p.who), tHtml: esc(whoName(p.who)), s: methodName(p.how), right: amt(p.amt) })).join('');
  return `<section>${sec('払った人', { act: { label: '直す', on: `openPayEditor({id:${q(id)}})` } })}${rows(body)}</section>`;
}
/* ---- 詳細の操作(全部 model の関数。done が取り消しと描き直しを受け持つ) ---- */
function pyReg(id) {
  const a = DB.acts[id], pr = a && rolesOf(a, '支払先')[0], e = pr && E(pr.e); if (!e || !e.stub) return;
  done(entUpdate(pr.e, { stub: null, types: e.types.length ? e.types : ['shop'] }), { msg: `「${e.n}」を店として登録しました` });
}
const pyConfirm = id => done(confirmRows([{ t: 'acts', k: id }]));
/** 相手ごとに精算した / 未精算に戻す。精算したら、その精算(出来事)へ行ける */
function pySettle(id, on, who) {
  const before = new Set(settlesOf(id).map(r => r[0])), r = paySettle(id, on, who === undefined ? {} : { who });
  if (!r.ok) return done(r);
  const s = on ? settlesOf(id).find(([k, x]) => !before.has(k) && (x.settle.who || null) === (who || null)) : null;
  done(r, s ? { act: { label: '精算を見る', on: () => go('#/a/' + s[0]) } } : {});
}
function pyOptPick(id, f) {
  const x = xOf(id), def = f === 'sp' ? catSp(x.cat) : catFx(x.cat), opts = f === 'sp' ? ['日常', '非日常'] : ['固定', '変動'];
  pick({ title: f === 'sp' ? '特別感' : '固定・変動', items: opts.map(k => ({ k, label: k })), cur: x[f] || null, none: { label: def ? `カテゴリの既定(${def})` : 'カテゴリの既定' }, on: k => done(expUpdate(id, { [f]: k || null })) });
}
function pyMemoSheet(id) {
  const cur = (xOf(id) || {}).memo || '';
  openSheet({ title: '支出のメモ', body: textarea({ id: 'py-memo-t', rows: 4, value: cur, auto: 1, label: 'メモ' }), dirty: () => val('py-memo-t') !== cur, primary: { label: 'メモを保存', on: () => done(expUpdate(id, { memo: val('py-memo-t').trim() || null }), { close: 1 }) } });
}
function pyXAdd(id) { const a = A(id), r = rolesOf(a, '支払先')[0]; done(expUpdate(id, { basis: 'items', cat: catGuess(r ? { e: r.e } : null) })); }
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
function pyItemOpen(id, iid) {
  const a = A(id), x = xOf(id), it = iid == null ? null : a.pay.items.find(i => i.id === iid);
  const ls = it ? (it.lines && it.lines.length ? it.lines.map(l => ({ ...l })) : [{ n: '', k: 'amt', v: it.price || 0 }]) : [{ n: '', k: 'amt', v: a.pay.items.length ? 0 : payTotal(a) }];
  PYI = { id, iid: it ? it.id : null, n: it ? it.n : '', lines: ls.map((l, j) => j ? { n: l.n, k: l.k, v: Math.abs(l.v), dis: l.v < 0 } : { n: '', k: 'amt', v: l.v }), tax: it ? itemTax(it) : TAX_DEFAULT, users: it ? usersOf(it).slice() : ['me'],
    cat: it && x && x.itemCat && x.itemCat[it.id] || null, memo: it && it.memo || '', unk: it && it.unk ? 1 : 0, unsure: it && it.unsure ? 1 : 0 };
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
    <button class="py-unit" onclick="pyiUnit(${j})" aria-label="円と%を切り替える">${l.k === 'pct' ? '%' : '円'}</button>${iconBtn('close', 'この行を消す', `pyiLnDel(${j})`)}</div>`;
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
  const item = { id: I.iid || uid('i'), n: I.n.trim(), tax: I.tax, users: I.users.slice(), memo: I.memo.trim(), ...(ls.length > 1 && ls.slice(1).some(l => l.v) ? { lines: ls } : { price: ls[0].v }), ...(I.unsure ? { unsure: 1 } : {}) };
  const ix = I.iid == null ? -1 : o.items.findIndex(i => i.id === I.iid);
  if (ix < 0) o.items.push(item); else o.items[ix] = item;
  const nt = o.items.reduce((s, i) => s + itemAmt(i), 0);
  o.exp = o.exp || { basis: 'items', cat: catGuess(o.payee) };
  const ic2 = { ...(o.exp.itemCat || {}) }; if (I.cat) ic2[item.id] = I.cat; else delete ic2[item.id]; o.exp.itemCat = ic2;
  if (o.payers.length > 1 && nt !== oldTot) { let d = nt - oldTot; if (d > 0) o.payers[0].amt += d; else for (let i = o.payers.length - 1; i >= 0 && d < 0; i--) { const t = Math.min(-d, o.payers[i].amt); o.payers[i].amt -= t; d += t; } }
  done(paySave(o), { close: 1, msg: I.iid == null ? '品目を足しました' : '品目を直しました' });
}
function pyiDel() { done(itemRemove(PYI.id, PYI.iid), { close: 1 }); }

/* =====================================================================
   支払いを記録・直す(1画面)
   ===================================================================== */
let PYD = null, PYD0 = '';
const pyAsk = (cat, f) => (f === 'fx' ? catFx(cat) : catSp(cat)) == null;
/** カテゴリが「都度決める」(ask)になっているか(祖先をたどる) */
const pyRawAsk = (cat, f) => { for (const c of catAnc(cat).slice().reverse()) if (DB.cats[c][f] !== undefined) return DB.cats[c][f] === 'ask'; return false; };
const pyPrtDiff = (D = PYD) => D.printed != null && !D.printedOff && D.items.length && pyTotal(D) !== D.printed ? pyTotal(D) - D.printed : 0;
/** 保存を止める理由(無ければ空) */
function pyBlock(D = PYD) {
  for (const [f, l] of [['fx', '固定・変動'], ['sp', '特別感']]) if (pyRawAsk(D.cat, f) && !D[f]) return `${l}を選んでください`;
  if (pyPrtDiff(D)) return '印字の合計と違います。品目を直すか「印字の合計を使わない」を押してください';
  return '';
}
const pyDupKey = (D = PYD) => [D.date, D.start, pyTotal(D), D.photo || ''].join('|');
/** 重複の疑い。同じ写真(ハッシュ)も見る。model の payDup が photo を見ない間も動くよう、写真の一致はここでも拾う */
const pyDupFind = (o) => { const r = payDup(o); if (!o.photo) return r; const ids = new Set(r.map(x => x[0])); return [...Object.entries(DB.acts).filter(([id, a]) => a.photo === o.photo && !ids.has(id) && id !== o.id), ...r]; };
const pyDupNow = (D = PYD) => D.edit || D.dupKey !== pyDupKey(D) ? [] : pyDupFind({ id: D.id, date: D.date, start: D.start, total: pyTotal(D), photo: D.photo });
const pyShopIds = (D = PYD) => D.shopOff ? [] : shopMatches(D.items.map(i => ({ n: i.n }))).map(([k]) => k);
function pyCands(d) {
  const out = [], walk = (list, depth) => list.forEach(([id, a]) => { if (isPay(a)) return; if (isSpan(a) && !isPlan(a)) out.push({ id, a, depth }); walk(kidsOf(id), depth + 1); });
  walk(actsOn(d, { state: 'done' }), 0); return out;
}
function pyDefParent(d, t) {
  const ts = t ? tsOf(d, t) : '';
  const c = pyCands(d).filter(x => ts && x.a.start <= ts && ts < x.a.end).sort((p, r) => r.depth - p.depth || r.a.start.localeCompare(p.a.start))[0];
  return c ? c.id : null;
}
function pyParentPeople(pid) { return pid && DB.acts[pid] ? [...new Set([[pid, DB.acts[pid]], ...ancOf(pid)].flatMap(([, x]) => rolesOf(x, '同席', '参加').map(r => r.e).filter(e => e && E(e))))] : []; }
/** 支払先の id(名前だけの入力なら、同じ名前の ent) */
const pyPayeeId = payee => !payee ? null : payee.e || Object.keys(DB.ents).find(k => DB.ents[k].n === payee.name) || null;
function pyPrevMethod(payee) {
  const pid = pyPayeeId(payee); if (!pid) return null;
  const p = Object.values(DB.acts).filter(a => a.pay && !isPlan(a) && rolesOf(a, '支払先').some(r => r.e === pid)).sort((x, y) => y.start.localeCompare(x.start))[0];
  const me = p && paidOf(p).find(z => z.who === 'me'); return me && me.how || null;
}
function pyDraft(o) {
  const seed = window._capSeed && window._capSeed.type === 'pay' ? window._capSeed : null, cp = v => v == null ? v : JSON.parse(JSON.stringify(v));
  let D;
  if (o.draft) { D = cp(o.draft); D.batch = o.batch || 0; }
  else if (o.id) {
    const a = DB.acts[o.id]; if (!a || !a.pay) return null;
    const pr = rolesOf(a, '支払先')[0], x = DB.exps[o.id], items = cp(a.pay.items).map(i => ({ ...i, users: usersOf(i).slice() }));
    const ppl = new Set(rolesOf(a, '同席', '参加').map(r => r.e).filter(Boolean)); items.forEach(i => i.users.forEach(u => u !== 'me' && ppl.add(u))); a.pay.payers.forEach(p => p.who && p.who !== 'me' && ppl.add(p.who));
    const payers = a.pay.payers.length ? cp(a.pay.payers) : [{ who: 'me', amt: payTotal(a), how: methodDefault() }], mp = payers.find(p => p.who === 'me');
    D = { id: o.id, edit: 1, date: aDate(a), start: aStart(a) || NOW, parent: a.parent || null, parentTouched: 1, payee: pr ? { e: pr.e } : null, amt: items.length ? 0 : (a.pay.amt || 0), items,
      people: [...ppl], peopleTouched: 1, paySet: payers.map(p => p.who), payAmt: Object.fromEntries(payers.map(p => [p.who, p.amt])), payHow: Object.fromEntries(payers.map(p => [p.who, p.how])), method: mp ? mp.how : methodDefault(), methodTouched: 1,
      cat: x ? x.cat : catGuess(pr ? { e: pr.e } : null), catTouched: 1, basis: x ? x.basis : 'items', n: x && x.n || 0, me: x && x.me || 0, fx: x && x.fx || null, sp: x && x.sp || null, memo: x && x.memo || '', itemCat: x && x.itemCat ? cp(x.itemCat) : {},
      unsure: cp(a.unsure) || null, via: a.via, roles: a.roles.filter(r => r.r !== '支払先' && r.r !== '同席' && r.r !== '参加'), note: a.note };
  } else {
    const rd = o.read, sd = pyTsSplit(o.start, o.date), rs = rd && rd.start ? pyTsSplit(rd.start, rd.date) : ['', ''], ss = seed && seed.start ? pyTsSplit(seed.start) : ['', ''];
    const date = o.date || sd[0] || (rd && (rd.date || rs[0])) || ss[0] || TODAY, start = sd[1] || rs[1] || ss[1] || NOW;
    const payee = rd ? rd.payee : seed && seed.payee ? seed.payee : null, parent = o.parent !== undefined ? (o.parent && DB.acts[o.parent] ? o.parent : null) : pyDefParent(date, start);
    const ppl = [...new Set([...(seed && seed.ents || []).filter(e => E(e) && E(e).types[0] && isUnder(E(e).types[0], 'person')), ...pyParentPeople(parent)])];
    const items = rd ? rd.items.map(i => ({ id: uid('i'), n: i.n || '', price: i.price || 0, tax: i.tax || TAX_DEFAULT, users: ['me'], ...(i.unk ? { unk: 1 } : {}), unsure: 1 })) : [];
    D = { id: null, edit: 0, fromRead: rd ? 1 : 0, photo: rd && rd.photo || null, date, start, parent, parentTouched: o.parent !== undefined, payee, amt: rd && !rd.items.length ? rd.amt : (!rd && (o.amount || o.amt || seed && seed.amt)) || 0, items,
      people: ppl, peopleTouched: false, paySet: ['me'], payAmt: {}, payHow: {}, method: pyPrevMethod(payee) || methodDefault(), methodTouched: false,
      printed: rd && rd.items.length && rd.amt ? rd.amt : null, cat: catGuess(payee, seed && seed.text || ''), catTouched: false, basis: 'items', n: 0, me: 0, fx: null, sp: null, memo: '', itemCat: {}, unsure: rd && rd.unsure ? rd.unsure.slice() : null, via: rd ? 'ocr' : undefined, roles: [] };
  }
  if (!o.pure) PYD = D; return D;
}
function pyTotal(D = PYD) { return D.items.length ? D.items.reduce((s, i) => s + itemAmt(i), 0) : (+D.amt || 0); }
const pyUsers = (D, i) => i.users.filter(u => u === 'me' || D.people.includes(u));
const pyNeed = (D = PYD) => D.items.some(i => pyUsers(D, i).some(u => u !== 'me')) || D.paySet.some(w => w !== 'me');
/** 確かめた項目を、未確認から外す(全体が未確認の時はそのまま) */
function pyClr(f) { const u = PYD.unsure; if (Array.isArray(u)) { const n = u.filter(k => k !== f); PYD.unsure = n.length ? n : null; } }
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
  const pa = o.id && A(o.id); if (o.plan || (pa && isPlan(pa))) return openPayPlanEditor(o);
  if (!pyDraft(o)) return toast('支払いが見つかりません', { bad: 1 });
  PYD0 = JSON.stringify(PYD);
  openSheet({ id: 'pay-ed', title: o.id ? '支払いを直す' : '支払いを記録', tall: true, lead: o.id || o.batch ? '' : `<button class="btn sec sm" onclick="pyToReceipt()">${ic('camera')}<span>レシートを読み取る</span></button>`,
    body: pyEdBody(), dirty: () => PYD && (PYD.fromRead || JSON.stringify(PYD) !== PYD0), onClose: o.batch ? pyBtBack : undefined, primary: { label: pyPrimaryLabel(), on: pySaveEd, disabled: pyBad() } });
}
const pyPrimaryLabel = () => PYD.edit ? '保存' : pyDupNow().length ? '重複ではないので保存' : PYD.batch ? 'この内容で確かめた' : '支払いを保存';
const pyPrimary = () => { PYD._lbl = pyPrimaryLabel(); setSheetPrimary({ label: PYD._lbl, on: pySaveEd, disabled: pyBad() }); };
function pyRe() { setSheetBody(pyEdBody()); pyPrimary(); }
function pyEdBody() {
  const D = PYD, total = pyTotal(), hasIt = D.items.length > 0, need = pyNeed(), pp = D.people, c = pyCalc(), pun = pyUn(D.unsure, 'payee'), aun = pyUn(D.unsure, 'amt');
  const payeeV = D.payee ? ent(D.payee.e || null, { link: false, name: D.payee.name }) : (pun ? '読めませんでした' : '支払先を選ぶ');
  const par = D.parent && DB.acts[D.parent];
  const amtB = hasIt ? `<div class="py-amt-f"><span id="py-tot">${pyTotHtml()}</span><span class="mini">品目の合計</span></div>`
    : `<div class="py-amt-f"><i>¥</i>${input({ id: 'py-amt', value: D.amt || '', ph: '0', mode: 'numeric', oninput: 'pyAmt(this.value)', cls: `big num${aun ? ' py-unread' : ''}`, auto: !D.edit && !D.fromRead && !D.amt, label: '金額' })}</div>`;
  const itemRow = (it, i) => `<div class="py-eit${it.unk ? ' py-unk' : ''}"><div class="py-eit-r">${input({ value: it.n, ph: it.unk ? '読めませんでした' : '品名', oninput: `pyItN(${i},this.value)`, cls: it.unsure || it.unk ? 'py-unread' : '', label: '品名' })}
      ${it.lines ? `<span class="py-eit-a">${amt(itemAmt(it))}<small>金額行あり</small></span>` : input({ value: it.price || '', ph: '金額', mode: 'numeric', oninput: `pyItP(${i},this.value)`, cls: `num py-eit-p${it.unsure ? ' py-unread' : ''}`, label: '金額' })}${iconBtn('trash', 'この品目を消す', `pyItDel(${i})`)}</div>
      ${pp.length ? chips([['me', '自分'], ...pp.map(w => [w, pyShort(w)])], pyUsers(D, it), pyFn('pyItU', i)).replace('class="chips"', 'class="chips wrap"') : ''}</div>`;
  const dups = pyDupNow(), fxL = [['fx', '固定・変動', ['固定', '変動'], 'pyFxEd', catFx(D.cat)], ['sp', '特別感', ['日常', '非日常'], 'pySpEd', catSp(D.cat)]];
  const askF = f => `<div class="field${D.need && !D[f[0]] ? ' py-need' : ''}"><span class="field-l">${f[1]}</span>${seg(f[2].map(k => [k, k]), D[f[0]], f[3])}</div>`;
  const defF = f => `<div class="field"><span class="field-l">${f[1]}</span>${seg([['_', 'カテゴリの既定'], ...f[2].map(k => [k, k])], D[f[0]] || '_', f[3])}</div>`;
  const payers = `<div class="field"><span class="field-l">払った人</span>${chips([['me', '自分'], ...pyCandPeople(D).map(w => [w, pyShort(w)])], D.paySet, 'pyPayer').replace('class="chips"', 'class="chips wrap"')}
      ${D.paySet.length > 1 ? D.paySet.map(w => `<div class="py-pr"><span>${pyEnt(w, { short: 1 })}</span>${input({ id: 'py-pa-' + w, value: D.payAmt[w] || '', mode: 'numeric', ph: '0', oninput: `pyPayAmt(${q(w)},this.value)`, cls: 'num', label: pyShort(w) + 'が払った額' })}</div>`).join('') + `<div id="py-psum">${pyPsum()}</div>` : ''}</div>`;
  const regShop = pyRegShow(D) ? `<div class="py-regshop"><span>「${esc(pyRegName(D))}」を店として登録</span>${toggle(!!D.regShop, 'pyRegTog()', '店として登録')}</div>` : '';
  const split = need ? `<div class="field"><span class="field-l">自分の負担</span><div class="card pad py-split">${seg(PY_BASIS, D.basis, 'pyBasisEd')}
      ${D.basis === 'even' ? `<div class="py-ex"><span>人数</span><div class="py-step"><button onclick="pyNEd(-1)" aria-label="人数を減らす">−</button><b class="num">${D.n || pp.length + 1}人</b><button onclick="pyNEd(1)" aria-label="人数を増やす">＋</button></div></div>` : ''}
      ${D.basis === 'amount' ? `<div class="py-ex"><span>自分の負担</span>${input({ value: D.me || '', ph: '0', mode: 'numeric', oninput: 'pyMeEd(this.value)', cls: 'num', label: '自分の負担' })}</div>${pyMyPaid() > 0 && pyMyPaid() !== (D.me || 0) ? `<div class="py-ex py-fill">${btn(`自分が払った ${yen(pyMyPaid())} を入れる`, 'pyMeFill()', 'sec', { sm: 1 })}</div>` : ''}` : ''}
      <div id="py-res">${pyRes(c)}</div></div></div>` : '';
  return `${dups.length ? `<div class="py-dup">${banner('同じ日・同じ金額の支払いがあります', 'warn')}<div class="stack">${dups.map(([k]) => paySlip(k)).join('')}</div></div>` : ''}
    <div class="field"><span class="field-l">金額</span>${amtB}<div id="py-prt">${pyPrt()}</div></div>
    ${pickField('支払先', pun && D.payee ? readMark(payeeV) : payeeV, 'pyPayee()', { emptyv: !D.payee })}${regShop}
    <div class="grid2">${field('日付', input({ id: 'py-d', type: 'date', value: D.date, onchange: 'pyWhen()', label: '日付' }))}${field('時刻', input({ id: 'py-t', type: 'time', value: D.start, onchange: 'pyWhen()', label: '時刻' }))}</div>
    ${D.paySet.includes('me') ? `<div class="field"><span class="field-l">支払い方法</span>${chips(methodsAll().map(([k, e]) => [k, e.n]), D.method, 'pyMethod').replace('class="chips"', 'class="chips wrap"')}</div>` : ''}
    ${pickField('カテゴリ', catTag(D.cat, { path: 1 }), 'pyCatEd()')}
    ${pickField('活動の中', par ? `${kindIc(par)}<span>${esc(actTitle(par))}</span><span class="mini num">${timeText(par)}</span>` : 'なし(日の直下)', 'pyParent()', { emptyv: !par })}
    <div class="field"><span class="field-l">品目</span>${D.items.map(itemRow).join('')}${btn('品目を足す', 'pyItAdd()', 'dash', { icon: 'plus' })}<div id="py-shop">${pyShopHtml()}</div></div>
    ${pickField('一緒の人', pp.length ? `<span class="ents">${pp.map(e => pyEnt(e)).join('')}</span>` : '足す', 'pyPeople()', { emptyv: !pp.length })}
    ${payers}${split}
    ${fxL.filter(f => pyRawAsk(D.cat, f[0])).map(askF).join('')}
    <details class="py-more"${D.more ? ' open' : ''} ontoggle="PYD.more=this.open"><summary>くわしく</summary>${fxL.filter(f => !pyRawAsk(D.cat, f[0])).map(defF).join('')}</details>
    ${field('メモ', textarea({ rows: 2, ph: 'メモ', oninput: 'pyMemoEd(this.value)', value: D.memo, label: 'メモ' }))}`;
}
const pyMyPaid = (D = PYD) => D.paySet.includes('me') ? (D.paySet.length === 1 ? pyTotal(D) : (+D.payAmt.me || 0)) : 0;
const pyMeFill = () => { PYD.me = pyMyPaid(); pyRe(); };
function pyPrt() {
  const d = pyPrtDiff(); if (!d) return '';
  return `<div class="py-prt"><span class="num">印字 ${yen(PYD.printed)} · 差 ${yen(Math.abs(d))}</span>${btn('印字の合計を使わない', 'pyPrtOff()', 'sec', { sm: 1 })}</div>`;
}
const pyPrtOff = () => { PYD.printedOff = 1; pyRe(); };
function pyShopHtml() {
  const all = shopMatches(PYD.items.map(i => ({ n: i.n })));
  if (!all.length) return '';
  return `<div class="py-shop"><span class="py-shop-i">${ic('bag')}</span><div class="py-shop-m"><b>買い物リストの ${all.length}件を完了にする</b><span class="mini">${all.map(([, t]) => esc(t.t)).join('・')}</span></div>${toggle(!PYD.shopOff, 'pyShopTog()', '買い物リストを完了にする')}</div>`;
}
const pyShopTog = () => { PYD.shopOff = PYD.shopOff ? 0 : 1; pyShopRe(); };
const pyShopRe = () => { const e = $('#py-shop'); if (e) e.innerHTML = pyShopHtml(); };
const pyTotHtml = () => pyUn(PYD.unsure, 'amt') ? readMark(amt(pyTotal(), { cls: 'big' })) : amt(pyTotal(), { cls: 'big' });
const pyRes = c => `<div class="py-res"><span class="py-k">自分の負担</span>${amt(c.mine, { cls: 'big' })}${c.bal ? tag(pyBalText(c.bal, c.who), 'owe') : ''}</div>`;
function pyPsum() { const t = pyTotal(), s = pyPaySum(), d = t - s; return `<div class="py-psum num"><span>払った額の合計 ${yen(s)} / ${yen(t)}</span>${d ? tag(d > 0 ? `あと ${yen(d)}` : `超過 ${yen(-d)}`) : ''}</div>`; }
/** 入力のたびに、合計・自分の負担・払った額の合計だけを書き換える(フォーカスを失わない) */
function pyRefresh() {
  const t = $('#py-tot'); if (t) t.innerHTML = pyTotHtml(); const r = $('#py-res'); if (r) r.innerHTML = pyRes(pyCalc()); const p = $('#py-psum'); if (p) p.innerHTML = pyPsum();
  const pt = $('#py-prt'); if (pt) pt.innerHTML = pyPrt();
  const bad = pyBad(); if (PYD._bad !== bad || PYD._lbl !== pyPrimaryLabel()) { PYD._bad = bad; pyPrimary(); }
}
/* ---- 入力 ---- */
function pyAmt(v) { PYD.amt = pyNum(v); pyClr('amt'); if (PYD.paySet.length > 1) pyPayDist(); pyRefresh(); }
function pyItN(i, v) { const it = PYD.items[i]; it.n = v; if (v.trim()) delete it.unk; delete it.unsure; pyShopRe(); }
function pyItP(i, v) { const it = PYD.items[i]; it.price = pyNum(v); delete it.unsure; pyRefresh(); }
function pyItU(i, k) { const u = PYD.items[i].users, j = u.indexOf(k); if (j >= 0) { if (u.length > 1) u.splice(j, 1); } else u.push(k); pyRe(); }
/** 新しい品目は下書きの中で仮の id を持つ(保存でそのまま品目の id になる)。品目別カテゴリは id で持つので、足す・消す・並びで狂わない */
function pyItAdd() { const D = PYD; D.items.push({ id: uid('i'), n: '', price: D.items.length ? 0 : D.amt, tax: TAX_DEFAULT, users: ['me'] }); if (D.items.length === 1) D.amt = 0; pyRe(); }
function pyItDel(i) {
  const D = PYD, it = D.items[i]; D.items.splice(i, 1); delete D.itemCat[it.id];
  if (!D.items.length) D.amt = itemAmt(it); if (D.paySet.length > 1) pyPayDist(); pyRe();
}
const pyMethod = m => { PYD.method = m; PYD.methodTouched = 1; pyRe(); };
const pyBasisEd = k => { PYD.basis = k; if (k === 'even' && !PYD.n) PYD.n = PYD.people.length + 1; pyRe(); };
const pyNEd = d => { PYD.n = Math.max(2, Math.min(99, (PYD.n || PYD.people.length + 1) + d)); pyRe(); };
const pyMeEd = v => { PYD.me = pyNum(v); pyRefresh(); };
const pyFxEd = k => { PYD.fx = k === '_' ? null : k; pyRe(); };
const pySpEd = k => { PYD.sp = k === '_' ? null : k; pyRe(); };
const pyMemoEd = v => { PYD.memo = v; };
/** 払った人の候補 = 全員(登録済みの人すべて + 一緒の人の名前だけの相手。自分は別に先頭) */
const pyCandPeople = (D = PYD) => [...new Set([...entsOfType('person'), ...D.people])].filter(e => e !== 'me' && (pyIsName(e) || E(e)));
function pyPayer(k) {
  const D = PYD, s = D.paySet, i = s.indexOf(k); if (i >= 0) { if (s.length > 1) s.splice(i, 1); } else s.push(k);
  D.paySet = ['me', ...pyCandPeople(D)].filter(w => D.paySet.includes(w)); if (D.paySet.length > 1) pyPayDist(); pyRe();
}
/** 店として登録: 作る時、支払先が名前だけの相手(stub)か、新しい名前の時に切替を出す。保存で登録済みの店になる */
const pyRegName = (D = PYD) => !D.payee ? '' : D.payee.name || (E(D.payee.e) && E(D.payee.e).stub ? E(D.payee.e).n : '');
const pyRegShow = (D = PYD) => !D.edit && !!pyRegName(D) && (!D.payee.e || (E(D.payee.e) && E(D.payee.e).stub));
const pyRegTog = () => { PYD.regShop = !PYD.regShop; pyRe(); };
function pyPayAmt(w, v) { PYD.payAmt[w] = pyNum(v); pyRefresh(); }
/** 日付・時刻を変えたら、利用者が選んでいなければ活動の中を決め直す */
function pyWhen() {
  const D = PYD; D.date = val('py-d') || D.date; D.start = val('py-t') || D.start;
  if (D.parent && !pyCands(D.date).some(x => x.id === D.parent)) { D.parent = null; D.parentTouched = false; }
  if (!D.parentTouched) { D.parent = pyDefParent(D.date, D.start); if (!D.peopleTouched) pyApplyPeople(pyParentPeople(D.parent)); }
  pyRe();
}
function pyApplyPeople(list) {
  const D = PYD; D.people = list.slice(); const ok = w => w === 'me' || D.people.includes(w) || pyCandPeople(D).includes(w);
  D.paySet = D.paySet.filter(ok); if (!D.paySet.length) D.paySet = ['me']; if (D.paySet.length > 1) pyPayDist();
}
/* ---- 選ぶシート ---- */
/** 支払先を選ぶ(支払いと支払い予定で共通)。cur = {e}|{name}|null、on({e}|{name}) */
function pyPayeePick(cur, on) {
  const rec = Object.values(DB.acts).filter(a => a.pay && !isPlan(a)).sort((x, y) => y.start.localeCompare(x.start)), ord = new Map();
  rec.forEach(a => { const r = rolesOf(a, '支払先')[0]; if (r && r.e && !ord.has(r.e)) ord.set(r.e, ord.size); });
  const ents = [...new Set([...entsOfType('place', true, { stubs: true }), ...entsOfType('company', true, { stubs: true })])].filter(id => !E(id).stub || ord.has(id)).sort((x, y) => (ord.has(x) ? ord.get(x) : 999) - (ord.has(y) ? ord.get(y) : 999));
  const items = ents.map(id => ({ k: id, label: E(id).n, sub: E(id).stub ? '最近の支払先' : (DB.types[E(id).types[0]] || {}).n || '', lead: avatar(id) }));
  pick({ title: '支払先', items, cur: cur ? cur.e || (cur.name && Object.keys(DB.ents).find(k => DB.ents[k].n === cur.name)) || null : null, search: '店・会社をさがす・新しく入れる', create: { label: 'をそのまま使う', on: t => on({ name: t }) }, on: k => on({ e: k }) });
}
function pyPayee() {
  const D = PYD;
  pyPayeePick(D.payee, p => {
    D.payee = p; pyClr('payee');
    if (!D.catTouched) { const k = catGuess(p); if (k !== D.cat) { D.cat = k; D.fx = D.sp = null; } }
    if (!D.methodTouched) D.method = pyPrevMethod(p) || D.method; pyRe();
  });
}
function pyCatEd() {
  pick({ title: 'カテゴリ', items: pyCatItems(), cur: PYD.cat, search: true, on: k => { if (k !== PYD.cat) { PYD.cat = k; PYD.fx = PYD.sp = null; } PYD.catTouched = 1; pyRe(); } });
}
function pyParent() {
  const D = PYD, cs = pyCands(D.date);
  if (D.parent && DB.acts[D.parent] && !cs.some(x => x.id === D.parent)) cs.unshift({ id: D.parent, a: DB.acts[D.parent], depth: 0 });
  pick({ title: '活動の中', items: cs.map(x => ({ k: x.id, label: actTitle(x.a), sub: timeText(x.a), lead: kindIc(x.a), depth: x.depth })), cur: D.parent || null, none: { label: 'なし(日の直下)' },
    on: k => { D.parent = k; D.parentTouched = 1; if (!D.peopleTouched) pyApplyPeople(pyParentPeople(k)); pyRe(); } });
}
/** 一緒の人。名前だけの相手も足せる(下書きでは '~名前'、保存すると stub の id) */
function pyPeople() {
  const D = PYD, all = [...new Set([...entsOfType('person'), ...D.people])].filter(e => pyIsName(e) || E(e));
  pick({ title: '一緒の人', multi: true, items: all.map(e => pyIsName(e) ? { k: e, label: e.slice(1), lead: avatar(null, e.slice(1)) } : { k: e, label: E(e).n, lead: avatar(e) }), cur: D.people,
    create: { label: 'を名前だけで足す', on: t => { const k = pyKey(t); if (k === '~') return; D.peopleTouched = 1; pyApplyPeople([...D.people.filter(w => w !== k), k]); pyRe(); } },
    on: l => { D.peopleTouched = 1; pyApplyPeople(l); pyRe(); } });
}
function pyToReceipt() { openReceipt({ replace: 1, parent: PYD.parent, date: PYD.date }); }
/* ---- 保存 ---- */
/** 下書き → paySave の入力。名前だけの人は {name}(users の要素・payers・roles・payee)。品目は id を持つ */
function pyBuild(D) {
  const total = pyTotal(D), need = pyNeed(D), mi = methodDefault();
  const items = D.items.map(i => ({ id: i.id, n: i.n, ...(i.lines ? { lines: i.lines } : { price: +i.price || 0 }), tax: i.tax, users: pyUsers(D, i).map(pyRef), memo: i.memo, unk: i.unk, unsure: i.unsure }));
  const how = w => w === 'me' ? D.method : (D.payHow[w] || mi), pw = (w, amt) => ({ ...(pyIsName(w) ? { name: w.slice(1) } : { who: w }), amt, how: how(w) });
  const payers = D.paySet.length === 1 ? [pw(D.paySet[0], total)] : D.paySet.map(w => pw(w, +D.payAmt[w] || 0));
  const exp = { basis: need ? D.basis : 'items', cat: D.cat, itemCat: Object.fromEntries(Object.entries(D.itemCat).filter(([k]) => items.some(i => i.id === k))), sp: D.sp || undefined, fx: D.fx || undefined, memo: D.memo.trim() };
  if (exp.basis === 'even') exp.n = D.n || D.people.length + 1; if (exp.basis === 'amount') exp.me = D.me;
  return { id: D.id || undefined, start: tsOf(D.date, D.start), parent: D.parent, payee: D.payee, roles: [...D.roles, ...D.people.map(e => ({ r: '同席', ...(pyIsName(e) ? { name: e.slice(1) } : { e }) }))], items, amt: items.length ? undefined : total, payers,
    unsure: D.unsure || undefined, via: D.via, note: D.note, exp, doneTasks: pyShopIds(D) };
}
function pySaveEd() {
  const D = PYD, why = pyBlock();
  if (why) { D.need = 1; pyRe(); toast(why, { bad: 1 }); hapt(30); return; }
  if (!D.edit && !pyDupNow().length && pyDupFind({ date: D.date, start: D.start, total: pyTotal(), photo: D.photo }).length && D.dupKey !== pyDupKey()) {
    D.dupKey = pyDupKey(); pyRe(); const b = $$('.sheet-b').pop(); if (b) b.scrollTop = 0; hapt(30); return;
  }
  const o = pyBuild(D);
  if (D.batch) return pyBtStore(o);
  const r = paySave(o);
  if (!r.ok) return done(r);
  if (D.photo && !D.edit && typeof payPhotoSet === 'function') payPhotoSet(r.id, D.photo);
  /* 作成中に店として登録する(支払先が名前だけの相手の時、切替が入っていれば登録済みの店にする) */
  if (!D.edit && D.regShop && pyRegShow(D)) {
    const pr = rolesOf(DB.acts[r.id], '支払先')[0], e = pr && E(pr.e);
    if (e && e.stub) { const g = entUpdate(pr.e, { stub: null, types: e.types.length ? e.types : ['shop'] }); if (g.ok) { const u0 = r.undo; r.undo = () => { g.undo(); u0 && u0(); }; } }
  }
  if (D.edit) return done(r, { close: 1 });
  if (!done(r, { close: 1, go: '#/a/' + (r.id || ''), quiet: 1 })) return;
  toast(r.label, { undo: r.undo });
  if (D.fromRead) setTimeout(() => toast('続けて読み取れます', { act: { label: '続けて読み取る', on: () => openReceipt() } }), 60);
}

/* =====================================================================
   支払い予定を作る・直す(payPlanSave。支払いと同じ形の state:'plan')
   ===================================================================== */
let PYP = null, PYP0 = '';
const PY_EVERY = [['', '一度だけ'], ['month', '毎月'], ['week', '毎週'], ['day', '毎日']];
function openPayPlanEditor(o = {}) {
  const a = o.id ? A(o.id) : null, pr = a && rolesOf(a, '支払先')[0], x = a && xOf(o.id), r = a && a.routine && DB.routines[a.routine];
  if (o.id && !a) return toast('支払い予定が見つかりません', { bad: 1 });
  const sd = pyTsSplit(o.start, o.date);
  PYP = { id: o.id || null, payee: pr ? { e: pr.e } : null, title: a ? (a.title || '') : '', date: a ? aDate(a) : (sd[0] || dAdd(TODAY, 1)), time: a ? (aStart(a) && !isAllDay(a) ? aStart(a) : '') : sd[1], amt: a ? payTotal(a) : (+o.amount || +o.amt || 0), cat: x ? x.cat : catGuess(null), every: r ? r.every : '', fixedEvery: !!(a && !r) };
  PYP0 = JSON.stringify(PYP);
  openSheet({ id: 'pay-plan', title: o.id ? '支払い予定を直す' : '支払い予定を足す', tall: true, body: pypBody(), dirty: () => JSON.stringify(PYP) !== PYP0, primary: { label: '保存', on: pypSave } });
}
function pypBody() {
  const P = PYP, pv = P.payee ? ent(P.payee.e || null, { link: false, name: P.payee.name }) : '支払先を選ぶ';
  return `${field('金額', `<div class="py-amt-f"><i>¥</i>${input({ value: P.amt || '', ph: '0', mode: 'numeric', oninput: 'pypAmt(this.value)', cls: 'big num', auto: !P.id, label: '金額' })}</div>`)}
    ${pickField('支払先', pv, 'pypPayee()', { emptyv: !P.payee })}
    ${field('名前', input({ value: P.title, ph: '例: 家賃', oninput: 'PYP.title=this.value', label: '名前' }))}
    <div class="grid2">${field('日付', input({ type: 'date', value: P.date, onchange: 'PYP.date=this.value', label: '日付' }))}${field('時刻', input({ type: 'time', value: P.time, onchange: 'PYP.time=this.value', label: '時刻' }))}</div>
    ${pickField('カテゴリ', catTag(P.cat, { path: 1 }), 'pypCat()')}
    ${P.fixedEvery ? '' : `<div class="field"><span class="field-l">繰り返し</span>${seg(PY_EVERY, P.every, 'pypEvery')}</div>`}`;
}
const pypAmt = v => { PYP.amt = pyNum(v); };
const pypEvery = k => { PYP.every = k; setSheetBody(pypBody()); };
function pypPayee() { pyPayeePick(PYP.payee, p => { PYP.payee = p; setSheetBody(pypBody()); }); }
function pypCat() { pick({ title: 'カテゴリ', items: pyCatItems(), cur: PYP.cat, search: true, on: k => { PYP.cat = k; setSheetBody(pypBody()); } }); }
function pypSave() {
  const P = PYP; if (P.amt <= 0) return toast('金額を入れてください', { bad: 1 });
  const virt = P.id && P.id.includes('@'), rep = P.every ? { u: P.every } : virt ? { u: DB.routines[P.id.split('@')[0]].every } : null;
  const r = payPlanSave({ id: P.id || undefined, start: P.time ? tsOf(P.date, P.time) : P.date, payee: P.payee, title: P.title.trim() || undefined, amt: P.amt, cat: P.cat, rep });
  done(r, { close: 1, ...(!P.id && r.ok ? { go: '#/a/' + r.id } : {}) });
}

/* =====================================================================
   レシートの読み取り(模擬。見本から選ぶ → 読み取り中 → 値の入った支払いの入力へ。読めなかった値は空のまま)
   ===================================================================== */
const PY_RC = [
  { k: 'super', t: 'スーパー', sub: '6品目・1品が読めない', read: () => ({ payee: { e: 'super' }, date: TODAY, start: '12:41', amt: 2190, unsure: ['amt', 'payee'],
    items: [{ n: '牛乳', price: 238, tax: 8 }, { n: '食パン', price: 158, tax: 8 }, { n: '卵', price: 268, tax: 8 }, { n: '鶏むね肉', price: 498, tax: 8 }, { n: '洗濯洗剤', price: 698 }, { n: '', price: 330, unk: 1 }] }) },
  { k: 'cafe', t: 'カフェ', sub: '2品目', read: () => ({ payee: { e: 'minato' }, date: TODAY, start: '09:15', amt: 740, unsure: ['amt', 'payee'], items: [{ n: 'カフェラテ', price: 480 }, { n: '焼き菓子', price: 260 }] }) },
  { k: 'total', t: '合計だけ', sub: '店名と品目は読めない', read: () => ({ payee: null, date: dAdd(TODAY, -1), start: null, amt: 1480, unsure: ['amt', 'payee'], items: [] }) },
];
let PYRC = null;
const pyPaper = r => `<div class="py-paper" aria-hidden="true"><b class="py-paper-h">${r.payee ? esc(E(r.payee.e).n) : '▒▒▒▒▒▒'}</b>
  ${r.items.length ? r.items.map(i => `<div class="py-paper-r"><span>${i.n ? esc(i.n) : '▒▒▒▒'}</span><span class="num">${i.price.toLocaleString('ja-JP')}</span></div>`).join('') : `<div class="py-paper-r"><span>▒▒▒▒▒▒</span><span>▒▒▒</span></div><div class="py-paper-r"><span>▒▒▒▒</span><span>▒▒▒</span></div>`}
  <div class="py-paper-r tot"><span>合計</span><span class="num">${r.amt.toLocaleString('ja-JP')}</span></div></div>`;
const pyRcBody = () => `<div class="grid2"><label class="btn pri wide py-file">${ic('photo')}<span>写真を選ぶ</span><input type="file" accept="image/*" multiple onchange="pyPhotos(this)" aria-label="写真を選ぶ"></label>
  <label class="btn sec wide py-file">${ic('camera')}<span>撮影する</span><input type="file" accept="image/*" capture="environment" onchange="pyPhotos(this)" aria-label="撮影する"></label></div>
  ${sec('見本から選ぶ')}<div class="py-rcs">${PY_RC.map((s, i) => `<button class="py-rc" onclick="pyRcRead(${i})">${pyPaper(s.read())}<span class="py-rc-l"><b>${esc(s.t)}</b><span>${esc(s.sub)}</span></span></button>`).join('')}</div>`;
function openReceipt(o = {}) {
  PYRC = { o, timer: null, rows: null, view: 'pick' };
  openSheet({ id: 'receipt', title: 'レシートを読み取る', tall: true, body: pyRcBody(), dirty: () => !!(PYRC && PYRC.rows && PYRC.rows.length),
    onClose: () => { if (PYRC) { clearInterval(PYRC.timer); (PYRC.rows || []).forEach(r => r.url && URL.revokeObjectURL(r.url)); } } });
}
function pyRcRead(i, hash) {
  const s = PY_RC[i], r = s.read(); let p = 0; if (hash) r.photo = hash;
  setSheetBody(`<div class="py-scanbox">${pyPaper(r)}<i class="py-scan"></i></div><div id="py-prog">${progress(0, '読み取り中')}</div>`); setSheetPrimary(null);
  PYRC.timer = setInterval(() => {
    p = Math.min(1, p + .09); const e = $('#py-prog'); if (e) e.innerHTML = progress(p, p < 1 ? '読み取り中' : '読み取りました'); if (p < 1) return;
    clearInterval(PYRC.timer); const o = PYRC.o; if (o.replace) closeAllSheets(); else closeSheet(true);
    openPayEditor(o.parent ? { read: r, parent: o.parent } : { read: r });
  }, 90);
}
/* ---- 写真を選ぶ(模擬: 見本の読み取り結果を順に割り当てる。最大20枚) ---- */
/** 写真のハッシュ(SHA-256。使えない時は大きさと名前) */
async function pyHash(f) {
  try { const d = await crypto.subtle.digest('SHA-256', await f.arrayBuffer()); return Array.from(new Uint8Array(d)).map(b => b.toString(16).padStart(2, '0')).join(''); }
  catch (e) { return `size:${f.size}:${f.name}`; }
}
async function pyPhotos(inp) {
  let fs = [...inp.files]; inp.value = ''; if (!fs.length) return;
  if (fs.length > 20) { fs = fs.slice(0, 20); toast('最大20枚までです', { bad: 1 }); }
  const hs = await Promise.all(fs.map(pyHash));
  if (!PYRC) return;
  if (fs.length === 1) return pyRcRead(0, hs[0]);
  PYRC.rows = fs.map((f, i) => {
    const r = PY_RC[i % PY_RC.length].read(); if (i >= PY_RC.length) r.date = dAdd(r.date, -Math.floor(i / PY_RC.length));
    return { i, name: f.name, url: URL.createObjectURL(f), r, hash: hs[i], p: 0, st: 'wait', skip: false, keep: false, dupWhy: '', listDup: '' };
  });
  PYRC.view = 'list'; PYRC.tick = 0; pyBtRender();
  PYRC.timer = setInterval(pyBtTick, 90);
}
/** 保存済みの支払いとの重複(同じ写真 → 理由を写真に。同じ日・金額・時刻 → その理由) */
function pyBtSavedDup(D, hash) {
  const hit = pyDupFind({ date: D.date, start: D.start, total: pyTotal(D), photo: hash });
  if (!hit.length) return '';
  return hash && hit.some(([, a]) => a.photo === hash) ? '同じ写真が保存済み' : '同じ日・同じ金額の支払いが保存済み';
}
/** 一覧の中の重複: 確認済みの行と、同じ写真、または同じ日・金額・5分以内 */
function pyBtListDups(R) {
  R.rows.forEach((r, i) => {
    r.listDup = '';
    if (!r.D || r.st !== 'ready') return;
    const hit = R.rows.findIndex((o, j) => j !== i && o.done && o.D && ((r.hash && o.hash === r.hash) || (o.D.date === r.D.date && pyTotal(o.D) === pyTotal(r.D) && pyTotal(r.D) > 0 && Math.abs(hm2m(o.D.start || NOW) - hm2m(r.D.start || NOW)) <= 5)));
    if (hit >= 0) r.listDup = r.hash && R.rows[hit].hash === r.hash ? `同じ写真が${hit + 1}枚目にあります` : `同じ日・同じ金額が${hit + 1}枚目にあります`;
  });
}
/** 重複の理由(保存済み・一覧内)。残すと決めた行は理由があっても入る */
const pyBtDupWhy = r => r.dupWhy || r.listDup || '';
const pyBtIn = r => r.st === 'ready' && !r.skip && !r.block && (!pyBtDupWhy(r) || r.keep);
function pyBtTick() {
  const R = PYRC; if (!R || !R.rows) return clearInterval(R && R.timer); R.tick++;
  let busy = 0;
  R.rows.forEach(r => {
    if (r.st === 'ready') return; busy = 1;
    if (R.tick < r.i * 2) return; r.st = 'reading'; r.p = Math.min(1, r.p + .09);
    if (r.p >= 1) {
      const rd = { ...r.r, parent: R.o.parent, photo: r.hash }, D = pyDraft({ read: rd, parent: R.o.parent, pure: 1 });
      r.D = D; r.save = pyBuild(D); r.block = pyBlock(D); r.dupWhy = pyBtSavedDup(D, r.hash); r.st = 'ready';
    }
  });
  if (R.view === 'list') pyBtRender();
  if (!busy) clearInterval(R.timer);
}
const pyBtN = () => PYRC.rows.filter(pyBtIn).length;
function pyBtRow(r) {
  const D = r.D, name = D ? (D.payee ? (D.payee.e && E(D.payee.e) ? E(D.payee.e).n : D.payee.name) : '店名なし') : r.name, why = pyBtDupWhy(r), pending = r.st === 'ready' && why && !r.keep && !r.skip;
  const st = r.st !== 'ready' ? progress(r.st === 'wait' ? 0 : r.p, '読み取り中') : r.skip ? tag('飛ばす') : r.block ? tag('確認が要る', 'read') : pending ? tag('重複の疑い') : r.done ? tag('確認済み', 'me') : tag('確認待ち', 'read');
  const why2 = pending ? `<span class="py-bt-why">${esc(why)}</span>` : '';
  const acts = r.st !== 'ready' ? '' : `<div class="py-bt-acts">${pending
    ? `<button class="py-bt-x" onclick="pyBtKeep(${r.i})">別の支払いとして残す</button><button class="py-bt-x" onclick="pyBtSkip(${r.i})">スキップ</button>`
    : `<button class="py-bt-x" onclick="pyBtSkip(${r.i})">${r.skip ? '含める' : '飛ばす'}</button>`
    + (r.done ? `<button class="py-bt-x" onclick="pyBtUndo(${r.i})">戻す</button>` : '')}</div>`;
  return `<div class="py-bt${r.skip ? ' off' : ''}"><button class="py-bt-m" onclick="pyBtOpen(${r.i})" ${r.st === 'ready' ? '' : 'disabled'}><img class="py-bt-th" src="${r.url}" alt=""><span class="py-bt-t"><b>${esc(name)}</b><span class="py-bt-s">${st}</span>${why2}</span>${r.st === 'ready' ? amt(pyTotal(D)) : ''}</button>${acts}</div>`;
}
function pyBtRender() {
  const R = PYRC; if (!R || !R.rows || R.view !== 'list') return;
  pyBtListDups(R);
  const N = R.rows.length, rd = R.rows.filter(r => r.st === 'ready').length, cf = R.rows.filter(r => r.done).length;
  setSheetTitle(`レシート ${N}枚`);
  setSheetBody(`<div class="py-bts-sum"><div class="py-bts-h"><b class="num">${rd}/${N}枚 読み取り済み</b><span class="mini num">${cf}件 確認済み</span></div>${progress(rd / N)}</div><div class="py-bts">${R.rows.map(pyBtRow).join('')}</div>`);
  const n = pyBtN(), busy = R.rows.some(r => r.st !== 'ready'); setSheetPrimary({ label: `${n}件の支払いを保存`, on: pyBtSaveAll, disabled: !n || busy });
}
function pyBtBack() { if (PYRC && PYRC.rows) { PYRC.view = 'list'; pyBtRender(); } }
function pyBtOpen(i) { const r = PYRC.rows[i]; if (!r || r.st !== 'ready') return; PYRC.view = 'edit'; openPayEditor({ draft: r.D, batch: i + 1 }); }
function pyBtSkip(i) { const r = PYRC.rows[i]; r.skip = !r.skip; if (!r.skip) r.keep = false; pyBtRender(); }
/** 重複のある行を、別の支払いとして残す */
function pyBtKeep(i) { const r = PYRC.rows[i]; r.keep = true; r.skip = false; pyBtRender(); }
/** 確認済みの行を確認待ちに戻す(入力は残る) */
function pyBtUndo(i) { const r = PYRC.rows[i]; r.done = 0; pyBtRender(); }
function pyBtStore(o) {
  const r = PYRC.rows[PYD.batch - 1]; r.D = JSON.parse(JSON.stringify(PYD)); r.D.batch = 0; r.save = o; r.block = ''; r.done = 1; r.skip = false;
  r.dupWhy = pyBtSavedDup(r.D, r.hash);
  closeSheet(true);
}
function pyBtSaveAll() {
  const rs = PYRC.rows.filter(pyBtIn), undos = []; let bad = '';
  rs.forEach(r => { const x = paySave(r.save); if (x.ok) { undos.push(x.undo); if (r.hash && typeof payPhotoSet === 'function') payPhotoSet(x.id, r.hash); } else bad = x.why; });
  if (!undos.length) return toast(bad || 'できませんでした', { bad: 1 });
  closeAllSheets(); rerender();
  toast(`${undos.length}件の支払いを保存しました`, { undo: () => undos.slice().reverse().forEach(u => u()) }); hapt();
}
