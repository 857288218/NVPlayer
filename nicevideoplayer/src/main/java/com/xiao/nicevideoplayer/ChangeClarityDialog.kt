package com.xiao.nicevideoplayer

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import com.xiao.nicevideoplayer.utils.NiceUtil

/**
 * 切换清晰度对话框（仿腾讯视频切换清晰度的对话框）.
 */
class ChangeClarityDialog(context: Context) : Dialog(context, R.style.dialog_change_clarity) {

    private var mLinearLayout: LinearLayout? = null
    private var mCurrentCheckedIndex = 0

    private fun init(context: Context) {
        mLinearLayout = LinearLayout(context)
        mLinearLayout!!.gravity = Gravity.CENTER
        mLinearLayout!!.orientation = LinearLayout.VERTICAL
        mLinearLayout!!.setOnClickListener {
            if (mListener != null) {
                mListener!!.onClarityNotChanged()
            }
            dismiss()
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            MarginLayoutParams.MATCH_PARENT
        )
        setContentView(mLinearLayout!!, params)
        val windowParams = window!!.attributes
        windowParams.width = NiceUtil.getScreenHeight(context)
        windowParams.height = NiceUtil.getScreenWidth(context)
        window!!.attributes = windowParams
    }

    /**
     * 设置清晰度等级
     *
     * @param items          清晰度等级items
     * @param defaultChecked 默认选中的清晰度索引
     */
    fun setClarityGrade(items: List<String?>, defaultChecked: Int) {
        mCurrentCheckedIndex = defaultChecked
        for (i in items.indices) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_change_clarity, mLinearLayout, false) as TextView
            itemView.tag = i
            itemView.setOnClickListener { v: View ->
                if (mListener != null) {
                    val checkIndex = v.tag as Int
                    if (checkIndex != mCurrentCheckedIndex) {
                        for (j in 0 until mLinearLayout!!.childCount) {
                            mLinearLayout!!.getChildAt(j).isSelected = checkIndex == j
                        }
                        mListener!!.onClarityChanged(checkIndex)
                        mCurrentCheckedIndex = checkIndex
                    } else {
                        mListener!!.onClarityNotChanged()
                    }
                }
                dismiss()
            }
            itemView.text = items[i]
            itemView.isSelected = i == defaultChecked
            val params = itemView.layoutParams as MarginLayoutParams
            params.topMargin = if (i == 0) 0 else NiceUtil.dp2px(context, 16f)
            mLinearLayout!!.addView(itemView, params)
        }
    }

    interface OnClarityChangedListener {
        /**
         * 切换清晰度后回调
         *
         * @param clarityIndex 切换到的清晰度的索引值
         */
        fun onClarityChanged(clarityIndex: Int)

        /**
         * 清晰度没有切换，比如点击了空白位置，或者点击的是之前的清晰度
         */
        fun onClarityNotChanged()
    }

    private var mListener: OnClarityChangedListener? = null
    fun setOnClarityCheckedListener(listener: OnClarityChangedListener?) {
        mListener = listener
    }

    override fun onBackPressed() {
        // 按返回键时回调清晰度没有变化
        if (mListener != null) {
            mListener!!.onClarityNotChanged()
        }
        super.onBackPressed()
    }

    init {
        init(context)
    }
}