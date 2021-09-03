package com.pengke.paper.scanner

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pengke.paper.scanner.model.Image

fun jsonToImageArray(json: String): ArrayList<Image> {
    val gson = Gson()
    val type = object : TypeToken<ArrayList<Image>>() {}.type
    return gson.fromJson(json, type)
}