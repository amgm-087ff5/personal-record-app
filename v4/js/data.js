/* データ(架空) — v2 モックから移植。window グローバル。 */
const TYPES={
  person:{n:'人物'}, colleague:{n:'同僚',p:'person'}, split:{n:'割り勘メンバー',p:'person'},
  place:{n:'場所'}, shop:{n:'店',p:'place'}, food:{n:'飲食店',p:'shop'}, ramen:{n:'ラーメン屋',p:'food'}, cafe:{n:'カフェ',p:'food'}, bakery:{n:'パン屋',p:'food'},
  super:{n:'スーパー',p:'shop'}, drug:{n:'ドラッグストア',p:'shop'}, home:{n:'住まい',p:'place'}, work:{n:'作業場所',p:'place'},
  company:{n:'会社'}, thing:{n:'物'}, doc:{n:'書類'},
  project:{n:'プロジェクト'}, work_proj:{n:'仕事',p:'project'}, life_proj:{n:'生活',p:'project'}
};
const E={
  me:{n:'自分',i:'自',t:['person'],facts:[]},
  hanako:{n:'田中 花子',i:'花',t:['person','colleague','split'],facts:[
    {k:'姓',v:'田中',ver:'版2 · 2020-04〜',hist:[['佐藤','〜2020-04','結婚']]},
    {k:'名',v:'花子'},{k:'電話',v:'090-1234-5678'},{k:'誕生日',v:'1994-06-12'},
    {k:'住所',v:'横浜市青葉区…',ver:'版3 · 2023-03〜',hist:[['川崎市宮前区…','2020-04〜2023-03','転居'],['世田谷区…','〜2020-04','']]},
    {k:'メール',v:'hanako@example.com',rare:1},{k:'略称',v:'花',rare:1},{k:'血液型',v:'A',rare:1}],
    rels:[['配偶者','taro','2020-04〜',1],['勤務先','acorp','2018-04〜',0],['友人','ken','',1]]},
  taro:{n:'田中 太郎',i:'太',t:['person'],facts:[{k:'電話',v:'080-2222-3333'}]},
  ken:{n:'山本 健',i:'健',t:['person','split'],facts:[{k:'電話',v:'070-4444-5555'}]},
  acorp:{n:'A社',i:'A',t:['company'],facts:[{k:'所在地',v:'東京都渋谷区…'}]},
  aoba:{n:'らーめん青葉',i:'ら',t:['ramen'],facts:[{k:'住所',v:'青葉区青葉台1-2'},{k:'営業',v:'11:00–21:00',rare:1},{k:'定休日',v:'水曜',rare:1}]},
  minato:{n:'カフェ ミナト',i:'ミ',t:['cafe','work'],facts:[{k:'住所',v:'青葉区美しが丘3'},{k:'電源',v:'あり(窓側)',rare:1}]},
  super:{n:'スーパー青葉台',i:'ス',t:['super'],facts:[{k:'営業',v:'9:00–23:00'}]},
  drug:{n:'ドラッグ桜',i:'桜',t:['drug'],facts:[{k:'住所',v:'青葉区桜台5'}]},
  home:{n:'自宅',i:'家',t:['home'],facts:[{k:'住所',v:'青葉区…'}]},
  proj1:{n:'引っ越し準備',i:'引',t:['project','life_proj'],facts:[{k:'期間',v:'2026-10〜2026-12'},{k:'状況',v:'進行中'}],rels:[['一緒に','hanako','',1]]},
};
const NOTES={
  hanako:{upd:'10/28 21:04',ver:7,body:[['好み','甘い物が好き。辛い物は苦手。お酒はビールなら飲む。'],['最近のこと','転職を考えている。{e:acorp} は2018年から。10/30に {a:c1} で話した。']],subs:[['誕生日プレゼント候補','更新 10/12'],['好きな店','リンク 3']]},
  aoba:{upd:'10/30 13:02',ver:2,body:[['メモ','麺は硬めがおいしい。餃子は2人で1皿で足りる。']],subs:[]},
};
/* 記録(アクティビティ)。v4.3(11/04 ユーザー指示): 時刻は2種類。
   - 期間(活動): time=開始, end=終了。食事・会話・作業・移動・買い物 など。中に子(支払いなど)を持てる(kids)
   - 瞬間: time だけ。起床・出発・到着・支払い(決済した時)
   親子: 日記(日) › 活動 › 子(支払い)。活動を記録していない支払い(振込・ネット購入)は日の直下に置く */
const A={
  d1030:{type:'日記',date:'2026-10-30',dd:'30',dw:'金',body:'花子さんとラーメン。午後はミナトで作業、夜は早めに帰宅。',photos:3,kids:['w1','l1','w2','m1']},
  d1029:{type:'日記',date:'2026-10-29',dd:'29',dw:'木',body:'雨。在宅で仕事。',kids:['h1','s2']},
  d1028:{type:'日記',date:'2026-10-28',dd:'28',dw:'水',body:'朝パンを買って出社。',kids:['p3']},
  d1027:{type:'日記',date:'2026-10-27',dd:'27',dw:'火',body:'',kids:['p4']},
  w1:{type:'起床',parent:'d1030',time:'07:40',title:'起床'},
  l1:{type:'食事',parent:'d1030',time:'12:00',end:'13:10',title:'花子さんとランチ',roles:[['場所','aoba'],['同席','hanako']],kids:['p1']},
  p1:{type:'支払い',parent:'l1',time:'13:05',title:'支払い',pay:'現金',amt:4580,roles:[['支払先','aoba'],['支払者','me']],
      items:[{n:'醤油らーめん',price:1100,lines:[{n:'',k:'amt',v:1000},{n:'大盛り',k:'amt',v:100}],b:'自分',tax:10},{n:'塩らーめん',price:1200,b:'花子',tax:10},{n:'餃子',price:680,b:'半分',tax:10},
             {n:'生ビール',price:800,b:'自分',tax:10,memo:'1杯目'},{n:'生ビール',price:800,b:'花子',tax:10,memo:'花子さんの分'}],x:'x1',adv:['hanako',2340,'未精算']},
  /* v4.14: 活動の中に活動(サブアクティビティ)。作業 › 会話 › 支払い の3段 */
  w2:{type:'作業',parent:'d1030',time:'13:40',end:'19:20',title:'カフェ ミナトで作業',roles:[['場所','minato']],kids:['c1']},
  c1:{type:'会話',parent:'w2',time:'15:00',end:'17:00',title:'転職の相談',roles:[['参加','hanako'],['場所','minato']],body:'A社を辞めるか迷っているとのこと。年内に決めたいらしい。',kids:['p16']},
  p16:{type:'支払い',parent:'c1',time:'15:05',title:'支払い',pay:'現金',amt:1100,roles:[['支払先','minato'],['支払者','me']],
      items:[{n:'カフェラテ',price:550,b:'自分',tax:10},{n:'カフェラテ',price:550,b:'自分',tax:10,memo:'花子さんの分(おごり)'}],x:'x16'},
  m1:{type:'移動',parent:'d1030',time:'19:30',end:'20:05',title:'帰宅',roles:[['出発','minato'],['到着','home']]},
  h1:{type:'出来事',parent:'d1029',time:'14:00',end:'15:30',title:'物件の内見',roles:[['プロジェクト','proj1']]},
  s2:{type:'買い物',parent:'d1029',time:'18:25',end:'18:50',title:'夕食の買い物',roles:[['場所','super']],kids:['p2']},
  p2:{type:'支払い',parent:'s2',time:'18:40',title:'支払い',pay:'カード ••1234',amt:2684,roles:[['支払先','super'],['支払者','me']],
      items:[{n:'牛乳',price:238,b:'自分',tax:8},{n:'卵',price:268,b:'自分',tax:8,memo:'特売'},{n:'卵',price:268,b:'自分',tax:8},{n:'鶏もも肉',price:612,lines:[{n:'',k:'amt',v:680},{n:'値引き',k:'pct',v:-10}],b:'自分',tax:8},{n:'キッチンペーパー',price:398,b:'自分',tax:10},{n:'その他',price:900,b:'自分',tax:8}],x:'x2'},
  p3:{type:'支払い',parent:'d1028',time:'08:20',title:'支払い',pay:'現金',amt:620,roles:[['支払先',null,'ベーカリー木漏れ日'],['支払者','me']],items:[{n:'クロワッサン',price:280,b:'自分',tax:8},{n:'カレーパン',price:340,b:'自分',tax:8}],x:'x3'},
  p4:{type:'支払い',parent:'d1027',time:'20:05',title:'支払い',pay:'カード ••1234',amt:1280,roles:[['支払先','drug'],['支払者','me']],items:[{n:'洗剤',price:680,b:'自分',tax:10},{n:'歯ブラシ',price:600,b:'自分',tax:10}],x:'x4'},
};
const X={
  x1:{pay:'p1',amt:2240,cat:'eatout',memo:'花子さんとランチ',items:[{n:'醤油らーめん',price:1100,b:'全額'},{n:'餃子',price:340,b:'1/2'},{n:'生ビール',price:800,b:'全額',memo:'1杯目'}]},
  x2:{pay:'p2',amt:2684,cat:'cook',memo:'',items:[{n:'牛乳',price:238,b:'全額'},{n:'卵',price:268,b:'全額',memo:'特売'},{n:'卵',price:268,b:'全額'},{n:'鶏もも肉',price:612,b:'全額'},{n:'キッチンペーパー',price:398,b:'全額'},{n:'その他',price:900,b:'全額',unk:1}]},
  x3:{pay:'p3',amt:620,cat:'cafe',memo:'',items:[{n:'クロワッサン',price:280,b:'全額'},{n:'カレーパン',price:340,b:'全額'}]},
  x16:{pay:'p16',amt:1100,cat:'cafe',memo:'花子さんの分もおごり',items:[{n:'カフェラテ',price:550,b:'全額'},{n:'カフェラテ',price:550,b:'全額',memo:'花子さんの分'}]},
  x4:{pay:'p4',amt:1280,cat:'daily',memo:'',items:[{n:'洗剤',price:680,b:'全額'},{n:'歯ブラシ',price:600,b:'全額'}]},
};
const DAYS=['d1030','d1029','d1028','d1027'];
/* カテゴリ: 階層 = 種類の軸。sp = 特別感の既定(子は親から継承、支出ごとに上書き可) */
const CATS={
  house:{n:'住居',c:'#8796B5',fx:'固定',sp:'日常'}, rent:{n:'家賃',p:'house'},
  comm:{n:'通信',c:'#7FB5B9',fx:'固定',sp:'日常'}, phone:{n:'スマホ',p:'comm'}, sub:{n:'サブスク',p:'comm'},
  food:{n:'食費',c:'#DC97AA',fx:'変動',sp:'日常'}, cook:{n:'自炊',p:'food'}, eatout:{n:'外食',p:'food'}, cafe:{n:'カフェ・軽食',p:'food'},
  daily:{n:'日用品',c:'#9CC7A3',fx:'変動',sp:'日常'},
  trans:{n:'交通費',c:'#8DB0DD',fx:'変動',sp:'日常'}, train:{n:'電車',p:'trans'}, taxi:{n:'タクシー',p:'trans',sp:'非日常'},
  fun:{n:'娯楽',c:'#B3D8EA',fx:'変動',sp:'日常'}, hobby:{n:'趣味',p:'fun'}, travel:{n:'旅行',p:'fun',sp:'非日常'},
  gift:{n:'贈り物',c:'#F2C6D3',fx:'ask',sp:'非日常'},
};
const COLORS=['#8796B5','#7FB5B9','#DC97AA','#9CC7A3','#8DB0DD','#B3D8EA','#F2C6D3','#A9B4C6'];
/* 10月のほかの支払い(架空データ)。支払いも瞬間のアクティビティなので、日の kids に入れてタイムラインにも出す(家計簿と食い違わない) */
[['d1025','2026-10-25','日','p5','電車','JR 青葉台駅',null,1280,'x5','train'],
 ['d1022','2026-10-22','木','p6','映画','シネマ青葉',null,2000,'x6','hobby'],
 ['d1015','2026-10-15','木','p7','買い物',null,'super',3120,'x7','cook'],
 ['d1012','2026-10-12','月','p8','誕生日ディナー',null,'aoba',6800,'x8','eatout','非日常'],
 ['d1008','2026-10-08','木','p9','日用品',null,'drug',980,'x9','daily'],
 ['d1005','2026-10-05','月','p10','タクシー','タクシー',null,2400,'x10','taxi'],
 ['d1003','2026-10-03','土','p11','温泉旅行の宿','宿 山の湯',null,18000,'x11','travel'],
 ['d1002','2026-10-02','金','p12','コーヒー',null,'minato',580,'x12','cafe'],
 ['d1027','2026-10-27','火','p13','家賃','大家(振込)',null,85000,'x13','rent'],
 ['d1010','2026-10-10','土','p14','スマホ料金','モバイル通信',null,3280,'x14','phone'],
 ['d1015','2026-10-15','木','p15','動画サブスク','動画配信',null,990,'x15','sub'],
 /* 10/17〜10/31 の変動費(家計簿・ホームの支出用。固定費は除く)。12番目の値は時刻 */
 ['d1017','2026-10-17','土','p17','ランチ','イタリアン 木の実',null,2840,'x17','eatout','日常','12:40'],
 ['d1019','2026-10-19','月','p18','洗剤・シャンプー',null,'drug',1120,'x18','daily','日常','19:40'],
 ['d1020','2026-10-20','火','p19','カフェラテ',null,'minato',980,'x19','cafe','日常','15:10'],
 ['d1022','2026-10-22','木','p20','カフェラテ',null,'minato',540,'x20','cafe','日常','14:10'],
 ['d1023','2026-10-23','金','p21','夕食の買い物',null,'super',3760,'x21','cook','日常','18:50'],
 ['d1024','2026-10-24','土','p22','ランチ','そば処 山の手',null,2160,'x22','eatout','日常','12:30'],
 ['d1028','2026-10-28','水','p23','夕食の買い物',null,'super',980,'x23','cook','日常','18:40'],
 ['d1031','2026-10-31','土','p24','カフェラテ',null,'minato',480,'x24','cafe','日常','08:40'],
].forEach(([d,date,dw,pid,title,shopName,shopId,amt,xid,cat,sp,time='19:00'])=>{
  if(!A[d])A[d]={type:'日記',date,dd:String(+date.slice(8)),dw,body:'',kids:[]};
  if(!A[d].kids.includes(pid))A[d].kids.push(pid);
  const roles=[['支払先',shopId,shopName],['支払者','me']];if(pid==='p8')roles.push(['同席','hanako']);
  A[pid]={type:'支払い',parent:d,time,title:'支払い',pay:'カード ••1234',amt,roles,items:[{n:title,price:amt,b:'自分',tax:10}],x:xid};
  X[xid]={pay:pid,amt,cat,sp,memo:'',items:[{n:title,price:amt,b:'全額'}]};
});
/* 過去の月: 大分類ごとの合計 */
const TREND=[['5月',{house:85000,comm:4270,food:19800,daily:2900,trans:4100,fun:6200,gift:0}],
 ['6月',{house:85000,comm:4270,food:21400,daily:3400,trans:3600,fun:2800,gift:3000}],
 ['7月',{house:85000,comm:4270,food:18800,daily:2600,trans:6900,fun:19800,gift:0}],
 ['8月',{house:85000,comm:4270,food:22600,daily:3100,trans:9200,fun:24500,gift:5400}],
 ['9月',{house:85000,comm:4270,food:20100,daily:2800,trans:3900,fun:4600,gift:0}]];
/* メモ(v4.3): 気軽に書く短い文。持ち主(縁・日・記録)は後から付けてよい(無くてもよい)。
   縁のメモ(NOTES)は縁ごとの長い文書、こちらは思いついた時の1件ずつ */
const MEMOS={
  q4:{body:'花子さんの誕生日、甘い物が良さそう。駅前のケーキ屋を見ておく',at:'2026-10-31 08:12',links:['hanako']},
  q3:{body:'カフェ ミナトは窓側に電源あり。午後は混む',at:'2026-10-30 16:40',links:['minato'],rec:'c1'},
  q2:{body:'年末調整の書類、会社のポータルから出す',at:'2026-10-29 21:15',links:[],pin:1},
  q1:{body:'洗剤の詰め替えは大きいサイズの方が安い',at:'2026-10-27 20:10',links:['drug']},
  q5:{body:'引っ越し業者は3社から見積もりを取る',at:'2026-10-31 09:30',links:['proj1']},
};
/* 予定(=これからのアクティビティ)。終わると記録(rec)に結び付く */
const TODAY='2026-10-31';
/* 予定とToDo(v4.13): 1つのデータ PLN に入れ、kind で種類を分ける。
   kind: '予定' = 通常の予定(終日・祝日を含む) / '支払い予定' = pay を持つ予定(固定費など) / 'ToDo' = やること・買う物。
   既存の項目名はそのまま(予定: t,d,s,e,all,hol,place,who,todo,memo,alarm,rep,rec,ent,pay / ToDo: t,due,done,list,pri,ev,ent,rep…)。
   EV と TD は PLN を種類で絞った見え方(Proxy)。EV[k]・TD[k]・Object.keys(TD)・delete TD[k] などは従来どおり動く。補助関数は js/plans.js */
const PLN={
  e9:{kind:'予定',t:'在宅作業',d:'2026-10-31',s:'10:00',e:'12:00',place:['home']},
  e1:{kind:'予定',t:'花子さんと映画',d:'2026-10-31',s:'15:00',e:'17:10',place:[null,'シネマ青葉'],who:['hanako'],todo:['t2'],memo:'チケットはネットで予約',alarm:'30分前'},
  e2:{kind:'予定',t:'夕食の買い物',d:'2026-10-31',s:'18:30',e:'19:00',place:['super'],todo:['t4','t5','t6'],alarm:'なし'},
  e3:{kind:'予定',t:'歯医者',d:'2026-11-02',s:'10:00',e:'11:00',place:[null,'さくら歯科'],todo:['t3'],alarm:'1時間前'},
  e4:{kind:'支払い予定',t:'家賃の支払い',d:'2026-10-27',all:1,rep:'毎月27日',pay:{amt:85000,cat:'rent'},rec:'p13'},
  e5:{kind:'予定',t:'花子さんとランチ',d:'2026-10-30',s:'12:00',e:'13:00',place:['aoba'],who:['hanako'],rec:'l1'},
  e6:{kind:'予定',t:'温泉旅行',d:'2026-11-08',d2:'2026-11-09',all:1,place:[null,'宿 山の湯'],who:['hanako']},
  e7:{kind:'支払い予定',t:'スマホ料金',d:'2026-10-10',all:1,rep:'毎月10日',pay:{amt:3280,cat:'phone'},rec:'p14'},
  e8:{kind:'予定',t:'文化の日',d:'2026-11-03',all:1,hol:1},
  e10:{kind:'支払い予定',t:'動画サブスク',d:'2026-10-15',all:1,rep:'毎月15日',pay:{amt:990,cat:'sub'},rec:'p15'},
  e11:{kind:'予定',t:'不動産屋と打ち合わせ',d:'2026-11-07',s:'14:00',e:'15:00',place:[null,'不動産ファースト'],ent:['proj1']},
  t2:{kind:'ToDo',t:'映画のチケットを予約',due:'2026-10-31',list:'inbox',ev:'e1',pri:1},
  t9:{kind:'ToDo',t:'資源ごみを出す',due:'2026-10-31',list:'life',rep:'毎週土曜',done:'2026-10-31'},
  t7:{kind:'ToDo',t:'ドラッグ桜のポイントを交換',due:'2026-10-29',list:'inbox',ent:['drug']},
  t3:{kind:'ToDo',t:'歯医者の問診票を書く',due:'2026-11-01',list:'life',ev:'e3'},
  t1:{kind:'ToDo',t:'花子さんの誕生日プレゼントを選ぶ',due:'2026-11-05',list:'life',pri:1,ent:['hanako'],sub:[['候補を3つ挙げる',1],['予算を決める',0],['注文する',0]],memo:'ノートの「誕生日プレゼント候補」を見る'},
  t8:{kind:'ToDo',t:'年末調整の書類を出す',due:'2026-11-14',list:'life'},
  t4:{kind:'ToDo',t:'牛乳',list:'shop',ev:'e2'},
  t6:{kind:'ToDo',t:'食器用洗剤',list:'shop',ev:'e2'},
  t5:{kind:'ToDo',t:'卵',list:'shop',ev:'e2',done:'2026-10-29',pay:'p2'},
  t10:{kind:'ToDo',t:'スマホの料金プランを見直す',list:'inbox',memo:'通信費(固定費)を下げたい'},
  t11:{kind:'ToDo',t:'不動産屋に連絡',due:'2026-11-02',list:'life',ent:['proj1']},
  t12:{kind:'ToDo',t:'転居の届け出を出す',due:'2026-11-30',list:'life',ent:['proj1']},
};

const LISTS={inbox:{n:'受信箱'},life:{n:'生活'},shop:{n:'買い物',shop:'super'}};
/* EV(予定)と TD(ToDo)= PLN を種類で絞った見え方。kind は代入で自動で決まる(EV は pay があれば '支払い予定'、TD は 'ToDo') */
const plView=isTodo=>{
  const has=k=>typeof k==='string'&&Object.prototype.hasOwnProperty.call(PLN,k)&&(PLN[k].kind==='ToDo')===isTodo;
  const put=(k,v)=>{if(typeof k!=='string'||!v||typeof v!=='object')throw new TypeError('予定・ToDoは「キー: オブジェクト」で入れてください');
    v.kind=isTodo?'ToDo':(v.pay?'支払い予定':'予定');PLN[k]=v;return true};
  return new Proxy({},{
    get:(_,k)=>has(k)?PLN[k]:Reflect.get(Object.prototype,k),
    set:(_,k,v)=>put(k,v),
    defineProperty:(_,k,d)=>'value' in d?put(k,d.value):false,
    has:(_,k)=>has(k),
    deleteProperty:(_,k)=>{if(has(k))delete PLN[k];return true},
    ownKeys:()=>Object.keys(PLN).filter(k=>(PLN[k].kind==='ToDo')===isTodo),
    getOwnPropertyDescriptor:(_,k)=>has(k)?{value:PLN[k],writable:true,enumerable:true,configurable:true}:undefined,
  });
};
const EV=plView(false);
const TD=plView(true);
const TYPE_PROPS={person:['電話','誕生日','住所'],colleague:['部署'],split:['略称'],place:['住所'],shop:['営業時間','定休日'],company:['所在地'],thing:['購入日'],doc:['発行元','期限'],project:['期間','状況']};

/* 純粋なヘルパー */
const yen=n=>'¥'+n.toLocaleString('ja-JP');
const $=s=>document.querySelector(s);
const typeAnc=t=>{const r=[];while(t){r.unshift(t);t=TYPES[t].p}return r};
const isUnder=(t,root)=>typeAnc(t).includes(root);
/** その縁が関わった記録。活動の中の支払いは、親の活動も関わっていれば親の1行にまとめる(同じことを2回出さない) */
const actsOf=id=>{const hit=Object.entries(A).filter(([k,a])=>a.roles&&a.roles.some(r=>r[1]===id));const keys=new Set(hit.map(([k])=>k));
  return hit.filter(([k,a])=>!(a.parent&&keys.has(a.parent))).map(([k,a])=>[k,a,a.roles.find(r=>r[1]===id)[0]])};
const relsOf=id=>{const out=[];for(const[k,e]of Object.entries(E))(e.rels||[]).forEach(r=>{if(k===id)out.push([r[0],r[1],r[2],r[3]]);else if(r[1]===id)out.push([r[3]?r[0]:({'勤務先':'従業員'}[r[0]]||r[0]),k,r[2],r[3]])});return out};
/** 親をたどって日(日記)を返す。活動の子(支払い)も日に届く */
const dayOf=a=>{let p=a&&A[a.parent];while(p&&p.type!=='日記')p=A[p.parent];return p};
const dateOf=a=>{const d=dayOf(a);return d?d.date:(a&&a.date)||''};
/** 期間(開始〜終了)か瞬間か */
const isSpan=a=>!!(a&&a.end);
const timeText=a=>a?(a.end?`${a.time}–${a.end}`:(a.time||'')):'';
const parentAct=a=>{const p=a&&A[a.parent];return p&&p.type!=='日記'?p:null};
/** 子孫(活動の中の支払いまで) */
/** 子のアクティビティ(支払いを含む全種類)。v4.14: 活動の子は支払いに限らない */
const kidActs=id=>(A[id]&&A[id].kids||[]).filter(c=>A[c]);
/** 祖先の活動(日記を除く)。外側(一番上の親)が先 */
const ancOf=a=>{const r=[];let p=a&&A[a.parent];while(p&&p.type!=='日記'){r.unshift(p);p=A[p.parent]}return r};
/** 祖先の活動のid。外側が先 */
const ancIds=id=>{const r=[];let k=A[id]&&A[id].parent;while(k&&A[k]&&A[k].type!=='日記'){r.unshift(k);k=A[k].parent}return r};
const descOf=id=>{const out=[];const walk=k=>(A[k]&&A[k].kids||[]).forEach(c=>{if(A[c]){out.push(c);walk(c)}});walk(id);return out};
const md=d=>{const[y,m,dd]=d.split('-');return `${+m}/${+dd}`};
const catAnc=k=>{const r=[];while(k){r.unshift(k);k=CATS[k].p}return r};
const catName=k=>CATS[k].n;
const catPath=k=>catAnc(k).map(c=>CATS[c].n).join(' › ');
const catColor=k=>{for(const c of catAnc(k).reverse())if(CATS[c].c)return CATS[c].c;return '#8B94A7'};
const catChip=(k,path)=>`<span class="cat" style="--cc:${catColor(k)}">${path?catPath(k):CATS[k].n}</span>`;
/* 固定・変動と特別感: 最も近い祖先の設定を引き継ぐ。'ask' = 都度指定(null: 支出ごとに選ぶ)。どこにも設定がなければ null(勝手な既定値は置かない) */
const setv=v=>v==='ask'||v==='none'||v===undefined?null:v;   /* 'ask' = 都度指定(カテゴリは既定値を持たず、支出ごとに選ぶ) */
const catFx=k=>{for(const c of catAnc(k).reverse())if(CATS[c].fx!==undefined)return setv(CATS[c].fx);return null};
const xFx=x=>x.fx!==undefined?setv(x.fx):catFx(x.cat);
const ROOTS=()=>Object.keys(CATS).filter(k=>!CATS[k].p);
const mdw=d=>{const dt=new Date(d);return `${dt.getMonth()+1}/${dt.getDate()}(${'日月火水木金土'[dt.getDay()]})`};
const addDays=(d,n)=>{const dt=new Date(d);dt.setDate(dt.getDate()+n);return dt.toISOString().slice(0,10)};
const repOn=(e,d)=>e.rep&&d>e.d&&+d.slice(8)===+e.d.slice(8);
const evOn=d=>Object.entries(EV).filter(([k,e])=>e.d===d||(e.d2&&e.d<=d&&d<=e.d2)||repOn(e,d)).sort((a,b)=>(a[1].all?'0':a[1].s).localeCompare(b[1].all?'0':b[1].s));
const actsOn=d=>Object.entries(A).filter(([k,a])=>a.type!=='日記'&&a.parent&&dateOf(a)===d);
const placeOf=e=>e.place?(e.place[0]?E[e.place[0]].n:e.place[1]):'';
const nextOn=e=>{if(!e.rep)return e.d;const dom=String(+e.d.slice(8)).padStart(2,'0');let y=+TODAY.slice(0,4),m=+TODAY.slice(5,7);let d=`${y}-${String(m).padStart(2,'0')}-${dom}`;if(d<=TODAY){m++;if(m>12){m=1;y++}d=`${y}-${String(m).padStart(2,'0')}-${dom}`}return d};
const tdOpen=k=>!TD[k].done;
const catSp=k=>{for(const c of catAnc(k).reverse())if(CATS[c].sp!==undefined)return setv(CATS[c].sp);return null};
const xSp=x=>x.sp!==undefined?setv(x.sp):catSp(x.cat);
const shopOf=x=>{const r=A[x.pay].roles[0];return r[1]?E[r[1]].n:r[2]};
/* 支払いの題名は行動名「支払い」に統一(v4.6)。何に払ったかは支払先と品目で示す */
const payeeOf=a=>{const r=(a&&a.roles||[]).find(r=>r[0]==='支払先');return r?(r[1]&&E[r[1]]?E[r[1]].n:(r[2]||'')):''};
/** 「支払い · らーめん青葉」(支払先があれば付ける) */
const payLabel=a=>{const p=payeeOf(a);return p?`支払い · ${p}`:'支払い'};
/** 家計簿の支出の題名: 親の活動があれば活動の題名(例「花子さんとランチ」)、無ければ支払先 */
const xTitle=x=>{const a=A[x.pay],pa=parentAct(a);return pa?pa.title:(payeeOf(a)||'支払い')};
const whoOf=x=>{const a=A[x.pay],pa=parentAct(a);const w=[...a.roles,...(pa&&pa.roles||[])].filter(r=>r[1]&&!['支払先','支払者','場所','出発','到着'].includes(r[0])).map(r=>E[r[1]].n).filter((v,i,a)=>a.indexOf(v)===i);return w.length?w:['ひとり']};
const xDate=x=>dayOf(A[x.pay]).date;
const propsOf=k=>typeAnc(k).flatMap(t=>(TYPE_PROPS[t]||[]).map(p=>[p,t]));
/* メモの共通操作(ホーム・メモ・作成シートで使う) */
const memoNow=()=>`${TODAY} ${typeof NOW!=='undefined'?NOW:'12:00'}`;
const memoAdd=(body,links=[],rec)=>{const id='q'+Date.now().toString(36);MEMOS[id]={body,at:memoNow(),links,...(rec?{rec}:{})};return id};
const memosSorted=()=>Object.entries(MEMOS).sort((a,b)=>(b[1].pin?1:0)-(a[1].pin?1:0)||b[1].at.localeCompare(a[1].at));
