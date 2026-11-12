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

/* ---------- 読む(readText。行ごとのプロパティ。決まりは INPUT3.md §5.1) ---------- */
const RD_TYPE = { '予定': 'plan', 'メモ': 'memo', '支払い': 'pay', '出来事': 'event', 'ToDo': 'task', 'todo': 'task', 'TODO': 'task', '日記': 'diary' };
const RD_TYPE_LABEL = { plan: '予定', memo: 'メモ', pay: '支払い', event: '出来事', task: 'ToDo', diary: '日記' };
const RD_TYPE_WHOLE = /^(予定|メモ|支払い|出来事|ToDo|todo|TODO|日記)$/;
const RD_TYPE_LINE = /^種類[:：](予定|メモ|支払い|出来事|ToDo|todo|TODO|日記)$/;
const RD_DAY = { '今日': 0, 'きょう': 0, '明日': 1, 'あした': 1, '明後日': 2, 'あさって': 2, '昨日': -1, 'きのう': -1 };
/* 感想の文末(かった・だった・たい・楽しみ・疲れた・形容詞の言い切り) */
const RD_FEEL = /(かった|だった|たい|楽しみ|疲れた|(楽し|嬉し|うれし|悲し|さびし|寂し|美味し|おいし|眠|寒|暑|痛|怖|忙|面白|かわい)い|楽しい)$/;
/** 行が種類の行か(まるごと「予定」、または「種類:メモ」)→ 種類の語 か null */
const _rdTyOf = s => { const m = s.match(RD_TYPE_LINE); return m ? m[1] : RD_TYPE_WHOLE.test(s) ? s : null; };
/** 時刻の1つ: H:MM / H時 / H時半 / H時MM分 → 'HH:MM'(範囲外は null) */
function _rdHM(s) {
  let m = s.match(/^(\d{1,2})[:：](\d{2})$/), h, mi;
  if (m) { h = +m[1]; mi = +m[2]; }
  else if ((m = s.match(/^(\d{1,2})時(?:(半)|(\d{1,2})分)?$/))) { h = +m[1]; mi = m[2] ? 30 : m[3] ? +m[3] : 0; }
  else return null;
  return h <= 23 && mi <= 59 ? `${pad2(h)}:${pad2(mi)}` : null;
}
/** 時刻の語: 1つ、または 始まり-終わり(終わりは始まりより後)→ {v, end} か null */
function _rdTime(t) {
  const ps = t.split(/[-–〜~]/);
  if (ps.length === 1) { const v = _rdHM(t); return v ? { v, end: null } : null; }
  if (ps.length !== 2) return null;
  const a = _rdHM(ps[0]), b = _rdHM(ps[1]);
  return a && b && b > a ? { v: a, end: b } : null;
}
/** 月日 → {abs} か null(その月日が存在しない時は null)。mo が null なら基準の月 */
function _rdMd(mo, d, bd) {
  const mm = mo == null ? +bd.slice(5, 7) : mo;
  const dt = new Date(+bd.slice(0, 4), mm - 1, d);
  return mm >= 1 && mm <= 12 && dt.getMonth() === mm - 1 && dt.getDate() === d ? { abs: dISO(dt) } : null;
}
/** 日付の語 → {off}(今日・明日…)/ {rel}(+N -N。前の日付から)/ {abs}(M/D・/D・D日)か null */
function _rdDate(t, bd) {
  if (Object.prototype.hasOwnProperty.call(RD_DAY, t)) return { off: RD_DAY[t] };
  let m = t.match(/^([+-])(\d{1,3})$/);
  if (m) return { rel: (m[1] === '-' ? -1 : 1) * +m[2] };
  if ((m = t.match(/^(\d{1,2})\/(\d{1,2})$/))) return _rdMd(+m[1], +m[2], bd);
  if ((m = t.match(/^\/(\d{1,2})$/)) || (m = t.match(/^(\d{1,2})日$/))) return _rdMd(null, +m[1], bd);
  return null;
}
/** 金額の語: ¥850 / 850円 / 850(3桁以上。1900〜2100 の4桁は年なので金額でない) → {v} か null */
function _rdAmt(t) {
  let m = t.match(/^[¥￥](\d[\d,]*)$/) || t.match(/^(\d[\d,]*)円$/);
  if (m) return { v: +m[1].replace(/,/g, '') };
  m = t.match(/^(\d[\d,]*)$/);
  if (!m) return null;
  const n = m[1].replace(/,/g, '');
  return n.length >= 3 && !/^(19|20)\d{2}$/.test(n) ? { v: +n } : null;
}
/** 1行を読む。語が全て 日付・時刻(範囲)・金額・#タグ のどれかで、日付・時刻・金額は各1つまで → {date, time, amt, tags} か null(備考) */
function _rdLine(s, bd) {
  const p = { date: null, time: null, amt: null, tags: [] };
  for (const t of s.split(' ').filter(Boolean)) {
    let m = t.match(/^[#＃](\S+)$/), d, tm, a;
    if (m) p.tags.push(m[1]);
    else if ((d = _rdDate(t, bd))) { if (p.date) return null; p.date = d; }
    else if ((tm = _rdTime(t))) { if (p.time) return null; p.time = tm; }
    else if ((a = _rdAmt(t))) { if (p.amt) return null; p.amt = a; }
    else return null;
  }
  return p;
}
/** 雑記の語から登録済みのエンティティを拾う(parseQuick と同じ当て方) */
const _rdEnts = raw => Object.keys(DB.ents || {}).filter(id => { const e = DB.ents[id], short = e.n.split(' ').pop(); return raw.includes(e.n) || (short.length > 1 && raw.includes(short)); });

/** 1つの文(空行で分けた1まとまり)を行ごとに読む → {cand, conf, marks, off, notes, hint, hintDate}
    行: 1行目(種類の行とタグの行を除く)が題。2行目以降はプロパティの行(日付・時刻・金額・タグ)。形が合わない行は備考(notes)
    base = {date, time} 既定の日時(無ければ今)。o = {type?, off?: 行番号の配列(0始まり。そのブロックの中の行)}
    o.type を渡すと、その種類で読む(cand は必ず返り、conf は strong。memo は行を読まず備考にする) */
function readText(text, base, o = {}) {
  const bd = (base && base.date) || TODAY, bt = (base && base.time) || NOW;
  const raw = String(text == null ? '' : text).replace(/\r\n?/g, '\n');
  const cancel = new Set((o.off || []).map(Number));
  const lines = raw.split('\n').map(s => s.replace(/[!！]/g, '').replace(/[ \t　]+/g, ' ').trim());
  const pri = /[!！]/.test(raw), ents = _rdEnts(raw);
  /* 1. 行ごとの種類: 空 / 種類の行 / 日付・時刻・金額の行 / タグだけの行 / 文字 */
  let tyW = null, tyI = -1;
  const K = lines.map((s, i) => {
    if (!s) return { k: 'blank' };
    const ty = _rdTyOf(s);
    if (ty && tyI < 0 && !cancel.has(i)) { tyI = i; tyW = ty; return { k: 'type' }; }
    const p = _rdLine(s, bd);
    if (p) return p.date || p.time || p.amt ? { k: 'prop', p } : { k: 'tag', p };
    return { k: 'text' };
  });
  /* 2. 題 = 種類の行でも #の行でもない最初の行 */
  const ti = K.findIndex((x, i) => (x.k === 'text' || x.k === 'prop') && !/^[#＃]/.test(lines[i]));
  if (ti >= 0) K[ti] = { k: 'title' };
  const T = ti >= 0 ? lines[ti] : '';
  /* 3. 種類(o.type が優先。次に種類の行) */
  const tyKey = o.type || (tyW ? RD_TYPE[tyW] : null) || null;
  const marks = [], off = [], notes = [], body = [], extra = [];
  const drop = (i, role, label) => { off.push({ role, word: lines[i], label, line: i }); notes.push({ line: i, text: lines[i] }); };
  if (tyKey === 'diary') {
    if (tyI >= 0) marks.push({ role: 'type', word: lines[tyI], label: RD_TYPE_LABEL.diary, line: tyI });
    return { cand: null, conf: 'none', marks, off, notes, hint: 'diary', hintDate: bd };
  }
  const forced = tyKey || null, isMemo = forced === 'memo';
  /* 4. プロパティの行を順に読む。日付の語は前の日付の行から(+N/-N)、時刻だけの行は直前の日付と同じ日 */
  const propIdx = [];
  K.forEach((x, i) => { if (x.k === 'prop' && !isMemo && !cancel.has(i)) propIdx.push(i); });
  const groups = [], dtAt = {}, usedDt = new Set();
  let last = bd, pend = null, prevI = -2, amtI = -1, amtV = null;
  for (const i of propIdx) {
    const p = K[i].p;
    if (p.amt && amtI < 0) { amtI = i; amtV = p.amt.v; }
    if (p.date) { const d = p.date; last = d.abs !== undefined ? d.abs : d.off !== undefined ? dAdd(bd, d.off) : dAdd(last, d.rel); }
    if (p.date || p.time) {
      const start = p.time ? p.time.v : null, end = p.time ? p.time.end : null;
      if (!p.date && start && pend && pend.i === prevI) {
        /* 日付だけの行の直後の時刻だけの行 = その日の始まりの時刻 */
        pend.start = start; pend.end = end; pend.dateOnly = false;
        dtAt[pend.i] = { date: pend.date, start, end }; dtAt[i] = dtAt[pend.i]; usedDt.add(i); pend = null;
      } else if (groups.length < 2) {
        const g = { i, date: last, start, end, dateOnly: !start };
        groups.push(g); pend = g.dateOnly ? g : null;
        dtAt[i] = { date: last, start, end }; usedDt.add(i);
      }
    }
    prevI = i;
  }
  const g1 = groups[0] || null, g2 = groups[1] || null;
  const tags = [];
  K.forEach((x, i) => { if ((x.k === 'tag' || x.k === 'prop') && !cancel.has(i)) tags.push(...x.p.tags); });
  const tagList = [...new Set(tags)];
  /* 5. 種類: 金額 → 支払い。日時 → 過去は出来事・今以後は予定(日付だけは今日以降が ToDo、過去は出来事)。タグだけ → ToDo */
  let type = forced;
  if (!type && amtI >= 0) type = 'pay';
  if (!type && g1) {
    if (g1.start) type = g1.date < bd || (g1.date === bd && g1.start < bt) ? 'event' : 'plan';
    else if (g2) type = g1.date < bd ? 'event' : 'plan';
    else type = g1.date >= bd ? 'task' : 'event';
  }
  if (!type && tagList.length) type = 'task';
  const amtUse = type === 'pay' && amtI >= 0;
  /* 6. 印(読んだ行)と備考(読まなかった行・取り消した行) */
  if (tyI >= 0) marks.push({ role: 'type', word: lines[tyI], label: RD_TYPE_LABEL[RD_TYPE[tyW]] || tyW, line: tyI });
  K.forEach((x, i) => {
    const s = lines[i];
    if (x.k === 'blank' || x.k === 'title' || x.k === 'type') return;
    if (x.k === 'tag') {
      const lb = x.p.tags.map(t => '#' + t).join(' ');
      if (cancel.has(i)) drop(i, 'tag', lb); else marks.push({ role: 'tag', word: s, label: lb, line: i });
      return;
    }
    if (x.k === 'text') { if (isMemo && !cancel.has(i)) body.push(s); else drop(i, 'note', '備考'); return; }
    const p = x.p;
    if (usedDt.has(i) || (amtUse && i === amtI)) {
      const d = dtAt[i], parts = [];
      if (d) parts.push(`${fmtMD(d.date)}${d.start ? ' ' + d.start + (d.end ? '–' + d.end : '') : ' 終日'}`);
      if (amtUse && i === amtI) parts.push(yen(amtV));
      if (p.tags.length) parts.push(p.tags.map(t => '#' + t).join(' '));
      marks.push({ role: usedDt.has(i) ? 'dt' : 'amt', word: s, label: parts.join(' '), line: i });
      return;
    }
    drop(i, p.date || p.time ? 'dt' : 'amt', p.date || p.time ? '日時' : '金額');
  });
  const none = () => ({ cand: null, conf: 'none', marks, off, notes, hint: null, hintDate: null });
  if (!type) {
    /* 題だけで、文末が感想の言葉 = 日記のヒント(予定にも ToDo にもしない) */
    const hasLine = K.some(x => x.k === 'prop' || x.k === 'tag' || x.k === 'type');
    const hint = !hasLine && !!T && RD_FEEL.test(T.replace(/[\s。、！!？?…]+$/, ''));
    return { cand: null, conf: 'none', marks, off, notes, hint: hint ? 'diary' : null, hintDate: hint ? bd : null };
  }
  /* 7. 候補 */
  let cand;
  if (type === 'pay') {
    if (!T && amtV == null) return none();
    const pe = ents.find(id => DB.ents[id] && DB.ents[id].types && isUnder(DB.ents[id].types[0], 'place'));
    cand = { type: 'pay', amt: amtUse ? amtV : 0, date: g1 && g1.date <= bd ? g1.date : bd, start: g1 && g1.start ? g1.start : bt, payee: pe ? { e: pe } : T ? { name: T } : null, text: T, ents };
  } else if (type === 'plan' || type === 'event') {
    if (!T) return none();
    cand = { type, title: T, date: g1 ? g1.date : bd, ents };
    if (g1 && g1.start) cand.start = g1.start;
    const end = (g1 && g1.end) || (g2 && g2.start) || null;
    if (end) { if (!cand.start || end > cand.start) cand.end = end; else extra.push(`終わり ${end}`); }
    if (g2 && g2.date > cand.date) cand.date2 = g2.date;
    if (type === 'event') { const kind = _jtKind(raw); if (kind) cand.kind = kind; }
  } else if (type === 'task') {
    if (!T) return none();
    cand = { type: 'task', t: T, ents };
    if (g1) cand.due = g1.date;
    if (g1 && g1.start) extra.push(`時刻 ${g1.start}`);
    if (pri) cand.pri = 1;
  } else {
    /* memo: 題と、題の後ろの文字の行が本文。形が合う行(日時など)は備考 */
    const b = [T, ...body].filter(Boolean).join('\n');
    if (!b) return none();
    cand = { type: 'memo', body: b, ents };
  }
  const note = [...notes.map(n => n.text), ...extra].join('\n');
  if (note) cand.note = note;
  if (tagList.length) cand.tags = tagList;
  return { cand, conf: 'strong', marks, off, notes, hint: null, hintDate: null };
}
/** 空行(2つ以上の改行)で分けた文の配列。前後の空白を除き、空は捨てる。文の中の改行は行の区切り */
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
  /* 欄の割り当て: 支払い・予定・出来事は備考(note)に #タグ を先頭の行で足す(Act にタグの欄は無い)。ToDo・メモは tags の欄を持つ(行に直に入れる) */
  const tags = (cand.tags || []).filter(Boolean);
  const tagLine = tags.map(x => '#' + x).join(' ');
  const join = (...xs) => xs.filter(Boolean).join('\n') || undefined;
  let r, t;
  if (cand.type === 'pay') {
    r = paySave({ date: cand.date || TODAY, start: cand.start || NOW, payee: cand.payee || null, amt: +cand.amt || 0, note: join(tagLine, cand.note), exp: { basis: 'items', cat: cand.cat || catGuess(cand.payee, cand.text || '') } });
    t = 'acts';
  } else if (cand.type === 'event' || cand.type === 'plan') {
    const start = isHM(cand.start) ? cand.start : undefined, end = start && isHM(cand.end) ? cand.end : undefined;
    const endNote = !start && isHM(cand.end) ? `終わり ${cand.end}` : null;   /* 終日は終わりの時刻を持てないので備考へ */
    r = actAdd({ kind: KINDS[cand.kind] ? cand.kind : 'event', title: cand.title, date: cand.date || TODAY, ...(cand.date2 ? { date2: cand.date2 } : {}), start, end, allDay: start ? 0 : 1, state: cand.type === 'plan' ? 'plan' : 'done', roles, note: join(tagLine, cand.note, endNote) });
    t = 'acts';
  } else if (cand.type === 'task') {
    r = taskAdd({ t: cand.t, due: cand.due, list: cand.list, pri: cand.pri, ents, memo: join(cand.note) });
    t = 'tasks';
  } else if (cand.type === 'memo') {
    r = memoAdd(join(cand.body, cand.note), { ents });
    t = 'memos';
  } else fail('種類を選んでください');
  if (!r.ok) fail(r.why);
  const row = _jtTbl(t)[r.id];
  row.jot = jid;
  if (tags.length && (t === 'tasks' || t === 'memos')) row.tags = tags;
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
    /* 金額0の支払い(¥0)は記録にできないので、雑記だけ置く(失敗させない) */
    if (o.convert !== false) { const cs = ruleCandidates(body, _jtBase(at)).filter(c => c.type !== 'pay' || c.amt > 0); if (cs.length) recs.push(candToRec(cs[0], jid, { tent: 1, via: 'rule' })); }
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
  const r1 = jotAdd('夕食 ラーメン\n980', { at: `${yd} 20:40` });
  jotAdd('なんかあの映画のタイトル思い出したい', { at: `${yd} 22:10` });
  jotAdd('歯医者\n明日 10時', { at: `${TODAY} 08:05` });
  jotAdd('牛乳\n#買い物', { at: `${TODAY} 12:30` });
  if (r1.ok && r1.recs && r1.recs[0]) _jtWithAt(`${yd} 20:41`, () => tentConfirm({ t: r1.recs[0].t, k: r1.recs[0].k }));
  /* ③ 時間順に並べる(安定ソート) */
  DB.log.sort((x, y) => (x.at < y.at ? -1 : x.at > y.at ? 1 : 0));
  return true;
}

/* const で定義した公開関数も window に載せる(他の画面の window.xxx && xxx() の判定が偽にならないように) */
Object.assign(window, { isTent, tentCount, jotsRaw, jotsSorted, inboxCount, jotUpdate, jotRemove, jotConvert, jotConvertMany, tentConfirm, tentConfirmAll, readText, readBlocks, ruleCandidates, candFromType, jotAddMany, diaryAppend });
