package com.pengke.paper.scanner

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Base64
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.helper.DbHelper
import com.pengke.paper.scanner.helper.ImageTable
import com.pengke.paper.scanner.model.Image
import kotlinx.android.synthetic.main.activity_contrast.*
import kotlinx.android.synthetic.main.activity_rotate.cancelBtn
import kotlinx.android.synthetic.main.activity_rotate.decisionBtn
import kotlinx.android.synthetic.main.activity_rotate.imageView
import setContrast
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class ContrastActivity : AppCompatActivity() {
    private lateinit var bm: Bitmap
    private var id = ""
    private val dbHelper = DbHelper(this)
    private var currentVal: Float= 1F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contrast)
        setImage()
        setBtnListener()
        setSlider()
    }

    override fun onBackPressed() {
        toDisableBtns()
        navToImageListScrn()
    }

    private fun setImage() {
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
        bm = bm.setContrast(currentVal)!!
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

    private fun setSlider() {
        var contrast = 1F
        slider.progress = 100
        slider.max = 200
        slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {

            // 値変更時に呼ばれる
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                thread {
                    val value = 200 - progress
                    // contrastの有効範囲は0..2
                    // デフォルトは1
                    contrast = value/100F
                    currentVal = contrast
                }
                imageView.setImageBitmap(
                    bm.setContrast(
                        contrast
                    )
                )
            }

            // つまみタッチ時に呼ばれる
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
               println("ドラッグスタート")
            }

            // つまみリリース時に呼ばれる
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                println("リリース")
            }
        })
    }

    private fun getContentValues(originBinary: ByteArray, thumbBinary: ByteArray): ContentValues {
        return ContentValues().apply {
            put("${ImageTable.COLUMN_NAME_ORIGINAL_BITMAP}", originBinary)
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