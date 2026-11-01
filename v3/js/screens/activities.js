/* =====================================================================
   タイムライン — アプリの背骨。上が未来、下が過去、「いま」が境目。
   ルート: #/activities , #/a/{id}(出来事の詳細=その点の前後を切り取った軸), #/day/{date}(日のページ)
   ===================================================================== */

let actFilter='all';
const ACT_FILTERS=[['all','すべて'],['予定','予定'],['支払い','支払い'],['会話','会話'],['移動','移動']];
const dayOfDate=d=>Object.entries(A).find(([k,a])=>a.type==='日記'&&a.date===d);
const kidsOf=d=>{const day=dayOfDate(d);return day?day[1].kids.filter(k=>A[k]).map(k=>[k,A[k]]):[]};
const passFilter=a=>actFilter==='all'||a.type===actFilter;
const planVisible=()=>actFilter==='all'||actFilter==='予定';
const daySpentAll=d=>kidsOf(d).reduce((s,[k,a])=>s+(a.x&&X[a.x]?X[a.x].amt:0),0);
const daySpent=d=>{const ps=kidsOf(d).filter(([k,a])=>a.x&&X[a.x]);return ps.length>=2?ps.reduce((s,[k,a])=>s+X[a.x].amt,0):0};
const dayDiary=d=>{const day=dayOfDate(d);return day&&day[1].body};
/** その日の項目(記録+未記録の予定)を時刻の新しい順に */
function dayItems(d){
  const items=actFilter==='予定'?[]:kidsOf(d).filter(([k,a])=>passFilter(a)).map(([k,a])=>({time:a.time||'',kind:'rec',k}));
  if(planVisible())evOn(d).filter(([k,e])=>!e.rec&&!e.hol).forEach(([k,e])=>items.push({time:e.all?'00:00':e.s,kind:'plan',k}));
  return items.sort((a,b)=>b.time.localeCompare(a.time));
}
const isFuturePlan=it=>it.kind==='plan'&&!EV[it.k].all&&EV[it.k].d===TODAY&&EV[it.k].s>NOW;
const itemHtml=(it,o={})=>it.kind==='rec'?momentRow(it.k,o):planRowAx(it.k,{...o,late:!o.future&&!isFuturePlan(it)});
function recordDates(){return [...new Set(Object.values(A).filter(a=>a.type==='日記'&&a.date<TODAY&&a.kids&&a.kids.some(k=>A[k]&&passFilter(A[k]))).map(a=>a.date))].sort().reverse()}
const axDay=(d,o={})=>{const items=dayItems(d);return d===TODAY?items.filter(isFuturePlan).map(it=>itemHtml(it,{future:true})).join('')+nowLine()+items.filter(it=>!isFuturePlan(it)).map(it=>itemHtml(it)).join(''):items.map(it=>itemHtml(it)).join('')};

route('activities',()=>{
  const upcoming=Object.entries(EV).filter(([k,e])=>!e.hol&&!e.rec&&e.d>TODAY).sort((a,b)=>a[1].d.localeCompare(b[1].d));
  const dates=recordDates().filter(d=>d>=TODAY.slice(0,8)+'01');
  const pm=+TODAY.slice(5,7)===1?12:+TODAY.slice(5,7)-1;
  const s=daySpent(TODAY);
  return largeTitle('タイムライン',{actions:[{icon:'search',label:'検索',onclick:"toast('検索はモックでは未対応です')"},{icon:'more',label:'このモックについて',onclick:'openAbout()'}]})
    +chips(ACT_FILTERS,actFilter,"actFilter='$v';rerender()")
    +`<div class="ax">`
    +(upcoming.length&&actFilter==='all'?`<a href="#/plan">${axEdge(`明日以降の予定 ${upcoming.length}件${IC.chev}`)}</a>`:'')
    +dayHead(TODAY,{href:`#/day/${TODAY}`,diary:dayDiary(TODAY),empty:'今日の日記を書く',sum:s?`自分 ${yen(s)}`:''})
    +axDay(TODAY)
    +dates.map(d=>{const sp=daySpent(d);return dayHead(d,{href:`#/day/${d}`,diary:dayDiary(d),sum:sp?`自分 ${yen(sp)}`:''})+axDay(d)}).join('')
    +axEdge(`${pm}月を読み込む`,true)
    +`</div>`;
},{tab:'activities'});

/* ---------- 日のページ: 1日=1ページの日記と、その日の軸 ---------- */
route('day',d=>{
  const day=dayOfDate(d);const a=day&&day[1];const w=new Date(d+'T00:00').getDay();
  const items=dayItems(d);const sp=daySpentAll(d);
  const people=new Set(items.flatMap(i=>i.kind==='rec'?(A[i.k].roles||[]).map(r=>r[1]).filter(x=>x&&x!=='me'):[]));
  const dl=[...new Set([...Object.values(A).filter(x=>x.type==='日記').map(x=>x.date),TODAY])].sort();const pi=dl.indexOf(d);const prev=dl.filter(x=>x<d).pop(),next=dl.find(x=>x>d);
  return appbar('',{actions:[...(next?[{icon:'up',label:'次の日(未来へ)',onclick:`go('#/day/${next}')`}]:[]),...(prev?[{icon:'down',label:'前の日(過去へ)',onclick:`go('#/day/${prev}')`}]:[]),{icon:'edit',label:'日記を書く',onclick:`openDiary('${d}')`}]})
    +`<section class="dp"><div class="dp-date"><b>${+d.slice(8)}</b><span>${WD[w]}曜日<small>${d.slice(0,4)}年${+d.slice(5,7)}月${d===TODAY?'・今日':''}</small></span></div>`
    +(a&&a.body?`<p class="dp-text">${esc(a.body)}</p>`:`<button class="dp-text none" type="button" onclick="openDiary('${d}')" style="text-align:left">この日のことを書く…</button>`)
    +(a&&a.photos?`<div class="dp-photos">${Array.from({length:a.photos},(_,i)=>photoThumb(d,i)).join('')}</div>`:'')
    +`</section>`
    +`<div class="stats"><div><b>${items.filter(i=>i.kind==='rec').length}件</b><span>出来事</span></div>${sp?`<div><b>${yen(sp)}</b><span>自分の支出</span></div>`:''}</div>`+(people.size?sh('この日に交わった縁',people.size)+`<div class="dp-kin">${[...people].map(p=>eref(p)).join('')}</div>`:'')
    +sh('この日の時間軸')
    +(items.length?`<div class="ax">${axDay(d)}</div>`:empty('activity','この日の出来事はまだありません',{label:'出来事を追加',onclick:`openNewHappening('${d}')`}));
},{tab:'activities'});

/* ---------- 出来事の詳細: その点の前後を切り取った軸 ---------- */
route('a',id=>{
  const a=A[id];if(!a)return appbar('')+empty('activity','この記録は削除されたか、見つかりません');
  if(a.type==='日記'){setTimeout(()=>location.replace('#/day/'+a.date));return ''}
  return momentView(id,a);
},{tab:'activities'});

const menuAct=id=>({icon:'more',label:'その他の操作',onclick:`actMenu('${id}')`});
function actMenu(id){
  openSheet({title:'操作',body:list([
    row({lead:ico('edit'),t:'編集',onclick:`closeSheet();toast('編集はモックでは未対応です')`}),
    row({lead:ico('trash','var(--danger)'),t:'<span style="color:var(--danger)">削除</span>',onclick:`deleteActivity('${id}')`})])});
}
function deleteActivity(id){
  const a=A[id],p=a.parent&&A[a.parent];const snap=JSON.stringify({a,x:a.x?X[a.x]:null});
  if(p)p.kids=p.kids.filter(k=>k!==id);const xid=a.x;delete A[id];if(xid)delete X[xid];
  closeSheet();save();goBack();
  toast('記録を削除しました',()=>{const s=JSON.parse(snap);A[id]=s.a;if(xid)X[xid]=s.x;if(p)p.kids.push(id)});
}

/** 品目を「同じ名前+同じ値段」でまとめる(表示だけ。データは1行=1個) */
let showBurden=false;const openGroups=new Set();
function itemGroups(items){const g=[];items.forEach((it,i)=>{const f=g.find(x=>x.n===it.n&&x.price===it.price);if(f)f.idx.push(i);else g.push({n:it.n,price:it.price,tax:it.tax,idx:[i]})});return g}
function itemsList(id,items){
  return itemGroups(items).map((g,gi)=>{
    const key=id+':'+gi,multi=g.idx.length>1,open=openGroups.has(key);
    const burden=showBurden?[...new Set(g.idx.map(i=>items[i].b))].map(b=>tag(b,{cls:b==='自分'?'brand':''})).join(''):'';
    const main=row({t:`${esc(g.n)}${multi?` <span class="faint">×${g.idx.length}</span>`:''}`,s:`${multi?`1個 ${yen(g.price)}、`:''}税${g.tax}%`,v:`${burden?`<span style="display:inline-flex;gap:4px;margin-right:8px;vertical-align:middle">${burden}</span>`:''}<span class="num" style="font-weight:600">${yen(g.price*g.idx.length)}</span>`,cls:'plain',onclick:multi?`openGroups.has('${key}')?openGroups.delete('${key}'):openGroups.add('${key}');rerender()`:'',chev:false});
    const subs=multi&&open?g.idx.map((i,n)=>row({cls:'plain',t:`<span class="faint">${n+1}個目</span> ${esc(items[i].memo||'メモなし')}`,v:tag(items[i].b)})).join(''):'';
    return main+subs;
  });
}
/** 支払いの割合: 自分の分 / 相手の分(立替・未精算は斜線) */
function paySplit(a){
  const x=a.x&&X[a.x];const me=x?x.amt:0;const rest=a.amt-me;
  const segs=[{v:me,cls:'me',label:'自分の支出',noAmt:!!x}];
  if(rest>0){const who=a.adv?E[a.adv[0]].n.split(' ').pop()+'さん':'ほかの人';segs.push({v:rest,cls:a.adv&&a.adv[2]==='未精算'?'owed':'other',label:a.adv?`${who}の分・${a.adv[2]}`:'ほかの人の分'})}
  return splitBar(segs,a.amt);
}
/** 全ての記録を時刻の新しい順に(日をまたいで前後をたどるため) */
function allMoments(){return Object.entries(A).filter(([k,a])=>a.type!=='日記'&&a.parent&&A[a.parent]).map(([k,a])=>({k,d:A[a.parent].date,time:a.time||''})).sort((x,y)=>(y.d+y.time).localeCompare(x.d+x.time))}
/** 前後2件ずつ。日が変わる所に日付を挟む */
function neighborsOf(id){
  const all=allMoments();const i=all.findIndex(m=>m.k===id);if(i<0)return {later:[],earlier:[]};
  const me=all[i];const pick=m=>({time:m.time,title:A[m.k].title,href:'#/a/'+m.k,icon:(AX_NODE[A[m.k].type]||[])[1]});
  const later=all.slice(Math.max(0,i-2),i);const earlier=all.slice(i+1,i+3);
  const L=[],R=[];let cur=null;
  later.forEach(m=>{if(m.d!==cur){L.push({day:m.d});cur=m.d}L.push(pick(m))});
  if(me.d!==cur)L.push({day:me.d});
  cur=me.d;earlier.forEach(m=>{if(m.d!==cur){R.push({day:m.d});cur=m.d}R.push(pick(m))});
  return {later:L,earlier:R};
}
function momentView(id,a){
  const day=A[a.parent];const d=day?day.date:TODAY;
  const [,icon]=AX_NODE[a.type]||['','activity'];
  const pl=(a.roles||[]).find(r=>['支払先','場所','到着'].includes(r[0]));
  const ppl=(a.roles||[]).filter(r=>r[1]&&E[r[1]]&&r[1]!=='me'&&!['支払先','場所','到着','出発'].includes(r[0])).map(r=>r[1]);
  const meta=[pl?`<span>${IC.place}${esc(placeName(pl))}</span>`:'',a.pay?`<span>${IC.pay}${esc(a.pay)}</span>`:'',ppl.length?`<span>${knots(ppl)}${ppl.map(p=>esc(E[p].n.split(' ').pop())).join('、')}</span>`:''].join('');
  const isPay=a.type==='支払い';
  const cap=`<section class="cap"><div class="kind">${IC[icon]}${esc(a.type)}<span class="tm">${esc(a.time||'')}</span></div><h1>${esc(a.title)}</h1>${a.amt!=null?`<div class="big">${yen(a.amt)}</div>`:''}${meta?`<div class="meta">${meta}</div>`:''}${isPay?paySplit(a):''}</section>`;
  const x=a.x&&X[a.x];
  /* 子: 支払い → 自分の支出 を └ でぶら下げる */
  const child=x?`<div class="hang"><a class="child" href="#/x/${a.x}">${ico('expense','var(--me)')}<span class="grow"><div class="lbl">自分の支出</div><div class="amt">${yen(x.amt)}</div></span>${catTag(x.cat)}<span class="chev">${IC.chev}</span></a></div>`:'';
  const nb=neighborsOf(id);
  const shop=(a.roles||[]).find(r=>r[0]==='支払先');
  const ev=Object.entries(EV).find(([k,e])=>e.rec===id);
  const att=(EXTRA.attach||{})[id];
  return appbar('',{actions:[menuAct(id)]})
    +pinView({later:nb.later,earlier:nb.earlier,cap:cap+child})
    +(a.adv&&a.adv[2]==='未精算'?`<div class="suggest owe"><span class="pbar lg" aria-hidden="true"><i class="owed" style="flex:1"></i></span><span class="t">${esc(E[a.adv[0]].n.split(' ').pop())}さんの分 <b class="num">${yen(a.adv[1])}</b> 未精算</span><button class="btn sm" type="button" onclick="settle('${id}')">精算済みにする</button></div>`:'')
    +((a.roles||[]).length?sh('交わった人・場所',a.roles.length,shop&&!shop[1]?{label:'支払先を店として登録',onclick:`registerShop('${id}')`}:null)+list(a.roles.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +(a.items?sh('品目',itemGroups(a.items).length,{label:showBurden?'誰の分かを隠す':'誰の分か表示',onclick:'showBurden=!showBurden;rerender()'})+list([...itemsList(id,a.items),row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(a.items.reduce((s,i)=>s+i.price,0))}</b>`})],'flat'):'')
    +(a.body?sh('内容')+`<div class="list"><p style="padding:14px 16px;line-height:1.85">${esc(a.body)}</p></div>`:'')
    +(ev?sh('元の予定')+objs([row({href:`#/ev/${ev[0]}`,lead:`<span class="ax-node plan" style="margin:0"></span>`,t:esc(ev[1].t),s:`${fmtMDW(ev[1].d)} ${ev[1].s||''}`,chev:1})]):'')
    +(att?sh('添付',1)+`<div class="list" style="padding:12px"><img src="${att}" alt="レシートの写真" style="max-width:120px;border-radius:8px;display:block"></div>`:'');
}
function registerShop(aid){
  const a=A[aid];const r=a.roles.find(r=>r[0]==='支払先');const n=r[2];
  const id=uid('e');E[id]={n,i:n[0],t:['shop'],facts:[]};r[1]=id;save();rerender();
  toast(`「${n}」を店として登録しました`,()=>{delete E[id];r[1]=null});
}
function settle(id){const a=A[id];a.adv[2]='精算済み';save();rerender();toast('精算済みにしました',()=>{a.adv[2]='未精算'})}

/** 写真のサムネイル(モックでは日付から決まる風景の絵) */
function photoThumb(d,i){
  const h=(+d.slice(8)*37+i*71)%40;const sky=`hsl(${195+h/2} 50% 74%)`,land=`hsl(${85+h} 28% 42%)`,sun=`hsl(${45+h/4} 85% 86%)`;
  return `<svg class="dp-ph" viewBox="0 0 88 88" role="img" aria-label="写真${i+1}"><rect width="88" height="88" fill="${sky}"/><circle cx="${20+i*18}" cy="26" r="9" fill="${sun}"/><path d="M0 64 Q22 ${46+i*6} 44 60 T88 56 V88 H0Z" fill="${land}"/><path d="M0 74 Q30 66 60 76 T88 72 V88 H0Z" fill="${land}" opacity=".7"/></svg>`;
}
