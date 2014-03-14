package org.inaturalist.android;

public class NearByGuidesTab extends BaseGuidesTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_NEAR_BY_GUIDES;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_NEAR_BY_GUIDES_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.GUIDES_RESULT;
    }
 
}
