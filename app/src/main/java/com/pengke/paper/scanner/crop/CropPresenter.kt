package com.pengke.paper.scanner.crop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import android.util.Log
import android.view.View
import android.widget.Toast
import com.pengke.paper.scanner.SourceManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils

import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import android.provider.MediaStore
import android.content.ContentValues
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Base64
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.jsonToImageArray
import com.pengke.paper.scanner.model.Image
import com.pengke.paper.scanner.processor.*
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs


const val IMAGES_DIR = "smart_scanner"

class CropPresenter(val context: Context, private val iCropView: ICropView.Proxy, private val itemIndex: Int) {
//    private val picture: Mat? = SourceManager.pic
    private var picture: Mat
    private val corners: Corners? = SourceManager.corners
    private var croppedPicture: Mat? = null
    private var enhancedPicture: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private val index = itemIndex
    private var sp: SharedPreferences = context.getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
    private lateinit var images: ArrayList<Image>
    private lateinit var decodedImg: Bitmap
    private lateinit var imageBytes: ByteArray

    init {
        val bitmap = getImage()
        val mat = Mat(Size(bitmap.width.toDouble(), bitmap.height.toDouble()), CvType.CV_8U)
        mat.put(0, 0, imageBytes)
//        println("picture.width: ${picture?.width()}") // 3096
//        println("picture.height: ${picture?.height()}") // 5504
        picture = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
        SourceManager.corners = processPicture(picture)
        mat.release()

        iCropView.getPaperRect().onCorners2Crop(corners, picture?.size())
        Utils.matToBitmap(picture, bitmap, true)
        iCropView.getPaper().setImageBitmap(bitmap)
    }

    private fun getImage(): Bitmap {
        val json = sp.getString(IMAGE_ARRAY, null)
        images = jsonToImageArray(json!!)
        val b64Image = images[index].b64
        imageBytes = Base64.decode(b64Image, Base64.DEFAULT)
        decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return decodedImg
    }

    fun addImageToGallery(filePath: String, context: Context) {

        val values = ContentValues()

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        values.put(MediaStore.MediaColumns.DATA, filePath)

        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
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
                    iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
                    iCropView.getPaper().visibility = View.GONE
                    iCropView.getPaperRect().visibility = View.GONE
                }
    }

    fun enhance() {
        if (croppedBitmap == null) {
            Log.i(TAG, "picture null?")
            return
        }

        Observable.create<Bitmap> {
            it.onNext(enhancePicture(croppedBitmap))
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { pc ->
                    enhancedPicture = pc
                    iCropView.getCroppedPaper().setImageBitmap(pc)
                }
    }

    fun save() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "please grant write file permission and trya gain", Toast.LENGTH_SHORT).show()
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), IMAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            //first save enhanced picture, if picture is not enhanced, save cropped picture, otherwise nothing to do
            val pic = enhancedPicture
            if (null != pic) {
                val file = File(dir, "enhance_${SystemClock.currentThreadTimeMillis()}.jpeg")
                val outStream = FileOutputStream(file)
                pic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.flush()
                outStream.close()
                addImageToGallery(file.absolutePath, this.context)
                Toast.makeText(context, "picture saved, path: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                val cropPic = croppedBitmap
                if (null != cropPic) {
                    val file = File(dir, "crop_${SystemClock.currentThreadTimeMillis()}.jpeg")
                    val outStream = FileOutputStream(file)
                    cropPic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                    outStream.flush()
                    outStream.close()
                    addImageToGallery(file.absolutePath, this.context)
                    Toast.makeText(context, "picture saved, path: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}