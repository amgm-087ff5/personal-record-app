"""項目4: 読みの取り消し(小札)は、その文の行にだけ効く。空行の後の次の文に持ち越さない"""
import asyncio, sys, os, json
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    from t_w1_common import open_page
except ImportError:   # 共通の土台が無い間の代用(BASE は v7/index.html。時計は 2026-11-11 13:20)
    URL = 'file://' + os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'index.html')) + '?now=2026-11-11T13:20'
    async def open_page(p):
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
        pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        await pg.goto(URL + '#/home'); await pg.wait_for_timeout(500)
        await pg.evaluate("localStorage.clear()"); await pg.goto(URL + '#/home'); await pg.wait_for_timeout(500)
        return b, pg, errs


async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        await pg.evaluate("openInbox()"); await pg.wait_for_timeout(400)
        await pg.evaluate("inSetDraft('A\\n明日 10:00')"); await pg.wait_for_timeout(200)
        # 小札「11/12 10:00」を押して取り消す(画面の操作どおり)
        btn = pg.locator('.in-mk[aria-label*="10:00を取り消す"]')
        if await btn.count() != 1: bad.append(f'小札が見つからない {await btn.count()}')
        else: await btn.first.click(); await pg.wait_for_timeout(200)
        # 空行のあと「B」「明日 12:00」を足す
        await pg.evaluate("inSetDraft('A\\n明日 10:00\\n\\nB\\n明日 12:00')"); await pg.wait_for_timeout(200)
        r = await pg.evaluate("(()=>{const R=inRead(); return R.items.map(it=>({t:it.text, cand: it.r.cand ? {type: it.r.cand.type, title: it.r.cand.title || it.r.cand.t || it.r.cand.body} : null}))})()")
        if len(r) != 2: bad.append(f'文が2つにならない {r}')
        elif not r[1]['cand'] or r[1]['cand'].get('type') != 'plan' or r[1]['cand'].get('title') != 'B':
            bad.append(f'2つ目の「B」が記録にならない(取り消しが持ち越された) {r[1]}')
        if r and r[0]['cand'] is not None: bad.append(f'1つ目の取り消しが効いていない {r[0]}')
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_4 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
