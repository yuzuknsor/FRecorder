/*
 * Copyright 2024 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vdo.frecorder.app.quickmic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.vdo.frecorder.ARApplication;
import com.vdo.frecorder.R;
import com.vdo.frecorder.app.AppRecorder;
import com.vdo.frecorder.app.RecordingService;
import com.vdo.frecorder.app.records.RecordsActivity;
import com.vdo.frecorder.audio.AudioDeviceManager;
import com.vdo.frecorder.audio.RecordingDeviceOverride;
import com.vdo.frecorder.data.FileRepository;
import com.vdo.frecorder.exception.CantCreateFileException;
import com.vdo.frecorder.util.TimeUtils;

/**
 * Quick recording screen that always captures from the phone's own built-in
 * microphone, no matter what audio input is selected in the settings.
 *
 * Use case: listening to music through a USB-C dongle / headset while recording
 * with the phone mic. Without an explicit device the framework routes recording
 * to the (possibly non-existent) headset microphone that the dongle reports.
 */
public class QuickMicActivity extends Activity implements View.OnClickListener {

	private static final int REQ_CODE_RECORD_AUDIO = 512;

	private TextView txtDevice;
	private TextView txtStatus;
	private TextView btnToggle;

	private AppRecorder appRecorder;
	private AudioDeviceManager audioDeviceManager;
	private FileRepository fileRepository;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable ticker = new Runnable() {
		@Override
		public void run() {
			updateState();
			handler.postDelayed(this, 300);
		}
	};

	public static Intent getStartIntent(Context context) {
		Intent intent = new Intent(context, QuickMicActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_quick_mic);

		appRecorder = ARApplication.getInjector().provideAppRecorder(getApplicationContext());
		audioDeviceManager = ARApplication.getInjector().provideAudioDeviceManager(getApplicationContext());
		fileRepository = ARApplication.getInjector().provideFileRepository(getApplicationContext());

		txtDevice = findViewById(R.id.txt_device);
		txtStatus = findViewById(R.id.txt_status);
		btnToggle = findViewById(R.id.btn_toggle);

		findViewById(R.id.btn_back).setOnClickListener(this);
		btnToggle.setOnClickListener(this);
		findViewById(R.id.btn_view_records).setOnClickListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		showDeviceInfo();
		updateState();
		handler.post(ticker);
	}

	@Override
	protected void onPause() {
		super.onPause();
		handler.removeCallbacks(ticker);
	}

	private void showDeviceInfo() {
		AudioDeviceInfo mic = audioDeviceManager != null ? audioDeviceManager.getBuiltInMicDevice() : null;
		if (mic != null) {
			txtDevice.setText(getString(R.string.quick_mic_device,
					AudioDeviceManager.getDeviceDisplayName(this, mic)));
		} else {
			txtDevice.setText(R.string.quick_mic_device_missing);
		}
	}

	private void updateState() {
		boolean recording = appRecorder != null && appRecorder.isRecording();
		if (recording) {
			btnToggle.setText(R.string.quick_mic_stop);
			txtStatus.setText(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.getRecordingDuration()));
		} else {
			btnToggle.setText(R.string.quick_mic_start);
			txtStatus.setText(R.string.zero_time);
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.btn_back) {
			finish();
		} else if (id == R.id.btn_toggle) {
			if (appRecorder != null && appRecorder.isRecording()) {
				stopRecording();
			} else {
				if (hasRecordPermission()) {
					startRecording();
				} else {
					requestRecordPermission();
				}
			}
		} else if (id == R.id.btn_view_records) {
			startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
		}
	}

	private boolean hasRecordPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
		}
		return true;
	}

	private void requestRecordPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQ_CODE_RECORD_AUDIO) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startRecording();
			} else {
				Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_LONG).show();
			}
		}
	}

	private void startRecording() {
		AudioDeviceInfo mic = audioDeviceManager != null ? audioDeviceManager.getBuiltInMicDevice() : null;
		if (mic != null) {
			// One-shot override: RecordingService picks this device instead of the saved
			// preference, then discards it. The saved preference is never modified.
			RecordingDeviceOverride.force(mic.getId());
		}
		try {
			String path = fileRepository.provideRecordFile().getAbsolutePath();
			Intent intent = new Intent(getApplicationContext(), RecordingService.class);
			intent.setAction(RecordingService.ACTION_START_RECORDING_SERVICE);
			intent.putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, path);
			startService(intent);
			if (mic == null) {
				Toast.makeText(this, R.string.quick_mic_device_missing, Toast.LENGTH_LONG).show();
			}
		} catch (CantCreateFileException e) {
			Toast.makeText(this, R.string.error_cant_create_file, Toast.LENGTH_LONG).show();
		}
	}

	private void stopRecording() {
		Intent intent = new Intent(getApplicationContext(), RecordingService.class);
		intent.setAction(RecordingService.ACTION_STOP_RECORDING);
		startService(intent);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(ticker);
	}
}
