package com.pengke.paper.scanner.model

import android.graphics.Point
import android.graphics.Rect
import java.io.Serializable

data class Image(
    val b64: String,
    val points: List<Point>? = null
): Serializable
