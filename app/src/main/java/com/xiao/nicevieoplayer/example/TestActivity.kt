package com.xiao.nicevieoplayer.example

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.xiao.nicevieoplayer.R

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun tinyWindow(view: View?) {
        startActivity(Intent(this, TinyWindowPlayActivity::class.java))
    }

    fun videoList(view: View?) {
        startActivity(Intent(this, IJKSurfaceListActivity::class.java))
    }

    fun videoList2(view: View?) {
        startActivity(Intent(this, IJKTextureListActivity::class.java))
    }

    fun videoList3(view: View?) {
        startActivity(Intent(this, AliSurfaceListActivity::class.java))
    }

    fun videoList4(view: View?) {
        startActivity(Intent(this, AliTextureListActivity::class.java))
    }

    fun videoList5(view: View?) {
        startActivity(Intent(this, MediaSurfaceListActivity::class.java))
    }

    fun videoList6(view: View?) {
        startActivity(Intent(this, MediaTextureListActivity::class.java))
    }

    fun changeClarity(view: View?) {
        startActivity(Intent(this, ChangeClarityActivity::class.java))
    }

    fun processHomeKeyInFragment(view: View?) {
        // 在Fragment中使用NiceVideoPlayer，如果需要处理播放时按下Home键的逻辑.
        startActivity(Intent(this, ProcessHome2Activity::class.java))
    }
}