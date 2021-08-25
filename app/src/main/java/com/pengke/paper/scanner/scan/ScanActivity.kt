package com.pengke.paper.scanner.scan

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Display
import android.view.SurfaceView
import com.pengke.paper.scanner.ImageListActivity
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.BaseActivity
import com.pengke.paper.scanner.base.SPKEY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.view.PaperRectangle

import kotlinx.android.synthetic.main.activity_scan.*
import org.json.JSONArray
import org.opencv.android.OpenCVLoader

const val IMAGE_COUNT_RESULT = 1000

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private val REQUEST_CAMERA_PERMISSION = 0
    private val EXIT_TIME = 2000

    private lateinit var mPresenter: ScanPresenter

    private var latestBackPressTime: Long = 0
    private lateinit var sp: SharedPreferences

    private var count = 0


    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {
        mPresenter = ScanPresenter(this, this)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
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

        shut.setOnClickListener {
            val isBusy = sp.getBoolean("isBusy", false)
            if(!isBusy) {
                mPresenter.shut()
                count++
                shut.text = count.toString()
            }
        }

        complete.setOnClickListener {
            val isBusy = sp.getBoolean("isBusy", false)
            if (!isBusy) {
                mPresenter.complete()
                val intent = Intent(this, ImageListActivity::class.java)
                startActivityForResult(intent, IMAGE_COUNT_RESULT)
            }
        }

        latestBackPressTime = System.currentTimeMillis()
    }

    // 撮影済み画像枚数取得
    private fun getImageCount(): Int {
        val images: String? = sp.getString(SPKEY, null)
        return if (images == null) {
            0
        } else {
            JSONArray(images).length()
        }
    }

    // 初回カメラ起動時、画像一覧画面から戻ってきた場合にのみ呼ばれる
    override fun onStart() {
        println("onStart")
        super.onStart()
        count = getImageCount()
        shut.text = count.toString()
        mPresenter.initJsonArray()
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