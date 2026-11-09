/* =====================================================================
   設定(#/settings) — 接頭辞 st_(関数)/ st-(CSS)。見た目は css/screens/settings.css。
   1 表示(テーマ) / 2 記録の分類(カテゴリ・エンティティの種類・ToDo のリスト) / 3 データ(書き出し・読み込み・初期化) / 4 版
   赤は使わない(赤は いま・遅れ だけ)。取り消せない操作は confirmIn で確認する。
   ===================================================================== */

/** 読み込みに使う隠しの入力(ファイル選択) */
const stPickFile = () => document.getElementById('st-file')?.click();
/** データを書き出す(JSON をダウンロード) */
function stExport() { exportData(); toast('データを書き出しました') }
/** 読み込みの確認 → 全データを置き換える */
function stImportFile(input) {
  const f = input.files && input.files[0];
  input.value = '';
  if (!f) return;
  confirmIn({
    title: 'データを読み込む',
    body: banner('warn', esc('今のデータを、選んだファイルの内容に置き換えます')),
    ok: {label: '読み込む'},
    on: () => importData(f)
      .then(() => { toast('データを読み込みました'); rerender() })
      .catch(e => toast(e.message || '読み込めませんでした')),
  });
}
/** 初期化の確認(文言は実際に消える範囲をそのまま書く) */
function stResetAsk() {
  confirmIn({
    title: 'データを初期状態に戻す',
    body: banner('warn', esc('全てのデータ(記録・予定・メモ・エンティティ・カテゴリ)を初期状態に戻します')),
    ok: {label: '初期状態に戻す'},
    on: () => resetData(),
  });
}
/** ToDo のリストの一覧(件数は未完了の数。押すと名前を変える・下で足す) */
function stListsBody() {
  const rows = Object.entries(LISTS).map(([k, l]) => row({
    t: l.n,
    right: `${Object.keys(TD).filter(t => TD[t].list === k && !TD[t].done).length}件`,
    on: `stListRename('${esc(k)}')`,
    chev: true,
  }));
  return list(rows)
    + sh('リストを足す')
    + `<div class="st-list-add">${field('名前', input({id: 'st-list-new', ph: '例: 仕事'}))}</div>`;
}
function stLists() {
  openSheet({title: 'ToDo のリスト', body: stListsBody(), id: 'st-lists', primary: {label: 'リストを足す', on: () => stListAdd()}});
}
/** 名前の重複・空を断る(LISTS の中で同じ名前が無いか) */
function stListCheck(name, except) {
  const n = String(name || '').trim();
  if (!n) return {ok: false, why: '名前を入れてください'};
  if (Object.entries(LISTS).some(([k, l]) => k !== except && l.n === n)) return {ok: false, why: '同じ名前のリストがあります'};
  return {ok: true, n};
}
/** リストを足す(LISTS に入れて save。取り消しは外す) */
function stListAdd() {
  const el = document.getElementById('st-list-new');
  const c = stListCheck(el && el.value, null);
  if (!c.ok) return toast(c.why);
  const k = uid('l');
  LISTS[k] = {n: c.n};
  save();
  toast('リストを足しました', {undo: () => { delete LISTS[k]; save(); rerender() }});
  setSheetBody(stListsBody());
  rerender();
}
/** 名前を変える(シートを重ねる。保存で元の一覧に戻る) */
function stListRename(k) {
  const l = LISTS[k]; if (!l) return;
  openSheet({
    title: 'リストの名前を変える',
    body: `<div class="st-list-add">${field('名前', input({id: 'st-list-name', value: l.n}))}</div>`,
    primary: {label: '名前を変える', on: () => stListRenameSave(k)},
  });
}
function stListRenameSave(k) {
  const el = document.getElementById('st-list-name');
  const c = stListCheck(el && el.value, k);
  if (!c.ok) return toast(c.why);
  const old = LISTS[k].n;
  LISTS[k].n = c.n;
  save();
  closeSheet(true);
  setSheetBody(stListsBody());
  rerender();
  toast('名前を変えました', {undo: () => { if (LISTS[k]) LISTS[k].n = old; save(); rerender() }});
}

route('settings', () => {
  const nCat = Object.keys(CATS).length, nType = Object.keys(TYPES).length, nList = Object.keys(LISTS).length;
  const cur = (() => { try { return document.documentElement.dataset.theme || '' } catch (e) { return '' } })();
  const d = new Date(TODAY + 'T00:00');
  return `<div class="st">`
    + largeTitle('設定')
    + sh('表示')
    + list([`<div class="st-theme">${seg([{k: '', label: '端末に合わせる'}, {k: 'light', label: 'ライト'}, {k: 'dark', label: 'ダーク'}], cur, 'stSetTheme')}</div>`])
    + sh('記録の分類')
    + list([
      row({href: '#/cats', lead: icon('cats'), t: 'カテゴリ', right: `${nCat}件`, chev: true}),
      row({href: '#/types', lead: icon('type'), t: 'エンティティの種類', right: `${nType}件`, chev: true}),
      row({on: 'stLists()', lead: icon('todo'), t: 'ToDo のリスト', right: `${nList}件`, chev: true}),
    ])
    + sh('データ')
    + list([
      prop('保存先', 'このブラウザ'),
      row({on: 'stExport()', lead: icon('share'), t: 'データを書き出す', chev: true}),
      row({on: 'stPickFile()', lead: icon('move'), t: 'データを読み込む', chev: true}),
      row({on: 'stResetAsk()', lead: icon('trash'), t: 'データを初期状態に戻す', chev: true}),
    ])
    + `<input id="st-file" class="st-file" type="file" accept="application/json,.json" aria-label="読み込むファイル" onchange="stImportFile(this)">`
    + sh('このモックについて')
    + list([
      prop('版', window.BUILD || '不明'),
      prop('今日', `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日(${dowJ(TODAY)})`),
      row({href: '../v4/', t: 'UI v4', chev: true}),
      row({href: '../v3/', t: 'UI v3', chev: true}),
      row({href: '../', t: 'UI v2', chev: true}),
    ])
    + `</div>`;
}, {tab: 'settings'});

/** テーマの切替(端末に合わせる = ''。保存は core の setTheme) */
function stSetTheme(v) { setTheme(v); rerender() }
