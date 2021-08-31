package com.pengke.paper.scanner

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pengke.paper.scanner.base.SPKEY
import com.pengke.paper.scanner.base.SPNAME
import kotlinx.android.synthetic.main.activity_rotate.*
import kotlinx.android.synthetic.main.activity_rotate.cancelBtn
import kotlinx.android.synthetic.main.activity_rotate.decisionBtn
import kotlinx.android.synthetic.main.activity_sort.*
import kotlinx.android.synthetic.main.grid_item.view.*
import org.json.JSONArray
import java.util.*
import kotlin.concurrent.thread

class SortActivity : AppCompatActivity() {
    private lateinit var sp: SharedPreferences
    private var index = 0
    private val bmList = mutableListOf<Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sort)
        index = intent.getIntExtra(INDEX, 0)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        val images = sp.getString(SPKEY, null)
        val jsons = JSONArray(images)

        for(i in 0 until jsons.length()) {
            val imageBytes = Base64.decode(jsons[i] as String, Base64.DEFAULT)
            val decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            bmList.add(decodedImg)
        }
        val adapter = ImageAdapter(grid, bmList)
        grid.adapter = adapter
        setBtnListener()
    }

    private fun setBtnListener() {
        cancelBtn.setOnClickListener {
            disableBtns()
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            disableBtns()
            thread {
                navToImageListScrn()
            }
        }
    }

    private fun disableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(INDEX, index)
        startActivityForResult(intent, 100)
        finish()
    }

    private inner class ImageAdapter(grid: GridView, bmList: List<Bitmap>): BaseAdapter() {
        override fun getCount(): Int {
            return bmList.size
        }

        override fun getItem(position: Int): Any {
            return position
        }

        override fun getItemId(position: Int): Long {
            return bmList[position].hashCode().toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = layoutInflater.inflate(R.layout.grid_item, parent, false)
            view.gridImg.setImageBitmap(bmList[position])

            return view
        }

    }
}