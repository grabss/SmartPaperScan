package com.pengke.paper.scanner.crop

import android.content.Intent
import android.widget.ImageView
import com.pengke.paper.scanner.ImageListActivity
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.BaseActivity
import com.pengke.paper.scanner.base.ID
import com.pengke.paper.scanner.view.PaperRectangle
import kotlinx.android.synthetic.main.activity_crop.*
import kotlinx.android.synthetic.main.activity_crop.cancelBtn
import kotlinx.android.synthetic.main.activity_crop.decisionBtn
import kotlinx.android.synthetic.main.activity_image_list.*
import kotlinx.android.synthetic.main.activity_rotate.*
import kotlin.concurrent.thread

class CropActivity : BaseActivity(), ICropView.Proxy {

    private lateinit var mPresenter: CropPresenter
    var id = ""

    override fun prepare() {
        setBtnListener()
    }

    override fun onBackPressed() {
        toDisableBtns()
        navToImageListScrn()
    }

    private fun setBtnListener() {
        cancelBtn.setOnClickListener {
            toDisableBtns()
            navToImageListScrn()
        }
        decisionBtn.setOnClickListener {
            toDisableBtns()
            thread {
                mPresenter.crop()
                navToImageListScrn()
            }
        }
    }

    private fun toDisableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(ID, id)
        startActivityForResult(intent, 100)
        finish()
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop

    override fun initPresenter() {
        id = intent.getStringExtra(ID).toString()
        mPresenter = CropPresenter(this, this, id, 1, 1)
        paper.viewTreeObserver.addOnGlobalLayoutListener {
            val width = paper.width
            val height = paper.height
            mPresenter = CropPresenter(this, this, id, width, height)
        }
    }

    override fun getPaper(): ImageView = paper

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun getCroppedPaper(): ImageView = picture_cropped
}