/* =====================================================================
   エンティティ(縁) — 時間をまたいで続くもの(人・場所・会社・物・書類・プロジェクト)。
   一覧 = 種類ごと(値札)に、最後に交わった順 / 詳細 = フォルダのタブ+その縁の糸で切り取った時間軸
   ルート: #/entities , #/e/{id} , #/n/{id}(メモ) , #/notes(メモの一覧) , #/types(種類)
   見た目は css/s-entities.css(値札・フォルダのタブ・角を折った紙)
   ===================================================================== */
const enRoots=()=>Object.keys(TYPES).filter(k=>!TYPES[k].p);
const enAll=()=>Object.entries(E).filter(([k])=>k!=='me');
const enCount=t=>enAll().filter(([k,e])=>e.t.some(x=>isUnder(x,t))).length;
const enTypePath=t=>typeAnc(t).map(a=>TYPES[a].n).join(' › ');
const enDesc=k=>{const r=[k];let grew=true;while(grew){grew=false;Object.keys(TYPES).forEach(c=>{if(!r.includes(c)&&r.includes(TYPES[c].p)){r.push(c);grew=true}})}return r};
let enKind='all';
/** 種類の値札(一覧の見出し・種類の木) */
const vtag=label=>`<span class="vtag">${esc(label)}</span>`;
/** 詳細のタブ: 種類のパンくず(先頭の種類の祖先をたどる) */
const enTabPath=id=>typeAnc(E[id].t[0]).map(a=>`<a href="#/types">${esc(TYPES[a].n)}</a>`).join('<span class="sep">›</span>');
/** 同じ月の項目を1つの面にまとめる(入力は時間順) */
const enByMonth=items=>items.reduce((out,x)=>{const g=out[out.length-1];if(g&&g.ym===x.ym)g.rows.push(x.html);else out.push({ym:x.ym,rows:[x.html]});return out},[]);
/** 予定の並びの鍵(繰り返しは次の日付)。上=過去・下=未来の昇順に使う */
const evKey=ev=>(ev.rep?nextOn(ev):ev.d)+' '+(ev.all?'00:00':(ev.s||''));
/** 前の月(YYYY-MM) */
const ymPrev=ym=>{const [y,m]=ym.split('-').map(Number);const d=new Date(y,m-2,1);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')};
/** 「これより前を見る」を押した縁(その縁の過去を全部出す) */
const enPastAll=new Set();
function enPastAllOn(id){enPastAll.add(id);rerender()}

/* ---------- 一覧: 種類ごと・最後に交わった順 ---------- */
route('entities',()=>{
  const all=enAll();
  const kinds=[['all','すべて',all.length],...enRoots().map(r=>[r,TYPES[r].n,enCount(r)]).filter(k=>k[2])];
  /* 一覧の見出し = 種類。「すべて」では根の種類、絞り込み中はその下の種類で束ねる */
  const groupOf=e=>{const t=(enKind==='all'?e.t[0]:e.t.find(x=>isUnder(x,enKind)))||e.t[0];
    return enKind==='all'?typeAnc(t)[0]:(typeAnc(t).find(a=>TYPES[a].p===enKind)||t)};
  const shown=all.filter(([k,e])=>enKind==='all'||e.t.some(t=>isUnder(t,enKind)))
    .map(([k,e])=>({k,e,d:lastMet(k),g:groupOf(e)}))
    .sort((a,b)=>(b.d||'').localeCompare(a.d||'')||a.e.n.localeCompare(b.e.n));
  const order=Object.keys(TYPES);
  const keys=[...new Set(shown.map(x=>x.g))].sort((a,b)=>order.indexOf(a)-order.indexOf(b));
  const rowOf=({k,e,d})=>row({href:'#/e/'+k,cls:'en-row',lead:knot(k),t:esc(e.n),s:esc(enTypePath(e.t[0])),
    v:`<span class="en-ago">${agoLabel(d)}${encStrip(k)}</span>`});
  const groups=keys.map(g=>{const rs=shown.filter(x=>x.g===g);
    return `<section class="en-g"><div class="en-gh">${vtag(TYPES[g].n)}<span class="n">${rs.length}件</span></div><div class="objs">${rs.map(rowOf).join('')}</div></section>`}).join('');
  return largeTitle('エンティティ',{actions:[
      {text:'追加',icon:'plus',primary:true,onclick:'openNewEntity()'},
      {label:'エンティティのメモ',icon:'lines',onclick:"go('#/notes')"},
      {label:'種類',icon:'type',onclick:"go('#/types')"}]})
    +`<div class="en-search">${IC.search}<input class="input" type="search" placeholder="名前で探す" aria-label="名前で探す" oninput="enFilter(this.value)"></div>`
    +chips(kinds,enKind,"enKind='$v';rerender()")
    +`<div id="en-groups">${groups||empty('people','エンティティ なし')}</div>`;
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
route('e',id=>{
  const e=E[id];if(!e)return appbar('')+empty('people','エンティティ なし');
  const tc=threadColor(id);
  const acts=actsOf(id).map(([k,a,role])=>({k,a,role,d:dateOf(a)||null})).filter(x=>x.d).sort((x,y)=>(x.d+x.a.time).localeCompare(y.d+y.a.time));
  const plans=Object.entries(EV).filter(([k,ev])=>!ev.rec&&!ev.hol&&ev.d>=TODAY&&((ev.who||[]).includes(id)||(ev.place&&ev.place[0]===id)||(ev.ent||[]).includes(id))).sort((a,b)=>evKey(a[1]).localeCompare(evKey(b[1])));
  /* ToDo: このエンティティに結び付いた未完了のもの(期限の近い順)。人・場所にも同じ節が出る */
  const todos=Object.keys(TD).filter(k=>tdOpen(k)&&(TD[k].ent||[]).includes(id)).sort((a,b)=>(TD[a].due||'9999').localeCompare(TD[b].due||'9999'));
  const yearN=acts.filter(x=>x.d.startsWith(TODAY.slice(0,4))).length;
  const money=acts.filter(x=>x.a.type==='支払い').reduce((s,x)=>s+mineOf(x.a),0);
  /* この人が関わる未精算の貸し(amt>0)と借り(amt<0) */
  const ob=openBalances().filter(([,,b])=>b.with.includes(id));
  const lendOpen=ob.filter(([,,b])=>b.amt>0).reduce((s,[,,b])=>s+b.amt,0), borrowOpen=ob.filter(([,,b])=>b.amt<0).reduce((s,[,,b])=>s-b.amt,0);
  const facts=(e.facts||[]).filter(f=>!f.rare),rare=(e.facts||[]).filter(f=>f.rare);
  const since=f=>{const m=(f.ver||'').match(/(\d{4})-(\d{2})〜/);return m?`${m[1]}年${+m[2]}月から`:''};
  const dl=facts.map(f=>`<dt>${esc(f.k)}</dt><dd>${esc(f.v)}${since(f)?`<span class="since">${since(f)}</span>`:''}</dd>`).join('')
    +relsOf(id).map(([rel,other,period,sym])=>`<dt>${esc(rel)}</dt><dd><span class="rel-dir" title="${sym?'お互い':'一方向'}">${sym?'⟷':'→'}</span>${eref(other)}${period?`<span class="since">${esc(period.replace(/(\d{4})-(\d{2})〜/,(m,y,mo)=>`${y}年${+mo}月から`))}</span>`:''}</dd>`).join('');
  /* 糸の時間軸(v4.7 上=過去・下=未来。いまの線で過去と未来が分かれる): 最近の2か月(前の月と今月)の記録と事実の変化 → いまの線 → これからの予定。
     これより前は一番上の「これより前を見る」で全部。月ごとの面にまとめ、今月の面にいまの線 */
  const pastAll=[...acts.map(x=>({key:x.d+' '+(x.a.time||''),ym:x.d.slice(0,7),html:momentRow(x.k,{exclude:id,showDate:true,role:x.role})})),
    ...factEvents(id).map(f=>({key:f.ym+'-00',ym:f.ym,html:axRow({time:'',node:{cls:'fact'},t:f.t,m:f.m})}))].sort((a,b)=>a.key.localeCompare(b.key));
  const cur=TODAY.slice(0,7),from=ymPrev(cur);
  const past=enPastAll.has(id)?pastAll:pastAll.filter(x=>x.ym>=from);
  const futAll=plans.map(([k,ev])=>({ym:evKey(ev).slice(0,7),html:planRowAx(k,{future:true,showDate:true,exclude:id})}));
  const pastG=enByMonth(past),futG=enByMonth(futAll);
  const rowsOf=(gs,ym)=>{const g=gs.find(x=>x.ym===ym);return g?g.rows.join(''):''};
  const yms=[...new Set([...pastG.map(g=>g.ym),...futG.map(g=>g.ym),...(past.length||futAll.length?[cur]:[])])].sort();
  const faces=yms.map(ym=>{const p=rowsOf(pastG,ym),f=rowsOf(futG,ym);
    return ym>cur?axGroup(monthHeadAx(ym,true),p+f,{future:true}):axGroup(monthHeadAx(ym),ym===cur?p+nowLine()+f:p)}).join('');
  const older=pastAll.length-past.length;
  const top=!pastAll.length?axEdge('記録 なし',true)
    :older?axEdge(`<button type="button" class="ax-more" onclick="enPastAllOn('${id}')">これより前を見る${IC.chev}</button>`,true)
    :axEdge('これより前の記録 なし',true);
  const note=NOTES[id];
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:"toast('編集はモックでは未対応です')"}]})
    +`<section class="obj-e"><div class="tab">${enTabPath(id)}</div>`
    +`<div class="hero"><div class="grow" style="min-width:0"><h1>${esc(e.n)}</h1>`
    +`<div class="kinds">${e.t.map(t=>vtag(TYPES[t].n)).join('')}<button class="vtag add" type="button" onclick="toast('種類の追加はモックでは未対応です')">＋種類</button></div></div></div></section>`
    +`<div class="stats"><div><b>${agoLabel(lastMet(id))}</b><span>最後に交わった</span></div><div><b>${yearN}回</b><span>今年</span></div>${money?`<div><b>${yen(money)}</b><span>${rootOf(id)==='person'?'一緒の時の自分の支出':'ここでの自分の支出'}</span></div>`:''}${lendOpen?`<div><b style="color:var(--owe-text)">${yen(lendOpen)}</b><span>貸し・未精算</span></div>`:''}${borrowOpen?`<div><b style="color:var(--owe-text)">${yen(borrowOpen)}</b><span>借り・未精算</span></div>`:''}</div>`
    +(dl?`<dl class="facts">${dl}</dl>`:'')
    +(rare.length?`<details class="en-more"><summary>ほかの情報 ${rare.length}件</summary><dl class="facts">${rare.map(f=>`<dt>${esc(f.k)}</dt><dd>${esc(f.v)}</dd>`).join('')}</dl></details>`:'')
    +(note?`<a class="en-paper en-memo" href="#/n/${id}"><span class="h">${IC.lines}エンティティのメモ<span class="grow"></span><span class="faint">更新 ${esc(note.upd)}</span></span><p>${esc((note.body[0]?note.body[0][1]:'').replace(/\{[ea]:\w+\}/g,''))}</p></a>`
      :`<button class="en-paper en-memo en-empty" type="button" onclick="enNewNote('${id}')"><span class="h">${IC.lines}エンティティのメモを書く</span></button>`)
    +(todos.length?sh('ToDo',todos.length)+objs(todos.map(enTaskRow)):'')
    +sh('このエンティティの時間軸')
    +`<div class="ax thread" style="--tc:${tc}">`+top+faces
    +`</div>`;
},{tab:'entities'});

/** 詳細のToDo節の1行(チェックで完了。押すとそのToDo) */
function enTaskRow(k){const t=TD[k];
  return row({cls:'en-todo',wrap:true,lead:checkbox(false,`toggleTask('${k}')`),href:'#/task/'+k,t:esc(t.t)+(t.pri?'<span class="pl-pri">!</span>':''),s:t.due?relDay(t.due):'',chev:1})}

/* ---------- メモ: 縁に綴じた文章(角を折った紙) ---------- */
function enNewNote(id){NOTES[id]={upd:`${+TODAY.slice(5,7)}/${+TODAY.slice(8)} ${NOW}`,ver:1,body:[],subs:[]};save();go('#/n/'+id)}
route('n',id=>{
  const e=E[id];if(!e)return appbar('')+empty('note','メモ なし');
  const note=NOTES[id];
  const rich=s=>esc(s).replace(/\{e:(\w+)\}/g,(_,k)=>eref(k)).replace(/\{a:(\w+)\}/g,(_,k)=>aref(k));
  return appbar('',{actions:[{icon:'edit',label:'メモを編集',onclick:"toast('編集はモックでは未対応です')"}]})
    +`<a class="en-owner" href="#/e/${id}">${knot(id,'sm')}<span class="grow ell">${esc(e.n)}</span>${IC.chev}</a>`
    +`<article class="en-paper en-doc"><h1>${esc(e.n)}のメモ</h1><div class="meta">${note?`主観・最新、更新 ${esc(note.upd)}、${note.ver}版目`:'未作成'}</div>`
    +(note&&note.body.length?note.body.map(([h,s])=>`<h3>${esc(h)}</h3><p>${rich(s)}</p>`).join(''):`<p class="faint">本文 なし</p>`)
    +`</article>`
    +(note&&note.subs.length?sh('サブページ',note.subs.length)+objs(note.subs.map(([s,m])=>row({lead:ico('lines'),t:esc(s),v:`<span class="faint">${esc(m)}</span>`,chev:1}))):'');
},{tab:'entities'});

/* ---------- メモの一覧: 縁ごとに1枚の紙 ---------- */
const enNoteKey=u=>{const m=(u||'').match(/(\d+)\/(\d+)\s*(\d+):(\d+)/);return m?((+m[1])*100+(+m[2]))*10000+(+m[3])*100+(+m[4]):0};
route('notes',()=>{
  const ids=Object.keys(NOTES).filter(k=>E[k]).sort((a,b)=>enNoteKey(NOTES[b].upd)-enNoteKey(NOTES[a].upd));
  return appbar('エンティティのメモ')
    +(ids.length?`<div class="en-papers">`+ids.map(id=>{const first=(NOTES[id].body[0]?NOTES[id].body[0][1]:'').replace(/\{[ea]:\w+\}/g,'');
      return `<a class="en-paper en-note" href="#/n/${id}"><span class="nh">${knot(id,'sm')}<b class="ell">${esc(E[id].n)}のメモ</b></span><span class="ln">${esc(first)}</span><span class="dt">${esc(NOTES[id].upd)}</span></a>`}).join('')+`</div>`
      :empty('lines','メモ なし'));
},{tab:'entities'});

/* ---------- 種類: 縁の分類(木)。根の種類が輪郭を決める。ラベルは値札 ---------- */
const enFlipped=new Set();
function enTreeRows(){
  const out=[];
  const walk=(k,d)=>{
    const kids=Object.keys(TYPES).filter(c=>TYPES[c].p===k);
    const open=(d<2)!==enFlipped.has(k);
    const props=propsOf(k).map(p=>p[0]).join('・')||'なし';
    const lead=`${kids.length?`<span class="en-tg" role="button" aria-label="${open?'閉じる':'開く'}" onclick="event.stopPropagation();enToggle('${k}')">${open?IC.down:IC.chev}</span>`:'<span class="en-tg"></span>'}${d===0?'<span class="kn-dot sm" aria-hidden="true"></span>':''}`;
    out.push(`<button type="button" class="row ${d?'ind':''}" style="--d:${d}" onclick="enEditType('${k}')">${lead}<span class="grow"><div class="t ell">${vtag(TYPES[k].n)}</div><div class="s ell">項目 ${esc(props)}</div></span><span class="v faint">${enCount(k)}</span></button>`);
    if(open)kids.forEach(c=>walk(c,d+1));
  };
  enRoots().forEach(r=>walk(r,0));
  return out;
}
function enToggle(k){enFlipped.has(k)?enFlipped.delete(k):enFlipped.add(k);rerender()}
route('types',()=>appbar('種類',{actions:[{icon:'plus',label:'種類を作成',onclick:'enNewType()'}]})+`<div class="mt"></div><div class="objs en-tree">${enTreeRows().join('')}</div>`,{tab:'entities'});
function enParentSelect(k,cur){const ban=k?enDesc(k):[];return `<select id="en-p"><option value="">いちばん上(輪郭を決める)</option>${Object.keys(TYPES).filter(c=>!ban.includes(c)).map(c=>`<option value="${c}" ${c===cur?'selected':''}>${esc(enTypePath(c))}</option>`).join('')}</select>`}
function enEditType(k){
  const t=TYPES[k];const own=TYPE_PROPS[k]||[];const inh=propsOf(k).filter(([p,from])=>from!==k);
  const hasKids=Object.values(TYPES).some(x=>x.p===k);const used=enCount(k);
  const blocked=hasKids?'子の種類があるため削除できません':used?`${used}件で使われているため削除できません`:'';
  const propRows=[...own.map(p=>row({t:esc(p),v:'<span class="faint">この種類</span>'})),...inh.map(([p,from])=>row({t:`<span class="faint">${esc(p)}</span>`,v:`<span class="faint">${esc(TYPES[from].n)}から</span>`}))];
  openSheet({title:'種類を編集',right:{label:'保存',onclick:`enSaveType('${k}')`},
    body:`<div class="list"><div class="field"><label>名前</label><input id="en-n" value="${esc(t.n)}"></div><div class="field"><label>親</label>${enParentSelect(k,t.p)}</div></div>`
      +sh('項目',propRows.length)+(propRows.length?list(propRows):list([row({t:'<span class="faint">項目 なし</span>'})]))
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
