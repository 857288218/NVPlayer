package com.xiao.nicevieoplayer.example.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xiao.nicevideoplayer.MyVideoViewController
import com.xiao.nicevieoplayer.R
import com.xiao.nicevieoplayer.example.adapter.holder.IJKHolder
import com.xiao.nicevieoplayer.example.bean.Video

class IJKAdapter(
    private val mContext: Context,
    private val mVideoList: List<Video>,
    private val isUseTexture: Boolean = true
) :
    RecyclerView.Adapter<IJKHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IJKHolder {
        val itemView =
            LayoutInflater.from(mContext).inflate(R.layout.item_ijk_list_video, parent, false)
        val holder = IJKHolder(itemView, isUseTexture)
        val controller = MyVideoViewController(mContext)
        holder.setController(controller)
        // itemView.findViewById<TextView>(R.id.tv_detail).setOnClickListener {
        //     VideoDetailActivity.startActivity(mContext as AppCompatActivity, holder.mVideoPlayer.getUrl(), holder.mVideoPlayer.currentPosition)
        // }
        //        holder.mVideoPlayer.setLooping(true);
        return holder
    }

    override fun onBindViewHolder(holder: IJKHolder, position: Int) {
        val video = mVideoList[position]
        holder.bindData(video)
    }

    override fun getItemCount(): Int {
        return mVideoList.size
    }
}