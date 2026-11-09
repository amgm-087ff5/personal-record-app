# v5 画面担当が使う API(土台の担当の報告から本体がまとめた)

## core.js
- 小道具: `esc(s)` `uid(p)` `yen(n)`(data.js) `fmtMD(d)` `fmtMDW(d)` `dowJ(d)` `relDay(d)` `minsBetween(a,b)`(b−a の分) `hapt()`。定数 `TODAY`(2026-10-31) `NOW`(13:20) `EXTRA`
- 保存: `save()`(plSettle してから保存) `load()` `resetData()` `exportData()` `importData(file)`(Promise)
- ルーター: `route(name, fn(id,q), {tab})` `go(hash)` `goBack()` `parseHash()` `render(keepScroll)` `rerender()` `after(fn)` `currentTab()`
  - ルートの持ち主: home→home / timeline→activities, day / detail→a, i, ev / plan→todo, task / ledger→ledger, x, cats / entities→entities, e, types / memo→memo, notes, n / settings→settings / create.js はルートなし(作成シートの登録)
  - 画面の左右余白は `.screen` が持つ。画面の中で足さない
- シート: `openSheet({title, body, primary:{label,on,disabled}, secondary:{label,on}, dirty(), onClose, full, id})` `closeSheet(force)` `sheetOpen(id?)` `setSheetBody(html)` `setSheetPrimary({label,on,disabled})`。下スワイプで閉じる・dirty の確認・重ねは core がやる
- トースト・確認: `toast(msg,{undo})`(undo は関数) `confirmIn({title, body, ok:{label}, on})`(取り消せない操作だけ。赤にしない)
- 作成: `addCreate({key,label,icon,primaryOn:[tab],run})` `openCreate()`
- ナビ: `setNavBadge(tab, fn)`(fn は件数を返す。ToDo の期限切れは plan.js が登録) `navSwitch()`
- テーマ: `setTheme(v)`('' / 'light' / 'dark') `getTheme()`

## parts.js(HTML 文字列を返す。sub/meta/kids/body/html は esc 済みを渡す。on は onclick に入る JS 文字列。chips/seg の on は関数名)
`icon(name,cls)` `navIcon(name)` `iconBtn(name,label,on)` `largeTitle(title,{actions:[{label,icon,on,primary}]})` `appbar(title,{back,actions})` `sh(title,{count,act:{label,on}})` `row({t,s,right,href,on,lead,chev})` `list(rows)` `objRow(o)` `prop(k,v,{on,html})` `dayCard(d,{sum,diary,href})` `monthCard(ym)` `axRow({time,end,node:'ev'|'plan'|'late'|'moment'|'pay',title,meta,amt,href,body,kids,future})` `nowLine()` `axEdge(text,future)` `capsule({kind:'done'|'plan'|'late',time,title,sub,href})` `ticket({stub:'日常'|'非日常'|'未設定',title,sub,amt,href,onclick,size:'sm'|'md'|'lg',fill,label})` `payBar(aid)` `splitBar(segs:[{v,kind:'me'|'other'|'owe',label,href}],total)` `payShell(aid,{mode:'row'|'kid'|'pay'|'x',xid})` `eref(id,name)` `tag(label,{color,kind:'owe'|'late'})` `catTag(k,{path})` `chips(items:[{k,label}],cur:[],on)` `seg(items,cur,on)` `periodNav(label,prev:{on,dis,text},next)` `checkbox(on,{late,click,label})` `toggle(on,{click,label})` `field(label,input)` `input({type,name,id,value,ph,oninput,onchange})` `select({options:[{v,l}],value,name,onchange})` `summaryCard({metrics:[{v,l,href|on}]×3,actions:[{label,icon,on,primary}]×2})` `empty({shape:'list'|'day'|'todo'|'memo'|'entity'|'ledger'|'plan'|'pay'|'type'|'cats',text,action:{label,on}})` `banner(kind:'warn'|'info',html)` `readMark({on,label})` `progress(p,{label})`
見本: `v5/tools/gallery.html`(クラス名は p- 接頭辞)

## data.js / plans.js / money.js
- データ: `A X E PLN EV TD TYPES CATS NOTES MEMOS LISTS TYPE_PROPS TREND COLORS PAY_METHODS`
- 既存の小道具(v4 と同じ): `typeAnc isUnder roleOf actsOf relsOf dayOf dateOf isSpan timeText parentAct kidActs ancOf ancIds descOf md mdw addDays catAnc catName catPath catColor catFx xFx catSp xSp ROOTS evOn actsOn placeOf nextOn tdOpen shopOf payeeOf payLabel xTitle whoOf xDate propsOf memoNow memoAdd memosSorted todosOf todosDueOn todoToEvent eventToTodo payItems payTotal paidOf myPaid payMethod peopleOf itemMine xShares xAmt xItems xCats xAmtIn mineOf balanceOf openBalances basisGuess makePayment xEnsure whoName whoShort usersOf usersText balanceText toggleUser itemAmt lnV lnAmts lnName lnSave BASIS`
- 新しい関数(戻り値は `{ok, undo}`、断る時は `{ok:false, why}`。取り消しは `undoRun(undo)`。トーストの undo には `()=>{undoRun(r.undo);rerender()}` を渡す)
  - エンティティ: `entAdd({n,t,facts,rels})→{ok,id,undo}` `entUpdate(id,{n,t,facts,rels})` `entRemove(id)` `entTypeAdd(id,t)` `entTypeRemove(id,t)` `spendWith(id)` `spendWithParts(id)`
  - 種類: `typeAdd(name,parent)→{ok,key,undo}` `typeRename(key,name)` `typeMove(key,parent)` `typeRemove(key)` `typePropAdd(t,name)` `typePropRemove(t,name)` `typePropRename(t,old,new)`
  - メモ: `noteSave(id,[[見出し,本文]])`(版+1・更新日時) `noteSubAdd(id,name,note)` `notesSorted()` `memoUpdate(id,{body,links,rec})` `memoPin(id,on)` `memoRemove(id)`
  - 記録: `recUpdate(aid,{title,time,end,roles})`(支払いの題は変えない) `recDelete(aid)` `countKids(id)`
  - 予定・ToDo: `planUpdate(key,patch)`(null で消す) `planRemove(key)` `todoToggle(key,d)` `todoDueCount(d)`(期限が d 以前の未完了)。ToDo の支払いのひもは `payRec`
  - お金: `itemRemove(aid,ix)` `itemTax(it)` `TAX_DEFAULT`(10) `xSetCat(xid,cat)` `xSetFx(xid,v|null)` `xSetSp(xid,v|null)` `xSetMemo(xid,s)` `xItemCatSet(xid,ix,cat|null)` `readMarkSet(aid,'amt'|'payee'|'item',ix,on)` `readMarkConfirm(aid)` `readMarkList(aid)` `readMarkCount(aid)`
- `makePayment(o)` は保存しない(呼んだ後に `save()`)。`o.unread` と `items[i].unread` で読み取り値を渡せる
- 品目が0件の支払いは合計 amt を1行として残る。画面では「品目 なし」の empty を出す
