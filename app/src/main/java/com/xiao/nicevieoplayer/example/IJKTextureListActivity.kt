package com.xiao.nicevieoplayer.example

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiao.nicevideoplayer.NiceVideoPlayerManager
import com.xiao.nicevideoplayer.player.IVideoPlayer
import com.xiao.nicevieoplayer.R
import com.xiao.nicevieoplayer.example.adapter.IJKAdapter
import com.xiao.nicevieoplayer.example.base.CompatHomeKeyActivity
import com.xiao.nicevieoplayer.example.util.DataUtil

class IJKTextureListActivity : CompatHomeKeyActivity() {
    private var mRecyclerView: RecyclerView? = null
    private var mLayoutManager: LinearLayoutManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view)
        init()
    }

    private fun init() {
        mRecyclerView = findViewById(R.id.recycler_view)
        mLayoutManager = LinearLayoutManager(this)
        mRecyclerView?.layoutManager = mLayoutManager
        val adapter = IJKAdapter(this, DataUtil.getVideoListData(), true)
        mRecyclerView?.adapter = adapter
        mRecyclerView?.addOnChildAttachStateChangeListener(object :
            RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {}
            override fun onChildViewDetachedFromWindow(view: View) {
                val niceVideoPlayer: IVideoPlayer = view.findViewById(R.id.nice_video_player)
                if (niceVideoPlayer === NiceVideoPlayerManager.instance()!!.currentNiceVideoPlayer) {
                    NiceVideoPlayerManager.instance()!!.releaseNiceVideoPlayer()
                }
            }
        })

        //滑动自动播放
//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
//                    AutoPlayUtils.onScrollPlayVideo(recyclerView, R.id.nice_video_player, mLayoutManager.findFirstVisibleItemPosition(), mLayoutManager.findLastVisibleItemPosition());
//                }
//            }
//
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                super.onScrolled(recyclerView, dx, dy);
//                if (dy != 0) {
//                    AutoPlayUtils.onScrollReleaseAllVideos(mLayoutManager.findFirstVisibleItemPosition(), mLayoutManager.findLastVisibleItemPosition(), 0.2f);
//                }
//            }
//        });
    }
}