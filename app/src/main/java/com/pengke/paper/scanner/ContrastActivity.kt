package com.pengke.paper.scanner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.pengke.paper.scanner.base.SPKEY
import com.pengke.paper.scanner.base.SPNAME
import kotlinx.android.synthetic.main.activity_contrast.*
import kotlinx.android.synthetic.main.activity_rotate.*
import kotlinx.android.synthetic.main.activity_rotate.cancelBtn
import kotlinx.android.synthetic.main.activity_rotate.decisionBtn
import kotlinx.android.synthetic.main.activity_rotate.imageView
import org.json.JSONArray
import setContrast
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class ContrastActivity : AppCompatActivity() {
    private lateinit var sp: SharedPreferences
    private lateinit var decodedImg: Bitmap
    private lateinit var jsons: JSONArray
    private var index = 0
    private var currentVal: Float= 1F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contrast)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        setImage()
        setBtnListener()
        setSlider()
    }

    private fun setImage() {
        // タップされた画像のインデックスを取得
        index = intent.getIntExtra(INDEX, 0)

        val images = sp.getString(SPKEY, null)
        jsons = JSONArray(images)
        val b64Image = jsons[index] as String
        val imageBytes = Base64.decode(b64Image, Base64.DEFAULT)
        decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        imageView.setImageBitmap(decodedImg)
    }

    private fun setBtnListener() {
        cancelBtn.setOnClickListener {
            disableBtns()
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            disableBtns()
            thread {
                setUpdatedImage()
                navToImageListScrn()
            }
        }
    }

    private fun disableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun setUpdatedImage() {
        decodedImg = decodedImg.setContrast(currentVal)!!
        val baos = ByteArrayOutputStream()
        decodedImg.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val b = baos.toByteArray()
        val updatedImg = Base64.encodeToString(b, Base64.DEFAULT)
        jsons.put(index, updatedImg)
        val editor = sp.edit()
        editor.putString(SPKEY, jsons.toString()).apply()
    }

    private fun setSlider() {
        var contrast = 1F
        slider.progress = 100
        slider.max = 200
        slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {

            // 値変更時に呼ばれる
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 200 - progress
                // contrastの有効範囲は0..2
                // デフォルトは1
                contrast = value/100F
            }

            // つまみタッチ時に呼ばれる
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
               println("ドラッグスタート")
            }

            // つまみリリース時に呼ばれる
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                println("リリース")
                currentVal = contrast
                imageView.setImageBitmap(
                    decodedImg.setContrast(
                        contrast
                    )
                )
            }
        })
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(INDEX, index)
        startActivityForResult(intent, 100)
        finish()
    }
}