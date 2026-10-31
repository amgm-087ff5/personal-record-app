/* =====================================================================
   記録(アクティビティ)タブ — 参照実装。他の画面はこの書き方に揃える。
   ルート: #/activities , #/a/{id}
   ===================================================================== */

let actFilter='all';
const ACT_FILTERS=[['all','すべて'],['支払い','支払い'],['会話','会話'],['移動','移動']];

/** 日のカードに出す行(記録 + その日の予定) */
function dayRows(d,{withPlans}={}){
  const day=Object.entries(A).find(([k,a])=>a.type==='日記'&&a.date===d);
  const acts=day?day[1].kids.map(k=>[k,A[k]]).filter(([k,a])=>a&&(actFilter==='all'||a.type===actFilter)):[];
  const rows=acts.map(([k,a])=>({time:a.time||'',html:evRow(k,a)}));
  if(withPlans&&actFilter==='all')evOn(d).filter(([k,e])=>!e.rec&&!e.hol).forEach(([k,e])=>rows.push({time:e.all?'':e.s,html:planRow(k,e)}));
  return rows.sort((a,b)=>a.time.localeCompare(b.time)).map(r=>r.html);
}
function evRow(k,a){
  const shop=a.roles&&a.roles.find(r=>r[0]==='支払先');
  const place=a.roles&&a.roles.find(r=>['場所','到着'].includes(r[0]));
  const sub=shop?(shop[1]?E[shop[1]].n:shop[2]):place?(place[1]?E[place[1]].n:place[2]):'';
  const mine=a.x&&X[a.x]?X[a.x].amt:null;
  return `<a class="ev" href="#/a/${k}"><span class="time">${esc(a.time||'')}</span><span class="spine"></span>${glyph('activity',ACT_IC[a.type])}<span class="t">${esc(a.title)}${sub?`<small>${esc(sub)}</small>`:''}</span>${a.amt!=null?`<span><span class="amt">${yen(a.amt)}</span>${mine!=null&&mine!==a.amt?`<span class="mine">自分 ${yen(mine)}</span>`:''}</span>`:''}</a>`;
}
function planRow(k,e){
  return `<a class="ev plan" href="#/ev/${k}"><span class="time">${e.all?'終日':esc(e.s)}</span><span class="spine"></span>${glyph('schedule')}<span class="t">${esc(e.t)}<small>予定</small></span></a>`;
}
function calBadge(d,today){const w=new Date(d+'T00:00').getDay();return `<span class="cal ${w===0?'sun':w===6?'sat':''} ${today?'today':''}" aria-label="${fmtMDW(d)}"><i>${WD[w]}</i><b>${+d.slice(8)}</b></span>`}
function dayCard(d){
  const day=Object.entries(A).find(([k,a])=>a.type==='日記'&&a.date===d);
  const isToday=d===TODAY;
  const rows=dayRows(d,{withPlans:isToday});
  if(!isToday&&!rows.length)return '';
  const body=day&&day[1].body;
  const head=`<div class="day-h">${calBadge(d,isToday)}<div class="diary ${body?'':'none'}">${body?esc(body):isToday?'今日の日記はまだありません':'日記なし'}</div>${day?`<a class="iconbtn" href="#/a/${day[0]}" aria-label="${fmtMD(d)}の日記を開く">${IC.chev}</a>`:''}</div>`;
  return `<section class="day" aria-label="${fmtMDW(d)}">${head}${rows.join('')||`<div class="hint" style="padding:0 14px 12px">記録はまだありません</div>`}${isToday&&!body?`<div class="day-f"><button class="btn sm tonal" type="button" onclick="openDiary('${TODAY}')">${IC.edit}日記を書く</button></div>`:''}</section>`;
}
function recordDates(){return [...new Set(Object.values(A).filter(a=>a.type==='日記'&&a.date<TODAY&&a.kids&&a.kids.length).map(a=>a.date))].sort().reverse()}

route('activities',()=>{
  const monthCount=Object.values(A).filter(a=>a.type!=='日記'&&a.parent&&A[a.parent].date.slice(0,7)===TODAY.slice(0,7)).length;
  const plansToday=evOn(TODAY).filter(([k,e])=>!e.rec&&!e.hol).length;
  const todoToday=typeof todoTodayCount==='function'?todoTodayCount():0;
  const dates=recordDates().slice(0,10);
  return largeTitle('記録',{actions:[{icon:'more',label:'このモックについて',onclick:'openAbout()'}],metrics:[
      {l:'今月',v:monthCount+'件'},{l:'今日の予定',v:plansToday+'件',href:'#/plan'},{l:'今日のToDo',v:todoToday+'件',href:'#/plan?view=todo'}]})
    +chips(ACT_FILTERS,actFilter,"actFilter='$v';rerender()")
    +dayCard(TODAY)
    +dates.map(dayCard).join('')
    +`<p class="hint" style="text-align:center;padding:8px 0 16px">これより前の記録は月ごとに読み込みます</p>`;
},{tab:'activities'});

/* ---------- 詳細 ---------- */
route('a',id=>{
  const a=A[id];if(!a)return appbar('記録')+empty('activity','activity','この記録は削除されたか、見つかりません');
  if(a.type==='日記')return diaryView(id,a);
  if(a.type==='支払い')return paymentView(id,a);
  return eventView(id,a);
},{tab:'activities'});

const menuAct=id=>({icon:'more',label:'その他の操作',onclick:`actMenu('${id}')`});
function actMenu(id){
  openSheet({title:'操作',body:list([
    row({lead:glyph('activity','edit'),t:'編集',onclick:`closeSheet();toast('編集はモックでは未対応です')`}),
    row({lead:`<span class="g" style="--gc:var(--danger);--gs:var(--danger-soft)">${IC.trash}</span>`,t:'<span style="color:var(--danger)">削除</span>',onclick:`deleteActivity('${id}')`})])});
}
function deleteActivity(id){
  const a=A[id],p=a.parent&&A[a.parent];const snap=JSON.stringify({a,x:a.x?X[a.x]:null});
  if(p)p.kids=p.kids.filter(k=>k!==id);const xid=a.x;delete A[id];if(xid)delete X[xid];
  closeSheet();save();goBack();
  toast('記録を削除しました',()=>{const s=JSON.parse(snap);A[id]=s.a;if(xid)X[xid]=s.x;if(p)p.kids.push(id)});
}

function diaryView(id,a){
  const kids=a.kids.map(k=>[k,A[k]]).filter(([k,x])=>x).sort((x,y)=>(x[1].time||'').localeCompare(y[1].time||''));
  const spent=kids.filter(([k,x])=>x.x&&X[x.x]).reduce((s,[k,x])=>s+X[x.x].amt,0);
  const photos=a.photos?`<div style="display:flex;gap:8px;padding:0 20px 18px">${Array.from({length:a.photos},(_,i)=>`<span style="width:64px;height:64px;border-radius:8px;background:linear-gradient(135deg,var(--sunk),var(--brand-soft))" role="img" aria-label="写真${i+1}"></span>`).join('')}</div>`:'';
  /* 日めくりの1枚 → その日の時間軸(子)をぶら下げる */
  return appbar('',{actions:[{icon:'edit',label:'日記を編集',onclick:`openDiary('${a.date}')`}]})
    +calSheet(a.date,esc(a.body||''),`<span class="grow"></span><span class="faint" style="font:var(--t-cap);padding-bottom:6px;text-align:right">出来事 ${kids.length}件${spent?`<br>自分の支出 ${yen(spent)}`:''}</span>`).replace('</section>',photos+'</section>')
    +sh('この日の時間軸',kids.length)
    +(kids.length?`<div class="day">${kids.map(([k,x])=>evRow(k,x)).join('')}</div>`:empty('activity','activity','この日の出来事はまだありません',{label:'出来事を追加',onclick:`openNewHappening('${a.date}')`}));
}
/** 主役の前後(同じ日の出来事)を時間軸に並べる */
function neighbors(id,a){
  const day=A[a.parent];if(!day)return {before:[],after:[]};
  const ks=day.kids.filter(k=>A[k]).sort((x,y)=>(A[x].time||'').localeCompare(A[y].time||''));const i=ks.indexOf(id);
  const m=k=>({time:A[k].time,title:A[k].title,href:'#/a/'+k});
  return {before:ks.slice(Math.max(0,i-2),i).map(m),after:ks.slice(i+1,i+3).map(m)};
}
const actKind=a=>`${IC[ACT_IC[a.type]]||IC.activity}${esc(a.type)}`;
/** 品目を「同じ名前+同じ値段」でまとめる(表示だけ。データは1行=1個) */
let showBurden=false;const openGroups=new Set();
function itemGroups(items){const g=[];items.forEach((it,i)=>{const f=g.find(x=>x.n===it.n&&x.price===it.price);if(f)f.idx.push(i);else g.push({n:it.n,price:it.price,tax:it.tax,idx:[i]})});return g}
function itemsList(id,items){
  return itemGroups(items).map((g,gi)=>{
    const key=id+':'+gi,multi=g.idx.length>1,open=openGroups.has(key);
    const burden=showBurden?[...new Set(g.idx.map(i=>items[i].b))].map(b=>tag(b,{cls:b==='自分'?'brand':''})).join(''):'';
    const main=row({t:`${esc(g.n)}${multi?` <span class="faint">×${g.idx.length}</span>`:''}`,s:`${multi?`@${yen(g.price)} · `:''}税${g.tax}%${burden?'':''}`,v:`${burden?`<span style="display:inline-flex;gap:4px;margin-right:8px;vertical-align:middle">${burden}</span>`:''}<span class="num" style="font-weight:600">${yen(g.price*g.idx.length)}</span>`,cls:'plain',onclick:multi?`openGroups.has('${key}')?openGroups.delete('${key}'):openGroups.add('${key}');rerender()`:'',chev:false});
    const subs=multi&&open?g.idx.map((i,n)=>row({cls:'plain',t:`<span class="faint">${n+1}個目</span> ${esc(items[i].memo||'メモなし')}`,v:tag(items[i].b)})).join(''):'';
    return main+subs;
  });
}

function paymentView(id,a){
  const x=a.x&&X[a.x];const day=A[a.parent];
  const shop=a.roles.find(r=>r[0]==='支払先');
  const others=a.roles.filter(r=>r!==shop);
  const unregistered=shop&&!shop[1];
  const att=(EXTRA.attach||{})[id];
  const nb=neighbors(id,a);
  /* 主役 = 時間軸に掛かった支払いのカプセル。自分の支出はそこから切り離した半券として └ でぶら下げる */
  const cur=capsule({kind:actKind(a),title:esc(a.title),big:yen(a.amt),sub:[esc(a.time||''),esc(a.pay||''),shop?esc(shop[1]?E[shop[1]].n:shop[2]):'']})
    +(x?`<div style="margin-top:8px">${hang(ticket({href:`#/x/${a.x}`,stub:`<span>自分の</span><span>支出</span>`,t:`<b class="num">${yen(x.amt)}</b>`,s:`${catPath(x.cat)}${xSp(x)==='非日常'?'・非日常':''}`,v:IC.chev}))}</div>`:'');
  return appbar('',{actions:[menuAct(id)]})
    +tlSlice({dayDate:day&&day.date,dayHref:day&&`#/a/${a.parent}`,before:nb.before,after:nb.after,cur})
    +(a.adv?`<div style="margin-top:12px">${banner('warn',`${esc(E[a.adv[0]].n)}さんへの立替 <b class="num">${yen(a.adv[1])}</b> が${esc(a.adv[2])}です`)}</div>`:'')
    +sh('関わった人・場所',a.roles.length)
    +list([shop?prop('支払先',eref(shop[1],shop[2])):'',...others.map(r=>prop(r[0],eref(r[1],r[2])))])
    +(unregistered?suggest(`「${esc(shop[2])}」はまだ登録されていません`,{label:'店として登録',onclick:`registerShop('${id}')`}):'')
    +sh('品目',a.items.length,{label:showBurden?'負担を隠す':'負担を表示',onclick:'showBurden=!showBurden;rerender()'})
    +list([...itemsList(id,a.items),row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(a.items.reduce((s,i)=>s+i.price,0))}</b>`})],'flat')
    +(att?sh('添付',1)+`<div class="list" style="padding:12px"><img src="${att}" alt="レシートの写真" style="max-width:120px;border-radius:8px;display:block"></div>`:'');
}
function registerShop(aid){
  const a=A[aid];const r=a.roles.find(r=>r[0]==='支払先');const n=r[2];
  const id=uid('e');E[id]={n,i:n[0],t:['shop'],facts:[]};r[1]=id;save();rerender();
  toast(`「${n}」を店として登録しました`,()=>{delete E[id];r[1]=null});
}

function eventView(id,a){
  const day=A[a.parent];const nb=neighbors(id,a);
  const ev=Object.entries(EV).find(([k,e])=>e.rec===id);
  const cur=capsule({kind:actKind(a),title:esc(a.title),sub:[esc(a.time||''),...(a.roles||[]).filter(r=>['場所','到着'].includes(r[0])).map(r=>esc(r[1]?E[r[1]].n:r[2]))]})
    +(ev?`<div style="margin-top:8px">${hang(`<a class="cap plan" href="#/ev/${ev[0]}" style="display:flex;align-items:center;gap:10px;padding:10px 16px;border-radius:20px"><span class="kind" style="color:var(--c-schedule)">${IC.schedule}元の予定</span><span class="grow ell">${esc(ev[1].t)}</span>${IC.chev}</a>`)}</div>`:'');
  return appbar('',{actions:[menuAct(id)]})
    +tlSlice({dayDate:day&&day.date,dayHref:day&&`#/a/${a.parent}`,before:nb.before,after:nb.after,cur})
    +((a.roles||[]).length?sh('関わった人・場所',a.roles.length)+list(a.roles.map(r=>prop(r[0],eref(r[1],r[2])))):'')
    +(a.body?sh('内容')+paper(null,esc(a.body)):'');
}
