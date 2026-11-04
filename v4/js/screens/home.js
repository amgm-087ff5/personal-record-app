/* =====================================================================
   ホーム — 毎日最初に見る統合ページ(route home)。見た目は css/s-home.css
   順: 0 ヒーロー(今日の大きな面: 日付・今日の数字3つ・日記の導線) / 1 次の予定 / 2 今日の残り(軸)
       / 3 今日のToDo / 4 すぐメモ / 5 お金
   見出しはヒーローが担う(他のタブの小さい大見出しは置かない)。今日・いまは data.js の TODAY と ui.js の NOW。
   予定・ToDo・メモの操作は各担当の関数を使う
   ===================================================================== */

const hmMin=s=>{const [h,m]=String(s).split(':').map(Number);return h*60+m};
/** 「あと1時間40分」(予定の詳細と同じ書き方) */
const hmLeft=s=>{const diff=hmMin(s)-hmMin(NOW);return `あと${diff<60?diff+'分':Math.floor(diff/60)+'時間'+(diff%60?(diff%60)+'分':'')}`};
/** 今日の予定のうち、まだ記録になっていないもの(休日の札は除く) */
const hmTodayPlans=()=>evOn(TODAY).filter(([k,e])=>!e.hol&&!plRecordedOn(e,TODAY));
/** いまより後で一番近い、時刻のある今日の予定 */
const hmNextEv=()=>{const xs=hmTodayPlans().filter(([k,e])=>!e.all&&e.s>NOW).sort((a,b)=>a[1].s.localeCompare(b[1].s));return xs[0]||null};
/** 今日のToDo(期限が今日以前・未完了。期限切れを含む)のid */
const hmTodoKeys=()=>Object.keys(TD).filter(k=>!TD[k].done&&TD[k].due&&TD[k].due<=TODAY).sort((a,b)=>TD[a].due.localeCompare(TD[b].due));
/** 今日の自分の支出の合計(支払いの日が今日。自分の取り分 X を使う。無ければ支払い額) */
const hmSpend=()=>Object.values(A).filter(a=>a.type==='支払い'&&dateOf(a)===TODAY).reduce((s,a)=>s+((a.x&&X[a.x])?X[a.x].amt:(a.amt||0)),0);

/* ---------- 0. ヒーロー: 今日の大きな面(青の塗り+光+艶)。押すと日のページ。日記の導線を1つ ---------- */
function hmHero(){
  const dk=dayOfDate(TODAY), diary=dk&&dk[1].body;
  const evN=evOn(TODAY).filter(([k,e])=>!e.hol).length;
  const nums=[
    {l:'今日の予定',v:`${evN}<small>件</small>`},
    {l:'ToDo',v:`${hmTodoKeys().length}<small>件</small>`},
    {l:'今日の支出',v:yen(hmSpend())},
  ];
  return `<section class="hm-hero">`
    
    +`<a class="hm-hero-a" href="#/day/${TODAY}">`
      +`<div class="hm-date"><span class="hm-n num">${+TODAY.slice(8)}</span><span class="hm-dw"><b>${+TODAY.slice(5,7)}月</b> ${wdOf(TODAY)}曜</span></div>`
      +`<div class="hm-nums">${nums.map(n=>`<span class="hm-num"><i>${n.l}</i><b class="num">${n.v}</b></span>`).join('')}</div>`
    +`</a>`
    +`<div class="hm-foot">`
      +(diary?`<span class="hm-diary-t">${esc(diary.split('\n')[0])}</span>`:'<span class="hm-diary-t"></span>')
      +`<button class="hm-diary-btn" type="button" onclick="openDiary(TODAY)">${diary?'日記を開く':'日記を書く'}</button>`
    +`</div>`
    +`</section>`;
}

/* ---------- 1. 次の予定(予定の詳細の主役と同じ形: 点線の中抜きのカプセル) ---------- */
function hmNext(){
  const n=hmNextEv();
  if(!n)return sh('次の予定')+empty('schedule','これからの予定 なし');
  const [k,e]=n;
  const ppl=(e.who||[]).filter(w=>E[w]);
  const meta=[
    placeOf(e)?`<span>${IC.place}${esc(placeOf(e))}</span>`:'',
    ppl.length?`<span>${knots(ppl)}${ppl.map(p=>esc(E[p].n.split(' ').pop())).join('、')}</span>`:'',
    `<span class="cd">${esc(hmLeft(e.s))}</span>`,
  ].join('');
  return sh('次の予定')
    +`<a class="cap plan hm-next" href="#/ev/${k}"><div class="kind">${IC.schedule}予定<span class="tm">${esc(e.s)}–${esc(e.e)}</span></div>`
    +`<h1>${esc(e.t)}</h1><div class="meta">${meta}</div></a>`;
}

/* ---------- 2. 今日の残り: 過ぎた記録・過ぎた予定(記録待ち) → いまの線 → これからの予定(時刻の古い順・上が過去・下が未来) ---------- */
function hmRest(){
  const nx=hmNextEv(), nk=nx&&nx[0];
  const items=[];
  hmTodayPlans().filter(([k])=>k!==nk).forEach(([k,e])=>{
    const fut=!e.all&&e.s>NOW, late=!fut&&!e.all;
    items.push({time:e.all?'00:00':e.s,fut,html:planRowAx(k,{future:fut,late})});
  });
  kidsOf(TODAY).forEach(([k,a])=>items.push({time:a.time||'',fut:false,html:momentRow(k)}));
  items.sort((a,b)=>a.time.localeCompare(b.time));
  if(!items.length)return sh('今日の残り')+empty('schedule','予定・記録 なし');
  const body=items.filter(x=>!x.fut).map(x=>x.html).join('')+nowLine()+items.filter(x=>x.fut).map(x=>x.html).join('');
  return sh('今日の残り',items.length)+`<div class="ax">${axGroup('',body,{future:false,cls:'today'})}</div>`;
}

/* ---------- 3. 今日のToDo(最大5件。すべて見るは #/todo) ---------- */
function hmTodoRow(k){
  const t=TD[k], over=t.due&&t.due<TODAY;
  const linked=t.ev&&EV[t.ev]?EV[t.ev].t:'';
  const parts=[
    t.due?(over?tag(relDay(t.due),{cls:'late'}):`<span>${relDay(t.due)}</span>`):'',
    linked?`<span>${esc(linked)}</span>`:'',
  ].filter(Boolean).join('');
  return row({cls:'hm-todo',wrap:true,lead:checkbox(false,`toggleTask('${k}')`),
    t:esc(t.t)+(t.pri?'<span class="hm-pri">!</span>':''),s:`<span class="hm-s">${parts}</span>`,href:'#/task/'+k,chev:1});
}
function hmTodo(){
  const ks=hmTodoKeys();
  const n=ks.length;
  if(!n)return sh('今日のToDo')+empty('todo','今日のToDo なし');
  return sh('今日のToDo',n,n>5?{label:'すべて見る',href:'#/todo'}:null)
    +objs(ks.slice(0,5).map(hmTodoRow));
}

/* ---------- 4. すぐメモ(1行入力+保存。最近のメモ2件) ---------- */
/** memoCard は担当(メモ)が作る共通部品。まだ無い時は本文だけの簡単な表示 */
function hmMemoCard(id){
  const m=MEMOS[id];
  return `<div class="row hm-memo"><span class="grow"><div class="t">${esc(m.body)}</div><div class="s">${esc(m.at.slice(5,7))}/${esc(m.at.slice(8,10))} ${esc(m.at.slice(11,16))}</div></span></div>`;
}
function hmMemoSave(){
  const el=document.getElementById('hm-memo');
  const body=(el?el.value:'').trim();
  if(!body){toast('メモを入れてください');return}
  memoAdd(body);save();rerender();toast('メモを保存しました');
}
function hmMemo(){
  const recent=Object.entries(MEMOS).sort((a,b)=>b[1].at.localeCompare(a[1].at)).slice(0,2);
  const input=`<div class="list hm-memo-in"><div class="field"><input id="hm-memo" placeholder="思いついたことを書く" aria-label="メモ" onkeydown="if(event.key==='Enter'&&!event.isComposing)hmMemoSave()"><button class="btn sm" type="button" onclick="hmMemoSave()">保存</button></div></div>`;
  const cards=recent.map(([id])=>typeof memoCard==='function'?`<div class="memo">${memoCard(id)}</div>`:hmMemoCard(id));
  return sh('すぐメモ',null,{label:'すべて見る',href:'#/memo'})+input+(cards.length?objs(cards):'');
}

/* ---------- 5. お金(今月の自分の支出 / 未精算の立替) ---------- */
function hmMoney(){
  const tot=ldTotal(ldMap());
  const mo=+TODAY.slice(5,7);
  const owed=Object.values(A).filter(a=>a.adv&&a.adv[2]==='未精算');
  const owedSum=owed.reduce((s,a)=>s+a.adv[1],0);
  const owedS=owed.length?owed.map(a=>`${esc(E[a.adv[0]]?E[a.adv[0]].n:'')} ・ ${esc(a.x&&X[a.x]?xTitle(X[a.x]):payLabel(a))}`).join('、'):'なし';
  return sh('お金')+objs([
    ticket({href:'#/ledger',stub:`<b>${mo}</b>月`,t:`${mo}月の自分の支出`,amt:yen(tot)}),
    row({href:'#/ledger',cls:'hm-owe',t:'未精算の立替',s:owedS,v:yen(owedSum),amt:true,chev:1}),
  ]);
}

route('home',()=>`<div class="hm-root"><h1 class="sr">ホーム</h1>`
  +hmHero()
  +hmNext()
  +hmRest()
  +hmTodo()
  +hmMemo()
  +hmMoney()
  +`</div>`,{tab:'home'});
