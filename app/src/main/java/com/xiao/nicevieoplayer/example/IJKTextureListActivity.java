package com.xiao.nicevieoplayer.example;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevideoplayer.player.IJKSurfaceVideoPlayer;
import com.xiao.nicevideoplayer.player.IJKTextureVideoPlayer;
import com.xiao.nicevideoplayer.player.INiceVideoPlayer;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.IJKTextureAdapter;
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyActivity;
import com.xiao.nicevieoplayer.example.util.DataUtil;

public class IJKTextureListActivity extends CompatHomeKeyActivity {
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
        IJKTextureAdapter adapter = new IJKTextureAdapter(this, DataUtil.getVideoListData());
        mRecyclerView.setAdapter(adapter);

        mRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {

            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                INiceVideoPlayer niceVideoPlayer = view.findViewById(R.id.nice_video_player);
                if (niceVideoPlayer == NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer()) {
                    NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
                }
            }
        });

        //滑动自动播放
//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
//                    AutoPlayUtils.onScrollPlayVideo(recyclerView, R.id.nice_video_player, mLayoutManager.findFirstVisibleItemPosition(), mLayoutManager.findLastVisibleItemPosition());
//                }
//            }
//
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                super.onScrolled(recyclerView, dx, dy);
//                if (dy != 0) {
//                    AutoPlayUtils.onScrollReleaseAllVideos(mLayoutManager.findFirstVisibleItemPosition(), mLayoutManager.findLastVisibleItemPosition(), 0.2f);
//                }
//            }
//        });
    }
}
