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

package com.vdo.frecorder.app.browser

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.R
import com.vdo.frecorder.app.DecodeService
import com.vdo.frecorder.app.DownloadService
import com.vdo.frecorder.app.info.ActivityInformation
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.util.AndroidUtils

/** 文件浏览器页。Kotlin 化（原 FileBrowserActivity.java）。 */
class FileBrowserActivity : Activity(), FileBrowserContract.View, View.OnClickListener {

	private lateinit var presenter: FileBrowserContract.UserActionsListener
	private lateinit var txtEmpty: TextView
	private lateinit var txtPath: TextView
	private lateinit var progressBar: ProgressBar
	private lateinit var btnPrivateDir: Button
	private lateinit var btnPublicDir: Button
	private lateinit var pnlImportProgress: LinearLayout
	private lateinit var tabsPanel: LinearLayout
	private lateinit var txtImportMessage: TextView

	private lateinit var adapter: FileBrowserAdapter

	override fun onCreate(@Nullable savedInstanceState: Bundle?) {
		setTheme(ARApplication.injector.provideColorMap(applicationContext).appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_file_browser)

		val btnBack = findViewById<ImageButton>(R.id.btn_back)
		btnBack.setOnClickListener {
			ARApplication.injector.releaseFileBrowserPresenter()
			finish()
		}

		txtEmpty = findViewById(R.id.txtEmpty)
		txtPath = findViewById(R.id.files_path)
		progressBar = findViewById(R.id.progress)
		tabsPanel = findViewById(R.id.tabs_panel)
		pnlImportProgress = findViewById(R.id.pnl_import_progress)
		txtImportMessage = findViewById(R.id.txt_import_message)

		btnPrivateDir = findViewById(R.id.tab_private_dir)
		btnPublicDir = findViewById(R.id.tab_public_dir)
		btnPrivateDir.setOnClickListener(this)
		btnPublicDir.setOnClickListener(this)

		val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
		recyclerView.layoutManager = LinearLayoutManager(applicationContext)
		adapter = FileBrowserAdapter(applicationContext, ARApplication.injector.provideSettingsMapper(applicationContext))
		adapter.setOnItemClickListener(object : FileBrowserAdapter.OnItemClickListener {
			override fun onItemClick(record: RecordInfo) {
				presenter.onRecordInfo(record)
			}

			override fun onImportItemClick(record: RecordInfo) {
				presenter.importAudioFile(applicationContext, record)
			}

			override fun onSaveAsItemClick(record: RecordInfo) {
				AndroidUtils.showDialogYesNo(
						this@FileBrowserActivity,
						R.drawable.ic_save_alt_dark,
						getString(R.string.save_as),
						getString(R.string.record_will_be_copied_into_downloads)
				) { DownloadService.startNotification(applicationContext, record.location) }
			}

			override fun onRemoveItemClick(record: RecordInfo) {
				AndroidUtils.showDialogYesNo(
						this@FileBrowserActivity,
						R.drawable.ic_delete_forever_dark,
						getString(R.string.warning),
						getString(R.string.delete_record_forever, record.name)
				) { presenter.deleteRecord(record) }
			}
		})
		recyclerView.adapter = adapter
		presenter = ARApplication.injector.provideFileBrowserPresenter(applicationContext)
	}

	override fun onStart() {
		super.onStart()
		presenter.bindView(this)
		presenter.loadFiles(applicationContext)
	}

	override fun onStop() {
		super.onStop()
		presenter.unbindView()
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		super.onBackPressed()
		ARApplication.injector.releaseFileBrowserPresenter()
	}

	override fun onClick(v: View) {
		val id = v.id
		if (id == R.id.tab_private_dir) {
			presenter.selectPrivateDir(applicationContext)
		} else if (id == R.id.tab_public_dir) {
			if (checkStoragePermissionImport(REQ_CODE_READ_EXTERNAL_STORAGE_LOAD_PUBLIC_DIR)) {
				presenter.selectPublicDir(applicationContext)
			}
		}
	}

	override fun showFileItems(items: List<RecordInfo>) {
		adapter.setData(items)
	}

	override fun showSelectedPrivateDir() {
		btnPrivateDir.setBackgroundResource(R.color.white_transparent_80)
		btnPublicDir.setBackgroundResource(R.color.transparent)
	}

	override fun showSelectedPublicDir() {
		btnPublicDir.setBackgroundResource(R.color.white_transparent_80)
		btnPrivateDir.setBackgroundResource(R.color.transparent)
	}

	override fun showRecordInfo(info: RecordInfo) {
		startActivity(ActivityInformation.getStartIntent(applicationContext, info))
	}

	override fun onDeletedRecord(path: String) {
		adapter.removeItem(path)
		Toast.makeText(applicationContext, R.string.record_deleted_successfully, Toast.LENGTH_LONG).show()
	}

	override fun onImportedRecord(path: String) {
		adapter.setRecordInDatabase(path)
		Toast.makeText(applicationContext, R.string.record_imported_successfully, Toast.LENGTH_LONG).show()
	}

	override fun updatePath(path: String) {
		txtPath.text = getString(R.string.records_location, path)
	}

	override fun decodeRecord(id: Int) {
		DecodeService.startNotification(applicationContext, id)
	}

	override fun showTabs(value: Boolean) {
		tabsPanel.visibility = if (value) View.VISIBLE else View.GONE
	}

	override fun showEmpty() {
		adapter.clearData()
		txtEmpty.visibility = View.VISIBLE
	}

	override fun hideEmpty() {
		txtEmpty.visibility = View.GONE
	}

	override fun showRecordProcessing() {
		txtImportMessage.setText(R.string.record_processing)
		pnlImportProgress.visibility = View.VISIBLE
	}

	override fun hideRecordProcessing() {
		pnlImportProgress.visibility = View.GONE
	}

	override fun showImportStart() {
		txtImportMessage.setText(R.string.import_progress)
		pnlImportProgress.visibility = View.VISIBLE
	}

	override fun hideImportProgress() {
		pnlImportProgress.visibility = View.GONE
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

	private fun checkStoragePermissionImport(code: Int): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						arrayOf(
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE),
						code)
				return false
			}
		}
		return true
	}

	@Deprecated("Deprecated in Java")
	override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
		if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_LOAD_PUBLIC_DIR && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.selectPublicDir(applicationContext)
		}
	}

	companion object {
		const val REQ_CODE_READ_EXTERNAL_STORAGE_LOAD_PUBLIC_DIR = 466

		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			return Intent(context, FileBrowserActivity::class.java)
		}
	}
}
