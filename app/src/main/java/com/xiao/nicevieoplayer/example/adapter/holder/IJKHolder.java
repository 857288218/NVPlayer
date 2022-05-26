package com.xiao.nicevieoplayer.example.adapter.holder;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevideoplayer.player.IJKVideoView;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.bean.Video;

public class IJKHolder extends RecyclerView.ViewHolder {
    public MyVideoViewController mController;
    public IJKVideoView mVideoPlayer;
    private boolean isUseTexture;

    public IJKHolder(View itemView, boolean isUseTexture) {
        super(itemView);
        this.isUseTexture = isUseTexture;
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
//        Glide.with(itemView.getContext())
//                .load(video.getImageUrl())
//                .placeholder(R.drawable.img_default)
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
//        mVideoPlayer.onlyPrepare();
        mVideoPlayer.setUp(video.getVideoUrl(), null);
        mVideoPlayer.setVideoBackgroundColor(Color.BLACK);
        mVideoPlayer.isUseTextureView = isUseTexture;
    }
}
