package org.inaturalist.shedd.android;

import org.inaturalist.shedd.android.R;

import android.content.Intent;

public abstract class BaseProjectsTab extends BaseTab {
    protected void onItemSelected(BetterJSONObject item) {
    	// Show project details
    	Intent intent = new Intent(getActivity(), ProjectDetails.class);
    	intent.putExtra("project", item);
    	startActivity(intent);  
    }
    
    
    protected String getSearchFilterTextHint() {
    	return getResources().getString(R.string.search_projects);
    }

    protected String getNoItemsFoundText() {
    	return getResources().getString(R.string.no_projects);
    }

    protected String getNoInternetText() {
    	return getResources().getString(R.string.no_internet_projects);
    }
}
