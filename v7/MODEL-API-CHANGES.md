# model.js v6.4.2 → v6.5 画面担当向けの変更表

保存の鍵は `prmock-v7`(旧データは使わない)。`node v7/tools/model_test.mjs` で model.js を検査できる。

## フィールド(旧 → 新)
- `a.date` → `aDate(a)` / `a.date2` → `aDate2(a)`(別の日なら日、無ければ null)/ `a.start`('HH:MM')→ `aStart(a)` / `a.end`('HH:MM')→ `aEnd(a)`。**`a.start` `a.end` は TS('YYYY-MM-DD' か 'YYYY-MM-DDTHH:MM')**。
- `a.allDay` → `isAllDay(a)`(start が日だけ)/ `a.hol` `kind:'holiday'` → 廃止(`DB.holidays`, `holOn(d)`)。
- `a.rep` → 廃止(`DB.routines[id]`)/ `a.src` → `a.routine` / `a.tpl`(仮の回) → `a.routine`。
- `a.planned:{start,end}` は TS。
- `DB.v` `DB.born` `DB.ibx` → `DB.meta.v` `DB.meta.born` `DB.meta.seeded`。
- `DB.methods`(名前の配列)→ ents の `method` 種類。`payers[].how` は支払い方法の ent の id(名前は `methodName(id)`)。
- `DB.days[d].photos` → `DB.photos`(`photosOn(d)`)。`DB.days[d]` は `{diary}` だけ。
- `DB.rels`(配列)→ オブジェクト `{id: {a,b,role,inv?,from?,to?}}`。`r.since` → `r.from`、`r.sym` → 廃止。`relsOf` の `i` → `id`、`since` → `from`/`to`。
- `ent.facts[].since/hist` → 平らな `[{k,v,from?,to?,why?,rare?}]`(`factsNow` `factHist` `factAt`)。
- `DB.notes[eid]`: `ver` 廃止、`body:[[h,t]]` → `[{h,text}]`、`subs:[[n,sub,text]]` → `[{id,n,sub,text}]`。
- 時刻の文字: `memos.at` `jots.at` `log.at` `notes.upd` は `'YYYY-MM-DDTHH:MM'`(旧 `'YYYY-MM-DD HH:MM'`)。`nowStamp()` も同じ。
- `roles[]`: `{r, e}` だけ(`name` は保存されない。入力で `{r, name}` を渡すと stub の id になる)。`payers[].who` `items[].users` も id だけ('other' は無い)。
- `pay.plan` → 廃止(支払い予定は `state:'plan'` + `pay:{amt, payers:[], items:[]}` + `DB.exps[id]`)。
- `pay.settled` → 廃止(`kind:'settle'` の Act。`settlesOf(payId)`)。
- `pay.unread` `items[].unread` `row.tent` → `row.unsure`(true か項目名の配列)・`items[].unsure`。`via` は 'rule'|'llm'|'ocr'|'gps'。
- `items[]` に `id` が付く。`exp.itemCat` のキーは品目の番号 → 品目 id。`xCats(..)[].idx`(番号)→ `ids`(品目 id)。`xItems(..)` の各行に `id`。
- `KINDS`: `wake` `holiday` を削除、`sleep` は「睡眠」(期間)、`settle`「精算」を追加。
- `E('me')` は本物の行(`DB.ents.me`)。`entRemove('me')` は失敗。名前だけの相手は `ent.stub === 1`。

## 関数
### 消えた
- `payConfirm(id)` `unreadCount(a)` → `confirmRows([{t:'acts', k}])` `unsureCount(a)` / `dayAddPhoto` `dayRemovePhoto` → `photoAdd` `photoRemove` / `recordsOfPlan` → `recordsOfRoutine` / `isTent`(m-inbox)→ `isUnsure(row)`。

### 名前・引数・戻り値が変わった
- `actAdd` `actUpdate` の入力: `{date, date2, start, end, allDay}` → `{start, end}`(TS)。`rep:{u}` を渡すと決まりを作り、戻り値は `{id:'決まりid@最初の日', routine}`。
- `actUpdate('決まりid@日付', patch)` は決まりを直す(時刻は at/endAt へ)。`rep:null` は決まりをやめて、その日の1回だけの予定にして `{id}` を返す。`actRemove('決まりid@日付')` はその回だけ skip。`A('決まりid')` は null。
- `planRecord(id, {start?, end?, amt?, how?})`: start/end は TS(`'HH:MM'` も可)。支払い予定は state を done にして payers・品目を作る。`how` は支払い方法の id(名前でも可)。
- `paySave`: `{date, start('HH:MM')}` → `{start: TS}`(`date` を併せて渡せば 'HH:MM' も可)。`settled` 廃止。`unread:{amt,payee}` → `unsure:['amt','payee']`、品目の `unread` → `unsure`、`via:'ocr'` を受ける。`exp.itemCat` は品目 id キー。`payee`/`payers`/`users` は `{name}` 可。
- `payPlanSave({id?, start, payee, title?, amt, cat, rep?})` を新設(支払い予定は actAdd で作れない)。
- `itemRemove(actId, 品目id)`(旧: 番号)。
- `paySettle(ids, on=true, {who?, date?, how?})`: on は相手ごとに settle の Act を作る(精算するものが無ければ失敗)。off は pays から外す。
- `balanceOf(id)` → `{amt, who, open, settled, debts}`(新: `debts`)。新設 `debtsOf(id)` `settlesOf(payId)`。`personBalances()` は未精算の debts の相手ごとの合計。
- `whoName(w, name)` `whoShort(w, name)` → 第2引数を廃止。`roleName(r)` = `E(r.e).n`。`roleIn` に「支払い方法」「精算」を追加。
- `payMethod(a)` は名前を「・」でつなぐ(見た目は同じ)。`methodAdd(名前)` → `{id}`、`methodRemove(id)`(使われていれば失敗)。新設 `methodsAll()`→[[id,ent]] `methodDefault()` `methodName(id)`。
- `relAdd({a,b,role,inv?,from?})` → `{id}`、`relRemove(id)`(旧: 配列の番号)、新設 `relUpdate(id, patch)`(`to` を入れる = 終わった)。`relsOf(id, {ended:false})`。
- `factSet(id, k, v, {keepHist, why, since})`: 履歴は同じ項目の行に `to`/`why` を入れて残し、新しい行 `{from}` を足す。**値を空にすると、その項目の行を全て(履歴も)消す。keepHist を付けると今の行だけ**。
- `entUpdate(id, {stub:null})` で登録済みになる。`entsOfType(k, deep=true, {stubs:false})` と新設 `entsAll({stubs})` は既定で stub を除く(`isStub(id)`)。
- `entRemove(id)`: 支払い(払った人・使った人・精算)から指されている相手は、消さずに名前だけの stub に戻す。
- `noteSave(eid, [{h,text}])` `noteSubSave(eid, subId|null, {n,sub,text})` → `{id}` `noteSubRemove(eid, subId)`。
- `photosOfAct(id)` → `[[id, photo]]`(旧: `[{d,i,t,cap}]`)。`dayOf(d)` → `{diary}`。
- `taskToPlan(id, {start, end?})`(TS。旧形 `{date, allDay, start, end}` も受ける。ToDo に rep があれば決まりを作る)。`planToTask(id)` は決まり(`決まりid` か `決まりid@日付`)も受け、決まりを消して ToDo の rep にする。
- `parseQuick`: 返す `date`/`start('HH:MM')`/`end` → `start`/`end`(TS)。ents に自分・支払い方法は入らない。
- `payDup({id?, start, total})`(`date` も可)。`searchAll`: 品目の id は `actId/品目id`、繰り返しの決まりは `#/a/決まりid@次の日` で出る。
- `dataCounts()`: `ents` は人・場所など(自分・支払い方法・stub を除く)。`photos` `routines` を追加。
- `importJSON`: `meta.v` が 65 でなければ失敗。
- 新設: `sleepWake(ts)` `holOn(d)` `photosOn(d)` `photoUpdate` `routineAdd/Update/Remove/Skip` `repOn(routine, d)`(旧は act を渡した)`isUnsure` `confirmRows` `isStub` `spanOn(a, d)` `aDate` `aDate2` `aStart` `aEnd` `isAllDay` `dOf` `tOf` `hasT` `tsOf` `isTS` `factsNow` `factHist` `factAt`。
- `sortKey(a)` は `a.start`(TS)を返す(旧は 'HH:MM')。日の中の並びは `actsOn(d)` の順に従う。`byTime` は TS 順。
- `isLate(a)`: 終日は「その日が TODAY より前」。`upcoming` `pendingPlans` は変わらない。
- `commit` `fail` `_logWhat` `_logPush` `TABLES` `uid` `nowStamp` `save` `dbNormalize` `dbSeedInbox` は名前・役割そのまま(`TABLES` は photos routines rels holidays を含む)。

## 画面担当が特に注意すること
- `DB.ibx` → `DB.meta.seeded`(m-inbox の `seedInbox` の判定。`dbSeedInbox` は更新済み)。m-inbox の `a.date` `a.tent` `diaryAppend`(`{photos:[]}` を足さない)・`candToRec`(`paySave` の入力)・`tentConfirm` は新形に直すこと。
- 仮の回の id は `決まりid@日付`。`planRecord` すると別の id の Act になる。決まりの再記録を判定するのは `a.routine + planned の日`。
- 日をまたぐ予定は `actsOn` が複数の日に返す。描画は `spanOn(a, d)` の `from`/`to` を使う。
- 名前だけの相手(stub)は一覧から外れる。登録は `entUpdate(id, {stub:null})`。
