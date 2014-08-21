package org.inaturalist.shedd.android;

import org.inaturalist.shedd.android.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.actionbarsherlock.view.MenuItem;

public class IdentificationActivity extends SherlockActivity {
    public static final String ID_REMARKS = "id_remarks";
    protected static final int TAXON_SEARCH_REQUEST_CODE = 301;
    public static final String TAXON_ID = "taxon_id";
    public static final String SPECIES_GUESS = "species_guess";
    public static final String ICONIC_TAXON_NAME = "iconic_taxon_name";
    private ActionBar mTopActionBar;
    private EditText mRemarks;
    private int mTaxonId = 0;
    private String mIconicTaxonName;
    private TextView mTaxonName;
    private TextView mIdName;
    private ImageView mIdPic;
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    // Respond to the action bar's Up/Home button
	    case android.R.id.home:
            setResult(RESULT_CANCELED);
            finish();
	        return true;
	    }
	    return super.onOptionsItemSelected(item);
	} 


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.new_identification);
 
        mRemarks = (EditText) findViewById(R.id.remarks);
        
        
        mTopActionBar = getSupportActionBar();
        mTopActionBar.setHomeButtonEnabled(true);
        mTopActionBar.setDisplayShowCustomEnabled(true);
        mTopActionBar.setDisplayHomeAsUpEnabled(true);
        mTopActionBar.setCustomView(R.layout.add_id_top_action_bar);
        mTopActionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#767676")));
        
        mTaxonName = (TextView) findViewById(R.id.id_taxon_name);
        mIdName = (TextView) findViewById(R.id.id_name);
        mIdPic = (ImageView) findViewById(R.id.id_pic);
        
        View saveId = (View) mTopActionBar.getCustomView().findViewById(R.id.save_id);
        saveId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putInt(TAXON_ID, mTaxonId);
                bundle.putString(ID_REMARKS, mRemarks.getText().toString());
                bundle.putString(SPECIES_GUESS, mIdName.getText().toString());
                bundle.putString(ICONIC_TAXON_NAME, mIconicTaxonName);
                intent.putExtras(bundle);

                setResult(RESULT_OK, intent);
                finish();
            }
        });
        
        
        View changeId = (View) findViewById(R.id.id_change);
        changeId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IdentificationActivity.this, TaxonSearchActivity.class);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        });
        
        // When loaded for the first time - show the taxon search dialog
        Intent intent = new Intent(IdentificationActivity.this, TaxonSearchActivity.class);
        startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                mTaxonId = data.getIntExtra(TaxonSearchActivity.TAXON_ID, 0);
                mIconicTaxonName = data.getStringExtra(TaxonSearchActivity.ICONIC_TAXON_NAME);
                mTaxonName.setText(data.getStringExtra(TaxonSearchActivity.TAXON_NAME));
                mTaxonName.setTypeface(null, Typeface.ITALIC);
                mIdName.setText(data.getStringExtra(TaxonSearchActivity.ID_NAME));
                UrlImageViewHelper.setUrlDrawable(mIdPic, data.getStringExtra(TaxonSearchActivity.ID_PIC_URL));
            } else {
                if (mTaxonId == 0) {
                    // User never selected a taxon (even once) - close this window as well
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        }
    }
        
}

