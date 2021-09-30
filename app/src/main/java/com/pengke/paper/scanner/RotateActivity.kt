package com.pengke.paper.scanner

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Base64
import androidx.core.database.getBlobOrNull
import com.google.gson.Gson
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.helper.DbHelper
import com.pengke.paper.scanner.helper.ImageTable
import com.pengke.paper.scanner.model.Image
import kotlinx.android.synthetic.main.activity_rotate.*
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class RotateActivity : AppCompatActivity() {
    private lateinit var bm: Bitmap
    private var id = ""
    private val matrix = Matrix()
    private val dbHelper = DbHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rotate)
        setImage()
        setBtnListener()
    }

    override fun onBackPressed() {
        toDisableBtns()
        navToImageListScrn()
    }

    private fun setImage() {
        // タップされた画像のIDを取得
        id = intent.getStringExtra(ID).toString()
        val db = dbHelper.readableDatabase
        val selection = "${BaseColumns._ID} = ?"
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(ImageTable.COLUMN_NAME_BITMAP),
            selection,
            arrayOf(id),
            null,
            null,
            null,
        )
        cursor.moveToFirst()
        val blob = cursor.getBlob(0)
        bm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
        imageView.setImageBitmap(bm)
    }

    private fun setBtnListener() {
        matrix.setRotate(90F, bm.width/2F, bm.height/2F)
        rotateBtn.setOnClickListener {
            bm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, matrix, true)
            imageView.setImageBitmap(bm)
        }

        cancelBtn.setOnClickListener {
            toDisableBtns()
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            toDisableBtns()
            thread {
                update()
                navToImageListScrn()
            }
        }
    }

    private fun toDisableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun update() {
        val db = dbHelper.writableDatabase
        val thumbBm = Bitmap.createScaledBitmap(bm, bm.width/2, bm.height/2, false)
        val original = getBinaryFromBitmap(bm)
        val thumb = getBinaryFromBitmap(thumbBm)
        val values = getContentValues(original, thumb)
        val selection = "${BaseColumns._ID} = ?"
        db.update(
            ImageTable.TABLE_NAME,
            values,
            selection,
            arrayOf(id)
        )
    }

    private fun getContentValues(originBinary: ByteArray, thumbBinary: ByteArray): ContentValues {
        return ContentValues().apply {
            put("${ImageTable.COLUMN_NAME_BITMAP}", originBinary)
            put("${ImageTable.COLUMN_NAME_THUMB_BITMAP}", thumbBinary)
        }
    }

    private fun getBinaryFromBitmap(bitmap: Bitmap): ByteArray{
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(ID, id)
        startActivityForResult(intent, 100)
        finish()
    }
}