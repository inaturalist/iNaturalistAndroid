package org.inaturalist.android;

public class AllGuidesTab extends BaseGuidesTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_ALL_GUIDES;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_ALL_GUIDES_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.GUIDES_RESULT;
    }
 
}
