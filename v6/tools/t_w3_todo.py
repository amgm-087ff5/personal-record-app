#!/usr/bin/env python3
"""W3 テスト: ToDo C-R2(完了の「もっと見る」)・C-R4(リスト追加の重複と空)・C-R5(繰り返しの次回)・C-R6(期限切れの件数)
使い方: python3 v6/tools/t_w3_todo.py   (W3_ROOT=/tmp/w3_before/v6 で修正前の再現も可)"""
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


async def c_r2_done_more(pg):
    await pg.evaluate("""()=>{const k=Object.keys(DB.lists)[0];
      for(let i=1;i<=25;i++){ DB.tasks['w3d'+i]={t:'完了テスト'+i,list:k,done:'2026-10-'+String(i).padStart(2,'0')}; }
      save(); }""")
    await go(pg, '#/todo')
    await pg.locator('.td-dn').click(); await pg.wait_for_timeout(400)
    total = await pg.evaluate("Object.values(DB.tasks).filter(t=>t.done).length")
    assert total > 20, 'テストの前提: 完了が20件を超えていない'
    n = await pg.locator('.td-dr').count()
    assert n == 20, f'完了の初期表示が20件でない: {n}'
    more = pg.locator('button', has_text='もっと見る')
    assert await more.count() == 1, '「もっと見る」が無い'
    # 下のナビに隠れないよう、ボタンを画面の中ほどへ送る(スクロールする祖先を探す)
    await more.evaluate("""e=>{const r=e.getBoundingClientRect(), t=innerHeight*0.45; let p=e.parentElement;
      while(p){ if(p.scrollHeight>p.clientHeight+2 && /auto|scroll/.test(getComputedStyle(p).overflowY)){ p.scrollTop+=r.top-t; break; } p=p.parentElement; }
      window.scrollBy(0, r.top-t); }"""); await pg.wait_for_timeout(300)
    await more.click(); await pg.wait_for_timeout(400)
    n = await pg.locator('.td-dr').count()
    assert n == total, f'もっと見るで全部見えない: {n} / {total}'


async def c_r4_list_dup_empty(pg):
    name = await pg.evaluate("DB.lists[Object.keys(DB.lists)[0]].n")
    cnt = await pg.evaluate("Object.keys(DB.lists).length")
    await go(pg, '#/todo')
    await pg.evaluate("tdLists()"); await pg.wait_for_timeout(400)
    await pg.fill('#tdl-in', name)
    await pg.locator('.sheet button', has_text='足す').last.click(); await pg.wait_for_timeout(300)
    body = await pg.inner_text('body')
    assert '同じ名前のリストがあります' in body, '同名のリストを断る表示が無い'
    assert await pg.evaluate("Object.keys(DB.lists).length") == cnt, '同名のリストが増えた'
    await pg.fill('#tdl-in', '   ')
    await pg.locator('.sheet button', has_text='足す').last.click(); await pg.wait_for_timeout(300)
    body = await pg.inner_text('body')
    assert 'リストの名前を入れてください' in body, '空のリストを断る表示が無い'
    assert await pg.evaluate("Object.keys(DB.lists).length") == cnt, '空のリストが増えた'


async def c_r5_next_repeat(pg):
    await pg.evaluate("""()=>{const k=Object.keys(DB.lists)[0]; DB.tasks['w3r']={t:'毎週の掃除',list:k,due:'2026-11-14',rep:{u:'week'}}; save();}""")
    await go(pg, '#/task/w3r')
    txt = await pg.inner_text('.s-task')
    assert '次回 11/21(土)' in txt, f'繰り返しの次回が出ない'


async def c_r6_late_count(pg):
    await pg.evaluate("""()=>{const k=Object.keys(DB.lists)[0]; DB.tasks['w3late']={t:'期限切れテスト',list:k,due:'2026-11-01'}; save();}""")
    await go(pg, '#/todo')
    sm = pg.locator('.td-sum')
    assert await sm.count() == 1, '一覧の要約が無い'
    txt = await sm.inner_text()
    want = await pg.evaluate("Object.values(DB.tasks).filter(t=>!t.done && t.due && t.due < TODAY).length")
    assert f'期限切れ {want}' in txt and want >= 1, f'期限切れの件数が出ない: {txt!r} (期待 {want})'


CASES = [('C-R2 完了のもっと見る', c_r2_done_more), ('C-R4 リスト追加の重複と空', c_r4_list_dup_empty),
         ('C-R5 繰り返しの次回', c_r5_next_repeat), ('C-R6 期限切れの件数', c_r6_late_count)]


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
