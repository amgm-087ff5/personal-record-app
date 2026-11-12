/* 個人記録 v6.2 — ＋の長押しの音声入力(担当 E。仕様 INBOX.md §3.3) */
'use strict';
/* 長押し(450ms・10px 以内)で録音開始 → 離しても続く → 録音中の ＋ のタップで完了 → 雑記に置く。
   ＋(.nav-plus / .rail-plus)は renderNav で描き直されるので、要素には直接付けず document に委譲する。 */

const VC_SEL = '.nav-plus,.rail-plus';
const VC_HOLD = 450, VC_SLOP = 10, VC_MAX_FAILS = 3;
let vcS = { active: false, from: 'nav', onText: null, rec: null, base: '', sess: '', pend: '', gotResult: false, fails: 0, rt: 0 };
let vcPress = null;      /* 押している最中(長押しの判定) */
let vcEat = false;       /* 長押しの後の離す操作の click を1回だけ抑える */
let vcPill = null, vcTxt = null, vcMO = null;

function vcCls() { return window.__speechMock || window.webkitSpeechRecognition || window.SpeechRecognition || null; }
function voiceSupported() { return !!vcCls(); }
function voiceActive() { return !!vcS.active; }
function vcOnPlus(t) { return t && t.closest ? t.closest(VC_SEL) : null; }

function vcText() { return (vcS.base + vcS.sess + vcS.pend).replace(/\s+/g, ' ').trim(); }
function vcPaint() {
  if (!vcTxt) return;
  const t = vcText();
  const show = t ? (t.length > 60 ? '…' + t.slice(-60) : t) : '聞いています…';
  vcTxt.innerHTML = esc(show);
}
/* ＋ に録音中の印を付ける(描き直されても付け直す) */
function vcMark() { document.querySelectorAll(VC_SEL).forEach(el => el.classList.toggle('is-rec', vcS.active)); }
function vcShowPill(on) { if (!vcPill) return; vcPill.classList.toggle('is-on', !!on); if (on) vcPaint(); }

/* 結果の扱い: nav → 雑記に置く / composer → onText に渡す */
function vcDeliver(text, from, onText, failMsg) {
  if (!text) { toast(failMsg || '聞き取れませんでした', { bad: 1 }); return; }
  if (from === 'composer') { onText && onText(text); if (failMsg) toast(failMsg, { bad: 1 }); return; }
  if (typeof jotAdd !== 'function') { toast('雑記に置けませんでした', { bad: 1 }); return; }
  const r = jotAdd(text, { src: 'voice' });
  if (r && r.ok) toast('雑記に置きました', { undo: r.undo, act: { label: '開く', on: () => window.openInbox && openInbox() } });
  else toast((r && r.why) || '置けませんでした', { bad: 1 });
  window.inRefresh && inRefresh();
  if (failMsg) toast(failMsg, { bad: 1 });
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
  const text = vcText(), from = s.from, cb = s.onText;
  vcTeardown('stop');
  vcDeliver(text, from, cb, msg);
  if (open && from === 'nav' && window.openCapture) openCapture();
}

function vcRun() {
  const s = vcS; if (!s.active) return;
  let rec;
  try { const C = vcCls(); rec = new C(); } catch (e) { return vcStop('この端末では音声入力が使えません', true); }
  s.rec = rec; s.gotResult = false;
  rec.lang = 'ja-JP'; rec.continuous = true; rec.interimResults = true;
  /* 1回の認識の中の全結果を読み直す(確定 = sess、途中 = pend)。前の区切りは base に積んである */
  rec.onresult = e => {
    if (s.rec !== rec) return;
    let fin = '', mid = '';
    const list = (e && e.results) || [];
    for (let i = 0; i < list.length; i++) { const r = list[i]; const t = (r && r[0] && r[0].transcript) || ''; if (r && r.isFinal) fin += t; else mid += t; }
    s.sess = fin; s.pend = mid;
    if ((fin + mid).trim()) s.gotResult = true;
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
    s.base += s.sess + s.pend; s.sess = ''; s.pend = ''; s.rec = null;
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
  Object.assign(vcS, { active: true, from, onText: o.onText || null, rec: null, base: '', sess: '', pend: '', gotResult: false, fails: 0 });
  if (vcMO) vcMO.observe(document.body, { childList: true, subtree: true });
  vcMark(); vcShowPill(true); hapt();
  vcRun();
  return vcS.active;
}

/* 録音を終える: 文字を返し、nav なら雑記に置く */
function voiceFinish() {
  if (!vcS.active) return '';
  const text = vcText(), from = vcS.from, cb = vcS.onText;
  vcTeardown('stop');
  hapt();
  vcDeliver(text, from, cb);
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
