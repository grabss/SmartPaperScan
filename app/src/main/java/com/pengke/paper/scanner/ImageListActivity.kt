package com.pengke.paper.scanner

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import kotlinx.android.synthetic.main.activity_image_list.*

class ImageListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)
        setImages()
    }

    private fun setImages() {
        val sp = getSharedPreferences("images", Context.MODE_PRIVATE)
        val images = sp.getStringSet("imageArray", null)
        println("images size: ${images?.size}")
        if (images == null) {
            return
        }
        val imageBytes = Base64.decode(images.toList()[0], Base64.DEFAULT)
        val decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        imageView.setImageBitmap(decodedImg)
    }
}