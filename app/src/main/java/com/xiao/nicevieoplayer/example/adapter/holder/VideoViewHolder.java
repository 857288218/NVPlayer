package com.xiao.nicevieoplayer.example.adapter.holder;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.xiao.nicevideoplayer.player.IJKSurfaceVideoPlayer;
import com.xiao.nicevideoplayer.TxVideoPlayerController;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.bean.Video;

public class VideoViewHolder extends RecyclerView.ViewHolder {

    public TxVideoPlayerController mController;
    public IJKSurfaceVideoPlayer mVideoPlayer;

    public VideoViewHolder(View itemView) {
        super(itemView);
        mVideoPlayer = (IJKSurfaceVideoPlayer) itemView.findViewById(R.id.nice_video_player);
        // 将列表中的每个视频设置为默认16:9的比例
        ViewGroup.LayoutParams params = mVideoPlayer.getLayoutParams();
        params.width = itemView.getResources().getDisplayMetrics().widthPixels; // 宽度为屏幕宽度
//        params.height = (int) (params.width * 9f / 16f);    // 高度为宽度的9/16
        mVideoPlayer.setLayoutParams(params);
    }

    public void setController(TxVideoPlayerController controller) {
        mController = controller;
        mVideoPlayer.setController(mController);
    }

    public void bindData(Video video) {
        // 这里不用在onBindViewHolder中新建NiceVideoPlayerController进行设置(在onCreateViewHolder中设置就行)
        // 因为在item不可见时，Controller就reset了
        mController.setTitle(video.getTitle());
        mController.setLenght(video.getLength());
        Glide.with(itemView.getContext())
                .load(video.getImageUrl())
//                .placeholder(R.drawable.img_default)
                .into(mController.imageView());
        mVideoPlayer.setUp(video.getVideoUrl(), null);
    }
}
