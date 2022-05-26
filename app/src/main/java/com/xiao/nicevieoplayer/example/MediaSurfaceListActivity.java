package com.xiao.nicevieoplayer.example;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevideoplayer.player.MediaVideoView;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.MediaAdapter;
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyActivity;
import com.xiao.nicevieoplayer.example.util.DataUtil;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;

public class MediaSurfaceListActivity extends CompatHomeKeyActivity {

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
        MediaAdapter adapter = new MediaAdapter(this, DataUtil.getVideoListData(), false);
        mRecyclerView.setAdapter(adapter);

        mRecyclerView.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
                    @Override
                    public void onChildViewAttachedToWindow(View view) {

                    }

                    @Override
                    public void onChildViewDetachedFromWindow(View view) {
                        MediaVideoView niceVideoPlayer =
                                view.findViewById(R.id.nice_video_player);
                        if (niceVideoPlayer == NiceVideoPlayerManager.instance()
                                                                     .getCurrentNiceVideoPlayer()) {
                            NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
                        }
                    }
                });
    }
}