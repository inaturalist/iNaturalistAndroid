package org.inaturalist.android;

public class NearByProjectsTab extends BaseProjectsTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_NEARBY_PROJECTS;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_NEARBY_PROJECTS_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.PROJECTS_RESULT;
    }
 
}
