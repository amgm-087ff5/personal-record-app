/* 個人記録 v6.2 — 雑記・未確定・履歴の読み書き(担当 A。仕様 INBOX.md §3.1)
   読むのは DB の行、書くのは全て commit を通す(ログは commit が書く)。接頭辞 jt。 */
'use strict';

/* ---------- 小道具 ---------- */
const _jtKind = raw => /ランチ|ディナー|朝ごはん|昼ごはん|夜ごはん|晩ごはん|朝食|昼食|夕食|夕飯|ご飯|ごはん|ラーメン|うどん|寿司|カレー|定食|焼肉|パスタ|ピザ|食べ|飲み会|カフェ|喫茶/.test(raw) ? 'meal' : /電車|バス|新幹線|タクシー|移動|出発|到着/.test(raw) ? 'move' : undefined;
const _jtStamp = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(s);
/** 文から日付の言葉と時刻を外す → {date?, time?, title} (date/time は言葉があった時だけ) */
function _jtParts(raw) {
  let t = ' ' + raw + ' ', time;
  const d = parseDateWord(t);
  if (d) t = t.replace(d.word, ' ');
  t = t.replace(/(\d{1,2})[:：](\d{2})(?:\s*[-–〜~]\s*\d{1,2}[:：]\d{2})?/, (_, h, m) => { const v = `${pad2(h)}:${m}`; if (isHM(v)) time = v; return ' '; });
  if (!time) t = t.replace(/(\d{1,2})時(半|(\d{1,2})分)?(?!間)/, (_, h, half, mi) => { const v = `${pad2(h)}:${half === '半' ? '30' : pad2(mi || 0)}`; if (isHM(v)) time = v; return ' '; });
  return { date: d ? d.date : undefined, time, title: t.replace(/\s+/g, ' ').trim() };
}
/** 既定の日時(雑記を書いた時刻。無ければ今) */
const _jtBase = at => _jtStamp(at) ? { date: at.slice(0, 10), time: at.slice(11, 16) } : { date: TODAY, time: NOW };
function _jtWithAt(at, fn) { _logAt = at || null; try { return fn(); } finally { _logAt = null; } }
/** 行の場所 */
const _jtTbl = t => t === 'acts' ? DB.acts : t === 'tasks' ? DB.tasks : t === 'memos' ? DB.memos : null;

/* ---------- 読む ---------- */
const isTent = row => !!(row && row.tent);
/** 雑記から出た記録(jot を持つ行)の一覧 [{t, k, row}] */
function recsOfJot(jid) {
  const out = [];
  for (const t of ['acts', 'tasks', 'memos']) for (const [k, row] of Object.entries(DB[t])) if (row.jot === jid) out.push({ t, k, row });
  return out;
}
/** 雑記の状態: 記録が無い → raw / 未確定の記録がある → tent / 全て確定 → done */
function jotState(jid) {
  const rs = recsOfJot(jid);
  if (!rs.length) return 'raw';
  return rs.some(r => r.row.tent) ? 'tent' : 'done';
}
const jotOfRec = row => (row && row.jot) || null;
/** 未確定の記録 [{t, k, row, jot}](新しい順。日付・時刻、無ければ雑記の at) */
function tentAll() {
  const out = [];
  for (const t of ['acts', 'tasks', 'memos']) for (const [k, row] of Object.entries(DB[t])) {
    if (!row.tent) continue;
    const jot = row.jot || null, jat = jot && DB.jots[jot] ? DB.jots[jot].at : '';
    const key = t === 'acts' ? `${row.date} ${row.start || '00:00'}` : t === 'tasks' ? (row.due ? `${row.due} 00:00` : jat) : (row.at || jat);
    out.push({ t, k, row, jot, key });
  }
  return out.sort((x, y) => (y.key < x.key ? -1 : y.key > x.key ? 1 : 0)).map(({ key, ...r }) => r);
}
const tentCount = () => tentAll().length;
/** 変換先の無い雑記 [[id, jot]](新しい順) */
const jotsRaw = () => jotsSorted().filter(([id]) => jotState(id) === 'raw').reverse();
const inboxCount = () => tentCount() + jotsRaw().length;
/** 雑記 [[id, jot]](古い順) */
const jotsSorted = () => Object.entries(DB.jots).sort((x, y) => (x[1].at < y[1].at ? -1 : x[1].at > y[1].at ? 1 : 0));
/** 題 */
function recTitle(t, row) {
  if (!row) return '';
  if (t === 'acts') return row.pay ? (payeeOf(row) || '支払い') : (row.title || (KINDS[row.kind] || {}).n || '');
  if (t === 'tasks') return row.t || '';
  if (t === 'memos') return String(row.body || '').split('\n')[0].slice(0, 40);
  return '';
}
/** 見せる要約 {ty, title, facts:[…]}。k は記録の id(支出のカテゴリを出す時だけ使う。無くてもよい) */
function recSummary(t, row, k) {
  if (!row) return { ty: '', title: '', facts: [] };
  if (t === 'acts') {
    if (row.pay) {
      const facts = [yen(payTotal(row)), `${relDay(row.date)} ${row.start || ''}`.trim()];
      const x = k && DB.exps[k];
      if (x) facts.push(catName(x.cat));
      return { ty: '支払い', title: recTitle(t, row), facts };
    }
    const facts = [`${relDay(row.date)} ${timeText(row)}`];
    const place = placeOf(row); if (place) facts.push(place);
    return { ty: isPlan(row) ? '予定' : '出来事', title: recTitle(t, row), facts };
  }
  if (t === 'tasks') {
    const facts = [];
    if (row.due) facts.push(fmtMD(row.due));
    const l = DB.lists[row.list]; if (l) facts.push(l.n);
    return { ty: 'ToDo', title: recTitle(t, row), facts };
  }
  return { ty: 'メモ', title: recTitle(t, row), facts: [] };
}

/* ---------- 候補(LLM を使わない) ---------- */
/** 1行 → 候補 [cand](0件か1件)。base = {date, time} は既定の日時(雑記を書いた時刻)。無ければ今 */
function ruleCandidates(text, base) {
  const raw = String(text == null ? '' : text).trim();
  const p = raw ? parseQuick(raw) : null; if (!p) return [];
  const B = base || { date: TODAY, time: NOW };
  const parts = _jtParts(raw), ents = p.ents || [];
  if (p.type === 'pay') return [{ type: 'pay', amt: p.amt, date: parts.date ? p.date : B.date, start: parts.time ? p.start : B.time, payee: p.payee, text: p.text, ents }];
  if (p.type === 'task') return [{ type: 'task', t: p.t, due: p.due, list: p.list, pri: p.pri, ents }];
  /* 過去の日付・過ぎた時刻の文は出来事。ただし「〜したい」「思い出す」は出来事でなく思いつき(雑記のまま) */
  const eff = parts.date || B.date;
  const past = (parts.date && parts.date < TODAY) || (parts.time && (eff < TODAY || (eff === TODAY && parts.time < NOW)));
  if (past) {
    if (/たい|思い出/.test(raw)) return [];
    const c = { type: 'event', title: parts.title || raw, date: eff, ents };
    if (parts.time) c.start = parts.time;
    const kind = _jtKind(raw); if (kind) c.kind = kind;
    return [c];
  }
  if (p.type === 'plan') return [{ type: 'plan', title: p.title, date: p.date, start: p.start, end: p.end, ents }];
  return [];
}
/** 指定の種類で読み替えた候補(必ず1件)。pay で金額が無ければ amt:0 */
function candFromType(text, type, base) {
  const raw = String(text == null ? '' : text).trim();
  const B = base || { date: TODAY, time: NOW };
  const p = raw ? parseQuick(raw) : null, parts = _jtParts(raw), ents = (p && p.ents) || [];
  const date = parts.date || B.date;
  if (type === 'pay') {
    if (p && p.type === 'pay') return { ...p, date: parts.date ? p.date : B.date, start: parts.time ? p.start : B.time, ents };
    return { type: 'pay', amt: 0, date, start: parts.time || B.time, payee: parts.title ? { name: parts.title } : null, text: parts.title, ents };
  }
  if (type === 'task') { const q = parseTask(raw); return { type: 'task', t: q.t || raw, due: q.due, list: q.list, pri: q.pri, ents }; }
  if (type === 'plan') return { type: 'plan', title: parts.title || raw, date, start: parts.time, ents };
  if (type === 'event') { const c = { type: 'event', title: parts.title || raw, date, ents }; if (parts.time) c.start = parts.time; const kind = _jtKind(raw); if (kind) c.kind = kind; return c; }
  return { type: 'memo', body: raw, ents };
}

/* ---------- 記録にする(commit の中で呼ぶ内部関数) ---------- */
/** 候補 → 記録を1つ作り、{t, k} を返す。tent / via を行に付け、jot で雑記に結ぶ */
function candToRec(cand, jid, o = {}) {
  if (!cand) fail('変換できませんでした');
  const ents = (cand.ents || []).filter(e => E(e));
  const roles = ents.map(e => ({ r: isUnder(E(e).types[0], 'place') ? '場所' : isUnder(E(e).types[0], 'project') ? 'プロジェクト' : '同席', e }));
  let r, t;
  if (cand.type === 'pay') {
    r = paySave({ date: cand.date || TODAY, start: cand.start || NOW, payee: cand.payee || null, amt: +cand.amt || 0, exp: { basis: 'items', cat: cand.cat || catGuess(cand.payee, cand.text || '') } });
    t = 'acts';
  } else if (cand.type === 'event' || cand.type === 'plan') {
    const start = isHM(cand.start) ? cand.start : undefined, end = start && isHM(cand.end) ? cand.end : undefined;
    r = actAdd({ kind: KINDS[cand.kind] ? cand.kind : 'event', title: cand.title, date: cand.date || TODAY, start, end, allDay: start ? 0 : 1, state: cand.type === 'plan' ? 'plan' : 'done', roles });
    t = 'acts';
  } else if (cand.type === 'task') {
    r = taskAdd({ t: cand.t, due: cand.due, list: cand.list, pri: cand.pri, ents });
    t = 'tasks';
  } else if (cand.type === 'memo') {
    r = memoAdd(cand.body, { ents });
    t = 'memos';
  } else fail('種類を選んでください');
  if (!r.ok) fail(r.why);
  const row = _jtTbl(t)[r.id];
  row.jot = jid;
  if (o.tent) row.tent = 1;
  if (o.via) row.via = o.via;
  return { t, k: r.id };
}

/* ---------- 雑記を消す時に記録を消す(その雑記の未確定の行。確定した子は親を外して残す) ---------- */
function _jtDrop(t, k) {
  const row = DB[t] && DB[t][k]; if (!row) return;
  if (t === 'acts') {
    for (const [cid, c] of Object.entries(DB.acts)) if (c.parent === k) { if (c.tent) _jtDrop('acts', cid); else delete c.parent; }
    delete DB.exps[k];
  }
  delete DB[t][k];
  for (const x of Object.values(DB.tasks)) { if (x.act === k) delete x.act; if (x.payAct === k) delete x.payAct; }
  for (const m of Object.values(DB.memos)) if (m.act === k) delete m.act;
}

/* ---------- 書く ---------- */
/** 雑記を置く。o = {src, convert:true, at}。convert なら候補があれば同じ変更で未確定の記録も作る → {id, recs} */
function jotAdd(text, o = {}) {
  const body = String(text == null ? '' : text).trim().slice(0, 2000);
  const at = _jtStamp(o.at) ? o.at : nowStamp();
  const run = () => commit('雑記に置きました', () => {
    if (!body) fail('内容を入れてください');
    const jid = uid('j');
    DB.jots[jid] = { body, at, src: o.src || 'text' };
    const recs = [];
    if (o.convert !== false) { const cs = ruleCandidates(body, _jtBase(at)); if (cs.length) recs.push(candToRec(cs[0], jid, { tent: 1, via: 'rule' })); }
    return { id: jid, recs };
  });
  return o.at ? _jtWithAt(at, run) : run();
}
/** 雑記の本文を直す */
const jotUpdate = (jid, o = {}) => commit('雑記を直しました', () => {
  const j = DB.jots[jid]; if (!j) fail('雑記が見つかりません');
  j.body = _str(o.body, '内容を入れてください').slice(0, 2000);
});
/** 雑記を消す。recs:true なら未確定の記録も消す(確定したものは残して雑記との結び付きを外す)。recs:false なら残る記録は確定扱いにする */
const jotRemove = (jid, o = {}) => commit('雑記を消しました', () => {
  if (!DB.jots[jid]) fail('雑記が見つかりません');
  for (const { t, k, row } of recsOfJot(jid)) {
    if (o.recs && row.tent) { _jtDrop(t, k); continue; }
    const cur = DB[t][k]; delete cur.jot; delete cur.tent; delete cur.via;
  }
  delete DB.jots[jid];
});
/** 未確定の記録を全て消してから cands で作り直す(確定済みは触らない) */
function _jtConvert(jid, cands, o, label) {
  return commit(label, () => {
    if (!DB.jots[jid]) fail('雑記が見つかりません');
    const cs = [].concat(cands || []).filter(Boolean);
    if (!cs.length) fail('変換できませんでした');
    for (const { t, k, row } of recsOfJot(jid)) if (row.tent) _jtDrop(t, k);
    if (o.clearLlm) delete DB.jots[jid].llm;
    const recs = cs.map(c => candToRec(c, jid, { tent: o.confirm ? 0 : 1, via: o.via || 'rule' }));
    return { recs };
  });
}
/** 雑記を記録にする。cand は1件か配列。confirm なら確定で作る → {recs} */
const jotConvert = (jid, cand, o = {}) => _jtConvert(jid, cand, { confirm: o.confirm, via: o.via || 'rule' }, '雑記を記録にしました');
/** LLM の結果を入れる(jotConvert の配列版。LLM の印は消す。未確定でだけ入る) */
const jotConvertMany = (jid, cands, o = {}) => _jtConvert(jid, cands, { confirm: false, via: o.via || 'llm', clearLlm: true }, '雑記を記録にしました');
/** 未確定を確定にする。refs = {t,k} か配列 */
const tentConfirm = refs => commit('確定しました', () => {
  let n = 0;
  for (const r of [].concat(refs || [])) {
    if (!_jtTbl(r.t)) continue;
    const row = _jtTbl(r.t)[r.k];
    if (row && row.tent) { delete row.tent; n++; }
  }
  if (!n) fail('未確定のものがありません');
});
const tentConfirmAll = () => tentConfirm(tentAll().map(({ t, k }) => ({ t, k })));
/** LLM の印を付ける・消す(取り消しの対象にしない。_snap の外で書いて保存する) */
function jotLlmMark(jid, st, why) {
  const j = DB.jots[jid]; if (!j) return { ok: false, why: '雑記が見つかりません' };
  if (st == null) delete j.llm; else j.llm = { st, at: nowStamp(), ...(why ? { why: String(why).slice(0, 120) } : {}) };
  save();
  return { ok: true };
}

/* ---------- 履歴 ---------- */
/** 行の状態 {alive, tent, jot} */
function _jtStatus(t, k) {
  const tb = DB[t], row = tb && tb[k], alive = !!row;
  if (t === 'jots') return { alive, tent: alive && jotState(k) === 'tent', jot: k };
  return { alive, tent: alive && !!row.tent, jot: (row && row.jot) || null };
}
/** 履歴の行 [{i, at, op, t, k, ty, s, alive, tent, jot?}](古い順)。o = {filter, q} */
function logView(o = {}) {
  const filter = o.filter || 'all';
  const words = String(o.q || '').toLowerCase().split(/\s+/).filter(Boolean);
  const log = DB.log || [];
  /* 編集は同じ t:k の最新の1行だけ残す(作成・削除は全部) */
  const seen = new Set(), keep = log.map(() => true);
  for (let i = log.length - 1; i >= 0; i--) {
    const e = log[i]; if (e.op !== 'edit') continue;
    const key = e.t + ':' + e.k;
    if (seen.has(key)) keep[i] = false; else seen.add(key);
  }
  const rows = [];
  log.forEach((e, i) => {
    if (!keep[i]) return;
    const st = _jtStatus(e.t, e.k);
    if (e.op === 'add' && e.t !== 'jots' && st.tent) return;        /* 雑記から作られた未確定の作成は、雑記の吹き出しの中に出す */
    const row = { i: e.i, at: e.at, op: e.op, t: e.t, k: e.k, ty: e.ty, s: e.s, alive: st.alive, tent: st.tent };
    if (e.t === 'jots') row.jot = e.k; else if (st.jot) row.jot = st.jot;
    rows.push(row);
  });
  rows.sort((x, y) => (x.at < y.at ? -1 : x.at > y.at ? 1 : 0));
  return rows.filter(r => {
    if (filter === 'jot' && r.t !== 'jots') return false;
    if (filter === 'tent' && !(r.alive && r.tent)) return false;
    if ((filter === 'add' || filter === 'edit' || filter === 'del') && r.op !== filter) return false;
    if (words.length) { const hay = `${r.s} ${r.ty}`.toLowerCase(); if (!words.every(w => hay.includes(w))) return false; }
    return true;
  });
}
/** 日ごとのまとまり [{d, items}](list は logView の結果) */
function logDays(list) {
  const m = new Map();
  for (const r of list || []) { const d = String(r.at || '').slice(0, 10); if (!m.has(d)) m.set(d, { d, items: [] }); m.get(d).items.push(r); }
  return [...m.values()];
}
/** 履歴を消す(取り消せない。確認は呼ぶ側) */
function jtLogClear() { DB.log = []; save(); return { ok: true, label: '履歴を消しました' }; }

/* ---------- 見本(初めて開いた時だけ。何度呼ばれても重複させない) ---------- */
/** 過去の記録から履歴の下地を作り、見本の雑記を4件置く。DB は見本データ(または既存の保存データ) */
function seedInbox() {
  if (DB.ibx || Object.keys(DB.jots || {}).length) return false;
  /* ① 履歴の下地: 日付が今日以前の記録・未完了の ToDo・メモから新しい順に最大14件 */
  const cands = [];
  for (const [k, a] of Object.entries(DB.acts)) {
    if (isPlan(a) || a.rep || a.hol || a.date > TODAY) continue;
    cands.push({ t: 'acts', k, row: a, at: `${a.date} ${a.start || '09:00'}`, lb: '記録を追加' });
  }
  for (const [k, t] of Object.entries(DB.tasks)) if (!t.done && t.due && t.due <= TODAY) cands.push({ t: 'tasks', k, row: t, at: `${t.due} 09:00`, lb: 'ToDoを追加' });
  for (const [k, m] of Object.entries(DB.memos)) if (m.at && m.at.slice(0, 10) <= TODAY) cands.push({ t: 'memos', k, row: m, at: m.at, lb: 'メモを保存しました' });
  cands.sort((x, y) => (y.at < x.at ? -1 : y.at > x.at ? 1 : 0));
  const pick = cands.slice(0, 14);
  const add = (c, op, at, lb) => { const w = _logWhat(c.t, c.k, c.row); DB.log.push({ i: uid('l'), at, op, t: c.t, k: c.k, ty: w.ty, s: w.s, lb }); };
  pick.forEach(c => add(c, 'add', c.at, c.lb));
  const nowAt = nowStamp();
  [2, 6, 10].forEach((ix, n) => {
    const c = pick[ix]; if (!c) return;
    const d = new Date(c.at.replace(' ', 'T') + ':00'); d.setHours(d.getHours() + n + 1);
    let at = `${dISO(d)} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
    if (at > nowAt) at = nowAt;
    add(c, 'edit', at, '直しました');
  });
  /* ② 見本の雑記(昨日の食事は確定まで) */
  const yd = dAdd(TODAY, -1);
  const r1 = jotAdd('夕食 ラーメン 980', { at: `${yd} 20:40` });
  jotAdd('なんかあの映画のタイトル思い出したい', { at: `${yd} 22:10` });
  jotAdd('明日 10時 歯医者', { at: `${TODAY} 08:05` });
  jotAdd('牛乳 #買い物', { at: `${TODAY} 12:30` });
  if (r1.ok && r1.recs && r1.recs[0]) _jtWithAt(`${yd} 20:41`, () => tentConfirm({ t: r1.recs[0].t, k: r1.recs[0].k }));
  /* ③ 時間順に並べる(安定ソート) */
  DB.log.sort((x, y) => (x.at < y.at ? -1 : x.at > y.at ? 1 : 0));
  return true;
}

/* const で定義した公開関数も window に載せる(他の画面の window.xxx && xxx() の判定が偽にならないように) */
Object.assign(window, { isTent, tentCount, jotsRaw, jotsSorted, inboxCount, jotUpdate, jotRemove, jotConvert, jotConvertMany, tentConfirm, tentConfirmAll });
