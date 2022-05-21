package com.xiao.nicevieoplayer.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.holder.AliVideoViewHolder;
import com.xiao.nicevieoplayer.example.bean.Video;

import java.util.List;

public class AliVideoAdapter extends RecyclerView.Adapter<AliVideoViewHolder>{
    private Context mContext;
    private List<Video> mVideoList;

    public AliVideoAdapter(Context context, List<Video> videoList) {
        mContext = context;
        mVideoList = videoList;
    }

    @Override
    public AliVideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_alivideo, parent, false);
        AliVideoViewHolder holder = new AliVideoViewHolder(itemView);
        MyVideoViewController controller = new MyVideoViewController(mContext);
        holder.setController(controller);
//        holder.mVideoPlayer.setLooping(true);
        return holder;
    }

    @Override
    public void onBindViewHolder(AliVideoViewHolder holder, int position) {
        Video video = mVideoList.get(position);
        holder.bindData(video);
    }

    @Override
    public int getItemCount() {
        return mVideoList.size();
    }
}
