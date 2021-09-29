package com.pengke.paper.scanner.model

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.pengke.paper.scanner.processor.Corners
import java.io.Serializable

data class Image(
    val id: String = "",
    val b64: String? = null,
    val originalB64: String? = null,
    val thumbB64: String? = null,
    val bm: Bitmap? = null,
    val thumbBm: Bitmap? = null,
    val orderIndex: Int = 0
): Serializable
