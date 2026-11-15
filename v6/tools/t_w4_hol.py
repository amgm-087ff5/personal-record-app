#!/usr/bin/env python3
"""W4 E6: 祝日は規則で計算され、先の日付にも出る(振替休日・国民の休日・春分秋分・ハッピーマンデー)"""
import asyncio, os
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'
# 内閣府の公表値(既知の日付)
KNOWN = {'2027-01-11': '成人の日', '2027-03-21': '春分の日', '2027-05-03': '憲法記念日', '2027-05-05': 'こどもの日', '2027-07-19': '海の日',
         '2027-08-11': '山の日', '2027-09-20': '敬老の日', '2027-09-23': '秋分の日', '2027-10-11': 'スポーツの日', '2027-11-23': '勤労感謝の日',
         '2030-05-06': '振替休日', '2026-03-20': '春分の日', '2026-09-23': '秋分の日', '2026-10-12': 'スポーツの日'}

async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        pg = await (await b.new_context(viewport={'width': 412, 'height': 900})).new_page()
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(400)
        got = await pg.evaluate("(ks) => Object.fromEntries(ks.map(d => [d, holidayName(d)]))", list(KNOWN))
        for d, n in KNOWN.items():
            if got[d] != n: bad.append(f'{d} {got[d]!r} != {n}')
        # 表示にも出る(保存していない先の日)
        r = await pg.evaluate("() => actsOn('2027-05-03').filter(([, a]) => a.hol).map(([, a]) => a.title)")
        if r != ['憲法記念日']: bad.append(f'actsOn 2027-05-03 {r}')
        # 国民の休日: 祝日でない日で前後が祝日(2030-2035 を走査して、あれば前後が祝日であることを確かめる)
        ok = await pg.evaluate("""() => { const out = []; for (let y = 2026; y <= 2035; y++) for (const [d, n] of Object.entries(holidaysOf(y))) if (n === '国民の休日') out.push(d); return out; }""")
        for d in ok:
            prev = await pg.evaluate("(d) => [holidayName(dAdd(d,-1)) !== '', holidayName(dAdd(d,1)) !== '']", d)
            if not all(prev): bad.append(f'国民の休日 {d} の前後が祝日でない')
        # 既存の保存データの hol 行も、そのまま出る
        await pg.evaluate("() => { DB.acts['hol2027-05-03'] = {kind:'holiday', title:'テスト祝日', date:'2027-05-03', allDay:1, state:'plan', hol:1, roles:[]}; }")
        n = await pg.evaluate("() => actsOn('2027-05-03').filter(([, a]) => a.hol).length")
        if n != 1: bad.append(f'hol 行の重複または欠落 {n}')
        await b.close()
    print('NG:', bad) if bad else print('t_w4_hol OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
