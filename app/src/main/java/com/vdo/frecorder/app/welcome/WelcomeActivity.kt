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

package com.vdo.frecorder.app.welcome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.annotation.Nullable
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.R
import com.vdo.frecorder.app.setup.SetupActivity
import com.vdo.frecorder.util.AndroidUtils

/** 欢迎页。Kotlin 化（原 WelcomeActivity.java）。 */
class WelcomeActivity : Activity(), WelcomeContract.View {

	private lateinit var actionButton: Button

	override fun onCreate(@Nullable savedInstanceState: Bundle?) {
		setTheme(ARApplication.injector.provideColorMap(applicationContext).appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_welcome)

		actionButton = findViewById(R.id.btn_action)
		actionButton.setOnClickListener {
			startActivity(SetupActivity.getStartIntent(applicationContext))
			finish()
		}

		val space = findViewById<android.view.View>(R.id.navigation_height)
		val params = space.layoutParams
		params.height = AndroidUtils.getNavigationBarHeight(applicationContext)
		space.layoutParams = params
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
		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			return Intent(context, WelcomeActivity::class.java)
		}
	}
}
