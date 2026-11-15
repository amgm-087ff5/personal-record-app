"""項目2: 確定(記録する)で 備考・タグ・終わりの日(date2) が消えない。ToDo・メモのタグも保たれる"""
import asyncio, sys, os
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from t_w1_common import open_page

async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        # 支払い: 備考(残りの行) とタグ(#外食) が残る
        await pg.evaluate("inSetDraft('ランチ\\n¥850 #外食\\n駅前')")
        await pg.evaluate("inRecord()")
        pay = await pg.evaluate("Object.values(DB.acts).find(a=>a.pay && payTotal(a)===850) || null")
        if not pay: bad.append('支払いが作られない')
        else:
            if '#外食' not in (pay.get('note') or ''): bad.append(f"支払い: タグが備考に無い {pay.get('note')!r}")
            if '駅前' not in (pay.get('note') or ''): bad.append(f"支払い: 備考が消えた {pay.get('note')!r}")
        # 予定: 終わりの日(date2) と 備考
        await pg.evaluate("inSetDraft('出張\\n明日 10:00\\n+1 12:00\\n新幹線で移動')")
        await pg.evaluate("inRecord()")
        plan = await pg.evaluate("Object.values(DB.acts).find(a=>a.title==='出張') || null")
        if not plan: bad.append('予定が作られない')
        else:
            if plan.get('date2') != '2026-11-13': bad.append(f"予定: 終わりの日が消えた {plan.get('date2')}")
            if '新幹線で移動' not in (plan.get('note') or ''): bad.append(f"予定: 備考が消えた {plan.get('note')!r}")
        # ToDo: タグが残る
        await pg.evaluate("inSetDraft('牛乳を買う\\n#買い物 #急ぎ\\n明日')")
        await pg.evaluate("inRecord()")
        task = await pg.evaluate("Object.values(DB.tasks).find(t=>t.t==='牛乳を買う') || null")
        if not task: bad.append('ToDoが作られない')
        elif sorted(task.get('tags') or []) != ['急ぎ', '買い物']: bad.append(f"ToDo: タグが消えた {task.get('tags')}")
        # メモ: タグが残る
        await pg.evaluate("inSetDraft('ピクミン3の感想\\n種類:メモ\\n#ゲーム')")
        await pg.evaluate("inRecord()")
        memo = await pg.evaluate("Object.values(DB.memos).find(m=>m.body.indexOf('ピクミン3の感想')===0) || null")
        if not memo: bad.append('メモが作られない(記録)')
        elif (memo.get('tags') or []) != ['ゲーム']: bad.append(f"メモ: タグが消えた {memo.get('tags')}")
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_2 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
