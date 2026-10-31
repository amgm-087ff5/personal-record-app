/* =====================================================================
   予定 — 月カレンダー(時間軸で選んだ日を見る) / ToDo / 予定の詳細 / ToDoの詳細
   ルート: #/plan(?view=cal|todo), #/ev/{id}, #/task/{id}
   ===================================================================== */

document.head.insertAdjacentHTML('beforeend','<style>'+`
.pl-cal{padding:8px 8px 10px}
.pl-mh{display:flex;align-items:center;gap:4px;height:44px}
.pl-mh b{flex:1;text-align:center;font:var(--t-head)}
.pl-wd,.pl-grid{display:grid;grid-template-columns:repeat(7,1fr)}
.pl-wd span{font:500 12px/24px var(--font);text-align:center;color:var(--ink-3)}
.pl-wd .sun{color:var(--danger)} .pl-wd .sat{color:var(--plan)}
.pl-cell{width:100%;min-height:48px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;padding:4px 0;border-radius:var(--r-sm)}
.pl-cell:active{background:var(--sunk)}
.pl-cell.sel{background:var(--brand-soft)}
.pl-dn{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;font:500 15px/1 var(--font);font-variant-numeric:tabular-nums}
.pl-dn.sun{color:var(--danger)} .pl-dn.sat{color:var(--plan)}
.pl-cell.today .pl-dn{background:var(--brand);color:var(--brand-ink)}
.pl-marks{display:flex;gap:3px;align-items:center;height:6px}
.pl-m{display:block;width:5px;height:5px;flex:none}
.pl-ring{width:6px;height:6px;border:1.5px dashed var(--plan);border-radius:50%}
.pl-sq{background:var(--c-expense);border-radius:1px}
.pl-rec{background:var(--axis-past);border-radius:50%}
.pl-legend{display:flex;justify-content:center;gap:14px;padding:6px 0 0;font:var(--t-cap);color:var(--ink-3)}
.pl-legend span{display:flex;align-items:center;gap:5px}
.pl-legend .pl-m{width:6px;height:6px}
.pl-over{color:var(--danger)}
.pl-pri{color:var(--danger);font-weight:700;margin-left:4px}
.pl-done-t{text-decoration:line-through;color:var(--ink-3)}
.pl-gap{margin-top:20px}
.pl-s span+span{margin-left:8px}
.pl-cd{margin-top:8px;font:var(--t-label);color:var(--plan)}
.pl-card::before{display:none}
.pl-tk{display:flex;align-items:flex-start;gap:12px;margin-top:6px}
.pl-tk h1{flex:1;min-width:0;margin-top:0}
.pl-tk .check,.row .check{position:relative}
.pl-tk .check::before,.row .check::before{content:"";position:absolute;inset:-10px}
`+'</style>');

/* ---------- 状態(画面をまたいで残す) ---------- */
let planView='cal';
let plSel=TODAY, plYM=[+TODAY.slice(0,4),+TODAY.slice(5,7)];
let plTodoView='today', plShowDone=false;

/* ---------- 共通ヘルパー ---------- */
function overdueCount(){return Object.values(TD).filter(t=>!t.done&&t.due&&t.due<TODAY).length}
function todoTodayCount(){return Object.values(TD).filter(t=>!t.done&&t.due&&t.due<=TODAY).length}
/** 予定がその日に記録済みか(記録の親の日付で判定。繰り返しは日付ごと) */
function plRecordedOn(e,d){const r=e.rec&&A[e.rec];const day=r&&A[r.parent];return !!day&&day.date===d}
/** その日の日記を探し、無ければ作る */
function plDayFor(date){
  let k=Object.keys(A).find(k=>A[k].type==='日記'&&A[k].date===date);
  if(!k){k=uid('d');A[k]={type:'日記',date,dd:String(+date.slice(8)),dw:wdOf(date),body:'',kids:[]}}
  return k;
}
function plSetView(v){
  planView=v;
  location.replace('#/plan?view='+(v==='todo'?'todo':'cal'));
  rerender();
}

/* ---------- 予定タブ ---------- */
route('plan',(id,q)=>{
  if(q.view)planView=q.view==='todo'?'todo':'cal';
  const today=evOn(TODAY).filter(([k,e])=>!e.hol);
  const nextE=today.find(([k,e])=>!e.all&&e.s>NOW);
  const over=overdueCount();
  const metrics=[
    {l:'今日',v:today.length+'件'},
    {l:'次',v:nextE?nextE[1].s:'—'},
    {l:'期限切れのToDo',v:over+'件',alert:over>0,href:'#/plan?view=todo'},
  ];
  return largeTitle('予定',{metrics})
    +seg([['cal','カレンダー'],['todo','ToDo']],planView,"plSetView('$v')")
    +(planView==='todo'?plTodoPane():plCalPane()+plSoonAx());
},{tab:'plan'});

/* ---------- カレンダー(月) ---------- */
/** 日の印: 予定=点線の輪 / 支払い予定=四角 / 記録=点 (最大3つ) */
function plMarks(d){
  const evs=evOn(d).filter(([k,e])=>!e.hol&&!plRecordedOn(e,d));
  const m=[];
  if(evs.some(([k,e])=>!e.pay))m.push('<i class="pl-m pl-ring"></i>');
  if(evs.some(([k,e])=>e.pay))m.push('<i class="pl-m pl-sq"></i>');
  if(actsOn(d).length)m.push('<i class="pl-m pl-rec"></i>');
  return m.join('');
}
function plCalPane(){
  const [y,m]=plYM;
  const first=new Date(y,m-1,1).getDay(), dim=new Date(y,m,0).getDate();
  let cells='<span></span>'.repeat(first);
  for(let n=1;n<=dim;n++){
    const d=`${y}-${String(m).padStart(2,'0')}-${String(n).padStart(2,'0')}`;
    const wd=(first+n-1)%7;
    const hol=evOn(d).some(([k,e])=>e.hol);
    const cls=hol||wd===0?'sun':wd===6?'sat':'';
    cells+=`<button class="pl-cell${d===plSel?' sel':''}${d===TODAY?' today':''}" type="button" aria-label="${fmtMDW(d)}" aria-pressed="${d===plSel}" onclick="plSelect('${d}')"><span class="pl-dn num ${cls}">${n}</span><span class="pl-marks">${plMarks(d)}</span></button>`;
  }
  const head=`<div class="pl-mh"><button class="iconbtn" type="button" aria-label="前の月" onclick="plMove(-1)">${IC.back}</button><b class="num">${y}年${m}月</b><button class="iconbtn" type="button" aria-label="次の月" onclick="plMove(1)">${IC.chev}</button></div>`;
  const wd=`<div class="pl-wd">${WD.split('').map((w,i)=>`<span class="${i===0?'sun':i===6?'sat':''}">${w}</span>`).join('')}</div>`;
  const legend=`<div class="pl-legend"><span><i class="pl-m pl-ring"></i>予定</span><span><i class="pl-m pl-sq"></i>支払い予定</span><span><i class="pl-m pl-rec"></i>記録</span></div>`;
  return `<div class="list pl-cal">${head}${wd}<div class="pl-grid">${cells}</div>${legend}</div>`
    +plDayAx(plSel);
}
function plSelect(d){plSel=d;rerender()}
function plMove(n){
  let [y,m]=plYM;m+=n;if(m>12){m=1;y++}if(m<1){m=12;y--}
  plYM=[y,m];
  plSel=(y===+TODAY.slice(0,4)&&m===+TODAY.slice(5,7))?TODAY:`${y}-${String(m).padStart(2,'0')}-01`;
  rerender();
}

/** 選んだ日を時間軸で見る。今日は「いま」の線で未来と過去を分ける */
function plDayAx(d){
  const future=d>TODAY;
  const hol=evOn(d).find(([k,e])=>e.hol);
  const s=daySpent(d);
  const head=dayHead(d,{href:'#/day/'+d,diary:dayDiary(d),sum:s?`自分 ${yen(s)}`:'',empty:hol?hol[1].t:future?' ':'',future});
  const recs=actsOn(d).map(([k,a])=>({time:a.time||'',fut:false,html:momentRow(k)}));
  const plans=evOn(d).filter(([k,e])=>!e.hol&&!plRecordedOn(e,d)).map(([k,e])=>{
    const fut=future||(d===TODAY&&!e.all&&e.s>NOW);
    const late=!fut&&!(d===TODAY&&e.all);
    return {time:e.all?'00:00':e.s,fut,html:planRowAx(k,{future:fut,late})};
  });
  const items=[...recs,...plans].sort((a,b)=>b.time.localeCompare(a.time));
  const body=d===TODAY
    ?items.filter(x=>x.fut).map(x=>x.html).join('')+nowLine()+items.filter(x=>!x.fut).map(x=>x.html).join('')
    :items.map(x=>x.html).join('');
  return `<div class="ax">${head}${body||'<p class="hint">予定も記録もありません</p>'}</div>`;
}

/** これから2週間(今日を除く)。繰り返しは次の日付で出す */
function plSoonAx(){
  const end=addDays(TODAY,14);
  const ups=Object.entries(EV).filter(([k,e])=>!e.hol)
    .map(([k,e])=>({k,e,d:e.rep?nextOn(e):e.d}))
    .filter(x=>x.d>TODAY&&x.d<=end&&(x.e.rep||!x.e.rec))
    .sort((a,b)=>a.d.localeCompare(b.d)||(a.e.all?'0':a.e.s).localeCompare(b.e.all?'0':b.e.s));
  if(!ups.length)return sh('これから2週間')+empty('schedule','2週間の予定はありません');
  let cur='', html='';
  ups.forEach(x=>{
    if(x.d!==cur){cur=x.d;html+=dayHead(cur,{href:'#/day/'+cur,future:true,empty:' '})}
    html+=planRowAx(x.k,{future:true});
  });
  return sh('これから2週間',ups.length+'件')+`<div class="ax">${html}</div>`;
}

/* ---------- ToDo ---------- */
const PL_PICK={
  today:k=>TD[k].due&&TD[k].due<=TODAY,
  linked:k=>!!TD[k].ev,
  all:()=>true,
};
function plPickFn(v){
  if(v.startsWith('list:')){const l=v.slice(5);return k=>TD[k].list===l}
  return PL_PICK[v]||PL_PICK.all;
}
function plTodoPane(){
  const ks=Object.keys(TD);
  const open=ks.filter(k=>!TD[k].done);
  const cnt=v=>open.filter(plPickFn(v)).length;
  const items=[
    ['today','今日(期限切れ含む)',cnt('today')],
    ['linked','予定あり',cnt('linked')],
    ['all','すべて',cnt('all')],
    ...Object.entries(LISTS).map(([l,L])=>['list:'+l,L.n,cnt('list:'+l)]),
  ];
  const sel=ks.filter(plPickFn(plTodoView));
  const openSel=sel.filter(k=>!TD[k].done), doneSel=sel.filter(k=>TD[k].done);
  const secs=[
    ['期限切れ',k=>TD[k].due&&TD[k].due<TODAY],
    ['今日',k=>TD[k].due===TODAY],
    ['これから',k=>TD[k].due&&TD[k].due>TODAY],
    ['期限なし',k=>!TD[k].due],
  ];
  const quick=`<div class="list"><div class="field"><input id="pl-qa" placeholder="例: 明日 牛乳 #買い物" aria-label="ToDoを追加" onkeydown="if(event.key==='Enter')plQuickAdd()"><button class="btn sm" type="button" onclick="plQuickAdd()">追加</button></div></div>`;
  let body='';
  for(const [title,f] of secs){
    const xs=openSel.filter(f);
    if(xs.length)body+=sh(title,xs.length)+list(xs.map(plTodoRow));
  }
  if(!openSel.length&&!doneSel.length)body+=empty('todo','ToDoはありません');
  if(doneSel.length){
    body+=`<div class="pl-gap">${list([
      row({onclick:'plToggleDone()',t:`完了 ${doneSel.length}`,v:plShowDone?'閉じる':'表示'}),
      ...(plShowDone?doneSel.map(plDoneRow):[]),
    ])}</div>`;
  }
  return quick+chips(items,plTodoView,"plTodoView='$v';rerender()")+body;
}
function plToggleDone(){plShowDone=!plShowDone;rerender()}
/** 「明日 牛乳 #買い物」→ 件名・期限・リスト */
function plParse(v,defList){
  let due, lst=defList||'inbox';
  for(const [w,n] of [['明後日',2],['明日',1],['今日',0]]){
    if(v.includes(w)){due=addDays(TODAY,n);v=v.replace(w,' ');break}
  }
  const m=v.match(/#(\S+)/);
  if(m){const hit=Object.entries(LISTS).find(([k,L])=>L.n===m[1]);if(hit){lst=hit[0];v=v.replace(m[0],' ')}}
  return {t:v.replace(/\s+/g,' ').trim(),due,list:lst};
}
function plQuickAdd(){
  const el=document.getElementById('pl-qa');
  const p=plParse(el?el.value:'',plTodoView.startsWith('list:')?plTodoView.slice(5):'inbox');
  if(!p.t){toast('ToDoの名前を入力してください');return}
  const id=uid('t');
  TD[id]=p.due?{t:p.t,list:p.list,due:p.due}:{t:p.t,list:p.list};
  save();rerender();toast('ToDoを追加しました');
}
function plTodoRow(k){
  const t=TD[k], over=t.due&&t.due<TODAY;
  const linked=t.ev&&EV[t.ev]?EV[t.ev].t:'';
  const parts=[
    t.due?`<span class="${over?'pl-over':''}">${relDay(t.due)}</span>`:'',
    LISTS[t.list]?`<span>${esc(LISTS[t.list].n)}</span>`:'',
    linked?`<span>${esc(linked)}</span>`:'',
  ].filter(Boolean).join('');
  return row({lead:checkbox(false,`toggleTask('${k}')`),t:esc(t.t)+(t.pri?'<span class="pl-pri">!</span>':''),s:`<span class="pl-s">${parts}</span>`,href:'#/task/'+k,chev:1});
}
function plDoneRow(k){
  const t=TD[k];
  return row({lead:checkbox(true,`toggleTask('${k}')`),t:`<span class="pl-done-t">${esc(t.t)}</span>`,href:'#/task/'+k,chev:1});
}
function toggleTask(k){
  const t=TD[k];if(!t)return;
  if(t.done){delete t.done;save();rerender();toast('未完了に戻しました');return}
  t.done=TODAY;save();rerender();
  toast('完了にしました',()=>{delete TD[k].done});
}

/* ---------- 新規ToDo ---------- */
CREATE.push({id:'task',label:'ToDo',sub:'やること・買う物',icon:'todo',color:'var(--c-todo)',primaryOn:['plan'],onclick:'openNewTask()'});
function openNewTask(){
  openSheet({title:'ToDoを追加',body:`<div class="list">
    <div class="field"><label for="pl-nt-t">タイトル</label><input id="pl-nt-t" placeholder="例: 牛乳を買う"></div>
    <div class="field"><label for="pl-nt-d">期限</label><input id="pl-nt-d" type="date"></div>
    <div class="field"><label for="pl-nt-l">リスト</label><select id="pl-nt-l">${Object.entries(LISTS).map(([k,L])=>`<option value="${k}">${esc(L.n)}</option>`).join('')}</select></div>
  </div>`,right:{label:'ToDoを追加',onclick:'plSaveTask()'}});
}
function plSaveTask(){
  const t=(document.getElementById('pl-nt-t')?.value||'').trim();
  if(!t){toast('タイトルを入れてください');return}
  const due=document.getElementById('pl-nt-d').value, lst=document.getElementById('pl-nt-l').value;
  TD[uid('t')]=due?{t,list:lst,due}:{t,list:lst};
  closeSheet();save();rerender();toast('ToDoを追加しました');
}

/* ---------- 予定の詳細: 時間軸の上の1点(主役) ---------- */
route('ev',id=>{
  const e=EV[id];
  if(!e)return appbar('予定')+empty('schedule','この予定は削除されたか、見つかりません');
  const rec=e.rec&&A[e.rec];
  const unreg=e.place&&!e.place[0]&&e.place[1];
  const tasks=Object.keys(TD).filter(k=>TD[k].ev===id);
  const od=e.rep?nextOn(e):e.d;
  const toMin=s=>{const [h,m]=s.split(':').map(Number);return h*60+m};
  let cd='';
  if(od===TODAY&&!e.all&&e.s>NOW){
    const diff=toMin(e.s)-toMin(NOW);
    cd=`あと${diff<60?diff+'分':Math.floor(diff/60)+'時間'+(diff%60?(diff%60)+'分':'')}`;
  }else if(od>TODAY){
    const n=Math.round((new Date(od+'T00:00')-new Date(TODAY+'T00:00'))/864e5);
    cd=n===1?'明日':`${n}日後`;
  }
  const card=`<div class="kind">${IC.schedule}予定</div><h1>${esc(e.t)}</h1><div class="big">${e.all?'終日':`${e.s}–${e.e}`}</div>${cd?`<div class="pl-cd">${cd}</div>`:''}<div class="meta"><span>${IC.schedule}${fmtMDW(e.d)}${e.d2?' – '+fmtMDW(e.d2):''}</span>${placeOf(e)?`<span>${IC.place}${esc(placeOf(e))}</span>`:''}${e.pay?`<span>${IC.pay}${yen(e.pay.amt)} 支払い予定</span>`:''}</div>`;
  const isTodayFuture=e.d===TODAY&&!e.all&&e.s>NOW;
  const axis=`<div class="ax">${dayHead(e.d,{href:'#/day/'+e.d,diary:dayDiary(e.d),future:e.d>=TODAY})}`
    +focusRow({time:e.all?'終日':e.s,node:{cls:'plan',icon:'schedule'},plan:true,card})
    +(isTodayFuture?nowLine():'')
    +`</div>`;
  const props=[
    e.place?prop('場所',eref(e.place[0],e.place[1])):'',
    e.who?prop('参加者',e.who.filter(w=>E[w]).map(w=>eref(w)).join('')):'',
    prop('繰り返し',esc(e.rep||'なし')),
    prop('通知',esc(e.alarm||'なし')),
    e.memo?prop('メモ',esc(e.memo)):'',
  ].join('');
  return appbar('',{actions:[{icon:'more',label:'操作',onclick:"toast('編集はモックでは未対応です')"}]})
    +axis
    +sh('詳細')+list([props])
    +(unreg?suggest(`「${esc(e.place[1])}」はまだ登録されていません`,{label:'場所として登録',onclick:`plRegisterPlace('${id}')`}):'')
    +(tasks.length?sh('この予定のToDo',tasks.length)+list(tasks.map(plEvTaskRow)):'')
    +sh('記録')
    +(rec
      ?list([row({href:'#/a/'+e.rec,lead:`<span class="ax-node" style="margin:0">${IC.activity}</span>`,t:esc(rec.title),s:'記録済み',chev:1})])
      :`<div class="btnrow"><button class="btn block" type="button" onclick="recordEvent('${id}')">${IC.check}記録にする</button></div>`
        +(e.d<=TODAY?'<p class="hint">終わったら、記録として時間軸に残せます</p>':''));
},{tab:'plan'});
function plEvTaskRow(k){
  const t=TD[k];
  return row({lead:checkbox(!!t.done,`toggleTask('${k}')`),href:'#/task/'+k,t:`<span class="${t.done?'pl-done-t':''}">${esc(t.t)}</span>`,s:t.due?relDay(t.due):'',chev:1});
}
/** 予定の場所を未登録から登録する */
function plRegisterPlace(evId){
  const e=EV[evId];if(!e||!e.place)return;
  const name=e.place[1];
  const id=uid('e');
  E[id]={n:name,i:name[0],t:['place'],facts:[]};
  e.place[0]=id;
  save();rerender();
  toast(`「${name}」を場所として登録しました`,()=>{delete E[id];e.place[0]=null});
}
/** 予定を記録にする(支払いの予定は支払いとして) */
function recordEvent(id){
  const e=EV[id];if(!e)return;
  if(e.rec&&A[e.rec])return;
  const day=plDayFor(e.d);
  const aid=uid('c');
  const placeId=e.place?e.place[0]:null;
  const placeName=e.place?(e.place[0]?E[e.place[0]].n:e.place[1]):'';
  if(e.pay){
    const xid=uid('x');
    const roles=[['支払先',placeId,placeName||e.t],['支払者','me'],...(e.who||[]).map(w=>['同席',w])];
    A[aid]={type:'支払い',parent:day,time:e.s||'09:00',title:e.t,pay:'カード ••1234',amt:e.pay.amt,roles,
      items:[{n:e.t,price:e.pay.amt,b:'自分',tax:10}],x:xid};
    X[xid]={pay:aid,amt:e.pay.amt,cat:e.pay.cat,memo:'',items:[{n:e.t,price:e.pay.amt,b:'全額'}]};
  }else{
    const roles=(e.who||[]).map(w=>['参加',w]);
    if(e.place)roles.push(['場所',placeId,placeName]);
    A[aid]={type:e.who?'会話':'出来事',parent:day,time:e.s||'09:00',title:e.t,roles,body:''};
  }
  A[day].kids.push(aid);e.rec=aid;
  save();go('#/a/'+aid);toast('記録にしました');
}

/* ---------- ToDoの詳細 ---------- */
route('task',id=>{
  const t=TD[id];
  if(!t)return appbar('ToDo')+empty('todo','このToDoは削除されたか、見つかりません');
  const ev=t.ev&&EV[t.ev];
  const subs=t.sub||[];
  const listOpts=Object.entries(LISTS).map(([k,L])=>`<option value="${k}" ${k===t.list?'selected':''}>${esc(L.n)}</option>`).join('');
  const links=[
    ev?row({href:'#/ev/'+t.ev,lead:`<span class="ax-node plan" style="margin:0">${IC.schedule}</span>`,t:esc(ev.t),s:fmtMDW(ev.d),chev:1}):'',
    ...(t.ent||[]).filter(x=>E[x]).map(x=>row({t:eref(x)})),
    t.pay&&A[t.pay]?row({t:aref(t.pay)}):'',
  ].filter(Boolean);
  const sub=[t.due?'期限 '+fmtMDW(t.due):'期限なし',t.done?'完了 '+fmtMD(t.done):''].filter(Boolean);
  return appbar('ToDo')
    +`<div class="mo-card pl-card" style="margin:4px var(--gutter) 0"><div class="kind">${IC.todo}ToDo</div><div class="pl-tk">${checkbox(!!t.done,`toggleTask('${id}')`,true)}<h1 class="${t.done?'pl-done-t':''}">${esc(t.t)}</h1></div>${sub.length?`<div class="meta">${sub.map(x=>`<span>${x}</span>`).join('')}</div>`:''}</div>`
    +sh('詳細')+list([
      prop('期限',`<input class="input" type="date" aria-label="期限" value="${t.due||''}" onchange="plSetDue('${id}',this.value)">`),
      prop('リスト',`<select class="input" aria-label="リスト" onchange="plSetList('${id}',this.value)">${listOpts}</select>`),
      prop('繰り返し',esc(t.rep||'なし')),
      prop('優先',toggle(t.pri,`plTogPri('${id}')`)),
    ])
    +(links.length?sh('結び付き')+list(links):'')
    +(subs.length?sh('サブタスク',subs.filter(x=>x[1]).length+'/'+subs.length)+list(subs.map((x,i)=>row({cls:'plain',lead:checkbox(x[1],`plSubToggle('${id}',${i})`),t:`<span class="${x[1]?'pl-done-t':''}">${esc(x[0])}</span>`}))):'')
    +(t.memo?sh('メモ')+list([row({cls:'plain',wrap:true,t:esc(t.memo)})]):'');
},{tab:'plan'});
function plSetDue(k,v){if(v)TD[k].due=v;else delete TD[k].due;save();rerender()}
function plSetList(k,v){TD[k].list=v;save();rerender()}
function plTogPri(k){if(TD[k].pri)delete TD[k].pri;else TD[k].pri=1;save();rerender()}
function plSubToggle(k,i){const s=TD[k].sub[i];s[1]=s[1]?0:1;save();rerender()}
