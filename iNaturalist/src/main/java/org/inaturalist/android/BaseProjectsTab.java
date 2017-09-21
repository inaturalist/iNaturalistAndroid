package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public abstract class BaseProjectsTab extends BaseTab {
	private static final int PROJECT_REQUEST_CODE = 101;
	private int mIndex;

    protected void onItemSelected(BetterJSONObject item, int index) {
    	// Show project details
    	Intent intent = new Intent(getActivity(), ProjectDetails.class);
    	intent.putExtra("project", item.getJSONObject().toString());
    	startActivityForResult(intent, PROJECT_REQUEST_CODE);
    	
    	mIndex = index;
    }
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

         if (requestCode == PROJECT_REQUEST_CODE) {
            if ((resultCode == Activity.RESULT_OK) || (resultCode == ProjectDetails.RESULT_REFRESH_RESULTS)) {
                BetterJSONObject project = new BetterJSONObject(data.getStringExtra("project"));
                
                if (project != null) updateProject(mIndex, project);
            }
         }
    }
    
    public static String getSearchFilterTextHint(Context context) {
    	return context.getResources().getString(R.string.search_projects);
    }

    protected String getNoItemsFoundText() {
    	return getResources().getString(R.string.no_projects);
    }

    protected String getNoInternetText() {
    	return getResources().getString(R.string.no_internet_projects);
    }
    
    public static String getSearchUrl(INaturalistApp app) {
        String inatNetwork = app.getInaturalistNetworkMember();
        String inatHost = app.getStringResourceByName("inat_host_" + inatNetwork);

        return inatHost + "/projects/search.json";
    }

    protected boolean recallServiceActionIfNoResults() {
        return false;
    }
}
