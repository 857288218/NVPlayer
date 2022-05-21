package com.xiao.nicevieoplayer.example.adapter.holder;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevideoplayer.player.IJKTextureVideoView;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.bean.Video;

public class IJKTextureHolder extends RecyclerView.ViewHolder {
    public MyVideoViewController mController;
    public IJKTextureVideoView mVideoPlayer;

    public IJKTextureHolder(View itemView) {
        super(itemView);
        mVideoPlayer = itemView.findViewById(R.id.nice_video_player);
        // 将列表中的每个视频设置为默认16:9的比例
        ViewGroup.LayoutParams params = mVideoPlayer.getLayoutParams();
        params.width = itemView.getResources().getDisplayMetrics().widthPixels; // 宽度为屏幕宽度
//        params.height = (int) (params.width * 9f / 16f);    // 高度为宽度的9/16
        mVideoPlayer.setLayoutParams(params);
        mVideoPlayer.continueFromLastPosition(false);
    }

    public void setController(MyVideoViewController controller) {
        mController = controller;
        mVideoPlayer.setController(mController, true);
    }

    public void bindData(Video video) {
        // 这里不用在onBindViewHolder中新建NiceVideoPlayerController进行设置(在onCreateViewHolder中设置就行)
        // 因为在item不可见时，Controller就reset了
        mController.setTitle(video.getTitle());
        mController.setLength(video.getLength());
        Glide.with(itemView.getContext())
                .load(video.getImageUrl())
//                .placeholder(R.drawable.img_default)
                .into(mController.imageView());
        mVideoPlayer.prepare();
        mVideoPlayer.setUp(video.getVideoUrl(), null);
    }
}
