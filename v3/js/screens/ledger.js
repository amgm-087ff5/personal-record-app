/* =====================================================================
   家計簿 — 自分の支出を割合で見る。明細は日ごとの一覧(時間軸ではない)。
   ルート: #/ledger(月) , #/x/{id}(自分の支出の詳細) , #/cats(カテゴリの管理)
   ===================================================================== */
document.head.insertAdjacentHTML('beforeend',`<style>
.ld-sq{width:14px;height:6px;border-radius:3px;background:var(--cc);flex:none}
.ld-month{display:flex;align-items:center;justify-content:center;gap:12px;padding:0 var(--gutter) 10px}
.ld-month b{font:600 17px/1.4 var(--font);min-width:132px;text-align:center;font-variant-numeric:tabular-nums}
.ld-month .iconbtn[disabled]{opacity:.3;pointer-events:none}
.ld-sum{padding:16px 16px 14px;display:flex;flex-direction:column;gap:12px}
.ld-lbl{font:var(--t-label);color:var(--tx-2)}
.ld-r{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.ld-big{font:700 36px/1.1 var(--font);letter-spacing:-.01em;font-variant-numeric:tabular-nums}
.ld-bar{display:flex;height:14px;border-radius:7px;overflow:hidden;gap:2px;background:var(--sunk)}
.ld-bar i{display:block;height:100%;min-width:0}
.ld-legend{display:flex;flex-wrap:wrap;gap:6px 14px;font:var(--t-cap);color:var(--tx-2);font-variant-numeric:tabular-nums}
.ld-legend span{display:inline-flex;align-items:center;gap:6px}
.ld-legend i{width:14px;height:6px;border-radius:3px;background:var(--cc)}
.ld-fig{display:grid;grid-template-columns:repeat(3,1fr);border-top:1px solid var(--line)}
.ld-fig button{display:flex;flex-direction:column;gap:4px;align-items:flex-start;padding:12px 14px;min-height:60px;text-align:left}
.ld-fig button+button{border-left:1px solid var(--line)}
.ld-fig button.on{background:var(--brand-soft)}
.ld-fig .k{font:var(--t-cap);color:var(--tx-3)}
.ld-fig .k.strong{color:var(--tx)}
.ld-fig .v{font:600 15px/1.3 var(--font);font-variant-numeric:tabular-nums}
.ld-chart{padding:14px 16px 12px;display:flex;flex-direction:column;gap:12px}
.ld-svg{width:100%;height:auto;display:block;overflow:visible}
.ld-svg text{font:500 12px var(--font);fill:var(--tx-3)}
.ld-svg .on{fill:var(--tx);font-weight:700}
.ld-svg .vl{fill:var(--tx);font-weight:600;font-variant-numeric:tabular-nums}
.ld-svg .avg{fill:var(--tx-2);paint-order:stroke;stroke:var(--surface);stroke-width:4px;stroke-linejoin:round}
.ld-svg line{stroke:var(--tx-3);stroke-width:1}
.ld-svg .cur{fill:none;stroke:var(--tx);stroke-width:2}
.ld-seg .seg{margin-bottom:12px}
.ld-seg .seg button{height:44px}
.ld-row.on{background:var(--brand-soft)}
.ld-row .ld-m{display:block;margin-top:6px}
.ld-pct{color:var(--tx-3);font-weight:400;font-size:12px;margin-left:6px}
.ld-kid{padding-left:36px;background:linear-gradient(var(--line-strong),var(--line-strong)) 19px 0/2px 100% no-repeat}
.ld-kid2{padding-left:58px;background:linear-gradient(var(--line-strong),var(--line-strong)) 19px 0/2px 100% no-repeat,linear-gradient(var(--line-strong),var(--line-strong)) 41px 0/2px 100% no-repeat}
.ld-clear{padding:0 0 12px}
.ld-clear .chip{height:32px}
.ld-mini{display:inline-flex;gap:6px}
.ld-tap{width:100%;text-align:left}
.ld-in{flex:1;min-width:0;height:44px;font:var(--t-body);color:var(--tx);outline:none}
.ld-in::placeholder{color:var(--tx-3)}
.ld-ref{min-height:44px;height:auto;margin-top:12px;padding:0 14px}
.ld-chk{color:var(--brand);display:grid;place-items:center}
.ld-sw{display:flex;flex-wrap:wrap;padding:0}
.ld-sw .sw{width:44px;height:44px;border-radius:50%;display:grid;place-items:center}
.ld-sw .sw::before{content:"";width:32px;height:32px;border-radius:50%;background:var(--c);box-shadow:inset 0 0 0 1px var(--line-strong)}
.ld-sw .sw[aria-pressed="true"]::before{box-shadow:0 0 0 2px var(--surface),0 0 0 4px var(--brand)}
/* 支出の一覧の1件: 支払いの棒(44x8)の中の自分の区間 */
.ld-pbar{display:flex;width:44px;height:8px;border-radius:4px;overflow:hidden;flex:none}
.ld-pbar i{display:block;height:100%;min-width:0}
.ld-pbar i.me{background:var(--me)}
.ld-pbar i.rest{background:var(--axis)}
.ld-pbar i.owed{background:repeating-linear-gradient(135deg,var(--owe) 0 2px,color-mix(in srgb,var(--owe) 40%,transparent) 2px 4px)}
.ld-exp .v small{display:block;font:var(--t-cap);color:var(--tx-3);font-weight:400;text-align:right}
</style>`);

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

/** 推移: 月ごとの「自分の支出の棒」を縦に並べる(最上部の棒と同じ形。区間=カテゴリ)。上=新しい月 */
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

/** 支出の1件(独立したオブジェクト): 支払いの棒の中の自分の区間 + 支払いの題名 */
function ldExpRow(k,x){
  const p=A[x.pay],rest=p.amt-x.amt,owed=p.adv&&p.adv[2]==='未精算';
  const bar=`<span class="ld-pbar" aria-hidden="true"><i class="me" style="flex:${x.amt}"></i>${rest>0?`<i class="${owed?'owed':'rest'}" style="flex:${rest}"></i>`:''}</span>`;
  return row({href:`#/x/${k}`,cls:'ld-exp',lead:bar,t:esc(p.title),
    s:`${esc(shopOf(x))} ${catTag(x.cat)}${xSp(x)==='非日常'?' '+tag('✦ 非日常'):''}`,
    v:`${yen(x.amt)}${x.amt!==p.amt?`<small>支払い ${yen(p.amt)}</small>`:''}`,amt:true,chev:true});
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
  const top=ranked.slice(0,4),restV=ranked.slice(4).reduce((a,r)=>a+cur[r],0);
  const legend=`<div class="ld-legend">${top.map(r=>`<span style="--cc:${catColor(r)}"><i></i>${esc(CATS[r].n)} ${ldPc(cur[r],tot)}%</span>`).join('')}${restV?`<span style="--cc:var(--axis)"><i></i>ほか ${ldPc(restV,tot)}%</span>`:''}</div>`;
  const figBtn=(l,ax,k,v,cls='')=>`<button type="button" class="${ldFilter&&ldFilter.ax===ax&&ldFilter.k===k?'on':''}" onclick="ldPick('${ax}','${k}',1)"><span class="k ${cls}">${l}</span><span class="v num">${yen(v)}</span></button>`;
  const figs=isNow?`<div class="ld-fig">${figBtn('固定費','fx','固定',fixed)}${figBtn('変動費','fx','変動',varia)}${tot-fixed-varia>0?figBtn('固定・変動 未指定','fx','未指定',tot-fixed-varia):''}${figBtn('✦ 非日常','sp','非日常',nonDaily,'strong')}</div>`:'';
  const summary=list([`<div class="ld-sum"><div class="ld-lbl">自分の支出</div><div class="ld-r"><span class="ld-big num">${yen(tot)}</span>${diff!=null?tag(`前月比 ${diff>0?'+':''}${diff}%`):''}</div>${bar}</div>${figs}`]);

  const chart=list([`<div class="ld-chart">${ldChart(months)}</div>`]);

  let breakdown='',upcoming='',spend='';
  if(isNow){
    breakdown=sh('内訳')+`<div class="ld-seg">${seg([['cat','カテゴリ'],['fx','固定・変動'],['sp','特別感']],ldAxis,"ldAxisSet('$v')")}</div>`+list(ldRows(tot,fixed,nonDaily));

    const ups=Object.entries(EV).filter(([k,e])=>e.pay&&e.rep).map(([k,e])=>[k,e,nextOn(e)]).sort((a,b)=>a[2].localeCompare(b[2]));
    if(ups.length)upcoming=sh('これからの固定費')+`<div class="ax">`+ups.map(([k,e,d])=>axRow({href:`#/ev/${k}`,time:md(d),node:{cls:'plan',icon:'pay'},future:true,t:esc(e.t),m:esc(catPath(e.pay.cat)),amt:`<b>${yen(e.pay.amt)}</b>`})).join('')+`</div>`;

    const fl=Object.entries(X).filter(([k,x])=>ldMatch(x));
    const byDay={};fl.forEach(e=>{const d=xDate(e[1]);(byDay[d]=byDay[d]||[]).push(e)});
    const body=Object.keys(byDay).sort().reverse().map(d=>{
      const items=byDay[d].sort((a,b)=>(A[b[1].pay].time||'').localeCompare(A[a[1].pay].time||''));
      const sum=items.reduce((s,e)=>s+e[1].amt,0);
      return dayHead(d,{noax:true,href:'#/day/'+d,sum:items.length>=2?`自分 ${yen(sum)}`:''})+objs(items.map(([k,x])=>ldExpRow(k,x)));
    }).join('');
    const clear=ldFilter?`<div class="ld-clear">${chips([['x',`${AX_NAME[ldFilter.ax]}: ${ldFilterName()} ×`]],'x','ldClear()')}</div>`:'';
    spend=sh('支出',`${fl.length}件`)+clear+(fl.length?body:empty('expense','該当する支出はありません'));
  }

  return largeTitle('家計簿',{actions:[{text:'カテゴリ',icon:'cats',onclick:"go('#/cats')"}]})
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
  const p=A[x.pay],date=xDate(x),rest=p.amt-x.amt;
  /* 最上部: 親の支払い(棒) → 自分の区間を拡大したもの = このページ */
  const f=Math.max(0.04,Math.min(1,x.amt/p.amt));
  const owed=p.adv&&p.adv[2]==='未精算';
  const pr=(p.roles||[]).filter(r=>r[1]&&E[r[1]]&&r[1]!=='me'&&!['支払先','支払者'].includes(r[0])).map(r=>r[1]);
  const top=`<section class="zm"><a class="zm-parent" href="#/a/${x.pay}">${ico('pay','var(--tx)')}<span class="grow"><div class="t ell">${esc(p.title)}<span class="faint" style="font-weight:400">、${esc(shopOf(x))}</span></div><div class="s">支払い ${fmtMD(date)} ${esc(p.time||'')}</div></span><span class="v">${yen(p.amt)}</span>${IC.chev}</a>`
    +`<div class="zm-bar" aria-label="支払い ${yen(p.amt)} のうち自分 ${yen(x.amt)}"><i class="me" style="flex:${x.amt}"></i>${rest>0?`<i class="${owed?'owed':'other'}" style="flex:${rest}"></i>`:''}</div>`
    +`<svg class="zm-lens" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true"><polygon points="0,0 ${(f*100).toFixed(1)},0 100,30 0,30" fill="var(--me)" opacity=".16"/><line x1="${(f*100).toFixed(1)}" y1="0" x2="100" y2="30" stroke="var(--me)" stroke-width="1" vector-effect="non-scaling-stroke" opacity=".5"/></svg>`
    +`<div class="zm-card ld-zcard"><div class="big">${yen(x.amt)}</div><div class="pct">支払い ${yen(p.amt)} の ${Math.round(x.amt/p.amt*100)}%</div></div></section>`;
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

/* カテゴリの木: 独立した面の行。子は縦のガイド線で親に繋ぐ(└は使わない) */
route('cats',()=>{
  const sumOf=k=>ldSum(x=>catAnc(x.cat).includes(k));
  const defs=k=>{
    const f=catFx(k),sp=catSp(k);
    const parts=[f?`<span class="${CATS[k].fx!==undefined?'':'faint'}">${f}</span>`:`<span class="faint">固定・変動は都度指定</span>`];
    if(sp==='非日常')parts.push(`<span class="${CATS[k].sp!==undefined?'':'faint'}">非日常</span>`);
    return parts.join('、');
  };
  const tree=(k,d)=>{
    const r=row({onclick:`ldOpenCat('${k}')`,lead:ldSq(k),t:esc(CATS[k].n),s:defs(k),v:yen(sumOf(k)),amt:true,chev:true,cls:d?'ind':''});
    return [d?r.replace('class="row ind"',`style="--d:${d}" class="row ind"`):r,...childrenOf(k).flatMap(c=>tree(c,d+1))];
  };
  const rows=ROOTS().flatMap(r=>tree(r,0));
  return appbar('カテゴリ',{actions:[{icon:'plus',label:'カテゴリを作成',onclick:'ldNewCat()'}]})
    +sh('すべて',Object.keys(CATS).length)+objs(rows);
},{tab:'ledger'});

CREATE.push({id:'expense',label:'金額だけで支払い',sub:'金額とカテゴリだけ',icon:'pay',color:'var(--ev)',primaryOn:[],onclick:'openQuickExpense()'});
