/* 個人記録 v6.3 — 音声入力(担当 C。接頭辞 vc。仕様 INPUT2.md §3) */
'use strict';
/* 長押し(450ms・10px 以内)で録音開始 → 離しても続く → 録音中の ＋ のタップで完了 → 雑記に置く。
   ＋(.nav-plus / .rail-plus)は renderNav で描き直されるので、要素には直接付けず document に委譲する。
   区切り: 確定の1件どうしは改行(話の間)、「改行」は区切りの改行の代わり、「改行」を2回続けると空行(次の記録)。
   認識の再開の境目も間と同じ1つの改行。composer は確定の1件ごとに onText へ渡す。 */

const VC_SEL = '.nav-plus,.rail-plus';
const VC_HOLD = 450, VC_SLOP = 10, VC_MAX_FAILS = 3;
const VC_NL = /^(改行|かいぎょう)[\s。、．.!！?？]*$/;   /* 改行だけの確定 */
const VC_NUMRUN = /[0-9〇一二三四五六七八九十百千万][0-9〇一二三四五六七八九十百千万 ]*(?=[円時分])/g;   /* 円・時・分の直前の数字の並び */
const VC_DIG = { 〇: '0', 一: '1', 二: '2', 三: '3', 四: '4', 五: '5', 六: '6', 七: '7', 八: '8', 九: '9' };
const VC_KV = { 〇: 0, 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
let vcS = { active: false, from: 'nav', onText: null, rec: null, base: '', sess: '', pend: '', cut: false, gotResult: false, fails: 0, rt: 0 };
let vcPress = null;      /* 押している最中(長押しの判定) */
let vcEat = false;       /* 長押しの後の離す操作の click を1回だけ抑える */
let vcPill = null, vcTxt = null, vcMO = null;

function vcCls() { return window.__speechMock || window.webkitSpeechRecognition || window.SpeechRecognition || null; }
function voiceSupported() { return !!vcCls(); }
function voiceActive() { return !!vcS.active; }
function vcOnPlus(t) { return t && t.closest ? t.closest(VC_SEL) : null; }

/* 漢数字の並び(空白を除いた形)を算用数字にする。単位(十百千万)が無ければ1文字ずつ(二〇〇→200)。算用数字と混ざる時は直さない */
function vcNum(run) {
  const r = run.replace(/ /g, '');
  if (!/[十百千万]/.test(r)) return r.replace(/[〇一二三四五六七八九]/g, c => VC_DIG[c]);
  if (/[0-9]/.test(r)) return r;
  let total = 0, sec = 0, n = 0;
  for (const c of r) {
    if (c in VC_KV) n = VC_KV[c];
    else if (c === '十' || c === '百' || c === '千') { sec += (n || 1) * { 十: 10, 百: 100, 千: 1000 }[c]; n = 0; }
    else if (c === '万') { total += ((sec + n) || 1) * 10000; sec = 0; n = 0; }
  }
  return String(total + sec + n);
}
/* 確定・途中の1件を断片にする: 改行の言葉だけなら '\n'。それ以外は空白をまとめ、円・時・分の前の数字を算用数字にし、数字の間の空白を詰める */
function vcPiece(t) {
  const s = String(t == null ? '' : t).trim();
  if (!s) return '';
  if (VC_NL.test(s)) return '\n';
  return s.replace(/\s+/g, ' ').replace(VC_NUMRUN, m => vcNum(m)).replace(/(\d) +(?=\d)/g, '$1');
}
/* 断片をつなぐ。sep は断片どうしの区切り(確定の1件どうし・認識の再開の境目は '\n')。改行の後と、改行で始まる断片の前には改行を足さない */
function vcPut(text, piece, sep) {
  if (!piece) return text;
  if (!text) return piece;
  if (piece[0] === '\n') return text.replace(/ +$/, '') + piece;
  if (text[text.length - 1] === '\n') return text + piece;
  return text + sep + piece;
}
/* 今までの全文 = 前の認識の分 + 今の認識の確定 + 途中 */
function vcAll() { const s = vcS; return vcPut(vcPut(s.base, s.sess, '\n'), s.pend, '\n'); }
/* 空白の連続だけまとめる(改行は残す)。改行の前後の空白は外す */
function vcText() { return vcAll().replace(/[^\S\n]+/g, ' ').replace(/ *\n */g, '\n').trim(); }

function vcPaint() {
  if (!vcTxt) return;
  const t = vcText();
  const show = t ? (t.length > 60 ? '…' + t.slice(-60) : t) : '聞いています…';
  vcTxt.innerHTML = esc(show.replace(/\n/g, ' ↵ '));
}
/* ＋ に録音中の印を付ける(描き直されても付け直す) */
function vcMark() { document.querySelectorAll(VC_SEL).forEach(el => el.classList.toggle('is-rec', vcS.active)); }
function vcShowPill(on) { if (!vcPill) return; vcPill.classList.toggle('is-on', !!on); if (on) vcPaint(); }

/* composer へ1断片を渡す。認識の再開の後の最初の断片の前に改行を1つ入れる(改行の言葉そのものが来た時は入れない) */
function vcOut(s, piece) {
  if (!piece || !s.onText) return;
  if (s.cut) {
    s.cut = false;
    if (piece !== '\n' && s.base[s.base.length - 1] !== '\n') s.onText('\n', { live: 1 });
  }
  s.onText(piece, { live: 1 });
}

/* nav の終わり: 全文を置く。空行があり、空行で分ける関数があれば、空行で分けた文を一度に置く */
function vcSave(text) {
  const many = /\n\s*\n/.test(text) && typeof jotAddMany === 'function' && typeof readBlocks === 'function';
  let r = null;
  if (many) r = jotAddMany(readBlocks(text), { src: 'voice' });
  else if (typeof jotAdd === 'function') r = jotAdd(text, { src: 'voice' });
  else { toast('雑記に置けませんでした', { bad: 1 }); return; }
  if (r && r.ok) toast(r.label || '雑記に置きました', { undo: r.undo, act: { label: '開く', on: () => window.openInbox && openInbox() } });
  else toast((r && r.why) || '置けませんでした', { bad: 1 });
  window.inRefresh && inRefresh();
}

/* 終わり(完了・やめる・止まった): nav は全文を置く。composer は確定の1件ずつ渡し済みなので、まだ確定していない文だけ渡す */
function vcEnd(s, msg) {
  const text = vcText();
  if (!text) { toast(msg || '聞き取れませんでした', { bad: 1 }); return; }
  if (s.from === 'composer') vcOut(s, vcPiece(s.pend));
  else vcSave(text);
  if (msg) toast(msg, { bad: 1 });
}

function vcTeardown(how) {
  const s = vcS; if (!s.active) return;
  s.active = false; clearTimeout(s.rt); s.rt = 0;
  const rec = s.rec; s.rec = null;
  if (vcMO) vcMO.disconnect();
  vcShowPill(false); vcMark();
  try { if (rec) (how === 'abort' ? rec.abort : rec.stop).call(rec); } catch (e) { }
}

/* 途中で止まった時: 聞けた分は残して知らせる */
function vcStop(msg, open) {
  const s = vcS; if (!s.active) return;
  const from = s.from;
  vcTeardown('stop');
  vcEnd(s, msg);
  if (open && from === 'nav' && window.openCapture) openCapture();
}

function vcRun() {
  const s = vcS; if (!s.active) return;
  let rec;
  try { const C = vcCls(); rec = new C(); } catch (e) { return vcStop('この端末では音声入力が使えません', true); }
  s.rec = rec; s.gotResult = false;
  const got = new Set();   /* この認識の中で composer へ渡し済みの確定(results の位置) */
  rec.lang = 'ja-JP'; rec.continuous = true; rec.interimResults = true;
  /* 1回の認識の中の全結果を読み直す(確定 = sess、途中 = pend)。前の区切りは base に積んである */
  rec.onresult = e => {
    if (s.rec !== rec) return;
    let fin = '', mid = '';
    const list = (e && e.results) || [];
    for (let i = 0; i < list.length; i++) {
      const r = list[i]; if (!r) continue;
      const pc = vcPiece(r[0] && r[0].transcript);
      if (r.isFinal) {
        fin = vcPut(fin, pc, '\n');
        if (!got.has(i)) { got.add(i); if (s.from === 'composer' && pc) vcOut(s, pc); }
      } else mid = vcPut(mid, pc, '\n');
    }
    s.sess = fin; s.pend = mid;
    if (fin || mid) s.gotResult = true;
    vcPaint();
  };
  rec.onerror = e => {
    if (s.rec !== rec) return;
    const code = (e && e.error) || '';
    if (code === 'no-speech' || code === 'aborted') return;      /* 直後の onend で再開する */
    if (code === 'not-allowed' || code === 'service-not-allowed') return vcStop('マイクを許可してください', true);
    vcStop('音声入力が止まりました', false);
  };
  rec.onend = () => {
    if (s.rec !== rec) return;
    if (s.from === 'composer') vcOut(s, vcPiece(s.pend));       /* 確定しないまま切れた文は渡す */
    s.base = vcAll(); s.sess = ''; s.pend = ''; s.rec = null;
    s.cut = !!s.base;                                           /* 次の認識の前で改行 */
    vcPaint();
    if (!s.active) return;
    s.fails = s.gotResult ? 0 : s.fails + 1;
    if (s.fails >= VC_MAX_FAILS) return vcStop('音声が聞き取れないため止めました', false);
    s.rt = setTimeout(vcRun, 50);
  };
  try { rec.start(); } catch (e) { vcStop('この端末では音声入力が使えません', true); }
}

/* 録音を始める。from: 'nav'(＋の長押し) / 'composer'(入力欄の 🎤) */
function voiceStart(o = {}) {
  if (vcS.active) return true;
  const from = o.from === 'composer' ? 'composer' : 'nav';
  if (!voiceSupported()) {
    toast('この端末では音声入力が使えません', { bad: 1 });
    if (from === 'nav' && window.openCapture) openCapture();
    return false;
  }
  Object.assign(vcS, { active: true, from, onText: o.onText || null, rec: null, base: '', sess: '', pend: '', cut: false, gotResult: false, fails: 0 });
  if (vcMO) vcMO.observe(document.body, { childList: true, subtree: true });
  vcMark(); vcShowPill(true); hapt();
  vcRun();
  return vcS.active;
}

/* 録音を終える: 文字を返し、nav なら雑記に置く */
function voiceFinish() {
  if (!vcS.active) return '';
  const s = vcS, text = vcText();
  vcTeardown('stop');
  hapt();
  vcEnd(s, '');
  return text;
}

/* 録音をやめる(何も作らない) */
function voiceCancel() { if (vcS.active) vcTeardown('abort'); }

/* ---------- 長押しの判定(委譲) ---------- */
function vcDown(e) {
  if (e.button > 0 || !e.isPrimary) return;
  const el = vcOnPlus(e.target);
  if (!el) return;
  vcEat = false;
  if (vcPress) clearTimeout(vcPress.t);
  const p = { id: e.pointerId, x: e.clientX, y: e.clientY, fired: false, t: 0 };
  p.t = setTimeout(() => {
    p.t = 0; p.fired = true;
    if (!vcS.active && el) voiceStart({ from: 'nav' });
  }, VC_HOLD);
  vcPress = p;
}
function vcMove(e) {
  const p = vcPress; if (!p || p.fired || e.pointerId !== p.id) return;
  if (Math.hypot(e.clientX - p.x, e.clientY - p.y) > VC_SLOP) { clearTimeout(p.t); p.t = 0; }
}
function vcUp(e) {
  const p = vcPress; if (!p || e.pointerId !== p.id) return;
  vcPress = null; clearTimeout(p.t);
  if (p.fired) vcEat = true;                     /* 長押しだった: 続く click を抑える */
}
function vcCancelPress(e) {
  const p = vcPress; if (!p || e.pointerId !== p.id) return;
  vcPress = null; clearTimeout(p.t);
}
/* 捕捉段階で止める: 長押しの後の click は無視、録音中の ＋ のタップは完了 */
function vcClick(e) {
  if (!vcOnPlus(e.target)) return;
  if (vcEat) { vcEat = false; e.preventDefault(); e.stopImmediatePropagation(); return; }
  if (vcS.active) { e.preventDefault(); e.stopImmediatePropagation(); voiceFinish(); }
}

/* boot の後に1回 */
function voiceInit() {
  if (window._vcInit) return; window._vcInit = true;
  document.body.insertAdjacentHTML('beforeend', '<div id="voice-pill" class="vc-pill" role="status" aria-live="polite"><span class="vc-dot" aria-hidden="true"></span><span class="vc-txt"></span><button class="vc-stop" type="button">やめる</button></div>');
  vcPill = document.getElementById('voice-pill');
  vcTxt = vcPill.querySelector('.vc-txt');
  vcPill.querySelector('.vc-stop').addEventListener('click', () => voiceCancel());
  vcMO = new MutationObserver(() => vcMark());
  document.addEventListener('pointerdown', vcDown);
  document.addEventListener('pointermove', vcMove, { passive: true });
  document.addEventListener('pointerup', vcUp);
  document.addEventListener('pointercancel', vcCancelPress);
  document.addEventListener('contextmenu', e => { if (vcOnPlus(e.target)) e.preventDefault(); }, true);
  document.addEventListener('click', vcClick, true);
}
