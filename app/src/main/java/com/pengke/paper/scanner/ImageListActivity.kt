package com.pengke.paper.scanner

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.AttributeSet
import android.util.Base64
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.pengke.paper.scanner.base.SPKEY
import com.pengke.paper.scanner.base.SPNAME
import kotlinx.android.synthetic.main.activity_image_list.*
import org.json.JSONArray

class ImageListActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        val imageCount = getImageCount()

        val pagerAdapter = ImageListPagerAdapter(this, imageCount)

        viewPager = findViewById(R.id.pager)
        viewPager.adapter = pagerAdapter

        setListener()

        TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
    }


    private fun getImageCount() : Int {
        val images: String? = sp.getString(SPKEY, null)
        return if (images != null) {
            val a = JSONArray(images)
            a.length()
        } else {
            0
        }
    }

    private fun setListener() {
        trash_btn.setOnClickListener {
            showAlertDlg()
            println("currentItem: ${viewPager.currentItem}")
        }
        rect_btn.setOnClickListener { println("tapped rect_btn") }
        rotate_btn.setOnClickListener { println("tapped rotate_btn") }
        contrast_btn.setOnClickListener { println("tapped contrast_btn") }
        sort_btn.setOnClickListener { println("tapped sort_btn") }
        upload_btn.setOnClickListener {
            upload()
        }
    }

    private fun showAlertDlg() {
        AlertDialog.Builder(this)
            .setTitle("削除してよろしいですか")
            .setPositiveButton("はい") { _, _ ->
                println("tapped yes btn")
            }
            .setNegativeButton("キャンセル") { _, _ ->
                println("tapped cancel btn")
            }
            .show()
    }

    // アップロード実行。Flutterに2次元配列のbyte配列を渡す
    private fun upload() {
        val images: String? = sp.getString(SPKEY, null)
        val a = JSONArray(images)
        println("images length: ${a.length()}")
    }






    private inner class ImageListPagerAdapter(fa: FragmentActivity, imageCount: Int) : FragmentStateAdapter(fa) {
        val sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)!!
        val images: String? = sp.getString(SPKEY, null)
        val imageCount = imageCount

        // 要素数
        override fun getItemCount(): Int = imageCount

        // base64形式の画像を引数で渡す
        override fun createFragment(position: Int): Fragment {
            return ImageListFragment.newInstance(JSONArray(images).optString(position))
        }

    }
}