/* 個人記録 v6 — 家計簿(月のまとめ・貸し借り・カテゴリの木)。接頭辞 lg */
'use strict';

/* 状態(モジュールの変数): 月・絞り込み・開いている枝 */
const LG = { ym: '', shown: '', f: 'all', cat: null, open: {}, owe: {} };
const lgNow = () => ymOf(TODAY);
const lgDay = d => `${+d.slice(8)}日 ${dowJ(d)}曜`;

/* ================= 家計簿 ================= */
route('ledger', (id, qs) => {
  if (qs.m && /^\d{4}-(0[1-9]|1[0-2])$/.test(qs.m)) LG.ym = qs.m;
  if (!LG.ym) LG.ym = lgNow();
  const oldest = oldestYm();
  if (LG.ym > lgNow()) LG.ym = lgNow(); if (LG.ym < oldest) LG.ym = oldest;
  if (LG.shown !== LG.ym) { LG.shown = LG.ym; LG.f = 'all'; LG.cat = null; LG.open = {}; }
  if (LG.cat && !DB.cats[LG.cat]) { LG.cat = null; if (LG.f === 'cat') LG.f = 'all'; }
  const ym = LG.ym, S = monthSum(ym);
  if (qs.v === 'owe' && !isRerender()) after(() => { const el = $('#owe'); if (el) el.scrollIntoView({ block: 'start' }); });
  return `<div class="screen s-ledger">
    ${head('家計簿', { actions: iconBtn('tag', 'カテゴリ', "go('#/cats')", 'on-card') + iconBtn('plus', '支払いを記録', 'lgAddPay()', 'pri') })}
    <div class="tl-sticky">${monthNav(ym, 'lgStep', { noPrev: ym <= oldest, noNext: ym >= lgNow() })}</div>
    ${S.n ? lgHero(ym, S) + lgStats(S) + lgPace(ym) + lgTrend(ym) + lgTree(ym, S) : `<section class="card lg-hero">${lgHeroTop(ym, S)}${empty({ icon: 'wallet', text: `${fmtYM(ym)}の支出は ありません`, action: { label: '支払いを記録', on: 'lgAddPay()' } })}</section>`}
    ${lgOwe()}
    ${ym === lgNow() ? lgUpcoming() : ''}
    ${S.n ? lgList(ym) : ''}
  </div>`;
}, { tab: 'ledger', root: 1 });

const lgAddPay = () => window.openPayEditor && openPayEditor({ date: LG.ym === lgNow() ? TODAY : LG.ym + '-01' });
function lgStep(n) { const ym = ymAdd(LG.ym, n); if (ym > lgNow() || ym < oldestYm()) return; go(`#/ledger?m=${ym}`, { replace: 1 }); }
function lgMonth(ym) { go(`#/ledger?m=${ym}`, { replace: 1 }); }
function lgToList() { after(() => { const el = $('#lg-list'); if (el) el.scrollIntoView({ block: 'start', behavior: 'smooth' }); }); }
/** 絞り込みを選ぶ(chips)。選んでいるものをもう一度で解除 */
function lgF(k) { LG.f = LG.f === k && k !== 'all' ? 'all' : k; if (k === 'cat') LG.cat = LG.f === 'cat' ? LG.cat : null; rerender(); }
/** 数字3つから絞る(下の一覧へ運ぶ) */
function lgStat(k) { LG.f = LG.f === k ? 'all' : k; if (LG.f !== 'cat') LG.cat = null; if (LG.f !== 'all') lgToList(); rerender(); }
function lgCatFilter(k) { if (LG.f === 'cat' && LG.cat === k) { LG.f = 'all'; LG.cat = null; } else { LG.f = 'cat'; LG.cat = k; lgToList(); } rerender(); }
function lgToggle(k) { LG.open[k] = !LG.open[k]; rerender(); }

/* ---------- 主役: その月の自分の支出 ---------- */
function lgHeroTop(ym, S) {
  const cur = ym === lgNow(), prev = ymAdd(ym, -1), P = monthSum(prev);
  let cmp = '';
  if (P.n) {
    const dd = +TODAY.slice(8), base = cur ? expsIn(prev).filter(([, a]) => +a.date.slice(8) <= dd).reduce((s, r) => s + r[2], 0) : P.total, diff = S.total - base;
    const pct = base > 0 ? Math.round(diff / base * 100) : null;
    cmp = `<div class="lg-cmp"><span>${cur ? '先月の同じ日まで' : '先月'} <b class="num">${yen(base)}</b></span><span class="lg-diff num" aria-label="差額">${diff === 0 ? '±¥0' : amt(diff, { sign: 1 })}${pct != null ? `<small class="lg-pct">${pct > 0 ? '+' : pct < 0 ? '−' : '±'}${Math.abs(pct)}%</small>` : ''}</span></div>`;
  }
  return `<div class="lg-hl">自分の支出</div>${amt(S.total, { cls: 'hero' })}${cmp}`;
}
function lgHero(ym, S) {
  const rs = S.roots.filter(r => r.amt > 0), top = rs.slice(0, 5), rest = rs.slice(5), restSum = rest.reduce((s, r) => s + r.amt, 0);
  const stops = rest.slice(0, 4).map((r, i, a) => `${catColor(r.cat)} ${i * 100 / a.length}% ${(i + 1) * 100 / a.length}%`).join(',');
  return `<section class="card lg-hero">${lgHeroTop(ym, S)}
    <div class="lg-stack" role="img" aria-label="大分類ごとの割合">${rs.map(r => `<i style="flex:${r.amt};background:${catColor(r.cat)}"></i>`).join('')}</div>
    <ul class="lg-leg">${top.map(r => `<li><i style="background:${catColor(r.cat)}"></i><span>${esc(catName(r.cat))}</span><b class="num">${yen(r.amt)}</b></li>`).join('')}${rest.length ? `<li><i style="background:linear-gradient(90deg,${stops})"></i><span>ほか ${rest.length}件</span><b class="num">${yen(restSum)}</b></li>` : ''}</ul></section>`;
}
const lgStats = S => stats([['fixed', '固定費', S.fixed], ['variable', '変動費', S.variable], ['special', '非日常', S.special], ...(S.unset > 0 ? [['unset', '未指定', S.unset]] : [])].map(([k, l, v]) => ({ v: amt(v), l, on: `lgStat('${k}')`, kind: LG.f === k ? 'me' : '' }))).replace('class="stats"', `class="stats${S.unset > 0 ? ' lg-st4' : ''}"`);

/* ---------- 変動費のペース(今月=青の実線・今日まで / 先月=灰の点線) ---------- */
function lgPace(ym) {
  const cur = ym === lgNow(), prev = ymAdd(ym, -1), dd = cur ? +TODAY.slice(8) : ymDays(ym);
  const A_ = paceOf(ym, dd), B = monthSum(prev).n ? paceOf(prev) : [];
  const max = Math.max(...A_, ...B, 0); if (!max) return '';
  const N = Math.max(ymDays(ym), B.length || 0), X = i => N > 1 ? i / (N - 1) * 100 : 0, Y = v => 100 - v / max * 100;
  const pts = arr => arr.map((v, i) => `${X(i).toFixed(2)},${Y(v).toFixed(2)}`).join(' ');
  const last = A_[A_.length - 1], px = X(A_.length - 1), same = B[dd - 1];
  const alignR = px > 55;
  return `<section>${sec('変動費のペース')}<div class="card pad lg-pace">
    <div class="lg-pl"><span class="lg-pmax num">${yen(max)}</span>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><line x1="0" y1="0" x2="100" y2="0" class="g"/><line x1="0" y1="100" x2="100" y2="100" class="g"/>
        ${B.length > 1 ? `<polyline points="${pts(B)}" class="b"/>` : ''}${A_.length > 1 ? `<polyline points="${pts(A_)}" class="a"/>` : ''}</svg>
      <i class="lg-dot" style="left:${px}%;top:${Y(last)}%"></i>
      <span class="lg-dl num ${alignR ? 'r' : ''}" style="left:${px}%;top:${Y(last)}%">${yen(last)}</span>
      <div class="lg-px num" aria-hidden="true"><span style="left:0">1日</span><span style="left:${X(14)}%">15日</span><span style="left:100%">${ymDays(ym)}日</span></div></div>
    <div class="lg-pk"><span><i class="a"></i>${cur ? '今月' : fmtYM(ym).slice(5)}${cur ? ` ${dd}日まで` : ''}</span>${B.length ? `<span><i class="b"></i>先月${same != null ? ` 同じ日 <b class="num">${yen(same)}</b>` : ''}</span>` : ''}</div></div></section>`;
}

/* ---------- 推移(6か月) ---------- */
function lgTrend(ym) {
  const now = lgNow(), oldest = oldestYm();
  let end = now; if (ym < ymAdd(end, -5)) end = ymAdd(ym, 5) > now ? now : ymAdd(ym, 5);
  const ms = [...Array(6)].map((_, i) => ymAdd(end, i - 5)).filter(m => m >= oldest);
  if (ms.length < 2) return '';
  const order = catKids(null), data = ms.map(m => { const S = monthSum(m); return { m, t: S.total, segs: S.roots.filter(r => r.amt > 0).sort((a, b) => order.indexOf(a.cat) - order.indexOf(b.cat)) }; });
  const past = data.filter(d => d.m < now), max = Math.max(1, ...data.map(d => d.t)), avg = past.reduce((s, d) => s + d.t, 0) / (past.length || 1), ap = avg / max * 100, hasAvg = past.length > 0;
  const al = ap < 22 ? 'l' : ap > 70 ? 'r' : 'c';
  return `<section>${sec('推移', { n: `${data.length}か月` })}<div class="card pad lg-tr">
    <div class="lg-trh"><span class="lg-trl">${hasAvg ? `<b class="${al} num" style="left:${ap.toFixed(1)}%">${past.length < data.length ? '先月までの平均' : '平均'} ${yen(avg)}</b>` : ''}</span></div>
    ${data.map(d => `<button class="lg-mr" aria-current="${d.m === ym}" onclick="lgMonth('${d.m}')" aria-label="${fmtYM(d.m)} ${yen(d.t)}"><span class="lg-ml num">${+d.m.slice(5)}月</span>
      <span class="lg-mt"><span class="lg-mb" style="width:${(d.t / max * 100).toFixed(2)}%">${d.segs.map(r => `<i style="flex:${r.amt};background:${catColor(r.cat)}"></i>`).join('')}</span>${hasAvg && d.m < now ? `<i class="lg-avg" style="left:${ap.toFixed(2)}%"></i>` : ''}</span>
      <span class="lg-mv">${amt(d.t, { cls: d.t ? '' : 'mute' })}</span></button>`).join('')}</div></section>`;
}

/* ---------- 内訳(カテゴリの木) ---------- */
function lgTree(ym, S) {
  const total = S.total || 1;
  const node = (k, depth) => {
    const v = catSum(ym, k); if (v <= 0) return [];
    const kids = catKids(k).filter(c => catSum(ym, c) > 0), p = Math.round(v / total * 100), open = !!LG.open[k], on = LG.f === 'cat' && LG.cat === k;
    const out = [row({
      on: kids.length ? `lgToggle(${q(k)})` : `lgCatFilter(${q(k)})`, cls: `lg-n d${Math.min(depth, 3)}${open ? ' is-open' : ''}${on ? ' is-on' : ''}`, chev: !!kids.length,
      lead: `<i class="lg-sw" style="background:${catColor(k)}"></i>`, tHtml: `${esc(catName(k))}`, sHtml: `<i class="lg-bar"><b style="width:${Math.max(1.5, v / total * 100).toFixed(1)}%;background:${catColor(k)}"></b></i>`,
      right: `<span class="lg-pc num">${p || '<1'}%</span>${amt(v)}`
    })];
    if (kids.length && open) {
      out.push(row({ on: `lgCatFilter(${q(k)})`, cls: `lg-n lg-fl d${Math.min(depth + 1, 3)}${on ? ' is-on' : ''}`, lead: ic('filter'), t: 'この分類で絞る', right: on ? tag('絞り込み中', 'me') : '' }));
      kids.forEach(c => out.push(...node(c, depth + 1)));
      const own = v - kids.reduce((s, c) => s + catSum(ym, c), 0);
      if (own > 0) out.push(row({ cls: `lg-n d${Math.min(depth + 1, 3)}`, lead: `<i class="lg-sw" style="background:${catColor(k)}"></i>`, t: 'そのまま', right: `<span class="lg-pc num">${Math.round(own / total * 100) || '<1'}%</span>${amt(own)}` }));
    }
    return out;
  };
  const list = S.roots.filter(r => r.amt > 0).flatMap(r => node(r.cat, 0));
  return `<section>${sec('内訳')}${rows(list)}</section>`;
}

/* ---------- 貸し借り ---------- */
function lgOweOpen(who) { LG.owe[who] = !LG.owe[who]; rerender(); }
function lgSettle(ids) { done(paySettle(ids)); }
function lgOwe() {
  const ps = personBalances(); if (!ps.length) return '';
  const lendSum = ps.filter(b => b.amt > 0).reduce((s, b) => s + b.amt, 0), borrowSum = ps.filter(b => b.amt < 0).reduce((s, b) => s - b.amt, 0);
  const tot = `<span class="lg-ot">${lendSum > 0 ? `<span>貸し ${amt(lendSum)}</span>` : ''}${borrowSum > 0 ? `<span>借り ${amt(borrowSum)}</span>` : ''}</span>`;
  return `<section id="owe" class="lg-owe">${sec('貸し借り', { n: ps.length + tot })}${ps.map(b => {
    const open = !!LG.owe[b.who], name = whoName(b.who), lend = b.amt > 0;
    return `<div class="card lg-p">${rows(row({ on: `lgOweOpen(${q(b.who)})`, cls: open ? 'is-open' : '', lead: avatar(b.who, name), t: E(b.who) ? name + 'さん' : name, sHtml: `${tag(lend ? '貸し' : '借り', 'owe')}<span>${b.ids.length}件 未精算</span>`, right: amt(Math.abs(b.amt)), chev: true }), 'lg-ph')}
      ${open ? `<div class="lg-pb stack">${b.ids.map(id => `<div class="lg-ps">${paySlip(id)}${b.ids.length > 1 ? `<button class="btn ghost sm" onclick="lgSettle(${q([id])})">${ic('check')}<span>精算済みにする</span></button>` : ''}</div>`).join('')}
        ${b.ids.length > 1 ? btn('まとめて精算済みにする', `lgSettle(${q(b.ids)})`, 'sec', { wide: 1, icon: 'check' }) : btn('精算済みにする', `lgSettle(${q(b.ids)})`, 'sec', { wide: 1, icon: 'check' })}</div>` : ''}</div>`;
  }).join('')}</section>`;
}

/* ---------- これからの支払い予定(今月を見ている時だけ) ---------- */
function lgUpcoming() {
  const L = upcoming(40).filter(([, a]) => a.kind === 'pay'); if (!L.length) return '';
  const sum = L.reduce((s, [, a]) => s + ((a.pay && a.pay.plan && a.pay.plan.amt) || 0), 0);
  return `<section>${sec('これからの支払い', { n: L.length })}${rows([...L.map(([id, a, d]) => { const pl = (a.pay && a.pay.plan) || {};
    return row({ href: `#/a/${id}`, lead: `<span class="lg-ud num">${fmtMD(d)}<small>${dowJ(d)}</small></span>`, t: actTitle(a) || payeeOf(a), sHtml: `${pl.cat ? catTag(pl.cat) : ''}<span class="mini">${ic('repeat')}${esc(repText(A(a.tpl || id)) || '一度だけ')}</span>`, right: amt(pl.amt || 0), chev: true }); }),
    `<div class="row lg-tot"><span class="row-m"><span class="row-t">あと</span></span><span class="row-r">${amt(sum)}</span></div>`])}</section>`;
}

/* ---------- 支出の一覧 ---------- */
function lgMatch(id) {
  switch (LG.f) {
    case 'fixed': return xFx(id) === '固定';
    case 'variable': return xFx(id) === '変動';
    case 'unset': return xFx(id) == null;
    case 'special': return xSp(id) === '非日常';
    case 'shared': return isShared(id);
    case 'cat': return xCats(id).some(g => catAnc(g.cat).includes(LG.cat));
    default: return true;
  }
}
function lgList(ym) {
  const all = expsIn(ym), byCat = LG.f === 'cat' && LG.cat, L = all.filter(([id]) => lgMatch(id));
  const amtOf = r => byCat ? xCats(r[0]).filter(g => catAnc(g.cat).includes(LG.cat)).reduce((t, g) => t + g.amt, 0) : r[2], sum = L.reduce((s, r) => s + amtOf(r), 0);
  const days = [...new Set(L.map(r => r[1].date))];
  const chipL = [['all', 'すべて'], ['fixed', '固定費'], ['variable', '変動費'], ...(monthSum(ym).unset > 0 || LG.f === 'unset' ? [['unset', '未指定']] : []), ['special', '非日常'], ...(LG.cat ? [['cat', catName(LG.cat)]] : []), ['shared', '割り勘・立替あり']];
  return `<section id="lg-list">${sec('支出')}${chips(chipL, LG.f, 'lgF').replace('class="chips"', 'class="chips wrap"')}
    <div class="lg-sum"><span class="num">${L.length}件</span>${amt(sum)}</div>
    ${L.length ? days.map(d => `<div class="lg-dg"><div class="lg-dh"><b>${lgDay(d)}${d === TODAY ? ' <span class="tag me">今日</span>' : ''}</b>${amt(L.filter(r => r[1].date === d).reduce((s, r) => s + amtOf(r), 0), { cls: 'mute' })}</div>
      <div class="stack">${L.filter(r => r[1].date === d).map(([id]) => paySlip(id, byCat ? { cat: LG.cat } : {})).join('')}</div></div>`).join('') : `<div class="card">${empty({ icon: 'filter', text: 'あてはまる支出は ありません', action: { label: 'すべて見る', on: "lgF('all')" } })}</div>`}</section>`;
}

/* ================= カテゴリの木 ================= */
const LGC = { key: null, n: '', p: null, c: '', fx: '変動', sp: '日常', init: '' };
route('cats', () => {
  const ym = lgNow(), roots = catKids(null);
  const tagOf = (v, a, b) => v === undefined ? '' : v === 'ask' ? tag(`${a}${b}は都度`) : tag(v);
  /* 自分で決めている = ふつうの札 / 親から引き継ぎ = 点線の札(plan)。どちらも無ければ「都度決める」 */
  const tagsOf = k => {
    const c = DB.cats[k], out = [];
    [['fx', '固定・', '変動', catFx], ['sp', '日常・', '非日常', catSp]].forEach(([f, a, b, fn]) => { if (c[f] !== undefined) out.push(tagOf(c[f], a, b)); else if (fn(k)) out.push(tag(fn(k), 'plan')); });
    return out.length ? out.join('') : tag('都度決める');
  };
  const lines = (k, depth) => {
    const c = DB.cats[k];
    const own = [row({ on: `lgCatOpen(${q(k)})`, cls: `lg-n d${Math.min(depth, 3)}`, lead: `<i class="lg-sw" style="background:${catColor(k)}"></i>`, tHtml: esc(c.n),
      sHtml: tagsOf(k), right: amt(catSum(ym, k), { cls: catSum(ym, k) ? '' : 'mute' }), chev: true })];
    catKids(k).forEach(x => own.push(...lines(x, depth + 1)));
    if (depth === 0) own.push(row({ on: `lgCatNew(${q(k)})`, cls: `lg-n lg-add d1`, lead: ic("plus"), t: "子を足す" }));
    return own;
  };
  return `<div class="screen s-cats">
    ${bar('カテゴリ', { actions: iconBtn('plus', '分類を足す', 'lgCatNew()', 'pri') })}
    ${head('カテゴリ', { sub: `${Object.keys(DB.cats).length}件 · ${fmtYM(ym)}の額` })}
    ${roots.length ? roots.map(k => `<section>${rows(lines(k, 0))}</section>`).join('') : `<div class="card">${empty({ icon: 'tag', text: 'カテゴリは ありません', action: { label: '分類を足す', on: 'lgCatNew()' } })}</div>`}
    ${btn('分類を足す', 'lgCatNew()', 'dash', { icon: 'plus' })}
  </div>`;
}, { tab: 'ledger' });

const lgSnap = () => JSON.stringify([LGC.n, LGC.p, LGC.c, LGC.fx, LGC.sp]);
function lgCatOpen(key) {
  const c = DB.cats[key]; if (!c) return;
  Object.assign(LGC, { key, n: c.n, p: c.p || null, c: c.c || CAT_COLORS[0], fx: c.p && c.fx === undefined ? 'inherit' : (c.fx === undefined ? 'ask' : c.fx), sp: c.p && c.sp === undefined ? 'inherit' : (c.sp === undefined ? 'ask' : c.sp) });
  LGC.init = lgSnap(); lgCatSheet();
}
function lgCatNew(parent) {
  Object.assign(LGC, { key: null, n: '', p: parent || null, c: CAT_COLORS.find(c => !Object.values(DB.cats).some(x => x.c === c)) || CAT_COLORS[0], fx: parent ? 'inherit' : '変動', sp: parent ? 'inherit' : '日常' });
  LGC.init = lgSnap(); lgCatSheet();
}
function lgCatBody() {
  const root = !LGC.p, opts = (a, mid) => [...(root ? [] : [['inherit', '引き継ぐ']]), ...a, ['ask', '都度決める']];
  return `<div class="stack lg-cf">
    ${field('名前', input({ id: 'lgc-n', value: LGC.n, ph: '例: 外食', oninput: 'LGC.n=this.value', auto: !LGC.key, label: '名前' }))}
    ${pickField('親', LGC.p && DB.cats[LGC.p] ? `<span class="lg-pv"><i class="lg-sw" style="background:${catColor(LGC.p)}"></i>${esc(catPath(LGC.p))}</span>` : 'なし(大分類)', 'lgCatParent()')}
    ${root ? field('色', `<div class="lg-cols" role="radiogroup" aria-label="色">${CAT_COLORS.map(c => `<button class="lg-col" role="radio" aria-checked="${c === LGC.c}" aria-label="${c}" style="background:${c}" onclick="LGC.c='${c}';setSheetBody(lgCatBody())">${c === LGC.c ? ic('check') : ''}</button>`).join('')}</div>`) : ''}
    ${field('固定・変動', seg(opts([['固定', '固定'], ['変動', '変動']]), LGC.fx, 'lgSetFx'))}
    ${field('特別感', seg(opts([['日常', '日常'], ['非日常', '非日常']]), LGC.sp, 'lgSetSp'))}
  </div>`;
}
function lgSetFx(v) { LGC.fx = v; setSheetBody(lgCatBody()); }
function lgSetSp(v) { LGC.sp = v; setSheetBody(lgCatBody()); }
function lgCatParent() {
  const items = []; const walk = (k, d) => { if (LGC.key && k === LGC.key) return; items.push({ k, label: catName(k), depth: d, lead: `<i class="lg-sw" style="background:${catColor(k)}"></i>` }); catKids(k).forEach(c => walk(c, d + 1)); };
  catKids(null).forEach(k => walk(k, 0));
  pick({ title: '親', items, cur: LGC.p, none: { label: 'なし(大分類)' }, on: k => { LGC.p = k; if (!k) { if (LGC.fx === 'inherit') LGC.fx = 'ask'; if (LGC.sp === 'inherit') LGC.sp = 'ask'; } setSheetBody(lgCatBody()); } });
}
function lgCatSheet() {
  openSheet({ title: LGC.key ? 'カテゴリを直す' : LGC.p ? '子を足す' : '分類を足す', body: lgCatBody(), dirty: () => lgSnap() !== LGC.init,
    primary: { label: '保存', on: lgCatSave }, secondary: LGC.key ? { label: '消す', on: () => done(catRemove(LGC.key), { close: 1 }) } : null });
}
function lgCatSave() {
  const f = v => v === 'inherit' || v === 'ask' ? (v === 'ask' ? 'ask' : null) : v, patch = { c: LGC.p ? null : LGC.c, fx: f(LGC.fx), sp: f(LGC.sp) };
  if (!LGC.key) {
    const r = catAdd(LGC.n, LGC.p); if (!r.ok) return done(r);
    const r2 = catUpdate(r.key, patch); if (!r2.ok) { r.undo(); return done(r2); }
    return done({ ok: true, label: 'カテゴリを足しました', undo: () => { r2.undo(); r.undo(); } }, { close: 1 });
  }
  done(catUpdate(LGC.key, { n: LGC.n, p: LGC.p || null, ...patch }), { close: 1 });
}
