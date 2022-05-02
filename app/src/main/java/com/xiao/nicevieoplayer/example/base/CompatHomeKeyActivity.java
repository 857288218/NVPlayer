package com.xiao.nicevieoplayer.example.base;

import android.graphics.PixelFormat;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevieoplayer.example.util.HomeKeyWatcher;

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
        //在某些手机activity和fragment中加载SurfaceView，屏幕会闪一下(黑色)
        //解决办法：在activity的oncreate方法中加入getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
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
        isPlaying = NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer() != null
                && (NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isPlaying()
                || NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isBufferingPlaying());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
    }
}
