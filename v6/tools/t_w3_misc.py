#!/usr/bin/env python3
"""W3 テスト: A2(通知の選択肢)・A3(アクティビティの支払い入口)・A4(すぐメモ)・A5(ホームの金額欄)・E7(不正な月)・E8(不正な日)・E12(写真の書きかけ)
使い方: python3 v6/tools/t_w3_misc.py   (W3_ROOT=/tmp/w3_before/v6 で修正前の再現も可)"""
import asyncio, os, sys
from playwright.async_api import async_playwright
ROOT = os.environ.get('W3_ROOT', os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
BASE = 'file://' + ROOT + '/index.html?now=2026-11-11T13:20'
ARGS = ['--no-sandbox', '--allow-file-access-from-files']


async def fresh(p):
    b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=ARGS)
    ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
    pg = await ctx.new_page(); errs = []
    pg.on('pageerror', lambda e: errs.append(str(e)))
    await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(600)
    return b, pg, errs


async def go(pg, h):
    await pg.evaluate(f"location.hash='{h}'"); await pg.wait_for_timeout(500)


async def a2_alarm_options(pg):
    await go(pg, '#/a/e1')
    await pg.evaluate("openActEditor({id:'e1'})"); await pg.wait_for_timeout(500)
    chips = await pg.locator('.sheet button.chip').all_inner_texts()
    for want in ['5分前', '1日前']:
        assert want in chips, f'通知に「{want}」が無い: {chips}'
    assert '前日' not in chips, f'「前日」が残っている: {chips}'
    # 旧データの「前日」は「1日前」として選択表示
    await pg.evaluate("DB.acts.e1.alarm='前日'; save(); closeAllSheets(); rerender()"); await pg.wait_for_timeout(500)
    txt = await pg.evaluate("document.querySelector('#main').innerText")
    assert '1日前' in txt, '旧データ「前日」が「1日前」で表示されない'
    await pg.evaluate("openActEditor({id:'e1'})"); await pg.wait_for_timeout(500)
    pressed = await pg.locator('.sheet button.chip', has_text='1日前').get_attribute('aria-pressed')
    assert pressed == 'true', f'旧データ「前日」で「1日前」が選択になっていない: {pressed}'


async def a3_pay_entry(pg):
    await go(pg, '#/acts')
    b = pg.locator('button[aria-label="支払いを記録"]')
    assert await b.count() == 1, 'アクティビティに「支払いを記録」の入口が無い'
    await b.click(); await pg.wait_for_timeout(500)
    assert (await pg.locator('.sheet h2').last.inner_text()) == '支払いを記録', '支払いの編集が開かない'


async def a4_quick_memo(pg):
    await go(pg, '#/home')
    await pg.fill('#hm-memo', 'テストのすぐメモ')
    await pg.locator('.hm-quick button', has_text='メモを保存').click(); await pg.wait_for_timeout(500)
    bodies = await pg.evaluate("Object.values(DB.memos).map(m=>m.body)")
    assert 'テストのすぐメモ' in bodies, 'すぐメモが保存されない'
    assert await pg.locator('#hm-memo').input_value() == '', '保存後に欄が空にならない'


async def a5_home_amount(pg):
    await go(pg, '#/home')
    await pg.evaluate("""()=>{ window.__pay=null; const o=openPayEditor; window.openPayEditor=(x)=>{ window.__pay=x; return o(x); }; }""")
    await pg.fill('#hm-qe', '1280')
    await pg.locator('.hm-qe button', has_text='支払いを記録').click(); await pg.wait_for_timeout(500)
    got = await pg.evaluate('window.__pay')
    assert got and got.get('amount') == 1280, f'金額が渡されない: {got}'


async def e7_bad_month(pg):
    await go(pg, '#/acts?view=month&m=2026-99'); await pg.wait_for_timeout(200)
    txt = await pg.locator('.mnav b').first.inner_text()
    assert txt == '2026年11月', f'不正な月が今月に直らない: {txt}'


async def e8_bad_day(pg):
    for d in ['abc', '2026-13-45']:
        await go(pg, f'#/day/{d}')
        txt = await pg.inner_text('.screen')
        assert '日が見つかりません' in txt, f'#/day/{d} が「見つかりません」にならない'


async def e12_photo_dirty(pg):
    await go(pg, '#/day/2026-11-11')
    await pg.evaluate("dayPhoto('2026-11-11')"); await pg.wait_for_timeout(400)
    await pg.fill('#ph-c', '書きかけ')
    await pg.keyboard.press('Escape'); await pg.wait_for_timeout(300)
    assert await pg.locator('.sheet').count() >= 1, '書きかけで閉じて消えた'
    assert await pg.locator('.sheet-ask').last.is_visible(), '書きかけの確認が出ない'


CASES = [('A2 通知の選択肢', a2_alarm_options), ('A3 アクティビティの支払い入口', a3_pay_entry),
         ('A4 ホームのすぐメモ', a4_quick_memo), ('A5 ホームの金額欄', a5_home_amount),
         ('E7 不正な月', e7_bad_month), ('E8 不正な日', e8_bad_day), ('E12 写真の書きかけ', e12_photo_dirty)]


async def main():
    bad = []
    async with async_playwright() as p:
        for name, fn in CASES:
            b, pg, errs = await fresh(p)
            try:
                await fn(pg)
                if errs: raise AssertionError(f'JSエラー {errs}')
                print('PASS', name)
            except Exception as e:
                print('FAIL', name, '-', str(e).splitlines()[0]); bad.append(name)
            finally:
                await b.close()
    sys.exit(1 if bad else 0)

asyncio.run(main())
