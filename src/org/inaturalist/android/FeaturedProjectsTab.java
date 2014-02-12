package org.inaturalist.android;

public class FeaturedProjectsTab extends BaseProjectsTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_FEATURED_PROJECTS;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_FEATURED_PROJECTS_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.PROJECTS_RESULT;
    }
 
}
