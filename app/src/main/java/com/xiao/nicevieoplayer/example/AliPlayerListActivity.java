package com.xiao.nicevieoplayer.example;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevideoplayer.utils.ScreenRotateUtils;
import com.xiao.nicevideoplayer.player.AliVideoPlayer;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.AliVideoAdapter;
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyActivity;
import com.xiao.nicevieoplayer.example.util.DataUtil;
//todo 第一次打开列表滑动卡顿
public class AliPlayerListActivity extends CompatHomeKeyActivity implements ScreenRotateUtils.OrientationChangeListener {
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycler_view);
        init();
    }

    private void init() {
        mRecyclerView = findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        AliVideoAdapter adapter = new AliVideoAdapter(this, DataUtil.getVideoListData());
        mRecyclerView.setAdapter(adapter);

        mRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {

            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                //屏幕中最后一个可见item在进入全屏后会执行该回调，导致释放播放器了
                //解决：设置一个一个标志变量进入全屏时禁止release，退出全屏可以release
                AliVideoPlayer niceVideoPlayer = view.findViewById(R.id.nice_video_player);
                if (niceVideoPlayer == NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer()) {
                    NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
                }
            }
        });
        ScreenRotateUtils.getInstance(getApplicationContext()).setOrientationChangeListener(this);
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
        if (NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer() != null
                && (NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isPaused() || NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isPlaying())
                && !NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isTinyWindow()) {
            if (orientation >= 45 && orientation <= 315 && NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isNormal()) {
                NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().enterFullScreen();
            } else if (((orientation >= 0 && orientation < 45) || orientation > 315)
                    && NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().isFullScreen()) {
                NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer().exitFullScreen();
            }
        }
    }

}
