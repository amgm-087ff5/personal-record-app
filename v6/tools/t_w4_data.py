#!/usr/bin/env python3
"""W4: E1(欠けた表・取り込み・描画の失敗で白い画面を残さない) / E2(壊れた % の URL) / E3(壊れた保存データの退避)"""
import asyncio, os, json
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'
CTX = dict(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)

async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(**CTX); pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(500)

        # E1: 欠けた表だけの取り込みは通り、描ける
        r = await pg.evaluate("() => importJSON(JSON.stringify({v:6,acts:{},ents:{},cats:{}}))")
        if not r['ok']: bad.append('E1 欠けた表の取り込みを断った')
        await pg.evaluate("() => render()"); await pg.wait_for_timeout(300)
        st = await pg.evaluate("() => ({ready: document.documentElement.classList.contains('ready'), main: document.querySelector('#main').innerText.length, fb: !!document.querySelector('#main [role=alert]')})")
        if not st['ready'] or st['fb'] or st['main'] < 40: bad.append(f'E1 取り込み後の描画 {st}')
        # E1: 型の違う表は断る
        for bad_json in ['{"v":6,"acts":[],"ents":{},"cats":{}}', '{"v":6,"acts":{},"tasks":"x"}', '{"v":6,"rels":{}}']:
            r = await pg.evaluate("(s) => importJSON(s)", bad_json)
            if r['ok']: bad.append(f'E1 型違いを受けた {bad_json}')
        # E1: 描画が例外を出しても、白くならず読み込み失敗の面と設定への手段が出る
        await pg.evaluate("() => { window.__rn = renderNav; renderNav = () => { throw new Error('boom'); }; render(); }")
        st = await pg.evaluate("() => ({vis: getComputedStyle(document.body).visibility, ready: document.documentElement.classList.contains('ready'), txt: document.querySelector('#main').innerText, btn: [...document.querySelectorAll('#main button')].map(b=>b.textContent)})")
        if st['vis'] != 'visible' or not st['ready'] or '読み込みに失敗しました' not in st['txt'] or '設定を開く' not in st['btn']: bad.append(f'E1 失敗の面 {st}')
        await pg.evaluate("() => { renderNav = window.__rn; }")

        # E3: 壊れた保存データは退避してから見本で始め、お知らせを1回出す
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(300)
        await pg.evaluate("() => localStorage.setItem('prmock-v6', '{broken')")
        await pg.reload(); await pg.wait_for_timeout(600)
        k = await pg.evaluate("() => ({kept: localStorage.getItem('prmock-v6-broken'), toast: [...document.querySelectorAll('#toasts .toast')].map(t=>t.textContent).join('|'), now: localStorage.getItem('prmock-v6')})")
        if k['kept'] != '{broken': bad.append(f'E3 退避されていない {k["kept"]!r}')
        if '読めなかった' not in k['toast']: bad.append(f'E3 お知らせが出ていない {k["toast"]!r}')
        if not k['now'] or json.loads(k['now']).get('v') != 6: bad.append('E3 見本で保存されていない')

        # E2: 壊れた % の並びで落ちず、本文が出る(見つからない扱い)
        for h, want in [('#/a/%', '見つかりません'), ('#/%', None), ('#/e/%E3%81', None)]:
            errs.clear()
            await pg.goto(BASE + h); await pg.wait_for_timeout(350)
            txt = await pg.evaluate("() => document.querySelector('#main').innerText + document.querySelector('#side').innerText")
            if errs: bad.append(f'E2 {h} で例外 {errs[:1]}')
            if not txt.strip(): bad.append(f'E2 {h} で本文が空')
            if want and want not in txt: bad.append(f'E2 {h} の表示 {txt[:30]!r}')
        await b.close()
    print('NG:', bad) if bad else print('t_w4_data OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
