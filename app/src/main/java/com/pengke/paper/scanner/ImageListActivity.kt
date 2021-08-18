package com.pengke.paper.scanner

import android.content.Context
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.activity_image_list.*
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.android.synthetic.main.fragment_image_list.*

private const val NUM_PAGES = 5

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
        override fun getItemCount(): Int = NUM_PAGES

        override fun createFragment(position: Int): Fragment = ImageListFragment()
    }
}