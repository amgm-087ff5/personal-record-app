# v6 実装の約束と API(画面担当はまずこれを全部読む)

## 0. 決まり(厳守)
1. **書いてよいファイルは、割り当てられた `js/s-<名前>.js` と `css/s-<名前>.css` だけ。** `model.js` `core.js` `parts.js` `tokens.css` `base.css` `parts.css` `screens.css` `index.html` と他の担当のファイルは変更しない。足りない関数・部品があれば、自分のファイルの中に(自分の接頭辞を付けて)書き、最後の報告に「共通に上げたいもの」として書く。git の操作(commit / push / checkout)はしない。Notion には書かない。
2. ビルドなし・グローバル関数。**自分の関数名・CSS クラスには担当の接頭辞を付ける**(下の表)。`top` `name` `status` `open` `close` `event` `length` `parent` `self` `find` `stop` `print` `focus` `history` `screen` `origin` などブラウザの既存の名前を最上位の変数名にしない。
3. データの読み書きは **model.js の関数だけ**(`DB` を直接書き換えない。読むのは可)。変更は必ず `commit` を通る関数で行い、結果は `done(結果, {…})` に渡す(トースト・取り消し・描き直しを共通化)。
4. 文字は必ず `esc()`。`onclick` に値を入れる時は `q(値)`(例: `onclick="xxOpen(${q(id)})"`)。
5. 見た目: 色・影・角丸・文字サイズ・余白は **tokens.css の変数だけ**(`#` で始まる色や px の文字サイズを自分のCSSに直書きしない。例外: 1px/1.5px/2px の線、アイコンの寸法)。同じ意味の部品を自作しない(parts.js を使う)。DESIGN.md 4章の意味表に無い形・色を作らない。
6. **説明文を置かない**(見出しと値と形で示す)。言葉は短く、利用者の言葉で。削除は「消す」、編集は「直す」。赤(`--now`)は「いま・遅れ」だけ(削除ボタンを赤くしない)。琥珀(`--owe`)は貸し借りだけ。
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

## 1. model.js(データ。全体は DESIGN.md 2章とファイル先頭のコメント)
時計: `TODAY`('YYYY-MM-DD') `NOW`('HH:MM')。
日付: `dAdd(d,n)` `dDiff(a,b)` `dDow(d)` `dowJ(d)`(月) `ymOf(d)` `ymAdd(ym,n)` `ymDays(ym)` `hm2m(t)` `m2hm(m)` `isHM(s)` `isDate(s)` `fmtMD(d)`(11/10) `fmtMDJ(d)`(11月10日) `fmtFull(d)`(11月10日 火曜) `fmtYM(ym)`(2026年11月) `relDay(d)`(今日・昨日・3日前・2週間後) `durJ(分)`(1時間10分) `yen(n)`(¥1,234) `uid(接頭辞)` `pad2(n)`。
定数: `KINDS`{鍵:{n, ic, moment?}}(meal talk work move shop play health trip event wake sleep pay holiday) `ROLES`(場所 同席 参加 出発 到着 プロジェクト 関連。支払いは 支払先) `BASIS`{items:'使った分', even:'割り勘', amount:'金額を指定'} `TAX_DEFAULT`(10) `CAT_COLORS` `DOW`。

### 読む
- `A(id)` → Act(仮の回 `ひな形id@日付` も解決。その時 `virt:1, tpl:ひな形id`)/ `E(id)` → エンティティ(`'me'` は自分)
- `isPay(a)` `isSpan(a)` `isPlan(a)` `isLate(a)`(過ぎたのに記録になっていない予定) `timeText(a)` `actMins(a)` `actTitle(a)`(支払いは支払先の名前) `payeeOf(a)` `placeOf(a)` `rolesOf(a, ...役割名)` `roleName(role)`
- `actsOn(d, {state})` → その日のいちばん外側 [[id, act]](時刻順。繰り返しの仮の回を含む) / `kidsOf(id)` [[id, act]] / `descOf(id)` [id] / `ancOf(id)` [[id, act]](外側が先) / `daysWith(from, to)` / `dayOf(d)` {diary, photos}
- `pendingPlans()` 記録待ち / `upcoming(日数)` [[id, act, date]] / `repOn(ひな形, d)` `repText(ひな形)`(毎月27日) `repNext(ひな形)`
- `roleIn(a, eid)` / `actsOfEnt(eid)` [[id, act, 役割]](新しい順。親も関わっていれば親にまとめる)
- 種類: `DB.types[k]` {n, p?, props} `typeAnc(k)` `typeKids(k|null)` `isUnder(t, root)` `typePath(k)` `propsOf(k)` [[項目, 定義した種類]] `entsOfType(k, deep=true)` `entRoot(id)` / 関係: `relsOf(id)` [{role, other, since, i}]
- カテゴリ: `DB.cats[k]` {n, p?, c?, fx?, sp?} `catAnc(k)` `catKids(k|null)` `catName(k)` `catPath(k)` `catRoot(k)` `catColor(k)` `catFx(k)`(固定|変動|null) `catSp(k)`(日常|非日常|null) `catGuess(payee, text)`
- お金(id = 支払いの act の id): `itemAmt(品目)` `itemTax(品目)` `usersOf(品目)` `payItems(a)`(品目なしは合計1行 virtual:1) `payTotal(a)` `paidOf(a)` [{who, amt, how}] `myPaid(a)` `payMethod(a)` `whoName(w)` `whoShort(w)` `peopleOf(id)`(関わった人。自分 'me' を含む) `xOf(id)`(支出 or null) `xShares(id)` `xAmt(id)`(自分の負担) `xItems(id)` [{i, n, price(自分の額), full, how:'全額'|'1/2'|'按分'|'奢り', cat, memo, unk}] `xCats(id)` [{cat, amt, idx}] `xFx(id)` `xSp(id)` `balanceOf(id)` {amt(>0 貸し), who:[id], open, settled} `balanceText(b)`(花子さんに貸し ¥2,340) `openBalances()` [[id, act, bal]] `personBalances()` [{who, amt, ids}] `isShared(id)` `unreadCount(a)` `basisGuess(items)`('items' か null=聞く) `lnAmts(lines)`
- 家計簿: `expsIn(ym)` [[id, act, 自分の額]](新しい順) `monthSum(ym)` {total, fixed, variable, special, roots:[{cat, amt}], n} `catSum(ym, k)` `daySpend(d, {noFixed})` `paceOf(ym, 日数)`(変動費の累計の配列)
- エンティティ: `spendWith(eid)` `entStats(eid)` {last, year, total, spend, weeks[12], bal, next}
- ToDo: `DB.tasks[id]` {t, due?, done?, list, pri?, act?, ents?, rep?:{u}, memo?, sub?:[{t, d}], payAct?} `taskOpen(t)` `taskLate(t)` `tasksDue()` [[id, t]](期限が今日以前の未完了。「今日まで」の件数は必ずこれ) `tasksOfAct(id)` `tasksOfEnt(eid)` `DB.lists`
- メモ: `DB.memos[id]` {body, at, ents, act?, pin?} `memosSorted()`(ピン留めが先・新しい順) `memosOn(d)` / ノート: `DB.notes[eid]` {upd, ver, body:[[見出し, 本文]], subs:[[名前, 補足, 本文]]}
- `recallDays()` / `searchAll(q)` [{type:'act'|'item'|'ent'|'memo'|'task'|'note'|'day', id, title, sub, date, href, amt?, plan?, done?}] / `parseQuick(s)` `parseTask(s)` `parseDateWord(s)` / `dataCounts()`

### 書く(全て `{ok:true, label, undo(), …}` か `{ok:false, why}` を返す → `done()` に渡す)
- 日: `daySetDiary(d, 文)` `dayAddPhoto(d, ひとこと)` `dayRemovePhoto(d, i)`
- アクティビティ: `actAdd({kind, title, date, date2?, start?, end?, allDay?, state:'done'|'plan', parent?, roles:[{r, e|name}], note?, rep?:{u:'day'|'week'|'month'}, alarm?})` → {id} / `actUpdate(id, 直す項目だけ)`(消す項目は null)/ `actRemove(id)`(子・支出ごと)/ `actMove(id, 親id|null)` / `planRecord(id, {start?, end?, amt?, how?})` → {id}(予定を記録にする。仮の回も可。支払い予定は支払い+支出になる)
- 支払い: `paySave({id?, date, start, parent?, payee:{e}|{name}, roles?, items:[{n, price | lines:[{n, k:'amt'|'pct', v}], tax, users:[who], memo?, unk?, unread?}], amt?(品目なしの合計), payers?:[{who, amt, how}], settled?, unread?:{amt?, payee?}, note?, exp:{basis, n?, me?, cat, itemCat?, sp?, fx?, memo?}})` → {id} / `expUpdate(id, 直す項目)` / `itemRemove(id, 番号)` / `paySettle(id か配列, on=true)` / `payConfirm(id)`(読み取りを確かめた)
- ToDo: `taskAdd({t, due?, list?, pri?, act?, ents?, rep?, memo?, sub?})` → {id} / `taskUpdate(id, patch)`(消す項目は null)/ `taskToggle(id)` → {done} / `taskRemove(id)` / `listAdd(名前)` → {id} / `listRemove(id)`
- メモ: `memoAdd(本文, {ents, act})` → {id} / `memoUpdate(id, {body?, ents?, act?})` / `memoPin(id)` → {pin} / `memoRemove(id)`
- エンティティ: `entAdd({n, types, facts?})` → {id} / `entUpdate(id, {n?, types?, facts?})` / `factSet(id, 項目, 値, {keepHist, why, since})`(値を空にすると消える。keepHist で前の値を履歴へ)/ `entRemove(id)` / `relAdd({a, b, role, inv?, since?})` / `relRemove(i)`
- 種類: `typeAdd(名前, 親?)` → {key} / `typeUpdate(key, {n?, p?})` / `typeRemove(key)` / `typePropAdd(key, 名前)` `typePropRename(key, 旧, 新)` `typePropRemove(key, 名前)`
- ノート: `noteSave(eid, [[見出し, 本文]])`(版+1)/ `noteSubSave(eid, 番号|null, [名前, 補足, 本文])` / `noteSubRemove(eid, 番号)`
- カテゴリ: `catAdd(名前, 親?)` → {key} / `catUpdate(key, {n?, c?, fx?, sp?, p?})` / `catRemove(key)` / 支払い方法: `DB.methods` `methodAdd(名前)` `methodRemove(名前)`
- 全体: `exportJSON()` `importJSON(text)` → {ok, undo} `resetData()`(取り消せない)

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

## 4. v6.1 で足した共通の関数(退化の監査を受けて)
- model.js: `shopMatches(items)` → 品目名と同じ未完了の買い物 ToDo [[taskId, task]] / `paySave({... doneTasks:[taskId]})`(同じ変更の中で ToDo を完了にし `payAct` を付ける)/ `payDup({id?, date, start, total})` → 重複の疑いのある支払い [[id, act]] / `photosOfAct(id)` [{d, i, t, cap}] と `dayAddPhoto(d, ひとこと, actId?)` / `recordsOfPlan(ひな形id)` [[id, act]] / `oldestYm()` / `listRename(id, 名前)` `listSetShop(id, エンティティid|null)` / `taskToPlan(taskId, {date, allDay, start, end})` → {id} / `planToTask(actId)` → {id} / `monthSum(ym).unset`(固定・変動が未指定の額。variable には入らない)
- parts.js: `paySlip(id, {cat})`(そのカテゴリに計上した額を右に出し、全額と違えば「支出 ¥全額」の札)。支払いの面に特別感の印(非日常 / 未設定)。`tlAct(id, {roleOf: エンティティid})`(その相手の役割の札を出す)。`dayHead` に日記の一言。
- core.js: トーストは3つまで表示し、残りは順番待ち(取り消しは消えない)。画面を移ると、未保存でないシートは閉じる。保存に失敗すると知らせる。
