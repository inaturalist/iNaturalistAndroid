package org.inaturalist.android;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public abstract class BaseProjectsTab extends BaseTab {
	private static final int PROJECT_REQUEST_CODE = 101;
	private int mIndex;
	
    protected void onItemSelected(BetterJSONObject item, int index) {
    	// Show project details
    	Intent intent = new Intent(getActivity(), ProjectDetails.class);
    	intent.putExtra("project", item);
    	startActivityForResult(intent, PROJECT_REQUEST_CODE);
    	
    	mIndex = index;
    }
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

         if (requestCode == PROJECT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                BetterJSONObject project = (BetterJSONObject) data.getSerializableExtra("project");
                
                if (project != null) updateProject(mIndex, project);
            }
         }
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
    
    protected String getSearchUrl() {
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

        return "http://" + inatHost + "/projects/search.json";
    }

    protected boolean recallServiceActionIfNoResults() {
        return false;
    }
}
