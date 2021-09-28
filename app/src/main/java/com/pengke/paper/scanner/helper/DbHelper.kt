package com.pengke.paper.scanner.helper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

// テーブル定義
object ImageTable: BaseColumns {
    const val TABLE_NAME = "images"
    const val COLUMN_NAME_BITMAP = "bitmap"
}

// テーブル作成
private const val SQL_CREATE_IMAGE = "CREATE TABLE ${ImageTable.TABLE_NAME}" +
        " (${BaseColumns._ID} INTEGER PRIMARY KEY, " +
        " ${ImageTable.COLUMN_NAME_BITMAP} BLOB NOT NULL)"

// 削除
private const val SQL_DELETE_IMAGE = "DROP TABLE IF EXISTS ${ImageTable.TABLE_NAME}"



class DbHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null,DATABASE_VERSION ) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(SQL_CREATE_IMAGE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL(SQL_DELETE_IMAGE)
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "Image.db"
        private const val DATABASE_VERSION = 1
    }
}