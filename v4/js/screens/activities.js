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
   縮み具合 actP(0=広い / 1=縮んだ)は、動いた量 ÷ 縮められる幅(v4.13: ACT_R=86px)で決まる。どこでスクロールしても、動いた分だけ縮み・戻る。
   タイムラインは自分の枠を持たず、ページ(または一覧)がそのまま動く。
   開いた時・月の切替は、今日の札が上部のすぐ下(8px)に来る位置へ。いまの時刻は使わない。 */
/* v4.13: 縮められる量(px)。css/s-activities.css の 86px と同じ値。上部の高さは CSS で固定し、縮みは transform/opacity だけで見せる */
const ACT_R=86;
let actP=0,actLastY=null,actRaf=0,actWasOn=false,actFullH=0;
const actHdr=()=>document.getElementById('act-top');
/** 動くのが一覧の枠(幅が広い時の .pane-l)なら枠、窓なら null */
const actScroller=()=>innerWidth>=1100?document.querySelector('#app .pane-l'):null;
const actY=s=>s?s.scrollTop:scrollY;
/** 要素の上端の位置(文書の座標。枠があれば枠の中の座標) */
const actDocTop=(el,s)=>s?el.getBoundingClientRect().top-s.getBoundingClientRect().top+s.scrollTop:el.getBoundingClientRect().top+scrollY;
const actVH=s=>s?s.clientHeight:innerHeight;
const actH=s=>s?s.scrollHeight:document.documentElement.scrollHeight;
const actSet=(s,y)=>{if(s)s.scrollTop=y;else scrollTo({top:y,behavior:'instant'})};
/** v4.11: 縮み具合を上部に反映(CSS の --p と、縮んだ時の表示の切り替え) */
/* v4.13: 1フレームに1回だけ --p を書く。クラスは境目を越えた時だけ付け外しする(毎フレームの属性変更をなくす) */
function actApply(h){h=h||actHdr();if(!h)return;h.style.setProperty('--p',actP.toFixed(4));const l=actP>.375;if(h.classList.contains('is-line')!==l)h.classList.toggle('is-line',l)}
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
}
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
  const tb=`<div class="act-tb"><button class="act-ib" type="button" aria-label="レシートで支払い" onclick="openReceipt()">${IC.camera}</button><button class="act-ib" type="button" aria-label="出来事を追加" onclick="openNewHappening()">${IC.plus}</button></div>`;
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
  const acts=[{text:'レシートで支払い',icon:'camera',primary:true,onclick:'openReceipt()'},{text:'出来事',icon:'plus',onclick:'openNewHappening()'}];
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
function deleteActivity(id){
  const a=A[id],p=a.parent&&A[a.parent];const snap=JSON.stringify({a,x:a.x?X[a.x]:null});
  if(p)p.kids=p.kids.filter(k=>k!==id);const xid=a.x;delete A[id];if(xid)delete X[xid];
  closeSheet();save();goBack();
  toast('記録を削除しました',()=>{const s=JSON.parse(snap);A[id]=s.a;if(xid)X[xid]=s.x;if(p)p.kids.push(id)});
}

/** 品目を「同じ名前+同じ合計+同じ金額行」でまとめる(表示だけ。データは1行=1個) */
let showBurden=false;const openGroups=new Set();
function itemGroups(items){const g=[];items.forEach((it,i)=>{const f=g.find(x=>x.n===it.n&&x.price===it.price&&lnKey(x)===lnKey(it));if(f)f.idx.push(i);else g.push({n:it.n,price:it.price,lines:it.lines,tax:it.tax,idx:[i]})});return g}
/** 品目の一覧。同じものは「×N」にまとめ、開くと子(1個目,2個目…)が親の下に入れ子で並ぶ。金額行がある品目は補足行に内訳。o.x = 自分の支出側の表示 */
function itemsList(id,items,o={}){
  return itemGroups(items).map((g,gi)=>{
    const key=id+':'+gi,multi=g.idx.length>1,open=multi&&openGroups.has(key);
    const u=itemAmt(g),ad=lnSumHtml(g);
    const sub=o.x?(multi?`1個 ${yen(u)}`:esc(items[g.idx[0]].b)):`${multi?`1個 ${yen(u)}、`:''}${ad?ad+`<span class="adj-l">税${g.tax}%</span>`:`税${g.tax}%`}`;
    const main=row({cls:`plain grp${multi?' multi':''}${open?' open':''}`,t:`${esc(g.n)}${multi?` <span class="faint">×${g.idx.length}</span><span class="grp-chev" aria-hidden="true">${IC.chev}</span>`:''}`,s:sub,
      v:`<span class="num" style="font-weight:600">${yen(u*g.idx.length)}</span>`,
      chev:multi?0:1,onclick:multi?`openGroups.has('${key}')?openGroups.delete('${key}'):openGroups.add('${key}');rerender()`:`go('#/i/${id}/${g.idx[0]}')`});
    const kids=open?`<div class="grp-kids" role="group" aria-label="${esc(g.n)}の内訳">${g.idx.map((i,n)=>row({cls:'plain kid',lead:`<span class="kid-n">${n+1}</span>`,t:esc(items[i].memo||'メモなし'),v:tag(items[i].b),chev:1,onclick:`go('#/i/${id}/${i}')`})).join('')}</div>`:'';
    return main+kids;
  });
}
/* ---------- 負担の計算(品目ごと / 全体割)。a.split が無ければ品目ごと(各品目の b)。
   全体割 = {mode:'whole', how:'half'|'amount', me}。自分の分を品目の金額比で按分する ---------- */
const ipPartner=a=>a.adv&&E[a.adv[0]]?E[a.adv[0]].n.split(' ').pop():null;
const ipMine=(it,partner)=>it.b===partner?0:it.b==='半分'?Math.floor(itemAmt(it)/2):itemAmt(it);
function payShares(a){
  const pt=ipPartner(a),amts=a.items.map(itemAmt),total=amts.reduce((s,x)=>s+x,0);
  if(a.split&&a.split.mode==='whole'){
    const me=a.split.how==='amount'?Math.min(a.split.me||0,total):Math.floor(total/2);let left=me;
    return amts.map((x,i)=>{const v=i===amts.length-1?left:Math.min(left,total?Math.floor(x*me/total):0);left-=v;return v});
  }
  return a.items.map(it=>ipMine(it,pt));
}
/** 支払いの品目・負担から、自分の支出と相手の分(立替)を計算し直す */
function applyShares(a){
  const x=a.x&&X[a.x];if(!x)return;
  const sh=payShares(a),whole=a.split&&a.split.mode==='whole',unk=new Set(x.items.filter(i=>i.unk).map(i=>i.n));
  x.items=a.items.map((i,k)=>{const o={n:i.n,price:sh[k],b:whole?(sh[k]===itemAmt(i)?'全額':'按分'):(i.b==='半分'?'1/2':'全額')};if(i.memo)o.memo=i.memo;if(unk.has(i.n))o.unk=1;return o}).filter(i=>i.price>0);
  x.amt=x.items.reduce((s,i)=>s+i.price,0);
  if(a.adv)a.adv[1]=a.amt-x.amt;
}
const bpSnap=a=>{const x=a.x&&X[a.x];return JSON.stringify({a:{items:a.items,amt:a.amt,adv:a.adv,split:a.split},x:x&&{items:x.items,amt:x.amt}})};
const bpRestore=(a,snap)=>{const o=JSON.parse(snap),x=a.x&&X[a.x];a.items=o.a.items;a.amt=o.a.amt;a.adv=o.a.adv;if(o.a.split)a.split=o.a.split;else delete a.split;if(x&&o.x){x.items=o.x.items;x.amt=o.x.amt}};

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
  a.items=items.map(i=>{const o={n:i.n,price:itemAmt(i),tax:i.tax||8,b:i.b||'自分'};const ln=lnSave(i);if(ln)o.lines=ln;if(i.memo)o.memo=i.memo;return o});
  a.amt=a.items.reduce((s,i)=>s+i.price,0);
  applyShares(a);
  closeSheet();crFooter('');save();rerender();
  toast('品目を保存しました',()=>bpRestore(a,snap));
}

/* ---------- 負担(誰がいくら)。品目の編集とは別の画面 ---------- */
let bpD=null;
const bpCalc=()=>{const a=A[bpD.id],t={items:a.items.map((i,k)=>({...i,b:bpD.bs[k]})),adv:a.adv,split:bpD.mode==='whole'?{mode:'whole',how:bpD.how,me:bpD.me}:undefined};
  const sh=payShares(t),total=t.items.reduce((s,i)=>s+itemAmt(i),0),me=sh.reduce((s,x)=>s+x,0);return {sh,total,me}};
const bpMode=m=>{bpD.mode=m;bpRe()};
function bpBody(){
  const a=A[bpD.id],pt=ipPartner(a),c=bpCalc();
  const head=seg([['items','食べた分'],['whole','全体で割る']],bpD.mode,"bpMode('$v')");
  const mid=bpD.mode==='items'
    ?sh('品目ごとの負担',a.items.length)+list(a.items.map((it,i)=>`<div class="bp-i"><div class="bp-h"><span>${esc(it.n)}</span><b class="num">${yen(itemAmt(it))}</b></div><div class="bp-c" role="group" aria-label="${esc(it.n)}の負担">${['自分','半分',pt].map(v=>`<button class="cr-b" type="button" aria-pressed="${bpD.bs[i]===v}" onclick="bpD.bs[${i}]='${v}';bpRe()">${esc(v)}</button>`).join('')}</div></div>`),'flat')
    :sh('全体の割り方')+`<div class="bp-w">${seg([['half','半分ずつ'],['amount','自分の金額を決める']],bpD.how,"bpD.how='$v';bpRe()")}`
      +(bpD.how==='amount'?`<label class="ie-a"><span>自分の負担</span><input class="ie-p num" inputmode="numeric" value="${bpD.me||''}" placeholder="¥0" oninput="bpD.me=+this.value.replace(/[^\\d]/g,'')||0;bpSum()"></label>`:'')+`</div>`;
  return head+mid+`<div id="bp-sum" style="margin-top:12px">${bpSumHtml(c,pt)}</div>`;
}
const bpSumHtml=(c,pt)=>list([row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(c.total)}</b>`}),row({cls:'plain',t:'自分',v:`<b class="num" style="color:var(--me)">${yen(c.me)}</b>`}),row({cls:'plain',t:`${esc(pt)}さん`,v:`<span class="num">${yen(c.total-c.me)}</span>`})],'flat');
const bpSum=()=>{const e=document.getElementById('bp-sum');if(e)e.innerHTML=bpSumHtml(bpCalc(),ipPartner(A[bpD.id]))};
const bpRe=()=>setSheetBody(bpBody());
function bpOpen(id){
  const a=A[id],sp=a.split&&a.split.mode==='whole'?a.split:null;
  bpD={id,mode:sp?'whole':'items',how:sp?sp.how:'half',me:sp?sp.me||0:0,bs:a.items.map(i=>i.b||'自分')};
  openSheet({title:'負担',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:bpBody()});
  crFooter(`<button class="btn block" type="button" onclick="bpSave()">保存</button>`);
}
function bpSave(){
  const a=A[bpD.id],snap=bpSnap(a);
  a.items.forEach((i,k)=>{i.b=bpD.bs[k]});
  if(bpD.mode==='whole')a.split={mode:'whole',how:bpD.how,me:bpD.me};else delete a.split;
  applyShares(a);closeSheet();crFooter('');save();rerender();
  toast('負担を保存しました',()=>bpRestore(a,snap));
}
const bpText=a=>a.split&&a.split.mode==='whole'?`全体で割る(${a.split.how==='amount'?'自分の金額を決めた':'半分ずつ'})`:'食べた分(品目ごと)';
/** 支払いの割合: 自分の分 / 相手の分(立替・未精算は斜線) */
function paySplit(a){
  const x=a.x&&X[a.x];const me=x?x.amt:0;const rest=a.amt-me;
  const segs=[{v:me,cls:'me',label:'自分の支出',noAmt:!!x}];
  if(rest>0){const who=a.adv?E[a.adv[0]].n.split(' ').pop()+'さん':'ほかの人';segs.push({v:rest,cls:a.adv&&a.adv[2]==='未精算'?'owed':'other',label:a.adv?`${who}の分・${a.adv[2]}`:'ほかの人の分'})}
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
function ckRow(c){const k=A[c],[ncls,icon]=AX_NODE[k.type]||['','activity'];const inst=INSTANT_TYPES.includes(k.type),n=kidActs(c).length;
  return `<a class="ck${inst?' inst':''}" href="#/a/${c}"><span class="ck-n ${ncls}">${IC[icon]||''}</span><span class="ck-tm num">${esc(timeText(k))}</span><span class="ck-t">${esc(k.title)}</span>${n?`<span class="ck-c">${n}件</span>`:''}${inst?'':`<span class="ck-chev">${IC.chev}</span>`}</a>`}
/** 子1つと、その子(孫)を案内線つきで字下げして並べる。最大3段 */
function ckKid(c,depth=1){const k=A[c];if(!k)return '';
  const body=k.type==='支払い'?payShell(c,{mode:'kid'}):ckRow(c);
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
/** 活動の中に支払いを追加: 金額だけで支払いを、日付・時刻・親=この活動にして開く */
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
  const cap=`<section class="cap"><div class="kind"><span class="lbl">${IC.pay}支払い</span><span class="tm">${esc(a.time||'')}</span></div><h1>${esc(payeeOf(a)||a.title)}</h1>${a.amt!=null?`<div class="pay-sum"><span>合計</span><b class="num">${yen(a.amt)}</b></div>`:''}${a.pay?`<div class="meta"><span>${IC.pay}${esc(a.pay)}</span></div>`:''}${paySplit(a)}${payShell(id,{mode:'pay'})}</section>`; /* v4.12: 半券は主役の中の下端(旧 .hang の └ は廃止) */
  /* v4.14: 親の活動は軸の並びに出さず、主役を包む枠(anc)として出す(親が子を包む) */
  const later=[{day:d}];
  /* 交わった人・場所 = 支払いの役割(支払先・支払者) + 親の活動の同席者(whoOf と同じ考え方) */
  const roles=[...(a.roles||[])];
  if(pa)(pa.roles||[]).forEach(r=>{if(r[1]&&r[1]!=='me'&&!['場所','出発','到着','支払先','支払者'].includes(r[0])&&!roles.some(q=>q[1]===r[1]))roles.push(r)});
  const shop=roles.find(r=>r[0]==='支払先');
  const att=(EXTRA.attach||{})[id];
  return appbar('',{actions:[menuAct(id)]})
    +pinView({later,earlier:[],cap,anc:ancPins(id)})
    +(a.adv&&a.adv[2]==='未精算'?`<div class="suggest owe"><span class="pbar lg" aria-hidden="true"><i class="owed" style="flex:1"></i></span><span class="t"><span class="nm">${esc(E[a.adv[0]].n.split(' ').pop())}さんの分 <b class="num">${yen(a.adv[1])}</b></span><span class="st">未精算</span></span><button class="btn sm" type="button" onclick="settle('${id}')">精算済みにする</button></div>`:'')
    +shopTodoSug(id,a)
    +(roles.length?sh('関わったエンティティ',roles.length,shop&&!shop[1]?{label:'支払先を店として登録',onclick:`registerShop('${id}')`}:null)+list(roles.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +(a.items?sh('品目',itemGroups(a.items).length,{label:'追加',icon:'plus',onclick:`itAdd('${id}')`})+list(itemsList(id,a.items),'flat'):'')
    +(a.items&&ipPartner(a)?sh('負担')+list([row({cls:'plain',t:bpText(a),s:`自分 ${yen(a.x&&X[a.x]?X[a.x].amt:0)}、${esc(ipPartner(a))}さん ${yen(a.adv?a.adv[1]:0)}`,chev:1,onclick:`bpOpen('${id}')`})]):'')
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
function settle(id){const a=A[id];a.adv[2]='精算済み';save();rerender();toast('精算済みにしました',()=>{a.adv[2]='未精算'})}

/** 写真のサムネイル(モックでは日付から決まる風景の絵) */
function photoThumb(d,i){
  const h=(+d.slice(8)*37+i*71)%40;const sky=`hsl(${195+h/2} 50% 74%)`,land=`hsl(${85+h} 28% 42%)`,sun=`hsl(${45+h/4} 85% 86%)`;
  return `<svg class="dp-ph" viewBox="0 0 88 88" role="img" aria-label="写真${i+1}"><rect width="88" height="88" fill="${sky}"/><circle cx="${20+i*18}" cy="26" r="9" fill="${sun}"/><path d="M0 64 Q22 ${46+i*6} 44 60 T88 56 V88 H0Z" fill="${land}"/><path d="M0 74 Q30 66 60 76 T88 72 V88 H0Z" fill="${land}" opacity=".7"/></svg>`;
}

/* ---------- 品目の詳細(v12): 1品目=1ページ。見る→同じ画面で直せる ---------- */
route('i',rid=>{
  const [id,ix]=(rid||'').split('/'),a=A[id],it=a&&a.items&&a.items[+ix];
  if(!it)return appbar('')+empty('activity','品目 なし');
  const pt=ipPartner(a),amt=itemAmt(it),am=it.lines&&it.lines.length>1?lnAmts(it.lines):null;
  const bs=['自分','半分',...(pt?[pt]:[])];
  const whole=a.split&&a.split.mode==='whole';
  const mine=payShares(a)[+ix];
  return appbar('品目',{actions:[{icon:'edit',label:'編集',onclick:`itOpen('${id}',${ix})`}]})
    +`<section class="it-hero"><div class="it-n">${esc(it.n)}</div><div class="it-a num">${yen(amt)}</div><div class="it-m">${esc(payLabel(a))} ・ 税${it.tax||8}%${it.memo?' ・ '+esc(it.memo):''}</div></section>`
    +(am?sh('内訳')+list([row({cls:'plain',t:'本体',v:`<span class="num">${yen(am[0])}</span>`}),...it.lines.slice(1).map((l,j)=>row({cls:'plain',t:esc(lnName(l))+(l.k==='pct'?` <span class="faint">${Math.abs(lnV(l))}%</span>`:''),v:`<span class="num" style="color:${am[j+1]<0?'var(--late)':'inherit'}">${am[j+1]<0?'−':'＋'}${yen(Math.abs(am[j+1]))}</span>`})),row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(amt)}</b>`})],'flat'):'')
    +(pt?sh('負担')+(whole?`<div class="it-note">全体で割る設定です。この品目の自分の分は ${yen(mine)}（負担画面で変更）</div>`
      :`<div class="it-bs" role="group" aria-label="負担">${bs.map(v=>`<button class="cr-b" type="button" aria-pressed="${it.b===v}" onclick="itSetB('${id}',${ix},'${v}')">${esc(v)}</button>`).join('')}</div><div class="it-note">自分の分 ${yen(mine)}</div>`):'')
    +`<div style="margin-top:20px"><button class="btn block ghost" type="button" onclick="itOpen('${id}',${ix})">この品目を編集</button></div>`;
});
function itSetB(id,ix,v){const a=A[id],snap=bpSnap(a);a.items[ix].b=v;applyShares(a);save();rerender();toast('負担を変更しました',()=>{bpRestore(a,snap);save();rerender()})}
let itD=null;
itemEd('it',{items:()=>itD.items,redraw:()=>setSheetBody(itBody()),upd:i=>{const e=document.getElementById('ie-eq-it-'+i);if(e)e.innerHTML=IED.it.eq(i)}});
const itBody=()=>IED.it.html()+`<div class="it-memo"><label>メモ<input value="${esc(itD.items[itD.ix].memo||'')}" placeholder="任意" oninput="itD.items[itD.ix].memo=this.value"></label></div>`
;
function itOpen(id,ix){
  const a=A[id];itD={id,ix:+ix,items:[itemToEdit(a.items[+ix])]};itD.ix=0;itD.at=+ix;IED.it.open=0;
  openSheet({title:'品目を編集',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:itBody()});
  crFooter(`<button class="btn block" type="button" onclick="itSave()">保存</button>`);
}
function itAdd(id){const a=A[id];itD={id,ix:0,at:-1,isNew:1,items:[{n:'',price:0,tax:8,b:'自分'}]};IED.it.open=0;
  openSheet({title:'品目を追加',full:true,left:{label:'閉じる',onclick:'closeSheet()'},body:itBody()});
  crFooter(`<button class="btn block" type="button" onclick="itSave()">追加</button>`)}
function itSave(){
  const a=A[itD.id],i=itD.items[0];if(!i.n||!itemAmt(i)){toast('品名と金額を入れてください');return}
  const snap=bpSnap(a),o={n:i.n,price:itemAmt(i),tax:i.tax||8,b:i.b||'自分'},ln=lnSave(i);if(ln)o.lines=ln;if(i.memo)o.memo=i.memo;
  if(itD.at>=0)a.items[itD.at]=o;else a.items.push(o);
  a.amt=a.items.reduce((s,x)=>s+x.price,0);applyShares(a);closeSheet();crFooter('');save();rerender();toast('保存しました',()=>{bpRestore(a,snap);save();rerender()});
}
function itDel(){const a=A[itD.id];if(a.items.length<2){toast('最後の1品目は削除できません');return}
  const snap=bpSnap(a);a.items.splice(itD.at,1);a.amt=a.items.reduce((s,x)=>s+itemAmt(x),0);applyShares(a);closeSheet();crFooter('');save();go('#/a/'+itD.id);toast('削除しました',()=>{bpRestore(a,snap);save();rerender()})}
