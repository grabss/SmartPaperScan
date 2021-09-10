package com.pengke.paper.scanner.model

import android.graphics.Point
import android.graphics.Rect
import com.pengke.paper.scanner.processor.Corners
import java.io.Serializable

data class Image(
    val b64: String,
    val originalB64: String,
    val corners: Corners? = null,
): Serializable
