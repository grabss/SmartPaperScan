package com.pengke.paper.scanner

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_rotate.*

class RotateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rotate)

        setBtnListener()
    }

    private fun setBtnListener() {
        cancelBtn.setOnClickListener {
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            navToImageListScrn()
        }
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        startActivity(intent)
        finish()
    }
}