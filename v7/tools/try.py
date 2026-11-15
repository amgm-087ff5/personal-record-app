#!/usr/bin/env python3
"""ルートを開いて JS エラーを出す小道具。 使い方: python3 tools/try.py '/home,/a/l1' ["評価するJS式"] [スクショの出力先.png]
各ルートを 412 幅で開き、pageerror と console.error を表示する。JS式を渡すと各ルートで評価して結果を表示する(await 可)。"""
import sys, asyncio, os, json
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'
async def main():
    routes = sys.argv[1].split(','); js = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] else None; shot = sys.argv[3] if len(sys.argv) > 3 else None
    n = 0
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        for r in routes:
            ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True); pg = await ctx.new_page()
            errs = []
            pg.on('pageerror', lambda e: errs.append('pageerror: ' + str(e)[:300]))
            pg.on('console', lambda m: errs.append('console: ' + m.text[:300]) if m.type == 'error' else None)
            await pg.goto(BASE + '#' + r); await pg.wait_for_timeout(600)
            out = None
            if js:
                try: out = await pg.evaluate('(async()=>{ return ' + js + ' })()')
                except Exception as e: errs.append('eval: ' + str(e)[:400])
                await pg.wait_for_timeout(300)
            txt = await pg.evaluate("(document.querySelector('#main')||document.body).innerText.length")
            if shot: await pg.screenshot(path=shot if len(routes) == 1 else shot.replace('.png', '') + '_' + str(routes.index(r)) + '.png', full_page=False)
            print(('NG ' if errs else 'ok ') + r + f' (text {txt})' + ('' if out is None else ' => ' + json.dumps(out, ensure_ascii=False)[:1500]))
            for e in errs: print('   ' + e)
            n += len(errs); await ctx.close()
        await b.close()
    print('errors:', n); sys.exit(1 if n else 0)
asyncio.run(main())
