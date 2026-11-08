/* =====================================================================
   タイムライン — アプリの背骨。上が過去、下が未来、「いま」が境目(v4.7 ユーザー指示「上が過去で下に未来」)。
   ルート: #/activities , #/a/{id}(出来事の詳細=その点の前後を切り取った軸), #/day/{date}(日のページ)
   ===================================================================== */

let actFilter='all';let actMonth=TODAY.slice(0,7);
/* v4.13: 表示(リスト/日/月)。#/activities?view=… で持つ。wide の左の一覧(別の画面の時)は最後に選んだ表示 */
let actViewNow='list';
/** 表示の切替。日と月は位置を引き継ぐ(リストの月 ⇔ 日・月の月)。描き直しは hashchange が行う */
function actSetView(v){
  const cur=actViewNow;if(v===cur)return;
  if(v==='list')actMonth=plSel.slice(0,7);
  else if(cur==='list')plFromMonth(actMonth);
  location.replace('#/activities?view='+v);
}
const monthShift=n=>{const [y,m]=actMonth.split('-').map(Number);const d=new Date(y,m-1+n,1);actMonth=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');landWant=actMonth===TODAY.slice(0,7)?'today':'bottom';rerender()};
const ACT_FILTERS=[['all','すべて'],['予定','予定'],['支払い','支払い'],['会話','会話'],['移動','移動']];
/* ---------- 開いた時・月の切替の位置合わせ(上=過去・下=未来。開いた時は「いま」の線を上に) ----------
   ui.js の render は描画の後に scrollTo(0,…) するので、位置合わせは次の tick に回す。
   開いた時 = hashchange(描画の後に走る)と初回の描画の後。絞り込みや削除など同じ画面の描き直しは位置を保つ(landWant は月の切替だけ) */
let landWant=null;
/** 要素を囲むスクロール枠(幅が広い時は左の一覧)。無ければ null(窓)。 */
function scrollBoxOf(el){let b=el.parentElement;while(b&&b!==document.body&&!(/(auto|scroll)/.test(getComputedStyle(b).overflowY)&&b.scrollHeight>b.clientHeight))b=b.parentElement;return b&&b!==document.body?b:null}
/** 要素の上端を、その枠の上から top px の位置へ(瞬時)。end なら枠の一番下へ */
function landTo(el,{top=12,end=false}={}){
  const s=scrollBoxOf(el),box=s||window;
  if(end){box.scrollTo({top:s?s.scrollHeight:document.documentElement.scrollHeight,behavior:'instant'});return}
  const off=s?s.getBoundingClientRect().top-s.scrollTop:-scrollY;
  box.scrollTo({top:el.getBoundingClientRect().top-off-top,behavior:'instant'});
}
/* ---------- v4.11: 上部(固定)とページ全体のスクロール ----------
   上部 #act-top は position:fixed(幅が広い時は左の一覧 .pane-l の上に合わせる)。
   縮み具合 actP(0=広い / 1=縮んだ)は、動いた量 ÷ 縮められる幅(v4.14: ACT_R=146px)で決まる。どこでスクロールしても、動いた分だけ縮み・戻る。
   タイムラインは自分の枠を持たず、ページ(または一覧)がそのまま動く。
   開いた時・月の切替は、今日の札が上部のすぐ下(8px)に来る位置へ。いまの時刻は使わない。 */
/* v4.14: 縮められる量(px)。css/s-activities.css の --act-r(146px)と同じ値。まとめの縮み 86 + 絞り込みの行 60。上部の高さは CSS で固定し、縮みは transform/opacity だけで見せる */
const ACT_R=146;
let actP=0,actLastY=null,actRaf=0,actWasOn=false,actFullH=0,actSettleT=0,actSnapRaf=0;
const actHdr=()=>document.getElementById('act-top');
/** 動くのが一覧の枠(幅が広い時の .pane-l)なら枠、窓なら null */
const actScroller=()=>innerWidth>=1100?document.querySelector('#app .pane-l'):null;
const actY=s=>s?s.scrollTop:scrollY;
/** 要素の上端の位置(文書の座標。枠があれば枠の中の座標) */
const actDocTop=(el,s)=>s?el.getBoundingClientRect().top-s.getBoundingClientRect().top+s.scrollTop:el.getBoundingClientRect().top+scrollY;
const actVH=s=>s?s.clientHeight:innerHeight;
const actH=s=>s?s.scrollHeight:document.documentElement.scrollHeight;
const actSet=(s,y)=>{if(s)s.scrollTop=y;else scrollTo({top:y,behavior:'instant'})};
/** v4.11: 縮み具合を上部に反映(CSS の --p と、縮んだ時の表示の切り替え)
    v4.14: 位置用のイージング --e(easeInOutCubic)と、消える・出る途中の --fo / --fi を一緒に書く。どれも transform/opacity だけに効く(レイアウトは変わらない)
    クラスは境目を越えた時だけ付け外しする: is-fold(p>0.4 で絞り込み・ボタンの段を隠す) / is-line(p>0.6 で作る丸ボタンを出す) */
function actApply(h){h=h||actHdr();if(!h)return;const p=actP;
  const e=p<.5?4*p*p*p:1-Math.pow(-2*p+2,3)/2;
  h.style.setProperty('--p',p.toFixed(4));
  h.style.setProperty('--e',e.toFixed(4));
  h.style.setProperty('--fo',Math.max(0,1-p/.4).toFixed(4));
  h.style.setProperty('--fi',Math.min(1,Math.max(0,(p-.6)/.4)).toFixed(4));
  const l=p>.6,f=p>.4;
  if(h.classList.contains('is-line')!==l)h.classList.toggle('is-line',l);
  if(h.classList.contains('is-fold')!==f)h.classList.toggle('is-fold',f)}
/** v4.11: 上部の幅・位置と高さを合わせる。p=0(広い)と p=1(縮んだ)の高さを測り、上部の下に広い時の高さの余白を置く。
    今日の札を上部のすぐ下(8px)に置けるよう、下の余白(.ax-room)を足りない分だけにする */
function actLayout(){
  const h=actHdr();if(!h||CUR.name!=='activities')return;
  const s=actScroller(),box=(s||document.getElementById('app')).getBoundingClientRect();
  h.style.left=box.left+'px';h.style.width=box.width+'px';h.style.top=(s?box.top:0)+'px';
  /* v4.13: 高さは CSS で固定なので、測るのはここの一度だけ(縮む時の毎フレームでは測らない) */
  const full=h.offsetHeight;
  actFullH=full;
  document.getElementById('act-sp').style.height=full+'px';
  const room=document.querySelector('#app .ax-room'),t=document.querySelector('#app .dh2.today .dcard');
  if(room){room.style.height='0px';if(t){const below=actH(s)-actDocTop(t,s);room.style.height=Math.max(0,Math.round(actVH(s)-full-8-below))+'px'}}
  actApply(h);
}
/** v4.11: 位置合わせ。today=今日の札の上端を上部のすぐ下(8px)へ / bottom=一番下。位置合わせは上部を広げて(p=0)行い、その動きで縮まないよう最後の位置を覚える */
function actLand(want){
  const h=actHdr();if(!h)return;const s=actScroller();
  actP=0;actLayout();
  if(want==='bottom')actSet(s,actH(s)-actVH(s));
  else{const t=document.querySelector('#app .dh2.today .dcard')||document.querySelector('#app .dh2.today');if(!t)return;actSet(s,Math.max(0,actDocTop(t,s)-actFullH-8))}
  actLastY=actY(s);actApply(h);
}
/** v4.11: 位置合わせは、描き直しの入る動き(.screen の変形 4px)が終わってから行う。終わらないと今日の札の位置が 4px ずれる */
function actLandSoon(want){
  const an=document.getAnimations?document.getAnimations():[];
  Promise.all(an.map(a=>a.finished.catch(()=>{}))).then(()=>{if(CUR.name==='activities')actLand(want)});
}
/** 描き直しの後(ui.js の rendered)。別の画面から来た時は上部を広げる(位置合わせは landOnOpen)。同じ画面の描き直し(絞り込み・チェック)は縮み具合を保つ */
function actSync(){
  if(CUR.name!=='activities'){actWasOn=false;return}
  if(!actWasOn){actP=0;actWasOn=true}
  actLayout();actLastY=actY(actScroller());
}
/** 動いた量で上部の縮み具合を決める(1フレームに1回)。上端では必ず広げる。動いたのが位置合わせ(プログラムの移動)の分は、その後に actLastY を合わせてあるので縮まない */
function actOnScroll(){
  actRaf=0;const h=actHdr();if(!h||CUR.name!=='activities')return;
  const s=actScroller(),y=actY(s);
  if(actLastY===null){actLastY=y;return}
  const dy=y-actLastY;actLastY=y;
  /* v4.13: 読むのは scrollTop/scrollY だけ(枠や位置の測定はしない) */
  if(y<=0)actP=0;else if(dy)actP=Math.min(1,Math.max(0,actP+dy/ACT_R));
  actApply(h);
  /* v4.14: 止まったら、中途半端な形(0<p<1)を 0 か 1 へ送る。スクロール中は何もしない */
  clearTimeout(actSettleT);actSettleT=setTimeout(actSettle,100);
}
/* v4.14: スナップ。上部の形は一覧のスクロールで決まるので、形だけ 180ms で動かすと一覧と隙間(または重なり)が出る。
   そこで止まった所から、残りの分だけスクロールを 180ms で動かす(ease-out)。動くのは縮み具合 p が追う。
   50% 未満なら 0(広い)へ戻し、以上なら 1(縮む)へ送る。ユーザーが触ったら止める(actCancelSnap)。 */
function actSettle(){
  actSettleT=0;
  if(CUR.name!=='activities'||actP<=.001||actP>=.999)return;
  const s=actScroller(),y=actY(s),max=Math.max(0,actH(s)-actVH(s));
  const to=Math.min(max,Math.max(0,actP<.5?y-ACT_R*actP:y+ACT_R*(1-actP)));
  const dur=matchMedia('(prefers-reduced-motion: reduce)').matches?0:180,t0=performance.now();
  actCancelSnap();
  const step=now=>{const k=dur?Math.min(1,(now-t0)/dur):1,ea=1-Math.pow(1-k,3);
    actSet(s,y+(to-y)*ea);actSnapRaf=k<1?requestAnimationFrame(step):0};
  actSnapRaf=requestAnimationFrame(step);
}
function actCancelSnap(){if(actSnapRaf){cancelAnimationFrame(actSnapRaf);actSnapRaf=0}}
['wheel','touchstart','pointerdown','keydown'].forEach(t=>addEventListener(t,actCancelSnap,{capture:true,passive:true}));
/* 窓か左の一覧の、スクロールを1か所で受ける(scroll は泡立たないので capture で受ける) */
document.addEventListener('scroll',e=>{const t=e.target;
  if((t===document||(t.classList&&t.classList.contains('pane-l')))&&!actRaf)actRaf=requestAnimationFrame(actOnScroll)},{capture:true,passive:true});
document.addEventListener('rendered',actSync);
addEventListener('resize',()=>{if(CUR.name==='activities')actLayout()});
if(document.fonts)document.fonts.ready.then(()=>{if(CUR.name==='activities')actLayout()});
/** 画面を開いた時の位置: アクティビティは今月なら今日の札を上部のすぐ下・前の月なら一番下 / 今日の日のページは「いま」を上に(sticky の見出しの分 68px) */
function landOnOpen(){
  /* v4.13: 日・月の表示は一番上から(リストは今まで通り今日/前の月の位置)。wide の左の一覧は枠の中を0へ */
  if(CUR.name==='activities'&&CUR.q.view&&CUR.q.view!=='list')setTimeout(()=>{const s=actScroller();if(s)s.scrollTop=0;else scrollTo({top:0,behavior:'instant'})},0);
  else if(CUR.name==='activities')setTimeout(()=>actLandSoon(actMonth===TODAY.slice(0,7)?'today':'bottom'),0);
  else if(CUR.name==='day'&&CUR.id===TODAY)setTimeout(()=>{const n=document.querySelector('#app .ax .now');if(n)landTo(n,{top:68})},0);
}
addEventListener('hashchange',landOnOpen);
document.addEventListener('DOMContentLoaded',landOnOpen); /* 初回の描画(index.html 末尾の render)の後 */
const dayOfDate=d=>Object.entries(A).find(([k,a])=>a.type==='日記'&&a.date===d);
const kidsOf=d=>{const day=dayOfDate(d);return day?day[1].kids.filter(k=>A[k]).map(k=>[k,A[k]]):[]};
/** その日の全部の記録(活動の中の支払いも) */
const allOf=d=>{const day=dayOfDate(d);return day?descOf(day[0]).map(k=>[k,A[k]]):[]};
const passFilter=a=>actFilter==='all'||a.type===actFilter;
const planVisible=()=>actFilter==='all'||actFilter==='予定';
const daySpentAll=d=>allOf(d).reduce((s,[k,a])=>s+(a.x&&X[a.x]?X[a.x].amt:0),0);
const daySpent=d=>{const ps=allOf(d).filter(([k,a])=>a.x&&X[a.x]);return ps.length>=2?ps.reduce((s,[k,a])=>s+X[a.x].amt,0):0};
const dayDiary=d=>{const day=dayOfDate(d);return day&&day[1].body};
/** その日の項目(記録+未記録の予定)を時刻の古い順(上=過去・下=未来) */
function dayItems(d){
  const items=actFilter==='予定'?[]:(actFilter==='支払い'?allOf(d):kidsOf(d)).filter(([k,a])=>passFilter(a)).map(([k,a])=>({time:a.time||'',kind:'rec',k}));
  if(planVisible())evOn(d).filter(([k,e])=>!e.rec&&!e.hol).forEach(([k,e])=>items.push({time:e.all?'00:00':e.s,kind:'plan',k}));
  return items.sort((a,b)=>a.time.localeCompare(b.time));
}
const isFuturePlan=it=>it.kind==='plan'&&!EV[it.k].all&&EV[it.k].d===TODAY&&EV[it.k].s>NOW;
const itemHtml=(it,o={})=>it.kind==='rec'?momentRow(it.k,o):planRowAx(it.k,{...o,late:!o.future&&!isFuturePlan(it)});
function recordDates(){const ds=new Set(Object.values(A).filter(a=>a.type==='日記'&&a.date<TODAY&&a.kids&&descOf(Object.keys(A).find(id=>A[id]===a)).some(k=>A[k]&&passFilter(A[k]))).map(a=>a.date));
  if(planVisible())Object.values(EV).forEach(e=>{if(!e.rec&&!e.hol&&e.d<TODAY&&e.d>=TODAY.slice(0,8)+'01')ds.add(e.d)});
  return [...ds].sort()}
/** 日の行(上=過去・下=未来)。今日は 過ぎた記録・過ぎた予定 → いまの線 → これからの予定 */
const axDay=(d,o={})=>{const items=dayItems(d);if(d!==TODAY)return items.map(it=>itemHtml(it)).join('');
  return items.filter(it=>!isFuturePlan(it)).map(it=>itemHtml(it)).join('')+nowLine()+items.filter(it=>isFuturePlan(it)).map(it=>itemHtml(it,{future:true})).join('')};

route('activities',(id,q)=>{
  /* v4.13: 表示(リスト/日/月)。今の画面(#/activities)なら URL の view(既定はリスト)。別の画面の時(wide の左の一覧)は最後に選んだ表示 */
  if(CUR.name==='activities')actViewNow=['day','month'].includes(q.view)?q.view:'list';
  const view=actViewNow;
  /* 上部の大見出しの行(縮んだ時に出る丸ボタン2つ=作る)と、表示の切替の行 */
  const tb=`<div class="act-tb"><button class="act-ib" type="button" aria-label="レシートから支払いを記録" onclick="openReceipt()">${IC.camera}</button><button class="act-ib" type="button" aria-label="出来事を追加" onclick="openNewHappening()">${IC.plus}</button></div>`;
  const titleRow=`<div class="lt act-lt"><h1>アクティビティ</h1>${tb}<button class="iconbtn" type="button" aria-label="検索" onclick="toast('検索はモックでは未対応です')">${IC.search}</button></div>`;
  const viewRow=`<div class="act-views">${seg([['list','リスト'],['day','日'],['month','月']],view,"actSetView('$v')")}</div>`;
  /* 日・月: まとめ・月の送り・絞り込みは出さず、期間の送りと「予定を追加」だけ。中身は plan.js の描き方 */
  if(view!=='list'){
    return `<header class="act-top" id="act-top">${titleRow}${viewRow}<div class="act-per">${view==='day'?plDayNav():plMonthNav()}<button class="act-ib" type="button" aria-label="予定を追加" onclick="openNewEvent('${plSel}')">${IC.plus}</button></div></header><div class="act-sp" id="act-sp" aria-hidden="true"></div>`
      +`<div class="pl-root">${view==='day'?plDayView():plMonthView()}</div><div class="act-end" aria-hidden="true"></div>`;
  }
  const upcoming=Object.entries(EV).filter(([k,e])=>!e.hol&&!e.rec&&e.d>TODAY).sort((a,b)=>a[1].d.localeCompare(b[1].d));
  const isNow=actMonth===TODAY.slice(0,7),dates=recordDates().filter(d=>d.startsWith(actMonth));
  const allD=[...recordDates(),TODAY],minM=allD.sort()[0].slice(0,7),maxM=TODAY.slice(0,7);
  const s=daySpent(TODAY);
  /* まとめの数字: 表示中の月の記録と今日。押せる数字は予定・ToDoへ */
  const monthRec=Object.values(A).filter(a=>a.type!=='日記'&&dateOf(a).startsWith(actMonth)).length;
  const todayEv=evOn(TODAY).filter(([k,e])=>!e.hol).length;
  /* 月の切替の後の位置(前の月=一番下・今月=今日の札を上部のすぐ下)。開いた時の位置は landOnOpen */
  const want=landWant;landWant=null;
  if(want)setTimeout(()=>actLandSoon(want),0);
  /* v4.11 上部(固定の帯): 「広い形」と「1行の形」を両方描き、縮み具合 --p で見せる方を入れ替える(css/s-activities.css) */
  const mets=[{v:`${monthRec}件`,l:`${+actMonth.slice(5)}月の記録`,s:`${+actMonth.slice(5)}月`},{v:`${todayEv}件`,l:'今日の予定',s:'予定',href:'#/activities?view=day'},{v:`${todoTodayCount()}件`,l:'今日のToDo',s:'ToDo',href:'#/todo'}];
  const acts=[{text:'支払いを記録',icon:'pay',primary:true,onclick:'openPayment()'},{text:'出来事',icon:'plus',onclick:'openNewHappening()'}];
  /* v4.11 本体調整: 数字は1組だけ(2つの形を重ねない=二重に見えない・空にならない)。縮むと 数字が小さくなり、下の長いラベルが畳まれ、数字の右に短いラベルが出る */
  const metU=m=>{const inner=`<span class="n"><b class="num">${esc(m.v)}</b><span class="s">${esc(m.s)}${m.href?IC.chev:''}</span></span><span class="l">${esc(m.l)}${m.href?IC.chev:''}</span>`;return m.href?`<a class="act-m" href="${m.href}">${inner}</a>`:`<div class="act-m">${inner}</div>`};
  const full=`<div class="act-full"><div class="act-ms">${mets.map(metU).join('')}</div><div class="act-acts">${sumActs(acts)}</div></div>`;
  const line='';
  /* 縮んだ時は、作る2つの丸ボタンを大見出しの行へ移す(行の幅を --p で広げる。全部の幅は 0→88px。ぶつからない) */
  const head=`<header class="act-top" id="act-top">`+titleRow+viewRow
    +`<section class="sc act-sc"><div class="act-sci">${full}${line}</div></section>`
    +`<div class="mnav act-mn"><button type="button" aria-label="前の月" ${actMonth<=minM?'disabled':''} onclick="monthShift(-1)">${IC.back}</button><b>${actMonth.slice(0,4)}年${+actMonth.slice(5)}月</b><button type="button" class="nx" aria-label="次の月" ${actMonth>=maxM?'disabled':''} onclick="monthShift(1)">${IC.back}</button></div>`
    +`<div class="act-chips">${chips(ACT_FILTERS,actFilter,"actFilter='$v';rerender()")}</div>`
    +`</header><div class="act-sp" id="act-sp" aria-hidden="true"></div>`;
  return head+`<div class="ax">`
    +(actMonth<=minM?axEdge('これより前の記録 なし',true):'')
    +dates.map(d=>{const sp=daySpent(d);return dayHead(d,{href:`#/day/${d}`,diary:dayDiary(d),sum:sp?`自分 ${yen(sp)}`:''})+axDay(d)}).join('')
    +(isNow?dayHead(TODAY,{href:`#/day/${TODAY}`,diary:dayDiary(TODAY),empty:'今日の日記を書く',sum:s?`自分 ${yen(s)}`:''})+axDay(TODAY):'')
    +(!isNow&&!dates.length?`<div class="it-note" style="text-align:center;padding:32px 0">この月の記録はありません</div>`:'')
    /* 下=未来: 明日から2週間の予定を日ごとにそのまま続ける(リンクだけにせず、時間の流れとして見せる) */
    +(isNow&&planVisible()?(()=>{const out=[];for(let i=1;i<=14;i++){const d=addDays(TODAY,i);const evs=evOn(d).filter(([k,e])=>!e.rec);if(!evs.length)continue;
        const hol=evs.find(([k,e])=>e.hol);const rows=evs.filter(([k,e])=>!e.hol).map(([k])=>planRowAx(k,{future:true})).join('');
        out.push(dayHead(d,{href:`#/day/${d}`,future:true,empty:hol?hol[1].t:''})+rows)}
      const later=upcoming.filter(([k,e])=>e.d>addDays(TODAY,14)).length;
      return out.join('')+`<a href="#/activities?view=month">`+axEdge(later?`これより先の予定 ${later}件${IC.chev}`:`予定 › 月で見る${IC.chev}`)+`</a>`})():'')
    +(isNow?'<div class="ax-room" aria-hidden="true"></div>':'')
    +`</div><div class="act-end" aria-hidden="true"></div>`;
},{tab:'activities'});

/* ---------- 日のページ: 1日=1ページの日記と、その日の軸 ---------- */
route('day',d=>{
  const day=dayOfDate(d);const a=day&&day[1];const w=new Date(d+'T00:00').getDay();
  const items=dayItems(d);const sp=daySpent(d);
  const people=new Set(items.flatMap(i=>i.kind==='rec'?(A[i.k].roles||[]).map(r=>r[1]).filter(x=>x&&x!=='me'):[]));
  const dl=[...new Set([...Object.values(A).filter(x=>x.type==='日記').map(x=>x.date),TODAY])].sort();const pi=dl.indexOf(d);const prev=dl.filter(x=>x<d).pop(),next=dl.find(x=>x>d);
  const html=appbar('',{actions:[...(next?[{icon:'up',label:'次の日(未来へ)',onclick:`go('#/day/${next}')`}]:[]),...(prev?[{icon:'down',label:'前の日(過去へ)',onclick:`go('#/day/${prev}')`}]:[]),{icon:'edit',label:'日記を書く',onclick:`openDiary('${d}')`}]})
    +`<section class="dp"><div class="dp-date"><b>${+d.slice(8)}</b><span>${WD[w]}曜日<small>${d.slice(0,4)}年${+d.slice(5,7)}月${d===TODAY?'・今日':''}</small></span></div>`
    +(a&&a.body?`<p class="dp-text">${esc(a.body)}</p>`:`<button class="dp-text none" type="button" onclick="openDiary('${d}')" style="text-align:left">この日のことを書く…</button>`)
    +(a&&a.photos?`<div class="dp-photos">${Array.from({length:a.photos},(_,i)=>photoThumb(d,i)).join('')}</div>`:'')
    +`</section>`
    +`<div class="stats"><div><b>${items.filter(i=>i.kind==='rec').length}件</b><span>出来事</span></div>${sp?`<div><b>${yen(sp)}</b><span>自分の支出</span></div>`:''}</div>`+(people.size?sh('この日に交わった縁',people.size)+`<div class="dp-kin">${[...people].map(p=>eref(p)).join('')}</div>`:'')
    +sh('この日の時間軸')
    +(items.length?`<div class="ax">${axDay(d)}${d===TODAY?'<div class="ax-room" aria-hidden="true"></div>':''}</div>`:empty('activity','この日の出来事はまだありません',{label:'出来事を追加',onclick:`openNewHappening('${d}')`}));
  return html;
},{tab:'activities'});

/* ---------- 出来事の詳細: その点の前後を切り取った軸 ---------- */
route('a',id=>{
  const a=A[id];if(!a)return appbar('')+empty('activity','記録 なし');
  if(a.type==='日記'){setTimeout(()=>location.replace('#/day/'+a.date));return ''}
  return momentView(id,a);
},{tab:'activities'});

const menuAct=id=>({icon:'more',label:'その他の操作',onclick:`actMenu('${id}')`});
function actMenu(id){
  const a=A[id],ins=!isSpan(a)&&a.type!=='支払い';
  openSheet({title:'操作',body:list([
    row({lead:ico('edit'),t:ins?'時刻を編集':'編集',onclick:ins?`closeSheet();insTimeEd('${id}')`:`closeSheet();toast('編集はモックでは未対応です')`}),
    row({lead:ico('trash'),t:'削除',onclick:`deleteActivity('${id}')`})])});
}
/* v4.14 子孫(活動の中の活動・支払い)も一緒に消す。取り消しで全部と親の並び位置を戻す */
function deleteActivity(id){
  const a=A[id],p=a.parent&&A[a.parent];
  const desc=descOf(id),all=[id,...desc];
  const snap=JSON.stringify(all.map(k=>({k,a:A[k],x:A[k].x?X[A[k].x]:null})));
  const at=p?p.kids.indexOf(id):-1;
  if(p)p.kids=p.kids.filter(k=>k!==id);
  all.forEach(k=>{const xid=A[k].x;delete A[k];if(xid)delete X[xid]});
  closeSheet();save();goBack();
  toast(desc.length?`記録と中の ${desc.length} 件を削除しました`:'記録を削除しました',()=>{
    JSON.parse(snap).forEach(({k,a,x})=>{A[k]=a;if(x&&a.x)X[a.x]=x});moneyInstall();
    if(p){if(at<0)p.kids.push(id);else p.kids.splice(Math.min(at,p.kids.length),0,id)}});
}

/** 品目を「同じ名前+同じ合計+同じ金額行」でまとめる(表示だけ。データは1行=1個) */
let showBurden=false;const openGroups=new Set();
/* v4.16: 使った人が違う品目はまとめない(使った人は事実なので、まとめると見えなくなる) */
const usersKey=it=>it&&it.users?usersOf(it).slice().sort().join(','):'';
function itemGroups(items){const g=[];items.forEach((it,i)=>{const f=g.find(x=>x.n===it.n&&x.price===it.price&&lnKey(x)===lnKey(it)&&x.uk===usersKey(it));if(f)f.idx.push(i);else g.push({n:it.n,price:it.price,lines:it.lines,tax:it.tax,users:it.users,uk:usersKey(it),idx:[i]})});return g}
/** 品目の一覧。同じものは「×N」にまとめ、開くと子(1個目,2個目…)が親の下に入れ子で並ぶ。金額行がある品目は補足行に内訳。o.x = 自分の支出側の表示 */
function itemsList(id,items,o={}){
  return itemGroups(items).map((g,gi)=>{
    const key=id+':'+gi,multi=g.idx.length>1,open=multi&&openGroups.has(key);
    const u=itemAmt(g),ad=lnSumHtml(g);
    /* 使った人は1行目(事実)、内訳と税率は2行目。長い内訳で使った人が切れないように行を分ける */
    const who=`<span class="it-u">使った人 ${esc(usersText(items[g.idx[0]]))}</span>`;
    const sub=o.x?(multi?`1個 ${yen(u)}`:esc(usersText(items[g.idx[0]]))):`${who}<span class="it-d">${multi?`1個 ${yen(u)}、`:''}${ad?ad+`<span class="adj-l">税${g.tax}%</span>`:`税${g.tax}%`}</span>`;
    const main=row({cls:`plain grp${multi?' multi':''}${open?' open':''}`,t:`${esc(g.n)}${multi?` <span class="faint">×${g.idx.length}</span><span class="grp-chev" aria-hidden="true">${IC.chev}</span>`:''}`,s:sub,
      v:`<span class="num" style="font-weight:600">${yen(u*g.idx.length)}</span>`,
      chev:multi?0:1,onclick:multi?`openGroups.has('${key}')?openGroups.delete('${key}'):openGroups.add('${key}');rerender()`:`go('#/i/${id}/${g.idx[0]}')`});
    const kids=open?`<div class="grp-kids" role="group" aria-label="${esc(g.n)}の内訳">${g.idx.map((i,n)=>row({cls:'plain kid',lead:`<span class="kid-n">${n+1}</span>`,t:esc(items[i].memo||'メモなし'),v:tag(usersText(items[i])),chev:1,onclick:`go('#/i/${id}/${i}')`})).join('')}</div>`:'';
    return main+kids;
  });
}
/* ---------- 支払いの事実(払った人・使った人)と自分の支出(X)の同期 ----------
   計算で出るもの(自分の負担・品目ごとの自分の額・貸し借り)は保存しない。
   ここで変えるのは a.items / a.paid / a.amt / a.settled と X[a.x] の basis・me・n だけ */
/** 品目を変えた後: 合計 a.amt を品目の合計に。払った人が1人なら その人の額も合計に。自分の支出は xEnsure で作る/消す */
function itemsChanged(aid){const a=A[aid];a.amt=payTotal(a);if(a.paid&&a.paid.length===1)a.paid[0].amt=a.amt;xEnsure(aid)}
/** 取り消し用のスナップショット: 支払い(items・amt・paid・settled)と、その自分の支出(X を丸ごと) */
const bpSnap=a=>{const x=a.x&&X[a.x];return JSON.stringify({items:a.items,amt:a.amt,paid:a.paid,settled:a.settled||null,xid:a.x||null,x:x||null})};
/** スナップショットに戻す。X は丸ごと戻し、読み取り専用の値(x.amt・a.adv など)は moneyInstall で作り直す */
const bpRestore=(a,snap)=>{const o=JSON.parse(snap);
  a.items=o.items;a.amt=o.amt;a.paid=o.paid;
  if(o.settled)a.settled=o.settled;else delete a.settled;
  if(a.x&&a.x!==o.xid)delete X[a.x];
  if(o.x){X[o.xid]=o.x;a.x=o.xid}else delete a.x;
  moneyInstall()};
/** 自分の負担(X)を決める。無ければ作り、負担が0になったら支出を消す(xEnsure) */
function xSetBasis(aid,v){const a=A[aid];
  if(!a.x||!X[a.x]){const xid=uid('x');X[xid]={pay:aid,basis:'items',cat:'eatout',memo:''};a.x=xid}
  const x=X[a.x];x.basis=v.basis;
  if(v.basis==='amount')x.me=v.me||0;else delete x.me;
  if(v.basis==='even')x.n=v.n||2;else delete x.n;
  xEnsure(aid);moneyInstall()}
/** 自分の負担を v(basis・n・me)に変えた時の結果(保存しない)。balanceOf と同じ計算を一時の X で行う */
function balPreview(aid,v){const a=A[aid],tk='_preview',tmp={pay:aid,basis:v.basis,n:v.n,me:v.me};
  X[tk]=tmp;try{return {mine:xAmt(tmp),...balanceOf({...a,x:tk})}}finally{delete X[tk]}}

/* ---------- 品目の編集(1品目ずつ開く。負担は別の画面) ---------- */
let ipD=null;
itemEd('ip',{items:()=>ipD.items,redraw:()=>setSheetBody(ipBody()),upd:i=>{const e=document.getElementById('ie-eq-ip-'+i);if(e)e.innerHTML=IED.ip.eq(i);const t=document.getElementById('ip-tot');if(t)t.innerHTML=ipTot()}});
const ipTot=()=>list([row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(ipD.items.reduce((s,i)=>s+itemAmt(i),0))}</b>`})],'flat');
function ipBody(){
  return sh('品目',ipD.items.length,{label:'品目を追加',icon:'plus',onclick:'IED.ip.add()'})
    +IED.ip.html()
    +`<div id="ip-tot" style="margin-top:12px">${ipTot()}</div>`;
}
function ipOpen(id){
  ipD={id,items:A[id].items.map(itemToEdit)};IED.ip.open=-1;
  openSheet({title:'品目を編集',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:ipBody()});
  crFooter(`<button class="btn block" type="button" onclick="ipSave()">保存</button>`);
}
function ipSave(){
  const a=A[ipD.id],items=ipD.items.filter(i=>i.n&&itemAmt(i));
  if(!items.length){toast('品目を入れてください');return}
  const snap=bpSnap(a);
  a.items=items.map(i=>{const o={n:i.n,price:itemAmt(i),tax:i.tax||8,users:usersOf(i).slice()};const ln=lnSave(i);if(ln)o.lines=ln;if(i.memo)o.memo=i.memo;if(i.unk)o.unk=1;return o});
  itemsChanged(ipD.id);
  closeSheet();crFooter('');save();rerender();
  toast('品目を保存しました',()=>bpRestore(a,snap));
}

/* ---------- 払った人(paid): 1つのシート。人ごとに who・金額・方法。合計が支払いの合計と違えば保存しない ---------- */
let pdD=null;
const pdSum=()=>pdD.rows.reduce((s,p)=>s+(p.amt||0),0);
const paidSumHtml=amt=>{const d=amt-pdSum();return list([row({cls:'plain',t:'支払いの合計',v:`<span class="num">${yen(amt)}</span>`}),row({cls:'plain',t:'払った額の合計',v:`<span class="num">${yen(pdSum())}</span>`}),...(d?[row({cls:'plain',t:'差額',v:`<b class="num">${d>0?'あと ':'超過 '}${yen(Math.abs(d))}</b>`})]:[])],'flat')};
function paidOpen(aid){
  pdD={id:aid,rows:paidOf(A[aid]).map(p=>({...p}))};
  openSheet({title:'払った人',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:paidBody()});
  paidUpd();
}
function paidBody(){
  const a=A[pdD.id],people=peopleOf(a);
  return sh('払った人',pdD.rows.length,{label:'追加',icon:'plus',onclick:'paidAdd()'})
    +pdD.rows.map((p,i)=>`<div class="ed-box"><div class="ed-h"><span class="ed-n">${esc(whoName(p.who,p.name))}</span><button class="ie-d" type="button" aria-label="この人を外す" onclick="paidDel(${i})">${IC.trash}</button></div>`
      +whoChips(people,[p.who],`paidWho(${i},'$v')`,'払った人')
      +`<label class="ie-a"><span>金額</span><input class="ie-p num" inputmode="numeric" value="${p.amt||''}" placeholder="¥0" aria-label="払った額" oninput="paidAmt(${i},this.value)"></label>`
      +(p.who==='me'?`<label class="ie-a"><span>支払い方法</span>${payMethodSelect(p.how||PAY_METHODS[0],`pdD.rows[${i}].how=this.value`).replace('<select ','<select class="pd-how" aria-label="支払い方法" ')}</label>`:'')
      +`</div>`).join('')
    +`<div id="pd-sum" style="margin-top:12px">${paidSumHtml(a.amt)}</div>`;
}
/** 合計の行と、下の保存ボタン(差額があれば押せない)を入れ直す。入力中は本文を描き直さない */
function paidUpd(){const a=A[pdD.id],d=a.amt-pdSum();
  const e=document.getElementById('pd-sum');if(e)e.innerHTML=paidSumHtml(a.amt);
  crFooter(`<button class="btn block" type="button" onclick="paidSave()" ${d?'disabled':''}>保存</button>`)}
function paidWho(i,w){const p=pdD.rows[i];p.who=w;delete p.name;if(w!=='me')delete p.how;else if(!p.how)p.how=PAY_METHODS[0];setSheetBody(paidBody());paidUpd()}
function paidAmt(i,v){pdD.rows[i].amt=+String(v).replace(/[^\d]/g,'')||0;paidUpd()}
function paidDel(i){pdD.rows.splice(i,1);setSheetBody(paidBody());paidUpd()}
function paidAdd(){const a=A[pdD.id],used=pdD.rows.map(p=>p.who),who=peopleOf(a).find(w=>!used.includes(w))||'me';
  pdD.rows.push({who,amt:Math.max(0,a.amt-pdSum()),how:who==='me'?PAY_METHODS[0]:undefined});setSheetBody(paidBody());paidUpd()}
function paidSave(){const a=A[pdD.id],rows=pdD.rows.filter(p=>p.amt>0);
  if(!rows.length||pdSum()!==a.amt){toast('払った額の合計を支払いの合計に合わせてください');return}
  const old=JSON.parse(JSON.stringify(a.paid));
  a.paid=rows.map(p=>{const o={who:p.who,amt:p.amt};if(p.name)o.name=p.name;if(p.who==='me'&&p.how)o.how=p.how;return o});
  closeSheet();crFooter('');save();rerender();
  toast('払った人を保存しました',()=>{a.paid=old})}
/** 払った人の節(支払いの詳細): 1行ごとに 名前 · 方法 と 金額。押すと払った人のシート */
function paidSec(id,a){const ps=paidOf(a);
  return sh('払った人',ps.length,{label:'編集',icon:'edit',onclick:`paidOpen('${id}')`})
    +list(ps.map(p=>row({cls:'plain',t:esc(whoName(p.who,p.name))+(p.how?` · ${esc(p.how)}`:''),v:`<span class="num" style="font-weight:600">${yen(p.amt)}</span>`,chev:1,onclick:`paidOpen('${id}')`})),'flat')}

/* ---------- 自分の負担(X.basis): 使った分 / 割り勘 / 金額を指定。家計簿の支出の画面からも openBasis を呼ぶ ---------- */
let bsD=null;
function openBasis(aid){
  const a=A[aid],x=a.x&&X[a.x];
  const n0=x&&x.basis==='even'&&x.n?x.n:peopleOf(a).length;
  bsD={id:aid,basis:x?(x.basis||'items'):(basisGuess(a)||'items'),n:Math.max(2,n0),me:x?(x.basis==='amount'?(x.me||0):xAmt(x)):0};
  openSheet({title:'自分の負担',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:basisBody(),footer:`<button class="btn block" type="button" onclick="basisSave()">保存</button>`});
}
function basisBody(){
  const a=A[bsD.id],mp=myPaid(a);
  return seg([['items','使った分'],['even','割り勘'],['amount','金額を指定']],bsD.basis,"basisSet('$v')")
    +(bsD.basis==='even'?`<div class="ed-box"><div class="bs-n"><button type="button" aria-label="人数を減らす" onclick="basisN(-1)">−</button><b class="num">${bsD.n}人で割る</b><button type="button" aria-label="人数を増やす" onclick="basisN(1)">＋</button></div></div>`:'')
    +(bsD.basis==='amount'?`<div class="ed-box"><label class="ie-a"><span>自分の負担</span><input class="ie-p num" inputmode="numeric" value="${bsD.me||''}" placeholder="¥0" aria-label="自分の負担" oninput="basisMe(this.value)"></label><button class="btn sm tonal" type="button" onclick="basisMe(${mp},1)">自分が払った ${yen(mp)} を入れる</button></div>`:'')
    +`<div id="bs-res" style="margin-top:12px">${basisRes()}</div>`;
}
function basisRes(){const pv=balPreview(bsD.id,bsD);
  return list([row({cls:'plain',t:'<b>自分の負担</b>',v:`<b class="num">${yen(pv.mine)}</b>`}),...(pv.amt?[row({cls:'plain',t:esc(balanceText(pv)),s:pv.settled?'精算済み':'未精算'})]:[])],'flat')}
function basisSet(v){bsD.basis=v;setSheetBody(basisBody())}
function basisN(d){bsD.n=Math.min(99,Math.max(2,bsD.n+d));setSheetBody(basisBody())}
/** 金額の入力(set=0)は結果だけ入れ直す。ボタン(set=1)は入力欄も描き直す */
function basisMe(v,set){bsD.me=set?v:(+String(v).replace(/[^\d]/g,'')||0);
  if(set)setSheetBody(basisBody());else{const r=document.getElementById('bs-res');if(r)r.innerHTML=basisRes()}}
function basisSave(){const a=A[bsD.id],snap=bpSnap(a);
  xSetBasis(bsD.id,{basis:bsD.basis,n:bsD.n,me:bsD.me});
  closeSheet();crFooter('');save();rerender();
  toast('自分の負担を保存しました',()=>bpRestore(a,snap))}

/** 精算済み ⇔ 未精算(a.settled)。取り消せる */
function settleToggle(aid){const a=A[aid],was=!!a.settled;if(was)delete a.settled;else a.settled=1;
  save();rerender();toast(was?'未精算に戻しました':'精算済みにしました',()=>{if(was)a.settled=1;else delete a.settled})}
/** 支払いの詳細の「自分の負担」: 1行(決め方と額)+貸し借りの行と精算のボタン(貸し借りがある時だけ) */
function mineSec(id,a){
  const x=a.x&&X[a.x],b=balanceOf(a);
  const r=list([row({cls:'plain',t:esc(BASIS[x&&x.basis||'items']),v:`<span class="num" style="font-weight:600">${yen(mineOf(a))}</span>`,chev:1,onclick:`openBasis('${id}')`})]);
  const bal=b.amt?list([row({cls:'plain',t:esc(balanceText(b)),s:b.settled?'精算済み':'未精算'})],'flat')
    +`<div class="pd-st"><button class="btn sm tonal" type="button" onclick="settleToggle('${id}')">${b.settled?'未精算に戻す':'精算済みにする'}</button></div>`:'';
  return sh('自分の負担')+r+bal}
/** 支払いの割合: 自分の分 / 相手の分(立替・未精算は斜線) */
function paySplit(a){
  const x=a.x&&X[a.x];const me=x?x.amt:0;const rest=a.amt-me;
  const segs=[{v:me,cls:'me',label:'自分の支出',noAmt:!!x}];
  /* v4.16: ほかの人の分の名前は「使った人・関わった人」から。貸しで未精算なら斜線(琥珀) */
  if(rest>0){const bal=balanceOf(a),ids=peopleOf(a).filter(w=>w!=='me'),who=ids.length?ids.map(w=>whoShort(w)+(E[w]?'さん':'')).join('・'):'ほかの人';
    const owed=bal.amt>0&&bal.open;segs.push({v:rest,cls:owed?'owed':'other',label:`${who}の分${owed?'・未精算':''}`})}
  return splitBar(segs,a.amt);
}
/* ---------- 記録の詳細(v4.3・新しいデータモデル): 種類ごとに形を分ける ----------
   活動(期間): 主役のカプセル(種類の札+時刻の範囲+長さ)。その下に「この中の支払い」の白い行
   支払い: 上に日の札(親が活動なら、その活動の1行)。主役は瞬間の時刻。自分の支出は半券
   瞬間(起床など): 小さな主役(時刻+名前)だけ。時刻の編集と削除は「その他の操作」 */
/** 長さ(終了-開始)。0時をまたぐ時は +24時間 */
const durText=a=>{if(!isSpan(a))return '';const t=s=>{const[h,m]=s.split(':').map(Number);return h*60+m};let m=t(a.end)-t(a.time);if(m<0)m+=1440;const h=Math.floor(m/60),r=m%60;return h&&r?`${h}時間${r}分`:h?`${h}時間`:`${r}分`};
/** もとの予定(この記録になった予定)。予定→記録の導線 */
function origPlan(id){const ev=Object.entries(EV).find(([k,e])=>e.rec===id);if(!ev)return '';
  return sh('元の予定')+objs([row({href:`#/ev/${ev[0]}`,lead:`<span class="ax-node plan" style="margin:0"></span>`,t:esc(ev[1].t),s:`${fmtMDW(ev[1].d)} ${ev[1].s||''}`,chev:1})])}
/* ---------- v4.14: 活動の子を主役の中に入れ子で並べる(見出しなし) ---------- */
/** 子を時刻順に(時刻なしは後ろ) */
const kidSorted=id=>kidActs(id).slice().sort((x,y)=>(A[x].time||'99:99').localeCompare(A[y].time||'99:99'));
/** 支払い以外の子の行: 種類の印・時刻・題名・子の件数・›(瞬間は時刻と題名だけ) */
function ckRow(c,showN){const k=A[c],[ncls,icon]=AX_NODE[k.type]||['','activity'];const inst=INSTANT_TYPES.includes(k.type),n=showN?kidActs(c).length:0; /* 件数は、孫を並べない深さの時だけ(並べている時は重複) */
  return `<a class="ck${inst?' inst':''}" href="#/a/${c}"><span class="ck-n ${ncls}">${IC[icon]||''}</span><span class="ck-tm num">${esc(timeText(k))}</span><span class="ck-t">${esc(k.title)}</span>${n?`<span class="ck-c">${n}件</span>`:''}${inst?'':`<span class="ck-chev">${IC.chev}</span>`}</a>`}
/** 子1つと、その子(孫)を案内線つきで字下げして並べる。最大3段 */
function ckKid(c,depth=1){const k=A[c];if(!k)return '';
  const body=k.type==='支払い'?payShell(c,{mode:'kid'}):ckRow(c,depth>=3);
  const sub=depth<3?kidSorted(c).map(g=>ckKid(g,depth+1)).join(''):'';
  return body+(sub?`<div class="ck-sub">${sub}</div>`:'')}
/** 主役の中の子の並び(見出しなし)。最後に点線の「アクティビティを追加」。支払いが2件以上で自分の支出があれば合計を下端に */
function capKids(id){const ks=kidSorted(id),payK=ks.filter(c=>A[c].type==='支払い');
  const mine=payK.reduce((s,c)=>s+(A[c].x&&X[A[c].x]?X[A[c].x].amt:0),0);
  return `<div class="cap-kids">${ks.map(c=>ckKid(c)).join('')}`
    +`<button class="ck-add" type="button" onclick="typeof actAdd==='function'?actAdd('${id}'):actPayAdd('${id}')">${IC.plus}アクティビティを追加</button>`
    +(payK.length>1&&mine?`<div class="xk-sum"><span>自分の支出 合計</span><b class="num">${yen(mine)}</b></div>`:'')+`</div>`}
/** 祖先の活動を、軸の見出し用の {href,time,title,icon} の並びにする(外側が先)。子の詳細で親が子を包む */
const ancPins=id=>ancIds(id).map(k=>({href:'#/a/'+k,time:timeText(A[k]),title:A[k].title,icon:(AX_NODE[A[k].type]||['','activity'])[1]}));
/** 活動の中に支払いを追加: 支払いを記録を、日付・時刻・親=この活動にして開く */
function actPayAdd(id){const a=A[id];openQuickExpense();Object.assign(CQ,{date:dateOf(a),time:a.time,act:id});crQeRender()}

/** 活動(期間)の詳細 */
function actView(id,a,d){
  const [,icon]=AX_NODE[a.type]||['','activity'];
  const mv=a.type==='移動',dur=durText(a);
  const dep=(a.roles||[]).find(r=>r[0]==='出発'),arr=(a.roles||[]).find(r=>r[0]==='到着');
  /* 移動: 出発 カフェ ミナト 19:30 → 到着 自宅 20:05(主役の中)。駅名・店名は縁の詳細へ */
  const stop=(r,t)=>`<span class="rs"><small>${esc(r[0])}</small>${r[1]&&E[r[1]]?`<a href="#/e/${r[1]}">${esc(E[r[1]].n)}</a>`:esc(placeName(r))} <b>${esc(t)}</b></span>`;
  const rt=mv?`<div class="route">${dep?stop(dep,a.time):''}<i aria-hidden="true">→</i>${arr?stop(arr,a.end):''}</div>`:'';
  const tm=mv?esc(dur):`${esc(timeText(a))}${dur?` <small>${esc(dur)}</small>`:''}`;
  /* v4.14: 子(どの種類のアクティビティでも)は主役の中に入れ子で並ぶ(見出しなし)。子の一覧と追加ボタンは capKids */
  const cap=`<section class="cap"><div class="kind"><span class="lbl">${IC[icon]}${esc(a.type)}</span><span class="tm">${tm}</span></div><h1>${esc(a.title)}</h1>${rt}${capKids(id)}</section>`;
  /* 交わった人・場所(リンク付き)。移動の出発・到着は主役の行に出しているので重ねない */
  const rr=(a.roles||[]).filter(r=>!mv||!['出発','到着'].includes(r[0]));
  return appbar('',{actions:[menuAct(id)]})
    +pinView({later:[{day:d}],earlier:[],cap,anc:ancPins(id)})
    +(rr.length?sh('関わったエンティティ',rr.length)+list(rr.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +(a.body?sh('内容')+`<div class="list"><p class="ac-body">${esc(a.body)}</p></div>`:'')
    +origPlan(id);
}

/** 支払いの詳細: 上に日の札(親が活動なら、その1行を日の札の下に)。主役は瞬間の時刻。自分の支出は半券 */
function payView(id,a,d){
  const pa=parentAct(a);
  /* 主役(v4.6): 種類の札「支払い」+時刻 / 題名=支払先 / 合計は普通の大きさの1行(割合の棒の上に1回だけ) / 補足=支払い方法 */
  const cap=`<section class="cap"><div class="kind"><span class="lbl">${IC.pay}支払い</span><span class="tm">${esc(a.time||'')}</span></div><h1>${esc(payeeOf(a)||a.title)}</h1>${a.amt!=null?`<div class="pay-sum"><span>合計</span><b class="num">${yen(a.amt)}</b></div>`:''}${a.pay?`<div class="meta"><span>${esc(a.pay)}</span></div>`:''}${paySplit(a)}${payShell(id,{mode:'pay'})}</section>`; /* v4.12: 半券は主役の中の下端(旧 .hang の └ は廃止) */
  /* v4.14: 親の活動は軸の並びに出さず、主役を包む枠(anc)として出す(親が子を包む) */
  const later=[{day:d}];
  /* 交わった人・場所 = 支払いの役割(支払先・支払者) + 親の活動の同席者(whoOf と同じ考え方) */
  const roles=[...(a.roles||[])];
  if(pa)(pa.roles||[]).forEach(r=>{if(r[1]&&r[1]!=='me'&&!['場所','出発','到着','支払先','支払者'].includes(r[0])&&!roles.some(q=>q[1]===r[1]))roles.push(r)});
  const shop=roles.find(r=>r[0]==='支払先');
  const att=(EXTRA.attach||{})[id];
  return appbar('',{actions:[menuAct(id)]})
    +pinView({later,earlier:[],cap,anc:ancPins(id)})
    +shopTodoSug(id,a)
    +(roles.length?sh('関わったエンティティ',roles.length,shop&&!shop[1]?{label:'支払先を店として登録',onclick:`registerShop('${id}')`}:null)+list(roles.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +paidSec(id,a)
    +(a.items?sh('品目',itemGroups(a.items).length,{label:'追加',icon:'plus',onclick:`itAdd('${id}')`})+list(itemsList(id,a.items),'flat'):'')
    +mineSec(id,a)
    +origPlan(id)
    +(att?sh('添付',1)+`<div class="list ac-att"><img src="${att}" alt="レシートの写真"></div>`:'');
}

/** 瞬間の詳細(起床など): 小さな主役(時刻+名前)だけ。上に日の札。時刻の編集・削除は「その他の操作」 */
function insView(id,a,d){
  const cap=`<div class="ins-c"><span class="ins-tm num">${esc(a.time||'')}</span><span class="ins-t">${esc(a.title)}</span></div>`;
  return appbar('',{actions:[menuAct(id)]})+pinView({later:[{day:d}],earlier:[],cap,anc:ancPins(id)})+origPlan(id);
}
/** 時刻の編集(瞬間の記録) */
function insTimeEd(id){const a=A[id];
  openSheet({title:'時刻を編集',body:list([`<div class="row ins-f"><span class="t">時刻</span><input id="ins-t" type="time" value="${esc(a.time||'')}"></div>`],'flat'),
    footer:`<button class="btn block" type="button" onclick="insTimeSave('${id}')">保存</button>`});
}
function insTimeSave(id){const a=A[id],v=(document.getElementById('ins-t')||{}).value;if(!v){toast('時刻を入れてください');return}
  const old=a.time;a.time=v;closeSheet();save();rerender();toast('時刻を保存しました',()=>{a.time=old;save();rerender()});
}

function momentView(id,a){
  const day=dayOf(a);const d=day?day.date:TODAY;
  if(a.type==='支払い')return payView(id,a,d);
  if(isSpan(a))return actView(id,a,d);
  return insView(id,a,d);
}

function registerShop(aid){
  const a=A[aid];const r=a.roles.find(r=>r[0]==='支払先');const n=r[2];
  const id=uid('e');E[id]={n,i:n[0],t:['shop'],facts:[]};r[1]=id;save();rerender();
  toast(`「${n}」を店として登録しました`,()=>{delete E[id];r[1]=null});
}
/** 買い物リストのToDoが、この支払いの品目にあれば完了を提案する(サジェスト+ボタン1つ) */
function shopTodoSug(id,a){
  if(a.type!=='支払い'||!a.items)return '';
  const shop=(a.roles||[]).find(r=>r[0]==='支払先');const names=new Set(a.items.map(i=>i.n));
  const ks=Object.keys(TD).filter(k=>!TD[k].done&&names.has(TD[k].t)&&LISTS[TD[k].list]&&(!LISTS[TD[k].list].shop||(shop&&LISTS[TD[k].list].shop===shop[1])));
  if(!ks.length)return '';
  return `<div class="suggest"><span class="t">買い物リストの ${ks.map(k=>'「'+esc(TD[k].t)+'」').join('')} がこの支払いにあります</span><button class="btn sm" type="button" onclick="doneByPay(['${ks.join("','")}'],'${id}')">完了にする</button></div>`;
}
function doneByPay(ks,pay){const d=dayOf(A[pay]).date;ks.forEach(k=>{TD[k].done=d;TD[k].pay=pay});save();rerender();toast('支払いの品目から完了にしました',()=>ks.forEach(k=>{delete TD[k].done;delete TD[k].pay}))}

/** 写真のサムネイル(モックでは日付から決まる風景の絵) */
function photoThumb(d,i){
  const h=(+d.slice(8)*37+i*71)%40;const sky=`hsl(${195+h/2} 50% 74%)`,land=`hsl(${85+h} 28% 42%)`,sun=`hsl(${45+h/4} 85% 86%)`;
  return `<svg class="dp-ph" viewBox="0 0 88 88" role="img" aria-label="写真${i+1}"><rect width="88" height="88" fill="${sky}"/><circle cx="${20+i*18}" cy="26" r="9" fill="${sun}"/><path d="M0 64 Q22 ${46+i*6} 44 60 T88 56 V88 H0Z" fill="${land}"/><path d="M0 74 Q30 66 60 76 T88 72 V88 H0Z" fill="${land}" opacity=".7"/></svg>`;
}

/* ---------- 品目の詳細(v12): 1品目=1ページ。見る→同じ画面で直せる ---------- */
route('i',rid=>{
  const [id,ix]=(rid||'').split('/'),a=A[id],it=a&&a.items&&a.items[+ix];
  if(!it)return appbar('')+empty('activity','品目 なし');
  const amt=itemAmt(it),am=it.lines&&it.lines.length>1?lnAmts(it.lines):null;
  const mine=xShares(a.x&&X[a.x]||{pay:id,basis:'items'})[+ix];
  return appbar('品目',{actions:[{icon:'edit',label:'編集',onclick:`itOpen('${id}',${ix})`}]})
    +`<section class="it-hero"><div class="it-n">${esc(it.n)}</div><div class="it-a num">${yen(amt)}</div><div class="it-m">${esc(payLabel(a))} ・ 税${it.tax||8}%${it.memo?' ・ '+esc(it.memo):''}</div></section>`
    +(am?sh('内訳')+list([row({cls:'plain',t:'本体',v:`<span class="num">${yen(am[0])}</span>`}),...it.lines.slice(1).map((l,j)=>row({cls:'plain',t:esc(lnName(l))+(l.k==='pct'?` <span class="faint">${Math.abs(lnV(l))}%</span>`:''),v:`<span class="num" >${am[j+1]<0?'−':'＋'}${yen(Math.abs(am[j+1]))}</span>`})),row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(amt)}</b>`})],'flat'):'')
    +sh('使った人')+`<div class="it-bs">${whoChips(peopleOf(a),usersOf(it),`itSetU('${id}',${ix},'$v')`)}</div><div class="it-note">自分の分 ${yen(mine)}</div>`
    +`<div style="margin-top:20px"><button class="btn block ghost" type="button" onclick="itOpen('${id}',${ix})">この品目を編集</button></div>`;
});
/** 品目の「使った人」を切り替える。自分の支出は計算で出る(0になれば支出は消え、無ければ作る)。取り消せる */
function itSetU(id,ix,w){const a=A[id],snap=bpSnap(a);toggleUser(a.items[ix],w);xEnsure(id);save();rerender();toast('使った人を変えました',()=>bpRestore(a,snap))}
let itD=null;
itemEd('it',{items:()=>itD.items,redraw:()=>setSheetBody(itBody()),upd:i=>{const e=document.getElementById('ie-eq-it-'+i);if(e)e.innerHTML=IED.it.eq(i)}});
const itBody=()=>IED.it.html()+`<div class="it-memo"><label>メモ<input value="${esc(itD.items[itD.ix].memo||'')}" placeholder="任意" oninput="itD.items[itD.ix].memo=this.value"></label></div>`
;
function itOpen(id,ix){
  const a=A[id];itD={id,ix:+ix,items:[itemToEdit(a.items[+ix])]};itD.ix=0;itD.at=+ix;IED.it.open=0;
  openSheet({title:'品目を編集',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:itBody()});
  crFooter(`<button class="btn block" type="button" onclick="itSave()">保存</button>`);
}
function itAdd(id){const a=A[id];itD={id,ix:0,at:-1,isNew:1,items:[{n:'',price:0,tax:8,users:['me']}]};IED.it.open=0;
  openSheet({title:'品目を追加',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:itBody()});
  crFooter(`<button class="btn block" type="button" onclick="itSave()">追加</button>`)}
function itSave(){
  const a=A[itD.id],i=itD.items[0];if(!i.n||!itemAmt(i)){toast('品名と金額を入れてください');return}
  const snap=bpSnap(a),o={n:i.n,price:itemAmt(i),tax:i.tax||8,users:usersOf(i).slice()},ln=lnSave(i);if(ln)o.lines=ln;if(i.memo)o.memo=i.memo;if(i.unk)o.unk=1;
  if(itD.at>=0)a.items[itD.at]=o;else a.items.push(o);
  itemsChanged(itD.id);closeSheet();crFooter('');save();rerender();toast('保存しました',()=>{bpRestore(a,snap);save();rerender()});
}
function itDel(){const a=A[itD.id];if(a.items.length<2){toast('最後の1品目は削除できません');return}
  const snap=bpSnap(a);a.items.splice(itD.at,1);itemsChanged(itD.id);closeSheet();crFooter('');go('#/a/'+itD.id);toast('削除しました',()=>{bpRestore(a,snap);save();rerender()})}
