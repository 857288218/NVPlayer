package com.xiao.nicevideoplayer.player;

import java.util.Map;

public interface IVideoPlayer {
    /**
     * 播放错误
     **/
    int STATE_ERROR = -1;
    /**
     * 播放未开始
     **/
    int STATE_IDLE = 0;
    /**
     * 播放准备中
     **/
    int STATE_PREPARING = 1;
    /**
     * 播放准备就绪
     **/
    int STATE_PREPARED = 2;
    /**
     * 第一帧画面渲染
     **/
    int STATE_RENDERING_START = 3;
    /**
     * 正在播放
     **/
    int STATE_PLAYING = 4;
    /**
     * 暂停播放
     **/
    int STATE_PAUSED = 5;
    /**
     * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，缓冲区数据足够后恢复播放)
     **/
    int STATE_BUFFERING_PLAYING = 6;
    /**
     * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，此时暂停播放器，继续缓冲，缓冲区数据足够后恢复暂停;或播放器暂停时缓冲
     **/
    int STATE_BUFFERING_PAUSED = 7;
    /**
     * 播放完成
     **/
    int STATE_COMPLETED = 8;

    /**
     * 普通模式
     **/
    int MODE_NORMAL = 10;
    /**
     * 全屏模式
     **/
    int MODE_FULL_SCREEN = 11;
    /**
     * 小窗口模式
     **/
    int MODE_TINY_WINDOW = 12;

    /**
     * 设置视频Url，以及headers
     *
     * @param url     视频地址，可以是本地，也可以是网络视频
     * @param headers 请求header.
     */
    void setUp(String url, Map<String, String> headers);

    /**
     * 开始播放
     */
    void start();

    /**
     * 从指定的位置开始播放
     *
     * @param position 播放位置
     */
    void start(long position);

    /**
     * 到指定的位置暂停
     *
     * @param pos 暂停位置
     */
    void startToPause(long pos);

    /**
     * 重新播放，播放器被暂停、播放错误、播放完成后，需要调用此方法重新播放
     */
    void restart();

    /**
     * 暂停播放
     */
    void pause();

    /**
     * seek到指定的位置,如果是播放状态则继续播放，如果是暂停则还是暂停
     *
     * @param pos 播放位置
     */
    void seekTo(long pos);

    /**
     * 设置音量,指手机媒体音量
     *
     * @param volume 音量值
     */
    void setVolume(int volume);

    /**
     * 设置视频静音，非手机媒体音量静音
     */
    void setMute(boolean isMute);

    /**
     * 设置播放速度，目前只有IjkPlayer有效果，原生MediaPlayer暂不支持
     *
     * @param speed 播放速度
     */
    void setSpeed(float speed);

    /**
     * 开始播放时，是否从上一次的位置继续播放
     *
     * @param continueFromLastPosition true 接着上次的位置继续播放，false从头开始播放
     */
    void continueFromLastPosition(boolean continueFromLastPosition);

    /*********************************
     * 以下9个方法是播放器在当前的播放状态
     **********************************/
    boolean isIdle();

    boolean isPreparing();

    boolean isPrepared();

    boolean isBufferingPlaying();

    boolean isBufferingPaused();

    boolean isPlaying();

    boolean isPaused();

    boolean isError();

    boolean isCompleted();

    /*********************************
     * 以下3个方法是播放器的模式
     **********************************/
    boolean isFullScreen();

    boolean isTinyWindow();

    boolean isNormal();

    /**
     * 获取最大音量,指手机媒体音量
     *
     * @return 最大音量值
     */
    int getMaxVolume();

    /**
     * 获取当前音量,指手机媒体音量
     *
     * @return 当前音量值
     */
    int getVolume();

    /**
     * 获取总时长，毫秒
     *
     * @return 视频总时长ms
     */
    long getDuration();

    /**
     * 获取当前播放的位置，毫秒
     *
     * @return 当前播放位置，ms
     */
    long getCurrentPosition();

    /**
     * 获取视频缓冲百分比
     *
     * @return 缓冲白百分比
     */
    int getBufferPercentage();

    /**
     * 获取播放速度
     *
     * @param speed 播放速度
     * @return 播放速度
     */
    float getSpeed(float speed);

    /**
     * 进入全屏模式
     */
    void enterFullScreen();

    /**
     * 退出全屏模式
     *
     * @return true 退出
     */
    boolean exitFullScreen();

    /**
     * 进入小窗口模式
     */
    void enterTinyWindow();

    /**
     * 退出小窗口模式
     *
     * @return true 退出小窗口
     */
    boolean exitTinyWindow();

    /**
     * 此处只释放播放器（如果要释放播放器并恢复控制器状态需要调用{@link #release()}方法） 不管是全屏、小窗口还是Normal状态下控制器的UI都不恢复初始状态
     * 这样以便在当前播放器状态下可以方便的切换不同的清晰度的视频地址
     */
    void releasePlayer();

    /**
     * 释放INiceVideoPlayer，释放后，内部的播放器被释放掉，同时如果在全屏、小窗口模式下都会退出 并且控制器的UI也应该恢复到最初始的状态.
     */
    void release();
}
