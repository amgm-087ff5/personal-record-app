/* =====================================================================
   人・場所(縁) — 時間をまたいで続くもの。
   一覧 = 最近いつ交わったか / 詳細 = その縁の糸で切り取った時間軸
   ルート: #/entities , #/e/{id} , #/n/{id}(メモ) , #/types(種類)
   ===================================================================== */
document.head.insertAdjacentHTML('beforeend',`<style>
.en-search{margin:0 var(--gutter) 12px;position:relative}
.en-search svg.i{position:absolute;left:12px;top:14px;color:var(--ink-3)}
.en-search input{padding-left:40px}
.en-row .r{display:flex;flex-direction:column;align-items:flex-end;gap:5px;flex:none}
.en-row .r small{font:var(--t-cap);color:var(--ink-3)}
.en-memo{margin:12px var(--gutter) 0;padding:14px 16px 14px 18px;border-radius:var(--r-md);background:var(--surface);box-shadow:var(--e1),var(--hl);position:relative;display:block}
.en-memo::before{content:"";position:absolute;left:0;top:12px;bottom:12px;width:3px;border-radius:0 3px 3px 0;background:var(--tc)}
.en-memo .h{display:flex;align-items:center;gap:6px;font:var(--t-label);color:var(--ink-2)}
.en-memo .h svg.i{width:16px;height:16px}
.en-memo p{margin-top:6px;font:var(--t-body);display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
.en-doc{margin:4px var(--gutter) 0;padding:20px 20px 24px 22px;background:var(--surface);border-radius:var(--r-lg);box-shadow:var(--e2),var(--hl);position:relative}
.en-doc::before{content:"";position:absolute;left:0;top:20px;bottom:20px;width:3px;border-radius:0 3px 3px 0;background:var(--tc)}
.en-doc h1{font:var(--t-title)} .en-doc .meta{font:var(--t-cap);color:var(--ink-3);margin-top:4px}
.en-doc h3{font:600 14px/1.4 var(--font);color:var(--ink-2);margin-top:22px}
.en-doc p{font:400 16px/1.9 var(--font);margin-top:6px}
.en-doc .ref{vertical-align:middle;margin:0 2px}
.en-tree .row{padding-left:calc(10px + var(--d,0)*22px)}
.en-tree .row .lv{position:absolute;left:calc(10px + (var(--d,0) - 1)*22px + 13px);top:0;bottom:0;width:2px;background:var(--line)}
.en-tree .row+.row::before{left:calc(10px + var(--d,0)*22px)}
.en-tg{width:28px;height:28px;display:grid;place-items:center;color:var(--ink-3);flex:none}
.en-tg svg.i{width:16px;height:16px}
.en-dot{width:10px;height:10px;border-radius:3px;background:var(--line-strong);flex:none;margin:0 6px}
</style>`);

const enRoots=()=>Object.keys(TYPES).filter(k=>!TYPES[k].p);
const enAll=()=>Object.entries(E).filter(([k])=>k!=='me');
const enCount=t=>enAll().filter(([k,e])=>e.t.some(x=>isUnder(x,t))).length;
const enTypePath=t=>typeAnc(t).map(a=>TYPES[a].n).join(' › ');
const enDesc=k=>{const r=[k];let grew=true;while(grew){grew=false;Object.keys(TYPES).forEach(c=>{if(!r.includes(c)&&r.includes(TYPES[c].p)){r.push(c);grew=true}})}return r};
let enKind='all';

/** 種類のパンくず(1番目の種類の祖先)+ほかの種類 */
const crumb=id=>{const e=E[id];const [t,...rest]=e.t;return typeAnc(t).map(a=>`<a href="#/types">${esc(TYPES[a].n)}</a>`).join('<span class="sep">›</span>')+(rest.length?`<span class="also">ほか ${rest.map(r=>esc(TYPES[r].n)).join('・')}</span>`:'')};

/* ---------- 一覧: 最近いつ交わったか ---------- */
route('entities',()=>{
  const all=enAll();
  const kinds=[['all','すべて',all.length],...enRoots().map(r=>[r,ROOT_LABEL[r]||TYPES[r].n,enCount(r)]).filter(k=>k[2])];
  const shown=all.filter(([k,e])=>enKind==='all'||e.t.some(t=>isUnder(t,enKind)))
    .map(([k,e])=>[k,e,lastMet(k)]).sort((a,b)=>(b[2]||'').localeCompare(a[2]||'')||a[1].n.localeCompare(b[1].n));
  const age=d=>{if(!d)return 3;const n=(new Date(TODAY+'T00:00')-new Date(d+'T00:00'))/864e5;return n<=7?0:n<=31?1:2};
  const groups=[['この1週間に交わった',0],['この1か月',1],['それより前',2],['まだ記録なし',3]];
  const rowOf=([k,e,d])=>row({href:'#/e/'+k,cls:'en-row',lead:knot(k),t:esc(e.n),s:esc(enTypePath(e.t[0])),
    v:`<span class="r">${encStrip(k)}<small>${agoLabel(d)}</small></span>`});
  return largeTitle('人・場所',{actions:[{icon:'type',label:'種類',onclick:"go('#/types')"}]})
    +`<div class="en-search">${IC.search}<input class="input" type="search" placeholder="名前で探す" aria-label="名前で探す" oninput="enFilter(this.value)"></div>`
    +chips(kinds,enKind,"enKind='$v';rerender()")
    +`<div id="en-groups">${groups.map(([label,g])=>{const rs=shown.filter(x=>age(x[2])===g);return rs.length?`<div class="en-g">${sh(label,rs.length)}${list(rs.map(rowOf))}</div>`:''}).join('')}</div>`;
},{tab:'entities'});
function enFilter(v){
  v=v.trim();document.querySelectorAll('#en-groups .en-g').forEach(g=>{let n=0;g.querySelectorAll('.row').forEach(r=>{const hit=!v||r.querySelector('.t').textContent.includes(v);r.hidden=!hit;if(hit)n++});g.hidden=!n});
}

/* ---------- 詳細: その縁の糸 ---------- */
/** 事実・関係の変化を時間軸上の点にする */
function factEvents(id){
  const e=E[id];const out=[];
  const since=s=>{const m=(s||'').match(/(\d{4}-\d{2})〜/);return m?m[1]:null};
  (e.facts||[]).forEach(f=>{if(!f.hist)return;
    const chain=[[f.v,since(f.ver)],...f.hist.map(h=>[h[0],since(h[1])])];
    for(let i=0;i<chain.length-1;i++){const [nv,when]=chain[i];const [ov]=chain[i+1];const reason=f.hist[i]&&f.hist[i][2];
      if(when)out.push({ym:when,t:`${esc(f.k)}が変わった`,m:`<span class="ell">${esc(ov)} → ${esc(nv)}${reason?`(${esc(reason)})`:''}</span>`})}
  });
  relsOf(id).forEach(([rel,other,period])=>{const m=(period||'').match(/(\d{4}-\d{2})/);if(m)out.push({ym:m[1],t:`${esc(rel)}: ${esc(E[other].n)}`,m:`${knot(other,'sm')}<span class="ell">関係が始まった</span>`})});
  return out;
}
function monthHead(ym){return `<div class="dh"><span class="d"><b>${+ym.slice(5)}<small style="font-size:12px;font-weight:600">月</small></b><i>${ym.slice(0,4)}</i></span><span class="tick" style="background:var(--axis-past)"></span><span class="c"></span></div>`}
route('e',id=>{
  const e=E[id];if(!e)return appbar('')+empty('people','この人・場所は見つかりません');
  const tc=threadColor(id);
  const acts=actsOf(id).map(([k,a,role])=>({k,a,role,d:a.parent&&A[a.parent]?A[a.parent].date:null})).filter(x=>x.d).sort((x,y)=>(y.d+y.a.time).localeCompare(x.d+x.a.time));
  const plans=Object.entries(EV).filter(([k,ev])=>!ev.rec&&!ev.hol&&ev.d>=TODAY&&((ev.who||[]).includes(id)||(ev.place&&ev.place[0]===id))).sort((a,b)=>b[1].d.localeCompare(a[1].d));
  const yearN=acts.filter(x=>x.d.startsWith(TODAY.slice(0,4))).length;
  const money=acts.filter(x=>x.a.type==='支払い').reduce((s,x)=>s+(x.a.x&&X[x.a.x]?X[x.a.x].amt:0),0);
  const owed=Object.values(A).filter(a=>a.adv&&a.adv[0]===id&&a.adv[2]==='未精算').reduce((s,a)=>s+a.adv[1],0);
  const facts=(e.facts||[]).filter(f=>!f.rare),rare=(e.facts||[]).filter(f=>f.rare);
  const since=f=>{const m=(f.ver||'').match(/(\d{4})-(\d{2})〜/);return m?`${m[1]}年${+m[2]}月から`:''};
  const dl=facts.map(f=>`<dt>${esc(f.k)}</dt><dd>${esc(f.v)}${since(f)?`<span class="since">${since(f)}</span>`:''}</dd>`).join('')
    +relsOf(id).map(([rel,other,period])=>`<dt>${esc(rel)}</dt><dd>${eref(other)}${period?`<span class="since">${esc(period.replace(/(\d{4})-(\d{2})〜/,(m,y,mo)=>`${y}年${+mo}月から`))}</span>`:''}</dd>`).join('');
  /* 糸の時間軸: 予定(点線) → いま → 出会い・事実の変化を新しい順に。月ごとに区切る */
  const evs=[...acts.map(x=>({key:x.d+' '+(x.a.time||''),ym:x.d.slice(0,7),html:momentRow(x.k,{exclude:id,showDate:true})})),
    ...factEvents(id).map(f=>({key:f.ym+'-00',ym:f.ym,html:axRow({time:'',node:{cls:'fact'},t:f.t,m:f.m})}))].sort((a,b)=>b.key.localeCompare(a.key));
  let lastYm='';const body=evs.map(ev=>{let h='';if(ev.ym!==lastYm){lastYm=ev.ym;h=monthHead(ev.ym)}return h+ev.html}).join('');
  const note=NOTES[id];
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:"toast('編集はモックでは未対応です')"}]})
    +`<section class="eh">${knot(id,'lg')}<div class="grow" style="min-width:0"><h1>${esc(e.n)}</h1><div class="crumb">${crumb(id)}</div></div></section>`
    +`<div class="stats"><div><b>${agoLabel(lastMet(id))}</b><span>最後に交わった</span></div><div><b>${yearN}回</b><span>今年</span></div>${money?`<div><b>${yen(money)}</b><span>${rootOf(id)==='person'?'一緒の時の自分の支出':'ここでの自分の支出'}</span></div>`:''}${owed?`<div><b style="color:var(--warn)">${yen(owed)}</b><span>立替・未精算</span></div>`:''}</div>`
    +(dl?`<dl class="facts">${dl}</dl>`:'')
    +(rare.length?`<details class="list mt" style="overflow:hidden"><summary class="more" style="border:0;list-style:none;cursor:pointer">ほかの情報 ${rare.length}件</summary><dl class="facts" style="margin:0;box-shadow:none;border-radius:0;border-top:1px solid var(--line)">${rare.map(f=>`<dt>${esc(f.k)}</dt><dd>${esc(f.v)}</dd>`).join('')}</dl></details>`:'')
    +(note?`<a class="en-memo" href="#/n/${id}" style="--tc:${tc}"><span class="h">${IC.note}メモ<span class="grow"></span><span class="faint">更新 ${esc(note.upd)}</span></span><p>${esc((note.body[0]?note.body[0][1]:'').replace(/\{[ea]:\w+\}/g,''))}</p></a>`
      :`<a class="en-memo" href="javascript:void 0" onclick="enNewNote('${id}')" style="--tc:${tc}"><span class="h">${IC.note}メモを書く</span></a>`)
    +sh('この縁の時間軸',acts.length?`交わった ${acts.length}回`:'')
    +`<div class="ax thread" style="--tc:${tc}">`
    +(plans.length?plans.map(([k])=>planRowAx(k,{future:true})).join('')+nowLine():'')
    +(body||axEdge('まだ記録はありません',true))
    +(body?axEdge('これより前の記録はありません',true):'')
    +`</div>`;
},{tab:'entities'});

/* ---------- メモ: 縁に綴じた文章 ---------- */
function enNewNote(id){NOTES[id]={upd:`${+TODAY.slice(5,7)}/${+TODAY.slice(8)} ${NOW}`,ver:1,body:[],subs:[]};save();go('#/n/'+id)}
route('n',id=>{
  const e=E[id];if(!e)return appbar('')+empty('note','このメモは見つかりません');
  const note=NOTES[id];
  const rich=s=>esc(s).replace(/\{e:(\w+)\}/g,(_,k)=>eref(k)).replace(/\{a:(\w+)\}/g,(_,k)=>aref(k));
  return appbar('',{actions:[{icon:'edit',label:'メモを編集',onclick:"toast('編集はモックでは未対応です')"}]})
    +`<a class="parent" href="#/e/${id}">${knot(id,'sm')}<b class="grow ell">${esc(e.n)}</b>${IC.chev}</a>`
    +`<article class="en-doc" style="--tc:${threadColor(id)}"><h1>${esc(e.n)}のメモ</h1><div class="meta">${note?`更新 ${esc(note.upd)}、${note.ver}版目`:'まだ書いていません'}</div>`
    +(note&&note.body.length?note.body.map(([h,s])=>`<h3>${esc(h)}</h3><p>${rich(s)}</p>`).join(''):`<p class="faint">本文はまだありません</p>`)
    +`</article>`
    +(note&&note.subs.length?sh('サブページ',note.subs.length)+list(note.subs.map(([s,m])=>row({lead:ico('note'),t:esc(s),v:`<span class="faint">${esc(m)}</span>`,chev:1}))):'');
},{tab:'entities'});

/* ---------- 種類: 縁の分類(木)。根の種類が輪郭を決める ---------- */
const enFlipped=new Set();
function enTreeRows(){
  const out=[];
  const walk=(k,d)=>{
    const kids=Object.keys(TYPES).filter(c=>TYPES[c].p===k);
    const open=(d<2)!==enFlipped.has(k);
    const props=propsOf(k).map(p=>p[0]).join('・')||'なし';
    const root=typeAnc(k)[0];
    const lead=`${d?'<span class="lv"></span>':''}${kids.length?`<span class="en-tg" role="button" aria-label="${open?'閉じる':'開く'}" onclick="event.stopPropagation();enToggle('${k}')">${open?IC.down:IC.chev}</span>`:'<span class="en-tg"></span>'}${d===0?`<span class="kn sm ${ROOT_SHAPE[root]||'thing'}"><b>${esc(TYPES[k].n[0])}</b></span>`:'<span class="en-dot"></span>'}`;
    out.push(`<button type="button" class="row" style="--d:${d}" onclick="enEditType('${k}')">${lead}<span class="grow"><div class="t ell">${esc(TYPES[k].n)}</div><div class="s ell">項目 ${esc(props)}</div></span><span class="v faint">${enCount(k)}</span></button>`);
    if(open)kids.forEach(c=>walk(c,d+1));
  };
  enRoots().forEach(r=>walk(r,0));
  return out;
}
function enToggle(k){enFlipped.has(k)?enFlipped.delete(k):enFlipped.add(k);rerender()}
route('types',()=>appbar('種類',{actions:[{icon:'plus',label:'種類を作成',onclick:'enNewType()'}]})+list(enTreeRows(),'en-tree mt'),{tab:'entities'});
function enParentSelect(k,cur){const ban=k?enDesc(k):[];return `<select id="en-p"><option value="">いちばん上(輪郭を決める)</option>${Object.keys(TYPES).filter(c=>!ban.includes(c)).map(c=>`<option value="${c}" ${c===cur?'selected':''}>${esc(enTypePath(c))}</option>`).join('')}</select>`}
function enEditType(k){
  const t=TYPES[k];const own=TYPE_PROPS[k]||[];const inh=propsOf(k).filter(([p,from])=>from!==k);
  const hasKids=Object.values(TYPES).some(x=>x.p===k);const used=enCount(k);
  const blocked=hasKids?'子の種類があるため削除できません':used?`${used}件で使われているため削除できません`:'';
  const propRows=[...own.map(p=>row({t:esc(p),v:'<span class="faint">この種類</span>'})),...inh.map(([p,from])=>row({t:`<span class="faint">${esc(p)}</span>`,v:`<span class="faint">${esc(TYPES[from].n)}から</span>`}))];
  openSheet({title:'種類を編集',right:{label:'保存',onclick:`enSaveType('${k}')`},
    body:`<div class="list"><div class="field"><label>名前</label><input id="en-n" value="${esc(t.n)}"></div><div class="field"><label>親</label>${enParentSelect(k,t.p)}</div></div>`
      +sh('項目',propRows.length)+(propRows.length?list(propRows):list([row({t:'<span class="faint">項目はありません</span>'})]))
      +(blocked?`<p class="hint">${blocked}</p>`:''),
    footer:`<button class="btn tonal" type="button" onclick="enNewType('${k}')">子の種類を追加</button><button class="btn danger" type="button" ${blocked?'disabled':''} onclick="enDeleteType('${k}')">削除</button>`});
}
function enNewType(parent=''){openSheet({title:'種類を作成',right:{label:'作成',onclick:'enCreateType()'},body:`<div class="list mt"><div class="field"><label>名前</label><input id="en-n" placeholder="例: 居酒屋"></div><div class="field"><label>親</label>${enParentSelect('',parent)}</div></div>`})}
function enCreateType(){const n=document.getElementById('en-n').value.trim();if(!n){toast('名前を入れてください');return}TYPES[uid('t')]={n,p:document.getElementById('en-p').value||undefined};save();closeSheet();rerender();toast(`「${n}」を作成しました`)}
function enSaveType(k){const n=document.getElementById('en-n').value.trim();if(!n){toast('名前を入れてください');return}TYPES[k].n=n;TYPES[k].p=document.getElementById('en-p').value||undefined;save();closeSheet();rerender();toast(`「${n}」を保存しました`)}
function enDeleteType(k){
  if(Object.values(TYPES).some(x=>x.p===k)||enCount(k)){toast('使用中・子ありのため削除できません');return}
  const snap=TYPES[k],props=TYPE_PROPS[k];delete TYPES[k];if(props)delete TYPE_PROPS[k];
  closeSheet();save();rerender();toast(`「${snap.n}」を削除しました`,()=>{TYPES[k]=snap;if(props)TYPE_PROPS[k]=props});
}
