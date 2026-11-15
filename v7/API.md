# v6 実装の約束と API(画面担当はまずこれを全部読む)

## 0. 決まり(厳守)
1. **書いてよいファイルは、割り当てられた `js/s-<名前>.js` と `css/s-<名前>.css` だけ。** `model.js` `core.js` `parts.js` `tokens.css` `base.css` `parts.css` `screens.css` `index.html` と他の担当のファイルは変更しない。足りない関数・部品があれば、自分のファイルの中に(自分の接頭辞を付けて)書き、最後の報告に「共通に上げたいもの」として書く。git の操作(commit / push / checkout)はしない。Notion には書かない。
2. ビルドなし・グローバル関数。**自分の関数名・CSS クラスには担当の接頭辞を付ける**(下の表)。`top` `name` `status` `open` `close` `event` `length` `parent` `self` `find` `stop` `print` `focus` `history` `screen` `origin` などブラウザの既存の名前を最上位の変数名にしない。
3. データの読み書きは **model.js の関数だけ**(`DB` を直接書き換えない。読むのは可)。変更は必ず `commit` を通る関数で行い、結果は `done(結果, {…})` に渡す(トースト・取り消し・描き直しを共通化)。
4. 文字は必ず `esc()`。`onclick` に値を入れる時は `q(値)`(例: `onclick="xxOpen(${q(id)})"`)。
5. 見た目: 色・影・角丸・文字サイズ・余白は **tokens.css の変数だけ**(`#` で始まる色や px の文字サイズを自分のCSSに直書きしない。例外: 1px/1.5px/2px の線、アイコンの寸法)。同じ意味の部品を自作しない(parts.js を使う)。DESIGN.md 4章の意味表に無い形・色を作らない。
6. **説明文を置かない**(見出しと値と形で示す)。言葉は短く、利用者の言葉で。削除は「消す」、編集は「直す」。赤(`--over`)は「ToDoの期限切れ」だけ(削除ボタン・いま・見出しを赤くしない)。琥珀(`--owe`)は貸し借りだけ。
7. 押せる所は 44px 以上(見た目が小さい時は `::after{content:"";position:absolute;inset:-8px}` で広げる)。文字は 12px 以上。幅 360px でも横にはみ出さない。長い名前は折り返す。
8. 取り消せる操作はすぐ実行して「取り消す」を出す(`done` がやる)。確認(`confirmSheet`)は取り消せない操作(全データの初期化・読み込み)だけ。
9. シートで入力する時は、下書きを自分のグローバル変数(例 `PYD`)に持ち、文字入力は `oninput` で下書きに書くだけ(描き直さない = フォーカスを失わない)。構造が変わる時だけ `setSheetBody(…)`。`dirty` を必ず渡す(入力途中で閉じても消えない)。
10. 全ての一覧に空の状態 `empty()`。既定値を置く(同じことを2度入力させない)。
11. 画面は `<div class="screen s-xxx">…</div>` を返す。タブの最上位は `head(題, {actions})`、詳細は `bar(題, {actions})` を先頭に置き、主役の題の要素に `data-bar-watch` を付ける(流れ去るとバーに題が出る)。
12. 確認の方法(必須): `python3 /home/claude/tools/shoot.py /home/claude/personal-record-app/v6/index.html /home/claude/s6 '<ルート,ルート>' both 412 full` でスクショを撮り、**Read ツールで画像を見て**崩れ・はみ出し・重なり・読みにくさを直す(ライトとダークの両方)。操作は `/home/claude/tools/ev.py <ルート> "<JS式>"` で関数を呼んで確かめる(例: シートを開く → `document.querySelector('.sheet').innerText`)。シートを開いた状態のスクショは `python3 /home/claude/tools/shoot2.py '<ルート>' "<開くJS>" <出力名> [light|dark]`。時計は `?now=2026-11-11T13:20` に固定されている(今日 = 2026-11-11 水曜)。JS エラー 0 を確かめる。
13. 最後の報告(短く): 作ったルートと関数 / v5 の棚卸し(指定の inv6 ファイル)の各項目を「対応した・形を変えて対応した(どう変えたか)・対応していない(理由)」で / 共通に上げたいもの / 確かめたこと・確かめられなかったこと。

| 担当 | ファイル | 接頭辞 | 登録するルート・公開する関数 |
|---|---|---|---|
| 詳細 | s-detail.js / .css | `dt` | route `a`。`openActEditor(o)` |
| 支払い | s-pay.js / .css | `py` | `payDetail(id)`(HTML を返す。s-detail が呼ぶ)、`openPayEditor(o)`、`openReceipt(o)` |
| 家計簿 | s-ledger.js / .css | `lg` | route `ledger`(root, tab ledger)、`cats` |
| エンティティ | s-ents.js / .css | `en` | route `ents`(root, tab ents)、`e`、`types`、`n`。`openEntEditor(o)` |
| ToDo | s-todo.js / .css | `td` | route `todo`(root, tab todo)、`task`。`openTaskEditor(o)` |
| メモ | s-memo.js / .css | `mm` | route `memo`(root, tab memo)。`openMemoEditor(id?)` |
| さがす・設定 | s-misc.js / .css | `ms` | route `search`(root, tab search)、`settings`(root, tab settings) |

他の担当の関数を呼ぶ時は `window.openPayEditor && openPayEditor({...})` のように、無くても落ちない形で呼ぶ。＋(すばやく書く)から詳しい入力へ移る時、書きかけの解釈が `window._capSeed`(`parseQuick` の戻り値。無ければ null)に入っているので、各 `openXxxEditor` は開く時にそれを初期値に使う。

## 1. model.js(v6.5。データ。形の正本は MODEL.md、ファイル先頭のコメント)
時計: `TODAY`('YYYY-MM-DD') `NOW`('HH:MM') `nowStamp()`('YYYY-MM-DDTHH:MM')。
日付: `dAdd(d,n)` `dDiff(a,b)` `dDow(d)` `dowJ(d)` `ymOf(d)` `ymAdd(ym,n)` `ymDays(ym)` `hm2m(t)` `m2hm(m)` `isHM(s)` `isDate(s)` `fmtMD(d)` `fmtMDJ(d)` `fmtFull(d)` `fmtYM(ym)` `relDay(d)` `durJ(分)` `yen(n)` `uid(接頭辞)` `pad2(n)`。
TS(時刻。'YYYY-MM-DD' = 終日 / 'YYYY-MM-DDTHH:MM'): `isTS(s)` `dOf(ts)`(日) `tOf(ts)`('HH:MM' か '') `hasT(ts)` `tsOf(d, t)` / Act から: `aDate(a)` `aDate2(a)`(終わりの日が別の日ならその日、無ければ null) `aStart(a)` `aEnd(a)`('HH:MM' か '') `isAllDay(a)` `spanOn(a, d)` → その日に掛かる部分 `{s, e('24:00' もある), from(前日から続く), to(翌日へ続く)}`(終日は null)。
定数: `KINDS`{鍵:{n, ic, moment?}}(meal talk work move shop play health trip event sleep pay settle) `ROLES` `BASIS` `TAX_DEFAULT` `CAT_COLORS` `DOW`。

### 読む
- `A(id)` → Act。`決まりid@日付` は仮の回(`virt:1, routine:決まりid, state:'plan'`)。`決まりid` 単体は null。`E(id)` → ent(`'me'` も本物の行)/ `isStub(id)`
- `isPay` `isSpan`(= end がある) `isPlan` `isLate` `isUnsure(row)` `timeText(a)` `actMins(a)`(日またぎも正しい。終日は 0) `actTitle(a)`(支払い = 支払先、精算 = 「花子さんから ¥…」) `payeeOf(a)` `placeOf(a)` `rolesOf(a, ...役割名)` `roleName(role)`
- `actsOn(d, {state})` → [[id, act]](start の日〜end の日。時刻順、終日が先頭、前日から続くものは 00:00 扱い。仮の回を含む)/ `kidsOf` `descOf` `ancOf` `daysWith(from, to)` `dayOf(d)` → {diary} / `holOn(d)` → 祝日の名前か '' / `photosOn(d)` `photosOfAct(id)` → [[id, photo]]
- `pendingPlans()` `upcoming(日数)` / 繰り返し: `DB.routines[id]` `repOn(routine, d)` `repText(routine)` `repNext(routine, from)` `recordsOfRoutine(id)`
- `roleIn(a, eid)`(役割名 / 支払った / 使った / 支払い方法 / 精算)`actsOfEnt(eid)`
- 種類: `typeAnc` `typeKids` `isUnder` `typePath` `propsOf` / ent の一覧: `entsAll({stubs})` `entsOfType(k, deep=true, {stubs})`(既定は stub を除く)`entRoot(id)` / 事実: `factsNow(eid)` `factHist(eid, k)`(新しい順)`factAt(eid, k, 日付)` / 関係: `relsOf(id, {ended})` → [{id, role, other, from, to}]
- カテゴリ: `catAnc` `catKids` `catName` `catPath` `catRoot` `catColor` `catFx` `catSp` `catGuess({e}|{name}, text)`
- 支払い方法(ent): `methodsAll()` → [[id, ent]] `methodDefault()` `methodName(id)`
- お金(id = 支払いの act の id): `itemAmt` `itemTax` `usersOf` `payItems(a)` `payTotal(a)` `paidOf(a)` [{who, amt, how(支払い方法の id)}] `myPaid(a)` `payMethod(a)`(名前を「・」でつなぐ)`whoName(w)` `whoShort(w)` `peopleOf(id)` `xOf(id)`(仮の回も)`xShares` `xAmt` `xItems(id)` [{i, id(品目id), n, price, full, how, cat, memo, unk}] `xCats(id)` [{cat, amt, ids}] `xFx` `xSp` `isShared(id)` `basisGuess` `lnAmts`
- 貸し借り: `debtsOf(id)` → [{who, amt(>0 相手が自分に負う), settle(精算の act id か null)}] / `balanceOf(id)` → {amt(差 N), who:[id], open, settled, debts} / `balanceText(b)` / `openBalances()` / `personBalances()` [{who(id か 'other'), amt, ids}] / `settlesOf(payId)` [[id, act]]
- 未確認: `unsureCount(a)`(支払い: 項目の数 + unsure の品目の数)
- 家計簿: `expsIn(ym)` `monthSum(ym)` `catSum(ym, k)` `daySpend(d, {noFixed})` `paceOf(ym, 日数)`(settle は入らない)
- ent の数字: `spendWith(eid)` `entStats(eid)`
- ToDo: `taskOpen` `taskLate` `tasksDue()` `tasksOfAct(id)`(決まりid の ToDo も返す)`tasksOfEnt` / メモ: `memosSorted()` `memosOn(d)` / `recallDays()` `searchAll(q)` `parseQuick(s)`(pay/plan の時刻は TS の start・end)`parseTask(s)` `parseDateWord(s)` `dataCounts()` `oldestYm()` `shopMatches(items)` `payDup({id?, start, total})`

### 書く(全て `{ok:true, label, undo(), …}` か `{ok:false, why}`)
- 日・写真: `daySetDiary(d, 文)` `photoAdd({at?, d?, cap, act?, ents?, geo?})` → {id} `photoUpdate(id, patch)` `photoRemove(id)`
- Act: `actAdd({kind, title, start, end?, state, parent?, roles:[{r, e|name}], note?, alarm?, rep?:{u}})` → {id}(rep があれば決まりを作り {id:'決まりid@最初の日', routine})/ `actUpdate(id, patch)`(`決まりid@日付` は決まりを直す。`rep:null` は決まりをやめて1回だけの予定にして {id})/ `actRemove(id)`(仮の回はその回だけ skip)/ `actMove(id, 親|null)` / `planRecord(id, {start?, end?, amt?, how?})` → {id} / `sleepWake(ts)` → {id}
- 繰り返し: `routineAdd({every, from, until?, tpl})` `routineUpdate(id, patch)`(tpl は部分上書き)`routineRemove(id)` `routineSkip(id, 日付, on=true)`
- 支払い: `paySave({id?, start, parent?, payee:{e}|{name}, roles?, items:[{n, price|lines, tax, users, memo?, unk?, unsure?}], amt?, payers?:[{who, amt, how}], note?, unsure?:[…]|true, via?, exp:{…}, doneTasks?})` → {id} / `payPlanSave({id?, start, payee, title?, amt, cat, rep?})` / `expUpdate(id, patch)` / `itemRemove(id, 品目id)` / `paySettle(id か配列, on=true, {who?, date?, how?})` / `confirmRows([{t:'acts'|'tasks'|'memos', k}])`
- ToDo: `taskAdd` `taskUpdate` `taskToggle` `taskRemove` `listAdd` `listRemove` `listRename` `listSetShop` `taskToPlan(id, {start, end?})` `planToTask(id)`
- メモ: `memoAdd` `memoUpdate` `memoPin` `memoRemove`
- ent: `entAdd({n, types, facts?})` `entUpdate(id, {n?, types?, facts?, stub?:null})` `factSet(id, 項目, 値, {keepHist, why, since})` `entRemove(id)` / `relAdd({a, b, role, inv?, from?})` → {id} `relUpdate(id, patch)` `relRemove(id)`
- 種類: `typeAdd` `typeUpdate` `typeRemove` `typePropAdd` `typePropRename` `typePropRemove` / ノート: `noteSave(eid, [{h, text}])` `noteSubSave(eid, subId|null, {n, sub, text})` → {id} `noteSubRemove(eid, subId)`
- カテゴリ: `catAdd` `catUpdate` `catRemove` / 支払い方法: `methodAdd(名前)` → {id} `methodRemove(id)`
- 全体: `exportJSON()` `importJSON(text)`(v65 のみ)`resetData()`
- 内部(m-inbox 用): `commit(label, fn)` `fail(msg)` `_logWhat` `_logPush` `TABLES` `save` `dbNormalize` `dbSeedInbox`

## 2. core.js
- `esc(s)` `q(v)` `$(sel, el?)` `$$(sel, el?)` `hapt()` `isWide()`
- `route(name, fn(id, qs, rest) → HTML, {tab, root})` `go(hash, {replace})` `goBack()` `rerender()`(位置を保って描き直す) `after(fn)`(描いた直後に1回) `isRerender()` `parseHash()` `currentTab()` `scroller()`
- `openSheet({title, body, primary:{label, on, disabled}, secondary:{label, on}, dirty:()=>bool, onClose, tall, id, lead(題の右のHTML)})` / `setSheetBody(html)` `setSheetPrimary({…})` `setSheetTitle(t)` `closeSheet(force)` `closeAllSheets()` `sheetOpen()`。シートは重ねられる(選ぶシートを上に開ける)。
- `pick({title, items:[{k, label, sub, lead(HTML), depth}], cur, multi, on(k か配列), search, create:{label:'を新しく作る', on(文字)}, none:{label}})` 選ぶシート / `menu(題, [{label, icon, sub, on}])` 操作の一覧 / `confirmSheet({title, body, ok, on})`
- `toast(msg, {undo, bad, act:{label, on}})` / **`done(res, {msg, close, go, replace, quiet, noUndo, act:{label, on}})`** → 成功なら true(失敗は理由をトースト。成功は(close ならシートを閉じ)描き直して、取り消し付きトースト)
- `val(id)` 入力の値 / `downloadText(名前, 文字)` / `wireSwipe(root)`(`.swipe > .swipe-in + button.swipe-act` を横スワイプで実行) / `setTheme('auto'|'light'|'dark')` `getTheme()` / `setNavBadge(tab, fn)` / `openCapture(種類?)` `openDiary(d)` `recordPlan(id)`(共通の「記録にする」) `taskCheck(id)`(共通のチェック)

## 3. parts.js(HTML を返す)
- `ic(名前, cls?)` アイコン(名前は parts.js の `IC`: plus close back chev down up check more edit trash pin search sliders cal clock place yen camera photo memo task people user wallet home axis sky sun moon meal talk work move bag spark heart trip flag repeat bell link warn info download upload filter receipt swap tag book tree theme undo list grid hand pencil day)/ `kindIc(act)`
- `iconBtn(アイコン, 読み上げ名, onclick, cls?)`(cls: `on-card` 白い面 / `pri` 主) `btn(ラベル, onclick, 'pri'|'sec'|'ghost'|'dash', {icon, sm, wide, disabled})`
- `head(題, {sub, actions})` `bar(題, {actions})` `sec(題, {n, act:{label, on|href}})`
- `amt(n, {cls:'hero'|'big'|'mute', sign})` `tag(文字, ''|'owe'|'late'|'plan'|'me'|'kin'|'read')` `catTag(k, {path})` `avatar(id, 名前?, 'lg'|'xl')` `ent(id, {name, link:false, short})` `roleEnts(act, {link, short, payee})`
- `seg([[k, ラベル]], cur, 関数名)` `chips([[k, ラベル, 件数?]], cur(k か配列), 関数名)`(折り返すなら外側に `.chips.wrap` は不可 → `chips(...)` の結果を `.replace('class="chips"','class="chips wrap"')`)
- `row({lead, t, s, tHtml, sHtml, right, href, on, chev, cls})` `rows(HTML か配列)`(1枚の面) `prop(名前, 値HTML, {on})`(`rows()` の中で使う) `stats([{v(HTML), l, href, on, kind:'owe'|'late'|'me'}])` `empty({icon, text, action:{label, on}})` `banner(html, 'warn'|'info')`
- `check(done, onclick, {late, label})` `toggle(on, onclick, 読み上げ名)` `field(ラベル, control, {hint})` `input({id, value, type, ph, oninput, onchange, mode, auto, cls:'big', label})` `textarea({…rows})` `select({id, value, options:[[v, l]], onchange})` `pickField(ラベル, 値HTML, onclick, {emptyv})` `readMark(html)` `progress(0〜1, ラベル?)`
- 時間: `skyBand(d, {mini})` `skyAt('HH:MM')`(色) `dayHead(d)` `photoStrip(d, {add, on})` `dayAxis(d, {memos, filter})` `tlAct(id)`(軸の1行。`<div class="tl">…</div>` の中で使う) `tlNow()` `kidRows(id)`
- お金: `paySlip(id, {noLink})`(支払いの面) `splitBar(id)` `itemsText(a)` / `taskRow(id, {noList})` `memoCard(id, {clamp, bare, on, href})`
- CSS の共通クラス: `.card`(白い面) `.card.pad` `.rows` `.grid2`(2列) `.stack`(縦に 8px) `.ents`(エンティティの並び) `.mini`(小さい補足) `.num`(等幅の数字) `.hit`(面全体を押せるリンク: `<div class="card" style="position:relative"><a class="hit" href></a>…中のボタンは自動で上に出る</div>`) `.clamp`

## 4. v6.1 で足した共通の関数(model.js 部分は v6.5 で書き直し)
- model.js: 旧 → 新の対応表は `MODEL-API-CHANGES.md`。`shopMatches(items)` `payDup({id?, start, total})` `photosOfAct(id)` → [[id, photo]] `recordsOfRoutine(決まりid)` `oldestYm()` `listRename` `listSetShop` `taskToPlan(taskId, {start, end?})` `planToTask(id)` `monthSum(ym).unset` `paySave({... doneTasks:[taskId]})`。人・店・支払い方法は ents の id(名前だけは `{name}` で入力 → stub が自動で作られ、指されなくなると消える)。
- parts.js: `paySlip(id, {cat})`(そのカテゴリに計上した額を右に出し、全額と違えば「支出 ¥全額」の札)。支払いの面に特別感の印(非日常 / 未設定)。`tlAct(id, {roleOf: エンティティid})`(その相手の役割の札を出す)。`dayHead` に日記の一言。
- core.js: トーストは3つまで表示し、残りは順番待ち(取り消しは消えない)。画面を移ると、未保存でないシートは閉じる。保存に失敗すると知らせる。
