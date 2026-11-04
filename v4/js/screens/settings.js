/* =====================================================================
   設定(#/settings) — UI v4(11/04 指示: 設定系は2枚目のナビから開く)
   節ごとに1枚の面(.list)。行は56px以上、押せる行は右に ›。説明文は書かない(ラベルと値だけ)。
   1 表示(テーマ) / 2 記録の分類(カテゴリ・エンティティの種類・ToDoのリスト) / 3 データ / 4 このモックについて
   見た目は css/s-settings.css。色は tokens.css の変数だけを使う。
   ===================================================================== */

/* 現在のテーマ(端末に合わせる = '') */
const setThemeCur=()=>{try{return document.documentElement.dataset.theme||''}catch(e){return ''}};

/* データの初期化は確認のシートを挟んでから resetData() */
function setResetConfirm(){
  openSheet({title:'データを初期状態に戻す',
    body:banner('warn','このブラウザの記録・予定・メモをすべて消して、初期状態に戻します'),
    footer:`<div class="set-cf"><button class="btn tonal" type="button" onclick="closeSheet()">やめる</button><button class="btn danger" type="button" onclick="resetData()">${IC.trash}初期状態に戻す</button></div>`});
}

route('settings',()=>{
  const nCat=Object.keys(CATS).length,nType=Object.keys(TYPES).length,nList=Object.keys(LISTS).length;
  return `<div class="set">`
    +largeTitle('設定')
    +sh('表示')
    +list([`<div class="row set-theme"><div class="t">テーマ</div>${seg([['','端末に合わせる'],['light','ライト'],['dark','ダーク']],setThemeCur(),"setTheme('$v');rerender()")}</div>`])
    +sh('記録の分類')
    +list([
      row({href:'#/cats',lead:ico('cats'),t:'カテゴリ',v:`${nCat}件`,chev:true}),
      row({href:'#/types',lead:ico('type'),t:'エンティティの種類',v:`${nType}件`,chev:true}),
      row({onclick:"toast('ToDo のリストは未対応です')",lead:ico('todo'),t:'ToDo のリスト',v:`${nList}件`,chev:true})])
    +sh('データ')
    +list([
      prop('保存先','このブラウザ'),
      row({cls:'set-danger',onclick:'setResetConfirm()',lead:ico('trash','var(--danger)'),t:'データを初期状態に戻す',chev:true})])
    +sh('このモックについて')
    +list([
      prop('版',esc(window.BUILD||'不明')),
      prop('今日',`${TODAY.slice(0,4)}年${+TODAY.slice(5,7)}月${+TODAY.slice(8)}日(${wdOf(TODAY)})`),
      row({href:'../v3/',t:'UI v3',chev:true}),
      row({href:'../',t:'UI v2',chev:true})])
    +`</div>`;
},{tab:'settings'});
