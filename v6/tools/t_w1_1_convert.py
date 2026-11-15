"""項目1: 確定済みの記録の「直す」→「確定して登録」「詳しく直す」で記録が二重にならない"""
import asyncio, sys, os
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from t_w1_common import open_page

async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        jid = await pg.evaluate("jotAdd('ランチ\\n¥980').id")
        await pg.evaluate("tentAll().forEach(x=>tentConfirm([{t:x.t,k:x.k}]))")
        rec = await pg.evaluate(f"recsOfJot('{jid}')[0]")
        ref = "{rec:{t:'%s',k:'%s'}}" % (rec['t'], rec['k'])
        n0 = await pg.evaluate(f"recsOfJot('{jid}').length")
        await pg.evaluate(f"openConv('{jid}', {ref}); cvSave(true)")
        n1 = await pg.evaluate(f"recsOfJot('{jid}').length")
        if n1 != n0: bad.append(f'確定して登録で件数が変わった {n0}→{n1}(二重)')
        await pg.evaluate(f"openConv('{jid}', {ref}); cvDetail()")
        n2 = await pg.evaluate(f"recsOfJot('{jid}').length")
        if n2 != n1: bad.append(f'詳しく直すで件数が変わった {n1}→{n2}(二重)')
        if await pg.evaluate(f"recsOfJot('{jid}').some(x=>x.row.tent)") : bad.append('未確定に戻った(詳しく直す後)')
        # ToDo・メモの確定済みの記録も、直すと件数が変わらず、タグ・期限が保たれる
        t_id = await pg.evaluate("jotAdd('牛乳を買う\\n#買い物 #急ぎ\\n明日').id")
        await pg.evaluate("tentAll().forEach(x=>tentConfirm([{t:x.t,k:x.k}]))")
        tr = await pg.evaluate(f"recsOfJot('{t_id}')[0]")
        tref = "{rec:{t:'%s',k:'%s'}}" % (tr['t'], tr['k'])
        m0 = await pg.evaluate(f"recsOfJot('{t_id}').length")
        await pg.evaluate(f"openConv('{t_id}', {tref}); cvSave(true)")
        m1 = await pg.evaluate(f"recsOfJot('{t_id}').length")
        if m1 != m0: bad.append(f'ToDo 直すで件数が変わった {m0}→{m1}')
        tags = await pg.evaluate(f"recsOfJot('{t_id}')[0].row.tags||[]")
        if sorted(tags) != ['急ぎ', '買い物']: bad.append(f'ToDo のタグが保たれない {tags}')
        # 種類を変える(ToDo→メモ)は、古い記録を消して1件にする
        await pg.evaluate(f"openConv('{t_id}', {tref}); cvType('memo'); cvSave(true)")
        m2 = await pg.evaluate(f"recsOfJot('{t_id}').length")
        if m2 != 1: bad.append(f'種類を変えた後 {m2} 件(1件のはず)')
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_1 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
