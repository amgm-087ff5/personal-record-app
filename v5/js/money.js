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
   互換: x.amt・x.items・a.adv・a.pay は読み取り専用の計算値(保存されない)。新しいコードは関数を使う

   【v5 で足した関数】(戻り値は {ok, undo} / {ok:false, why}。undo は undoRun(undo) で戻す)
     itemRemove(支払いid, 品目の番号) → {ok, undo}      品目を消す(最後の1件も可)。支出の品目別カテゴリの番号はずらす。合計と払った額を合わせる
     itemRestore(undo) = undoRun
     itemTax(品目) → 8 | 10                             税率(無ければ TAX_DEFAULT=10)
     xSetCat(支出id, カテゴリ) → {ok, undo}
     xSetFx(支出id, '固定'|'変動'|null) → {ok, undo}    null や既定と同じ値は上書きを外す
     xSetSp(支出id, '日常'|'非日常'|null) → {ok, undo}  同上
     xSetMemo(支出id, 文字) → {ok, undo}
     xItemCatSet(支出id, 品目の番号, カテゴリ|null) → {ok, undo}   既定と同じ・null は上書きを外す
     readMarkSet(支払いid, 'amt'|'payee'|'item', 品目の番号?, on=true) → {ok, undo}   読み取った値の「未確認」
     readMarkConfirm(支払いid) → {ok, undo}              確定: 未確認を全部消す
     readMarkList(支払いid) → {amt, payee, items:[番号…]}   表示用
     readMarkCount(支払いid) → number
     spendWith(縁id) → number                            一緒の時の自分の支出(その縁が関わる活動と子孫の支払いの自分の負担。二重に数えない)
     spendWithParts(縁id) → [{aid, amt}]                 その内訳(上の活動ごと。0は出さない)
     makePayment(o) は o.unread(読み取りの未確認) と o.items[i].unread を受け取るようになった */

/** 税率の既定(品目の税率が無い時)。画面ごとに違う値を使わない */
const TAX_DEFAULT=10;
/** 品目の税率(無ければ既定) */
const itemTax=it=>(it&&it.tax)||TAX_DEFAULT;

/* ---- 金額行(品目の値段・オプション・割引)。v4 の ui.js から移した計算部分(画面の HTML は含まない) ---- */
const lnV=l=>l.sg!=null?l.sg*(l.v||0):(l.v||0);
const lnAmts=lines=>{let s=0;return lines.map((l,j)=>{const a=l.k==='pct'?(j?Math.round(s*lnV(l)/100):0):lnV(l);s+=a;return a})};
/** 品目の金額(行があれば行の計算、無ければ price) */
const itemAmt=it=>it.lines&&it.lines.length?lnAmts(it.lines).reduce((s,a)=>s+a,0):(it.price||0);
const lnName=l=>l.n||(lnV(l)<0?'割引':'オプション');
/** 保存形の行(2行以上のときだけ持つ。0の追加行は捨てる) */
const lnSave=it=>{if(!(it.lines&&it.lines.length>1))return undefined;const r=it.lines.map((l,j)=>({n:j?lnName(l):'',k:l.k||'amt',v:lnV(l)})).filter((l,j)=>j===0||l.v!==0);return r.length>1?r:undefined};

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
  const items=(o.items||[]).map(i=>{const r={n:i.n,price:itemAmt(i),tax:i.tax||TAX_DEFAULT,users:(i.users&&i.users.length?i.users:['me']).slice()};const ln=lnSave(i);if(ln)r.lines=ln;if(i.memo)r.memo=i.memo;if(i.unk)r.unk=1;if(i.unread)r.unread=1;return r});
  const amt=items.length?items.reduce((s,i)=>s+itemAmt(i),0):(o.amt||0);
  const roles=[['支払先',o.payee&&o.payee[0]||null,o.payee&&o.payee[1]||''],...(o.roles||[])];
  A[aid]={type:'支払い',parent:o.parent,time:o.time,title:'支払い',amt,roles,items,paid:(o.paid&&o.paid.length?o.paid:[{who:'me',amt,how:o.how||'現金'}]).map(p=>({...p}))};
  if(o.unread&&Object.keys(o.unread).length)A[aid].unread={...o.unread};
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

/* ================= v5 お金の関数(仕様は先頭の一覧) ================= */

/** 払った額の内訳を diff 分だけ減らす(後ろの人から。diff が負なら最後の人に足す) */
function paidShift(paid,diff){
  const out=paid.map(p=>({...p}));if(!out.length)return out;
  if(diff>=0){let left=diff;for(let i=out.length-1;i>=0&&left>0;i--){const take=Math.min(left,out[i].amt||0);out[i].amt=(out[i].amt||0)-take;left-=take}}
  else out[out.length-1].amt=(out[out.length-1].amt||0)-diff;
  return out;
}
/** 支払いの品目を消す。最後の1件でも可(残りが無ければ合計は a.amt のまま1行として扱う)。
    残りがあれば合計を残りの品目の合計に合わせ、払った額が合計と同じだった時は払った額も減らす。
    支出の品目別カテゴリ(itemCat)の番号は後ろの分をずらす。自分の負担が0になれば支出も消す */
function itemRemove(aid,ix){
  const a=A[aid];if(!a||a.type!=='支払い')return notOk('支払いが見つかりません');
  const its=a.items||[];if(!Number.isInteger(ix)||ix<0||ix>=its.length)return notOk('品目が見つかりません');
  const log=[],oldAmt=a.amt||0,rest=its.filter((_,i)=>i!==ix);
  const paidSum=(a.paid||[]).reduce((s,p)=>s+(p.amt||0),0);
  logSet(log,a,'items',rest);
  if(rest.length){
    const newAmt=rest.reduce((s,i)=>s+itemAmt(i),0);
    if(newAmt!==oldAmt){
      if(a.paid&&a.paid.length&&paidSum===oldAmt)logSet(log,a,'paid',paidShift(a.paid,oldAmt-newAmt));
      logSet(log,a,'amt',newAmt);
    }
  }
  const x=a.x&&X[a.x];
  if(x){
    if(x.itemCat){const nic={};Object.entries(x.itemCat).forEach(([k,v])=>{const n=+k;if(n===ix)return;nic[n>ix?n-1:n]=v});
      if(Object.keys(nic).length)logSet(log,x,'itemCat',nic);else logDel(log,x,'itemCat')}
    if(xAmt(x)<=0){logDel(log,X,a.x);logDel(log,a,'x')}
  }
  save();return okUndo(log,'品目を消した');
}
const itemRestore=undoRun;

/* 支出の変更。カテゴリ・固定/変動・特別感・メモ・品目ごとのカテゴリ */
function xSetCat(xid,cat){const x=X[xid];if(!x)return notOk('支出が見つかりません');if(!CATS[cat])return notOk('カテゴリが見つかりません');const log=[];logSet(log,x,'cat',cat);save();return okUndo(log,'カテゴリを変えた')}
/** 固定/変動。null か、カテゴリの既定と同じ値は上書きを外す(既定に戻る) */
function xSetFx(xid,fx){const x=X[xid];if(!x)return notOk('支出が見つかりません');if(fx!=null&&fx!=='固定'&&fx!=='変動')return notOk('固定か変動を選んでください');const log=[];if(fx==null||fx===catFx(x.cat))logDel(log,x,'fx');else logSet(log,x,'fx',fx);save();return okUndo(log,'固定・変動を変えた')}
/** 特別感。null か、カテゴリの既定と同じ値は上書きを外す */
function xSetSp(xid,sp){const x=X[xid];if(!x)return notOk('支出が見つかりません');if(sp!=null&&sp!=='日常'&&sp!=='非日常')return notOk('日常か非日常を選んでください');const log=[];if(sp==null||sp===catSp(x.cat))logDel(log,x,'sp');else logSet(log,x,'sp',sp);save();return okUndo(log,'特別感を変えた')}
function xSetMemo(xid,memo){const x=X[xid];if(!x)return notOk('支出が見つかりません');const log=[];logSet(log,x,'memo',String(memo||''));save();return okUndo(log,'メモを直した')}
/** 品目ごとのカテゴリ。cat が null か既定と同じなら上書きを外す */
function xItemCatSet(xid,ix,cat){
  const x=X[xid];if(!x)return notOk('支出が見つかりません');
  const a=A[x.pay];const n=(a&&a.items&&a.items.length)||1;
  if(!Number.isInteger(ix)||ix<0||ix>=n)return notOk('品目が見つかりません');
  if(cat!=null&&!CATS[cat])return notOk('カテゴリが見つかりません');
  const ic={...(x.itemCat||{})};if(cat==null||cat===x.cat)delete ic[ix];else ic[ix]=cat;
  const log=[];if(Object.keys(ic).length)logSet(log,x,'itemCat',ic);else logDel(log,x,'itemCat');
  save();return okUndo(log,'品目のカテゴリを変えた');
}

/* 読み取った値の「未確認」。amt(金額)・payee(支払先)は支払いに、item(品目)は品目に付く。確定で全部消える */
function readMarkSet(aid,field,ix,on=true){
  const a=A[aid];if(!a)return notOk('支払いが見つかりません');const log=[];
  if(field==='item'){const it=(a.items||[])[ix];if(!it)return notOk('品目が見つかりません');if(on)logSet(log,it,'unread',1);else logDel(log,it,'unread')}
  else if(field==='amt'||field==='payee'){const u={...(a.unread||{})};if(on)u[field]=1;else delete u[field];if(Object.keys(u).length)logSet(log,a,'unread',u);else logDel(log,a,'unread')}
  else return notOk('amt・payee・item のどれかです');
  save();return okUndo(log,'未確認を変えた');
}
function readMarkConfirm(aid){const a=A[aid];if(!a)return notOk('支払いが見つかりません');const log=[];if(a.unread)logDel(log,a,'unread');(a.items||[]).forEach(it=>{if(it.unread)logDel(log,it,'unread')});save();return okUndo(log,'読み取りを確定した')}
function readMarkList(aid){const a=A[aid];if(!a)return {amt:false,payee:false,items:[]};return {amt:!!(a.unread&&a.unread.amt),payee:!!(a.unread&&a.unread.payee),items:(a.items||[]).map((it,i)=>it.unread?i:-1).filter(i=>i>=0)}}
const readMarkCount=aid=>{const l=readMarkList(aid);return (l.amt?1:0)+(l.payee?1:0)+l.items.length};

/** 一緒の時の自分の支出の内訳: その縁が関わる活動(役割・払った人・使った人)のうち、親も関わるものは親にまとめ、
    その活動と子孫の支払いの自分の負担を足す。同じ支払いは1回だけ数える */
function spendWithParts(id){
  if(!E[id])return [];
  const rel=new Set(Object.keys(A).filter(k=>A[k].type!=='日記'&&roleOf(A[k],id)));
  return [...rel].filter(k=>!ancIds(k).some(p=>rel.has(p))).map(k=>{
    const amt=[k,...descOf(k)].filter(i=>A[i]&&A[i].type==='支払い').reduce((s,i)=>s+mineOf(A[i]),0);
    return {aid:k,amt};
  }).filter(p=>p.amt>0);
}
/** 一緒の時の自分の支出(合計) */
const spendWith=id=>spendWithParts(id).reduce((s,p)=>s+p.amt,0);
