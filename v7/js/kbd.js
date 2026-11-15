/* 個人記録 v6.2 — ソフトウェアキーボードで隠れない(全画面・全シート共通)
   Android Chrome: meta の interactive-widget=resizes-content で画面ごと縮む。
   それが効かない端末(iOS・古い Chrome)は visualViewport で縮んだ分(--kb)を余白にして、最下部までスクロールできるようにする。
   キーボード中は下のナビを隠す(入力欄に重ならない)。 */
(function () {
  const vv = window.visualViewport;
  const editable = el => !!el && (el.isContentEditable || (el.tagName === 'TEXTAREA') || (el.tagName === 'INPUT' && !/^(checkbox|radio|button|submit|range|color|file)$/.test(el.type)));
  let base = Math.max(innerHeight, vv ? vv.height : 0);
  function upd() {
    const ed = editable(document.activeElement);
    const vh = vv ? vv.height : innerHeight;
    if (!ed) base = Math.max(innerHeight, vh);                 /* キーボードが無い時の高さを覚える */
    const shrunk = ed && (base - vh > 120);                    /* 画面ごと縮んだ(resizes-content)・表示域だけ縮んだ(visual)のどちらも */
    const kb = ed && vv ? Math.max(0, innerHeight - vh - vv.offsetTop) : 0;   /* 画面が縮んでいない時だけ残る分 */
    const r = document.documentElement.style;
    r.setProperty('--kb', kb + 'px');
    document.body.classList.toggle('kb-open', !!shrunk);
  }
  function reveal() {
    setTimeout(() => { const a = document.activeElement; if (editable(a) && a.scrollIntoView) a.scrollIntoView({ block: 'center', behavior: 'smooth' }); }, 320);
  }
  addEventListener('focusin', e => { if (editable(e.target)) { upd(); reveal(); } });
  addEventListener('focusout', () => setTimeout(upd, 60));
  if (vv) { vv.addEventListener('resize', () => { upd(); reveal(); }); vv.addEventListener('scroll', upd); }
  addEventListener('resize', upd);
})();
