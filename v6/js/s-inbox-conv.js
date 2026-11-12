/* 個人記録 v6.2 — 雑記を記録にする変換シート(担当 D。仕様 INBOX.md §8) */
'use strict';
/* 変換シート。開いている間の下書きは CV に持つ(oninput では描き直さない) */
let CV = null;
const CV_TYPES = [['pay', '支払い'], ['event', '出来事'], ['plan', '予定'], ['task', 'ToDo'], ['memo', 'メモ']];
const CV_KIND_SKIP = ['pay', 'holiday', 'wake', 'sleep'];
const cvHasTime = t => t === 'pay' || t === 'event' || t === 'plan';
const cvOn = k => `cvIn(${q(k)}, this.value)`;

/* 記録 {t, k} の今の値を cand の形に戻す(未確定の記録を直す時の初期値) */
function cvRecCand(t, k) {
  if (t === 'acts') {
    const a = DB.acts[k]; if (!a) return null;
    const ents = (a.roles || []).map(r => r.e).filter(Boolean), start = a.allDay ? undefined : (a.start || undefined);
    if (isPay(a) && !isPlan(a)) {
      const pr = rolesOf(a, '支払先')[0], x = xCats(k)[0];
      return { type: 'pay', amt: payTotal(a), date: a.date, start: a.start || NOW, payee: pr && pr.e ? { e: pr.e } : { name: payeeOf(a) || actTitle(a) }, text: payeeOf(a) || actTitle(a), cat: x && x.cat || undefined, ents };
    }
    if (isPlan(a)) return { type: 'plan', title: actTitle(a), date: a.date, start, end: a.end || undefined, kind: KINDS[a.kind] ? a.kind : 'event', ents };
    return { type: 'event', title: actTitle(a), date: a.date, start, end: a.end || undefined, kind: KINDS[a.kind] ? a.kind : 'event', ents };
  }
  if (t === 'tasks') { const k2 = DB.tasks[k]; return k2 ? { type: 'task', t: k2.t, due: k2.due || undefined, list: k2.list || undefined, pri: !!k2.pri, ents: k2.ents || [] } : null; }
  if (t === 'memos') { const m = DB.memos[k]; return m ? { type: 'memo', body: m.body, ents: m.ents || [] } : null; }
  return null;
}
/** cand を下書き(CV.f)に写す */
function cvFromCand(c) {
  const f = { title: '', date: '', start: '', end: '', allDay: false, kind: 'event', amt: '', cat: '', list: '', pri: false, payeeE: '', ents: c.ents || [] };
  if (c.type === 'pay') {
    const e = c.payee && c.payee.e ? E(c.payee.e) : null;
    f.payeeE = e ? c.payee.e : '';
    f.title = e ? e.n : (c.payee && c.payee.name) || c.text || '';
    f.amt = c.amt ? String(c.amt) : '';
    f.date = c.date || TODAY; f.start = c.start || NOW;
    f.cat = c.cat || (typeof catGuess === 'function' ? catGuess(c.payee, c.text || f.title) : '');
  } else if (c.type === 'event' || c.type === 'plan') {
    f.title = c.title || ''; f.date = c.date || TODAY; f.start = c.start || ''; f.end = c.end || '';
    f.allDay = !c.start; f.kind = c.kind || 'event';
  } else if (c.type === 'task') {
    f.title = c.t || ''; f.date = c.due || ''; f.list = c.list || ''; f.pri = !!c.pri;
  } else {
    f.title = c.body || '';
  }
  return f;
}
/** 種類を切り替えた時、共通の項目(題・日付・時刻)を引き継ぐ */
function cvType(k) {
  if (!CV || k === CV.type) return;
  const old = CV.f, body = CV.body;
  const c = (typeof candFromType === 'function' && candFromType(body, k)) || { type: k, title: body, body, ents: [] };
  const next = cvFromCand({ ...c, type: k });
  if (old.title.trim()) { next.title = old.title; next.payeeE = old.payeeE; }
  if (old.date) next.date = old.date;
  if (old.start && cvHasTime(k)) { next.start = old.start; next.end = old.end; next.allDay = old.allDay && k !== 'pay'; }
  if ((k === 'event' || k === 'plan') && (CV.type === 'event' || CV.type === 'plan')) next.kind = old.kind;
  CV.f = next; CV.type = k; CV.touched = true;
  setSheetBody(cvBody());
}
/** 入力の値を下書きに書く(描き直さない) */
function cvIn(k, v) { if (!CV) return; CV.f[k] = v; CV.touched = true; }
/** 選ぶシートの結果を下書きに入れて描き直す */
function cvSetPick(k, v) { if (!CV) return; CV.f[k] = v || ''; CV.touched = true; setSheetBody(cvBody()); }
function cvToggle(k) { if (!CV) return; CV.f[k] = !CV.f[k]; if (k === 'allDay' && !CV.f.allDay && !CV.f.start) CV.f.start = NOW; CV.touched = true; setSheetBody(cvBody()); }
function cvClearDue() { if (!CV) return; CV.f.date = ''; CV.touched = true; setSheetBody(cvBody()); }
function cvMore() { const el = $('#cv-jb'); if (el) el.classList.add('is-full'); const b = $('.cv-more'); if (b) b.remove(); }

/* 下書き → cand(§3.1 の形) */
const cvNum = s => { const n = Number(String(s == null ? '' : s).replace(/[,，\s¥￥円]/g, '')); return Number.isInteger(n) ? n : NaN; };
/* 空の項目は cand から外す(時刻は外さない。空の時刻を検査で止めるため) */
const cvStrip = o => { Object.keys(o).forEach(k => (o[k] === undefined || o[k] === null) && delete o[k]); return o; };
function cvPayee() {
  const name = CV.f.title.trim(); if (!name) return null;
  const e = CV.f.payeeE; return e && E(e) && E(e).n === name ? { e } : { name };
}
function cvCand() {
  const f = CV.f, t = CV.type, ents = f.ents || [];
  if (t === 'pay') return cvStrip({ type: 'pay', amt: cvNum(f.amt), date: f.date, start: f.start, payee: cvPayee(), text: f.title.trim(), cat: f.cat || undefined, ents });
  if (t === 'event' || t === 'plan') {
    const o = cvStrip({ type: t, title: f.title.trim(), date: f.date, kind: f.kind || 'event', ents });
    if (!f.allDay) { o.start = f.start; if (f.end) o.end = f.end; }
    return o;
  }
  if (t === 'task') return cvStrip({ type: 'task', t: f.title.trim(), due: f.date || undefined, list: f.list || undefined, pri: f.pri ? 1 : undefined, ents });
  return { type: 'memo', body: f.title, ents };
}
/** 検査(失敗は理由の文字。done() がトーストにする) */
function cvCheck(c) {
  if (c.type === 'memo') return c.body.trim() ? '' : '本文を入れてください';
  if (c.type === 'task') return !c.t ? '題を入れてください' : c.due && !isDate(c.due) ? '期限を直してください' : '';
  const title = c.type === 'pay' ? c.text : c.title;
  if (!title) return c.type === 'pay' ? '支払先を入れてください' : '題を入れてください';
  if (c.type === 'pay' && !(c.amt > 0)) return '金額を入れてください';
  if (!isDate(c.date)) return '日付を入れてください';
  if (c.start === undefined) return '';                 /* 終日(予定・出来事) */
  if (!isHM(c.start)) return '時刻を入れてください';
  if (c.end !== undefined && (!isHM(c.end) || c.end <= c.start)) return '終わりは開始より後にしてください';
  return '';
}

/* 種類ごとの項目 */
function cvCatItems() {
  const out = [], walk = (p, d) => catKids(p).forEach(k => { out.push({ k, label: catName(k), sub: d ? catPath(k) : '', depth: d }); walk(k, d + 1); });
  walk(null, 0); return out;
}
function cvBody() {
  const f = CV.f, t = CV.type, long = CV.body.length > 90 || CV.body.split('\n').length > 3;
  const top = `<section class="card pad cv-jot"><p class="cv-jot-b${long ? '' : ' is-full'}" id="cv-jb">${esc(CV.body)}</p>${long ? `<button class="cv-more" onclick="cvMore()">全文</button>` : ''}</section>`;
  const dateF = field('日付', input({ type: 'date', value: f.date, oninput: cvOn('date'), label: '日付' }));
  const timeF = (k, l) => field(l, input({ type: 'time', value: f[k], oninput: cvOn(k), label: l }));
  let html = '';
  if (t === 'pay') {
    const cat = f.cat ? catTag(f.cat, { path: true }) : '選ぶ';
    html = field('支払先', input({ value: f.title, oninput: cvOn('title'), label: '支払先' }))
      + field('金額', input({ mode: 'numeric', value: f.amt, oninput: cvOn('amt'), label: '金額', ph: '0' }))
      + `<div class="grid2">${dateF}${timeF('start', '時刻')}</div>`
      + pickField('カテゴリ', cat, 'cvPickCat()', { emptyv: !f.cat });
  } else if (t === 'event' || t === 'plan') {
    const kind = KINDS[f.kind] ? f.kind : 'event';
    html = field('題', input({ value: f.title, oninput: cvOn('title'), label: '題' })) + dateF
      + rows(row({ t: '終日', right: toggle(f.allDay, 'cvToggle("allDay")', '終日') }))
      + (f.allDay ? '' : `<div class="grid2">${timeF('start', '開始')}${timeF('end', '終了')}</div>`)
      + pickField('種類', `${ic(KINDS[kind].ic)}${esc(KINDS[kind].n)}`, 'cvPickKind()');
  } else if (t === 'task') {
    const lists = Object.keys(DB.lists).map(k => [k, DB.lists[k].n]);
    html = field('やること', input({ value: f.title, oninput: cvOn('title'), label: 'やること' }))
      + field('期限', input({ type: 'date', value: f.date, oninput: cvOn('date'), label: '期限' }))
      + (f.date ? `<div>${btn('期限を消す', 'cvClearDue()', 'ghost', { sm: 1 })}</div>` : '')
      + field('リスト', select({ value: f.list, options: [['', '未分類'], ...lists], onchange: cvOn('list'), label: 'リスト' }))
      + rows(row({ t: '大事', right: toggle(f.pri, 'cvToggle("pri")', '大事') }));
  } else {
    html = field('本文', textarea({ value: f.title, rows: 6, oninput: cvOn('title'), label: '本文' }));
  }
  const ents = (f.ents || []).filter(e => E(e));
  return `<div class="cv">${top}${chips(CV_TYPES, t, 'cvType')}<div class="stack cv-f">${html}${ents.length ? `<div class="ents">${ents.map(e => ent(e, { link: false, short: 1 })).join('')}</div>` : ''}</div><div class="cv-end">${btn('詳しく直す', 'cvDetail()', 'ghost', { icon: 'pencil' })}</div></div>`;
}
function cvPickCat() {
  if (!CV) return;
  pick({ title: 'カテゴリ', items: cvCatItems(), cur: CV.f.cat || null, none: { label: '未設定' }, on: k => cvSetPick('cat', k) });
}
function cvPickKind() {
  if (!CV) return;
  const items = Object.keys(KINDS).filter(k => !CV_KIND_SKIP.includes(k)).map(k => ({ k, label: KINDS[k].n, lead: ic(KINDS[k].ic) }));
  pick({ title: '種類', items, cur: CV.f.kind, on: k => cvSetPick('kind', k) });
}

/* 保存・詳しく直す */
const cvHref = r => r.t === 'acts' ? `#/a/${r.k}` : r.t === 'tasks' ? `#/task/${r.k}` : `#/memo?focus=${r.k}`;
function cvRun(c, confirm) { return typeof jotConvert === 'function' ? jotConvert(CV.jid, c, { confirm: !!confirm }) : { ok: false, why: '変換の仕組みがまだありません' }; }
/** 確定して登録(confirm = true)/ 未確定のまま保存(false) */
function cvSave(confirm) {
  if (!CV) return;
  const c = cvCand(), why = cvCheck(c); if (why) return done({ ok: false, why });
  const r = cvRun(c, confirm);
  if (done(r, { close: true, msg: confirm ? '記録にしました' : '未確定のまま保存しました' })) typeof inRefresh === 'function' && inRefresh();
}
/** 今の下書きを未確定で保存して、その記録の詳細へ */
function cvDetail() {
  if (!CV) return;
  const c = cvCand(), why = cvCheck(c); if (why) return done({ ok: false, why });
  const r = cvRun(c, false);
  if (!r.ok) return done(r);
  const rec = (r.recs || [])[0];
  if (done(r, { close: true, go: rec ? cvHref(rec) : null, msg: '未確定で保存しました' })) typeof inRefresh === 'function' && inRefresh();
}

/** 変換シートを開く。o = {rec:{t,k}, cand} */
function openConv(jid, o = {}) {
  const jot = DB.jots && DB.jots[jid];
  if (!jot) { toast('雑記が見つかりません', { bad: 1 }); return; }
  const body = jot.body || '';
  CV = { jid, body, type: 'memo', f: null, touched: false };
  let c = o.cand || (o.rec ? cvRecCand(o.rec.t, o.rec.k) : null);
  if (!c) c = (typeof ruleCandidates === 'function' && (ruleCandidates(body) || [])[0]);
  if (!c) c = (typeof candFromType === 'function' && candFromType(body, 'memo')) || { type: 'memo', body, ents: [] };
  if (!CV_TYPES.some(([k]) => k === c.type)) c = { ...c, type: 'memo' };
  CV.type = c.type; CV.f = cvFromCand(c);
  openSheet({
    id: 'conv', title: '雑記を記録にする', body: cvBody(), dirty: () => !!(CV && CV.touched),
    onClose: () => { CV = null; },
    primary: { label: '確定して登録', on: () => cvSave(true) },
    secondary: { label: '未確定のまま保存', on: () => cvSave(false) },
  });
}
