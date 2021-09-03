package com.pengke.paper.scanner

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.pengke.paper.scanner.base.SPKEY
import com.pengke.paper.scanner.base.SPNAME
import kotlinx.android.synthetic.main.activity_image_list.*
import org.json.JSONArray

const val INDEX = "INDEX"

class ImageListActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences
    private lateinit var pagerAdapter: ImageListPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        val images: String? = sp.getString(SPKEY, null)
        var jsons = JSONArray(images)

        pagerAdapter = ImageListPagerAdapter(this, jsons)

        // 編集画面からインデックスを取得
        val index = intent.getIntExtra(INDEX, 0)

        viewPager = pager
        viewPager.adapter = pagerAdapter
        viewPager.post {
            viewPager.setCurrentItem(index, true)
        }

        setBtnListener()

        TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()

    }


    private fun setBtnListener() {
        trash_btn.setOnClickListener {
            showAlertDlg()
        }
        rect_btn.setOnClickListener { println("tapped rect_btn") }
        rotate_btn.setOnClickListener {
            navToRotateScrn()
        }
        contrast_btn.setOnClickListener {
            navToContrastScrn()
        }
        sort_btn.setOnClickListener {
            navToSortScrn()
        }
        upload_btn.setOnClickListener {
            upload()
        }
    }

    private fun showAlertDlg() {
        AlertDialog.Builder(this)
            .setTitle("削除してよろしいですか")
            .setPositiveButton("はい") { _, _ ->
                val images: String? = sp.getString(SPKEY, null)
                var jsons = JSONArray(images)
                val index = viewPager.currentItem
                jsons.remove(index)

                pagerAdapter.removeImage(jsons)
                viewPager.post {
                    viewPager.setCurrentItem(index, true)
                }
            }
            .setNegativeButton("キャンセル") { _, _ ->
            }
            .show()
    }

    // finish()で画像一覧画面をスタックから除外しないとエラー発生。
    // 画像をスタックに積んだままの遷移はNG。
    private fun navToRotateScrn() {
        val intent = Intent(this, RotateActivity::class.java)
        intent.putExtra(INDEX, viewPager.currentItem)
        startActivity(intent)
        finish()
    }

    private fun navToContrastScrn() {
        val intent = Intent(this, ContrastActivity::class.java)
        intent.putExtra(INDEX, viewPager.currentItem)
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
        val images: String? = sp.getString(SPKEY, null)
        val a = JSONArray(images)
        println("images length: ${a.length()}")
    }


    private inner class ImageListPagerAdapter(fa: FragmentActivity, jsons: JSONArray) : FragmentStateAdapter(fa) {
        val sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)!!
        var jsons = jsons

        private fun getPageIds(): Array<Long> {
            return Array(jsons.length()) { i -> jsons.optString(i).hashCode().toLong() }
        }

        // 要素数
        override fun getItemCount(): Int = jsons.length()

        // base64形式の画像を引数で渡す
        override fun createFragment(position: Int): Fragment {
            return ImageListFragment.newInstance(jsons.optString(position))
        }

        override fun getItemId(position: Int): Long {
            return jsons[position].hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            val pageIds = getPageIds()
            return pageIds.contains(itemId)
        }

        // 画像削除
        fun removeImage(newImages: JSONArray) {
            try {
                jsons = JSONArray()
                notifyDataSetChanged()
                jsons = newImages
                notifyDataSetChanged()

            } catch(e: Exception) {
                print(e)
            }
            val editor = sp.edit()
            editor.putString(SPKEY, jsons.toString()).apply()
        }
    }
}