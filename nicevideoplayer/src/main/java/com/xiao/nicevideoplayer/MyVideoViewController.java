package com.xiao.nicevideoplayer;

import com.xiao.nicevideoplayer.player.AliVideoView;
import com.xiao.nicevideoplayer.player.IJKVideoView;
import com.xiao.nicevideoplayer.player.IVideoPlayer;
import com.xiao.nicevideoplayer.utils.NiceUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.DrawableRes;

/**
 * 仿腾讯视频热点列表页播放器控制器.
 */
public class MyVideoViewController
        extends VideoViewController
        implements View.OnClickListener,
                   SeekBar.OnSeekBarChangeListener,
                   ChangeClarityDialog.OnClarityChangedListener {

    private final Context mContext;
    private ImageView mImage;
    private ImageView mCenterStart;

    private LinearLayout mTop;
    private ImageView mBack;
    private TextView mTitle;
    private LinearLayout mBatteryTime;
    private ImageView mBattery;
    private TextView mTime;

    private LinearLayout mBottom;
    private ImageView mRestartPause;
    private TextView mPosition;
    private TextView mDuration;
    private SeekBar mSeek;
    private TextView mClarity;
    private ImageView mFullScreen;

    private TextView mLength;
    private TextView mMute, tvChangeVideo;
    private LinearLayout mLoading;
    private TextView mLoadText;

    private LinearLayout mChangePositon;
    private TextView mChangePositionCurrent;
    private ProgressBar mChangePositionProgress;

    private LinearLayout mChangeBrightness;
    private ProgressBar mChangeBrightnessProgress;

    private LinearLayout mChangeVolume;
    private ProgressBar mChangeVolumeProgress;

    private LinearLayout mError;
    private TextView mRetry;

    private LinearLayout mCompleted;
    private TextView mReplay;
    private TextView mShare;

    private boolean topBottomVisible;
    private CountDownTimer mDismissTopBottomCountDownTimer;

    private List<Clarity> clarities;
    private int defaultClarityIndex;

    private ChangeClarityDialog mClarityDialog;

    private boolean hasRegisterBatteryReceiver; // 是否已经注册了电池广播
    private boolean isMute;

    public MyVideoViewController(Context context) {
        super(context);
        mContext = context;
        init();
    }

    private void init() {
        LayoutInflater.from(mContext).inflate(R.layout.tx_video_palyer_controller, this, true);

        mCenterStart = findViewById(R.id.center_start);
        mImage = findViewById(R.id.image);
        mTop = findViewById(R.id.top);
        mBack = findViewById(R.id.back);
        mTitle = findViewById(R.id.title);
        mBatteryTime = findViewById(R.id.battery_time);
        mBattery = findViewById(R.id.battery);
        mTime = findViewById(R.id.time);
        mBottom = findViewById(R.id.bottom);
        mRestartPause = findViewById(R.id.restart_or_pause);
        mPosition = findViewById(R.id.position);
        mDuration = findViewById(R.id.duration);
        mSeek = findViewById(R.id.seek);
        mFullScreen = findViewById(R.id.full_screen);
        mClarity = findViewById(R.id.clarity);
        mLength = findViewById(R.id.length);
        mLoading = findViewById(R.id.loading);
        mLoadText = findViewById(R.id.load_text);
        mChangePositon = findViewById(R.id.change_position);
        mChangePositionCurrent = findViewById(R.id.change_position_current);
        mChangePositionProgress = findViewById(R.id.change_position_progress);
        mChangeBrightness = findViewById(R.id.change_brightness);
        mChangeBrightnessProgress = findViewById(R.id.change_brightness_progress);
        mChangeVolume = findViewById(R.id.change_volume);
        mChangeVolumeProgress = findViewById(R.id.change_volume_progress);
        mError = findViewById(R.id.error);
        mRetry = findViewById(R.id.retry);
        mCompleted = findViewById(R.id.completed);
        mReplay = findViewById(R.id.replay);
        mShare = findViewById(R.id.share);
        mMute = findViewById(R.id.tv_mute);
        tvChangeVideo = findViewById(R.id.tv_change_video);

        tvChangeVideo.setOnClickListener(this);
        mMute.setOnClickListener(this);
        mCenterStart.setOnClickListener(this);
        mBack.setOnClickListener(this);
        mRestartPause.setOnClickListener(this);
        mFullScreen.setOnClickListener(this);
        mClarity.setOnClickListener(this);
        mRetry.setOnClickListener(this);
        mReplay.setOnClickListener(this);
        mShare.setOnClickListener(this);
        mSeek.setOnSeekBarChangeListener(this);
        this.setOnClickListener(this);
    }

    public void setTitle(String title) {
        mTitle.setText(title);
    }

    public ImageView imageView() {
        return mImage;
    }

    public void setImage(@DrawableRes int resId) {
        mImage.setImageResource(resId);
    }

    public void setLength(long length) {
        mLength.setText(NiceUtil.formatTime(length));
    }

    @Override
    public void setVideoPlayer(IVideoPlayer niceVideoPlayer) {
        super.setVideoPlayer(niceVideoPlayer);
        // 给播放器配置视频链接地址
        if (clarities != null && clarities.size() > 1 && mNiceVideoPlayer != null) {
            mNiceVideoPlayer.setUp(clarities.get(defaultClarityIndex).getVideoUrl(), null);
        }
    }

    /**
     * 设置清晰度
     *
     * @param clarities 清晰度及链接
     */
    public void setClarity(List<Clarity> clarities, int defaultClarityIndex) {
        if (clarities != null && clarities.size() > 1) {
            this.clarities = clarities;
            this.defaultClarityIndex = defaultClarityIndex;

            List<String> clarityGrades = new ArrayList<>();
            for (Clarity clarity : clarities) {
                clarityGrades.add(clarity.getGrade() + " " + clarity.getP());
            }
            mClarity.setText(clarities.get(defaultClarityIndex).getGrade());
            // 初始化切换清晰度对话框
            mClarityDialog = new ChangeClarityDialog(mContext);
            mClarityDialog.setClarityGrade(clarityGrades, defaultClarityIndex);
            mClarityDialog.setOnClarityCheckedListener(this);
            // 给播放器配置视频链接地址
            if (mNiceVideoPlayer != null) {
                mNiceVideoPlayer.setUp(clarities.get(defaultClarityIndex).getVideoUrl(), null);
            }
        }
    }

    @Override
    public void onPlayStateChanged(int playState) {
        switch (playState) {
            case IVideoPlayer.STATE_IDLE:
                break;
            case IVideoPlayer.STATE_PREPARING:
                mLoading.setVisibility(View.VISIBLE);
                mLoadText.setText("正在准备...");
                mError.setVisibility(View.GONE);
                mCompleted.setVisibility(View.GONE);
                mTop.setVisibility(View.GONE);
                mBottom.setVisibility(View.GONE);
                mLength.setVisibility(View.GONE);
                if (!(mNiceVideoPlayer instanceof AliVideoView)
                        && !(mNiceVideoPlayer instanceof IJKVideoView)) {
                    mCenterStart.setVisibility(View.GONE);
                }
                mCenterStart.setVisibility(View.GONE);
                break;
            case IVideoPlayer.STATE_PREPARED:
                mLoading.setVisibility(View.GONE);
                mCompleted.setVisibility(View.GONE);
                if (!(mNiceVideoPlayer instanceof AliVideoView)) {
                    startUpdateProgressTimer();
                }
                break;
            case IVideoPlayer.STATE_RENDERING_START:
                mCenterStart.setVisibility(View.GONE);
                // 首帧渲染显示再隐藏封面图
                mImage.setVisibility(View.GONE);
                break;
            case IVideoPlayer.STATE_PLAYING:
//                mImage.setVisibility(View.GONE);
                mLoading.setVisibility(View.GONE);
                mRestartPause.setImageResource(R.drawable.ic_player_pause);
                startDismissTopBottomTimer();
                break;
            case IVideoPlayer.STATE_PAUSED:
                mLoading.setVisibility(View.GONE);
                mRestartPause.setImageResource(R.drawable.ic_player_start);
                cancelDismissTopBottomTimer();
                break;
            case IVideoPlayer.STATE_BUFFERING_PLAYING:
                mLoading.setVisibility(View.VISIBLE);
                mRestartPause.setImageResource(R.drawable.ic_player_pause);
                mLoadText.setText("正在缓冲...");
                startDismissTopBottomTimer();
                break;
            case IVideoPlayer.STATE_BUFFERING_PAUSED:
                mLoading.setVisibility(View.VISIBLE);
                mRestartPause.setImageResource(R.drawable.ic_player_start);
                mLoadText.setText("正在缓冲...");
                cancelDismissTopBottomTimer();
                break;
            case IVideoPlayer.STATE_ERROR:
                cancelUpdateProgressTimer();
                setTopBottomVisible(false);
                mTop.setVisibility(View.VISIBLE);
                mError.setVisibility(View.VISIBLE);
                break;
            case IVideoPlayer.STATE_COMPLETED:
                cancelUpdateProgressTimer();
                setTopBottomVisible(false);
//                mImage.setVisibility(View.VISIBLE);  //播放完成就停在最后一帧
                mCompleted.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onPlayModeChanged(int playMode) {
        switch (playMode) {
            case IVideoPlayer.MODE_NORMAL:
                mBack.setVisibility(View.GONE);
                mFullScreen.setImageResource(R.drawable.ic_player_enlarge);
                mFullScreen.setVisibility(View.VISIBLE);
                mClarity.setVisibility(View.GONE);
                mBatteryTime.setVisibility(View.GONE);
                if (hasRegisterBatteryReceiver) {
                    mContext.unregisterReceiver(mBatterReceiver);
                    hasRegisterBatteryReceiver = false;
                }
                break;
            case IVideoPlayer.MODE_FULL_SCREEN:
                mBack.setVisibility(View.VISIBLE);
                mFullScreen.setVisibility(View.GONE);
                mFullScreen.setImageResource(R.drawable.ic_player_shrink);
                if (clarities != null && clarities.size() > 1) {
                    mClarity.setVisibility(View.VISIBLE);
                }
                mBatteryTime.setVisibility(View.VISIBLE);
                if (!hasRegisterBatteryReceiver) {
                    mContext.registerReceiver(mBatterReceiver,
                                              new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    hasRegisterBatteryReceiver = true;
                }
                break;
            case IVideoPlayer.MODE_TINY_WINDOW:
                mBack.setVisibility(View.VISIBLE);
                mClarity.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * 电池状态即电量变化广播接收器
     */
    private final BroadcastReceiver mBatterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                                            BatteryManager.BATTERY_STATUS_UNKNOWN);
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                // 充电中
                mBattery.setImageResource(R.drawable.battery_charging);
            } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
                // 充电完成
                mBattery.setImageResource(R.drawable.battery_full);
            } else {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
                int percentage = (int) (((float) level / scale) * 100);
                if (percentage <= 10) {
                    mBattery.setImageResource(R.drawable.battery_10);
                } else if (percentage <= 20) {
                    mBattery.setImageResource(R.drawable.battery_20);
                } else if (percentage <= 50) {
                    mBattery.setImageResource(R.drawable.battery_50);
                } else if (percentage <= 80) {
                    mBattery.setImageResource(R.drawable.battery_80);
                } else if (percentage <= 100) {
                    mBattery.setImageResource(R.drawable.battery_100);
                }
            }
        }
    };

    @Override
    public void reset() {
        topBottomVisible = false;
        cancelUpdateProgressTimer();
        cancelDismissTopBottomTimer();
        mSeek.setProgress(0);
        mSeek.setSecondaryProgress(0);

        mCenterStart.setVisibility(View.VISIBLE);
        mImage.setVisibility(View.VISIBLE);

        mBottom.setVisibility(View.GONE);
        mFullScreen.setImageResource(R.drawable.ic_player_enlarge);

        mLength.setVisibility(View.VISIBLE);

        mTop.setVisibility(View.VISIBLE);
        mBack.setVisibility(View.GONE);

        mLoading.setVisibility(View.GONE);
        mError.setVisibility(View.GONE);
        mCompleted.setVisibility(View.GONE);
    }

    /**
     * 尽量不要在onClick中直接处理控件的隐藏、显示及各种UI逻辑。 UI相关的逻辑都尽量到{@link #onPlayStateChanged}和{@link
     * #onPlayModeChanged}中处理.
     */
    @Override
    public void onClick(View v) {
        if (mNiceVideoPlayer != null) {
            if (v == tvChangeVideo) {
                mNiceVideoPlayer.playOtherVideo(
                        "http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-03_13-02-41.mp4", 0);
            } else if (v == mMute) {
                (mNiceVideoPlayer).setMute(!isMute);
                isMute = !isMute;
            } else if (v == mCenterStart) {
                NiceVideoPlayerManager.instance().setCurrentNiceVideoPlayer(mNiceVideoPlayer);
//                mNiceVideoPlayer.startToPause(32000);
//                mNiceVideoPlayer.start(15000);
                mNiceVideoPlayer.start();
            } else if (v == mBack) {
                if (mNiceVideoPlayer.isFullScreen()) {
                    mNiceVideoPlayer.exitFullScreen();
                } else if (mNiceVideoPlayer.isTinyWindow()) {
                    mNiceVideoPlayer.exitTinyWindow();
                }
            } else if (v == mRestartPause) {
                if (mNiceVideoPlayer.isPlaying() || mNiceVideoPlayer.isBufferingPlaying()) {
                    mNiceVideoPlayer.pause();
                } else if (mNiceVideoPlayer.isPaused() || mNiceVideoPlayer.isBufferingPaused()) {
                    mNiceVideoPlayer.restart();
                }
            } else if (v == mFullScreen) {
                if (mNiceVideoPlayer.isNormal() || mNiceVideoPlayer.isTinyWindow()) {
                    mNiceVideoPlayer.enterFullScreen();
                } else if (mNiceVideoPlayer.isFullScreen()) {
                    mNiceVideoPlayer.exitFullScreen();
                }
            } else if (v == mClarity) {
                setTopBottomVisible(false); // 隐藏top、bottom
                mClarityDialog.show();     // 显示清晰度对话框
            } else if (v == mRetry) {
                mNiceVideoPlayer.restart();
            } else if (v == mReplay) {
                mRetry.performClick();
            } else if (v == mShare) {
                Toast.makeText(mContext, "分享", Toast.LENGTH_SHORT).show();
            } else if (v == this) {
                if (mNiceVideoPlayer.isPlaying()
                        || mNiceVideoPlayer.isPaused()
                        || mNiceVideoPlayer.isBufferingPlaying()
                        || mNiceVideoPlayer.isBufferingPaused()) {
                    setTopBottomVisible(!topBottomVisible);
                }
            }
        }
    }

    @Override
    public void onClarityChanged(int clarityIndex) {
        // 根据切换后的清晰度索引值，设置对应的视频链接地址，并从当前播放位置接着播放
        Clarity clarity = clarities.get(clarityIndex);
        mClarity.setText(clarity.getGrade());
        long currentPosition = mNiceVideoPlayer.getCurrentPosition();

        mNiceVideoPlayer.playOtherVideo(clarity.getVideoUrl(), currentPosition);
    }

    @Override
    public void onClarityNotChanged() {
        // 清晰度没有变化，对话框消失后，需要重新显示出top、bottom
        setTopBottomVisible(true);
    }

    /**
     * 设置top、bottom的显示和隐藏
     *
     * @param visible true显示，false隐藏.
     */
    private void setTopBottomVisible(boolean visible) {
        mTop.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBottom.setVisibility(visible ? View.VISIBLE : View.GONE);
        topBottomVisible = visible;
        if (visible) {
            if (!mNiceVideoPlayer.isPaused() && !mNiceVideoPlayer.isBufferingPaused()) {
                startDismissTopBottomTimer();
            }
        } else {
            cancelDismissTopBottomTimer();
        }
    }

    /**
     * 开启top、bottom自动消失的timer
     */
    private void startDismissTopBottomTimer() {
        cancelDismissTopBottomTimer();
        if (mDismissTopBottomCountDownTimer == null) {
            mDismissTopBottomCountDownTimer = new CountDownTimer(5000, 5000) {
                @Override
                public void onTick(long millisUntilFinished) {

                }

                @Override
                public void onFinish() {
                    setTopBottomVisible(false);
                }
            };
        }
        mDismissTopBottomCountDownTimer.start();
    }

    /**
     * 取消top、bottom自动消失的timer
     */
    private void cancelDismissTopBottomTimer() {
        if (mDismissTopBottomCountDownTimer != null) {
            mDismissTopBottomCountDownTimer.cancel();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && mNiceVideoPlayer != null) {
            //人为滑动seekbar更新当前滑动的进度时间
            long position = (long) (mNiceVideoPlayer.getDuration() * progress / 100f);
            mPosition.setText(NiceUtil.formatTime(position));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        //拖动seekbar时先取消进度条更新timer，防止进度条跳的问题
        canUpdateProgress = false;
        cancelUpdateProgressTimer();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
//        if (mNiceVideoPlayer.isBufferingPaused() || mNiceVideoPlayer.isPaused()) {
//            mNiceVideoPlayer.restart();
//        }
        // 为什么往前拖动进度条后，进度条还会往后退几秒：seekTo只支持关键帧，出现这个情况就是原始的视频文件中关键帧比较少，
        // 播放器会在拖动的位置找最近的关键帧，然后在updateProgress(1秒更一次)时候根据CurrentPosition重新计算正确的progress，所以mSeek可能出现退几秒
        long position = (long) (mNiceVideoPlayer.getDuration() * seekBar.getProgress() / 100f);
        mNiceVideoPlayer.seekTo(position);
        startDismissTopBottomTimer();
        canUpdateProgress = true;
        //先这样解决拖动进度条AliPlayer seekTo后在onInfo回调中取到的currentPosition不是最新的，有延时。目的是避免seekbar拖动后大幅度跳动
        if (!(mNiceVideoPlayer instanceof AliVideoView)) {
            startUpdateProgressTimer();
        }
    }

    @Override
    public void updateProgress() {
        if (canUpdateProgress && mNiceVideoPlayer != null) {
            long position = mNiceVideoPlayer.getCurrentPosition();
            long duration = mNiceVideoPlayer.getDuration();
            int bufferPercentage = mNiceVideoPlayer.getBufferPercentage();
            mSeek.setSecondaryProgress(bufferPercentage);
            int progress = (int) (100f * position / duration);
            mSeek.setProgress(progress);
            mPosition.setText(NiceUtil.formatTime(position));
            mDuration.setText(NiceUtil.formatTime(duration));
            // 更新时间
            mTime.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
        }
    }

    @Override
    public void showChangePosition(long duration, int newPositionProgress) {
        mChangePositon.setVisibility(View.VISIBLE);
        long newPosition = (long) (duration * newPositionProgress / 100f);
        mChangePositionCurrent.setText(NiceUtil.formatTime(newPosition));
        mChangePositionProgress.setProgress(newPositionProgress);
        mSeek.setProgress(newPositionProgress);
        mPosition.setText(NiceUtil.formatTime(newPosition));
    }

    @Override
    public void hideChangePosition() {
        mChangePositon.setVisibility(View.GONE);
    }

    @Override
    public void showChangeVolume(int newVolumeProgress) {
        mChangeVolume.setVisibility(View.VISIBLE);
        mChangeVolumeProgress.setProgress(newVolumeProgress);
    }

    @Override
    public void hideChangeVolume() {
        mChangeVolume.setVisibility(View.GONE);
    }

    @Override
    public void showChangeBrightness(int newBrightnessProgress) {
        mChangeBrightness.setVisibility(View.VISIBLE);
        mChangeBrightnessProgress.setProgress(newBrightnessProgress);
    }

    @Override
    public void hideChangeBrightness() {
        mChangeBrightness.setVisibility(View.GONE);
    }
}
