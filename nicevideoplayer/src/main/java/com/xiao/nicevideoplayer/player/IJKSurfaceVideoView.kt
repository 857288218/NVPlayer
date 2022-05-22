package com.xiao.nicevideoplayer.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xiao.nicevideoplayer.NiceSurfaceView
import com.xiao.nicevideoplayer.VideoViewController
import com.xiao.nicevideoplayer.NiceVideoPlayerManager
import com.xiao.nicevideoplayer.utils.LogUtil
import com.xiao.nicevideoplayer.utils.NiceUtil
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException

// 问题：
// 1.切后台暂停后，回到前台不主动播放，会黑屏。使用TextureView或AliPlayer没问题；
// 2.MEDIA_INFO_VIDEO_RENDERING_START回调不会马上显示画面会闪黑一下，使用TextureView或AliPlayer没问题
// 如果使用IJKPlayer建议使用TextuewView显示画面;该类不是最新代码不维护了，如果非要使用surfaceView，需要将该类同步IJKTexture的逻辑代码
class IJKSurfaceVideoView(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(mContext, attrs), IVideoPlayer, SurfaceHolder.Callback {

    private var mCurrentState = IVideoPlayer.STATE_IDLE
    private var mCurrentMode = IVideoPlayer.MODE_NORMAL

    private var mAudioManager: AudioManager? = null
    private var mMediaPlayer: IjkMediaPlayer? = null
    private var mContainer: FrameLayout? = null
    private var surfaceView: NiceSurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var mController: VideoViewController? = null
    private var mUrl: String? = null
    private var mRawId: Int? = null
    private var mHeaders: Map<String, String>? = null
    private var mBufferPercentage = 0
    private var continueFromLastPosition = true
    private var startToPosition: Long = 0
    private var isLoop = false
    private var isStartToPause = false

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
        mContainer = FrameLayout(mContext)
        this.addView(
            mContainer, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
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
            setVideoPlayer(this@IJKSurfaceVideoView)
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

    fun setLooping(looping: Boolean) {
        mMediaPlayer?.isLooping = looping
        isLoop = looping
    }

    // 该方法是使手机媒体静音，不是单纯的静音播放的视频
    override fun setMute(mute: Boolean) {
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
        (mMediaPlayer as IjkMediaPlayer).setSpeed(speed)
    }

    override fun start() {
        if (isIdle) {
            NiceVideoPlayerManager.instance()!!.currentNiceVideoPlayer = this
            initAudioManager()
            initMediaPlayer()
            initSurfaceView()
            addSurfaceView()
        } else if (isCompleted || isError || isPaused || isBufferingPaused) {
            restart()
        } else {
            LogUtil.d("NiceVideoPlayer只有在mCurrentState == STATE_IDLE时才能调用start方法.")
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
        start(pos)
    }

    override fun restart() {
        if (isPaused) {
            mMediaPlayer!!.start()
            mCurrentState = IVideoPlayer.STATE_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onPlayingCallback?.invoke()
            LogUtil.d("STATE_PLAYING")
        } else if (isBufferingPaused) {
            mMediaPlayer!!.start()
            mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPlayingCallback?.invoke()
            LogUtil.d("STATE_BUFFERING_PLAYING")
        } else if (isCompleted || isError) {
            // reset后会使播放器设置的属性清空，openMediaPlayer重新设置回调和属性
            // 播放完成可以直接mMediaPlayer!!.start()再次播放，这样不会回调prepared和render_start
            mMediaPlayer!!.reset()
            openMediaPlayer()
        } else {
            LogUtil.d("NiceVideoPlayer在mCurrentState == " + mCurrentState + "时不能调用restart()方法.")
        }
    }

    override fun pause() {
        if (isPlaying) {
            mMediaPlayer!!.pause()
            mCurrentState = IVideoPlayer.STATE_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onPauseCallback?.invoke()
            LogUtil.d("STATE_PAUSED")
        }
        if (isBufferingPaused) {
            mMediaPlayer!!.pause()
            mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPauseCallback?.invoke()
            LogUtil.d("STATE_BUFFERING_PAUSED")
        }
    }

    override fun seekTo(pos: Long) {
        if (mMediaPlayer == null) {
            start(pos)
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

    private fun initMediaPlayer() {
        if (mMediaPlayer == null) {
            // 这里player别设置属性，播放完成点重新播放会主动调用reset清空属性 然后openMediaPlayer在这里设置
            mMediaPlayer = IjkMediaPlayer()
        }
    }

    private fun setOptions() {
        if (mMediaPlayer != null) {
            mMediaPlayer?.setOption(
                IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "enable-accurate-seek",
                1
            )
            // 关闭准备后自动播放
            mMediaPlayer?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
            mMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
    }

    private fun initSurfaceView() {
        if (surfaceView == null) {
            surfaceView = NiceSurfaceView(mContext)
            surfaceView!!.holder.addCallback(this)
        }
    }

    private fun addSurfaceView() {
        mContainer!!.removeView(surfaceView)
        //添加完surfaceView后，会回调surfaceCreated
        mContainer!!.addView(
            surfaceView, 0, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (surfaceHolder == null) {
            surfaceHolder = holder
            openMediaPlayer()
        } else {
            //todo(rjq) 切后台暂停后，回到前台不主动播放，会黑屏。原因是activity onPause后，SurfaceView会被销毁，回调surfaceDestroyed()方法;
            // 使用TextureView没有该问题;AliPlayer也没问题
            //下面代码可以解决切后台暂停后，回到前台主动播放黑屏问题，但是不能解决上述问题
            mMediaPlayer!!.setDisplay(surfaceHolder)
        }
        LogUtil.d("surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LogUtil.d("surfaceDestroyed")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        LogUtil.d("surfaceChanged")
    }

    private fun openMediaPlayer() {
        // 屏幕常亮
        mContainer?.keepScreenOn = true
        mMediaPlayer?.run {
            setOptions()
            //设置是否循环播放
            isLooping = isLoop
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
                setDisplay(surfaceHolder)
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

    private val mOnPreparedListener = IMediaPlayer.OnPreparedListener { mp: IMediaPlayer ->
        mCurrentState = IVideoPlayer.STATE_PREPARED
        //在视频准备完成后才能获取Duration，mMediaPlayer.getDuration();
        //当开始循环播放时，不会回调该方法
        mController?.onPlayStateChanged(mCurrentState)
        onPreparedCallback?.invoke()
        LogUtil.d("onPrepared ——> STATE_PREPARED")

        mp.start()
        //这里用else if的方式只能执行一个，由于seekTo是异步方法，可能导致，清晰度切换后，又切到continueFromLastPosition的情况
        when {
            startToPosition != 0L -> {
                // 跳到指定位置播放
                seekTo(startToPosition)
                startToPosition = 0
            }
            continueFromLastPosition -> {
                // 从上次的保存位置播放
                val savedPlayPosition = NiceUtil.getSavedPlayPosition(mContext, mUrl)
                mp.seekTo(savedPlayPosition)
            }
        }
    }

    private val mOnVideoSizeChangedListener =
        IMediaPlayer.OnVideoSizeChangedListener { _: IMediaPlayer, width, height, _, _ ->
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
            // 播放器开始渲染，当循环播放时，不会回调MEDIA_INFO_VIDEO_RENDERING_START
            // MEDIA_INFO_VIDEO_RENDERING_START不会马上显示画面，会闪黑一下；使用TextureView或Aliplayer没有该问题
            // 使用startToPause时不显示画面黑屏
            LogUtil.d("onInfo ——> MEDIA_INFO_VIDEO_RENDERING_START：STATE_PLAYING")
            onVideoRenderStartCallback?.invoke()
            // 这里先回调mController的STATE_RENDERING_START，然后如果不是isStartToPause再回调STATE_PLAYING
            mCurrentState = IVideoPlayer.STATE_PLAYING
            mController?.onPlayStateChanged(IVideoPlayer.STATE_RENDERING_START)
            if (isStartToPause) {
                pause()
                isStartToPause = false
            } else {
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
            }
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            // MediaPlayer暂时不播放，以缓冲更多的数据；该回调可能早于MEDIA_INFO_VIDEO_RENDERING_START
            if (mCurrentState == IVideoPlayer.STATE_PAUSED || mCurrentState == IVideoPlayer.STATE_BUFFERING_PAUSED) {
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PAUSED
                onBufferPauseCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED")
            } else {
                mCurrentState = IVideoPlayer.STATE_BUFFERING_PLAYING
                onBufferPlayingCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING")
            }
            mController?.onPlayStateChanged(mCurrentState)
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            // 填充缓冲区后，MediaPlayer恢复播放/暂停
            if (mCurrentState == IVideoPlayer.STATE_BUFFERING_PLAYING) {
                mCurrentState = IVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING")
            }
            if (mCurrentState == IVideoPlayer.STATE_BUFFERING_PAUSED) {
                mCurrentState = IVideoPlayer.STATE_PAUSED
                mController?.onPlayStateChanged(mCurrentState)
                onPauseCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED")
            }
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            // 视频旋转了extra度，需要恢复
            surfaceView?.rotation = extra.toFloat()
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
        NiceVideoPlayerManager.instance()!!.setAllowRelease(false)
        // 隐藏ActionBar、状态栏，并横屏
        NiceUtil.hideActionBar(mContext)
        NiceUtil.scanForActivity(mContext).requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val contentView = NiceUtil.scanForActivity(mContext)
            .findViewById<ViewGroup>(android.R.id.content)
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
            NiceVideoPlayerManager.instance()!!.setAllowRelease(true)
            NiceUtil.showActionBar(mContext)
            NiceUtil.scanForActivity(mContext).requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val contentView = NiceUtil.scanForActivity(mContext)
                .findViewById<ViewGroup>(android.R.id.content)
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

    override fun releasePlayer() {
        mAudioManager?.abandonAudioFocus(null)
        mAudioManager = null
        // todo(rjq) 使用协程或线程池
        Thread { // 在主线程释放会卡顿
            mMediaPlayer?.release()
            mMediaPlayer = null
        }.start()
        surfaceHolder = null
        // 解决释放播放器黑一下,使用TextureView没有该问题
        Handler(Looper.getMainLooper()).post { mContainer?.removeView(surfaceView) }
        mCurrentState = IVideoPlayer.STATE_IDLE
    }

    override fun release() {
        // 保存播放位置
        if (isPlaying || isBufferingPlaying || isBufferingPaused || isPaused) {
            NiceUtil.savePlayPosition(mContext, mUrl, currentPosition)
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
        mCurrentMode = IVideoPlayer.MODE_NORMAL

        // 恢复控制器
        mController?.reset()

        // 释放播放器
        releasePlayer()
    }
}