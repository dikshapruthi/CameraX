package com.example.camerax.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun showToast(text: String?) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    fun showToast(resid: Int) {
        Toast.makeText(this, getString(resid), Toast.LENGTH_SHORT).show()
    }
}