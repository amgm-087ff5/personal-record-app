#!/usr/bin/env python3
"""W4(v7): ＋の数は青・赤は ToDo だけ / theme-color は tokens.css から / 動きを減らす設定で scrollIntoView を瞬時に /
段階外の大きさ・色は変数 / 不正な URL・欠けたデータで白画面にならない / ホームのすぐメモ・支払い入口 / 通知の選択肢 / 取り消しの「戻せなかった」知らせ"""
import asyncio, os, re
from playwright.async_api import async_playwright
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BASE = 'file://' + os.path.join(ROOT, 'index.html') + '?now=2026-11-11T13:20'

async def main():
    bad = []
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox', '--allow-file-access-from-files'])
        ctx = await b.new_context(viewport={'width': 412, 'height': 900}, has_touch=True, is_mobile=True)
        pg = await ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(500)
        # E5: ＋の数は青系(白地・青の文字)、ToDo の数は赤
        c = await pg.evaluate("""() => { const pb = document.querySelector('.nav-plus .nav-badge'); const tb = document.querySelector('a.nav-i[href="#/todo"] .nav-badge');
          const cs = e => e && getComputedStyle(e); const rt = getComputedStyle(document.documentElement);
          const px = v => { const d = document.createElement('i'); d.style.color = v; document.body.appendChild(d); const r = getComputedStyle(d).color; d.remove(); return r; };
          return { plus: pb ? [cs(pb).backgroundColor, cs(pb).color] : null, todo: tb ? cs(tb).backgroundColor : null, me: px(rt.getPropertyValue('--me').trim()), over: px(rt.getPropertyValue('--over').trim()) } }""")
        if not c['plus']: bad.append('E5 ＋の数が出ていない')
        else:
            if c['plus'][0] == c['over']: bad.append(f'E5 ＋の数が赤 {c["plus"]}')
            if c['plus'][1] != c['me']: bad.append(f'E5 ＋の数の文字が青でない {c["plus"]} me={c["me"]}')
        if c['todo'] and c['todo'] != c['over']: bad.append(f'E5 ToDo の数が赤でない {c["todo"]}')
        # E10: meta theme-color は tokens.css の --bg と同じ(ライト・ダーク)
        for th in ('light', 'dark'):
            await pg.evaluate(f"() => setTheme('{th}')"); await pg.wait_for_timeout(100)
            m = await pg.evaluate("() => document.querySelector('meta[name=theme-color]').content")
            bg = await pg.evaluate("() => getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()")
            if not m or m.lower() != bg.lower(): bad.append(f'E10 theme-color {th} {m} != {bg}')
        await pg.evaluate("() => setTheme('auto')")
        # E11: 動きを減らす時は瞬時、通常は滑らか(kbd.js の reveal)
        for mode, want in [('reduce', 'auto'), ('no-preference', 'smooth')]:
            await pg.emulate_media(reduced_motion=mode)
            await pg.goto(BASE + '#/search'); await pg.wait_for_timeout(400)
            await pg.evaluate("() => { window.__so = []; const o = Element.prototype.scrollIntoView; Element.prototype.scrollIntoView = function (x) { window.__so.push(x); return o.call(this, {block:'nearest'}); }; }")
            await pg.evaluate("() => { const i = document.querySelector('#main input'); if (i) { i.blur(); i.focus(); } }")
            await pg.wait_for_timeout(450)
            got = await pg.evaluate("() => { const x = (window.__so || []).filter(o => o && o.block === 'center').at(-1); return x ? x.behavior : null; }")
            if got != want: bad.append(f'E11 reduced={mode} behavior={got} want={want}')
        await pg.emulate_media(reduced_motion='no-preference')
        # 土台: 壊れた URL / 不正な日・月で落ちない
        for h, must in [('#/a/%', None), ('#/e/%E0%A4%A', None), ('#/day/xxxx', '日が見つかりません'), ('#/day/2026-13-45', '日が見つかりません')]:
            errs.clear(); await pg.goto(BASE + h); await pg.wait_for_timeout(300)
            txt = await pg.evaluate("() => document.querySelector('#main').innerText")
            if errs: bad.append(f'URL {h} JSエラー {errs[:1]}')
            if not txt.strip(): bad.append(f'URL {h} 白い画面')
            if must and must not in txt: bad.append(f'URL {h} 「{must}」が無い')
        errs.clear(); await pg.goto(BASE + '#/acts?m=2026-99'); await pg.wait_for_timeout(300)
        ym = await pg.evaluate("() => TLS.ym")
        if ym != '2026-11' or errs: bad.append(f'不正な月 ym={ym} {errs[:1]}')
        # 土台: 描画が例外を投げても白い画面にならず、書き出し・設定・初期化の手段が出る
        await pg.goto(BASE + '#/home'); await pg.wait_for_timeout(300)
        await pg.evaluate("() => { ROUTES.home.fn = () => { throw new Error('x'); }; }")
        await pg.evaluate("() => { try { rerender(); } catch (e) {} }"); await pg.wait_for_timeout(100)
        r = await pg.evaluate("() => ({ a: !!document.querySelector('#main [role=alert]'), b: [...document.querySelectorAll('#main button')].map(x => x.textContent.trim()), ready: document.documentElement.classList.contains('ready') })")
        if not r['a'] or not r['ready'] or not all(k in r['b'] for k in ('書き出す', '設定を開く', '初期化して始める')): bad.append(f'失敗面 {r}')
        # 壊れた保存データでも起動して画面が出る
        ctx2 = await b.new_context(viewport={'width': 412, 'height': 900}); p2 = await ctx2.new_page(); e2 = []
        p2.on('pageerror', lambda e: e2.append(str(e)))
        await p2.goto(BASE + '#/home')
        await p2.evaluate("() => localStorage.setItem(STORE_KEY, '{壊れた')"); await p2.goto(BASE + '#/home'); await p2.wait_for_timeout(500)
        t2 = await p2.evaluate("() => document.querySelector('#main').innerText.length")
        if t2 < 20 or e2: bad.append(f'壊れた保存データで画面が出ない len={t2} {e2[:1]}')
        await ctx2.close()
        # ホーム: すぐメモ(空は理由を出して残す・入れたら保存)と支払いの入口(金額を渡す)
        await pg.goto(BASE + '#/home'); await pg.reload(); await pg.wait_for_timeout(500)
        n0 = await pg.evaluate("() => Object.keys(DB.memos).length")
        await pg.evaluate("() => hmMemoSave()"); await pg.wait_for_timeout(100)
        n1 = await pg.evaluate("() => Object.keys(DB.memos).length")
        if n1 != n0: bad.append('すぐメモ: 空で保存された')
        await pg.fill('#hm-memo', 'いまの思いつき'); await pg.evaluate("() => hmMemoSave()"); await pg.wait_for_timeout(200)
        n2 = await pg.evaluate("() => Object.keys(DB.memos).length")
        if n2 != n0 + 1: bad.append('すぐメモ: 保存されない')
        await pg.fill('#hm-qe', '1,280'); await pg.evaluate("() => hmPayQuick()"); await pg.wait_for_timeout(500)
        amt = await pg.evaluate("() => typeof PYD !== 'undefined' && PYD ? PYD.amt : null")
        if amt != 1280: bad.append(f'支払いの入口: 金額が渡らない {amt}')
        await pg.evaluate("() => closeAllSheets()")
        # 通知の選択肢: 5分前〜1日前、旧「前日」は1日前として選ばれる
        r = await pg.evaluate("() => ({ ks: DT_ALARMS.map(x => x[0]), n: alarmNorm('前日'), e: alarmNorm('') })")
        if r['ks'] != ['', '5分前', '10分前', '30分前', '1時間前', '1日前'] or r['n'] != '1日前' or r['e'] != '': bad.append(f'通知 {r}')
        # 取り消し: 戻せなかった項目があれば知らせる
        await pg.evaluate("() => { document.querySelector('#toasts').innerHTML = ''; _toastQ.length = 0; }")
        await pg.evaluate("() => { toast('x', { undo: () => ({ skipped: 2 }) }); }"); await pg.wait_for_timeout(100)
        await pg.evaluate("() => document.querySelector('[data-undo]').click()"); await pg.wait_for_timeout(150)
        t = await pg.evaluate("() => [...document.querySelectorAll('.toast-m')].map(e => e.textContent).join('|')")
        if '2か所' not in t: bad.append(f'取り消しの知らせ {t}')
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    # 静的な検査: 直書きの大きさ・色・smooth が残っていない(自分の担当ファイル)
    for f in ['css/base.css', 'css/parts.css', 'css/screens.css', 'css/s-detail.css', 'js/kbd.js', 'js/core.js']:
        src = open(os.path.join(ROOT, f), encoding='utf-8').read()
        for mm in re.finditer(r'font-size:\s*(34|17|16|64)px', src): bad.append(f'{f} 直書きの文字 {mm.group(0)}')
        if re.search(r'rgba\(|#fff\b', src): bad.append(f'{f} 直書きの色')
        if "behavior: 'smooth'" in src and f.endswith('kbd.js'): bad.append(f'{f} smooth 直書き')
    print('NG:', bad) if bad else print('t_w4_ui OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
