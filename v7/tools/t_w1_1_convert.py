"""項目1: 確かめ済みの記録の「直す」→「確かめて登録」「詳しく直す」で記録が二重にならない(v7: 未確認 = unsure)"""
import asyncio, sys, os, json
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    from t_w1_common import open_page
except ImportError:   # 共通の土台が無い間の代用(BASE は v7/index.html。時計は 2026-11-11 13:20)
    URL = 'file://' + os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'index.html')) + '?now=2026-11-11T13:20'
    async def open_page(p):
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
        pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        await pg.goto(URL + '#/home'); await pg.wait_for_timeout(500)
        await pg.evaluate("localStorage.clear()"); await pg.goto(URL + '#/home'); await pg.wait_for_timeout(500)
        return b, pg, errs

async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        jid = await pg.evaluate("jotAdd('ランチ\\n¥980').id")
        await pg.evaluate("confirmAll()")
        rec = await pg.evaluate(f"recsOfJot('{jid}')[0]")
        ref = "{rec:{t:'%s',k:'%s'}}" % (rec['t'], rec['k'])
        n0 = await pg.evaluate(f"recsOfJot('{jid}').length")
        if n0 != 1: bad.append(f'前提: 記録が1件でない {n0}')
        await pg.evaluate(f"openConv('{jid}', {ref}); cvSave(true)")
        n1 = await pg.evaluate(f"recsOfJot('{jid}').length")
        if n1 != n0: bad.append(f'確かめて登録で件数が変わった {n0}→{n1}(二重)')
        await pg.evaluate(f"openConv('{jid}', {ref}); cvDetail()")
        n2 = await pg.evaluate(f"recsOfJot('{jid}').length")
        if n2 != n1: bad.append(f'詳しく直すで件数が変わった {n1}→{n2}(二重)')
        if await pg.evaluate(f"recsOfJot('{jid}').some(x=>isUnsure(x.row))"): bad.append('未確認に戻った(詳しく直す後)')
        # 金額を直すと同じ行が直る(id が変わらない)
        await pg.evaluate(f"openConv('{jid}', {ref}); cvIn('amt','1200'); cvSave(true)")
        r2 = await pg.evaluate(f"recsOfJot('{jid}').map(x=>({{k:x.k, amt:payTotal(x.row)}}))")
        if len(r2) != 1 or r2[0]['k'] != rec['k'] or r2[0]['amt'] != 1200: bad.append(f'金額の直しが同じ行に入らない {r2}')
        # 未確認にして保存 → 未確認になる(件数は同じ)
        await pg.evaluate(f"openConv('{jid}', {ref}); cvSave(false)")
        if not await pg.evaluate(f"recsOfJot('{jid}').length===1 && isUnsure(recsOfJot('{jid}')[0].row)"): bad.append('未確認のまま保存で未確認にならない')
        # ToDo・メモの確かめ済みの記録も、直すと件数が変わらず、タグ・期限が保たれる
        t_id = await pg.evaluate("jotAdd('牛乳を買う\\n#買い物 #急ぎ\\n明日').id")
        await pg.evaluate("confirmAll()")
        tr = await pg.evaluate(f"recsOfJot('{t_id}')[0]")
        tref = "{rec:{t:'%s',k:'%s'}}" % (tr['t'], tr['k'])
        m0 = await pg.evaluate(f"recsOfJot('{t_id}').length")
        await pg.evaluate(f"openConv('{t_id}', {tref}); cvSave(true)")
        m1 = await pg.evaluate(f"recsOfJot('{t_id}').length")
        if m1 != m0: bad.append(f'ToDo 直すで件数が変わった {m0}→{m1}')
        row = await pg.evaluate(f"recsOfJot('{t_id}')[0].row")
        if row.get('due') != '2026-11-12': bad.append(f"ToDo の期限が保たれない {row.get('due')}")
        if '#買い物' not in (row.get('memo') or '') or '#急ぎ' not in (row.get('memo') or ''): bad.append(f"ToDo のタグが保たれない {row.get('memo')!r}")
        # 種類を変える(ToDo→メモ)は、古い記録を消して1件にする
        await pg.evaluate(f"openConv('{t_id}', {tref}); cvType('memo'); cvSave(true)")
        rs = await pg.evaluate(f"recsOfJot('{t_id}').map(x=>x.t)")
        if rs != ['memos']: bad.append(f'種類を変えた後 {rs}(メモ1件のはず)')
        if await pg.evaluate(f"Object.values(DB.tasks).some(t=>t.t==='牛乳を買う')"): bad.append('種類を変えたのに ToDo が残った')
        # 日またぎの予定(date2 + 終わりの時刻が始まりより前)を直しても終わりが保たれる
        e_id = await pg.evaluate("jotAdd('出張\\n明日 22:00\\n+1 06:00').id")
        await pg.evaluate("confirmAll()")
        er = await pg.evaluate(f"recsOfJot('{e_id}')[0]")
        eref = "{rec:{t:'%s',k:'%s'}}" % (er['t'], er['k'])
        await pg.evaluate(f"openConv('{e_id}', {eref}); cvSave(true)")
        ev = await pg.evaluate(f"recsOfJot('{e_id}')")
        if len(ev) != 1 or ev[0]['row'].get('end') != '2026-11-13T06:00': bad.append(f"日またぎの終わりが保たれない {[x['row'] for x in ev]}")
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_1 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
