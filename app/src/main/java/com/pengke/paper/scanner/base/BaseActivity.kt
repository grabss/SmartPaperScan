package com.pengke.paper.scanner.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast

const val SPNAME = "images"
const val CAN_EDIT_IMAGES = "canEditImages"
const val PHOTO_MAX_COUNT = 50
const val SCALE_SIZE = 1280
const val ID = "ID"

abstract class BaseActivity : AppCompatActivity() {

    protected val TAG = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(provideContentViewId())
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initPresenter()
        prepare()
    }

    fun showMessage(id: Int) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show();
    }

    abstract fun provideContentViewId(): Int

    abstract fun initPresenter()

    abstract fun prepare()
}
