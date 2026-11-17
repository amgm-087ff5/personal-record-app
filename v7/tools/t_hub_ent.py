#!/usr/bin/env python3
"""v7 検査: エンティティ詳細の評価(星を押す・付け直し・同じ星で外す)と、プロジェクトの目標(期日・連続)。
使い方: python3 -I tools/t_hub_ent.py"""
import asyncio, os, sys
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from t_w1_common import open_page

SHOT = '/tmp/ent_rating.png'


async def go(pg, h):
    await pg.evaluate(f"location.hash='{h}'"); await pg.wait_for_timeout(500)


async def rating_flow(pg):
    await pg.evaluate("rateSet('hanako', null)")
    await go(pg, '#/e/hanako')
    stars = pg.locator('.rt-now button.rate-s')
    assert await stars.count() == 5, '星の列が5つ無い'
    # 星4 を押す
    await stars.nth(3).click(); await pg.wait_for_timeout(400)
    assert await pg.evaluate("ratingNow('hanako')") == 4, '星4 が付かない'
    # 星3 に付け直す(履歴に 4 が出る)
    await pg.locator('.rt-now button.rate-s').nth(2).click(); await pg.wait_for_timeout(400)
    assert await pg.evaluate("ratingNow('hanako')") == 3, '星3 に付け直せない'
    assert await pg.locator('.rt-hs[aria-label="星4"]').count() == 1, '履歴に星4 が出ない'
    assert await pg.locator('.rt-l b').inner_text() == '3', '今の評価のラベルが違う'
    # 同じ星3 を押すと外れる
    await pg.locator('.rt-now button.rate-s').nth(2).click(); await pg.wait_for_timeout(400)
    assert await pg.evaluate("ratingNow('hanako')") is None, '同じ星で外れない'
    assert await pg.locator('.rt-now', has_text='まだ評価していません').count() == 1, '外れた後の文言が無い'
    # 事実の一覧に 評価 が出ない
    assert await pg.locator('.prop .prop-k', has_text='評価').count() == 0, '事実の一覧に評価が出ている'
    # 星を付けたまま、評価セクションを画像に残す
    await pg.evaluate("rateSet('hanako', 4); rerender()"); await pg.wait_for_timeout(400)
    await pg.evaluate("document.querySelector('.rt-now').closest('section').scrollIntoView({block:'start'})")
    await pg.wait_for_timeout(300)
    await pg.screenshot(path=SHOT)


async def goal_flow(pg):
    await go(pg, '#/e/proj2')
    assert await pg.locator('.prop .prop-k', has_text='連続').count() == 1, '連続が出ない'
    txt = await pg.locator('.prop', has_text='連続').first.inner_text()
    assert '5' in txt and '日' in txt, f'連続の数が違う: {txt}'
    assert await pg.locator('.prop .prop-k', has_text='期日').count() == 1, '期日が出ない'
    # 期日・繰り返しの無い人物には目標を出さない
    await go(pg, '#/e/hanako')
    assert await pg.locator('.sec h2', has_text='目標').count() == 0, '人物に目標が出ている'


async def main():
    bad = []
    async with async_playwright() as p:
        for name, fn in [('評価 星4→3→外す', rating_flow), ('目標 期日・連続', goal_flow)]:
            b, pg, errs = await open_page(p)
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
