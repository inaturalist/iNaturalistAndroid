package org.inaturalist.android;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class IdentificationActivity extends Activity {
	public static final String TAXON_ID = "taxon_id";
    public static final String ID_REMARKS = "id_remarks";
    private ActionBar mTopActionBar;
    private MultilineEditText mRemarks;

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
        
        Button saveId = (Button) findViewById(R.id.save_id);
        saveId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putInt(TAXON_ID, 0);
                bundle.putString(ID_REMARKS, mRemarks.getText().toString());
                intent.putExtras(bundle);

                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}

