package com.xiao.nicevieoplayer.example.base;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;

/**
 * 在此Activity种，如果视频正在播放或缓冲，按下Home键，暂停视频播放，回到此Activity后继续播放视频；
 * 如果离开次Activity（跳转到其他Activity或按下Back键），则释放视频播放器
 */
public class CompatHomeKeyActivity extends AppCompatActivity {

    private boolean pressedHome;
    private HomeKeyWatcher mHomeKeyWatcher;
    private boolean isPlaying;   //用于切界面时记录该视频是否是播放状态，如果是播放状态切回来继续播放

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHomeKeyWatcher = new HomeKeyWatcher(this);
        mHomeKeyWatcher.setOnHomePressedListener(new HomeKeyWatcher.OnHomePressedListener() {
            @Override
            public void onHomePressed() {
                pressedHome = true;
            }
        });
//        pressedHome = false;
//        mHomeKeyWatcher.startWatch();
    }

    @Override
    protected void onStop() {
        super.onStop();
        isPlaying = NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isPlaying()
                || NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isBufferingPlaying();
        NiceVideoPlayerManager.instance().suspendNiceVideoPlayer();
        // 在OnStop中是release还是suspend播放器，需要看是不是因为按了Home键
//        if (pressedHome) {
//            NiceVideoPlayerManager.instance().suspendNiceVideoPlayer();
//        } else {
//            NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
//        }
//        mHomeKeyWatcher.stopWatch();
    }

    @Override
    protected void onRestart() {
//        mHomeKeyWatcher.startWatch();
//        pressedHome = false;
        super.onRestart();
        if (isPlaying) {
            NiceVideoPlayerManager.instance().resumeNiceVideoPlayer();
        }
    }

    @Override
    public void onBackPressed() {
        if (NiceVideoPlayerManager.instance().onBackPressd()) {
            return;
        }
        super.onBackPressed();
    }

}
