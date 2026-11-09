/* =====================================================================
   家計簿(画面担当 S5)— #/ledger(?m=YYYY-MM) / #/x/{支出id} / #/cats
   接頭辞 lg_。金額の計算は money.js / data.js の関数だけを使う(この画面で金額を作らない)。
   見た目は css/screens/ledger.css(値は tokens の変数)。
   ===================================================================== */

let lgAxis = 'cat', lgFilter = null, lgFilterYm = null, lgDraft = null, lgDraftKey = null, lgDraftSnap = '', lgEd = null;
const LG_AX = {cat: 'カテゴリ', fx: '固定・変動', sp: '特別感'};
/* 6か月(古い月が上)。5〜9月は TREND の固定値、10月と記録のある月は支出(X)から出す */
const LG_MONTHS = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];
const LG_NOW = TODAY.slice(0, 7);
const lgMo = ym => `${+ym.slice(5)}月`;
const lgPct = (v, t) => t ? Math.round(v / t * 100) : 0;
const lgSq = k => `<i class="lg-sq" style="--cc:${esc(catColor(k))}"></i>`;
const lgXDate = x => (dayOf(A[x.pay]) || {}).date || '';
const lgXsIn = ym => Object.entries(X).filter(([, x]) => lgXDate(x).slice(0, 7) === ym);
const childrenOf = k => Object.keys(CATS).filter(c => CATS[c].p === k);
const lgPills = (items, cur, fn) => `<div class="p-seg lg-pills" role="group">${items.map(([v, l]) => `<button class="p-seg-i" type="button" aria-pressed="${cur === v}" onclick="${fn.replace('$v', v)}">${esc(l)}</button>`).join('')}</div>`;

/* ---------- 計算(月ごとの大分類の合計・数字) ---------- */
/** 月の大分類ごとの合計 {大分類: 額}。記録がある月は支出から、無い月は TREND から */
function lgMap(ym) {
  const m = {};
  ROOTS().forEach(r => m[r] = 0);
  const xs = lgXsIn(ym);
  if (xs.length) {
    xs.forEach(([, x]) => xCats(x).forEach(g => { m[catAnc(g.cat)[0]] += g.amt }));
    return m;
  }
  const t = TREND[LG_MONTHS.indexOf(ym)];
  if (t) Object.entries(t[1]).forEach(([k, v]) => { if (m[k] !== undefined) m[k] = v });
  return m;
}
const lgTotal = m => Object.values(m).reduce((a, b) => a + b, 0);
/** 月の数字: 合計・固定費・変動費・非日常 */
function lgFigs(ym) {
  const xs = lgXsIn(ym);
  if (xs.length) {
    const where = p => xs.reduce((a, [, x]) => a + (p(x) ? xAmt(x) : 0), 0);
    return {tot: where(() => true), fixed: where(x => xFx(x) === '固定'), varia: where(x => xFx(x) === '変動'), nonDaily: where(x => xSp(x) === '非日常'), rec: true};
  }
  const m = lgMap(ym), tot = lgTotal(m);
  const sumF = f => ROOTS().filter(r => catFx(r) === f).reduce((a, r) => a + m[r], 0);
  return {tot, fixed: sumF('固定'), varia: sumF('変動'), nonDaily: 0, rec: false};
}
/** 支出がフィルタに合うか(固定・変動・特別感・カテゴリ) */
function lgMatch(x) {
  if (!lgFilter) return true;
  const {ax, k} = lgFilter;
  return ax === 'cat' ? xAmtIn(x, k, 1) > 0 : ax === 'fx' ? (xFx(x) || '未指定') === k : (xSp(x) || '未指定') === k;
}
const lgFilterName = () => lgFilter.ax === 'cat' ? CATS[lgFilter.k].n : lgFilter.k;

/* ---------- 操作 ---------- */
function lgGo(ym) { lgFilter = null; go(`#/ledger?m=${ym}`) }
/** 絞り込み。setAx=true は軸も合わせる(固定費・変動費・非日常の数字から)。同じ行の再タップで解除 */
function lgPick(ax, k, setAx) {
  if (setAx) lgAxis = ax;
  const same = !!lgFilter && lgFilter.ax === ax && lgFilter.k === k;
  lgFilter = same ? null : {ax, k};
  rerender();
}
function lgAxisSet(a) { lgAxis = a; lgFilter = null; rerender() }
function lgClear() { lgFilter = null; rerender() }

/* ---------- 家計簿(月) ---------- */
/** 推移の1行。押すとその月へ */
function lgTrRow(ym, m, tot, max, avg, on) {
  const segs = ROOTS().filter(r => m[r] > 0).sort((a, b) => m[b] - m[a]);
  return `<button class="lg-tr${on ? ' is-on' : ''}" type="button" onclick="lgGo('${ym}')"><span class="lg-mo">${lgMo(ym)}</span><span class="lg-tb"><span class="lg-bar2" style="width:${(tot / max * 100).toFixed(1)}%">${segs.map(r => `<i style="flex:${m[r]} 1 0;background:${esc(catColor(r))}"></i>`).join('')}</span><i class="lg-avg" style="left:${(avg / max * 100).toFixed(1)}%"></i></span><span class="lg-tv num">${yen(tot)}</span></button>`;
}
/** 内訳の行(軸は lgAxis)。xs = その月の支出 */
function lgRow(label, v, tot, ax, k, color, kid) {
  const on = !!lgFilter && lgFilter.ax === ax && lgFilter.k === k;
  return `<button class="lg-row${kid ? ' is-kid' : ''}${on ? ' is-on' : ''}" type="button" aria-pressed="${on}" onclick="lgPick('${ax}','${esc(k)}')"><span class="lg-sq" style="--cc:${esc(color)}"></span><span class="lg-rn">${esc(label)}</span><span class="lg-meter"><i style="width:${lgPct(v, tot)}%;background:${esc(color)}"></i></span><span class="lg-rv num">${yen(v)}<small>${lgPct(v, tot)}%</small></span></button>`;
}
function lgRows(xs, tot) {
  const out = [];
  const sumOf = k => xs.reduce((a, [, x]) => a + xAmtIn(x, k, 1), 0);
  const where = p => xs.reduce((a, [, x]) => a + (p(x) ? xAmt(x) : 0), 0);
  if (lgAxis === 'cat') {
    ROOTS().filter(r => sumOf(r) > 0).sort((a, b) => sumOf(b) - sumOf(a)).forEach(r => {
      out.push(lgRow(CATS[r].n, sumOf(r), tot, 'cat', r, catColor(r)));
      if (lgFilter && lgFilter.ax === 'cat' && catAnc(lgFilter.k)[0] === r)
        childrenOf(r).filter(c => sumOf(c) > 0).sort((a, b) => sumOf(b) - sumOf(a))
          .forEach(c => out.push(lgRow(CATS[c].n, sumOf(c), tot, 'cat', c, catColor(c), true)));
    });
  } else if (lgAxis === 'fx') {
    const fx = where(x => xFx(x) === '固定'), vr = where(x => xFx(x) === '変動'), un = tot - fx - vr;
    out.push(lgRow('固定', fx, tot, 'fx', '固定', 'var(--tx-2)'), lgRow('変動', vr, tot, 'fx', '変動', 'var(--axis)'));
    if (un > 0) out.push(lgRow('未指定', un, tot, 'fx', '未指定', 'var(--line-strong)'));
  } else {
    const dl = where(x => xSp(x) === '日常'), nd = where(x => xSp(x) === '非日常'), un = tot - dl - nd;
    out.push(lgRow('日常', dl, tot, 'sp', '日常', 'var(--axis)'), lgRow('非日常', nd, tot, 'sp', '非日常', 'var(--ev)'));
    if (un > 0) out.push(lgRow('未設定', un, tot, 'sp', '未指定', 'var(--line-strong)'));
  }
  return out;
}
/** 支出の1件 = 半券1枚。左=特別感、本体=題・日付・支払先・カテゴリ、右=金額だけ */
function lgTicket(id, x) {
  const p = A[x.pay], full = xAmt(x), byCat = !!lgFilter && lgFilter.ax === 'cat';
  const cut = byCat ? xAmtIn(x, lgFilter.k, 1) : full, rest = p.amt - full, b = balanceOf(p);
  const shop = shopOf(x), title = xTitle(x);
  const cats = byCat ? [lgFilter.k] : xCats(x).map(g => g.cat);
  const sub = `<span>${esc(fmtMDW(lgXDate(x)))}${title !== shop ? ` · ${esc(shop)}` : ''}</span>`
    + cats.map(c => catTag(c)).join('')
    + (cut !== full ? tag(`支出 ${yen(full)}`) : '')
    + (p.amt !== full ? tag(`支払い ${yen(p.amt)}`) : '')
    + (rest > 0 ? payBar(x.pay) : '')
    + (b.open ? tag(balanceText(b), {kind: 'owe'}) : '');
  return ticket({href: `#/x/${id}`, stub: xSp(x), title, sub, amt: yen(cut), size: 'sm'});
}

route('ledger', (id, q) => {
  const ym = LG_MONTHS.includes(q.m) ? q.m : LG_NOW, i = LG_MONTHS.indexOf(ym), isNow = ym === LG_NOW;
  if (lgFilterYm !== ym) { lgFilter = null; lgFilterYm = ym }
  const m = lgMap(ym), f = lgFigs(ym), xs = lgXsIn(ym), has = xs.length > 0;
  const prevYm = LG_MONTHS[i - 1], nextYm = LG_MONTHS[i + 1];
  const prevTot = prevYm ? lgFigs(prevYm).tot : 0;
  /* 前月比は今月だけ */
  const diff = isNow && prevTot ? Math.round((f.tot - prevTot) / prevTot * 100) : null;
  const roots = ROOTS().filter(r => m[r] > 0).sort((a, b) => m[b] - m[a]);

  const nav = `<div class="lg-nav">${periodNav(`${lgMo(ym)} ${ym.slice(0, 4)}`, {on: prevYm ? `lgGo('${prevYm}')` : '', dis: !prevYm}, {on: nextYm ? `lgGo('${nextYm}')` : '', dis: !nextYm})}</div>`;
  const bar = `<div class="lg-bar" role="img" aria-label="大分類の割合">${roots.map(r => `<i style="flex:${m[r]} 1 0;background:${esc(catColor(r))}"></i>`).join('')}</div>`;
  const head = `<div class="lg-head"><div class="lg-lbl">自分の支出</div><div class="lg-amt"><span class="lg-big num">${yen(f.tot)}</span>${diff != null ? tag(`前月比 ${diff > 0 ? '+' : ''}${diff}%`) : ''}</div>${bar}</div>`;
  /* まとめの数字: 固定費・変動費・非日常(未指定は >0 の時の4つ目) */
  const unfixed = f.tot - f.fixed - f.varia;
  const ms = (isNow || has) ? [
    {v: yen(f.fixed), l: '固定費', on: has ? `lgPick('fx','固定',1)` : ''},
    {v: yen(f.varia), l: '変動費', on: has ? `lgPick('fx','変動',1)` : ''},
    {v: yen(f.nonDaily), l: '非日常', on: has ? `lgPick('sp','非日常',1)` : ''},
    ...(isNow && unfixed > 0 ? [{v: yen(unfixed), l: '未指定', on: `lgPick('fx','未指定',1)`}] : [])
  ] : [];
  const actions = isNow ? [
    {label: '支払いを記録', icon: 'pay', primary: true, on: 'openPayment()'},
    {label: 'レシートから読み取る', icon: 'camera', on: 'openReceipt()'}
  ] : [];
  const sumHtml = `<section class="p-sum">${ms.length ? `<div class="p-sum-ms">${ms.map(lgMetric).join('')}</div>` : ''}${actions.length ? `<div class="p-sum-acts">${actions.map(lgAct).join('')}</div>` : ''}</section>`;
  const top = `<div class="lg-top">${head}${sumHtml}</div>`;

  /* 推移: 上=古い月・下=新しい月 */
  const tots = LG_MONTHS.map(y => lgTotal(lgMap(y)));
  const max = Math.max(1, ...tots), past = tots.slice(0, -1);
  const avg = past.length ? Math.round(past.reduce((a, b) => a + b, 0) / past.length) : 0;
  const chart = list([`<div class="lg-chart">${LG_MONTHS.map((y, j) => lgTrRow(y, lgMap(y), tots[j], max, avg, y === ym)).join('')}<div class="lg-avgl num">${past.length}か月平均 ${yen(avg)}</div></div>`]);

  /* 内訳・支出は、明細のある月だけ。過去の月で明細が無い時は推移とまとめだけ(帯で知らせる) */
  let body = '';
  if (!has && !isNow) body = banner('info', 'この月の明細は10月だけ表示しています。');
  else if (has) {
    const tot = f.tot;
    const ids = xs.filter(([, x]) => lgMatch(x))
      .sort((a, b) => lgXDate(a[1]).localeCompare(lgXDate(b[1])) || (A[a[1].pay].time || '').localeCompare(A[b[1].pay].time || ''));
    const clear = lgFilter ? `<div class="lg-clear">${chips([{k: 'x', label: `${LG_AX[lgFilter.ax]}: ${lgFilterName()} ×`}], [], 'lgClear')}</div>` : '';
    body = sh('内訳') + `<div class="lg-seg">${seg([{k: 'cat', label: 'カテゴリ'}, {k: 'fx', label: '固定・変動'}, {k: 'sp', label: '特別感'}], lgAxis, 'lgAxisSet')}</div>`
      + list(lgRows(xs, tot))
      + sh('支出', {count: `${ids.length}件`}) + clear
      + (ids.length ? `<div class="lg-tix">${ids.map(([k, x]) => lgTicket(k, x)).join('')}</div>` : empty({shape: 'ledger', text: '該当の支出 なし'}));
  }

  /* これからの固定費(今月だけ): 支払い予定のうち繰り返すもの。次回日の早い順 */
  let upcoming = '';
  if (isNow) {
    const ups = Object.entries(EV).filter(([, e]) => e.pay && e.rep).map(([k, e]) => [k, e, nextOn(e)]).sort((a, b) => a[2].localeCompare(b[2]));
    if (ups.length) upcoming = sh('これからの固定費', {count: `${ups.length}件`})
      + `<div class="lg-ax">${ups.map(([k, e, d]) => axRow({href: `#/ev/${k}`, time: fmtMD(d), node: 'pay', future: true, title: e.t, meta: esc(catPath(e.pay.cat)), amt: yen(e.pay.amt)})).join('')}</div>`;
  }

  return largeTitle('家計簿', {actions: [{label: 'カテゴリ', icon: 'cats', primary: true, on: "go('#/cats')"}]})
    + nav + top + sh('推移', {count: '6か月'}) + chart + body + upcoming;
}, {tab: 'ledger'});

/** まとめの数字の1つ(summaryCard と同じ見た目。4つ目を出すため自前) */
function lgMetric(m) {
  const inner = `<b class="num">${esc(m.v)}</b><span>${esc(m.l)}${m.on ? icon('chev', 'p-ic-xs') : ''}</span>`;
  return m.on ? `<button class="p-sum-m" type="button" onclick="${m.on}">${inner}</button>` : `<div class="p-sum-m">${inner}</div>`;
}
function lgAct(a) { return `<button class="p-btn ${a.primary ? 'p-btn-fill' : 'p-btn-tonal'} p-sum-btn" type="button" onclick="${a.on}">${icon(a.icon)}${esc(a.label)}</button>` }

/* ---------- 支出の詳細 ---------- */
/** 支出の編集(その他 › 編集)。カテゴリ・固定変動・特別感・メモを1つのシートで。保存は各操作+取り消し付き */
function lgEditBody(id) {
  const d = lgEd;
  return `<div class="lg-form">`
    + field('カテゴリ', select({options: Object.keys(CATS).map(k => ({v: k, l: catPath(k)})), value: d.cat, onchange: 'lgEd.cat=this.value'}))
    + `<div class="lg-fl"><span class="p-field-k">固定・変動</span>${lgPills([['', 'カテゴリの既定'], ['固定', '固定'], ['変動', '変動']], d.fx, `lgEdSet('fx','$v')`)}</div>`
    + `<div class="lg-fl"><span class="p-field-k">特別感</span>${lgPills([['', 'カテゴリの既定'], ['日常', '日常'], ['非日常', '非日常']], d.sp, `lgEdSet('sp','$v')`)}</div>`
    + field('メモ', input({value: d.memo, oninput: 'lgEd.memo=this.value'}))
    + `</div>`;
}
function lgEdSet(key, v) { lgEd[key] = v; setSheetBody(lgEditBody(lgEdId)) }
let lgEdId = null;
function lgEdit(id) {
  const x = X[id]; if (!x) return;
  lgEdId = id;
  lgEd = {cat: x.cat, fx: x.fx || '', sp: x.sp || '', memo: x.memo || ''};
  closeSheet(true);
  openSheet({title: '支出を編集', body: lgEditBody(id), primary: {label: '保存', on: () => lgEditSave(id)}});
}
function lgEditSave(id) {
  const x = X[id], d = lgEd, undos = [];
  const use = r => { if (r && r.ok) undos.push(r.undo); else if (r && !r.ok) toast(r.why) };
  if (d.cat !== x.cat) use(xSetCat(id, d.cat));
  if (d.fx !== (x.fx || '')) use(xSetFx(id, d.fx || null));
  if (d.sp !== (x.sp || '')) use(xSetSp(id, d.sp || null));
  if (d.memo !== (x.memo || '')) use(xSetMemo(id, d.memo));
  closeSheet(true);
  if (undos.length) toast('支出を直しました', {undo: () => { undos.slice().reverse().forEach(u => undoRun(u)); rerender() }});
  rerender();
}
function lgMenu(id) {
  openSheet({title: '操作', body: list([
    row({t: '編集', on: `lgEdit('${id}')`}),
    row({t: '支払いを開く', href: `#/a/${X[id].pay}`})
  ])});
}
/** カテゴリを選ぶシート(支出のカテゴリ) */
function lgCatPick(id) {
  const cur = X[id].cat;
  const item = k => row({on: `lgSetCat('${id}','${k}')`, lead: lgSq(k), t: CATS[k].n, s: catPath(k) !== CATS[k].n ? catPath(k) : '', right: k === cur ? '✓' : '', chev: false});
  openSheet({title: 'カテゴリを選ぶ', body: list(ROOTS().flatMap(r => [item(r), ...childrenOf(r).map(item)]))});
}
function lgSetCat(id, k) {
  const r = xSetCat(id, k);
  if (!r.ok) return toast(r.why);
  closeSheet(true); rerender();
  toast('カテゴリを変えました', {undo: () => { undoRun(r.undo); rerender() }});
}
function lgSetFx(id, v) {
  const r = xSetFx(id, v || null);
  if (!r.ok) return toast(r.why);
  rerender();
  toast('固定・変動を変えました', {undo: () => { undoRun(r.undo); rerender() }});
}
function lgSetSp(id, v) {
  const r = xSetSp(id, v || null);
  if (!r.ok) return toast(r.why);
  rerender();
  toast('特別感を変えました', {undo: () => { undoRun(r.undo); rerender() }});
}
function lgMemo(id, v) { xSetMemo(id, v) }
/** 品目のカテゴリ(既定と同じなら上書きを外す) */
function lgItemCatPick(id, i) {
  const x = X[id], cur = (x.itemCat && x.itemCat[i]) || x.cat;
  const item = k => row({on: `lgSetItemCat('${id}',${i},'${k}')`, lead: lgSq(k), t: CATS[k].n, s: catPath(k) !== CATS[k].n ? catPath(k) : '', right: k === cur ? '✓' : '', chev: false});
  openSheet({title: 'この品目のカテゴリ', body: list(ROOTS().flatMap(r => [item(r), ...childrenOf(r).map(item)]))});
}
function lgSetItemCat(id, i, k) {
  const x = X[id], r = xItemCatSet(id, i, k === x.cat ? null : k);
  if (!r.ok) return toast(r.why);
  closeSheet(true); rerender();
  toast('この品目のカテゴリを変えました', {undo: () => { undoRun(r.undo); rerender() }});
}
/** 品目の編集の一覧(行 = 金額行・税率を直す品目の編集へ。detail の品目の編集シート) */
function lgItemList(id) {
  const x = X[id], p = A[x.pay], its = (p && p.items) || [];
  openSheet({title: '品目', body: list(its.map((it, i) => row({on: `lgItemEdit('${x.pay}',${i})`, t: it.n, s: `税${itemTax(it)}% · ${usersText(it)}`, right: yen(itemAmt(it)), chev: true})))});
}
function lgItemEdit(aid, i) { closeSheet(true); dtItemSheet(aid, i) }
function lgConfirmRead(aid) {
  const r = readMarkConfirm(aid);
  if (!r.ok) return toast(r.why);
  rerender();
  toast('読み取った値を確定しました', {undo: () => { undoRun(r.undo); rerender() }});
}

route('x', id => {
  const x = X[id];
  if (!x || !A[x.pay]) return appbar('自分の支出') + empty({shape: 'ledger', text: '支出 なし'});
  const p = A[x.pay], marks = readMarkCount(x.pay);
  const pay = `<div class="lg-top">${payShell(x.pay, {mode: 'x', xid: id})}</div>`;
  const note = marks ? banner('info', `読み取った値が ${marks} 件 未確認です。<button class="lg-link" type="button" onclick="lgConfirmRead('${x.pay}')">確定する</button>`) : '';
  const catRow = prop('カテゴリ', catTag(x.cat), {html: true, on: `lgCatPick('${id}')`});
  const fxRow = prop('固定・変動', lgPills([['', '既定'], ['固定', '固定'], ['変動', '変動']], x.fx || '', `lgSetFx('${id}','$v')`) + (x.fx !== undefined ? ` ${tag('この支出だけ')}` : (catFx(x.cat) === null ? ` ${tag('都度指定')}` : '')), {html: true});
  const spRow = prop('特別感', lgPills([['', '既定'], ['日常', '日常'], ['非日常', '非日常']], x.sp || '', `lgSetSp('${id}','$v')`) + (x.sp !== undefined ? ` ${tag('この支出だけ')}` : (catSp(x.cat) === null ? ` ${tag('都度指定')}` : '')), {html: true});
  const memoRow = prop('メモ', `<input class="p-input lg-memo" value="${esc(x.memo || '')}" placeholder="メモを書く" oninput="lgMemo('${id}',this.value)">`, {html: true});
  const items = xItems(x);
  const itemRows = items.length ? items.map(o => row({on: `lgItemCatPick('${id}',${o.i})`, lead: lgSq(o.cat), t: o.n, s: `${o.b} · 税${itemTax(p.items && p.items[o.i])}%${o.cat !== x.cat ? ` · ${catPath(o.cat)}` : ''}`, right: yen(o.price), chev: true})) : [];
  const basisRow = row({t: `自分の負担 · ${BASIS[x.basis] || '使った分'}`, s: x.basis === 'even' ? `${yen(payTotal(p))} ÷ ${x.n || 2}人` : x.basis === 'amount' ? '金額を指定' : '使った品目の合計', right: yen(xAmt(x)), on: `dtBasisOpen('${x.pay}')`, chev: true});
  return appbar('自分の支出', {actions: [{icon: 'more', label: 'その他の操作', on: `lgMenu('${id}')`}]})
    + pay + note
    + sh('分類') + list([catRow, fxRow, spRow, memoRow])
    + (xCats(x).length > 1 ? sh('カテゴリの内訳') + list(xCats(x).map(g => row({lead: lgSq(g.cat), t: catPath(g.cat), right: yen(g.amt), chev: false}))) : '')
    + sh('品目', {count: items.length, act: items.length ? {label: '編集', on: `lgItemList('${id}')`} : undefined}) + (items.length ? list([...itemRows, basisRow]) : empty({shape: 'list', text: '品目 なし'}))
    ;
}, {tab: 'ledger'});

/* ---------- カテゴリの木 ---------- */
function lgDefs(k) {
  const f = catFx(k), sp = catSp(k), parts = [f || '固定・変動は都度指定'];
  if (sp === '非日常') parts.push('非日常');
  return parts.join('、');
}
route('cats', () => {
  const xs = lgXsIn(LG_NOW);
  const sumOf = k => xs.reduce((a, [, x]) => a + xAmtIn(x, k, 1), 0);
  const node = k => {
    const kids = childrenOf(k);
    return `<div class="lg-node">${row({on: `lgOpenCat('${k}')`, lead: lgSq(k), t: CATS[k].n, s: lgDefs(k), right: yen(sumOf(k)), chev: true})}${kids.length ? `<div class="lg-kids">${kids.map(node).join('')}</div>` : ''}</div>`;
  };
  return appbar('カテゴリ', {actions: [{icon: 'plus', label: 'カテゴリを作成', on: 'lgNewCat()'}]})
    + sh('すべて', {count: Object.keys(CATS).length})
    + `<div class="p-list lg-tree">${ROOTS().map(node).join('')}</div>`;
}, {tab: 'ledger'});

/** カテゴリの編集・作成のシート。fx/sp は 'inh'(親から・子のみ) / '固定' / '変動' / 'ask'(都度指定) */
function lgCatBody() {
  const d = lgDraft, isNew = !lgDraftKey, isKid = !!d.p;
  const parentOpts = [{v: '', l: 'いちばん上'}, ...Object.keys(CATS).filter(c => !d.ban.includes(c)).map(c => ({v: c, l: catPath(c)}))];
  const sw = COLORS.map(c => `<button type="button" class="lg-sw" style="--c:${esc(c)}" aria-label="色 ${esc(c)}" aria-pressed="${d.c === c}" onclick="lgDraftSet('c','${esc(c)}')"></button>`).join('');
  const fxItems = [...(isKid ? [['inh', '親から']] : []), ['固定', '固定'], ['変動', '変動'], ['ask', '都度指定']];
  const spItems = [...(isKid ? [['inh', '親から']] : []), ['日常', '日常'], ['非日常', '非日常'], ['ask', '都度指定']];
  const fxCur = d.fx === 'inh' && !isKid ? 'ask' : d.fx, spCur = d.sp === 'inh' && !isKid ? 'ask' : d.sp;
  const kids = !isNew && childrenOf(lgDraftKey).length > 0;
  const used = !isNew && Object.values(X).some(x => catDesc(lgDraftKey).includes(x.cat));
  const blocked = kids || used;
  return `<div class="lg-form">`
    + field('名前', input({value: d.n, ph: '例: 外食', oninput: 'lgDraft.n=this.value'}))
    + field('親', select({options: parentOpts, value: d.p, onchange: "lgDraftSet('p',this.value)"}))
    + (isKid ? '' : `<div class="lg-fl"><span class="p-field-k">色</span><div class="lg-sws">${sw}</div></div>`)
    + `<div class="lg-fl"><span class="p-field-k">固定・変動</span>${lgPills(fxItems, fxCur, "lgDraftSet('fx','$v')")}</div>`
    + `<div class="lg-fl"><span class="p-field-k">特別感</span>${lgPills(spItems, spCur, "lgDraftSet('sp','$v')")}</div>`
    + `<p class="lg-hint">都度指定: このカテゴリは既定値を持たず、支出を入れる時に毎回選びます。</p>`
    + (isNew ? '' : `<button class="p-btn p-btn-tonal lg-del" type="button" ${blocked ? 'disabled' : ''} onclick="lgDelCat()">${icon('trash')}カテゴリを削除</button>`
      + (blocked ? `<p class="lg-hint">${kids ? '子カテゴリがあるので削除できません' : '使われているので削除できません'}</p>` : ''))
    + `</div>`;
}
function catDesc(k) { return [k, ...childrenOf(k).flatMap(catDesc)] }
function lgDraftSet(key, v) { lgDraft[key] = v; setSheetBody(lgCatBody()) }
function lgOpenCat(k) {
  const c = CATS[k];
  lgDraftKey = k;
  lgDraft = {n: c.n, p: c.p || '', c: c.c || COLORS[0], fx: c.fx === 'none' ? 'ask' : c.fx || 'inh', sp: c.sp === 'none' ? 'ask' : c.sp || 'inh', ban: catDesc(k)};
  lgDraftSnap = JSON.stringify(lgDraft);
  openSheet({title: 'カテゴリを編集', body: lgCatBody(), primary: {label: '保存', on: () => lgSaveCat()}, dirty: () => JSON.stringify(lgDraft) !== lgDraftSnap});
}
function lgNewCat() {
  lgDraftKey = null;
  lgDraft = {n: '', p: '', c: COLORS[0], fx: 'inh', sp: 'inh', ban: []};
  lgDraftSnap = JSON.stringify(lgDraft);
  openSheet({title: 'カテゴリを作成', body: lgCatBody(), primary: {label: '作成', on: () => lgSaveCat()}, dirty: () => JSON.stringify(lgDraft) !== lgDraftSnap});
}
function lgSaveCat() {
  const d = lgDraft, n = (d.n || '').trim();
  if (!n) return toast('名前を入れてください');
  const isNew = !lgDraftKey, key = lgDraftKey || uid('cat'), o = {n};
  if (d.p) o.p = d.p; else o.c = d.c;
  const fx = d.fx === 'inh' && !d.p ? 'ask' : d.fx, sp = d.sp === 'inh' && !d.p ? 'ask' : d.sp;
  if (fx !== 'inh') o.fx = fx;
  if (sp !== 'inh') o.sp = sp;
  CATS[key] = o;
  save(); closeSheet(true); rerender();
  toast(`カテゴリを${isNew ? '作成' : '保存'}しました`);
}
function lgDelCat() {
  const k = lgDraftKey, c = CATS[k];
  if (!c) return;
  if (childrenOf(k).length || Object.values(X).some(x => catDesc(k).includes(x.cat))) return toast('使われているので削除できません');
  delete CATS[k];
  save(); closeSheet(true); rerender();
  toast(`「${c.n}」を削除しました`, {undo: () => { CATS[k] = c; save(); rerender() }});
}
