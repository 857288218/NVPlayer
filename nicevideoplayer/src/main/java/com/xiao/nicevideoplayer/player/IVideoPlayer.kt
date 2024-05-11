package com.xiao.nicevideoplayer.player

interface IVideoPlayer {

    companion object {
        /**
         * 播放错误
         **/
        const val STATE_ERROR = -1

        /**
         * 播放未开始
         **/
        const val STATE_IDLE = 0

        /**
         * 播放准备中
         **/
        const val STATE_PREPARING = 1

        /**
         * 播放准备就绪
         **/
        const val STATE_PREPARED = 2

        /**
         * 第一帧画面渲染
         **/
        const val STATE_RENDERING_START = 3

        /**
         * 正在播放
         **/
        const val STATE_PLAYING = 4

        /**
         * 暂停播放
         **/
        const val STATE_PAUSED = 5

        /**
         * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，缓冲区数据足够后恢复播放)
         **/
        const val STATE_BUFFERING_PLAYING = 6

        /**
         * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，此时暂停播放器，继续缓冲，缓冲区数据足够后恢复暂停;或播放器暂停时缓冲
         **/
        const val STATE_BUFFERING_PAUSED = 7

        /**
         * 播放完成
         **/
        const val STATE_COMPLETED = 8

        /**
         * 普通模式
         **/
        const val MODE_NORMAL = 10

        /**
         * 全屏模式
         **/
        const val MODE_FULL_SCREEN = 11

        /**
         * 小窗口模式
         **/
        const val MODE_TINY_WINDOW = 12
    }

    /**
     * 设置视频Url，以及headers
     *
     * @param url     视频地址，可以是本地，也可以是网络视频
     * @param headers 请求header.
     */
    fun setUp(url: String, headers: Map<String, String>?)

    /**
     * 开始播放
     */
    fun start()

    /**
     * 从指定的位置开始播放
     *
     * @param position 播放位置
     */
    fun start(position: Long)

    /**
     * 重新播放，播放器暂停、播放错误、播放完成后，需要调用此方法重新播放
     */
    fun restart()

    /**
     * 暂停播放
     */
    fun pause()

    /**
     * seek到指定的位置,如果是播放状态则继续播放，如果是暂停则还是暂停
     *
     * @param pos 播放位置
     */
    fun seekTo(pos: Long)

    /**
     * 设置视频静音，非手机媒体音量静音
     */
    fun setMute(isMute: Boolean)

    /**
     * 设置是否循环播放
     */
    fun setLooping(looping: Boolean)

    /**
     * 设置播放速度，原生MediaPlayer暂不支持
     *
     * @param speed 播放速度
     */
    fun setSpeed(speed: Float)

    /**
     * 获取播放速度
     *
     * @param speed 播放速度
     * @return 播放速度
     */
    fun getSpeed(speed: Float): Float

    /**
     * 获取总时长，毫秒
     *
     * @return 视频总时长ms
     */
    fun getDuration(): Long

    /**
     * 获取当前播放的位置，毫秒
     *
     * @return 当前播放位置，ms
     */
    fun getCurrentPosition(): Long

    /**
     * 获取视频缓冲百分比
     *
     * @return 缓冲白百分比
     */
    fun getBufferPercentage(): Int

    /*********************************
     * 以下9个方法是播放器在当前的播放状态
     **********************************/
    fun isIdle(): Boolean

    fun isPreparing(): Boolean

    fun isPrepared(): Boolean

    fun isBufferingPlaying(): Boolean

    fun isBufferingPaused(): Boolean

    fun isPlaying(): Boolean

    fun isPaused(): Boolean

    fun isError(): Boolean

    fun isCompleted(): Boolean

    /*********************************
     * 以下3个方法是播放器的模式
     **********************************/
    fun isFullScreen(): Boolean

    fun isTinyWindow(): Boolean

    fun isNormal(): Boolean

    /**
     * 获取最大音量,指手机媒体音量
     *
     * @return 最大音量值
     */
    fun getMaxVolume(): Int

    /**
     * 获取当前音量,指手机媒体音量
     *
     * @return 当前音量值
     */
    fun getVolume(): Int

    /**
     * 设置音量,指手机媒体音量
     *
     * @param volume 音量值
     */
    fun setVolume(volume: Int)

    /**
     * 进入全屏模式
     */
    fun enterFullScreen()

    /**
     * 退出全屏模式
     *
     * @return true 退出
     */
    fun exitFullScreen(): Boolean

    /**
     * 进入小窗口模式
     */
    fun enterTinyWindow()

    /**
     * 退出小窗口模式
     *
     * @return true 退出小窗口
     */
    fun exitTinyWindow(): Boolean

    // 重置播放器，使播放器设置的属性全部清空;一般用于播放另一个视频，出错重播
    fun reset()

    /**
     * 释放INiceVideoPlayer，释放后，内部的播放器被释放掉，同时如果在全屏、小窗口模式下都会退出且控制器的UI也应该恢复到最初始的状态.
     */
    fun release()
}
