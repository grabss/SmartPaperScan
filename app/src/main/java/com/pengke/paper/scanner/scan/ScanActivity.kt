package com.pengke.paper.scanner.scan

import android.app.ActivityManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.opengl.Visibility
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Display
import android.view.SurfaceView
import android.view.View
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.pengke.paper.scanner.ConfirmDialogFragment
import com.pengke.paper.scanner.ImageListActivity
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.BaseActivity
import com.pengke.paper.scanner.base.CAN_EDIT_IMAGES
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.jsonToImageArray
import com.pengke.paper.scanner.model.Image
import com.pengke.paper.scanner.view.PaperRectangle

import kotlinx.android.synthetic.main.activity_scan.*
import org.json.JSONArray
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

const val IMAGE_COUNT_RESULT = 1000
const val REQUEST_GALLERY_TAKE = 1

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private val REQUEST_CAMERA_PERMISSION = 0
    private val EXIT_TIME = 2000

    private lateinit var mPresenter: ScanPresenter

    private var latestBackPressTime: Long = 0
    private lateinit var sp: SharedPreferences

    private var count = 0

    private val gson = Gson()

    override fun provideContentViewId(): Int = R.layout.activity_scan

    private var needFlash = false
    private lateinit var matrix: Matrix

    override fun initPresenter() {
        mPresenter = ScanPresenter(this, this, this)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        matrix = Matrix()
        matrix.postRotate(90F)
//        sp.edit().clear().apply()
    }

    override fun prepare() {
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "loading opencv error, exit")
            finish()
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CAMERA_PERMISSION)
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CAMERA_PERMISSION)
        }

        setBtnListener()
        latestBackPressTime = System.currentTimeMillis()
    }

    private fun setBtnListener() {
        flashBtn.setOnClickListener {
            thread {
                mPresenter.toggleFlashMode()
            }
            needFlash = !needFlash
            if (needFlash) {
                flashBtn.setImageResource(R.drawable.ic_baseline_flash_on_24)
            } else {
                flashBtn.setImageResource(R.drawable.ic_baseline_flash_off_24)
            }
        }

        gallery.setOnClickListener {
            val editor = sp.edit()
            editor.putBoolean(CAN_EDIT_IMAGES, false).apply()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)

                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_GALLERY_TAKE)
        }

        shut.setOnClickListener {
            toDisableBtns()
            mPresenter.shut()
        }

        complete.setOnClickListener {
            toDisableBtns()
            val intent = Intent(this, ImageListActivity::class.java)
            startActivity(intent)
            val editor = sp.edit()
            editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
        }
    }

    fun setSlider(max: Int?) {
        println("max: $max")
        exposureSlider.progress = max ?: 0

        // Android8.0以上でしかminの値がセットできないため、
        // maxの値を2倍に
        if (max != null) {
            exposureSlider.max = max * 2
        }
        exposureSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {

            // 値変更時に呼ばれる
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (max ?: 0) - progress
                println("value: $value")
                mPresenter.setExposure(value)
            }

            // つまみタッチ時に呼ばれる
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                println("ドラッグスタート")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                println("リリース")
            }
        })
    }

    private fun adjustBtnsState() {
        if(count == 0) {
            complete.isEnabled = false
        }
    }

    private fun toDisableBtns() {
        shut.isEnabled = false
        complete.isEnabled = false
    }

    private fun toEnableBtns() {
        shut.isEnabled = true
        complete.isEnabled = true
    }

    fun updateCount() {
        count = getImageCount()
        // UI更新をメインスレッドで行うための記述
        Handler(Looper.getMainLooper()).post  {
            shut.text = count.toString()
            toEnableBtns()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_GALLERY_TAKE && resultCode == RESULT_OK) {
            if (data?.clipData != null) {
                println("複数選択")

                val intent = Intent(this, ImageListActivity::class.java)
                startActivity(intent)

                thread {
                    // 複数画像選択時
                    val count = data.clipData!!.itemCount
                    println("count: $count")
                    val images = ArrayList<Image>()
                    for (i in 0 until count) {
                        val imageUri = data.clipData!!.getItemAt(i).uri
                        val byte = this.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        var bitmap = BitmapFactory.decodeByteArray(byte, 0, byte!!.size)

                        // リサイズ
                        val matrix = Matrix()
                        matrix.postScale(0.5f, 0.5f)
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        val rotatedBm = rotate(bitmap)
                        val baos = ByteArrayOutputStream()
                        rotatedBm.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                        val b = baos.toByteArray()
                        val uuid = UUID.randomUUID().toString()
                        val b64 = Base64.encodeToString(b, Base64.DEFAULT)
                        val image = Image(id = uuid, b64 = b64, originalB64 = b64)
                        images.add(image)
                    }
                    saveImages(images)
                }
            } else if(data?.data != null) {
                println("単体選択")
                val intent = Intent(this, ImageListActivity::class.java)
                startActivity(intent)
                
                thread {
                    // 単体選択時
                    val imageUri = data.data!!
                    val byte = this.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    var bitmap = BitmapFactory.decodeByteArray(byte, 0, byte!!.size)

                    // リサイズ
                    val matrix = Matrix()
                    matrix.postScale(0.5f, 0.5f)
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    val rotatedBm = rotate(bitmap)
                    val baos = ByteArrayOutputStream()
                    rotatedBm.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    val b = baos.toByteArray()
                    val uuid = UUID.randomUUID().toString()
                    val b64 = Base64.encodeToString(b, Base64.DEFAULT)
                    val image = Image(id = uuid, b64 = b64, originalB64 = b64)
                    saveImage(image)
                }
            }
        }
    }

    private fun rotate(bm: Bitmap): Bitmap {
        return Bitmap.createBitmap(
            bm,
            0,
            0,
            bm.width,
            bm.height,
            matrix,
            true
        )
    }

    private fun saveImage(image: Image) {
        val images = mutableListOf<Image>()
        images.add(image)
        val editor = sp.edit()
        editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
        editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
    }


    // 複数画像
    private fun saveImages(images: ArrayList<Image>) {
        val editor = sp.edit()
        editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
        editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
    }

    // 撮影済み画像枚数取得
    private fun getImageCount(): Int {
        val json = sp.getString(IMAGE_ARRAY, null)
        return if (json == null) {
            0
        } else {
            val images = jsonToImageArray(json)
            images.size
        }
    }

    // 初回カメラ起動時、画像一覧画面から戻ってきた場合にのみ呼ばれる
    override fun onStart() {
        println("onStart")
        super.onStart()
        needFlash = false
        flashBtn.setImageResource(R.drawable.ic_baseline_flash_off_24)
        count = getImageCount()
        shut.text = count.toString()
        toEnableBtns()
        adjustBtnsState()
        mPresenter.initImageArray()
        mPresenter.start()
    }

    override fun onStop() {
        println("onStop")
        super.onStop()
        mPresenter.stop()
    }

    override fun exit() {
        println("exit")
        finish()
    }

    override fun onBackPressed() {
        if (System.currentTimeMillis().minus(latestBackPressTime) > EXIT_TIME) {
            showMessage(R.string.press_again_logout)
        } else {
            super.onBackPressed()
        }
        latestBackPressTime = System.currentTimeMillis()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && (grantResults[permissions.indexOf(android.Manifest.permission.CAMERA)] == PackageManager.PERMISSION_GRANTED)) {
            showMessage(R.string.camera_grant)
            mPresenter.initCamera()
            mPresenter.updateCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getDisplay(): Display = windowManager.defaultDisplay

    override fun getSurfaceView(): SurfaceView = surface

    override fun getPaperRect(): PaperRectangle = paper_rect

}