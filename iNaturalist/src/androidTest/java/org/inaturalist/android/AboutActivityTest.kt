package org.inaturalist.android

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test

class AboutActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AboutActivity::class.java)

    @Test
    fun verifyCreditsSection() {
        onView(withText(R.string.credits_title))
            .perform(click())

        onView(withId(R.id.inat_credits))
            .check(matches(isDisplayed()))

        Espresso.pressBack()

        onView(withText(R.string.credits_title))
            .check(matches(isDisplayed()))
    }
}
