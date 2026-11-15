"""項目5: 別の日の終わりの時刻(「+N 時刻」の行)は終わりの日と終わりの時刻に分けて入る(v7)。同じ日の終わりは終わりの欄、始まり以前は日付つきで備考"""
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
        base = "{date:'2026-11-11', time:'13:20'}"
        # 別の日の終わり(+N 時刻): v7 は終わりを TS で持つので、終わりの日 date2 + 終わりの時刻 end に分ける(備考に退避しない)
        r = await pg.evaluate(f"readText('歯医者\\n明日 10:00\\n+1 10:00', {base}).cand")
        if not r or r.get('date2') != '2026-11-13' or r.get('end') != '10:00' or r.get('note'): bad.append(f'別の日の終わり {r}')
        # 始まりより前の時刻でも別の日なら終わり(夜行)
        r = await pg.evaluate(f"readText('夜行\\n明日 22:00\\n+1 06:00', {base}).cand")
        if not r or r.get('date2') != '2026-11-13' or r.get('end') != '06:00' or r.get('note'): bad.append(f'日またぎ(終わりが前の時刻) {r}')
        # 同じ日の終わり: 終わりの欄へ(備考に入れない)
        r = await pg.evaluate(f"readText('会議\\n明日 10:00\\n+0 11:00', {base}).cand")
        if not r or r.get('end') != '11:00' or r.get('note') or r.get('date2'): bad.append(f'同じ日の終わり {r}')
        # 同じ日で始まり以前の終わり: 終わりの欄に入れず備考へ
        r = await pg.evaluate(f"readText('会議\\n明日 10:00\\n+0 09:00', {base}).cand")
        if not r or r.get('end') or r.get('note') != '終わり 09:00': bad.append(f'同じ日の終わりが前 {r}')
        # 日付の行の直後の +N(始まりの日が無い): 始まりは今日から +1
        r = await pg.evaluate(f"readText('歯医者\\n+1 10:00', {base}).cand")
        if not r or r.get('date') != '2026-11-12' or r.get('start') != '10:00' or r.get('note'): bad.append(f'+1 の単独の行 {r}')
        # 記録にしても日またぎが保たれる(jotAdd → 未確認の記録の start/end)
        jid = await pg.evaluate("jotAdd('夜行\\n明日 22:00\\n+1 06:00').id")
        a = await pg.evaluate(f"recsOfJot('{jid}')[0].row")
        if a.get('start') != '2026-11-12T22:00' or a.get('end') != '2026-11-13T06:00': bad.append(f'記録の日またぎ {a}')
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_5 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
