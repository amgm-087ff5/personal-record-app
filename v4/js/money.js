/* v4.16 お金の土台(2026-11-08 ユーザー指示)
   支払い = アクティビティの事実 / 支出 = 家計簿のエントリ。同じことを2回入力しない。計算で出せるものは保存しない。

   支払い A[p] (type:'支払い')
     amt   : 支払いの合計(品目があれば品目の合計と同じ)
     paid  : [{who, amt, how}]  誰がいくら払ったか。who = 'me' | エンティティid | null(名前だけ: name)
     items : [{n, price, lines?, tax, users:[who…], memo?, unk?}]  品目と「使った人」(既定 ['me'])
     settled: 1  貸し借りを精算済み(無ければ未精算)
     x     : 自分の支出のid(自分の負担が0なら無い)
   支出 X[x]
     pay   : 支払いのid
     basis : 自分の負担の決め方 'items'(使った分) | 'even'(人数で割り勘) | 'amount'(金額を指定。奢りなど)
     n     : basis='even' の人数
     me    : basis='amount' の自分の金額
     cat   : 既定のカテゴリ / itemCat: {品目の番号: カテゴリ}(品目ごとに変える時だけ)
     sp・fx・memo: 特別感・固定/変動(都度指定のカテゴリだけ)・メモ
   計算で出すもの(保存しない): 自分の負担 xAmt / 品目ごとの自分の額 xShares / カテゴリ配分 xCats / 貸し借り balanceOf
   互換: x.amt・x.items・a.adv・a.pay は読み取り専用の計算値(保存されない)。新しいコードは関数を使う */

/* 支払い方法(v4.17)。レシートは支払い方法ではなく、支払いを記録する手段の1つ(読み取り) */
const PAY_METHODS=['現金','クレジットカード ••1234','Suica','QRコード決済','デビットカード','銀行振込'];
const payMethodSelect=(cur,onchange)=>`<select onchange="${onchange}">${[...new Set([...PAY_METHODS,...(cur?[cur]:[])])].map(m=>`<option ${m===cur?'selected':''}>${esc(m)}</option>`).join('')}</select>`;
const BASIS={items:'使った分',even:'割り勘',amount:'金額を指定'};
/* who: 'me' = 自分 / エンティティid / 'other' = 名前の無いほかの人 */
const whoName=(w,name)=>w==='me'?'自分':w&&E[w]?E[w].n:(name||'ほかの人');
const whoShort=(w,name)=>w==='me'?'自分':w&&E[w]?E[w].n.split(' ').pop():(name||'ほかの人');
const usersOf=it=>it&&it.users&&it.users.length?it.users:['me'];
/** 支払いの品目(無ければ合計1行として扱う) */
const payItems=a=>a&&a.items&&a.items.length?a.items:[{n:'合計',price:a?a.amt||0:0,users:['me'],virtual:1}];
const payTotal=a=>a?(a.items&&a.items.length?a.items.reduce((s,i)=>s+itemAmt(i),0):(a.amt||0)):0;
/** 誰がいくら払ったか(無ければ自分が全額) */
const paidOf=a=>a&&a.paid&&a.paid.length?a.paid:[{who:'me',amt:a?a.amt||0:0,how:'現金'}];
const myPaid=a=>paidOf(a).filter(p=>p.who==='me').reduce((s,p)=>s+(p.amt||0),0);
const payMethod=a=>{const m=paidOf(a).filter(p=>p.who==='me').map(p=>p.how).filter(Boolean);return m.join('・')};
/** この支払いに関わった人(自分を含む。使った人・払った人・親の活動の同席者) */
function peopleOf(a){const s=new Set(['me']);
  payItems(a).forEach(i=>usersOf(i).forEach(u=>s.add(u)));paidOf(a).forEach(p=>{if(p.who)s.add(p.who)});
  const pa=a&&parentAct(a);[...(a&&a.roles||[]),...(pa&&pa.roles||[])].forEach(r=>{if(['同席','参加','一緒に'].includes(r[0])&&r[1])s.add(r[1])});
  return [...s]}
/** 品目のうち自分が使った額(使った人数で割る。端数は切り捨て) */
const itemMine=it=>{const u=usersOf(it);return u.includes('me')?Math.floor(itemAmt(it)/u.length):0};
/** 品目ごとの自分の額。basis に従う。比例で割る時は端数を最後の品目に寄せる */
function xShares(x){
  const a=A[x.pay];if(!a)return [];
  const its=payItems(a),amts=its.map(itemAmt),total=amts.reduce((s,v)=>s+v,0);
  if((x.basis||'items')==='items')return its.map(itemMine);
  const me=x.basis==='amount'?Math.max(0,Math.min(x.me||0,total)):Math.floor(total/Math.max(1,x.n||2));
  let left=me;
  return amts.map((v,i)=>{const s=i===amts.length-1?left:Math.min(left,total?Math.floor(v*me/total):0);left-=s;return s});
}
const xAmt=x=>xShares(x).reduce((s,v)=>s+v,0);
/** 自分の支出の品目(表示用。支払いの品目を参照し、自分の額だけ持つ)。b = 全額 / 1/2 / 按分 など */
function xItems(x){
  const a=A[x.pay];if(!a)return [];
  const its=payItems(a),sh=xShares(x),basis=x.basis||'items';
  return its.map((it,i)=>{const full=itemAmt(it),u=usersOf(it);
    /* 品目の自分の額の言い方: 全額 / 1/2(使った人数で割った) / 按分(割り勘・金額の指定を比で分けた) / 奢り(自分は使っていないが自分が負担) */
    const b=!u.includes('me')&&sh[i]>0?'奢り':basis==='items'?(sh[i]===full?'全額':`1/${u.length}`):(sh[i]>=full?'全額':'按分');
    const o={i,n:it.n,price:sh[i],full,b,cat:(x.itemCat&&x.itemCat[i])||x.cat};if(it.memo)o.memo=it.memo;if(it.unk)o.unk=1;return o}).filter(o=>o.price>0);
}
/** カテゴリ配分: [{cat, amt, idx:[品目の番号]}]。既定のカテゴリが先 */
function xCats(x){const m=new Map();
  xItems(x).forEach(o=>{if(!m.has(o.cat))m.set(o.cat,{cat:o.cat,amt:0,idx:[]});const g=m.get(o.cat);g.amt+=o.price;g.idx.push(o.i)});
  if(!m.size)m.set(x.cat,{cat:x.cat,amt:0,idx:[]});
  return [...m.values()].sort((p,q)=>(p.cat===x.cat?-1:0)-(q.cat===x.cat?-1:0))}
/** カテゴリ k に計上した自分の額(家計簿の集計用。子カテゴリも含めたい時は under=1) */
const xAmtIn=(x,k,under)=>xCats(x).filter(g=>under?catAnc(g.cat).includes(k):g.cat===k).reduce((s,g)=>s+g.amt,0);
/** 自分の負担(支出が無ければ0) */
const mineOf=a=>a&&a.x&&X[a.x]?xAmt(X[a.x]):0;
/** 貸し借り: amt>0 = 相手に貸し(自分が多く払った) / amt<0 = 借り。with = 相手(エンティティid の配列) */
function balanceOf(a){
  const amt=myPaid(a)-mineOf(a);
  let w;if(amt>0){const used=new Set();payItems(a).forEach(i=>usersOf(i).forEach(u=>{if(u!=='me')used.add(u)}));w=[...used];if(!w.length)w=peopleOf(a).filter(p=>p!=='me')}
  else w=paidOf(a).filter(p=>p.who!=='me').map(p=>p.who);
  return {amt,with:w.filter(v=>v&&E[v]),names:w.map(v=>whoShort(v)),settled:!!a.settled,open:amt!==0&&!a.settled};
}
/** 未精算の支払い一覧 [[id, a, bal]] */
const openBalances=()=>Object.entries(A).filter(([k,a])=>a.type==='支払い').map(([k,a])=>[k,a,balanceOf(a)]).filter(([,,b])=>b.open);
/** 自分の負担の既定の決め方。全部自分が使ったなら items(聞かない)。ほかの人が使った品目があれば null(1回だけ聞く) */
const basisGuess=a=>payItems(a).every(i=>usersOf(i).length===1&&usersOf(i)[0]==='me')?'items':null;

/** 支払い + 自分の支出を作る(作成の入口はすべてここを通る)
   o: {parent, time, amt?, items?, paid?, payee:[id,name], roles?, basis?, me?, n?, cat, fx?, sp?, memo?} → 支払いのid */
function makePayment(o){
  const aid=uid('p');
  const items=(o.items||[]).map(i=>{const r={n:i.n,price:itemAmt(i),tax:i.tax||10,users:(i.users&&i.users.length?i.users:['me']).slice()};const ln=typeof lnSave==='function'?lnSave(i):null;if(ln)r.lines=ln;if(i.memo)r.memo=i.memo;if(i.unk)r.unk=1;return r});
  const amt=items.length?items.reduce((s,i)=>s+itemAmt(i),0):(o.amt||0);
  const roles=[['支払先',o.payee&&o.payee[0]||null,o.payee&&o.payee[1]||''],...(o.roles||[])];
  A[aid]={type:'支払い',parent:o.parent,time:o.time,title:'支払い',amt,roles,items,paid:(o.paid&&o.paid.length?o.paid:[{who:'me',amt,how:o.how||'現金'}]).map(p=>({...p}))};
  if(A[o.parent])(A[o.parent].kids=A[o.parent].kids||[]).push(aid);
  const xid=uid('x'),x={pay:aid,basis:o.basis||basisGuess(A[aid])||'items',cat:o.cat,memo:o.memo||''};
  if(x.basis==='amount')x.me=o.me||0;if(x.basis==='even')x.n=o.n||2;
  if(o.fx&&catFx(o.cat)===null)x.fx=o.fx;if(o.sp&&catSp(o.cat)===null)x.sp=o.sp;
  X[xid]=x;
  if(xAmt(x)>0)A[aid].x=xid;else delete X[xid];
  moneyInstall();
  return aid;
}
/** 自分の支出を作る/消す(負担が0になったら支出は消える。0より大きくなれば作る) */
function xEnsure(aid,base={}){
  const a=A[aid];if(!a)return null;
  if(!a.x||!X[a.x]){const xid=uid('x');X[xid]={pay:aid,basis:'items',cat:base.cat||'eatout',memo:'',...base};a.x=xid}
  const x=X[a.x];
  if(xAmt(x)<=0&&!base.keep){delete X[a.x];delete a.x;return null}
  moneyInstall();return x;
}

/* ---- 旧データ(v4.15 まで)からの移行: b(自分/半分/相手) → users、支払者・adv → paid・settled、X.items → basis ---- */
function moneyMigrate(){
  Object.entries(A).forEach(([k,a])=>{
    if(a.type!=='支払い')return;
    const adv=Object.getOwnPropertyDescriptor(a,'adv');const oldAdv=adv&&'value' in adv?adv.value:null;
    const partner=oldAdv&&oldAdv[0];
    (a.items||[]).forEach(it=>{if(it.users)return;
      const b=it.b;let u=['me'];
      if(b==='半分')u=['me',partner||'other'];else if(b&&b!=='自分'&&b!=='全額')u=[partner||Object.keys(E).find(id=>E[id].n.split(' ').pop()===b)||'other'];
      it.users=u;
      delete it.b});
    const pd=Object.getOwnPropertyDescriptor(a,'pay');
    if(!a.paid){const how=pd&&'value' in pd?pd.value:'現金';a.paid=[{who:'me',amt:a.amt||0,how}]}
    if(pd&&'value' in pd)delete a.pay;
    if(a.roles)a.roles=a.roles.filter(r=>r[0]!=='支払者');
    if(oldAdv){if(oldAdv[2]&&oldAdv[2]!=='未精算')a.settled=1;delete a.adv}
  });
  Object.values(X).forEach(x=>{
    const d=Object.getOwnPropertyDescriptor(x,'items');
    if(d&&'value' in d){if(!x.basis){const a=A[x.pay];const want=d.value.reduce((s,i)=>s+(i.price||0),0);x.basis='items';
        if(a&&xAmt(x)!==want){x.basis='amount';x.me=want}}
      delete x.items}
    const da=Object.getOwnPropertyDescriptor(x,'amt');if(da&&'value' in da){if(!x.basis){x.basis='amount';x.me=da.value}delete x.amt}
    if(!x.basis)x.basis='items';
  });
}
/* ---- 互換の読み取り専用値(保存されない)。古い画面のコードが壊れないように ---- */
function moneyInstall(){
  Object.values(X).forEach(x=>{
    for(const [k,get] of [['amt',function(){return xAmt(this)}],['items',function(){return xItems(this)}]]){
      const d=Object.getOwnPropertyDescriptor(x,k);if(d&&!d.get)delete x[k];
      if(!d||!d.get)Object.defineProperty(x,k,{get,enumerable:false,configurable:true})}
  });
  Object.values(A).forEach(a=>{if(a.type!=='支払い')return;
    for(const [k,get] of [['adv',function(){const b=balanceOf(this);return b.amt>0?[b.with[0]||null,b.amt,this.settled?'精算済み':'未精算']:undefined}],['pay',function(){return payMethod(this)}]]){
      const d=Object.getOwnPropertyDescriptor(a,k);if(d&&!d.get)delete a[k];
      if(!d||!d.get)Object.defineProperty(a,k,{get,enumerable:false,configurable:true})}
  });
}

/* ---- 共通の部品(どの画面でも同じ見た目・同じ言葉) ---- */
/** 使った人・払った人のチップ(複数選べる)。people = 候補(自分を含む)、sel = 選んでいる人、fn = 押した時の JS(その人のidを $v に入れる) */
const whoChips=(people,sel,fn,label='使った人')=>`<div class="who-c" role="group" aria-label="${esc(label)}">${people.map(w=>`<button class="cr-b" type="button" aria-pressed="${sel.includes(w)}" onclick="${fn.replace(/\$v/g,w)}">${esc(whoShort(w))}</button>`).join('')}</div>`;
/** 使った人の短い文(「自分」「花子」「自分・花子」) */
const usersText=it=>usersOf(it).map(u=>whoShort(u)).join('・');
/** 貸し借りの文(「花子さんに貸し ¥2,340」「健さんに借り ¥2,840」)。無ければ '' */
function balanceText(b){if(!b||!b.amt)return '';const who=b.names.length?b.names.map(n=>n==='ほかの人'?n:n+'さん').join('・'):'相手';
  return `${who}に${b.amt>0?'貸し':'借り'} ${yen(Math.abs(b.amt))}`}
/** 品目の「使った人」を切り替える(自分だけは外せない…ではなく、最後の1人は外せない) */
function toggleUser(it,w){const u=usersOf(it).slice();const i=u.indexOf(w);if(i>=0){if(u.length>1)u.splice(i,1)}else u.push(w);it.users=u}
