package com.xiao.nicevieoplayer.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.xiao.nicevideoplayer.MyVideoViewController;
import com.xiao.nicevieoplayer.R;
import com.xiao.nicevieoplayer.example.adapter.holder.AliHolder;
import com.xiao.nicevieoplayer.example.bean.Video;

import java.util.List;

public class AliAdapter extends RecyclerView.Adapter<AliHolder>{
    private Context mContext;
    private List<Video> mVideoList;
    private boolean isUseTexture;

    public AliAdapter(Context context, List<Video> videoList, boolean isUseTexture) {
        mContext = context;
        mVideoList = videoList;
        this.isUseTexture = isUseTexture;
    }

    @Override
    public AliHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_alivideo, parent, false);
        AliHolder holder = new AliHolder(itemView, isUseTexture);
        MyVideoViewController controller = new MyVideoViewController(mContext);
        holder.setController(controller);
//        holder.mVideoPlayer.setLooping(true);
        return holder;
    }

    @Override
    public void onBindViewHolder(AliHolder holder, int position) {
        Video video = mVideoList.get(position);
        holder.bindData(video);
    }

    @Override
    public int getItemCount() {
        return mVideoList.size();
    }
}
