package org.inaturalist.android;

public class JoinedProjectsTab extends BaseProjectsTab {
    @Override
    protected String getActionName() {
        return INaturalistService.ACTION_GET_JOINED_PROJECTS;
    }
    
    @Override
    protected String getFilterResultName() {
        return INaturalistService.ACTION_JOINED_PROJECTS_RESULT;
    }
    
    @Override
    protected String getFilterResultParamName() {
        return INaturalistService.PROJECTS_RESULT;
    }
    
    @Override
    protected boolean requiresLogin() {
        return true;
    }
 
 
    @Override
    protected String getUserLoginRequiredText() {
    	return getResources().getString(R.string.please_sign_in_via_settings);
    }
}
