#!/usr/bin/env python3
"""v6 の機械点検。全ルート × ライト/ダーク × 幅 412/1280 で開き、次を数える:
  JSエラー / 横はみ出し / 12px未満の文字 / 44px未満の押せる所(::after・::before の当たり判定の拡張と .hit は合格)/
  CSS・JS に直書きされた色(tokens.css 以外。許した例外を除く)
使い方: python3 tools/audit.py [quick]
"""
import sys, asyncio, re, glob, os
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'
ROUTES = ['/home', '/acts', '/acts?view=day', '/acts?view=month', '/acts?m=2026-10', '/day/2026-11-10', '/day/2026-11-11', '/day/2026-11-13', '/day/2025-11-11',
          '/a/l1', '/a/w2', '/a/c1', '/a/w1', '/a/m1', '/a/e1', '/a/e9', '/a/e6', '/a/f_rent@2026-11-27', '/a/p1', '/a/p16', '/a/p17', '/a/p2', '/a/p31', '/a/p3', '/a/f_rent_2026-10-27',
          '/todo', '/task/t1', '/task/t2', '/task/t5', '/memo', '/memo?v=notes', '/ents', '/e/hanako', '/e/aoba', '/e/minato', '/e/acorp', '/e/proj1', '/e/ken', '/types', '/n/hanako', '/n/ken',
          '/ledger', '/ledger?m=2026-10', '/ledger?m=2026-06', '/cats', '/search', '/search?q=らーめん', '/search?q=zzzz', '/settings']
JS = r"""() => {
  const vis = el => { const r = el.getBoundingClientRect(); const cs = getComputedStyle(el); return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none' && +cs.opacity > 0.05 };
  const small = [], taps = [];
  for (const el of document.querySelectorAll('#main *, #side *, #nav *')) {
    if (!vis(el)) continue;
    if ([...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) { const fs = parseFloat(getComputedStyle(el).fontSize); if (fs < 11.95) small.push(`${fs}px「${el.textContent.trim().slice(0,12)}」<${el.className}>`); }
    if (el.matches('a[href],button,[onclick],select,input,textarea')) {
      const r = el.getBoundingClientRect(); let ok = r.height >= 43.5 && r.width >= 43.5;
      for (const ps of ['::after', '::before']) { if (ok) break; const b = getComputedStyle(el, ps); if (b.content !== 'none' && b.position === 'absolute') { const t = parseFloat(b.top) || 0, l = parseFloat(b.left) || 0; if (r.height - 2 * t >= 43.5 && (r.width - 2 * l >= 43.5 || r.width >= 60)) ok = true; } }
      if (!ok && r.height >= 43.5 && r.width >= 24 && el.closest('.chips,.seg,.cal-g,.mnav,.wk,.hg-in')) ok = true;
      if (!ok && el.matches('.sky-b')) ok = true;   /* 帯の上の期間は、幅が時間の長さ(同じ物は下の一覧からも開ける) */
      if (!ok) { const p = el.closest('.row,.prop,.field,.task,.rv-i'); if (p && p !== el && p.getBoundingClientRect().height >= 44 && r.height >= 30 && r.width >= 30) ok = true; }
      if (!ok) taps.push(`${Math.round(r.width)}x${Math.round(r.height)}「${(el.getAttribute('aria-label')||el.textContent).trim().slice(0,12)}」<${el.className}>`);
    }
  }
  const sc = innerWidth >= 900 ? document.querySelector('#main') : document.documentElement;
  return { small, taps, over: sc.scrollWidth - sc.clientWidth };
}"""
async def main():
    quick = len(sys.argv) > 1; out = []; tot = {'err': 0, 'small': 0, 'taps': 0, 'over': 0}
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        for dark in ((False,) if quick else (False, True)):
            for w in ((412,) if quick else (412, 360, 1280)):
                ctx = await b.new_context(viewport={'width': w, 'height': 900}, color_scheme='dark' if dark else 'light')
                pg = await ctx.new_page(); errs = []
                pg.on('pageerror', lambda e: errs.append(str(e)))
                pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' and 'net::' not in m.text else None)
                for r in ROUTES:
                    errs.clear()
                    await pg.goto(BASE + '#' + r); await pg.wait_for_timeout(300)
                    res = await pg.evaluate(JS); tag = f"{'dark' if dark else 'light'} {w} {r}"
                    landed = await pg.evaluate("document.body.dataset.route")
                    if landed != r.split('/')[1].split('?')[0]: out.append(f'[ルートなし] {tag} → {landed}'); tot['err'] += 1
                    if errs: out.append(f'[JS] {tag}: {errs[:2]}'); tot['err'] += len(errs)
                    if res['over'] > 0: out.append(f'[はみ出し] {tag}: {res["over"]}px'); tot['over'] += 1
                    if res['small'] and not dark and w == 412: out.append(f'[12px未満] {tag}: {sorted(set(res["small"]))[:6]}'); tot['small'] += len(set(res['small']))
                    if res['taps'] and not dark and w == 412: out.append(f'[押し面<44] {tag}: {sorted(set(res["taps"]))[:8]}'); tot['taps'] += len(set(res['taps']))
                await ctx.close()
        await b.close()
    bad = []
    for f in glob.glob(os.path.join(ROOT, 'css', '*.css')) + glob.glob(os.path.join(ROOT, 'js', '*.js')):
        if f.endswith('tokens.css'): continue
        for i, line in enumerate(open(f).read().split('\n'), 1):
            for m in re.finditer(r'#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![0-9a-zA-Z_-])', line):
                if os.path.basename(f) == 'model.js' or 'SKY' in line or m.group(0).lower() in ('#fff', '#ffffff') or '.toast' in line: continue
                bad.append(f'{os.path.basename(f)}:{i} {m.group(0)}')
            if f.endswith('.css'):
                for m in re.finditer(r'font-size:\s*(\d+)px', line):
                    if int(m.group(1)) < 12: bad.append(f'{os.path.basename(f)}:{i} font-size {m.group(1)}px')
    print('\n'.join(out)); print('直書きの色・小さい文字:', bad or 'なし'); print('合計:', tot)
asyncio.run(main())
