/* =====================================================================
   ホーム(route home) — UI v4.8 作り直し(ユーザー指示「ホーム画面、いちから作り直して」)
   構成(上から。ホームだけの専用の形。他の画面の行リスト・軸の部品は使わない):
     1 見出し: 大きな日付(34px・800)+右下の曜日、右上に「日記を書く」の淡いピル
     2 主役の面: 次の予定(残り時間・題名・時刻・場所と人) / 今日の流れ(6:00〜24:00 の帯。記録=白い短冊、予定=白い破線の枠、いま=白い線)
     3 記録待ち: 過ぎて未記録の今日の予定(あれば)。記録にする
     4 タイルの格子(2列): ToDo(2列) / 今月の支出 / 未精算 / すぐメモ(2列)
   予定・ToDo・メモ・記録の操作は各担当の共通関数を使う(recordEvent / toggleTask / memoAdd / memoOpen)
   見た目は css/s-home.css。色は tokens.css の変数だけ。
   ===================================================================== */

const hmMin=s=>{const [h,m]=String(s).split(':').map(Number);return h*60+m};
/** 「あと 1時間40分」(予定の詳細と同じ書き方) */
const hmLeft=s=>{const diff=hmMin(s)-hmMin(NOW);return `あと ${diff<60?diff+'分':Math.floor(diff/60)+'時間'+(diff%60?(diff%60)+'分':'')}`};
/** 今日の予定のうち、まだ記録になっていないもの(休日の札は除く) */
const hmTodayPlans=()=>evOn(TODAY).filter(([k,e])=>!e.hol&&!plRecordedOn(e,TODAY));
/** いまより後で一番近い、時刻のある今日の予定 */
const hmNextEv=()=>{const xs=hmTodayPlans().filter(([k,e])=>!e.all&&e.s>NOW).sort((a,b)=>a[1].s.localeCompare(b[1].s));return xs[0]||null};
/** 過ぎて未記録の今日の予定(終わった時刻が いま より前) */
const hmPastPlans=()=>hmTodayPlans().filter(([k,e])=>!e.all&&e.e&&e.e<=NOW);
/** 今日まで(期限切れを含む)の未完了のToDo。期限の古い順 */
const hmTodoKeys=()=>Object.keys(TD).filter(k=>!TD[k].done&&TD[k].due&&TD[k].due<=TODAY).sort((a,b)=>TD[a].due.localeCompare(TD[b].due));

/* ---------- 1. 見出し: 大きな日付 + 右上の日記の導線 ---------- */
function hmHead(){
  const dk=dayOfDate(TODAY), diary=dk&&dk[1].body;
  const pill=diary
    ?`<a class="hm-pill" href="#/day/${TODAY}">日記を開く</a>`
    :`<button class="hm-pill" type="button" onclick="openDiary(TODAY)">日記を書く</button>`;
  return `<header class="hm-head"><div class="hm-date"><h1 class="hm-d num">${+TODAY.slice(5,7)}月${+TODAY.slice(8,10)}日</h1><span class="hm-w">${wdOf(TODAY)}曜</span></div>${pill}</header>`;
}

/* ---------- 2. 主役の面: 次の予定 + 今日の流れ ---------- */
const HM_A=6*60, HM_B=24*60;
/** 時刻を帯の位置(%)に。6:00〜24:00 */
const hmPct=t=>Math.min(100,Math.max(0,(hmMin(t)-HM_A)/(HM_B-HM_A)*100));
/** 帯の中の短冊。記録=白い塗り、予定=白い破線。押すとその記録・予定へ */
function hmBand(){
  const bars=[];
  kidsOf(TODAY).forEach(([k,a])=>{
    if(!a.time)return;
    const l=hmPct(a.time), r=a.end?hmPct(a.end):l;
    bars.push(`<a class="hm-bar rec${a.end?'':' inst'}" href="#/a/${k}" style="left:${l.toFixed(2)}%;width:${Math.max(0,r-l).toFixed(2)}%" aria-label="${esc(a.title||'記録')} ${esc(timeText(a)||a.time)}"></a>`);
  });
  hmTodayPlans().filter(([k,e])=>!e.all&&e.s).forEach(([k,e])=>{
    const l=hmPct(e.s), r=e.e?hmPct(e.e):l;
    bars.push(`<a class="hm-bar plan" href="#/ev/${k}" style="left:${l.toFixed(2)}%;width:${Math.max(0,r-l).toFixed(2)}%" aria-label="予定 ${esc(e.t)} ${esc(e.s)}–${esc(e.e||'')}"></a>`);
  });
  const now=`<span class="hm-now" style="left:${hmPct(NOW).toFixed(2)}%" aria-label="いま ${NOW}"><b>${NOW}</b></span>`;
  const ticks=[6,12,18,24].map((h,i)=>`<span class="${i===0?'first':i===3?'last':''}" style="left:${((h*60-HM_A)/(HM_B-HM_A)*100).toFixed(2)}%">${h}</span>`).join('');
  return `<div class="hm-track">${now}<div class="hm-band">${bars.join('')}</div></div><div class="hm-ticks">${ticks}</div>`;
}
function hmMain(){
  const n=hmNextEv();
  let top;
  if(n){
    const [k,e]=n;
    const ppl=(e.who||[]).filter(w=>E[w]);
    const meta=[
      placeOf(e)?`<span class="hm-mi">${IC.place}${esc(placeOf(e))}</span>`:'',
      ppl.length?`<span class="hm-mi">${ppl.map(p=>`<span class="hm-who"><i class="hm-dot" aria-hidden="true"></i>${esc(E[p].n)}</span>`).join('')}</span>`:'',
    ].join('');
    top=`<a class="hm-nx" href="#/ev/${k}">`
      +`<span class="hm-r1"><span class="hm-lbl">次の予定</span><b class="hm-left num">${esc(hmLeft(e.s))}</b></span>`
      +`<span class="hm-title">${esc(e.t)}</span>`
      +`<span class="hm-time num">${esc(e.s)}–${esc(e.e)}</span>`
      +(meta?`<span class="hm-meta">${meta}</span>`:'')
      +`</a>`;
  }else{
    top=`<div class="hm-nx"><span class="hm-r1"><span class="hm-lbl">次の予定</span></span><span class="hm-title">今日の予定 なし</span></div>`;
  }
  return `<section class="hm-main">${top}<div class="hm-flow"><div class="hm-flow-l">今日の流れ</div>${hmBand()}</div></section>`;
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

/* ---------- 4. タイルの格子 ---------- */
/** ToDo(2列ぶち抜き): 今日までの未完了を最大3件。チェック欄・題名・期限(期限切れは赤の札) */
function hmTodoTile(){
  const ks=hmTodoKeys(), n=ks.length;
  const rows=ks.slice(0,3).map(k=>{
    const t=TD[k], over=t.due<TODAY, linked=t.ev&&EV[t.ev]?EV[t.ev].t:'';
    return `<div class="hm-tr">${checkbox(false,`toggleTask('${k}')`)}`
      +`<a class="hm-tt" href="#/task/${k}"><span class="hm-tt-t">${esc(t.t)}${t.pri?'<b class="hm-pri">!</b>':''}</span>`
      +`<span class="hm-tt-s">${over?'<span class="hm-od">期限切れ</span>':''}${t.due?`<span>${esc(relDay(t.due))}</span>`:''}${linked?`<span class="ell">${esc(linked)}</span>`:''}</span></a></div>`;
  }).join('');
  return `<section class="hm-tile hm-wide"><div class="hm-th"><h2>ToDo</h2><span class="hm-cnt num">${n}件</span><a class="hm-more" href="#/todo">すべて ›</a></div>`
    +(rows?`<div class="hm-tl">${rows}</div>`:'<p class="hm-none">今日までのToDo なし</p>')
    +`</section>`;
}
/** 今月の自分の支出(1列)。前月比は TREND の最後の月(9月)との比 */
function hmSpendTile(){
  const tot=ldTotal(ldMap());
  const prev=TREND[TREND.length-1];
  const pv=prev?Object.values(prev[1]).reduce((a,b)=>a+b,0):0;
  const rate=pv?Math.round((tot-pv)/pv*100):null;
  return `<a class="hm-tile" href="#/ledger"><span class="hm-k">${+TODAY.slice(5,7)}月の支出</span>`
    +`<b class="hm-amt num">${yen(tot)}</b>`
    +`<span class="hm-sub num">${rate==null?'':`前月比 ${rate>=0?'+':'−'}${Math.abs(rate)}%`}</span></a>`;
}
/** 未精算(1列): 立替の合計と相手。無ければ「なし」 */
function hmOweTile(){
  const owed=Object.values(A).filter(a=>a.adv&&a.adv[2]==='未精算');
  const sum=owed.reduce((s,a)=>s+a.adv[1],0);
  const people=[...new Set(owed.map(a=>a.adv[0]).filter(id=>E[id]))];
  return `<a class="hm-tile" href="#/ledger"><span class="hm-k">未精算</span>`
    +`<b class="hm-amt num">${owed.length?yen(sum):'なし'}</b>`
    +`<span class="hm-sub">${people.length?knots(people):''}</span></a>`;
}
/** すぐメモ(2列ぶち抜き): 1行の入力+保存。最新のメモ1件(2行まで・時刻)。押すとメモのシート */
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
  return `<div class="hm-grid">${hmTodoTile()}${hmSpendTile()}${hmOweTile()}${hmMemoTile()}</div>`;
}

route('home',()=>`<div class="hm-root">`
  +hmHead()
  +hmMain()
  +hmPending()
  +hmGrid()
  +`</div>`,{tab:'home'});
