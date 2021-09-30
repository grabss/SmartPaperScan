package com.pengke.paper.scanner

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.CursorWindow
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.pengke.paper.scanner.base.CAN_EDIT_IMAGES
import com.pengke.paper.scanner.base.ID
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.crop.CropActivity
import com.pengke.paper.scanner.helper.DbHelper
import com.pengke.paper.scanner.helper.ImageTable
import com.pengke.paper.scanner.model.Image
import kotlinx.android.synthetic.main.activity_image_list.*

class ImageListActivity : FragmentActivity(), ConfirmDialogFragment.BtnListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences
    private lateinit var pagerAdapter: ImageListPagerAdapter
    private lateinit var images: ArrayList<Image>
    private val dialog = ConfirmDialogFragment()
    private var id = ""
    private val handler = Handler(Looper.getMainLooper())
    private val dbHelper = DbHelper(this)

    private val result = object: Runnable {
        override fun run() {
            val result = sp.getBoolean(CAN_EDIT_IMAGES, false)
            if (result) {
                images = getImagesFromDB()
                updateFirstAndSecondImage()
                pagerAdapter = ImageListPagerAdapter(images)

                // 編集画面からIDを取得
                id = intent.getStringExtra(ID).toString()

                val index = images.indexOfFirst {
                    it.id == id
                }

                viewPager = pager
                viewPager.offscreenPageLimit = 5
                viewPager.adapter = pagerAdapter
                viewPager.setCurrentItem(index, false)
                TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
                toEnableBtns()
                setViewPagerListener()
                return
            } else {
                handler.postDelayed(this, 200)
            }
        }
    }

    private fun setViewPagerListener() {
        viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                println("onPageScrolled position: $position")
                if (images.isEmpty()) {
                    return
                }

                // 表示中の画像をハイクオリティ画像に差し替え
//                val currentImage = getHighQualityImage(position)
//                images[position] = currentImage

                // 表示中の前後画像を差し替え
                if (images.size >= 1 && position >= 1) {
                    images[position - 1] = getHighQualityImage(position - 1)
                }
                if (images.size >= 1 && images.size - 2 >= position) {
                    images[position + 1] = getHighQualityImage(position + 1)
                }

                // 非表示の画像をサムネイルに戻す
                if (images.size >= 3 && position <= images.size - 3) {
                    images[position + 2] = getThumb(position + 2)
                }

                if (images.size >= 3 && position >= 2) {
                    images[position - 2] = getThumb(position - 2)
                }

                Handler(Looper.getMainLooper()).post {
                    pagerAdapter.updateData(images)
//                    viewPager.setCurrentItem(position, false)
                }

            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                println("onPageSelected position: $position")
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                println("onPageScrollStateChanged state: $state")
            }
        })
    }

    private fun getHighQualityImage(position: Int): Image{
        val db = dbHelper.readableDatabase
        val image = images[position]
        val selection = "${BaseColumns._ID} = ?"
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(ImageTable.COLUMN_NAME_BITMAP, ImageTable.COLUMN_NAME_ORDER_INDEX),
            selection,
            arrayOf(image.id),
            null,
            null,
            null,
        )
        cursor.moveToFirst()
        val blob = cursor.getBlob(0)
        val bm = BitmapFactory.decodeByteArray(blob, 0, blob.size)

        // 一覧表示用に正規画像を取得
        return image.copy(thumbBm = bm)
    }

    private fun getThumb(position: Int): Image{
        val db = dbHelper.readableDatabase
        val image = images[position]
        val selection = "${BaseColumns._ID} = ?"
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(ImageTable.COLUMN_NAME_THUMB_BITMAP, ImageTable.COLUMN_NAME_ORDER_INDEX),
            selection,
            arrayOf(image.id),
            null,
            null,
            null,
        )
        cursor.moveToFirst()
        val blob = cursor.getBlob(0)
        val thumb = BitmapFactory.decodeByteArray(blob, 0, blob.size)

        // 一覧表示用に正規画像を取得
        return image.copy(thumbBm = thumb)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        // CursorWindowの設定値増加(上限500MB)
        val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
        field.isAccessible = true
        field.set(null, 500 * 1024 * 1024)

        // ギャラリーから選択した画像の加工処理が終わっているかを200ミリ秒毎に確認
        handler.post(result)
        toDisableBtns()
        setBtnListener()
    }

    fun getImagesFromDB(): ArrayList<Image> {
        val db = dbHelper.readableDatabase
        val order = "${ImageTable.COLUMN_NAME_ORDER_INDEX} ASC"

        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(BaseColumns._ID, ImageTable.COLUMN_NAME_THUMB_BITMAP, ImageTable.COLUMN_NAME_ORDER_INDEX),
            null,
            null,
            null,
            null,
            order,
        )
        val imageList = ArrayList<Image>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID)).toString()
                val blob = getBlob(getColumnIndexOrThrow(ImageTable.COLUMN_NAME_THUMB_BITMAP))
                val thumbBm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                val image = Image(id = id, thumbBm = thumbBm)
                imageList.add(image)
            }
        }
        return imageList
    }

    private fun updateFirstAndSecondImage() {
        val firstImage = getHighQualityImage(0)
        images[0] = firstImage
        if (images.size > 1) {
            val secondImage = getHighQualityImage(1)
            images[1] = secondImage
        }
    }

    private fun setBtnListener() {
        trashBtn.setOnClickListener {
            dialog.show(supportFragmentManager, "TAG")
        }
        cropBtn.setOnClickListener {
            navToCropScrn()
        }
        rotateBtn.setOnClickListener {
            navToRotateScrn()
        }
        contrastBtn.setOnClickListener {
            navToContrastScrn()
        }
        sortBtn.setOnClickListener {
            navToSortScrn()
        }
        uploadBtn.setOnClickListener {
            upload()
        }
        toDisableBtns()
    }

    private fun toEnableBtns() {
        trashBtn.isEnabled = true
        cropBtn.isEnabled = true
        rotateBtn.isEnabled = true
        contrastBtn.isEnabled = true
        sortBtn.isEnabled = true
        uploadBtn.isEnabled = true
    }

    private fun toDisableBtns() {
        trashBtn.isEnabled = false
        cropBtn.isEnabled = false
        rotateBtn.isEnabled = false
        contrastBtn.isEnabled = false
        sortBtn.isEnabled = false
        uploadBtn.isEnabled = false
    }

    private fun navToCropScrn() {
        val intent = Intent(this, CropActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    // finish()で画像一覧画面をスタックから除外しないとエラー発生。
    // 画像をスタックに積んだままの遷移はNG。
    private fun navToRotateScrn() {
        val intent = Intent(this, RotateActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    private fun navToContrastScrn() {
        val intent = Intent(this, ContrastActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    private fun navToSortScrn() {
        val intent = Intent(this, SortActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    // アップロード実行。Flutterに2次元配列のbyte配列を渡す
    private fun upload() {
        println("=============")
        println("=============")
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            ImageTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        )
        println("column count: ${cursor.columnCount}")
        println("count: ${cursor.count}")
        println("=============")
        println("=============")
    }


    override fun onDecisionClick() {
        val index = viewPager.currentItem
        val image = images[index]
        deleteRowFromDB(image.id)
        images.removeAt(index)
        pagerAdapter.updateData(images)
        viewPager.setCurrentItem(index, false)
        if (images.isEmpty()) {
            toDisableBtns()
        }
    }

    private fun deleteRowFromDB(id: String) {
        val db = dbHelper.writableDatabase
        db.delete(ImageTable.TABLE_NAME, "${BaseColumns._ID} = ?", arrayOf(id))
    }

    // ダイアログのキャンセルボタンタップ時に処理を加える場合はここに記述
    override fun onCancelClick() {
    }


    private inner class ImageListPagerAdapter(images: ArrayList<Image>) : RecyclerView.Adapter<PagerViewHolder>() {
        var images = images

        // 要素数
        override fun getItemCount(): Int = images.size

        override fun getItemId(position: Int): Long {
            return images[position].hashCode().toLong()
        }

        // データ更新
        fun updateData(newImages: ArrayList<Image>) {
            try {
                images = ArrayList()
                notifyDataSetChanged()
                images = newImages
                notifyDataSetChanged()
            } catch(e: Exception) {
                print(e)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder =
            PagerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.fragment_image_list, parent, false))

        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            holder.bind(images[position])
        }
    }

    class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)

        fun bind(image: Image) {
            imageView.setImageBitmap(image.thumbBm)
        }
    }
}