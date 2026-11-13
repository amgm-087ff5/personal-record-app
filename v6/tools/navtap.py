import asyncio, sys
from playwright.async_api import async_playwright
BASE='file://'+__import__('os').path.abspath(__import__('os').path.join(__import__('os').path.dirname(__file__),'..','index.html'))+'?now=2026-11-11T13:20'
async def tap(pg, href):
    el=pg.locator(f'a.nav-i[href="{href}"]').first
    bb=await el.bounding_box(); cx,cy=bb['x']+bb['width']/2,bb['y']+bb['height']/2
    top=await pg.evaluate("([x,y])=>{const e=document.elementFromPoint(x,y);return e?e.tagName+'.'+e.className:null}",[cx,cy])
    await pg.touchscreen.tap(cx,cy); await pg.wait_for_timeout(350)
    h=await pg.evaluate("location.hash")
    return top,h
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch(executable_path='/usr/bin/google-chrome',args=['--no-sandbox','--allow-file-access-from-files'])
        ctx=await b.new_context(viewport={'width':412,'height':900},has_touch=True,is_mobile=True); pg=await ctx.new_page()
        errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
        await pg.goto(BASE+'#/ents'); await pg.wait_for_timeout(700)
        bad=0
        # deck 1 -> deck 0 via knob, then tap deck0 tabs
        await pg.locator('.nav-knob').click(); await pg.wait_for_timeout(500)
        for href in ['#/home','#/acts','#/todo','#/memo']:
            top,h=await tap(pg,href); ok=(h==href[0:]); bad+=not ok
            print('deck0',href,'->',h,'| top:',top,'OK' if ok else 'NG')
            await pg.locator('.nav-knob').click() if False else None
        # deck 1 tabs
        await pg.locator('.nav-knob').click(); await pg.wait_for_timeout(500)
        for href in ['#/ents','#/ledger','#/search','#/settings']:
            top,h=await tap(pg,href); ok=(h==href); bad+=not ok
            print('deck1',href,'->',h,'| top:',top,'OK' if ok else 'NG')
        # after opening/closing + sheet
        await pg.evaluate("openCapture()"); await pg.wait_for_timeout(500)
        await pg.evaluate("closeAllSheets && closeAllSheets()"); await pg.wait_for_timeout(500)
        await pg.evaluate("location.hash='#/acts'"); await pg.wait_for_timeout(400)
        for href in ['#/home','#/todo']:
            top,h=await tap(pg,href); ok=(h==href); bad+=not ok
            print('after-capture',href,'->',h,'| top:',top,'OK' if ok else 'NG')
        print('errors',errs,'NG count',bad)
        await b.close()
asyncio.run(main())
