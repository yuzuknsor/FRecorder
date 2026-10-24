package com.vdo.frecorder.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.vdo.frecorder.R

/** 设置项组合视图（标题 + 信息按钮 + Chips 选择器）。Kotlin 化（原 SettingView.java）。 */
class SettingView : LinearLayout {

	private lateinit var chipsView: ChipsView
	private lateinit var txtTitle: TextView
	private lateinit var btnInfo: ImageButton
	private lateinit var imgInfo: ImageView

	constructor(@NonNull context: Context) : super(context) {
		init()
	}

	constructor(@NonNull context: Context, @Nullable attrs: AttributeSet?) : super(context, attrs) {
		init()
	}

	constructor(@NonNull context: Context, @Nullable attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		init()
	}

	constructor(@NonNull context: Context, @Nullable attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
		init()
	}

	private fun init() {
		orientation = VERTICAL
		inflate(context, R.layout.view_setting_view, this)

		chipsView = findViewById(R.id.chips_view)
		txtTitle = findViewById(R.id.setting_title)
		btnInfo = findViewById(R.id.setting_btn_info)
		imgInfo = findViewById(R.id.setting_image)
	}

	override fun setVisibility(visibility: Int) {
		super.setVisibility(visibility)
		chipsView.visibility = visibility
	}

	fun setData(names: Array<String>, keys: Array<String>, colors: IntArray) {
		chipsView.setData(names, keys, colors)
	}

	fun setData(names: Array<String>, keys: Array<String>) {
		chipsView.setData(names, keys, null)
	}

	fun removeChip(keys: Array<String>) {
		chipsView.removeChip(keys)
	}

	fun addChip(keys: Array<String>, names: Array<String>) {
		chipsView.addChip(keys, names)
	}

	fun setSelected(key: String) {
		chipsView.setSelected(key)
	}

	fun getSelected(): String? {
		return chipsView.getSelected()
	}

	fun setOnChipCheckListener(l: ChipsView.OnCheckListener?) {
		chipsView.setOnChipCheckListener(l)
	}

	fun setTitle(title: String) {
		txtTitle.text = title
	}

	fun setTitle(resId: Int) {
		txtTitle.setText(resId)
	}

	fun setImageInfo(imgRes: Int) {
		imgInfo.setImageResource(imgRes)
	}

	override fun setEnabled(enabled: Boolean) {
		chipsView.isEnabled = enabled
	}

	fun setOnInfoClickListener(listener: View.OnClickListener?) {
		btnInfo.setOnClickListener(listener)
	}
}
