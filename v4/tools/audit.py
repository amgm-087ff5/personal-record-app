#!/usr/bin/env python3
"""v4 の機械的な確認(DESIGN.md の確認チェックリストのうち、計測できるもの)。
全ルート × ライト/ダーク × 幅412/1280 で開き、次を数える:
  - JSエラー
  - 横はみ出し(document の横スクロール)
  - 12px未満の文字(見えている要素)
  - 44px未満の押し面(a / button / [onclick]。::before の当たり判定拡張は計測できないので、
    要素の矩形が 44 未満で、かつ親の行が 44 未満のものを数える)
  - 文字が「…」で切れている要素の数(参考)
  - CSS の禁止色(墨・茶・紫): tokens.css 以外に直書きされた色を一覧
使い方: python3 tools/audit.py [base_url]
"""
import sys, asyncio, re, glob, os, colorsys
from playwright.async_api import async_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else 'file://' + os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'index.html'))
ROUTES = ['/home', '/memo', '/activities', '/day/2026-10-30', '/day/2026-10-31', '/a/p1', '/a/p16', '/a/c1', '/a/l1', '/a/m1', '/a/w1', '/i/p1/0', '/activities?view=day', '/activities?view=month', '/todo',
          '/ev/e1', '/ev/e5', '/task/t1', '/entities', '/e/hanako', '/e/aoba', '/e/acorp', '/e/proj1', '/n/hanako', '/notes', '/types', '/ledger', '/x/x1', '/x/x2', '/cats', '/settings']

JS = r"""() => {
  const vis = el => { const r = el.getBoundingClientRect(); const cs = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none' && +cs.opacity > 0.05 };
  const small = [], taps = [], cut = [];
  for (const el of document.querySelectorAll('#app *')) {
    if (!vis(el)) continue;
    const hasText = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
    if (hasText) { const fs = parseFloat(getComputedStyle(el).fontSize); if (fs < 11.95) small.push(`${fs}px「${el.textContent.trim().slice(0,12)}」<${el.className}>`) ;
      if (el.scrollWidth > el.clientWidth + 1 && getComputedStyle(el).textOverflow === 'ellipsis') cut.push(el.textContent.trim().slice(0,16)); }
    if (el.matches('a[href],button,[onclick],select,input')) {
      const r = el.getBoundingClientRect(); let ok = r.height >= 43.5 && r.width >= 43.5;
      if (!ok) { const b = getComputedStyle(el, '::before'); if (b.content !== 'none' && b.position === 'absolute') ok = true; }
      if (!ok) { const p = el.closest('.row,.ax-row,.prop,.field,.tkt-w'); if (p && p !== el && p.getBoundingClientRect().height >= 44 && r.height >= 32) ok = true; }
      if (!ok) taps.push(`${Math.round(r.width)}x${Math.round(r.height)}「${(el.getAttribute('aria-label')||el.textContent).trim().slice(0,12)}」<${el.className}>`);
    }
  }
  const over = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  return { small, taps, cut, over };
}"""

async def main():
    out = []; tot = {'err':0,'small':0,'taps':0,'over':0}
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox'])
        for dark in (False, True):
            for w in (412, 1280):
                ctx = await b.new_context(viewport={'width': w, 'height': 900}, color_scheme='dark' if dark else 'light')
                pg = await ctx.new_page(); errs = []
                pg.on('pageerror', lambda e: errs.append(str(e)))
                for r in ROUTES:
                    errs.clear()
                    await pg.goto(BASE + '#' + r); await pg.wait_for_timeout(250)
                    res = await pg.evaluate(JS)
                    tag = f"{'dark' if dark else 'light'} {w} {r}"
                    if errs: out.append(f'[JS] {tag}: {errs[:2]}'); tot['err'] += len(errs)
                    if res['over'] > 0: out.append(f'[はみ出し] {tag}: {res["over"]}px'); tot['over'] += 1
                    if res['small'] and not dark and w == 412: out.append(f'[12px未満] {tag}: {sorted(set(res["small"]))[:6]}'); tot['small'] += len(set(res['small']))
                    if res['taps'] and not dark and w == 412: out.append(f'[押し面<44] {tag}: {sorted(set(res["taps"]))[:6]}'); tot['taps'] += len(set(res['taps']))
                await ctx.close()
        await b.close()
    # 禁止色(tokens.css 以外の直書き)
    root = os.path.join(os.path.dirname(__file__), '..')
    bad = []
    for f in glob.glob(os.path.join(root, 'css', '*.css')) + glob.glob(os.path.join(root, 'js', '**', '*.js'), recursive=True):
        if f.endswith('tokens.css'): continue
        for m in re.finditer(r'#([0-9a-fA-F]{6})\b', open(f).read()):
            h = m.group(1); r, g, bl = (int(h[i:i+2], 16) / 255 for i in (0, 2, 4))
            hh, l, s = colorsys.rgb_to_hls(r, g, bl); deg = hh * 360
            if l < 0.18 and s < 0.5: bad.append(f'墨? #{h} {os.path.basename(f)}')
            elif 15 <= deg <= 50 and s > 0.25 and l < 0.45: bad.append(f'茶? #{h} {os.path.basename(f)}')
            elif 255 <= deg <= 300 and s > 0.3: bad.append(f'紫? #{h} {os.path.basename(f)}')
    print('\n'.join(out))
    print('禁止色の疑い:', sorted(set(bad)) or 'なし')
    print('合計:', tot)

asyncio.run(main())
