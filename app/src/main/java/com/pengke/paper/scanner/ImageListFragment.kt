package com.pengke.paper.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_image_list.*
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.widget.ImageView
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.model.Image

private const val ARG_PARAM1 = "params"

class ImageListFragment : Fragment() {
    private lateinit var imageView: ImageView
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_image_list, container, false)
        imageView = layout.findViewById(R.id.imageView)
        return layout
    }


}