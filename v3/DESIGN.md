# UI v3 デザインシステム(実装者向け)

個人記録アプリ(写真・位置・支払いなどを日記・家計簿に統合)のモック。v2(`../index.html` 相当、作業用コピー: `/tmp/claude-0/-home-claude/dc1f4990-4967-593e-ac74-c2abec424518/scratchpad/v2/index.html`)の**機能と世界観**を保ち、UI/UXをプロダクト品質で作り直したもの。

## 世界観(変えない)
- データモデルをそのまま画面構造に: 画面=オブジェクト、参照は同じ部品(`eref`/`aref`)、親は上に `parentLink`、子は下にセクション。
- クラス=形の印(`glyph(cls)`)+色。activity / schedule(点線) / todo / entity(フォルダ) / note(角折れ) / type(値札) / expense(半券)。
- 青系・光は左上から(主役カードの上端ハイライトと柔らかい影のみ)。IBM Plex Sans JP。明朝・紫系AI配色・茶色・白背景上部の濃いグラデーション・説明文の段落は禁止。

## v2 からの主な是正点(必ず守る)
1. **作成の入口は中央の＋だけ**。画面上部に「＋〇〇」ボタンを並べない。各画面ファイルは `CREATE.push({...})` で作成シートに項目を登録する。空状態の画面だけ CTA ボタン可。
2. **タブ上部は `largeTitle()` + 1行の `metrics`**(大きなヒーローカードを置かない)。
3. **一覧は1件1行(最大2行)**。`row()` を `list()` で包む。カードを並べない(2列カード禁止)。
4. **色は印と小さいタグだけ**。面の背景を画面ごとの色にしない。
5. 文字は最小12px、タップ領域は44px以上。部品にある値を使い、独自の font-size/padding を作らない。
6. 選択肢は少なく: 切替は `seg()`(2〜4個)、絞込みは `chips()`。
7. 削除・完了など取り消せる操作は `toast(msg, undoFn)` で取り消しを出す(確認ダイアログより優先)。
8. 状態を変えたら `save()` してから `rerender()`。
9. 空状態は `empty(cls, icon, 文, {label,onclick})`。エラーは `banner('warn', html)`。
10. 文言: 能動・具体(「保存」でなく「支払いを保存」)。ボタン名とトーストの動詞を揃える(「記録にする」→「記録にしました」)。`A · B · C` の羅列や英大文字ラベルは使わない(日付・時刻の区切りなど必要最小限のみ)。

## 部品(js/ui.js)
`largeTitle(title,{actions,metrics})` / `appbar(title,{actions})` / `seg(items,cur,"js $v")` / `chips(items,cur,"js $v")` / `sh(title,n,{label,icon,onclick|href})` / `list(rows,cls)` / `row({href|onclick,lead,t,s,v,amt,chev,time,cls})` / `prop(k,val,meta)` / `hero(cls,{kind,title,big,sub})` / `parentLink(href,leadHtml,name)` / `glyph(cls,icon,size)` / `avatar(id,size)` / `eref(id,name)` / `aref(id)` / `tag(label,{color,cls,href})` / `catTag(k)` / `typeTag(t)` / `empty()` / `banner()` / `suggest(text,{label,onclick})` / `toggle(on,onclick)` / `checkbox(on,onclick,lg)` / `openSheet({title,body,left,right,footer,full})` / `setSheetBody(html)` / `closeSheet()` / `toast(msg,undo)` / `route(name,fn,{tab})` / `go(hash)` / `goBack()` / `rerender()` / `save()` / `uid(prefix)` / `EXTRA`(自由な保存領域) / 日付: `TODAY`,`fmtMD`,`fmtMDW`,`relDay`,`wdOf`,`addDays`,`evOn`,`yen`
CSS クラス: `.field`(フォーム行: `<div class="field"><label>名</label><input></div>` を `.list` で包む), `.input`, `.btn`(`.tonal` `.ghost` `.danger` `.sm` `.block`), `.btnrow`, `.hint`, `.bar100`, `.meter`, `.tag.special|warn|danger|ok|brand`, `.mt`, `.num`, `.muted`, `.faint`, `.ell`, `.grow`。
新しい CSS が必要なら各画面ファイルの先頭で `document.head.insertAdjacentHTML('beforeend','<style>…</style>')` し、クラス名は画面の接頭辞付き(例 `.ld-chart`)。トークン(`var(--…)`)だけを使う。

## タブとルート
- 記録 `#/activities`, `#/a/{id}` … js/screens/activities.js(参照実装。書き方を真似る)
- 予定 `#/plan`(seg: カレンダー / ToDo、`?view=todo`), `#/ev/{id}`, `#/task/{id}` … plan.js
- 人・場所 `#/entities`, `#/e/{id}`, `#/n/{id}`(ノート), `#/types` … entities.js
- 家計簿 `#/ledger`, `#/x/{id}`, `#/cats` … ledger.js
- 作成フロー(レシート・支出・予定・出来事・日記・エンティティ) … create.js

## データ(js/data.js、グローバル)
TYPES, E(エンティティ), NOTES, A(アクティビティ: 日記/支払い/会話/移動。日記が親で kids), X(自分の支出。pay=支払いID), CATS(階層・色・fx固定/変動・sp非日常), TREND, TODAY='2026-10-31', EV(予定。rec=記録済みのアクティビティ、pay=定期支払い), LISTS, TD(ToDo), TYPE_PROPS。ヘルパー: typeAnc, catAnc, catPath, catColor, catFx, xFx, catSp, xSp, shopOf, whoOf, xDate, actsOf, relsOf, propsOf, evOn, nextOn, placeOf, mdw, addDays。
