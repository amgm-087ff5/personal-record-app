#!/usr/bin/env python3
"""W3 検査(v7): エンティティ C-R1(関係を直す)・C-R3(種類の子の種類)
使い方: python3 v7/tools/t_w3_ents.py"""
import asyncio, os, json, sys
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
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
    ids = await pg.evaluate("Object.keys(DB.ents).filter(k=>k!=='me'&&!DB.ents[k].stub&&!DB.ents[k].types.includes('method')).slice(0,3)")
    rid = await pg.evaluate("(ids)=>{ DB.rels={}; return relAdd({a:ids[0],b:ids[1],role:'友人',from:'2020-04'}).id }", ids)
    await go(pg, f'#/e/{ids[0]}')
    row = pg.locator('.prop').filter(has_text='友人')
    await row.locator('.en-x').click(); await pg.wait_for_timeout(400)
    assert await pg.locator('.sheet .row', has_text='直す').count() == 1, '関係の操作に「直す」が無い'
    await pg.locator('.sheet .row', has_text='直す').click(); await pg.wait_for_timeout(500)
    assert (await sheet_title(pg)) == '関係を直す', 'シートの題が違う'
    assert (await pg.input_value('#enr-role')) == '友人' and (await pg.input_value('#enr-s')) == '2020-04', '今の値が入っていない'
    # 名前だけ直す(始まりは残る)
    await pg.fill('#enr-role', '親友'); await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('(id)=>DB.rels[id]', rid)
    assert r['role'] == '親友' and not r.get('inv') and r.get('from') == '2020-04' and r['a'] == ids[0] and r['b'] == ids[1], f'名前だけ直す: {r}'
    # 相手を変える
    await pg.evaluate(f"enRelEdit({json.dumps(ids[0])},{json.dumps(rid)})"); await pg.wait_for_timeout(400)
    await pg.evaluate(f"ENR.other={json.dumps(ids[2])}; setSheetBody(enrBody())"); await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('(id)=>DB.rels[id]', rid)
    assert r['b'] == ids[2] and r['a'] == ids[0], f'相手を直す: {r}'
    # 一方向に変えると相手から見た呼び名が要る
    await pg.evaluate(f"enRelEdit({json.dumps(ids[0])},{json.dumps(rid)})"); await pg.wait_for_timeout(400)
    await pg.locator('.sheet').last.get_by_role('tab', name='一方向').click(); await pg.wait_for_timeout(300)
    assert await pg.locator('[data-pri]').last.is_disabled(), '呼び名が空でも保存できる'
    await pg.fill('#enr-inv', '相手の呼び名'); await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('(id)=>DB.rels[id]', rid)
    assert r.get('inv') == '相手の呼び名' and r['role'] == '親友', f'一方向に直す: {r}'
    # 相手側から開いても、見ている側の呼び名で直せる(a/b を入れ替えない)
    await pg.evaluate(f"enRelEdit({json.dumps(ids[2])},{json.dumps(rid)})"); await pg.wait_for_timeout(400)
    assert (await pg.input_value('#enr-role')) == '相手の呼び名' and (await pg.input_value('#enr-inv')) == '親友', '相手側から見た値が逆'
    await pg.fill('#enr-role', '部下'); await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('(id)=>DB.rels[id]', rid)
    assert r['a'] == ids[0] and r['b'] == ids[2] and r['inv'] == '部下' and r['role'] == '親友', f'相手側から直す: {r}'
    # お互いに戻すと inv が消え、終わりの月は残る
    await pg.evaluate(f"relUpdate({json.dumps(rid)},{{to:'2024-03'}})")
    await pg.evaluate(f"enRelEdit({json.dumps(ids[0])},{json.dumps(rid)})"); await pg.wait_for_timeout(400)
    await pg.locator('.sheet').last.get_by_role('tab', name='お互い').click(); await pg.wait_for_timeout(300)
    await pg.evaluate("document.querySelector('#toasts').innerHTML=''; _toastQ.length=0")
    await pg.locator('[data-pri]').last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('(id)=>DB.rels[id]', rid)
    assert 'inv' not in r and r.get('to') == '2024-03', f'お互いに戻す: {r}'
    # 取り消しで戻る
    btn = pg.locator('[data-undo]')
    assert await btn.count() >= 1, '取り消しのトーストが無い'
    await btn.last.click(); await pg.wait_for_timeout(400)
    r = await pg.evaluate('(id)=>DB.rels[id]', rid)
    assert r.get('inv') == '部下', f'取り消しで戻らない: {r}'


async def c_r3_child_type(pg):
    parent = await pg.evaluate("(()=>{const k=Object.keys(DB.types)[0]; DB.types['w3child']={n:'子テスト',props:[],p:k}; save(); return k})()")
    await go(pg, '#/ents')
    await pg.evaluate(f"enTypeOpen({json.dumps(parent)})"); await pg.wait_for_timeout(400)
    assert await pg.locator('.sheet .sec h2', has_text='子の種類').count() == 1, '「子の種類」の欄が無い'
    await pg.locator('.sheet .row', has_text='子テスト').first.click(); await pg.wait_for_timeout(600)
    assert (await pg.locator('#ent-n').input_value()) == '子テスト', '子の種類を開けない'
    assert await pg.locator('.sheet').count() == 1, 'シートが重なっている'
    # 編集中に子を押すと確認が出て、続けるなら開かない
    await pg.evaluate(f"closeAllSheets(); enTypeOpen({json.dumps(parent)})"); await pg.wait_for_timeout(500)
    await pg.fill('#ent-n', '書きかけ')
    await pg.locator('.sheet .row', has_text='子テスト').first.click(); await pg.wait_for_timeout(400)
    assert await pg.locator('.sheet-ask').last.is_visible(), '書きかけの確認が出ない'
    await pg.locator('.sheet-ask [data-keep]').last.click(); await pg.wait_for_timeout(300)
    assert (await pg.locator('#ent-n').input_value()) == '書きかけ', '続けるで中身が消えた'
    await pg.locator('.sheet .row', has_text='子テスト').first.click(); await pg.wait_for_timeout(300)
    await pg.locator('.sheet-ask [data-drop]').last.click(); await pg.wait_for_timeout(700)
    assert (await pg.locator('#ent-n').input_value()) == '子テスト', '破棄したあと子が開かない'


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
