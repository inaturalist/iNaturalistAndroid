package org.inaturalist.android;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class IdentificationActivity extends Activity {
	public static final String TAXON_ID = "taxon_id";
	public static final String ID_NAME = "id_name";
	public static final String TAXON_NAME = "taxon_name";
    public static final String ID_REMARKS = "id_remarks";
    public static final String ID_PIC_URL = "id_url";
    protected static final int TAXON_SEARCH_REQUEST_CODE = 301;
    private ActionBar mTopActionBar;
    private MultilineEditText mRemarks;
    private int mTaxonId = 0;
    
    private TextView mTaxonName;
    private TextView mIdName;
    private ImageView mIdPic;

     private class BackAction extends AbstractAction {

        public BackAction() {
            super(R.drawable.back);
        }

        @Override
        public void performAction(View view) {
            finish();
        }

    }
   
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_identification);
 
        mRemarks = (MultilineEditText) findViewById(R.id.remarks);
        
        mTopActionBar = (ActionBar) findViewById(R.id.top_actionbar);
        mTopActionBar.setHomeAction(new BackAction());
        
        mTaxonName = (TextView) findViewById(R.id.id_taxon_name);
        mIdName = (TextView) findViewById(R.id.id_name);
        mIdPic = (ImageView) findViewById(R.id.id_pic);
        
        Button saveId = (Button) findViewById(R.id.save_id);
        saveId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putInt(TAXON_ID, mTaxonId);
                bundle.putString(ID_REMARKS, mRemarks.getText().toString());
                intent.putExtras(bundle);

                setResult(RESULT_OK, intent);
                finish();
            }
        });
        
        
        Button changeId = (Button) findViewById(R.id.id_change);
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
                mTaxonId = data.getIntExtra(IdentificationActivity.TAXON_ID, 0);
                mTaxonName.setText(data.getStringExtra(IdentificationActivity.TAXON_NAME));
                mIdName.setText(data.getStringExtra(IdentificationActivity.ID_NAME));
                UrlImageViewHelper.setUrlDrawable(mIdPic, data.getStringExtra(IdentificationActivity.ID_PIC_URL));
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

