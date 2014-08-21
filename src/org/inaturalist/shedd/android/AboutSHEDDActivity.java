package org.inaturalist.shedd.android;

import org.inaturalist.shedd.android.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;


public class AboutSHEDDActivity extends SherlockActivity {
	private static final String TAG = "AboutSHEDDActivity";
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
         case android.R.id.home:
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		ActionBar actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);

	    setContentView(R.layout.about_shedd);
	    
	    /*
	    TextView about = (TextView) findViewById(R.id.about_text);
	    about.setText(Html.fromHtml(about.getText().toString()));
	    about.setMovementMethod(LinkMovementMethod.getInstance()); 
	    */
	}
	
}
