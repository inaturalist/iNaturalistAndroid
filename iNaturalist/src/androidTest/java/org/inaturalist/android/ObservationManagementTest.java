package org.inaturalist.android;

import android.app.Activity;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.test.espresso.IdlingResource;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.support.test.runner.lifecycle.Stage;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.Objects;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.registerIdlingResources;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class ObservationManagementTest {

    @Rule
    public ActivityTestRule<ObservationEditor> observationEditorRule = new ActivityTestRule(ObservationEditor.class, true, false);
    @Rule
    public ActivityTestRule<ObservationListActivity> observationListRule = new ActivityTestRule(ObservationListActivity.class, true, false);

    // This class is used to test whether or not at least 3 taxon results were loaded into the list view
    // (this means the listview contains results that were returned from the server, and not the default ones)
    private class TaxonLoaderIdlingResource implements IdlingResource {
        private ResourceCallback mCallback;
        private Activity mActivity;
        private int mCount = 0;

        public TaxonLoaderIdlingResource(Activity activity) {
            mActivity = activity;
            final ListView listView = (ListView) mActivity.findViewById(android.R.id.list);
            listView.getAdapter().registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    super.onChanged();
                    mCount = listView.getAdapter().getCount();
                    if (mCount >= 3) {
                        mCallback.onTransitionToIdle();
                    }
                }

                @Override
                public void onInvalidated() {
                    super.onInvalidated();
                }
            });
        }

        @Override
        public String getName() {
            return "TaxonLoaderIdlingResource";
        }

        @Override
        public boolean isIdleNow() {
            return mCount >= 3;
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            mCallback = callback;
        }
    }

    // Utility method to retrieve the currently-displayed activity
    public Activity getCurrentActivity(){
        final Activity currentActivity[] = new Activity[] { null };
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                Collection resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
                if (resumedActivities.iterator().hasNext()){
                    currentActivity[0] = (Activity) resumedActivities.iterator().next();
                }
            }
        });

        return currentActivity[0];
    }

    @Test
    public void addNewObservation() {
        // Add a new observation

        // New observation (no photo)
        Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI);
        observationEditorRule.launchActivity(intent);

        // Set some details
        onView(withId(R.id.description)).perform(typeText("Some notes..."));

        // Taxon text - This triggers the taxon search activity
        onView(withId(R.id.speciesGuess)).perform(typeText("Human"));

        // This will delay the rest of the tests until the taxon results are loaded up from the server
        registerIdlingResources(new TaxonLoaderIdlingResource(getCurrentActivity()));

        // Choose a taxon result (that was downloaded from the server)
        onView(withId(android.R.id.list)).perform(click());

        // Back in observation editor screen

        // Click the save observation button
        onView(withId(R.id.save_observation)).perform(click());
    }

    @Test
    public void viewObservationAndEdit() {
        // Views an existing observation and edit it

        // View all observations
        Intent intent = new Intent();
        observationListRule.launchActivity(intent);

        // Choose first observation to view
        onView(withId(R.id.observations_list)).perform(click());

        // Now we're in the observation viewer screen - click the edit button
        onView(withId(R.id.edit_observation)).perform(click());

        // Click the save observation button
        onView(withId(R.id.save_observation)).perform(click());
    }
}

