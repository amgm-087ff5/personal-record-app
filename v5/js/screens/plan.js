/* 画面: ToDo(仮置き。接頭辞 pl_) */
route('todo', () => `<h1 class="pl_title">ToDo</h1>`, {tab: 'todo'});
route('task', (id) => `<h1 class="pl_title">ToDo ${esc(id)}</h1>`, {tab: 'todo'});
