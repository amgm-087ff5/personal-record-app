#!/usr/bin/env python3
"""W2 の家計簿テスト: B3(内訳の軸切替: カテゴリ / 固定・変動 / 特別感。各軸で金額と割合) B4(家計簿ヘッダの「レシートから読み取る」)
使い方: python3 tools/t_w2_ledger.py  (失敗があれば NG を出して終了コード1)"""
import os, asyncio
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'

async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(viewport={'width': 412, 'height': 900})
        pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        await pg.goto(BASE + '#/ledger'); await pg.wait_for_timeout(600)
        async def clk(js):
            ok = await pg.evaluate(f"(() => {{ const e = {js}; if (!e) return false; e.click(); return true; }})()")
            await pg.wait_for_timeout(300); return ok

        # B4: ヘッダのレシートボタン
        has_cam = await pg.evaluate("!!document.querySelector('.top-a [aria-label=\"レシートから読み取る\"]')")
        if not has_cam: bad.append('B4 家計簿ヘッダに「レシートから読み取る」が無い')

        # B3: 軸の切替。初期はカテゴリ(大分類の木)
        axes = await pg.evaluate("[...document.querySelectorAll('#main .seg button')].map(b => b.textContent.trim())")
        if not {'カテゴリ', '固定・変動', '特別感'} <= set(axes): bad.append(f'B3 軸の切替が無い {axes}')
        if not await clk("[...document.querySelectorAll('#main .seg button')].find(b => b.textContent.trim() === '固定・変動')"):
            bad.append('B3 固定・変動の軸ボタンが無い')
        fx = await pg.evaluate("[...document.querySelectorAll('#main .lg-n .row-t')].map(e => e.textContent.trim())")
        fx_pct = await pg.evaluate("[...document.querySelectorAll('#main .lg-n .lg-pc')].map(e => e.textContent.trim())")
        if '固定' not in fx or '変動' not in fx: bad.append(f'B3 固定・変動の行が無い {fx}')
        if not fx_pct or not all(t.endswith('%') or t == '<1%' for t in fx_pct): bad.append(f'B3 固定・変動の割合 {fx_pct}')
        # 行を押すと、下の一覧が絞られる
        await clk("[...document.querySelectorAll('#main .lg-n')].find(e => e.querySelector('.row-t') && e.querySelector('.row-t').textContent.trim() === '固定')")
        chip_on = await pg.evaluate("[...document.querySelectorAll('#main .chip[aria-pressed=\"true\"]')].map(e => e.textContent.trim())")
        if not any(c.startswith('固定費') for c in chip_on): bad.append(f'B3 固定の行で一覧が絞られない {chip_on}')

        await clk("[...document.querySelectorAll('#main .seg button')].find(b => b.textContent.trim() === '特別感')")
        sp = await pg.evaluate("[...document.querySelectorAll('#main .lg-n .row-t')].map(e => e.textContent.trim())")
        if '日常' not in sp or '非日常' not in sp: bad.append(f'B3 特別感の行が無い {sp}')
        sp_amt = await pg.evaluate("[...document.querySelectorAll('#main .lg-n .amt')].length")
        if sp_amt < 2: bad.append('B3 特別感の金額が出ない')

        # カテゴリの木に戻る
        await clk("[...document.querySelectorAll('#main .seg button')].find(b => b.textContent.trim() === 'カテゴリ')")
        if await pg.evaluate("document.querySelectorAll('#main .lg-n').length") < 1: bad.append('B3 カテゴリの木に戻らない')
        if errs: bad.append(f'JSエラー {errs[:3]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w2_ledger OK')
    raise SystemExit(1 if bad else 0)

asyncio.run(main())
