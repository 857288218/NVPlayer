package com.xiao.nicevideoplayer.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
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
import com.xiao.nicevideoplayer.NiceVideoPlayerController
import com.xiao.nicevideoplayer.NiceVideoPlayerManager
import com.xiao.nicevideoplayer.utils.LogUtil
import com.xiao.nicevideoplayer.utils.NiceUtil
import tv.danmaku.ijk.media.player.AndroidMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException

class IJKSurfaceVideoPlayer(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(mContext, attrs), INiceVideoPlayer, SurfaceHolder.Callback {
    private var mPlayerType = INiceVideoPlayer.TYPE_IJK
    private var mCurrentState = INiceVideoPlayer.STATE_IDLE
    private var mCurrentMode = INiceVideoPlayer.MODE_NORMAL
    private var mAudioManager: AudioManager? = null
    private var mMediaPlayer: IMediaPlayer? = null
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
        mController?.run {
            reset()
            setNiceVideoPlayer(this@IJKSurfaceVideoPlayer)
            mContainer?.addView(
                this, LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    fun setLooping(looping: Boolean) {
        isLoop = looping
    }

    // 该方法是使手机媒体静音，不是单纯的静音播放的视频
    fun setMute(mute: Boolean) {
        mAudioManager?.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0
        )
    }

    /**
     * 设置播放器类型
     *
     * @param playerType IjkPlayer or MediaPlayer.
     */
    fun setPlayerType(playerType: Int) {
        mPlayerType = playerType
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
        if (mMediaPlayer is IjkMediaPlayer) {
            (mMediaPlayer as IjkMediaPlayer).setSpeed(speed)
        } else {
            LogUtil.d("只有IjkPlayer才能设置播放速度")
        }
    }

    override fun start() {
        if (mCurrentState == INiceVideoPlayer.STATE_IDLE) {
            NiceVideoPlayerManager.instance()!!.currentNiceVideoPlayer = this
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
            mMediaPlayer!!.start()
            mCurrentState = INiceVideoPlayer.STATE_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onPlayingCallback?.invoke()
            LogUtil.d("STATE_PLAYING")
        } else if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED) {
            mMediaPlayer!!.start()
            mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPlayingCallback?.invoke()
            LogUtil.d("STATE_BUFFERING_PLAYING")
        } else if (mCurrentState == INiceVideoPlayer.STATE_COMPLETED || mCurrentState == INiceVideoPlayer.STATE_ERROR) {
            mMediaPlayer!!.reset()
            openMediaPlayer()
        } else {
            LogUtil.d("NiceVideoPlayer在mCurrentState == " + mCurrentState + "时不能调用restart()方法.")
        }
    }

    override fun pause() {
        if (mCurrentState == INiceVideoPlayer.STATE_PLAYING) {
            mMediaPlayer!!.pause()
            mCurrentState = INiceVideoPlayer.STATE_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onPauseCallback?.invoke()
            LogUtil.d("STATE_PAUSED")
        }
        if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PLAYING) {
            mMediaPlayer!!.pause()
            mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PAUSED
            mController?.onPlayStateChanged(mCurrentState)
            onBufferPauseCallback?.invoke()
            LogUtil.d("STATE_BUFFERING_PAUSED")
        }
    }

    override fun seekTo(pos: Long) {
        mMediaPlayer?.seekTo(pos)
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

    override fun getDuration(): Long = mMediaPlayer?.duration ?: 0

    override fun getCurrentPosition(): Long = mMediaPlayer?.currentPosition ?: 0

    override fun getBufferPercentage(): Int = mBufferPercentage

    override fun getSpeed(speed: Float) = if (mMediaPlayer is IjkMediaPlayer) {
        (mMediaPlayer as IjkMediaPlayer).getSpeed(speed)
    } else 0F

    override fun getTcpSpeed(): Long = if (mMediaPlayer is IjkMediaPlayer) {
        (mMediaPlayer as IjkMediaPlayer).tcpSpeed
    } else 0

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
            mMediaPlayer = when (mPlayerType) {
                INiceVideoPlayer.TYPE_NATIVE -> AndroidMediaPlayer()
                INiceVideoPlayer.TYPE_IJK -> IjkMediaPlayer()
                else -> {
                    IjkMediaPlayer()
//                    ((IjkMediaPlayer)mMediaPlayer).setOption(1, "analyzemaxduration", 100L);
//                    ((IjkMediaPlayer)mMediaPlayer).setOption(1, "probesize", 10240L);
//                    ((IjkMediaPlayer)mMediaPlayer).setOption(1, "flush_packets", 1L);
//                    ((IjkMediaPlayer)mMediaPlayer).setOption(4, "packet-buffering", 0L);
//                    ((IjkMediaPlayer)mMediaPlayer).setOption(4, "framedrop", 1L);
                }
            }
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
                setDataSource(mContext.applicationContext, Uri.parse(mUrl), mHeaders)
                setDisplay(surfaceHolder)
                prepareAsync()
                mCurrentState = INiceVideoPlayer.STATE_PREPARING
                mController?.onPlayStateChanged(mCurrentState)
                LogUtil.d("STATE_PREPARING")
            } catch (e: IOException) {
                e.printStackTrace()
                LogUtil.e("打开播放器发生错误", e)
            }
        }
    }

    private val mOnPreparedListener = IMediaPlayer.OnPreparedListener { mp: IMediaPlayer ->
        mCurrentState = INiceVideoPlayer.STATE_PREPARED
        //在视频准备完成后才能获取Duration，mMediaPlayer.getDuration();
        //当开始循环播放时，不会回调该方法
        mController?.onPlayStateChanged(mCurrentState)
        LogUtil.d("onPrepared ——> STATE_PREPARED")
        mp.start()
        //这里用else if的方式只能执行一个，由于seekTo是异步方法，可能导致，清晰度切换后，又切到continueFromLastPosition的情况
        if (skipToPosition != 0L) {
            // 跳到指定位置播放
            mp.seekTo(skipToPosition)
            skipToPosition = 0
        } else if (continueFromLastPosition) {
            // 从上次的保存位置播放
            val savedPlayPosition = NiceUtil.getSavedPlayPosition(mContext, mUrl)
            mp.seekTo(savedPlayPosition)
        }
    }

    private val mOnVideoSizeChangedListener =
        IMediaPlayer.OnVideoSizeChangedListener { _: IMediaPlayer, width, height, _, _ ->
            surfaceView?.adaptVideoSize(width, height)
            LogUtil.d("onVideoSizeChanged ——> width：$width， height：$height")
        }

    private val mOnCompletionListener = IMediaPlayer.OnCompletionListener {
        //设置了循环播放后，就不会再执行这个回调了
        mCurrentState = INiceVideoPlayer.STATE_COMPLETED
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
                mCurrentState = INiceVideoPlayer.STATE_ERROR
                mController?.onPlayStateChanged(mCurrentState)
                LogUtil.d("onError ——> STATE_ERROR ———— what：$what, extra: $extra")
            }
            true
        }

    private val mOnInfoListener = IMediaPlayer.OnInfoListener { _: IMediaPlayer, what, extra ->
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            // 播放器开始渲染，当开始循环播放时，不会回调该方法。回调到这里可能还是没有画面，还需要缓冲
            mCurrentState = INiceVideoPlayer.STATE_PLAYING
            mController?.onPlayStateChanged(mCurrentState)
            onVideoRenderStartCallback?.invoke()
            onPlayingCallback?.invoke()
            LogUtil.d("onInfo ——> MEDIA_INFO_VIDEO_RENDERING_START：STATE_PLAYING")
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            // MediaPlayer暂时不播放，以缓冲更多的数据
            if (mCurrentState == INiceVideoPlayer.STATE_PAUSED || mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED) {
                mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PAUSED
                onBufferPauseCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED")
            } else {
                mCurrentState = INiceVideoPlayer.STATE_BUFFERING_PLAYING
                onBufferPlayingCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING")
            }
            mController?.onPlayStateChanged(mCurrentState)
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            // 填充缓冲区后，MediaPlayer恢复播放/暂停
            if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PLAYING) {
                mCurrentState = INiceVideoPlayer.STATE_PLAYING
                mController?.onPlayStateChanged(mCurrentState)
                onPlayingCallback?.invoke()
                LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING")
            }
            if (mCurrentState == INiceVideoPlayer.STATE_BUFFERING_PAUSED) {
                mCurrentState = INiceVideoPlayer.STATE_PAUSED
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
        if (mCurrentMode == INiceVideoPlayer.MODE_FULL_SCREEN) return
        NiceVideoPlayerManager.instance()!!.setAllowRelease(false)
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
        contentView.addView(
            mContainer, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
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
    @SuppressLint("SourceLockedOrientationActivity")
    override fun exitFullScreen(): Boolean {
        if (mCurrentMode == INiceVideoPlayer.MODE_FULL_SCREEN) {
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
            this.addView(
                mContainer, LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
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
        // todo(rjq) 使用协程或线程池
        Thread { // 在主线程释放会卡顿
            mMediaPlayer?.release()
            mMediaPlayer = null
        }.start()
        surfaceHolder = null
        // 解决释放播放器黑一下,使用TextureView没有该问题
        Handler(Looper.getMainLooper()).post { mContainer?.removeView(surfaceView) }
        mCurrentState = INiceVideoPlayer.STATE_IDLE
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
        mCurrentMode = INiceVideoPlayer.MODE_NORMAL

        // 恢复控制器
        mController?.reset()

        // 释放播放器
        releasePlayer()

        //会引起列表卡顿
//        Runtime.getRuntime().gc();
    }
}