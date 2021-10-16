package org.inaturalist.android

import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.Matcher

object RecyclerViewHelpers {
    fun requireAssertions(viewAssertions: List<(View) -> Boolean>) = object : ViewAction {
        override fun getConstraints(): Matcher<View>? = null

        override fun getDescription(): String = "Action view holder assert all predicates true"

        override fun perform(uiController: UiController, view: View) {
            check(viewAssertions.all { it(view) })
        }
    }
}