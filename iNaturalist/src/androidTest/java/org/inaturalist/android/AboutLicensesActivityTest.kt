package org.inaturalist.android

import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test

class AboutLicensesActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AboutLicensesActivity::class.java)

    @Test
    fun verifyPublicDomainLicensesSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(PUBLIC_DOMAIN_LICENSE)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isVisible },
                            { it.findViewById<View>(R.id.gbif).isVisible }
                        )
                    )
                )
            )
    }
    @Test
    fun verifyAttributionLicensesSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ATTRIBUTION_LICENSE)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isVisible },
                            { it.findViewById<View>(R.id.gbif).isVisible }
                        )
                    )
                )
            )
    }

    @Test
    fun verifyAttributionNonCommercialLicensesSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ATTRIBUTION_NONCOMMERCIAL)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isGone },
                            { it.findViewById<View>(R.id.gbif).isVisible }
                        )
                    )
                )
            )
    }
    @Test
    fun verifyAttributionNonCommercialShareAlikeLicensesSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ATTRIBUTION_NONCOMERCIAL_SHAREALIKE)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isGone },
                            { it.findViewById<View>(R.id.gbif).isGone }
                        )
                    )
                )
            )
    }
    @Test
    fun verifyAttributionNonCommercialNoDerivsLicensesSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ATTRIBUTION_NONCOMERCIAL_NODERIVS)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isGone },
                            { it.findViewById<View>(R.id.gbif).isGone }
                        )
                    )
                )
            )
    }
    @Test
    fun verifyAttributionNoDerivsLicensesSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ATTRIBUTION_NODERIVS)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isGone },
                            { it.findViewById<View>(R.id.gbif).isGone }
                        )
                    )
                )
            )
    }
    @Test
    fun verifyAttributionShareAlikeSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ATTRIBUTION_SHAREALIKE)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isVisible },
                            { it.findViewById<View>(R.id.wikimedia).isVisible },
                            { it.findViewById<View>(R.id.gbif).isGone }
                        )
                    )
                )
            )
    }
    @Test
    fun verifyAllRightsReservedSection() {
        onView(withId(R.id.licenses_list))
            .perform(
                RecyclerViewActions.actionOnItem<AboutLicensesAdapter.ViewHolder>(
                    hasDescendant(withText(ALL_RIGHTS_RESERVED)),
                    RecyclerViewHelpers.requireAssertions(
                        listOf(
                            { it.findViewById<View>(R.id.license_link).isGone },
                            { it.findViewById<View>(R.id.wikimedia).isGone },
                            { it.findViewById<View>(R.id.gbif).isGone }
                        )
                    )
                )
            )
    }

    companion object {
        private const val PUBLIC_DOMAIN_LICENSE = "Public Domain"
        private const val ATTRIBUTION_LICENSE = "Attribution"
        private const val ATTRIBUTION_NONCOMMERCIAL = "Attribution-NonCommercial"
        private const val ATTRIBUTION_NONCOMERCIAL_SHAREALIKE = "Attribution-NonCommercial-ShareAlike"
        private const val ATTRIBUTION_NONCOMERCIAL_NODERIVS = "Attribution-NonCommercial-NoDerivs"
        private const val ATTRIBUTION_NODERIVS = "Attribution-NoDerivs"
        private const val ATTRIBUTION_SHAREALIKE = "Attribution-ShareAlike"
        private const val ALL_RIGHTS_RESERVED = "All rights reserved"
    }
}