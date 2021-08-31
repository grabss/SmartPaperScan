package com.pengke.paper.scanner

import android.content.*
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.annotation.RequiresApi
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
        setGridView()
        setGridListener()
        setBtnListener()
    }

    private fun setGridView() {
        val images = sp.getString(SPKEY, null)
        val jsons = JSONArray(images)

        for(i in 0 until jsons.length()) {
            val imageBytes = Base64.decode(jsons[i] as String, Base64.DEFAULT)
            val decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            bmList.add(decodedImg)
        }
        val adapter = ImageAdapter(grid, bmList)
        grid.adapter = adapter
    }

    private fun setGridListener() {
        grid.setOnItemLongClickListener { parent, view, position, id ->
            println("aaaaaaaa")
            println(position)
            val item = ClipData.Item(view.tag as? CharSequence)
            val dragData = ClipData(
                view.tag as? CharSequence,
                arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                item)
            val myShadow = MyDragShadowBuilder(view)
            view.startDrag(
                dragData,
                myShadow,
                null,
                0
            )
            true
        }
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

    private inner class MyDragShadowBuilder(v: View) : View.DragShadowBuilder(v) {

        private val shadow = ColorDrawable(Color.LTGRAY)

        // Defines a callback that sends the drag shadow dimensions and touch point back to the
        // system.
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            // Sets the width of the shadow to half the width of the original View
            val width: Int = view.width / 2

            // Sets the height of the shadow to half the height of the original View
            val height: Int = view.height / 2

            // The drag shadow is a ColorDrawable. This sets its dimensions to be the same as the
            // Canvas that the system will provide. As a result, the drag shadow will fill the
            // Canvas.
            shadow.setBounds(0, 0, width, height)

            // Sets the size parameter's width and height values. These get back to the system
            // through the size parameter.
            size.set(width, height)

            // Sets the touch point's position to be in the middle of the drag shadow
            touch.set(width / 2, height / 2)
        }

        // Defines a callback that draws the drag shadow in a Canvas that the system constructs
        // from the dimensions passed in onProvideShadowMetrics().
        override fun onDrawShadow(canvas: Canvas) {
            // Draws the ColorDrawable in the Canvas passed in from the system.
            shadow.draw(canvas)
        }
    }
}