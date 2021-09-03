package com.pengke.paper.scanner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
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
    private lateinit var imageAdapter: ImageAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sort)
        index = intent.getIntExtra(INDEX, 0)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        setGridView()
        setHelper()
        grid.layoutManager = GridLayoutManager(this, 3, RecyclerView.VERTICAL, false)
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
        imageAdapter = ImageAdapter(bmList)
        grid.adapter = imageAdapter
    }

    private fun setHelper() {
        val helper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN
                , ItemTouchHelper.ANIMATION_TYPE_DRAG
            ) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    println("onMove")
                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition
                    imageAdapter.notifyItemMoved(fromPos, toPos)
                    var moto = bmList[fromPos]
                    bmList.removeAt(fromPos)
                    bmList.add(toPos, moto)

                    if (fromPos < toPos) {
                        println("fromPos")
                        imageAdapter.notifyItemRangeChanged(fromPos, toPos - fromPos + 1)
                    } else {
                        println("toPos")
                        imageAdapter.notifyItemRangeChanged(toPos, fromPos - toPos + 1)
                    }
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    TODO("Not yet implemented")
                }
            }
        )
        helper.attachToRecyclerView(grid)
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

    private inner class ImageAdapter(bmList: List<Bitmap>): RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
        var bmList = bmList

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.gridImg)
            val textView: TextView = view.findViewById(R.id.index)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val textView = holder.textView
            textView.text = (position + 1).toString()

            val imageView = holder.imageView
            imageView.setImageBitmap(bmList[position])

            imageView.setOnClickListener {
                zoomImageFromThumb(imageView, position)
                shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
                true
            }
        }

        override fun getItemCount() = bmList.size
    }
}
