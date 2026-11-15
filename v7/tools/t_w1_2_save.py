"""項目2: 確かめた記録(記録する)で 備考・タグ・終わりの日 が消えない。ToDo・メモのタグも保たれる(v7: タグは備考の #行・終わりの日は end の TS)"""
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
        await pg.evaluate("openInbox()"); await pg.wait_for_timeout(300)
        await pg.evaluate("inSetDraft('ランチ\\n¥850 #外食\\n駅前')")
        await pg.evaluate("inRecord()")
        pay = await pg.evaluate("Object.values(DB.acts).find(a=>a.pay && payTotal(a)===850) || null")
        if not pay: bad.append('支払いが作られない')
        else:
            if '#外食' not in (pay.get('note') or ''): bad.append(f"支払い: タグが備考に無い {pay.get('note')!r}")
            if '駅前' not in (pay.get('note') or ''): bad.append(f"支払い: 備考が消えた {pay.get('note')!r}")
        await pg.evaluate("inSetDraft('出張\\n明日 10:00\\n+1 12:00\\n新幹線で移動')")
        await pg.evaluate("inRecord()")
        plan = await pg.evaluate("Object.values(DB.acts).find(a=>a.title==='出張') || null")
        if not plan: bad.append('予定が作られない')
        else:
            if plan.get('end') != '2026-11-13T12:00': bad.append(f"予定: 終わり(日・時刻)が消えた {plan.get('end')}")
            if plan.get('start') != '2026-11-12T10:00': bad.append(f"予定: 始まり {plan.get('start')}")
            if '新幹線で移動' not in (plan.get('note') or ''): bad.append(f"予定: 備考が消えた {plan.get('note')!r}")
        await pg.evaluate("inSetDraft('牛乳を買う\\n#買い物 #急ぎ\\n明日')")
        await pg.evaluate("inRecord()")
        task = await pg.evaluate("Object.values(DB.tasks).find(t=>t.t==='牛乳を買う') || null")
        if not task: bad.append('ToDoが作られない')
        elif '#買い物' not in (task.get('memo') or '') or '#急ぎ' not in (task.get('memo') or ''): bad.append(f"ToDo: タグが消えた {task.get('memo')!r}")
        elif task.get('due') != '2026-11-12': bad.append(f"ToDo: 期限 {task.get('due')}")
        await pg.evaluate("inSetDraft('ピクミン3の感想\\n種類:メモ\\n#ゲーム')")
        await pg.evaluate("inRecord()")
        memo = await pg.evaluate("Object.values(DB.memos).find(m=>m.body.indexOf('ピクミン3の感想')===0) || null")
        if not memo: bad.append('メモが作られない(記録)')
        elif '#ゲーム' not in memo.get('body', ''): bad.append(f"メモ: タグが消えた {memo.get('body')!r}")
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_2 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
