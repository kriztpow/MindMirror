package com.example.mindmirror

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DetectionRecord(val id: Long, val timestamp: Long, val label: String, val score: Float)

class DetectionDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "mindmirror.db"
        const val DATABASE_VERSION = 1
        const val TABLE = "detections"
        const val COL_ID = "_id"
        const val COL_TIMESTAMP = "ts"
        const val COL_LABEL = "label"
        const val COL_SCORE = "score"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER,
                $COL_LABEL TEXT,
                $COL_SCORE REAL
            )
        """.trimIndent()
        db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun insert(timestamp: Long, label: String, score: Float) : Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_TIMESTAMP, timestamp)
            put(COL_LABEL, label)
            put(COL_SCORE, score)
        }
        return db.insert(TABLE, null, cv)
    }

    fun queryAll(): List<DetectionRecord> {
        val db = readableDatabase
        val cur: Cursor = db.query(TABLE, arrayOf(COL_ID, COL_TIMESTAMP, COL_LABEL, COL_SCORE), null, null, null, null, COL_TIMESTAMP + " DESC")
        val res = mutableListOf<DetectionRecord>()
        while (cur.moveToNext()) {
            res.add(DetectionRecord(cur.getLong(0), cur.getLong(1), cur.getString(2), cur.getFloat(3)))
        }
        cur.close()
        return res
    }
}