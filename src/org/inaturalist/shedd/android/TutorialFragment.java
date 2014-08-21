package org.inaturalist.shedd.android;

import org.inaturalist.shedd.android.R;

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
 
        String[] images = getResources().getStringArray(R.array.tutorial_images);
        int resID = getResources().getIdentifier("@drawable/" + images[index] , "drawable", getActivity().getApplicationContext().getPackageName());
        imageView.setImageResource(resID);
        
        return v;
    }
}
