/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vdo.frecorder.app.records

import android.Manifest
import android.animation.Animator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.DecodeService
import com.vdo.frecorder.app.DownloadService
import com.vdo.frecorder.app.PlaybackService
import com.vdo.frecorder.app.info.ActivityInformation
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.trash.TrashActivity
import com.vdo.frecorder.app.widget.SimpleWaveformView
import com.vdo.frecorder.app.widget.TouchLayout
import com.vdo.frecorder.app.widget.WaveformViewNew
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.AnimationUtil
import com.vdo.frecorder.util.FileUtil
import com.vdo.frecorder.util.TimeUtils
import timber.log.Timber

/** 录音列表页。Kotlin 化（原 RecordsActivity.java）。 */
class RecordsActivity : Activity(), RecordsContract.View, View.OnClickListener {

	private lateinit var recyclerView: RecyclerView
	private lateinit var layoutManager: LinearLayoutManager
	private lateinit var adapter: RecordsAdapter

	private lateinit var toolbar: LinearLayout
	private lateinit var progressBar: ProgressBar
	private lateinit var bottomDivider: View
	private lateinit var btnPlay: ImageButton
	private lateinit var btnBookmarks: ImageButton
	private lateinit var btnSort: ImageButton
	private lateinit var btnCheckBookmark: ImageButton
	private lateinit var btnDecoding: ImageButton
	private lateinit var txtProgress: TextView
	private lateinit var txtDuration: TextView
	private lateinit var txtName: TextView
	private lateinit var txtEmpty: TextView
	private lateinit var txtTitle: TextView
	private lateinit var txtSubTitle: TextView
	private lateinit var touchLayout: TouchLayout
	private lateinit var waveformView: WaveformViewNew
	private lateinit var panelProgress: ProgressBar
	private lateinit var playProgress: SeekBar
	private lateinit var multiSelectPanel: View
	private lateinit var txtSelectedCount: TextView
	private lateinit var btnCloseMulti: ImageButton
	private lateinit var btnShareMulti: ImageButton
	private lateinit var btnDeleteMulti: ImageButton
	private lateinit var btnDownloadMulti: ImageButton

	private lateinit var presenter: RecordsContract.UserActionsListener
	private lateinit var colorMap: com.vdo.frecorder.ColorMap

	private val downloadRecords: MutableList<String> = ArrayList()

	override fun onCreate(savedInstanceState: Bundle?) {
		colorMap = ARApplication.injector.provideColorMap(applicationContext)
		SimpleWaveformView.setWaveformColorRes(colorMap.primaryColorRes)
		setTheme(colorMap.appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_records)

		toolbar = findViewById(R.id.toolbar)

		val btnBack = findViewById<ImageButton>(R.id.btn_back)
		btnBack.setOnClickListener {
			finish()
			ARApplication.injector.releaseRecordsPresenter()
		}

		bottomDivider = findViewById(R.id.bottomDivider)
		progressBar = findViewById(R.id.progress)
		panelProgress = findViewById(R.id.wave_progress)
		btnPlay = findViewById(R.id.btn_play)
		val btnStop = findViewById<ImageButton>(R.id.btn_stop)
		val btnNext = findViewById<ImageButton>(R.id.btn_next)
		val btnPrev = findViewById<ImageButton>(R.id.btn_prev)
		val btnDelete = findViewById<ImageButton>(R.id.btn_delete)
		btnBookmarks = findViewById(R.id.btn_bookmarks)
		btnSort = findViewById(R.id.btn_sort)
		btnCheckBookmark = findViewById(R.id.btn_check_bookmark)
		btnDecoding = findViewById(R.id.btn_decoding)
		txtEmpty = findViewById(R.id.txtEmpty)
		txtTitle = findViewById(R.id.txt_title)
		txtSubTitle = findViewById(R.id.txt_sub_title)
		btnPlay.setOnClickListener(this)
		btnStop.setOnClickListener(this)
		btnNext.setOnClickListener(this)
		btnPrev.setOnClickListener(this)
		btnDelete.setOnClickListener(this)
		btnBookmarks.setOnClickListener(this)
		btnCheckBookmark.setOnClickListener(this)
		btnDecoding.setOnClickListener(this)
		btnSort.setOnClickListener(this)

		multiSelectPanel = findViewById(R.id.menu_multi_select)
		multiSelectPanel.setBackgroundResource(colorMap.primaryDarkColorRes)
		txtSelectedCount = findViewById(R.id.txt_selected_multi)
		btnCloseMulti = findViewById(R.id.btn_close_multi_select)
		btnCloseMulti.setOnClickListener(this)
		btnShareMulti = findViewById(R.id.btn_share_multi)
		btnDeleteMulti = findViewById(R.id.btn_delete_multi)
		btnDownloadMulti = findViewById(R.id.btn_download_multi)
		btnShareMulti.setOnClickListener(this)
		btnDeleteMulti.setOnClickListener(this)
		btnDownloadMulti.setOnClickListener(this)

		playProgress = findViewById(R.id.play_progress)
		txtProgress = findViewById(R.id.txt_progress)
		txtDuration = findViewById(R.id.txt_duration)
		txtName = findViewById(R.id.txt_name)
		waveformView = findViewById(R.id.record)
		waveformView.showTimeline(false)

		txtName.setOnClickListener(this)

		playProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				if (fromUser) {
					val valPx = AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000).toInt()
					waveformView.seekPx(valPx)
					presenter.seekPlayback(waveformView.pxToMill(valPx))
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {
				presenter.disablePlaybackProgressListener()
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				presenter.enablePlaybackProgressListener()
			}
		})

		touchLayout = findViewById(R.id.touch_layout)
		touchLayout.setBackgroundResource(colorMap.playbackPanelBackground)
		touchLayout.setOnThresholdListener(object : TouchLayout.ThresholdListener {
			override fun onTopThreshold() {
				hidePanel()
				presenter.stopPlayback()
			}

			override fun onBottomThreshold() {
				hidePanel()
				presenter.stopPlayback()
			}

			override fun onTouchDown() {}

			override fun onTouchUp() {}
		})

		recyclerView = findViewById(R.id.recycler_view)
		recyclerView.setHasFixedSize(true)
		layoutManager = LinearLayoutManager(applicationContext)
		recyclerView.layoutManager = layoutManager
		recyclerView.addOnScrollListener(MyScrollListener(layoutManager))

		recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(@NonNull rv: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(rv, dx, dy)
				handleToolbarScroll(dy)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (isListOnTop) {
						AnimationUtil.viewElevationAnimation(toolbar, 0f, object : Animator.AnimatorListener {
							override fun onAnimationStart(animation: Animator) {}

							override fun onAnimationEnd(animation: Animator) {
								toolbar.setBackgroundResource(android.R.color.transparent)
							}

							override fun onAnimationCancel(animation: Animator) {}

							override fun onAnimationRepeat(animation: Animator) {}
						})
					}
				}
				if (adapter.itemCount < 5 || isListOnBottom) {
					bottomDivider.visibility = View.GONE
				} else {
					bottomDivider.visibility = View.VISIBLE
				}
			}
		})

		adapter = RecordsAdapter(ARApplication.injector.provideSettingsMapper(applicationContext))
		adapter.setItemClickListener { _, id, _, position ->
			presenter.setActiveRecord(id, object : RecordsContract.Callback {
				override fun onSuccess() {
					presenter.stopPlayback()
					if (startPlayback()) {
						adapter.setActiveItem(position)
					}
				}

				override fun onError(e: Exception) {
					Timber.e(e)
				}
			})
		}
		adapter.setOnAddToBookmarkListener(object : RecordsAdapter.OnAddToBookmarkListener {
			override fun onAddToBookmarks(id: Int) {
				presenter.addToBookmark(id)
			}

			override fun onRemoveFromBookmarks(id: Int) {
				presenter.removeFromBookmarks(id)
			}
		})
		adapter.setOnItemOptionListener { menuId, item ->
			if (menuId == R.id.menu_share) {
				AndroidUtils.shareAudioFile(applicationContext, item.path, item.name, item.format)
			} else if (menuId == R.id.menu_info) {
				presenter.onRecordInfo(Mapper.toRecordInfo(item)!!)
			} else if (menuId == R.id.menu_rename) {
				setRecordName(item.id, item.name, item.format)
			} else if (menuId == R.id.menu_open_with) {
				AndroidUtils.openAudioFile(applicationContext, item.path, item.name)
			} else if (menuId == R.id.menu_save_as) {
				if (isPublicDir(item.path)) {
					if (checkStoragePermissionDownload()) {
						//通过 Service 下载录音文件
						DownloadService.startNotification(
								applicationContext,
								item.path
						)
					} else {
						downloadRecords.add(item.path)
					}
				} else {
					//通过 Service 下载录音文件
					DownloadService.startNotification(
							applicationContext,
							item.path
					)
				}
			} else if (menuId == R.id.menu_delete) {
				AndroidUtils.showDialogYesNo(
						this@RecordsActivity,
						R.drawable.ic_delete_forever_dark,
						getString(R.string.warning),
						getString(R.string.delete_record, item.name)
				) { presenter.deleteRecord(item.id, item.path) }
			}
		}
		adapter.setBtnTrashClickListener { startActivity(TrashActivity.getStartIntent(applicationContext)) }
		adapter.setOnMultiSelectModeListener(object : RecordsAdapter.OnMultiSelectModeListener {
			override fun onMultiSelectMode(selected: Boolean) {
				stopPlayback()
				multiSelectPanel.visibility = if (selected) View.VISIBLE else View.GONE
			}

			override fun onSelectDeselect(selectedCount: Int) {
				txtSelectedCount.text = resources.getString(R.string.selected, selectedCount)
			}
		})
		recyclerView.adapter = adapter

		presenter = ARApplication.injector.provideRecordsPresenter(applicationContext)

		waveformView.setOnSeekListener(object : WaveformViewNew.OnSeekListener {
			override fun onStartSeek() {
				presenter.disablePlaybackProgressListener()
			}

			override fun onSeek(px: Int, mills: Long) {
				presenter.enablePlaybackProgressListener()
				//TODO: 找到更好的 px 转 mills 方式
				presenter.seekPlayback(waveformView.pxToMill(px))

				val length = waveformView.getWaveformLength()
				if (length > 0) {
					playProgress.progress = 1000 * AndroidUtils.pxToDp(px).toInt() / length
				}
				txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			}

			override fun onSeeking(px: Int, mills: Long) {
				val length = waveformView.getWaveformLength()
				if (length > 0) {
					playProgress.progress = 1000 * AndroidUtils.pxToDp(px).toInt() / length
				}
				txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			}
		})
	}

	override fun onResume() {
		super.onResume()
		presenter.onResumeView()
	}

	override fun showPlayerPanel() {
		if (touchLayout.visibility != View.VISIBLE) {
			touchLayout.visibility = View.VISIBLE
			if (touchLayout.height == 0) {
				touchLayout.translationY = AndroidUtils.dpToPx(800)
			} else {
				touchLayout.translationY = touchLayout.height.toFloat()
			}
			adapter.showFooter()
			val animator: ViewPropertyAnimator = touchLayout.animate()
			animator.translationY(0f)
					.setDuration(200)
					.setListener(object : Animator.AnimatorListener {
						override fun onAnimationStart(animation: Animator) {}

						override fun onAnimationEnd(animation: Animator) {
							val o = recyclerView.computeVerticalScrollOffset()
							val r = recyclerView.computeVerticalScrollRange()
							val e = recyclerView.computeVerticalScrollExtent()
							val k = o.toFloat() / (r - e).toFloat()
							recyclerView.smoothScrollBy(0, (touchLayout.height * k).toInt())
							animator.setListener(null)
						}

						override fun onAnimationCancel(animation: Animator) {}

						override fun onAnimationRepeat(animation: Animator) {}
					})
					.start()
		}
	}

	override fun startPlaybackService() {
		PlaybackService.startServiceForeground(applicationContext, presenter.getRecordName())
	}

	fun hidePanel() {
		if (touchLayout.visibility == View.VISIBLE) {
			adapter.hideFooter()
			showToolbar()
			val animator: ViewPropertyAnimator = touchLayout.animate()
			animator.translationY(touchLayout.height.toFloat())
					.setDuration(200)
					.setListener(object : Animator.AnimatorListener {
						override fun onAnimationStart(animation: Animator) {}

						override fun onAnimationEnd(animation: Animator) {
							touchLayout.visibility = View.GONE
							animator.setListener(null)
						}

						override fun onAnimationCancel(animation: Animator) {}

						override fun onAnimationRepeat(animation: Animator) {}
					})
					.start()
		}
	}

	private fun showToolbar() {
		AnimationUtil.viewAnimationY(toolbar, 0f, null)
	}

	private fun startPlayback(): Boolean {
		val path = presenter.getActiveRecordPath()
		if (FileUtil.isFileInExternalStorage(applicationContext, path)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				AndroidUtils.showRecordFileNotAvailable(this, path!!)
				return false
			} else if (checkStoragePermissionPlayback()) {
				presenter.startPlayback()
				return true
			}
		} else {
			presenter.startPlayback()
			return true
		}
		return false
	}

	override fun onClick(view: View) {
		val id = view.id
		if (id == R.id.btn_play) {
			//此方法开始或暂停播放
			startPlayback()
		} else if (id == R.id.btn_stop) {
			stopPlayback()
		} else if (id == R.id.btn_next) {
			presenter.pausePlayback()
			val recId = adapter.getNextTo(presenter.getActiveRecordId())
			presenter.setActiveRecord(recId, object : RecordsContract.Callback {
				override fun onSuccess() {
					presenter.stopPlayback()
					if (startPlayback()) {
						val pos = adapter.findPositionById(recId)
						if (pos >= 0) {
							recyclerView.scrollToPosition(pos)
							val o = recyclerView.computeVerticalScrollOffset()
							if (o > 0) {
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
									toolbar.translationZ = resources.getDimension(R.dimen.toolbar_elevation)
									toolbar.setBackgroundResource(colorMap.primaryColorRes)
								}
							}
							adapter.setActiveItem(pos)
						}
					}
				}

				override fun onError(e: Exception) {
					Timber.e(e)
				}
			})
		} else if (id == R.id.btn_prev) {
			presenter.pausePlayback()
			val prevRecId = adapter.getPrevTo(presenter.getActiveRecordId())
			presenter.setActiveRecord(prevRecId, object : RecordsContract.Callback {
				override fun onSuccess() {
					presenter.stopPlayback()
					if (startPlayback()) {
						val pos2 = adapter.findPositionById(prevRecId)
						if (pos2 >= 0) {
							recyclerView.scrollToPosition(pos2)
							adapter.setActiveItem(pos2)
						}
					}
				}

				override fun onError(e: Exception) {
					Timber.e(e)
				}
			})
		} else if (id == R.id.btn_delete) {
			presenter.pausePlayback()
			AndroidUtils.showDialogYesNo(
					this@RecordsActivity,
					R.drawable.ic_delete_forever_dark,
					getString(R.string.warning),
					getString(R.string.delete_record, presenter.getRecordName())
			) { presenter.deleteActiveRecord() }
		} else if (id == R.id.btn_check_bookmark) {
			presenter.checkBookmarkActiveRecord()
		} else if (id == R.id.btn_decoding) {
			presenter.decodeActiveRecord()
		} else if (id == R.id.btn_bookmarks) {
			presenter.applyBookmarksFilter()
		} else if (id == R.id.btn_sort) {
			showMenu(view)
		} else if (id == R.id.txt_name) {
			presenter.onRenameClick()
		} else if (id == R.id.btn_close_multi_select) {
			cancelMultiSelect()
		} else if (id == R.id.btn_delete_multi) {
			val count = adapter.getSelected().size
			AndroidUtils.showDialogYesNo(
					this@RecordsActivity,
					R.drawable.ic_delete_forever_dark,
					getString(R.string.warning),
					this.resources.getQuantityString(R.plurals.delete_selected_records, count, count)
			) { deleteSelectedRecords() }
		} else if (id == R.id.btn_share_multi) {
			shareSelectedRecords()
		} else if (id == R.id.btn_download_multi) {
			val count = adapter.getSelected().size
			AndroidUtils.showDialogYesNo(
					this@RecordsActivity,
					R.drawable.ic_save_alt_dark,
					getString(R.string.save_as),
					this.resources.getQuantityString(R.plurals.download_selected_records, count, count)
			) { downloadSelectedRecords() }
		}
	}

	private fun shareSelectedRecords() {
		val selected = adapter.getSelected()
		val share: MutableList<String> = ArrayList()
		for (i in selected.indices) {
			val item = adapter.getItem(selected[i])
			share.add(item.path)
		}
		AndroidUtils.shareAudioFiles(applicationContext, share)
		cancelMultiSelect()
	}

	private fun downloadSelectedRecords() {
		downloadRecords.clear()
		val selected = adapter.getSelected()
		for (i in selected.indices) {
			val item = adapter.getItem(selected[i])
			downloadRecords.add(item.path)
		}
		var hasPublicDir = false
		for (i in downloadRecords.indices) {
			if (isPublicDir(downloadRecords[i])) {
				hasPublicDir = true
				break
			}
		}
		if (hasPublicDir) {
			if (checkStoragePermissionDownload()) {
				//通过 Service 下载录音文件
				DownloadService.startNotification(
						applicationContext,
						ArrayList(downloadRecords)
				)
			} else {
				showError(resources.getQuantityString(
						R.plurals.downloading_failed_count,
						downloadRecords.size,
						downloadRecords.size)
				)
			}
		} else {
			//通过 Service 下载录音文件
			DownloadService.startNotification(
					applicationContext,
					ArrayList(downloadRecords)
			)
		}
		cancelMultiSelect()
		downloadRecords.clear()
	}

	private fun deleteSelectedRecords() {
		val ids: MutableList<Long> = ArrayList()
		val selected = adapter.getSelected()
		for (i in selected.indices) {
			val item = adapter.getItem(selected[i])
			ids.add(item.id)
		}
		presenter.deleteRecords(ids)
	}

	private fun stopPlayback() {
		presenter.stopPlayback()
		hidePanel()
	}

	private fun showMenu(v: View) {
		val popup = PopupMenu(v.context, v)
		popup.setOnMenuItemClickListener { item ->
			val id = item.itemId
			if (id == R.id.menu_date) {
				presenter.updateRecordsOrder(AppConstants.SORT_DATE)
			} else if (id == R.id.menu_date_desc) {
				presenter.updateRecordsOrder(AppConstants.SORT_DATE_DESC)
			} else if (id == R.id.menu_name) {
				presenter.updateRecordsOrder(AppConstants.SORT_NAME)
			} else if (id == R.id.menu_name_desc) {
				presenter.updateRecordsOrder(AppConstants.SORT_NAME_DESC)
			} else if (id == R.id.menu_duration) {
				presenter.updateRecordsOrder(AppConstants.SORT_DURATION)
			} else if (id == R.id.menu_duration_desc) {
				presenter.updateRecordsOrder(AppConstants.SORT_DURATION_DESC)
			}
			false
		}
		val inflater: MenuInflater = popup.menuInflater
		inflater.inflate(R.menu.menu_sort, popup.menu)
		AndroidUtils.insertMenuItemIcons(v.context, popup)
		popup.show()
	}

	override fun onStart() {
		super.onStart()
		presenter.bindView(this)
		presenter.loadRecords()
	}

	override fun onStop() {
		super.onStop()
		presenter.unbindView()
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		super.onBackPressed()
		ARApplication.injector.releaseRecordsPresenter()
	}

	private fun handleToolbarScroll(dy: Int) {
		var inset = toolbar.translationY - dy
		val height = toolbar.height

		if (inset < -height) {
			inset = -height.toFloat()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				toolbar.translationZ = resources.getDimension(R.dimen.toolbar_elevation)
				toolbar.setBackgroundResource(colorMap.primaryColorRes)
			}
		}

		if (toolbar.translationY <= 0 && inset > 0) {
			toolbar.translationY = 0f
		} else {
			toolbar.translationY = inset
		}
	}

	val isListOnTop: Boolean
		get() = layoutManager.findFirstCompletelyVisibleItemPosition() == 0

	val isListOnBottom: Boolean
		get() = layoutManager.findLastCompletelyVisibleItemPosition() == adapter.itemCount - 1

	private fun isPublicDir(path: String): Boolean {
		return FileUtil.getAppDir()?.let { path.contains(it.absolutePath) } == true
	}

	override fun showPlayStart() {
		btnPlay.setImageResource(R.drawable.ic_pause)
	}

	override fun showPlayPause() {
		btnPlay.setImageResource(R.drawable.ic_play)
	}

	override fun showPlayStop() {
		waveformView.moveToStart()
		btnPlay.setImageResource(R.drawable.ic_play)
		playProgress.progress = 0
		adapter.setActiveItem(-1)
	}

	override fun showNextRecord() {
	}

	override fun showPrevRecord() {
	}

	override fun showTrashBtn() {
		adapter.showTrash(true)
	}

	override fun hideTrashBtn() {
		adapter.showTrash(false)
	}

	override fun showWaveForm(waveForm: IntArray, duration: Long, playbackMills: Long) {
		waveformView.setWaveform(waveForm, duration / 1000, playbackMills)
	}

	override fun showDuration(duration: String) {
		txtProgress.text = duration
		txtDuration.text = duration
	}

	override fun onPlayProgress(mills: Long, percent: Int) {
		playProgress.progress = percent
		waveformView.setPlayback(mills)
		txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
	}

	override fun showRecords(records: List<ListItem>, order: Int) {
		if (records.isEmpty()) {
			txtEmpty.visibility = View.VISIBLE
			adapter.setData(ArrayList(), order)
		} else {
			adapter.setData(records, order)
			txtEmpty.visibility = View.GONE
			if (touchLayout.visibility == View.VISIBLE) {
				adapter.showFooter()
			}
		}
	}

	override fun addRecords(records: List<ListItem>, order: Int) {
		adapter.addData(records, order)
		txtEmpty.visibility = View.GONE
	}

	override fun showEmptyList() {
		txtEmpty.setText(R.string.no_records)
		txtEmpty.visibility = View.VISIBLE
	}

	override fun showEmptyBookmarksList() {
		txtEmpty.setText(R.string.no_bookmarks)
		txtEmpty.visibility = View.VISIBLE
	}

	override fun showPanelProgress() {
		panelProgress.visibility = View.VISIBLE
	}

	override fun hidePanelProgress() {
		panelProgress.visibility = View.GONE
	}

	override fun decodeRecord(id: Int) {
		DecodeService.startNotification(applicationContext, id)
	}

	override fun showRecordName(name: String) {
		txtName.text = name
	}

	override fun showRename(record: Record?) {
		if (record != null) {
			setRecordName(record.id.toLong(), record.name, record.format)
		}
	}

	override fun onDeleteRecord(id: Long) {
		adapter.deleteItem(id)
		if (adapter.getAudioRecordsCount() == 0) {
			showEmptyList()
		}
	}

	override fun hidePlayPanel() {
		hidePanel()
	}

	override fun addedToBookmarks(id: Int, isActive: Boolean) {
		if (isActive) {
			btnCheckBookmark.setImageResource(R.drawable.ic_bookmark)
		}
		adapter.markAddedToBookmarks(id)
	}

	override fun removedFromBookmarks(id: Int, isActive: Boolean) {
		if (isActive) {
			btnCheckBookmark.setImageResource(R.drawable.ic_bookmark_bordered)
		}
		adapter.markRemovedFromBookmarks(id)
	}

	override fun showSortType(type: Int) {
		when (type) {
			AppConstants.SORT_DATE -> txtSubTitle.setText(R.string.by_date)
			AppConstants.SORT_DATE_DESC -> txtSubTitle.setText(R.string.by_date_desc)
			AppConstants.SORT_NAME -> txtSubTitle.setText(R.string.by_name)
			AppConstants.SORT_NAME_DESC -> txtSubTitle.setText(R.string.by_name_desc)
			AppConstants.SORT_DURATION -> txtSubTitle.setText(R.string.by_duration)
			AppConstants.SORT_DURATION_DESC -> txtSubTitle.setText(R.string.by_duration_desc)
		}
	}

	override fun showActiveRecord(id: Int) {
		val pos = adapter.findPositionById(id.toLong())
		if (pos >= 0) {
			adapter.setActiveItem(pos)
		}
	}

	override fun bookmarksSelected() {
		btnBookmarks.setImageResource(R.drawable.ic_bookmark)
		txtTitle.setText(R.string.bookmarks)
		btnSort.visibility = View.GONE
		txtSubTitle.visibility = View.GONE
	}

	override fun bookmarksUnselected() {
		btnBookmarks.setImageResource(R.drawable.ic_bookmark_bordered)
		txtTitle.setText(R.string.records)
		btnSort.visibility = View.VISIBLE
		txtSubTitle.visibility = View.VISIBLE
	}

	override fun showRecordInfo(info: RecordInfo) {
		startActivity(ActivityInformation.getStartIntent(applicationContext, info))
	}

	override fun showRecordsLostMessage(list: List<Record>) {
		AndroidUtils.showLostRecordsDialog(this, list)
	}

	override fun cancelMultiSelect() {
		multiSelectPanel.visibility = View.GONE
		adapter.cancelMultiSelect()
	}

	override fun showProgress() {
		progressBar.visibility = View.VISIBLE
	}

	override fun hideProgress() {
		progressBar.visibility = View.GONE
	}

	override fun showError(message: String) {
		Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
	}

	override fun showError(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	override fun showMessage(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	fun setRecordName(recordId: Long, name: String, extension: String) {
		AndroidUtils.showRenameDialog(this, name, false, { newName ->
			if (!name.equals(newName, ignoreCase = true)) {
				presenter.renameRecord(recordId, newName, extension)
			}
		}, {}) { _, _ -> }
	}

	private fun checkStoragePermissionPlayback(): Boolean {
		return checkStoragePermission(REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK)
	}

	private fun checkStoragePermissionDownload(): Boolean {
		return checkStoragePermission(REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD)
	}

	private fun checkStoragePermission(requestCode: Int): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						arrayOf(
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE),
						requestCode)
				return false
			}
		}
		return true
	}

	@Deprecated("Deprecated in Java")
	override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
		if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.startPlayback()
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			if (downloadRecords.isNotEmpty()) {
				//通过 Service 下载录音文件
				DownloadService.startNotification(
						applicationContext,
						ArrayList(downloadRecords)
				)
				downloadRecords.clear()
			}
		}
	}

	inner class MyScrollListener(layoutManager: RecyclerView.LayoutManager) :
			EndlessRecyclerViewScrollListener(layoutManager) {

		override fun onLoadMore(page: Int, totalItemsCount: Int) {
			presenter.loadRecordsPage(page)
		}
	}

	companion object {
		const val REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK = 406
		const val REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD = 407

		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			val intent = Intent(context, RecordsActivity::class.java)
			intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
			return intent
		}
	}
}
