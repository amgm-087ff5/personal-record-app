/* =====================================================================
   UI v3 — 共通部品・ルーター・シート・状態保存
   画面ファイル(js/screens/*.js)はここの関数だけで組み立てる。
   ===================================================================== */

/* ---------- アイコン(24グリッド、線2px。AndroidのVectorDrawableへ移せる) ---------- */
const sv=(d,fill)=>`<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="${fill?'currentColor':'none'}" stroke="${fill?'none':'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
const IC={
  axis:sv('<path d="M6 2.5v19"/><rect x="3.5" y="4.5" width="5" height="5" rx="1.6" fill="currentColor"/><circle cx="6" cy="16" r="2.4"/><path d="M11.5 7h9M11.5 16h7"/>'),
  activity:sv('<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>'),
  schedule:sv('<rect x="3.5" y="5" width="17" height="15.5" rx="3.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>'),
  todo:sv('<rect x="3.5" y="3.5" width="17" height="17" rx="4.5"/><path d="m8 12.5 3 3 5-6"/>'),
  entity:sv('<path d="M3 6.5A2.5 2.5 0 0 1 5.5 4h4.2l2 2.5h6.8A2.5 2.5 0 0 1 21 9v9.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5Z"/>'),
  note:sv('<path d="M6 3h8.5L20 8.5V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>'),
  type:sv('<path d="M3 12 10 4.5h9.5A1.5 1.5 0 0 1 21 6v12a1.5 1.5 0 0 1-1.5 1.5H10Z"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/>'),
  expense:sv('<path d="M4 5h16a1 1 0 0 1 1 1v3a3 3 0 0 0 0 6v3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-3a3 3 0 0 0 0-6V6a1 1 0 0 1 1-1Z"/><path d="M9 6v12" stroke-dasharray="1.5 2.5"/>'),
  ledger:sv('<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>'),
  pay:sv('<rect x="2.5" y="5.5" width="19" height="13" rx="2.5"/><path d="M2.5 10h19M6.5 15h4"/>'),
  talk:sv('<path d="M4 5h16v11H9l-5 4Z"/>'),
  move:sv('<path d="M5 19 19 5M19 5h-8M19 5v8"/>'),
  diary:sv('<rect x="4" y="3.5" width="16" height="17" rx="2.5"/><path d="M8 3.5v17M11.5 8h5M11.5 12h5"/>'),
  camera:sv('<path d="M4 7.5h3l1.5-2.5h7L17 7.5h3a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1Z"/><circle cx="12" cy="13" r="3.5"/>'),
  photo:sv('<rect x="3" y="4" width="18" height="16" rx="2.5"/><circle cx="8.5" cy="9.5" r="1.8"/><path d="m21 16-5-5-9 9"/>'),
  plus:sv('<path d="M12 5v14M5 12h14"/>'),
  back:sv('<path d="M15 5l-7 7 7 7"/>'),
  close:sv('<path d="M6 6l12 12M18 6 6 18"/>'),
  chev:sv('<path d="m9 6 6 6-6 6"/>'),
  down:sv('<path d="m6 9 6 6 6-6"/>'),
  more:sv('<circle cx="5" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.6" fill="currentColor" stroke="none"/>'),
  search:sv('<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>'),
  check:sv('<path d="m5 12.5 4.5 4.5L19 7.5"/>'),
  edit:sv('<path d="M4 20h4L19 9l-4-4L4 16Z"/>'),
  trash:sv('<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>'),
  warn:sv('<path d="M12 4 2.5 20h19Z"/><path d="M12 10v4M12 17.2v.1"/>'),
  info:sv('<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8v.1"/>'),
  bell:sv('<path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15Z"/><path d="M10 20a2 2 0 0 0 4 0"/>'),
  repeat:sv('<path d="M4 11V9a3 3 0 0 1 3-3h12M16 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H5M8 21l-3-3 3-3"/>'),
  place:sv('<path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21Z"/><circle cx="12" cy="9.5" r="2.5"/>'),
  people:sv('<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5"/>'),
  link:sv('<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>'),
  sparkle:sv('<path d="M12 3c.6 4.2 2.8 6.4 7 7-4.2.6-6.4 2.8-7 7-.6-4.2-2.8-6.4-7-7 4.2-.6 6.4-2.8 7-7Z"/>',1),
  settings:sv('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3.6 14H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.1-2.7l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9.7 3.6V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z"/>'),
};
/* アクティビティの小分類 → アイコン */
const ACT_IC={'支払い':'pay','会話':'talk','移動':'move','日記':'diary','出来事':'activity'};

/* ---------- 文字列ヘルパー ---------- */
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const WD='日月火水木金土';
const wdOf=d=>WD[new Date(d+'T00:00').getDay()];
const fmtMD=d=>`${+d.slice(5,7)}月${+d.slice(8,10)}日`;
const fmtMDW=d=>`${fmtMD(d)}(${wdOf(d)})`;
const relDay=d=>{const diff=Math.round((new Date(d+'T00:00')-new Date(TODAY+'T00:00'))/864e5);return {0:'今日',1:'明日','-1':'昨日',2:'明後日'}[diff]||fmtMDW(d)};

/* ---------- 部品(HTML文字列を返す) ---------- */
const NOW='13:20'; /* モックの「いま」(2026-10-31 13:20) */
/** 小さいアイコンの台。c: 色(CSS値) */
const ico=(icon,c='var(--ink-2)')=>`<span class="ico" style="--ic:${c}">${IC[icon]||''}</span>`;

/* ---- 縁(エンティティ) ---- */
const ROOT_SHAPE={person:'person',place:'place',company:'org',thing:'thing',doc:'doc'};
const ROOT_LABEL={person:'人物',place:'場所',company:'会社',thing:'物',doc:'書類'};
const rootOf=id=>{const e=E[id];return e&&e.t&&e.t[0]?typeAnc(e.t[0])[0]:'thing'};
const shapeOf=id=>ROOT_SHAPE[rootOf(id)]||'thing';
const threadColor=id=>`var(--th-${shapeOf(id)})`;
/** 縁の輪郭。人物=円 / 場所=ピン / 会社=建物 / 物=六角 / 書類=紙 */
const knot=(id,size='')=>{const e=E[id];return `<span class="kn ${shapeOf(id)} ${id==='me'?'me':''} ${size}" title="${esc(e?e.n:'')}"><b>${esc(e?e.i:'?')}</b></span>`};
const knots=ids=>ids.length?`<span class="knots">${ids.map(i=>knot(i,'sm')).join('')}</span>`:'';
/** 名前つき参照。未登録は斜線 */
const eref=(id,name)=>id&&E[id]?`<a class="ref" href="#/e/${id}">${knot(id)}${esc(E[id].n)}</a>`:`<span class="ref unreg">${esc(name||'未登録')}</span>`;
const aref=id=>`<a class="ref" href="#/a/${id}" style="padding-left:10px">${IC[ACT_IC[A[id].type]]||IC.activity}${esc(A[id].title||fmtMD(A[id].date))}</a>`;
/** 12週の出会い(週ごとの登場数) */
function encStrip(id){
  const end=new Date(TODAY+'T00:00');const c=Array(12).fill(0);
  actsOf(id).forEach(([k,a])=>{const d=a.parent&&A[a.parent]?A[a.parent].date:a.date;if(!d)return;const w=Math.floor((end-new Date(d+'T00:00'))/(7*864e5));if(w>=0&&w<12)c[11-w]++});
  return `<span class="enc" style="--kc:${threadColor(id)}" aria-label="最近12週の登場">${c.map(n=>`<i class="${n>=3?'h3':n==2?'h2':n==1?'h1':''}"></i>`).join('')}</span>`;
}
function lastMet(id){const ds=actsOf(id).map(([k,a])=>a.parent&&A[a.parent]?A[a.parent].date:a.date).filter(Boolean).sort();return ds.length?ds[ds.length-1]:null}
const agoLabel=d=>{if(!d)return 'まだなし';const n=Math.round((new Date(TODAY+'T00:00')-new Date(d+'T00:00'))/864e5);return n===0?'今日':n===1?'昨日':n<7?`${n}日前`:n<35?`${Math.round(n/7)}週間前`:`${Math.round(n/30)}か月前`};

/* ---- 時間軸 ---- */
const AX_NODE={'支払い':['pay','pay'],'会話':['','talk'],'移動':['','move'],'出来事':['','activity'],'日記':['','diary']};
/** 軸の1行。o:{href,time,node:{cls,icon},t,m,amt,cls,future} */
const axRow=o=>{const tagN=o.href?'a':'div';return `<${tagN} class="ax-row ${o.cls||''} ${o.future?'future':''}" ${o.href?`href="${o.href}"`:''}><span class="ax-time">${o.time&&typeof o.time==='object'?`<b>${o.time.d}日</b><small>${esc(o.time.t||'')}</small>`:esc(o.time??'')}</span><span class="ax-node ${o.node&&o.node.cls||''}">${o.node&&IC[o.node.icon]||''}</span><span class="ax-c"><span class="main"><div class="t">${o.t}</div>${o.m?`<div class="m">${o.m}</div>`:''}</span>${o.amt?`<span class="amt">${o.amt}</span>`:''}${o.right||''}</span></${tagN}>`};
const placeName=r=>r?(r[1]&&E[r[1]]?E[r[1]].n:r[2]):'';
/** 記録(アクティビティ)の1行 */
function momentRow(k,o={}){
  const a=A[k];const [ncls,icon]=AX_NODE[a.type]||['','activity'];
  const pl=(a.roles||[]).find(r=>['支払先','場所','到着'].includes(r[0]));
  const ppl=(a.roles||[]).filter(r=>r[1]&&E[r[1]]&&r[1]!=='me'&&r[1]!==o.exclude&&!['支払先','場所','到着','出発'].includes(r[0])).map(r=>r[1]);
  const x=a.x&&X[a.x];
  const dd=a.parent&&A[a.parent]?A[a.parent].date:'';
  return axRow({href:`#/a/${k}`,time:o.showDate&&dd?{d:+dd.slice(8),t:a.time}:a.time,node:{cls:ncls,icon},cls:o.cls,future:o.future,
    t:esc(a.title),m:`${pl?`<span class="ell">${esc(placeName(pl))}</span>`:''}${knots(ppl)}`,
    amt:a.amt!=null?`<b>${yen(a.amt)}</b>${x&&x.amt!==a.amt?`<small>自分 ${yen(x.amt)}</small>`:''}`:''});
}
/** 予定の1行(中抜き・点線)。late: いまより前で未記録 */
function planRowAx(k,o={}){
  const e=EV[k];const late=o.late;
  return axRow({href:`#/ev/${k}`,time:e.all?'終日':e.s,node:{cls:'plan'+(late?' late':''),icon:'schedule'},cls:'plan',future:o.future,
    t:esc(e.t),m:`${placeOf(e)?`<span class="ell">${esc(placeOf(e))}</span>`:''}${knots((e.who||[]).filter(i=>E[i]))}${late?'<span class="tag warn">記録にする?</span>':''}`,
    amt:e.pay?`<b>${yen(e.pay.amt)}</b><small>支払い予定</small>`:''});
}
/** 日の区切り */
function dayHead(d,o={}){
  const w=new Date(d+'T00:00').getDay();const tagN=o.href?'a':'div';
  return `<${tagN} class="dh" ${o.href?`href="${o.href}"`:''} ${o.future?'style="--rail-c:repeating-linear-gradient(to bottom,var(--plan) 0 4px,#0000 4px 9px)"':''}><span class="d ${w===0?'sun':w===6?'sat':''}"><b>${+d.slice(8)}</b><i>${d===TODAY?'今日':WD[w]+'曜'}</i></span><span class="tick ${d===TODAY?'today':''}"></span><span class="c"><span class="diary ${o.diary?'':'none'}">${o.diary?esc(o.diary):(o.empty||'日記なし')}</span>${o.sum?`<span class="sum">${o.sum}</span>`:''}</span></${tagN}>`;
}
const nowLine=()=>`<div class="now" aria-label="いま ${NOW}"><span class="tm">${NOW}</span><span class="dot"></span><span class="ln"><span>いま</span></span></div>`;
const axEdge=(html,past)=>`<div class="ax-edge ${past?'past':''}"><span class="c">${html}</span></div>`;
/** 詳細の主役: 軸上の点を大きくし、カードを掛ける */
const focusRow=({time,node,card,plan})=>`<div class="ax-row focus ${plan?'plan future':''}"><span class="ax-time" style="padding-top:28px">${esc(time||'')}</span><span class="ax-node ${node.cls||''}">${IC[node.icon]||''}</span><div class="focus-card"><div class="mo-card">${card}</div></div></div>`;
/** 割合の棒。segs:[{v,cls,label,href}] total */
function splitBar(segs,total){
  return `<div class="split"><div class="bar">${segs.map(s=>`<i class="${s.cls}" style="flex:${s.v}"></i>`).join('')}</div><div class="lg">${segs.map(s=>{const c={me:'var(--brand)',other:'var(--th-person)',owed:'var(--warn)',dim:'var(--axis)'}[s.cls];const inner=`${esc(s.label)} <b>${yen(s.v)}</b>`;return s.href?`<a class="lgl" href="${s.href}"><span style="--c:${c}">${inner}</span></a>`:`<span style="--c:${c}">${inner}</span>`}).join('')}</div></div>`;
}

/* ---- 汎用 ---- */
const tag=(label,o={})=>{const inner=`${o.color?`<span class="dot" style="--cc:${o.color}"></span>`:''}${esc(label)}`;return o.href?`<a class="tag ${o.cls||''}" href="${o.href}">${inner}</a>`:`<span class="tag ${o.cls||''}">${inner}</span>`};
const catTag=k=>tag(catPath(k),{color:catColor(k)});
const sh=(title,n,act)=>`<div class="sh"><h2>${esc(title)}</h2>${n!=null&&n!==''?`<span class="n">${n}</span>`:''}${act?`<a class="act" ${act.href?`href="${act.href}"`:`href="javascript:void 0" onclick="${act.onclick}"`}>${act.icon?IC[act.icon]:''}${esc(act.label)}</a>`:''}</div>`;
const list=(rows,cls='')=>`<div class="list ${cls}">${rows.join('')}</div>`;
const row=o=>{const tagN=o.href?'a':o.onclick?'button':'div';const attr=o.href?`href="${o.href}"`:o.onclick?`type="button" onclick="${o.onclick}"`:'';
  return `<${tagN} class="row ${o.cls||''}" ${attr}>${o.time!=null?`<span class="time num">${esc(o.time)}</span>`:''}${o.lead||''}<span class="grow"><div class="t ${o.wrap?'':'ell'}">${o.t}</div>${o.s?`<div class="s ell">${o.s}</div>`:''}</span>${o.v!=null?`<span class="v ${o.amt?'amt num':''}">${o.v}</span>`:''}${o.chev?`<span class="chev">${IC.chev}</span>`:''}</${tagN}>`};
const prop=(k,val,meta)=>`<div class="prop"><span class="k">${esc(k)}</span><span class="val">${val}</span>${meta?`<span class="meta">${meta}</span>`:''}</div>`;
const largeTitle=(title,{actions=[],metrics=[]}={})=>`<header class="lt"><h1>${esc(title)}</h1>${actions.map(a=>`<button class="iconbtn" type="button" aria-label="${esc(a.label)}" onclick="${a.onclick}">${IC[a.icon]}</button>`).join('')}</header>${metrics.length?`<nav class="metrics" aria-label="サマリ">${metrics.map(m=>`<a class="metric ${m.alert?'alert':''}" href="${m.href||'javascript:void 0'}" ${m.onclick?`onclick="${m.onclick}"`:''}><span>${esc(m.l)}</span><b class="num">${m.v}</b></a>`).join('')}</nav>`:''}`;
const appbar=(title='',{back=true,actions=[]}={})=>`<header class="appbar">${back?`<button class="iconbtn" type="button" aria-label="戻る" onclick="goBack()">${IC.back}</button>`:''}<div class="title">${esc(title)}</div>${actions.map(a=>`<button class="iconbtn" type="button" aria-label="${esc(a.label)}" onclick="${a.onclick}">${IC[a.icon]}</button>`).join('')}</header>`;
const seg=(items,cur,on)=>`<div class="seg" role="group">${items.map(([v,l])=>`<button type="button" aria-pressed="${v===cur}" onclick="${on.replace('$v',v)}">${esc(l)}</button>`).join('')}</div>`;
const chips=(items,cur,on)=>`<div class="chips" role="group">${items.map(([v,l,n])=>`<button class="chip" type="button" aria-pressed="${v===cur}" onclick="${on.replace('$v',v)}">${esc(l)}${n!=null?`<span class="n">${n}</span>`:''}</button>`).join('')}</div>`;
const empty=(icon,text,btn)=>`<div class="empty">${ico(icon)}<div>${text}</div>${btn?`<button class="btn sm tonal" type="button" onclick="${btn.onclick}">${esc(btn.label)}</button>`:''}</div>`;
const banner=(kind,html)=>`<div class="banner ${kind}">${IC[kind==='warn'?'warn':'info']}<div class="grow">${html}</div></div>`;
const suggest=(text,btn)=>`<div class="suggest"><span class="t">${text}</span><button class="btn sm" type="button" onclick="${btn.onclick}">${esc(btn.label)}</button></div>`;
const toggle=(on,onclick)=>`<button class="toggle" type="button" role="switch" aria-checked="${!!on}" onclick="${onclick}"></button>`;
const checkbox=(on,onclick,lg)=>`<button class="check ${lg?'lg':''}" type="button" role="checkbox" aria-checked="${!!on}" onclick="event.preventDefault();event.stopPropagation();${onclick}">${on?IC.check:''}</button>`;

/* ---------- ルーター ----------
   route('name', params=>html, {tab})  … #/name/id?k=v
   tab: 下部ナビで選択中にするタブ */
const ROUTES={};
const route=(name,fn,opt={})=>{ROUTES[name]={fn,...opt}};
let CUR={name:'activities',id:null,q:{}};
const parseHash=()=>{const h=location.hash.slice(2)||'activities';const [path,qs]=h.split('?');const [name,...rest]=path.split('/');const q=Object.fromEntries(new URLSearchParams(qs||''));return {name,id:rest.join('/')||null,q}};
const go=h=>{location.hash=h};
const goBack=()=>{history.length>1?history.back():go('#/activities')};
let lastTab='activities';
function render(keepScroll){
  CUR=parseHash();const r=ROUTES[CUR.name]||ROUTES.activities;
  const y=scrollY;
  let html;try{html=r.fn(CUR.id,CUR.q)}catch(e){console.error(e);html=appbar('表示できません')+banner('warn',`この画面を表示できませんでした。<br><small>${esc(e.message)}</small>`)}
  document.getElementById('app').innerHTML=`<main class="screen" id="screen">${html}</main>`;
  if(r.tab)lastTab=r.tab;
  renderNav(r.tab||lastTab);
  if(keepScroll)scrollTo(0,y);else scrollTo(0,0);
  document.title=(document.querySelector('.lt h1,.appbar .title')?.textContent||'個人記録')+' — 個人記録';
}
/** 状態変更後に同じ画面を描き直す(スクロール位置を保つ) */
const rerender=()=>render(true);
addEventListener('hashchange',()=>{closeSheet(true);render()});
addEventListener('scroll',()=>{const b=document.querySelector('.appbar');if(b)b.classList.toggle('scrolled',scrollY>4)},{passive:true});

/* ---------- 下部ナビ ---------- */
const TABS=[
  ['activities','タイムライン','axis'],
  ['plan','予定','schedule'],
  null,
  ['entities','人・場所','people'],
  ['ledger','家計簿','ledger'],
];
const navBadge=k=>k==='plan'?(typeof overdueCount==='function'?overdueCount():0):0;
function renderNav(cur){
  document.getElementById('nav').innerHTML=`<div class="nav-in">${TABS.map(t=>t?`<a href="#/${t[0]}" ${t[0]===cur?'aria-current="page"':''}><span class="pill">${IC[t[2]]}</span>${t[1]}${navBadge(t[0])?`<span class="badge">${navBadge(t[0])}</span>`:''}</a>`:`<div class="fab-slot"><button class="fab" type="button" aria-label="作成" onclick="openCreate()">${IC.plus}</button></div>`).join('')}</div>`;
}

/* ---------- シート ----------
   openSheet({title, body, left:{label,onclick}, right:{label,onclick,primary}, footer, full})
   本文を描き直すときは setSheetBody(html) */
let SHEET_STACK=0;
function openSheet(o){
  const sc=document.getElementById('scrim'),sh=document.getElementById('sheet');
  sh.className='sheet'+(o.full?' full':'');
  sh.setAttribute('aria-label',o.title||'');
  sh.innerHTML=`<div class="grab"></div><div class="sheet-h">${o.left?`<button class="txtbtn" type="button" onclick="${o.left.onclick}">${esc(o.left.label)}</button>`:`<button class="iconbtn" type="button" aria-label="閉じる" onclick="closeSheet()">${IC.close}</button>`}<div class="title">${esc(o.title||'')}</div>${o.right?`<button class="${o.right.primary===false?'txtbtn':'btn sm'}" type="button" ${o.right.disabled?'disabled':''} onclick="${o.right.onclick}">${esc(o.right.label)}</button>`:'<span style="width:44px"></span>'}</div><div class="sheet-b" id="sheet-b">${o.body||''}</div>${o.footer?`<div class="sheet-f">${o.footer}</div>`:''}`;
  requestAnimationFrame(()=>{sc.classList.add('on');sh.classList.add('on')});
  SHEET_STACK=1;document.body.style.overflow='hidden';
}
const setSheetBody=html=>{const b=document.getElementById('sheet-b');if(b){const y=b.scrollTop;b.innerHTML=html;b.scrollTop=y}};
function closeSheet(silent){
  const sc=document.getElementById('scrim'),sh=document.getElementById('sheet');
  if(!sh||!SHEET_STACK)return;sc.classList.remove('on');sh.classList.remove('on');SHEET_STACK=0;document.body.style.overflow='';
}
addEventListener('keydown',e=>{if(e.key==='Escape')closeSheet()});

/* ---------- トースト(取り消し付き) ---------- */
let toastT;
function toast(msg,undo){
  const t=document.getElementById('toast');
  t.innerHTML=`<span>${esc(msg)}</span>${undo?`<button type="button" id="toast-undo">取り消す</button>`:''}`;
  if(undo)document.getElementById('toast-undo').onclick=()=>{undo();t.classList.remove('on');save();rerender()};
  t.classList.add('on');clearTimeout(toastT);toastT=setTimeout(()=>t.classList.remove('on'),undo?5000:2600);
}

/* ---------- 作成シート(中央の＋) ----------
   各画面ファイルが CREATE に登録する: CREATE.push({id,tab,label,sub,cls,icon,onclick,primaryOn:[tab]}) */
const CREATE=[];
function openCreate(){
  const tab=lastTab;
  const items=[...CREATE].sort((a,b)=>(b.primaryOn||[]).includes(tab)-(a.primaryOn||[]).includes(tab));
  const [first,...rest]=items;
  const act=(c,primary)=>`<button class="action ${primary?'primary':''}" type="button" onclick="closeSheet();setTimeout(()=>{${c.onclick}},120)">${ico(c.icon,c.color||'var(--brand)')}<span class="grow"><div class="t">${esc(c.label)}</div>${c.sub?`<div class="s">${esc(c.sub)}</div>`:''}</span></button>`;
  openSheet({title:'作成',body:`<div class="actions">${first?act(first,true):''}${rest.map(c=>act(c)).join('')}</div>`});
}

/* ---------- 保存(このブラウザ) ---------- */
const SKEY='prmock-v3';
function save(){try{localStorage.setItem(SKEY,JSON.stringify({A,X,E,EV,TD,TYPES,CATS,NOTES,LISTS,TYPE_PROPS,EXTRA:typeof EXTRA!=='undefined'?EXTRA:{}}))}catch(e){}}
function load(){try{const s=JSON.parse(localStorage.getItem(SKEY)||'null');if(!s)return;
  for(const [k,o] of Object.entries({A,X,E,EV,TD,TYPES,CATS,NOTES,LISTS,TYPE_PROPS})){if(s[k]){for(const kk of Object.keys(o))delete o[kk];Object.assign(o,s[k])}}
  if(s.EXTRA&&typeof EXTRA!=='undefined')Object.assign(EXTRA,s.EXTRA);}catch(e){}}
function resetData(){try{localStorage.removeItem(SKEY)}catch(e){}location.hash='#/activities';location.reload()}
/** 画面ファイルが自由に使える追加の保存領域 */
const EXTRA={};
const uid=p=>p+Date.now().toString(36)+Math.random().toString(36).slice(2,5);

/* ---------- このモックについて ---------- */
function openAbout(){
  openSheet({title:'このモックについて',body:`
    ${sh('概要')}${list([prop('版','UI v3(プロダクト品質の再設計)'),prop('データ','架空。操作はこのブラウザに保存'),prop('今日','2026年10月31日(土) として表示')])}
    ${sh('表示')}${list([row({t:'テーマ',v:`<select class="input" style="height:36px;width:auto" onchange="setTheme(this.value)"><option value="">端末に合わせる</option><option value="light">ライト</option><option value="dark">ダーク</option></select>`})])}
    ${sh('データ')}<div class="btnrow"><button class="btn danger" type="button" onclick="resetData()">${IC.trash}データを初期状態に戻す</button></div>
    <p class="hint">旧版(UI v2)は別のURLに残しています。<a href="../" style="color:var(--brand)">UI v2 を開く</a></p>`});
}
function setTheme(v){if(v)document.documentElement.dataset.theme=v;else delete document.documentElement.dataset.theme;try{localStorage.setItem('prmock-theme',v)}catch(e){}}
try{const t=localStorage.getItem('prmock-theme');if(t)document.documentElement.dataset.theme=t}catch(e){}

