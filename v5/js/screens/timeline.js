/* 画面: アクティビティ(タイムライン・日・月)(仮置き。接頭辞 tl_) */
route('activities', () => `<h1 class="tl_title">アクティビティ</h1>`, {tab: 'activities'});
route('day', (id) => `<h1 class="tl_title">${esc(id)}</h1>`, {tab: 'activities'});
