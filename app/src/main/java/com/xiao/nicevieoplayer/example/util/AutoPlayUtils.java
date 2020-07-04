package com.xiao.nicevieoplayer.example.util;

import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.xiao.nicevideoplayer.SurfaceVideoPlayer;
import com.xiao.nicevideoplayer.NiceVideoPlayerManager;

/**
 * 列表自动播放工具类
 *
 * @author Liberations
 */
public class AutoPlayUtils {
    public static int positionInList = -1;//记录当前播放列表位置

    private AutoPlayUtils() {
    }

    /**
     * @param firstVisiblePosition 首个可见item位置
     * @param lastVisiblePosition  最后一个可见item位置
     */
    public static void onScrollPlayVideo(RecyclerView recyclerView, int jzvdId, int firstVisiblePosition, int lastVisiblePosition) {
        for (int i = 0; i <= lastVisiblePosition - firstVisiblePosition; i++) {
            View child = recyclerView.getChildAt(i);
            View view = child.findViewById(jzvdId);
            if (view != null && view instanceof SurfaceVideoPlayer) {
                SurfaceVideoPlayer player = (SurfaceVideoPlayer) view;
                if (getViewVisiblePercent(player) == 1f) {
                    if (positionInList != i + firstVisiblePosition) {
                        if (player.isIdle()) {
                            player.start();
                        } else {
                            player.restart();
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * @param firstVisiblePosition 首个可见item位置
     * @param lastVisiblePosition  最后一个可见item位置
     * @param percent              当item被遮挡percent/1时释放,percent取值0-1
     */
    public static void onScrollReleaseAllVideos(int firstVisiblePosition, int lastVisiblePosition, float percent) {
        if (NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer() == null) return;
        if (positionInList >= 0) {
            if ((positionInList <= firstVisiblePosition || positionInList >= lastVisiblePosition - 1)) {
                if (getViewVisiblePercent((View) NiceVideoPlayerManager.instance().getCurrentNiceVideoPlayer()) < percent) {
                    NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
                }
            }
        }
    }

    /**
     * @param view
     * @return 当前视图可见比列
     */
    public static float getViewVisiblePercent(View view) {
        if (view == null) {
            return 0f;
        }
        float height = view.getHeight();
        Rect rect = new Rect();
        if (!view.getLocalVisibleRect(rect)) {
            return 0f;
        }
        float visibleHeight = rect.bottom - rect.top;
        return visibleHeight / height;
    }
}
