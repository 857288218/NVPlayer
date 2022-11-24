package com.xiao.nicevideoplayer.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.xiao.nicevideoplayer.NiceSurfaceView
import com.xiao.nicevideoplayer.NiceTextureView
import com.xiao.nicevideoplayer.NiceVideoPlayerManager
import com.xiao.nicevideoplayer.R
import com.xiao.nicevideoplayer.VideoViewController
import com.xiao.nicevideoplayer.utils.LogUtil
import com.xiao.nicevideoplayer.utils.NiceUtil
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException

// 1.SurfaceView：切后台暂停后，回到前台不主动播放，会黑屏。使用TextureView或AliPlayer没问题；
//                暂停时切全屏或退出全屏会黑屏。使用TextureView或AliPlayer没问题；
// 2.MEDIA_INFO_VIDEO_RENDERING_START回调不会马上显示画面会闪黑一下，使用TextureView或AliPlayer、MediaPlayer没问题
// 如果使用IJKPlayer建议使用TextuewView显示画面

// IJKPlayer暂停时使用seekTo如果不主动播放更新画面很慢(info:MEDIA_INFO_VIDEO_SEEK_RENDERING_START回调慢),Aliplayer和MediaPlayer没问题
class IJKVideoView(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(mContext, attrs), IVideoPlayer, SurfaceTextureListener, SurfaceHolder.Callback {

    private var mCurrentState = IVideoPlayer.STATE_IDLE
    private var mCurrentMode = IVideoPlayer.MODE_NORMAL

    private var mAudioManager: AudioManager? = null
    private var mMediaPlayer: IjkMediaPlayer? = null
    private var mContainer: FrameLayout? = null
    private var mTextureView: NiceTextureView? = null
    private var mController: VideoViewController? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mSurface: Surface? = null
    private var surfaceView: NiceSurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var mUrl: String? = null
    private var mRawId: Int? = null
    private var mHeaders: Map<String, String>? = null
    private var mBufferPercentage = 0
    private var continueFromLastPosition = true
    private var startToPosition: Long = 0
    private var isLoop = false
    private var isMute = false
    private var isStartToPause = false
    private var isOnlyPrepare = false
    private var portraitSystemUiVisibility = -1

    @JvmField
    var isUseTextureView = true

    // 播放完成回调
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

    init {
        val types = context.obtainStyledAttributes(attrs, R.styleable.IJKVideoView)
        isUseTextureView = types.getBoolean(R.styleable.IJKVideoView_isUseTexture, true)
        types.recycle()

        mContainer = FrameLayout(mContext)
        this.addView(
            mContainer, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun setVideoBackgroundColor(bgColor: Int) {
        mContainer?.setBackgroundColor(bgColor)
    }

    fun getUrl() = mUrl

    override fun setUp(url: String, headers: Map<String, String>?) {
        mUrl = url
        mHeaders = headers
    }

    fun setUp(rawId: Int) {
        mRawId = rawId
    }

    fun setController(controller: VideoViewController?, isAdd: Boolean = true) {
        mContainer?.removeView(mController)
        mController = controller
        mController?.run {
            reset()
            setVideoPlayer(this@IJKVideoView)
            if (isAdd) {
                mContainer?.addView(
                    this, LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    }

    override fun setLooping(looping: Boolean) {
        mMediaPlayer?.isLooping = looping
        isLoop = looping
    }

    override fun setMute(mute: Boolean) {
        isMute = mute
        if (mute) {
            mMediaPlayer?.setVolume(0F, 0F)
        } else {
            mMediaPlayer?.setVolume(1F, 1F)
        }
    }

    /**
     * 是否从上一次的位置继续播放
     *
     * @param continueFromLastPosition true从上一次的位置继续播放
     */
    override fun continueFromLastPosition(continueFromLastPosition: Boolean) {
        this.continueFromLastPosition = continueFromLastPosition
    }

    override fun setSpeed(speed: Float) {
        mMediaPlayer?.setSpeed(speed)
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
            mMediaPlayer?.start()
            customStartToPos()
        } else if (isPreparing && isOnlyPrepare) {
            isOnlyPrepare = false
        } else {
            LogUtil.d("NiceVideoPlayer mCurrentState == ${mCurrentState}.不能调用start()")
        }
    }

    // 如果startToPosition ！= 0，在start前可以选择调整skipToPosition
    fun fixStartToPosition(delta: Long) {
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
        setMute(true)
        start(pos)
    }

    fun onlyPrepare() {
        isOnlyPrepare = true
        start()
    }

    override fun restart() {
        when {
            isPaused -> {
                mMediaPlayer!!.start()
                mCurrentState = IVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
                LogUtil.d("STATE_PLAYING")
            }
            isBufferingPaused -> {
                mMediaPlayer!!.start()
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onBufferPlayingCallback?.invoke()
                LogUtil.d("STATE_BUFFERING_PLAYING")
            }
            isError -> {
                mMediaPlayer!!.reset()
                start()
            }
            isCompleted -> {
                mController?.onPlayStateChanged(IVideoPlayer.STATE_PREPARED)
                onPreparedCallback?.invoke()
                mMediaPlayer!!.start()
                mController?.onPlayStateChanged(IVideoPlayer.STATE_RENDERING_START)
                onVideoRenderStartCallback?.invoke()
                mCurrentState = IVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(IVideoPlayer.STATE_PLAYING)
                onPlayingCallback?.invoke()
            }
            else -> {
                LogUtil.d("NiceVideoPlayer在mCurrentState == " + mCurrentState + "时不能调用restart()方法.")
            }
        }
    }

    // start后调用，切换另一个视频播放
    override fun playOtherVideo(videoPath: String, startPosition: Long, iaAutoPlay: Boolean) {
        mMediaPlayer?.run {
            setUp(videoPath, null)
            stop()
            this@IJKVideoView.reset()
            if (iaAutoPlay) {
                start(startPosition)
            } else {
                startToPause(startPosition)
            }
            // 不setSurface(mSurface)画面不会切换,AliVideoView、MediaVideoView不需要调用
            setSurface(mSurface ?: surfaceHolder?.surface)
        }
    }

    override fun pause() {
        if (isPlaying) {
            mMediaPlayer!!.pause()
            mCurrentState = IVideoPlayer.STATE_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onPauseCallback?.invoke()
            LogUtil.d("STATE_PAUSED")
        } else if (isBufferingPlaying) {
            mMediaPlayer!!.pause()
            mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPauseCallback?.invoke()
            LogUtil.d("STATE_BUFFERING_PAUSED")
        }
    }

    override fun seekTo(pos: Long) {
        if (mMediaPlayer == null) {
            LogUtil.d("seekTo需要在start后调用")
        } else {
            mMediaPlayer!!.seekTo(pos)
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

    override fun getDuration(): Long = mMediaPlayer?.duration ?: 0

    override fun getCurrentPosition(): Long = mMediaPlayer?.currentPosition ?: 0

    override fun getBufferPercentage(): Int = mBufferPercentage

    override fun getSpeed(speed: Float) = mMediaPlayer?.getSpeed(speed) ?: 0F

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

    private fun setOptions() {
        mMediaPlayer?.run {
            // 精准seek
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
            // 关闭prepared后自动播放
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
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

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        LogUtil.d("onSurfaceTextureAvailable")
        if (mSurfaceTexture == null) {
            mSurfaceTexture = surfaceTexture
            mSurface = Surface(surfaceTexture)
            mMediaPlayer?.setSurface(mSurface)
        } else {
            // 避免暂停时进入(退出)全屏没有画面；进入(退出)全屏会回调onSurfaceTextureAvailable
            mTextureView?.setSurfaceTexture(mSurfaceTexture!!)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        LogUtil.d("onSurfaceTextureSizeChanged")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        LogUtil.d("onSurfaceTextureDestroyed")
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    // 使用TextureView
    private fun addSurfaceView() {
        if (surfaceView == null) {
            surfaceView = NiceSurfaceView(mContext)
            surfaceView!!.holder.addCallback(this)

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
        surfaceHolder = holder
        mMediaPlayer?.setDisplay(holder)
        LogUtil.d("surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LogUtil.d("surfaceDestroyed")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        LogUtil.d("surfaceChanged")
    }

    private fun openMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = IjkMediaPlayer()
        }
        // 屏幕常亮
        mContainer?.keepScreenOn = true
        mMediaPlayer!!.run {
            setOptions()
            //设置是否循环播放
            isLooping = isLoop
            setMute(isMute)
            // 设置监听
            setOnPreparedListener(mOnPreparedListener)
            setOnVideoSizeChangedListener(mOnVideoSizeChangedListener)
            setOnCompletionListener(mOnCompletionListener)
            setOnErrorListener(mOnErrorListener)
            setOnInfoListener(mOnInfoListener)
            setOnBufferingUpdateListener(mOnBufferingUpdateListener)
            // 设置dataSource
            try {
                if (mRawId != null) {
                    val afd = resources.openRawResourceFd(mRawId!!)
                    val rawDataSourceProvider = IJKRawDataSourceProvider(afd)
                    setDataSource(rawDataSourceProvider)
                } else {
                    setDataSource(mContext.applicationContext, Uri.parse(mUrl), mHeaders)
                }
                prepareAsync()
                mCurrentState = IVideoPlayer.STATE_PREPARING
                mController?.onPlayStateChanged(mCurrentState)
                LogUtil.d("STATE_PREPARING")
            } catch (e: IOException) {
                e.printStackTrace()
                LogUtil.e("打开播放器发生错误", e)
            }
        }
    }

    private val mOnPreparedListener = IMediaPlayer.OnPreparedListener { mp ->
        mCurrentState = IVideoPlayer.STATE_PREPARED
        //在视频准备完成后才能获取Duration，mMediaPlayer.getDuration()
        mController?.onPlayStateChanged(mCurrentState)
        onPreparedCallback?.invoke()
        LogUtil.d("onPrepared ——> STATE_PREPARED")

        // seekTo只能在start后调用，start前调用seekTo无作用
        if (!isOnlyPrepare) {
            mp.start()
            customStartToPos()
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
        IMediaPlayer.OnVideoSizeChangedListener { _: IMediaPlayer, width, height, _, _ ->
            mTextureView?.adaptVideoSize(width, height)
            surfaceView?.adaptVideoSize(width, height)
            LogUtil.d("onVideoSizeChanged ——> width：$width， height：$height")
        }

    private val mOnCompletionListener = IMediaPlayer.OnCompletionListener {
        //设置了循环播放后，就不会再执行这个回调了
        mCurrentState = IVideoPlayer.STATE_COMPLETED
        mController?.onPlayStateChanged(mCurrentState)
        onCompletionCallback?.invoke()
        LogUtil.d("onCompletion ——> STATE_COMPLETED")
        // 清除屏幕常亮
        mContainer?.keepScreenOn = false
        // 重置当前播放进度
        NiceUtil.savePlayPosition(context, mUrl, 0)
    }

    private val mOnErrorListener =
        IMediaPlayer.OnErrorListener { _: IMediaPlayer, what, extra ->
            // 直播流播放时去调用mediaPlayer.getDuration会导致-38和-2147483648错误，忽略该错误
            if (what != -38 && what != -2147483648 && extra != -38 && extra != -2147483648) {
                mCurrentState = IVideoPlayer.STATE_ERROR
                mController?.onPlayStateChanged(mCurrentState)
                LogUtil.d("onError ——> STATE_ERROR ———— what：$what, extra: $extra")
            }
            true
        }

    private val mOnInfoListener = IMediaPlayer.OnInfoListener { _: IMediaPlayer, what, extra ->
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            // 播放器开始渲染,当循环播放时，不会回调MEDIA_INFO_VIDEO_RENDERING_START
            LogUtil.d("onInfo ——> MEDIA_INFO_VIDEO_RENDERING_START：STATE_PLAYING")
            onVideoRenderStartCallback?.invoke()
            // 这里先回调mController的STATE_RENDERING_START，然后如果不是isStartToPause再回调STATE_PLAYING
            mCurrentState = IVideoPlayer.STATE_PLAYING
            mController?.onPlayStateChanged(IVideoPlayer.STATE_RENDERING_START)
            if (isStartToPause) {
                pause()
                setMute(false)
                isStartToPause = false
            } else {
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
            }
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START) {
            LogUtil.d("onInfo ——> MEDIA_INFO_VIDEO_SEEK_RENDERING_START")
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            // MediaPlayer暂时不播放，以缓冲更多的数据；该回调可能早于MEDIA_INFO_VIDEO_RENDERING_START
            if (isPaused || isBufferingPaused) {
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
                onBufferPauseCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED")
            } else if (isPlaying || isBufferingPlaying) {
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
                onBufferPlayingCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING")
            }
            mController?.onPlayStateChanged(mCurrentState)
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            // 填充缓冲区后，MediaPlayer恢复播放/暂停
            if (isBufferingPlaying) {
                mCurrentState = IVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING")
            }
            if (isBufferingPaused) {
                mCurrentState = IVideoPlayer.STATE_PAUSED
                mController?.onPlayStateChanged(mCurrentState)
                onPauseCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED")
            }
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            // 视频旋转了extra度，需要恢复
            mTextureView?.rotation = extra.toFloat()
            LogUtil.d("视频旋转角度：$extra")
        } else if (what == IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE) {
            LogUtil.d("视频不能seekTo，为直播视频")
        } else {
            LogUtil.d("onInfo ——> what：$what")
        }
        true
    }

    private val mOnBufferingUpdateListener =
        IMediaPlayer.OnBufferingUpdateListener { _: IMediaPlayer, percent ->
            mBufferPercentage = percent
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
        val contentView =
            NiceUtil.scanForActivity(mContext).findViewById<ViewGroup>(android.R.id.content)
        if (isTinyWindow) {
            contentView.removeView(mContainer)
        } else {
            removeView(mContainer)
        }
        contentView.addView(
            mContainer, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
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
    @SuppressLint("SourceLockedOrientationActivity")
    override fun exitFullScreen(): Boolean {
        if (isFullScreen) {
            NiceVideoPlayerManager.instance().setAllowRelease(true)
            val activity = NiceUtil.scanForActivity(mContext)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // 恢复状态栏和导航栏
            activity.window.decorView.systemUiVisibility = portraitSystemUiVisibility
            val contentView =
                NiceUtil.scanForActivity(mContext).findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            this.addView(
                mContainer, LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
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
        val contentView =
            NiceUtil.scanForActivity(mContext).findViewById<ViewGroup>(android.R.id.content)
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
            val contentView =
                NiceUtil.scanForActivity(mContext).findViewById<ViewGroup>(android.R.id.content)
            contentView.removeView(mContainer)
            this.addView(
                mContainer, LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            mCurrentMode = IVideoPlayer.MODE_NORMAL
            mController?.onPlayModeChanged(mCurrentMode)
            LogUtil.d("MODE_NORMAL")
            return true
        }
        return false
    }

    override fun reset() {
        mMediaPlayer?.reset()
        mCurrentState = IVideoPlayer.STATE_IDLE
    }

    override fun release() {
        // 保存播放位置
        // if (isPlaying || isBufferingPlaying || isBufferingPaused || isPaused) {
        //     NiceUtil.savePlayPosition(mContext, mUrl, currentPosition)
        // } else if (isCompleted) {
        //     NiceUtil.savePlayPosition(mContext, mUrl, 0)
        // }
        // 退出全屏或小窗口
        exitFullScreen()
        exitTinyWindow()
        mCurrentMode = IVideoPlayer.MODE_NORMAL
        // 恢复控制器
        mController?.reset()
        LogUtil.d("release")

        mAudioManager?.abandonAudioFocus(null)
        mAudioManager = null
        Thread {
            mMediaPlayer?.release()
            mMediaPlayer = null
        }.start()
        if (isUseTextureView) {
            mContainer?.removeView(mTextureView)
            mSurface?.release()
            mSurface = null
            mSurfaceTexture?.release()
            mSurfaceTexture = null
            mTextureView = null
        } else {
            surfaceHolder = null
            surfaceView = null
        }
        mCurrentState = IVideoPlayer.STATE_IDLE
    }
}