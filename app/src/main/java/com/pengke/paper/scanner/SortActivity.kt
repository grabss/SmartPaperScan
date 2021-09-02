package com.pengke.paper.scanner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.view.animation.DecelerateInterpolator
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
    private var currentAnimator: Animator? = null
    private var shortAnimationDuration: Int = 0

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

        grid.setOnItemClickListener { parent, view, position, id ->
            zoomImageFromThumb(view, position)
        }
        shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
    }

    private fun zoomImageFromThumb(thumbView: View, position: Int) {
        currentAnimator?.cancel()

        expandedImage.setImageBitmap(bmList[position])

        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

        thumbView.getGlobalVisibleRect(startBoundsInt)
        container.getGlobalVisibleRect(finalBoundsInt, globalOffset)
        startBoundsInt.offset(-globalOffset.x, -globalOffset.y)
        finalBoundsInt.offset(-globalOffset.x, -globalOffset.y)

        val startBounds = RectF(startBoundsInt)
        val finalBounds = RectF(finalBoundsInt)

        val startScale: Float
        if((finalBounds.width() / finalBounds.height() > startBounds.width() / startBounds.height())) {

            // Extend start bounds horizontally
            println("Extend start bounds horizontally")
            startScale = startBounds.height() / finalBounds.height()
            val startWidth: Float = startScale * finalBounds.height()
            val deltaWidth: Float = (startWidth - startBounds.width()) / 2
            startBounds.left -= deltaWidth.toInt()
            startBounds.right += deltaWidth.toInt()
        } else {

            // Extend start bounds vertically
            println("Extend start bounds vertically")
            startScale = startBounds.width() / finalBounds.width()
            val startHeight: Float = startScale * finalBounds.height()
            val deltaHeight: Float = (startHeight - startBounds.height()) / 2f
            startBounds.top -= deltaHeight.toInt()
            startBounds.bottom += deltaHeight.toInt()
        }

        thumbView.alpha = 0f
        expandedImage.visibility = View.VISIBLE

        expandedImage.pivotX = 0f
        expandedImage.pivotY = 0f

        currentAnimator = AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(
                expandedImage,
                View.X,
                startBounds.left,
                finalBounds.left)
            ).apply {
                with(ObjectAnimator.ofFloat(expandedImage, View.Y, startBounds.top, finalBounds.top))
                with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_X, startScale, 1f))
                with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_Y, startScale, 1f))

            }
            duration = shortAnimationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
//                    super.onAnimationEnd(animation)
                    currentAnimator = null
                }

                override fun onAnimationCancel(animation: Animator?) {
//                    super.onAnimationCancel(animation)
                    currentAnimator = null
                }
            })
            start()
        }

        expandedImage.setOnClickListener {
            currentAnimator?.cancel()

            // Animate the four positioning/sizing properties in parallel,
            // back to their original values.
            currentAnimator = AnimatorSet().apply {
                play(ObjectAnimator.ofFloat(expandedImage, View.X, startBounds.left)).apply {
                    with(ObjectAnimator.ofFloat(expandedImage, View.Y, startBounds.top))
                    with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_X, startScale))
                    with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_Y, startScale))
                }
                duration = shortAnimationDuration.toLong()
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator) {
                        thumbView.alpha = 1f
                        expandedImage.visibility = View.GONE
                        currentAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        thumbView.alpha = 1f
                        expandedImage.visibility = View.GONE
                        currentAnimator = null
                    }
                })
                start()
            }
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