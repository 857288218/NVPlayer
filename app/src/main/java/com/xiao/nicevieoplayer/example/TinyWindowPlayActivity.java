package com.xiao.nicevieoplayer.example;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.xiao.nicevideoplayer.player.AliVideoView;
import com.xiao.nicevideoplayer.utils.ScreenRotateUtils;
import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyActivity;

public class TinyWindowPlayActivity extends CompatHomeKeyActivity implements ScreenRotateUtils.OrientationChangeListener {

    private AliVideoView mNiceVideoPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiny_window_play);
        init();
    }

    private void init() {
        mNiceVideoPlayer = findViewById(R.id.nice_video_player);
        String videoUrl = "http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4";
        mNiceVideoPlayer.setUp(videoUrl, null);
        mNiceVideoPlayer.setLooping(true);

        MyVideoViewController controller = new MyVideoViewController(this);
        controller.setTitle("办公室小野开番外了，居然在办公室开澡堂！老板还点赞？");
        controller.setLength(98000);
        Glide.with(this)
                .load("http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-30-43.jpg")
                .placeholder(R.drawable.img_default)
                .into(controller.imageView());
        mNiceVideoPlayer.setController(controller, true);
        ScreenRotateUtils.getInstance(getApplicationContext()).setOrientationChangeListener(this);
    }

    public void enterTinyWindow(View view) {
        if (mNiceVideoPlayer.isIdle()) {
            Toast.makeText(this, "要点击播放后才能进入小窗口", Toast.LENGTH_SHORT).show();
        } else {
            mNiceVideoPlayer.enterTinyWindow();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ScreenRotateUtils.getInstance(this).start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScreenRotateUtils.getInstance(this).stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ScreenRotateUtils.getInstance(this.getApplicationContext()).setOrientationChangeListener(null);
    }

    @Override
    public void orientationChange(int orientation) {
        if (mNiceVideoPlayer != null
                && (mNiceVideoPlayer.isPaused() || mNiceVideoPlayer.isPlaying())
                && !mNiceVideoPlayer.isTinyWindow()) {
            if (orientation >= 45 && orientation <= 315 && mNiceVideoPlayer.isNormal()) {
                mNiceVideoPlayer.enterFullScreen();
            } else if (((orientation >= 0 && orientation < 45) || orientation > 315)
                    && mNiceVideoPlayer.isFullScreen()) {
                mNiceVideoPlayer.exitFullScreen();
            }
        }
    }
}
