/* 画面: メモ(すぐメモ・エンティティのメモ)(仮置き。接頭辞 mm_) */
route('memo', () => `<h1 class="mm_title">メモ</h1>`, {tab: 'memo'});
route('notes', () => `<h1 class="mm_title">エンティティのメモ</h1>`, {tab: 'memo'});
route('n', (id) => `<h1 class="mm_title">${esc(id)}</h1>`);
