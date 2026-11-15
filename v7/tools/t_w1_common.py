"""W1 テストの共通: 起動して、保存データを空にした状態から始める(関数は page.evaluate で直接呼ぶ)"""
import os
from playwright.async_api import async_playwright
URL = 'file://' + os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'index.html')) + '?now=2026-11-11T13:20'

async def open_page(p):
    b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
    ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
    pg = await ctx.new_page(); errs = []
    pg.on('pageerror', lambda e: errs.append(str(e)))
    await pg.goto(URL + '#/home'); await pg.wait_for_timeout(500)
    await pg.evaluate("localStorage.clear()"); await pg.goto(URL + '#/home'); await pg.wait_for_timeout(500)
    return b, pg, errs
