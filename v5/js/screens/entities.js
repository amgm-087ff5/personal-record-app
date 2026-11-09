/* 画面: エンティティ(仮置き。接頭辞 en_) */
route('entities', () => `<h1 class="en_title">エンティティ</h1>`, {tab: 'entities'});
route('e', (id) => `<h1 class="en_title">${esc(id)}</h1>`);
route('types', () => `<h1 class="en_title">種類</h1>`, {tab: 'entities'});
