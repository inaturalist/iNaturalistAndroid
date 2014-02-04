package org.inaturalist.android;

import android.content.Intent;

public abstract class BaseGuidesTab extends BaseTab {
    protected void onItemSelected(BetterJSONObject item) {
    	// Show guide details
    	Intent intent = new Intent(getActivity(), GuideDetails.class);
    	intent.putExtra("guide", item);
    	startActivity(intent);  
    }
    
    
    protected String getSearchFilterTextHint() {
    	return getResources().getString(R.string.search_guides);
    }

    protected String getNoItemsFoundText() {
    	return getResources().getString(R.string.no_guides);
    }

}
