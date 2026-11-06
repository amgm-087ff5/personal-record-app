package com.amgm.personallog.v4.data

import java.time.LocalDate

/**
 * 見本データ — v4/js/data.js の値をそのまま移したもの(架空)。モックの「今日」は 2026-10-31。
 * 初回起動時だけ入れる。shiftTo(今日) で日付をずらし、モックと同じ見え方を今日を基準に再現する。
 */
object Seed {
    const val MOCK_TODAY = "2026-10-31"
    const val MOCK_NOW = "13:20"

    private fun r(role: String, ent: String?, name: String? = null) = Role(role, ent, name)
    private fun it(n: String, price: Int, b: String = "自分", tax: Int = 10, memo: String? = null, lines: List<AmtLine> = emptyList()) =
        PayItem(n, price, lines, b, tax, memo)

    fun data(): AppData {
        val types = linkedMapOf(
            "person" to TypeDef("人物"), "colleague" to TypeDef("同僚", "person"), "split" to TypeDef("割り勘メンバー", "person"),
            "place" to TypeDef("場所"), "shop" to TypeDef("店", "place"), "food" to TypeDef("飲食店", "shop"),
            "ramen" to TypeDef("ラーメン屋", "food"), "cafe" to TypeDef("カフェ", "food"), "bakery" to TypeDef("パン屋", "food"),
            "super" to TypeDef("スーパー", "shop"), "drug" to TypeDef("ドラッグストア", "shop"), "home" to TypeDef("住まい", "place"),
            "work" to TypeDef("作業場所", "place"),
            "company" to TypeDef("会社"), "thing" to TypeDef("物"), "doc" to TypeDef("書類"),
            "project" to TypeDef("プロジェクト"), "work_proj" to TypeDef("仕事", "project"), "life_proj" to TypeDef("生活", "project"),
        )
        val ents = linkedMapOf(
            "me" to Entity("自分", "自", listOf("person")),
            "hanako" to Entity(
                "田中 花子", "花", listOf("person", "colleague", "split"),
                facts = listOf(
                    Fact("姓", "田中", "版2 · 2020-04〜", listOf(listOf("佐藤", "〜2020-04", "結婚"))),
                    Fact("名", "花子"), Fact("電話", "090-1234-5678"), Fact("誕生日", "1994-06-12"),
                    Fact("住所", "横浜市青葉区…", "版3 · 2023-03〜", listOf(listOf("川崎市宮前区…", "2020-04〜2023-03", "転居"), listOf("世田谷区…", "〜2020-04", ""))),
                    Fact("メール", "hanako@example.com", rare = true), Fact("略称", "花", rare = true), Fact("血液型", "A", rare = true),
                ),
                rels = listOf(Rel("配偶者", "taro", "2020-04〜", true), Rel("勤務先", "acorp", "2018-04〜", false), Rel("友人", "ken", "", true)),
            ),
            "taro" to Entity("田中 太郎", "太", listOf("person"), listOf(Fact("電話", "080-2222-3333"))),
            "ken" to Entity("山本 健", "健", listOf("person", "split"), listOf(Fact("電話", "070-4444-5555"))),
            "acorp" to Entity("A社", "A", listOf("company"), listOf(Fact("所在地", "東京都渋谷区…"))),
            "aoba" to Entity("らーめん青葉", "ら", listOf("ramen"), listOf(Fact("住所", "青葉区青葉台1-2"), Fact("営業", "11:00–21:00", rare = true), Fact("定休日", "水曜", rare = true))),
            "minato" to Entity("カフェ ミナト", "ミ", listOf("cafe", "work"), listOf(Fact("住所", "青葉区美しが丘3"), Fact("電源", "あり(窓側)", rare = true))),
            "super" to Entity("スーパー青葉台", "ス", listOf("super"), listOf(Fact("営業", "9:00–23:00"))),
            "drug" to Entity("ドラッグ桜", "桜", listOf("drug"), listOf(Fact("住所", "青葉区桜台5"))),
            "home" to Entity("自宅", "家", listOf("home"), listOf(Fact("住所", "青葉区…"))),
            "proj1" to Entity("引っ越し準備", "引", listOf("project", "life_proj"), listOf(Fact("期間", "2026-10〜2026-12"), Fact("状況", "進行中")), listOf(Rel("一緒に", "hanako", "", true))),
        )
        val notes = linkedMapOf(
            "hanako" to Note("10/28 21:04", 7, listOf(
                listOf("好み", "甘い物が好き。辛い物は苦手。お酒はビールなら飲む。"),
                listOf("最近のこと", "転職を考えている。{e:acorp} は2018年から。10/30に {a:c1} で話した。"),
            ), listOf(listOf("誕生日プレゼント候補", "更新 10/12"), listOf("好きな店", "リンク 3"))),
            "aoba" to Note("10/30 13:02", 2, listOf(listOf("メモ", "麺は硬めがおいしい。餃子は2人で1皿で足りる。"))),
        )
        val a = linkedMapOf(
            "d1030" to Activity("日記", date = "2026-10-30", dd = "30", dw = "金", body = "花子さんとラーメン。午後はミナトで作業、夜は早めに帰宅。", photos = 3, kids = listOf("w1", "l1", "w2", "m1")),
            "d1029" to Activity("日記", date = "2026-10-29", dd = "29", dw = "木", body = "雨。在宅で仕事。", kids = listOf("h1", "s2")),
            "d1028" to Activity("日記", date = "2026-10-28", dd = "28", dw = "水", body = "朝パンを買って出社。", kids = listOf("p3")),
            "d1027" to Activity("日記", date = "2026-10-27", dd = "27", dw = "火", body = "", kids = listOf("p4")),
            "w1" to Activity("起床", parent = "d1030", time = "07:40", title = "起床"),
            "l1" to Activity("食事", parent = "d1030", time = "12:00", end = "13:10", title = "花子さんとランチ", roles = listOf(r("場所", "aoba"), r("同席", "hanako")), kids = listOf("p1")),
            "p1" to Activity(
                "支払い", parent = "l1", time = "13:05", title = "支払い", pay = "現金", amt = 4580,
                roles = listOf(r("支払先", "aoba"), r("支払者", "me")),
                items = listOf(
                    it("醤油らーめん", 1100, lines = listOf(AmtLine("", "amt", 1000), AmtLine("大盛り", "amt", 100))),
                    it("塩らーめん", 1200, "花子"), it("餃子", 680, "半分"),
                    it("生ビール", 800, memo = "1杯目"), it("生ビール", 800, "花子", memo = "花子さんの分"),
                ),
                x = "x1", adv = Advance("hanako", 2340, "未精算"),
            ),
            "w2" to Activity("作業", parent = "d1030", time = "13:40", end = "19:20", title = "カフェ ミナトで作業", roles = listOf(r("場所", "minato")), kids = listOf("c1")),
            "c1" to Activity("会話", parent = "w2", time = "15:00", end = "17:00", title = "転職の相談", roles = listOf(r("参加", "hanako"), r("場所", "minato")), body = "A社を辞めるか迷っているとのこと。年内に決めたいらしい。", kids = listOf("p16")),
            "p16" to Activity(
                "支払い", parent = "c1", time = "15:05", title = "支払い", pay = "現金", amt = 1100,
                roles = listOf(r("支払先", "minato"), r("支払者", "me")),
                items = listOf(it("カフェラテ", 550), it("カフェラテ", 550, memo = "花子さんの分(おごり)")), x = "x16",
            ),
            "m1" to Activity("移動", parent = "d1030", time = "19:30", end = "20:05", title = "帰宅", roles = listOf(r("出発", "minato"), r("到着", "home"))),
            "h1" to Activity("出来事", parent = "d1029", time = "14:00", end = "15:30", title = "物件の内見", roles = listOf(r("プロジェクト", "proj1"))),
            "s2" to Activity("買い物", parent = "d1029", time = "18:25", end = "18:50", title = "夕食の買い物", roles = listOf(r("場所", "super")), kids = listOf("p2")),
            "p2" to Activity(
                "支払い", parent = "s2", time = "18:40", title = "支払い", pay = "カード ••1234", amt = 2684,
                roles = listOf(r("支払先", "super"), r("支払者", "me")),
                items = listOf(
                    it("牛乳", 238, tax = 8), it("卵", 268, tax = 8, memo = "特売"), it("卵", 268, tax = 8),
                    it("鶏もも肉", 612, tax = 8, lines = listOf(AmtLine("", "amt", 680), AmtLine("値引き", "pct", -10))),
                    it("キッチンペーパー", 398), it("その他", 900, tax = 8),
                ), x = "x2",
            ),
            "p3" to Activity("支払い", parent = "d1028", time = "08:20", title = "支払い", pay = "現金", amt = 620, roles = listOf(r("支払先", null, "ベーカリー木漏れ日"), r("支払者", "me")), items = listOf(it("クロワッサン", 280, tax = 8), it("カレーパン", 340, tax = 8)), x = "x3"),
            "p4" to Activity("支払い", parent = "d1027", time = "20:05", title = "支払い", pay = "カード ••1234", amt = 1280, roles = listOf(r("支払先", "drug"), r("支払者", "me")), items = listOf(it("洗剤", 680), it("歯ブラシ", 600)), x = "x4"),
        )
        val x = linkedMapOf(
            "x1" to Expense("p1", 2240, "eatout", memo = "花子さんとランチ", items = listOf(XItem("醤油らーめん", 1100), XItem("餃子", 340, "1/2"), XItem("生ビール", 800, memo = "1杯目"))),
            "x2" to Expense("p2", 2684, "cook", items = listOf(XItem("牛乳", 238), XItem("卵", 268, memo = "特売"), XItem("卵", 268), XItem("鶏もも肉", 612), XItem("キッチンペーパー", 398), XItem("その他", 900, unk = true))),
            "x3" to Expense("p3", 620, "cafe", items = listOf(XItem("クロワッサン", 280), XItem("カレーパン", 340))),
            "x16" to Expense("p16", 1100, "cafe", memo = "花子さんの分もおごり", items = listOf(XItem("カフェラテ", 550), XItem("カフェラテ", 550, memo = "花子さんの分"))),
            "x4" to Expense("p4", 1280, "daily", items = listOf(XItem("洗剤", 680), XItem("歯ブラシ", 600))),
        )
        // 10月のほかの支払い(data.js の配列と同じ)。[日のキー, 日付, 曜日, 支払い, 題名, 支払先の名前, 支払先, 金額, 支出, カテゴリ, 特別感, 時刻]
        val more = listOf(
            listOf("d1025", "2026-10-25", "日", "p5", "電車", "JR 青葉台駅", null, 1280, "x5", "train", null, null),
            listOf("d1022", "2026-10-22", "木", "p6", "映画", "シネマ青葉", null, 2000, "x6", "hobby", null, null),
            listOf("d1015", "2026-10-15", "木", "p7", "買い物", null, "super", 3120, "x7", "cook", null, null),
            listOf("d1012", "2026-10-12", "月", "p8", "誕生日ディナー", null, "aoba", 6800, "x8", "eatout", "非日常", null),
            listOf("d1008", "2026-10-08", "木", "p9", "日用品", null, "drug", 980, "x9", "daily", null, null),
            listOf("d1005", "2026-10-05", "月", "p10", "タクシー", "タクシー", null, 2400, "x10", "taxi", null, null),
            listOf("d1003", "2026-10-03", "土", "p11", "温泉旅行の宿", "宿 山の湯", null, 18000, "x11", "travel", null, null),
            listOf("d1002", "2026-10-02", "金", "p12", "コーヒー", null, "minato", 580, "x12", "cafe", null, null),
            listOf("d1027", "2026-10-27", "火", "p13", "家賃", "大家(振込)", null, 85000, "x13", "rent", null, null),
            listOf("d1010", "2026-10-10", "土", "p14", "スマホ料金", "モバイル通信", null, 3280, "x14", "phone", null, null),
            listOf("d1015", "2026-10-15", "木", "p15", "動画サブスク", "動画配信", null, 990, "x15", "sub", null, null),
            listOf("d1017", "2026-10-17", "土", "p17", "ランチ", "イタリアン 木の実", null, 2840, "x17", "eatout", "日常", "12:40"),
            listOf("d1019", "2026-10-19", "月", "p18", "洗剤・シャンプー", null, "drug", 1120, "x18", "daily", "日常", "19:40"),
            listOf("d1020", "2026-10-20", "火", "p19", "カフェラテ", null, "minato", 980, "x19", "cafe", "日常", "15:10"),
            listOf("d1022", "2026-10-22", "木", "p20", "カフェラテ", null, "minato", 540, "x20", "cafe", "日常", "14:10"),
            listOf("d1023", "2026-10-23", "金", "p21", "夕食の買い物", null, "super", 3760, "x21", "cook", "日常", "18:50"),
            listOf("d1024", "2026-10-24", "土", "p22", "ランチ", "そば処 山の手", null, 2160, "x22", "eatout", "日常", "12:30"),
            listOf("d1028", "2026-10-28", "水", "p23", "夕食の買い物", null, "super", 980, "x23", "cook", "日常", "18:40"),
            listOf("d1031", "2026-10-31", "土", "p24", "カフェラテ", null, "minato", 480, "x24", "cafe", "日常", "08:40"),
        )
        for (m in more) {
            val d = m[0] as String; val date = m[1] as String; val dw = m[2] as String; val pid = m[3] as String
            val title = m[4] as String; val shopName = m[5] as String?; val shopId = m[6] as String?; val amt = m[7] as Int
            val xid = m[8] as String; val cat = m[9] as String; val sp = m[10] as String?; val time = (m[11] as String?) ?: "19:00"
            val day = a[d] ?: Activity("日記", date = date, dd = date.substring(8).toInt().toString(), dw = dw, body = "")
            a[d] = if (pid in day.kids) day else day.copy(kids = day.kids + pid)
            val roles = mutableListOf(r("支払先", shopId, shopName), r("支払者", "me"))
            if (pid == "p8") roles.add(r("同席", "hanako"))
            a[pid] = Activity("支払い", parent = d, time = time, title = "支払い", pay = "カード ••1234", amt = amt, roles = roles, items = listOf(it(title, amt)), x = xid)
            x[xid] = Expense(pid, amt, cat, sp = sp, items = listOf(XItem(title, amt)))
        }
        val cats = linkedMapOf(
            "house" to Category("住居", c = "#8796B5", fx = "固定", sp = "日常"), "rent" to Category("家賃", "house"),
            "comm" to Category("通信", c = "#7FB5B9", fx = "固定", sp = "日常"), "phone" to Category("スマホ", "comm"), "sub" to Category("サブスク", "comm"),
            "food" to Category("食費", c = "#DC97AA", fx = "変動", sp = "日常"), "cook" to Category("自炊", "food"), "eatout" to Category("外食", "food"), "cafe" to Category("カフェ・軽食", "food"),
            "daily" to Category("日用品", c = "#9CC7A3", fx = "変動", sp = "日常"),
            "trans" to Category("交通費", c = "#8DB0DD", fx = "変動", sp = "日常"), "train" to Category("電車", "trans"), "taxi" to Category("タクシー", "trans", sp = "非日常"),
            "fun" to Category("娯楽", c = "#B3D8EA", fx = "変動", sp = "日常"), "hobby" to Category("趣味", "fun"), "travel" to Category("旅行", "fun", sp = "非日常"),
            "gift" to Category("贈り物", c = "#F2C6D3", fx = "ask", sp = "非日常"),
        )
        val trend = listOf(
            TrendMonth("5月", mapOf("house" to 85000, "comm" to 4270, "food" to 19800, "daily" to 2900, "trans" to 4100, "fun" to 6200, "gift" to 0)),
            TrendMonth("6月", mapOf("house" to 85000, "comm" to 4270, "food" to 21400, "daily" to 3400, "trans" to 3600, "fun" to 2800, "gift" to 3000)),
            TrendMonth("7月", mapOf("house" to 85000, "comm" to 4270, "food" to 18800, "daily" to 2600, "trans" to 6900, "fun" to 19800, "gift" to 0)),
            TrendMonth("8月", mapOf("house" to 85000, "comm" to 4270, "food" to 22600, "daily" to 3100, "trans" to 9200, "fun" to 24500, "gift" to 5400)),
            TrendMonth("9月", mapOf("house" to 85000, "comm" to 4270, "food" to 20100, "daily" to 2800, "trans" to 3900, "fun" to 4600, "gift" to 0)),
        )
        val memos = linkedMapOf(
            "q4" to Memo("花子さんの誕生日、甘い物が良さそう。駅前のケーキ屋を見ておく", "2026-10-31 08:12", listOf("hanako")),
            "q3" to Memo("カフェ ミナトは窓側に電源あり。午後は混む", "2026-10-30 16:40", listOf("minato"), rec = "c1"),
            "q2" to Memo("年末調整の書類、会社のポータルから出す", "2026-10-29 21:15", pin = true),
            "q1" to Memo("洗剤の詰め替えは大きいサイズの方が安い", "2026-10-27 20:10", listOf("drug")),
            "q5" to Memo("引っ越し業者は3社から見積もりを取る", "2026-10-31 09:30", listOf("proj1")),
        )
        val E = Plan.KIND_EVENT; val P = Plan.KIND_PAY; val T = Plan.KIND_TODO
        val plans = linkedMapOf(
            "e9" to Plan(E, "在宅作業", d = "2026-10-31", s = "10:00", e = "12:00", place = PlaceRef("home")),
            "e1" to Plan(E, "花子さんと映画", d = "2026-10-31", s = "15:00", e = "17:10", place = PlaceRef(null, "シネマ青葉"), who = listOf("hanako"), todo = listOf("t2"), memo = "チケットはネットで予約", alarm = "30分前"),
            "e2" to Plan(E, "夕食の買い物", d = "2026-10-31", s = "18:30", e = "19:00", place = PlaceRef("super"), todo = listOf("t4", "t5", "t6"), alarm = "なし"),
            "e3" to Plan(E, "歯医者", d = "2026-11-02", s = "10:00", e = "11:00", place = PlaceRef(null, "さくら歯科"), todo = listOf("t3"), alarm = "1時間前"),
            "e4" to Plan(P, "家賃の支払い", d = "2026-10-27", all = true, rep = "毎月27日", pay = PlanPay(85000, "rent"), rec = "p13"),
            "e5" to Plan(E, "花子さんとランチ", d = "2026-10-30", s = "12:00", e = "13:00", place = PlaceRef("aoba"), who = listOf("hanako"), rec = "l1"),
            "e6" to Plan(E, "温泉旅行", d = "2026-11-08", d2 = "2026-11-09", all = true, place = PlaceRef(null, "宿 山の湯"), who = listOf("hanako")),
            "e7" to Plan(P, "スマホ料金", d = "2026-10-10", all = true, rep = "毎月10日", pay = PlanPay(3280, "phone"), rec = "p14"),
            "e8" to Plan(E, "文化の日", d = "2026-11-03", all = true, hol = true),
            "e10" to Plan(P, "動画サブスク", d = "2026-10-15", all = true, rep = "毎月15日", pay = PlanPay(990, "sub"), rec = "p15"),
            "e11" to Plan(E, "不動産屋と打ち合わせ", d = "2026-11-07", s = "14:00", e = "15:00", place = PlaceRef(null, "不動産ファースト"), ent = listOf("proj1")),
            "t2" to Plan(T, "映画のチケットを予約", due = "2026-10-31", list = "inbox", ev = "e1", pri = true),
            "t9" to Plan(T, "資源ごみを出す", due = "2026-10-31", list = "life", rep = "毎週土曜", done = "2026-10-31"),
            "t7" to Plan(T, "ドラッグ桜のポイントを交換", due = "2026-10-29", list = "inbox", ent = listOf("drug")),
            "t3" to Plan(T, "歯医者の問診票を書く", due = "2026-11-01", list = "life", ev = "e3"),
            "t1" to Plan(T, "花子さんの誕生日プレゼントを選ぶ", due = "2026-11-05", list = "life", pri = true, ent = listOf("hanako"), sub = listOf(SubTask("候補を3つ挙げる", true), SubTask("予算を決める"), SubTask("注文する")), memo = "ノートの「誕生日プレゼント候補」を見る"),
            "t8" to Plan(T, "年末調整の書類を出す", due = "2026-11-14", list = "life"),
            "t4" to Plan(T, "牛乳", list = "shop", ev = "e2"),
            "t6" to Plan(T, "食器用洗剤", list = "shop", ev = "e2"),
            "t5" to Plan(T, "卵", list = "shop", ev = "e2", done = "2026-10-29", payRef = "p2"),
            "t10" to Plan(T, "スマホの料金プランを見直す", list = "inbox", memo = "通信費(固定費)を下げたい"),
            "t11" to Plan(T, "不動産屋に連絡", due = "2026-11-02", list = "life", ent = listOf("proj1")),
            "t12" to Plan(T, "転居の届け出を出す", due = "2026-11-30", list = "life", ent = listOf("proj1")),
        )
        val lists = linkedMapOf("inbox" to TodoList("受信箱"), "life" to TodoList("生活"), "shop" to TodoList("買い物", "super"))
        val typeProps = linkedMapOf(
            "person" to listOf("電話", "誕生日", "住所"), "colleague" to listOf("部署"), "split" to listOf("略称"), "place" to listOf("住所"),
            "shop" to listOf("営業時間", "定休日"), "company" to listOf("所在地"), "thing" to listOf("購入日"), "doc" to listOf("発行元", "期限"), "project" to listOf("期間", "状況"),
        )
        return AppData(
            types = types, entities = ents, notes = notes, acts = a, expenses = x, cats = cats, trend = trend,
            memos = memos, plans = plans, lists = lists, typeProps = typeProps,
            colors = listOf("#8796B5", "#7FB5B9", "#DC97AA", "#9CC7A3", "#8DB0DD", "#B3D8EA", "#F2C6D3", "#A9B4C6"),
        )
    }

    /**
     * 見本データの日付を days 日ずらす(モックの今日 2026-10-31 → 実際の今日)。
     * 日付(YYYY-MM-DD)・日のキー(d1030 など)・曜日・メモの日時・ToDo の期限を揃えて動かす。
     * 繰り返し(毎月27日)は元の日付の「日」を使うので、表示上の日はずれる(見本のため問題なし)。
     */
    fun shift(data: AppData, days: Int): AppData {
        if (days == 0) return data
        fun sd(s: String?): String? = s?.let { if (it.length >= 10 && it[4] == '-') LocalDate.parse(it.substring(0, 10)).plusDays(days.toLong()).toString() + it.substring(10) else it }
        val keyMap = HashMap<String, String>()
        for ((k, v) in data.acts) if (v.type == "日記" && v.date != null) {
            val nd = LocalDate.parse(v.date).plusDays(days.toLong())
            keyMap[k] = "d" + nd.toString().replace("-", "")
        }
        fun mk(k: String?) = k?.let { keyMap[it] ?: it }
        val acts = LinkedHashMap<String, Activity>()
        for ((k, v) in data.acts) {
            val nv = if (v.type == "日記" && v.date != null) {
                val nd = LocalDate.parse(v.date).plusDays(days.toLong())
                v.copy(date = nd.toString(), dd = nd.dayOfMonth.toString(), dw = Dates.wd(nd.toString()), kids = v.kids.map { mk(it)!! })
            } else v.copy(parent = mk(v.parent), kids = v.kids.map { mk(it)!! })
            acts[mk(k)!!] = nv
        }
        val memos = data.memos.mapValuesTo(LinkedHashMap()) { (_, m) -> m.copy(at = sd(m.at)!!, rec = mk(m.rec)) }
        val plans = data.plans.mapValuesTo(LinkedHashMap()) { (_, p) ->
            p.copy(d = sd(p.d), d2 = sd(p.d2), due = sd(p.due), done = sd(p.done), rec = mk(p.rec))
        }
        return data.copy(acts = acts, memos = memos, plans = plans, settings = data.settings.copy(seededShiftDays = days))
    }
}
