package com.xiao.nicevideoplayer.utils

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import java.lang.ref.WeakReference
import kotlin.math.atan2
import kotlin.math.roundToInt

class ScreenRotateUtils private constructor(private val sm: SensorManager) {

    private var mActivity: WeakReference<Activity>? = null
    private val isOpenSensor = true // 是否打开传输，默认打开
    private val isEffectSysSetting = true // 手机系统的重力感应设置是否生效，默认无效，想要生效改成true就好了
    private val listener = OrientationSensorListener()
    private var changeListener: OrientationChangeListener? = null
    private val sensor: Sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun setOrientationChangeListener(changeListener: OrientationChangeListener?) {
        this.changeListener = changeListener
    }

    /**
     * 开启监听
     * 绑定切换横竖屏Activity的生命周期
     */
    fun start(activity: Activity) {
        mActivity = WeakReference(activity)
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * 注销监听
     */
    fun stop() {
        sm.unregisterListener(listener)
    }

    interface OrientationChangeListener {
        fun orientationChange(orientation: Int)
    }

    internal inner class OrientationSensorListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values
            var orientation = ORIENTATION_UNKNOWN
            val x = -values[DATA_X]
            orientationDirection = -x
            val y = -values[DATA_Y]
            val z = -values[DATA_Z]
            val magnitude = x * x + y * y
            if (magnitude * 4 >= z * z) {
                val oneEightyOverPi = 57.29577957855f
                val angle = (atan2(-y.toDouble(), x.toDouble()) * oneEightyOverPi).toFloat()
                orientation = 90 - angle.roundToInt()
                // normalize to 0 - 359 range
                while (orientation >= 360) {
                    orientation -= 360
                }
                while (orientation < 0) {
                    orientation += 360
                }
            }

            // 获取手机系统的重力感应开关设置，这段代码看需求，不要就删除,screenchange = 1表示开启，screenchange = 0表示禁用要是禁用了就直接返回
            if (isEffectSysSetting) {
                try {
                    val isRotate = Settings.System.getInt(
                        mActivity!!.get()!!.contentResolver, Settings.System.ACCELEROMETER_ROTATION
                    )
                    // 如果用户禁用掉了重力感应就直接return
                    if (isRotate == 0) {
                        return
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // 判断是否要进行中断信息传递
                if (!isOpenSensor) {
                    return
                }
                changeListener?.orientationChange(orientation)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {}
    }

    companion object {
        var orientationDirection = 0f
        private const val DATA_X = 0
        private const val DATA_Y = 1
        private const val DATA_Z = 2
        private const val ORIENTATION_UNKNOWN = -1
        private var instance: ScreenRotateUtils? = null

        @JvmStatic
        fun getInstance(context: Context): ScreenRotateUtils? {
            if (instance == null) {
                instance =
                    ScreenRotateUtils(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            }
            return instance
        }
    }
}