/* 予定とToDoの補助(v4.13)。データは data.js の PLN(kind で種類を分ける)。
   EV(予定)と TD(ToDo)は PLN の見え方なので、この中では PLN を直接いじってもよい。
   予定の todo 配列は ev の付け替えに合わせて plSettle() で揃える(save() が呼ぶ)。 */

/** 種類: '予定' | '支払い予定' | 'ToDo'。無いキーは undefined */
function plKindOf(k){const e=PLN[k];if(!e)return undefined;return e.kind==='ToDo'?'ToDo':(e.pay?'支払い予定':'予定')}

/** 予定の子のToDo(ev がこの予定のもの。完了済みも含む。キーの順) */
function todosOf(evKey){return Object.keys(TD).filter(k=>TD[k].ev===evKey)}

/** 期限が d の未完了のToDo → [[k,t]] */
function todosDueOn(d){return Object.keys(TD).filter(k=>!TD[k].done&&TD[k].due===d).map(k=>[k,TD[k]])}

/** ToDo を予定へ(同じキーのまま、その場で変える)。
    opt: d=日付(無ければ due、それも無ければ今日) / s,e=時刻(無ければ終日) /
    ToDo の完了・リスト・優先・期限と、支払いへのひも(pay の文字列)は落とす。
    親の予定(ev)があった場合は from に残す。ent・memo・rep などは残す。 */
function todoToEvent(k,opt={}){
  const t=PLN[k];
  if(!t||t.kind!=='ToDo')throw new Error('ToDoではありません: '+k);
  const from=t.ev&&PLN[t.ev]&&PLN[t.ev].kind!=='ToDo'?t.ev:undefined;
  const d=opt.d||t.due||TODAY;
  for(const f of ['done','list','pri','due','ev','pay'])delete t[f];
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
    if(!e.kind)e.kind=(e.done!==undefined||e.list||e.ev||e.due)?'ToDo':'予定';
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
