package com.xiao.nicevieoplayer.example.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xiao.nicevideoplayer.MyVideoViewController
import com.xiao.nicevieoplayer.R
import com.xiao.nicevieoplayer.example.adapter.holder.MediaHolder
import com.xiao.nicevieoplayer.example.bean.Video

class MediaAdapter(private val mContext: Context, private val mVideoList: List<Video>, private val isUseTexture: Boolean = true) :
    RecyclerView.Adapter<MediaHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder {
        val itemView =
            LayoutInflater.from(mContext).inflate(R.layout.item_media_texture_video, parent, false)
        val holder = MediaHolder(itemView, isUseTexture)
        val controller = MyVideoViewController(mContext)
        holder.setController(controller)
        // itemView.findViewById<TextView>(R.id.tv_detail).setOnClickListener {
        //     VideoDetailActivity.startActivity(
        //         mContext as AppCompatActivity,
        //         holder.mVideoPlayer.getUrl(),
        //         holder.mVideoPlayer.currentPosition
        //     )
        // }
        //        holder.mVideoPlayer.setLooping(true);
        return holder
    }

    override fun onBindViewHolder(holder: MediaHolder, position: Int) {
        val video = mVideoList[position]
        holder.bindData(video)
    }

    override fun getItemCount(): Int {
        return mVideoList.size
    }
}