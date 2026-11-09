/* =====================================================================
   parts.js — 共通部品(DESIGN.md 2.2)。HTML 文字列を返す関数だけ。
   見た目は parts.css、値は tokens.css の変数だけ。
   約束: 文字は esc() を通す(ただし sub・meta・kids・body など「HTML を渡す引数」は呼び手が esc 済みにする)。
   on(押した時の処理)は JS の文字列を onclick にそのまま入れる。chips / seg / 日付の操作は、関数名を渡す(呼ぶと on('キー'))。
   ===================================================================== */

/* ---------- 線のアイコン(24格子・線2px。名前は DESIGN 6章) ---------- */
const PARTS_IC={
  home:'<path d="M4 11.5 12 5l8 6.5"/><path d="M6.5 10v9h11v-9"/><path d="M10.5 19v-4.5h3V19"/>',
  memo:'<path d="M6 3.5h8.5L19 8v12.5H6z"/><path d="M14.5 3.5V8H19"/><path d="M9 12h7M9 15.5h5"/>',
  axis:'<path d="M6 2.5v19"/><rect x="3.5" y="4.5" width="5" height="5" rx="1.6" fill="currentColor"/><circle cx="6" cy="16" r="2.4"/><path d="M11.5 7h9M11.5 16h7"/>',
  plan:'<path d="M5 2.5v19" stroke-dasharray="2 2.6"/><rect x="8.5" y="8" width="13" height="8" rx="4" stroke-dasharray="2.4 1.8"/>',
  moment:'<path d="M5 2.5v19"/><rect x="8.5" y="8" width="13" height="8" rx="4" fill="currentColor"/>',
  activity:'<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  todo:'<rect x="3.5" y="3.5" width="17" height="17" rx="4.5"/><path d="m8 12.5 3 3 5-6"/>',
  entity:'<path d="M3 6.5A2.5 2.5 0 0 1 5.5 4h4.2l2 2.5h6.8A2.5 2.5 0 0 1 21 9v9.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5Z"/>',
  type:'<path d="M3 12 10 4.5h9.5A1.5 1.5 0 0 1 21 6v12a1.5 1.5 0 0 1-1.5 1.5H10Z"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/>',
  cats:'<rect x="3" y="5" width="8" height="4" rx="2" fill="currentColor"/><rect x="3" y="11" width="13" height="4" rx="2"/><rect x="3" y="17" width="18" height="4" rx="2"/>',
  ledger:'<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  pay:'<rect x="2.5" y="5.5" width="19" height="13" rx="2.5"/><path d="M2.5 10h19M6.5 15h4"/>',
  expense:'<rect x="2.5" y="8.5" width="19" height="7" rx="1.5"/><rect x="2.5" y="8.5" width="8.5" height="7" rx="1.5" fill="currentColor"/>',
  talk:'<path d="M4 5h16v11H9l-5 4Z"/>',
  move:'<path d="M5 19 19 5M19 5h-8M19 5v8"/>',
  diary:'<rect x="4" y="3.5" width="16" height="17" rx="2.5"/><path d="M8 3.5v17M11.5 8h5M11.5 12h5"/>',
  lines:'<path d="M5 7h14M5 12h14M5 17h9"/>',
  camera:'<path d="M4 7.5h3l1.5-2.5h7L17 7.5h3a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1Z"/><circle cx="12" cy="13" r="3.5"/>',
  photo:'<rect x="3" y="4" width="18" height="16" rx="2.5"/><circle cx="8.5" cy="9.5" r="1.8"/><path d="m21 16-5-5-9 9"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  back:'<path d="M15 5l-7 7 7 7"/>',
  close:'<path d="M6 6l12 12M18 6 6 18"/>',
  chev:'<path d="m9 6 6 6-6 6"/>',
  up:'<path d="m6 15 6-6 6 6"/>',
  down:'<path d="m6 9 6 6 6-6"/>',
  more:'<circle cx="5" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.6" fill="currentColor" stroke="none"/>',
  search:'<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
  check:'<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  edit:'<path d="M4 20h4L19 9l-4-4L4 16Z"/>',
  trash:'<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  warn:'<path d="M12 4 2.5 20h19Z"/><path d="M12 10v4M12 17.2v.1"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8v.1"/>',
  bell:'<path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15Z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  repeat:'<path d="M4 11V9a3 3 0 0 1 3-3h12M16 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H5M8 21l-3-3 3-3"/>',
  place:'<path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21Z"/><circle cx="12" cy="9.5" r="2.5"/>',
  people:'<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5"/>',
  link:'<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  sparkle:'<path d="M12 3c.6 4.2 2.8 6.4 7 7-4.2.6-6.4 2.8-7 7-.6-4.2-2.8-6.4-7-7 4.2-.6 6.4-2.8 7-7Z" fill="currentColor" stroke="none"/>',
  settings:'<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3.6 14H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.1-2.7l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9.7 3.6V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z"/>',
  share:'<rect x="2.5" y="6.5" width="19" height="7" rx="1.5"/><rect x="2.5" y="6.5" width="8.5" height="7" rx="1.5" fill="currentColor"/><path d="M2.5 17v2.5h8.5V17"/>',
  spark:'<path d="M12 4v4M12 16v4M4 12h4M16 12h4M6.5 6.5l2.5 2.5M15 15l2.5 2.5M17.5 6.5 15 9M9 15l-2.5 2.5"/>',
  kin:'<circle cx="8" cy="9.5" r="4.5"/><path d="M16.5 21.5s-5-4.3-5-8.2a5 5 0 0 1 10 0c0 3.9-5 8.2-5 8.2Z"/><circle cx="16.5" cy="13.3" r="1.6" fill="currentColor"/>',
  note:'<path d="M6 3h8.5L20 8.5V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>',
  schedule:'<rect x="3" y="7" width="18" height="10" rx="5" stroke-dasharray="2.6 2"/>',
  future:'<path d="M5 2.5v19" stroke-dasharray="2 2.6"/><rect x="8.5" y="8" width="13" height="8" rx="4" stroke-dasharray="2.4 1.8"/>'
};
const PARTS_EMPTY_IC={list:'lines',day:'diary',todo:'todo',memo:'memo',entity:'entity',ledger:'ledger',plan:'plan',pay:'pay',type:'type',cats:'cats',activity:'activity',none:'activity'};
const PARTS_WD='日月火水木金土';
const pDow=d=>PARTS_WD[new Date(d+'T00:00').getDay()];

/** 線のアイコン。name は PARTS_IC の名前。cls は追加のクラス */
function icon(name,cls=''){const p=PARTS_IC[name];
  return p?`<svg class="p-ic ${cls}" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`:''}
/** ナビの面のアイコン(同じ形の面を薄く塗る) */
function navIcon(name,cls=''){const p=PARTS_IC[name];
  return p?`<svg class="p-navic ${cls}" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor" fill-opacity=".18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`:''}
/** 言葉なしのボタン(44px)。label は必ず入れる(読み上げ名) */
function iconBtn(name,label,on,cls=''){
  return `<button class="p-ibtn ${cls}" type="button" aria-label="${esc(label)}" onclick="${on}">${icon(name)}</button>`}

/* ---------- 見出し・バー ---------- */
/** タブの最上位の大見出し。actions=[{label,icon,on,primary}]。icon だけ=丸いボタン、primary=塗りの文字ボタン */
function largeTitle(title,o={}){
  return `<header class="p-largetitle"><h1 class="p-lt-h">${esc(title)}</h1><div class="p-lt-acts">${(o.actions||[]).map(pAction).join('')}</div></header>`}
function pAction(a){
  if(a.icon&&!a.primary)return iconBtn(a.icon,a.label,a.on);
  return `<button class="p-btn ${a.primary?'p-btn-fill':'p-btn-tonal'}" type="button" onclick="${a.on}">${a.icon?icon(a.icon):''}${esc(a.label)}</button>`}
/** 詳細の上部バー(戻る + 題 + 右の操作)。下にスクロールすると下線が出る(下の監視で付く) */
function appbar(title='',o={}){
  const back=o.back===false?'':iconBtn('back','戻る','goBack()');
  return `<header class="p-appbar">${back}<h2 class="p-ab-t">${esc(title)}</h2><span class="p-grow"></span>${(o.actions||[]).map(a=>iconBtn(a.icon,a.label,a.on)).join('')}</header>`}
/** 節見出し。count=右の件数、act={label,on}=右の青い文字の操作 */
function sh(title,o={}){
  return `<div class="p-sh"><h2 class="p-sh-h">${esc(title)}</h2>${o.count!=null&&o.count!==''?`<span class="p-sh-n num">${esc(o.count)}</span>`:''}<span class="p-grow"></span>${o.act?`<button class="p-sh-act" type="button" onclick="${o.act.on}">${esc(o.act.label)}</button>`:''}</div>`}

/* ---------- 面・行・属性 ---------- */
/** 行の中身(row と objRow で共通)。o={t,s,right,href,on,lead,chev} */
function pRowHTML(o,cls){
  const tag=o.href?'a':o.on?'button':'div';
  const at=o.href?`href="${esc(o.href)}"`:o.on?`type="button" onclick="${o.on}"`:'';
  const chev=(o.href||o.on)&&o.chev!==false;
  return `<${tag} class="${cls}" ${at}>${o.lead?`<span class="p-row-lead">${o.lead}</span>`:''}<span class="p-row-body"><span class="p-row-t">${esc(o.t)}</span>${o.s!=null&&o.s!==''?`<span class="p-row-s">${esc(o.s)}</span>`:''}</span>${o.right!=null&&o.right!==''?`<span class="p-row-r num">${esc(o.right)}</span>`:''}${chev?`<span class="p-chev">${icon('chev')}</span>`:''}</${tag}>`}
/** 枠線の面の中の行(影なし。行はオブジェクトではない) */
function row(o={}){return pRowHTML(o,'p-row')}
/** 枠線の面。rows は row() の結果の配列 */
function list(rows){return `<div class="p-list">${rows.join('')}</div>`}
/** 影のある白い面の行(独立したオブジェクト。押すと開く) */
function objRow(o={}){return pRowHTML(o,'p-obj')}
/** 属性の行(ラベル+値)。on があれば行全体が押せる(ペンは出さない)。html=true なら v は HTML */
function prop(k,v,o={}){
  const val=o.html?v:esc(v);
  const inner=`<span class="p-prop-k">${esc(k)}</span><span class="p-prop-v">${val}</span>${o.on?`<span class="p-chev">${icon('chev')}</span>`:''}`;
  return o.on?`<button class="p-prop is-btn" type="button" onclick="${o.on}">${inner}</button>`:`<div class="p-prop">${inner}</div>`}

/* ---------- 日・月 ---------- */
/** 日のカード。白い面、輪郭が光る(今日は強く、他の日はうっすら)。押すと日のページ。
    o={sum(金額の文字), diary(日記の一言), href} */
function dayCard(d,o={}){
  const isT=d===TODAY;const [,m,dd]=d.split('-');
  return `<a class="p-daycard ${isT?'is-today':''}" href="${esc(o.href||'#/day/'+d)}"><span class="p-dc-num" aria-hidden="true"><b>${+dd}</b><small>${pDow(d)}</small></span><span class="p-dc-body"><span class="p-dc-h"><b>${+m}月${+dd}日 ${pDow(d)}曜</b>${isT?'<span class="p-dc-today">今日</span>':''}<span class="p-grow"></span>${o.sum?`<span class="p-dc-sum num">${esc(o.sum)}</span>`:''}</span>${o.diary?`<span class="p-dc-diary">${esc(o.diary)}</span>`:''}</span></a>`}
/** 月の区切り(同じカードの小さい版) ym='2026-10' */
function monthCard(ym){const [y,m]=ym.split('-');return `<div class="p-month"><b>${y}年${+m}月</b></div>`}

/* ---------- 時間軸 ---------- */
/** 軸の1行。o={time(文字 または {d,t}), end, node:'ev'|'plan'|'late'|'moment'|'pay', title, meta(HTML), amt, href, body(HTML・支払いの殻など), kids(HTML), future} */
function axRow(o={}){
  const tag=o.href?'a':'div';const at=o.href?`href="${esc(o.href)}"`:'';
  const tm=o.time&&typeof o.time==='object'?`<b>${o.time.d}日</b><small>${esc(o.time.t||'')}</small>`:`${esc(o.time||'')}${o.end?`<small class="p-ax-to">${esc(o.end)}</small>`:''}`;
  const body=o.body?`<span class="p-ax-c is-raw">${o.body}</span>`
    :`<span class="p-ax-c"><span class="p-grow"><span class="p-ax-t">${esc(o.title||'')}</span>${o.meta?`<span class="p-ax-m">${o.meta}</span>`:''}${o.kids?`<span class="p-ax-kids">${o.kids}</span>`:''}</span>${o.amt!=null&&o.amt!==''?`<span class="p-ax-amt num">${esc(o.amt)}</span>`:''}</span>`;
  return `<${tag} class="p-axrow is-${o.node||'moment'} ${o.future?'is-future':''}" ${at}><span class="p-ax-time num">${tm}</span><span class="p-ax-node">${o.node==='pay'?icon('pay'):''}</span>${body}</${tag}>`}
/** いまの線(赤い横線と赤い時刻。点は付けない) */
function nowLine(){const t=typeof NOW!=='undefined'?NOW:'13:20';
  return `<div class="p-now" aria-label="いま ${esc(t)}"><span class="p-now-t num">${esc(t)}</span><span class="p-now-ln"></span></div>`}
/** 軸の端(過去の続き・未来の続き。薄れて消える) */
function axEdge(text,future){return `<div class="p-axedge ${future?'is-future':'is-past'}"><span>${esc(text)}</span></div>`}
/** カプセル(詳細の主役)。kind: done=塗り(起きた) / plan=点線・中抜き(予定) / late=赤の点線(過ぎた予定)
    o={time,title,sub(HTML),href} */
function capsule(o={}){
  const tag=o.href?'a':'div';const at=o.href?`href="${esc(o.href)}"`:'';const k=o.kind||'done';
  return `<${tag} class="p-capsule is-${k}" ${at}>${o.time?`<span class="p-cap-t num">${esc(o.time)}</span>`:''}<b class="p-cap-h">${esc(o.title||'')}</b>${o.sub?`<span class="p-cap-s">${o.sub}</span>`:''}</${tag}>`}

/* ---------- 半券・支払い ---------- */
/** 半券の中身。日常 / ✦非日常 / 未設定 */
function pStub(s){
  return s==='非日常'?`<span class="p-tk-sp">${icon('sparkle')}<b>非日常</b></span>`
    :s==='日常'?`<b class="p-tk-sp">日常</b>`:`<span class="p-tk-none">未設定</span>`}
/** 半券(自分の支出)。o={stub:'日常'|'非日常'|'未設定', title, sub(HTML), amt, href, onclick, size:'sm'|'md'|'lg', fill, label}
    fill=true は塗りの上に置く時の白い殻(半券が地の灰に触れないように、面で包む) */
function ticket(o={}){
  const tag=o.href?'a':o.onclick?'button':'div';
  const at=o.href?`href="${esc(o.href)}"`:o.onclick?`type="button" onclick="${o.onclick}"`:'';
  return `<${tag} class="p-ticket is-${o.size||'md'} ${o.fill?'is-fill':''}" ${at} ${o.label?`aria-label="${esc(o.label)}"`:''}><span class="p-tk"><span class="p-tk-stub">${pStub(o.stub)}</span><span class="p-tk-body"><span class="p-grow"><span class="p-tk-t">${esc(o.title||'')}</span>${o.sub?`<span class="p-tk-s">${o.sub}</span>`:''}</span>${o.amt!=null?`<span class="p-tk-amt num">${esc(o.amt)}</span>`:''}${o.href?`<span class="p-chev">${icon('chev')}</span>`:''}</span></span></${tag}>`}
/** お金の棒(自分=青 / ほかの人=灰 / 未精算=琥珀の斜線)。支払い aid の内訳 */
function payBar(aid){
  const p=A[aid];const x=p&&p.x&&X[p.x];if(!x)return '';
  const mine=xAmt(x),rest=Math.max(0,p.amt-mine);const b=balanceOf(p);const owed=b.amt>0&&!b.settled;
  return `<span class="p-bar" role="img" aria-label="支払い ${yen(p.amt)} のうち自分 ${yen(mine)}"><i class="me" style="flex:${mine}"></i>${rest>0?`<i class="${owed?'owe':'other'}" style="flex:${rest}"></i>`:''}</span>`}
/** 割合の棒+凡例。segs=[{v, kind:'me'|'other'|'owe', label, href}] */
function splitBar(segs,total){
  return `<div class="p-split"><div class="p-bar p-bar-lg">${segs.map(g=>`<i class="${esc(g.kind)}" style="flex:${+g.v||0}"></i>`).join('')}</div><div class="p-split-lg">${segs.map(g=>{
    const inner=`<span class="p-split-k is-${esc(g.kind)}">${esc(g.label)}</span><b class="num">${yen(g.v)}</b>`;
    return g.href?`<a class="p-split-i" href="${esc(g.href)}">${inner}</a>`:`<span class="p-split-i">${inner}</span>`}).join('')}</div></div>`}
/** 支払いの殻。mode: row(白い面の見出し+半券) / kid(活動の中の薄い面) / pay(主役の下端の半券) / x(支出の詳細の最上部) */
function payShell(aid,o={}){
  const mode=o.mode||'row';const p=A[aid];if(!p)return '';
  const xk=o.xid||p.x;const x=xk&&X[xk];
  const payHref='#/a/'+aid,xHref='#/x/'+xk;
  const bal=balanceOf(p);
  const tot=`<span class="p-pay-tot">合計 <b class="num">${yen(p.amt)}</b></span>`;
  const xTk=(extra)=>x?ticket(Object.assign({stub:xSp(x),title:'自分の支出',
    sub:`<span class="p-tk-cat">${esc(catPath(x.cat))}</span>${x.amt!==p.amt?payBar(aid):''}${bal.open?tag(balanceText(bal),{kind:'owe'}):''}`,
    amt:yen(xAmt(x))},extra)):'';
  if(mode==='pay')return x?`<div class="p-pay is-pay">${xTk({size:'md',fill:true,href:xHref})}</div>`:'';
  if(mode==='x'){
    const d=xDate(x);
    return `<div class="p-pay is-x"><a class="p-pay-h" href="${payHref}"><span class="p-pay-ic">${icon('pay')}</span><span class="p-pay-l">${esc(payLabel(p))} · ${esc(fmtMDW(d))}${p.time?` ${esc(p.time)}`:''}</span>${tot}</a>${x?xTk({size:'lg',fill:true,href:xHref,label:'自分の支出'}):''}</div>`}
  if(mode==='kid'){
    const g1=`event.stopPropagation();go('${payHref}')`,g2=`event.stopPropagation();go('${xHref}')`;
    return `<div class="p-pay is-kid"><span class="p-pay-h" role="link" tabindex="0" onclick="${g1}"><span class="p-pay-ic">${icon('pay')}</span><span class="p-pay-l">${esc(payeeOf(p)||'支払い')}</span>${p.time?`<small class="p-pay-tm num">${esc(p.time)}</small>`:''}${tot}</span>${x?`<span class="p-pay-tk" role="link" tabindex="0" onclick="${g2}">${xTk({size:'sm'})}</span>`:''}</div>`}
  /* row(既定) */
  return `<div class="p-pay is-row"><a class="p-pay-h" href="${payHref}"><span class="p-pay-ic">${icon('pay')}</span><span class="p-pay-l">${esc(payLabel(p))}</span>${tot}</a>${x?xTk({size:'sm',href:xHref}):''}</div>`}

/* ---------- 人・分類 ---------- */
/** 参照(緑のドット+名前)。未登録は中抜きのドット。押すと開く */
function eref(id,name){
  return id&&E[id]?`<a class="p-ref" href="#/e/${esc(id)}"><span class="p-dot" aria-hidden="true"></span>${esc(E[id].n)}</a>`
    :`<span class="p-ref is-unreg"><span class="p-dot is-unreg" aria-hidden="true"></span>${esc(name||'未登録')}</span>`}
/** データに付いた分類(角丸6)。color=カテゴリの短い横棒。kind=owe(琥珀)/late(赤) */
function tag(label,o={}){
  return `<span class="p-tag ${o.kind?'is-'+o.kind:''}">${o.color?`<i class="p-tag-bar" style="--cc:${esc(o.color)}" aria-hidden="true"></i>`:''}${esc(label)}</span>`}
/** カテゴリのタグ。path を渡すとその文字を出す */
function catTag(k,o={}){return tag(o.path||catPath(k),{color:catColor(k)})}

/* ---------- 操作(ピル・チェック・切替・入力) ---------- */
function pPills(items,isOn,on,cls){
  return items.map(it=>`<button class="${cls}" type="button" aria-pressed="${isOn(it.k)?'true':'false'}" onclick="${on}('${esc(it.k)}')">${esc(it.label)}</button>`).join('')}
/** 複数選べるピル。items=[{k,label}], cur=選ばれたkの配列, on=関数名 */
function chips(items,cur,on){const c=[].concat(cur||[]);return `<div class="p-chips" role="group">${pPills(items,k=>c.includes(k),on,'p-chip')}</div>`}
/** 1つ選ぶピル(切替)。cur=選ばれたk, on=関数名 */
function seg(items,cur,on){return `<div class="p-seg" role="group">${pPills(items,k=>k===cur,on,'p-seg-i')}</div>`}
/** 期間の移動。label=「10月 2026」など。prev/next={on,dis,text} */
function periodNav(label,prev={},next={}){
  const b=(x,def)=>`<button class="p-period-b" type="button" ${x.dis?'disabled':''} onclick="${x.on||''}">${esc(x.text||def)}</button>`;
  return `<div class="p-period" role="group" aria-label="期間">${b(prev,'前の月')}<b class="p-period-l num">${esc(label)}</b>${b(next,'次の月')}</div>`}
/** ToDo のチェック欄(角丸の正方形。塗り=完了)。o={late, click, label} */
function checkbox(on,o={}){
  return `<button class="p-check ${on?'is-on':''} ${o.late?'is-late':''}" type="button" role="checkbox" aria-checked="${on?'true':'false'}" aria-label="${esc(o.label||'完了')}" onclick="${o.click||''}"><span class="p-check-m">${on?icon('check'):''}</span></button>`}
/** 切替(トグル)。o={click,label} */
function toggle(on,o={}){
  return `<button class="p-toggle ${on?'is-on':''}" type="button" role="switch" aria-checked="${on?'true':'false'}" aria-label="${esc(o.label||'')}" onclick="${o.click||''}"><span class="p-toggle-k"></span></button>`}
/** 入力の枠(名前+入力) */
function field(label,input){return `<label class="p-field"><span class="p-field-k">${esc(label)}</span>${input}</label>`}
/** 入力欄。o={type,name,id,value,ph,oninput,onchange} */
function input(o={}){
  return `<input class="p-input" type="${esc(o.type||'text')}" ${o.name?`name="${esc(o.name)}"`:''} ${o.id?`id="${esc(o.id)}"`:''} value="${esc(o.value??'')}" placeholder="${esc(o.ph||'')}" ${o.oninput?`oninput="${o.oninput}"`:''} ${o.onchange?`onchange="${o.onchange}"`:''}>`}
/** 選択。o={options:[{v,l}|文字], value, name, onchange} */
function select(o={}){
  const opts=(o.options||[]).map(x=>{const v=typeof x==='string'?x:x.v,l=typeof x==='string'?x:x.l;
    return `<option value="${esc(v)}" ${v===o.value?'selected':''}>${esc(l)}</option>`}).join('');
  return `<select class="p-input p-select" ${o.name?`name="${esc(o.name)}"`:''} onchange="${o.onchange||''}">${opts}</select>`}

/* ---------- まとめ・空・知らせ ---------- */
/** タブのまとめカード。metrics=[{v,l,href|on}] (3つまで) / actions=[{label,icon,on,primary}] (2つまで) 説明文は書かない */
function summaryCard(o={}){
  const ms=(o.metrics||[]).map(m=>{
    const inner=`<b class="num">${esc(m.v)}</b><span>${esc(m.l)}${m.href||m.on?icon('chev','p-ic-xs'):''}</span>`;
    return m.href?`<a class="p-sum-m" href="${esc(m.href)}">${inner}</a>`:m.on?`<button class="p-sum-m" type="button" onclick="${m.on}">${inner}</button>`:`<div class="p-sum-m">${inner}</div>`}).join('');
  const acts=(o.actions||[]).map(a=>`<button class="p-btn ${a.primary?'p-btn-fill':'p-btn-tonal'} p-sum-btn" type="button" onclick="${a.on}">${a.icon?icon(a.icon):''}${esc(a.label)}</button>`).join('');
  return `<section class="p-sum">${ms?`<div class="p-sum-ms">${ms}</div>`:''}${acts?`<div class="p-sum-acts">${acts}</div>`:''}</section>`}
/** 空の状態(何もないこと+最初の一手)。o={shape:'list'|'day'|'todo'|'memo'|'entity'|'ledger'|'plan'|'pay'|'type'|'cats', text, action:{label,on}} */
function empty(o={}){
  const ic=PARTS_EMPTY_IC[o.shape||'none']||'activity';
  return `<div class="p-empty">${icon(ic,'p-empty-ic')}<p class="p-empty-t">${esc(o.text||'まだ何もありません')}</p>${o.action?`<button class="p-btn p-btn-tonal" type="button" onclick="${o.action.on}">${esc(o.action.label)}</button>`:''}</div>`}
/** 知らせの面。kind=warn(琥珀)/info(青の薄い面)。html は呼び手が esc 済みで渡す */
function banner(kind,html){
  const k=kind==='warn'?'warn':'info';
  return `<div class="p-banner is-${k}" role="status">${icon(k==='warn'?'warn':'info')}<div class="p-banner-b">${html}</div></div>`}
/** 読み取った値(未確認)の印。on を渡すと押せる(確かめる)。確かめると呼び手が印を外す */
function readMark(o={}){
  const inner=`<span class="p-rm-t">${esc(o.label||'読み取り')}</span>`;
  return o.on?`<button class="p-readmark is-btn" type="button" aria-label="読み取った値。確かめる" onclick="${o.on}">${inner}</button>`
    :`<span class="p-readmark" title="読み取った値(未確認)">${inner}</span>`}
/** 進み具合。p=0〜1 */
function progress(p=0,o={}){
  const v=Math.max(0,Math.min(1,+p||0));const pct=Math.round(v*100);
  return `<div class="p-progress" role="progressbar" aria-label="${esc(o.label||'進み')}" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${pct}"><i style="width:${pct}%"></i></div>`}

/* appbar の下線: スクロールしたら .scrolled を付ける(画面の描き直しにも追随する) */
document.addEventListener('scroll',()=>{
  document.querySelectorAll('.p-appbar').forEach(b=>{const s=b.closest('.screen');b.classList.toggle('scrolled',(window.scrollY||0)>4||(s&&s.scrollTop>4))})},{passive:true,capture:true});
