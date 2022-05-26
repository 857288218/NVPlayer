package com.xiao.nicevieoplayer.example;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevideoplayer.player.IJKVideoView;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.IJKAdapter;
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyActivity;
import com.xiao.nicevieoplayer.example.util.DataUtil;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class IJKPlayerListActivity extends CompatHomeKeyActivity {

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private boolean isUseTexture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycler_view);
        isUseTexture = getIntent().getBooleanExtra("isUseTexture", false);
        init();
    }

    private void init() {
        mRecyclerView = findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        IJKAdapter adapter = new IJKAdapter(this, DataUtil.getVideoListData(), isUseTexture);
        mRecyclerView.setAdapter(adapter);

        mRecyclerView.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
                    @Override
                    public void onChildViewAttachedToWindow(View view) {

                    }

                    @Override
                    public void onChildViewDetachedFromWindow(View view) {
                        IJKVideoView niceVideoPlayer =
                                view.findViewById(R.id.nice_video_player);
                        if (niceVideoPlayer == NiceVideoPlayerManager.instance()
                                                                     .getCurrentNiceVideoPlayer()) {
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
