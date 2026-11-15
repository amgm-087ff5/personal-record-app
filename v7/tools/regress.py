#!/usr/bin/env python3
"""v6 の退行の検査(公開前に必ず): 1) 全8タブをタッチで押せる 2) 360px幅でナビ題が＋と重ならない 3) 高さだけの変化で描き直さない 4) 時刻が線の右・カードの下"""
import asyncio, os
from playwright.async_api import async_playwright
BASE='file://'+os.path.abspath(os.path.join(os.path.dirname(__file__),'..','index.html'))+'?now=2026-11-11T13:20'
async def main():
    bad=[]
    async with async_playwright() as p:
        b=await p.chromium.launch(executable_path='/usr/bin/google-chrome',args=['--no-sandbox','--allow-file-access-from-files'])
        ctx=await b.new_context(viewport={'width':412,'height':900},has_touch=True,is_mobile=True); pg=await ctx.new_page()
        errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
        await pg.goto(BASE+'#/ents'); await pg.wait_for_timeout(700)
        async def tap(h):
            bb=await pg.locator(f'a.nav-i[href="{h}"]').first.bounding_box()
            await pg.touchscreen.tap(bb['x']+bb['width']/2,bb['y']+bb['height']/2); await pg.wait_for_timeout(350)
            return await pg.evaluate('location.hash')
        await pg.locator('.nav-knob').click(); await pg.wait_for_timeout(500)
        for h in ['#/home','#/acts','#/todo','#/memo']:
            if await tap(h)!=h: bad.append('tap '+h)
        await pg.locator('.nav-knob').click(); await pg.wait_for_timeout(500)
        for h in ['#/ents','#/ledger','#/search','#/settings']:
            if await tap(h)!=h: bad.append('tap '+h)
        # 高さだけの変化で描き直さない
        await pg.goto(BASE+'#/home'); await pg.wait_for_timeout(500)
        await pg.evaluate("window.__n=0; new MutationObserver(()=>{window.__n++}).observe(document.querySelector('#main'),{childList:true})")
        await pg.set_viewport_size({'width':412,'height':800}); await pg.wait_for_timeout(400)
        if await pg.evaluate('window.__n')!=0: bad.append('高さ変化で描き直した')
        await pg.set_viewport_size({'width':360,'height':800}); await pg.wait_for_timeout(500)
        if await pg.evaluate('window.__n')<1: bad.append('幅変化で描き直さない')
        # 360px: 短い題と＋が重ならない
        await pg.goto(BASE+'#/home'); await pg.wait_for_timeout(500)
        r=await pg.evaluate("""()=>{const v=e=>e&&e.offsetParent!==null; const a=[...document.querySelectorAll('a.nav-i[href=\"#/acts\"] span')].filter(v).map(e=>e.textContent);
          const s=document.querySelector('a.nav-i[href=\"#/acts\"] span:last-of-type').getBoundingClientRect(), pl=document.querySelector('.nav-plus').getBoundingClientRect(); return {labels:a, overlap: s.right>pl.left && s.left<pl.right}}""")
        if '活動' not in r['labels'] or r['overlap']: bad.append(f'360px ナビ {r}')
        await pg.set_viewport_size({'width':412,'height':900}); await pg.goto(BASE+'#/acts'); await pg.wait_for_timeout(600)
        lay=await pg.evaluate("""()=>{const t=document.querySelector('.tl-row .tl-t:not(:empty)'); if(!t) return 'no-tl'; const c=t.parentElement; const n=c.parentElement.querySelector('.tl-n').getBoundingClientRect(); const r=t.getBoundingClientRect(); const body=t.nextElementSibling&&t.nextElementSibling.getBoundingClientRect(); return {right_of_line:r.left>=n.right-1, card_below: body? body.top>=r.bottom-1:null}}""")
        if lay=='no-tl' or not lay['right_of_line'] or lay['card_below'] is False: bad.append(f'時刻の配置 {lay}')
        await pg.screenshot(path='/tmp/acts.png')
        if errs: bad.append(f'JSエラー {errs}')
        await b.close()
    print('NG:',bad) if bad else print('regress OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
