/* 画面: 記録・品目・予定の詳細(仮置き。接頭辞 dt_)。タブは変えない(来た所のタブ) */
route('a', (id) => `<h1 class="dt_title">記録 ${esc(id)}</h1>`);
route('i', (id) => `<h1 class="dt_title">品目 ${esc(id)}</h1>`);
route('ev', (id) => `<h1 class="dt_title">予定 ${esc(id)}</h1>`);
