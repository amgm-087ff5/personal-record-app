/* =====================================================================
   タイムライン — アプリの背骨。上が未来、下が過去、「いま」が境目。
   ルート: #/activities , #/a/{id}(出来事の詳細=その点の前後を切り取った軸), #/day/{date}(日のページ)
   ===================================================================== */

let actFilter='all';let actMonth=TODAY.slice(0,7);
const monthShift=n=>{const [y,m]=actMonth.split('-').map(Number);const d=new Date(y,m-1+n,1);actMonth=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');rerender()};
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
function recordDates(){const ds=new Set(Object.values(A).filter(a=>a.type==='日記'&&a.date<TODAY&&a.kids&&a.kids.some(k=>A[k]&&passFilter(A[k]))).map(a=>a.date));
  if(planVisible())Object.values(EV).forEach(e=>{if(!e.rec&&!e.hol&&e.d<TODAY&&e.d>=TODAY.slice(0,8)+'01')ds.add(e.d)});
  return [...ds].sort().reverse()}
const axDay=(d,o={})=>{const items=dayItems(d);return d===TODAY?items.filter(isFuturePlan).map(it=>itemHtml(it,{future:true})).join('')+nowLine()+items.filter(it=>!isFuturePlan(it)).map(it=>itemHtml(it)).join(''):items.map(it=>itemHtml(it)).join('')};

route('activities',()=>{
  const upcoming=Object.entries(EV).filter(([k,e])=>!e.hol&&!e.rec&&e.d>TODAY).sort((a,b)=>a[1].d.localeCompare(b[1].d));
  const isNow=actMonth===TODAY.slice(0,7),dates=recordDates().filter(d=>d.startsWith(actMonth));
  const allD=[...recordDates(),TODAY],minM=allD.sort()[0].slice(0,7),maxM=TODAY.slice(0,7);
  const pm=+TODAY.slice(5,7)===1?12:+TODAY.slice(5,7)-1;
  const s=daySpent(TODAY);
  return largeTitle('タイムライン',{actions:[{text:'日記を書く',icon:'edit',primary:true,onclick:`openDiary('${TODAY}')`},{icon:'search',label:'検索',onclick:"toast('検索はモックでは未対応です')"},{icon:'more',label:'このモックについて',onclick:'openAbout()'}]})
    +`<div class="toolbar">${periodNav({label:`${+actMonth.slice(5)}月`,sub:actMonth.slice(0,4),prev:{on:'monthShift(-1)',dis:actMonth<=minM},next:{on:'monthShift(1)',dis:actMonth>=maxM}})}${chips(ACT_FILTERS,actFilter,"actFilter='$v';rerender()")}</div>`
    +`<div class="ax">`
    +(isNow&&upcoming.length&&actFilter==='all'?`<a href="#/plan">${axEdge(`明日以降の予定 ${upcoming.length}件${IC.chev}`)}</a>`:'')
    +(isNow?axGroup(dayHead(TODAY,{href:`#/day/${TODAY}`,diary:dayDiary(TODAY),empty:'今日の日記を書く',sum:s?`自分 ${yen(s)}`:''}),axDay(TODAY),{cls:'today'}):'')
    +dates.map(d=>{const sp=daySpent(d);return axGroup(dayHead(d,{href:`#/day/${d}`,diary:dayDiary(d),sum:sp?`自分 ${yen(sp)}`:''}),axDay(d))}).join('')
    +(!isNow&&!dates.length?`<div class="it-note" style="text-align:center;padding:32px 0">この月の記録はありません</div>`:'')
    +`</div>`;
},{tab:'activities'});

/* ---------- 日のページ: 1日=1ページの日記と、その日の軸 ---------- */
route('day',d=>{
  const day=dayOfDate(d);const a=day&&day[1];const w=new Date(d+'T00:00').getDay();
  const items=dayItems(d);const sp=daySpent(d);
  const people=new Set(items.flatMap(i=>i.kind==='rec'?(A[i.k].roles||[]).map(r=>r[1]).filter(x=>x&&x!=='me'):[]));
  const dl=[...new Set([...Object.values(A).filter(x=>x.type==='日記').map(x=>x.date),TODAY])].sort();const pi=dl.indexOf(d);const prev=dl.filter(x=>x<d).pop(),next=dl.find(x=>x>d);
  return appbar('',{actions:[...(next?[{icon:'up',label:'次の日(未来へ)',onclick:`go('#/day/${next}')`}]:[]),...(prev?[{icon:'down',label:'前の日(過去へ)',onclick:`go('#/day/${prev}')`}]:[]),{icon:'edit',label:'日記を書く',onclick:`openDiary('${d}')`}]})
    +`<section class="dp"><div class="dp-date"><b>${+d.slice(8)}</b><span>${WD[w]}曜日<small>${d.slice(0,4)}年${+d.slice(5,7)}月${d===TODAY?'・今日':''}</small></span></div>`
    +(a&&a.body?`<p class="dp-text">${esc(a.body)}</p>`:`<button class="dp-text none" type="button" onclick="openDiary('${d}')" style="text-align:left">この日のことを書く…</button>`)
    +(a&&a.photos?`<div class="dp-photos">${Array.from({length:a.photos},(_,i)=>photoThumb(d,i)).join('')}</div>`:'')
    +`</section>`
    +`<div class="stats"><div><b>${items.filter(i=>i.kind==='rec').length}件</b><span>出来事</span></div>${sp?`<div><b>${yen(sp)}</b><span>自分の支出</span></div>`:''}</div>`+(people.size?sh('この日に交わった縁',people.size)+`<div class="dp-kin">${[...people].map(p=>eref(p)).join('')}</div>`:'')
    +sh('この日の時間軸')
    +(items.length?`<div class="ax">${axGroup('',axDay(d),{cls:d===TODAY?'today':''})}</div>`:empty('activity','この日の出来事はまだありません',{label:'出来事を追加',onclick:`openNewHappening('${d}')`}));
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
function momentView(id,a){
  const day=A[a.parent];const d=day?day.date:TODAY;
  const [,icon]=AX_NODE[a.type]||['','activity'];
  const pl=(a.roles||[]).find(r=>['支払先','場所','到着'].includes(r[0]));
  const ppl=(a.roles||[]).filter(r=>r[1]&&E[r[1]]&&r[1]!=='me'&&!['支払先','場所','到着','出発'].includes(r[0])).map(r=>r[1]);
  const meta=[pl?`<span>${IC.place}${esc(placeName(pl))}</span>`:'',a.pay?`<span>${IC.pay}${esc(a.pay)}</span>`:'',ppl.length?`<span>${knots(ppl)}${ppl.map(p=>esc(E[p].n.split(' ').pop())).join('、')}</span>`:''].join('');
  const isPay=a.type==='支払い';
  const cap=`<section class="cap"><div class="kind">${IC[icon]}${esc(a.type)}<span class="tm">${esc(a.time||'')}</span></div><h1>${esc(a.title)}</h1>${a.amt!=null?`<div class="big">${yen(a.amt)}</div>`:''}${meta?`<div class="meta">${meta}</div>`:''}${isPay?paySplit(a):''}</section>`;
  const x=a.x&&X[a.x];
  /* 子: 支払い → 自分の支出 を └ でぶら下げる。自分の支出は半券チケット(DESIGN v4 = 自分の支出の形) */
  const child=x?`<div class="hang">${ticket({href:`#/x/${a.x}`,t:'自分の支出',s:esc(catPath(x.cat)),amt:esc(yen(x.amt)),cls:'fill',chev:true})}</div>`:'';
  const shop=(a.roles||[]).find(r=>r[0]==='支払先');
  const ev=Object.entries(EV).find(([k,e])=>e.rec===id);
  const att=(EXTRA.attach||{})[id];
  return appbar('',{actions:[menuAct(id)]})
    +pinView({later:[{day:d}],earlier:[],cap:cap+child})
    +(a.adv&&a.adv[2]==='未精算'?`<div class="suggest owe"><span class="pbar lg" aria-hidden="true"><i class="owed" style="flex:1"></i></span><span class="t">${esc(E[a.adv[0]].n.split(' ').pop())}さんの分 <b class="num">${yen(a.adv[1])}</b> 未精算</span><button class="btn sm" type="button" onclick="settle('${id}')">精算済みにする</button></div>`:'')
    +shopTodoSug(id,a)
    +((a.roles||[]).length?sh('交わった人・場所',a.roles.length,shop&&!shop[1]?{label:'支払先を店として登録',onclick:`registerShop('${id}')`}:null)+list(a.roles.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +(a.items?sh('品目',itemGroups(a.items).length,{label:'追加',icon:'plus',onclick:`itAdd('${id}')`})+list([...itemsList(id,a.items),row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(a.items.reduce((s,i)=>s+itemAmt(i),0))}</b>`})],'flat'):'')
    +(a.items&&ipPartner(a)?sh('負担')+list([row({cls:'plain',t:bpText(a),s:`自分 ${yen(a.x&&X[a.x]?X[a.x].amt:0)}、${esc(ipPartner(a))}さん ${yen(a.adv?a.adv[1]:0)}`,chev:1,onclick:`bpOpen('${id}')`})]):'')
    +(a.body?sh('内容')+`<div class="list"><p style="padding:14px 16px;line-height:1.85">${esc(a.body)}</p></div>`:'')
    +(ev?sh('元の予定')+objs([row({href:`#/ev/${ev[0]}`,lead:`<span class="ax-node plan" style="margin:0"></span>`,t:esc(ev[1].t),s:`${fmtMDW(ev[1].d)} ${ev[1].s||''}`,chev:1})]):'')
    +(att?sh('添付',1)+`<div class="list" style="padding:12px"><img src="${att}" alt="レシートの写真" style="max-width:120px;border-radius:8px;display:block"></div>`:'');
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
  if(!it)return appbar('')+empty('activity','この品目は見つかりません');
  const pt=ipPartner(a),amt=itemAmt(it),am=it.lines&&it.lines.length>1?lnAmts(it.lines):null;
  const bs=['自分','半分',...(pt?[pt]:[])];
  const whole=a.split&&a.split.mode==='whole';
  const mine=payShares(a)[+ix];
  return appbar('品目',{actions:[{icon:'edit',label:'編集',onclick:`itOpen('${id}',${ix})`}]})
    +`<section class="it-hero"><div class="it-n">${esc(it.n)}</div><div class="it-a num">${yen(amt)}</div><div class="it-m">${esc(a.title)} ・ 税${it.tax||8}%${it.memo?' ・ '+esc(it.memo):''}</div></section>`
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
