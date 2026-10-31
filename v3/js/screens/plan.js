/* =====================================================================
   予定タブ — カレンダー / ToDo、予定の詳細、ToDoの詳細
   ルート: #/plan(?view=cal|todo), #/ev/{id}, #/task/{id}
   ===================================================================== */

document.head.insertAdjacentHTML('beforeend','<style>'+`
.pl-cal{padding:8px 8px 10px}
.pl-mh{display:flex;align-items:center;gap:4px;height:44px}
.pl-mh b{flex:1;text-align:center;font:var(--t-head)}
.pl-wd,.pl-grid{display:grid;grid-template-columns:repeat(7,1fr)}
.pl-wd span{font:500 12px/28px var(--font);text-align:center;color:var(--ink-3)}
.pl-wd .sun,.pl-dn.sun{color:var(--danger)}
.pl-wd .sat,.pl-dn.sat{color:var(--c-schedule)}
.pl-cell{width:100%;min-height:48px;display:flex;flex-direction:column;align-items:center;gap:2px;padding:4px 0;border-radius:var(--r-sm)}
.pl-cell:active{background:var(--sunk)}
.pl-cell.sel{background:var(--brand-soft)}
.pl-dn{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;font:500 15px/1 var(--font);font-variant-numeric:tabular-nums}
.pl-cell.today .pl-dn{box-shadow:inset 0 0 0 2px var(--brand);color:var(--brand)}
.pl-dots{display:flex;gap:3px;height:5px}
.pl-dot{display:block;width:5px;height:5px;border-radius:50%}
.pl-d-s{background:var(--c-schedule)} .pl-d-p{background:var(--c-expense)} .pl-d-a{background:var(--c-activity)}
.pl-legend{display:flex;justify-content:center;gap:14px;padding:8px 0 0;font:var(--t-cap);color:var(--ink-3)}
.pl-legend span{display:flex;align-items:center;gap:5px}
.pl-pri{color:var(--danger);font-weight:700;margin-left:4px}
.pl-over{color:var(--danger)}
.pl-done-t{text-decoration:line-through;color:var(--ink-3)}
.g.pl-danger{--gc:var(--danger);--gs:var(--danger-soft)}
.pl-gap{margin-top:20px}
`+'</style>');

/* 状態(画面をまたいで残す) */
let planView='cal';
let plSel=TODAY, plYM=[+TODAY.slice(0,4),+TODAY.slice(5,7)];
let plTodoView='today', showDone=false;
const PL_NOW='14:46'; /* モックの現在時刻(v2と同じ) */

/* ---------- 共通ヘルパー ---------- */
function overdueCount(){return Object.values(TD).filter(t=>!t.done&&t.due&&t.due<TODAY).length}
function todoTodayCount(){return Object.values(TD).filter(t=>!t.done&&t.due&&t.due<=TODAY).length}
/** 繰り返し予定の、今日以前で直近の日付 */
function plPrevOcc(e){const d=nextOn(e);let y=+d.slice(0,4),m=+d.slice(5,7)-1;if(m<1){m=12;y--}return `${y}-${String(m).padStart(2,'0')}-${d.slice(8)}`}
/** その日の日記を探し、無ければ作る */
function plDayFor(date){
  let k=Object.keys(A).find(k=>A[k].type==='日記'&&A[k].date===date);
  if(!k){k=uid('d');A[k]={type:'日記',date,dd:String(+date.slice(8)),dw:wdOf(date),body:'',kids:[]}}
  return k;
}

/* ---------- 予定タブ ---------- */
route('plan',(id,q)=>{
  if(q.view)planView=q.view==='todo'?'todo':'cal';
  const today=evOn(TODAY).filter(([k,e])=>!e.hol);
  const next=today.find(([k,e])=>!e.all&&e.s>PL_NOW);
  const over=overdueCount();
  const metrics=[
    {l:'今日',v:today.length+'件'},
    {l:'次の予定',v:next?next[1].s:'—'},
    {l:'期限切れ',v:over+'件',alert:over>0,href:'#/plan?view=todo'},
  ];
  return largeTitle('予定',{metrics})
    +seg([['cal','カレンダー'],['todo','ToDo']],planView,"go('#/plan?view=$v')")
    +(planView==='todo'?plTodoPane():plCalPane());
},{tab:'plan'});

/* ---------- カレンダー ---------- */
function plCalPane(){
  const [y,m]=plYM;
  const first=new Date(y,m-1,1).getDay(), dim=new Date(y,m,0).getDate();
  let cells='';
  for(let i=0;i<first;i++)cells+='<span></span>';
  for(let n=1;n<=dim;n++){
    const d=`${y}-${String(m).padStart(2,'0')}-${String(n).padStart(2,'0')}`;
    const evs=evOn(d), hol=evs.some(([k,e])=>e.hol), wd=(first+n-1)%7;
    const kinds=evs.filter(([k,e])=>!e.hol).map(([k,e])=>e.pay?'p':'s');
    if(actsOn(d).length)kinds.push('a');
    const cls=hol||wd===0?'sun':wd===6?'sat':'';
    cells+=`<button class="pl-cell ${d===plSel?'sel':''} ${d===TODAY?'today':''}" type="button" aria-label="${fmtMDW(d)}" aria-pressed="${d===plSel}" onclick="plSelect('${d}')"><span class="pl-dn num ${cls}">${n}</span><span class="pl-dots">${kinds.slice(0,3).map(k=>`<i class="pl-dot pl-d-${k}"></i>`).join('')}</span></button>`;
  }
  const head=`<div class="pl-mh"><button class="iconbtn" type="button" aria-label="前の月" onclick="plMove(-1)">${IC.back}</button><b class="num">${y}年${m}月</b><button class="iconbtn" type="button" aria-label="次の月" onclick="plMove(1)">${IC.chev}</button></div>`;
  const wd=`<div class="pl-wd">${'日月火水木金土'.split('').map((w,i)=>`<span class="${i===0?'sun':i===6?'sat':''}">${w}</span>`).join('')}</div>`;
  const legend=`<div class="pl-legend"><span><i class="pl-dot pl-d-s"></i>予定</span><span><i class="pl-dot pl-d-p"></i>支払い予定</span><span><i class="pl-dot pl-d-a"></i>記録</span></div>`;
  const evs=evOn(plSel);
  return `<div class="list pl-cal">${head}${wd}<div class="pl-grid">${cells}</div>${legend}</div>`
    +sh(fmtMDW(plSel)+(plSel===TODAY?' 今日':''),evs.length+'件')
    +(evs.length
      ?list(evs.map(([k,e])=>plEvRow(k,e)))
      :empty('schedule','schedule','予定はありません',{label:'予定を追加',onclick:"typeof openNewEvent==='function'&&openNewEvent()"}));
}
function plEvRow(k,e){
  const rec=e.rec&&A[e.rec];
  const v=[e.pay?`<span class="num">${yen(e.pay.amt)}</span>`:'',rec?tag('記録済み',{cls:'ok'}):''].filter(Boolean).join(' ');
  return row({href:'#/ev/'+k,time:e.all?'終日':e.s,lead:glyph('schedule'),t:esc(e.t),s:esc(placeOf(e)),v:v||undefined,amt:!!e.pay,chev:1});
}
function plSelect(d){plSel=d;plYM=[+d.slice(0,4),+d.slice(5,7)];rerender()}
function plMove(n){
  let [y,m]=plYM;m+=n;if(m>12){m=1;y++}if(m<1){m=12;y--}
  plYM=[y,m];
  plSel=(y===+TODAY.slice(0,4)&&m===+TODAY.slice(5,7))?TODAY:`${y}-${String(m).padStart(2,'0')}-01`;
  rerender();
}

/* ---------- ToDo ---------- */
function plPick(v){
  if(v==='today')return k=>TD[k].due&&TD[k].due<=TODAY;
  if(v==='upcoming')return k=>TD[k].due&&TD[k].due>TODAY;
  if(v.startsWith('list:'))return k=>TD[k].list===v.slice(5);
  return ()=>true;
}
function plTodoPane(){
  const ks=Object.keys(TD), open=ks.filter(k=>!TD[k].done);
  const items=[
    ['today','今日(期限切れ含む)',open.filter(plPick('today')).length],
    ['upcoming','予定あり',open.filter(plPick('upcoming')).length],
    ['all','すべて',open.length],
    ...Object.entries(LISTS).map(([l,L])=>['list:'+l,L.n,open.filter(plPick('list:'+l)).length]),
  ];
  const sel=ks.filter(plPick(plTodoView));
  const openSel=sel.filter(k=>!TD[k].done), doneSel=sel.filter(k=>TD[k].done);
  const secs=[
    ['期限切れ',k=>TD[k].due&&TD[k].due<TODAY],
    ['今日',k=>TD[k].due===TODAY],
    ['これから',k=>TD[k].due&&TD[k].due>TODAY],
    ['期限なし',k=>!TD[k].due],
  ];
  const quick=`<div class="list"><div class="field"><input id="plQa" placeholder="例: 明日 牛乳 #買い物" aria-label="ToDoを追加" onkeydown="if(event.key==='Enter')plQuickAdd()"><button class="btn sm" type="button" onclick="plQuickAdd()">追加</button></div></div>`;
  let body='';
  for(const [title,f] of secs){
    const xs=openSel.filter(f);
    if(xs.length)body+=sh(title,xs.length)+list(xs.map(plTodoRow));
  }
  if(!openSel.length&&!doneSel.length)body+=empty('todo','todo','ToDoはありません');
  if(doneSel.length){
    body+=`<div class="pl-gap">${list([row({onclick:'showDone=!showDone;rerender()',t:`完了 ${doneSel.length}`,v:showDone?'閉じる':'表示'}),
      ...(showDone?doneSel.map(plDoneRow):[])])}</div>`;
  }
  return quick
    +chips(items,plTodoView,"plTodoView='$v';rerender()")
    +body;
}
/** 「明日 牛乳 #買い物」→ 期限・リスト・件名 */
function plParse(v,defList){
  let due, list=defList||'inbox';
  for(const [w,n] of [['明後日',2],['明日',1],['今日',0]]){
    if(v.includes(w)){due=addDays(TODAY,n);v=v.replace(w,' ');break}
  }
  const m=v.match(/#(\S+)/);
  if(m){const l=Object.entries(LISTS).find(([k,L])=>L.n===m[1]);if(l)list=l[0];v=v.replace(m[0],' ')}
  return {t:v.replace(/\s+/g,' ').trim(),due,list};
}
function plQuickAdd(){
  const el=$('#plQa');if(!el)return;
  const p=plParse(el.value,plTodoView.startsWith('list:')?plTodoView.slice(5):'inbox');
  if(!p.t)return;
  TD[uid('t')]=p.due?{t:p.t,list:p.list,due:p.due}:{t:p.t,list:p.list};
  save();rerender();toast('ToDoを追加しました');
}
function plTodoRow(k){
  const t=TD[k], over=t.due&&t.due<TODAY;
  const due=t.due?(over?`<span class="pl-over">${relDay(t.due)}</span>`:relDay(t.due)):'';
  const s=[due,LISTS[t.list]?LISTS[t.list].n:'',t.ev&&EV[t.ev]?EV[t.ev].t:'',(t.ent||[]).filter(e=>E[e]).map(e=>E[e].n).join('・')].filter(Boolean).join(' · ');
  return row({lead:checkbox(false,`toggleTask('${k}')`),t:esc(t.t)+(t.pri?'<span class="pl-pri">!</span>':''),s,href:'#/task/'+k,chev:1});
}
function plDoneRow(k){
  const t=TD[k];
  return row({lead:checkbox(true,`toggleTask('${k}')`),t:`<span class="pl-done-t">${esc(t.t)}</span>`,href:'#/task/'+k,chev:1});
}
function toggleTask(k){
  const t=TD[k];if(!t)return;
  const prev=t.done;
  if(t.done)delete t.done;else t.done=TODAY;
  save();rerender();
  if(t.done)toast('完了にしました',()=>{if(prev)TD[k].done=prev;else delete TD[k].done});
}

/* ---------- 新規ToDo ---------- */
CREATE.push({id:'task',label:'ToDo',sub:'やること・買う物',cls:'todo',icon:'todo',primaryOn:['plan'],onclick:'openNewTask()'});
function openNewTask(){
  openSheet({title:'ToDoを追加',body:`<div class="list">
    <div class="field"><label for="ntT">タイトル</label><input id="ntT" placeholder="例: 牛乳を買う"></div>
    <div class="field"><label for="ntD">期限</label><input id="ntD" type="date"></div>
    <div class="field"><label for="ntL">リスト</label><select id="ntL">${Object.entries(LISTS).map(([k,L])=>`<option value="${k}">${esc(L.n)}</option>`).join('')}</select></div>
    </div>`,right:{label:'追加',onclick:'saveNewTask()'}});
}
function saveNewTask(){
  const t=($('#ntT')?.value||'').trim();
  if(!t){toast('タイトルを入力してください');return}
  const due=$('#ntD').value, list=$('#ntL').value;
  TD[uid('t')]=due?{t,list,due}:{t,list};
  closeSheet();save();rerender();toast('ToDoを追加しました');
}

/* ---------- 予定の詳細 ---------- */
route('ev',id=>{
  const e=EV[id];
  if(!e)return appbar('予定')+empty('schedule','schedule','この予定は削除されたか、見つかりません');
  const tasks=Object.keys(TD).filter(k=>TD[k].ev===id);
  const rec=e.rec&&A[e.rec];
  const unreg=e.place&&!e.place[0]&&e.place[1];
  const sub=[fmtMDW(e.d)+(e.d2?' – '+fmtMDW(e.d2):''),e.hol?'祝日':'',e.rep?esc(e.rep):''].filter(Boolean);
  const props=[
    e.place?prop('場所',eref(e.place[0],e.place[1])):'',
    e.who?prop('参加者',e.who.map(w=>eref(w)).join('')):'',
    e.pay?prop('金額',`<span class="num">${yen(e.pay.amt)}</span>`,catPath(e.pay.cat)):'',
    prop('繰り返し',esc(e.rep||'なし')),
    prop('通知',esc(e.alarm||'なし')),
    e.memo?prop('メモ',esc(e.memo)):'',
  ].join('');
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:`plEvMenu('${id}')`}]})
    +hero('schedule',{kind:glyph('schedule')+'予定',title:esc(e.t),big:e.all?'終日':`${e.s} – ${e.e}`,sub})
    +sh('詳細')+list([props])
    +(unreg?suggest(`「${esc(e.place[1])}」は未登録です`,{label:'場所として登録',onclick:`registerPlace('${id}')`}):'')
    +(tasks.length?sh('この予定のToDo',tasks.length)+list(tasks.map(plEvTaskRow)):'')
    +sh('記録')
    +(rec
      ?list([row({href:'#/a/'+e.rec,lead:glyph('activity'),t:esc(rec.title),s:'記録済み',chev:1})])
      :`<div class="btnrow"><button class="btn block" type="button" onclick="recordEvent('${id}')">記録にする</button></div>`);
},{tab:'plan'});
function plEvTaskRow(k){
  const t=TD[k];
  return row({lead:checkbox(!!t.done,`toggleTask('${k}')`),href:'#/task/'+k,t:`<span class="${t.done?'pl-done-t':''}">${esc(t.t)}</span>`,s:t.due?relDay(t.due):'',chev:1});
}
function plEvMenu(id){
  openSheet({title:'操作',body:list([
    row({lead:glyph('schedule','edit'),t:'編集',onclick:`closeSheet();toast('編集はモックでは未対応です')`}),
    row({lead:glyph('pl-danger','trash'),t:'<span class="pl-over">削除</span>',onclick:`plDeleteEv('${id}')`}),
  ])});
}
function plDeleteEv(id){
  const snap=JSON.parse(JSON.stringify(EV[id]));
  const tds=Object.keys(TD).filter(k=>TD[k].ev===id);
  closeSheet();
  delete EV[id];tds.forEach(k=>delete TD[k].ev);
  save();goBack();
  toast('予定を削除しました',()=>{EV[id]=snap;tds.forEach(k=>TD[k].ev=id)});
}
/** 予定の場所を未登録から登録する */
function registerPlace(evId){
  const e=EV[evId];const name=e.place[1];
  const id=uid('e');
  E[id]={n:name,i:name[0],t:['place'],facts:[]};
  e.place[0]=id;
  save();rerender();
  toast(`「${name}」を場所として登録しました`,()=>{delete E[id];e.place[0]=null});
}
/** 予定を記録にする(支払いの予定は支払いとして) */
function recordEvent(id){
  const e=EV[id];if(!e||(e.rec&&A[e.rec]))return;
  const date=e.rep?plPrevOcc(e):(e.d<=TODAY?e.d:TODAY);
  const day=plDayFor(date);
  const aid=uid('c');
  const place=e.place||[];
  const placeId=place[0]||null, placeName=place[1]||(placeId?E[placeId].n:'');
  if(e.pay){
    const xid=uid('x');
    const roles=[['支払先',placeId,placeName||e.t],['支払者','me'],...(e.who||[]).map(w=>['同席',w])];
    A[aid]={type:'支払い',parent:day,time:e.s||'',title:e.t,pay:'カード ••1234',amt:e.pay.amt,roles,
      items:[{n:e.t,price:e.pay.amt,tax:10,b:'自分'}],x:xid};
    X[xid]={pay:aid,amt:e.pay.amt,cat:e.pay.cat,memo:'',items:[{n:e.t,price:e.pay.amt,b:'全額'}]};
  }else{
    const roles=(e.who||[]).map(w=>['参加',w]);
    if(e.place)roles.push(['場所',placeId,placeName]);
    A[aid]={type:e.who?'会話':'出来事',parent:day,time:e.s||'',title:e.t,roles,body:''};
  }
  A[day].kids.push(aid);e.rec=aid;
  save();go('#/a/'+aid);toast('記録にしました');
}

/* ---------- ToDoの詳細 ---------- */
route('task',id=>{
  const t=TD[id];
  if(!t)return appbar('ToDo')+empty('todo','todo','このToDoは削除されたか、見つかりません');
  const ev=t.ev&&EV[t.ev];
  const subs=t.sub||[];
  const listOpts=Object.entries(LISTS).map(([k,L])=>`<option value="${k}" ${k===t.list?'selected':''}>${esc(L.n)}</option>`).join('');
  const links=[
    ev?row({href:'#/ev/'+t.ev,lead:glyph('schedule'),t:esc(ev.t),s:fmtMDW(ev.d),chev:1}):'',
    ...(t.ent||[]).map(e=>row({t:eref(e)})),
    t.pay&&A[t.pay]?row({t:aref(t.pay)}):'',
  ].filter(Boolean);
  const sub=[t.due?'期限 '+fmtMDW(t.due):'期限なし',t.done?'完了 '+fmtMD(t.done):'',t.pri?'優先':''].filter(Boolean);
  return appbar('ToDo')
    +hero('todo',{kind:checkbox(!!t.done,`toggleTask('${id}')`,true)+'ToDo',title:`<span class="${t.done?'pl-done-t':''}">${esc(t.t)}</span>`,sub})
    +sh('詳細')+list([
      prop('期限',`<input class="input" type="date" aria-label="期限" value="${t.due||''}" onchange="plSetDue('${id}',this.value)">`),
      prop('リスト',`<select class="input" aria-label="リスト" onchange="plSetList('${id}',this.value)">${listOpts}</select>`),
      prop('繰り返し',esc(t.rep||'なし')),
      prop('優先',toggle(t.pri,`plTogPri('${id}')`)),
    ])
    +sh('結び付き')
    +(links.length?list(links):`<p class="hint">人・場所・予定・支払いに結び付けられます</p>`)
    +(subs.length?sh('サブタスク',subs.filter(x=>x[1]).length+'/'+subs.length)+list(subs.map((x,i)=>row({cls:'plain',lead:checkbox(x[1],`plSubToggle('${id}',${i})`),t:`<span class="${x[1]?'pl-done-t':''}">${esc(x[0])}</span>`}))):'')
    +(t.memo?sh('メモ')+list([row({cls:'plain',wrap:true,t:esc(t.memo)})]):'');
},{tab:'plan'});
function plSetDue(k,v){if(v)TD[k].due=v;else delete TD[k].due;save();rerender()}
function plSetList(k,v){TD[k].list=v;save();rerender()}
function plTogPri(k){if(TD[k].pri)delete TD[k].pri;else TD[k].pri=1;save();rerender()}
function plSubToggle(k,i){const s=TD[k].sub[i];s[1]=s[1]?0:1;save();rerender()}
