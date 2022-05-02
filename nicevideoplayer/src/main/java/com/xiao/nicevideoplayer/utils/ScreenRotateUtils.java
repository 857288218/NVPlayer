package com.xiao.nicevideoplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;

import java.lang.ref.WeakReference;

public class ScreenRotateUtils {
    public static float orientationDirection;
    private static final int DATA_X = 0;
    private static final int DATA_Y = 1;
    private static final int DATA_Z = 2;
    private static final int ORIENTATION_UNKNOWN = -1;
    private static ScreenRotateUtils instance;
    private WeakReference<Activity> mActivity;
    private boolean isOpenSensor = true;         // 是否打开传输，默认打开
    private boolean isEffectSysSetting = true;   // 手机系统的重力感应设置是否生效，默认无效，想要生效改成true就好了
    private final SensorManager sm;
    private final OrientationSensorListener listener;
    private OrientationChangeListener changeListener;
    private final Sensor sensor;

    private ScreenRotateUtils(SensorManager sm) {
        this.sm = sm;
        this.sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        listener = new OrientationSensorListener();
    }

    public static ScreenRotateUtils getInstance(Context context) {
        if (instance == null) {
            instance = new ScreenRotateUtils((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
        }
        return instance;
    }

    public void setOrientationChangeListener(OrientationChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    /**
     * 开启监听
     * 绑定切换横竖屏Activity的生命周期
     */
    public void start(Activity activity) {
        mActivity = new WeakReference<>(activity);
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    /**
     * 注销监听
     */
    public void stop() {
        sm.unregisterListener(listener);
    }

    public interface OrientationChangeListener {
        void orientationChange(int orientation);
    }

    class OrientationSensorListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            int orientation = ORIENTATION_UNKNOWN;
            float x = -values[DATA_X];
            orientationDirection = -x;
            float y = -values[DATA_Y];
            float z = -values[DATA_Z];
            float magnitude = x * x + y * y;
            if (magnitude * 4 >= z * z) {
                float oneEightyOverPi = 57.29577957855f;
                float angle = (float) (Math.atan2(-y, x) * oneEightyOverPi);

                orientation = 90 - Math.round(angle);
                // normalize to 0 - 359 range
                while (orientation >= 360) {
                    orientation -= 360;
                }
                while (orientation < 0) {
                    orientation += 360;
                }
            }

            // 获取手机系统的重力感应开关设置，这段代码看需求，不要就删除,screenchange = 1表示开启，screenchange = 0表示禁用要是禁用了就直接返回
            if (isEffectSysSetting) {
                try {
                    int isRotate = Settings.System.getInt(mActivity.get().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
                    // 如果用户禁用掉了重力感应就直接return
                    if (isRotate == 0) {
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // 判断是否要进行中断信息传递
                if (!isOpenSensor) {
                    return;
                }
                changeListener.orientationChange(orientation);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

}