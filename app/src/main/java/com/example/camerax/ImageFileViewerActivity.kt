package com.example.camerax

import android.net.Uri
import android.os.Bundle
import android.view.View
import com.bumptech.glide.Glide
import com.example.camerax.base.BaseActivity
import com.example.camerax.constants.AppConstant
import kotlinx.android.synthetic.main.activity_image_file_viewer.*
import java.io.File

class ImageFileViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_file_viewer)

        val imageFile = File((intent.getParcelableExtra(AppConstant.URI) as Uri).path!!.replace("/document/raw:", ""))
        if (imageFile.name.endsWith(".gif")) {
            ivNormalImage.visibility = View.VISIBLE
            Glide.with(this).asGif().load(imageFile).into(ivNormalImage)
        } else {
            ivZoomableImage.visibility = View.VISIBLE
            Glide.with(this).asBitmap().load(imageFile).into(ivZoomableImage)
        }
    }
}
