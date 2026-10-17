package com.amgm.personallog.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Room を開く前に、DB の版が古ければ DB ファイル(と -wal・-shm)を filesDir/db-backup/v<旧版>/ に複製する。
 * 移行に失敗しても元のデータが残るようにするため。複製は同じ版で一度だけ行い、既にあれば上書きしない。
 * 複製の失敗でアプリの起動は止めない(ログだけ残す)。
 */
object PreMigrationBackup {
    private const val TAG = "PreMigrationBackup"
    private const val DIR = "db-backup"

    /** DB の user_version を読み取り専用で調べる。ファイルが無い・読めないときは null。 */
    fun readUserVersion(dbFile: File): Int? {
        if (!dbFile.isFile) return null
        return try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (e: Exception) {
            Log.w(TAG, "user_version を読めませんでした", e)
            null
        }
    }

    /**
     * 複製した場合はその置き先のディレクトリ、しなかった(不要・既にある・失敗)場合は null。
     * targetVersion: Room の現在の版(これ未満の DB だけ複製する)。
     */
    fun backupIfNeeded(context: Context, dbName: String, targetVersion: Int): File? = try {
        backupIfNeeded(context.getDatabasePath(dbName), File(context.filesDir, DIR), targetVersion)
    } catch (e: Exception) {
        Log.w(TAG, "移行前の複製に失敗しました", e)
        null
    }

    internal fun backupIfNeeded(dbFile: File, backupRoot: File, targetVersion: Int): File? {
        val version = readUserVersion(dbFile) ?: return null
        // 版0 は空の DB(未初期化)。複製しない
        if (version <= 0 || version >= targetVersion) return null
        val dest = File(backupRoot, "v$version")
        if (File(dest, dbFile.name).isFile) return null // 同じ版では一度だけ。上書きしない

        // 一時のディレクトリに作ってから名前を変える(途中で落ちた不完全な複製を「ある」とみなさない)
        val tmp = File(backupRoot, "v$version.tmp")
        tmp.deleteRecursively()
        if (!tmp.mkdirs()) return null
        try {
            for (suffix in listOf("", "-wal", "-shm")) {
                val src = File(dbFile.path + suffix)
                if (src.isFile) src.copyTo(File(tmp, src.name), overwrite = false)
            }
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) return null
        } catch (e: Exception) {
            tmp.deleteRecursively()
            throw e
        }
        return dest
    }
}
