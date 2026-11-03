#!/usr/bin/env python3
"""v3の上書きの層を取り除くための道具。
指定したクラスを含むセレクタを CSS から取り除く(混在するセレクタリストは該当部分だけ外す)。
取り除いたルールは --save に書き出す(新しく書くときの参照用)。

使い方:
  python3 tools/cssprune.py css/app.css --classes ax,ax-row,dh2 --save /tmp/removed.css
  python3 tools/cssprune.py css/app.css --prefix ld-,zm --save /tmp/removed.css   (接頭辞で指定)
  --dry で書き換えずに件数だけ表示
"""
import re, sys, argparse

def split_blocks(css):
    """トップレベルを [ (prelude, body or None) ] に分ける。コメントは prelude 側に残す。"""
    out = []; i = 0; n = len(css)
    while i < n:
        j = css.find('{', i)
        if j < 0:
            out.append((css[i:], None)); break
        depth = 1; k = j + 1
        while k < n and depth:
            if css[k] == '{': depth += 1
            elif css[k] == '}': depth -= 1
            k += 1
        out.append((css[i:j], css[j+1:k-1])); i = k
    return out

def matches(sel, classes, prefixes):
    toks = re.findall(r'\.([A-Za-z0-9_-]+)', sel)
    return any(t in classes or any(t.startswith(p) for p in prefixes) for t in toks)

def prune(css, classes, prefixes, removed):
    res = []
    for prelude, body in split_blocks(css):
        if body is None:
            res.append(prelude); continue
        head = prelude.strip()
        # コメントは残す
        m = re.match(r'(?s)(.*\*/)?(\s*)(.*)$', prelude)
        lead = (m.group(1) or '') + (m.group(2) or '')
        sel = m.group(3)
        if sel.strip().startswith('@media') or sel.strip().startswith('@supports'):
            inner = prune(body, classes, prefixes, removed)
            if inner.strip():
                res.append(f'{lead}{sel}{{{inner}}}')
            else:
                res.append(lead)
            continue
        if sel.strip().startswith('@'):
            res.append(f'{prelude}{{{body}}}'); continue
        parts = [p for p in re.split(r',(?![^()]*\))', sel)]
        keep = [p for p in parts if not matches(p, classes, prefixes)]
        drop = [p for p in parts if matches(p, classes, prefixes)]
        if drop:
            removed.append(f"{','.join(p.strip() for p in drop)}{{{body.strip()}}}")
        if keep:
            res.append(f"{lead}{','.join(keep)}{{{body}}}")
        else:
            res.append(lead)
    return ''.join(res)

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('file'); ap.add_argument('--classes', default=''); ap.add_argument('--prefix', default='')
    ap.add_argument('--save'); ap.add_argument('--dry', action='store_true')
    a = ap.parse_args()
    classes = set(c for c in a.classes.split(',') if c); prefixes = [p for p in a.prefix.split(',') if p]
    css = open(a.file).read(); removed = []
    new = prune(css, classes, prefixes, removed)
    new = re.sub(r'\n{3,}', '\n\n', new)
    print(f'removed {len(removed)} selectors/rules')
    if a.save: open(a.save, 'w').write('\n'.join(removed) + '\n')
    if not a.dry: open(a.file, 'w').write(new)
