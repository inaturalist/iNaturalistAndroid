package org.inaturalist.manitoba.android;

public class MyGuidesTab extends BaseGuidesTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_MY_GUIDES;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_MY_GUIDES_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.GUIDES_RESULT;
    }

    @Override
    protected boolean recallServiceActionIfNoResults() {
        // If the search filter returns no results - load up the default my guides + offline guides
        return true;
    }
}
