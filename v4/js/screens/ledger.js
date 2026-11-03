/* =====================================================================
   家計簿 — 自分の支出を割合で見る。明細は1件=1枚の半券チケットの一覧(日の区切りは置かない)。
   ルート: #/ledger(月) , #/x/{id}(自分の支出の詳細) , #/cats(カテゴリの管理)
   スタイルは css/s-ledger.css(この画面の規則は1か所)
   ===================================================================== */

/* ---------- 状態 ---------- */
let ldM=5, ldAxis='cat', ldFilter=null, ldDraft=null, ldDraftKey=null;
const AX_NAME={cat:'カテゴリ',fx:'固定・変動',sp:'特別感'};

/* ---------- 計算 ---------- */
const ldSum=f=>Object.values(X).filter(f).reduce((a,x)=>a+x.amt,0);
const ldTotal=m=>Object.values(m).reduce((a,b)=>a+b,0);
/** 大分類ごとの今月の合計 */
const ldMap=()=>{const m={};ROOTS().forEach(r=>m[r]=0);Object.values(X).forEach(x=>{m[catAnc(x.cat)[0]]+=x.amt});return m};
/** 6か月: [年月, ラベル, 大分類ごとの合計]。最後が10月(明細あり) */
const ldMonths=()=>[...TREND.map((m,i)=>[`2026-${String(5+i).padStart(2,'0')}`,m[0],m[1]]),['2026-10','10月',ldMap()]];
const ldMatch=x=>{if(!ldFilter)return true;const {ax,k}=ldFilter;return ax==='cat'?catAnc(x.cat).includes(k):ax==='fx'?(xFx(x)||'未指定')===k:(xSp(x)||'未指定')===k};
const ldFilterName=()=>ldFilter.ax==='cat'?CATS[ldFilter.k].n:ldFilter.k;
const childrenOf=k=>Object.keys(CATS).filter(c=>CATS[c].p===k);
const catDesc=k=>[k,...childrenOf(k).flatMap(catDesc)];
const depthCls=k=>{const d=catAnc(k).length-1;return d>1?'ld-kid2':d?'ld-kid':''};
/** カテゴリの印 = 短い横棒(14x6) */
const ldSq=k=>`<span class="ld-sq" style="--cc:${catColor(k)}"></span>`;
const ldPc=(v,t)=>Math.round(v/t*100);

/* ---------- 操作 ---------- */
/** 絞り込みの切り替え。setAx: 軸も合わせる(固定費・変動費・非日常の数字から) */
function ldPick(ax,k,setAx){
  if(setAx)ldAxis=ax;
  const same=!!ldFilter&&ldFilter.ax===ax&&ldFilter.k===k;
  ldFilter=same?null:{ax,k};rerender();
}
function ldAxisSet(a){ldAxis=a;ldFilter=null;rerender()}
function ldClear(){ldFilter=null;rerender()}
function ldStep(d){ldM=Math.min(ldMonths().length-1,Math.max(0,ldM+d));ldFilter=null;rerender()}

/** 推移: 月ごとの「自分の支出の棒」を縦に並べる(区間=カテゴリ)。上=新しい月 */
function ldChart(months){
  const roots=ROOTS(),tots=months.map(m=>ldTotal(m[2])),max=Math.max(...tots),past=tots.slice(0,-1),avg=Math.round(past.reduce((x,y)=>x+y,0)/past.length);
  return months.map((m,i)=>[m,i]).reverse().map(([m,i])=>{
    const cur=i===months.length-1;
    const segs=roots.filter(r=>m[2][r]>0).sort((a,b)=>m[2][b]-m[2][a]);
    return `<div class="ld-tr ${cur?'on':''}"><span class="mo">${m[1]}</span><span class="tb"><i class="ld-avg" style="left:${(avg/max*100).toFixed(1)}%"></i><span class="ld-bar" style="width:${(tots[i]/max*100).toFixed(1)}%">${segs.map(r=>`<i style="flex:${m[2][r]} 1 0;background:${catColor(r)}"></i>`).join('')}</span></span><span class="v num">${yen(tots[i])}</span></div>`;
  }).join('')+`<div class="ld-tr ld-avgl"><span></span><span class="tb"><span style="margin-left:${(avg/max*100).toFixed(1)}%">${past.length}か月平均 ${yen(avg)} ┆</span></span><span></span></div>`;
}

/** 内訳の行(軸は ldAxis)。今月だけ */
function ldRows(tot,fixed,nonDaily){
  const brow=(label,v,ax,k,color,cls='')=>{
    const on=!!ldFilter&&ldFilter.ax===ax&&ldFilter.k===k;
    return row({cls:`ld-row ${cls}${on?' on':''}`,onclick:`ldPick('${ax}','${k}')`,
      lead:`<span class="ld-sq" style="--cc:${color}"></span>`,t:esc(label),
      s:`<span class="meter ld-m"><i style="width:${(v/tot*100).toFixed(1)}%;background:${color}"></i></span>`,
      v:`${yen(v)}<span class="ld-pct">${ldPc(v,tot)}%</span>`,amt:true});
  };
  const out=[];
  if(ldAxis==='cat'){
    const sumOf=k=>ldSum(x=>catAnc(x.cat).includes(k));
    const byAmt=(a,b)=>sumOf(b)-sumOf(a);
    ROOTS().filter(r=>sumOf(r)>0).sort(byAmt).forEach(r=>{
      out.push(brow(CATS[r].n,sumOf(r),'cat',r,catColor(r)));
      if(ldFilter&&ldFilter.ax==='cat'&&catAnc(ldFilter.k)[0]===r)
        childrenOf(r).filter(c=>sumOf(c)>0).sort(byAmt).forEach(c=>out.push(brow(CATS[c].n,sumOf(c),'cat',c,catColor(c),'ld-kid')));
    });
  }else if(ldAxis==='fx'){
    const vr=ldSum(x=>xFx(x)==='変動'),un=tot-fixed-vr;
    out.push(brow('固定',fixed,'fx','固定','var(--tx-2)'),brow('変動',vr,'fx','変動','var(--axis)'));
    if(un>0)out.push(brow('未指定',un,'fx','未指定','var(--line-strong)'));
  }else{
    const dl=ldSum(x=>xSp(x)==='日常'),un=tot-dl-nonDaily;
    out.push(brow('日常',dl,'sp','日常','var(--axis)'),brow('非日常',nonDaily,'sp','非日常','var(--ev)'));
    if(un>0)out.push(brow('未指定',un,'sp','未指定','var(--line-strong)'));
  }
  return out;
}

/** 支出の1件 = 半券チケット1枚。半券に日と曜日、本体に支払先・カテゴリ・金額。
    支払いが割り勘・立替なら、自分の区間の棒を補足に小さく出す */
function ldTicket(k,x){
  const p=A[x.pay],d=xDate(x),rest=p.amt-x.amt,owed=p.adv&&p.adv[2]==='未精算';
  const pbar=rest>0?`<span class="ld-pbar" aria-hidden="true"><i class="me" style="flex:${x.amt}"></i><i class="${owed?'owed':'rest'}" style="flex:${rest}"></i></span>`:'';
  const nd=xSp(x)==='非日常';
  return ticket({href:`#/x/${k}`,stub:`<b>${+d.slice(8)}</b>${wdOf(d)}`,t:esc(p.title),
    s:`${esc(shopOf(x))} ${catTag(x.cat)}${nd?' '+tag('✦ 非日常'):''}${pbar?' '+pbar:''}`,
    amt:`${yen(x.amt)}${x.amt!==p.amt?`<small>支払い ${yen(p.amt)}</small>`:''}`});
}

/* ---------- 家計簿(月) ---------- */
route('ledger',()=>{
  const months=ldMonths(),last=months.length-1,[ym,,cur]=months[ldM],isNow=ldM===last;
  const tot=ldTotal(cur),prev=ldM>0?ldTotal(months[ldM-1][2]):0;
  const diff=prev?Math.round((tot-prev)/prev*100):null;
  const roots=ROOTS(),ranked=roots.filter(r=>cur[r]>0).sort((a,b)=>cur[b]-cur[a]);
  const fixed=ldSum(x=>xFx(x)==='固定'),varia=ldSum(x=>xFx(x)==='変動'),nonDaily=ldSum(x=>xSp(x)==='非日常');

  const nav=`<div class="ld-month"><button class="btn sm ghost" type="button" onclick="ldStep(-1)" ${ldM===0?'disabled':''}>前の月</button><b>${ym.slice(0,4)}年${+ym.slice(5)}月</b><button class="btn sm ghost" type="button" onclick="ldStep(1)" ${isNow?'disabled':''}>次の月</button></div>`;
  const note=isNow?'':banner('info','この月の明細は10月だけ表示しています。');

  const bar=`<div class="ld-bar" role="img" aria-label="大分類の割合">${ranked.map(r=>`<i style="flex:${cur[r]} 1 0;background:${catColor(r)}"></i>`).join('')}</div>`;
  const figBtn=(l,ax,k,v,cls='')=>`<button type="button" class="${ldFilter&&ldFilter.ax===ax&&ldFilter.k===k?'on':''}" onclick="ldPick('${ax}','${k}',1)"><span class="k ${cls}">${l}</span><span class="v num">${yen(v)}</span></button>`;
  const figs=isNow?`<div class="ld-fig">${figBtn('固定費','fx','固定',fixed)}${figBtn('変動費','fx','変動',varia)}${tot-fixed-varia>0?figBtn('固定・変動 未指定','fx','未指定',tot-fixed-varia):''}${figBtn('✦ 非日常','sp','非日常',nonDaily,'strong')}</div>`:'';
  const summary=list([`<div class="ld-sum"><div class="ld-lbl">自分の支出</div><div class="ld-r"><span class="ld-big num">${yen(tot)}</span>${diff!=null?tag(`前月比 ${diff>0?'+':''}${diff}%`):''}</div>${bar}</div>${figs}`]);

  const chart=list([`<div class="ld-chart">${ldChart(months)}</div>`]);

  let breakdown='',upcoming='',spend='';
  if(isNow){
    breakdown=sh('内訳')+`<div class="ld-seg">${seg([['cat','カテゴリ'],['fx','固定・変動'],['sp','特別感']],ldAxis,"ldAxisSet('$v')")}</div>`+list(ldRows(tot,fixed,nonDaily));

    /* これからの固定費 = 予定の行。1枚の面にまとめ、軸(点線)で包む */
    const ups=Object.entries(EV).filter(([k,e])=>e.pay&&e.rep).map(([k,e])=>[k,e,nextOn(e)]).sort((a,b)=>a[2].localeCompare(b[2]));
    if(ups.length)upcoming=sh('これからの固定費',`${ups.length}件`)+`<div class="ax">`+axGroup('',ups.map(([k,e,d])=>axRow({href:`#/ev/${k}`,time:md(d),node:{cls:'plan',icon:'pay'},future:true,t:esc(e.t),m:esc(catPath(e.pay.cat)),amt:`<b>${yen(e.pay.amt)}</b>`})).join(''),{future:true})+`</div>`;

    /* 支出: 日付の区切りは置かず、日付・曜日は半券が持つ。新しい順 */
    const fl=Object.entries(X).filter(([k,x])=>ldMatch(x))
      .sort((a,b)=>xDate(b[1]).localeCompare(xDate(a[1]))||(A[b[1].pay].time||'').localeCompare(A[a[1].pay].time||''));
    const clear=ldFilter?`<div class="ld-clear">${chips([['x',`${AX_NAME[ldFilter.ax]}: ${ldFilterName()} ×`]],'x','ldClear()')}</div>`:'';
    spend=sh('支出',`${fl.length}件`)+clear+(fl.length?`<div class="ld-tix">${fl.map(([k,x])=>ldTicket(k,x)).join('')}</div>`:empty('expense','該当する支出はありません'));
  }

  return largeTitle('家計簿',{actions:[
      {text:'カテゴリ',icon:'cats',onclick:"go('#/cats')"},
      {text:'レシートで支払い',icon:'camera',primary:true,onclick:'openReceipt()'}]})
    +nav+note+summary+sh('推移','6か月')+chart+breakdown+upcoming+spend;
},{tab:'ledger'});

/* ---------- 支出の詳細(自分の分だけ切り取った点) ---------- */
const ldMini=(items,cur,on)=>`<span class="ld-mini" role="group">${items.map(([v,l])=>`<button type="button" aria-pressed="${v===cur}" onclick="${on.replace('$v',v)}">${esc(l)}</button>`).join('')}</span>`;
/* 押した値が今の値と同じ=選びなおし(上書きを外す)。カテゴリの既定値と同じ値を選んだ時も上書きは持たない */
function ldSetFx(id,v){const x=X[id];x.fx=(x.fx===v||v===catFx(x.cat))?undefined:v;save();rerender()}
function ldSetSp(id,v){const x=X[id];x.sp=(x.sp===v||v===catSp(x.cat))?undefined:v;save();rerender()}
function ldMemo(id,v){X[id].memo=v;save()}
function ldSetCat(id,k){X[id].cat=k;closeSheet();save();rerender();toast('カテゴリを変えました')}
function ldCatPick(id){
  const cur=X[id].cat;
  const item=k=>row({onclick:`ldSetCat('${id}','${k}')`,lead:ldSq(k),t:esc(CATS[k].n),v:k===cur?`<span class="ld-chk">${IC.check}</span>`:null,cls:depthCls(k)});
  openSheet({title:'カテゴリを選ぶ',body:list(ROOTS().flatMap(r=>[item(r),...childrenOf(r).map(item)]))});
}
function ldMenu(id){
  openSheet({title:'操作',body:list([
    row({lead:ico('edit'),t:'編集',onclick:"closeSheet();toast('編集はモックでは未対応です')"}),
    row({lead:ico('pay'),t:'支払いを開く',href:`#/a/${X[id].pay}`})])});
}

route('x',id=>{
  const x=X[id];
  if(!x)return appbar('')+empty('expense','この支出は見つかりません');
  const p=A[x.pay],date=xDate(x),pct=Math.round(x.amt/p.amt*100);
  const owed=p.adv&&p.adv[2]==='未精算';
  /* 最上部: 親の支払いへの参照(1行)+ 自分の分の半券チケット(主役・塗り) */
  const parent=`<a class="ld-parent" href="#/a/${x.pay}">${ico('pay','var(--tx-2)')}<span class="grow">${esc(p.title)}、${esc(shopOf(x))}</span><span class="v num">${yen(p.amt)}</span>${IC.chev}</a>`;
  const top=`<div class="ld-top">${parent}${ticket({cls:'lg fill',label:`自分の支出 ${yen(x.amt)}`,stub:`${IC.expense}自分の支出`,
    t:yen(x.amt),s:`支払い ${yen(p.amt)} の ${pct}%${owed?' '+tag('未精算',{cls:'warn'}):''}`})}</div>`;
  const over=tag('この支出だけ',{cls:'brand'});
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:`ldMenu('${id}')`}]})
    +top
    +sh('分類')
    +list([
      `<button class="prop ld-tap" type="button" onclick="ldCatPick('${id}')"><span class="k">カテゴリ</span><span class="val">${catTag(x.cat)}</span><span class="meta">${IC.chev}</span></button>`,
      prop('固定・変動',ldMini([['固定','固定'],['変動','変動']],xFx(x),`ldSetFx('${id}','$v')`),x.fx!==undefined?over:catFx(x.cat)===null?tag('都度指定',{cls:'warn'}):''),
      prop('特別感',ldMini([['日常','日常'],['非日常','非日常']],xSp(x),`ldSetSp('${id}','$v')`),x.sp!==undefined?over:catSp(x.cat)===null?tag('都度指定',{cls:'warn'}):''),
      prop('メモ',`<input class="ld-in" value="${esc(x.memo||'')}" placeholder="メモを書く" oninput="ldMemo('${id}',this.value)">`)
    ])
    +sh('品目',itemGroups(x.items).length,{label:'編集',icon:'edit',onclick:`ipOpen('${x.pay}')`})
    +list([...itemsList(id,x.items,{x:1}),
      row({cls:'plain',t:'<b>品目の合計 = 自分の支出</b>',v:`<b class="num" style="color:var(--me)">${yen(x.items.reduce((s,i)=>s+i.price,0))}</b>`})],'flat');
},{tab:'ledger'});

/* ---------- カテゴリの管理 ---------- */
function ldEditBody(){
  const d=ldDraft,isNew=!ldDraftKey,isKid=!!d.p;
  const parentOpts=['<option value="">いちばん上</option>',...Object.keys(CATS).filter(c=>!d.ban.includes(c)).map(c=>`<option value="${c}" ${c===d.p?'selected':''}>${esc(catPath(c))}</option>`)].join('');
  const sw=COLORS.map(c=>`<button type="button" class="sw" style="--c:${c}" aria-label="色 ${c}" aria-pressed="${d.c===c}" onclick="ldDraftSet('c','${c}')"></button>`).join('');
  /* 'ask' = 都度指定(カテゴリは既定値を持たず、支出を入れる時に選ぶ)。子は 'inh' = 親から引き継ぐ */
  const fxItems=[...(isKid?[['inh','親から']]:[]),['固定','固定'],['変動','変動'],['ask','都度指定']];
  const spItems=[...(isKid?[['inh','親から']]:[]),['日常','日常'],['非日常','非日常'],['ask','都度指定']];
  const fxCur=d.fx==='inh'&&!isKid?'ask':d.fx, spCur=d.sp==='inh'&&!isKid?'ask':d.sp;
  const kids=!isNew&&childrenOf(ldDraftKey).length>0;
  const used=!isNew&&Object.values(X).some(x=>catDesc(ldDraftKey).includes(x.cat));
  const blocked=kids||used;
  return `<div class="list">`
    +`<div class="field"><label>名前</label><input value="${esc(d.n)}" placeholder="例: 外食" oninput="ldDraft.n=this.value"></div>`
    +`<div class="field"><label>親</label><select onchange="ldDraftSet('p',this.value)">${parentOpts}</select></div>`
    +(isKid?'':`<div class="field" style="align-items:flex-start"><label style="padding-top:10px">色</label><div class="ld-sw">${sw}</div></div>`)
    +`<div class="field"><label>固定・変動</label><span class="ld-mini" role="group">${fxItems.map(([v,l])=>`<button type="button" aria-pressed="${fxCur===v}" onclick="ldDraftSet('fx','${v}')">${l}</button>`).join('')}</span></div>`
    +`<div class="field"><label>特別感</label><span class="ld-mini" role="group">${spItems.map(([v,l])=>`<button type="button" aria-pressed="${spCur===v}" onclick="ldDraftSet('sp','${v}')">${l}</button>`).join('')}</span></div>`
    +`</div><p class="hint">都度指定: このカテゴリは既定値を持たず、支出を入れる時に毎回選びます。</p>`
    +(isNew?'':`<div class="btnrow"><button class="btn danger block" type="button" ${blocked?'disabled':''} onclick="ldDelCat()">${IC.trash}カテゴリを削除</button></div>`
      +(blocked?`<p class="hint">${kids?'子カテゴリがあるので削除できません。':'使われているので削除できません。'}</p>`:''));
}
const ldDraftSet=(key,val)=>{ldDraft[key]=val;setSheetBody(ldEditBody())};
function ldOpenCat(k){
  const c=CATS[k];ldDraftKey=k;
  ldDraft={n:c.n,p:c.p||'',c:c.c||COLORS[0],fx:c.fx==='none'?'ask':c.fx||'inh',sp:c.sp==='none'?'ask':c.sp||'inh',ban:catDesc(k)};
  openSheet({title:'カテゴリを編集',left:{label:'閉じる',onclick:'closeSheet()'},right:{label:'保存',onclick:'ldSaveCat()'},body:ldEditBody()});
}
function ldNewCat(){
  ldDraftKey=null;ldDraft={n:'',p:'',c:COLORS[0],fx:'inh',sp:'inh',ban:[]};
  openSheet({title:'カテゴリを作成',left:{label:'閉じる',onclick:'closeSheet()'},right:{label:'作成',onclick:'ldSaveCat()'},body:ldEditBody()});
}
function ldSaveCat(){
  const d=ldDraft,n=(d.n||'').trim();
  if(!n){toast('名前を入れてください');return}
  const isNew=!ldDraftKey,key=ldDraftKey||uid('cat'),o={n};
  if(d.p)o.p=d.p;else o.c=d.c;           /* 色は大分類だけ。子は親の色を使う */
  const fx=d.fx==='inh'&&!d.p?'ask':d.fx, sp=d.sp==='inh'&&!d.p?'ask':d.sp;   /* 大分類には「親から」がない = 設定しない */
  if(fx!=='inh')o.fx=fx;   /* 'ask' = 都度指定、を明示的に保存 */
  if(sp!=='inh')o.sp=sp;
  CATS[key]=o;
  closeSheet();save();rerender();toast(`「${n}」を${isNew?'作成':'保存'}しました`);
}
function ldDelCat(){
  const k=ldDraftKey,c=CATS[k];
  if(childrenOf(k).length||Object.values(X).some(x=>catDesc(k).includes(x.cat))){toast('使われているので削除できません');return}
  delete CATS[k];closeSheet();save();rerender();
  toast(`「${c.n}」を削除しました`,()=>{CATS[k]=c});
}

/* カテゴリの木: 1枚の面の中に並べる。子は .ld-kids の縦の細い線で親に繋ぐ(字下げ) */
route('cats',()=>{
  const sumOf=k=>ldSum(x=>catAnc(x.cat).includes(k));
  const defs=k=>{
    const f=catFx(k),sp=catSp(k);
    const parts=[f?`<span class="${CATS[k].fx!==undefined?'':'faint'}">${f}</span>`:`<span class="faint">固定・変動は都度指定</span>`];
    if(sp==='非日常')parts.push(`<span class="${CATS[k].sp!==undefined?'':'faint'}">非日常</span>`);
    return parts.join('、');
  };
  const node=k=>{
    const kids=childrenOf(k);
    const r=row({onclick:`ldOpenCat('${k}')`,lead:ldSq(k),t:esc(CATS[k].n),s:defs(k),v:yen(sumOf(k)),amt:true,chev:true});
    return `<div class="ld-node">${r}${kids.length?`<div class="ld-kids">${kids.map(node).join('')}</div>`:''}</div>`;
  };
  return appbar('カテゴリ',{actions:[{icon:'plus',label:'カテゴリを作成',onclick:'ldNewCat()'}]})
    +sh('すべて',Object.keys(CATS).length)+`<div class="list ld-tree">${ROOTS().map(node).join('')}</div>`;
},{tab:'ledger'});

CREATE.push({id:'expense',label:'金額だけで支払い',sub:'金額とカテゴリだけ',icon:'pay',color:'var(--ev)',primaryOn:[],onclick:'openQuickExpense()'});
