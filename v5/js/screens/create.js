/* =====================================================================
   作成(画面担当 S5)— 「支払いを記録」(手で入力・レシートから読み取る)と「出来事」。
   接頭辞 cr_。ルートは持たない。addCreate で ＋ の一覧に登録する。
   お金は makePayment(money.js) だけを通る。読み取りは固定のサンプル(モック。実際の OCR はしない)。
   レシート: 1枚のシートの中で「一覧(cr_viewBatch)」と「1枚の確認(cr_payBody)」を切り替える。
   ===================================================================== */

/** 読み取りのサンプル(モック)。4件目は1件目と同じ写真(同じ id = 同じハッシュ) */
const CR_SAMPLES = [
  {id: 'super', shop: 'スーパー青葉', date: '2026-10-15', time: '18:20', total: 3120, cat: 'cook',
    items: [{n: '野菜', price: 1080}, {n: '牛乳', price: 238}, {n: 'パン', price: 198}, {n: '洗剤', price: 1604}]},
  {id: 'drug', shop: null, date: '2026-10-08', time: null, total: 980, cat: 'daily',
    items: [{n: '日用品', price: 980}]},
  {id: 'cafe', shop: 'カフェ ミナト', date: '2026-10-20', time: '09:15', total: 540, cat: 'cafe',
    items: [{n: 'カフェラテ', price: 480}, {n: '焼き菓子', price: 50}]},
  {id: 'super', shop: 'スーパー青葉', date: '2026-10-15', time: '18:20', total: 3120, cat: 'cook',
    items: [{n: '野菜', price: 1080}, {n: '牛乳', price: 238}, {n: 'パン', price: 198}, {n: '洗剤', price: 1604}]}
];
const CR_STATUS = {reading: '読み取り中', ready: '確認待ち', confirmed: '確認済み', dup: '重複の疑い', skip: 'スキップ', saved: '保存済み'};
let crPay = null, crPaySnap = '', crRc = null, crIdx = 0, crView = '', crEv = null, crEvSnap = '', crIt = null, crItSnap = '';

/* ---------- 小道具 ---------- */
const cr_toM = t => { const [h, m] = String(t).split(':').map(Number); return h * 60 + m };
const cr_people = () => ['me', ...Object.keys(E).filter(k => k !== 'me' && (E[k].t || []).includes('person'))];
const cr_shopList = () => Object.keys(E).filter(k => k !== 'me' && !(E[k].t || []).includes('person')).map(k => E[k].n);
const cr_entId = name => { const n = (name || '').trim(); return n ? (Object.keys(E).find(k => E[k].n === n) || null) : null };
const cr_dayFind = date => Object.keys(A).find(k => A[k].type === '日記' && A[k].date === date) || null;
const cr_dayEnsure = date => {
  const k = cr_dayFind(date);
  if (k) return k;
  const nk = 'd' + date.slice(5).replace('-', '');
  A[nk] = {type: '日記', date, dd: String(+date.slice(8)), dw: dowJ(date), body: '', kids: []};
  return nk;
};
/** 日付の期間の活動(活動の中の選択肢) */
const cr_spans = date => Object.entries(A).filter(([, a]) => a.type !== '日記' && a.parent && isSpan(a) && dateOf(a) === date);
/** 時刻を含む一番内側の期間(既定の活動) */
const cr_defaultAct = (date, time) => {
  const hit = cr_spans(date).filter(([, a]) => time && a.time <= time && time <= a.end).sort((p, q) => (q[1].time || '').localeCompare(p[1].time || ''));
  return hit.length ? hit[0][0] : '';
};
const cr_actOptions = date => [{v: '', l: 'なし(日の直下)'}, ...cr_spans(date).map(([k, a]) => ({v: k, l: `${a.title} ${a.time}–${a.end}`}))];
const cr_total = p => p.items.reduce((s, i) => s + (+i.price || 0), 0);
const cr_payers = p => p.payers && p.payers.length ? p.payers : ['me'];
const cr_needSplit = p => cr_payers(p).some(w => w !== 'me') || p.items.some(i => (i.users || ['me']).some(u => u !== 'me'));
/** 自分の負担の見積もり(保存前。makePayment と同じ式) */
function cr_mine(p) {
  const total = cr_total(p), n = Math.max(1, p.n || 2);
  if (p.basis === 'even') return Math.floor(total / n);
  if (p.basis === 'amount') return Math.max(0, Math.min(p.me || 0, total));
  return p.items.reduce((s, i) => { const u = i.users || ['me']; return s + (u.includes('me') ? Math.floor((+i.price || 0) / Math.max(1, u.length)) : 0) }, 0);
}
/** 自分が払った額 */
function cr_myPaid(p) {
  const total = cr_total(p), ws = cr_payers(p);
  if (ws.length === 1) return ws[0] === 'me' ? total : 0;
  return ws.includes('me') ? (+p.payAmt.me || 0) : 0;
}
/** 貸し借り(保存前の計算。balanceText と同じ文) */
function cr_balance(p) {
  const amt = cr_myPaid(p) - cr_mine(p);
  const names = amt > 0
    ? [...new Set(p.items.flatMap(i => i.users || ['me']).filter(u => u !== 'me'))].map(u => whoShort(u))
    : cr_payers(p).filter(w => w !== 'me').map(w => whoShort(w));
  return {amt, names, settled: false};
}
/** 重複: 同じ写真(ハッシュ)、同じ日・同じ金額・5分以内(保存済みと一覧の確認済み) */
function cr_dupOf(p, others) {
  const hashes = EXTRA.hashes || {};
  if (p.hash && hashes[p.hash]) return {why: '同じ写真を保存済み'};
  const same = (others || []).findIndex(o => o !== p && o.hash && o.hash === p.hash && o.status === 'confirmed');
  if (same >= 0) return {why: `同じ写真が${same + 1}枚目にあります`};
  const total = cr_total(p), near = q => q.time && p.time && Math.abs(cr_toM(q.time) - cr_toM(p.time)) <= 5;
  if (Object.keys(A).some(k => A[k].type === '支払い' && payTotal(A[k]) === total && (dayOf(A[k]) || {}).date === p.date && near(A[k]))) return {why: '同じ日時・金額の支払いあり'};
  if ((others || []).some(o => o !== p && o.status === 'confirmed' && o.date === p.date && cr_total(o) === total && near(o))) return {why: '同じ日時・金額の読み取りあり'};
  return null;
}
const cr_todoKeys = p => [...new Set(p.items.map(i => plShopTodoFor(i.n)).filter(Boolean))];
/** 写真のハッシュ(SHA-256。使えない時は大きさと名前) */
async function cr_hash(file) {
  try {
    const d = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
    return Array.from(new Uint8Array(d)).map(b => b.toString(16).padStart(2, '0')).join('');
  } catch (e) { return `size:${file.size}:${file.name}` }
}
/** 開いている一番上のシートの題 */
function cr_title(t) { const els = document.querySelectorAll('#sheets .sheet'); const h = els.length && els[els.length - 1].querySelector('.sheet-t'); if (h) h.textContent = t }

/* ---------- 支払いを記録(手で入力) ---------- */
function openPayment(o = {}) {
  crRc = null; crView = 'manual';
  crPay = {mode: 'manual', date: o.date || TODAY, time: NOW, shop: '', regShop: false, how: PAY_METHODS[0], act: null, cat: 'eatout', fx: null, sp: null,
    items: [{n: '', price: o.amt > 0 ? +o.amt : 0, users: ['me']}], payers: ['me'], payAmt: {}, basis: 'items', n: 2, me: 0, unread: null, memo: '',
    dupReason: null, dupOk: false, status: 'ready', todoDone: false};
  crPaySnap = JSON.stringify(crPay);
  openSheet({title: '支払いを記録', body: cr_payBody(), primary: {label: '支払いを保存', on: () => cr_save()}, dirty: () => JSON.stringify(crPay) !== crPaySnap});
}
/** 今の主ボタン(一覧・1枚の確認・手入力で違う) */
function cr_primary() {
  if (crView === 'batch') {
    const k = crRc.filter(r => r.pay.status === 'confirmed').length;
    return {label: `${k}件の支払いを保存`, on: () => crBatchSave(), disabled: k === 0};
  }
  const p = crPay, bad = p.mode === 'rc' && p.printed != null && cr_total(p) !== p.printed;
  if (crView === 'review' && crRc.length > 1) return {label: '確認済みにする', on: () => crConfirmRow(crIdx), disabled: bad};
  return {label: '支払いを保存', on: () => cr_save(), disabled: bad};
}
/** 今の表示を描き直す */
function cr_redraw() {
  if (crView === 'batch') return cr_viewBatch();
  setSheetBody(cr_payBody());
  setSheetPrimary(cr_primary());
}
/** 入力欄の値(再描画しない) */
function crField(k, v) {
  const p = crPay;
  if (k === 'amt') { if (p.items.length === 1) p.items[0].price = Math.max(0, parseInt(v, 10) || 0); const t = document.getElementById('cr_tot'); if (t) t.textContent = yen(cr_total(p)) }
  else if (k === 'shop') p.shop = v;
  else if (k === 'memo') p.memo = v;
  p.dupReason = null; p.dupOk = false;
}
/** 選ぶ・切り替える(再描画) */
function crSet(k, v) {
  const p = crPay;
  if (k === 'how') p.how = v;
  else if (k === 'act') p.act = v;
  else if (k === 'cat') { p.cat = v; if (catFx(v) !== null) p.fx = null; if (catSp(v) !== null) p.sp = null }
  else if (k === 'fx') p.fx = v || null;
  else if (k === 'sp') p.sp = v || null;
  else if (k === 'n') p.n = Math.max(2, parseInt(v, 10) || 2);
  else if (k === 'me') p.me = Math.max(0, parseInt(v, 10) || 0);
  else if (k === 'date') { p.date = v; p.act = null }
  else if (k === 'time') p.time = v;
  p.dupReason = null; p.dupOk = false;
  cr_redraw();
}
function crRoot(r) { crSet('cat', r) }
function crShopPick(name) { crPay.shop = name; crPay.regShop = false; cr_redraw() }
function crRegToggle() { crPay.regShop = !crPay.regShop; cr_redraw() }
/** 払った人(複数選べる)。1人は全額、複数は割って入れ、あとで直せる */
function crPayerToggle(w) {
  const p = crPay, ws = cr_payers(p).slice(), i = ws.indexOf(w);
  if (i >= 0) { if (ws.length > 1) ws.splice(i, 1) } else ws.push(w);
  p.payers = ws;
  const total = cr_total(p), each = Math.floor(total / ws.length);
  p.payAmt = {};
  ws.forEach((x, k) => { p.payAmt[x] = k === 0 ? total - each * (ws.length - 1) : each });
  cr_redraw();
}
function crPayAmt(w, v) { crPay.payAmt[w] = Math.max(0, parseInt(v, 10) || 0); const t = document.getElementById('cr_paysum'); if (t) t.textContent = yen(Object.values(crPay.payAmt).reduce((s, x) => s + (+x || 0), 0)) }
function crBasis(v) { crPay.basis = v; crPay.dupReason = null; cr_redraw() }
function crItemAdd() { crPay.items.push({n: '', price: 0, users: ['me']}); cr_redraw() }
function crItemDel(i) { if (crPay.items.length > 1) { crPay.items.splice(i, 1); cr_redraw() } }
function crItemName(i, v) { crPay.items[i].n = v; crPay.dupReason = null }
function crItemPrice(i, v) { const it = crPay.items[i]; it.price = Math.max(0, parseInt(v, 10) || 0); delete it.lines; const t = document.getElementById('cr_tot'); if (t) t.textContent = yen(cr_total(crPay)); setSheetPrimary(cr_primary()) }
function crItemUser(i, w) { toggleUser(crPay.items[i], w); cr_redraw() }
/** 読み取りの印を外す(amt・payee・品目の番号) */
function crUnmark(k) { if (k === 'amt' || k === 'payee') crPay.unread[k] = 0; else crPay.items[+k].unread = 0; cr_redraw() }
function crAllConfirm() { crPay.unread = {}; crPay.items.forEach(i => { i.unread = 0 }); cr_redraw() }
function crTodoToggle(k) { crPay.todoDone = !crPay.todoDone; cr_redraw() }

/* ---------- 品目の金額行・税率(detail の品目の編集と同じ形) ---------- */
function crItemSheet(i) {
  const it = crPay.items[i];
  const lines = it.lines && it.lines.length > 1 ? it.lines.map(l => ({n: l.n || '', k: l.k || 'amt', neg: lnV(l) < 0, v: Math.abs(lnV(l))}))
    : [{n: '', k: 'amt', neg: false, v: it.price || 0}];
  crIt = {i, n: it.n || '', tax: String(itemTax(it)), users: (it.users || ['me']).slice(), lines};
  crItSnap = JSON.stringify(crIt);
  openSheet({title: '品目', full: true, body: cr_itBody(), primary: {label: '適用', on: () => crItApply()}, dirty: () => JSON.stringify(crIt) !== crItSnap});
}
const cr_itSigned = () => crIt.lines.map(l => ({n: l.n, k: l.k, v: l.neg ? -l.v : l.v}));
const cr_itPrice = () => { const sg = cr_itSigned(); return itemAmt({lines: sg.length > 1 ? sg : undefined, price: sg[0].v}) };
function cr_itBody() {
  return `<div class="cr-form">`
    + field('品名', input({value: crIt.n, oninput: 'crIt.n=this.value'}))
    + field('本体の値段', input({type: 'number', value: crIt.lines[0].v || '', ph: '¥0', oninput: 'crItSetV(0,this.value)'}))
    + crIt.lines.slice(1).map((l, j0) => { const j = j0 + 1; return `<div class="cr-box">
        <div class="cr-boxh">${select({options: [{v: 'a+', l: '＋円'}, {v: 'a-', l: '−円'}, {v: 'p+', l: '＋%'}, {v: 'p-', l: '−%'}], value: (l.k === 'pct' ? 'p' : 'a') + (l.neg ? '-' : '+'), onchange: `crItKind(${j},this.value)`})}
          <button class="p-ibtn" type="button" aria-label="この行を消す" onclick="crItLineDel(${j})">${icon('trash')}</button></div>
        ${field('名前', input({value: l.n, oninput: `crIt.lines[${j}].n=this.value`}))}
        ${field('値', input({type: 'number', value: l.v || '', ph: '0', oninput: `crItSetV(${j},this.value)`}))}</div>`; }).join('')
    + `<button class="p-btn p-btn-tonal" type="button" onclick="crItLineAdd()">${icon('plus')}オプション・割引を追加</button>`
    + `<div id="cr-ieq">${cr_itEq()}</div>`
    + `<div class="cr-fl"><span class="p-field-k">税率</span>${seg([{k: '8', label: '税8%'}, {k: '10', label: '税10%'}], crIt.tax, 'crItTax')}</div>`
    + (cr_people().length > 1 ? `<div class="cr-fl"><span class="p-field-k">使った人</span>${whoChips(cr_people(), crIt.users, 'crItUser($v)', '使った人')}</div>` : '')
    + (crPay.items.length > 1 ? `<button class="p-btn p-btn-tonal" type="button" onclick="crItDel()">${icon('trash')}この品目を消す</button>` : '')
    + `</div>`;
}
function cr_itEq() {
  const sg = cr_itSigned(), am = lnAmts(sg);
  return list([row({t: '本体', right: yen(am[0]), chev: false}),
    ...sg.slice(1).map((l, j) => row({t: lnName(l), right: (am[j + 1] < 0 ? '−' : '＋') + yen(Math.abs(am[j + 1])), chev: false})),
    row({t: '合計', right: yen(cr_itPrice()), chev: false})]);
}
function crItSetV(j, v) { crIt.lines[j].v = +String(v).replace(/[^\d]/g, '') || 0; const e = document.getElementById('cr-ieq'); if (e) e.innerHTML = cr_itEq() }
function crItKind(j, key) { const l = crIt.lines[j]; l.k = key[0] === 'p' ? 'pct' : 'amt'; l.neg = key[1] === '-'; setSheetBody(cr_itBody()) }
function crItLineAdd() { crIt.lines.push({n: '', k: 'amt', neg: false, v: 0}); setSheetBody(cr_itBody()) }
function crItLineDel(j) { crIt.lines.splice(j, 1); setSheetBody(cr_itBody()) }
function crItTax(v) { crIt.tax = v; setSheetBody(cr_itBody()) }
function crItUser(w) { toggleUser(crIt, w); setSheetBody(cr_itBody()) }
function crItDel() { if (crPay.items.length > 1) { crPay.items.splice(crIt.i, 1); closeSheet(true); cr_redraw() } }
function crItApply() {
  const sg = cr_itSigned(), price = cr_itPrice(), name = (crIt.n || '').trim();
  if (!name || price <= 0) return toast('品名と金額を入れてください');
  const it = crPay.items[crIt.i];
  it.n = name; it.price = price; it.tax = +crIt.tax || TAX_DEFAULT; it.users = crIt.users.slice();
  const ln = lnSave({lines: sg}); if (ln) it.lines = ln; else delete it.lines;
  closeSheet(true);
  cr_redraw();
}

/* ---------- 入力欄(手入力・1枚の確認で共通) ---------- */
function cr_itemRow(p, i, it, people, rc) {
  const mark = rc && it.unread ? readMark({label: '読み取り', on: `crUnmark(${i})`}) : '';
  const users = people.length > 1 ? `<div class="cr-pu">${whoChips(people, it.users || ['me'], `crItemUser(${i},'$v')`, '使った人')}</div>` : '';
  return `<div class="cr-pitem">`
    + `<div class="cr-pitem-r"><input class="p-input cr-name${!it.n && rc ? ' is-empty' : ''}" value="${esc(it.n || '')}" placeholder="品名" oninput="crItemName(${i},this.value)">`
    + `<input class="p-input cr-price num" type="number" inputmode="numeric" value="${it.price || ''}" placeholder="¥0" oninput="crItemPrice(${i},this.value)">`
    + `<button class="p-btn p-btn-tonal cr-tx" type="button" onclick="crItemSheet(${i})">税・金額</button>`
    + (p.items.length > 1 ? `<button class="p-ibtn" type="button" aria-label="この品目を消す" onclick="crItemDel(${i})">${icon('close')}</button>` : '')
    + `</div><div class="cr-pmeta">${mark}<span class="cr-tax num">税${itemTax(it)}%${it.lines ? ' · 金額行あり' : ''}</span></div>${users}</div>`;
}
/** 支払いの入力欄の中身(手入力・1枚の確認で共通) */
function cr_payBody() {
  const p = crPay, people = cr_people(), rc = p.mode === 'rc', total = cr_total(p);
  const act = p.act == null ? cr_defaultAct(p.date, p.time) : p.act;
  const root = catAnc(p.cat)[0], subs = childrenOf(root);
  const warns = [];
  if (rc && p.printed != null && total !== p.printed) warns.push(`印字 ${yen(p.printed)} / 差 ${yen(Math.abs(p.printed - total))}。品目を直すと保存できます`);
  if (rc && !p.shop) warns.push('店は読めませんでした。入れてください');
  if (rc && !p.time) warns.push('時刻は読めませんでした。入れてください');
  const ask = [];
  if (catFx(p.cat) === null) ask.push(`<div class="cr-fl"><span class="p-field-k">固定・変動</span>${lgPills([['固定', '固定'], ['変動', '変動']], p.fx || '', `crSet('fx','$v')`)}</div>`);
  if (catSp(p.cat) === null) ask.push(`<div class="cr-fl"><span class="p-field-k">特別感</span>${lgPills([['日常', '日常'], ['非日常', '非日常']], p.sp || '', `crSet('sp','$v')`)}</div>`);
  const payers = cr_payers(p), multi = payers.length > 1;
  const payHtml = `<div class="cr-fl"><span class="p-field-k">払った人</span>${whoChips(people, payers, 'crPayerToggle($v)', '払った人')}</div>`
    + (multi ? `<div class="cr-split">${payers.map(w => field(whoName(w), input({type: 'number', value: p.payAmt[w] || 0, oninput: `crPayAmt('${w}',this.value)`}))).join('')}<p class="cr-mine">払った額の合計 <b class="num" id="cr_paysum">${yen(Object.values(p.payAmt).reduce((s, x) => s + (+x || 0), 0))}</b> / ${yen(total)}</p></div>` : '');
  const mine = cr_mine(p), bal = cr_balance(p);
  const split = cr_needSplit(p) ? `<div class="cr-split"><span class="p-field-k">負担の決め方</span>${seg([{k: 'items', label: '使った分'}, {k: 'even', label: '割り勘'}, {k: 'amount', label: '金額を指定'}], p.basis, 'crBasis')}`
    + (p.basis === 'even' ? field('人数', input({type: 'number', value: p.n, onchange: `crSet('n',this.value)`})) : '')
    + (p.basis === 'amount' ? field('自分の負担', input({type: 'number', value: p.me, onchange: `crSet('me',this.value)`})) : '')
    + `<p class="cr-mine">自分の負担 <b class="num">${yen(mine)}</b></p>${bal.amt ? `<p class="cr-mine">${esc(balanceText(bal))}</p>` : ''}</div>` : '';
  const ticketHtml = `<div class="cr-tk">${ticket({stub: p.sp || catSp(p.cat), title: '自分の支出', sub: catTag(p.cat), amt: yen(mine), size: 'sm'})}</div>`;
  const shopChips = `<div class="cr-scroll cr-chips">${cr_shopList().slice(0, 8).map(n => `<button class="p-chip" type="button" aria-pressed="${p.shop === n}" onclick="crShopPick('${esc(n)}')">${esc(n)}</button>`).join('')}</div>`;
  const regShop = p.shop && !cr_entId(p.shop) ? `<div class="cr-chips"><button class="p-chip" type="button" aria-pressed="${!!p.regShop}" onclick="crRegToggle()">「${esc(p.shop)}」を店として登録</button></div>` : '';
  const todo = cr_todoKeys(p).map(k => banner('info', `買い物リストの「${esc(TD[k].t)}」がこの支払いにあります <button class="lg-link" type="button" onclick="crTodoToggle()">${p.todoDone ? '完了を取り消す' : '完了にする'}</button>`)).join('');
  const dup = p.dupReason ? banner('warn', `${esc(p.dupReason)}。<button class="lg-link" type="button" onclick="crDupKeep()">${crView === 'review' && crRc && crRc.length > 1 ? '別の支払いとして残す' : '重複ではないとして保存'}</button>`) : '';
  const top = rc
    ? (crRc && crRc.length > 1 ? `<button class="p-btn p-btn-tonal cr-top" type="button" onclick="cr_viewBatch()">${icon('back')}一覧に戻る</button>` : '')
    : `<button class="p-btn p-btn-tonal cr-top" type="button" onclick="openReceipt()">${icon('camera')}レシートから読み取る</button>`;
  const thumb = rc ? (p.url ? `<img class="cr-thumb" src="${esc(p.url)}" alt="読み取ったレシート">` : `<div class="cr-thumb is-sample">${icon('photo')}<span>見本</span></div>`) : '';
  return `<div class="cr-form">${top}${thumb}${warns.map(w => banner('warn', esc(w))).join('')}${dup}${todo}`
    + `<div class="cr-row2">${field('日付', input({type: 'date', value: p.date, onchange: `crSet('date',this.value)`}))}${field('時刻', input({type: 'time', value: p.time || '', onchange: `crSet('time',this.value)`}))}</div>`
    + `<label class="p-field"><span class="p-field-k">金額</span>${rc && p.unread && p.unread.amt ? readMark({label: '読み取り', on: `crUnmark('amt')`}) : ''}${rc ? `<span class="cr-amt num" id="cr_tot">${yen(total)}</span>` : input({type: 'number', inputmode: 'numeric', value: p.items.length === 1 ? (p.items[0].price || '') : total, ph: '¥0', oninput: "crField('amt',this.value)"})}</label>`
    + `<label class="p-field"><span class="p-field-k">店</span>${rc && p.unread && p.unread.payee ? readMark({label: '読み取り', on: `crUnmark('payee')`}) : ''}<input class="p-input${!p.shop && rc ? ' is-empty' : ''}" value="${esc(p.shop || '')}" placeholder="${rc && !p.shop ? '読めませんでした' : '店の名前'}" oninput="crField('shop',this.value)"></label>`
    + shopChips + regShop
    + `<div class="cr-fl"><span class="p-field-k">支払い方法</span>${payMethodSelect(p.how, "crSet('how',this.value)")}</div>`
    + field('活動の中', select({options: cr_actOptions(p.date), value: act, onchange: `crSet('act',this.value)`}))
    + `<div class="cr-fl"><span class="p-field-k">カテゴリ</span><div class="cr-scroll">${seg(ROOTS().map(r => ({k: r, label: CATS[r].n})), root, 'crRoot')}</div></div>`
    + (subs.length ? field('小分類', select({options: [{v: root, l: CATS[root].n}, ...subs.map(c => ({v: c, l: CATS[c].n}))], value: p.cat, onchange: `crSet('cat',this.value)`})) : '')
    + ask.join('')
    + `<div class="cr-fl"><span class="p-field-k">品目</span></div>`
    + p.items.map((it, i) => cr_itemRow(p, i, it, people, rc)).join('')
    + `<button class="p-btn p-btn-tonal cr-add" type="button" onclick="crItemAdd()">${icon('plus')}品目を追加</button>`
    + (rc ? `<button class="p-btn p-btn-tonal cr-add" type="button" onclick="crAllConfirm()">すべて確かめた</button>` : '')
    + payHtml + split + ticketHtml
    + `</div>`;
}
/** 重複を残す(手入力は保存へ。一覧・1枚の確認は一覧で確認待ちに戻す) */
function crDupKeep() {
  const p = crPay;
  p.dupOk = true; p.dupReason = null;
  if (crView === 'review' && crRc && crRc.length > 1) { p.status = 'ready'; return cr_viewBatch() }
  cr_save();
}

/* ---------- 保存 ---------- */
/** 1件作る(makePayment)。検査と重複の確認は呼ぶ前に済ませる。戻り値 = 支払いの id */
function cr_commit(p) {
  const total = cr_total(p);
  let payeeId = cr_entId(p.shop);
  if (!payeeId && p.regShop && p.shop) { const er = entAdd({n: p.shop, t: ['shop'], facts: [], rels: []}); if (er.ok) payeeId = er.id }
  const parent = p.act || cr_dayEnsure(p.date);
  const ws = cr_payers(p);
  const paid = ws.length === 1 ? [{who: ws[0], amt: total, how: p.how}]
    : ws.map(w => ({who: w, amt: +p.payAmt[w] || 0, how: w === 'me' ? p.how : undefined}));
  const items = p.items.map(i => {
    const o = {n: i.n || CATS[p.cat].n, price: +i.price || 0, tax: i.tax || TAX_DEFAULT, users: (i.users || ['me']).slice()};
    if (i.lines) o.lines = i.lines;
    if (i.unread) o.unread = 1;
    return o;
  });
  const unread = {};
  if (p.unread && p.unread.amt) unread.amt = 1;
  if (p.unread && p.unread.payee) unread.payee = 1;
  const aid = makePayment({parent, time: p.time || undefined, items, paid, payee: [payeeId, p.shop || ''],
    basis: cr_needSplit(p) ? p.basis : undefined, n: p.n, me: p.me, cat: p.cat,
    fx: p.fx || undefined, sp: p.sp || undefined, memo: p.memo || '',
    unread: Object.keys(unread).length ? unread : undefined});
  if (p.hash) { EXTRA.hashes = EXTRA.hashes || {}; EXTRA.hashes[p.hash] = aid }
  if (p.todoDone) cr_todoKeys(p).forEach(k => plShopDone(k, aid));
  p.status = 'saved';
  return aid;
}
/** 1件の保存(手入力・1枚だけのレシート) */
function cr_save() {
  const p = crPay, total = cr_total(p);
  if (!total) return toast('金額を入れてください');
  if (!p.date) return toast('日付を入れてください');
  if (p.mode === 'rc' && p.printed != null && total !== p.printed) return toast('合計が違います。品目を直してください');
  if (catFx(p.cat) === null && !p.fx) return toast('固定か変動を選んでください');
  if (catSp(p.cat) === null && !p.sp) return toast('日常か非日常を選んでください');
  if (cr_payers(p).length > 1 && cr_payers(p).reduce((s, w) => s + (+p.payAmt[w] || 0), 0) !== total) return toast('払った額の合計が合計と違います');
  if (!p.dupOk) {
    const d = cr_dupOf(p, crRc ? crRc.map(r => r.pay) : [p]);
    if (d) { p.dupReason = d.why; return cr_redraw() }
  }
  const aid = cr_commit(p);
  save();
  closeSheet(true);
  toast('支払いを保存しました');
  if (['home', 'ledger', 'settings'].includes(CUR.name)) rerender();
  else go('#/a/' + aid);
}
/** 一覧の1枚を確認済みにする */
function crConfirmRow(i) {
  const p = crRc[i].pay;
  if (cr_total(p) !== p.printed) return toast('合計が違います。品目を直してください');
  if (!p.dupOk) {
    const d = cr_dupOf(p, crRc.map(r => r.pay));
    if (d) { p.status = 'dup'; p.dupReason = d.why; return cr_redraw() }
  }
  p.status = 'confirmed'; p.dupReason = null;
  cr_viewBatch();
}
function crSkip(i) { const p = crRc[i].pay; p.status = 'skip'; p.dupReason = null; cr_viewBatch() }
function crBack(i) { crRc[i].pay.status = 'ready'; cr_viewBatch() }
function crKeep(i) { const p = crRc[i].pay; p.dupOk = true; p.dupReason = null; p.status = 'ready'; cr_viewBatch() }
/** 確認済みの枚を全部作る */
function crBatchSave() {
  const ok = crRc.filter(r => r.pay.status === 'confirmed');
  if (!ok.length) return;
  let last = null;
  ok.forEach(r => { last = cr_commit(r.pay) });
  save();
  closeSheet(true);
  toast(`${ok.length}件の支払いを保存しました`);
  if (['home', 'ledger', 'settings'].includes(CUR.name)) rerender();
  else go(ok.length === 1 ? '#/a/' + last : '#/activities');
}

/* ---------- レシートから読み取る(一覧 → 1枚の確認 → 確認済み → まとめて保存) ---------- */
function openReceipt() {
  const body = `<div class="cr-pick">`
    + `<button class="p-btn p-btn-fill" type="button" onclick="document.getElementById('cr_cam').click()">${icon('camera')}撮影する</button>`
    + `<button class="p-btn p-btn-tonal" type="button" onclick="document.getElementById('cr_pick').click()">${icon('photo')}写真を選ぶ</button>`
    + `<button class="p-btn p-btn-tonal" type="button" onclick="crRcSamples()">サンプルのレシートで試す</button>`
    + `<input id="cr_cam" type="file" accept="image/*" capture="environment" hidden onchange="crRcFiles(this.files)">`
    + `<input id="cr_pick" type="file" accept="image/*" multiple hidden onchange="crRcFiles(this.files)">`
    + `</div>`;
  openSheet({title: 'レシートから読み取る', body});
}
function crRcSamples() { crRcStart(CR_SAMPLES.map(s => ({url: null, hash: 'sample:' + s.id, sample: s}))) }
async function crRcFiles(files) {
  if (!files || !files.length) return;
  const picked = Array.from(files).slice(0, 20);
  const rows = await Promise.all(picked.map(async (f, i) => ({url: URL.createObjectURL(f), hash: await cr_hash(f), sample: CR_SAMPLES[i % CR_SAMPLES.length]})));
  crRcStart(rows);
}
/** 読み取りを始める。1枚なら一覧を飛ばして確認へ。複数は一覧で進みを見せる */
function crRcStart(rows) {
  for (let n = 0; n < 6 && sheetOpen(); n++) closeSheet(true);
  crRc = rows.map(r => ({url: r.url, pay: crPayFromSample(r.sample, r.url, r.hash)}));
  crIdx = 0;
  if (crRc.length === 1) {
    crRc[0].pay.status = 'ready';
    crPay = crRc[0].pay; crView = 'review';
    crPaySnap = JSON.stringify(crPay);
    openSheet({title: 'レシートを確認', body: cr_payBody(), primary: cr_primary(), dirty: () => JSON.stringify(crPay) !== crPaySnap});
    return;
  }
  crPay = null; crView = 'batch';
  openSheet({title: 'レシートの読み取り', body: cr_batchBody(), primary: cr_primary()});
  /* 読み取りは1枚ずつ進む(モック。実際は読み取りの結果を待つ) */
  crRc.forEach((r, i) => setTimeout(() => { r.pay.status === 'reading' && (r.pay.status = 'ready'); if (crView === 'batch' && sheetOpen()) cr_viewBatch() }, 300 * (i + 1)));
}
function crPayFromSample(s, url, hash) {
  const items = s.items.map(i => ({n: i.n, price: i.price, users: ['me'], tax: TAX_DEFAULT, unread: 1}));
  return {mode: 'rc', url, hash, printed: s.total, date: s.date, time: s.time || '', shop: s.shop || '', regShop: false,
    how: PAY_METHODS[0], act: null, cat: s.cat, fx: null, sp: null, items, payers: ['me'], payAmt: {}, basis: 'items', n: 2, me: 0,
    unread: {amt: 1, payee: s.shop ? 1 : 0}, memo: '', todoDone: false, dupReason: null, dupOk: false, status: 'reading'};
}
/** 一覧に戻す(進み・各枚の状態・ボタン・主ボタン) */
function cr_viewBatch() {
  crView = 'batch'; crPay = null;
  setSheetBody(cr_batchBody());
  setSheetPrimary(cr_primary());
  cr_title('レシートの読み取り');
}
function cr_batchBody() {
  const n = crRc.length, done = crRc.filter(r => r.pay.status !== 'reading').length;
  const rows = crRc.map((r, i) => {
    const p = r.pay, st = p.status;
    const thumb = r.url ? `<img class="cr-bthumb" src="${esc(r.url)}" alt="">` : `<span class="cr-bthumb is-sample">${icon('photo')}</span>`;
    const btn = st === 'skip' ? `<button class="p-btn p-btn-tonal" type="button" onclick="crBack(${i})">戻す</button>`
      : st === 'dup' ? `<button class="p-btn p-btn-tonal" type="button" onclick="crKeep(${i})">別の支払いとして残す</button><button class="p-btn p-btn-tonal" type="button" onclick="crSkip(${i})">スキップ</button>`
      : (st === 'ready' || st === 'confirmed') ? `<button class="p-btn p-btn-tonal" type="button" onclick="crSkip(${i})">スキップ</button>` : '';
    return `<div class="cr-brow">`
      + `<button class="cr-bopen" type="button" ${st === 'reading' ? 'disabled' : `onclick="crOpen(${i})"`}>${thumb}`
      + `<span class="cr-bmain"><span class="cr-bshop">${esc(p.shop || '店名なし')}</span><span class="cr-bsub num">${esc(fmtMDW(p.date))}${p.time ? ' ' + esc(p.time) : ''}</span></span>`
      + `<span class="cr-bamt num">${yen(p.printed)}</span></button>`
      + `<div class="cr-bst">${tag(CR_STATUS[st])}${p.dupReason ? `<span class="cr-bwhy">${esc(p.dupReason)}</span>` : ''}</div>`
      + (btn ? `<div class="cr-bact">${btn}</div>` : '') + `</div>`;
  }).join('');
  return `<div class="cr-form">`
    + `<div class="cr-prog"><span class="num">${done}/${n}枚 読み取り済み</span>${progress(done / n, {label: '読み取りの進み'})}</div>`
    + `<div class="cr-blist">${rows}</div></div>`;
}
/** 一覧の1枚を確認する */
function crOpen(i) {
  crIdx = i; crPay = crRc[i].pay; crView = 'review';
  crPaySnap = JSON.stringify(crPay);
  setSheetBody(cr_payBody()); setSheetPrimary(cr_primary());
  cr_title(`レシート ${i + 1}/${crRc.length}`);
}

/* ---------- 出来事(期間 / 瞬間) ---------- */
const CR_EV = {span: ['食事', '会話', '作業', '移動', '買い物', 'その他'], moment: ['起床', '就寝', '出発', '到着', 'その他']};
function cr_openEvent(d) {
  crEv = {kind: 'span', type: '食事', title: '', date: d || TODAY, start: NOW, end: '', act: '', place: '', from: '', to: '', people: [], body: ''};
  crEvSnap = JSON.stringify(crEv);
  openSheet({title: '出来事を記録', body: cr_evBody(), primary: {label: '出来事を保存', on: () => cr_evSave()}, dirty: () => JSON.stringify(crEv) !== crEvSnap});
}
function cr_evBody() {
  const e = crEv, move = e.type === '移動';
  const acts = cr_spans(e.date).filter(([, a]) => !e.start || (a.time <= e.start && e.start <= a.end));
  return `<div class="cr-form">`
    + `<div class="cr-fl"><span class="p-field-k">種類</span>${seg([{k: 'span', label: '期間'}, {k: 'moment', label: '瞬間'}], e.kind, 'crEvKind')}</div>`
    + `<div class="cr-fl"><span class="p-field-k">内容</span><div class="cr-scroll">${seg(CR_EV[e.kind].map(t => ({k: t, label: t})), e.type, 'crEvType')}</div></div>`
    + field('題', input({value: e.title, ph: e.type, oninput: 'crEv.title=this.value'}))
    + `<div class="cr-row2">${field('日付', input({type: 'date', value: e.date, onchange: "crEvSet('date',this.value)"}))}${field(e.kind === 'span' ? '開始' : '時刻', input({type: 'time', value: e.start, onchange: "crEvSet('start',this.value)"}))}`
    + (e.kind === 'span' ? field('終了', input({type: 'time', value: e.end, onchange: "crEvSet('end',this.value)"})) : '') + `</div>`
    + field('活動の中', select({options: [{v: '', l: 'なし(日の直下)'}, ...acts.map(([k, a]) => ({v: k, l: `${a.title} ${a.time}–${a.end}`}))], value: e.act || '', onchange: "crEvSet('act',this.value)"}))
    + (move
      ? field('出発地', input({value: e.from, ph: '出発地', oninput: 'crEv.from=this.value'})) + field('到着地', input({value: e.to, ph: '到着地', oninput: 'crEv.to=this.value'}))
      : field('場所', input({value: e.place, ph: '場所の名前', oninput: 'crEv.place=this.value'})))
    + (move || e.kind !== 'span' ? '' : `<div class="cr-fl"><span class="p-field-k">参加者</span>${whoChips(cr_people().filter(w => w !== 'me'), e.people, 'crEvPeople($v)', '参加者')}</div>`)
    + (e.kind === 'span' ? `<label class="p-field"><span class="p-field-k">内容</span><textarea class="p-input cr-body" rows="3" oninput="crEv.body=this.value">${esc(e.body)}</textarea></label>` : '')
    + `</div>`;
}
function crEvSet(k, v) { crEv[k] = v; if (k === 'date' || k === 'start') crEv.act = ''; setSheetBody(cr_evBody()) }
function crEvKind(k) { crEv.kind = k; crEv.type = CR_EV[k][0]; setSheetBody(cr_evBody()) }
function crEvType(t) { crEv.type = t; setSheetBody(cr_evBody()) }
function crEvPeople(w) { const ps = crEv.people, i = ps.indexOf(w); if (i >= 0) ps.splice(i, 1); else ps.push(w); setSheetBody(cr_evBody()) }
function cr_evSave() {
  const e = crEv;
  if (!e.start) return toast('時刻を入れてください');
  if (e.kind === 'span' && (!e.end || e.end <= e.start)) return toast('終了は開始より後にしてください');
  const parent = e.act || cr_dayEnsure(e.date);
  const id = uid('a');
  const roles = [];
  if (e.type === '移動') {
    if (e.from) roles.push(['出発', cr_entId(e.from), e.from]);
    if (e.to) roles.push(['到着', cr_entId(e.to), e.to]);
  } else if (e.place) roles.push(['場所', cr_entId(e.place), e.place]);
  if (e.kind === 'span' && e.type !== '移動') e.people.forEach(w => roles.push(['同席', w, '']));
  A[id] = {type: e.type, parent, time: e.start, title: e.title.trim() || e.type, roles, kids: []};
  if (e.kind === 'span') A[id].end = e.end;
  if (e.kind === 'span' && e.body.trim()) A[id].body = e.body.trim();
  (A[parent].kids = A[parent].kids || []).push(id);
  closeSheet(true); save();
  toast('出来事を保存しました');
  go('#/a/' + id);
}

/* ---------- ＋ の一覧に登録 ---------- */
addCreate({key: 'payment', label: '支払いを記録', icon: 'pay', primaryOn: ['home', 'ledger', 'settings'], run: (o) => openPayment(o || {})});
addCreate({key: 'event', label: '出来事', icon: 'activity', primaryOn: ['activities'], run: () => cr_openEvent()});

/* ---------- 画面どうしの入口(統合: 本体) ---------- */
function openNewHappening(d) { return cr_openEvent(d) }
function openNewEvent(d) { return plOpenNewEvent(d) }
function openDiary(d) { return hmDiary(d || TODAY) }
