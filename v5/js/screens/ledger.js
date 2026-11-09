/* 画面: 家計簿(仮置き。接頭辞 lg_) */
route('ledger', () => `<h1 class="lg_title">家計簿</h1>`, {tab: 'ledger'});
route('x', (id) => `<h1 class="lg_title">支出 ${esc(id)}</h1>`);
route('cats', () => `<h1 class="lg_title">カテゴリ</h1>`, {tab: 'ledger'});
