/*
 * Copyright 2020 Dmytro Ponomarenko
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

package com.vdo.frecorder.app.trash

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.info.ActivityInformation
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.lostrecords.RecordItem
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.RippleUtils

/** 回收站页。Kotlin 化（原 TrashActivity.java）。 */
class TrashActivity : Activity(), TrashContract.View {

	private lateinit var presenter: TrashContract.UserActionsListener

	private lateinit var adapter: TrashAdapter
	private lateinit var txtEmpty: TextView
	private lateinit var btnDeleteAll: TextView

	override fun onCreate(@Nullable savedInstanceState: Bundle?) {
		setTheme(ARApplication.injector.provideColorMap(applicationContext).appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_trash)

		val btnBack = findViewById<ImageButton>(R.id.btn_back)
		btnBack.setOnClickListener {
			ARApplication.injector.releaseTrashPresenter()
			finish()
		}

		btnDeleteAll = findViewById(R.id.btn_delete_all)
		btnDeleteAll.background = RippleUtils.createRippleShape(
				ContextCompat.getColor(applicationContext, R.color.white_transparent_80),
				ContextCompat.getColor(applicationContext, R.color.white_transparent_50),
				applicationContext.resources.getDimension(R.dimen.spacing_normal)
		)
		btnDeleteAll.setOnClickListener {
			AndroidUtils.showDialogYesNo(
					this@TrashActivity,
					R.drawable.ic_delete_forever_dark,
					getString(R.string.warning),
					getString(R.string.delete_all_records)
			) { presenter.deleteAllRecordsFromTrash() }
		}

		txtEmpty = findViewById(R.id.txtEmpty)
		val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
		recyclerView.layoutManager = LinearLayoutManager(applicationContext)
		adapter = TrashAdapter()
		adapter.setOnItemClickListener(object : TrashAdapter.OnItemClickListener {
			override fun onItemClick(record: RecordItem) {
				presenter.onRecordInfo(Mapper.toRecordInfoInTrash(record)!!)
			}

			override fun onDeleteItemClick(record: RecordItem) {
				AndroidUtils.showDialogYesNo(
						this@TrashActivity,
						R.drawable.ic_delete_forever_dark,
						getString(R.string.warning),
						getString(R.string.delete_record_forever, record.name)
				) { presenter.deleteRecordFromTrash(record.id, record.path) }
			}

			override fun onRestoreItemClick(record: RecordItem) {
				AndroidUtils.showDialogYesNo(
						this@TrashActivity,
						R.drawable.ic_restore_from_trash,
						getString(R.string.warning),
						getString(R.string.restore_record, record.name)
				) { presenter.restoreRecordFromTrash(record.id) }
			}
		})
		recyclerView.adapter = adapter
		presenter = ARApplication.injector.provideTrashPresenter(applicationContext)
	}

	override fun onStart() {
		super.onStart()
		presenter.bindView(this)
	}

	override fun onStop() {
		super.onStop()
		presenter.unbindView()
	}

	override fun showProgress() {
	}

	override fun hideProgress() {
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

	override fun showRecords(items: List<RecordItem>) {
		adapter.setData(items)
	}

	override fun showRecordInfo(info: RecordInfo) {
		startActivity(ActivityInformation.getStartIntent(applicationContext, info))
	}

	override fun recordDeleted(resId: Int) {
		adapter.removeItem(resId)
		if (adapter.itemCount == 0) {
			showEmpty()
		}
	}

	override fun recordRestored(resId: Int) {
		adapter.removeItem(resId)
		if (adapter.itemCount == 0) {
			showEmpty()
		}
	}

	override fun allRecordsRemoved() {
		adapter.clearData()
		showEmpty()
	}

	override fun showEmpty() {
		txtEmpty.visibility = View.VISIBLE
		btnDeleteAll.visibility = View.GONE
	}

	override fun hideEmpty() {
		txtEmpty.visibility = View.GONE
		btnDeleteAll.visibility = View.VISIBLE
	}

	companion object {
		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			return Intent(context, TrashActivity::class.java)
		}
	}
}
