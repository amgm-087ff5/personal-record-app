#!/usr/bin/env python3
"""ホームの「取り組み中」(目標カード): 表示・「記録にする」で今日の回が done になり連続が増える / JS エラー 0"""
import asyncio, os, sys
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(__file__))
from t_w1_common import open_page

async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        # 表示: 進行中の目標が2件、「顔やせする」は連続5日・今日の回が未記録
        info = await pg.evaluate("""() => { const c = [...document.querySelectorAll('.gl-card')];
          const card = c.find(x => x.textContent.includes('顔やせする'));
          return { n: c.length, has: !!card, streak: card ? card.querySelector('.gl-meta').innerText : '',
                   btn: card ? card.querySelectorAll('.gl-today .btn').length : -1,
                   before: streakOf('proj2') } }""")
        if info['n'] != 2 or not info['has']: bad.append(f'目標カード {info}')
        if not info['streak'].count('5日 連続'): bad.append(f'連続の表示 {info["streak"]!r}')
        if info['btn'] != 1: bad.append(f'今日の記録ボタン数 {info["btn"]}')
        if info['before']['n'] != 5: bad.append(f'連続の前 {info["before"]}')

        # 操作: 「記録にする」→ 今日の回が done、連続は 6
        await pg.locator('.gl-card', has_text='顔やせする').get_by_role('button', name='記録にする').click()
        await pg.wait_for_timeout(500)
        after = await pg.evaluate("""() => { const c = [...document.querySelectorAll('.gl-card')].find(x => x.textContent.includes('顔やせする'));
          return { done: c ? c.querySelectorAll('.gl-t.done').length : -1, btn: c ? c.querySelectorAll('.gl-today .btn').length : -1,
                   streak: streakOf('proj2'), meta: c ? c.querySelector('.gl-meta').innerText : '' } }""")
        if after['done'] != 1 or after['btn'] != 0: bad.append(f'記録後の表示 {after}')
        if after['streak']['n'] != 6: bad.append(f'記録後の連続 {after["streak"]}')
        if '6' not in after['meta'] or '連続' not in after['meta']: bad.append(f'記録後の連続の文字 {after["meta"]!r}')

        if errs: bad.append(f'JS エラー {errs[:3]}')
        await b.close()
    print('NG:', bad) if bad else print('t_hub_home OK')
    raise SystemExit(1 if bad else 0)

asyncio.run(main())
