/* =====================================================================
   作成フロー(中央の＋から開く): レシート・支出・予定・出来事・日記・エンティティ
   画面ルートは持たず、シートで入力して保存後に各詳細へ移動する。
   名前は cr 接頭辞。入力は oninput で状態へ書き戻し、構造が変わる時だけ描き直す。
   ===================================================================== */

if(!document.getElementById('cr-style'))document.head.insertAdjacentHTML('beforeend',`<style id="cr-style">
.cr-tiles{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:8px var(--gutter) 4px}
.cr-tile{display:flex;flex-direction:column;align-items:flex-start;justify-content:space-between;gap:12px;min-height:132px;padding:16px 14px;border-radius:var(--r-md);background:var(--surface);box-shadow:var(--e1),var(--hl);text-align:left;font:600 16px/1.3 var(--font);color:var(--ink);cursor:pointer}
.cr-tile:active{background:var(--sunk)}
.cr-tile small{font:var(--t-cap);font-weight:400;color:var(--ink-3)}
.cr-tile svg.i{width:28px;height:28px;color:var(--c-activity)}
.cr-th{width:40px;height:40px;flex:none;border-radius:var(--r-xs);object-fit:cover;background:var(--sunk);box-shadow:var(--e1)}
.cr-head{display:flex;align-items:center;gap:12px;padding:4px var(--gutter) 10px}
.cr-head .cr-th{width:56px;height:56px}
.cr-back{display:inline-flex;align-items:center;gap:4px;margin:4px var(--gutter) 6px;color:var(--brand);font:500 15px/1 var(--font);height:44px}
.cr-back svg.i{width:18px;height:18px}
.cr-prog{display:flex;flex-direction:column;gap:8px;padding:4px var(--gutter) 12px}
.cr-prog span{font:var(--t-label);color:var(--ink-2)}
.cr-note{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:-6px 14px 8px 66px;font:var(--t-cap);color:var(--ink-2)}
.cr-dupbtn{display:flex;gap:8px;padding:0 14px 12px 66px;flex-wrap:wrap}
.cr-it{display:flex;flex-wrap:wrap;align-items:center;gap:8px;min-height:52px;padding:6px 14px;position:relative}
.cr-it+.cr-it::before{content:"";position:absolute;top:0;left:14px;right:0;height:1px;background:var(--line)}
.cr-it input,.cr-it select{height:40px;min-width:0;background:transparent;font:var(--t-body);color:var(--ink);outline:none}
.cr-it .cr-n{flex:1;min-width:0}
.cr-it .cr-pr{width:96px;text-align:right;font-variant-numeric:tabular-nums}
.cr-it .cr-tx{width:70px}
.cr-it .cr-del{width:44px;height:44px;display:grid;place-items:center;color:var(--ink-3);border-radius:var(--r-xs)}
.cr-it .cr-del svg.i{width:18px;height:18px}
.cr-it .cr-bur{display:flex;gap:6px}
.cr-b{height:32px;min-width:48px;padding:0 10px;border-radius:8px;background:var(--sunk);color:var(--ink-2);font:500 13px/1 var(--font)}
.cr-b[aria-pressed="true"]{background:var(--brand-soft);color:var(--brand);font-weight:600}
.cr-sum{display:flex;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:12px 14px;font:var(--t-label);color:var(--ink-2)}
.cr-sum b{color:var(--ink);font-weight:600}
.cr-diary textarea{min-height:240px}
.cr-tot{display:flex;justify-content:space-between;align-items:center;padding:12px 14px}
</style>`);

/* ---------- 共通の小部品 ---------- */
const crDl=(id,list)=>`<datalist id="${id}">${list.map(([k,e])=>`<option value="${esc(e.n)}">`).join('')}</datalist>`;
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
/** 支払い + 自分の支出(子)を作る。品目の負担から自分の分を計算。図の画像と重複判定用ハッシュは EXTRA に入れる */
function crPayment(o){
  const day=crDay(o.date);let shopId=crFindEnt(o.shop);
  if(!shopId&&o.register&&o.shop)shopId=crNewEnt(o.shop.trim(),[o.shopType||'shop']);
  const roles=[['支払先',shopId,o.shop||'(店なし)'],['支払者','me']];if(o.with)roles.push(['同席',o.with]);
  const amt=o.items.reduce((s,i)=>s+i.price,0);
  const aid=uid('p'),xid=uid('x');
  const mine=o.items.filter(i=>i.b!=='相手').map(i=>({n:i.n,price:i.b==='半分'?Math.floor(i.price/2):i.price,b:i.b==='半分'?'1/2':'全額'}));
  const xamt=mine.reduce((s,i)=>s+i.price,0);
  const who=o.with?E[o.with].n.split(' ').pop():'相手';
  A[aid]={type:'支払い',parent:day,time:o.time,title:o.title||(o.items.length===1?o.items[0].n:'買い物'),pay:o.pay||'現金',amt,roles,
    items:o.items.map(i=>({n:i.n,price:i.price,tax:i.tax||10,b:i.b==='相手'?who:i.b})),x:xamt?xid:undefined};
  if(o.with&&amt-xamt>0)A[aid].adv=[o.with,amt-xamt,'未精算'];
  if(xamt)X[xid]={pay:aid,amt:xamt,cat:o.cat||crGuessCat(shopId),memo:'',items:mine};
  A[day].kids.push(aid);
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
    if('footer' in opt)crFooter(opt.footer);
    return;
  }
  openSheet({title,body,right:opt.right,full:opt.full});
  crFooter(opt.footer||'');
}

/* ---------- 登録 ---------- */
CREATE.push({id:'receipt',label:'レシートで支払い',sub:'撮影・写真から',cls:'activity',icon:'camera',primaryOn:['activities','ledger'],onclick:'openReceipt()'});
CREATE.push({id:'happening',label:'出来事',sub:'会話・移動など',cls:'activity',icon:'activity',primaryOn:[],onclick:"openNewHappening()"});
CREATE.push({id:'event',label:'予定',sub:'これからの予定',cls:'schedule',icon:'schedule',primaryOn:['plan'],onclick:'openNewEvent()'});
CREATE.push({id:'entity',label:'人・場所',sub:'人物・店・会社',cls:'entity',icon:'entity',primaryOn:['entities'],onclick:"openNewEntity()"});
CREATE.push({id:'diary',label:'今日の日記',sub:'',cls:'activity',icon:'diary',primaryOn:[],onclick:"openDiary(TODAY)"});

/* ---------- ＋支出(金額だけ) ---------- */
let CQ={amt:'',shop:'',date:TODAY,root:'food',cat:'eatout'};
function openQuickExpense(){CQ={amt:'',shop:'',date:TODAY,root:'food',cat:'eatout'};crQeRender()}
function crQeChildren(root){return Object.keys(CATS).filter(k=>CATS[k].p===root)}
function crQeRoot(k){CQ.root=k;const ks=crQeChildren(k);CQ.cat=ks.length?ks[0]:k;crQeRender()}
function crQeRender(){
  const kids=crQeChildren(CQ.root);
  const body=`<div class="list">
    <div class="field"><label>金額</label><input class="amt num" id="cr-qe-amt" inputmode="numeric" autofocus placeholder="¥0" value="${esc(CQ.amt)}" oninput="CQ.amt=this.value"></div>
    <div class="field"><label>日付</label><input type="date" value="${CQ.date}" oninput="CQ.date=this.value"></div>
    <div class="field"><label>店</label><input list="cr-dl-qe" value="${esc(CQ.shop)}" placeholder="候補から選ぶか入力" oninput="CQ.shop=this.value"></div>
  </div>
  ${sh('カテゴリ')}
  ${chips(ROOTS().map(k=>[k,CATS[k].n]),CQ.root,"crQeRoot('$v')")}
  ${kids.length?`<div class="list">
    <div class="field"><label>小分類</label><select onchange="CQ.cat=this.value">${kids.map(k=>`<option value="${k}" ${CQ.cat===k?'selected':''}>${esc(CATS[k].n)}</option>`).join('')}</select></div></div>`:''}
  <p class="hint">品目1つの支払いと、自分の支出を作成します</p>
  ${crDl('cr-dl-qe',crPlaces())}`;
  crPanel('支出を記録',body,{right:{label:'支出を保存',onclick:'crQeSave()'}});
}
function crQeSave(){
  const amt=+String(CQ.amt).replace(/[^\d]/g,'');if(!amt){toast('金額を入れてください');return}
  const cat=CQ.cat;const shop=(CQ.shop||'').trim();
  const aid=crPayment({date:CQ.date||TODAY,time:crNowHM(),shop,register:false,items:[{n:CATS[cat].n,price:amt,tax:10,b:'自分'}],cat,title:CATS[cat].n});
  closeSheet();save();go('#/x/'+A[aid].x);toast('支出を保存しました');
}

/* ---------- 予定 ---------- */
let CE=null;
function openNewEvent(date){CE={t:'',all:false,d:date||TODAY,s:'10:00',e:'11:00',place:'',who:[],rep:'なし',alarm:'なし'};crEvRender()}
function crEvWho(k){CE.who=CE.who.includes(k)?CE.who.filter(x=>x!==k):[...CE.who,k];crEvRender()}
function crEvAll(){CE.all=!CE.all;crEvRender()}
function crEvRender(){
  const body=`<div class="list">
    <div class="field"><label>タイトル</label><input value="${esc(CE.t)}" placeholder="例: 花子さんとカフェ" oninput="CE.t=this.value"></div>
    <div class="field"><label>終日</label>${toggle(CE.all,'crEvAll()')}</div>
    <div class="field"><label>日付</label><input type="date" value="${CE.d}" oninput="CE.d=this.value"></div>
    ${CE.all?'':`<div class="field"><label>開始</label><input type="time" value="${CE.s}" oninput="CE.s=this.value"></div>
    <div class="field"><label>終了</label><input type="time" value="${CE.e}" oninput="CE.e=this.value"></div>`}
    <div class="field"><label>場所</label><input list="cr-dl-ev" value="${esc(CE.place)}" placeholder="候補から選ぶか入力" oninput="CE.place=this.value"></div>
    <div class="field"><label>繰り返し</label><select onchange="CE.rep=this.value">${['なし','毎日','毎週','毎月'].map(r=>`<option ${CE.rep===r?'selected':''}>${r}</option>`).join('')}</select></div>
    <div class="field"><label>通知</label><select onchange="CE.alarm=this.value">${['なし','10分前','30分前','1時間前','前日'].map(r=>`<option ${CE.alarm===r?'selected':''}>${r}</option>`).join('')}</select></div>
  </div>
  ${sh('参加者')}
  <div class="chips" role="group">${crPeople().map(([k,e])=>`<button class="chip" type="button" aria-pressed="${CE.who.includes(k)}" onclick="crEvWho('${k}')">${esc(e.n)}</button>`).join('')}</div>
  ${crDl('cr-dl-ev',crPlaces())}`;
  crPanel('予定を作成',body,{right:{label:'予定を追加',onclick:'crEvSave()'}});
}
function crEvSave(){
  if(!CE.t.trim()){toast('タイトルを入れてください');return}
  const place=CE.place.trim();const pid=crFindEnt(place);const id=uid('ev');
  const rep=CE.rep==='毎月'?`毎月${+CE.d.slice(8)}日`:CE.rep==='毎週'?`毎週${wdOf(CE.d)}曜`:CE.rep==='毎日'?'毎日':undefined;
  EV[id]={t:CE.t.trim(),d:CE.d,all:CE.all?1:undefined,s:CE.all?undefined:CE.s,e:CE.all?undefined:CE.e,
    place:place?[pid,place]:undefined,who:CE.who.length?[...CE.who]:undefined,rep,alarm:CE.alarm};
  CE=null;closeSheet();save();go('#/ev/'+id);toast('予定を追加しました');
}

/* ---------- 出来事 ---------- */
let CH=null;
function openNewHappening(date){CH={d:date||TODAY,type:'会話',t:'',time:crNowHM(),place:'',who:[],body:''};crHpRender()}
function crHpType(v){CH.type=v;crHpRender()}
function crHpWho(k){CH.who=CH.who.includes(k)?CH.who.filter(x=>x!==k):[...CH.who,k];crHpRender()}
function crHpRender(){
  const mv=CH.type==='移動';
  const body=`${seg([['会話','会話'],['移動','移動'],['出来事','出来事']],CH.type,"crHpType('$v')")}
  <div class="list">
    <div class="field"><label>タイトル</label><input value="${esc(CH.t)}" placeholder="例: 進路の相談" oninput="CH.t=this.value"></div>
    <div class="field"><label>時刻</label><input type="time" value="${CH.time}" oninput="CH.time=this.value"></div>
    <div class="field"><label>${mv?'到着した場所':'場所'}</label><input list="cr-dl-hp" value="${esc(CH.place)}" placeholder="候補から選ぶか入力" oninput="CH.place=this.value"></div>
  </div>
  ${mv?'':`${sh('参加者')}<div class="chips" role="group">${crPeople().map(([k,e])=>`<button class="chip" type="button" aria-pressed="${CH.who.includes(k)}" onclick="crHpWho('${k}')">${esc(e.n)}</button>`).join('')}</div>`}
  ${sh('内容')}
  <div class="list"><div class="field"><textarea rows="3" placeholder="何があったか" oninput="CH.body=this.value">${esc(CH.body)}</textarea></div></div>
  ${crDl('cr-dl-hp',crPlaces())}`;
  crPanel('出来事を記録',body,{right:{label:'出来事を保存',onclick:'crHpSave()'}});
}
function crHpSave(){
  if(!CH.t.trim()){toast('タイトルを入れてください');return}
  const day=crDay(CH.d);const aid=uid('c');const roles=CH.who.map(w=>['参加',w]);
  const place=CH.place.trim();
  if(place)roles.push([CH.type==='移動'?'到着':'場所',crFindEnt(place),place]);
  A[aid]={type:CH.type,parent:day,time:CH.time,title:CH.t.trim(),roles,body:CH.body};
  A[day].kids.push(aid);CH=null;closeSheet();save();go('#/a/'+aid);toast('出来事を保存しました');
}

/* ---------- 日記 ---------- */
let CD=null;
function openDiary(date){const d=date||TODAY;const f=Object.values(A).find(a=>a.type==='日記'&&a.date===d);CD={d,body:f?f.body||'':''};crDiaRender()}
function crDiaRender(){
  const body=`<p class="hint" style="padding-top:0">${fmtMDW(CD.d)}</p>
  <div class="list"><div class="field cr-diary"><textarea rows="8" placeholder="今日はどんな日だった?" oninput="CD.body=this.value">${esc(CD.body)}</textarea></div></div>`;
  crPanel(CD.d===TODAY?'今日の日記':'日記',body,{right:{label:'日記を保存',onclick:'crDiaSave()'}});
}
function crDiaSave(){const id=crDay(CD.d);A[id].body=CD.body;CD=null;closeSheet();save();rerender();toast('日記を保存しました')}

/* ---------- エンティティ ---------- */
let CN=null;
function openNewEntity(type){CN={t:type||'person',n:'',f:{}};crEntRender()}
function crEntRender(){
  const isP=isUnder(CN.t,'person');
  const types=Object.keys(TYPES).map(k=>`<option value="${k}" ${CN.t===k?'selected':''}>${'　'.repeat(typeAnc(k).length-1)}${esc(TYPES[k].n)}</option>`).join('');
  const props=propsOf(CN.t).map(([p])=>`<div class="field"><label>${esc(p)}</label><input value="${esc(CN.f[p]||'')}" placeholder="任意" oninput="CN.f['${p}']=this.value"></div>`).join('');
  const body=`<div class="list">
    <div class="field"><label>名前</label><input value="${esc(CN.n)}" placeholder="${isP?'例: 鈴木 一郎':'例: 本屋 ひだまり'}" oninput="CN.n=this.value"></div>
    <div class="field"><label>種類</label><select onchange="CN.t=this.value;crEntRender()">${types}</select></div>
    ${props}
  </div>`;
  crPanel('人・場所を追加',body,{right:{label:'追加',onclick:'crEntSave()'}});
}
function crEntSave(){
  const n=CN.n.trim();if(!n){toast('名前を入れてください');return}
  const facts=Object.entries(CN.f).filter(([k,v])=>v&&v.trim()).map(([k,v])=>({k,v:v.trim()}));
  const id=uid('e');E[id]={n,i:n[0],t:[CN.t],facts};
  CN=null;closeSheet();save();go('#/e/'+id);toast(`「${n}」を追加しました`);
}

/* ---------- レシートで支払い(複数枚・重複判定) ---------- */
const CR_MAXR=20;
const CR_PAYS=['現金','カード ••1234','電子マネー'];
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
  items:r.items.map(([n,price,tax])=>({n,price,tax,b:'自分'}))};rc.cat=crGuessCat(crFindEnt(rc.shop));return rc}

/** 作業中の状態。mode: batch(一覧から1枚ずつ) / single(1枚だけ) */
let crBT=[];
let CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};

function openReceipt(){
  crBT=[];CRS={rc:null,idx:-1,mode:'batch',td:{},open:false};
  crPanel('レシートで支払い',`<div class="cr-tiles">
    <label class="cr-tile"><input type="file" accept="image/*" capture="environment" hidden onchange="crAddFiles(this.files)">${IC.camera}<span>撮影する</span><small>レシート1枚</small></label>
    <label class="cr-tile"><input type="file" accept="image/*" multiple hidden onchange="crAddFiles(this.files)">${IC.photo}<span>写真を選ぶ</span><small>最大${CR_MAXR}枚まで</small></label>
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
  const t=b.rc.items.reduce((s,i)=>s+i.price,0);
  const same=Object.entries(A).find(([k,a])=>a.type==='支払い'&&a.amt===t&&a.time===b.rc.time&&A[a.parent]&&A[a.parent].date===b.rc.date);
  if(same){b.status='dup';b.dup={msg:'同じ日時・金額の支払いあり',aid:same[0]}}
}
const CR_ST={scan:['読み取り中',''],ready:['確認待ち','brand'],ok:['確認済み','ok'],dup:['重複の疑い','warn'],skip:['スキップ',''],saved:['保存済み','ok']};
function crRowParts(b,i){
  const rc=b.rc;const [lab,cls]=CR_ST[b.status];
  const total=rc?rc.items.reduce((s,x)=>s+x.price,0):0;
  const openable=rc&&(b.status==='ready'||b.status==='ok'||b.status==='dup');
  const main=row({lead:`<img class="cr-th" src="${b.img}" alt="">`,
    t:rc?esc(rc.shop||'店名なし'):'<span class="skel" style="display:inline-block;width:120px;height:14px"></span>',
    s:rc?`${fmtMD(rc.date)} ${esc(rc.time)}`:'',
    v:rc?`<span class="num">${yen(total)}</span>`:'',
    onclick:openable?`crOpenItem(${i})`:'',chev:openable});
  let note=`<div class="cr-note">${tag(lab,{cls})}`;
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
  crPanel('レシートで支払い',body,{full:true,top:true,footer:`<button class="btn block" type="button" ${ok?'':'disabled'} onclick="crBatchSave()">確認済みをまとめて保存 (${ok}件)</button>`});
}
function crSkip(i){crBT[i].status='skip';crBatchView()}
function crUnskip(i){crCheckDup(crBT[i]);crBatchView()}
function crOverride(i){const b=crBT[i];b.override=true;b.status='ready';b.dup=null;crBatchView()}

/* ---------- 1枚の確認 ---------- */
function crOpenItem(i){const b=crBT[i];if(!b||!b.rc)return;CRS={rc:JSON.parse(JSON.stringify(b.rc)),idx:i,mode:CRS.mode==='single'?'single':'batch',td:{...(b.td||{})},open:false};crReview(true)}
function crTotal(){return CRS.rc.items.reduce((s,i)=>s+(i.price||0),0)}
function crTotHtml(){const rc=CRS.rc,t=crTotal();
  return list([row({cls:'plain',t:'<b>合計</b>',v:`<b class="num">${yen(t)}</b>`})],'flat')
    +(rc.printed===t?`<p class="hint">印字の合計と一致</p>`:banner('warn',`印字の合計 ${yen(rc.printed)} と ${yen(Math.abs(rc.printed-t))} ずれています`));}
/** 負担の計算: 自分 = 自分の品目 + 半分(切り捨て)。立替 = 合計 - 自分 */
function crBurCalc(){const rc=CRS.rc;const total=crTotal();let mine=0;
  rc.items.forEach(it=>{const p=it.price||0;if(it.b==='相手')return;mine+=it.b==='半分'?Math.floor(p/2):p});
  return {total,mine,adv:total-mine}}
function crBurHtml(){const c=crBurCalc();return `<div class="cr-sum"><span>自分の負担 <b class="num">${yen(c.mine)}</b></span>${CRS.rc.with?`<span>立替 <b class="num">${yen(c.adv)}</b></span>`:''}</div>`}
/** 金額だけ変わった時は本文を描き直さず、合計と負担だけ更新する */
function crTotUpdate(){const t=document.getElementById('cr-tot');if(t)t.innerHTML=crTotHtml();const b=document.getElementById('cr-bur');if(b)b.innerHTML=crBurHtml()}
function crItem(i,k,v){const it=CRS.rc.items[i];if(!it)return;
  if(k==='price'){it.price=+String(v).replace(/[^\d]/g,'')||0;crTotUpdate();return}
  if(k==='tax')it.tax=+v;else it[k]=v}
function crBurSet(i,v){crItem(i,'b',v);crReview()}
function crAddItem(){CRS.rc.items.push({n:'',price:0,tax:8,b:'自分'});crReview();setTimeout(()=>{const ns=document.querySelectorAll('.cr-it input.cr-n');if(ns.length)ns[ns.length-1].focus()},30)}
function crDelItem(i){CRS.rc.items.splice(i,1);crReview()}
function crShopSet(v){CRS.rc.shop=v;CRS.rc.cat=crGuessCat(crFindEnt(v));crReview()}
function crToggleReg(){CRS.rc.register=!CRS.rc.register;crReview()}
function crToggleSec(){CRS.open=!CRS.open;crReview()}
function crSetWith(v){CRS.rc.with=v||null;if(!v)CRS.rc.items.forEach(i=>i.b='自分');crReview()}
function crTdToggle(k){CRS.td[k]=!CRS.td[k];crReview()}
function crSugHtml(){
  const names=CRS.rc.items.map(i=>i.n).filter(Boolean);
  return Object.keys(TD).filter(k=>tdOpen(k)&&TD[k].list==='shop'&&names.some(n=>n.includes(TD[k].t)||TD[k].t.includes(n)))
    .map(k=>suggest(`買い物リストの「${esc(TD[k].t)}」`,{label:CRS.td[k]?'取り消す':'完了にする',onclick:`crTdToggle('${k}')`})).join('');
}
function crSumText(){const rc=CRS.rc;return `${esc(catPath(rc.cat))} · ${rc.with?esc(E[rc.with].n)+'と分ける':'分けない'}`}
function crReview(top){
  const rc=CRS.rc;const single=CRS.mode==='single';const b=crBT[CRS.idx];
  const shopNew=!!rc.shop.trim()&&!crFindEnt(rc.shop);
  const catOpts=Object.keys(CATS).filter(k=>CATS[k].p).map(k=>`<option value="${k}" ${rc.cat===k?'selected':''}>${esc(catPath(k))}</option>`).join('');
  const burBtns=i=>['自分','半分','相手'].map(v=>`<button class="cr-b" type="button" aria-pressed="${rc.items[i].b===v}" onclick="crBurSet(${i},'${v}')">${v}</button>`).join('');
  const body=(!single?`<button class="cr-back" type="button" onclick="crBatchView()">${IC.back}${crBT.length}枚の一覧に戻る</button>`:'')
    +`<div class="cr-head"><img class="cr-th" src="${rc.img}" alt="レシートの写真"><span class="hint" style="padding:0">読み取り結果。違うところだけ直す</span></div>`
    +(b&&b.dup?banner('warn',`重複の疑い: ${esc(b.dup.msg)}`):'')
    +list([
      `<div class="field"><label>店</label><input value="${esc(rc.shop)}" list="cr-dl-shop" placeholder="店名" oninput="CRS.rc.shop=this.value" onchange="crShopSet(this.value)"></div>`,
      `<div class="field"><label>日付</label><input type="date" value="${rc.date}" oninput="CRS.rc.date=this.value"></div>`,
      `<div class="field"><label>時刻</label><input type="time" value="${rc.time}" oninput="CRS.rc.time=this.value"></div>`,
      `<div class="field"><label>支払い方法</label><select onchange="CRS.rc.pay=this.value">${CR_PAYS.map(p=>`<option ${rc.pay===p?'selected':''}>${esc(p)}</option>`).join('')}</select></div>`])
    +(shopNew?`<div class="suggest"><span class="t">「${esc(rc.shop)}」を店として登録</span>${toggle(rc.register,'crToggleReg()')}</div>`:'')
    +sh('品目',rc.items.length,{label:'品目を追加',icon:'plus',onclick:'crAddItem()'})
    +list(rc.items.map((it,i)=>`<div class="cr-it"><input class="cr-n" value="${esc(it.n)}" placeholder="品名" oninput="crItem(${i},'n',this.value)"><input class="cr-pr num" inputmode="numeric" value="${it.price||''}" placeholder="¥0" oninput="crItem(${i},'price',this.value)"><select class="cr-tx" aria-label="税率" onchange="crItem(${i},'tax',this.value)"><option value="8" ${it.tax===8?'selected':''}>8%</option><option value="10" ${it.tax===10?'selected':''}>10%</option></select><button class="cr-del" type="button" aria-label="品目を削除" onclick="crDelItem(${i})">${IC.close}</button></div>`),'flat')
    +`<div id="cr-tot">${crTotHtml()}</div>`
    +crSugHtml()
    +`<div style="margin-top:12px">${list([row({t:'分け方・カテゴリ',s:crSumText(),chev:true,onclick:'crToggleSec()'})])}</div>`
    +(CRS.open?`<div style="margin-top:8px">`
      +list([
        `<div class="field"><label>誰かと分ける</label><select onchange="crSetWith(this.value)"><option value="">分けない</option>${crPeople().map(([k,e])=>`<option value="${k}" ${rc.with===k?'selected':''}>${esc(e.n)}</option>`).join('')}</select></div>`,
        `<div class="field"><label>カテゴリ</label><select onchange="CRS.rc.cat=this.value">${catOpts}</select></div>`])
      +(rc.with?sh('品目ごとの負担')+list(rc.items.map((it,i)=>`<div class="cr-it"><span class="cr-n ell">${esc(it.n||'品名なし')}</span><span class="cr-bur">${burBtns(i)}</span></div>`),'flat'):'')
      +`<div id="cr-bur">${crBurHtml()}</div></div>`:'')
    +crDl('cr-dl-shop',crPlaces());
  const foot=single?`<button class="btn block" type="button" onclick="crSaveSingle()">支払いを保存</button>`
    :`<button class="btn block" type="button" onclick="crConfirm()">確定</button>`;
  crPanel('レシートで支払い',body,{full:true,top:!!top,footer:foot});
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
  const items=rc.items.filter(i=>i.n&&i.price);
  return crPayment({date:rc.date,time:rc.time,shop:rc.shop,register:rc.register,items,pay:rc.pay,with:rc.with||null,cat:rc.cat,hash,img,todo});
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
