/* =====================================================================
   メモ(#/memo・#/notes・#/n/{id}) — 接頭辞 mm_(関数)/ mm-(CSS)。見た目は css/screens/memo.css。
   ・すぐメモ: 角を折った紙。編集のシート(本文・縁・ピン留め・削除。取り消しあり)。ホームも mmCard を使う
   ・#/memo: 書く欄 → ピン留め → 日ごと → エンティティのメモ(検索は全文が対象)
   ・#/notes: エンティティのメモの一覧(年つきの日時・上から新しく書く)
   ・#/n/{id}: エンティティのメモ本文(編集・版+1・サブページの追加と開閉・{e:}{a:} は押せる)
   ===================================================================== */

/* 画面の状態(再描画しても残す) */
let mmDraft = {body: '', links: []};   /* 書く欄の下書き */
let mmPick = false;                    /* 「エンティティを付ける」の候補を開いているか */
let mmSearch = false, mmQ = '';        /* 検索(小文字) */
let mmSheet = null;                    /* すぐメモの編集シートの作業中の値 {id, body, links, pin, orig} */
let mmNW = null;                       /* エンティティのメモの編集シート {id, secs, orig, at} */
const mmSubOpen = {};                  /* サブページの開閉 key → bool */

/* ---------- 文字・日付の小道具 ---------- */
/** 'YYYY-MM-DD HH:MM' → 'YYYY/MM/DD HH:MM'(年つき) */
const mmDT = s => String(s || '').replace(/-/g, '/');
/** 相対日付の見出し(今日・昨日・明日は言葉、それ以外は 10/27(火)) */
const mmDayLabel = d => { const r = relDay(d); return ['今日', '昨日', '明日'].includes(r) ? r : `${fmtMD(d)}(${dowJ(d)})` };
/** 本文の {e:id} {a:id} を取り除いた文字(検索・一覧の1行) */
const mmPlain = s => String(s || '').replace(/\{[ea]:[\w-]+\}/g, '');
/** エンティティのメモの本文の冒頭2行まで(一覧の紙。見出しは除く) */
const mmNoteLines = id => {
  const n = NOTES[id]; if (!n) return '';
  const out = [];
  for (const [, t] of n.body) {
    for (const x of mmPlain(t).split('\n')) { const line = x.trim(); if (line) out.push(line); if (out.length === 2) return out.join('\n') }
  }
  return out.join('\n');
};
/** ピン留めの印(ピンの形。文字の札ではない)。parts の PARTS_IC に pin が入るまでは画面の中で持つ */
const mm_pinIcon = () => icon('pin');
/** 本文の参照を押せる形にする(先に esc してから {e:} {a:} を置き換える) */
const mmRich = s => esc(s)
  .replace(/\{e:([\w-]+)\}/g, (m, k) => E[k]
    ? `<a class="mm-in-e" href="#/e/${k}"><span class="p-dot" aria-hidden="true"></span>${esc(E[k].n)}</a>`
    : `<span class="p-ref is-unreg"><span class="p-dot is-unreg" aria-hidden="true"></span>未登録</span>`)
  .replace(/\{a:([\w-]+)\}/g, (m, k) => A[k]
    ? `<a class="mm-in-a" href="#/a/${k}">${esc(A[k].type === '支払い' ? payLabel(A[k]) : (A[k].title || '記録'))}</a>`
    : '');
/** 検索の対象: すぐメモ(本文・縁の名前・結び付いた記録の題名) */
const mmText = id => {
  const m = MEMOS[id]; if (!m) return '';
  return [m.body, ...(m.links || []).map(k => E[k] ? E[k].n : ''), m.rec && A[m.rec] ? (A[m.rec].title || '') : ''].join(' ').toLowerCase();
};
/** 検索の対象: エンティティのメモ(縁の名前・本文の全文。すぐメモと同じ範囲) */
const mmNoteText = id => {
  if (!E[id]) return '';
  const body = NOTES[id] ? NOTES[id].body.map(([h, t]) => `${h} ${mmPlain(t)}`).join(' ') : '';
  return `${E[id].n} ${body}`.toLowerCase();
};
const mmHit = id => !mmQ || mmText(id).includes(mmQ);

/* ---------- 紙(すぐメモ・エンティティのメモの一覧) ---------- */
/** すぐメモ1件 = 角を折った紙1枚。押すと編集のシート。o.lines=2 でホームの短い形。o.withDate で日付も出す */
function mmCard(id, o = {}) {
  const m = MEMOS[id]; if (!m) return '';
  const when = (o.withDate ? fmtMD(m.at.slice(0, 10)) + ' ' : '') + m.at.slice(11, 16);
  const links = (m.links || []).filter(k => E[k]).map(k => `<span onclick="event.stopPropagation()">${eref(k)}</span>`).join('');
  const rec = m.rec && A[m.rec]
    ? `<span onclick="event.stopPropagation()"><a class="mm-rec" href="#/a/${esc(m.rec)}">${icon('activity')}${esc(A[m.rec].type === '支払い' ? payLabel(A[m.rec]) : (A[m.rec].title || '記録'))}</a></span>`
    : '';
  const bound = links ? ' is-bound' : '';
  return `<div class="mm-card mm-paper${o.lines ? ' is-short' : ''}${bound}" role="button" tabindex="0" data-q="${esc(mmText(id))}" onclick="mmOpen('${esc(id)}')" onkeydown="if(event.key==='Enter')mmOpen('${esc(id)}')">`
    + `<p class="mm-body">${esc(m.body)}</p>`
    + `<div class="mm-meta"><time class="mm-time num">${esc(when)}</time>${m.pin ? `<span class="mm-pin" role="img" aria-label="ピン留め">${mm_pinIcon()}</span>` : ''}</div>`
    + (links || rec ? `<div class="mm-refs">${links}${rec}</div>` : '')
    + `</div>`;
}
/** エンティティのメモ = 縁ごとに1枚。押すと #/n/{縁} */
function mmNoteCard(id) {
  const n = NOTES[id];
  return `<a class="mm-card mm-paper mm-note is-bound" href="#/n/${esc(id)}" data-q="${esc(mmNoteText(id))}">`
    + `<span class="mm-note-h"><span class="p-dot" aria-hidden="true"></span><b>${esc(E[id].n)}のメモ</b></span>`
    + `<span class="mm-dt num">更新 ${esc(mmDT(n.upd))}</span>`
    + `<span class="mm-ln">${esc(mmNoteLines(id))}</span></a>`;
}

/* ---------- すぐメモ: 書く欄・保存 ---------- */
function mmGrow(el) { el.style.height = 'auto'; el.style.height = el.scrollHeight + 'px' }
function mmComposer() {
  const d = mmDraft;
  const ents = Object.keys(E).filter(k => k !== 'me');
  return `<section class="mm-compose mm-paper">`
    + `<textarea id="mm-body" class="mm-ta" rows="2" placeholder="思いついたことを書く" aria-label="メモの本文" oninput="mmDraft.body=this.value;mmGrow(this)" onkeydown="if(event.key==='Enter'&&(event.metaKey||event.ctrlKey))mmSaveNew()">${esc(d.body)}</textarea>`
    + (d.links.length ? `<div class="mm-refs">${d.links.filter(k => E[k]).map(k => eref(k)).join('')}</div>` : '')
    + (mmPick ? `<div class="mm-pick">${chips(ents.map(k => ({k, label: E[k].n})), d.links, 'mmPickLink')}</div>` : '')
    + `<div class="mm-acts"><button class="p-btn p-btn-tonal" type="button" aria-pressed="${mmPick}" onclick="mmTogglePick()">エンティティを付ける</button>`
    + `<button class="p-btn p-btn-fill" type="button" onclick="mmSaveNew()">保存</button></div>`
    + `</section>`;
}
function mmTogglePick() { mmPick = !mmPick; rerender() }
function mmPickLink(k) { const l = mmDraft.links; l.includes(k) ? l.splice(l.indexOf(k), 1) : l.push(k); rerender() }
function mmSaveNew() {
  const body = mmDraft.body.trim();
  if (!body) { toast('メモを入れてください'); return }
  const id = memoAdd(body, [...mmDraft.links]);
  mmDraft = {body: '', links: []}; mmPick = false;
  save();
  rerender();
  toast('メモを保存しました', {undo: () => { memoRemove(id) }});
}
/** 検索: 入力のたびに描き直さず、表示の出し入れだけをする(入力欄の focus を保つ) */
function mmSearchToggle() {
  mmSearch = !mmSearch; if (!mmSearch) mmQ = '';
  if (mmSearch) after(() => document.getElementById('mm-q')?.focus());
  rerender();
}
function mmFilter(v) {
  mmQ = v.trim().toLowerCase();
  document.querySelectorAll('#mm-list .mm-card').forEach(c => { c.hidden = !!mmQ && !c.dataset.q.includes(mmQ) });
  let any = 0;
  document.querySelectorAll('#mm-list .mm-grp').forEach(g => { const n = [...g.querySelectorAll('.mm-card')].filter(c => !c.hidden).length; g.hidden = !n; any += n });
  const none = document.getElementById('mm-none'); if (none) none.hidden = !!any;
}

/* ---------- すぐメモの編集シート(本文・縁・ピン留め・削除) ---------- */
const mmSheetKey = s => s.body + '|' + s.links.join(',') + '|' + !!s.pin;
function mmOpen(id) {
  const m = MEMOS[id]; if (!m) return;
  mmSheet = {id, body: m.body, links: [...(m.links || [])], pin: !!m.pin, orig: ''};
  mmSheet.orig = mmSheetKey(mmSheet);
  openSheet({title: 'メモ', body: mmSheetBody(), primary: {label: '保存', on: () => mmSheetSave()}, dirty: () => !!mmSheet && mmSheetKey(mmSheet) !== mmSheet.orig});
}
function mmSheetBody() {
  const s = mmSheet, ents = Object.keys(E).filter(k => k !== 'me');
  return `<div class="mm-ed">`
    + `<div class="mm-paper mm-ed-paper"><textarea class="mm-ed-body" aria-label="メモの本文" oninput="mmSheet.body=this.value">${esc(s.body)}</textarea></div>`
    + sh('縁')
    + `<div class="mm-pick">${chips(ents.map(k => ({k, label: E[k].n})), s.links, 'mmSheetLink')}</div>`
    + `<div class="p-list">`
    + `<div class="p-row"><span class="p-row-body"><span class="p-row-t">ピン留め</span></span>${toggle(s.pin, {click: 'mmSheetPin()', label: 'ピン留め'})}</div>`
    + `</div>`
    + `<button class="p-btn p-btn-tonal mm-del" type="button" onclick="mmDel('${esc(s.id)}')">${icon('trash')}メモを削除</button>`
    + `</div>`;
}
function mmSheetLink(k) { const l = mmSheet.links; l.includes(k) ? l.splice(l.indexOf(k), 1) : l.push(k); setSheetBody(mmSheetBody()) }
function mmSheetPin() { mmSheet.pin = !mmSheet.pin; setSheetBody(mmSheetBody()) }
function mmSheetSave() {
  const s = mmSheet, m = MEMOS[s.id]; if (!m) return;
  const r1 = memoUpdate(s.id, {body: s.body, links: s.links});
  if (!r1.ok) return toast(r1.why);
  const undos = [r1];
  if (!!m.pin !== s.pin) { const r2 = memoPin(s.id, s.pin); if (r2.ok) undos.push(r2) }
  mmSheet = null;
  save();
  closeSheet(true);
  rerender();
  toast('メモを保存しました', {undo: () => undos.slice().reverse().forEach(r => undoRun(r.undo))});
}
function mmDel(id) {
  const r = memoRemove(id);
  if (!r.ok) return toast(r.why);
  mmSheet = null;
  closeSheet(true);
  rerender();
  toast('メモを削除しました', {undo: () => undoRun(r.undo)});
}

/* ---------- エンティティのメモ(#/n/{id}) の編集 ---------- */
/** 編集シートを開く。メモが無い縁なら空の1段落から始める(保存で作る) */
function mmNoteEdit(id) {
  const n = NOTES[id];
  const secs = n && n.body.length ? n.body.map(([h, t]) => [h, t]) : [['', '']];
  mmNW = {id, secs, at: secs.length - 1, orig: JSON.stringify(secs)};
  openSheet({
    title: 'メモを編集',
    body: mmNoteEditBody(),
    primary: {label: '保存', on: () => mmNoteSave()},
    dirty: () => !!mmNW && JSON.stringify(mmNW.secs) !== mmNW.orig,
  });
}
function mmNoteEditBody() {
  const w = mmNW, ents = Object.keys(E).filter(k => k !== 'me');
  return `<div class="mm-ed">`
    + w.secs.map(([h, t], i) => `<div class="mm-paper mm-ed-sec">`
      + `<div class="mm-ed-top"><input class="p-input mm-ed-h" aria-label="見出し ${i + 1}" placeholder="見出し" value="${esc(h)}" oninput="mmNW.secs[${i}][0]=this.value">`
      + iconBtn('trash', `段落 ${i + 1} を消す`, `mmNoteDelSec(${i})`) + `</div>`
      + `<textarea class="mm-ed-t" aria-label="本文 ${i + 1}" placeholder="本文" oninput="mmNW.secs[${i}][1]=this.value" onfocus="mmNW.at=${i}">${esc(t)}</textarea></div>`).join('')
    + `<button class="p-btn p-btn-tonal" type="button" onclick="mmNoteAddSec()">${icon('plus')}段落を足す</button>`
    + sh('縁を入れる')
    + `<div class="mm-pick">${chips(ents.map(k => ({k, label: E[k].n})), [], 'mmNoteRef')}</div>`
    + `</div>`;
}
function mmNoteAddSec() { mmNW.secs.push(['', '']); mmNW.at = mmNW.secs.length - 1; setSheetBody(mmNoteEditBody()) }
function mmNoteDelSec(i) {
  mmNW.secs.splice(i, 1);
  if (!mmNW.secs.length) mmNW.secs.push(['', '']);
  mmNW.at = mmNW.secs.length - 1;
  setSheetBody(mmNoteEditBody());
}
/** 縁を参照として本文の末尾に入れる({e:id})。最後に触った段落へ */
function mmNoteRef(k) {
  const w = mmNW, i = Math.min(Math.max(w.at || 0, 0), w.secs.length - 1);
  const t = w.secs[i][1];
  w.secs[i][1] = (t && !/\s$/.test(t) ? t + ' ' : t) + `{e:${k}}`;
  setSheetBody(mmNoteEditBody());
}
function mmNoteSave() {
  const w = mmNW;
  const secs = w.secs.map(([h, t]) => [String(h).trim(), String(t).trim()]).filter(([h, t]) => h || t);
  if (!secs.length) return toast('本文を入れてください');
  const r = noteSave(w.id, secs);
  if (!r.ok) return toast(r.why);
  mmNW = null;
  closeSheet(true);
  rerender();
  toast('メモを保存しました', {undo: () => undoRun(r.undo)});
}

/* ---------- エンティティのメモの一覧(#/notes)の新規 ---------- */
function mmNewNote() {
  const ks = Object.keys(E).filter(k => k !== 'me' && !NOTES[k]);
  if (!ks.length) return toast('メモのない縁がありません');
  openSheet({
    title: 'メモを書く縁',
    body: list(ks.map(k => row({t: E[k].n, s: (E[k].t || []).map(t => TYPES[t] ? TYPES[t].n : '').filter(Boolean).join('・'),
      lead: '<span class="p-dot" aria-hidden="true"></span>', on: `mmStartNote('${esc(k)}')`}))),
  });
}
function mmStartNote(id) {
  if (!NOTES[id]) { const r = noteSave(id, []); if (!r.ok) return toast(r.why) }
  closeSheet(true);
  go('#/n/' + id);
}

/* ---------- サブページ(追加・開閉) ---------- */
function mmSubToggle(key) { mmSubOpen[key] = !mmSubOpen[key]; rerender() }
function mmSubAddSheet(id) {
  openSheet({
    title: 'サブページを足す',
    body: `<div class="mm-ed">${field('名前', input({id: 'mm-sub-name', ph: '例: 好きな店'}))}${field('補足', input({id: 'mm-sub-note', ph: '例: リンク 3'}))}</div>`,
    primary: {label: 'サブページを足す', on: () => mmSubAdd(id)},
  });
}
function mmSubAdd(id) {
  const name = (document.getElementById('mm-sub-name') || {}).value || '';
  const note = (document.getElementById('mm-sub-note') || {}).value || '';
  const r = noteSubAdd(id, name, note);
  if (!r.ok) return toast(r.why);
  closeSheet(true);
  rerender();
  toast('サブページを足しました', {undo: () => undoRun(r.undo)});
}

/** 1つのエンティティのメモ本文(角を折った紙。段落ごとに見出しと本文) */
function mmNoteBody(id, n) {
  return `<article class="mm-paper mm-doc is-bound">`
    + `<header class="mm-doc-h"><h3 class="mm-doc-t">${esc(E[id].n)}のメモ</h3>`
    + `<p class="mm-doc-m num">主観・最新 · 更新 ${esc(mmDT(n.upd))} · ${esc(n.ver || 1)}版目</p></header>`
    + (n.body.length ? n.body.map(([h, t]) => `${h ? `<h4 class="mm-sec-h">${esc(h)}</h4>` : ''}`
      + String(t).split('\n').filter(x => x.trim()).map(x => `<p class="mm-p">${mmRich(x)}</p>`).join('')).join('')
      : `<p class="mm-p mm-none-p">本文 なし</p>`)
    + `</article>`;
}
/** サブページの一覧(開閉)と追加。サブページは名前と補足を持つ */
function mmSubs(id, n) {
  const subs = n.subs || [];
  return `<section class="mm-subs">${sh('サブページ', {count: subs.length ? subs.length + '件' : '', act: {label: '足す', on: `mmSubAddSheet('${esc(id)}')`}})}`
    + (subs.length ? `<div class="p-list">${subs.map(([name, note, body], i) => {
      const key = id + ':' + i, open = !!mmSubOpen[key];
      /* 行の右の補足は常に出す。開いて出すのは本文だけ */
      return `<button class="p-row mm-sub" type="button" aria-expanded="${open}" onclick="mmSubToggle('${esc(key)}')">`
        + `<span class="p-row-body"><span class="p-row-t">${esc(name)}</span></span>`
        + (note ? `<span class="p-row-r num">${esc(note)}</span>` : '')
        + `<span class="p-chev${open ? ' is-open' : ''}">${icon('down')}</span></button>`
        + (open ? `<div class="mm-sub-x">${body ? mmRich(body) : '本文 なし'}</div>` : '');
    }).join('')}</div>` : '')
    + `</section>`;
}

/** メモの無い縁: その場で書き始める紙(保存で noteSave が作る。シートは開かない) */
function mmNoteStart(id) {
  return `<article class="mm-paper mm-doc is-bound mm-start">`
    + `<header class="mm-doc-h"><h3 class="mm-doc-t">${esc(E[id].n)}のメモ</h3></header>`
    + `<textarea id="mm-n-start" class="mm-ed-t" aria-label="${esc(E[id].n)}のメモの本文" placeholder="${esc(E[id].n)}について書く"></textarea>`
    + `<div class="mm-acts"><button class="p-btn p-btn-fill" type="button" onclick="mmNoteStartSave('${esc(id)}')">保存</button></div>`
    + `</article>`;
}
function mmNoteStartSave(id) {
  const body = String((document.getElementById('mm-n-start') || {}).value || '').trim();
  if (!body) return toast('本文を入れてください');
  const r = noteSave(id, [['', body]]);
  if (!r.ok) return toast(r.why);
  rerender();
  toast('メモを保存しました', {undo: () => undoRun(r.undo)});
}

/* ---------- 画面: すぐメモ(#/memo) ---------- */
route('memo', () => {
  const all = memosSorted().map(([k]) => k);
  const ids = all.filter(mmHit);
  const pinned = ids.filter(k => MEMOS[k].pin);
  const groups = [];
  ids.filter(k => !MEMOS[k].pin).forEach(k => {
    const d = MEMOS[k].at.slice(0, 10), g = groups[groups.length - 1];
    if (g && g.d === d) g.ids.push(k); else groups.push({d, ids: [k]});
  });
  const notes = notesSorted().map(([k]) => k).filter(k => E[k]).filter(k => !mmQ || mmNoteText(k).includes(mmQ));
  const grp = (title, n, html) => `<section class="mm-grp">${sh(title, {count: n})}<div class="mm-cards">${html}</div></section>`;
  const body = [
    pinned.length ? grp('ピン留め', pinned.length + '件', pinned.map(k => mmCard(k, {withDate: true})).join('')) : '',
    ...groups.map(g => grp(mmDayLabel(g.d), g.ids.length + '件', g.ids.map(k => mmCard(k)).join(''))),
    notes.length ? grp('エンティティのメモ', notes.length + '件', notes.map(mmNoteCard).join('')) : '',
  ].join('');
  const hasAny = all.length > 0 || notesSorted().some(([k]) => E[k]);
  return `<div class="mm">`
    + largeTitle('メモ', {actions: [{label: 'メモを探す', icon: 'search', on: 'mmSearchToggle()'}]})
    + (mmSearch ? `<div class="mm-search">${icon('search')}<input id="mm-q" class="p-input" type="search" placeholder="メモを探す" aria-label="メモを探す" value="${esc(mmQ)}" oninput="mmFilter(this.value)"></div>` : '')
    + mmComposer()
    + (hasAny
      ? `<div id="mm-list" class="mm-list">${body}<p class="mm-none" id="mm-none" ${body ? 'hidden' : ''}>該当 なし</p></div>`
      : empty({shape: 'memo', text: 'メモ なし', action: {label: 'メモを書く', on: "document.getElementById('mm-body')?.focus()"}}))
    + `</div>`;
}, {tab: 'memo'});

/* ---------- 画面: エンティティのメモの一覧(#/notes) ---------- */
route('notes', () => {
  const ids = notesSorted().map(([k]) => k).filter(k => E[k]);
  return `<div class="mm mm-notes">`
    + appbar('エンティティのメモ', {actions: [{icon: 'plus', label: 'エンティティのメモを書く', on: 'mmNewNote()'}]})
    + (ids.length
      ? `<div class="mm-cards mm-notes-l">${ids.map(mmNoteCard).join('')}</div>`
      : empty({shape: 'memo', text: 'メモ なし', action: {label: 'メモを書く', on: 'mmNewNote()'}}))
    + `</div>`;
}, {tab: 'memo'});

/* ---------- 画面: エンティティのメモ(#/n/{id}) ---------- */
route('n', id => {
  const e = E[id];
  if (!e) return `<div class="mm mm-n">${appbar('メモ')}${empty({shape: 'entity', text: '縁が見つかりません'})}</div>`;
  const n = NOTES[id];
  return `<div class="mm mm-n">`
    + appbar(e.n + 'のメモ', {actions: [{icon: 'edit', label: 'メモを編集', on: `mmNoteEdit('${esc(id)}')`}]})
    + `<div><a class="mm-owner" href="#/e/${esc(id)}"><span class="p-dot" aria-hidden="true"></span>${esc(e.n)}</a></div>`
    + (n ? mmNoteBody(id, n) : mmNoteStart(id))
    + (n ? mmSubs(id, n) : '')
    + `</div>`;
}, {});

/* ---------- 作成の登録(統合: 本体) ---------- */
addCreate({key: 'memo', label: 'メモ', icon: 'memo', primaryOn: ['memo'], run: () => { go('#/memo'); after(() => { const t = document.getElementById('mm-body'); if (t) t.focus() }) }});
