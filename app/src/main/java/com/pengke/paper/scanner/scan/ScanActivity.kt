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
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Display
import android.view.SurfaceView
import android.widget.SeekBar
import com.google.gson.Gson
import com.pengke.paper.scanner.ImageListActivity
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.*
import com.pengke.paper.scanner.crop.BeforehandCropPresenter
import com.pengke.paper.scanner.jsonToImageArray
import com.pengke.paper.scanner.model.Image
import com.pengke.paper.scanner.processor.processPicture
import com.pengke.paper.scanner.view.PaperRectangle

import kotlinx.android.synthetic.main.activity_scan.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.concurrent.thread

const val IMAGE_COUNT_RESULT = 1000
const val REQUEST_GALLERY_TAKE = 1

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private var latestBackPressTime: Long = 0
    private val REQUEST_CAMERA_PERMISSION = 0
    private val EXIT_TIME = 2000
    private lateinit var mPresenter: ScanPresenter
    private lateinit var sp: SharedPreferences

    private var count = 0
    private val gson = Gson()

    override fun provideContentViewId(): Int = R.layout.activity_scan

    private var needFlash = false

    override fun initPresenter() {
        mPresenter = ScanPresenter(this, this, this)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
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
                        val mat = Mat(Size(bitmap.width.toDouble(), bitmap.height.toDouble()), CvType.CV_8U)

                        // リサイズ
                        val matrix = Matrix()
                        matrix.postScale(0.5f, 0.5f)
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
                        val baos = ByteArrayOutputStream()
                        rotatedBm.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val b = baos.toByteArray()
                        val b64 = Base64.encodeToString(b, Base64.DEFAULT)
                        val thumbB64 = getThumbB64(rotatedBm)

                        val uuid = UUID.randomUUID().toString()
                        val image = Image(id = uuid, b64 = b64, originalB64 = b64, thumbB64 = thumbB64)
                        mat.release()

                        // 矩形が取得できるか確認し、取得できた場合はimageを更新する
                        val updatedMat = Mat(Size(rotatedBm.width.toDouble(), rotatedBm.height.toDouble()), CvType.CV_8U)
                        updatedMat.put(0, 0, b)
                        val editMat = Imgcodecs.imdecode(updatedMat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
                        val corners = processPicture(editMat)
                        if (corners != null) {
                            val beforeCropPresenter = BeforehandCropPresenter(this, corners, editMat)
                            beforeCropPresenter.cropAndSave(image = image)
                        } else {
                            saveImage(image)
                        }
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
                    val mat = Mat(Size(bitmap.width.toDouble(), bitmap.height.toDouble()), CvType.CV_8U)

                    // リサイズ
                    val matrix = Matrix()
                    matrix.postScale(0.4f, 0.4f)
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
                    val baos = ByteArrayOutputStream()
                    rotatedBm.compress(Bitmap.CompressFormat.JPEG, 90, baos)

                    val b = baos.toByteArray()
                    val b64 = Base64.encodeToString(b, Base64.DEFAULT)
                    val thumbB64 = getThumbB64(rotatedBm)

                    val uuid = UUID.randomUUID().toString()
                    val image = Image(id = uuid, b64 = b64, originalB64 = b64, thumbB64 = thumbB64)
                    mat.release()

                    val updatedMat = Mat(Size(rotatedBm.width.toDouble(), rotatedBm.height.toDouble()), CvType.CV_8U)
                    updatedMat.put(0, 0, b)
                    val editMat = Imgcodecs.imdecode(updatedMat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
                    val corners = processPicture(editMat)

                    // 矩形が取得できるか確認し、取得できた場合はimageを更新する
                    if (corners != null) {
                        val beforeCropPresenter = BeforehandCropPresenter(this, corners, editMat)
                        beforeCropPresenter.cropAndSave(image = image)
                        editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
                    } else {
                        saveImage(image)
                        editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
                    }
                }
            }
        }
    }

    private fun getThumbB64(rotatedBm: Bitmap): String {
        // ※単体表示用(0.4倍)のさらに半分→オリジナルの0.2倍
        val thumbBm = Bitmap.createScaledBitmap(rotatedBm, rotatedBm.width/2, rotatedBm.height/2, false)
        val thumbBaos = ByteArrayOutputStream()
        thumbBm.compress(Bitmap.CompressFormat.JPEG, 100, thumbBaos)
        val thumbB = thumbBaos.toByteArray()
        return Base64.encodeToString(thumbB, Base64.DEFAULT)
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

    fun saveImage(image: Image) {
        var images = mutableListOf<Image>()
        val json = sp.getString(IMAGE_ARRAY, null)
        if (json != null) {
            images = jsonToImageArray(json)
        }
        images.add(image)
        val editor = sp.edit()
        editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
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
        if (PHOTO_MAX_COUNT <= count) {
            shut.isEnabled = false
            shut.background = resources.getDrawable(R.drawable.reached_max_count_picture_button, null)
            maxCountDesc.text = resources.getString(R.string.reached_max_count)
        } else {
            shut.background = resources.getDrawable(R.drawable.picture_button, null)
            maxCountDesc.text = resources.getString(R.string.max_count_desc)
        }
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