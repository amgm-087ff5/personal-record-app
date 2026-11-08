/* =====================================================================
   予定の描き方 — v4.13: 予定タブは廃止。日・月の表示は アクティビティ(#/activities?view=day|month)の中に入る。
   ここは中身だけ: 月(日のマス・件数の印)・日(時間グリッド)・その日のToDo・予定の詳細・ToDo(一覧・詳細)
   ルート: #/plan(古い入口。#/activities?view=day|month へ置き換え), #/todo, #/ev/{id}, #/task/{id}
   見た目は css/s-plan.css。軸の一覧は axGroup(日の面)で作る(DESIGN.md v4)
   ===================================================================== */

/* ---------- 状態(画面をまたいで残す) ---------- */
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
/** v4.13: リストの月 → 日・月の期間(表示を切り替えた時に位置を引き継ぐ。今月なら今日) */
function plFromMonth(ym){plYM=[+ym.slice(0,4),+ym.slice(5,7)];plSel=ym===TODAY.slice(0,7)?TODAY:ym+'-01'}
/** その日の記録(実線)と予定(点線)を時間順に。繰り返しは evOn が日付ごとに含める */
function plItemsOn(d){
  const recs=actsOn(d).map(([k,a])=>({k,kind:'rec',t:a.title||'',time:a.time||'00:00',end:'',all:false,cat:a.x&&X[a.x]?X[a.x].cat:''}));
  const plans=evOn(d).filter(([k,e])=>!e.hol&&!plRecordedOn(e,d)).map(([k,e])=>({k,kind:'plan',t:e.t,time:e.all?'00:00':e.s,end:e.all?'':e.e,all:!!e.all,cat:e.pay?e.pay.cat:''}));
  return [...recs,...plans].sort((a,b)=>a.time.localeCompare(b.time));
}
/** その日が期限のToDoのキー(js/plans.js の todosDueOn があれば使う。無ければ空)。戻りがキーでも [キー,中身] でもよい */
function plTodosOn(d){
  if(typeof todosDueOn!=='function')return [];
  return todosDueOn(d).map(x=>Array.isArray(x)?x[0]:x).filter(k=>TD[k]);
}
/** 見出しの下の小さい見出し。期限切れは赤(cls='late') */
function plSec(title,n,cls='',act){
  return `<div class="sh pl-sec ${cls}"><h2>${esc(title)}</h2>${n!=null&&n!==''?`<span class="n">${n}</span>`:''}${act?`<a class="act" href="javascript:void 0" onclick="${act.onclick}">${esc(act.label)}</a>`:''}</div>`;
}

/* ---------- ToDo(独立したタブ #/todo) ---------- */
route('todo',()=>{
  /* まとめカード: 数字=期限切れ / 今日(今日が期限のもの) / 全部の未完了。押すと下の絞り込みを切り替える。主ボタン=ToDoを追加 */
  const all=Object.values(TD).filter(t=>!t.done).length;
  const today=Object.values(TD).filter(t=>!t.done&&t.due===TODAY).length;
  return '<div class="pl-root">'+largeTitle('ToDo')
    +summaryCard({metrics:[
        {v:`${overdueCount()}件`,l:'期限切れ',onclick:"plTodoView='today';rerender()"},
        {v:`${today}件`,l:'今日',onclick:"plTodoView='today';rerender()"},
        {v:`${all}件`,l:'全部の未完了',onclick:"plTodoView='all';rerender()"}],
      actions:[{text:'ToDoを追加',icon:'plus',primary:true,onclick:'plFocusQuick()'}]})
    +plTodoPane()+'</div>';
},{tab:'todo'});
/** まとめカードの「ToDoを追加」: 下の入力欄にフォーカス(無ければ作成シート) */
function plFocusQuick(){
  const el=document.getElementById('pl-qa');
  if(!el){openNewTask();return}
  el.scrollIntoView({block:'center'});el.focus();
}

/* ---------- 月(アクティビティの「月」表示の中身。期間の送りは plMonthNav をヘッダーへ) ---------- */
function plMonthNav(){const [y,m]=plYM;return periodNav({label:`${m}月`,sub:String(y),prev:{on:'plMove(-1)'},next:{on:'plMove(1)'}})}
function plMonthView(){
  const [y,m]=plYM;
  const first=new Date(y,m-1,1).getDay(), dim=new Date(y,m,0).getDate();
  let cells='<span></span>'.repeat(first);
  for(let n=1;n<=dim;n++){
    const d=`${y}-${String(m).padStart(2,'0')}-${String(n).padStart(2,'0')}`;
    const its=plItemsOn(d), tds=plTodosOn(d).length;
    const hol=evOn(d).some(([k,e])=>e.hol);
    const sun=hol||(first+n-1)%7===0;
    /* 月のマスは件数だけ: 小さいカプセル(塗り=記録、点線=予定、角が四角=支払い)を最大4つ。ToDoは下に小さい✓の件数。文字は下の日の面で読む */
    const dots=its.slice(0,4).map(it=>`<i class="pl-dot ${it.kind==='rec'?'rec':'plan'}${it.cat?' pay':''}"></i>`).join('');
    const td=tds?`<span class="pl-td">${IC.check}${tds}</span>`:'';
    cells+=`<button class="pl-cell${sun?' sun':''}${d===plSel?' sel':''}${d===TODAY?' today':''}" type="button" aria-label="${fmtMDW(d)}${its.length?' '+its.length+'件':''}${tds?' ToDo '+tds+'件':''}" aria-pressed="${d===plSel}" onclick="plSelect('${d}')"><span class="pl-dn num">${n}</span><span class="pl-bars">${dots}</span><span class="pl-tdr">${td}</span></button>`;
  }
  const wd=`<div class="pl-wd">${WD.split('').map((w,i)=>`<span class="${i===0?'sun':''}">${w}</span>`).join('')}</div>`;
  const legend=`<div class="pl-legend"><span><i class="pl-dot plan"></i>予定</span><span><i class="pl-dot rec"></i>記録</span><span><i class="pl-dot plan pay"></i>支払い予定</span><span>${IC.check}ToDo</span></div>`;
  return `<div class="list pl-cal">${wd}<div class="pl-grid">${cells}</div>${legend}</div>`
    +plDayAx(plSel)+plTodoBlock(plSel);
}
function plSelect(d){plSel=d;rerender()}
function plMove(n){
  let [y,m]=plYM;m+=n;if(m>12){m=1;y++}if(m<1){m=12;y--}
  plYM=[y,m];
  plSel=(y===+TODAY.slice(0,4)&&m===+TODAY.slice(5,7))?TODAY:`${y}-${String(m).padStart(2,'0')}-01`;
  rerender();
}

/** 1日の面: 日の札+その日の記録と予定の行(時間の古い順 = 上が過去・下が未来)。今日は「いま」の線で過去と未来を分ける。月の下で使う */
function plDayAx(d){
  const future=d>TODAY;
  const hol=evOn(d).find(([k,e])=>e.hol);
  const s=daySpent(d);
  const head=dayHead(d,{href:'#/day/'+d,diary:dayDiary(d),sum:s?`自分 ${yen(s)}`:'',empty:hol?hol[1].t:'',future});
  const recs=actsOn(d).map(([k,a])=>({time:a.time||'',fut:false,html:momentRow(k)}));
  const plans=evOn(d).filter(([k,e])=>!e.hol&&!plRecordedOn(e,d)).map(([k,e])=>{
    const fut=future||(d===TODAY&&!e.all&&e.s>NOW);
    const late=!fut&&!(d===TODAY&&e.all);
    return {time:e.all?'00:00':e.s,fut,html:planRowAx(k,{future:fut,late})};
  });
  const items=[...recs,...plans].sort((a,b)=>a.time.localeCompare(b.time));
  const body=d===TODAY
    ?items.filter(x=>!x.fut).map(x=>x.html).join('')+nowLine()+items.filter(x=>x.fut).map(x=>x.html).join('')
    :items.map(x=>x.html).join('');
  return `<div class="ax">${axGroup(head,body||`<div class="pl-none">予定・記録 なし</div>`,{future,cls:d===TODAY?'today':''})}</div>`;
}

/* ---------- 日(アクティビティの「日」表示の中身。期間の送りは plDayNav をヘッダーへ) ---------- */
function plStepDay(n){
  plSel=addDays(plSel,n);
  plYM=[+plSel.slice(0,4),+plSel.slice(5,7)];
  rerender();
}
function plDayNav(){
  const d=plSel,hol=evOn(d).find(([k,e])=>e.hol);
  return periodNav({label:fmtMDW(d),sub:d===TODAY?'今日':(hol?esc(hol[1].t):''),prev:{on:'plStepDay(-1)',text:'前の日'},next:{on:'plStepDay(1)',text:'次の日'}});
}
/** 日表示: 時間グリッド(6:00–24:00)。重なりは列に分けて横に並べる(v2の日表示。v3から継続)。
    v4: グリッドは1日=1枚の面の中。記録=塗りのカプセルの帯、予定=点線の中抜き */
const plCatMark=c=>c?`<i class="pl-cat" style="--cc:${catColor(c)}"></i>`:'';
function plDayView(){
  const d=plSel, isToday=d===TODAY;
  const H0=6,H1=24,PH=48;
  const minOf=t=>{const [h,m]=String(t).split(':').map(Number);return h*60+m};
  const hol=evOn(d).find(([k,e])=>e.hol);
  const its=plItemsOn(d);
  const allday=its.filter(x=>x.all);
  const timed=its.filter(x=>!x.all).map(x=>{
    const s=Math.max(minOf(x.time),H0*60);
    const e=Math.min(Math.max(x.end?minOf(x.end):s+36,s+36),H1*60);
    return {x,s,e};
  }).sort((a,b)=>a.s-b.s);
  const lanesEnd=[];
  timed.forEach(b=>{let i=lanesEnd.findIndex(en=>en<=b.s);if(i<0){i=lanesEnd.length;lanesEnd.push(0)}lanesEnd[i]=b.e;b.i=i});
  const N=Math.max(1,lanesEnd.length);
  const blocks=timed.map(({x,s,e,i})=>{
    /* v4.13 本体調整: 瞬間(終わりの無い記録・支払い)は時間の帯にせず、その時刻に細いカプセル(高さ28・時刻に中心)。支払いは「支払い · 支払先 ¥金額」 */
    const inst=!x.end&&x.kind==='rec';
    const top=inst?(s-H0*60)/60*PH-14:(s-H0*60)/60*PH, h=inst?28:Math.max((e-s)/60*PH,44);
    const isRec=x.kind==='rec';
    const href=isRec?'#/a/'+x.k:'#/ev/'+x.k;
    const tm=x.end?`${x.time}–${x.end}`:x.time;
    const ra=isRec&&A[x.k];
    const ttl=ra&&ra.type==='支払い'?`${payLabel(ra)} ${yen(ra.amt)}`:x.t;
    return `<a class="pl-blk ${isRec?'rec':'plan'}${inst?' inst':''}" href="${href}" style="top:${top}px;height:${h}px;left:calc(52px + (100% - 52px) * ${i/N});width:calc((100% - 52px) / ${N} - 4px)"><b>${inst?`<small class="num">${esc(x.time)}</small> `:''}${esc(ttl)}</b>${h>=48?`<span>${tm}</span>`:''}</a>`;
  }).join('');
  let grid='';
  for(let h=H0;h<=H1;h++){const y=(h-H0)*PH;grid+=`<span class="pl-hl num" style="top:${y}px">${h}:00</span><i class="pl-hr" style="top:${y}px"></i>`}
  const nowHtml=isToday?`<div class="pl-now" style="top:${(minOf(NOW)-H0*60)/60*PH}px"><span>${NOW}</span></div>`:'';
  const alld=allday.map(x=>`<a class="pl-bar ${x.kind==='rec'?'rec':'plan'}" href="${x.kind==='rec'?'#/a/'+x.k:'#/ev/'+x.k}">${plCatMark(x.cat)}${esc(x.t)}</a>`).join('');
  const s=daySpent(d);
  return `<section class="pl-dpanel ${isToday?'today':''}">${dayHead(d,{href:'#/day/'+d,diary:dayDiary(d),sum:s?`自分 ${yen(s)}`:'',empty:hol?hol[1].t:''})}`
    +(alld?`<div class="pl-allday">${alld}</div>`:'')
    +`<div class="pl-tg" style="height:${(H1-H0)*PH+12}px">${grid}${blocks}${nowHtml}</div></section>`
    +plTodoBlock(d);
}

/** v4.13: その日が期限のToDo(日・月の表示の下)。チェック欄=完了、行=ToDoの詳細。0件なら何も出さない */
function plTodoBlock(d){
  const ks=plTodosOn(d);if(!ks.length)return '';
  const open=ks.filter(k=>!TD[k].done), done=ks.filter(k=>TD[k].done);
  return `<div class="pl-dtodo">${plSec('ToDo',`${ks.length}件`)}${objs([...open.map(plTodoRow),...done.map(plDoneRow)])}</div>`;
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
    ['期限切れ',k=>TD[k].due&&TD[k].due<TODAY,'late'],
    ['今日',k=>TD[k].due===TODAY,''],
    ['これから',k=>TD[k].due&&TD[k].due>TODAY,''],
    ['期限なし',k=>!TD[k].due,''],
  ];
  const quick=`<div class="list"><div class="field"><input id="pl-qa" placeholder="例: 明日 牛乳 #買い物" aria-label="ToDoを追加" onkeydown="if(event.key==='Enter')plQuickAdd()"><button class="btn sm" type="button" onclick="plQuickAdd()">追加</button></div></div>`;
  let body='';
  for(const [title,f,cls] of secs){
    const xs=openSel.filter(f);
    if(xs.length)body+=plSec(title,xs.length,cls)+objs(xs.map(plTodoRow));
  }
  if(!openSel.length&&!doneSel.length)body+=empty('todo','ToDo なし');
  if(doneSel.length){
    body+=plSec('完了',doneSel.length,'',{label:plShowDone?'閉じる':'表示',onclick:'plToggleDone()'})+(plShowDone?objs(doneSel.map(plDoneRow)):'');
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
/** ToDoの行: 行56px・チェック欄(塗り=完了)・行全体が詳細への押し面 */
function plTodoRow(k){
  const t=TD[k], over=t.due&&t.due<TODAY;
  const linked=t.ev&&EV[t.ev]?EV[t.ev].t:'';
  const parts=[
    t.due?(over?tag(relDay(t.due),{cls:'late'}):`<span>${relDay(t.due)}</span>`):'',
    LISTS[t.list]?`<span>${esc(LISTS[t.list].n)}</span>`:'',
    linked?`<span>${esc(linked)}</span>`:'',
  ].filter(Boolean).join('');
  return row({cls:'pl-todo',wrap:true,lead:checkbox(false,`toggleTask('${k}')`),t:esc(t.t)+(t.pri?'<span class="pl-pri">!</span>':''),s:`<span class="pl-s">${parts}</span>`,href:'#/task/'+k,chev:1});
}
function plDoneRow(k){
  const t=TD[k];
  return row({cls:'pl-todo',wrap:true,lead:checkbox(true,`toggleTask('${k}')`),t:`<span class="pl-done-t">${esc(t.t)}</span>`,href:'#/task/'+k,chev:1});
}
function toggleTask(k){
  const t=TD[k];if(!t)return;
  if(t.done){delete t.done;save();rerender();toast('未完了に戻しました');return}
  t.done=TODAY;save();rerender();
  toast('完了にしました',()=>{delete TD[k].done});
}

/* ---------- 新規ToDo ---------- */
CREATE.push({id:'task',label:'ToDo',sub:'やること・買う物',icon:'todo',color:'var(--tx-2)',primaryOn:['todo'],onclick:'openNewTask()'});
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

/* ---------- 古い入口: #/plan は廃止。アクティビティの 月 / 日 へ置き換える(ToDoは #/todo) ---------- */
route('plan',(id,q)=>{
  location.replace(q.view==='todo'?'#/todo':'#/activities?view='+(q.view==='day'?'day':'month'));
  return '<div class="pl-root"></div>';
},{tab:'activities'});

/* ---------- 予定の詳細: 時間軸の上の1点(主役) ---------- */
route('ev',id=>{
  const e=EV[id];
  if(!e)return appbar('予定')+empty('schedule','予定 なし');
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
  const late=!rec&&(od<TODAY||(od===TODAY&&!e.all&&e.s<NOW));
  const ppl=(e.who||[]).filter(w=>E[w]);
  const meta=[placeOf(e)?`<span>${IC.place}${esc(placeOf(e))}</span>`:'',ppl.length?`<span>${knots(ppl)}${ppl.map(p=>esc(E[p].n.split(' ').pop())).join('、')}</span>`:'',cd?`<span class="cd">${esc(cd)}</span>`:'',e.rep?`<span>${IC.repeat}次回 ${fmtMDW(nextOn(e))}</span>`:''].join('');
  const tm=e.all?(e.d2?`〜${fmtMD(e.d2)}`:'終日'):`${e.s}–${e.e}`;
  const cap=`<section class="cap plan ${late?'late':''}"><div class="kind">${IC.schedule}予定${e.pay?`・支払い`:''}<span class="tm">${esc(tm)}</span></div><h1>${esc(e.t)}</h1>${e.pay?`<div class="big">${yen(e.pay.amt)}</div>`:''}${meta?`<div class="meta">${meta}</div>`:''}</section>`;
  /* 予定の詳細: 記録の詳細と同じ骨格。上に日のカード、点線の軸の切れ端に掛かった中抜きのカプセル。前後の出来事は出さない */
  const axis=pinView({later:[{day:od,fut:od>=TODAY}],earlier:[],cap,future:!late,plan:true,late});
  const kin=[e.place?prop('場所',eref(e.place[0],e.place[1],'place')):'',e.who?prop('参加者',e.who.filter(w=>E[w]).map(w=>eref(w)).join('')):''].filter(Boolean);
  const props=[
    prop('繰り返し',esc(e.rep||'なし')),
    e.pay?`<a class="prop" href="#/ledger"><span class="k">支払い</span><span class="val">${catTag(e.pay.cat)}<span>${esc(catFx(e.pay.cat))}費</span></span><span class="meta">家計簿 ${IC.chev}</span></a>`:'',
    prop('通知',esc(e.alarm||'なし')),
    e.memo?prop('メモ',esc(e.memo)):'',
  ].join('');
  return '<div class="pl-root">'+appbar('',{actions:[{icon:'more',label:'操作',onclick:"toast('編集はモックでは未対応です')"}]})
    +axis
    +(kin.length?sh('関わるエンティティ',kin.length,unreg?{label:'場所として登録',onclick:`plRegisterPlace('${id}')`}:null)+list(kin):'')
    +`<div class="mt"></div>`+list([props])
    +(tasks.length?sh('この予定のToDo',tasks.length)+objs(tasks.map(plEvTaskRow)):'')
    +sh(e.rep&&rec?'前回の記録':'記録')
    +(rec
      ?`<div class="ax">${momentRow(e.rec)}</div>`
      :`<div class="btnrow"><button class="btn block" type="button" onclick="recordEvent('${id}')">${IC.check}${e.pay?'支払いとして記録する':'記録にする'}</button></div>`)+'</div>';
},{tab:'activities'});
function plEvTaskRow(k){
  const t=TD[k];
  return row({cls:'pl-todo',wrap:true,lead:checkbox(!!t.done,`toggleTask('${k}')`),href:'#/task/'+k,t:`<span class="${t.done?'pl-done-t':''}">${esc(t.t)}</span>`,s:t.due?relDay(t.due):'',chev:1});
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
  let aid;
  const placeId=e.place?e.place[0]:null;
  const placeName=e.place?(e.place[0]?E[e.place[0]].n:e.place[1]):'';
  if(e.pay){
    /* 支払いの予定は支払い + 自分の支出として作る(money.js の makePayment。親の kids にも入る) */
    aid=makePayment({parent:day,time:e.s||'09:00',items:[{n:e.t,price:e.pay.amt,users:['me'],tax:10}],
      payee:[placeId,placeName||e.t],roles:(e.who||[]).map(w=>['同席',w]),how:'カード ••1234',cat:e.pay.cat});
  }else{
    aid=uid('c');
    /* 予定の時刻の範囲のまま活動(期間)にする。種類は内容から推定せず「出来事」。終日は時刻の範囲が無いので1つの時刻 */
    const roles=(e.who||[]).map(w=>['参加',w]);
    if(e.place)roles.push(['場所',placeId,placeName]);
    const span=!e.all&&e.s&&e.e?{time:e.s,end:e.e}:{time:e.s||'09:00'};
    A[aid]={type:'出来事',parent:day,...span,title:e.t,roles,body:'',kids:[]};
    A[day].kids.push(aid);
  }
  e.rec=aid;
  save();go('#/a/'+aid);toast('記録にしました');
}

/* ---------- ToDoの詳細 ---------- */
route('task',id=>{
  const t=TD[id];
  if(!t)return appbar('ToDo')+empty('todo','ToDo なし');
  const ev=t.ev&&EV[t.ev];
  const subs=t.sub||[];
  const listOpts=Object.entries(LISTS).map(([k,L])=>`<option value="${k}" ${k===t.list?'selected':''}>${esc(L.n)}</option>`).join('');
  const links=[
    ev?row({href:'#/ev/'+t.ev,lead:`<span class="pl-ev-node"></span>`,t:esc(ev.t),s:fmtMDW(ev.d),chev:1}):'',
    ...(t.ent||[]).filter(x=>E[x]).map(x=>row({t:eref(x)})),
    t.pay&&A[t.pay]?row({t:aref(t.pay)}):'',
  ].filter(Boolean);
  const sub=[t.due?'期限 '+fmtMDW(t.due):'期限なし',t.done?'完了 '+fmtMD(t.done):''].filter(Boolean);
  /* 戻り先は ToDo の一覧(予定ではない) */
  return `<div class="pl-root"><header class="appbar"><button class="iconbtn" type="button" aria-label="ToDoへ戻る" onclick="go('#/todo')">${IC.back}</button><div class="title">ToDo</div></header>`
    +`<div class="pl-card"><div class="pl-tk">${checkbox(!!t.done,`toggleTask('${id}')`,true)}<h1 class="${t.done?'pl-done-t':''}">${esc(t.t)}</h1></div>${sub.length?`<div class="meta">${sub.map(x=>`<span>${x}</span>`).join('')}</div>`:''}</div>`
    +`<div class="mt"></div>`+list([
      prop('期限',`<input class="input" type="date" aria-label="期限" value="${t.due||''}" onchange="plSetDue('${id}',this.value)">`),
      prop('リスト',`<select class="input" aria-label="リスト" onchange="plSetList('${id}',this.value)">${listOpts}</select>`),
      prop('繰り返し',esc(t.rep||'なし')),
      prop('優先',toggle(t.pri,`plTogPri('${id}')`)),
    ])
    +(links.length?sh('結び付き')+list(links):'')
    +(subs.length?sh('サブタスク',subs.filter(x=>x[1]).length+'/'+subs.length)+list(subs.map((x,i)=>row({cls:'plain',lead:checkbox(x[1],`plSubToggle('${id}',${i})`),t:`<span class="${x[1]?'pl-done-t':''}">${esc(x[0])}</span>`}))):'')
    +(t.memo?sh('メモ')+list([row({cls:'plain',wrap:true,t:esc(t.memo)})]):'')+'</div>';
},{tab:'todo'});
function plSetDue(k,v){if(v)TD[k].due=v;else delete TD[k].due;save();rerender()}
function plSetList(k,v){TD[k].list=v;save();rerender()}
function plTogPri(k){if(TD[k].pri)delete TD[k].pri;else TD[k].pri=1;save();rerender()}
function plSubToggle(k,i){const s=TD[k].sub[i];s[1]=s[1]?0:1;save();rerender()}
