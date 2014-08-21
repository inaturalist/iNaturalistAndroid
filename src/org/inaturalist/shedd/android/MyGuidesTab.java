package org.inaturalist.shedd.android;

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
 
}
