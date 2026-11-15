#!/usr/bin/env python3
"""W2 の支払い系テスト: B1(写真の重複) B2(撮影) B5(払った人) B6(n/N枚) B7(一覧の重複) B8(作成中に店を登録) A1(買い物ToDo案内) A6(関わった) A5(金額を渡して開く)
使い方: python3 tools/t_w2_pay.py  (失敗があれば NG を出して終了コード1)"""
import os, asyncio
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'
TMP = '/tmp/w2_v7'

def mk_images():
    """同じ写真(a と a の複製)と別の写真(b, c)。中身が違えばハッシュも違う。"""
    os.makedirs(TMP, exist_ok=True)
    for n, body in [('rc_a', b'A' * 300), ('rc_b', b'B' * 301), ('rc_c', b'C' * 302)]:
        open(f'{TMP}/{n}.jpg', 'wb').write(b'\xff\xd8\xff\xe0' + body + b'\xff\xd9')
    open(f'{TMP}/rc_a_copy.jpg', 'wb').write(open(f'{TMP}/rc_a.jpg', 'rb').read())

async def main():
    mk_images(); bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(viewport={'width': 412, 'height': 900})
        pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        await pg.goto(BASE + '#/ledger'); await pg.wait_for_timeout(600)

        # A5: 金額を渡して開く
        await pg.evaluate('openPayEditor({amount: 1234})'); await pg.wait_for_timeout(300)
        v = await pg.evaluate("(document.querySelector('#py-amt')||{}).value")
        if v != '1234': bad.append(f'A5 金額の初期値 {v!r}')
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(200)

        # B5: 払った人は常に出る。候補は全員(自分含む)
        await pg.evaluate('openPayEditor({})'); await pg.wait_for_timeout(300)
        has_payer = await pg.evaluate("[...document.querySelectorAll('#sheets .field-l')].some(e => e.textContent.trim() === '払った人')")
        if not has_payer: bad.append('B5 払った人が出ない(一緒の人が0人の時)')
        cand = await pg.evaluate("[...document.querySelectorAll('#sheets .field')].find(f => f.querySelector('.field-l') && f.querySelector('.field-l').textContent.trim() === '払った人') ? [...document.querySelectorAll('#sheets .field')].find(f => f.querySelector('.field-l').textContent.trim() === '払った人').querySelectorAll('.chip').length : 0")
        n_people = await pg.evaluate("entsOfType('person').filter(e => e !== 'me').length")
        if cand < n_people + 1: bad.append(f'B5 払った人の候補 {cand} < 全員{n_people}+自分')
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(200)

        # B2: 撮影する(カメラ直撮り)
        await pg.evaluate('openReceipt()'); await pg.wait_for_timeout(300)
        cap = await pg.evaluate("document.querySelectorAll('#sheets input[capture=\"environment\"]').length")
        if cap < 1: bad.append('B2 撮影する(capture=environment)が無い')
        pick_btn = await pg.evaluate("[...document.querySelectorAll('#sheets .py-file')].map(e => e.textContent.trim())")
        if not any('撮影' in t for t in pick_btn): bad.append(f'B2 撮影ボタンの文言 {pick_btn}')
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(200)

        # B6/B7/B1: 複数枚。a と a の複製(同じ写真)と b
        await pg.evaluate('openReceipt()'); await pg.wait_for_timeout(300)
        await pg.locator('#sheets input[type=file][multiple]').set_input_files([f'{TMP}/rc_a.jpg', f'{TMP}/rc_b.jpg', f'{TMP}/rc_a_copy.jpg'])
        await pg.wait_for_timeout(2500)
        prog = await pg.evaluate("(document.querySelector('#sheets .py-bts-sum')||{}).textContent || ''")
        if '3枚 読み取り済み' not in prog and '3/3枚 読み取り済み' not in prog: bad.append('B6 n/N枚 読み取り済みの表示が無い')

        # 1件を確かめる(一覧の 1 件目を開いて確かめた)
        await pg.evaluate('pyBtOpen(0)'); await pg.wait_for_timeout(300)
        await pg.evaluate("[...document.querySelectorAll('#sheets button')].find(b => b.textContent.trim() === 'この内容で確かめた').click()")
        await pg.wait_for_timeout(400)
        row2 = await pg.evaluate("(document.querySelectorAll('#sheets .py-bt')[2]||{}).textContent || ''")
        if '同じ写真が1枚目' not in row2: bad.append(f'B1/B7 一覧内の同じ写真(1枚目と同じ)が出ない: {row2[:60]}')
        keep_btn = await pg.evaluate("[...document.querySelectorAll('#sheets .py-bt button')].some(b => b.textContent.includes('別の支払いとして残す'))")
        if not keep_btn: bad.append('B7 「別の支払いとして残す」が無い')
        await pg.wait_for_timeout(200)
        # 「別の支払いとして残す」を押すと、その行が入る(3件)
        await pg.evaluate("[...document.querySelectorAll('#sheets .py-bt button')].find(b => b.textContent.includes('別の支払いとして残す')).click()")
        await pg.wait_for_timeout(200)
        n_keep = await pg.evaluate("[...document.querySelectorAll('#sheets button')].map(b => b.textContent.trim()).filter(t => t.includes('件の支払いを保存'))")
        if not n_keep or not n_keep[0].startswith('3件'): bad.append(f'B7 残すを押しても件数が変わらない {n_keep}')
        # 一覧の保存: 重複の行は既定で入らない(件数は 2 件 = a と b)
        save_lbl = await pg.evaluate("[...document.querySelectorAll('#sheets button')].map(b => b.textContent.trim()).filter(t => t.includes('件の支払いを保存'))")
        if not save_lbl or not save_lbl[0].startswith('3件'): bad.append(f'B7 残した後の保存件数 {save_lbl}')
        # 保存して、同じ写真をもう一度入れる → 保存済みとの重複
        await pg.evaluate("[...document.querySelectorAll('#sheets button')].find(b => b.textContent.includes('件の支払いを保存')).click()")
        await pg.wait_for_timeout(400)
        await pg.evaluate('openReceipt()'); await pg.wait_for_timeout(300)
        await pg.locator('#sheets input[type=file][multiple]').set_input_files([f'{TMP}/rc_a.jpg', f'{TMP}/rc_c.jpg'])
        await pg.wait_for_timeout(2000)
        saved_dup = await pg.evaluate("(document.querySelector('#sheets .py-bts')||{}).textContent || ''")
        if '同じ写真が保存済み' not in saved_dup: bad.append('B1 保存済みと同じ写真が出ない')
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(200)

        # B8: 作成中に店を登録する(名前だけの支払先)。切替を入れて保存すると店が登録される
        await pg.evaluate("pyDraft({read:{payee:{name:'テスト店W2'},date:TODAY,start:'10:00',amt:500,items:[],unread:{}}}); openPayEditor({draft: PYD})")
        await pg.wait_for_timeout(300)
        reg = await pg.evaluate("!!document.querySelector('#sheets .py-regshop .tgl')")
        if not reg: bad.append('B8 作成中の「店として登録」が無い')
        await pg.evaluate("document.querySelector('#sheets .py-regshop .tgl').click()"); await pg.wait_for_timeout(200)
        await pg.evaluate("[...document.querySelectorAll('#sheets button')].find(b => b.textContent.trim() === '支払いを保存').click()")
        await pg.wait_for_timeout(400)
        shop_ok = await pg.evaluate("Object.values(DB.ents).some(e => e.n === 'テスト店W2' && e.types.includes('shop'))")
        if not shop_ok: bad.append('B8 保存時に店が登録されない')
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(200)

        # A6 / A1: 子の支払い詳細 (p16 は c1 の中。c1 は hanako が参加)、親の関わった
        await pg.goto(BASE + '#/a/p16'); await pg.wait_for_timeout(500)
        inv = await pg.evaluate("[...document.querySelectorAll('#main .sec h2')].map(e => e.textContent.trim())")
        if '関わった' not in inv: bad.append(f'A6 関わった が無い {inv}')
        # A1: p2 は 牛乳 が買い物ToDo t4 に一致
        # 買い物ToDo t4 は上のテストで完了にしていることがあるので、初期状態の新しい文脈で見る
        ctx2 = await b.new_context(viewport={'width': 412, 'height': 900}); pg = await ctx2.new_page()
        await pg.goto(BASE + '#/a/p2')
        try:
            await pg.wait_for_selector('#main .py-shop', timeout=3000); sug = True
        except Exception:
            sug = False
        if not sug: bad.append('A1 詳細に買い物ToDoの完了案内が無い')
        else:
            await pg.evaluate("[...document.querySelectorAll('#main button')].find(b => b.textContent.trim() === '完了にする').click()")
            await pg.wait_for_timeout(300)
            if not await pg.evaluate("!!DB.tasks.t4.done"): bad.append('A1 完了にしても ToDo が完了にならない')
        if errs: bad.append(f'JSエラー {errs[:3]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w2_pay OK')
    raise SystemExit(1 if bad else 0)

asyncio.run(main())
