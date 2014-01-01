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
 
        LayerDrawable tutorialImages = (LayerDrawable) getResources().getDrawable(R.drawable.tutorial_images);
        Drawable image = tutorialImages.getDrawable(index);
        
        imageView.setImageDrawable(image);
        
        return v;
    }
}
