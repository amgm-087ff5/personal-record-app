# v6.5 データモデル(正本)

2026-11-15。v6.4.2 のモデルを「現実世界の反映・シンプル・実用」で見直し、一から作り直した版。
既存の保存データとの互換は取らない(ユーザー指示 2026-11-15「現行のデータは気にせずいちから修正してよい」)。保存の鍵は `prmock-v7`。
経緯と理由: Notion 作業スペース「v6 データモデル: 現行の記録(Mermaid)とレビュー、改訂案 v6.5」。

## 0. 約束(5つ)
1. **表1つ = 現実の物事1つ**。acts は「実際の1回」だけ。繰り返しの決まり・祝日・写真は別の表。
2. **参照は全て id**。人・店・支払い方法は ents の id。自分(`me`)も、名前だけの相手(`stub`)も ents の行。配列の番号では指さない(例外: 親の行の中だけで完結する並び = tasks.sub)。
3. **期間は「始まり・終わり」の2項目**。act は `start`・`end`、事実と関係は `from`・`to`。
4. **未確認は `unsure` の1つ**。確かめる = `unsure` を消す。
5. **同じものは同じ形**。予定と記録は state だけが違う(支払い予定も、支払いと同じ形)。
引き継ぐ約束: 計算で出せる値は保存しない / 支払い(事実)と支出(家計簿)は分ける / 変更は全て `commit()` を通る / 画面は model.js の関数だけでデータを読み書きする。

## 0.5 UIを変える時(必読。機械検査と連動)
UI(`js/s-*.js` `js/parts.js` `js/core.js` `js/m-*.js` `index.html`)を変える前に、このファイルと `API.md` を読む。
1. 新しいデータを読み書きしたい・新しい意味(評価・目標のようなもの)を持たせたい時は、**先に既存の型で足りないか調べる**(ents・facts・routines・acts・tasks・memos・photos)。例: 2026-11-18 の「評価」= 事実 `評価`、「目標」= プロジェクト + 繰り返し(新しい表は作らなかった)。足りなければ、UIより先に `model.js` → このファイル → `API.md` → `tools/model_test.mjs` の順に直す。
2. 画面は `model.js` の関数だけでデータにさわる(`DB` は読むだけ。書き換えない)。
3. コミットメッセージに `Data-Model:` の行を必ず書く(`Data-Model: changed` / `Data-Model: unchanged — 理由` / `Data-Model: docs-na — 理由`)。UI を変えたのにモデル一式(model.js・MODEL.md・API.md・tools/model_test.mjs)が変わっていなければ `unchanged — 理由` が必要。model.js を変えたのに文書・検査が変わっていなければ `docs-na — 理由`(例: 色の定数だけ)が必要。
4. `sh tools/check_all.sh`(中の model_sync)が通ること。
気づかせる仕組み: リポジトリ直下の `CLAUDE.md`、`.claude/settings.json` のフック、`.githooks/`、CI(`.github/workflows/model-sync.yml`)。しくみの説明は `tools/MODEL-GUARD.md`。

## 1. 形
```
時刻(TS) = 'YYYY-MM-DD'(日だけ = 終日) か 'YYYY-MM-DDTHH:MM'(日と時刻)。文字のまま大小を比べられる。
月・日の粗い日付(事実・関係の from/to) = 'YYYY' か 'YYYY-MM' か 'YYYY-MM-DD'

DB = {
  meta    : { v:7, born:'YYYY-MM-DD', seeded?:1 }
  days    : { 'YYYY-MM-DD': {diary} }                          日 = 鍵。日記だけ
  photos  : { id: {at:TS, cap?, file?, act?, ents?:[], geo?:{lat,lng}} }
  acts    : { id: Act }                                        実際の1回(記録も予定も)
  routines: { id: Routine }                                    繰り返しの決まり
  exps    : { 支払いのactId: Exp }                             支出 = 家計簿(支払い1件に0か1件。予定の支払いにも付く)
  tasks   : { id: Task }
  lists   : { id: {n, shop?} }
  ents    : { id: Ent }                                        人・場所・物・会社・プロジェクト・支払い方法。自分は id 'me'
  rels    : { id: {a, b, role, inv?, from?, to?} }             a から見て b は role。b から見ると inv(無ければ同じ呼び名)。to = 終わった関係
  types   : { key: {n, p?, props:[項目名]} }                   種類の木(支払い方法は key 'method')
  notes   : { エンティティid: {upd:TS, body:[{h, text}], subs:[{id, n, sub, text}]} }
  memos   : { id: {body, at:TS, ents:[], act?, pin?, unsure?, via?, jot?} }
  cats    : { key: {n, p?, c?, fx?, sp?} }
  jots    : { id: {body, at:TS, src:'text'|'voice', llm?} }    雑記(入力したままの文)
  log     : [ {i, at:TS, op, t, k, ty, s, lb} ]                履歴(最大1000。commit が書く)
}
Act = { kind, title?, start:TS, end?:TS, state:'plan'|'done', parent?, roles:[{r, e}],
        note?, alarm?, routine?(元の決まりの id), planned?:{start,end?}(予定の時の時刻),
        unsure?, via?, jot?, pay?:Pay, settle?:Settle,
        photo?(支払いのレシート写真のハッシュ。重複の検出に使う) }
   - end が無い = 瞬間 / ある = 期間。end は日をまたげる(睡眠 11-14T23:30 → 11-15T07:00、旅行 11-21 → 11-23)。
   - start が日だけなら end も日だけ(か無し)。
   - kind は KINDS の鍵: meal talk work move shop play health trip event sleep pay settle。
     (v6.4 の wake・holiday は無い。睡眠は1件の期間。祝日は表に持たず `holOn(d)` が規則で計算する)
Routine = { every:'day'|'week'|'month', from:'YYYY-MM-DD', until?, skip?:['YYYY-MM-DD'],
            tpl:{ kind, title?, at?:'HH:MM', endAt?:'HH:MM', roles:[{r,e}], note?, alarm?, pay?:{amt}, exp?:{cat} } }
   - 各回は「決まりid@日付」という仮の回(保存しない。state:'plan', virt:1, routine:決まりid)。
   - 記録すると routine 付きの Act になる。その回の日付 = (planned.start か start)の日付。
   - skip = その回だけ無し。
Pay = { amt?(合計。品目が無い時・印字の合計), payers:[{who, amt, how?}], items:[Item] }
   - 支払い予定 = state:'plan' の同じ形(pay:{amt, payers:[], items:[]} + exps[id]:{basis:'items', cat})。v6.4 の pay.plan は無い。
   - who・users は ents の id(自分は 'me')。how は支払い方法の ent の id。
Item = { id, n, price | lines:[{n, k:'amt'|'pct', v}], tax, users:[who], memo?, unk?, unsure? }
Exp = { basis:'items'|'even'|'amount', n?, me?, cat, itemCat?:{品目のid: カテゴリ}, sp?, fx?, memo? }
Settle = { who(相手の ent id か null), dir:'in'|'out'(in = 受け取った / out = 返した), amt, how?, pays:[支払いのactId] }
   - 精算 = kind:'settle' の Act(日付のある出来事)。まとめて精算 = pays が複数の1件。v6.4 の pay.settled は無い。
   - ある支払いの、ある相手との貸し借りが精算済み = その相手の settle の pays にその支払いが入っている。
Ent = { n, types:[種類], stub?:1(名前だけ・未登録), geo?:{lat,lng}, facts:[Fact] }
Fact = { k, v, from?, to?, why?, rare? }                       to が無い行 = 今の値。to がある行 = 前の値(why = 変わった理由)
   - 特別なキー: '評価' = 星1〜5の整数(文字で持つ)。ent なら何にでも付く(人・店・場所・物・会社・書類・プロジェクト)。付け直しは前の行に to を付けて新しい行(日まで)。
   - プロジェクトの '期間' = 「始まり〜終わり」(年・月だけでも可。「〜」が無ければ期日だけ)、'状況' = '進行中' など。
Task = { t, due?, done?(日付), list, pri?, act?, ents?:[], rep?:{u}, memo?, sub?:[{t,d}], payAct?, unsure?, via?, jot? }
unsure = true(行の全体が未確認) | ['amt','payee', …](その項目だけ未確認)。品目は Item.unsure:1
via = 'rule' | 'llm' | 'ocr' | 'gps'(推し量った出所。手で入れた行には無い)
```

## 2. 計算(保存しない)
- 祝日: 保存しない。`holOn(d)` が日本の祝日を規則で計算する(1980〜2099 年。固定日・第n月曜・春分/秋分の近似式・振替休日・国民の休日)。
- 日に属する: act は start の日〜end の日の全ての日に出る(`actsOn`)。写真・メモは at の日。
- 長さ: end − start(日をまたいでも正しい分)。
- 記録待ち: state:'plan' で、終わり(無ければ始まり)を過ぎたもの。
- 自分の負担・月の合計・カテゴリ別: v6.4 と同じ(支払い + 支出から計算。予定は含めない)。
- 貸し借り(相手ごと): `debtsOf(支払いid)` → [{who, amt(>0 相手が自分に負う / <0 自分が負う), settle(精算の act id か null)}]
  1. 合計 T、自分の負担 M = xAmt、自分が払った額 P。差 N = P − M(0 なら貸し借り無し)。
  2. ほかの人 = 使った人・払った人・(この支払いと親の)同席/参加 から自分を除く。
  3. ほかの人の負担: basis 'items' は品目ごとに 額/使った人数。'even'・'amount' は (T − M) を等分。
  4. N > 0(貸し): 不足(負担 − 払った額 > 0)のある相手に、不足の比で N を配る。N < 0(借り): 余り(払った額 − 負担 > 0)のある相手に、余りの比で配る。
  5. 相手が1人も分からなければ who:null の1件。端数は先頭に寄せ、合計が必ず N になる。
- 相手ごとの差し引き(`personBalances`)= 未精算の debts の合計。
- 評価: 保存するのは事実 `評価` の行だけ(新しい表は無い)。いまの評価 = `to` の無い行(`ratingNow`)、前の評価 = `to` のある行(`ratingHist`、新しい順)。付け直しは `rateSet`(日まで持つ。同じ星は変化なし。null で外す)。`entUpdate` の facts に評価が無ければ既存の評価は残す。集計は今の値だけを使う。星の呼び名は `RATE_LABELS`(1・2 は仮)。
- 目標: 状況が '進行中' のプロジェクトの ent(`goalsNow`。期日が近い順)。毎日の行動 = tpl.roles に {r:'プロジェクト', e} を持つ routines(`projRoutines`)、実績 = routine 付きの state:'done' の act。
- 期日: 事実 `期間` を読む(`projSpan`。月だけなら月初・月末)。期日まで = `projDaysLeft`(過ぎていれば負)。
- 連続日数: `streakOf(プロジェクトid)` → {n, unit, todayDue, todayDone}。予定の回(skip は無いものとして数えない)が、今日から過去へ記録済みで続く数。今日が未記録でも途切れない。毎日の繰り返しだけなら単位「日」、週・月を含めば「回」。保存しない。
- ホームのカード: `goalCard(プロジェクトid)` → {name, to, daysLeft, streak, today:[{id, title, done}]}。today[].id の未記録分は仮の回の id なので「記録にする」= `planRecord(id)`。

## 3. 現実の場面
| 場面 | 表し方 |
|---|---|
| 23:30 に寝て翌 7:00 に起きた | kind sleep、start 11-14T23:30、end 11-15T07:00 の1件(両方の日に出る。長さ 7時間30分) |
| 2泊3日の旅行と中の食事・支払い | 旅行 start 11-21、end 11-23。食事・支払いは parent |
| 毎月27日に家賃 | routines: every month、tpl に pay:{amt} と exp:{cat}。払ったら routine 付きの支払い |
| 今月だけジムが休み | その決まりの skip に日付 |
| 花子さんと割り勘、後日まとめて返してもらった | 支払い2件(payers 自分)。精算1件: settle {who:花子, dir:'in', amt, pays:[2件]} |
| 名前しか知らない鈴木さんと会った | ents に stub の鈴木さん。roles の同席が指す。登録 = stub を外す |
| 花子さんが転居・改姓した | 事実「住所」の今の行に to、新しい行を足す |
| 同僚が退職した | 関係に to(消さない) |
| レシートを読み取った | 支払い1件、unsure に未確認の項目、via 'ocr'。確かめると unsure が消える |
| 来月の支払い予定 | state:'plan' の支払い(pay.amt)+ 支出(cat)。払ったら state を done に |
| 本に5つ星を付け、後で3に付け直した | その物の ent の事実 `評価`。5 の行に to、新しい行 3(今の値)。履歴は `ratingHist`、集計は今の値だけ |
| 「顔やせする」(期日 12/31)を毎日やる | プロジェクト ent(種類 生活、期間 …〜2026-12-31、状況 進行中)+ routines(every day、tpl.roles にそのプロジェクト)。連続日数は `streakOf`(保存しない) |

## 4. モデルの外に置くもの・未決
- 位置情報の生の記録(GPS の履歴)はモデルの外(取り込み元)。取り込んだ結果は via:'gps' + unsure の act(滞在・移動)として入る想定。場所の座標は Ent.geo、写真の位置は photos.geo。取り込みは未実装。
- 写真のファイル(photos.file)の保存先は未決。プロトタイプはひとことだけ。
- 未決: タイムゾーン(その場所の時計の時刻のまま持つ)/ 外貨(円の整数)/ 収入・口座間の振替(チャージ)・返金 / 端末間の同期(行の更新時刻)/ アクティビティの種類を利用者が増やす。
