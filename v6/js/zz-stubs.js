/* 仮の受け皿(各担当のファイルが揃うまで)。本体が最後に消す */
['a','x','todo','task','memo','notes','n','ents','e','types','ledger','cats','search','settings'].forEach(n=>{ if(!ROUTES[n]) route(n,()=>`<div class="screen">${bar(n)}<div class="empty"><p>${n}</p></div></div>`,{tab:{todo:'todo',memo:'memo',ents:'ents',ledger:'ledger',search:'search',settings:'settings'}[n],root:['todo','memo','ents','ledger','search','settings'].includes(n)}); });
['openActEditor','openPayEditor','openReceipt','openTaskEditor','openEntEditor'].forEach(f=>{ if(!window[f]) window[f]=()=>toast(f); });
