package com.xiao.nicevieoplayer.example.adapter.holder;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevideoplayer.player.AliVideoView;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.bean.Video;

import android.graphics.Color;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class AliVideoViewHolder extends RecyclerView.ViewHolder {
    public MyVideoViewController mController;
    public AliVideoView mVideoPlayer;

    public AliVideoViewHolder(View itemView) {
        super(itemView);
        mVideoPlayer = itemView.findViewById(R.id.nice_video_player);
    }

    public void setController(MyVideoViewController controller) {
        mController = controller;
        mVideoPlayer.setController(mController);
    }

    public void bindData(Video video) {
        // 这里不用在onBindViewHolder中新建NiceVideoPlayerController进行设置(在onCreateViewHolder中设置就行)
        // 因为在item不可见时，Controller就reset了
        mController.setTitle(video.getTitle());
        mController.setLength(video.getLength());

//        Glide.with(itemView.getContext())
//                .load(video.getImageUrl())
////                .placeholder(R.drawable.img_default)
//                .into(mController.imageView());
        //获取第一帧作为封面
        Glide.with(itemView.getContext())
             .setDefaultRequestOptions(
                     new RequestOptions()
                             .frame(0000000)
                             .fitCenter()
                                      )
             .load(video.getVideoUrl())
//                .placeholder(R.drawable.img_default)
             .into(mController.imageView());
        mVideoPlayer.setUp(video.getVideoUrl(), null);
//        mVideoPlayer.onlyPrepare();
        mVideoPlayer.setVideoBackgroundColor(Color.parseColor("#000000"));
    }
}
