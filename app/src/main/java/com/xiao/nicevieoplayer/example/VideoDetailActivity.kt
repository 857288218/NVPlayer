package com.xiao.nicevieoplayer.example

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.xiao.nicevideoplayer.player.IJKTextureVideoPlayer
import com.xiao.nicevieoplayer.R

class VideoDetailActivity : AppCompatActivity() {

    companion object {
        fun startActivity(activity: AppCompatActivity, url: String?, position: Long) {
            activity.startActivity(Intent(activity, VideoDetailActivity::class.java).apply {
                putExtra("url", url)
                putExtra("position", position)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detail)

        val videoPlayer = findViewById<IJKTextureVideoPlayer>(R.id.nice_video_player)
        videoPlayer.setUp(intent.getStringExtra("url")!!, null)
        videoPlayer.start(intent.getLongExtra("intent", 0))
    }
}