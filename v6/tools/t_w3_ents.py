#!/usr/bin/env python3
"""W3 テスト: エンティティ画面 C-R1(関係を直す)・C-R3(種類の子の種類)
使い方: python3 v6/tools/t_w3_ents.py   (W3_ROOT=/tmp/w3_before/v6 で修正前の再現も可)"""
import asyncio, os, json, sys
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


async def sheet_title(pg):
    return await pg.locator('.sheet h2').last.inner_text()


async def c_r1_edit_relation(pg):
    ids = await pg.evaluate("Object.keys(DB.ents).slice(0,2)")
    await pg.evaluate("(ids)=>{ DB.rels=[{a:ids[0],b:ids[1],role:'友人',sym:1,since:'2020-04'}]; save(); }", ids)
    await go(pg, f'#/e/{ids[0]}')
    row = pg.locator('.prop').filter(has_text='友人')
    n = await row.locator('button', has_text='直す').count()
    assert n == 1, f'関係の行に「直す」が無い(数={n})'
    await row.locator('button', has_text='直す').click(); await pg.wait_for_timeout(400)
    assert (await sheet_title(pg)) == '関係を直す', 'シートの題が違う'
    await pg.fill('#enr-role', '親友'); await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('DB.rels[0]')
    assert r['role'] == '親友' and r.get('sym') == 1 and r.get('since') == '2020-04', f'名前だけ直す: {r}'
    # 一方向に変えると相手から見た呼び名が要る
    await go(pg, f'#/e/{ids[0]}')
    await pg.locator('.prop').filter(has_text='親友').locator('button', has_text='直す').click(); await pg.wait_for_timeout(400)
    await pg.locator('.sheet').last.get_by_role('tab', name='一方向').click(); await pg.wait_for_timeout(300)
    await pg.fill('#enr-inv', '相手の呼び名'); await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('DB.rels[0]')
    assert not r.get('sym') and r.get('inv') == '相手の呼び名' and r['role'] == '親友', f'一方向に直す: {r}'
    # 取り消しで元に戻る
    btn = pg.locator('[data-undo]')
    assert await btn.count() >= 1, '取り消しのトーストが無い'
    await btn.last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('DB.rels[0]')
    assert r.get('sym') == 1 and r.get('role') == '親友' and not r.get('inv'), f'取り消しで戻らない: {r}'


async def c_r3_child_type(pg):
    parent = await pg.evaluate("(()=>{const k=Object.keys(DB.types)[0]; DB.types['w3child']={n:'子テスト',props:[],p:k}; save(); return k})()")
    await go(pg, '#/ents')
    await pg.evaluate(f"enTypeOpen({json.dumps(parent)})"); await pg.wait_for_timeout(400)
    assert await pg.locator('.sheet .sec h2', has_text='子の種類').count() == 1, '「子の種類」の欄が無い'
    await pg.locator('.sheet .row', has_text='子テスト').first.click(); await pg.wait_for_timeout(500)
    assert (await pg.locator('#ent-n').input_value()) == '子テスト', '子の種類を開けない'


CASES = [('C-R1 関係を直す', c_r1_edit_relation), ('C-R3 子の種類を開く', c_r3_child_type)]


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
