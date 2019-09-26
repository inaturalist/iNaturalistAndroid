package org.inaturalist.android;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatRadioButton;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;



import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;

public class NetworkSettings extends AppCompatActivity {
    private static final String TAG = "NetworkSettings";

    private INaturalistApp mApp;

    private ActivityHelper mHelper;

    private RadioGroup mNetworks;
    private List<RadioButton> mNetworkRadioButtons;
    private ViewGroup mMoreInfo;
    private int mFormerSelectedNetworkRadioButton;


@Override
	protected void onStop()
	{
		super.onStop();

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

        mNetworkRadioButtons = new ArrayList<RadioButton>();

        for (int i = 0; i < networks.length; i++) {

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final ViewGroup customNetworkOption = (ViewGroup) inflater.inflate(R.layout.network_option, null, false);

            TextView networkName = (TextView) customNetworkOption.findViewById(R.id.title);
            TextView networkLocation = (TextView) customNetworkOption.findViewById(R.id.sub_title);
            final AppCompatRadioButton radioButton = (AppCompatRadioButton) customNetworkOption.findViewById(R.id.radio_button);

            networkName.setText(mApp.getStringResourceByName("network_" + networks[i]));
            networkLocation.setText(mApp.getStringResourceByName("inat_country_name_" + networks[i]));
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

            final int index = i;
            customNetworkOption.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Uncheck all other radio buttons
                    for (int c = 0; c <  mNetworkRadioButtons.size(); c++) {
                        if (c == index) continue;
                        RadioButton r = mNetworkRadioButtons.get(c);
                        r.setChecked(false);
                    }
                    onINatNetworkRadioButtonClicked(index);
                }
            });

            mNetworkRadioButtons.add(radioButton);
            mNetworks.addView(customNetworkOption);

            if (networks[i].equals(network)) {
                selectedNetwork = i;
            }
        }

        mFormerSelectedNetworkRadioButton = selectedNetwork;
        mNetworkRadioButtons.get(selectedNetwork).setChecked(true);
    }

    public void onINatNetworkRadioButtonClicked(final int index) {
	    final String[] networks = mApp.getINatNetworks();

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SETTINGS_NETWORK_CHANGE_BEGAN);

        try {
            JSONObject eventParams = new JSONObject();
            eventParams.put(AnalyticsClient.EVENT_PARAM_PARTNER, mApp.getStringResourceByName("network_" + networks[index]));

            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_PARTNER_ALERT_PRESENTED, eventParams);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

	    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
	        @Override
	        public void onClick(DialogInterface dialog, int which) {
	            switch (which){
	            case DialogInterface.BUTTON_POSITIVE:
                    mApp.setInaturalistNetworkMember(networks[index]);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SETTINGS_NETWORK_CHANGE_COMPLETED);

	            	mFormerSelectedNetworkRadioButton = index;
	            	mApp.applyLocaleSettings();
	        	    mApp.restart();
					finish();
	                break;

	            case DialogInterface.BUTTON_NEGATIVE:
	                //No button clicked
                    mNetworkRadioButtons.get(index).setChecked(false);
	            	mNetworkRadioButtons.get(mFormerSelectedNetworkRadioButton).setChecked(true);
	                break;
	            }
	        }
	    };

        LayoutInflater inflater = getLayoutInflater();
		View titleBarView = inflater.inflate(R.layout.change_network_title_bar, null);
		ImageView titleBarLogo = (ImageView) titleBarView.findViewById(R.id.title_bar_logo);

	    String logoName = mApp.getStringResourceByName("inat_logo_" + networks[index]);
	    String packageName = getPackageName();
	    int resId = getResources().getIdentifier(logoName, "drawable", packageName);
	    titleBarLogo.setImageResource(resId);

        mHelper.confirm(titleBarView, mApp.getStringResourceByName("alert_message_use_" + networks[index]),
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
