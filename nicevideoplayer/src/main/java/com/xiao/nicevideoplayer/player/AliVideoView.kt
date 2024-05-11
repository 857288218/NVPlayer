package com.xiao.nicevideoplayer.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Build
import android.os.Build.VERSION
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
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
import com.cicada.player.utils.Logger
import com.xiao.nicevideoplayer.BaseApplication
import com.xiao.nicevideoplayer.NiceSurfaceView
import com.xiao.nicevideoplayer.NiceTextureView
import com.xiao.nicevideoplayer.R
import com.xiao.nicevideoplayer.VideoViewController
import com.xiao.nicevideoplayer.utils.NiceUtil
import java.io.File

// 问题：.不支持播放项目raw/assets文件夹中视频
class AliVideoView(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(mContext, attrs),
    IVideoPlayer,
    SurfaceHolder.Callback,
    TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "AliVideoView"
        private const val MAX_BUFFER_MEMORY_KB = 100 * 1024
        private const val MAX_CAPACITY_MB = 600L
        private const val EXPIRE_MIN = 30 * 24 * 60L
        private const val MAX_BUFFER_DURATION = 50000
        private const val HIGH_BUFFER_DURATION = 3000
        private const val START_BUFFER_DURATION = 500

        init {
            // 本地缓存 https://help.aliyun.com/document_detail/124714.html#p-gzq-d6a-r9r
            val context = BaseApplication.getApplication()
            // maxBufferMemoryKB - 5.4.7.1及以后版本已废弃，暂无作用
            AliPlayerGlobalSettings.enableLocalCache(
                true,
                MAX_BUFFER_MEMORY_KB,
                context.externalCacheDir?.absolutePath + File.separator + "AliPlayerVideoCache"
            )
            // expireMin - 5.4.7.1及以后版本已废弃，暂无作用
            AliPlayerGlobalSettings.setCacheFileClearConfig(EXPIRE_MIN, MAX_CAPACITY_MB, 0)
            Logger.getInstance(context).enableConsoleLog(true)
            Logger.getInstance(context).logLevel = Logger.LogLevel.AF_LOG_LEVEL_FATAL
        }
    }

    public var mCurrentState = IVideoPlayer.STATE_IDLE
    private var mCurrentMode = IVideoPlayer.MODE_NORMAL

    private var mAudioManager: AudioManager? = null
    private var aliPlayer: AliPlayer? = null
    private var mContainer: FrameLayout? = null
    private var mTextureView: NiceTextureView? = null
    private var surfaceView: NiceSurfaceView? = null
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

    // 播放视频时是否抢占音频焦点
    var isGainAudioFocus = true

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
    private val onCurrentPositionChangeCallbackList = ArrayList<(Long) -> Unit>()

    fun addOnCurrentPositionChangeListener(listener: (Long) -> Unit) {
        onCurrentPositionChangeCallbackList.add(listener)
    }

    fun removeOnCurrentPositionChangeListener(listener: (Long) -> Unit) {
        onCurrentPositionChangeCallbackList.remove(listener)
    }

    enum class ScaleMode {
        SCALE_ASPECT_FIT, SCALE_ASPECT_FILL, SCALE_TO_FILL
    }

    init {
        val types = context.obtainStyledAttributes(attrs, R.styleable.AliVideoView)
        isUseTextureView = types.getBoolean(R.styleable.AliVideoView_isUseTexture, false)
        // enableLocalCache = types.getBoolean(R.styleable.AliVideoView_enableLocalCache, false)
        types.recycle()

        mContainer = FrameLayout(mContext)
        this.addView(
            mContainer,
            LayoutParams(
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

    fun removeController() {
        mController?.let {
            mContainer?.removeView(
                it
            )
        }
        mContainer?.removeView(mController)
        Log.v("removeView", "viewSize:${mContainer?.childCount}")
    }

    fun getController() = mController

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

    fun setScaleMode(scaleMode: ScaleMode) {
        val aliScaleMode = when (scaleMode) {
            ScaleMode.SCALE_ASPECT_FILL -> IPlayer.ScaleMode.SCALE_ASPECT_FILL
            ScaleMode.SCALE_ASPECT_FIT -> IPlayer.ScaleMode.SCALE_ASPECT_FIT
            else -> IPlayer.ScaleMode.SCALE_TO_FILL
        }
        if (scaleMode.name != this.scaleMode.name) {
            aliPlayer?.scaleMode = aliScaleMode
            this.scaleMode = aliScaleMode
        }
    }

    // 支持0.5～5倍速的播放，通常按0.5的倍数来设置，例如0.5倍、1倍、1.5倍等
    override fun setSpeed(speed: Float) {
        aliPlayer?.speed = speed
    }

    @Suppress("ComplexCondition")
    override fun start() {
        if (isIdle()) {
            notPlayingPause = false
            initAudioManager()
            openMediaPlayer()
            if (isUseTextureView) {
                addTextureView()
            } else {
                addSurfaceView()
            }
        } else if (isPrepared()) {
            aliPlayer?.start()
            customStartToPos()
        } else {
            restart()
        }
    }

    override fun start(position: Long) {
        startToPosition = position
        start()
    }

    // 提前准备视频不自动播放 需要再调用start()播放；用于视频的提前准备减少直接调用start()时的准备视频时间
    fun onlyPrepare() {
        isOnlyPrepare = true
        start()
    }

    override fun restart() {
        when {
            isPaused() || isPlaying() || isBufferingPlaying() -> {
                Log.v(TAG, "STATE_PLAYING")
                aliPlayer?.start()
                mCurrentState = IVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
            }
            isBufferingPaused() -> {
                Log.v(TAG, "STATE_BUFFERING_PLAYING")
                aliPlayer?.start()
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onBufferPlayingCallback?.invoke()
            }
            isError() -> {
                // 将播放器设置的属性都清空,重新设置打开
                reset()
                start()
            }
            isCompleted() -> {
                mCurrentState = IVideoPlayer.STATE_PREPARING
                mController?.onPlayStateChanged(mCurrentState)
                aliPlayer?.prepare()
            }
            isPreparing() -> {
                Log.v(TAG, "restart()：currentState=Preparing, change notPlayingPause = false")
                isOnlyPrepare = false
                notPlayingPause = false
            }
            else -> Log.v(TAG, "AliVideoView在mCurrentState == $mCurrentState 时不能调用restart()")
        }
    }

    override fun pause() {
        if (isPlaying()) {
            Log.v(TAG, "STATE_PAUSED")
            aliPlayer?.pause()
            mCurrentState = IVideoPlayer.STATE_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onPauseCallback?.invoke()
        } else if (isBufferingPlaying()) {
            Log.v(TAG, "STATE_BUFFERING_PAUSED")
            aliPlayer?.pause()
            mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPauseCallback?.invoke()
        } else if (isIdle() || isPreparing()) {
            notPlayingPause = true
        } else if (isPrepared()) {
            // mOnPreparedListener中pause了
            mCurrentState = IVideoPlayer.STATE_PLAYING
            pause()
        }
    }

    override fun seekTo(pos: Long) {
        if (aliPlayer == null) {
            Log.v(TAG, "seekTo需要在start后调用")
        } else {
            Log.v(TAG, "seekTo:$pos")
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
            if (isGainAudioFocus) {
                mAudioManager!!.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun setConfig() {
        if (aliPlayer != null) {
            val config = aliPlayer!!.config
            config?.apply {
                // 是否针对播放的URL开启本地缓存，默认值为true。当AliPlayerGlobalSettings处的本地缓开启时，且同时开启此处的本地缓存，
                // 该URL的本地缓存才会生效；若此处设置为false，则关闭该URL的本地缓存。
                mEnableLocalCache = enableLocalCache

                // 配置缓冲区
                // 最大缓冲区时长。单位ms。播放器每次最多加载这么长时间的缓冲数据。
                mMaxBufferDuration = MAX_BUFFER_DURATION
                // 高缓冲时长。单位ms。当网络不好导致加载数据时，如果加载的缓冲时长到达这个值，结束加载状态。
                mHighBufferDuration = HIGH_BUFFER_DURATION
                // 起播缓冲区时长。单位ms。这个时间设置越短，起播越快。也可能会导致播放之后很快就会进入加载状态。
                mStartBufferDuration = START_BUFFER_DURATION

                // 设置HTTP Header
                val headers = arrayOfNulls<String>((mHeaders?.size ?: 0) + 1)
                if ((mHeaders?.size ?: 0) > 0) {
                    var i = 0
                    mHeaders!!.forEach { entry ->
                        headers[++i] = "${entry.key}:${entry.value}"
                    }
                }
                customHeaders = headers

                // 设置UserAgent
                if (userAgent.isNotEmpty()) {
                    mUserAgent = userAgent
                }
                aliPlayer!!.config = this
            }
        }
    }

    // 使用SurfaceView
    private fun addSurfaceView() {
        if (surfaceView == null) {
            surfaceView = NiceSurfaceView(mContext)
            surfaceView?.holder?.addCallback(this)

            mContainer?.removeView(surfaceView)
            // 添加完surfaceView后，会回调surfaceCreated
            mContainer?.addView(
                surfaceView,
                0,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        aliPlayer?.setSurface(holder.surface)
        Log.v(TAG, "surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.v(TAG, "surfaceDestroyed")
        aliPlayer?.setSurface(null)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // 解决切后台暂停后，回到前台不主动播放，会黑屏
        // 用来刷新视频画面的。如果view的大小变化了，调用此方法将会更新画面大小，保证视频画面与View的变化一致。
        aliPlayer?.surfaceChanged()
        Log.v(TAG, "surfaceChanged")
    }

    // 使用TextureView
    private fun addTextureView() {
        if (mTextureView == null) {
            mTextureView = NiceTextureView(mContext)
            mTextureView!!.surfaceTextureListener = this

            mContainer?.removeView(mTextureView)
            mContainer?.addView(
                mTextureView,
                0,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.v(TAG, "onSurfaceTextureAvailable")
        aliPlayer?.setSurface(Surface(surface))
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        aliPlayer?.surfaceChanged()
        Log.v(TAG, "onSurfaceTextureSizeChanged")
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.v(TAG, "onSurfaceTextureDestroyed")
        aliPlayer?.setSurface(null)
        return false
    }

    private fun openMediaPlayer() {
        if (aliPlayer == null) {
            aliPlayer = AliPlayerFactory.createAliPlayer(mContext)
        }
        // 屏幕常亮
        mContainer?.keepScreenOn = true
        aliPlayer?.run {
            setConfig()
            this@AliVideoView.setLooping(isLooping)
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
            Log.v(TAG, "STATE_PREPARING")
        }
    }

    private val mOnPreparedListener = IPlayer.OnPreparedListener {
        mCurrentState = IVideoPlayer.STATE_PREPARED
        mController?.onPlayStateChanged(mCurrentState)
        onPreparedCallback?.invoke()
        Log.v(TAG, "onPrepared ——> STATE_PREPARED")
        // 不设置autoPlay Prepared后不自动播放 && aliPlayer.start()前seekTo也不会播放只是定位到指定位置
        if (!isOnlyPrepare) {
            if (!isStartToPause) {
                aliPlayer?.start()
            }
            customStartToPos()
            if (notPlayingPause) {
                Log.v(TAG, "onPrepared ——> notPlayingPause=true, so invoke pause()")
                // 这里mCurrentState = IVideoPlayer.STATE_PLAYING就是为了满足pause()条件
                mCurrentState = IVideoPlayer.STATE_PLAYING
                pause()
                notPlayingPause = false
            }
        }
        isOnlyPrepare = false
    }

    private fun customStartToPos() {
        // 这里用else if的方式只能执行一个，由于seekTo是异步方法，可能导致，清晰度切换后，又切到continueFromLastPosition的情况
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
            Log.v(TAG, "onVideoSizeChanged ——> width：$width， height：$height")
            onVideoSizeChangedListener?.invoke(width, height)
        }
    private val mOnCompletionListener = IPlayer.OnCompletionListener {
        // 设置了循环播放后，就不会再执行这个回调了
        Log.v(TAG, "onCompletion ——> STATE_COMPLETED")
        mCurrentState = IVideoPlayer.STATE_COMPLETED
        mController?.onPlayStateChanged(mCurrentState)
        onCompletionCallback?.invoke()
        // 清除屏幕常亮
        mContainer?.keepScreenOn = false
        // 重置当前播放进度
        NiceUtil.savePlayPosition(context, mUrl, 0)
    }
    private val mOnErrorListener = IPlayer.OnErrorListener {
        // 出错事件
        mCurrentState = IVideoPlayer.STATE_ERROR
        mController?.onPlayStateChanged(mCurrentState)
        aliPlayer?.stop()
        Log.v(TAG, "onError ——> STATE_ERROR: ${it.code},${it.extra},${it.msg}")
    }
    private val mOnRenderingStartListener = OnRenderingStartListener {
        // 首帧渲染显示事件,循环播放时不会回调onRenderingStart
        Log.v(TAG, "onRenderingStart")
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
                // 缓冲开始, 可能还没播放画面就开始缓冲
                if (isPaused() || isBufferingPaused()) {
                    mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
                    onBufferPauseCallback?.invoke()
                    Log.v(TAG, "onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED")
                } else {
                    mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
                    onBufferPlayingCallback?.invoke()
                    Log.v(TAG, "onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING")
                }
                mController?.onPlayStateChanged(mCurrentState)
            }

            override fun onLoadingProgress(percent: Int, v: Float) {
                // 缓冲进度
                mBufferPercentage = percent
            }

            override fun onLoadingEnd() {
                // 缓冲结束
                if (isBufferingPlaying()) {
                    Log.v(TAG, "onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING")
                    mCurrentState = IVideoPlayer.STATE_PLAYING
                    mController?.onPlayStateChanged(mCurrentState)
                    onPlayingCallback?.invoke()
                }
                if (isBufferingPaused()) {
                    Log.v(TAG, "onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED")
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
                Log.v(TAG, "onInfo ——> AutoPlayStart")
            }
            infoBean.code.value == InfoCode.LoopingStart.value -> {
                // 循环播放开始事件,不会回调onCompletion,onPrepared,onRenderingStart
                Log.v(TAG, "onInfo ——> LoopingStart")
            }
            infoBean.code == InfoCode.CurrentPosition -> {
                // 更新currentPosition
                currentPosition = infoBean.extraValue
                mController?.updateProgress()
                onCurrentPositionChangeCallbackList.forEach { listener ->
                    listener(currentPosition)
                }
            }
//            else -> JaLog.v(TAG, "onInfo ——> code：${infoBean.code}, msg: ${infoBean.extraMsg}", isUpload = false)
        }
    }
    private val mOnSeekCompleteListener = IPlayer.OnSeekCompleteListener {
        Log.v(TAG, "onSeekComplete" + getCurrentPosition())
        // 这里取到的CurrentPosition可能也不是拖动后的最新的,onInfo获取到的seekTo后的position有延迟
    }

    /**
     * 全屏，将mContainer(内部包含mTextureView和mController)从当前容器中移除，并添加到android.R.content中.
     * 切换横屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期
     */
    override fun enterFullScreen() {
        if (isFullScreen()) return
        val activity = NiceUtil.scanForActivity(mContext)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // 解决横屏时刘海处变为黑条问题
        if (VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes = activity.window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
//        NiceUtil.hideActionBar(mContext)
        portraitSystemUiVisibility = activity.window.decorView.systemUiVisibility
        // 全屏显示，隐藏状态栏和导航栏，拉出状态栏和导航栏显示一会儿后消失
        NiceUtil.scanForActivity(mContext).window.decorView.systemUiVisibility =
            (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
        if (isTinyWindow()) {
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
        Log.v(TAG, "MODE_FULL_SCREEN")
    }

    /**
     * 退出全屏，移除mTextureView和mController，并添加到非全屏的容器中。
     * 切换竖屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期.
     *
     * @return true退出全屏.
     */
    override fun exitFullScreen(): Boolean {
        if (isFullScreen()) {
            val activity = NiceUtil.scanForActivity(mContext)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // 恢复状态栏和导航栏
            activity.window.decorView.systemUiVisibility = portraitSystemUiVisibility
//            NiceUtil.showActionBar(mContext)
            val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.addView(mContainer, params)
            mCurrentMode = IVideoPlayer.MODE_NORMAL
            mController?.onPlayModeChanged(mCurrentMode)
            Log.v(TAG, "MODE_NORMAL")
            return true
        }
        return false
    }

    /**
     * 进入小窗口播放，小窗口播放的实现原理与全屏播放类似。
     */
    override fun enterTinyWindow() {
    }

    /**
     * 退出小窗口播放
     */
    override fun exitTinyWindow(): Boolean {
        return false
    }

    override fun reset() {
        aliPlayer?.reset()
        mCurrentState = IVideoPlayer.STATE_IDLE
    }

    override fun release() {
        // 保存播放位置
//        if (isPlaying || isBufferingPlaying || isBufferingPaused || isPaused) {
//            NiceUtil.savePlayPosition(mContext, mUrl, getCurrentPosition())
//        } else if (isCompleted) {
//            NiceUtil.savePlayPosition(mContext, mUrl, 0)
//        }
        // 退出全屏或小窗口
        exitFullScreen()
        exitTinyWindow()
        mCurrentMode = IVideoPlayer.MODE_NORMAL
        // 恢复控制器
        mController?.reset()
        Log.v(TAG, "release")

        mAudioManager?.abandonAudioFocus(null)
        mAudioManager = null
        aliPlayer?.release()
        aliPlayer = null
        if (isUseTextureView) {
            mContainer?.removeView(mTextureView)
            mTextureView = null
        } else {
            surfaceView = null
        }
        mCurrentState = IVideoPlayer.STATE_IDLE
        notPlayingPause = false
    }
}
