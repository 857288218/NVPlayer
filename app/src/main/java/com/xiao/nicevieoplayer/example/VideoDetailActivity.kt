package com.xiao.nicevieoplayer.example

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xiao.nicevideoplayer.player.AliVideoPlayer
import com.xiao.nicevideoplayer.player.MediaVideoPlayer
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

    private val videoUrlTop =
        "https://mtenroll.oss-cn-hangzhou.aliyuncs.com/ueditor/video/20180131/6365302297303492635856363.mp4"
    private val videoUrlBottom =
        "https://mtenroll.oss-cn-hangzhou.aliyuncs.com/ueditor/video/20180131/6365302297303492635856363.mp4"

    private lateinit var videoPlayer: AliVideoPlayer
    private lateinit var videoPlayer2: MediaVideoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detail)

        videoPlayer = findViewById(R.id.nice_video_player)
        videoPlayer2 = findViewById(R.id.nice_video_player2)
        videoPlayer2.setUp(videoUrlBottom, null)
        videoPlayer.setUp(videoUrlTop, null)
        // 两个视频同时播放需要把NiceVideoManager中release注释
        videoPlayer2.start(6000)
        // videoPlayer.start(6000)
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPlayer2.release()
        videoPlayer.release()
    }
}