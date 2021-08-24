package com.pengke.paper.scanner

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_image_list.*
import org.json.JSONArray

class ImageListActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        viewPager = findViewById(R.id.pager)

        val imageSize = getImageSize()

        val pagerAdapter = ImageListPagerAdapter(this, imageSize)
        viewPager.adapter = pagerAdapter

        setListener()

        TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
    }

    private fun setListener() {
        trash_btn.setOnClickListener { println("tapped trash_btn") }
        rect_btn.setOnClickListener { println("tapped rotate_btn") }
        rotate_btn.setOnClickListener { println("tapped rotate_btn") }
        contrast_btn.setOnClickListener { println("tapped contrast_btn") }
        sort_btn.setOnClickListener { println("tapped sort_btn") }
        upload_btn.setOnClickListener { println("tapped upload_btn") }
    }

    private fun getImageSize() : Int {
        val sp = getSharedPreferences("images", Context.MODE_PRIVATE)
        val images: String? = sp.getString("imageArray", null)
        return if (images != null) {
            val a = JSONArray(images)
            a.length()
        } else {
            0
        }
    }


    private inner class ImageListPagerAdapter(fa: FragmentActivity, imageSize: Int) : FragmentStateAdapter(fa) {
        val sp = getSharedPreferences("images", Context.MODE_PRIVATE)!!
        val images: String? = sp.getString("imageArray", null)
        val imageSize = imageSize

        // 要素数
        override fun getItemCount(): Int = imageSize

        // base64形式の画像を引数で渡す
        override fun createFragment(position: Int): Fragment {
            return ImageListFragment.newInstance(JSONArray(images).optString(position))
        }

    }
}