package com.xiao.nicevideoplayer.player;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.aliyun.player.AliPlayer;
import com.aliyun.player.AliPlayerFactory;
import com.aliyun.player.IPlayer;
import com.aliyun.player.bean.ErrorInfo;
import com.aliyun.player.bean.InfoBean;
import com.aliyun.player.bean.InfoCode;
import com.aliyun.player.nativeclass.CacheConfig;
import com.aliyun.player.nativeclass.PlayerConfig;
import com.aliyun.player.source.UrlSource;
import com.xiao.nicevideoplayer.LogUtil;
import com.xiao.nicevideoplayer.NiceSurfaceView;
import com.xiao.nicevideoplayer.NiceUtil;
import com.xiao.nicevideoplayer.NiceVideoPlayerController;
import com.xiao.nicevideoplayer.NiceVideoPlayerManager;

import java.util.Map;

public class AliVideoPlayer extends FrameLayout
        implements INiceVideoPlayer,
        SurfaceHolder.Callback {

    private int mCurrentState = STATE_IDLE;
    private int mCurrentMode = MODE_NORMAL;

    private Context mContext;
    private AudioManager mAudioManager;
    private AliPlayer aliPlayer;
    private FrameLayout mContainer;
    private NiceSurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private NiceVideoPlayerController mController;
    private String mUrl;
    private Map<String, String> mHeaders;
    private int mBufferPercentage;
    private boolean continueFromLastPosition = true;
    private long skipToPosition;
    private boolean isLoop;
    private long currentPosition;

    public AliVideoPlayer(Context context) {
        this(context, null);
    }

    public AliVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    private void init() {
        mContainer = new FrameLayout(mContext);
        mContainer.setBackgroundColor(Color.BLACK);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mContainer, params);
    }

    public void setUp(String url, Map<String, String> headers) {
        mUrl = url;
        mHeaders = headers;
    }

    public void setController(NiceVideoPlayerController controller) {
        mContainer.removeView(mController);
        mController = controller;
        mController.reset();
        mController.setNiceVideoPlayer(this);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer.addView(mController, params);
    }

    public void setLooping(boolean looping) {
        isLoop = looping;
    }

    /**
     * 是否从上一次的位置继续播放
     *
     * @param continueFromLastPosition true从上一次的位置继续播放
     */
    @Override
    public void continueFromLastPosition(boolean continueFromLastPosition) {
        this.continueFromLastPosition = continueFromLastPosition;
    }

    @Override
    public void setSpeed(float speed) {
        aliPlayer.setSpeed(speed);
    }

    @Override
    public void start() {
        if (mCurrentState == STATE_IDLE) {
            NiceVideoPlayerManager.instance().setCurrentNiceVideoPlayer(this);
            initAudioManager();
            initMediaPlayer();
            initSurfaceView();
            addSurfaceView();
        } else {
            LogUtil.d("NiceVideoPlayer只有在mCurrentState == STATE_IDLE时才能调用start方法.");
        }
    }

    @Override
    public void start(long position) {
        skipToPosition = position;
        start();
    }

    @Override
    public void restart() {
        if (mCurrentState == STATE_PAUSED) {
            aliPlayer.start();
            mCurrentState = STATE_PLAYING;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("STATE_PLAYING");
        } else if (mCurrentState == STATE_BUFFERING_PAUSED) {
            aliPlayer.start();
            mCurrentState = STATE_BUFFERING_PLAYING;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("STATE_BUFFERING_PLAYING");
        } else if (mCurrentState == STATE_COMPLETED || mCurrentState == STATE_ERROR) {
            aliPlayer.reset();
            openMediaPlayer();
        } else {
            LogUtil.d("NiceVideoPlayer在mCurrentState == " + mCurrentState + "时不能调用restart()方法.");
        }
    }

    @Override
    public void pause() {
        if (mCurrentState == STATE_PLAYING) {
            aliPlayer.pause();
            mCurrentState = STATE_PAUSED;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("STATE_PAUSED");
        }
        if (mCurrentState == STATE_BUFFERING_PLAYING) {
            aliPlayer.pause();
            mCurrentState = STATE_BUFFERING_PAUSED;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("STATE_BUFFERING_PAUSED");
        }
    }

    @Override
    public void seekTo(long pos) {
        if (aliPlayer != null) {
            aliPlayer.seekTo(pos);
        }
    }

    @Override
    public void setVolume(int volume) {
        if (mAudioManager != null) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        }
    }

    @Override
    public boolean isIdle() {
        return mCurrentState == STATE_IDLE;
    }

    @Override
    public boolean isPreparing() {
        return mCurrentState == STATE_PREPARING;
    }

    @Override
    public boolean isPrepared() {
        return mCurrentState == STATE_PREPARED;
    }

    @Override
    public boolean isBufferingPlaying() {
        return mCurrentState == STATE_BUFFERING_PLAYING;
    }

    @Override
    public boolean isBufferingPaused() {
        return mCurrentState == STATE_BUFFERING_PAUSED;
    }

    @Override
    public boolean isPlaying() {
        return mCurrentState == STATE_PLAYING;
    }

    @Override
    public boolean isPaused() {
        return mCurrentState == STATE_PAUSED;
    }

    @Override
    public boolean isError() {
        return mCurrentState == STATE_ERROR;
    }

    @Override
    public boolean isCompleted() {
        return mCurrentState == STATE_COMPLETED;
    }

    @Override
    public boolean isFullScreen() {
        return mCurrentMode == MODE_FULL_SCREEN;
    }

    @Override
    public boolean isTinyWindow() {
        return mCurrentMode == MODE_TINY_WINDOW;
    }

    @Override
    public boolean isNormal() {
        return mCurrentMode == MODE_NORMAL;
    }

    @Override
    public int getMaxVolume() {
        if (mAudioManager != null) {
            return mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        return 0;
    }

    @Override
    public int getVolume() {
        if (mAudioManager != null) {
            return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        return 0;
    }

    @Override
    public long getDuration() {
        return aliPlayer != null ? aliPlayer.getDuration() : 0;
    }

    @Override
    public long getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public int getBufferPercentage() {
        return mBufferPercentage;
    }

    @Override
    public float getSpeed(float speed) {
        return aliPlayer.getSpeed();
    }

    @Override
    public long getTcpSpeed() {
//        if (mMediaPlayer instanceof IjkMediaPlayer) {
//            return ((IjkMediaPlayer) mMediaPlayer).getTcpSpeed();
//        }
        return 0;
    }

    private void initAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void initMediaPlayer() {
        if (aliPlayer == null) {
            aliPlayer = AliPlayerFactory.createAliPlayer(mContext);
            CacheConfig cacheConfig = new CacheConfig();
            //开启缓存功能
            cacheConfig.mEnable = true;
            //能够缓存的单个文件最大时长。超过此长度则不缓存
            cacheConfig.mMaxDurationS = 100;
            //缓存目录的位置
            cacheConfig.mDir = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            //缓存目录的最大大小。超过此大小，将会删除最旧的缓存文件
            cacheConfig.mMaxSizeMB = 200;
            //设置缓存配置给到播放器
            aliPlayer.setCacheConfig(cacheConfig);

            PlayerConfig config = aliPlayer.getConfig();
            // 最大缓冲区时长。单位ms。播放器每次最多加载这么长时间的缓冲数据。
            config.mMaxBufferDuration = 50000;
            //高缓冲时长。单位ms。当网络不好导致加载数据时，如果加载的缓冲时长到达这个值，结束加载状态。
            config.mHighBufferDuration = 3000;
            // 起播缓冲区时长。单位ms。这个时间设置越短，起播越快。也可能会导致播放之后很快就会进入加载状态。
            config.mStartBufferDuration = 500;
            aliPlayer.setConfig(config);
        }
    }

    private void initSurfaceView() {
        if (surfaceView == null) {
            surfaceView = new NiceSurfaceView(mContext);
            surfaceView.getHolder().addCallback(this);
        }
    }

    private void addSurfaceView() {
        mContainer.removeView(surfaceView);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        //添加完surfaceView后，会回调surfaceCreated
        mContainer.addView(surfaceView, 0, params);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (surfaceHolder == null) {
            surfaceHolder = holder;
            openMediaPlayer();
        } else {
            // activity onPause后，SurfaceView会被销毁，回调surfaceDestroyed()方法,
            // 回到前台会回调surfaceCreated，需要重新添加holder,否则没有画面
            aliPlayer.setDisplay(holder);
        }
        LogUtil.d("surfaceCreated");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.d("surfaceDestroyed");
        if (aliPlayer != null) {
            aliPlayer.setDisplay(null);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //解决切后台暂停后，回到前台不主动播放，会黑屏
        //用来刷新视频画面的。如果view的大小变化了，调用此方法将会更新画面大小，保证视频画面与View的变化一致。
        LogUtil.d("surfaceChanged");
        aliPlayer.redraw();
    }

    private void openMediaPlayer() {
        // 屏幕常亮
        mContainer.setKeepScreenOn(true);
        //设置是否循环播放
        aliPlayer.setLoop(isLoop);
        // 设置监听
        aliPlayer.setOnPreparedListener(mOnPreparedListener);
        aliPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
        aliPlayer.setOnCompletionListener(mOnCompletionListener);
        aliPlayer.setOnErrorListener(mOnErrorListener);
        aliPlayer.setOnRenderingStartListener(mOnRenderingStartListener);
        aliPlayer.setOnLoadingStatusListener(mOnLoadingStatusListener);
        aliPlayer.setOnInfoListener(mOnInfoListener);
        aliPlayer.setOnSeekCompleteListener(mOnSeekCompleteListener);
        // 设置dataSource
        UrlSource urlSource = new UrlSource();
        urlSource.setUri(mUrl);
        aliPlayer.setDataSource(urlSource);
        aliPlayer.setDisplay(surfaceHolder);
        aliPlayer.prepare();
        mCurrentState = STATE_PREPARING;
        mController.onPlayStateChanged(mCurrentState);
        LogUtil.d("STATE_PREPARING");
    }

    private IPlayer.OnPreparedListener mOnPreparedListener
            = new IPlayer.OnPreparedListener() {
        @Override
        public void onPrepared() {//自动播放的时候将不会回调onPrepared回调，而会回调onInfo回调。
            mCurrentState = STATE_PREPARED;
            //在视频准备完成后才能获取Duration，mMediaPlayer.getDuration();
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("onPrepared ——> STATE_PREPARED");
            aliPlayer.start();
            //这里用else if的方式只能执行一个，由于seekTo是异步方法，可能导致，清晰度切换后，又切到continueFromLastPosition的情况
            if (skipToPosition != 0) {
                // 跳到指定位置播放
                aliPlayer.seekTo(skipToPosition);
                skipToPosition = 0;
            } else if (continueFromLastPosition) {
                // 从上次的保存位置播放
                long savedPlayPosition = NiceUtil.getSavedPlayPosition(mContext, mUrl);
                aliPlayer.seekTo(savedPlayPosition);
            }
        }
    };

    private IPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener
            = new IPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(int width, int height) {
            surfaceView.adaptVideoSize(width, height);
            LogUtil.d("onVideoSizeChanged ——> width：" + width + "， height：" + height);
        }
    };

    private IPlayer.OnCompletionListener mOnCompletionListener
            = new IPlayer.OnCompletionListener() {
        @Override
        public void onCompletion() {  //设置了循环播放后，就不会再执行这个回调了
            mCurrentState = STATE_COMPLETED;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("onCompletion ——> STATE_COMPLETED");
            // 清除屏幕常亮
            mContainer.setKeepScreenOn(false);
            // 重置当前播放进度
            NiceUtil.savePlayPosition(getContext(), mUrl, 0);
        }
    };

    private IPlayer.OnErrorListener mOnErrorListener
            = new IPlayer.OnErrorListener() {
        @Override
        public void onError(ErrorInfo errorInfo) {
            //出错事件
            mCurrentState = STATE_ERROR;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("onError ——> STATE_ERROR");
        }
    };

    private IPlayer.OnRenderingStartListener mOnRenderingStartListener
            = new IPlayer.OnRenderingStartListener() {
        @Override
        public void onRenderingStart() {
            //首帧渲染显示事件
            mCurrentState = STATE_PLAYING;
            mController.onPlayStateChanged(mCurrentState);
            LogUtil.d("onRenderingStart");
        }
    };

    private IPlayer.OnLoadingStatusListener mOnLoadingStatusListener
            = new IPlayer.OnLoadingStatusListener() {
        @Override
        public void onLoadingBegin() {
            //缓冲开始
            if (mCurrentState == STATE_PAUSED || mCurrentState == STATE_BUFFERING_PAUSED) {
                mCurrentState = STATE_BUFFERING_PAUSED;
                LogUtil.d("onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED");
            } else {
                mCurrentState = STATE_BUFFERING_PLAYING;
                LogUtil.d("onLoadingBegin ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING");
            }
            mController.onPlayStateChanged(mCurrentState);
        }

        @Override
        public void onLoadingProgress(int percent, float v) {
            //缓冲进度
            mBufferPercentage = percent;
        }

        @Override
        public void onLoadingEnd() {
            //缓冲结束
            if (mCurrentState == STATE_BUFFERING_PLAYING) {
                mCurrentState = STATE_PLAYING;
                mController.onPlayStateChanged(mCurrentState);
                LogUtil.d("onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING");
            }
            if (mCurrentState == STATE_BUFFERING_PAUSED) {
                mCurrentState = STATE_PAUSED;
                mController.onPlayStateChanged(mCurrentState);
                LogUtil.d("onLoadingEnd ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED");
            }
        }
    };

    private IPlayer.OnInfoListener mOnInfoListener
            = new IPlayer.OnInfoListener() {
        @Override
        public void onInfo(InfoBean infoBean) {
            if (infoBean.getCode().getValue() == InfoCode.AutoPlayStart.getValue()) {
                //自动播放开始事件;注意：自动播放的时候将不会回调onPrepared回调，而会回调onInfo回调。
                mCurrentState = STATE_PREPARED;
                mController.onPlayStateChanged(mCurrentState);
                LogUtil.d("onInfo ——> AutoPlayStart");
            } else if (infoBean.getCode().getValue() == InfoCode.LoopingStart.getValue()) {
                //循环播放开始事件,不会回调onPrepared，onRenderingStart，onCompletion
                LogUtil.d("onInfo ——> LoopingStart");
            } else if (infoBean.getCode() == InfoCode.CurrentPosition) {
                //更新currentPosition
                currentPosition = infoBean.getExtraValue();
            }
        }
    };

    private IPlayer.OnSeekCompleteListener mOnSeekCompleteListener
            = new IPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete() {
            LogUtil.d("onSeekComplete" + getCurrentPosition());
            //这里取到的CurrentPosition可能也不是拖动后的最新的,onInfo获取到的seekTo后的position有延迟
        }
    };

    /**
     * 全屏，将mContainer(内部包含mTextureView和mController)从当前容器中移除，并添加到android.R.content中.
     * 切换横屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期
     */
    @Override
    public void enterFullScreen() {
        if (mCurrentMode == MODE_FULL_SCREEN) return;

        // 隐藏ActionBar、状态栏，并横屏
        NiceUtil.hideActionBar(mContext);
        NiceUtil.scanForActivity(mContext)
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        ViewGroup contentView = NiceUtil.scanForActivity(mContext)
                .findViewById(android.R.id.content);
        if (mCurrentMode == MODE_TINY_WINDOW) {
            contentView.removeView(mContainer);
        } else {
            this.removeView(mContainer);
        }
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        contentView.addView(mContainer, params);

        mCurrentMode = MODE_FULL_SCREEN;
        mController.onPlayModeChanged(mCurrentMode);
        LogUtil.d("MODE_FULL_SCREEN");
    }

    /**
     * 退出全屏，移除mTextureView和mController，并添加到非全屏的容器中。
     * 切换竖屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期.
     *
     * @return true退出全屏.
     */
    @Override
    public boolean exitFullScreen() {
        if (mCurrentMode == MODE_FULL_SCREEN) {
            NiceUtil.showActionBar(mContext);
            NiceUtil.scanForActivity(mContext)
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            ViewGroup contentView = NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);
            contentView.removeView(mContainer);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            this.addView(mContainer, params);

            mCurrentMode = MODE_NORMAL;
            mController.onPlayModeChanged(mCurrentMode);
            LogUtil.d("MODE_NORMAL");
            return true;
        }
        return false;
    }

    /**
     * 进入小窗口播放，小窗口播放的实现原理与全屏播放类似。
     */
    @Override
    public void enterTinyWindow() {
        if (mCurrentMode == MODE_TINY_WINDOW) return;
        removeView(mContainer);

        ViewGroup contentView =  NiceUtil.scanForActivity(mContext)
                .findViewById(android.R.id.content);
        // 小窗口的宽度为屏幕宽度的60%，长宽比默认为16:9，右边距、下边距为8dp。
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (NiceUtil.getScreenWidth(mContext) * 0.6f),
                (int) (NiceUtil.getScreenWidth(mContext) * 0.6f * 9f / 16f));
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.rightMargin = NiceUtil.dp2px(mContext, 8f);
        params.bottomMargin = NiceUtil.dp2px(mContext, 8f);
        contentView.addView(mContainer, params);

        mCurrentMode = MODE_TINY_WINDOW;
        mController.onPlayModeChanged(mCurrentMode);
        LogUtil.d("MODE_TINY_WINDOW");
    }

    /**
     * 退出小窗口播放
     */
    @Override
    public boolean exitTinyWindow() {
        if (mCurrentMode == MODE_TINY_WINDOW) {
            ViewGroup contentView = NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);
            contentView.removeView(mContainer);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            this.addView(mContainer, params);

            mCurrentMode = MODE_NORMAL;
            mController.onPlayModeChanged(mCurrentMode);
            LogUtil.d("MODE_NORMAL");
            return true;
        }
        return false;
    }

    @Override
    public void releasePlayer() {
        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocus(null);
            mAudioManager = null;
        }
        if (aliPlayer != null) {
            aliPlayer.release();
            aliPlayer = null;
        }
        surfaceHolder = null;
        mContainer.removeView(surfaceView);
        mCurrentState = STATE_IDLE;
    }

    @Override
    public void release() {
        // 保存播放位置
        if (isPlaying() || isBufferingPlaying() || isBufferingPaused() || isPaused()) {
            NiceUtil.savePlayPosition(mContext, mUrl, getCurrentPosition());
        } else if (isCompleted()) {
            NiceUtil.savePlayPosition(mContext, mUrl, 0);
        }
        // 退出全屏或小窗口
        if (isFullScreen()) {
            exitFullScreen();
        }
        if (isTinyWindow()) {
            exitTinyWindow();
        }
        mCurrentMode = MODE_NORMAL;

        // 释放播放器
        releasePlayer();

        // 恢复控制器
        if (mController != null) {
            mController.reset();
        }
        Runtime.getRuntime().gc();
    }
}
