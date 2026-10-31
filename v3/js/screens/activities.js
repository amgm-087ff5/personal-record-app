/* =====================================================================
   タイムライン — アプリの背骨。上が未来、下が過去、「いま」が境目。
   ルート: #/activities , #/a/{id}(出来事の詳細=その点の前後を切り取った軸), #/day/{date}(日のページ)
   ===================================================================== */

let actFilter='all';
const ACT_FILTERS=[['all','すべて'],['支払い','支払い'],['会話','会話'],['移動','移動']];
const dayOfDate=d=>Object.entries(A).find(([k,a])=>a.type==='日記'&&a.date===d);
const kidsOf=d=>{const day=dayOfDate(d);return day?day[1].kids.filter(k=>A[k]).map(k=>[k,A[k]]):[]};
const passFilter=a=>actFilter==='all'||a.type===actFilter;
const daySpent=d=>kidsOf(d).reduce((s,[k,a])=>s+(a.x&&X[a.x]?X[a.x].amt:0),0);
const dayDiary=d=>{const day=dayOfDate(d);return day&&day[1].body};
/** その日の項目(記録+未記録の予定)を時刻の新しい順に */
function dayItems(d){
  const items=kidsOf(d).filter(([k,a])=>passFilter(a)).map(([k,a])=>({time:a.time||'',kind:'rec',k}));
  if(actFilter==='all')evOn(d).filter(([k,e])=>!e.rec&&!e.hol).forEach(([k,e])=>items.push({time:e.all?'00:00':e.s,kind:'plan',k}));
  return items.sort((a,b)=>b.time.localeCompare(a.time));
}
const isFuturePlan=it=>it.kind==='plan'&&!EV[it.k].all&&EV[it.k].d===TODAY&&EV[it.k].s>NOW;
const itemHtml=(it,o={})=>it.kind==='rec'?momentRow(it.k,o):planRowAx(it.k,{...o,late:!o.future&&!isFuturePlan(it)});
function recordDates(){return [...new Set(Object.values(A).filter(a=>a.type==='日記'&&a.date<TODAY&&a.kids&&a.kids.some(k=>A[k]&&passFilter(A[k]))).map(a=>a.date))].sort().reverse()}
const axDay=(d,o={})=>{const items=dayItems(d);return d===TODAY?items.filter(isFuturePlan).map(it=>itemHtml(it,{future:true})).join('')+nowLine()+items.filter(it=>!isFuturePlan(it)).map(it=>itemHtml(it)).join(''):items.map(it=>itemHtml(it)).join('')};

route('activities',()=>{
  const upcoming=Object.entries(EV).filter(([k,e])=>!e.hol&&!e.rec&&e.d>TODAY).sort((a,b)=>a[1].d.localeCompare(b[1].d));
  const dates=recordDates().slice(0,10);
  const s=daySpent(TODAY);
  return largeTitle('タイムライン',{actions:[{icon:'search',label:'検索',onclick:"toast('検索はモックでは未対応です')"},{icon:'more',label:'このモックについて',onclick:'openAbout()'}]})
    +chips(ACT_FILTERS,actFilter,"actFilter='$v';rerender()")
    +`<div class="ax">`
    +(upcoming.length&&actFilter==='all'?`<a href="#/plan">${axEdge(`明日以降の予定 ${upcoming.length}件 ― ${relDay(upcoming[0][1].d)} ${esc(upcoming[0][1].t)}${IC.chev}`)}</a>`:'')
    +dayHead(TODAY,{href:`#/day/${TODAY}`,diary:dayDiary(TODAY),empty:'今日の日記を書く',sum:s?`自分 ${yen(s)}`:''})
    +axDay(TODAY)
    +dates.map(d=>{const sp=daySpent(d);return dayHead(d,{href:`#/day/${d}`,diary:dayDiary(d),sum:sp?`自分 ${yen(sp)}`:''})+axDay(d)}).join('')
    +axEdge('10月1日より前を読み込む',true)
    +`</div>`;
},{tab:'activities'});

/* ---------- 日のページ: 1日=1ページの日記と、その日の軸 ---------- */
route('day',d=>{
  const day=dayOfDate(d);const a=day&&day[1];const w=new Date(d+'T00:00').getDay();
  const items=dayItems(d);const sp=daySpent(d);
  const people=new Set(items.flatMap(i=>i.kind==='rec'?(A[i.k].roles||[]).map(r=>r[1]).filter(x=>x&&x!=='me'):[]));
  return appbar('',{actions:[{icon:'edit',label:'日記を書く',onclick:`openDiary('${d}')`}]})
    +`<section class="dp"><div class="dp-date"><b class="${w===0?'sun':''}">${+d.slice(8)}</b><span>${WD[w]}曜日<small>${d.slice(0,4)}年${+d.slice(5,7)}月${d===TODAY?'・今日':''}</small></span></div>`
    +(a&&a.body?`<p class="dp-text">${esc(a.body)}</p>`:`<button class="dp-text none" type="button" onclick="openDiary('${d}')" style="text-align:left">この日のことを書く…</button>`)
    +(a&&a.photos?`<div class="dp-photos">${Array.from({length:a.photos},(_,i)=>`<i role="img" aria-label="写真${i+1}"></i>`).join('')}</div>`:'')
    +`</section>`
    +`<div class="stats"><div><b>${items.filter(i=>i.kind==='rec').length}件</b><span>出来事</span></div>${sp?`<div><b>${yen(sp)}</b><span>自分の支出</span></div>`:''}${people.size?`<div><b class="knots" style="margin-top:2px">${[...people].map(p=>knot(p,'sm')).join('')}</b><span>交わった縁</span></div>`:''}</div>`
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
  const segs=[{v:me,cls:'me',label:'自分の支出',href:x?`#/x/${a.x}`:''}];
  if(rest>0){const who=a.adv?E[a.adv[0]].n.split(' ').pop()+'さん':'ほかの人';segs.push({v:rest,cls:a.adv&&a.adv[2]==='未精算'?'owed':'other',label:a.adv?`${who}の分・${a.adv[2]}`:'ほかの人の分'})}
  return splitBar(segs,a.amt);
}
function momentView(id,a){
  const day=A[a.parent];const d=day?day.date:TODAY;
  const [ncls,icon]=AX_NODE[a.type]||['','activity'];
  const all=dayItems(d);const i=all.findIndex(it=>it.kind==='rec'&&it.k===id);
  const later=i<0?[]:all.slice(Math.max(0,i-2),i),earlier=i<0?[]:all.slice(i+1,i+3);
  const pl=(a.roles||[]).find(r=>['支払先','場所','到着'].includes(r[0]));
  const meta=[pl?`<span>${IC.place}${esc(placeName(pl))}</span>`:'',a.pay?`<span>${IC.pay}${esc(a.pay)}</span>`:''].join('');
  const card=`<div class="kind">${IC[icon]}${esc(a.type)}</div><h1>${esc(a.title)}</h1>${a.amt!=null?`<div class="big">${yen(a.amt)}</div>`:''}${meta?`<div class="meta">${meta}</div>`:''}${a.type==='支払い'?paySplit(a):''}`;
  const shop=(a.roles||[]).find(r=>r[0]==='支払先');
  const ev=Object.entries(EV).find(([k,e])=>e.rec===id);
  const att=(EXTRA.attach||{})[id];
  return appbar('',{actions:[menuAct(id)]})
    +`<div class="ax">`+dayHead(d,{href:`#/day/${d}`,diary:dayDiary(d)})
    +later.map(it=>itemHtml(it,{cls:'dim'})).join('')
    +focusRow({time:a.time,node:{cls:ncls,icon},card})
    +earlier.map(it=>itemHtml(it,{cls:'dim'})).join('')
    +`</div>`
    +(a.adv&&a.adv[2]==='未精算'?suggest(`${esc(E[a.adv[0]].n)}さんの分 <b class="num">${yen(a.adv[1])}</b> を立て替え中`,{label:'精算済みにする',onclick:`settle('${id}')`}):'')
    +((a.roles||[]).length?sh('交わった人・場所',a.roles.length)+list(a.roles.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +(shop&&!shop[1]?suggest(`「${esc(shop[2])}」はまだ登録されていません`,{label:'店として登録',onclick:`registerShop('${id}')`}):'')
    +(a.items?sh('品目',a.items.length,{label:showBurden?'誰の分かを隠す':'誰の分か表示',onclick:'showBurden=!showBurden;rerender()'})+list([...itemsList(id,a.items),row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(a.items.reduce((s,i)=>s+i.price,0))}</b>`})],'flat'):'')
    +(a.body?sh('内容')+`<div class="list"><p style="padding:14px 16px;line-height:1.85">${esc(a.body)}</p></div>`:'')
    +(ev?sh('元の予定')+list([row({href:`#/ev/${ev[0]}`,lead:`<span class="ax-node plan" style="margin:0">${IC.schedule}</span>`,t:esc(ev[1].t),s:`${fmtMDW(ev[1].d)} ${ev[1].s||''}`,chev:1})]):'')
    +(att?sh('添付',1)+`<div class="list" style="padding:12px"><img src="${att}" alt="レシートの写真" style="max-width:120px;border-radius:8px;display:block"></div>`:'');
}
function registerShop(aid){
  const a=A[aid];const r=a.roles.find(r=>r[0]==='支払先');const n=r[2];
  const id=uid('e');E[id]={n,i:n[0],t:['shop'],facts:[]};r[1]=id;save();rerender();
  toast(`「${n}」を店として登録しました`,()=>{delete E[id];r[1]=null});
}
function settle(id){const a=A[id];a.adv[2]='精算済み';save();rerender();toast('精算済みにしました',()=>{a.adv[2]='未精算'})}
