package org.inaturalist.android;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatRadioButton;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.flurry.android.FlurryAgent;

public class NetworkSettings extends AppCompatActivity {
    private static final String TAG = "NetworkSettings";

    private INaturalistApp mApp;

    private ActivityHelper mHelper;

    private RadioGroup mNetworks;
    private ViewGroup mMoreInfo;
    private int mFormerSelectedNetworkRadioButton;


    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		FlurryAgent.onEndSession(this);
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        setContentView(R.layout.inat_network_settings);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(R.string.inat_network);

        mNetworks = (RadioGroup) findViewById(R.id.networks);
        mMoreInfo = (ViewGroup) findViewById(R.id.more_info);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }


        mMoreInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(getString(R.string.inat_network_info_url)));
                startActivity(i);
            }
        });

        String[] networks = mApp.getINatNetworks();
        String network = mApp.getInaturalistNetworkMember();
        int selectedNetwork = 0;

        for (int i = 0; i < networks.length; i++) {
            AppCompatRadioButton radioButton = new AppCompatRadioButton(this);
            radioButton.setText(Html.fromHtml(
                    // Network name
                    mApp.getStringResourceByName("network_" + networks[i]) +
                    // Network location (country)
                    "<br/><font color='#8B8B8B'><small>" + mApp.getStringResourceByName("inat_country_name_" + networks[i]) +
                    "</small></font>"));
            radioButton.setId(i);

            // Set radio button color
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{
                            new int[]{-android.R.attr.state_checked},
                            new int[]{android.R.attr.state_checked}
                    },
                    new int[]{
                            Color.DKGRAY, getResources().getColor(R.color.inatapptheme_color)
                    }
            );
            radioButton.setSupportButtonTintList(colorStateList);

            // Set layout_marginBottom to 15dp
            float scale = getResources().getDisplayMetrics().density;
            int dpAsPixels = (int) (15 * scale + 0.5f);
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dpAsPixels);
            radioButton.setLayoutParams(params);

            radioButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onINatNetworkRadioButtonClicked(v);
                }
            });
            mNetworks.addView(radioButton);

            if (networks[i].equals(network)) {
                selectedNetwork = i;
            }
        }

        mFormerSelectedNetworkRadioButton = selectedNetwork;
        mNetworks.check(selectedNetwork);
    }

    public void onINatNetworkRadioButtonClicked(View view){
	    final boolean checked = ((RadioButton) view).isChecked();
	    final int selectedRadioButtonId = view.getId();
	    final String[] networks = mApp.getINatNetworks();

	    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
	        @Override
	        public void onClick(DialogInterface dialog, int which) {
	            switch (which){
	            case DialogInterface.BUTTON_POSITIVE:
	            	if (checked) {
	            		mApp.setInaturalistNetworkMember(networks[selectedRadioButtonId]);
	            	}

	            	mFormerSelectedNetworkRadioButton = selectedRadioButtonId;
	            	mApp.applyLocaleSettings();
	        	    mApp.restart();
					finish();
	                break;

	            case DialogInterface.BUTTON_NEGATIVE:
	                //No button clicked
	            	mNetworks.check(mFormerSelectedNetworkRadioButton);
	                break;
	            }
	        }
	    };

        LayoutInflater inflater = getLayoutInflater();
		View titleBarView = inflater.inflate(R.layout.change_network_title_bar, null);
		ImageView titleBarLogo = (ImageView) titleBarView.findViewById(R.id.title_bar_logo);

	    String logoName = mApp.getStringResourceByName("inat_logo_" + networks[selectedRadioButtonId]);
	    String packageName = getPackageName();
	    int resId = getResources().getIdentifier(logoName, "drawable", packageName);
	    titleBarLogo.setImageResource(resId);

        mHelper.confirm(titleBarView, mApp.getStringResourceByName("alert_message_use_" + networks[selectedRadioButtonId]),
                dialogClickListener, dialogClickListener, R.string.yes, R.string.cancel);
	}

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
    }

}
