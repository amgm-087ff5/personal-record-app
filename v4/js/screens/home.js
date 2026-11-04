/* =====================================================================
   ホーム(route home) — UI v4.9(ユーザー指示 第7回: 今日と、この1週間に絞る)
   構成(上から。ホームだけの専用の形):
     1 見出し: 大きな日付(34px・800)+右上の「日記を書く」ピル
     2 主役の面: 今日の流れ(縦の時間軸・上=過去・下=未来)。過去の記録 / いまの線 / 次の予定の大きな札 / これからの予定
     3 記録待ち: 過ぎて未記録の今日の予定(あれば)。記録にする
     4 タイルの格子(全部ぶち抜き・1列): 支出(今日+直近8日の棒グラフ+その場で登録) / ToDo / 未精算 / すぐメモ
   予定・ToDo・メモ・記録の操作は各担当の共通関数を使う(recordEvent / toggleTask / memoAdd / memoOpen / openQuickExpense)
   見た目は css/s-home.css。色は tokens.css の変数だけ。
   ===================================================================== */

const hmMin=s=>{const [h,m]=String(s).split(':').map(Number);return h*60+m};
/** 「あと 1時間40分」(予定の詳細と同じ書き方) */
const hmLeft=s=>{const diff=hmMin(s)-hmMin(NOW);return `あと ${diff<60?diff+'分':Math.floor(diff/60)+'時間'+(diff%60?(diff%60)+'分':'')}`};
/** 今日の予定のうち、まだ記録になっていないもの(休日の札は除く) */
const hmTodayPlans=()=>evOn(TODAY).filter(([k,e])=>!e.hol&&!plRecordedOn(e,TODAY));
/** いまより後で一番近い、時刻のある今日の予定(次の予定の大きな札になる) */
const hmNextEv=()=>{const xs=hmTodayPlans().filter(([k,e])=>!e.all&&e.s>NOW).sort((a,b)=>a[1].s.localeCompare(b[1].s));return xs[0]||null};
/** 過ぎて未記録の今日の予定(終わった時刻が いま より前)。記録待ちに出す */
const hmPastPlans=()=>hmTodayPlans().filter(([k,e])=>!e.all&&e.e&&e.e<=NOW);
/** 今日まで(期限切れを含む)の未完了のToDo。期限の古い順 */
const hmTodoKeys=()=>Object.keys(TD).filter(k=>!TD[k].done&&TD[k].due&&TD[k].due<=TODAY).sort((a,b)=>TD[a].due.localeCompare(TD[b].due));
/** 日付を n 日ずらす(ローカル時刻で計算し、UTC のずれを避ける) */
const hmAdd=(d,n)=>{const t=new Date(d+'T00:00');t.setDate(t.getDate()+n);const p=x=>String(x).padStart(2,'0');return `${t.getFullYear()}-${p(t.getMonth()+1)}-${p(t.getDate())}`};

/* ---------- 1. 見出し: 大きな日付 + 右上の日記の導線 ---------- */
function hmHead(){
  const dk=dayOfDate(TODAY), diary=dk&&dk[1].body;
  const pill=diary
    ?`<a class="hm-pill" href="#/day/${TODAY}">日記を開く</a>`
    :`<button class="hm-pill" type="button" onclick="openDiary(TODAY)">日記を書く</button>`;
  return `<header class="hm-head"><div class="hm-date"><h1 class="hm-d num">${+TODAY.slice(5,7)}月${+TODAY.slice(8,10)}日</h1><span class="hm-w">${wdOf(TODAY)}曜</span></div>${pill}</header>`;
}

/* ---------- 2. 主役の面: 今日の流れ(縦の時間軸) ----------
   上=過去・下=未来。過去は実線、これからは点線。いまの線で切り替わる。
   過去の記録は直近3件まで(それより前は「これより前 n件」から日のページへ) */
/** 記録の行(過去・これから共通の形)。押すとその記録へ */
function hmRecRow(k,a,cls){
  const t=a.type==='支払い'?payLabel(a):(a.title||'記録');
  return `<a class="hm-tr ${cls}" href="#/a/${k}">`
    +`<span class="hm-tc num">${esc(a.time)}</span><span class="hm-rl" aria-hidden="true"></span>`
    +`<span class="hm-c"><span class="hm-ct">${esc(t)}</span>${a.end?`<span class="hm-cs num">${esc(timeText(a))}</span>`:''}</span></a>`;
}
/** いまの線: 時刻の上に「いま」、横に細い白い線 */
const hmNowRow=()=>`<div class="hm-tr hm-nowr" role="separator" aria-label="いま ${NOW}">`
  +`<span class="hm-tc num"><b class="hm-nt">いま</b>${NOW}</span><span class="hm-rl" aria-hidden="true"></span>`
  +`<span class="hm-c"><span class="hm-line" aria-hidden="true"></span></span></div>`;
/** これからの予定(小さい行・点線の軸)。進行中の予定もここに入る */
function hmPlanRow(k,e){
  const ing=e.s<=NOW;
  return `<a class="hm-tr hm-fut" href="#/ev/${k}">`
    +`<span class="hm-tc num">${esc(e.s)}</span><span class="hm-rl" aria-hidden="true"></span>`
    +`<span class="hm-c"><span class="hm-ct hm-soft">${esc(e.t)}</span>`
    +`<span class="hm-cs num">${esc(e.s)}–${esc(e.e)}${ing?'<b class="hm-ing">進行中</b>':''}</span></span></a>`;
}
/** 次の予定: 大きな札(時刻の行の中に入れ込む)。題名は 20px まで */
function hmNextRow(k,e){
  const ppl=(e.who||[]).filter(w=>E[w]);
  const meta=[
    placeOf(e)?`<span class="hm-mi">${IC.place}${esc(placeOf(e))}</span>`:'',
    ppl.length?`<span class="hm-mi">${ppl.map(p=>`<span class="hm-who"><i class="hm-dot" aria-hidden="true"></i>${esc(E[p].n)}</span>`).join('')}</span>`:'',
  ].join('');
  return `<div class="hm-tr hm-fut hm-next">`
    +`<span class="hm-tc num">${esc(e.s)}</span><span class="hm-rl" aria-hidden="true"></span>`
    +`<span class="hm-c"><a class="hm-nx" href="#/ev/${k}">`
    +`<span class="hm-r1"><span class="hm-lbl">次の予定</span><b class="hm-left num">${esc(hmLeft(e.s))}</b></span>`
    +`<span class="hm-title">${esc(e.t)}</span>`
    +`<span class="hm-time num">${esc(e.s)}–${esc(e.e)}</span>`
    +(meta?`<span class="hm-meta">${meta}</span>`:'')
    +`</a></span></div>`;
}
/** 今日の流れ本体。記録(過去)・いまの線・次の予定・これからの予定 */
function hmTimeline(){
  const recs=kidsOf(TODAY).filter(([k,a])=>a.time).sort((x,y)=>x[1].time.localeCompare(y[1].time));
  const past=recs.filter(([k,a])=>a.time<=NOW), fut=recs.filter(([k,a])=>a.time>NOW);
  /* 予定は時刻のあるもの。終わった予定は記録待ちへ(ここには出さない) */
  const plans=hmTodayPlans().filter(([k,e])=>!e.all&&e.s&&e.e>NOW).sort((x,y)=>x[1].s.localeCompare(y[1].s));
  if(!past.length&&!fut.length&&!plans.length)return `<p class="hm-none-t">今日の予定 なし</p>`;
  const nx=hmNextEv(), nxk=nx?nx[0]:null;
  const rows=[];
  const more=past.length-3;
  if(more>0)rows.push(`<div class="hm-tr hm-past"><span class="hm-tc"></span><span class="hm-rl" aria-hidden="true"></span><a class="hm-more-l" href="#/day/${TODAY}">これより前 ${more}件</a></div>`);
  past.slice(-3).forEach(([k,a])=>rows.push(hmRecRow(k,a,'hm-past')));
  rows.push(hmNowRow());
  fut.forEach(([k,a])=>rows.push(hmRecRow(k,a,'hm-fut')));
  plans.forEach(([k,e])=>rows.push(k===nxk?hmNextRow(k,e):hmPlanRow(k,e)));
  return `<div class="hm-tl">${rows.join('')}</div>`;
}
function hmMain(){
  return `<section class="hm-main"><div class="hm-hd"><h2 class="hm-hh">今日の流れ</h2><a class="hm-more" href="#/activities">アクティビティ ›</a></div>${hmTimeline()}</section>`;
}

/* ---------- 3. 記録待ち(過ぎて未記録の今日の予定) ---------- */
function hmPending(){
  const xs=hmPastPlans();
  if(!xs.length)return '';
  return `<section class="hm-pend"><div class="hm-pend-h">記録待ち</div>`
    +xs.map(([k,e])=>`<div class="hm-pr"><span class="cap plan late hm-cap" aria-hidden="true"></span>`
      +`<span class="hm-pr-b"><span class="hm-pr-t">${esc(e.t)}</span><span class="hm-pr-s num">${esc(e.s)}–${esc(e.e)}</span></span>`
      +`<button class="hm-rec" type="button" onclick="recordEvent('${k}')">記録にする</button></div>`).join('')
    +`</section>`;
}

/* ---------- 4. タイルの格子(すべて1列ぶち抜き) ---------- */
/** 1日の支出(変動費の合計)。架空の支払いも数える。固定費(家賃・通信・サブスクなど)は除く */
const hmSpendOn=d=>Object.values(X).reduce((s,x)=>{const a=A[x.pay];if(!a)return s;return dateOf(a)===d&&xFx(x)!=='固定'?s+x.amt:s},0);
/** 期間(両端を含む)の支出の合計 */
const hmSpendRange=(from,to)=>{let s=0;for(let d=from;d<=to;d=hmAdd(d,1))s+=hmSpendOn(d);return s};
/** 支出: 今日の額と先週の同じ曜日との差 / 直近8日の棒グラフ / この7日間と前の7日間 / その場で登録 */
function hmMoney(){
  const days=[];for(let i=-7;i<=0;i++)days.push(hmAdd(TODAY,i));   /* 10/24(土)〜10/31(今日) */
  const vals=days.map(hmSpendOn);
  const max=Math.max(...vals,1);
  const cols=days.map((d,i)=>{
    const today=i===days.length-1;
    const h=vals[i]?Math.max(2,Math.round(vals[i]/max*88)):2;   /* 最大88px・最小2px */
    return `<div class="hm-col${today?' today':''}" role="img" aria-label="${md(d)}(${wdOf(d)}) ${yen(vals[i])}">`
      +`<span class="hm-bv" style="height:${h}px"></span><span class="hm-wd">${today?'今日':wdOf(d)}</span></div>`;
  }).join('');
  const today=vals[7], sat=vals[0], diff=today-sat;
  const diffTxt=diff===0?'±0':(diff>0?'+':'−')+yen(Math.abs(diff));
  const thisW=hmSpendRange(hmAdd(TODAY,-6),TODAY), prevW=hmSpendRange(hmAdd(TODAY,-13),hmAdd(TODAY,-7));
  const rate=prevW?Math.round((thisW-prevW)/prevW*100):null;
  return `<section class="hm-tile hm-wide hm-money">`
    +`<div class="hm-th"><h2>支出</h2><a class="hm-more" href="#/ledger">家計簿 ›</a></div>`
    +`<div class="hm-sp-top"><div><span class="hm-k">今日 <span class="hm-note">固定費を除く</span></span><b class="hm-big num">${yen(today)}</b></div>`
    +`<div class="hm-sp-cmp"><span class="hm-sub num">先週の土曜 ${yen(sat)}</span>${tag(diffTxt)}</div></div>`
    +`<div class="hm-chart" role="group" aria-label="直近8日間の支出">${cols}</div>`
    +`<div class="hm-wk"><div><span class="hm-k">この7日間 ${md(hmAdd(TODAY,-6))}〜${md(TODAY)}</span><b class="hm-amt2 num">${yen(thisW)}</b></div>`
    +`<div class="hm-wk-b"><span class="hm-sub num">前の7日間 ${yen(prevW)}</span>${rate==null?'':tag(`${rate>=0?'+':'−'}${Math.abs(rate)}%`)}</div></div>`
    +`<form class="hm-qe" onsubmit="hmQeGo();return false"><input id="hm-qe-amt" class="num" inputmode="numeric" placeholder="¥ 金額" aria-label="金額" autocomplete="off">`
    +`<button class="hm-qe-b" type="submit">記録</button><button class="hm-qe-more" type="button" onclick="openQuickExpense({stay:true})">詳しく</button></form>`
        +`</section>`;
}
/** 入力した金額で支払いのシートを開く(カテゴリを選んで保存するだけ)。保存後はホームに留まる */
function hmQeGo(){
  const el=document.getElementById('hm-qe-amt');
  const v=String(el?el.value:'').replace(/[^\d]/g,'');
  if(!v||+v===0){toast('金額を入れてください');return}
  openQuickExpense({stay:true});CQ.amt=v;crQeRender();
}
/** ToDo(全幅): 今日までの未完了を最大3件。チェック欄・題名・期限(期限切れは赤の札) */
function hmTodoTile(){
  const ks=hmTodoKeys(), n=ks.length;
  const rows=ks.slice(0,3).map(k=>{
    const t=TD[k], over=t.due<TODAY, linked=t.ev&&EV[t.ev]?EV[t.ev].t:'';
    return `<div class="hm-tr2">${checkbox(false,`toggleTask('${k}')`)}`
      +`<a class="hm-tt" href="#/task/${k}"><span class="hm-tt-t">${esc(t.t)}${t.pri?'<b class="hm-pri">!</b>':''}</span>`
      +`<span class="hm-tt-s">${over?'<span class="hm-od">期限切れ</span>':''}${t.due?`<span>${esc(relDay(t.due))}</span>`:''}${linked?`<span class="ell">${esc(linked)}</span>`:''}</span></a></div>`;
  }).join('');
  return `<section class="hm-tile hm-wide"><div class="hm-th"><h2>ToDo</h2><span class="hm-cnt num">${n}件</span><a class="hm-more" href="#/todo">すべて ›</a></div>`
    +(rows?`<div class="hm-tl">${rows}</div>`:'<p class="hm-none">今日までのToDo なし</p>')
    +`</section>`;
}
/** 未精算(全幅・1行): 立替の合計と相手。無ければ「なし」 */
function hmOweTile(){
  const owed=Object.values(A).filter(a=>a.adv&&a.adv[2]==='未精算');
  const sum=owed.reduce((s,a)=>s+a.adv[1],0);
  const people=[...new Set(owed.map(a=>a.adv[0]).filter(id=>E[id]))];
  return `<a class="hm-tile hm-wide hm-owe" href="#/ledger"><span class="hm-k">未精算</span>`
    +`<b class="hm-amt num">${owed.length?yen(sum):'なし'}</b>`
    +`<span class="hm-sub">${people.length?knots(people):''}</span></a>`;
}
/** すぐメモ(全幅): 1行の入力+保存。最新のメモ1件(2行まで・時刻)。押すとメモのシート */
function hmMemoSave(){
  const el=document.getElementById('hm-memo');
  const body=(el?el.value:'').trim();
  if(!body){toast('メモを入れてください');return}
  memoAdd(body);save();rerender();toast('メモを保存しました');
}
function hmMemoTile(){
  const last=Object.entries(MEMOS).sort((a,b)=>b[1].at.localeCompare(a[1].at))[0];
  return `<section class="hm-tile hm-wide"><div class="hm-th"><h2>すぐメモ</h2><a class="hm-more" href="#/memo">すべて ›</a></div>`
    +`<div class="hm-memo" onkeydown="if(event.key==='Enter'&&!event.isComposing)hmMemoSave()"><input id="hm-memo" placeholder="思いついたことを書く" aria-label="メモ"><button class="hm-save" type="button" onclick="hmMemoSave()">保存</button></div>`
    +(last?`<button class="hm-last" type="button" onclick="memoOpen('${last[0]}')"><span class="hm-last-b">${esc(last[1].body)}</span><span class="hm-last-t num">${md(last[1].at.slice(0,10))} ${esc(last[1].at.slice(11,16))}</span></button>`:'')
    +`</section>`;
}
function hmGrid(){
  return `<div class="hm-grid">${hmMoney()}${hmTodoTile()}${hmOweTile()}${hmMemoTile()}</div>`;
}

route('home',()=>`<div class="hm-root">`
  +hmHead()
  +hmMain()
  +hmPending()
  +hmGrid()
  +`</div>`,{tab:'home'});
