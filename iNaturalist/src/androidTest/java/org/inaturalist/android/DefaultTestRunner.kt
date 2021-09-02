package org.inaturalist.android

import android.content.Context
import android.os.Bundle
import androidx.test.espresso.Espresso
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.base.DefaultFailureHandler
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult

class DefaultTestRunner : AndroidJUnitRunner() {

    override fun onStart() {
        AccessibilityChecks
            .enable()
            .setRunChecksFromRootView(true)
            .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.ERROR)

        Espresso.setFailureHandler { error, viewMatcher ->
            runCatching {
                DefaultFailureHandler(targetContext)
                    .handle(error, viewMatcher)
            }.getOrThrow()
        }

        disableFirstTimeUserTutorial()

        super.onStart()
    }

    private fun disableFirstTimeUserTutorial() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesEditor = targetContext.getSharedPreferences(
            "iNaturalistPreferences",
            Context.MODE_PRIVATE
        ).edit()
        preferencesEditor.putBoolean("first_time", false)
        preferencesEditor.commit()
    }
}