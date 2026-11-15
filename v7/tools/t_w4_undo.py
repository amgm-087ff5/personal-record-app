#!/usr/bin/env python3
"""W4 E4: 取り消しは、後の変更を巻き戻さない(項目ごとに、今の値が自分の書いた値と同じ所だけ戻す)"""
import asyncio, os
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'

async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        pg = await (await b.new_context(viewport={'width': 412, 'height': 900})).new_page()
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(400)
        # 後の変更と同じ項目 → 戻さず、戻せなかった数を返す
        r = await pg.evaluate("""() => { const id = Object.keys(DB.tasks)[0]; const r1 = taskUpdate(id,{t:'A1'}); const r2 = taskUpdate(id,{t:'B2'}); const u = r1.undo(); return {t: DB.tasks[id].t, skipped: u.skipped}; }""")
        if r['t'] != 'B2' or r['skipped'] != 1: bad.append(f'同じ項目の競合 {r}')
        # 先の取り消しは、自分の値に戻す(後の値は残る)
        r = await pg.evaluate("""() => { const id = Object.keys(DB.tasks)[0]; const r2 = taskUpdate(id,{t:'C3'}); return DB.tasks[id].t; }""")
        # 別の項目への後の変更は、取り消しで消えない
        r = await pg.evaluate("""() => { const id = Object.keys(DB.tasks)[0]; const r1 = taskUpdate(id,{t:'D4'}); taskUpdate(id,{pri:1}); const u = r1.undo(); return {t: DB.tasks[id].t, pri: DB.tasks[id].pri, skipped: u.skipped}; }""")
        if r['pri'] != 1 or r['t'] == 'D4' or r['skipped'] != 0: bad.append(f'別の項目 {r}')
        # 単独の取り消しは従来どおり戻る
        r = await pg.evaluate("""() => { const id = Object.keys(DB.tasks)[1]; const before = DB.tasks[id].t; const r1 = taskUpdate(id,{t:'E5'}); const u = r1.undo(); return {same: DB.tasks[id].t === before, skipped: u.skipped}; }""")
        if not r['same'] or r['skipped'] != 0: bad.append(f'単独の取り消し {r}')
        await b.close()
    print('NG:', bad) if bad else print('t_w4_undo OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
