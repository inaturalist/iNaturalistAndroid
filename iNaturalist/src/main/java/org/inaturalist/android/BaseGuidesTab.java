package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;

public abstract class BaseGuidesTab extends BaseTab {
    protected void onItemSelected(BetterJSONObject item, int index) {
    	// Show guide details
    	Intent intent = new Intent(getActivity(), GuideDetails.class);
    	intent.putExtra("guide", item);
    	startActivity(intent);  
    }
    
    
    public static String getSearchFilterTextHint(Context context) {
    	return context.getResources().getString(R.string.search_guides);
    }

    protected String getNoItemsFoundText() {
    	return getResources().getString(R.string.no_guides);
    }

    protected String getNoInternetText() {
    	return getResources().getString(R.string.no_internet_guides);
    }

    protected int getDefaultIcon() {
        return R.drawable.ic_book_black_24dp;
    }

    public static String getSearchUrl(INaturalistApp app) {
        String inatNetwork = app.getInaturalistNetworkMember();
        String inatHost = app.getStringResourceByName("inat_host_" + inatNetwork);

        return inatHost + "/guides/search.json";
    }

    protected boolean recallServiceActionIfNoResults() {
        return false;
    }


}
