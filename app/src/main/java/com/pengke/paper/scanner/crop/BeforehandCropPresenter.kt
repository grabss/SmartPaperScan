package com.pengke.paper.scanner.crop

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils

import org.opencv.core.Mat
import android.util.Base64
import com.google.gson.Gson
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.jsonToImageArray
import com.pengke.paper.scanner.model.Image
import com.pengke.paper.scanner.processor.*
import com.pengke.paper.scanner.scan.ScanPresenter
import org.opencv.core.Point
import java.io.ByteArrayOutputStream

class BeforehandCropPresenter(val context: Context, private val corners: Corners, private val mat: Mat) {
    private var picture: Mat
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null

    init {
        println("init BeforehandCropPresenter")
        picture = mat
    }

    fun cropAndSave(scanPre: ScanPresenter? = null) {

        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        Observable.create<Mat> {
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
                val thumbBm = Bitmap.createScaledBitmap(croppedBitmap!!, croppedBitmap!!.width/2, croppedBitmap!!.height/2, false)
                scanPre?.saveImageToDB(croppedBitmap!!, thumbBm)
//                val b = baos.toByteArray()
//                val croppedB64 = Base64.encodeToString(b, Base64.DEFAULT)

                // サムネイル生成
                // ※単体表示用(0.5倍のさらに半分→オリジナルの0.25倍)
//                val croppedThumbB64 = getThumbB64(croppedBitmap!!)
//
//                val croppedImg = image.copy(b64 = croppedB64, thumbB64 = croppedThumbB64)
//                saveImage(croppedImg)
//                scanPre?.addImageToList(croppedImg)
            }
    }

}