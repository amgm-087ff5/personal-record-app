/* =====================================================================
   メモ(#/memo) — 気軽に書いて見返す(v4.3 / 11/04 ユーザー指示)
   並び: 大見出し → 書く欄(白い紙)→ ピン留め → 日ごと → 縁のメモ
   紙の形は .en-paper(角を折った紙、css/s-entities.css)。見た目は css/s-memo.css
   共通関数 memoCard(id, o) は他の画面(ホーム)からも使う。
   ===================================================================== */

/* 画面の状態(再描画しても残す) */
let memoDraft={body:'',links:[]};   /* 書く欄の下書き */
let memoPick=false;                 /* 「縁を付ける」の候補を開いているか */
let memoSearchOpen=false,memoQ='';  /* 絞り込み(小文字) */
let memoSheet=null;                 /* 編集シートの作業中の値 {id,body,links,pin} */

const memoEnts=()=>Object.keys(E).filter(k=>k!=='me');
/** メモの検索対象の文字(本文・縁の名前・結び付いた記録の題名) */
const memoText=id=>{const m=MEMOS[id];if(!m)return '';
  return [m.body,...(m.links||[]).map(k=>E[k]?E[k].n:''),m.rec&&A[m.rec]?A[m.rec].title:''].join(' ').toLowerCase()};
const memoHit=id=>!memoQ||memoText(id).includes(memoQ);
const noteLine=id=>(NOTES[id].body[0]?NOTES[id].body[0][1]:'').replace(/\{[ea]:\w+\}/g,'');
const noteText=id=>(E[id].n+' '+noteLine(id)).toLowerCase();
const updKey=u=>{const m=(u||'').match(/(\d+)\/(\d+)\s*(\d+):(\d+)/);return m?[m[1],m[2],m[3],m[4]].map(x=>x.padStart(2,'0')).join(''):''};
/** 縁の参照(クリックできる版と、カードの中の表示だけの版) */
const memoRef=(k,link=true)=>!E[k]?'':link
  ?`<a class="ref" href="#/e/${k}" onclick="event.stopPropagation()">${knot(k,'sm')}${esc(E[k].n)}</a>`
  :`<span class="ref">${knot(k,'sm')}${esc(E[k].n)}</span>`;

/** 結び付いた記録の参照(aref と同じ形。押すと記録。カードを開く操作には伝えない) */
const memoRecRef=id=>`<a class="ref" href="#/a/${id}" onclick="event.stopPropagation()">${IC[ACT_IC[A[id].type]]||IC.activity}${esc(A[id].type==='支払い'?payLabel(A[id]):(A[id].title||fmtMD(A[id].date)))}</a>`;

/** 1件のメモ = 角を折った紙1枚。押すと編集のシート。o.withDate: ピン留めの群のように日付をまたぐ時は日付も出す */
function memoCard(id,o={}){
  const m=MEMOS[id];if(!m)return '';
  const when=(o.withDate?md(m.at.slice(0,10))+' ':'')+m.at.slice(11,16);
  const pin=m.pin?`<span class="mc-pin" role="img" aria-label="ピン留め">${sv('<path d="M9 4h6l-1 5 3 3v1H7v-1l3-3-1-5z"/><path d="M12 13v7"/>')}</span>`:'';
  const rec=m.rec&&A[m.rec]?memoRecRef(m.rec):'';
  return `<div class="memo-card en-paper" role="button" tabindex="0" data-q="${esc(memoText(id))}" onclick="memoOpen('${id}')" onkeydown="if(event.key==='Enter')memoOpen('${id}')">`
    +`<p class="mc-body">${esc(m.body)}</p>`
    +`<div class="mc-meta"><span class="mc-time num">${when}</span>${(m.links||[]).map(k=>memoRef(k)).join('')}${rec}${pin}</div>`
    +`</div>`;
}
/** 縁のメモ = 縁ごとに1枚。押すと /n/{縁} */
function noteCard(id){
  const n=NOTES[id];
  return `<a class="memo-note en-paper" href="#/n/${id}" data-q="${esc(noteText(id))}">`
    +`<span class="mn-h">${knot(id,'sm')}<b>${esc(E[id].n)}のメモ</b></span>`
    +`<span class="mn-dt">更新 ${esc(n.upd)}</span>`
    +`<span class="mn-ln">${esc(noteLine(id))}</span></a>`;
}

/** 書く欄(白い紙)+「縁を付ける」+「保存」 */
function memoComposer(){
  const d=memoDraft;
  return `<section class="memo-compose">`
    +`<div class="en-paper mw"><textarea id="memo-body" class="mw-body" rows="2" placeholder="思いついたことを書く" aria-label="メモの本文" oninput="memoDraft.body=this.value;memoGrow(this)" onkeydown="if(event.key==='Enter'&&(event.metaKey||event.ctrlKey))memoSave()">${esc(d.body)}</textarea>`
    +(d.links.length?`<div class="mw-links">${d.links.map(k=>memoRef(k,false)).join('')}</div>`:'')
    +`</div>`
    +(memoPick?`<div class="chips memo-pick" role="group" aria-label="エンティティ">${memoEnts().map(k=>`<button class="chip" type="button" aria-pressed="${d.links.includes(k)}" onclick="memoPickLink('${k}')">${knot(k,'sm')}${esc(E[k].n)}</button>`).join('')}</div>`:'')
    +`<div class="memo-acts"><button class="btn tonal" type="button" aria-pressed="${memoPick}" onclick="memoTogglePick()">エンティティを付ける</button>`
    +`<button class="btn memo-save" type="button" onclick="memoSave()">保存</button></div>`
    +`</section>`;
}
/** 書く欄の高さを中身に合わせる(field-sizing に対応しない環境用) */
function memoGrow(el){el.style.height='auto';el.style.height=el.scrollHeight+'px'}
function memoTogglePick(){memoPick=!memoPick;rerender()}
function memoPickLink(k){const l=memoDraft.links;l.includes(k)?l.splice(l.indexOf(k),1):l.push(k);rerender()}
function memoSave(){
  const body=memoDraft.body.trim();
  if(!body){toast('メモを入れてください');return}
  const id=memoAdd(body,[...memoDraft.links]);
  memoDraft={body:'',links:[]};memoPick=false;
  save();rerender();
  toast('メモを保存しました',()=>{delete MEMOS[id]});
}

/* 検索: 入力のたびに再描画せず、表示の出し入れだけをする(入力欄の focus を保つ) */
function memoSearchToggle(){
  memoSearchOpen=!memoSearchOpen;if(!memoSearchOpen)memoQ='';
  rerender();
  if(memoSearchOpen)document.getElementById('memo-q')?.focus();
}
function memoFilter(v){
  memoQ=v.trim().toLowerCase();
  document.querySelectorAll('#memo-list .memo-card,#memo-list .memo-note').forEach(c=>{c.hidden=!!memoQ&&!c.dataset.q.includes(memoQ)});
  let any=0;
  document.querySelectorAll('#memo-list .memo-day').forEach(g=>{const n=[...g.querySelectorAll('.memo-card,.memo-note')].filter(c=>!c.hidden).length;g.hidden=!n;any+=n});
  const none=document.getElementById('memo-none');if(none)none.hidden=!!any;
}

/* 編集のシート: 本文・縁の付け外し・ピン・削除。主ボタン「保存」は下に出る */
function memoOpen(id){
  const m=MEMOS[id];if(!m)return;
  memoSheet={id,body:m.body,links:[...(m.links||[])],pin:!!m.pin};
  openSheet({title:'メモ',body:memoSheetBody(),right:{label:'保存',onclick:'memoSheetSave()'}});
}
function memoSheetBody(){
  const s=memoSheet;
  return `<div class="memo-ed">`
    +`<div class="en-paper mw"><textarea class="mw-body" aria-label="メモの本文" oninput="memoSheet.body=this.value">${esc(s.body)}</textarea></div>`
    +sh('エンティティ')
    +`<div class="chips memo-pick">${memoEnts().map(k=>`<button class="chip" type="button" aria-pressed="${s.links.includes(k)}" onclick="memoSheetLink('${k}')">${knot(k,'sm')}${esc(E[k].n)}</button>`).join('')}</div>`
    +`<div class="list mt">${row({t:'ピン留め',v:toggle(s.pin,'memoSheetPin()')})}</div>`
    +`<div class="memo-ed-del"><button class="btn danger block" type="button" onclick="memoDel('${s.id}')">メモを削除</button></div>`
    +`</div>`;
}
function memoSheetLink(k){const l=memoSheet.links;l.includes(k)?l.splice(l.indexOf(k),1):l.push(k);setSheetBody(memoSheetBody())}
function memoSheetPin(){memoSheet.pin=!memoSheet.pin;setSheetBody(memoSheetBody())}
function memoSheetSave(){
  const s=memoSheet,m=MEMOS[s.id];if(!m)return;
  const body=s.body.trim();
  if(!body){toast('本文を入れてください');return}
  const next={...m,body,links:[...s.links]};delete next.pin;if(s.pin)next.pin=1;
  MEMOS[s.id]=next;
  save();closeSheet();rerender();toast('メモを保存しました');
}
function memoDel(id){
  const snap=MEMOS[id];if(!snap)return;
  delete MEMOS[id];
  save();closeSheet();rerender();
  toast('メモを削除しました',()=>{MEMOS[id]=snap});
}

route('memo',()=>{
  const all=memosSorted().map(([k])=>k);
  const ids=all.filter(memoHit);
  const pinned=ids.filter(k=>MEMOS[k].pin);
  const days=[];
  ids.filter(k=>!MEMOS[k].pin).forEach(k=>{const d=MEMOS[k].at.slice(0,10),g=days[days.length-1];if(g&&g.d===d)g.ids.push(k);else days.push({d,ids:[k]})});
  const notes=Object.keys(NOTES).filter(k=>E[k]).sort((a,b)=>updKey(NOTES[b].upd).localeCompare(updKey(NOTES[a].upd))).filter(k=>!memoQ||noteText(k).includes(memoQ));
  const grp=(title,n,html)=>`<div class="memo-day">${sh(title,n)}<div class="memo-cards">${html}</div></div>`;
  const body=[
    pinned.length?grp('ピン留め',pinned.length,pinned.map(k=>memoCard(k,{withDate:true})).join('')):'',
    ...days.map(g=>grp(relDay(g.d),g.ids.length+'件',g.ids.map(k=>memoCard(k)).join(''))),
    notes.length?grp('エンティティのメモ',notes.length,notes.map(noteCard).join('')):''].join('');
  const hasAny=all.length>0||Object.keys(NOTES).some(k=>E[k]);
  return `<div class="memo">`
    +largeTitle('メモ',{actions:[{label:'メモを探す',icon:'search',onclick:'memoSearchToggle()'}]})
    +(memoSearchOpen?`<div class="memo-search">${IC.search}<input id="memo-q" class="input" type="search" placeholder="メモを探す" aria-label="メモを探す" value="${esc(memoQ)}" oninput="memoFilter(this.value)"></div>`:'')
    +memoComposer()
    +(hasAny?`<div id="memo-list">${body}<p class="memo-none" id="memo-none" ${body?'hidden':''}>該当 なし</p></div>`:empty('memo','メモ なし'))
    +`</div>`;
},{tab:'memo'});
