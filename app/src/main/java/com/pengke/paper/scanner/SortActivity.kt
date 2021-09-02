package com.pengke.paper.scanner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.*
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.opengl.Visibility
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isInvisible
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
            view.setOnDragListener(dragListen)
            val myShadow = View.DragShadowBuilder(view)
            view.startDrag(
                dragData,
                myShadow,
                view,
                0
            )
            view.alpha = 0f
            true
        }

        grid.setOnItemClickListener { parent, view, position, id ->
            zoomImageFromThumb(view, position)
        }
        shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
    }

    private val dragListen = View.OnDragListener { v, event ->
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                println("ACTION_DRAG_STARTED")
            }
            DragEvent.ACTION_DROP -> {
                println("ACTION_DROP")
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                println("ACTION_DRAG_ENTERED")
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                println("ACTION_DRAG_ENDED")
                v.alpha = 1f
                
                // ドラッグイベントの監視を解除
                v.setOnDragListener(null)
                println(v.hashCode())
            }
        }
        true
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
}