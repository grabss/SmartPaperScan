package com.pengke.paper.scanner

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.marginStart
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.pengke.paper.scanner.base.CAN_EDIT_IMAGES
import com.pengke.paper.scanner.base.IMAGE_ARRAY
import com.pengke.paper.scanner.base.SPNAME
import com.pengke.paper.scanner.crop.CropActivity
import com.pengke.paper.scanner.helper.DbHelper
import com.pengke.paper.scanner.helper.ImageTable
import com.pengke.paper.scanner.model.Image
import kotlinx.android.synthetic.main.activity_image_list.*
import org.json.JSONArray

const val INDEX = "INDEX"
const val ID = "ID"

class ImageListActivity : FragmentActivity(), ConfirmDialogFragment.BtnListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences
    private lateinit var pagerAdapter: ImageListPagerAdapter
    private lateinit var images: ArrayList<Image>
    private val dialog = ConfirmDialogFragment()
    private var id = ""
    private var index = 0
    private val handler = Handler(Looper.getMainLooper())
    private val dbHelper = DbHelper(this)

    private val result = object: Runnable {
        override fun run() {
            val result = sp.getBoolean(CAN_EDIT_IMAGES, false)
            if (result) {
                images = getImagesFromDB()
                pagerAdapter = ImageListPagerAdapter(images)

                // 編集画面からインデックスを取得
                index = intent.getIntExtra(INDEX, 0)

                // 編集画面からIDを取得
                id = intent.getStringExtra(ID).toString()

                index = images.indexOfFirst {
                    it.id == id
                }

                viewPager = pager
                viewPager.offscreenPageLimit = 5
                viewPager.adapter = pagerAdapter
                viewPager.setCurrentItem(index, false)
                TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
                toEnableBtns()
                return
            } else {
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

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
            null,
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
                val blob = getBlob(getColumnIndexOrThrow(ImageTable.COLUMN_NAME_BITMAP))
                val bm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                val image = Image(id = id, bm = bm)
                imageList.add(image)
            }
        }
        return imageList
    }

    override fun onDestroy() {
        super.onDestroy()
//        val db = dbHelper.writableDatabase
//        db.delete(ImageTable.TABLE_NAME, null, null)
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
        intent.putExtra(INDEX, viewPager.currentItem)
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
        intent.putExtra(INDEX, viewPager.currentItem)
        startActivity(intent)
        finish()
    }

    // アップロード実行。Flutterに2次元配列のbyte配列を渡す
    private fun upload() {
//        val images: String? = sp.getString(IMAGE_ARRAY, null)
//        val a = JSONArray(images)
//        println("images length: ${a.length()}")
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
//        with(cursor) {
//            while (moveToNext()) {
//                val hoge = getLong(getColumnIndexOrThrow(BaseColumns._ID))
//                println(hoge)
//                val fuga = getBlob(getColumnIndexOrThrow(ImageTable.COLUMN_NAME_BITMAP))
//                println(fuga)
//            }
//        }
//        println(cursor)
        println("=============")
        println("=============")
    }


    override fun onDecisionClick() {
        val image = images[index]
        deleteRowFromDB(image.id)
        val index = viewPager.currentItem
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
        val sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)!!
        var images = images
        private val gson = Gson()

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
            val editor = sp.edit()
            editor.putString(IMAGE_ARRAY, gson.toJson(newImages)).apply()
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
            imageView.setImageBitmap(image.bm)
        }
    }
}