/* 予定とToDoの補助(v4.13)。データは data.js の PLN(kind で種類を分ける)。
   EV(予定)と TD(ToDo)は PLN の見え方なので、この中では PLN を直接いじってもよい。
   予定の todo 配列は ev の付け替えに合わせて plSettle() で揃える(save() が呼ぶ)。

   【v5 で足した関数】(戻り値は data.js と同じく {ok, undo} / {ok:false, why})
     planUpdate(キー, {題・日付・時刻など}) → {ok, undo}   予定・ToDo・支払い予定の項目を直す。値に null・'' を渡すとその項目を消す
     planRemove(キー) → {ok, undo}                          予定・ToDo を消す。子の ToDo は親を外して残す
     planRestore(undo) = undoRun
     todoToggle(キー, 日付?) → {ok, undo}                    ToDo の完了⇄未完了(完了日は既定で今日)
     todoDueCount(日付) → number                             期限が日付以前(今日を含む)の未完了の ToDo の数。件数はこれで数える
   ToDo の支払いのひも = payRec(支払いid)。支払い予定の pay({amt,cat}) とは別。 */

/** 種類: '予定' | '支払い予定' | 'ToDo'。無いキーは undefined */
function plKindOf(k){const e=PLN[k];if(!e)return undefined;return e.kind==='ToDo'?'ToDo':(e.pay?'支払い予定':'予定')}

/** 予定の子のToDo(ev がこの予定のもの。完了済みも含む。キーの順) */
function todosOf(evKey){return Object.keys(TD).filter(k=>TD[k].ev===evKey)}

/** 期限が d の未完了のToDo → [[k,t]] */
function todosDueOn(d){return Object.keys(TD).filter(k=>!TD[k].done&&TD[k].due===d).map(k=>[k,TD[k]])}

/** ToDo を予定へ(同じキーのまま、その場で変える)。
    opt: d=日付(無ければ due、それも無ければ今日) / s,e=時刻(無ければ終日) /
    ToDo の完了・リスト・優先・期限と、支払いへのひも(payRec の文字列)は落とす。
    親の予定(ev)があった場合は from に残す。ent・memo・rep などは残す。 */
function todoToEvent(k,opt={}){
  const t=PLN[k];
  if(!t||t.kind!=='ToDo')throw new Error('ToDoではありません: '+k);
  const from=t.ev&&PLN[t.ev]&&PLN[t.ev].kind!=='ToDo'?t.ev:undefined;
  const d=opt.d||t.due||TODAY;
  for(const f of ['done','list','pri','due','ev','payRec'])delete t[f];
  t.kind='予定';
  t.d=d;
  if(opt.s){t.s=opt.s;t.e=opt.e||opt.s}else{t.all=1;delete t.s;delete t.e}
  if(from)t.from=from;
  plSettle();save();
  return k;
}

/** 予定をToDoへ(同じキーのまま)。due=元の日付。opt.list で リスト(既定 inbox)。
    予定の時刻・場所・参加者・通知・todo・記録の結び付きは落とす。子のToDoは親を失う(ev を外す)。
    支払い予定は金額・カテゴリを持つので変えられない(Error) */
function eventToTodo(k,opt={}){
  const e=PLN[k];
  if(!e||e.kind==='ToDo')throw new Error('予定ではありません: '+k);
  if(e.pay)throw new Error('支払い予定はToDoにできません: '+k);
  const due=e.d;
  for(const c of todosOf(k))delete PLN[c].ev;
  for(const f of ['d','d2','s','e','all','hol','place','who','alarm','todo','rec','from'])delete e[f];
  e.kind='ToDo';
  if(due)e.due=due;
  e.list=opt.list||'inbox';
  plSettle();save();
  return k;
}

/** 保存の前に揃える: kind を中身から決め直し、予定の todo 配列を ev の付け替えに合わせる */
function plNormalizeKinds(){
  for(const k of Object.keys(PLN)){const e=PLN[k];
    if(!e.kind)e.kind=(e.done!==undefined||e.list||e.ev||e.due||e.payRec!==undefined)?'ToDo':'予定';
    if(e.kind!=='ToDo')e.kind=e.pay?'支払い予定':'予定';
  }
}
function plSyncLinks(){
  /* ToDo の ev は親の予定だけ。存在しない・ToDo を指すものは外す */
  for(const k of Object.keys(TD)){const t=TD[k];if(t.ev!==undefined&&(!PLN[t.ev]||PLN[t.ev].kind==='ToDo'))delete t.ev}
  /* 予定の todo は ev のつながりから作り直す(無ければ項目ごと消す) */
  for(const k of Object.keys(EV)){const ks=todosOf(k);if(ks.length)EV[k].todo=ks;else delete EV[k].todo}
}
/** 種類と関係をそろえる(save・load から呼ぶ) */
function plSettle(){plNormalizeKinds();plSyncLinks()}

/* ---- v5: 予定・ToDo の編集・削除・完了 ---- */
/** 期限が d 以前(今日を含む)の未完了 ToDo の数。ホーム・ToDo・一覧の「今日」の件数はこれ */
function todoDueCount(d){return Object.keys(TD).filter(k=>!TD[k].done&&TD[k].due&&TD[k].due<=d).length}

/** 予定・ToDo・支払い予定の項目を直す。patch の値が null・''・undefined の項目は消す。kind は書けない(中身で決まる) */
function planUpdate(key,patch={}){
  const p=PLN[key];if(!p)return notOk('予定が見つかりません');
  const v={};
  for(const [k,val] of Object.entries(patch)){
    if(k==='kind'||k==='todo'||k==='from')continue;
    v[k]=val;
  }
  if(v.t!==undefined&&!String(v.t||'').trim())return notOk('題を入れてください');
  for(const k of ['d','d2','due','done'])if(v[k]!=null&&v[k]!==''&&!isDate(v[k]))return notOk('日付は 2026-11-01 の形で入れてください');
  for(const k of ['s','e'])if(v[k]!=null&&v[k]!==''&&!isHM(v[k]))return notOk('時刻は 00:00 の形で入れてください');
  if(v.ev!=null&&v.ev!==''&&(!PLN[v.ev]||PLN[v.ev].kind==='ToDo'))return notOk('つなぐ予定が見つかりません');
  const merged={...p,...v};
  if(merged.s&&merged.e&&merged.e<merged.s)return notOk('終わりは始まりより後にしてください');
  const log=[];
  for(const [k,val] of Object.entries(v)){if(val==null||val==='')logDel(log,p,k);else logSet(log,p,k,k==='t'?String(val).trim():val)}
  plSettle();save();return okUndo(log,'予定を直した');
}
/** 予定・ToDo を消す。子の ToDo は親を外して残す(取り消しで戻る) */
function planRemove(key){
  if(!PLN[key])return notOk('予定が見つかりません');
  const log=[];
  for(const k of Object.keys(PLN))if(PLN[k].ev===key)logDel(log,PLN[k],'ev');
  logDel(log,PLN,key);
  save();return okUndo(log,'予定を消した');
}
const planRestore=undoRun;
/** ToDo の完了⇄未完了。完了の日は既定で今日(date で指定可) */
function todoToggle(key,date){
  const t=PLN[key];if(!t||t.kind!=='ToDo')return notOk('ToDoが見つかりません');
  const log=[];
  if(t.done)logDel(log,t,'done');else logSet(log,t,'done',date||TODAY);
  save();return okUndo(log,t.done?'完了を取り消した':'完了にした');
}
