package org.inaturalist.android

import androidx.test.espresso.Espresso
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.base.DefaultFailureHandler
import androidx.test.runner.AndroidJUnitRunner
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult

class DefaultTestRunner: AndroidJUnitRunner() {

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
        super.onStart()
    }
}