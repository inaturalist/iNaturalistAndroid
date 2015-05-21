package org.inaturalist.android;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockFragment;

public class TutorialFragment extends SherlockFragment {
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.tutorial_page, container, false);
        
        Bundle args = getArguments();
        
        int index = args.getInt("id");
        
        ImageView imageView = (ImageView) v.findViewById(R.id.tutorial_image);
 
       INaturalistApp app = (INaturalistApp) getActivity().getApplicationContext();
       String inatNetwork = app.getInaturalistNetworkMember();
            
       String[] images;

       if (inatNetwork == null) {
    	   // No network selected - use default tutorial images
    	   images = getResources().getStringArray(R.array.tutorial_images);
       } else {
    	   // Use network specific tutorial images
    	   String imagesArrayName = app.getStringResourceByName("inat_tutorial_images_" + inatNetwork);
    	   images = app.getStringArrayResourceByName(imagesArrayName);
       }

        int resID = getResources().getIdentifier("@drawable/" + images[index] , "drawable", getActivity().getApplicationContext().getPackageName());
        imageView.setImageResource(resID);
        
        return v;
    }
}
