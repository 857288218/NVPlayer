package com.xiao.nicevideoplayer.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.FrameLayout
import com.aliyun.player.AliPlayer
import com.aliyun.player.AliPlayerFactory
import com.aliyun.player.IPlayer
import com.aliyun.player.IPlayer.OnLoadingStatusListener
import com.aliyun.player.IPlayer.OnRenderingStartListener
import com.aliyun.player.bean.InfoCode
import com.aliyun.player.nativeclass.CacheConfig
import com.aliyun.player.source.UrlSource
import com.xiao.nicevideoplayer.NiceSurfaceView
import com.xiao.nicevideoplayer.NiceVideoPlayerController
import com.xiao.nicevideoplayer.NiceVideoPlayerManager
import com.xiao.nicevideoplayer.utils.LogUtil
import com.xiao.nicevideoplayer.utils.NiceUtil

class AliVideoPlayer(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(
    mContext, attrs
), INiceVideoPlayer, SurfaceHolder.Callback {

    private var mCurrentState = INiceVideoPlayer.STATE_IDLE
    private var mCurrentMode = INiceVideoPlayer.MODE_NORMAL

    private var mAudioManager: AudioManager? = null
    private var aliPlayer: AliPlayer? = null
    private var mContainer: FrameLayout? = null
    private var surfaceView: NiceSurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var mController: NiceVideoPlayerController? = null
    private var mUrl: String? = null
    private var mHeaders: Map<String, String>? = null
    private var mBufferPercentage = 0
    private var continueFromLastPosition = true
    private var skipToPosition: Long = 0
    private var isLoop = false
    private var currentPosition: Long = 0

    var onCompletionCallback: (() -> Unit)? = null

    // 播放器开始渲染回调(首帧画面回调)
    var onVideoRenderStartCallback: (() -> Unit)? = null

    // 开始播放回调(包括首次播放、暂停后继续播放、缓冲结束后继续播放)
    var onPlayingCallback: (() -> Unit)? = null

    // 视频暂停回调
    var onPauseCallback: (() -> Unit)? = null

    // 暂停时视频缓冲回调
    var onBufferPauseCallback: (() -> Unit)? = null

    // 播放时视频缓冲回调
    var onBufferPlayingCallback: (() -> Unit)? = null

    init {
        mContainer = FrameLayout(mContext)
        this.addView(
            mContainer, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun setUp(url: String, headers: Map<String, String>?) {
        mUrl = url
        mHeaders = headers
    }

    fun setController(controller: NiceVideoPlayerController?) {
        mContainer?.removeView(mController)
        mController = controller
        mController?.let {
            it.reset()
            it.setNiceVideoPlayer(this)
            mContainer?.addView(
                it,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    fun setLooping(looping: Boolean) {
        isLoop = looping
    }

    fun setMute(isMute: Boolean) {
        aliPlayer?.isMute = isMute
    }

    fun setVideoBackgoundColor(bg: Int) = aliPlayer?.setVideoBackgroundColor(bg)

    /**
     * 是否从上一次的位置继续播放
     *
     * @param continueFromLastPosition true从上一次的位置继续播放
     */
    override fun continueFromLastPosition(continueFromLastPosition: Boolean) {
        this.continueFromLastPosition = continueFromLastPosition
    }

    override fun setSpeed(speed: Float) {
        aliPlayer?.speed = speed
    }

    override fun start() {
        if (mCurrentState == INiceVideoPlayer.STATE_IDLE) {
            NiceVideoPlayerManager.instance().currentNiceVideoPlayer = this
            initAudioManager()
            initMediaPlayer()
            initSurfaceView()
            addSurfaceView()
        } else {
            LogUtil.d("NiceVideoPlayer只有在mCurrentState == STATE_IDLE时才能调用start方法.")
        }
    }

    override fun start(position: Long) {
        skipToPosition = position
        start()
    }

    override fun restart() {
        if (mCurrentState == INiceVideoPlayer.STATE_PAUSED) {
            LogUtil.d("STATE_PLAYING")
            aliPlayer!!.start()
            mCurrentState = INiceVideoPlayer.STATE_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onPlayingCallback?.invoke()
        } else if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED) {
            LogUtil.d("STATE_BUFFERING_PLAYING")
            aliPlayer!!.start()
            mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPlayingCallback?.invoke()
        } else if (mCurrentState == INiceVideoPlayer.STATE_COMPLETED || mCurrentState == INiceVideoPlayer.STATE_ERROR) {
            aliPlayer!!.reset()
            openMediaPlayer()
        } else {
            LogUtil.d("NiceVideoPlayer在mCurrentState == " + mCurrentState + "时不能调用restart()方法.")
        }
    }

    override fun pause() {
        if (mCurrentState == INiceVideoPlayer.STATE_PLAYING) {
            LogUtil.d("STATE_PAUSED")
            aliPlayer!!.pause()
            mCurrentState = INiceVideoPlayer.STATE_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onPauseCallback?.invoke()
        }
        if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PLAYING) {
            LogUtil.d("STATE_BUFFERING_PAUSED")
            aliPlayer!!.pause()
            mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPauseCallback?.invoke()
        }
    }

    override fun seekTo(pos: Long) {
        aliPlayer?.seekTo(pos)
    }

    override fun setVolume(volume: Int) {
        mAudioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    override fun isIdle() = mCurrentState == INiceVideoPlayer.STATE_IDLE

    override fun isPreparing() = mCurrentState == INiceVideoPlayer.STATE_PREPARING

    override fun isPrepared() = mCurrentState == INiceVideoPlayer.STATE_PREPARED

    override fun isBufferingPlaying() = mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PLAYING

    override fun isBufferingPaused() = mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED

    override fun isPlaying() = mCurrentState == INiceVideoPlayer.STATE_PLAYING

    override fun isPaused() = mCurrentState == INiceVideoPlayer.STATE_PAUSED

    override fun isError() = mCurrentState == INiceVideoPlayer.STATE_ERROR

    override fun isCompleted() = mCurrentState == INiceVideoPlayer.STATE_COMPLETED

    override fun isFullScreen() = mCurrentMode == INiceVideoPlayer.MODE_FULL_SCREEN

    override fun isTinyWindow() = mCurrentMode == INiceVideoPlayer.MODE_TINY_WINDOW

    override fun isNormal() = mCurrentMode == INiceVideoPlayer.MODE_NORMAL

    override fun getMaxVolume() = mAudioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun getVolume(): Int = mAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun getDuration(): Long = aliPlayer?.duration ?: 0

    override fun getCurrentPosition(): Long = currentPosition

    override fun getBufferPercentage(): Int = mBufferPercentage

    override fun getSpeed(speed: Float): Float = aliPlayer?.speed ?: 0F

    override fun getTcpSpeed(): Long {
//        if (mMediaPlayer instanceof IjkMediaPlayer) {
//            return ((IjkMediaPlayer) mMediaPlayer).getTcpSpeed();
//        }
        return 0
    }

    private fun initAudioManager() {
        if (mAudioManager == null) {
            mAudioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mAudioManager!!.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun initMediaPlayer() {
        if (aliPlayer == null) {
            aliPlayer = AliPlayerFactory.createAliPlayer(mContext)
            val cacheConfig = CacheConfig()
            //开启缓存功能
            cacheConfig.mEnable = true
            //能够缓存的单个文件最大时长。超过此长度则不缓存
            cacheConfig.mMaxDurationS = 100
            //缓存目录的位置
            cacheConfig.mDir =
                mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            //缓存目录的最大大小。超过此大小，将会删除最旧的缓存文件
            cacheConfig.mMaxSizeMB = 200
            //设置缓存配置给到播放器
            aliPlayer!!.setCacheConfig(cacheConfig)
            val config = aliPlayer!!.config
            // 最大缓冲区时长。单位ms。播放器每次最多加载这么长时间的缓冲数据。
            config.mMaxBufferDuration = 50000
            //高缓冲时长。单位ms。当网络不好导致加载数据时，如果加载的缓冲时长到达这个值，结束加载状态。
            config.mHighBufferDuration = 3000
            // 起播缓冲区时长。单位ms。这个时间设置越短，起播越快。也可能会导致播放之后很快就会进入加载状态。
            config.mStartBufferDuration = 500
            aliPlayer!!.config = config
        }
    }

    private fun initSurfaceView() {
        if (surfaceView == null) {
            surfaceView = NiceSurfaceView(mContext)
            surfaceView?.holder?.addCallback(this)
        }
    }

    private fun addSurfaceView() {
        mContainer?.removeView(surfaceView)
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        )
        //添加完surfaceView后，会回调surfaceCreated
        mContainer?.addView(surfaceView, 0, params)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (surfaceHolder == null) {
            surfaceHolder = holder
            openMediaPlayer()
        } else {
            // activity onPause后，SurfaceView会被销毁，回调surfaceDestroyed()方法,
            // 回到前台会回调surfaceCreated，需要重新添加holder,否则没有画面
            aliPlayer!!.setDisplay(holder)
        }
        LogUtil.d("surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LogUtil.d("surfaceDestroyed")
        aliPlayer?.setDisplay(null)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        //解决切后台暂停后，回到前台不主动播放，会黑屏
        //用来刷新视频画面的。如果view的大小变化了，调用此方法将会更新画面大小，保证视频画面与View的变化一致。
        //改方法已废弃，不需要调用了
//        aliPlayer.redraw();
        LogUtil.d("surfaceChanged")
    }

    private fun openMediaPlayer() {
        // 屏幕常亮
        mContainer?.keepScreenOn = true
        aliPlayer?.run {
            //设置是否循环播放
            isLoop = isLoop
            //画面的缩放模式
//            if (width > NiceUtil.getScreenWidth(mContext) || height > NiceUtil.getScreenHeight(mContext)) {
//                aliPlayer.setScaleMode(IPlayer.ScaleMode.SCALE_ASPECT_FIT);
//            } else {
//                aliPlayer.setScaleMode(IPlayer.ScaleMode.SCALE_ASPECT_FILL);
//            }
            // 设置监听
            setOnPreparedListener(mOnPreparedListener)
            setOnVideoSizeChangedListener(mOnVideoSizeChangedListener)
            setOnCompletionListener(mOnCompletionListener)
            setOnErrorListener(mOnErrorListener)
            setOnRenderingStartListener(mOnRenderingStartListener)
            setOnLoadingStatusListener(mOnLoadingStatusListener)
            setOnInfoListener(mOnInfoListener)
            setOnSeekCompleteListener(mOnSeekCompleteListener)
            // 设置dataSource
            val urlSource = UrlSource()
            urlSource.uri = mUrl
            setDataSource(urlSource)
            setDisplay(surfaceHolder)
            prepare()
            mCurrentState = INiceVideoPlayer.STATE_PREPARING
            mController?.onPlayStateChanged(mCurrentState)
            LogUtil.d("STATE_PREPARING")
        }
    }

    private val mOnPreparedListener = IPlayer.OnPreparedListener {
        //自动播放的时候将不会回调onPrepared回调，而会回调onInfo回调。
        mCurrentState = INiceVideoPlayer.STATE_PREPARED
        mController?.onPlayStateChanged(mCurrentState)
        LogUtil.d("onPrepared ——> STATE_PREPARED")
        aliPlayer!!.start()
        //这里用else if的方式只能执行一个，由于seekTo是异步方法，可能导致，清晰度切换后，又切到continueFromLastPosition的情况
        if (skipToPosition != 0L) {
            // 跳到指定位置播放
            aliPlayer!!.seekTo(skipToPosition)
            skipToPosition = 0
        } else if (continueFromLastPosition) {
            // 从上次的保存位置播放
            val savedPlayPosition = NiceUtil.getSavedPlayPosition(mContext, mUrl)
            aliPlayer!!.seekTo(savedPlayPosition)
        }
    }
    private val mOnVideoSizeChangedListener =
        IPlayer.OnVideoSizeChangedListener { width, height ->
            // surfaceView.adaptVideoSize(width, height);
            LogUtil.d("onVideoSizeChanged ——> width：$width， height：$height")
        }
    private val mOnCompletionListener = IPlayer.OnCompletionListener {
        //设置了循环播放后，就不会再执行这个回调了
        LogUtil.d("onCompletion ——> STATE_COMPLETED")
        mCurrentState = INiceVideoPlayer.STATE_COMPLETED
        mController?.onPlayStateChanged(mCurrentState)
        onCompletionCallback?.invoke()
        // 清除屏幕常亮
        mContainer?.keepScreenOn = false
        // 重置当前播放进度
        NiceUtil.savePlayPosition(context, mUrl, 0)
    }
    private val mOnErrorListener = IPlayer.OnErrorListener {
        //出错事件
        mCurrentState = INiceVideoPlayer.STATE_ERROR
        mController?.onPlayStateChanged(mCurrentState)
        LogUtil.d("onError ——> STATE_ERROR")
    }
    private val mOnRenderingStartListener = OnRenderingStartListener {
        //首帧渲染显示事件
        LogUtil.d("onRenderingStart")
        mCurrentState = INiceVideoPlayer.STATE_PLAYING
        mController?.onPlayStateChanged(mCurrentState)
        onVideoRenderStartCallback?.invoke()
        onPlayingCallback?.invoke()
    }
    private val mOnLoadingStatusListener: OnLoadingStatusListener =
        object : OnLoadingStatusListener {
            override fun onLoadingBegin() {
                //缓冲开始, 可能还没播放画面就开始缓冲
                if (mCurrentState == INiceVideoPlayer.STATE_PAUSED || mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED) {
                    mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PAUSED
                    onBufferPauseCallback?.invoke()
                    LogUtil.d("onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED")
                } else {
                    mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PLAYING
                    onBufferPlayingCallback?.invoke()
                    LogUtil.d("onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING")
                }
                mController?.onPlayStateChanged(mCurrentState)
            }

            override fun onLoadingProgress(percent: Int, v: Float) {
                //缓冲进度
                mBufferPercentage = percent
            }

            override fun onLoadingEnd() {
                //缓冲结束
                if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PLAYING) {
                    LogUtil.d("onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING")
                    mCurrentState = INiceVideoPlayer.STATE_PLAYING
                    mController?.onPlayStateChanged(mCurrentState)
                    onPlayingCallback?.invoke()
                }
                if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED) {
                    LogUtil.d("onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED")
                    mCurrentState = INiceVideoPlayer.STATE_PAUSED
                    mController?.onPlayStateChanged(mCurrentState)
                    onPauseCallback?.invoke()
                }
            }
        }
    private val mOnInfoListener = IPlayer.OnInfoListener { infoBean ->
        when {
            infoBean.code.value == InfoCode.AutoPlayStart.value -> {
                // 自动播放开始事件;注意：自动播放的时候将不会回调onPrepared回调，而会回调onInfo回调。
                // 还没确认是否会回调onRenderingStart，如果不回调这里需要执行一下onPlayStateChanged（STATE_PLAYING）
                LogUtil.d("onInfo ——> AutoPlayStart")
                mCurrentState = INiceVideoPlayer.STATE_PREPARED
                mController?.onPlayStateChanged(mCurrentState)
            }
            infoBean.code.value == InfoCode.LoopingStart.value -> {
                //循环播放开始事件,不会回调onPrepared，onRenderingStart，onCompletion
                LogUtil.d("onInfo ——> LoopingStart")
            }
            infoBean.code == InfoCode.CurrentPosition -> {
                //更新currentPosition
                currentPosition = infoBean.extraValue
                mController?.updateProgress()
            }
        }
    }
    private val mOnSeekCompleteListener = IPlayer.OnSeekCompleteListener {
        LogUtil.d("onSeekComplete" + getCurrentPosition())
        //这里取到的CurrentPosition可能也不是拖动后的最新的,onInfo获取到的seekTo后的position有延迟
    }

    /**
     * 全屏，将mContainer(内部包含mTextureView和mController)从当前容器中移除，并添加到android.R.content中.
     * 切换横屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期
     */
    override fun enterFullScreen() {
        if (mCurrentMode == INiceVideoPlayer.MODE_FULL_SCREEN) return
        NiceVideoPlayerManager.instance().setAllowRelease(false)
        // 隐藏ActionBar、状态栏，并横屏
        NiceUtil.hideActionBar(mContext)
        NiceUtil.scanForActivity(mContext).requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val contentView = NiceUtil.scanForActivity(mContext)
            .findViewById<ViewGroup>(android.R.id.content)
        if (mCurrentMode == INiceVideoPlayer.MODE_TINY_WINDOW) {
            contentView.removeView(mContainer)
        } else {
            removeView(mContainer)
        }
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        contentView.addView(mContainer, params)
        mCurrentMode = INiceVideoPlayer.MODE_FULL_SCREEN
        mController?.onPlayModeChanged(mCurrentMode)
        LogUtil.d("MODE_FULL_SCREEN")
    }

    /**
     * 退出全屏，移除mTextureView和mController，并添加到非全屏的容器中。
     * 切换竖屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期.
     *
     * @return true退出全屏.
     */
    override fun exitFullScreen(): Boolean {
        if (mCurrentMode == INiceVideoPlayer.MODE_FULL_SCREEN) {
            NiceVideoPlayerManager.instance().setAllowRelease(true)
            NiceUtil.showActionBar(mContext)
            NiceUtil.scanForActivity(mContext).requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val contentView = NiceUtil.scanForActivity(mContext)
                .findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.addView(mContainer, params)
            mCurrentMode = INiceVideoPlayer.MODE_NORMAL
            mController?.onPlayModeChanged(mCurrentMode)
            LogUtil.d("MODE_NORMAL")
            return true
        }
        return false
    }

    /**
     * 进入小窗口播放，小窗口播放的实现原理与全屏播放类似。
     */
    override fun enterTinyWindow() {
        if (mCurrentMode == INiceVideoPlayer.MODE_TINY_WINDOW) return
        removeView(mContainer)
        val contentView = NiceUtil.scanForActivity(mContext)
            .findViewById<ViewGroup>(android.R.id.content)
        // 小窗口的宽度为屏幕宽度的60%，长宽比默认为16:9，右边距、下边距为8dp。
        val params = LayoutParams(
            (NiceUtil.getScreenWidth(mContext) * 0.6f).toInt(),
            (NiceUtil.getScreenWidth(mContext) * 0.6f * 9f / 16f).toInt()
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.rightMargin = NiceUtil.dp2px(mContext, 8f)
        params.bottomMargin = NiceUtil.dp2px(mContext, 8f)
        contentView.addView(mContainer, params)
        mCurrentMode = INiceVideoPlayer.MODE_TINY_WINDOW
        mController?.onPlayModeChanged(mCurrentMode)
        LogUtil.d("MODE_TINY_WINDOW")
    }

    /**
     * 退出小窗口播放
     */
    override fun exitTinyWindow(): Boolean {
        if (mCurrentMode == INiceVideoPlayer.MODE_TINY_WINDOW) {
            val contentView = NiceUtil.scanForActivity(mContext)
                .findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.addView(mContainer, params)
            mCurrentMode = INiceVideoPlayer.MODE_NORMAL
            mController?.onPlayModeChanged(mCurrentMode)
            LogUtil.d("MODE_NORMAL")
            return true
        }
        return false
    }

    override fun releasePlayer() {
        mAudioManager?.abandonAudioFocus(null)
        mAudioManager = null
        //缓解当列表滑动到正在播放的item不可见时，释放会造成列表卡一下的问题
        Thread {
            aliPlayer?.release()
            aliPlayer = null
        }.start()
        surfaceHolder = null

        // 解决释放播放器时黑一下,使用TextureView没有该问题
        Handler(Looper.getMainLooper()).post { mContainer?.removeView(surfaceView) }
        mCurrentState = INiceVideoPlayer.STATE_IDLE
    }

    override fun release() {
        // 保存播放位置
        if (isPlaying || isBufferingPlaying || isBufferingPaused || isPaused) {
            NiceUtil.savePlayPosition(mContext, mUrl, getCurrentPosition())
        } else if (isCompleted) {
            NiceUtil.savePlayPosition(mContext, mUrl, 0)
        }
        // 退出全屏或小窗口
        if (isFullScreen) {
            exitFullScreen()
        }
        if (isTinyWindow) {
            exitTinyWindow()
        }
        mCurrentMode = INiceVideoPlayer.MODE_NORMAL

        // 恢复控制器
        mController?.reset()

        // 释放播放器
        releasePlayer()
//        Runtime.getRuntime().gc();
    }
}