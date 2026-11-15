"""項目5: 別の日の終わりの時刻(「+N 時刻」の行)は、日付を付けて備考に入る。同じ日の終わりは終わりの欄に入る"""
import asyncio, sys, os
from playwright.async_api import async_playwright
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from t_w1_common import open_page

async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        base = "{date:'2026-11-11', time:'13:20'}"
        r = await pg.evaluate(f"readText('歯医者\\n明日 10:00\\n+1 10:00', {base}).cand")
        if not r or r.get('note') != '終わり 11/13 10:00': bad.append(f"別の日の終わり: 備考が日付つきでない {r and r.get('note')!r}")
        if not r or r.get('date2') != '2026-11-13': bad.append(f"別の日の終わり: 終わりの日 {r and r.get('date2')}")
        if r and r.get('end') is not None: bad.append('別の日の終わりを終わりの欄に入れた')
        # 同じ日の終わり: 終わりの欄へ(備考に入れない)
        r = await pg.evaluate(f"readText('会議\\n明日 10:00\\n+0 11:00', {base}).cand")
        if not r or r.get('end') != '11:00' or r.get('note'): bad.append(f'同じ日の終わり {r}')
        # 日付の行の直後の +N(始まりの日が無い): 始まりは今日から +1
        r = await pg.evaluate(f"readText('歯医者\\n+1 10:00', {base}).cand")
        if not r or r.get('date') != '2026-11-12' or r.get('start') != '10:00' or r.get('note'): bad.append(f'+1 の単独の行 {r}')
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_5 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
