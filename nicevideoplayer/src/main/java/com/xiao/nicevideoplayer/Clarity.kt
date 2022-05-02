package com.xiao.nicevideoplayer

/**
 * 清晰度
 */
data class Clarity(
    var grade: String, // 270P、480P、720P、1080P、4K ...
    var p: String, // 视频链接地址
    var videoUrl: String
)