package com.amgm.personallog.v4.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// 色は使う側の Icon(tint=...) で付ける。ここでは黒で作る。
private val Ink = SolidColor(Color.Black)

// 線(2px・丸端・丸結合)。filled=true は SVG の fill="currentColor" 付きの要素。
// その要素は線も同じ太さで描かれるので、塗りと線の両方を付ける。
private fun ImageVector.Builder.strokeD(d: String, filled: Boolean = false): ImageVector.Builder =
    addPath(
        pathData = addPathNodes(d),
        fill = if (filled) Ink else null,
        stroke = Ink,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

// 面(塗りのみ、線なし)。evenOdd は SVG の fill-rule="evenodd"。
private fun ImageVector.Builder.fillD(d: String, evenOdd: Boolean = false): ImageVector.Builder =
    addPath(
        pathData = addPathNodes(d),
        fill = Ink,
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
    )

// 24x24 の ImageVector を組み立てる共通部分。
private fun makeIcon(name: String, block: ImageVector.Builder.() -> ImageVector.Builder): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).block().build()

/* 線のアイコン(IC の移植)。キーは HTML 側の IC と同じ名前。 */
object V4Icons {

    /* 家=ホーム(毎日最初に戻る場所) */
    val home: ImageVector by lazy {
        makeIcon("home") {
            strokeD("M4 11.5 12 5l8 6.5")
                .strokeD("M6.5 10v9h11v-9")
                .strokeD("M10.5 19v-4.5h3V19")
        }
    }

    /* 角を折った紙=メモ */
    val memo: ImageVector by lazy {
        makeIcon("memo") {
            strokeD("M6 3.5h8.5L19 8v12.5H6z")
                .strokeD("M14.5 3.5V8H19")
                .strokeD("M9 12h7M9 15.5h5")
        }
    }

    /* 未来=点線の軸と角丸の枠(枠の点線は省略して実線。下のメモ参照) */
    val future: ImageVector by lazy {
        makeIcon("future") {
            // 軸の点線: 2px の線, 間隔 2.6 を展開して書く
            strokeD("M5 2.5v2M5 7.1v2M5 11.7v2M5 16.3v2M5 20.9v0.6")
                .strokeD("M12.5 8H17.5A4 4 0 0 1 21.5 12A4 4 0 0 1 17.5 16H12.5A4 4 0 0 1 8.5 12A4 4 0 0 1 12.5 8Z")
        }
    }

    /* 現在=軸と塗りのカプセル */
    val moment: ImageVector by lazy {
        makeIcon("moment") {
            strokeD("M5 2.5v19")
                .strokeD("M12.5 8H17.5A4 4 0 0 1 21.5 12A4 4 0 0 1 17.5 16H12.5A4 4 0 0 1 8.5 12A4 4 0 0 1 12.5 8Z", filled = true)
        }
    }

    /* 行の並び=一覧 */
    val lines: ImageVector by lazy {
        makeIcon("lines") {
            strokeD("M5 7h14M5 12h14M5 17h9")
        }
    }

    /* カテゴリ=3段の帯(上段だけ塗り) */
    val cats: ImageVector by lazy {
        makeIcon("cats") {
            strokeD("M5 5H9A2 2 0 0 1 11 7A2 2 0 0 1 9 9H5A2 2 0 0 1 3 7A2 2 0 0 1 5 5Z", filled = true)
                .strokeD("M5 11H14A2 2 0 0 1 16 13A2 2 0 0 1 14 15H5A2 2 0 0 1 3 13A2 2 0 0 1 5 11Z")
                .strokeD("M5 17H19A2 2 0 0 1 21 19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19A2 2 0 0 1 5 17Z")
        }
    }

    /* きらめき(AI・提案) */
    val spark: ImageVector by lazy {
        makeIcon("spark") {
            strokeD("M12 4v4M12 16v4M4 12h4M16 12h4M6.5 6.5l2.5 2.5M15 15l2.5 2.5M17.5 6.5 15 9M9 15l-2.5 2.5")
        }
    }

    /* 人=家族・知人 */
    val kin: ImageVector by lazy {
        makeIcon("kin") {
            strokeD("M3.5,9.5 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0")
                .strokeD("M16.5 21.5s-5-4.3-5-8.2a5 5 0 0 1 10 0c0 3.9-5 8.2-5 8.2Z")
                .strokeD("M14.9,13.3 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0", filled = true)
        }
    }

    /* 共有=左が塗りの帯 */
    val share: ImageVector by lazy {
        makeIcon("share") {
            strokeD("M4 6.5H20A1.5 1.5 0 0 1 21.5 8V12A1.5 1.5 0 0 1 20 13.5H4A1.5 1.5 0 0 1 2.5 12V8A1.5 1.5 0 0 1 4 6.5Z")
                .strokeD("M4 6.5H9.5A1.5 1.5 0 0 1 11 8V12A1.5 1.5 0 0 1 9.5 13.5H4A1.5 1.5 0 0 1 2.5 12V8A1.5 1.5 0 0 1 4 6.5Z", filled = true)
                .strokeD("M2.5 17v2.5h8.5V17")
        }
    }

    /* 軸=記録の軸(点と区間) */
    val axis: ImageVector by lazy {
        makeIcon("axis") {
            strokeD("M6 2.5v19")
                .strokeD("M5.1 4.5H6.9A1.6 1.6 0 0 1 8.5 6.1V7.9A1.6 1.6 0 0 1 6.9 9.5H5.1A1.6 1.6 0 0 1 3.5 7.9V6.1A1.6 1.6 0 0 1 5.1 4.5Z", filled = true)
                .strokeD("M3.6,16 a2.4,2.4 0 1,0 4.8,0 a2.4,2.4 0 1,0 -4.8,0")
                .strokeD("M11.5 7h9M11.5 16h7")
        }
    }

    /* 出来事(既定の活動アイコン) */
    val activity: ImageVector by lazy {
        makeIcon("activity") {
            strokeD("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0")
                .strokeD("M12 7.5V12l3 2")
        }
    }

    /* 予定。元は点線のカプセル。点線は省略して実線(下のメモ参照) */
    val schedule: ImageVector by lazy {
        makeIcon("schedule") {
            strokeD("M8 7H16A5 5 0 0 1 16 17H8A5 5 0 0 1 8 7Z")
        }
    }

    /* ToDo=角丸の箱にチェック */
    val todo: ImageVector by lazy {
        makeIcon("todo") {
            strokeD("M8 3.5H16A4.5 4.5 0 0 1 20.5 8V16A4.5 4.5 0 0 1 16 20.5H8A4.5 4.5 0 0 1 3.5 16V8A4.5 4.5 0 0 1 8 3.5Z")
                .strokeD("m8 12.5 3 3 5-6")
        }
    }

    /* エンティティ=フォルダ */
    val entity: ImageVector by lazy {
        makeIcon("entity") {
            strokeD("M3 6.5A2.5 2.5 0 0 1 5.5 4h4.2l2 2.5h6.8A2.5 2.5 0 0 1 21 9v9.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5Z")
        }
    }

    /* ノート=折り返しのある紙 */
    val note: ImageVector by lazy {
        makeIcon("note") {
            strokeD("M6 3h8.5L20 8.5V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z")
                .strokeD("M14 3v6h6M8 13h8M8 17h5")
        }
    }

    /* 種類=タグ */
    val type: ImageVector by lazy {
        makeIcon("type") {
            strokeD("M3 12 10 4.5h9.5A1.5 1.5 0 0 1 21 6v12a1.5 1.5 0 0 1-1.5 1.5H10Z")
                .strokeD("M10.7,12 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0", filled = true)
        }
    }

    /* 支出=左が塗りの帯 */
    val expense: ImageVector by lazy {
        makeIcon("expense") {
            strokeD("M4 8.5H20A1.5 1.5 0 0 1 21.5 10V14A1.5 1.5 0 0 1 20 15.5H4A1.5 1.5 0 0 1 2.5 14V10A1.5 1.5 0 0 1 4 8.5Z")
                .strokeD("M4 8.5H9.5A1.5 1.5 0 0 1 11 10V14A1.5 1.5 0 0 1 9.5 15.5H4A1.5 1.5 0 0 1 2.5 14V10A1.5 1.5 0 0 1 4 8.5Z", filled = true)
        }
    }

    /* 家計簿=棒グラフ */
    val ledger: ImageVector by lazy {
        makeIcon("ledger") {
            strokeD("M4 20V10M10 20V4M16 20v-7M22 20H2")
        }
    }

    /* 支払い=カード */
    val pay: ImageVector by lazy {
        makeIcon("pay") {
            strokeD("M5 5.5H19A2.5 2.5 0 0 1 21.5 8V16A2.5 2.5 0 0 1 19 18.5H5A2.5 2.5 0 0 1 2.5 16V8A2.5 2.5 0 0 1 5 5.5Z")
                .strokeD("M2.5 10h19M6.5 15h4")
        }
    }

    /* 会話=吹き出し */
    val talk: ImageVector by lazy {
        makeIcon("talk") {
            strokeD("M4 5h16v11H9l-5 4Z")
        }
    }

    /* 移動=矢印 */
    val move: ImageVector by lazy {
        makeIcon("move") {
            strokeD("M5 19 19 5M19 5h-8M19 5v8")
        }
    }

    /* 日記=ノートの綴じ */
    val diary: ImageVector by lazy {
        makeIcon("diary") {
            strokeD("M6.5 3.5H17.5A2.5 2.5 0 0 1 20 6V18A2.5 2.5 0 0 1 17.5 20.5H6.5A2.5 2.5 0 0 1 4 18V6A2.5 2.5 0 0 1 6.5 3.5Z")
                .strokeD("M8 3.5v17M11.5 8h5M11.5 12h5")
        }
    }

    /* カメラ */
    val camera: ImageVector by lazy {
        makeIcon("camera") {
            strokeD("M4 7.5h3l1.5-2.5h7L17 7.5h3a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1Z")
                .strokeD("M8.5,13 a3.5,3.5 0 1,0 7,0 a3.5,3.5 0 1,0 -7,0")
        }
    }

    /* 写真 */
    val photo: ImageVector by lazy {
        makeIcon("photo") {
            strokeD("M5.5 4H18.5A2.5 2.5 0 0 1 21 6.5V17.5A2.5 2.5 0 0 1 18.5 20H5.5A2.5 2.5 0 0 1 3 17.5V6.5A2.5 2.5 0 0 1 5.5 4Z")
                .strokeD("M6.7,9.5 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0")
                .strokeD("m21 16-5-5-9 9")
        }
    }

    /* 追加 */
    val plus: ImageVector by lazy {
        makeIcon("plus") {
            strokeD("M12 5v14M5 12h14")
        }
    }

    /* 戻る */
    val back: ImageVector by lazy {
        makeIcon("back") {
            strokeD("M15 5l-7 7 7 7")
        }
    }

    /* 閉じる */
    val close: ImageVector by lazy {
        makeIcon("close") {
            strokeD("M6 6l12 12M18 6 6 18")
        }
    }

    /* 右矢印(シェブロン) */
    val chev: ImageVector by lazy {
        makeIcon("chev") {
            strokeD("m9 6 6 6-6 6")
        }
    }

    /* 上 */
    val up: ImageVector by lazy {
        makeIcon("up") {
            strokeD("m6 15 6-6 6 6")
        }
    }

    /* 下 */
    val down: ImageVector by lazy {
        makeIcon("down") {
            strokeD("m6 9 6 6 6-6")
        }
    }

    /* その他(3つの点。線なしの塗り) */
    val more: ImageVector by lazy {
        makeIcon("more") {
            fillD("M3.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0M10.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0M17.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0")
        }
    }

    /* 検索 */
    val search: ImageVector by lazy {
        makeIcon("search") {
            strokeD("M4.5,11 a6.5,6.5 0 1,0 13,0 a6.5,6.5 0 1,0 -13,0")
                .strokeD("m20 20-4.2-4.2")
        }
    }

    /* チェック */
    val check: ImageVector by lazy {
        makeIcon("check") {
            strokeD("m5 12.5 4.5 4.5L19 7.5")
        }
    }

    /* 編集 */
    val edit: ImageVector by lazy {
        makeIcon("edit") {
            strokeD("M4 20h4L19 9l-4-4L4 16Z")
        }
    }

    /* 削除 */
    val trash: ImageVector by lazy {
        makeIcon("trash") {
            strokeD("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
        }
    }

    /* 警告 */
    val warn: ImageVector by lazy {
        makeIcon("warn") {
            strokeD("M12 4 2.5 20h19Z")
                .strokeD("M12 10v4M12 17.2v.1")
        }
    }

    /* 情報 */
    val info: ImageVector by lazy {
        makeIcon("info") {
            strokeD("M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0")
                .strokeD("M12 11v5M12 8v.1")
        }
    }

    /* 通知 */
    val bell: ImageVector by lazy {
        makeIcon("bell") {
            strokeD("M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15Z")
                .strokeD("M10 20a2 2 0 0 0 4 0")
        }
    }

    /* 繰り返し */
    val repeat: ImageVector by lazy {
        makeIcon("repeat") {
            strokeD("M4 11V9a3 3 0 0 1 3-3h12M16 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H5M8 21l-3-3 3-3")
        }
    }

    /* 場所=ピン */
    val place: ImageVector by lazy {
        makeIcon("place") {
            strokeD("M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21Z")
                .strokeD("M9.5,9.5 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0")
        }
    }

    /* 人々 */
    val people: ImageVector by lazy {
        makeIcon("people") {
            strokeD("M5.5,8 a3.5,3.5 0 1,0 7,0 a3.5,3.5 0 1,0 -7,0")
                .strokeD("M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5")
        }
    }

    /* リンク */
    val link: ImageVector by lazy {
        makeIcon("link") {
            strokeD("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
        }
    }

    /* きらめき(塗りのみ。sv の第2引数 fill=1 に当たる) */
    val sparkle: ImageVector by lazy {
        makeIcon("sparkle") {
            fillD("M12 3c.6 4.2 2.8 6.4 7 7-4.2.6-6.4 2.8-7 7-.6-4.2-2.8-6.4-7-7 4.2-.6 6.4-2.8 7-7Z")
        }
    }

    /* 設定=歯車(線) */
    val settings: ImageVector by lazy {
        makeIcon("settings") {
            strokeD("M9,12 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0")
                .strokeD("M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3.6 14H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.1-2.7l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9.7 3.6V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z")
        }
    }

    /* actIcon: 活動の小分類(ACT_IC)からアイコンを引く。無ければ activity */
    /** 種類 → 印(ui.js AX_NODE と同じ。詳細の種類の札・タイムラインの節で共通) */
    fun actIcon(type: String): ImageVector = when (type) {
        "支払い" -> pay
        "会話" -> talk
        "移動" -> move
        "日記" -> diary
        "出来事" -> spark
        "食事", "買い物", "作業" -> moment
        else -> activity
    }

    /* icon: IC のキー文字列から引く。無ければ null */
    fun icon(name: String): ImageVector? = when (name) {
        "home" -> home
        "memo" -> memo
        "future" -> future
        "moment" -> moment
        "lines" -> lines
        "cats" -> cats
        "spark" -> spark
        "kin" -> kin
        "share" -> share
        "axis" -> axis
        "activity" -> activity
        "schedule" -> schedule
        "todo" -> todo
        "entity" -> entity
        "note" -> note
        "type" -> type
        "expense" -> expense
        "ledger" -> ledger
        "pay" -> pay
        "talk" -> talk
        "move" -> move
        "diary" -> diary
        "camera" -> camera
        "photo" -> photo
        "plus" -> plus
        "back" -> back
        "close" -> close
        "chev" -> chev
        "up" -> up
        "down" -> down
        "more" -> more
        "search" -> search
        "check" -> check
        "edit" -> edit
        "trash" -> trash
        "warn" -> warn
        "info" -> info
        "bell" -> bell
        "repeat" -> repeat
        "place" -> place
        "people" -> people
        "link" -> link
        "sparkle" -> sparkle
        "settings" -> settings
        else -> null
    }
}

/* 面のアイコン(NAV_IC の移植)。塗りのみ。キーはタブのキーと同じ。 */
object V4NavIcons {
    /** タブのキー → アイコン */
    fun byKey(k: String): ImageVector = when (k) { "home" -> home; "activities" -> activities; "memo" -> memo; "todo" -> todo; "entities" -> entities; "ledger" -> ledger; else -> settings }


    /* 家=ホーム: 屋根と壁の面、戸口は抜く */
    val home: ImageVector by lazy {
        makeIcon("nav_home") {
            fillD("M12 2.8 2.9 10.1a1 1 0 0 0-.4.8v8.9a1.7 1.7 0 0 0 1.7 1.7h15.6a1.7 1.7 0 0 0 1.7-1.7v-8.9a1 1 0 0 0-.4-.8Z M9.8 21.5v-5a1.2 1.2 0 0 1 1.2-1.2h2a1.2 1.2 0 0 1 1.2 1.2v5Z", evenOdd = true)
        }
    }

    /* 時間の軸=記録: 軸の上に塗りの区間と点 */
    val activities: ImageVector by lazy {
        makeIcon("nav_activities") {
            fillD("M3 3.3A1.3 1.3 0 0 1 5.6 3.3V20.7A1.3 1.3 0 0 1 3 20.7Z")
                .fillD("M11 3.5H18A3 3 0 0 1 18 9.5H11A3 3 0 0 1 11 3.5Z")
                .fillD("M11 13H13.5A3 3 0 0 1 13.5 19H11A3 3 0 0 1 11 13Z")
                .fillD("M1.9,19 a2.4,2.4 0 1,0 4.8,0 a2.4,2.4 0 1,0 -4.8,0")
        }
    }

    /* 角を折った紙=メモ: 折り返しと文字の行は抜く */
    val memo: ImageVector by lazy {
        makeIcon("nav_memo") {
            fillD("M6 2.5h8.3l5.2 5.2V20a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 20V4A1.5 1.5 0 0 1 6 2.5Z M14.3 2.5v5.2h5.2Z M8.2 11.2h7.6v1.8H8.2Z M8.2 15h4.6v1.8H8.2Z", evenOdd = true)
        }
    }

    /* 箱の中のチェック=ToDo: チェックは抜く */
    val todo: ImageVector by lazy {
        makeIcon("nav_todo") {
            fillD("M8 3h8a5 5 0 0 1 5 5v8a5 5 0 0 1-5 5H8a5 5 0 0 1-5-5V8a5 5 0 0 1 5-5Z M6.6 12.4l1.9-1.9 1.8 1.8 5.1-5.1 1.9 1.9-7 7Z", evenOdd = true)
        }
    }

    /* 人の輪郭+ピン=エンティティ */
    val entities: ImageVector by lazy {
        makeIcon("nav_entities") {
            fillD("M4.5,7.5 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0")
                .fillD("M1.5 21.5a7 7 0 0 1 14 0Z")
                .fillD("M18 2.8a4.7 4.7 0 0 1 4.7 4.7c0 3.3-4.7 9.2-4.7 9.2s-4.7-5.9-4.7-9.2A4.7 4.7 0 0 1 18 2.8Zm0 3.1a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2Z", evenOdd = true)
        }
    }

    /* 棒の一部=家計簿: 3本の棒と台 */
    val ledger: ImageVector by lazy {
        makeIcon("nav_ledger") {
            fillD("M4.2 11H6A1.2 1.2 0 0 1 7.2 12.2V19.3A1.2 1.2 0 0 1 6 20.5H4.2A1.2 1.2 0 0 1 3 19.3V12.2A1.2 1.2 0 0 1 4.2 11Z")
                .fillD("M11.1 4H12.9A1.2 1.2 0 0 1 14.1 5.2V19.3A1.2 1.2 0 0 1 12.9 20.5H11.1A1.2 1.2 0 0 1 9.9 19.3V5.2A1.2 1.2 0 0 1 11.1 4Z")
                .fillD("M18 8H19.8A1.2 1.2 0 0 1 21 9.2V19.3A1.2 1.2 0 0 1 19.8 20.5H18A1.2 1.2 0 0 1 16.8 19.3V9.2A1.2 1.2 0 0 1 18 8Z")
                .fillD("M2.8 20.6H21.2A0.8 0.8 0 0 1 22 21.4A0.8 0.8 0 0 1 21.2 22.2H2.8A0.8 0.8 0 0 1 2 21.4A0.8 0.8 0 0 1 2.8 20.6Z")
        }
    }

    /* 歯車=設定: 中心の穴は抜く */
    val settings: ImageVector by lazy {
        makeIcon("nav_settings") {
            fillD("M19.60 12.00 L22.10 13.44 L21.65 15.30 L18.77 15.45 L18.15 16.47 L17.37 17.37 L18.12 20.16 L16.49 21.16 L14.35 19.23 L13.19 19.51 L12.00 19.60 L10.56 22.10 L8.70 21.65 L8.55 18.77 L7.53 18.15 L6.63 17.37 L3.84 18.12 L2.84 16.49 L4.77 14.35 L4.49 13.19 L4.40 12.00 L1.90 10.56 L2.35 8.70 L5.23 8.55 L5.85 7.53 L6.63 6.63 L5.88 3.84 L7.51 2.84 L9.65 4.77 L10.81 4.49 L12.00 4.40 L13.44 1.90 L15.30 2.35 L15.45 5.23 L16.47 5.85 L17.37 6.63 L20.16 5.88 L21.16 7.51 L19.23 9.65 L19.51 10.81ZM8.7 12a3.3 3.3 0 1 0 6.6 0a3.3 3.3 0 1 0 -6.6 0Z", evenOdd = true)
        }
    }
}
