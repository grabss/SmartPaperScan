package com.pengke.paper.scanner.crop

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils

import org.opencv.core.Mat
import android.util.Base64
import com.pengke.paper.scanner.model.Image
import com.pengke.paper.scanner.processor.*
import com.pengke.paper.scanner.scan.ScanActivity
import org.opencv.core.Point
import java.io.ByteArrayOutputStream

class BeforehandCropPresenter(val context: Context, private val corners: Corners, private val mat: Mat) {
    private var picture: Mat
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null

    init {
        println("BeforehandCropPresenter")
        picture = mat
    }

    fun cropAndSave(image: Image, scanActv: ScanActivity) {

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
                val b = baos.toByteArray()
                val updatedB64 = Base64.encodeToString(b, Base64.DEFAULT)
                val croppedImg = image.copy(b64 = updatedB64)
                scanActv.saveImage(croppedImg)
            }
    }
}