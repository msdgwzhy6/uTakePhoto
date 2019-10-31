package com.sl.utakephoto

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.sl.utakephoto_lib.compress.CompressConfig
import com.sl.utakephoto_lib.crop.CropOptions
import com.sl.utakephoto_lib.exception.TakeException
import com.sl.utakephoto_lib.manager.ITakePhotoResult
import com.sl.utakephoto_lib.manager.UTakePhoto
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cropRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.outputBtn) {
                cropText.text = "*"
            } else {
                cropText.text = "/"
            }
        }

        capture.setOnClickListener {

            val takePhotoManager = UTakePhoto.with(this)
            if (take_photo_btn.isChecked) {
                takePhotoManager.openCamera("Pictures/bodivis")
            } else {
                takePhotoManager.openAlbum()
            }
            var crop: CropOptions.Builder? = null
            if (noCropBtn.isChecked) {
                crop = null
            } else if (system_crop_btn.isChecked) {
                crop = CropOptions.Builder()
                crop.setWithOwnCrop(false)
            } else if (own_crop_btn.isChecked) {
                crop = CropOptions.Builder()
                crop.setWithOwnCrop(true)
            }
            if (crop != null && (TextUtils.isEmpty(width.text) || TextUtils.isEmpty(height.text))) {
                Toast.makeText(this@MainActivity, "请输入宽高", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (outputBtn.isChecked) {
                crop?.setOutputX(width.text.toString().toInt())
                    ?.setOutputY(height.text.toString().toInt())
            } else {
                crop?.setAspectX(width.text.toString().toInt())
                    ?.setAspectX(height.text.toString().toInt())
            }
            if (crop != null) {
                takePhotoManager.setCrop(crop.create())
            } else {
                takePhotoManager.setCrop(null)
            }
            if (compress.isChecked) {
                takePhotoManager.setCompressConfig(
                    CompressConfig.Builder().setLeastCompressSize(50).setTargetUri(
                        Uri.fromFile(
                            File(
                                Environment.getExternalStorageDirectory(),
                                "/bodivis/10086.png"
                            )
                        )

                    ).create()
                )
            } else {
                takePhotoManager.setCompressConfig(null)
            }

            takePhotoManager.create(object : ITakePhotoResult {
                override fun takeSuccess(uriList: MutableList<Uri>?) {
                    photoIv.setImageURI(uriList?.get(0))

                }

                override fun takeFailure(ex: TakeException?) {
                    if (ex != null) {
                        Toast.makeText(this@MainActivity, ex.message, Toast.LENGTH_LONG)
                            .show()
                    }
                }


                override fun takeCancel() {
                }

            })
        }
    }
}