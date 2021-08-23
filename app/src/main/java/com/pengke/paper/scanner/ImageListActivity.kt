package com.pengke.paper.scanner

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class ImageListActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        viewPager = findViewById(R.id.pager)

        val pagerAdapter = ImageListPagerAdapter(this)
        viewPager.adapter = pagerAdapter
    }


    private inner class ImageListPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        val sp = getSharedPreferences("images", Context.MODE_PRIVATE)!!
        val images: MutableSet<String>? = sp.getStringSet("imageArray", null)
        val imagesLength = images?.size ?: 0

        // 要素数
        override fun getItemCount(): Int = imagesLength

        // base64形式の画像を引数で渡す
        override fun createFragment(position: Int): Fragment = ImageListFragment.newInstance(images!!.toList()[position])
    }
}