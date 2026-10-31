/* =====================================================================
   人・場所タブ — 一覧・詳細・ノート・種類の管理
   ルート: #/entities(?type=) , #/e/{id} , #/n/{id} , #/notes , #/types
   ===================================================================== */

(function(){
  if(document.getElementById('en-style'))return;
  document.head.insertAdjacentHTML('beforeend',`<style id="en-style">
  .en-search{position:relative;margin:0 var(--gutter) 12px}
  .en-search svg.i{position:absolute;left:14px;top:50%;transform:translateY(-50%);width:18px;height:18px;color:var(--ink-3);pointer-events:none}
  .en-search .input{padding-left:42px}
  .en-dis{padding-bottom:8px}
  .en-hrow{display:flex;align-items:center;gap:14px;min-width:0}
  .en-hname{min-width:0;overflow-wrap:anywhere}
  .en-fx{position:relative}
  .en-fx::before{content:"";position:absolute;top:0;left:14px;right:0;height:1px;background:var(--line)}
  .list>.en-fx:first-child::before{display:none}
  .en-hbtn{min-height:44px;margin-right:-6px;padding:0 6px;font:var(--t-cap);color:var(--brand);white-space:nowrap}
  .en-hist{padding:0 14px 12px 110px;font:var(--t-cap);color:var(--ink-3)}
  .en-hist>div{display:flex;justify-content:space-between;gap:12px;padding:2px 0}
  .en-hist>div>span:first-child{color:var(--ink-2)}
  .en-p{padding:14px;font:var(--t-body);line-height:1.8}
  .en-tr{min-height:0;padding-top:6px;padding-bottom:6px}
  .en-tr+.en-tr::before{left:14px}
  .en-tr.en-first::before{display:none}
  .en-lead{display:flex;align-items:center;align-self:stretch;margin:-6px 0}
  .en-ind{align-self:stretch;flex:none;width:calc(var(--d,0)*20px);background:repeating-linear-gradient(to right,transparent 0 9px,var(--line-strong) 9px 10px,transparent 10px 20px)}
  .en-ph{width:44px;height:44px;flex:none}
  </style>`);
})();

let enIdx={};
const enFlipped=new Set();
const enRoots=()=>Object.keys(TYPES).filter(k=>!TYPES[k].p);
const enAll=()=>Object.entries(E).filter(([k])=>k!=='me');
const enCount=t=>enAll().filter(([k,e])=>e.t.some(x=>isUnder(x,t))).length;
const enTypePath=t=>typeAnc(t).map(a=>TYPES[a].n).join(' › ');
/** k と、その下にある種類すべて */
const enDesc=k=>{const r=[k];let grew=true;while(grew){grew=false;Object.keys(TYPES).forEach(c=>{if(!r.includes(c)&&r.includes(TYPES[c].p)){r.push(c);grew=true}})}return r};
const enEmptyList=text=>list([row({t:`<span class="muted">${text}</span>`})]);

/* ---------- 検索(再描画せず表示を切り替える) ---------- */
function enFilter(v){
  const q=v.trim().toLowerCase();let total=0;
  document.querySelectorAll('.en-grp').forEach(g=>{
    let first=true,n=0;
    g.querySelectorAll('.en-row').forEach(el=>{
      const id=el.getAttribute('href').slice(4);
      const hit=!q||(enIdx[id]||'').includes(q);
      el.style.display=hit?'':'none';
      el.classList.toggle('en-first',hit&&first);
      if(hit){first=false;n++}
    });
    g.style.display=n?'':'none';
    const c=g.querySelector('.sh .n');if(c)c.textContent=n;
    total+=n;
  });
  const none=document.getElementById('en-none');if(none)none.hidden=total>0;
}

/* ---------- 詳細の折りたたみ ---------- */
function enHist(b){const h=b.closest('.en-fx').querySelector('.en-hist');h.hidden=!h.hidden;b.setAttribute('aria-expanded',String(!h.hidden))}
function enMore(b){const r=b.previousElementSibling;r.hidden=!r.hidden;b.textContent=r.hidden?`その他 ${b.dataset.n}件`:'閉じる'}
function enMenu(){
  openSheet({title:'操作',body:list([
    row({lead:glyph('type'),t:'種類の管理',onclick:"closeSheet();go('#/types')",chev:true}),
    row({lead:glyph('entity','edit'),t:'名前や事実を編集',onclick:"closeSheet();toast('編集はモックでは未対応です')"})])});
}

/* ---------- 一覧 ---------- */
route('entities',(id,q)=>{
  const all=enAll();
  const qt=q.type&&TYPES[q.type]?q.type:'';
  const root=qt?typeAnc(qt)[0]:'';
  const roots=enRoots();
  const shown=qt?all.filter(([k,e])=>e.t.some(x=>isUnder(x,qt))):all;
  const groups={};
  shown.forEach(([k,e])=>{const r=typeAnc(e.t[0])[0];(groups[r]=groups[r]||[]).push([k,e])});
  enIdx={};
  shown.forEach(([k,e])=>{enIdx[k]=[e.n,...e.t.map(enTypePath)].join(' ').toLowerCase()});
  const rowFor=([k,e])=>{const more=e.t.length-1;const path=enTypePath(e.t[0])+(more>0?` ほか ${more}`:'');
    return row({cls:'en-tr en-row',href:'#/e/'+k,lead:avatar(k),t:esc(e.n),s:esc(path),v:actsOf(k).length?`<span class="faint">記録 ${actsOf(k).length}</span>`:'',chev:true})};
  const items=[['','すべて',all.length],...roots.filter(r=>enCount(r)>0).map(r=>[r,TYPES[r].n,enCount(r)])];
  const body=roots.filter(r=>groups[r]).map(r=>`<div class="en-grp">${sh(TYPES[r].n,groups[r].length)}${list(groups[r].map(rowFor))}</div>`).join('');
  return largeTitle('人・場所',{actions:[{icon:'note',label:'ノート一覧',onclick:"go('#/notes')"},{icon:'type',label:'種類の管理',onclick:"go('#/types')"}]})
    +(qt&&qt!==root?`<div class="chips en-dis" role="group"><button class="chip" type="button" aria-pressed="true" onclick="go('#/entities?type=${root}')">種類: ${esc(TYPES[qt].n)} ×</button></div>`:'')
    +chips(items,root,"go('#/entities?type=$v')")
    +`<div class="en-search">${IC.search}<input class="input" id="en-q" type="search" placeholder="名前や種類で探す" aria-label="名前や種類で探す" oninput="enFilter(this.value)"></div>`
    +body
    +`<div class="hint" id="en-none" ${shown.length?'hidden':''} style="text-align:center;padding:24px 0">該当する人・場所はありません</div>`;
},{tab:'entities'});

/* ---------- 詳細 ---------- */
route('e',id=>{
  const e=E[id];if(!e)return appbar('')+empty('entity','entity','この人・場所は見つかりません');
  const note=NOTES[id];
  const facts=e.facts||[];
  const fact=f=>{const hist=f.hist||[];
    return `<div class="en-fx">${prop(f.k,esc(f.v),hist.length?`<button class="en-hbtn" type="button" aria-expanded="false" onclick="enHist(this)">履歴 ${hist.length}</button>`:'')}${hist.length?`<div class="en-hist" hidden>${hist.map(h=>`<div><span>${esc(h[0])}</span><span>${esc(h[1])}${h[2]?` · ${esc(h[2])}`:''}</span></div>`).join('')}</div>`:''}</div>`};
  const main=facts.filter(f=>!f.rare),rare=facts.filter(f=>f.rare);
  const factRows=main.map(fact).join('')+(rare.length?`<div class="en-rare" hidden>${rare.map(fact).join('')}</div><button class="more" type="button" data-n="${rare.length}" onclick="enMore(this)">その他 ${rare.length}件</button>`:'');
  const rels=relsOf(id);
  const actKey=a=>(A[a.parent]?A[a.parent].date:'')+' '+(a.time||'');
  const acts=actsOf(id).sort((x,y)=>actKey(y[1]).localeCompare(actKey(x[1])));
  const evs=Object.entries(EV).filter(([k,ev])=>(ev.who||[]).includes(id)||(ev.place&&ev.place[0]===id)).sort((x,y)=>x[1].d.localeCompare(y[1].d));
  const tds=Object.entries(TD).filter(([k,t])=>(t.ent||[]).includes(id));
  const noteRow=note
    ?row({href:'#/n/'+id,lead:glyph('note'),t:esc(note.body[0]?note.body[0][0]:'ノート'),s:`更新 ${esc(note.upd)}`,chev:true})
    :row({onclick:`enNewNote('${id}')`,lead:glyph('note'),t:'ノートを書く',s:'まだ書いていません',chev:true});
  const actRows=acts.map(([k,a,role])=>{const d=A[a.parent]?A[a.parent].date:'';
    return row({href:'#/a/'+k,lead:glyph('activity',ACT_IC[a.type]||'activity'),t:esc(a.title),s:`${d?fmtMD(d)+' ':''}${esc(role)}`,v:a.type==='支払い'&&a.amt!=null?yen(a.amt):undefined,amt:true,chev:true})});
  const evRows=evs.map(([k,ev])=>row({href:'#/ev/'+k,lead:glyph('schedule'),t:esc(ev.t),s:`${fmtMDW(ev.d)}${ev.all?' 終日':' '+esc(ev.s)}`,chev:true}));
  const todoRows=tds.map(([k,t])=>row({lead:checkbox(!!t.done,`if(typeof toggleTask==='function')toggleTask('${k}')`),t:esc(t.t),s:t.due?fmtMDW(t.due):''}));
  return appbar('',{actions:[{icon:'more',label:'その他の操作',onclick:'enMenu()'}]})
    +hero('entity',{kind:`${glyph('entity')}エンティティ`,title:`<span class="en-hrow">${avatar(id,'xl')}<span class="en-hname">${esc(e.n)}</span></span>`,sub:e.t.map(t=>typeTag(t))})
    +sh('事実',facts.length)+list([factRows||row({t:'<span class="muted">まだありません</span>'})])
    +sh('関係',rels.length)+(rels.length?list(rels.map(r=>prop(r[0],eref(r[1]),esc(r[2])))):enEmptyList('関係はまだありません'))
    +sh('ノート')+list([noteRow])
    +sh('登場する記録',acts.length)+(acts.length?list(actRows):enEmptyList('まだありません'))
    +sh('予定',evs.length)+(evs.length?list(evRows):enEmptyList('予定はありません'))
    +sh('ToDo',tds.length)+(tds.length?list(todoRows):enEmptyList('ToDoはありません'));
},{tab:'entities'});

/* ---------- ノート ---------- */
function enNewNote(id){
  const d=new Date();
  NOTES[id]={upd:`${TODAY.slice(5).replace('-','/')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`,ver:1,body:[],subs:[]};
  save();go('#/n/'+id);
}
route('n',id=>{
  const e=E[id];if(!e)return appbar('')+empty('note','note','このノートは見つかりません');
  const note=NOTES[id];
  if(!note)return appbar('')+parentLink('#/e/'+id,avatar(id,'sm'),esc(e.n))+empty('note','note','まだノートはありません',{label:'ノートを書く',onclick:`enNewNote('${id}')`});
  const rich=s=>esc(s).replace(/\{e:(\w+)\}/g,(_,k)=>eref(k)).replace(/\{a:(\w+)\}/g,(_,k)=>aref(k));
  const bodyHtml=note.body.length
    ?note.body.map(([h,s])=>sh(h)+list([`<p class="en-p">${rich(s)}</p>`])).join('')
    :enEmptyList('本文はまだありません');
  return appbar('')
    +parentLink('#/e/'+id,avatar(id,'sm'),esc(e.n))
    +hero('note',{kind:`${glyph('note')}ノート`,title:`${esc(e.n)}のノート`,sub:[`更新 ${esc(note.upd)}`,`版 ${note.ver}`]})
    +bodyHtml
    +sh('サブページ',note.subs.length)
    +(note.subs.length?list(note.subs.map(([s,m])=>row({lead:glyph('note'),t:esc(s),v:`<span class="faint">${esc(m)}</span>`}))):enEmptyList('サブページはありません'));
},{tab:'entities'});

route('notes',()=>{
  const ids=Object.keys(NOTES);
  return appbar('ノート')
    +(ids.length
      ?list(ids.map(k=>row({href:'#/n/'+k,lead:glyph('note'),t:esc(E[k]?E[k].n:k),s:esc(NOTES[k].body[0]?NOTES[k].body[0][1]:'本文はまだありません'),chev:true})))
      :empty('note','note','ノートはまだありません'));
},{tab:'entities'});

/* ---------- 種類の管理 ---------- */
function enParentSelect(k,cur){
  const ban=k?enDesc(k):[];
  return `<select id="en-p"><option value="">(なし・いちばん上)</option>${Object.keys(TYPES).filter(c=>!ban.includes(c)).map(c=>`<option value="${c}" ${c===cur?'selected':''}>${enTypePath(c)}</option>`).join('')}</select>`;
}
function enTreeRows(){
  const out=[];
  const walk=(k,d)=>{
    const kids=Object.keys(TYPES).filter(c=>TYPES[c].p===k);
    const open=(d<2)!==enFlipped.has(k);
    const props=propsOf(k).map(p=>p[0]).join('・')||'なし';
    const toggle=kids.length
      ?`<span class="iconbtn en-tg" role="button" aria-label="${open?'閉じる':'開く'}" onclick="event.stopPropagation();enToggle('${k}')">${open?IC.down:IC.chev}</span>`
      :'<span class="en-ph"></span>';
    out.push(row({cls:'en-tr',onclick:`enEditType('${k}')`,lead:`<span class="en-lead" style="--d:${d}"><span class="en-ind"></span>${toggle}${glyph('type')}</span>`,t:esc(TYPES[k].n),s:`項目: ${esc(props)}`,v:`${enCount(k)}件`}));
    if(open)kids.forEach(c=>walk(c,d+1));
  };
  enRoots().forEach(r=>walk(r,0));
  return out;
}
function enToggle(k){enFlipped.has(k)?enFlipped.delete(k):enFlipped.add(k);rerender()}

route('types',()=>
  appbar('種類',{actions:[{icon:'plus',label:'種類を作成',onclick:'enNewType()'}]})
  +sh('種類',Object.keys(TYPES).length)
  +list(enTreeRows()),
{tab:'entities'});

function enEditType(k){
  const t=TYPES[k];const own=TYPE_PROPS[k]||[];
  const inh=propsOf(k).filter(([p,from])=>from!==k);
  const hasKids=Object.values(TYPES).some(x=>x.p===k);
  const used=enCount(k);
  const blocked=hasKids?'子の種類があるため削除できません':used?`${used}件で使われているため削除できません`:'';
  const propRows=[...own.map(p=>row({t:esc(p),v:'<span class="faint">この種類</span>'})),...inh.map(([p,from])=>row({t:`<span class="faint">${esc(p)}</span>`,v:`<span class="faint">${esc(TYPES[from].n)}から継承</span>`}))];
  openSheet({title:'種類を編集',right:{label:'保存',onclick:`enSaveType('${k}')`},
    body:`<div class="list"><div class="field"><label>名前</label><input id="en-n" value="${esc(t.n)}"></div><div class="field"><label>親</label>${enParentSelect(k,t.p)}</div></div>`
      +sh('項目',propRows.length)+(propRows.length?list(propRows):enEmptyList('項目はありません'))
      +(blocked?`<p class="hint">${blocked}</p>`:''),
    footer:`<button class="btn ghost" type="button" onclick="enNewType('${k}')">子の種類を追加</button><button class="btn danger" type="button" ${blocked?'disabled':''} onclick="enDeleteType('${k}')">削除</button>`});
}
function enNewType(parent=''){
  openSheet({title:'種類を作成',right:{label:'作成',onclick:'enCreateType()'},
    body:`<div class="list"><div class="field"><label>名前</label><input id="en-n" placeholder="例: 居酒屋"></div><div class="field"><label>親</label>${enParentSelect('',parent)}</div></div>`});
}
function enCreateType(){
  const n=$('#en-n').value.trim();if(!n){toast('名前を入れてください');return}
  const p=$('#en-p').value||undefined;
  TYPES[uid('t')]={n,p};save();closeSheet();rerender();toast(`「${n}」を作成しました`);
}
function enSaveType(k){
  const n=$('#en-n').value.trim();if(!n){toast('名前を入れてください');return}
  TYPES[k].n=n;TYPES[k].p=$('#en-p').value||undefined;
  save();closeSheet();rerender();toast(`「${n}」を保存しました`);
}
function enDeleteType(k){
  if(Object.values(TYPES).some(x=>x.p===k)||enCount(k)){toast('使用中・子ありのため削除できません');return}
  const snap=TYPES[k],props=TYPE_PROPS[k];
  delete TYPES[k];if(props)delete TYPE_PROPS[k];
  closeSheet();save();rerender();
  toast(`「${snap.n}」を削除しました`,()=>{TYPES[k]=snap;if(props)TYPE_PROPS[k]=props});
}
