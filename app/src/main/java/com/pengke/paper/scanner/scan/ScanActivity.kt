package com.pengke.paper.scanner.scan

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Display
import android.view.SurfaceView
import android.widget.SeekBar
import com.pengke.paper.scanner.PermissionAlertDialogFragment
import com.pengke.paper.scanner.ImageListActivity
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.*
import com.pengke.paper.scanner.helper.DbHelper
import com.pengke.paper.scanner.helper.ImageTable
import com.pengke.paper.scanner.view.PaperRectangle

import kotlinx.android.synthetic.main.activity_scan.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.concurrent.thread

const val IMAGE_COUNT_RESULT = 1000
const val REQUEST_GALLERY_TAKE = 1
const val REQUEST_CAMERA_PERMISSION = 0
const val REQUEST_READ_GALLERY_PERMISSION = 1

class ScanActivity : BaseActivity(), IScanView.Proxy, PermissionAlertDialogFragment.BtnListener {

    private var latestBackPressTime: Long = 0
    private val EXIT_TIME = 2000
    private lateinit var mPresenter: ScanPresenter
    private lateinit var sp: SharedPreferences

    private var count = 0

    override fun provideContentViewId(): Int = R.layout.activity_scan

    private var needFlash = false

    private val dbHelper = DbHelper(this)

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    override fun initPresenter() {
        mPresenter = ScanPresenter(this, this, this)

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
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_READ_GALLERY_PERMISSION)
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
            val db = dbHelper.writableDatabase
            db.delete(ImageTable.TABLE_NAME, null, null)
            val editor = sp.edit()
            editor.putBoolean(CAN_EDIT_IMAGES, false).apply()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
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

    override fun onDecisionClick() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val packageName = packageName ?: ""
        val uri = Uri.fromParts(
            "package",
            packageName,
            null
        )
        intent.data = uri
        startActivity(intent)
        finish()
    }

    override fun onCancelClick() {
        finish()
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
            if (PHOTO_MAX_COUNT <= count) {
                shut.isEnabled = false
                shut.background = resources.getDrawable(R.drawable.reached_max_count_picture_button, null)
                maxCountDesc.text = resources.getString(R.string.reached_max_count)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_GALLERY_TAKE && resultCode == RESULT_OK) {
            val editor = sp.edit()
            editor.clear().apply()
            if (data?.clipData != null) {
                println("複数選択")

                val intent = Intent(this, ImageListActivity::class.java)
                startActivity(intent)

                thread {
                    // 複数画像選択時
                    val count = data.clipData!!.itemCount
                    println("count: $count")
                    for (i in 0 until count) {
                        val imageUri = data.clipData!!.getItemAt(i).uri
                        val byte = this.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        var bitmap = BitmapFactory.decodeByteArray(byte, 0, byte!!.size)
                        var w = bitmap.width
                        println("w: $w")
                        var h = bitmap.height
                        println("h: $h")
                        var aspect = 1.0
                        if (w < h) {
                            aspect = h / w.toDouble()
                        } else if (h < w) {
                            aspect = w / h.toDouble()
                        }
                        println("aspect: $aspect")

                        // 正方形
                        if (w == h && SCALE_SIZE < w) {
                           w = SCALE_SIZE
                           h = SCALE_SIZE
                        } else if ((w < h) && (SCALE_SIZE < w)) {
                            w = SCALE_SIZE
                            h = (SCALE_SIZE * aspect).toInt()
                        } else if ((h < w) && (SCALE_SIZE < h)) {
                            h = SCALE_SIZE
                            w = (SCALE_SIZE * aspect).toInt()
                        }
                        println("w2: $w")
                        println("h2: $h")

                        val mat = Mat(Size(w.toDouble(), h.toDouble()), CvType.CV_8U)

                        bitmap = Bitmap.createScaledBitmap(bitmap, w, h,true)
                        println("bitmap width: ${bitmap.width}")
                        println("bitmap height: ${bitmap.height}")
                        grayScale(mat, bitmap)
                        val path = getPathFromUri(this, imageUri)
                        val exif = ExifInterface(path!!)
                        val rotatedBm = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90F)
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180F)
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270F)
                            ExifInterface.ORIENTATION_NORMAL -> bitmap
                            else -> bitmap
                        }
                        mat.release()
                        val thumbBm = Bitmap.createScaledBitmap(rotatedBm, rotatedBm.width/3, rotatedBm.height/3, false)
                        mPresenter.saveImageToDB(originalBm = rotatedBm, thumbBm = thumbBm, croppedBm = rotatedBm)
                    }
                    editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
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
                    var w = bitmap.width
                    println("w: $w")
                    var h = bitmap.height
                    println("h: $h")
                    var aspect = 1.0
                    if (w < h) {
                        aspect = h / w.toDouble()
                    } else if (h < w) {
                        aspect = w / h.toDouble()
                    }
                    println("aspect: $aspect")

                    // 正方形
                    if (w == h && SCALE_SIZE < w) {
                        w = SCALE_SIZE
                        h = SCALE_SIZE
                    } else if ((w < h) && (SCALE_SIZE < w)) {
                        w = SCALE_SIZE
                        h = (SCALE_SIZE * aspect).toInt()
                    } else if ((h < w) && (SCALE_SIZE < h)) {
                        h = SCALE_SIZE
                        w = (SCALE_SIZE * aspect).toInt()
                    }
                    println("w2: $w")
                    println("h2: $h")

                    val mat = Mat(Size(w.toDouble(), h.toDouble()), CvType.CV_8U)

                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h,true)
                    println("bitmap width: ${bitmap.width}")
                    println("bitmap height: ${bitmap.height}")
                    grayScale(mat, bitmap)
                    val path = getPathFromUri(this, imageUri)
                    val exif = ExifInterface(path!!)
                    val rotatedBm = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90F)
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180F)
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270F)
                        ExifInterface.ORIENTATION_NORMAL -> bitmap
                        else -> bitmap
                    }
                    mat.release()
                    val thumbBm = Bitmap.createScaledBitmap(rotatedBm, rotatedBm.width/3, rotatedBm.height/3, false)
                    mPresenter.saveImageToDB(originalBm = rotatedBm, thumbBm = thumbBm, croppedBm = rotatedBm)
                    editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
                }
            }
        }
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        val isAfterKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        // DocumentProvider
        Log.e("getPathFromUri", "uri:" + uri.authority!!)
        if (isAfterKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if ("com.android.externalstorage.documents" == uri.authority) {// ExternalStorageProvider
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true))
                {
                    return (Environment.getExternalStorageDirectory().path + "/" + split[1])
                } else
                {
                    return  "/stroage/" + type + "/" + split[1]
                }
            } else if ("com.android.providers.downloads.documents" == uri.authority) {// DownloadsProvider
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if ("com.android.providers.media.documents" == uri.authority) {// MediaProvider
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var contentUri: Uri? = MediaStore.Files.getContentUri("external")
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme!!, ignoreCase = true)) {//MediaStore
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme!!, ignoreCase = true)) {// File
            return uri.path
        }
        return null
    }

    private fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val cindex = cursor.getColumnIndexOrThrow(projection[0])
                return cursor.getString(cindex)
            }
        } finally {
            if (cursor != null)
                cursor.close()
        }
        return null
    }

    private fun grayScale(mat: Mat, bm: Bitmap) {
        Utils.bitmapToMat(bm, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
        Utils.matToBitmap(mat, bm)
    }

    private fun rotate(bm: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
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

    // 撮影済み画像枚数取得
    private fun getImageCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(BaseColumns._ID),
            null,
            null,
            null,
            null,
            null,
        )
        return cursor.count
    }

    // 初回カメラ起動時、画像一覧画面から戻ってきた場合にのみ呼ばれる
    override fun onStart() {
        println("onStart")
        super.onStart()
        count = getImageCount()
        Handler(Looper.getMainLooper()).post {
            shut.text = count.toString()
        }
        needFlash = false
        flashBtn.setImageResource(R.drawable.ic_baseline_flash_off_24)
        toEnableBtns()
        adjustBtnsState()
        if (PHOTO_MAX_COUNT <= count) {
            shut.isEnabled = false
            shut.background = resources.getDrawable(R.drawable.reached_max_count_picture_button, null)
            maxCountDesc.text = resources.getString(R.string.reached_max_count)
        } else {
            shut.background = resources.getDrawable(R.drawable.picture_button, null)
            maxCountDesc.text = resources.getString(R.string.max_count_desc)
        }
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
        if (requestCode == REQUEST_CAMERA_PERMISSION && (grantResults[permissions.indexOf(android.Manifest.permission.CAMERA)] == PackageManager.PERMISSION_DENIED)) {
            val cameraPermissionDlg = PermissionAlertDialogFragment("カメラの使用が許可されていません。\n設定で許可してください。")
            cameraPermissionDlg.show(supportFragmentManager, "TAG")
        }
        if (requestCode == REQUEST_READ_GALLERY_PERMISSION && (grantResults[permissions.indexOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)] == PackageManager.PERMISSION_DENIED)) {
            val cameraPermissionDlg = PermissionAlertDialogFragment("写真へのアクセスが許可されていません。\n設定で許可してください。")
            cameraPermissionDlg.show(supportFragmentManager, "TAG")
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getDisplay(): Display = windowManager.defaultDisplay

    override fun getSurfaceView(): SurfaceView = surface

    override fun getPaperRect(): PaperRectangle = paper_rect

}