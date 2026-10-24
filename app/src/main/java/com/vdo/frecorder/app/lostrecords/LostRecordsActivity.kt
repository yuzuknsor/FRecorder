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

package com.vdo.frecorder.app.lostrecords

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.browser.FileBrowserActivity
import com.vdo.frecorder.app.info.ActivityInformation
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.util.AndroidUtils

/** 丢失录音页。Kotlin 化（原 LostRecordsActivity.java）。 */
class LostRecordsActivity : Activity(), LostRecordsContract.View {

	private var presenter: LostRecordsContract.UserActionsListener? = null
	private lateinit var txtEmpty: TextView
	private lateinit var adapter: LostRecordsAdapter

	override fun onCreate(@Nullable savedInstanceState: Bundle?) {
		setTheme(ARApplication.injector.provideColorMap(applicationContext).appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_lost_records)

		val btnBack = findViewById<ImageButton>(R.id.btn_back)
		btnBack.setOnClickListener {
			ARApplication.injector.releaseLostRecordsPresenter()
			finish()
		}
		findViewById<View>(R.id.btn_file_browser).setOnClickListener {
			startActivity(FileBrowserActivity.getStartIntent(applicationContext))
		}

		txtEmpty = findViewById(R.id.txtEmpty)
		val btnDeleteAll = findViewById<TextView>(R.id.btn_delete_all)
		btnDeleteAll.setOnClickListener {
			AndroidUtils.showDialogYesNo(
					this@LostRecordsActivity,
					R.drawable.ic_delete_forever_dark,
					getString(R.string.warning),
					getString(R.string.delete_all_records)
			) { presenter?.deleteRecords(adapter.getData()) }
		}

		val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
		recyclerView.layoutManager = LinearLayoutManager(applicationContext)
		adapter = LostRecordsAdapter()
		adapter.setOnItemClickListener(object : LostRecordsAdapter.OnItemClickListener {
			override fun onItemClick(record: RecordItem) {
				presenter?.onRecordInfo(Mapper.toRecordInfo(record)!!)
			}

			override fun onRemoveItemClick(record: RecordItem) {
				AndroidUtils.showDialogYesNo(
						this@LostRecordsActivity,
						R.drawable.ic_delete_forever_dark,
						getString(R.string.warning),
						getString(R.string.delete_record, record.name)
				) { presenter?.deleteRecord(record) }
			}
		})
		recyclerView.adapter = adapter
		intent?.extras?.let { extras ->
			if (extras.containsKey(EXTRAS_RECORDS_LIST)) {
				@Suppress("DEPRECATION")
				extras.getParcelableArrayList<RecordItem>(EXTRAS_RECORDS_LIST)?.let { adapter.setData(it) }
			}
		}

		presenter = ARApplication.injector.provideLostRecordsPresenter(applicationContext)
	}

	override fun onStart() {
		super.onStart()
		presenter?.bindView(this)
	}

	override fun onStop() {
		super.onStop()
		presenter?.unbindView()
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		super.onBackPressed()
		ARApplication.injector.releaseLostRecordsPresenter()
	}

	override fun showLostRecords(items: List<RecordItem>) {
		adapter.setData(items)
	}

	override fun showRecordInfo(info: RecordInfo) {
		startActivity(ActivityInformation.getStartIntent(applicationContext, info))
	}

	override fun onDeletedRecord(id: Int) {
		adapter.removeItem(id)
		if (adapter.itemCount == 0) {
			showEmpty()
		}
	}

	override fun showEmpty() {
		adapter.clearData()
		txtEmpty.visibility = View.VISIBLE
	}

	override fun hideEmpty() {
		txtEmpty.visibility = View.GONE
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

	companion object {
		const val EXTRAS_RECORDS_LIST = "records_list"

		@JvmStatic
		fun getStartIntent(context: Context, data: ArrayList<RecordItem>): Intent {
			val intent = Intent(context, LostRecordsActivity::class.java)
			intent.putParcelableArrayListExtra(EXTRAS_RECORDS_LIST, data)
			return intent
		}
	}
}
