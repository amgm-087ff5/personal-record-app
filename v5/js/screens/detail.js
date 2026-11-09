/* 画面: 記録の詳細(#/a)・品目の詳細(#/i)・予定の詳細(#/ev)。接頭辞 dt_。タブは変えない(来た所のタブ) */

/* ---------- 小道具 ---------- */
const DT_IC={支払い:'pay',食事:'lines',会話:'talk',作業:'activity',移動:'move',出来事:'diary',買い物:'cats',起床:'moment',就寝:'moment',出発:'move',到着:'move'};
const dtIcon=t=>DT_IC[t]||'activity';
const dtName=r=>r?(r[1]&&E[r[1]]?E[r[1]].n:(r[2]||'')):'';
const dtDur=a=>{if(!isSpan(a))return '';const t=s=>{const[h,m]=s.split(':').map(Number);return h*60+m};let m=t(a.end)-t(a.time);if(m<0)m+=1440;const h=Math.floor(m/60),r=m%60;return h&&r?`${h}時間${r}分`:h?`${h}時間`:`${r}分`};
const dtLnText=it=>{const am=lnAmts(it.lines);return it.lines.map((l,j)=>j?`${am[j]<0?'−':'＋'}${lnName(l)} ${l.k==='pct'?Math.abs(lnV(l))+'%':yen(Math.abs(am[j]))}`:`本体 ${yen(am[0])}`).join(' ')};
const DT_OPEN=new Set();
const dtSnap=a=>{const x=a.x&&X[a.x];return JSON.stringify({items:a.items,amt:a.amt,paid:a.paid,settled:a.settled||null,xid:a.x||null,x:x?JSON.parse(JSON.stringify(x)):null})};
const dtRestore=(a,snap)=>{const o=JSON.parse(snap);
  if(o.items)a.items=o.items;a.amt=o.amt;a.paid=o.paid;
  if(o.settled)a.settled=o.settled;else delete a.settled;
  if(a.x&&a.x!==o.xid)delete X[a.x];
  if(o.x){X[o.xid]=o.x;a.x=o.xid}else delete a.x;
  moneyInstall();save()};
/** 日の記録を探す。無ければ作る(予定を記録にする時) */
function dtDayFor(date){let k=Object.keys(A).find(k=>A[k].type==='日記'&&A[k].date===date);
  if(!k){k=uid('d');A[k]={type:'日記',date,dd:String(+date.slice(8)),dw:dowJ(date),body:'',kids:[]}}
  return k}
/** 支払いの品目を「同じ名前・同じ金額・同じ金額行・同じ使った人・同じ税率」でまとめる(表示だけ) */
function dtGroups(items){const g=[];items.forEach((it,i)=>{const key=JSON.stringify([it.n,itemAmt(it),it.lines||null,usersOf(it).slice().sort(),itemTax(it)]);
  const f=g.find(x=>x.key===key);if(f)f.idx.push(i);else g.push({key,n:it.n,amt:itemAmt(it),it,idx:[i]})});return g}
/** 仮の自分の支出の額(保存しない) */
function dtPreview(aid,d){const tk='_dtp';X[tk]={pay:aid,basis:d.basis,n:d.n,me:d.me};try{return xAmt(X[tk])}finally{delete X[tk]}}
/** 買い物リストのToDoが、この支払いの品目にあるキー */
/** 支払いの品目と一致する買い物ToDoのキー(plan.js の plShopTodoFor) */
function dtTodoKeys(a){if(a.type!=='支払い'||!a.items)return [];const ks=[];
  a.items.forEach(i=>{const k=plShopTodoFor(i.n);if(k&&!ks.includes(k))ks.push(k)});return ks}

/* ---------- 骨組み: 日のカード・軸・祖先の枠 ---------- */
const dtPins=id=>ancIds(id).map(k=>({id:k,tm:timeText(A[k]),t:A[k].title||'',ic:dtIcon(A[k].type)}));
/** 軸の切れ端。中の主役(カプセル)が軸に掛かる */
const dtAx=(inner,o={})=>`<div class="dt_ax ${o.future?'is-future':''}">${inner}</div>`;
/** 祖先の枠: 外側の親から順に包む(pins は外側が先) */
const dtWrap=(pins,inner)=>pins.reduceRight((acc,p)=>`<div class="dt_anc"><a class="dt_anc_h" href="#/a/${p.id}"><span class="dt_anc_i">${icon(p.ic)}</span><span class="dt_anc_tm num">${esc(p.tm)}</span><span class="dt_anc_t">${esc(p.t)}</span><span class="p-chev">${icon('chev')}</span></a>${acc}</div>`,inner);
/** 種類の札の名前とアイコン。予定は「予定」、支払い予定は「予定・支払い」 */
const DT_KIND={支払い:'支払い',食事:'食事',会話:'会話',作業:'作業',移動:'移動',出来事:'出来事',買い物:'買い物'};
/** 移動の主役: 出発・到着の2行(地名は縁ならリンク) */
const dtRoute=(a,t)=>{const dep=(a.roles||[]).find(r=>r[0]==='出発'),arr=(a.roles||[]).find(r=>r[0]==='到着');
  const stop=(lab,r,tm)=>`<span class="dt_route"><span class="dt_route_k">${lab}</span>${r?eref(r[1],r[2]):'<span></span>'}<b class="num">${esc(tm||'')}</b></span>`;
  return (dep?stop('出発',dep,a.time):'')+(arr?stop('到着',arr,a.end):'')};
/** 割り勘・立替の割合の棒(自分 / ほかの人 / 未精算の琥珀)。ほかの人の分が無い時は出さない */
const dtSplit=a=>{const x=a.x&&X[a.x];if(!x)return '';const mine=xAmt(x),rest=(a.amt||0)-mine;if(rest<=0)return '';
  const b=balanceOf(a),owed=b.amt>0&&!b.settled;const who=b.names.length?b.names.map(n=>n==='ほかの人'?n:n+'さん').join('・'):'ほかの人';
  return splitBar([{v:mine,kind:'me',label:'自分'},{v:rest,kind:owed?'owe':'other',label:`${who}の分${owed?'・未精算':''}`}],a.amt)};

/* ---------- 記録の詳細(#/a) ---------- */
route('a', (id) => {
  const a = A[id];
  if (!a) return appbar('') + empty({shape: 'list', text: '記録が見つかりません'});
  if (a.type === '日記') { after(() => location.replace('#/day/' + a.date)); return ''; }
  return `<div class="dt_pg">${dtRecord(id, a)}</div>`;
});

function dtRecord(id, a) {
  const pay = a.type === '支払い', span = isSpan(a), mv = a.type === '移動';
  const dr = dayOf(a), d = dr ? dr.date : (a.date || TODAY);
  let cap;
  if (pay) {
    const rm = readMarkList(id), rmAny = rm.amt || rm.payee || rm.items.length;
    cap = capsule({kind: 'done', kindLabel: '支払い', kindIcon: 'pay', time: a.time || '', title: payeeOf(a) || '支払い',
        sub: `合計 <b class="num">${yen(a.amt || 0)}</b>${payMethod(a) ? ` · ${esc(payMethod(a))}` : ''}`})
      + (rmAny ? `<div class="dt_rm">${readMark({label: '読み取った値'})}<button class="p-btn p-btn-tonal" type="button" onclick="dtReadConfirm('${id}')">確定</button></div>` : '')
      + dtSplit(a)
      + payShell(id, {mode: 'pay'});
  } else if (!span) {
    /* 瞬間(起床など): 小さい白い面・時刻を大きく。種類と題が同じなら札は出さない(重複を消す) */
    cap = capsule({kind: 'moment', time: a.time || '', title: a.title || a.type,
      kindLabel: a.title && a.title !== a.type ? a.type : '', kindIcon: dtIcon(a.type)});
  } else {
    cap = capsule({kind: 'done', kindLabel: a.type, kindIcon: dtIcon(a.type), time: timeText(a), title: a.title,
      sub: mv ? dtRoute(a, true) : esc(dtDur(a))}) + dtKids(id);
  }
  /* 関わったエンティティ(移動の出発・到着は主役に出しているので除く。支払いは親の同席も出す) */
  let all = (a.roles || []).filter(r => !mv || !['出発', '到着'].includes(r[0]));
  if (pay) { const pa = parentAct(a); if (pa) (pa.roles || []).forEach(r => {
    if (r[1] && r[1] !== 'me' && !['場所', '出発', '到着', '支払先', '支払者'].includes(r[0]) && !all.some(q => q[1] === r[1])) all = [...all, r] }) }
  const shop = (a.roles || []).find(r => r[0] === '支払先');
  let secs = '';
  if (all.length) secs += sh('関わったエンティティ', {count: all.length,
      act: pay && shop && !shop[1] && shop[2] ? {label: '支払先を店として登録', on: `dtShopReg('${id}')`} : null})
    + list(all.map(r => prop(r[0], eref(r[1], r[2]), {html: true})));
  if (pay) secs += dtTodoSug(id, a) + dtPaidSec(id, a) + dtItemsSec(id, a) + dtMineSec(id, a);
  if (a.body) secs += sh('内容') + `<p class="dt_body">${esc(a.body)}</p>`;
  const att = (EXTRA.attach || {})[id];
  if (att) secs += sh('添付') + `<div class="dt_att"><img src="${esc(att)}" alt="レシートの写真"></div>`;
  secs += dtOrigPlan(id);
  return appbar('', {actions: [{icon: 'more', label: 'その他の操作', on: `dtMenu('${id}')`}]})
    + (dr ? dayCard(d, {diary: dr.body || ''}) : dayCard(d, {}))
    + dtWrap(dtPins(id), dtAx(cap))
    + secs;
}

/** 活動の中の子(支払いは殻、それ以外は白い小行。3段まで字下げ) */
const dtKids=id=>{const ks=kidActs(id).slice().sort((x,y)=>(A[x].time||'99:99').localeCompare(A[y].time||'99:99'));
  if (!ks.length) return `<div class="dt_kids"><button class="dt_add" type="button" onclick="typeof openCreateIn==='function'?openCreateIn('${id}'):openCreate()">${icon('plus')}アクティビティを追加</button></div>`;
  return `<div class="dt_kids">${ks.map(c => dtKid(c, 1)).join('')}<button class="dt_add" type="button" onclick="typeof openCreateIn==='function'?openCreateIn('${id}'):openCreate()">${icon('plus')}アクティビティを追加</button></div>`};
function dtKid(c, depth) {
  const k = A[c]; if (!k) return '';
  const body = k.type === '支払い' ? payShell(c, {mode: 'kid'})
    : `<a class="dt_kid" href="#/a/${c}"><span class="dt_kid_i">${icon(dtIcon(k.type))}</span><span class="dt_kid_tm num">${esc(timeText(k))}</span><span class="dt_kid_t">${esc(k.title)}</span>${kidActs(c).length ? `<span class="dt_kid_n">${kidActs(c).length}件</span>` : ''}</a>`;
  const subs = depth < 3 ? kidActs(c).slice().sort((x, y) => (A[x].time || '99:99').localeCompare(A[y].time || '99:99')).map(g => dtKid(g, depth + 1)).join('') : '';
  return body + (subs ? `<div class="dt_sub">${subs}</div>` : '');
}
/** 元の予定(この記録になった予定)。予定の詳細へ */
function dtOrigPlan(id) {
  const k = Object.keys(EV).find(k => EV[k].rec === id); if (!k) return '';
  const e = EV[k];
  return sh('元の予定') + list([row({lead: icon('plan'), t: e.t, s: `${fmtMDW(e.d)} ${e.all ? '終日' : e.s}`, href: '#/ev/' + k})]);
}

/* ---------- 支払い: 買い物リストの提案・払った人・品目・自分の負担 ---------- */
function dtTodoSug(id, a) {
  const ks = dtTodoKeys(a); if (!ks.length) return '';
  return banner('info', `<span>買い物リストの ${ks.map(k => '「' + esc(TD[k].t) + '」').join('')} がこの支払いにあります</span> <button class="p-btn p-btn-tonal" type="button" onclick="dtDoneByPay('${id}')">完了にする</button>`);
}
/** 買い物の ToDo を支払いで完了にする(plan.js の plShopDone。取り消せる) */
function dtDoneByPay(aid) {
  const ks = dtTodoKeys(A[aid]); if (!ks.length) return;
  const rs = ks.map(k => plShopDone(k, aid));
  rerender();
  toast('支払いの品目から完了にしました', {undo: () => { rs.forEach(r => undoRun(r.undo)); rerender() }});
}
function dtReadConfirm(aid) {
  const r = readMarkConfirm(aid); if (!r.ok) { toast(r.why); return }
  rerender();
  toast('読み取りを確定しました', {undo: () => { undoRun(r.undo); rerender() }});
}
/** 支払先を店として登録(縁を作り、支払先の役割につなぐ) */
function dtShopReg(aid) {
  const a = A[aid], r = (a.roles || []).find(x => x[0] === '支払先'); if (!r || !r[2]) return;
  const er = entAdd({n: r[2], t: ['shop'], facts: [], rels: []}); if (!er.ok) { toast(er.why); return }
  const old = r[1]; r[1] = er.id; save(); rerender();
  toast(`「${r[2]}」を店として登録しました`, {undo: () => { undoRun(er.undo); r[1] = old; save(); rerender() }});
}
function dtPaidSec(id, a) {
  const ps = paidOf(a);
  return sh('払った人', {count: ps.length, act: {label: '編集', on: `dtPaidOpen('${id}')`}})
    + list(ps.map(p => row({t: whoName(p.who, p.name) + (p.how ? ' · ' + p.how : ''), right: yen(p.amt), on: `dtPaidOpen('${id}')`})));
}
function dtItemsSec(id, a) {
  const its = a.items || [];
  if (!its.length) return sh('品目') + empty({shape: 'pay', text: '品目 なし', action: {label: '品目を追加', on: `dtItemSheet('${id}',null)`}});
  const gs = dtGroups(its); let rows = [];
  gs.forEach((g, gi) => {
    const multi = g.idx.length > 1, key = id + ':' + gi, open = multi && DT_OPEN.has(key);
    const sub = `使った人 ${usersText(g.it)} · 税${itemTax(g.it)}%${g.it.lines && g.it.lines.length > 1 ? ' · ' + dtLnText(g.it) : ''}`;
    rows.push(row({t: g.n + (multi ? ` ×${g.idx.length}` : ''), s: sub, right: yen(g.amt * g.idx.length),
      on: multi ? `dtGroupToggle('${key}')` : null, href: multi ? null : `#/i/${id}/${g.idx[0]}`}));
    if (open) g.idx.forEach((i, n) => rows.push(row({lead: `<span class="dt_n">${n + 1}</span>`, t: its[i].memo || '1個', s: usersText(its[i]), right: yen(itemAmt(its[i])), href: `#/i/${id}/${i}`})));
  });
  return sh('品目', {count: gs.length, act: {label: '追加', on: `dtItemSheet('${id}',null)`}}) + `<div class="dt_items">${list(rows)}</div>`;
}
function dtGroupToggle(key) { DT_OPEN.has(key) ? DT_OPEN.delete(key) : DT_OPEN.add(key); rerender() }
function dtMineSec(id, a) {
  const x = a.x && X[a.x], b = balanceOf(a);
  return sh('自分の負担') + list([row({t: BASIS[(x && x.basis) || 'items'], right: yen(mineOf(a)), on: `dtBasisOpen('${id}')`})])
    + (b.amt ? `<div class="dt_bal">${tag(balanceText(b), {kind: 'owe'})}<span class="dt_bal_s">${b.settled ? '精算済み' : '未精算'}</span><button class="p-btn p-btn-tonal" type="button" onclick="dtSettle('${id}')">${b.settled ? '未精算に戻す' : '精算済みにする'}</button></div>` : '');
}
function dtSettle(aid) {
  const a = A[aid], was = !!a.settled;
  if (was) delete a.settled; else a.settled = 1;
  save(); rerender();
  toast(was ? '未精算に戻しました' : '精算済みにしました', {undo: () => { if (was) a.settled = 1; else delete a.settled; save(); rerender() }});
}

/* ---------- 記録の操作・編集(時刻・終わり・題・役割) ---------- */
function dtMenu(id) {
  openSheet({title: '操作', body: list([
    row({t: '編集', chev: false, on: `closeSheet(true);dtRecEdit('${id}')`}),
    row({t: '削除', chev: false, on: `dtRecDel('${id}')`})])});
}
let dtD = null, dtDInit = '';
function dtRecEdit(id) {
  const a = A[id]; if (!a) return;
  dtD = {id, title: a.title || '', time: a.time || '', end: a.end || '', roles: (a.roles || []).map(r => ({r: r[0], e: r[1] || '', n: r[2] || ''}))};
  dtDInit = JSON.stringify(dtD);
  openSheet({title: '記録を編集', dirty: () => JSON.stringify(dtD) !== dtDInit,
    primary: {label: '保存', on: dtRecSave}, body: dtRecBody()});
}
function dtRecBody() {
  const a = A[dtD.id], pay = a.type === '支払い', span = isSpan(a);
  const ents = Object.keys(E).filter(k => k !== 'me');
  return (pay ? '' : field('題', input({value: dtD.title, oninput: 'dtD.title=this.value'})))
    + field('時刻', input({type: 'time', value: dtD.time, onchange: 'dtD.time=this.value'}))
    + (span ? field('終わり', input({type: 'time', value: dtD.end, onchange: 'dtD.end=this.value'})) : '')
    + sh('関わったエンティティ', {count: dtD.roles.length, act: {label: '役割を追加', on: 'dtRoleAdd()'}})
    + dtD.roles.map((x, i) => `<div class="dt_role">
        <input class="p-input" value="${esc(x.r)}" aria-label="役割" oninput="dtD.roles[${i}].r=this.value">
        <select class="p-input" aria-label="縁" onchange="dtRoleE(${i},this.value)">
          <option value="" ${!x.e ? 'selected' : ''}>未登録</option>
          ${ents.map(k => `<option value="${esc(k)}" ${x.e === k ? 'selected' : ''}>${esc(E[k].n)}</option>`).join('')}
        </select>
        ${x.e ? '' : `<input class="p-input dt_role_n" value="${esc(x.n)}" placeholder="名前" aria-label="名前" oninput="dtD.roles[${i}].n=this.value">`}
        ${pay && i === 0 ? '<span></span>' : `<button class="p-ibtn" type="button" aria-label="この役割を外す" onclick="dtRoleDel(${i})">${icon('trash')}</button>`}
      </div>`).join('');
}
function dtRoleE(i, v) { dtD.roles[i].e = v; setSheetBody(dtRecBody()) }
function dtRoleAdd() { dtD.roles.push({r: '同席', e: '', n: ''}); setSheetBody(dtRecBody()) }
function dtRoleDel(i) { dtD.roles.splice(i, 1); setSheetBody(dtRecBody()) }
function dtRecSave() {
  const a = A[dtD.id], pay = a.type === '支払い', span = isSpan(a);
  const patch = {time: dtD.time, roles: dtD.roles.map(x => x.e ? [x.r, x.e] : [x.r, null, x.n])};
  if (span) patch.end = dtD.end;
  if (!pay) patch.title = dtD.title;
  const r = recUpdate(dtD.id, patch);
  if (!r.ok) { toast(r.why); return }
  closeSheet(true); rerender();
  toast('記録を保存しました', {undo: () => { undoRun(r.undo); rerender() }});
}
/** 記録を消す(子・支払い・支出ごと。取り消しで戻る) */
function dtRecDel(id) {
  const r = recDelete(id); if (!r.ok) { toast(r.why); return }
  closeSheet(true); goBack();
  toast('記録を消しました', {undo: () => { undoRun(r.undo); go('#/a/' + id) }});
}

/* ---------- 払った人の編集 ---------- */
let dtP = null, dtPInit = '';
const dtPaidSum = () => dtP.rows.reduce((s, p) => s + (p.amt || 0), 0);
function dtPaidOpen(aid) {
  dtP = {id: aid, rows: paidOf(A[aid]).map(p => ({...p}))}; dtPInit = JSON.stringify(dtP.rows);
  openSheet({title: '払った人', full: true, dirty: () => JSON.stringify(dtP.rows) !== dtPInit,
    primary: {label: '保存', on: dtPaidSave, disabled: false}, body: dtPaidBody()});
  dtPaidUpd();
}
function dtPaidBody() {
  const a = A[dtP.id], people = peopleOf(a);
  return sh('払った人', {count: dtP.rows.length, act: {label: '追加', on: 'dtPaidAdd()'}})
    + dtP.rows.map((p, i) => `<div class="dt_box"><div class="dt_bh"><b>${esc(whoName(p.who, p.name))}</b><button class="p-ibtn" type="button" aria-label="この人を外す" onclick="dtPaidDel(${i})">${icon('trash')}</button></div>
        <div class="dt_who_row">${people.map(w => `<button class="dt_who" type="button" aria-pressed="${p.who === w}" onclick="dtPaidWho(${i},'${w}')">${esc(whoShort(w))}</button>`).join('')}</div>
        ${field('金額', input({value: p.amt || '', ph: '¥0', oninput: `dtPaidAmt(${i},this.value)`}))}
        ${p.who === 'me' ? field('支払い方法', select({options: PAY_METHODS, value: p.how || PAY_METHODS[0], onchange: `dtP.rows[${i}].how=this.value`})) : ''}
      </div>`).join('')
    + `<div id="dt-psum">${dtPaidSumHtml()}</div>`;
}
function dtPaidSumHtml() {
  const amt = A[dtP.id].amt || 0, d = amt - dtPaidSum();
  return list([row({t: '支払いの合計', right: yen(amt)}), row({t: '払った額の合計', right: yen(dtPaidSum())}),
    ...(d ? [row({t: d > 0 ? 'あと' : '超過', right: yen(Math.abs(d))})] : [])]);
}
function dtPaidUpd() {
  const e = document.getElementById('dt-psum'); if (e) e.innerHTML = dtPaidSumHtml();
  setSheetPrimary({disabled: !!(A[dtP.id].amt - dtPaidSum()) || !dtP.rows.some(p => p.amt > 0)});
}
function dtPaidWho(i, w) { const p = dtP.rows[i]; p.who = w; delete p.name; if (w !== 'me') delete p.how; else p.how = p.how || PAY_METHODS[0]; setSheetBody(dtPaidBody()); dtPaidUpd() }
function dtPaidAmt(i, v) { dtP.rows[i].amt = +String(v).replace(/[^\d]/g, '') || 0; dtPaidUpd() }
function dtPaidDel(i) { dtP.rows.splice(i, 1); setSheetBody(dtPaidBody()); dtPaidUpd() }
function dtPaidAdd() {
  const a = A[dtP.id], used = dtP.rows.map(p => p.who), who = peopleOf(a).find(w => !used.includes(w)) || 'me';
  dtP.rows.push({who, amt: Math.max(0, (a.amt || 0) - dtPaidSum()), how: who === 'me' ? PAY_METHODS[0] : undefined});
  setSheetBody(dtPaidBody()); dtPaidUpd();
}
function dtPaidSave() {
  const a = A[dtP.id], rows = dtP.rows.filter(p => p.amt > 0);
  if (!rows.length || dtPaidSum() !== (a.amt || 0)) { toast('払った額の合計を支払いの合計に合わせてください'); return }
  const old = JSON.parse(JSON.stringify(a.paid));
  a.paid = rows.map(p => {const o = {who: p.who, amt: p.amt}; if (p.name) o.name = p.name; if (p.who === 'me' && p.how) o.how = p.how; return o});
  closeSheet(true); save(); rerender();
  toast('払った人を保存しました', {undo: () => { a.paid = old; save(); rerender() }});
}

/* ---------- 自分の負担(使った分・割り勘・金額を指定) ---------- */
let dtB = null, dtBInit = '';
function dtBasisOpen(aid) {
  const a = A[aid], x = a.x && X[a.x];
  dtB = {id: aid, basis: (x && x.basis) || basisGuess(a) || 'items', n: Math.max(2, (x && x.basis === 'even' && x.n) || peopleOf(a).length), me: x ? (x.basis === 'amount' ? (x.me || 0) : xAmt(x)) : 0};
  dtBInit = JSON.stringify(dtB);
  openSheet({title: '自分の負担', full: true, dirty: () => JSON.stringify(dtB) !== dtBInit, primary: {label: '保存', on: dtBasisSave}, body: dtBasisBody()});
}
function dtBasisBody() {
  const a = A[dtB.id], mp = myPaid(a);
  return seg([{k: 'items', label: '使った分'}, {k: 'even', label: '割り勘'}, {k: 'amount', label: '金額を指定'}], dtB.basis, 'dtBasisSet')
    + (dtB.basis === 'even' ? `<div class="dt_row"><button class="p-period-b" type="button" aria-label="人数を減らす" onclick="dtBasisN(-1)">−</button><b class="num">${dtB.n}人で割る</b><button class="p-period-b" type="button" aria-label="人数を増やす" onclick="dtBasisN(1)">＋</button></div>` : '')
    + (dtB.basis === 'amount' ? field('自分の負担', input({value: dtB.me || '', ph: '¥0', oninput: 'dtBasisMe(this.value)'}))
      + `<div class="dt_row"><button class="p-btn p-btn-tonal" type="button" onclick="dtBasisFill(${mp})">自分が払った ${yen(mp)} を入れる</button></div>` : '')
    + `<div id="dt-bres">${dtBasisRes()}</div>`;
}
/** 結果: 自分の負担 + 貸し借りの文(「花子さんに貸し ¥2,340」)と未精算/精算済み。保存する前の計算(一時の支出で数える) */
function dtBasisRes() {
  const a = A[dtB.id], tk = '_dtb';
  X[tk] = {pay: dtB.id, basis: dtB.basis, n: dtB.n, me: dtB.me};
  let mine = 0, b = {amt: 0};
  try { mine = xAmt(X[tk]); b = balanceOf({...a, x: tk}) } finally { delete X[tk] }
  return list([row({t: '自分の負担', right: yen(mine)}),
    ...(b.amt ? [row({t: balanceText(b), s: b.settled ? '精算済み' : '未精算'})] : [])]);
}
function dtBasisUpd() { const r = document.getElementById('dt-bres'); if (r) r.innerHTML = dtBasisRes() }
function dtBasisSet(v) { dtB.basis = v; setSheetBody(dtBasisBody()) }
function dtBasisN(d) { dtB.n = Math.min(99, Math.max(2, dtB.n + d)); setSheetBody(dtBasisBody()) }
function dtBasisMe(v) { dtB.me = +String(v).replace(/[^\d]/g, '') || 0; dtBasisUpd() }
function dtBasisFill(v) { dtB.me = v; setSheetBody(dtBasisBody()) }
function dtBasisSave() {
  const a = A[dtB.id], snap = dtSnap(a);
  if (!a.x || !X[a.x]) { const xid = uid('x'); X[xid] = {pay: dtB.id, basis: 'items', cat: 'eatout', memo: ''}; a.x = xid }
  const x = X[a.x]; x.basis = dtB.basis;
  if (dtB.basis === 'amount') x.me = dtB.me || 0; else delete x.me;
  if (dtB.basis === 'even') x.n = dtB.n || 2; else delete x.n;
  xEnsure(dtB.id); moneyInstall();
  closeSheet(true); save(); rerender();
  toast('自分の負担を保存しました', {undo: () => { dtRestore(a, snap); rerender() }});
}

/* ---------- 品目の編集・追加・削除(金額行つき) ---------- */
let dtIt = null, dtItInit = '';
/** 金額行の符号つきの配列(保存の形) */
const dtSigned = () => dtIt.lines.map(l => ({n: l.n, k: l.k, v: l.neg ? -l.v : l.v}));
function dtItemSheet(aid, ix) {
  const a = A[aid], it = ix == null ? null : (a.items || [])[ix];
  const lines = it && it.lines && it.lines.length > 1
    ? it.lines.map(l => ({n: l.n || '', k: l.k || 'amt', neg: lnV(l) < 0, v: Math.abs(lnV(l))}))
    : [{n: '', k: 'amt', neg: false, v: it ? it.price : 0}];
  dtIt = {aid, ix, n: it ? it.n : '', tax: String(it ? itemTax(it) : TAX_DEFAULT), users: it ? usersOf(it).slice() : ['me'], memo: it ? it.memo || '' : '', lines};
  dtItInit = JSON.stringify(dtIt);
  openSheet({title: ix == null ? '品目を追加' : '品目を編集', full: true, dirty: () => JSON.stringify(dtIt) !== dtItInit,
    primary: {label: ix == null ? '追加' : '保存', on: dtItSave}, body: dtItBody()});
}
function dtItBody() {
  const a = A[dtIt.aid], ents = peopleOf(a);
  return field('品名', input({value: dtIt.n, oninput: 'dtIt.n=this.value'}))
    + field('本体の値段', input({value: dtIt.lines[0].v || '', ph: '¥0', oninput: 'dtItSetV(0,this.value)'}))
    + dtIt.lines.slice(1).map((l, j0) => { const j = j0 + 1; return `<div class="dt_box">
        <div class="dt_bh">${select({options: [{v: 'a+', l: '＋円'}, {v: 'a-', l: '−円'}, {v: 'p+', l: '＋%'}, {v: 'p-', l: '−%'}], value: (l.k === 'pct' ? 'p' : 'a') + (l.neg ? '-' : '+'), onchange: `dtItKind(${j},this.value)`})}
          <button class="p-ibtn" type="button" aria-label="この行を消す" onclick="dtItLineDel(${j})">${icon('trash')}</button></div>
        ${field('名前', input({value: l.n, oninput: `dtIt.lines[${j}].n=this.value`}))}
        ${field('値', input({value: l.v || '', ph: '0', oninput: `dtItSetV(${j},this.value)`}))}
      </div>`; }).join('')
    + `<div class="dt_row"><button class="p-btn p-btn-tonal" type="button" onclick="dtItLineAdd()">${icon('plus')}オプション・割引を追加</button></div>`
    + `<div id="dt-ieq">${dtItEq()}</div>`
    + sh('税率') + seg([{k: '8', label: '税8%'}, {k: '10', label: '税10%'}], dtIt.tax, 'dtItTax')
    + sh('使った人') + `<div class="dt_who_row" style="padding:0 var(--gutter)">${ents.map(w => `<button class="dt_who" type="button" aria-pressed="${dtIt.users.includes(w)}" onclick="dtItUser('${w}')">${esc(whoShort(w))}</button>`).join('')}</div>`
    + field('メモ', input({value: dtIt.memo, ph: '任意', oninput: 'dtIt.memo=this.value'}))
    + (dtIt.ix == null ? '' : `<button class="dt_quiet" type="button" onclick="dtItDel()">${icon('trash')}品目を消す</button>`);
}
function dtItEq() {
  const sg = dtSigned(), am = lnAmts(sg);
  return list([row({t: '本体', right: yen(am[0])}),
    ...sg.slice(1).map((l, j) => row({t: lnName(l), right: (am[j + 1] < 0 ? '−' : '＋') + yen(Math.abs(am[j + 1]))})),
    row({t: '合計', right: yen(itemAmt({lines: sg.length > 1 ? sg : undefined, price: sg[0].v}))})]);
}
function dtItSetV(j, v) { dtIt.lines[j].v = +String(v).replace(/[^\d]/g, '') || 0; const e = document.getElementById('dt-ieq'); if (e) e.innerHTML = dtItEq() }
function dtItKind(j, key) { const l = dtIt.lines[j]; l.k = key[0] === 'p' ? 'pct' : 'amt'; l.neg = key[1] === '-'; setSheetBody(dtItBody()) }
function dtItLineAdd() { dtIt.lines.push({n: '', k: 'amt', neg: false, v: 0}); setSheetBody(dtItBody()) }
function dtItLineDel(j) { dtIt.lines.splice(j, 1); setSheetBody(dtItBody()) }
function dtItTax(v) { dtIt.tax = v; setSheetBody(dtItBody()) }
function dtItUser(w) { toggleUser(dtIt, w); setSheetBody(dtItBody()) }
function dtItSave() {
  const a = A[dtIt.aid], name = (dtIt.n || '').trim(), sg = dtSigned();
  const amt = itemAmt({lines: sg.length > 1 ? sg : undefined, price: sg[0].v});
  if (!name || amt <= 0) { toast('品名と金額を入れてください'); return }
  const o = {n: name, price: amt, tax: +dtIt.tax || TAX_DEFAULT, users: dtIt.users.slice()};
  const ln = lnSave({lines: sg}); if (ln) o.lines = ln;
  if ((dtIt.memo || '').trim()) o.memo = dtIt.memo.trim();
  const snap = dtSnap(a), isNew = dtIt.ix == null;
  if (isNew) a.items = [...(a.items || []), o]; else { a.items = (a.items || []).slice(); a.items[dtIt.ix] = o }
  a.amt = payTotal(a); if (a.paid && a.paid.length === 1) a.paid = [{...a.paid[0], amt: a.amt}];
  xEnsure(dtIt.aid); moneyInstall();
  closeSheet(true); save(); rerender();
  toast(isNew ? '品目を追加しました' : '品目を保存しました', {undo: () => { dtRestore(a, snap); rerender() }});
}
function dtItDel() {
  const r = itemRemove(dtIt.aid, dtIt.ix); if (!r.ok) { toast(r.why); return }
  closeSheet(true); go('#/a/' + dtIt.aid);
  toast('品目を消しました', {undo: () => { undoRun(r.undo); go('#/a/' + dtIt.aid) }});
}

/* ---------- 品目の詳細(#/i/{支払いid}/{番号}) ---------- */
route('i', (rid) => {
  const [id, ixs] = (rid || '').split('/'), ix = +ixs, a = A[id], it = a && a.items && a.items[ix];
  if (!it) return appbar('') + empty({shape: 'pay', text: '品目が見つかりません'});
  const amt = itemAmt(it), am = it.lines && it.lines.length > 1 ? lnAmts(it.lines) : null;
  const mine = (xShares(a.x && X[a.x] ? X[a.x] : {pay: id, basis: 'items'})[ix]) || 0;
  const users = usersOf(it);
  return '<div class="dt_pg">' + appbar('品目', {actions: [{icon: 'edit', label: '編集', on: `dtItemSheet('${id}',${ix})`}]})
    + `<section class="dt_hero"><div class="dt_hero_n">${esc(it.n)}</div><div class="dt_hero_a num">${yen(amt)}</div>`
    + `<div class="dt_hero_m">${esc(payLabel(a))} · 税${itemTax(it)}%${it.memo ? ' · ' + esc(it.memo) : ''}</div>${it.unread ? readMark({label: '読み取り'}) : ''}</section>`
    + (am ? sh('内訳') + list([
        row({t: '本体', right: yen(am[0])}),
        ...it.lines.slice(1).map((l, j) => row({t: lnName(l) + (l.k === 'pct' ? ` ${Math.abs(lnV(l))}%` : ''), right: (am[j + 1] < 0 ? '−' : '＋') + yen(Math.abs(am[j + 1]))})),
        row({t: '合計', right: yen(amt)})]) : '')
    + sh('使った人') + `<div class="dt_chips">${peopleOf(a).map(w => `<button class="dt_who" type="button" aria-pressed="${users.includes(w)}" onclick="dtItemU('${id}',${ix},'${w}')">${esc(whoShort(w))}</button>`).join('')}</div>`
    + `<p class="dt_note">自分の分 ${yen(mine)}</p>`
    + `<div class="dt_act"><button class="p-btn p-btn-tonal dt_wide" type="button" onclick="dtItemSheet('${id}',${ix})">${icon('edit')}この品目を編集</button></div>` + '</div>';
});
/** 品目の使った人を切り替える(自分の支出も計算し直す。取り消せる) */
function dtItemU(id, ix, w) {
  const a = A[id], snap = dtSnap(a);
  toggleUser(a.items[ix], w); xEnsure(id); save(); rerender();
  toast('使った人を変えました', {undo: () => { dtRestore(a, snap); rerender() }});
}

/* ---------- 予定の詳細(#/ev) ---------- */
const DT_ALARM = ['なし', '5分前', '10分前', '30分前', '1時間前', '1日前'];
route('ev', (id) => {
  const e = EV[id];
  if (!e) return appbar('') + empty({shape: 'plan', text: '予定が見つかりません'});
  const rec = e.rec && A[e.rec];
  const od = e.rep ? nextOn(e) : e.d;
  const late = !rec && (od < TODAY || (od === TODAY && !e.all && e.s < NOW));
  const future = !late && od >= TODAY;
  const toMin = s => { const [h, m] = s.split(':').map(Number); return h * 60 + m };
  let cd = '';
  if (od === TODAY && !e.all && e.s > NOW) { const diff = toMin(e.s) - toMin(NOW); cd = `あと${diff < 60 ? diff + '分' : Math.floor(diff / 60) + '時間' + (diff % 60 ? (diff % 60) + '分' : '')}` }
  else if (od > TODAY) { const n = dayDiff(od); cd = n === 1 ? '明日' : `${n}日後` }
  const ppl = (e.who || []).filter(w => E[w]);
  const meta = `<span class="dt_meta">${placeOf(e) ? `<span>${icon('place')}${esc(placeOf(e))}</span>` : ''}${ppl.length ? `<span>${icon('people')}${ppl.map(p => esc(E[p].n)).join('、')}</span>` : ''}${cd ? `<span>${esc(cd)}</span>` : ''}${e.rep ? `<span>${icon('repeat')}次回 ${fmtMDW(od)}</span>` : ''}</span>`;
  const tm = e.all ? (e.d2 ? `〜${fmtMD(e.d2)}` : '終日') : `${e.s}–${e.e}`;
  /* 塗り=記録済み(起きた) / 点線=まだ / 赤の点線=過ぎて未記録。種類の札は「予定」か「予定・支払い」 */
  const cap = capsule({kind: rec ? 'done' : late ? 'late' : 'plan', kindLabel: e.pay ? '予定・支払い' : '予定', kindIcon: 'schedule',
    time: tm, title: e.t, sub: meta + (late && !rec ? '<span class="dt_meta">過ぎた予定</span>' : '')});
  /* 場所・参加者(未登録の場所は登録できる) */
  const unreg = e.place && !e.place[0] && e.place[1];
  let secs = sh('関わるエンティティ', {count: (e.place ? 1 : 0) + ppl.length, act: unreg ? {label: '場所として登録', on: `dtPlaceReg('${id}')`} : null})
    + list([
      ...(e.place ? [prop('場所', eref(e.place[0], e.place[1]), {html: true})] : []),
      ...(ppl.length ? [prop('参加者', ppl.map(w => eref(w)).join(' '), {html: true})] : [])]);
  if (!e.place && !ppl.length) secs = '';
  secs += list([
    prop('繰り返し', e.rep || 'なし'),
    ...(e.pay ? [row({t: '支払い', s: catPath(e.pay.cat), right: yen(e.pay.amt), href: '#/ledger'})] : []),
    prop('通知', e.alarm || 'なし'),
    ...(e.memo ? [prop('メモ', e.memo)] : [])]);
  /* この予定のToDo(チェック欄と行。追加できる) */
  const tks = todosOf(id);
  secs += sh('この予定のToDo', {count: tks.length || '', act: {label: '追加', on: `dtTodoAdd('${id}')`}})
    + (tks.length ? `<div class="p-list">${tks.map(k => `<div class="dt_td">${checkbox(!!TD[k].done, {label: TD[k].t, click: `dtTodo('${k}')`})}<a class="dt_td_t ${TD[k].done ? 'is-done' : ''}" href="#/task/${k}">${esc(TD[k].t)}</a><span class="p-chev">${icon('chev')}</span></div>`).join('')}</div>` : '');
  /* 記録: 記録済みならその記録、無ければ記録にする */
  if (rec) secs += sh(e.rep ? '前回の記録' : '記録') + list([row({t: rec.title || '', s: `${fmtMDW(dateOf(rec))} ${timeText(rec)}`, href: '#/a/' + e.rec})]);
  /* 記録にする: 主役のすぐ下(未記録の時。過ぎた予定も含む) */
  const act = rec ? '' : `<div class="dt_act"><button class="p-btn p-btn-fill dt_wide" type="button" onclick="dtRecordEv('${id}')">${icon('check')}${e.pay ? '支払いとして記録する' : '記録にする'}</button></div>`;
  return '<div class="dt_pg">' + appbar('', {actions: [{icon: 'more', label: 'その他の操作', on: `dtEvMenu('${id}')`}]})
    + dayCard(od, {})
    + dtWrap([], dtAx(cap, {future}))
    + act
    + secs + '</div>';
});
function dtEvMenu(id) {
  openSheet({title: '操作', body: list([
    row({t: '編集', chev: false, on: `closeSheet(true);dtEvEdit('${id}')`}),
    ...(EV[id] && !EV[id].pay ? [row({t: 'ToDoにする', chev: false, on: `closeSheet(true);plEvToTodo('${id}')`})] : []),
    row({t: '削除', chev: false, on: `dtEvDel('${id}')`})])});
}
function dtPlaceReg(id) {
  const e = EV[id]; if (!e || !e.place) return;
  const name = e.place[1], er = entAdd({n: name, t: ['place'], facts: [], rels: []});
  if (!er.ok) { toast(er.why); return }
  const old = e.place[0]; e.place[0] = er.id; save(); rerender();
  toast(`「${name}」を場所として登録しました`, {undo: () => { undoRun(er.undo); e.place[0] = old; save(); rerender() }});
}
/** 予定を記録にする(過ぎた予定も。支払いの予定は支払いとして) */
function dtRecordEv(id) {
  const e = EV[id]; if (!e || (e.rec && A[e.rec])) return;
  const day = dtDayFor(e.d);
  const placeId = e.place ? e.place[0] : null, placeName = e.place ? (e.place[0] && E[e.place[0]] ? E[e.place[0]].n : e.place[1]) : '';
  let aid;
  if (e.pay) {
    aid = makePayment({parent: day, time: e.s || '09:00', items: [{n: e.t, price: e.pay.amt, users: ['me'], tax: 10}],
      payee: [placeId, placeName || e.t], roles: (e.who || []).map(w => ['同席', w]), how: 'クレジットカード ••1234', cat: e.pay.cat});
  } else {
    aid = uid('c');
    const roles = (e.who || []).map(w => ['参加', w]);
    if (e.place) roles.push(['場所', placeId, placeName]);
    const span = !e.all && e.s && e.e ? {time: e.s, end: e.e} : {time: e.s || '09:00'};
    A[aid] = {type: '出来事', parent: day, ...span, title: e.t, roles, body: '', kids: []};
    A[day].kids.push(aid);
  }
  e.rec = aid; save(); go('#/a/' + aid);
  toast('記録にしました', {undo: () => { recDelete(aid); save(); go('#/ev/' + id) }});
}
/** ToDo の完了⇄未完了(予定の詳細の中で) */
function dtTodo(k) {
  const r = todoToggle(k); if (!r.ok) { toast(r.why); return }
  rerender();
  toast(TD[k] && TD[k].done ? '完了にしました' : '完了を取り消しました', {undo: () => { undoRun(r.undo); rerender() }});
}
function dtTodoAdd(evId) {
  dtTd = {t: ''};
  openSheet({title: 'ToDoを追加', primary: {label: '追加', on: () => dtTodoSave(evId)},
    body: field('ToDo', input({ph: 'やること', oninput: 'dtTd.t=this.value'}))});
}
let dtTd = null;
function dtTodoSave(evId) {
  const t = (dtTd.t || '').trim(); if (!t) { toast('ToDoを入れてください'); return }
  const k = uid('t'); PLN[k] = {kind: 'ToDo', t, ev: evId};
  closeSheet(true); save(); rerender();
  toast('ToDoを追加しました', {undo: () => { delete PLN[k]; save(); rerender() }});
}

/* ---------- 予定の編集・削除(planUpdate / planRemove。取り消せる) ---------- */
let dtE = null, dtEInit = '';
const DT_REP = ['なし', '毎日', '毎週', '毎月'];
/** 保存形の繰り返し(毎月27日 など)を選択肢の名前にする */
const DT_REP_OF = r => !r ? 'なし' : String(r).startsWith('毎日') ? '毎日' : String(r).startsWith('毎週') ? '毎週' : String(r).startsWith('毎月') ? '毎月' : 'なし';
function dtEvEdit(id) {
  const e = EV[id]; if (!e) return;
  const pn = e.place ? (e.place[0] && E[e.place[0]] ? E[e.place[0]].n : (e.place[1] || '')) : '';
  dtE = {id, t: e.t, d: e.d, all: !!e.all, s: e.s || '', e: e.e || '', pn, pid: e.place ? e.place[0] : null, pold: e.place ? e.place[1] : null,
    who: (e.who || []).filter(w => E[w]), rep: DT_REP_OF(e.rep), alarm: e.alarm || 'なし', memo: e.memo || ''};
  dtEInit = JSON.stringify(dtE);
  openSheet({title: '予定を編集', dirty: () => JSON.stringify(dtE) !== dtEInit, primary: {label: '保存', on: dtEvSave}, body: dtEvBody()});
}
function dtEvBody() {
  const people = Object.keys(E).filter(k => k !== 'me');
  const alarms = DT_ALARM.includes(dtE.alarm) ? DT_ALARM : [...DT_ALARM, dtE.alarm];
  return field('題', input({value: dtE.t, oninput: 'dtE.t=this.value'}))
    + field('日付', input({type: 'date', value: dtE.d, onchange: 'dtE.d=this.value'}))
    + `<div class="dt_row"><span>終日</span>${toggle(dtE.all, {label: '終日', click: 'dtEvAll()'})}</div>`
    + (dtE.all ? '' : field('開始', input({type: 'time', value: dtE.s, onchange: 'dtE.s=this.value'}))
      + field('終わり', input({type: 'time', value: dtE.e, onchange: 'dtE.e=this.value'})))
    + field('場所', input({value: dtE.pn, ph: '場所の名前', oninput: 'dtE.pn=this.value'}))
    + sh('参加者') + `<div class="dt_who_row" style="padding:0 var(--gutter)">${people.map(k => `<button class="dt_who" type="button" aria-pressed="${dtE.who.includes(k)}" onclick="dtEvWho('${k}')">${esc(E[k].n)}</button>`).join('')}</div>`
    + field('繰り返し', select({options: DT_REP, value: dtE.rep, onchange: 'dtE.rep=this.value'}))
    + field('通知', select({options: alarms, value: dtE.alarm, onchange: 'dtE.alarm=this.value'}))
    + field('メモ', input({value: dtE.memo, oninput: 'dtE.memo=this.value'}))
    + `<button class="dt_quiet" type="button" onclick="dtEvDel('${dtE.id}')">${icon('trash')}予定を消す</button>`;
}
function dtEvAll() { dtE.all = !dtE.all; setSheetBody(dtEvBody()) }
function dtEvWho(k) { dtE.who = dtE.who.includes(k) ? dtE.who.filter(w => w !== k) : [...dtE.who, k]; setSheetBody(dtEvBody()) }
function dtEvRep(v) { dtE.rep = v; setSheetBody(dtEvBody()) }
function dtEvSave() {
  const pn = (dtE.pn || '').trim(), same = pn && pn === dtE.pold;
  const patch = {
    t: dtE.t, d: dtE.d, all: dtE.all ? 1 : null,
    s: dtE.all ? null : (dtE.s || null), e: dtE.all ? null : (dtE.e || null),
    place: pn ? [same ? dtE.pid : null, pn] : null,
    who: dtE.who.length ? dtE.who : null,
    rep: dtE.rep === '毎日' ? '毎日' : dtE.rep === '毎週' ? `毎週${dowJ(dtE.d)}曜` : dtE.rep === '毎月' ? `毎月${+dtE.d.slice(8)}日` : null,
    alarm: dtE.alarm && dtE.alarm !== 'なし' ? dtE.alarm : null,
    memo: dtE.memo || null,
  };
  const r = planUpdate(dtE.id, patch); if (!r.ok) { toast(r.why); return }
  closeSheet(true); rerender();
  toast('予定を保存しました', {undo: () => { undoRun(r.undo); rerender() }});
}
function dtEvDel(id) {
  const r = planRemove(id); if (!r.ok) { toast(r.why); return }
  closeSheet(true); goBack();
  toast('予定を消しました', {undo: () => { undoRun(r.undo); go('#/ev/' + id) }});
}
