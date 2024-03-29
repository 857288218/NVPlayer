package com.xiao.nicevideoplayer.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.aliyun.player.AliPlayer
import com.aliyun.player.AliPlayerFactory
import com.aliyun.player.AliPlayerGlobalSettings
import com.aliyun.player.IPlayer
import com.aliyun.player.IPlayer.OnLoadingStatusListener
import com.aliyun.player.IPlayer.OnRenderingStartListener
import com.aliyun.player.bean.InfoCode
import com.aliyun.player.source.UrlSource
import com.xiao.nicevideoplayer.BaseApplication
import com.xiao.nicevideoplayer.NiceSurfaceView
import com.xiao.nicevideoplayer.NiceTextureView
import com.xiao.nicevideoplayer.NiceVideoPlayerManager
import com.xiao.nicevideoplayer.R
import com.xiao.nicevideoplayer.VideoViewController
import com.xiao.nicevideoplayer.utils.LogUtil
import com.xiao.nicevideoplayer.utils.NiceUtil

// 问题：1.不支持播放项目raw/assets文件夹中视频
//      2.使用surfaceView在Activity#onStop中暂停正在播放的视频后回到前台不播放视频会黑屏，onPause中没事,使用TextureView没事
class AliVideoView(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(mContext, attrs), IVideoPlayer, SurfaceHolder.Callback,
    TextureView.SurfaceTextureListener {

    private var mCurrentState = IVideoPlayer.STATE_IDLE
    private var mCurrentMode = IVideoPlayer.MODE_NORMAL

    private var mAudioManager: AudioManager? = null
    private var aliPlayer: AliPlayer? = null
    private var mContainer: FrameLayout? = null
    private var mTextureView: TextureView? = null
    private var surfaceView: SurfaceView? = null
    private var mController: VideoViewController? = null
    private var mUrl: String? = null
    private var mHeaders: Map<String, String>? = null
    private var mBufferPercentage = 0
    private var continueFromLastPosition = false
    private var startToPosition: Long = 0
    private var isLooping = false
    private var videoBgColor: Int? = null
    private var isMute = false
    private var scaleMode = IPlayer.ScaleMode.SCALE_ASPECT_FIT
    var enableLocalCache = false
    var userAgent = ""
    private var currentPosition: Long = 0
    private var isStartToPause = false
    private var isOnlyPrepare = false
    private var portraitSystemUiVisibility = -1

    // 标记是否还未开始播放时候的暂停,即mCurrentState < IVideoPlayer.STATE_PLAYING
    private var notPlayingPause = false

    @JvmField
    var isUseTextureView = false

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

    // 视频准备完成回调
    var onPreparedCallback: (() -> Unit)? = null

    var onVideoSizeChangedListener: ((Int, Int) -> Unit)? = null

    companion object {
        init {
            // 本地缓存 https://help.aliyun.com/document_detail/124714.html#p-gzq-d6a-r9r
            AliPlayerGlobalSettings.enableLocalCache(
                true,
                100 * 1024,
                BaseApplication.getApplication()
                    .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            )
            AliPlayerGlobalSettings.setCacheFileClearConfig(0, 100, 0)
        }
    }

    init {
        val types = context.obtainStyledAttributes(attrs, R.styleable.AliVideoView)
        isUseTextureView = types.getBoolean(R.styleable.AliVideoView_isUseTexture, false)
        types.recycle()

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

    fun setController(controller: VideoViewController?, isAdd: Boolean = true) {
        mContainer?.removeView(mController)
        mController = controller
        mController?.let {
            it.reset()
            it.setVideoPlayer(this)
            if (isAdd) {
                mContainer?.addView(
                    it,
                    LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    }

    override fun setLooping(looping: Boolean) {
        aliPlayer?.isLoop = looping
        this.isLooping = looping
    }

    override fun setMute(isMute: Boolean) {
        aliPlayer?.isMute = isMute
        this.isMute = isMute
    }

    fun setVideoBackgroundColor(bgColor: Int) {
        aliPlayer?.setVideoBackgroundColor(bgColor)
        videoBgColor = bgColor
    }

    fun setScaleMode(scaleMode: IPlayer.ScaleMode) {
        aliPlayer?.scaleMode = scaleMode
        this.scaleMode = scaleMode
    }

    /**
     * 是否从上一次的位置继续播放
     *
     * @param continueFromLastPosition true从上一次的位置继续播放
     */
    override fun continueFromLastPosition(continueFromLastPosition: Boolean) {
        this.continueFromLastPosition = continueFromLastPosition
    }

    //支持0.5～5倍速的播放，通常按0.5的倍数来设置，例如0.5倍、1倍、1.5倍等
    override fun setSpeed(speed: Float) {
        aliPlayer?.speed = speed
    }

    override fun start() {
        if (isIdle) {
            initAudioManager()
            openMediaPlayer()
            if (isUseTextureView) {
                addTextureView()
            } else {
                addSurfaceView()
            }
        } else if (isCompleted || isError || isPaused || isBufferingPaused) {
            restart()
        } else if (isPrepared) {
            aliPlayer?.start()
            customStartToPos()
        } else if (isPreparing && isOnlyPrepare) {
            isOnlyPrepare = false
        } else {
            LogUtil.d("NiceVideoPlayer mCurrentState = ${mCurrentState}, 不能调用start()")
        }
    }

    // 如果startToPosition ！= 0，在start前可以选择调整skipToPosition
    fun fixStarToPosition(delta: Long) {
        if (startToPosition > 0) {
            startToPosition += delta
        }
    }

    override fun start(position: Long) {
        startToPosition = position
        start()
    }

    override fun startToPause(pos: Long) {
        isStartToPause = true
        start(pos)
    }

    // 提前准备视频不自动播放 需要再调用start()播放；用于视频的提前准备减少直接调用start()时的准备视频时间
    fun onlyPrepare() {
        isOnlyPrepare = true
        start()
    }

    override fun restart() {
        when {
            isPaused -> {
                LogUtil.d("STATE_PLAYING")
                aliPlayer!!.start()
                mCurrentState = IVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
            }
            isBufferingPaused -> {
                LogUtil.d("STATE_BUFFERING_PLAYING")
                aliPlayer!!.start()
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onBufferPlayingCallback?.invoke()
            }
            isError -> {
                // 将播放器设置的属性都清空,重新设置打开
                reset()
                start()
            }
            isCompleted -> {
                mCurrentState = IVideoPlayer.STATE_PREPARING
                mController?.onPlayStateChanged(mCurrentState)
                aliPlayer?.prepare()
            }
            else -> {
                LogUtil.d("NiceVideoPlayer在mCurrentState = " + mCurrentState + "时不能调用restart()方法.")
            }
        }
    }

    // start后调用，切换另一个视频播放
    override fun playOtherVideo(videoPath: String, startPosition: Long, isAutoPlay: Boolean) {
        aliPlayer?.run {
            setUp(videoPath, null)
            stop()
            this@AliVideoView.reset()
            if (isAutoPlay) {
                start(startPosition)
            } else {
                startToPause(startPosition)
            }
        }
    }

    override fun pause() {
        if (isPlaying) {
            LogUtil.d("STATE_PAUSED")
            aliPlayer!!.pause()
            mCurrentState = IVideoPlayer.STATE_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onPauseCallback?.invoke()
        } else if (isBufferingPlaying) {
            LogUtil.d("STATE_BUFFERING_PAUSED")
            aliPlayer!!.pause()
            mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPauseCallback?.invoke()
        } else if (isIdle || isPreparing) {
            notPlayingPause = true
        } else if (isPrepared) {
            // mOnPreparedListener中start了
            mCurrentState = IVideoPlayer.STATE_PLAYING
            pause()
        }
    }

    override fun seekTo(pos: Long) {
        if (aliPlayer == null) {
            LogUtil.d("seekTo需要在start后调用")
        } else {
            aliPlayer!!.seekTo(pos, IPlayer.SeekMode.Accurate)
        }
    }

    override fun setVolume(volume: Int) {
        mAudioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    override fun isIdle() = mCurrentState == IVideoPlayer.STATE_IDLE

    override fun isPreparing() = mCurrentState == IVideoPlayer.STATE_PREPARING

    override fun isPrepared() = mCurrentState == IVideoPlayer.STATE_PREPARED

    override fun isBufferingPlaying() = mCurrentState == IVideoPlayer.STATE_BUFFERING_PLAYING

    override fun isBufferingPaused() = mCurrentState == IVideoPlayer.STATE_BUFFERING_PAUSED

    override fun isPlaying() = mCurrentState == IVideoPlayer.STATE_PLAYING

    override fun isPaused() = mCurrentState == IVideoPlayer.STATE_PAUSED

    override fun isError() = mCurrentState == IVideoPlayer.STATE_ERROR

    override fun isCompleted() = mCurrentState == IVideoPlayer.STATE_COMPLETED

    override fun isFullScreen() = mCurrentMode == IVideoPlayer.MODE_FULL_SCREEN

    override fun isTinyWindow() = mCurrentMode == IVideoPlayer.MODE_TINY_WINDOW

    override fun isNormal() = mCurrentMode == IVideoPlayer.MODE_NORMAL

    override fun getMaxVolume() = mAudioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun getVolume(): Int = mAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun getDuration(): Long = aliPlayer?.duration ?: 0

    override fun getCurrentPosition(): Long = currentPosition

    override fun getBufferPercentage(): Int = mBufferPercentage

    override fun getSpeed(speed: Float): Float = aliPlayer?.speed ?: 0F

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

    private fun setConfig() {
        if (aliPlayer != null) {
            val config = aliPlayer!!.config

            // 是否针对播放的URL开启本地缓存，默认值为true。当AliPlayerGlobalSettings处的本地缓开启时，且同时开启此处的本地缓存，
            // 该URL的本地缓存才会生效；若此处设置为false，则关闭该URL的本地缓存。
            config.mEnableLocalCache = enableLocalCache

            // 配置缓冲区
            // 最大缓冲区时长。单位ms。播放器每次最多加载这么长时间的缓冲数据。
            config.mMaxBufferDuration = 50000
            //高缓冲时长。单位ms。当网络不好导致加载数据时，如果加载的缓冲时长到达这个值，结束加载状态。
            config.mHighBufferDuration = 3000
            // 起播缓冲区时长。单位ms。这个时间设置越短，起播越快。也可能会导致播放之后很快就会进入加载状态。
            config.mStartBufferDuration = 200

            // 设置HTTP Header
            if (mHeaders?.size ?: 0 > 0) {
                val headers = arrayOfNulls<String>(mHeaders!!.size)
                var i = 0
                mHeaders!!.forEach {
                    headers[i++] = "${it.key}:${it.value}"
                }
                config.customHeaders = headers
            }
            // 设置UserAgent
            if (userAgent.isNotEmpty())
                config.mUserAgent = userAgent
            aliPlayer!!.config = config
        }
    }

    // 使用SurfaceView
    private fun addSurfaceView() {
        if (surfaceView == null) {
            surfaceView = NiceSurfaceView(mContext)
            surfaceView?.holder?.addCallback(this)

            mContainer?.removeView(surfaceView)
            //添加完surfaceView后，会回调surfaceCreated
            mContainer?.addView(
                surfaceView, 0, LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        aliPlayer?.setSurface(holder.surface)
        LogUtil.d("surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LogUtil.d("surfaceDestroyed")
        aliPlayer?.setSurface(null)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        //解决切后台暂停后，回到前台不主动播放，会黑屏
        //用来刷新视频画面的。如果view的大小变化了，调用此方法将会更新画面大小，保证视频画面与View的变化一致。
        aliPlayer?.surfaceChanged()
        LogUtil.d("surfaceChanged")
    }

    // 使用TextureView
    private fun addTextureView() {
        if (mTextureView == null) {
            mTextureView = NiceTextureView(mContext)
            mTextureView!!.surfaceTextureListener = this

            mContainer?.removeView(mTextureView)
            mContainer?.addView(
                mTextureView, 0, LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        LogUtil.d("onSurfaceTextureAvailable")
        aliPlayer?.setSurface(Surface(surface))
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        aliPlayer?.surfaceChanged()
        LogUtil.d("onSurfaceTextureSizeChanged")
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        LogUtil.d("onSurfaceTextureDestroyed")
        aliPlayer?.setSurface(null)
        return false
    }

    private fun openMediaPlayer() {
        if (aliPlayer == null) {
            aliPlayer = AliPlayerFactory.createAliPlayer(mContext)
        }
        // 屏幕常亮
        mContainer?.keepScreenOn = true
        aliPlayer!!.run {
            setConfig()
            isLoop = isLooping
            this@AliVideoView.setMute(this@AliVideoView.isMute)
            if (videoBgColor != null) {
                setVideoBackgroundColor(videoBgColor!!)
            }
            scaleMode = this@AliVideoView.scaleMode
            setOnPreparedListener(mOnPreparedListener)
            setOnVideoSizeChangedListener(mOnVideoSizeChangedListener)
            setOnCompletionListener(mOnCompletionListener)
            setOnErrorListener(mOnErrorListener)
            setOnRenderingStartListener(mOnRenderingStartListener)
            setOnLoadingStatusListener(mOnLoadingStatusListener)
            setOnInfoListener(mOnInfoListener)
            setOnSeekCompleteListener(mOnSeekCompleteListener)
            // 设置dataSource
            val urlSource = UrlSource().apply { uri = mUrl }
            setDataSource(urlSource)
            prepare()
            mCurrentState = IVideoPlayer.STATE_PREPARING
            mController?.onPlayStateChanged(mCurrentState)
            LogUtil.d("STATE_PREPARING")
        }
    }

    private val mOnPreparedListener = IPlayer.OnPreparedListener {
        mCurrentState = IVideoPlayer.STATE_PREPARED
        mController?.onPlayStateChanged(mCurrentState)
        onPreparedCallback?.invoke()
        LogUtil.d("onPrepared ——> STATE_PREPARED")
        // 不设置autoPlay Prepared后不自动播放 && aliPlayer.start()前seekTo也不会播放只是定位到指定位置
        if (!isOnlyPrepare) {
            if (!isStartToPause) {
                aliPlayer!!.start()
            }
            customStartToPos()
            if (notPlayingPause) {
                // 这里mCurrentState = IVideoPlayer.STATE_PLAYING就是为了满足pause()条件
                mCurrentState = IVideoPlayer.STATE_PLAYING
                pause()
                notPlayingPause = false
            }
        }
        isOnlyPrepare = false
    }

    private fun customStartToPos() {
        //这里用else if的方式只能执行一个，由于seekTo是异步方法，可能导致，清晰度切换后，又切到continueFromLastPosition的情况
        if (startToPosition > 0) {
            seekTo(startToPosition)
            startToPosition = 0
        } else if (continueFromLastPosition) {
            // 从上次的保存位置播放
            val savedPlayPosition = NiceUtil.getSavedPlayPosition(mContext, mUrl)
            seekTo(savedPlayPosition)
        }
    }

    private val mOnVideoSizeChangedListener =
        IPlayer.OnVideoSizeChangedListener { width, height ->
            // surfaceView?.adaptVideoSize(width, height);
            LogUtil.d("onVideoSizeChanged ——> width：$width， height：$height")
            onVideoSizeChangedListener?.invoke(width, height)
        }
    private val mOnCompletionListener = IPlayer.OnCompletionListener {
        //设置了循环播放后，就不会再执行这个回调了
        LogUtil.d("onCompletion ——> STATE_COMPLETED")
        mCurrentState = IVideoPlayer.STATE_COMPLETED
        mController?.onPlayStateChanged(mCurrentState)
        onCompletionCallback?.invoke()
        // 清除屏幕常亮
        mContainer?.keepScreenOn = false
        // 重置当前播放进度
        NiceUtil.savePlayPosition(context, mUrl, 0)
    }
    private val mOnErrorListener = IPlayer.OnErrorListener {
        //出错事件
        mCurrentState = IVideoPlayer.STATE_ERROR
        mController?.onPlayStateChanged(mCurrentState)
        aliPlayer?.stop()
        LogUtil.d("onError ——> STATE_ERROR: ${it.code},${it.extra},${it.msg}")
    }
    private val mOnRenderingStartListener = OnRenderingStartListener {
        //首帧渲染显示事件,循环播放时不会回调onRenderingStart
        LogUtil.d("onRenderingStart")
        onVideoRenderStartCallback?.invoke()
        // 这里先回调mController#STATE_RENDERING_START，然后如果不是 isStartToPause 再回调STATE_PLAYING
        mCurrentState = IVideoPlayer.STATE_PLAYING
        mController?.onPlayStateChanged(IVideoPlayer.STATE_RENDERING_START)
        if (isStartToPause) {
            pause()
            isStartToPause = false
        } else {
            mController?.onPlayStateChanged(mCurrentState)
            onPlayingCallback?.invoke()
        }
    }
    private val mOnLoadingStatusListener: OnLoadingStatusListener =
        object : OnLoadingStatusListener {
            override fun onLoadingBegin() {
                //缓冲开始, 可能还没播放画面就开始缓冲
                if (isPaused || isBufferingPaused) {
                    mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
                    onBufferPauseCallback?.invoke()
                    LogUtil.d("onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED")
                } else {
                    mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
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
                if (isBufferingPlaying) {
                    LogUtil.d("onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING")
                    mCurrentState = IVideoPlayer.STATE_PLAYING
                    mController?.onPlayStateChanged(mCurrentState)
                    onPlayingCallback?.invoke()
                }
                if (isBufferingPaused) {
                    LogUtil.d("onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED")
                    mCurrentState = IVideoPlayer.STATE_PAUSED
                    mController?.onPlayStateChanged(mCurrentState)
                    onPauseCallback?.invoke()
                }
            }
        }
    private val mOnInfoListener = IPlayer.OnInfoListener { infoBean ->
        when {
            infoBean.code.value == InfoCode.AutoPlayStart.value -> {
                // 自动播放开始事件：自动播放时依次回调onPrepared，OnInfoListener.AutoPlayStart，onRenderingStart
                LogUtil.d("onInfo ——> AutoPlayStart")
            }
            infoBean.code.value == InfoCode.LoopingStart.value -> {
                //循环播放开始事件,不会回调onCompletion,onPrepared,onRenderingStart
                LogUtil.d("onInfo ——> LoopingStart")
            }
            infoBean.code == InfoCode.CurrentPosition -> {
                //更新currentPosition
                currentPosition = infoBean.extraValue
                mController?.updateProgress()
            }
            // else -> LogUtil.d("onInfo ——> code：${infoBean.code}, msg: ${infoBean.extraMsg}")
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
        if (isFullScreen) return
        NiceVideoPlayerManager.instance().setAllowRelease(false)
        val activity = NiceUtil.scanForActivity(mContext)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // 解决横屏时刘海处变为黑条问题
        activity.window.attributes = activity.window.attributes.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // NiceUtil.hideActionBar(mContext)
        portraitSystemUiVisibility = activity.window.decorView.systemUiVisibility
        // 全屏显示，隐藏状态栏和导航栏，拉出状态栏和导航栏显示一会儿后消失
        NiceUtil.scanForActivity(mContext).window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val contentView = NiceUtil.scanForActivity(mContext)
            .findViewById<ViewGroup>(android.R.id.content)
        if (isTinyWindow) {
            contentView.removeView(mContainer)
        } else {
            removeView(mContainer)
        }
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        contentView.addView(mContainer, params)
        mCurrentMode = IVideoPlayer.MODE_FULL_SCREEN
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
        if (isFullScreen) {
            NiceVideoPlayerManager.instance().setAllowRelease(true)
            val activity = NiceUtil.scanForActivity(mContext)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // 恢复状态栏和导航栏
            activity.window.decorView.systemUiVisibility = portraitSystemUiVisibility
            // NiceUtil.showActionBar(mContext)
            val contentView = NiceUtil.scanForActivity(mContext)
                .findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.addView(mContainer, params)
            mCurrentMode = IVideoPlayer.MODE_NORMAL
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
        if (isTinyWindow) return
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
        mCurrentMode = IVideoPlayer.MODE_TINY_WINDOW
        mController?.onPlayModeChanged(mCurrentMode)
        LogUtil.d("MODE_TINY_WINDOW")
    }

    /**
     * 退出小窗口播放
     */
    override fun exitTinyWindow(): Boolean {
        if (isTinyWindow) {
            val contentView = NiceUtil.scanForActivity(mContext)
                .findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.addView(mContainer, params)
            mCurrentMode = IVideoPlayer.MODE_NORMAL
            mController?.onPlayModeChanged(mCurrentMode)
            LogUtil.d("MODE_NORMAL")
            return true
        }
        return false
    }

    override fun reset() {
        aliPlayer?.reset()
        mCurrentState = IVideoPlayer.STATE_IDLE
    }

    override fun release() {
        // 保存播放位置
        if (isPlaying || isBufferingPlaying || isBufferingPaused || isPaused) {
            NiceUtil.savePlayPosition(mContext, mUrl, getCurrentPosition())
        } else if (isCompleted) {
            NiceUtil.savePlayPosition(mContext, mUrl, 0)
        }
        // 退出全屏或小窗口
        exitFullScreen()
        exitTinyWindow()
        mCurrentMode = IVideoPlayer.MODE_NORMAL
        // 恢复控制器
        mController?.reset()
        LogUtil.d("release")

        mAudioManager?.abandonAudioFocus(null)
        mAudioManager = null
        //缓解当列表滑动到正在播放的item不可见时，释放会造成列表卡一下的问题
        Thread {
            aliPlayer?.release()
            aliPlayer = null
        }.start()
        if (isUseTextureView) {
            mContainer?.removeView(mTextureView)
            mTextureView = null
        } else {
            surfaceView = null
        }
        mCurrentState = IVideoPlayer.STATE_IDLE
    }
}