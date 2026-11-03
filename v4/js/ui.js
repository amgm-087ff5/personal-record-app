/* =====================================================================
   UI v3 — 共通部品・ルーター・シート・状態保存
   画面ファイル(js/screens/*.js)はここの関数だけで組み立てる。
   ===================================================================== */

/* ---------- アイコン(24グリッド、線2px。AndroidのVectorDrawableへ移せる) ---------- */
const sv=(d,fill)=>`<svg class="i" viewBox="0 0 24 24" aria-hidden="true" fill="${fill?'currentColor':'none'}" stroke="${fill?'none':'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
const IC={
  future:sv('<path d="M5 2.5v19" stroke-dasharray="2 2.6"/><rect x="8.5" y="8" width="13" height="8" rx="4" stroke-dasharray="2.4 1.8"/>'),
  moment:sv('<path d="M5 2.5v19"/><rect x="8.5" y="8" width="13" height="8" rx="4" fill="currentColor"/>'),
  lines:sv('<path d="M5 7h14M5 12h14M5 17h9"/>'),
  cats:sv('<rect x="3" y="5" width="8" height="4" rx="2" fill="currentColor"/><rect x="3" y="11" width="13" height="4" rx="2"/><rect x="3" y="17" width="18" height="4" rx="2"/>'),
  spark:sv('<path d="M12 4v4M12 16v4M4 12h4M16 12h4M6.5 6.5l2.5 2.5M15 15l2.5 2.5M17.5 6.5 15 9M9 15l-2.5 2.5"/>'),
  kin:sv('<circle cx="8" cy="9.5" r="4.5"/><path d="M16.5 21.5s-5-4.3-5-8.2a5 5 0 0 1 10 0c0 3.9-5 8.2-5 8.2Z"/><circle cx="16.5" cy="13.3" r="1.6" fill="currentColor"/>'),
  share:sv('<rect x="2.5" y="6.5" width="19" height="7" rx="1.5"/><rect x="2.5" y="6.5" width="8.5" height="7" rx="1.5" fill="currentColor"/><path d="M2.5 17v2.5h8.5V17"/>'),
  axis:sv('<path d="M6 2.5v19"/><rect x="3.5" y="4.5" width="5" height="5" rx="1.6" fill="currentColor"/><circle cx="6" cy="16" r="2.4"/><path d="M11.5 7h9M11.5 16h7"/>'),
  activity:sv('<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>'),
  schedule:sv('<rect x="3" y="7" width="18" height="10" rx="5" stroke-dasharray="2.6 2"/>'),
  todo:sv('<rect x="3.5" y="3.5" width="17" height="17" rx="4.5"/><path d="m8 12.5 3 3 5-6"/>'),
  entity:sv('<path d="M3 6.5A2.5 2.5 0 0 1 5.5 4h4.2l2 2.5h6.8A2.5 2.5 0 0 1 21 9v9.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5Z"/>'),
  note:sv('<path d="M6 3h8.5L20 8.5V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>'),
  type:sv('<path d="M3 12 10 4.5h9.5A1.5 1.5 0 0 1 21 6v12a1.5 1.5 0 0 1-1.5 1.5H10Z"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/>'),
  expense:sv('<rect x="2.5" y="8.5" width="19" height="7" rx="1.5"/><rect x="2.5" y="8.5" width="8.5" height="7" rx="1.5" fill="currentColor"/>'),
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
  up:sv('<path d="m6 15 6-6 6 6"/>'),
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
const ico=(icon,c='var(--tx-2)')=>`<span class="ico" style="--ic:${c}">${IC[icon]||''}</span>`;

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
const eref=(id,name,shape='place')=>id&&E[id]?`<a class="ref" href="#/e/${id}">${knot(id)}${esc(E[id].n)}</a>`:`<span class="ref unreg"><span class="kn ${shape} unreg"><b>${esc((name||'?')[0])}</b></span>${esc(name||'未登録')}</span>`;
const aref=id=>`<a class="ref" href="#/a/${id}">${IC[ACT_IC[A[id].type]]||IC.activity}${esc(A[id].title||fmtMD(A[id].date))}</a>`;
/** 12週の出会い(週ごとの登場数) */
function encStrip(id){
  const end=new Date(TODAY+'T00:00');const c=Array(12).fill(0);
  actsOf(id).forEach(([k,a])=>{const d=a.parent&&A[a.parent]?A[a.parent].date:a.date;if(!d)return;const w=Math.floor((end-new Date(d+'T00:00'))/(7*864e5));if(w>=0&&w<12)c[11-w]++});
  return `<span class="enc" style="--kc:${threadColor(id)}" aria-label="最近12週の登場">${c.map(n=>`<i class="${n>=3?'h3':n==2?'h2':n==1?'h1':''}"></i>`).join('')}</span>`;
}
function lastMet(id){const ds=actsOf(id).map(([k,a])=>a.parent&&A[a.parent]?A[a.parent].date:a.date).filter(Boolean).sort();return ds.length?ds[ds.length-1]:null}
const agoLabel=d=>{if(!d)return 'まだなし';const n=Math.round((new Date(TODAY+'T00:00')-new Date(d+'T00:00'))/864e5);return n===0?'今日':n===1?'昨日':n<7?`${n}日前`:n<35?`${Math.round(n/7)}週間前`:`${Math.round(n/30)}か月前`};

/* ---- 時間軸 ---- */
const AX_NODE={'支払い':['pay','pay'],'会話':['','talk'],'移動':['','move'],'出来事':['','spark'],'日記':['','diary']};
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
  return axRow({href:`#/a/${k}`,time:o.showDate&&dd?{d:+dd.slice(8),t:a.time}:a.time,node:{cls:ncls,icon},cls:(o.cls||'')+(a.type==='支払い'?' pay':''),future:o.future,
    t:esc(a.title),m:`${o.role?`<span class="role">${esc(o.role)}</span>`:''}${pl&&pl[1]!==o.exclude?`<span class="ell">${esc(placeName(pl))}</span>`:''}${knots(ppl)}`,
    amt:a.amt!=null?`<b>${yen(a.amt)}</b>${x&&x.amt!==a.amt?payBar(a,x):''}`:''});
}
/** 1つの支払いの棒: 自分(青) / ほかの人(灰) / 立替・未精算(琥珀の斜線) */
function payBar(a,x){const rest=a.amt-x.amt;const owed=a.adv&&a.adv[2]==='未精算';
  return `<span class="pbar" aria-label="支払い ${yen(a.amt)} のうち自分 ${yen(x.amt)}"><i class="me" style="flex:${x.amt}"></i>${rest>0?`<i class="${owed?'owed':'other'}" style="flex:${rest}"></i>`:''}</span>`}
/** 予定の1行(中抜き・点線)。late: いまより前で未記録 */
function planRowAx(k,o={}){
  const e=EV[k];const late=o.late;
  const od=e.rep?nextOn(e):e.d;const tm=e.all?'終日':e.s;
  return axRow({href:`#/ev/${k}`,time:o.showDate?{d:+od.slice(8),t:tm}:tm,node:{cls:'plan'+(late?' late':''),icon:''},cls:'plan',future:o.future,
    t:esc(e.t),m:`${placeOf(e)&&!(e.place&&e.place[0]===o.exclude)?`<span class="ell">${esc(placeOf(e))}</span>`:''}${knots((e.who||[]).filter(i=>E[i]&&i!==o.exclude))}${late?'<span class="tag late">記録にする?</span>':''}`,
    amt:e.pay?`<b>${yen(e.pay.amt)}</b><small>支払い予定</small>`:''});
}
/** 日の区切り */
function dayHead(d,o={}){
  const w=new Date(d+'T00:00').getDay();const tagN=o.href?'a':'div';
  const txt=o.diary?esc(o.diary):(o.empty||'');
  return `<${tagN} class="dh2 ${d===TODAY?'today':''} ${o.future?'future':''} ${o.noax?'noax':''}" ${o.href?`href="${o.href}"`:''}><span class="dcard"><span class="dnum" aria-hidden="true"><b>${+d.slice(8)}</b><small>${WD[w]}</small></span><span class="dbody"><span class="dt"><b>${+d.slice(5,7)}月${+d.slice(8)}日 ${WD[w]}曜</b>${d===TODAY?'<span class="td">今日</span>':''}<span class="grow"></span>${o.sum?`<span class="sum">${o.sum}</span>`:''}</span>${txt?`<span class="dd ${o.diary?'':'none'}">${txt}</span>`:''}</span></span></${tagN}>`;
}
/** v4: 1日(または1か月)=1枚の面。head=日付の札(dayHead/monthHeadAx)、rows=その日の行。
    o.future=面ごと未来(点線の軸) o.cls=追加クラス。軸は面の左の溝を途切れず通る(各部品が自分の区間を描く) */
const axGroup=(head,rows,o={})=>`<section class="axg ${o.future?'future':''} ${o.cls||''}"><div class="axp">${head||''}${rows?`<div class="axp-rows">${rows}</div>`:''}</div></section>`;
/** v4: 半券チケット=自分の支出(DESIGN.md v4)。どの画面でもこの1つの部品を使う。
    o:{href,onclick,stub(半券の中身HTML),t(題名),s(補足HTML),amt(金額HTML),cls:'fill'(主役・塗り)|'lg'(詳細の最上部)|'', label} */
const ticket=o=>{const tagN=o.href?'a':o.onclick?'button':'div';const attr=o.href?`href="${o.href}"`:o.onclick?`type="button" onclick="${o.onclick}"`:'';
  return `<${tagN} class="tkt-w ${o.cls||''}" ${attr} ${o.label?`aria-label="${esc(o.label)}"`:''}><span class="tkt"><span class="tkt-s">${o.stub||IC.expense||''}</span><span class="tkt-b"><span class="main">${o.t?`<span class="t">${o.t}</span>`:''}${o.s?`<span class="s">${o.s}</span>`:''}</span>${o.amt!=null?`<span class="amt num">${o.amt}</span>`:''}${o.chev?`<span class="chev">${IC.chev}</span>`:''}</span></span></${tagN}>`};
/** v4: 期間の移動(タイムライン・予定・家計簿で同じ部品)。‹ › は「戻る・開く」の意味なので使わず、文字で「前の月/次の月」。
    o:{label,sub,prev:{on,dis,text},next:{on,dis,text}} */
const periodNav=o=>`<div class="pnav" role="group" aria-label="期間"><button type="button" ${o.prev.dis?'disabled':''} onclick="${o.prev.on}">${esc(o.prev.text||'前の月')}</button><b class="num">${o.label}${o.sub?`<small>${o.sub}</small>`:''}</b><button type="button" ${o.next.dis?'disabled':''} onclick="${o.next.on}">${esc(o.next.text||'次の月')}</button></div>`;
/** 月の区切り(縁の時間軸) */
const monthHeadAx=(ym,future)=>`<div class="dh2 month ${future?'future':''}"><span class="dcard"><span class="dt"><b>${ym.slice(0,4)}年${+ym.slice(5)}月</b></span></span></div>`;
const nowLine=()=>`<div class="now" aria-label="いま ${NOW}"><span class="tm">${NOW}</span><span class="dot"></span><span class="ln"></span></div>`;
const axEdge=(html,past)=>`<div class="ax-edge ${past?'past':''}"><span class="c">${html}</span></div>`;
/** 詳細の主役: 軸上の点を大きくし、カードを掛ける */
const focusRow=({time,node,card,plan})=>`<div class="ax-row focus ${plan?'plan future':''}"><span class="ax-time" style="padding-top:28px">${esc(time||'')}</span><span class="ax-node ${node.cls||''}">${IC[node.icon]||''}</span><div class="focus-card"><div class="mo-card">${card}</div></div></div>`;
/** 割合の棒。segs:[{v,cls,label,href}] total */
function splitBar(segs,total){
  return `<div class="split"><div class="bar">${segs.map(s=>`<i class="${s.cls}" style="flex:${s.v}"></i>`).join('')}</div><div class="lg">${segs.map(s=>{const c={me:'var(--me)',other:'var(--axis)',owed:'var(--owe)',dim:'var(--axis)'}[s.cls];const inner=`${esc(s.label)}${s.noAmt?'':` <b>${yen(s.v)}</b>`}`;return s.href?`<a class="lgl" href="${s.href}"><span style="--c:${c}">${inner}</span></a>`:`<span style="--c:${c}">${inner}</span>`}).join('')}</div></div>`;
}

/** 独立したオブジェクトの並び(1件=1つの面) */
const objs=rows=>`<div class="objs">${rows.join('')}</div>`;
/** 詳細の最上部: 細い軸に掛かったカプセル。
    later/earlier の要素: {day} 日の区切り | {now} いまの線 | {time,title,href,icon,plan,late,fut} 前後の点。fut=いまより未来 */
function pinView({later=[],earlier=[],cap,future,plan,late}){
  const n=x=>x.now?`<div class="pin-now fut-b" aria-label="いま ${NOW}"><span>いま ${NOW}</span></div>`
    :x.day?dayHead(x.day,{href:'#/day/'+x.day,future:x.fut})
    :`<a class="pin-n ${x.plan?'plan':''} ${x.late?'late':''} ${x.fut?'fut':''}" href="${x.href}"><span class="pn">${x.icon&&!x.plan?IC[x.icon]:''}</span><span class="tm">${esc(x.time||'')}</span><span class="t">${esc(x.title)}</span></a>`;
  return `<div class="pin">${later.map(n).join('')}<div class="pin-cur ${future?'fut':''} ${plan?'plan':''} ${late?'late':''}"><span class="node"></span>${cap}</div>${earlier.map(n).join('')}</div>`;
}
/* ---- 汎用 ---- */
const tag=(label,o={})=>{const inner=`${o.color?`<span class="dot" style="--cc:${o.color}"></span>`:''}${esc(label)}`;return o.href?`<a class="tag ${o.cls||''}" href="${o.href}">${inner}</a>`:`<span class="tag ${o.cls||''}">${inner}</span>`};
const catTag=k=>tag(catPath(k),{color:catColor(k)});
const sh=(title,n,act)=>`<div class="sh"><h2>${esc(title)}</h2>${n!=null&&n!==''?`<span class="n">${n}</span>`:''}${act?`<a class="act" ${act.href?`href="${act.href}"`:`href="javascript:void 0" onclick="${act.onclick}"`}>${act.icon?IC[act.icon]:''}${esc(act.label)}</a>`:''}</div>`;
const list=(rows,cls='')=>`<div class="list ${cls}">${rows.join('')}</div>`;
const row=o=>{const tagN=o.href?'a':o.onclick?'button':'div';const attr=o.href?`href="${o.href}"`:o.onclick?`type="button" onclick="${o.onclick}"`:'';
  return `<${tagN} class="row ${o.cls||''}" ${attr}>${o.time!=null?`<span class="time num">${esc(o.time)}</span>`:''}${o.lead||''}<span class="grow"><div class="t ${o.wrap?'':'ell'}">${o.t}</div>${o.s?`<div class="s ell">${o.s}</div>`:''}</span>${o.v!=null?`<span class="v ${o.amt?'amt num':''}">${o.v}</span>`:''}${o.chev?`<span class="chev">${IC.chev}</span>`:''}</${tagN}>`};
const prop=(k,val,meta)=>`<div class="prop"><span class="k">${esc(k)}</span><span class="val">${val}</span>${meta?`<span class="meta">${meta}</span>`:''}</div>`;
const largeTitle=(title,{actions=[],metrics=[]}={})=>`<header class="lt"><h1>${esc(title)}</h1>${actions.map(a=>a.text?`<button class="btn sm ${a.primary?'lt-cta':'ghost'}" type="button" onclick="${a.onclick}">${a.icon?IC[a.icon]:''}${esc(a.text)}</button>`:`<button class="iconbtn" type="button" aria-label="${esc(a.label)}" onclick="${a.onclick}">${IC[a.icon]}</button>`).join('')}</header>`;
const appbar=(title='',{back=true,actions=[]}={})=>`<header class="appbar">${back?`<button class="iconbtn" type="button" aria-label="戻る" onclick="goBack()">${IC.back}</button>`:''}<div class="title">${esc(title)}</div>${actions.map(a=>`<button class="iconbtn" type="button" aria-label="${esc(a.label)}" onclick="${a.onclick}">${IC[a.icon]}</button>`).join('')}</header>`;
/* ---- 品目 = 金額行(lines)の配列。もとの本体も1行目。行がない品目は合計(price)だけ持つ。保存形の price は常に合計 ----
   行: {n, k:'amt'|'pct', v}。pct は直前までの小計にかける(−10 = 10%オフ)。編集中は v を絶対値、sg を符号にして持つ */
const lnV=l=>l.sg!=null?l.sg*(l.v||0):(l.v||0);
const lnAmts=lines=>{let s=0;return lines.map((l,j)=>{const a=l.k==='pct'?(j?Math.round(s*lnV(l)/100):0):lnV(l);s+=a;return a})};
const itemAmt=it=>it.lines&&it.lines.length?lnAmts(it.lines).reduce((s,a)=>s+a,0):(it.price||0);
const lnKey=it=>it.lines?JSON.stringify(it.lines.map(l=>[l.n||'',l.k||'amt',lnV(l)])):'';
const lnName=l=>l.n||(lnV(l)<0?'割引':'オプション');
const lnTxt=(l,a)=>`${a<0?'−':'＋'}${esc(lnName(l))} ${l.k==='pct'?Math.abs(lnV(l))+'%':yen(Math.abs(a))}`;
const lnSumHtml=it=>{if(!(it.lines&&it.lines.length>1))return '';const am=lnAmts(it.lines);
  return [`本体 ${yen(am[0])}`,...it.lines.slice(1).map((l,j)=>lnTxt(l,am[j+1]))].map(s=>`<span class="adj-l">${s}</span>`).join('')};
const lnEq=it=>{const am=lnAmts(it.lines);return [`本体 ${yen(am[0])}`,...it.lines.slice(1).map((l,j)=>lnTxt(l,am[j+1])+(l.k==='pct'?`(${am[j+1]<0?'−':'＋'}${yen(Math.abs(am[j+1]))})`:''))].join(' ')};
/** 保存形の行(2行以上のときだけ持つ。0の追加行は捨てる) */
const lnSave=it=>{if(!(it.lines&&it.lines.length>1))return undefined;const r=it.lines.map((l,j)=>({n:j?lnName(l):'',k:l.k||'amt',v:lnV(l)})).filter((l,j)=>j===0||l.v!==0);return r.length>1?r:undefined};
/** 保存形の品目 → 編集用(絶対値+符号) */
const itemToEdit=it=>({...it,lines:it.lines?it.lines.map(l=>({n:l.n,k:l.k,v:Math.abs(l.v),sg:l.v<0?-1:1})):undefined});
/** 旧データ(UI v9の本体+adj)を lines に移す */
const itemMigrate=it=>{if(it&&it.adj){it.lines=[{n:'',k:'amt',v:it.price||0},...it.adj.map(a=>({n:a.n,k:'amt',v:a.amt}))];delete it.adj;it.price=itemAmt(it)}};

/** 品目の編集部品(支払いの編集・レシート確認で共通)。1品目ずつ開く。P=名前(ip / cr)。
    o={items:()=>配列, redraw:()=>全体を描き直す, upd:i=>入力中の合計だけ更新} */
const IED={};
function itemEd(P,o){
  const ed=IED[P]={open:0};
  const foc=sel=>setTimeout(()=>{const e=document.querySelectorAll(sel);if(e.length)e[e.length-1].focus()},30);
  const num=(v,pct)=>pct?(parseFloat(String(v).replace(/[^\d.]/g,''))||0):(+String(v).replace(/[^\d]/g,'')||0);
  ed.tg=i=>{ed.open=ed.open===i?-1:i;o.redraw()};
  ed.set=(i,k,v)=>{const it=o.items()[i];if(!it)return;
    if(k==='price'){it.price=num(v);o.upd(i)}else if(k==='tax'){it.tax=+v;o.redraw()}else it[k]=v};
  ed.add=()=>{o.items().push({n:'',price:0,tax:8,b:'自分'});ed.open=o.items().length-1;o.redraw();foc('.ie-o .ie-n')};
  ed.del=i=>{o.items().splice(i,1);ed.open=-1;o.redraw()};
  ed.ladd=i=>{const it=o.items()[i];if(!it)return;if(!it.lines)it.lines=[{n:'',k:'amt',v:it.price||0,sg:1}];it.lines.push({n:'',k:'amt',v:0,sg:1});ed.open=i;o.redraw();foc('.ie-o .ie-l .ie-ln')};
  ed.lset=(i,j,k,v,re)=>{const l=((o.items()[i]||{}).lines||[])[j];if(!l)return;
    if(k==='v')l.v=num(v,l.k==='pct');else if(k==='kind'){l.k=v[0]==='p'?'pct':'amt';l.sg=v[1]==='-'?-1:1}else l.n=v;
    if(re)o.redraw();else o.upd(i)};
  ed.ldel=(i,j)=>{const it=o.items()[i];it.lines.splice(j,1);if(it.lines.length===1){it.price=lnV(it.lines[0]);delete it.lines}o.redraw()};
  ed.eq=i=>{const it=o.items()[i];return it&&it.lines?`<span>${lnEq(it)}</span><b class="num">= ${yen(itemAmt(it))}</b>`:''};
  ed.html=()=>{const items=o.items();
    return list(items.map((it,i)=>{
      const amt=itemAmt(it);
      if(ed.open!==i)return row({cls:'plain',t:esc(it.n||'品名なし'),s:lnSumHtml(it)||`税${it.tax||8}%`,v:`<span class="num" style="font-weight:600">${yen(amt)}</span>`,onclick:`IED.${P}.tg(${i})`});
      const kinds=[['a+','＋円'],['a-','−円'],['p+','＋%'],['p-','−%']];
      const lines=it.lines?`<div class="ie-ls">`+it.lines.map((l,j)=>{const k=(l.k==='pct'?'p':'a')+((l.sg||1)<0?'-':'+');
        return j===0
          ?`<div class="ie-l"><span class="ie-lab">本体</span><span></span><input class="ie-v num" inputmode="numeric" value="${l.v||''}" placeholder="¥0" aria-label="本体の金額" oninput="IED.${P}.lset(${i},0,'v',this.value)"><span></span></div>`
          :`<div class="ie-l"><select class="ie-k" aria-label="種類" onchange="IED.${P}.lset(${i},${j},'kind',this.value,1)">${kinds.map(([v,t])=>`<option value="${v}" ${v===k?'selected':''}>${t}</option>`).join('')}</select><input class="ie-ln" value="${esc(l.n)}" placeholder="${(l.sg||1)<0?'割引':'オプション'}" aria-label="名前" oninput="IED.${P}.lset(${i},${j},'n',this.value)"><input class="ie-v num" inputmode="${l.k==='pct'?'decimal':'numeric'}" value="${l.v||''}" placeholder="${l.k==='pct'?'0%':'¥0'}" aria-label="${l.k==='pct'?'割合':'金額'}" oninput="IED.${P}.lset(${i},${j},'v',this.value)"><button class="ie-d" type="button" aria-label="この行を削除" onclick="IED.${P}.ldel(${i},${j})">${IC.close}</button></div>`}).join('')+`<div class="ie-eq" id="ie-eq-${P}-${i}">${ed.eq(i)}</div></div>`
        :`<label class="ie-a"><span>金額</span><input class="ie-p num" inputmode="numeric" value="${it.price||''}" placeholder="¥0" aria-label="品目の金額" oninput="IED.${P}.set(${i},'price',this.value)"></label>`;
      return `<div class="ie-o"><div class="ie-top"><input class="ie-n" value="${esc(it.n)}" placeholder="品名" aria-label="品名" oninput="IED.${P}.set(${i},'n',this.value)"><button class="ie-c" type="button" aria-label="閉じる" onclick="IED.${P}.tg(${i})">${IC.chev}</button></div>`
        +lines
        +`<button class="ie-add" type="button" onclick="IED.${P}.ladd(${i})">${IC.plus}オプション・割引を追加</button>`
        +`<div class="ie-foot"><span class="ie-tax" role="group" aria-label="税率">${[8,10].map(t=>`<button class="cr-b" type="button" aria-pressed="${+it.tax===t}" onclick="IED.${P}.set(${i},'tax',${t})">税${t}%</button>`).join('')}</span><button class="ie-del" type="button" onclick="IED.${P}.del(${i})">${IC.trash}削除</button></div></div>`;
    }),'flat')};
  return ed;
}
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
  const safe=(fn,id,q)=>{try{return fn(id,q)}catch(e){console.error(e);return appbar('表示できません')+banner('warn',`この画面を表示できませんでした。<br><small>${esc(e.message)}</small>`)}};
  const ROOT={activities:'activities',plan:'plan',entities:'entities',ledger:'ledger'};
  const wide=innerWidth>=1100;const rootName=ROOT[r.tab||lastTab]||'activities';
  const app=document.getElementById('app');
  if(wide){
    const isRoot=CUR.name===rootName;const ly=app.querySelector('.pane-l')?.scrollTop||0;const sameRoot=app.dataset.root===rootName;
    const left=safe(ROUTES[rootName].fn,null,isRoot?CUR.q:{});
    const right=isRoot?'':safe(r.fn,CUR.id,CUR.q);
    app.innerHTML=`<section class="pane-l">${left}</section><main class="pane-r screen" id="screen">${right}</main>`;app.dataset.root=rootName;
    if(sameRoot)app.querySelector('.pane-l').scrollTop=ly;
    app.querySelectorAll(`.pane-l a[href="${location.hash}"]`).forEach(a=>a.classList.add('sel'));
  }else{
    app.innerHTML=`<main class="screen" id="screen">${safe(r.fn,CUR.id,CUR.q)}</main>`;delete app.dataset.root;
  }
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
  ['plan','予定','future'],
  null,
  ['entities','人・場所','kin'],
  ['ledger','家計簿','share'],
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
  const act=(c,primary)=>`<button class="action ${primary?'primary':''}" type="button" onclick="closeSheet();setTimeout(()=>{${c.onclick}},120)">${ico(c.icon,c.color||'var(--brand)')}<span class="grow"><div class="t">${esc(c.label)}</div></span></button>`;
  openSheet({title:'作成',body:`<div class="actions">${first?act(first,true):''}${rest.map(c=>act(c)).join('')}</div>`});
}

/* ---------- 保存(このブラウザ) ---------- */
const SKEY='prmock-v4';
function save(){try{localStorage.setItem(SKEY,JSON.stringify({A,X,E,EV,TD,TYPES,CATS,NOTES,LISTS,TYPE_PROPS,EXTRA:typeof EXTRA!=='undefined'?EXTRA:{}}))}catch(e){}}
function load(){try{const s=JSON.parse(localStorage.getItem(SKEY)||'null');if(!s)return;
  for(const [k,o] of Object.entries({A,X,E,EV,TD,TYPES,CATS,NOTES,LISTS,TYPE_PROPS})){if(s[k]){for(const kk of Object.keys(o))delete o[kk];Object.assign(o,s[k])}}
  if(s.EXTRA&&typeof EXTRA!=='undefined')Object.assign(EXTRA,s.EXTRA);
  Object.values(A).forEach(a=>(a.items||[]).forEach(itemMigrate));
  /* 旧データの移行: 以前は「設定なし」が暗黙に 変動/日常 だった。大分類は明示の値に置き換える(以後 'none' = 設定しない) */
  for(const k of Object.keys(CATS)){const c=CATS[k];if(c.fx==='none')c.fx='ask';if(c.sp==='none')c.sp='ask';if(!c.p){if(c.fx===undefined)c.fx='変動';if(c.sp===undefined)c.sp='日常'}}
  }catch(e){}}
function resetData(){try{localStorage.removeItem(SKEY)}catch(e){}location.hash='#/activities';location.reload()}
/** 画面ファイルが自由に使える追加の保存領域 */
const EXTRA={};
const uid=p=>p+Date.now().toString(36)+Math.random().toString(36).slice(2,5);

/* ---------- このモックについて ---------- */
function openAbout(){
  openSheet({title:'このモックについて',body:`
    ${sh('概要')}${list([prop('版',window.BUILD||'不明'),prop('データ','架空。操作はこのブラウザに保存'),prop('今日','2026年10月31日(土) として表示')])}
    ${sh('表示')}${list([row({t:'テーマ',v:`<select class="input" style="height:36px;width:auto" onchange="setTheme(this.value)"><option value="">端末に合わせる</option><option value="light">ライト</option><option value="dark">ダーク</option></select>`})])}
    ${sh('データ')}<div class="btnrow"><button class="btn danger" type="button" onclick="resetData()">${IC.trash}データを初期状態に戻す</button></div>
    ${sh('ほかの版')}${list([row({t:'UI v3',href:'../v3/',chev:true}),row({t:'UI v2',href:'../',chev:true})])}`});
}
function setTheme(v){if(v)document.documentElement.dataset.theme=v;else delete document.documentElement.dataset.theme;try{localStorage.setItem('prmock-theme',v)}catch(e){}}
try{const t=localStorage.getItem('prmock-theme');if(t)document.documentElement.dataset.theme=t}catch(e){}


addEventListener('resize',()=>{const w=innerWidth>=1100;if(w!==(document.getElementById('app').dataset.root!=null))render(true)});
