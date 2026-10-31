/* =====================================================================
   家計簿タブ — 月の支出(自分の負担)。ルート: #/ledger , #/x/{id} , #/cats
   ===================================================================== */
document.head.insertAdjacentHTML('beforeend',`<style>
.ld-sq{width:12px;height:12px;border-radius:3px;background:var(--cc);flex:none}
.ld-month{display:flex;align-items:center;justify-content:center;gap:8px;padding:0 var(--gutter) 10px}
.ld-month b{font:600 17px/1.4 var(--font);min-width:132px;text-align:center}
.ld-tot{padding:14px 14px 12px;display:flex;flex-direction:column;gap:10px}
.ld-lbl{font:var(--t-label);color:var(--ink-2)}
.ld-r{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.ld-big{font:700 32px/1.15 var(--font);letter-spacing:-.01em;font-variant-numeric:tabular-nums}
.ld-3{display:grid;grid-template-columns:repeat(3,1fr);border-top:1px solid var(--line)}
.ld-3 button{display:flex;flex-direction:column;gap:4px;align-items:flex-start;padding:12px;min-height:60px;text-align:left;border-left:1px solid var(--line)}
.ld-3 button:first-child{border-left:0}
.ld-3 button.on{background:var(--brand-soft)}
.ld-3 .k{font:var(--t-cap);color:var(--ink-3)}
.ld-3 .v{font:600 15px/1.3 var(--font);font-variant-numeric:tabular-nums}
.ld-chart{padding:12px 14px 14px;display:flex;flex-direction:column;gap:10px}
.ld-svg{width:100%;height:auto;display:block;overflow:visible}
.ld-svg .ax{font:500 12px var(--font);fill:var(--ink-3)}
.ld-svg .ax.on{fill:var(--ink);font-weight:600}
.ld-svg .vl{font:600 12px var(--font);fill:var(--ink)}
.ld-svg .avg{font:500 11px var(--font);fill:var(--ink-2);paint-order:stroke;stroke:var(--surface);stroke-width:4px;stroke-linejoin:round}
.ld-svg .avl{fill:none;stroke:var(--ink-2);stroke-width:1;stroke-dasharray:3 3}
.ld-legend{display:flex;flex-wrap:wrap;gap:6px 14px;font:var(--t-cap);color:var(--ink-2)}
.ld-legend span{display:inline-flex;align-items:center;gap:6px}
.ld-legend i{width:8px;height:8px;border-radius:2px;background:var(--cc)}
.ld-row.on{background:var(--brand-soft)}
.ld-sub{padding-left:34px}
.ld-m{width:100%;margin-top:6px}
.ld-pct{color:var(--ink-3);font-weight:400;font-size:12px;margin-left:6px}
.ld-tap{width:100%;text-align:left}
.ld-mini{display:inline-flex;padding:2px;background:var(--sunk);border-radius:8px;gap:2px}
.ld-mini button{height:30px;padding:0 12px;border-radius:6px;font:var(--t-label);color:var(--ink-2)}
.ld-mini button[aria-pressed="true"]{background:var(--surface);color:var(--ink);box-shadow:var(--e1);font-weight:600}
.ld-in{flex:1;min-width:0;height:40px;font:var(--t-body);color:var(--ink);outline:none}
.ld-in::placeholder{color:var(--ink-3)}
.ld-d1{padding-left:30px}
.ld-d2{padding-left:48px}
.ld-note{font:var(--t-cap);color:var(--ink-3)}
</style>`);

/* ---------- 共通の計算 ---------- */
let ldAxis='cat', ldFilter=null, ldM=5;
const ldSum=f=>Object.values(X).filter(f).reduce((a,x)=>a+x.amt,0);
const ldTotal=m=>Object.values(m).reduce((a,b)=>a+b,0);
const ldMap=()=>{const m={};ROOTS().forEach(r=>m[r]=0);Object.values(X).forEach(x=>{m[catAnc(x.cat)[0]]+=x.amt});return m};
/** 6か月分: [年月, ラベル, 大分類ごとの合計]。最後が10月(明細あり) */
const ldMonths=()=>[...TREND.map((m,i)=>[`2026-${String(5+i).padStart(2,'0')}`,m[0],m[1]]),['2026-10','10月',ldMap()]];
const ldAvg=()=>Math.round(TREND.reduce((a,m)=>a+ldTotal(m[1]),0)/TREND.length);
const ldMatch=x=>{if(!ldFilter)return true;const {ax,k}=ldFilter;return ax==='cat'?catAnc(x.cat).includes(k):ax==='fx'?xFx(x)===k:xSp(x)===k};
function ldPick(ax,k,name,setAx){if(setAx)ldAxis=ax;const same=ldFilter&&ldFilter.ax===ax&&ldFilter.k===k;ldFilter=same?null:{ax,k,name};rerender()}
function ldAxisSet(a){ldAxis=a;ldFilter=null;rerender()}
function ldClear(){ldFilter=null;rerender()}
function ldStep(d){ldM=Math.min(5,Math.max(0,ldM+d));ldFilter=null;rerender()}

/** 推移の積み上げ棒(SVG)。最後の棒が今月 */
function ldChart(months){
  const roots=ROOTS(),W=340,H=128,n=months.length,bw=36,gap=(W-bw*n)/(n-1);
  const tots=months.map(m=>ldTotal(m[2]));
  const avg=ldAvg();
  const max=Math.max(...tots,avg)*1.14;
  const Y=v=>H-v/max*H;
  const rp=(x,y,w,h,r)=>r<=0?`M${x} ${y}h${w}v${h}h${-w}Z`:`M${x} ${y+h}V${y+r}Q${x} ${y} ${x+r} ${y}H${x+w-r}Q${x+w} ${y} ${x+w} ${y+r}V${y+h}Z`;
  let g='';
  months.forEach((m,i)=>{
    const x0=i*(bw+gap),isCur=i===n-1,segs=roots.filter(r=>m[2][r]>0);
    let base=H,body='';
    segs.forEach((r,j)=>{
      const h=m[2][r]/max*H,sh=Math.max(h-2,1),top=j===segs.length-1;
      const y=base-h+1;
      body+=`<path d="${rp(x0,y,bw,sh,top?Math.min(6,sh/2):0)}" fill="${catColor(r)}"/>`;
      base-=h;
    });
    g+=`<g opacity="${isCur?1:.55}">${body}</g>`
      +`<text class="ax${isCur?' on':''}" x="${x0+bw/2}" y="${H+18}" text-anchor="middle">${m[1]}</text>`
      +(isCur?`<text class="vl" x="${x0+bw}" y="${Y(tots[i])-7}" text-anchor="end">${yen(tots[i])}</text>`:'');
  });
  return `<svg class="ld-svg" viewBox="0 -18 ${W} ${H+36}" role="img" aria-label="月々の推移、${n}か月">${g}`
    +`<line class="avl" x1="0" x2="${W}" y1="${Y(avg)}" y2="${Y(avg)}"/><text class="avg" x="0" y="${Y(avg)-5}">平均 ${yen(avg)}</text></svg>`;
}

/* ---------- 家計簿(月) ---------- */
route('ledger',()=>{
  const months=ldMonths(),[ym,,cur]=months[ldM],isNow=ldM===months.length-1;
  const tot=ldTotal(cur),prevT=ldTotal(TREND[TREND.length-1][1]);
  const diff=Math.round((tot-prevT)/prevT*100);
  const roots=ROOTS(),share=roots.filter(r=>cur[r]>0);
  const fixed=ldSum(x=>xFx(x)==='固定'),sp=ldSum(x=>xSp(x)==='非日常');
  const nav=`<div class="ld-month"><button class="iconbtn" type="button" aria-label="前の月" onclick="ldStep(-1)" ${ldM===0?'disabled':''}>${IC.back}</button><b class="num">${ym.slice(0,4)}年${+ym.slice(5)}月</b><button class="iconbtn" type="button" aria-label="次の月" onclick="ldStep(1)" ${isNow?'disabled':''}>${IC.chev}</button></div>`;
  const bar=`<div class="bar100">${share.map(r=>`<i style="flex:${cur[r]};background:${catColor(r)}"></i>`).join('')}</div>`;
  const three=isNow?`<div class="ld-3">${[['固定費','fx','固定',fixed],['変動費','fx','変動',tot-fixed],['非日常','sp','非日常',sp]].map(([l,ax,k,v])=>`<button type="button" class="${ldFilter&&ldFilter.ax===ax&&ldFilter.k===k?'on':''}" onclick="ldPick('${ax}','${k}','${l}',1)"><span class="k">${l}</span><span class="v num">${yen(v)}</span></button>`).join('')}</div>`:'';
  const summary=list([`<div class="ld-tot"><div class="ld-lbl">自分の支出</div><div class="ld-r"><span class="ld-big num">${yen(tot)}</span><span class="tag">前月比 ${diff>0?'+':''}${diff}%</span></div>${bar}</div>${three}`]);
  const legendRoots=roots.filter(r=>months.some(m=>m[2][r]>0));
  const chart=list([`<div class="ld-chart"><div class="ld-legend">${legendRoots.map(r=>`<span style="--cc:${catColor(r)}"><i></i>${esc(CATS[r].n)}</span>`).join('')}</div>${ldChart(months)}</div>`]);

  /* 内訳(今月だけ) */
  const brow=(label,v,ax,k,chip,color,opt={})=>{const on=ldFilter&&ldFilter.ax===ax&&ldFilter.k===k;
    return row({cls:`ld-row${opt.sub?' ld-sub':''}${on?' on':''}`,onclick:`ldPick('${ax}','${k}','${esc(chip)}')`,lead:`<span class="ld-sq" style="--cc:${color}"></span>`,t:esc(label),s:`<span class="meter ld-m"><i style="width:${(v/tot*100).toFixed(1)}%;background:${color}"></i></span>`,v:`${yen(v)}<span class="ld-pct">${Math.round(v/tot*100)}%</span>`,amt:true})};
  const catSum=k=>ldSum(x=>catAnc(x.cat).includes(k));
  const byV=(a,b)=>catSum(b)-catSum(a);
  let rowsB='';
  if(ldAxis==='cat'){
    roots.filter(catSum).sort(byV).forEach(r=>{
      rowsB+=brow(CATS[r].n,catSum(r),'cat',r,catPath(r),catColor(r));
      (ldFilter&&ldFilter.ax==='cat'&&catAnc(ldFilter.k)[0]===r?Object.keys(CATS).filter(c=>CATS[c].p===r&&catSum(c)):[]).sort(byV).forEach(c=>{rowsB+=brow(CATS[c].n,catSum(c),'cat',c,catPath(c),catColor(c),{sub:1})});
    });
  }else if(ldAxis==='fx'){
    rowsB=brow('固定',fixed,'fx','固定','固定費','var(--c-schedule)')+brow('変動',tot-fixed,'fx','変動','変動費','var(--c-activity)');
  }else{
    rowsB=brow('日常',tot-sp,'sp','日常','日常','var(--c-type)')+brow('非日常',sp,'sp','非日常','非日常','var(--special)');
  }
  const breakdown=isNow?sh('内訳')+seg([['cat','カテゴリ'],['fx','固定・変動'],['sp','特別感']],ldAxis,"ldAxisSet('$v')")+list([rowsB]):'';

  /* これからの固定費(今月だけ) */
  const up=Object.entries(EV).filter(([k,e])=>e.pay&&e.rep).map(([k,e])=>[k,e,nextOn(e)]).sort((a,b)=>a[2].localeCompare(b[2]));
  const upHtml=isNow&&up.length?sh('これからの固定費',up.length)+list(up.map(([k,e,d])=>row({href:`#/ev/${k}`,lead:glyph('schedule'),t:esc(e.t),s:relDay(d),v:yen(e.pay.amt),amt:true,chev:true}))):'';

  /* 支出一覧(日付ごと) */
  let ledger='';
  if(isNow){
    const fl=Object.entries(X).filter(([k,x])=>ldMatch(x)).sort((a,b)=>xDate(b[1]).localeCompare(xDate(a[1])));
    const days={};fl.forEach(([k,x])=>{const d=xDate(x);(days[d]=days[d]||[]).push([k,x])});
    ledger=sh('支出一覧',fl.length)+(ldFilter?chips([['x',`絞り込み: ${ldFilter.name} ×`]],'x','ldClear()'):'')
      +Object.keys(days).sort().reverse().map(d=>sh(fmtMDW(d))+list([days[d].map(([k,x])=>row({href:`#/x/${k}`,lead:glyph('expense'),t:esc(shopOf(x)),s:`${esc(catPath(x.cat))}${xSp(x)==='非日常'?' <span class="tag special">非日常</span>':''}`,v:yen(x.amt),amt:true})).join('')])).join('');
  }
  const note=isNow?'':banner('info','この月の明細は10月だけ表示しています。推移で月ごとの合計を確認できます。');

  return largeTitle('家計簿',{actions:[{icon:'settings',label:'カテゴリの管理',onclick:"go('#/cats')"}]})
    +nav+note+summary+sh('推移','6か月')+chart+breakdown+upHtml+ledger;
},{tab:'ledger'});

/* ---------- 支出の詳細 ---------- */
const miniSeg=(items,cur,on)=>`<span class="ld-mini" role="group">${items.map(([v,l])=>`<button type="button" aria-pressed="${v===cur}" onclick="${on.replace('$v',v)}">${esc(l)}</button>`).join('')}</span>`;
function ldSetFx(id,v){const x=X[id];x.fx=v===catFx(x.cat)?undefined:v;save();rerender()}
function ldSetSp(id,v){const x=X[id];x.sp=v===catSp(x.cat)?undefined:v;save();rerender()}
function ldSetMemo(id,v){X[id].memo=v.trim();save();rerender()}
function ldSetCat(id,k){X[id].cat=k;closeSheet();save();rerender();toast(`「${CATS[k].n}」に変えました`)}
function ldCatPick(id){
  const x=X[id];
  const item=k=>row({onclick:`ldSetCat('${id}','${k}')`,lead:`<span class="ld-sq" style="--cc:${catColor(k)}"></span>`,t:esc(CATS[k].n),v:k===x.cat?`<span style="color:var(--brand)">${IC.check}</span>`:null,cls:CATS[k].p?'ld-d1':''});
  const rows=ROOTS().flatMap(r=>[item(r),...Object.keys(CATS).filter(c=>CATS[c].p===r).map(item)]);
  openSheet({title:'カテゴリを選ぶ',body:list(rows)});
}
function ldMenu(id){
  openSheet({title:'操作',body:list([
    row({lead:glyph('expense','edit'),t:'編集',onclick:`closeSheet();toast('編集はモックでは未対応です')`}),
    row({lead:glyph('activity','pay'),t:'支払いを開く',href:`#/a/${X[id].pay}`})])});
}

route('x',id=>{
  const x=X[id];if(!x)return appbar('家計簿')+empty('expense','expense','この支出は見つかりません');
  const p=A[x.pay];
  const whoList=whoOf(x).map(n=>{const eid=Object.keys(E).find(k=>E[k].n===n);
    return eid?row({href:`#/e/${eid}`,lead:avatar(eid,'sm'),t:esc(n),chev:true}):row({t:esc(n)})});
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:`ldMenu('${id}')`}]})
    +parentLink(`#/a/${x.pay}`,glyph('activity','pay'),`${esc(p.title)} ${yen(p.amt)}`)
    +hero('expense',{kind:`${glyph('expense')}自分の支出`,big:yen(x.amt),sub:[fmtMDW(xDate(x)),esc(shopOf(x))]})
    +sh('分類')
    +list([
      `<button type="button" class="prop ld-tap" onclick="ldCatPick('${id}')"><span class="k">カテゴリ</span><span class="val">${catTag(x.cat)}</span><span class="meta">${IC.chev}</span></button>`,
      prop('固定・変動',miniSeg([['固定','固定'],['変動','変動']],xFx(x),`ldSetFx('${id}','$v')`),x.fx!==undefined?tag('この支出だけ'):'<span class="faint">カテゴリの既定</span>'),
      prop('特別感',miniSeg([['日常','日常'],['非日常','✦ 非日常']],xSp(x),`ldSetSp('${id}','$v')`),x.sp!==undefined?tag('この支出だけ'):'<span class="faint">カテゴリの既定</span>'),
      prop('メモ',`<input class="ld-in" value="${esc(x.memo||'')}" placeholder="メモを書く" onchange="ldSetMemo('${id}',this.value)">`)
    ])
    +sh('品目',x.items.length)
    +list([x.items.map(it=>row({t:`${esc(it.n)}${it.memo?` <span class="faint">${esc(it.memo)}</span>`:''}`,s:`${esc(it.b)}${it.unk?` ${tag('内訳不明',{cls:'warn'})}`:''}`,v:yen(it.price),amt:true,cls:'plain'})).join('')])
    +sh('一緒にいた人')
    +list(whoList);
},{tab:'ledger'});

/* ---------- カテゴリの管理 ---------- */
document.head.insertAdjacentHTML('beforeend',`<style>
.ld-sw{display:flex;flex-wrap:wrap;gap:10px;padding:6px 0}
.ld-sw .sw{width:32px;height:32px;border-radius:50%;flex:none;box-shadow:inset 0 0 0 1px var(--line-strong)}
.ld-sw .sw.inh{width:auto;padding:0 12px;border-radius:var(--r-pill);background:var(--surface);font:var(--t-label);color:var(--ink-2);box-shadow:inset 0 0 0 1px var(--line-strong);height:32px}
.ld-sw .sw.on{box-shadow:0 0 0 2px var(--surface),0 0 0 4px var(--brand)}
.ld-cat-hint{padding:4px var(--gutter) 0}
</style>`);

let ldDraft=null, ldDraftKey=null;
const catDesc=k=>[k,...Object.keys(CATS).filter(c=>CATS[c].p===k).flatMap(c=>catDesc(c))];
const ldDraftSet=(key,val)=>{ldDraft[key]=val;setSheetBody(ldEditBody())};

function ldEditBody(){
  const d=ldDraft,isNew=!ldDraftKey,isChild=!!d.p;
  const parents=['<option value="">(なし・いちばん上)</option>',...Object.keys(CATS).filter(c=>!d.ban.includes(c)).map(c=>`<option value="${c}" ${c===d.p?'selected':''}>${esc(catPath(c))}</option>`)].join('');
  const colorCur=d.c;
  const sw=[...(isChild?[`<button type="button" class="sw inh ${colorCur==='inh'?'on':''}" onclick="ldDraftSet('c','inh')">親と同じ</button>`]:[]),
    ...COLORS.map(c=>`<button type="button" class="sw ${colorCur===c?'on':''}" style="background:${c}" aria-label="色 ${c}" onclick="ldDraftSet('c','${c}')"></button>`)].join('');
  const fxItems=[...(isChild?[['inh','親と同じ']]:[]),['固定','固定'],['変動','変動']];
  const spItems=[...(isChild?[['inh','親と同じ']]:[]),['日常','日常'],['非日常','✦ 非日常']];
  const fxCur=d.fx==='inh'&&!isChild?'変動':d.fx;
  const spCur=d.sp==='inh'&&!isChild?'日常':d.sp;
  const kids=!isNew&&Object.keys(CATS).some(c=>CATS[c].p===ldDraftKey);
  const used=!isNew&&Object.values(X).some(x=>catDesc(ldDraftKey).includes(x.cat));
  const blocked=kids||used;
  return `<div class="list">
    <div class="field"><label>名前</label><input value="${esc(d.n)}" placeholder="例: 外食" oninput="ldDraft.n=this.value"></div>
    <div class="field"><label>親</label><select onchange="ldDraftSet('p',this.value)">${parents}</select></div>
    <div class="field" style="align-items:flex-start"><label style="padding-top:8px">色</label><div class="ld-sw">${sw}</div></div>
    <div class="field"><label>固定・変動</label><span class="ld-mini" role="group">${fxItems.map(([v,l])=>`<button type="button" aria-pressed="${fxCur===v}" onclick="ldDraftSet('fx','${v}')">${l}</button>`).join('')}</span></div>
    <div class="field"><label>特別感</label><span class="ld-mini" role="group">${spItems.map(([v,l])=>`<button type="button" aria-pressed="${spCur===v}" onclick="ldDraftSet('sp','${v}')">${l}</button>`).join('')}</span></div>
  </div>
  <p class="hint">${isChild?'子カテゴリは親の色・固定・特別感を引き継ぎます。':'固定・変動と特別感は、支出ごとに変えられます。'}</p>
  ${isNew?'':`<div class="btnrow"><button class="btn danger block" type="button" ${blocked?'disabled':''} onclick="ldDelCat()">${IC.trash}カテゴリを削除</button></div>
  ${blocked?`<p class="hint">${kids?'子カテゴリがあるため削除できません。先に子を移動してください。':'使用中の支出があるため削除できません。'}</p>`:''}`}`;
}
function ldOpenCat(k){
  const c=CATS[k];ldDraftKey=k;
  ldDraft={n:c.n,p:c.p||'',c:c.c||(c.p?'inh':COLORS[0]),fx:c.fx||'inh',sp:c.sp||'inh',ban:catDesc(k)};
  openSheet({title:'カテゴリを編集',left:{label:'閉じる',onclick:'closeSheet()'},right:{label:'保存',onclick:'ldSaveCat()'},body:ldEditBody()});
}
function ldOpenNew(){
  ldDraftKey=null;
  ldDraft={n:'',p:'',c:COLORS[0],fx:'inh',sp:'inh',ban:[]};
  openSheet({title:'カテゴリを作成',left:{label:'閉じる',onclick:'closeSheet()'},right:{label:'作成',onclick:'ldSaveCat()'},body:ldEditBody()});
}
function ldSaveCat(){
  const d=ldDraft,n=(d.n||'').trim();
  if(!n){toast('名前を入れてください');return}
  const k=ldDraftKey||uid('cat'),o={n};
  if(d.p)o.p=d.p;
  const col=d.p&&d.c==='inh'?undefined:(d.c==='inh'?COLORS[0]:d.c);
  if(col)o.c=col;
  if(d.fx&&d.fx!=='inh')o.fx=d.fx;
  if(d.sp&&d.sp!=='inh')o.sp=d.sp;
  CATS[k]=o;
  closeSheet();save();rerender();toast(`「${n}」を${ldDraftKey?'保存':'作成'}しました`);
}
function ldDelCat(){
  const k=ldDraftKey,c=CATS[k];
  const kids=Object.keys(CATS).some(x=>CATS[x].p===k),used=Object.values(X).some(x=>catDesc(k).includes(x.cat));
  if(kids||used){toast(kids?'子カテゴリがあるため削除できません':'使用中のため削除できません');return}
  delete CATS[k];closeSheet();save();rerender();
  toast(`「${c.n}」を削除しました`,()=>{CATS[k]=c});
}

route('cats',()=>{
  const sumC=k=>ldSum(x=>catAnc(x.cat).includes(k));
  const tree=(k,d)=>[row({onclick:`ldOpenCat('${k}')`,lead:`<span class="ld-sq" style="--cc:${catColor(k)}"></span>`,t:esc(CATS[k].n),
      s:`<span class="faint">${catFx(k)}${catSp(k)==='非日常'?' · ✦非日常':''}</span>`,v:yen(sumC(k)),amt:true,chev:true,cls:d?`ld-d${Math.min(d,2)}`:''}),
    ...Object.keys(CATS).filter(c=>CATS[c].p===k).flatMap(c=>tree(c,d+1))];
  const rows=ROOTS().flatMap(r=>tree(r,0));
  return appbar('カテゴリ',{actions:[{icon:'plus',label:'カテゴリを作成',onclick:'ldOpenNew()'}]})
    
    +sh('すべて',rows.length)+list(rows);
},{tab:'ledger'});

CREATE.push({id:'expense',label:'支出だけ記録',sub:'金額とカテゴリだけで',cls:'expense',icon:'expense',primaryOn:[],onclick:"openQuickExpense()"});
