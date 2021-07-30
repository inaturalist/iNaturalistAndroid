package org.inaturalist.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.BundleMatchers.hasKey
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.core.AllOf
import org.junit.Rule
import org.junit.Test

class AboutActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AboutActivity::class.java)

    @Test
    fun verifyContactSupportSection() {
        // Start inspecting launched intents.
        Intents.init()
        val intentParameters = AllOf.allOf(
            hasAction(Intent.ACTION_CHOOSER),
            hasExtras(hasKey(Intent.EXTRA_INTENT)),
            hasExtras(hasKey(Intent.EXTRA_TITLE)),
        )

        Intents.intending(intentParameters)
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        onView(withText(R.string.contact_support))
            .perform(click())

        // Stop inspecting launched intents.
        Intents.intended(intentParameters)
        Intents.release()
    }

    @Test
    fun verifyCreditsSection() {
        onView(withText(R.string.credits_title))
            .perform(click())

        onView(withId(R.id.inat_credits))
            .check(matches(isDisplayed()))

        pressBack()

        onView(withText(R.string.credits_title))
            .check(matches(isDisplayed()))
    }

    @Test
    fun verifyStoreSection() {
        Intents.init()
        val intentParameters = AllOf.allOf(
            hasAction(Intent.ACTION_VIEW),
            hasData(Uri.parse(SHOP_URL)),
        )

        onView(withText(R.string.shop_the_inat_store))
            .perform(click())

        Intents.intended(intentParameters)
        Intents.release()
    }

    @Test
    fun verifyDonateSection() {
        Intents.init()
        val intentParameters = AllOf.allOf(
            hasAction(Intent.ACTION_VIEW),
            hasData(Uri.parse(DONATION_URL)),
        )

        onView(withText(R.string.shop_the_inat_store))
            .perform(click())

        Intents.intended(intentParameters)
        Intents.release()
    }

    @Test
    fun verifyPrivacyPolicySection() {
        Intents.init()
        val intentParameters = AllOf.allOf(
            hasAction(Intent.ACTION_VIEW),
            hasData(Uri.parse(PRIVACY_POLICY_URL)),
        )

        onView(withText(R.string.shop_the_inat_store))
            .perform(click())

        Intents.intended(intentParameters)
        Intents.release()
    }

    @Test
    fun verifyTermsOfServiceSection() {
        Intents.init()
        val intentParameters = AllOf.allOf(
            hasAction(Intent.ACTION_VIEW),
            hasData(Uri.parse(TOS_URL)),
        )

        onView(withText(R.string.shop_the_inat_store))
            .perform(click())

        Intents.intended(intentParameters)
        Intents.release()
    }

    @Test
    fun verifyVersionSection() {
        onView(withText(R.string.version))
            .check(matches(isDisplayed()))
    }

    companion object {
        private const val DONATION_URL =
            "http://www.inaturalist.org/donate?utm_source=Android&utm_medium=mobile"
        private const val SHOP_URL =
            "https://store.inaturalist.org/?utm_source=android&utm_medium=mobile&utm_campaign=store"
        private const val TOS_URL =
            "https://www.inaturalist.org/terms"
        private const val PRIVACY_POLICY_URL =
            "https://www.inaturalist.org/privacy"
    }
}
