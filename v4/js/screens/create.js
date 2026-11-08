/* =====================================================================
   作成フロー(中央の＋から開く): 支払いを記録(手で入力・レシートから読み取る)・出来事・予定・ToDo・日記・メモ・エンティティ
   画面ルートは持たず、シートで入力して保存後に各詳細へ移動する。
   名前は cr 接頭辞。入力は oninput で状態へ書き戻し、構造が変わる時だけ描き直す。
   ===================================================================== */

/* ---------- 共通の小部品 ---------- */
const crPeople=()=>Object.entries(E).filter(([k,e])=>k!=='me'&&e.t.some(t=>isUnder(t,'person')));
const crPlaces=()=>Object.entries(E).filter(([k,e])=>e.t.some(t=>isUnder(t,'place')));
const crNowHM=()=>{const d=new Date();return String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0')};
const crFindEnt=n=>{n=(n||'').trim();const f=Object.entries(E).find(([k,e])=>e.n===n);return f?f[0]:null};
const crNewEnt=(n,t)=>{const id=uid('e');E[id]={n,i:n[0],t,facts:[]};return id};
/** 日付の日記を取得(無ければ作る) */
function crDay(date){
  const f=Object.entries(A).find(([k,a])=>a.type==='日記'&&a.date===date);if(f)return f[0];
  const id='d'+date.replace(/-/g,'');A[id]={type:'日記',date,dd:String(+date.slice(8)),dw:wdOf(date),body:'',kids:[]};return id;
}
/** 店の種類からカテゴリを推定(子カテゴリのみ。未登録は外食) */
function crGuessCat(eid){if(!eid)return 'eatout';const t=E[eid].t;
  if(t.some(x=>isUnder(x,'cafe')))return 'cafe';if(t.some(x=>isUnder(x,'food')))return 'eatout';
  if(t.some(x=>isUnder(x,'super')))return 'cook';if(t.some(x=>isUnder(x,'drug')))return 'daily';return 'eatout';}
/** 支払い + 自分の支出(子)を作る。v4.16: 中身は makePayment(js/money.js)。品目は users(使った人)。
   互換: 品目に b(自分/半分/相手)だけがある時は users に読み替える。o.basis/o.me/o.n で自分の負担の決め方 */
function crPayment(o){
  const day=crDay(o.date);let shopId=crFindEnt(o.shop);
  if(!shopId&&o.register&&o.shop)shopId=crNewEnt(o.shop.trim(),[o.shopType||'shop']);
  const parent=o.act&&A[o.act]&&A[o.act].type!=='日記'?o.act:day;
  const other=o.with||'other';
  const items=o.items.map(i=>({...i,users:i.users&&i.users.length?i.users:(i.b==='半分'?['me',other]:i.b==='相手'?[other]:['me'])}));
  const aid=makePayment({parent,time:o.time,items,amt:o.amt,payee:[shopId,o.shop||'(店なし)'],roles:o.with?[['同席',o.with]]:[],
    paid:o.paid,how:o.pay||'現金',basis:o.basis,me:o.me,n:o.n,cat:o.cat||crGuessCat(shopId),fx:o.fx,sp:o.sp,memo:o.memo});
  const kids=A[parent].kids;if(kids[kids.length-1]===aid)kids.pop(); /* 下の共通処理で入れる */
  A[parent].kids.push(aid);
  EXTRA.attach=EXTRA.attach||{};EXTRA.hashes=EXTRA.hashes||{};
  if(o.img)EXTRA.attach[aid]=o.img;
  if(o.hash)EXTRA.hashes[aid]=o.hash;
  (o.todo||[]).forEach(k=>{if(TD[k]){TD[k].done=TODAY;TD[k].pay=aid}});
  return aid;
}
const crSheetOpen=()=>document.getElementById('sheet').classList.contains('on');
/** シートの下部ボタン。html が '' なら消す */
function crFooter(html){const s=document.getElementById('sheet');let f=s.querySelector('.sheet-f');
  if(!html){if(f)f.remove();return}
  if(!f){s.insertAdjacentHTML('beforeend','<div class="sheet-f"></div>');f=s.querySelector('.sheet-f')}
  f.innerHTML=html;}
/** シートを開く/中身を描き直す。同じタイトルの間は本文だけ差し替える(入力の状態を保つ) */
function crPanel(title,body,opt={}){
  const h=document.querySelector('#sheet .sheet-h .title');
  if(crSheetOpen()&&h&&h.textContent===title){
    setSheetBody(body);
    if(opt.top){const b=document.getElementById('sheet-b');if(b)b.scrollTop=0}
    if('footer' in opt||opt.right)crFooter(opt.footer||sheetMainBtn(opt.right));
    return;
  }
  openSheet({title,body,full:opt.full});
  crFooter(opt.footer||sheetMainBtn(opt.right));
}

/* ---------- 支払いを入れる活動(期間)の選び方 ----------
   候補 = その日の活動(期間。支払い・瞬間は除く)。既定 = 支払いの時刻を含む活動、無ければ「なし(日の直下)」。
   st.act: undefined = 既定(時刻で決める) / '' = なし / 活動のid。日付が変わって合わなければ「なし」 */
const crActCands=d=>actsOn(d).filter(([k,a])=>a.type!=='支払い'&&isSpan(a)).sort((x,y)=>x[1].time.localeCompare(y[1].time));
/* v4.14 既定は時刻を含む活動のうち一番内側(開始が一番遅いもの。活動の中の活動に入る) */
function crActDefault(d,t){const h=crActCands(d).filter(([k,a])=>a.time<=t&&t<=a.end).pop();return h?h[0]:''}
function crActResolve(st,d,t){if(st.act===undefined)return crActDefault(d,t);return st.act&&A[st.act]&&dateOf(A[st.act])===d?st.act:''}
/** set: 選んだ時に実行する式(状態へ書き戻す。描き直さない) */
function crActInner(st,d,t,set){const cur=crActResolve(st,d,t);
  /* 候補に無い活動(瞬間の親など。act で渡された)も、選ばれた状態で1つ出す */
  const cands=crActCands(d),extra=cur&&A[cur]&&!cands.some(([k])=>k===cur)?[[cur,A[cur]]]:[];
  return `<label>活動の中</label><select aria-label="どの活動の中か" onchange="${set}">`
    +`<option value="" ${cur===''?'selected':''}>なし(日の直下)</option>`
    +[...extra,...cands].map(([k,a])=>`<option value="${k}" ${cur===k?'selected':''}>${esc(timeText(a))} ${esc(a.title||'')}</option>`).join('')+'</select>'}
function crActField(st,d,t,set,id){return `<div class="field" id="${id}">${crActInner(st,d,t,set)}</div>`}
/** 日付・時刻の入力のたびに、選択肢の中身だけ差し替える(入力欄の框は保つ) */
function crActSync(id,st,d,t,set){const el=document.getElementById(id);if(el)el.innerHTML=crActInner(st,d,t,set)}

/* ---------- 登録 ---------- */
/* 主の行(青の塗り)= タブごとに「そのタブで一番よく作るもの」1つだけ。
   ホーム・家計簿・設定=支払いを記録 / 記録=出来事 / 予定=予定 / メモ=メモ / ToDo=ToDo(plan.js) / エンティティ=エンティティ */
/* v4.17 支払いの記録は1つの入口。手で入力が既定、レシートはその中の「読み取る」手段の1つ(支払い方法ではない) */
CREATE.push({id:'payment',label:'支払いを記録',icon:'pay',color:'var(--ev)',primaryOn:['home','ledger','settings'],onclick:'openPayment()'});
CREATE.push({id:'happening',label:'出来事',icon:'moment',color:'var(--ev)',primaryOn:['activities'],onclick:'openNewHappening()'});
CREATE.push({id:'event',label:'予定',icon:'future',color:'var(--plan)',primaryOn:['activities'],onclick:'openNewEvent()'});
CREATE.push({id:'entity',label:'エンティティ',icon:'kin',color:'var(--kin)',primaryOn:['entities'],onclick:'openNewEntity()'});
CREATE.push({id:'diary',label:'今日の日記',icon:'diary',color:'var(--ev)',primaryOn:[],onclick:'openDiary(TODAY)'});
CREATE.push({id:'memo',label:'メモ',icon:'memo',color:'var(--ev)',primaryOn:['memo'],onclick:'openNewMemo()'});

/* ---------- メモ(1枚のシートで本文と保存) ---------- */
let CM=null;
function openNewMemo(){CM={body:''};crMemoRender()}
function crMemoRender(){
  crPanel('メモ',`<div class="list"><div class="field cr-memo"><textarea rows="5" placeholder="思いついたこと" oninput="CM.body=this.value">${esc(CM.body)}</textarea></div></div>`,{right:{label:'メモを保存',onclick:'crMemoSave()'}});
}
function crMemoSave(){const b=(CM.body||'').trim();if(!b){toast('本文を入れてください');return}
  memoAdd(b);CM=null;closeSheet();save();rerender();toast('メモを保存しました');
}

/* ---------- エンティティの候補(輪郭つき) ----------
   入力欄の下にチップで出す。datalist は要素を持てないため使わない */
function crPlaceSug(q,pick){
  const s=(q||'').trim();
  const hits=crPlaces().filter(([k,e])=>e.n!==s&&(!s||e.n.includes(s))).slice(0,8);
  if(!hits.length)return '';
  /* mousedown を止めて入力欄の blur(=再描画)より先にクリックを届ける */
  return `<div class="chips" role="group" aria-label="場所の候補">${hits.map(([k,e])=>`<button class="chip" type="button" onmousedown="event.preventDefault()" onclick="${pick}('${k}')">${knot(k,'sm')}${esc(e.n)}</button>`).join('')}</div>`;
}
/** 入力のたびに候補の要素だけ差し替える(入力欄の框は保つ) */
function crSugUpdate(id,q,pick){const el=document.getElementById(id);if(el)el.innerHTML=crPlaceSug(q,pick)}
/** 人の選択チップ(輪郭つき)。cur: 選択中のid、空なら「分けない」 */
function crWithChips(cur){
  return `<div class="chips" role="group" aria-label="誰かと分ける">
    <button class="chip" type="button" aria-pressed="${!cur}" onclick="crSetWith('')">分けない</button>
    ${crPeople().map(([k,e])=>`<button class="chip" type="button" aria-pressed="${cur===k}" onclick="crSetWith('${k}')">${knot(k,'sm')}${esc(e.n)}</button>`).join('')}
  </div>`;
}

/* カテゴリが「都度指定」の項目(固定・変動 / 特別感)だけ、入力時に選ぶ欄を出す。既定値のあるカテゴリでは何も出さない */
function crAskHtml(st,cat,fn){
  const rows=[];
  if(catFx(cat)===null)rows.push(`<div class="field"><label>固定・変動</label>${ldMini([['固定','固定'],['変動','変動']],st.fx||'',`${fn}('fx','$v')`)}</div>`);
  if(catSp(cat)===null)rows.push(`<div class="field"><label>特別感</label>${ldMini([['日常','日常'],['非日常','非日常']],st.sp||'',`${fn}('sp','$v')`)}</div>`);
  return rows.length?sh('このカテゴリは都度指定')+list(rows):'';
}
/* ---------- 支払いを記録(手で入力。上に「レシートから読み取る」)v4.17 ---------- */
let CQ={amt:'',shop:'',date:TODAY,root:'food',cat:'eatout'};
/* o.stay: ホームの入力欄から開いた時は、保存後に元の画面に留まる(記録の画面へ移動しない) */
function openQuickExpense(o){CQ={amt:'',shop:'',date:TODAY,time:crNowHM(),how:PAY_METHODS[0],root:'food',cat:'eatout',stay:!!(o&&o.stay)};crQeRender()}
const openPayment=openQuickExpense;
function crQeX(k,v){CQ[k]=CQ[k]===v?'':v;crQeRender()}
function crQeChildren(root){return Object.keys(CATS).filter(k=>CATS[k].p===root)}
function crQeRoot(k){CQ.root=k;const ks=crQeChildren(k);CQ.cat=ks.length?ks[0]:k;crQeRender()}
function crQeRender(){
  const kids=crQeChildren(CQ.root);
  const body=`<div class="btnrow"><button class="btn ghost block cr-ocr" type="button" onclick="openReceipt()">${IC.camera}レシートから読み取る</button></div>
  <div class="list">
    <div class="field"><label>金額</label><input class="amt num" id="cr-qe-amt" inputmode="numeric" autofocus placeholder="¥0" value="${esc(CQ.amt)}" oninput="CQ.amt=this.value"></div>
    <div class="field"><label>日付</label><input type="date" value="${CQ.date}" oninput="CQ.date=this.value;crActSync('cr-qe-act',CQ,CQ.date,CQ.time,'CQ.act=this.value')"></div>
    <div class="field"><label>店</label><input value="${esc(CQ.shop)}" placeholder="店名を入力" oninput="CQ.shop=this.value;crSugUpdate('cr-sug-qe',this.value,'crQePick')"></div>
    <div class="cr-sug" id="cr-sug-qe">${crPlaceSug(CQ.shop,'crQePick')}</div>
    <div class="field"><label>支払い方法</label>${payMethodSelect(CQ.how,'CQ.how=this.value')}</div>
    ${crActField(CQ,CQ.date,CQ.time,'CQ.act=this.value','cr-qe-act')}
  </div>
  ${sh('カテゴリ')}
  ${chips(ROOTS().map(k=>[k,CATS[k].n]),CQ.root,"crQeRoot('$v')")}
  ${kids.length?`<div class="list">
    <div class="field"><label>小分類</label><select onchange="CQ.cat=this.value;crQeRender()">${kids.map(k=>`<option value="${k}" ${CQ.cat===k?'selected':''}>${esc(CATS[k].n)}</option>`).join('')}</select></div></div>`:''}
  ${crAskHtml(CQ,CQ.cat,'crQeX')}`;
  crPanel('支払いを記録',body,{right:{label:'支払いを保存',onclick:'crQeSave()'}});
}
function crQePick(k){CQ.shop=E[k].n;crQeRender()}
function crQeSave(){
  const amt=+String(CQ.amt).replace(/[^\d]/g,'');if(!amt){toast('金額を入れてください');return}
  const cat=CQ.cat;const shop=(CQ.shop||'').trim();const time=crNowHM();const date=CQ.date||TODAY;
  const aid=crPayment({date,time,shop,register:false,items:[{n:CATS[cat].n,price:amt,tax:10,users:['me']}],pay:CQ.how,cat,fx:CQ.fx,sp:CQ.sp,act:crActResolve(CQ,date,time)});
  closeSheet();save();
  if(CQ.stay){rerender();toast('支払いを保存しました')}
  else{go('#/a/'+aid);toast('支払いを保存しました(自分の支出=全額)')}
}

/* ---------- 予定・ToDo(1枚のシート「予定」。上の切替で種類を選ぶ)(v4.13) ----------
   予定 = 日付・時刻・場所・参加者・繰り返し・通知。ToDo = 期限・リスト・優先・関係する人(entの値)。
   保存は EV / TD(PLN の見え方)へ。CREATE の「予定」は予定、「ToDo」(plan.js)は ToDo を選んだ状態で開く */
let CP=null;
function crPlNew(kind,o={}){return {kind,t:'',all:false,d:o.d||TODAY,s:'10:00',e:'11:00',place:'',who:[],rep:'なし',alarm:'なし',due:'',list:'inbox',pri:false}}
function openNewEvent(date){CP=crPlNew('予定',{d:date||TODAY});crPlRender()}
/* plan.js の openNewTask より後に読むので、こちらが使われる(plan.js 側の同名の関数は使われない) */
function openNewTask(){CP=crPlNew('ToDo');crPlRender()}
function crPlKind(v){CP.kind=v;crPlRender()}
function crPlAll(){CP.all=!CP.all;crPlRender()}
function crPlPri(){CP.pri=!CP.pri;crPlRender()}
function crPlWho(k){CP.who=CP.who.includes(k)?CP.who.filter(x=>x!==k):[...CP.who,k];crPlRender()}
function crPlPick(k){CP.place=E[k].n;crPlRender()}
function crPlRender(){
  const ev=CP.kind==='予定';
  const kind=seg([['予定','予定'],['ToDo','ToDo']],CP.kind,"crPlKind('$v')");
  const evRows=`<div class="field"><label>終日</label>${toggle(CP.all,'crPlAll()')}</div>
    <div class="field"><label>日付</label><input type="date" value="${CP.d}" oninput="CP.d=this.value"></div>
    ${CP.all?'':`<div class="field"><label>開始</label><input type="time" value="${CP.s}" oninput="CP.s=this.value"></div>
    <div class="field"><label>終了</label><input type="time" value="${CP.e}" oninput="CP.e=this.value"></div>`}
    <div class="field"><label>場所</label><input value="${esc(CP.place)}" placeholder="場所を入力" oninput="CP.place=this.value;crSugUpdate('cr-sug-ev',this.value,'crPlPick')"></div>
    <div class="cr-sug" id="cr-sug-ev">${crPlaceSug(CP.place,'crPlPick')}</div>
    <div class="field"><label>繰り返し</label><select onchange="CP.rep=this.value">${['なし','毎日','毎週','毎月'].map(r=>`<option ${CP.rep===r?'selected':''}>${r}</option>`).join('')}</select></div>
    <div class="field"><label>通知</label><select onchange="CP.alarm=this.value">${['なし','10分前','30分前','1時間前','前日'].map(r=>`<option ${CP.alarm===r?'selected':''}>${r}</option>`).join('')}</select></div>`;
  const tdRows=`<div class="field"><label>期限</label><input type="date" value="${CP.due}" oninput="CP.due=this.value"></div>
    <div class="field"><label>リスト</label><select onchange="CP.list=this.value">${Object.entries(LISTS).map(([k,L])=>`<option value="${k}" ${CP.list===k?'selected':''}>${esc(L.n)}</option>`).join('')}</select></div>
    <div class="field"><label>優先</label>${toggle(CP.pri,'crPlPri()')}</div>`;
  const body=`${kind}
  <div class="list">
    <div class="field"><label>タイトル</label><input value="${esc(CP.t)}" placeholder="${ev?'例: 花子さんとカフェ':'例: 牛乳を買う'}" oninput="CP.t=this.value"></div>
    ${ev?evRows:tdRows}
  </div>
  ${sh(ev?'参加者':'関係する人')}
  <div class="chips" role="group">${crPeople().map(([k,e])=>`<button class="chip" type="button" aria-pressed="${CP.who.includes(k)}" onclick="crPlWho('${k}')">${knot(k,'sm')}${esc(e.n)}</button>`).join('')}</div>`;
  crPanel('予定',body,{right:{label:ev?'予定を追加':'ToDoを追加',onclick:'crPlSave()'}});
}
function crPlSave(){
  if(!CP.t.trim()){toast('タイトルを入れてください');return}
  if(CP.kind==='ToDo')return crPlSaveTodo();
  const place=CP.place.trim();const pid=crFindEnt(place);const id=uid('ev');
  const rep=CP.rep==='毎月'?`毎月${+CP.d.slice(8)}日`:CP.rep==='毎週'?`毎週${wdOf(CP.d)}曜`:CP.rep==='毎日'?'毎日':undefined;
  EV[id]={t:CP.t.trim(),d:CP.d,all:CP.all?1:undefined,s:CP.all?undefined:CP.s,e:CP.all?undefined:CP.e,
    place:place?[pid,place]:undefined,who:CP.who.length?[...CP.who]:undefined,rep,alarm:CP.alarm};
  CP=null;closeSheet();save();go('#/ev/'+id);toast('予定を追加しました');
}
function crPlSaveTodo(){
  const id=uid('t');
  TD[id]={t:CP.t.trim(),list:CP.list||'inbox',...(CP.due?{due:CP.due}:{}),...(CP.pri?{pri:1}:{}),...(CP.who.length?{ent:[...CP.who]}:{})};
  CP=null;closeSheet();save();rerender();toast('ToDoを追加しました');
}

/* ---------- 出来事(期間 / 瞬間) ----------
   期間 = 種類 + 開始・終了(移動は出発地・到着地)。瞬間 = 種類 + 時刻1つ。どちらも日の kids に入る */
let CH=null;
const CH_SPAN=['食事','会話','作業','移動','買い物','その他'];
const CH_INST=['起床','就寝','出発','到着','その他'];
/** 時刻 hm に分を足す(23:59 まで) */
function crPlusMin(hm,min){const [h,m]=hm.split(':').map(Number);const t=Math.min(h*60+m+min,23*60+59);return String(Math.floor(t/60)).padStart(2,'0')+':'+String(t%60).padStart(2,'0')}
/** opt: {act:親の活動id, span:期間か(既定 true), time:開始, end:終了}。act を省くと親は時刻で決まる */
function openNewHappening(date,opt={}){const span=opt.span!==false;const t=opt.time||crNowHM();
  CH={d:date||TODAY,span,type:span?'食事':'起床',t:'',time:t,end:opt.end||crPlusMin(t,60),from:'',place:'',who:[],body:'',act:opt.act};crHpRender()}
function crHpMode(v){CH.span=v==='span';CH.type=CH.span?'食事':'起床';crHpRender()}
function crHpType(v){CH.type=v;crHpRender()}
function crHpWho(k){CH.who=CH.who.includes(k)?CH.who.filter(x=>x!==k):[...CH.who,k];crHpRender()}
function crHpRender(){
  const mv=CH.span&&CH.type==='移動';
  const mode=seg([['span','期間'],['inst','瞬間']],CH.span?'span':'inst',"crHpMode('$v')");
  const types=chips((CH.span?CH_SPAN:CH_INST).map(t=>[t,t]),CH.type,"crHpType('$v')");
  const times=CH.span
    ?`<div class="field"><label>開始</label><input type="time" value="${CH.time}" oninput="CH.time=this.value;crActSync('cr-act-hp',CH,CH.d,CH.time,'CH.act=this.value')"></div>
      <div class="field"><label>終了</label><input type="time" value="${CH.end}" oninput="CH.end=this.value"></div>`
    :`<div class="field"><label>時刻</label><input type="time" value="${CH.time}" oninput="CH.time=this.value;crActSync('cr-act-hp',CH,CH.d,CH.time,'CH.act=this.value')"></div>`;
  /* 親の活動の欄(支払いの作成と同じ仕組み)。時刻が変わったら候補の既定も変わる */
  const actRow=crActField(CH,CH.d,CH.time,'CH.act=this.value','cr-act-hp');
  const placeRows=!CH.span?'':mv
    ?`<div class="field"><label>出発地</label><input value="${esc(CH.from)}" placeholder="出発地を入力" oninput="CH.from=this.value;crSugUpdate('cr-sug-hpf',this.value,'crHpPickFrom')"></div>
      <div class="cr-sug" id="cr-sug-hpf">${crPlaceSug(CH.from,'crHpPickFrom')}</div>
      <div class="field"><label>到着地</label><input value="${esc(CH.place)}" placeholder="到着地を入力" oninput="CH.place=this.value;crSugUpdate('cr-sug-hp',this.value,'crHpPick')"></div>
      <div class="cr-sug" id="cr-sug-hp">${crPlaceSug(CH.place,'crHpPick')}</div>`
    :`<div class="field"><label>場所</label><input value="${esc(CH.place)}" placeholder="場所を入力" oninput="CH.place=this.value;crSugUpdate('cr-sug-hp',this.value,'crHpPick')"></div>
      <div class="cr-sug" id="cr-sug-hp">${crPlaceSug(CH.place,'crHpPick')}</div>`;
  const body=`${mode}${sh('種類')}${types}
  <div class="list">
    <div class="field"><label>タイトル</label><input value="${esc(CH.t)}" placeholder="例: 進路の相談" oninput="CH.t=this.value"></div>
    ${times}
    ${actRow}
    ${placeRows}
  </div>
  ${CH.span?`${mv?'':`${sh('参加者')}<div class="chips" role="group">${crPeople().map(([k,e])=>`<button class="chip" type="button" aria-pressed="${CH.who.includes(k)}" onclick="crHpWho('${k}')">${knot(k,'sm')}${esc(e.n)}</button>`).join('')}</div>`}
  ${sh('内容')}
  <div class="list"><div class="field"><textarea rows="3" placeholder="何があったか" oninput="CH.body=this.value">${esc(CH.body)}</textarea></div></div>`:''}`;
  crPanel('出来事を記録',body,{right:{label:'出来事を保存',onclick:'crHpSave()'}});
}
function crHpPick(k){CH.place=E[k].n;crHpRender()}
function crHpPickFrom(k){CH.from=E[k].n;crHpRender()}
function crHpSave(){
  const day=crDay(CH.d);const aid=uid('c');
  /* 親: 選んだ活動(期間・瞬間どちらでもよい)。無ければ日の直下 */
  const par=crActResolve(CH,CH.d,CH.time)||day;
  if(!CH.span){
    if(!CH.time){toast('時刻を入れてください');return}
    A[aid]={type:CH.type==='その他'?'出来事(瞬間)':CH.type,parent:par,time:CH.time,title:CH.type==='その他'?'出来事':CH.type,roles:[],kids:[]};
  }else{
    if(!CH.time||!CH.end){toast('開始と終了を入れてください');return}
    if(CH.end<=CH.time){toast('終了は開始より後にしてください');return}
    const roles=CH.who.map(w=>['参加',w]);
    if(CH.type==='移動'){
      const f=CH.from.trim(),t=CH.place.trim();
      if(f)roles.push(['出発',crFindEnt(f),f]);
      if(t)roles.push(['到着',crFindEnt(t),t]);
    }else{const p=CH.place.trim();if(p)roles.push(['場所',crFindEnt(p),p])}
    const tn=CH.type==='その他'?'出来事':CH.type;
    A[aid]={type:tn,parent:par,time:CH.time,end:CH.end,title:CH.t.trim()||tn,roles,body:CH.body,kids:[]};
  }
  A[par].kids.push(aid);CH=null;closeSheet();save();go('#/a/'+aid);toast('出来事を保存しました');
}

/* ---------- 活動の中に子を作る(活動の詳細の「＋ アクティビティを追加」) ----------
   作成の一覧 = 操作の並び(区切り線だけ)。親=この活動、日付・時刻は親から引き継ぐ */
function actAdd(id){const a=A[id];if(!a)return;const d=dateOf(a),end=isSpan(a)?a.end:crPlusMin(a.time,60);
  openSheet({title:'アクティビティを追加',body:list([
    row({lead:ico('pay'),t:'支払い',onclick:`closeSheet();actPayAdd('${id}')`}),
    row({lead:ico('moment'),t:'期間のアクティビティ',onclick:`closeSheet();openNewHappening('${d}',{act:'${id}',span:true,time:'${a.time}',end:'${end}'})`}),
    row({lead:ico('spark'),t:'瞬間',onclick:`closeSheet();openNewHappening('${d}',{act:'${id}',span:false,time:'${a.time}'})`})])});
}

/* ---------- 日記 ---------- */
let CD=null;
function openDiary(date){const d=date||TODAY;const f=Object.values(A).find(a=>a.type==='日記'&&a.date===d);CD={d,body:f?f.body||'':''};crDiaRender()}
function crDiaRender(){
  const body=`<div class="cr-date">${fmtMDW(CD.d)}</div>
  <div class="list"><div class="field cr-diary"><textarea rows="8" placeholder="今日はどんな日だった?" oninput="CD.body=this.value">${esc(CD.body)}</textarea></div></div>`;
  crPanel(CD.d===TODAY?'今日の日記':'日記',body,{right:{label:'日記を保存',onclick:'crDiaSave()'}});
}
function crDiaSave(){const id=crDay(CD.d);A[id].body=CD.body;CD=null;closeSheet();save();rerender();toast('日記を保存しました')}

/* ---------- エンティティ ---------- */
let CN=null;
function openNewEntity(type){CN={t:type||'person',n:'',f:{}};crEntRender()}
/** 種類の根(人物・場所・会社・物・書類・プロジェクト)の輪郭と頭文字。名前の入力のたびにこの要素だけ描き直す */
const CR_ROOTS=['person','place','company','thing','doc','project'];
function crPrev(){
  const root=typeAnc(CN.t)[0];const ini=(CN.n||'').trim()[0]||'?';
  return `<span class="kn-dot lg" aria-hidden="true"></span><small>${esc(ROOT_LABEL[root]||'')}</small>`;
}
function crPrevUpdate(){const p=document.getElementById('cr-prev');if(p)p.innerHTML=crPrev()}
function crEntRender(){
  const isP=isUnder(CN.t,'person');
  const types=CR_ROOTS.map(r=>`<optgroup label="${ROOT_LABEL[r]}">${Object.keys(TYPES).filter(k=>typeAnc(k)[0]===r).map(k=>`<option value="${k}" ${CN.t===k?'selected':''}>${'　'.repeat(typeAnc(k).length-1)}${esc(TYPES[k].n)}</option>`).join('')}</optgroup>`).join('');
  const props=propsOf(CN.t).map(([p])=>`<div class="field"><label>${esc(p)}</label><input value="${esc(CN.f[p]||'')}" placeholder="任意" oninput="CN.f['${p}']=this.value"></div>`).join('');
  const body=`<div class="cr-prev" id="cr-prev">${crPrev()}</div>
  <div class="list">
    <div class="field"><label>名前</label><input value="${esc(CN.n)}" placeholder="${isP?'例: 鈴木 一郎':isUnder(CN.t,'project')?'例: 引っ越し準備':'例: 本屋 ひだまり'}" oninput="CN.n=this.value;crPrevUpdate()"></div>
    <div class="field"><label>種類</label><select onchange="CN.t=this.value;crEntRender()">${types}</select></div>
    ${props}
  </div>`;
  crPanel('エンティティを追加',body,{right:{label:'追加',onclick:'crEntSave()'}});
}
function crEntSave(){
  const n=CN.n.trim();if(!n){toast('名前を入れてください');return}
  const facts=Object.entries(CN.f).filter(([k,v])=>v&&v.trim()).map(([k,v])=>({k,v:v.trim()}));
  const id=uid('e');E[id]={n,i:n[0],t:[CN.t],facts};
  CN=null;closeSheet();save();go('#/e/'+id);toast(`「${n}」を追加しました`);
}

/* ---------- レシートから読み取る(支払いを記録する手段の1つ。複数枚・重複判定) ---------- */
const CR_MAXR=20;
const CR_PAYS=PAY_METHODS;
/* モックの読み取り結果(実際の画像は読まない) */
const CR_POOL={
  super:{shop:'スーパー青葉台',date:TODAY,time:'16:20',items:[['牛乳',238,8],['食器用洗剤',298,10],['バナナ',158,8],['食パン',178,8],['食パン',178,8]]},
  drug:{shop:'ドラッグ桜',date:'2026-10-27',time:'20:05',items:[['洗剤',680,10],['歯ブラシ',600,10]]},
  cafe:{shop:'カフェ ミナト',date:TODAY,time:'11:10',items:[['カフェラテ',520,10],['クッキー',120,8]]},
  bakery:{shop:'ベーカリー木漏れ日',date:'2026-10-30',time:'08:05',items:[['クロワッサン',280,8],['メロンパン',220,8]]},
  conv:{shop:'コンビニ青葉台',date:TODAY,time:'13:02',items:[['おにぎり',160,8],['お茶',140,8]]},
};
const CR_POOLK=Object.keys(CR_POOL);
const crPtotal=k=>CR_POOL[k].items.reduce((s,i)=>s+i[1],0);
function crSampleSvg(k){const r=CR_POOL[k];const lines=[r.shop,r.date.replace(/-/g,'/')+' '+r.time,...r.items.map(i=>`${i[0]}  ¥${i[1]}`),`合計  ${yen(crPtotal(k))}`];
  return `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="${80+lines.length*32}"><rect width="100%" height="100%" fill="#fff"/>${lines.map((l,i)=>`<text x="${i<2?120:18}" y="${40+i*32}" font-family="monospace" font-size="${i?14:17}" ${i<2?'text-anchor="middle"':''} fill="#222">${l}</text>`).join('')}</svg>`}
const crSvgUrl=s=>'data:image/svg+xml;utf8,'+encodeURIComponent(s);
async function crSha256(buf){try{const h=await crypto.subtle.digest('SHA-256',buf);return [...new Uint8Array(h)].map(b=>b.toString(16).padStart(2,'0')).join('')}
  catch(e){let h=2166136261;const u=new Uint8Array(buf);for(const b of u){h^=b;h=Math.imul(h,16777619)>>>0}return 'fnv'+h.toString(16)}}
function crThumb(f){return new Promise(res=>{const im=new Image();im.onload=()=>{const c=document.createElement('canvas');const r=Math.min(1,360/im.width);c.width=im.width*r;c.height=im.height*r;c.getContext('2d').drawImage(im,0,0,c.width,c.height);res(c.toDataURL('image/jpeg',.7))};im.src=URL.createObjectURL(f)})}
function crToRc(k){const r=CR_POOL[k];const rc={shop:r.shop,date:r.date,time:r.time,pay:'現金',printed:crPtotal(k),register:true,with:null,
  items:r.items.map(([n,price,tax])=>({n,price,tax,users:['me']}))};rc.cat=crGuessCat(crFindEnt(rc.shop));return rc}

/** 作業中の状態。mode: batch(一覧から1枚ずつ) / single(1枚だけ) */
let crBT=[];
let CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};

function openReceipt(){
  crBT=[];CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};
  crPanel('レシートから読み取る',`<div class="cr-tiles">
    <label class="cr-tile"><input type="file" accept="image/*" capture="environment" hidden onchange="crAddFiles(this.files)">${ico('camera','var(--ev)')}<span>撮影する</span><small>1枚</small></label>
    <label class="cr-tile"><input type="file" accept="image/*" multiple hidden onchange="crAddFiles(this.files)">${ico('photo','var(--ev)')}<span>写真を選ぶ</span><small>最大${CR_MAXR}枚</small></label>
  </div>
  <div class="btnrow"><button class="btn ghost block" type="button" onclick="crSamples()">サンプルのレシートで試す</button></div>`,{full:true,footer:''});
}
async function crAddFiles(fl){
  let files=[...fl];if(!files.length)return;
  if(files.length>CR_MAXR){toast(`最大${CR_MAXR}枚まで。先頭の${CR_MAXR}枚を読み込みます`);files=files.slice(0,CR_MAXR)}
  const items=await Promise.all(files.map(async(f,i)=>{const buf=await f.arrayBuffer();return {img:await crThumb(f),hash:await crSha256(buf),pool:CR_POOLK[i%CR_POOLK.length]}}));
  crStart(items);
}
async function crSamples(){
  const keys=['super','drug','cafe','super'];
  const items=await Promise.all(keys.map(async k=>{const s=crSampleSvg(k);return {img:crSvgUrl(s),hash:await crSha256(new TextEncoder().encode(s)),pool:k}}));
  crStart(items);
}
function crStart(items){
  crBT=items.map((it,i)=>({...it,id:i,status:'scan',rc:null,dup:null,override:false,td:{}}));
  CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};
  crBatchView();
  crBT.forEach((b,i)=>setTimeout(()=>{b.rc=crToRc(b.pool);b.rc.img=b.img;crCheckDup(b);crTick()},500+i*350));
}
/** 読み取りが1件進むたび。1枚だけで重複なしなら、そのまま確認画面へ */
function crTick(){
  if(CRS.rc)return;/* 確認画面を開いている間は一覧に切り替えない */
  const b=crBT.length===1?crBT[0]:null;
  if(b&&b.status==='ready'&&!b.dup){CRS.mode='single';crOpenItem(0);return}
  crBatchView();
}
/** 重複判定: ①同じ写真(画像のハッシュ) ②同じ日時かつ同じ金額の支払い */
function crCheckDup(b){
  b.dup=null;
  if(b.override){b.status='ready';return}
  b.status='ready';
  const savedSame=Object.entries(EXTRA.hashes||{}).find(([k,h])=>h===b.hash);
  const batchSame=crBT.find(x=>x!==b&&x.id<b.id&&x.hash===b.hash&&x.status!=='skip');
  if(savedSame){b.status='dup';b.dup={msg:'同じ写真を保存済み',aid:savedSame[0]};return}
  if(batchSame){b.status='dup';b.dup={msg:`同じ写真が${batchSame.id+1}枚目にあります`};return}
  const t=b.rc.items.reduce((s,i)=>s+itemAmt(i),0);
  const same=Object.entries(A).find(([k,a])=>a.type==='支払い'&&a.amt===t&&a.time===b.rc.time&&dateOf(a)===b.rc.date);
  if(same){b.status='dup';b.dup={msg:'同じ日時・金額の支払いあり',aid:same[0]}}
}
const CR_ST={scan:['読み取り中',''],ready:['確認待ち','brand'],ok:['確認済み','ok'],dup:['重複の疑い','cr-dup'],skip:['スキップ',''],saved:['保存済み','ok']};
function crRowParts(b,i){
  const rc=b.rc;const [lab,cls]=CR_ST[b.status];
  const total=rc?rc.items.reduce((s,x)=>s+itemAmt(x),0):0;
  const openable=rc&&(b.status==='ready'||b.status==='ok'||b.status==='dup');
  const sid=rc?crFindEnt(rc.shop):null;
  const main=row({lead:`<img class="cr-th" src="${b.img}" alt="">`,
    t:rc?`<span class="cr-tn">${sid?knot(sid,'sm'):''}<span class="ell">${sid?esc(rc.shop):rc.shop?eref(null,rc.shop,'place'):'店名なし'}</span></span>`:'<span class="skel" style="display:inline-block;width:120px;height:14px"></span>',
    s:rc?`${fmtMD(rc.date)} ${esc(rc.time)}`:'',
    v:rc?`<span class="num">${yen(total)}</span>`:'',
    onclick:openable?`crOpenItem(${i})`:'',chev:openable});
  let note=`<div class="cr-note">${b.status==='dup'?`<span class="cr-wi">${IC.warn}</span>`:''}${tag(lab,{cls})}`;
  if(b.status==='dup'&&b.dup)note+=`<span>${esc(b.dup.msg)}</span>${b.dup.aid?aref(b.dup.aid):''}`;
  if(b.status==='ready'||b.status==='ok')note+=`<button class="btn sm ghost" type="button" onclick="crSkip(${i})">スキップ</button>`;
  if(b.status==='skip')note+=`<button class="btn sm ghost" type="button" onclick="crUnskip(${i})">戻す</button>`;
  note+='</div>';
  const parts=[main,note];
  if(b.status==='dup')parts.push(`<div class="cr-dupbtn"><button class="btn sm tonal" type="button" onclick="crOverride(${i})">別の支払いとして残す</button><button class="btn sm ghost" type="button" onclick="crSkip(${i})">スキップ</button></div>`);
  return parts;
}
function crBatchView(){
  const n=crBT.length,done=crBT.filter(b=>b.status!=='scan').length,ok=crBT.filter(b=>b.status==='ok').length;
  const body=`<div class="cr-prog"><div class="meter"><i style="width:${n?Math.round(done/n*100):0}%;background:var(--brand)"></i></div><span class="num">${done}/${n}枚 読み取り済み</span></div>`
    +list(crBT.flatMap((b,i)=>crRowParts(b,i)),'flat');
  crPanel('レシートから読み取る',body,{full:true,top:true,footer:`<button class="btn block" type="button" ${ok?'':'disabled'} onclick="crBatchSave()">${ok?`${ok}件の支払いを保存`:'支払いを保存'}</button>`});
}
function crSkip(i){crBT[i].status='skip';crBatchView()}
function crUnskip(i){crCheckDup(crBT[i]);crBatchView()}
function crOverride(i){const b=crBT[i];b.override=true;b.status='ready';b.dup=null;crBatchView()}

/* ---------- 1枚の確認 ---------- */
function crOpenItem(i){const b=crBT[i];if(!b||!b.rc)return;IED.cr.open=-1;CRS={rc:JSON.parse(JSON.stringify(b.rc)),idx:i,mode:CRS.mode==='single'?'single':'batch',td:{...(b.td||{})},open:false};crReview(true)}
function crTotal(){return CRS.rc.items.reduce((s,i)=>s+itemAmt(i),0)}
function crTotHtml(){const rc=CRS.rc,t=crTotal();
  return list([row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(t)}</b>`})],'flat')
    +(rc.printed===t?'':banner('warn',`印字 ${yen(rc.printed)} / 差 ${yen(Math.abs(rc.printed-t))}`));}
/* 受け取る前のレシート(rc)の負担。保存(crPayFrom)と同じ決め方: money.js の xShares / itemMine と揃える */
const crRcItems=rc=>rc.items.filter(i=>i.n&&i.price);
/* 自分の負担の決め方を聞くのは、誰かと分けていて、ほかの人が使った品目 or ほかの人が払った時だけ */
const crRcNeedBasis=rc=>!!rc.with&&((!!rc.payer&&rc.payer!=='me')||rc.items.some(i=>usersOf(i).some(u=>u!=='me')));
const crRcBasis=rc=>crRcNeedBasis(rc)?(rc.basis||'items'):'items';
/** 合計・自分の負担・貸し借り(正=貸し・負=借り)。basis: items=使った分 / even=割り勘(2人) / amount=金額指定(me) */
function crRcCalc(rc){const its=crRcItems(rc);const total=its.reduce((s,i)=>s+itemAmt(i),0);const basis=crRcBasis(rc);
  let mine;
  if(basis==='items')mine=its.reduce((s,i)=>s+itemMine(i),0);
  else if(basis==='even')mine=Math.floor(total/2);
  else mine=Math.max(0,Math.min(rc.me==null?total:rc.me,total));
  const paidByMe=(rc.payer||'me')==='me'?total:0;
  return {total,mine,adv:paidByMe-mine,basis}}
/** 受け取る前の支出の特別感: カテゴリに既定値があればそれ、無ければ入力欄で選んだ値(crAskHtml の rc.sp。crPayment と同じ) */
const rcSp=rc=>{const v=catSp(rc.cat);return v!==null?v:(rc.sp||null)};
function crBurHtml(){const rc=CRS.rc,c=crRcCalc(rc),sp=rcSp(rc);
  return ticket({cls:spCls(sp),stub:spStub(sp),t:'自分の支出',s:rc.with?balanceText({amt:c.adv,names:[whoShort(rc.with)]}):'',amt:yen(c.mine)})}
/** 金額だけ変わった時は本文を描き直さず、合計と負担だけ更新する */
function crTotUpdate(){const t=document.getElementById('cr-tot');if(t)t.innerHTML=crTotHtml();const b=document.getElementById('cr-bur');if(b)b.innerHTML=crBurHtml()}
function crItem(i,k,v){const it=CRS.rc.items[i];if(!it)return;
  if(k==='tax')it.tax=+v;else it[k]=v}
/* 品目の編集は ui.js の itemEd('cr')。入力中は式の行と合計だけ更新する */
itemEd('cr',{items:()=>CRS.rc.items,redraw:()=>crReview(),upd:i=>{const e=document.getElementById('ie-eq-cr-'+i);if(e)e.innerHTML=IED.cr.eq(i);crTotUpdate()}});
/* 使った人(品目ごと)・払った人・自分の負担・自分の金額。押したら描き直す(金額の入力だけは描き直さない) */
function crUserSet(i,w){const it=CRS.rc.items[i];if(!it)return;toggleUser(it,w);crReview()}
function crPayerSet(w){CRS.rc.payer=w;crReview()}
/* 金額を指定: 既定は自分が払った額(自分が払った奢り=合計、奢られた=0)。v4.16 */
function crBasisSet(v){const rc=CRS.rc;rc.basis=v;if(v==='amount')rc.me=(rc.payer&&rc.payer!=='me')?0:crRcCalc(rc).total;crReview()}
function crMeSet(v){CRS.rc.me=+String(v).replace(/[^\d]/g,'')||0;crTotUpdate()}
function crRvX(k,v){CRS.rc[k]=CRS.rc[k]===v?'':v;crReview()}
function crAddItem(){IED.cr.add()}
function crDelItem(i){IED.cr.del(i)}
function crShopSet(v){CRS.rc.shop=v;CRS.rc.cat=crGuessCat(crFindEnt(v));crReview()}
function crToggleReg(){CRS.rc.register=!CRS.rc.register;crReview()}
function crToggleSec(){CRS.open=!CRS.open;crReview()}
/* 同席者を変える/外す。使った人の旧い同席者は新しい人へ読み替え、外したら自分だけに戻す */
function crSetWith(v){const rc=CRS.rc,old=rc.with;rc.with=v||null;
  rc.items.forEach(it=>{const u=[...new Set(usersOf(it).map(x=>x===old?rc.with:x).filter(Boolean))];it.users=u.length?u:['me']});
  if(rc.payer!=='me'&&rc.payer!==rc.with)rc.payer='me';
  if(!rc.with)rc.basis=undefined;
  crReview()}
function crShopPick(k){crShopSet(E[k].n)}
function crTdToggle(k){CRS.td[k]=!CRS.td[k];crReview()}
function crSugHtml(){
  const names=CRS.rc.items.map(i=>i.n).filter(Boolean);
  return Object.keys(TD).filter(k=>tdOpen(k)&&TD[k].list==='shop'&&names.some(n=>n.includes(TD[k].t)||TD[k].t.includes(n)))
    .map(k=>suggest(`買い物リストの「${esc(TD[k].t)}」`,{label:CRS.td[k]?'取り消す':'完了にする',onclick:`crTdToggle('${k}')`})).join('');
}
function crSumText(){const rc=CRS.rc;return `<span class="cr-cat">${catTag(rc.cat)}</span> ${rc.with?esc(E[rc.with].n)+'と分ける':'分けない'}`}
function crReview(top){
  const rc=CRS.rc;const single=CRS.mode==='single';const b=crBT[CRS.idx];
  const shopNew=!!rc.shop.trim()&&!crFindEnt(rc.shop);
  const catOpts=Object.keys(CATS).filter(k=>CATS[k].p).map(k=>`<option value="${k}" ${rc.cat===k?'selected':''}>${esc(catPath(k))}</option>`).join('');
  const body=(!single?`<button class="cr-back" type="button" onclick="crBatchView()">${IC.back}${crBT.length}枚の一覧に戻る</button>`:'')
    +`<div class="cr-head"><img class="cr-th" src="${rc.img}" alt="レシートの写真">${b&&b.dup?`<span class="cr-wi">${IC.warn}</span>${tag('重複の疑い',{cls:'cr-dup'})}<span class="cr-dm">${esc(b.dup.msg)}</span>`:''}</div>`
    +list([
      `<div class="field"><label>店</label><input value="${esc(rc.shop)}" placeholder="店名" oninput="CRS.rc.shop=this.value;crSugUpdate('cr-sug-shop',this.value,'crShopPick')" onchange="crShopSet(this.value)"></div>`,
      `<div class="cr-sug" id="cr-sug-shop">${crPlaceSug(rc.shop,'crShopPick')}</div>`,
      `<div class="field"><label>日付</label><input type="date" value="${rc.date}" oninput="CRS.rc.date=this.value;crActSync('cr-act',CRS.rc,CRS.rc.date,CRS.rc.time,'CRS.rc.act=this.value')"></div>`,
      `<div class="field"><label>時刻</label><input type="time" value="${rc.time}" oninput="CRS.rc.time=this.value;crActSync('cr-act',CRS.rc,CRS.rc.date,CRS.rc.time,'CRS.rc.act=this.value')"></div>`,
      `<div class="field"><label>支払い方法</label><select onchange="CRS.rc.pay=this.value">${CR_PAYS.map(p=>`<option ${rc.pay===p?'selected':''}>${esc(p)}</option>`).join('')}</select></div>`,
      crActField(rc,rc.date,rc.time,'CRS.rc.act=this.value','cr-act')])
    +(shopNew?`<div class="suggest"><span class="t">${eref(null,rc.shop,'place')}を店として登録</span>${toggle(rc.register,'crToggleReg()')}</div>`:'')
    +sh('品目',rc.items.length,{label:'品目を追加',icon:'plus',onclick:'crAddItem()'})
    +IED.cr.html()
    +`<div id="cr-tot">${crTotHtml()}</div>`
    +crSugHtml()
    +`<div style="margin-top:12px">${list([row({t:'分け方・カテゴリ',s:crSumText(),chev:true,onclick:'crToggleSec()'})])}</div>`
    +crAskHtml(rc,rc.cat,'crRvX')
    +(CRS.open?`<div style="margin-top:8px">`
      +sh('誰かと分ける')+crWithChips(rc.with)
      +crSplitHtml(rc)
      +list([`<div class="field"><label>カテゴリ</label><select onchange="CRS.rc.cat=this.value;crReview()">${catOpts}</select></div>`])
      +`<div id="cr-bur">${crBurHtml()}</div></div>`:'');
  const foot=single?`<button class="btn block" type="button" onclick="crSaveSingle()">支払いを保存</button>`
    :`<button class="btn block" type="button" onclick="crConfirm()">確認済みにする</button>`;
  crPanel('レシートから読み取る',body,{full:true,top:!!top,footer:foot});
}
/** 誰かと分けた時の欄: 払った人 / 品目ごとの使った人 / 自分の負担(聞く時だけ)。誰とも分けない時は何も出さない */
function crSplitHtml(rc){
  if(!rc.with)return '';
  const pair=['me',rc.with];
  let h=list([`<div class="field"><label>払った人</label>${whoChips(pair,[rc.payer||'me'],"crPayerSet('$v')",'払った人')}</div>`]);
  h+=sh('使った人')+list(rc.items.map((it,i)=>`<div class="cr-it"><span class="cr-n ell">${esc(it.n||'品名なし')}</span><span class="cr-bur">${whoChips(pair,usersOf(it),`crUserSet(${i},'$v')`)}</span></div>`),'flat');
  if(crRcNeedBasis(rc)){
    const rows=[`<div class="field cr-basis"><label>自分の負担</label>${seg([['items','使った分'],['even','割り勘'],['amount','金額を指定']],crRcBasis(rc),"crBasisSet('$v')")}</div>`];
    if(crRcBasis(rc)==='amount')rows.push(`<div class="field"><label>自分の金額</label><input class="num" inputmode="numeric" value="${rc.me||0}" placeholder="¥0" oninput="crMeSet(this.value)"></div>`);
    h+=list(rows);
  }
  return h;
}
/** 確定: 一覧の中の1枚を確認済みにして一覧へ戻る */
function crConfirm(){
  if(!CRS.rc.items.some(i=>i.n&&i.price)){toast('品目を入れてください');return}
  const b=crBT[CRS.idx];b.rc=CRS.rc;b.td={...CRS.td};
  crCheckDup(b);if(b.status==='ready')b.status='ok';
  CRS.rc=null;crBatchView();
}
/** 1枚だけの保存 */
function crPayFrom(rc,hash,img,todo){
  const items=crRcItems(rc),c=crRcCalc(rc),who=rc.payer||'me';
  return crPayment({date:rc.date,time:rc.time,shop:rc.shop,register:rc.register,items,pay:rc.pay,with:rc.with||null,cat:rc.cat,fx:rc.fx,sp:rc.sp,hash,img,todo,act:crActResolve(rc,rc.date,rc.time),
    paid:[{who,amt:c.total,how:who==='me'?rc.pay:undefined}],basis:c.basis,me:c.basis==='amount'?(rc.me||0):undefined,n:c.basis==='even'?2:undefined});
}
function crSaveSingle(){
  const rc=CRS.rc;if(!rc.items.some(i=>i.n&&i.price)){toast('品目を入れてください');return}
  const todo=Object.keys(CRS.td).filter(k=>CRS.td[k]);
  const aid=crPayFrom(rc,crBT[0]?crBT[0].hash:null,rc.img,todo);
  crBT=[];CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};
  closeSheet();save();go('#/a/'+aid);toast('支払いを保存しました');
}
/** まとめて保存: 確認済み(ok)の分だけ作る */
function crBatchSave(){
  const list_=crBT.filter(b=>b.status==='ok');if(!list_.length)return;
  let last=null;
  list_.forEach(b=>{last=crPayFrom(b.rc,b.hash,b.img,Object.keys(b.td||{}).filter(k=>b.td[k]));b.status='saved'});
  const n=list_.length;const to=n===1?'#/a/'+last:'#/activities';
  crBT=[];CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};
  closeSheet();save();if(location.hash===to)rerender();else go(to);
  toast(`${n}件の支払いを保存しました`);
}
