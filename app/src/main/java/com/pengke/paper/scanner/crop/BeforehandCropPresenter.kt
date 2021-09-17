package com.pengke.paper.scanner.crop

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils

import org.opencv.core.Mat
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.DisplayMetrics
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.gson.Gson
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.jsonToImageArray
import com.pengke.paper.scanner.model.Image
import com.pengke.paper.scanner.processor.*
import com.pengke.paper.scanner.scan.ScanActivity
import kotlinx.android.synthetic.main.activity_crop.*
import org.opencv.core.CvType
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import java.io.ByteArrayOutputStream

class BeforehandCropPresenter(val context: Context, private val bitmap: Bitmap, private var image: Image, private val corners: Corners, private val mat: Mat) {
    private var picture: Mat
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null
    private lateinit var decodedImg: Bitmap
    private lateinit var imageBytes: ByteArray

    init {
        println("BeforehandCropPresenter")
        picture = mat
//        val mat = Mat(Size(bitmap.width.toDouble(), bitmap.height.toDouble()), CvType.CV_8U)
//        mat.put(0, 0, imageBytes)
//        picture = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
//        println("picture size: ${picture.size()}")
//        println("picture width: ${picture.width()}")
//        println("picture height: ${picture.height()}")
//        val size = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
//        println("size.width: ${size.width}")
//        println("size.height: ${size.height}")
//        println("corners: $corners")
//        mat.release()

        // 画像が四角形、もしくは横長の場合にレイアウトのパラメーターを設定
        // 上記の場合横幅が100%になり、高さが画像サイズにより動的に変わる
//        if (picture.height() <= picture.width()) {
//            iCropView.getPaper().layoutParams.width = 0
//            iCropView.getPaper().layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
//        }
//        iCropView.getPaperRect().onCorners2Crop(corners, picture?.size(), picWidth, picHeight)
//        Utils.matToBitmap(picture, bitmap, true)
//        iCropView.getPaper().setImageBitmap(bitmap)
    }

    fun crop(image: Image, scanActv: ScanActivity) {

        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        Observable.create<Mat> {
//            it.onNext(cropPicture(picture, iCropView.getPaperRect().getCorners2Crop()))
            it.onNext(cropPicture(picture, corners.corners as List<Point>))
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { pc ->
                Log.i(TAG, "cropped picture: " + pc.toString())
                croppedPicture = pc
                croppedBitmap = Bitmap.createBitmap(pc.width(), pc.height(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(pc, croppedBitmap)
                val baos = ByteArrayOutputStream()
                croppedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                val b = baos.toByteArray()
                val updatedB64 = Base64.encodeToString(b, Base64.DEFAULT)
                val croppedImg = image.copy(b64 = updatedB64)
                scanActv.saveImage(croppedImg)
            }
    }
}