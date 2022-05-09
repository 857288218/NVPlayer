package com.xiao.nicevieoplayer.example

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.xiao.nicevideoplayer.player.AliVideoPlayer
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

    private val videoUrlTop =
        "http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-10_10-20-26.mp4"
    private val videoUrlBottom =
        "https://mtenroll.oss-cn-hangzhou.aliyuncs.com/ueditor/video/20180131/6365302297303492635856363.mp4"

    private lateinit var videoPlayer: IJKTextureVideoPlayer
    private lateinit var videoPlayer2: IJKTextureVideoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detail)

        videoPlayer = findViewById(R.id.nice_video_player)
        videoPlayer2 = findViewById(R.id.nice_video_player2)
        videoPlayer2.setUp(videoUrlBottom, null)
        videoPlayer.setUp(videoUrlTop, null)
        videoPlayer2.start()
        videoPlayer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPlayer2.release()
        videoPlayer.release()
    }
}