package com.xiao.nicevideoplayer;

import com.xiao.nicevideoplayer.player.INiceVideoPlayer;

/**
 * 视频播放器管理器.
 */
public class NiceVideoPlayerManager {

    private INiceVideoPlayer mVideoPlayer;

    //是否允许释放播放器,解决屏幕中最后一个可见item在进入全屏后会执行onChildViewDetachedFromWindow，导致释放播放器会立刻退出全屏
    private boolean allowRelease = true;

    private NiceVideoPlayerManager() {
    }

    private static NiceVideoPlayerManager sInstance;

    public static synchronized NiceVideoPlayerManager instance() {
        if (sInstance == null) {
            sInstance = new NiceVideoPlayerManager();
        }
        return sInstance;
    }

    public void setAllowRelease(boolean allowRelease) {
        this.allowRelease = allowRelease;
    }

    public INiceVideoPlayer getCurrentNiceVideoPlayer() {
        return mVideoPlayer;
    }

    public void setCurrentNiceVideoPlayer(INiceVideoPlayer videoPlayer) {
        if (mVideoPlayer != videoPlayer) {
            releaseNiceVideoPlayer();
            mVideoPlayer = videoPlayer;
        }
    }

    public void suspendNiceVideoPlayer() {
        if (mVideoPlayer != null && (mVideoPlayer.isPlaying() || mVideoPlayer.isBufferingPlaying())) {
            mVideoPlayer.pause();
        }
    }

    public void resumeNiceVideoPlayer() {
        if (mVideoPlayer != null && (mVideoPlayer.isPaused() || mVideoPlayer.isBufferingPaused())) {
            mVideoPlayer.restart();
        }
    }

    public void releaseNiceVideoPlayer() {
        if (mVideoPlayer != null && allowRelease) {
            mVideoPlayer.release();
            mVideoPlayer = null;
        }
    }

    public boolean onBackPressd() {
        if (mVideoPlayer != null) {
            if (mVideoPlayer.isFullScreen()) {
                return mVideoPlayer.exitFullScreen();
            } else if (mVideoPlayer.isTinyWindow()) {
                return mVideoPlayer.exitTinyWindow();
            }
        }
        return false;
    }
}
