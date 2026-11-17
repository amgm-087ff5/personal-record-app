#!/usr/bin/env python3
"""出来事の詳細(s-detail.js)の評価: 実績の出来事に関わった物・店の星を押すと、評価が付き画面が描き直る。
予定と対象の無い出来事には評価の節が出ない。
使い方: python3 -I v7/tools/t_hub_act.py"""
import asyncio, os, sys
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + ROOT + '/index.html?now=2026-11-11T13:20'
ARGS = ['--no-sandbox', '--allow-file-access-from-files']


async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=ARGS)
        ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
        pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' and 'net::' not in m.text else None)
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(500)
        await pg.evaluate("localStorage.clear()"); await pg.goto(BASE + '#/a/l1'); await pg.wait_for_timeout(500)

        # 1. 実績の出来事(l1)に、対象の店の評価行が出る(人の同席は出ない)
        rows = pg.locator('.da-rate')
        n = await rows.count()
        if n != 1: bad.append(f'l1 の評価行 {n} 件(1 件のはず)')
        before = await pg.evaluate("ratingNow('aoba')")
        if before is not None: bad.append(f'初期の評価が入っている {before}')

        # 2. 4つ目の星を押すと、評価が 4 になり、画面の星が 4 つ点く
        await rows.first.locator('.rate-s').nth(3).click(); await pg.wait_for_timeout(500)
        after = await pg.evaluate("ratingNow('aoba')")
        if after != 4: bad.append(f'星を押した後の評価 {after}(4 のはず)')
        on = await pg.locator('.da-rate .rate-s.on').count()
        if on != 4: bad.append(f'画面の点いた星 {on}(4 のはず)')

        # 3. 同じ星をもう一度押すと外れる(rateTap の仕様)
        await pg.locator('.da-rate').first.locator('.rate-s').nth(3).click(); await pg.wait_for_timeout(500)
        if (await pg.evaluate("ratingNow('aoba')")) is not None: bad.append('同じ星で外れない')

        # 4. 予定(plan)の出来事には出ない: 予定の内見(h1)は対象の物が無い。予定を作って確かめる
        await pg.evaluate("() => { const r = actAdd({kind:'shop', title:'予定の買い物', start:'2026-11-20', state:'plan', roles:[{r:'場所', e:'super'}]}); window.__pid = r.id; }")
        await pg.goto(BASE + '#/a/' + await pg.evaluate("window.__pid")); await pg.wait_for_timeout(400)
        if await pg.locator('.da-rate').count() != 0: bad.append('予定に評価が出ている')

        # 5. 対象の無い実績(h1: プロジェクトと人だけ)は節ごと出ない
        await pg.goto(BASE + '#/a/h1'); await pg.wait_for_timeout(400)
        if await pg.locator('.da-rate').count() != 0: bad.append('対象の無い h1 に評価が出ている')
        if await pg.locator('.s-detail h2', has_text='評価').count() != 0: bad.append('対象の無い h1 に評価の節が出ている')

        if errs: bad.append(f'JSエラー {errs[:2]}')
        await ctx.close(); await b.close()
    if bad:
        print('NG'); [print(' -', x) for x in bad]; sys.exit(1)
    print('ok: 評価の付与・外し・予定と対象無しの非表示')


asyncio.run(main())
