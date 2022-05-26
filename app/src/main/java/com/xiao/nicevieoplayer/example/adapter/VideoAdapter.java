package com.xiao.nicevieoplayer.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.holder.IJKSurfaceHolder;
import com.xiao.nicevieoplayer.example.bean.Video;

import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<IJKSurfaceHolder> {

    private Context mContext;
    private List<Video> mVideoList;

    public VideoAdapter(Context context, List<Video> videoList) {
        mContext = context;
        mVideoList = videoList;
    }

    @Override
    public IJKSurfaceHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_video, parent, false);
        IJKSurfaceHolder holder = new IJKSurfaceHolder(itemView);
        MyVideoViewController controller = new MyVideoViewController(mContext);
        holder.setController(controller);
//        holder.mVideoPlayer.setLooping(true);
        return holder;
    }

    @Override
    public void onBindViewHolder(IJKSurfaceHolder holder, int position) {
        Video video = mVideoList.get(position);
        holder.bindData(video);
    }

    @Override
    public int getItemCount() {
        return mVideoList.size();
    }
}
