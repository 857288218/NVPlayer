package com.xiao.nicevieoplayer.example;

import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevideoplayer.player.IJKVideoView;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.IJKAdapter;
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyFragment;
import com.xiao.nicevieoplayer.example.util.DataUtil;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 如果你需要在播放的时候按下Home键能暂停，回调此Fragment又继续的话，需要继承自CompatHomeKeyFragment
 */
public class DemoProcessHomeKeyFragenment extends CompatHomeKeyFragment {

    private RecyclerView mRecyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_fragment_demo, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
    }

    private void init() {
        mRecyclerView = getView().findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setHasFixedSize(true);
        IJKAdapter adapter = new IJKAdapter(getActivity(), DataUtil.getVideoListData(), true);
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
    }
}
