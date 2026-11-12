/* 個人記録 v6.2 — 雑記・未確定・履歴の読み書き(担当 A。仕様 INBOX.md §3.1)
   読むのは DB の行、書くのは全て commit を通す(ログは commit が書く)。接頭辞 jt。 */
'use strict';

/* ---------- 小道具 ---------- */
const _jtKind = raw => /ランチ|ディナー|朝ごはん|昼ごはん|夜ごはん|晩ごはん|朝食|昼食|夕食|夕飯|ご飯|ごはん|ラーメン|うどん|寿司|カレー|定食|焼肉|パスタ|ピザ|食べ|飲み会|カフェ|喫茶/.test(raw) ? 'meal' : /電車|バス|新幹線|タクシー|移動|出発|到着/.test(raw) ? 'move' : undefined;
const _jtStamp = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(s);
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

/* ---------- 読む(readText。区切り・項目・強い/弱い。決まりは INPUT2.md §1.2) ---------- */
const RD_TYPE = { '予定': 'plan', 'メモ': 'memo', '支払い': 'pay', '出来事': 'event', 'ToDo': 'task', 'todo': 'task', 'TODO': 'task' };
const RD_TYPE_LABEL = { plan: '予定', memo: 'メモ', pay: '支払い', event: '出来事', task: 'ToDo' };
const RD_TYPE_WHOLE = /^(予定|メモ|支払い|出来事|ToDo|todo|TODO)$/;
const RD_HASH_WHOLE = /^[#＃](\S+)$/;
/* 感想の文末(かった・だった・たい・楽しみ・疲れた・形容詞の言い切り) */
const RD_FEEL = /(かった|だった|たい|楽しみ|疲れた|(楽し|嬉し|うれし|悲し|さびし|寂し|美味し|おいし|眠|寒|暑|痛|怖|忙|面白|かわい)い|楽しい)$/;
const RD_DATE_AT = /^(今日|きょう|明後日|あさって|明日|あした|昨日|きのう|(来週)?[日月火水木金土]曜日?|\d{1,2}[\/月]\d{1,2}日?)/;
const RD_HM_AT = /^(\d{1,2})[:：](\d{2})(?:[-–〜~](\d{1,2})[:：](\d{2}))?/;
const RD_JI_AT = /^(\d{1,2})時(?:(半)|(\d{1,2})分)?(?!間)/;
const RD_AMT_AT = /^([¥￥]\s?\d[\d,]*|\d[\d,]*円)(だった|です|でした)?/;
const RD_NUM_WHOLE = /^\d[\d,]{2,}$/;
const RD_LEAD = /^(に|は|から|で|も|、)+/;
const RD_YEN = /[¥￥]\s?\d|\d[\d,]*円/;

/** 文の頭から1つの語を読む → {role, word, len, val, label, end?, dep?} か null。whole は区切りがまるごとの時だけ種類と数字を読む */
function _rdAt(s, whole) {
  let m, d;
  if (whole && RD_TYPE_WHOLE.test(s)) { const val = RD_TYPE[s]; return { role: 'type', word: s, len: s.length, val, label: RD_TYPE_LABEL[val] }; }
  if (whole && (m = s.match(RD_HASH_WHOLE))) return { role: 'type', word: s, len: s.length, val: 'task', label: 'ToDo', list: m[1] };
  if ((m = s.match(RD_DATE_AT)) && (d = parseDateWord(m[0]))) return { role: 'date', word: m[0], len: m[0].length, val: d.date, label: m[0] };
  if ((m = s.match(RD_HM_AT))) { const v = `${pad2(m[1])}:${m[2]}`; if (isHM(v)) return { role: 'time', word: m[0], len: m[0].length, val: v, end: m[3] ? `${pad2(m[3])}:${m[4]}` : null, label: v }; }
  if ((m = s.match(RD_JI_AT))) { const v = `${pad2(m[1])}:${m[2] ? '30' : pad2(m[3] || 0)}`; if (isHM(v)) return { role: 'time', word: m[0], len: m[0].length, val: v, end: null, label: v }; }
  if ((m = s.match(RD_AMT_AT))) { const n = +m[1].replace(/[¥￥\s,円]/g, ''); return { role: 'amt', word: m[1], len: m[1].length + (m[2] ? m[2].length : 0), val: n, label: yen(n) }; }
  /* 3桁以上の数字だけの区切りは金額。1900〜2100 の4桁は年に見えるので金額にしない */
  if (whole && RD_NUM_WHOLE.test(s) && !/^(19|20)\d{2}$/.test(s)) { const n = +s.replace(/,/g, ''); return { role: 'amt', word: s, len: s.length, val: n, label: yen(n) }; }
  return null;
}
/** 区切り1つを読む。項目は items へ、o.off の役は読まずに文字として残す(off へ)。返り値 = 題に残る文字 */
function _rdSeg(s, offSet, items, off) {
  let pos = 0, lead = '', last = null;
  for (;;) {
    const r = s.slice(pos); if (!r) break;
    const it = _rdAt(r, pos === 0); if (!it) break;
    pos += it.len;
    if (offSet.has(it.role)) { off.push({ role: it.role, word: r.slice(0, it.len), label: it.label }); lead += r.slice(0, it.len); last = null; continue; }
    items.push(it); last = it;
  }
  const tail = s.slice(pos);
  /* 日付・時刻の直後が「は」「も」「って」「の」なら文の一部(独立した項目でない → 弱い) */
  if (last && (last.role === 'date' || last.role === 'time') && /^(は|も|って|の)/.test(tail)) last.dep = true;
  /* 区切りの途中の語: 円・¥の金額は文中でも強い。日付・時刻は文の一部(弱い) */
  let mid = '';
  for (let i = 0; i < tail.length;) {
    /* 数字の途中(「25時」の「5時」)からは読まない */
    const it = i > 0 && /[\d:：]/.test(tail[i - 1]) ? null : _rdAt(tail.slice(i), false);
    if (!it) { mid += tail[i]; i++; continue; }
    if (offSet.has(it.role)) { off.push({ role: it.role, word: tail.slice(i, i + it.len), label: it.label }); mid += tail.slice(i, i + it.len); i += it.len; continue; }
    if (it.role === 'amt') { items.push(it); i += it.len; continue; }
    it.dep = true; items.push(it); mid += tail.slice(i, i + it.len); i += it.len;
  }
  return (lead + mid).replace(RD_LEAD, '').trim();
}
/** 種類の語と #リスト を除いた行ごとの文(memo の本文・弱い読みの題に使う) */
function _rdBody(raw) {
  return String(raw || '').split('\n')
    .map(l => l.split(/[\s　]+/).filter(p => p && !RD_TYPE_WHOLE.test(p) && !RD_HASH_WHOLE.test(p)).join(' '))
    .filter(Boolean).join('\n');
}
/** 文末が動詞の終止形(買う・行く・する 等) */
const _rdVerbEnd = t => t.length >= 2 && /[うくすつぬぶむぐる]$/.test(t);
/** 文に出てくるエンティティ(parseQuick と同じ当て方) */
const _rdEnts = raw => Object.keys(DB.ents || {}).filter(id => { const e = DB.ents[id], short = e.n.split(' ').pop(); return raw.includes(e.n) || (short.length > 1 && raw.includes(short)); });

/** 1つの文(空行で分けた1まとまり)を読む → {cand, conf:'strong'|'weak'|'none', marks, off, hint, hintDate}
    base = {date, time} 既定の日時(無ければ今)。o = {type?, off?:[役…]}。o.type は種類で読み替える(cand は必ず返り、conf は strong) */
function readText(text, base, o = {}) {
  const raw = String(text == null ? '' : text).replace(/\r\n?/g, '\n').trim();
  const bd = (base && base.date) || TODAY, bt = (base && base.time) || NOW;
  const offSet = new Set(o.off || []);
  const ents = _rdEnts(raw), pri = /[!！]/.test(raw);
  const segs = raw.replace(/[!！]/g, ' ').split(/[\s　、。]+/).filter(Boolean);
  const forced = o.type && o.type !== 'diary' ? o.type : null;
  /* 感想の文は最初に見分ける。種類の語・金額(円・¥)があれば日記にしない */
  const hasType = segs.some(s => RD_TYPE_WHOLE.test(s) || RD_HASH_WHOLE.test(s));
  const endText = raw.replace(/[\s。、！!？?…]+$/, '');
  if (o.type === 'diary' || (!forced && !hasType && !RD_YEN.test(raw) && RD_FEEL.test(endText))) {
    const dm = parseDateWord(raw);
    return { cand: null, conf: 'none', marks: [], off: dm ? [{ role: 'date', word: dm.word, label: dm.word }] : [], hint: 'diary', hintDate: dm && !offSet.has('date') ? dm.date : bd };
  }
  const items = [], off = [], pieces = [];
  for (const s of segs) { const r = _rdSeg(s, offSet, items, off); if (r) pieces.push(r); }
  const dateI = items.find(i => i.role === 'date'), timeI = items.find(i => i.role === 'time');
  const amtI = items.find(i => i.role === 'amt'), typeI = items.find(i => i.role === 'type');
  const weak = items.some(i => i.dep);
  const title = (weak ? _rdBody(raw).replace(/\s+/g, ' ') : pieces.join(' ')).replace(/[!！]/g, '').replace(/\s+/g, ' ').trim();
  const dA = dateI ? dateI.val : undefined;
  /* 種類: 指定 → 種類の語 → 金額(支払い) → #(ToDo) → 日時(過去=出来事 / 先=予定) */
  let type = forced || (typeI ? typeI.val : null);
  if (!type && amtI) type = 'pay';
  if (!type && timeI) {
    const eff = dA || bd, past = eff < TODAY || (eff === TODAY && timeI.val < NOW);
    type = past ? (/たい|思い出/.test(raw) ? null : 'event') : 'plan';
  } else if (!type && dateI) {
    if (dA > TODAY) type = _rdVerbEnd(title) ? 'task' : 'plan';
    else if (dA === TODAY) type = /(た|だった)$/.test(title) ? 'event' : 'task';
    else type = /たい|思い出/.test(raw) ? null : 'event';
  }
  /* 先の日付の支払いは今日にし、日付の言葉は off へ */
  const dropDate = type === 'pay' && dA > TODAY;
  if (dropDate) off.push({ role: 'date', word: dateI.word, label: dateI.label });
  const marks = (type === 'memo' ? [typeI].filter(Boolean) : items.filter(i => !(dropDate && i === dateI))).map(i => ({ role: i.role, word: i.word, label: i.label }));
  const none = { cand: null, conf: 'none', marks, off, hint: null, hintDate: null };
  if (!type) return none;
  const T = title || (forced ? raw : '');
  const body = type === 'memo' ? (_rdBody(raw) || (forced ? raw : '')) : '';
  if (!forced && (type === 'memo' ? !body : type === 'pay' ? !amtI && !T : !T)) return none;
  let cand;
  if (type === 'pay') {
    const pe = ents.find(id => DB.ents[id] && DB.ents[id].types && isUnder(DB.ents[id].types[0], 'place'));
    cand = { type: 'pay', amt: amtI ? amtI.val : 0, date: dA ? (dA > TODAY ? TODAY : dA) : bd, start: timeI ? timeI.val : bt, payee: pe ? { e: pe } : T ? { name: T } : null, text: T, ents };
  } else if (type === 'task') {
    const lst = typeI && typeI.list ? Object.keys(DB.lists).find(k => DB.lists[k].n === typeI.list) : undefined;
    cand = { type: 'task', t: T, due: dA, list: lst, pri: pri ? 1 : undefined, ents };
  } else if (type === 'plan') {
    cand = { type: 'plan', title: T, date: dA || bd, start: timeI ? timeI.val : undefined, ents };
    if (timeI && timeI.end) cand.end = timeI.end;
  } else if (type === 'event') {
    cand = { type: 'event', title: T, date: dA || bd, ents };
    if (timeI) cand.start = timeI.val;
    const kind = _jtKind(raw); if (kind) cand.kind = kind;
  } else cand = { type: 'memo', body, ents };
  /* 弱い読み = 文の一部の日付・時刻。種類を指定した時と種類の語がある時は確かな読み */
  const conf = forced || typeI ? 'strong' : weak ? 'weak' : 'strong';
  return { cand, conf, marks, off, hint: null, hintDate: null };
}
/** 空行(2つ以上の改行)で分けた文の配列。前後の空白を除き、空は捨てる。文の中の改行は項目の区切り */
function readBlocks(text) {
  return String(text == null ? '' : text).replace(/\r\n?/g, '\n').split(/\n[ \t　]*\n/).map(s => s.trim()).filter(Boolean);
}

/* ---------- 候補(LLM を使わない) ---------- */
/** 1行 → 候補 [cand](強い読みの時だけ1件。弱い・読めない時は []) */
function ruleCandidates(text, base) {
  const r = readText(text, base);
  return r.conf === 'strong' && r.cand ? [r.cand] : [];
}
/** 指定の種類で読み替えた候補(必ず1件)。pay で金額が無ければ amt:0 */
function candFromType(text, type, base) {
  const r = readText(text, base, { type });
  return r.cand || { type: 'memo', body: String(text == null ? '' : text).trim(), ents: [] };
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
/** 複数の文を1回の commit で置く(各文は jotAdd と同じ。1件でも失敗したら全部置かない)。取り消し1回で全部戻る → {ids} */
const jotAddMany = (texts, o = {}) => {
  const list = [].concat(texts == null ? [] : texts).map(t => String(t == null ? '' : t).trim()).filter(Boolean);
  if (!list.length) return { ok: false, why: '内容を入れてください' };
  return commit(`${list.length}件を置きました`, () => {
    const ids = [];
    for (const body of list) {
      const r = jotAdd(body, { src: o.src || 'text', convert: o.convert, at: o.at });
      if (!r.ok) fail(r.why);
      ids.push(r.id);
    }
    return { ids };
  });
};
/** 日記の末尾に改行して足す(既にある日は改行でつなぐ)。commit 1回 */
const diaryAppend = (d, text) => {
  const add = String(text == null ? '' : text).trim();
  if (!isDate(d)) return { ok: false, why: '日を選んでください' };
  if (!add) return { ok: false, why: '内容を入れてください' };
  return commit('日記に書きました', () => {
    const cur = String((DB.days[d] && DB.days[d].diary) || '').trim();
    DB.days[d] = { photos: [], ...DB.days[d], diary: cur ? `${cur}\n${add}` : add };
  });
};
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
Object.assign(window, { isTent, tentCount, jotsRaw, jotsSorted, inboxCount, jotUpdate, jotRemove, jotConvert, jotConvertMany, tentConfirm, tentConfirmAll, readText, readBlocks, ruleCandidates, candFromType, jotAddMany, diaryAppend });
