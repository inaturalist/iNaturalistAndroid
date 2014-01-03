package org.inaturalist.android;

public class GroupProjectsTab extends BaseProjectsTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_GROUP_PROJECTS;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_GROUP_PROJECTS_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.PROJECTS_RESULT;
    }
 
}
