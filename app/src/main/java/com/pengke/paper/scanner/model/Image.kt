package com.pengke.paper.scanner.model

import android.graphics.Point
import android.graphics.Rect
import com.pengke.paper.scanner.processor.Corners
import java.io.Serializable

data class Image(
    val id: String,
    val b64: String,
    val originalB64: String,
    val thumbB64: String
): Serializable
