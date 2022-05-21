package com.xiao.nicevieoplayer.example.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.xiao.nicevideoplayer.MyVideoViewController
import com.xiao.nicevieoplayer.R
import com.xiao.nicevieoplayer.example.VideoDetailActivity
import com.xiao.nicevieoplayer.example.adapter.holder.IJKTextureHolder
import com.xiao.nicevieoplayer.example.bean.Video

class IJKTextureAdapter(private val mContext: Context, private val mVideoList: List<Video>) :
    RecyclerView.Adapter<IJKTextureHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IJKTextureHolder {
        val itemView =
            LayoutInflater.from(mContext).inflate(R.layout.item_ijk_texture_video, parent, false)
        val holder = IJKTextureHolder(itemView)
        val controller = MyVideoViewController(mContext)
        holder.setController(controller)
        itemView.findViewById<TextView>(R.id.tv_detail).setOnClickListener {
            VideoDetailActivity.startActivity(mContext as AppCompatActivity, holder.mVideoPlayer.getUrl(), holder.mVideoPlayer.currentPosition)
        }
        //        holder.mVideoPlayer.setLooping(true);
        return holder
    }

    override fun onBindViewHolder(holder: IJKTextureHolder, position: Int) {
        val video = mVideoList[position]
        holder.bindData(video)
    }

    override fun getItemCount(): Int {
        return mVideoList.size
    }
}