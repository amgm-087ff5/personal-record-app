/* =====================================================================
   家計簿 — 自分の支出を割合で見る。明細は、お金が流れた点だけの時間軸。
   ルート: #/ledger(月) , #/x/{id}(自分の支出の詳細) , #/cats(カテゴリの管理)
   ===================================================================== */
document.head.insertAdjacentHTML('beforeend',`<style>
.ld-sq{width:12px;height:12px;border-radius:3px;background:var(--cc);flex:none}
.ld-sq.sm{width:10px;height:10px;border-radius:2px}
.ld-month{display:flex;align-items:center;justify-content:center;gap:12px;padding:0 var(--gutter) 10px}
.ld-month b{font:600 17px/1.4 var(--font);min-width:132px;text-align:center;font-variant-numeric:tabular-nums}
.ld-month .iconbtn[disabled]{opacity:.3;pointer-events:none}
.ld-sum{padding:16px 16px 14px;display:flex;flex-direction:column;gap:12px}
.ld-lbl{font:var(--t-label);color:var(--ink-2)}
.ld-r{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.ld-big{font:700 36px/1.1 var(--font);letter-spacing:-.01em;font-variant-numeric:tabular-nums}
.ld-bar{display:flex;height:14px;border-radius:7px;overflow:hidden;gap:2px;background:var(--sunk)}
.ld-bar i{display:block;height:100%;min-width:0}
.ld-legend{display:flex;flex-wrap:wrap;gap:6px 14px;font:var(--t-cap);color:var(--ink-2);font-variant-numeric:tabular-nums}
.ld-legend span{display:inline-flex;align-items:center;gap:6px}
.ld-legend i{width:8px;height:8px;border-radius:2px;background:var(--cc)}
.ld-fig{display:grid;grid-template-columns:repeat(3,1fr);border-top:1px solid var(--line)}
.ld-fig button{display:flex;flex-direction:column;gap:4px;align-items:flex-start;padding:12px 14px;min-height:60px;text-align:left}
.ld-fig button+button{border-left:1px solid var(--line)}
.ld-fig button.on{background:var(--brand-soft)}
.ld-fig .k{font:var(--t-cap);color:var(--ink-3)}
.ld-fig .v{font:600 15px/1.3 var(--font);font-variant-numeric:tabular-nums}
.ld-fig .v.sp{color:var(--special)}
.ld-chart{padding:14px 16px 12px;display:flex;flex-direction:column;gap:12px}
.ld-svg{width:100%;height:auto;display:block;overflow:visible}
.ld-svg text{font:500 12px var(--font);fill:var(--ink-3)}
.ld-svg .on{fill:var(--ink);font-weight:600}
.ld-svg .vl{fill:var(--ink);font-weight:600;font-variant-numeric:tabular-nums}
.ld-svg .avg{fill:var(--ink-2);paint-order:stroke;stroke:var(--surface);stroke-width:4px;stroke-linejoin:round}
.ld-svg line{stroke:var(--ink-2);stroke-width:1;stroke-dasharray:3 3}
.ld-seg .seg{margin-bottom:12px}
.ld-seg .seg button{height:44px}
.ld-row.on{background:var(--brand-soft)}
.ld-row .ld-m{display:block;margin-top:6px}
.ld-pct{color:var(--ink-3);font-weight:400;font-size:12px;margin-left:6px}
.ld-kid{padding-left:36px;background:linear-gradient(var(--line-strong),var(--line-strong)) 19px 0/2px 100% no-repeat}
.ld-kid2{padding-left:58px;background:linear-gradient(var(--line-strong),var(--line-strong)) 19px 0/2px 100% no-repeat,linear-gradient(var(--line-strong),var(--line-strong)) 41px 0/2px 100% no-repeat}
.ld-clear{padding:0 0 12px}
.ld-clear .chip{height:44px}
.ld-mini{display:inline-flex;padding:2px;background:var(--sunk);border-radius:10px;gap:2px}
.ld-mini button{height:40px;padding:0 14px;border-radius:8px;font:var(--t-label);color:var(--ink-2)}
.ld-mini button[aria-pressed="true"]{background:var(--surface);color:var(--ink);box-shadow:var(--e1);font-weight:600}
.ld-tap{width:100%;text-align:left}
.ld-in{flex:1;min-width:0;height:44px;font:var(--t-body);color:var(--ink);outline:none}
.ld-in::placeholder{color:var(--ink-3)}
.ld-ref{min-height:44px;height:auto;margin-top:12px;padding:0 14px}
.ld-chk{color:var(--brand);display:grid;place-items:center}
.ld-sw{display:flex;flex-wrap:wrap;padding:0}
.ld-sw .sw{width:44px;height:44px;border-radius:50%;display:grid;place-items:center}
.ld-sw .sw::before{content:"";width:32px;height:32px;border-radius:50%;background:var(--c);box-shadow:inset 0 0 0 1px var(--line-strong)}
.ld-sw .sw[aria-pressed="true"]::before{box-shadow:0 0 0 2px var(--surface),0 0 0 4px var(--brand)}
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
const ldMatch=x=>{if(!ldFilter)return true;const {ax,k}=ldFilter;return ax==='cat'?catAnc(x.cat).includes(k):ax==='fx'?xFx(x)===k:xSp(x)===k};
const ldFilterName=()=>ldFilter.ax==='cat'?CATS[ldFilter.k].n:ldFilter.k;
const childrenOf=k=>Object.keys(CATS).filter(c=>CATS[c].p===k);
const catDesc=k=>[k,...childrenOf(k).flatMap(catDesc)];
const depthCls=k=>{const d=catAnc(k).length-1;return d>1?'ld-kid2':d?'ld-kid':''};
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

/** 推移の積み上げ棒(SVG)。最後の棒が10月 */
const ldBarPath=(x,y,w,h,r)=>r?`M${x} ${y+h}V${y+r}Q${x} ${y} ${x+r} ${y}H${x+w-r}Q${x+w} ${y} ${x+w} ${y+r}V${y+h}Z`:`M${x} ${y}h${w}v${h}h${-w}Z`;
function ldChart(months){
  const roots=ROOTS(),W=340,H=112,n=months.length,bw=34,gap=(W-bw*n)/(n-1);
  const tots=months.map(m=>ldTotal(m[2]));
  const past=tots.slice(0,-1),avg=Math.round(past.reduce((a,b)=>a+b,0)/past.length);
  const max=Math.max(...tots)*1.2,Y=v=>H-v/max*H;
  let g='';
  months.forEach((m,i)=>{
    const x=i*(bw+gap),cur=i===n-1,op=cur?1:.55;
    const segs=roots.filter(r=>m[2][r]>0);
    let base=H;
    segs.forEach((r,j)=>{
      const h=Math.max(m[2][r]/max*H,1),y=base-h,top=j===segs.length-1;
      g+=`<path d="${ldBarPath(x,y,bw,h,top?Math.min(4,h/2):0)}" fill="${catColor(r)}" opacity="${op}"/>`;
      base=y;
    });
    g+=`<text class="${cur?'on':''}" x="${x+bw/2}" y="${H+18}" text-anchor="middle">${m[1]}</text>`;
    if(cur)g+=`<text class="vl" x="${x+bw}" y="${Y(tots[i])-6}" text-anchor="end">${yen(tots[i])}</text>`;
  });
  g+=`<line x1="0" x2="${W}" y1="${Y(avg)}" y2="${Y(avg)}"/><text class="avg" x="0" y="${Y(avg)-6}">平均 ${yen(avg)}</text>`;
  return `<svg class="ld-svg" viewBox="0 -20 ${W} ${H+42}" role="img" aria-label="月ごとの支出の推移">${g}</svg>`;
}

/** 内訳の行(軸は ldAxis)。今月だけ */
function ldRows(tot,fixed,nonDaily){
  const brow=(label,v,ax,k,color,cls='')=>{
    const on=!!ldFilter&&ldFilter.ax===ax&&ldFilter.k===k;
    return row({cls:`ld-row ${cls}${on?' on':''}`,onclick:`ldPick('${ax}','${k}')`,
      lead:`<span class="ld-sq sm" style="--cc:${color}"></span>`,t:esc(label),
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
    out.push(brow('固定',fixed,'fx','固定','var(--c-schedule)'),brow('変動',tot-fixed,'fx','変動','var(--c-activity)'));
  }else{
    out.push(brow('日常',tot-nonDaily,'sp','日常','var(--c-type)'),brow('非日常',nonDaily,'sp','非日常','var(--special)'));
  }
  return out;
}

/** 支出の1行(時間軸の点) */
function ldExpRow(k,x){
  const p=A[x.pay];
  return axRow({href:`#/x/${k}`,time:p.time,node:{cls:'pay',icon:'pay'},t:esc(shopOf(x)),
    m:catTag(x.cat)+(xSp(x)==='非日常'?tag('非日常',{cls:'special'}):''),
    amt:`<b>${yen(x.amt)}</b>${x.amt!==p.amt?`<small>支払い ${yen(p.amt)}</small>`:''}`});
}

/* ---------- 家計簿(月) ---------- */
route('ledger',()=>{
  const months=ldMonths(),last=months.length-1,[ym,,cur]=months[ldM],isNow=ldM===last;
  const tot=ldTotal(cur),prev=ldM>0?ldTotal(months[ldM-1][2]):0;
  const diff=prev?Math.round((tot-prev)/prev*100):null;
  const roots=ROOTS(),ranked=roots.filter(r=>cur[r]>0).sort((a,b)=>cur[b]-cur[a]);
  const fixed=ldSum(x=>xFx(x)==='固定'),nonDaily=ldSum(x=>xSp(x)==='非日常');

  const nav=`<div class="ld-month"><button class="iconbtn" type="button" aria-label="前の月" onclick="ldStep(-1)" ${ldM===0?'disabled':''}>${IC.back}</button><b>${ym.slice(0,4)}年${+ym.slice(5)}月</b><button class="iconbtn" type="button" aria-label="次の月" onclick="ldStep(1)" ${isNow?'disabled':''}>${IC.chev}</button></div>`;
  const note=isNow?'':banner('info','この月の明細は10月だけ表示しています。');

  const bar=`<div class="ld-bar" role="img" aria-label="大分類の割合">${ranked.map(r=>`<i style="flex:${cur[r]} 1 0;background:${catColor(r)}"></i>`).join('')}</div>`;
  const top=ranked.slice(0,4),restV=ranked.slice(4).reduce((a,r)=>a+cur[r],0);
  const legend=`<div class="ld-legend">${top.map(r=>`<span style="--cc:${catColor(r)}"><i></i>${esc(CATS[r].n)} ${ldPc(cur[r],tot)}%</span>`).join('')}${restV?`<span style="--cc:var(--axis)"><i></i>ほか ${ldPc(restV,tot)}%</span>`:''}</div>`;
  const figBtn=(l,ax,k,v,sp)=>`<button type="button" class="${ldFilter&&ldFilter.ax===ax&&ldFilter.k===k?'on':''}" onclick="ldPick('${ax}','${k}',1)"><span class="k">${l}</span><span class="v num${sp?' sp':''}">${yen(v)}</span></button>`;
  const figs=isNow?`<div class="ld-fig">${figBtn('固定費','fx','固定',fixed)}${figBtn('変動費','fx','変動',tot-fixed)}${figBtn('非日常','sp','非日常',nonDaily,1)}</div>`:'';
  const summary=list([`<div class="ld-sum"><div class="ld-lbl">自分の支出</div><div class="ld-r"><span class="ld-big num">${yen(tot)}</span>${diff!=null?tag(`前月比 ${diff>0?'+':''}${diff}%`):''}</div>${bar}${legend}</div>${figs}`]);

  const chartLegend=roots.filter(r=>months.some(m=>m[2][r]>0)).map(r=>`<span style="--cc:${catColor(r)}"><i></i>${esc(CATS[r].n)}</span>`).join('');
  const chart=list([`<div class="ld-chart"><div class="ld-legend">${chartLegend}</div>${ldChart(months)}</div>`]);

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
      return dayHead(d,{href:`#/day/${d}`,sum:`自分 ${yen(sum)}`})+items.map(([k,x])=>ldExpRow(k,x)).join('');
    }).join('');
    const clear=ldFilter?`<div class="ld-clear">${chips([['x',`${AX_NAME[ldFilter.ax]}: ${ldFilterName()} ×`]],'x','ldClear()')}</div>`:'';
    spend=sh('支出',`${fl.length}件`)+clear+(fl.length?`<div class="ax">${body}${axEdge('10月1日より前',true)}</div>`:empty('expense','該当する支出はありません'));
  }

  return largeTitle('家計簿',{actions:[{icon:'settings',label:'カテゴリ',onclick:"go('#/cats')"}]})
    +nav+note+summary+sh('推移','6か月')+chart+breakdown+upcoming+spend;
},{tab:'ledger'});

/* ---------- 支出の詳細(自分の分だけ切り取った点) ---------- */
const ldMini=(items,cur,on)=>`<span class="ld-mini" role="group">${items.map(([v,l])=>`<button type="button" aria-pressed="${v===cur}" onclick="${on.replace('$v',v)}">${esc(l)}</button>`).join('')}</span>`;
function ldSetFx(id,v){const x=X[id];x.fx=v===catFx(x.cat)?undefined:v;save();rerender()}
function ldSetSp(id,v){const x=X[id];x.sp=v===catSp(x.cat)?undefined:v;save();rerender()}
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
  const segs=[{v:x.amt,cls:'me',label:'自分の支出'}];
  if(rest>0)segs.push({v:rest,cls:'dim',label:'ほかの人の分',href:''});
  const card=`<div class="kind">${IC.expense}自分の支出</div><div class="big">${yen(x.amt)}</div>`
    +`<div class="meta"><span>${IC.place}${esc(shopOf(x))}</span><span>${IC.pay}${esc(p.pay)}</span></div>`
    +splitBar(segs)
    +`<a class="ref ld-ref" href="#/a/${x.pay}">${IC.pay}支払い ${yen(p.amt)} を開く</a>`;
  const none='<span class="faint">カテゴリの既定</span>',over=tag('この支出だけ',{cls:'brand'});
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:`ldMenu('${id}')`}]})
    +`<div class="ax">`+dayHead(date,{href:`#/day/${date}`,diary:dayDiary(date)})+focusRow({time:p.time,node:{cls:'pay',icon:'expense'},card})+`</div>`
    +sh('分類')
    +list([
      `<button class="prop ld-tap" type="button" onclick="ldCatPick('${id}')"><span class="k">カテゴリ</span><span class="val">${catTag(x.cat)}</span><span class="meta">${IC.chev}</span></button>`,
      prop('固定・変動',ldMini([['固定','固定'],['変動','変動']],xFx(x),`ldSetFx('${id}','$v')`),x.fx!==undefined?over:none),
      prop('特別感',ldMini([['日常','日常'],['非日常','非日常']],xSp(x),`ldSetSp('${id}','$v')`),x.sp!==undefined?over:none),
      prop('メモ',`<input class="ld-in" value="${esc(x.memo||'')}" placeholder="メモを書く" oninput="ldMemo('${id}',this.value)">`)
    ])
    +sh('品目',x.items.length)
    +list(x.items.map(it=>row({t:`${esc(it.n)}${it.memo?` <span class="faint">${esc(it.memo)}</span>`:''}`,s:`${esc(it.b)}${it.unk?` ${tag('内訳不明',{cls:'warn'})}`:''}`,v:yen(it.price),amt:true})));
},{tab:'ledger'});

/* ---------- カテゴリの管理 ---------- */
function ldEditBody(){
  const d=ldDraft,isNew=!ldDraftKey,isKid=!!d.p;
  const parentOpts=['<option value="">いちばん上</option>',...Object.keys(CATS).filter(c=>!d.ban.includes(c)).map(c=>`<option value="${c}" ${c===d.p?'selected':''}>${esc(catPath(c))}</option>`)].join('');
  const sw=COLORS.map(c=>`<button type="button" class="sw" style="--c:${c}" aria-label="色 ${c}" aria-pressed="${d.c===c}" onclick="ldDraftSet('c','${c}')"></button>`).join('');
  const fxItems=isKid?[['inh','親から'],['固定','固定'],['変動','変動']]:[['固定','固定'],['変動','変動']];
  const spItems=isKid?[['inh','親から'],['日常','日常'],['非日常','非日常']]:[['日常','日常'],['非日常','非日常']];
  const fxCur=d.fx==='inh'&&!isKid?'変動':d.fx, spCur=d.sp==='inh'&&!isKid?'日常':d.sp;
  const kids=!isNew&&childrenOf(ldDraftKey).length>0;
  const used=!isNew&&Object.values(X).some(x=>catDesc(ldDraftKey).includes(x.cat));
  const blocked=kids||used;
  return `<div class="list">`
    +`<div class="field"><label>名前</label><input value="${esc(d.n)}" placeholder="例: 外食" oninput="ldDraft.n=this.value"></div>`
    +`<div class="field"><label>親</label><select onchange="ldDraftSet('p',this.value)">${parentOpts}</select></div>`
    +(isKid?'':`<div class="field" style="align-items:flex-start"><label style="padding-top:10px">色</label><div class="ld-sw">${sw}</div></div>`)
    +`<div class="field"><label>固定・変動</label><span class="ld-mini" role="group">${fxItems.map(([v,l])=>`<button type="button" aria-pressed="${fxCur===v}" onclick="ldDraftSet('fx','${v}')">${l}</button>`).join('')}</span></div>`
    +`<div class="field"><label>特別感</label><span class="ld-mini" role="group">${spItems.map(([v,l])=>`<button type="button" aria-pressed="${spCur===v}" onclick="ldDraftSet('sp','${v}')">${l}</button>`).join('')}</span></div>`
    +`</div>`
    +(isNew?'':`<div class="btnrow"><button class="btn danger block" type="button" ${blocked?'disabled':''} onclick="ldDelCat()">${IC.trash}カテゴリを削除</button></div>`
      +(blocked?`<p class="hint">${kids?'子カテゴリがあるので削除できません。':'使われているので削除できません。'}</p>`:''));
}
const ldDraftSet=(key,val)=>{ldDraft[key]=val;setSheetBody(ldEditBody())};
function ldOpenCat(k){
  const c=CATS[k];ldDraftKey=k;
  ldDraft={n:c.n,p:c.p||'',c:c.c||COLORS[0],fx:c.fx||'inh',sp:c.sp||'inh',ban:catDesc(k)};
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
  if(d.fx!=='inh')o.fx=d.fx;
  if(d.sp!=='inh')o.sp=d.sp;
  CATS[key]=o;
  closeSheet();save();rerender();toast(`「${n}」を${isNew?'作成':'保存'}しました`);
}
function ldDelCat(){
  const k=ldDraftKey,c=CATS[k];
  if(childrenOf(k).length||Object.values(X).some(x=>catDesc(k).includes(x.cat))){toast('使われているので削除できません');return}
  delete CATS[k];closeSheet();save();rerender();
  toast(`「${c.n}」を削除しました`,()=>{CATS[k]=c});
}

route('cats',()=>{
  const sumOf=k=>ldSum(x=>catAnc(x.cat).includes(k));
  const defs=k=>{
    const parts=[`<span class="${CATS[k].fx?'':'faint'}">${catFx(k)}</span>`];
    if(catSp(k)==='非日常')parts.push(`<span class="${CATS[k].sp?'':'faint'}">非日常</span>`);
    return parts.join(' · ');
  };
  const tree=(k,d)=>[row({onclick:`ldOpenCat('${k}')`,lead:d?'':ldSq(k),t:esc(CATS[k].n),s:defs(k),v:yen(sumOf(k)),amt:true,chev:true,cls:depthCls(k)}),
    ...childrenOf(k).flatMap(c=>tree(c,d+1))];
  const rows=ROOTS().flatMap(r=>tree(r,0));
  return appbar('カテゴリ',{actions:[{icon:'plus',label:'カテゴリを作成',onclick:'ldNewCat()'}]})
    +sh('すべて',Object.keys(CATS).length)+list(rows);
},{tab:'ledger'});

CREATE.push({id:'expense',label:'支出だけ記録',sub:'金額とカテゴリだけ',icon:'expense',color:'var(--c-expense)',primaryOn:[],onclick:'openQuickExpense()'});
