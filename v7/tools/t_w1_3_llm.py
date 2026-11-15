"""項目3・6: 門(llmSanitize)が note・tags・date2 を通す。危険な入力は今まで通り無害化。模擬(llmMock)は雑記の作成時刻を基準にする"""
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


def resp(c, n=0):
    return json.dumps({'items': [{'n': n, 'cands': [c]}]}, ensure_ascii=False)

async def san(pg, c):
    return await pg.evaluate("(s)=>llmSanitize(s,1)", resp(c))

async def main():
    bad = []
    async with async_playwright() as p:
        b, pg, errs = await open_page(p)
        # --- 項目3: 通すべき項目 ---
        r = await san(pg, {'type': 'pay', 'amt': 850, 'date': '2026-11-11', 'start': '12:00', 'payee': {'name': 'ランチ'}, 'text': 'ランチ', 'note': '駅前のビル', 'tags': ['#外食', '買い物']})
        c = (r or {}).get('0', [None])[0] or {}
        if c.get('note') != '駅前のビル': bad.append(f"note が通らない {c.get('note')!r}")
        if c.get('tags') != ['外食', '買い物']: bad.append(f"tags が通らない(# を除く) {c.get('tags')!r}")
        r = await san(pg, {'type': 'plan', 'title': '出張', 'date': '2026-11-11', 'start': '10:00', 'date2': '2026-11-13', 'end': '12:00', 'note': '終わり 11/13 12:00'})
        c = (r or {}).get('0', [None])[0] or {}
        if c.get('date2') != '2026-11-13': bad.append(f"date2 が通らない {c.get('date2')!r}")
        if c.get('end') != '12:00': bad.append(f"日またぎの終わりの時刻 {c.get('end')!r}")
        if c.get('note') != '終わり 11/13 12:00': bad.append(f"予定の note が通らない {c.get('note')!r}")
        # 型・長さ・文字列化: HTML は文字のまま、長い note は切る、tags は数と長さを制限
        long_note = 'あ' * 3000
        many = ['t%d' % i for i in range(30)] + ['x' * 300, '<img src=x onerror=alert(1)>']
        r = await san(pg, {'type': 'memo', 'body': '<b>本文</b>', 'note': long_note, 'tags': many})
        c = (r or {}).get('0', [None])[0] or {}
        if c.get('body') != '<b>本文</b>': bad.append(f"HTML が文字として残らない {c.get('body')!r}")
        if len(c.get('note') or '') > 500: bad.append(f"note が長すぎる {len(c.get('note') or '')}")
        if len(c.get('tags') or []) > 10: bad.append(f"tags の数が多すぎる {len(c.get('tags') or [])}")
        if any(len(t) > 30 for t in (c.get('tags') or [])): bad.append('tags の長さが長すぎる')
        if any(t.startswith('#') for t in (c.get('tags') or [])): bad.append('tags に # が残る')
        if any(not isinstance(t, str) for t in (c.get('tags') or [])): bad.append('tags に文字列以外')
        r = await san(pg, {'type': 'memo', 'body': 'x', 'tags': 'not-array', 'note': 123, 'date2': 'bad'})
        c = (r or {}).get('0', [None])[0] or {}
        if c.get('tags') or c.get('note') is not None or c.get('date2') is not None: bad.append(f'型違いの note/tags/date2 が入った {c}')
        # date2 が始まりより前・不正の日は捨てる(候補は残す)
        r = await san(pg, {'type': 'event', 'title': '映画', 'date': '2026-11-11', 'start': '20:00', 'date2': '2026-11-10'})
        c = (r or {}).get('0', [None])[0] or {}
        if not c or c.get('date2') is not None: bad.append(f'date2 が前の日付のまま {c}')
        # --- 危険な入力は今まで通り無害化 ---
        neg = await san(pg, {'type': 'pay', 'amt': -500, 'date': '2026-11-11', 'start': '12:00', 'payee': {'name': 'x'}})
        if neg and neg.get('0'): bad.append(f'負の金額が通った {neg}')
        unk = await san(pg, {'type': 'delete_all', 'body': 'x'})
        if unk and unk.get('0'): bad.append(f'未知の型が通った {unk}')
        inj = await san(pg, {'type': 'pay', 'amt': 100, 'date': '2026-11-11', 'start': '12:00', 'payee': {'e': 'ent_1', 'name': 'x'}, 'ents': ['hanako'], 'delete_all': True, 'id': 'a1'})
        ci = (inj or {}).get('0', [None])[0] or {}
        if 'e' in (ci.get('payee') or {}) or ci.get('ents') or 'delete_all' in ci or 'id' in ci: bad.append(f'注入が通った {ci}')
        # --- 項目6: 模擬は雑記の作成時刻を基準にする ---
        m = await pg.evaluate("llmMock('買い物\\n明日', '2026-11-10T20:00')")
        d = (m or [{}])[0].get('due')
        if d != '2026-11-11': bad.append(f'模擬: 昨日の雑記の「明日」が今日基準 → {d}')
        m = await pg.evaluate("llmMock('映画\\n昨日', '2026-11-10T20:00')")
        ev = (m or [{}])[0]
        if ev.get('date') != '2026-11-09' or ev.get('start') is not None: bad.append(f'模擬: 終日の出来事が時刻付きになった {ev}')
        # 日またぎ: 終わりの日が後なら、終わりの時刻が始まりより前でもよい。同じ日で終わりが前なら候補ごと捨てる
        r = await san(pg, {'type': 'plan', 'title': '夜行', 'date': '2026-11-11', 'start': '22:00', 'date2': '2026-11-12', 'end': '06:00'})
        c = (r or {}).get('0', [None])[0] or {}
        if c.get('date2') != '2026-11-12' or c.get('end') != '06:00': bad.append(f'日またぎが通らない {c}')
        r = await san(pg, {'type': 'plan', 'title': '同日', 'date': '2026-11-11', 'start': '22:00', 'end': '06:00'})
        if r and r.get('0'): bad.append(f'同じ日で終わりが前の候補が通った {r}')
        if errs: bad.append(f'JSエラー {errs[:2]}')
        await b.close()
    print('NG:', bad) if bad else print('t_w1_3 OK')
    raise SystemExit(1 if bad else 0)
asyncio.run(main())
