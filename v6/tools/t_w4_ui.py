#!/usr/bin/env python3
"""W4: E5(＋の数は青、赤は ToDo だけ) / E10(色・meta theme-color は tokens.css から) / E11(動きを減らす設定で scrollIntoView を瞬時に) / E9(段階外の大きさは変数)"""
import asyncio, os, re, glob
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'

async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
        pg = await ctx.new_page()
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(500)
        # E5: ＋の数は青系、ToDo の数は赤
        c = await pg.evaluate("""() => { const pb = document.querySelector('.nav-plus .nav-badge'); const tb = document.querySelector('a.nav-i[href="#/todo"] .nav-badge');
          const cs = e => e && getComputedStyle(e); return { plus: pb ? [cs(pb).backgroundColor, cs(pb).color] : null, todo: tb ? cs(tb).backgroundColor : null, me: getComputedStyle(document.documentElement).getPropertyValue('--me').trim(), over: getComputedStyle(document.documentElement).getPropertyValue('--over').trim() } }""")
        if not c['plus']: bad.append('E5 ＋の数が出ていない')
        else:
            if c['plus'][0].startswith('rgb(229') or c['plus'][0].startswith('rgb(229, 72'): bad.append(f'E5 ＋の数が赤 {c["plus"]}')
            if c['plus'][1] != 'rgb(43, 91, 215)': bad.append(f'E5 ＋の数の文字が青でない {c["plus"]}')
        if c['todo'] and c['todo'] != 'rgb(229, 72, 77)': bad.append(f'E5 ToDo の数が赤でない {c["todo"]}')
        # E10: meta theme-color は tokens.css の --bg と同じ
        m = await pg.evaluate("() => document.querySelector('meta[name=theme-color]').content")
        bg = await pg.evaluate("() => getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()")
        if m != '#EDF1F8' or bg != '#EDF1F8': bad.append(f'E10 theme-color light {m} {bg}')
        await pg.evaluate("() => setTheme('dark')"); await pg.wait_for_timeout(100)
        m2 = await pg.evaluate("() => document.querySelector('meta[name=theme-color]').content")
        if m2 != '#0E1525': bad.append(f'E10 theme-color dark {m2}')
        await pg.evaluate("() => setTheme('auto')")
        # E11: 動きを減らす時は瞬時、通常は滑らか。kbd.js の reveal が使う
        await pg.goto(BASE + '#/search'); await pg.wait_for_timeout(400)
        for mode, want in [('reduce', 'auto'), ('no-preference', 'smooth')]:
            await pg.emulate_media(reduced_motion=mode)
            await pg.goto(BASE + '#/search'); await pg.wait_for_timeout(400)
            await pg.evaluate("() => { window.__so = []; const o = Element.prototype.scrollIntoView; Element.prototype.scrollIntoView = function (x) { window.__so.push(x); return o.call(this, {block:'nearest'}); }; }")
            await pg.evaluate("() => { const i = document.querySelector('#main input'); if (i) { i.blur(); i.focus(); } }")
            await pg.wait_for_timeout(450)
            dbg = await pg.evaluate("() => ({n: (window.__so||[]).length, a: document.activeElement.tagName})")
            got = await pg.evaluate("() => { const x = (window.__so || []).filter(o => o && o.block === 'center').at(-1); return x ? x.behavior : null; }")
            if got != want: bad.append(f'E11 reduced={mode} behavior={got} want={want} {dbg}')
        await pg.emulate_media(reduced_motion='no-preference')
        await b.close()
    # E9/E10/E11 の静的な検査: 直書きの大きさ・色・smooth が残っていない(自分の担当ファイル)
    for f in ['css/base.css', 'css/parts.css', 'css/screens.css', 'js/kbd.js', 'js/core.js']:
        src = open(os.path.join(ROOT, f), encoding='utf-8').read()
        for mm in re.finditer(r'font-size:\s*(34|17|16|64)px', src): bad.append(f'{f} 直書きの文字 {mm.group(0)}')
        if re.search(r'rgba\(|#fff\b', src): bad.append(f'{f} 直書きの色')
        if "behavior: 'smooth'" in src and f.endswith('kbd.js'): bad.append(f'{f} smooth 直書き')
    print('NG:', bad) if bad else print('t_w4_ui OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
