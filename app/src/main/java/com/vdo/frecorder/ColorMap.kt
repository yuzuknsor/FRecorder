package com.vdo.frecorder

import com.vdo.frecorder.data.Prefs

/** 主题色映射：按选中的主题 key 提供样式与颜色资源。Kotlin 化（原 ColorMap.java）。 */
class ColorMap private constructor(prefs: Prefs) {

	private var appThemeRes = 0
	private var primaryColorValue = R.color.md_blue_700
	private var primaryDarkValue = R.color.md_blue_gray_500
	private var playbackPanelValue = R.drawable.panel_amber
	private var selectedKey: String? = null
	private val onThemeColorChangeListeners = ArrayList<OnThemeColorChangeListener>()

	init {
		selectedKey = prefs.getSettingThemeColor()
		init()
	}

	private fun init() {
		when (selectedKey) {
			AppConstants.THEME_BLACK -> {
				appThemeRes = R.style.AppTheme_Black
				primaryColorValue = R.color.md_black_1000
				primaryDarkValue = R.color.md_grey_900x
				playbackPanelValue = R.drawable.panel_dark
			}
			AppConstants.THEME_TEAL -> {
				appThemeRes = R.style.AppTheme_Teal
				primaryColorValue = R.color.md_teal_700
				primaryDarkValue = R.color.md_teal_700x
				playbackPanelValue = R.drawable.panel_green
			}
			AppConstants.THEME_PURPLE -> {
				appThemeRes = R.style.AppTheme_Purple
				primaryColorValue = R.color.md_deep_purple_700
				primaryDarkValue = R.color.md_deep_purple_700x
				playbackPanelValue = R.drawable.panel_pink
			}
			AppConstants.THEME_PINK -> {
				appThemeRes = R.style.AppTheme_Pink
				primaryColorValue = R.color.md_pink_800
				primaryDarkValue = R.color.md_pink_800x
				playbackPanelValue = R.drawable.panel_purple
			}
			AppConstants.THEME_ORANGE -> {
				appThemeRes = R.style.AppTheme_DeepOrange
				primaryColorValue = R.color.md_deep_orange_800
				primaryDarkValue = R.color.md_deep_orange_900
				playbackPanelValue = R.drawable.panel_yellow
			}
			AppConstants.THEME_RED -> {
				appThemeRes = R.style.AppTheme_Red
				primaryColorValue = R.color.md_red_700
				primaryDarkValue = R.color.md_red_900
				playbackPanelValue = R.drawable.panel_purple_light
			}
			AppConstants.THEME_BROWN -> {
				appThemeRes = R.style.AppTheme_Brown
				primaryColorValue = R.color.md_brown_700
				primaryDarkValue = R.color.md_brown_800
				playbackPanelValue = R.drawable.panel_deep_orange
			}
			AppConstants.THEME_BLUE -> {
				primaryColorValue = R.color.md_blue_700
				appThemeRes = R.style.AppTheme_Blue
				primaryDarkValue = R.color.md_blue_700x
				playbackPanelValue = R.drawable.panel_amber
			}
			AppConstants.THEME_BLUE_GREY, null -> {
				appThemeRes = R.style.AppTheme_Gray
				primaryColorValue = R.color.md_blue_gray_700
				primaryDarkValue = R.color.md_blue_gray_500
				playbackPanelValue = R.drawable.panel_red
			}
			else -> {
				appThemeRes = R.style.AppTheme_Gray
				primaryColorValue = R.color.md_blue_gray_700
				primaryDarkValue = R.color.md_blue_gray_500
				playbackPanelValue = R.drawable.panel_red
			}
		}
	}

	fun updateColorMap(colorKey: String?) {
		val oldSelected = selectedKey
		selectedKey = colorKey
		if (oldSelected != selectedKey) {
			init()
			onThemeColorChange(selectedKey ?: "")
		}
	}

	fun getSelected(): String? = selectedKey

	val appThemeResource: Int
		get() = appThemeRes

	val primaryColorRes: Int
		get() = primaryColorValue

	val primaryDarkColorRes: Int
		get() = primaryDarkValue

	val playbackPanelBackground: Int
		get() = playbackPanelValue

	fun getColorResources(): IntArray = intArrayOf(
			R.color.md_blue_gray_700,
			R.color.md_black_1000,
			R.color.md_teal_700,
			R.color.md_blue_700,
			R.color.md_deep_purple_700,
			R.color.md_pink_800,
			R.color.md_deep_orange_800,
			R.color.md_red_700,
			R.color.md_brown_700
	)

	fun addOnThemeColorChangeListener(listener: OnThemeColorChangeListener) {
		onThemeColorChangeListeners.add(listener)
	}

	fun removeOnThemeColorChangeListener(listener: OnThemeColorChangeListener) {
		onThemeColorChangeListeners.remove(listener)
	}

	fun onThemeColorChange(colorKey: String) {
		for (listener in onThemeColorChangeListeners) {
			listener.onThemeColorChange(colorKey)
		}
	}

	interface OnThemeColorChangeListener {
		fun onThemeColorChange(colorKey: String)
	}

	companion object {
		@Volatile
		private var singleton: ColorMap? = null

		@JvmStatic
		fun getInstance(prefs: Prefs): ColorMap {
			singleton?.let { return it }
			synchronized(ColorMap::class.java) {
				singleton?.let { return it }
				val created = ColorMap(prefs)
				singleton = created
				return created
			}
		}
	}
}
