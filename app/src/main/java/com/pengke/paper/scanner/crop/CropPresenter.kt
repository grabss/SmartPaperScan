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


const val IMAGES_DIR = "smart_scanner"

class CropPresenter(val context: Context, private val iCropView: ICropView.Proxy, private val itemIndex: Int) {
    private var picture: Mat
    private var corners: Corners? = null
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null
    private val index = itemIndex
    private var sp: SharedPreferences = context.getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
    private lateinit var images: ArrayList<Image>
    private lateinit var decodedImg: Bitmap
    private lateinit var imageBytes: ByteArray
    private lateinit var image: Image
    private val gson = Gson()

    init {
        val bitmap = getOriginalImage()
        val mat = Mat(Size(bitmap.width.toDouble(), bitmap.height.toDouble()), CvType.CV_8U)
        mat.put(0, 0, imageBytes)
        picture = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
        println("picture size: ${picture.size()}")
        println("picture width: ${picture.width()}")
        println("picture height: ${picture.height()}")
        val size = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
        println("size.width: ${size.width}")
        println("size.height: ${size.height}")
        if (image.corners != null) {
            corners = processPicture(picture)
            println("corners: $corners")
        } else {
            corners = processPicture(picture)
            println("not exist corners: $corners")
        }
        mat.release()

        iCropView.getPaperRect().onCorners2Crop(corners, picture?.size())
        Utils.matToBitmap(picture, bitmap, true)
        iCropView.getPaper().setImageBitmap(bitmap)
    }

    private fun getOriginalImage(): Bitmap {
        val json = sp.getString(IMAGE_ARRAY, null)
        images = jsonToImageArray(json!!)
        image = images[index]
        val b64Image = images[index].originalB64
        imageBytes = Base64.decode(b64Image, Base64.DEFAULT)
        decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return decodedImg
    }

    fun crop() {
        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        Observable.create<Mat> {
            it.onNext(cropPicture(picture, iCropView.getPaperRect().getCorners2Crop()))
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
                    val editedImage = image.copy(b64 = updatedB64)
                    images[index] = editedImage
                    val editor = sp.edit()
                    editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
//                    iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
//                    iCropView.getPaper().visibility = View.GONE
//                    iCropView.getPaperRect().visibility = View.GONE
                }
    }
}