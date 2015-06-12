package org.inaturalist.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.DrawerLayout;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class GuideDetails extends SherlockActivity implements INaturalistApp.OnDownloadFileProgress {

    private static final String TAG = "GuideDetails";
    private static final double MAX_TAXA = 35; // Max number of taxa to show in the grid

    private INaturalistApp mApp;
	private BetterJSONObject mGuide;
    private GuideXML mGuideXml;
    private String mGuideXmlFilename;
	private ProgressBar mProgress;
	private GridViewExtended mGuideTaxaGrid;
	private TextView mTaxaEmpty;

	private GuideTaxaReceiver mTaxaGuideReceiver;

	private EditText mSearchText;

    private GuideTaxonFilter mFilter;

	private TaxaListAdapter mAdapter;

    private List<GuideTaxonXML> mTaxa;
    private DrawerLayout mDrawerLayout;
    private ListView mGuideMenu;
    private GuideMenuListAdapter mGuideMenuListAdapter;

    private Handler mHandler;
    private Runnable mTypingTask;
    private TextView mDownloadTitle;
    private TextView mDownloadSubtitle;
    private View mDownloadGuide;
    private View mDownloadingGuide;
    private ProgressBar mDownloadingProgress;
    private boolean mIsDownloading;
    private ImageView mDownloadGuideImage;
    private TextView mDescription;
    private TextView mEditorName;
    private TextView mLicense;
    private int mDownloadProgress;
    private TextView mDownloadingSubtitle;

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
        if (mTypingTask != null) mHandler.removeCallbacks(mTypingTask);
	}

    private class TaxaListAdapter extends ArrayAdapter<GuideTaxonXML> {

        private List<GuideTaxonXML> mItems;
        private Context mContext;

        public TaxaListAdapter(Context context, List<GuideTaxonXML> objects) {
            super(context, R.layout.guide_taxon_item, objects);

            mItems = objects;
            mContext = context;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public GuideTaxonXML getItem(int index) {
            return mItems.get(index);
        }

        // Gets the photo location (local/remote) - tries a specific size, and if not found,
        // tries the next best size until something is found
        private String getPhotoLocation(GuideTaxonPhotoXML photo, GuideTaxonPhotoXML.PhotoType photoType) {
            final GuideTaxonPhotoXML.PhotoSize[] DEFAULT_SIZES = {
                    GuideTaxonPhotoXML.PhotoSize.THUMBNAIL,
                    GuideTaxonPhotoXML.PhotoSize.SMALL,
                    GuideTaxonPhotoXML.PhotoSize.MEDIUM,
                    GuideTaxonPhotoXML.PhotoSize.LARGE
            };

            String photoLocation = null;
            for (GuideTaxonPhotoXML.PhotoSize size : DEFAULT_SIZES) {
                photoLocation = photo.getPhotoLocation(photoType, size);

                // See if we found a photo for current size - if not, try the next best size
                if ((photoLocation != null) && (photoLocation.length() > 0)) break;
            }

            return photoLocation;
        }


		@SuppressLint("NewApi")
		@Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.guide_taxon_item, parent, false);
            } else {
                view = convertView;
            }

            GuideTaxonXML item = mItems.get(position);

            TextView idName = (TextView) view.findViewById(R.id.id_name);
            idName.setText(item.getDisplayName());

            ImageView taxonPic = (ImageView) view.findViewById(R.id.taxon_pic);
            
            taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
                    mGuideTaxaGrid.getColumnWidth(),
                    mGuideTaxaGrid.getColumnWidth()
            ));

            List<GuideTaxonPhotoXML> photos = item.getPhotos();
            if (photos.size() == 0) {
                // No photos - use default image
                taxonPic.setImageResource(R.drawable.unknown_large);
            } else {

                if (mGuideXml.isGuideDownloaded()) {
                    // Use offline photo
                    String photoPath = getPhotoLocation(photos.get(0), GuideTaxonPhotoXML.PhotoType.LOCAL);
                    Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
                    taxonPic.setImageBitmap(bitmap);

                } else {
                    // Use online photo
                    String url = getPhotoLocation(photos.get(0), GuideTaxonPhotoXML.PhotoType.REMOTE);
                    UrlImageViewHelper.setUrlDrawable(
                            taxonPic,
                            url,
                            R.drawable.unknown_large,
                            new UrlImageViewCallback() {
                                @Override
                                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url,
                                                     boolean loadedFromCache) {
                                    imageView.setLayoutParams(new RelativeLayout.LayoutParams(
                                            mGuideTaxaGrid.getColumnWidth(),
                                            mGuideTaxaGrid.getColumnWidth()
                                    ));
                                }
                            });
                }
            }


            view.setTag(item);

            return view;
        }
    }
 
	
	private class GuideTaxaReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxaGuideReceiver);

            mGuideXmlFilename = intent.getStringExtra(INaturalistService.GUIDE_XML_RESULT);

            if (mGuideXmlFilename == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(GuideDetails.this);
                builder.setTitle(R.string.error)
                        .setMessage(R.string.could_not_retrieve_guide)
                        .setNegativeButton(getString(R.string.ok), null)
                        .show();
                mProgress.setVisibility(View.GONE);
                return;
            }

            mGuideXml = new GuideXML(GuideDetails.this, mGuide.getInt("id").toString(), mGuideXmlFilename);
            mTaxa = new ArrayList<GuideTaxonXML>();
            mAdapter = new TaxaListAdapter(GuideDetails.this, mTaxa);
            updateTaxaByFilter();

            mProgress.setVisibility(View.GONE);

            updateSideMenu();
        }
    }


    private void updateSideMenu() {
        if (mGuideXml == null) return;
        Map<String, Set<String>> tags = mGuideXml.getAllTags();
        if (tags == null) return;

        // Build the guide side menu

        // Build the tags and predicates (section header)
        List<GuideMenuItem> sideMenuItems = new ArrayList<GuideMenuItem>();
        Map<String, Integer> tagCounts = mGuideXml.getTagCounts();

        for (String sectionName : tags.keySet()) {
            sideMenuItems.add(new GuideMenuSection(sectionName));

            Set<String> currentTags = tags.get(sectionName);

            for (String tagName : currentTags) {
                sideMenuItems.add(new GuideMenuTag(tagName, tagCounts.get(tagName)));
            }
        }

        mGuideMenuListAdapter = new GuideMenuListAdapter(GuideDetails.this, mFilter, sideMenuItems);
        mGuideMenu.setAdapter(mGuideMenuListAdapter);
        mGuideMenu.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                GuideMenuItem item = (GuideMenuItem) view.getTag();
                if (item == null) return;
                if (item.isSectionHeader()) return;

                // A tag was added/removed

                String tagName = item.getText();

                if (mFilter.hasTag(tagName)) {
                    mFilter.removeTag(tagName);
                } else {
                    mFilter.addTag(tagName);
                }

                updateTaxaByFilter();
                mGuideMenuListAdapter.notifyDataSetChanged();
            }
        });

        refreshGuideSideMenu();
    }


    private void refreshGuideSideMenu() {

        // Prepare the footer of the guide side menu (description, license, etc.)

        mDownloadingGuide.setVisibility(View.GONE);
        mDownloadGuide.setVisibility(View.VISIBLE);

        if (mGuideXml == null) return;

        mDescription.setText(Html.fromHtml(mGuideXml.getDescription()));
        mDescription.setMovementMethod(LinkMovementMethod.getInstance());
        mEditorName.setText(mGuideXml.getCompiler());
        mLicense.setText(GuideXML.licenseToText(this, mGuideXml.getLicense()));

        if (mIsDownloading) {
            // Currently downloading guide
            mDownloadingGuide.setVisibility(View.VISIBLE);
            mDownloadingSubtitle.setText(R.string.downloading);
            mDownloadGuide.setVisibility(View.GONE);
            updateDownloadProgress(mDownloadProgress);

        } else if (mGuideXml.isGuideDownloaded()) {
            // Guide was already downloaded
            mDownloadTitle.setText(R.string.downloaded);
            SimpleDateFormat  formatter = new SimpleDateFormat();
            mDownloadSubtitle.setText(formatter.format(mGuideXml.getDownloadedGuideDate()));
            mDownloadGuideImage.setImageResource(R.drawable.guide_downloaded);

            mDownloadGuide.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEditDownloadDialog();
                }
            });
            
        } else if (!mGuideXml.isOfflineGuideAvailable()) {
            // Download not available
            mDownloadTitle.setText(R.string.download_not_available);
            mDownloadSubtitle.setText(R.string.guide_editor_must_enable_this_feature);
            mDownloadGuideImage.setImageResource(R.drawable.download_guide);
        } else {
            // Download is available
            mDownloadTitle.setText(R.string.download_for_offline_use);
            mDownloadSubtitle.setText(mGuideXml.getNgzFileSize());
            mDownloadGuideImage.setImageResource(R.drawable.download_guide);

            mDownloadGuide.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(GuideDetails.this);
                    builder.setTitle(R.string.are_you_sure)
                            .setMessage(String.format(getString(R.string.download_guide_alert), mGuideXml.getNgzFileSize()))
                            .setNegativeButton(getString(R.string.cancel), null)
                            .setPositiveButton(getString(R.string.download), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    downloadGuide();
                                }
                            })
                            .show();

                }
            });
        }
    }

    private void showEditDownloadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Set the adapter
        String[] items = {
                getResources().getString(R.string.delete_download),
                getResources().getString(R.string.re_download),
                getResources().getString(R.string.cancel)
        };
        builder.setAdapter(
                new ArrayAdapter<String>(this,
                        android.R.layout.simple_list_item_1, items), null);

        final AlertDialog alertDialog = builder.create();

        ListView listView = alertDialog.getListView();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                alertDialog.dismiss();

                if (position == 0) {
                    // Delete download
                    mGuideXml.deleteOfflineGuide();
                    refreshGuideSideMenu();
                } else if (position == 1) {
                    // Re-download
                    mGuideXml.deleteOfflineGuide();
                    downloadGuide();
                } else {
                    // Cancel
                }
            }
        });

        alertDialog.show();
    }

 	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

    private void showDownloadGuideError() {
        mIsDownloading = false;
        mDownloadProgress = 0;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(GuideDetails.this);
                builder.setTitle(R.string.failed_to_download_guide)
                        .setMessage(R.string.internet_connection_seems_to_be_offline)
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();
                mGuideXml.deleteOfflineGuide();
                refreshGuideSideMenu();
            }
        });
    }

    private void updateDownloadProgress(int progress) {
        mDownloadProgress = progress;
        mDownloadingProgress.setProgress(progress);
    }

    private void downloadGuide() {
        if (!isNetworkAvailable()) {
            showDownloadGuideError();
            return;
        }

        mDownloadingGuide.setVisibility(View.VISIBLE);
        mDownloadGuide.setVisibility(View.GONE);

        mIsDownloading = true;
        updateDownloadProgress(0);

        // Download guide as a background task
        mApp.downloadFile(mGuideXml.getNgzURL(), this);
    }


    @Override
    public boolean onDownloadProgress(long downloaded, long total, String downloadedFilename) {
        final int progress = (int)((((float)downloaded) / total) * 100);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Update progress
                updateDownloadProgress(progress);
            }
        });

        if (downloaded == total) {
            // Download complete

            // Extract the downloaded compressed guide file
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDownloadingSubtitle.setText(R.string.extracting);
                }
            });
            boolean status = mGuideXml.extractOfflineGuide(downloadedFilename);

            if (!status) {
                showDownloadGuideError();
                return true;
            }

            mIsDownloading = false;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshGuideSideMenu();
                }
            });
        }

        return true;
    }

    @Override
    public void onDownloadError() {
        showDownloadGuideError();
    }


    // Represents a list item in the guide menu (header/item)
    private interface GuideMenuItem {
        public boolean isSectionHeader();
        public String getText();
    }
    
    private class GuideMenuSection implements  GuideMenuItem {
        private final String mTitle;

        public GuideMenuSection(String title) {
            mTitle = title;
        }
        public boolean isSectionHeader() {
            return true;
        }
        public String getText() {
            return mTitle;
        }
    }

    private class GuideMenuTag implements  GuideMenuItem {
        private final String mTitle;
        private final int mCount;

        public GuideMenuTag(String title, int count) {
            mTitle = title;
            mCount = count;
        }
        public boolean isSectionHeader() {
            return false;
        }
        public String getText() {
            return mTitle;
        }

        public int getCount() {
            return mCount;
        }
    }

	private class GuideMenuListAdapter extends ArrayAdapter<GuideMenuItem> {

        private List<GuideMenuItem> mItems;
        private Context mContext;
        private GuideTaxonFilter mFilter;

        public GuideMenuListAdapter(Context context, GuideTaxonFilter filter, List<GuideMenuItem>items) {
            super(context, R.layout.guide_menu_tag, items);

            mContext = context;
            mFilter = filter;
            mItems = items;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public GuideMenuItem getItem(int index) {
            return mItems.get(index);
        }

		@SuppressLint("NewApi")
		@Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            GuideMenuItem item = mItems.get(position);
            View view;
            TextView title;
            String itemText = item.getText();

            if (item.isSectionHeader()) {
                // Header section
                view = inflater.inflate(R.layout.guide_menu_header, parent, false);
                title = (TextView) view.findViewById(R.id.title);
            } else {
                // Tag list item
                view = inflater.inflate(R.layout.guide_menu_tag, parent, false);
                title = (TextView) view.findViewById(R.id.tagName);
                TextView tagCount = (TextView) view.findViewById(R.id.tagCount);
                tagCount.setText(String.valueOf(((GuideMenuTag)item).getCount()));

                if (mFilter.hasTag(item.getText())) {
                    // Tag is checked on
                    view.setBackgroundColor(Color.parseColor("#006600"));
                    tagCount.setTextColor(Color.parseColor("#FFFFFF"));
                }
            }

            title.setText(itemText);

            view.setTag(item);

            return view;
        }
    }


    // Refresh the taxa list according to current filter
    private void updateTaxaByFilter() {
        if ((mFilter == null) || (mGuideXml == null) || (mTaxa == null)) return;

        List<GuideTaxonXML> taxa = mGuideXml.getTaxa(mFilter);
        mTaxa.clear();
        mTaxa.addAll(taxa.subList(0, (int)Math.min(taxa.size(), MAX_TAXA)));
        mAdapter.notifyDataSetChanged();

        mSearchText.setEnabled(true);

        if (mTaxa.size() > 0) {
            mTaxaEmpty.setVisibility(View.GONE);
            mGuideTaxaGrid.setVisibility(View.VISIBLE);

            mAdapter = new TaxaListAdapter(GuideDetails.this, mTaxa);
            mGuideTaxaGrid.setAdapter(mAdapter);

        } else {
            mTaxaEmpty.setText(R.string.no_check_list);
            mTaxaEmpty.setVisibility(View.VISIBLE);
            mGuideTaxaGrid.setVisibility(View.GONE);
        }
    }

 

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case R.id.show_side_menu:
            if (mGuideMenuListAdapter == null) {
                // Guide still not loaded
                return true;
            }
            if (mDrawerLayout.isDrawerOpen(mGuideMenu)) {
                mDrawerLayout.closeDrawer(mGuideMenu);
            } else {
                mDrawerLayout.openDrawer(mGuideMenu);
            }

            return true;
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.guide_details_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.guide_details);

        mHandler = new Handler();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mGuideMenu = (ListView) findViewById(R.id.guide_menu);

        mFilter = new GuideTaxonFilter();
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        
        final Intent intent = getIntent();

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mGuide = (BetterJSONObject) intent.getSerializableExtra("guide");
        } else {
            mGuide = (BetterJSONObject) savedInstanceState.getSerializable("guide");
            mGuideXmlFilename = savedInstanceState.getString("guideXmlFilename");
            if (mGuideXmlFilename != null) mGuideXml = new GuideXML(GuideDetails.this, mGuide.getInt("id").toString(), mGuideXmlFilename);

            mFilter.setSearchText(savedInstanceState.getString("filterSearchText"));
            mFilter.setTags(savedInstanceState.getStringArrayList("filterTags"));
            mIsDownloading = savedInstanceState.getBoolean("isDownloading");
            mDownloadProgress = savedInstanceState.getInt("downloadProgress", 0);
        }
 
        actionBar.setTitle(mGuide.getString("title"));
        
        
        mTaxaGuideReceiver = new GuideTaxaReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GUIDE_XML_RESULT);
        Log.i(TAG, "Registering ACTION_GUIDE_XML_RESULT");
        registerReceiver(mTaxaGuideReceiver, filter);

        mSearchText = (EditText) findViewById(R.id.search_filter);
        mSearchText.setEnabled(false);
        mSearchText.setText(mFilter.getSearchText());
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) {
                    mFilter.setSearchText(s.toString());

                    if (mTypingTask != null) {
                        mHandler.removeCallbacks(mTypingTask);
                    }

                    mTypingTask = new Runnable() {
                        @Override
                        public void run() {
                            updateTaxaByFilter();
                        }
                    };

                    mHandler.postDelayed(mTypingTask, 400);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });
 

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mTaxaEmpty = (TextView) findViewById(R.id.guide_taxa_empty);
        mGuideTaxaGrid = (GridViewExtended) findViewById(R.id.taxa_grid);
        mGuideTaxaGrid.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                GuideTaxonXML taxon = (GuideTaxonXML) arg1.getTag();

                // Show taxon details
                Intent intent = new Intent(GuideDetails.this, GuideTaxonActivity.class);
                intent.putExtra("guide_taxon", true);
                intent.putExtra("guide_id", mGuideXml.getID());
                intent.putExtra("taxon_id", taxon.getTaxonId());
                intent.putExtra("guide_xml_filename", mGuideXmlFilename);
                startActivity(intent);

            }
        });
 
        mProgress.setVisibility(View.VISIBLE);
        mGuideTaxaGrid.setVisibility(View.GONE);
        mTaxaEmpty.setVisibility(View.GONE);
 

        if (mGuideXml == null) {
            // Get the guide's XML file
            int guideId = mGuide.getInt("id");
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GUIDE_XML, null, GuideDetails.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.ACTION_GUIDE_ID, guideId);
            startService(serviceIntent);
        }



        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View footerView = inflater.inflate(R.layout.guide_menu_footer, null, false);
        mDescription = (TextView) footerView.findViewById(R.id.description);
        mEditorName = (TextView) footerView.findViewById(R.id.editorName);
        mLicense = (TextView) footerView.findViewById(R.id.license);
        mDownloadTitle = (TextView) footerView.findViewById(R.id.downloadTitle);
        mDownloadSubtitle = (TextView) footerView.findViewById(R.id.downloadSubtitle);
        mDownloadingSubtitle = (TextView) footerView.findViewById(R.id.downloadingSubtitle);
        mDownloadGuideImage = (ImageView) footerView.findViewById(R.id.downloadGuideImage);

        mDownloadingGuide = (View) footerView.findViewById(R.id.downloadingGuide);
        mDownloadingProgress = (ProgressBar) footerView.findViewById(R.id.downloadingProgress);

        mDownloadGuide = (View) footerView.findViewById(R.id.downloadGuide);

        mGuideMenu.addFooterView(footerView);

        if (mIsDownloading) {
            refreshGuideSideMenu();
            mApp.setDownloadCallback(this);
        }

        if (mGuideXml != null) {
            mTaxa = new ArrayList<GuideTaxonXML>();
            mAdapter = new TaxaListAdapter(GuideDetails.this, mTaxa);
            updateTaxaByFilter();

            mProgress.setVisibility(View.GONE);
            updateSideMenu();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        
        if (mTaxaGuideReceiver != null) {
            Log.i(TAG, "Unregistering ACTION_GUIDE_XML_RESULT");
            try {
                unregisterReceiver(mTaxaGuideReceiver);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
            mTaxaGuideReceiver = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("guide", mGuide);
        outState.putString("guideXmlFilename", mGuideXmlFilename);
        outState.putString("filterSearchText", mFilter.getSearchText());
        outState.putStringArrayList("filterTags", (ArrayList<String>) mFilter.getAllTags());
        outState.putBoolean("isDownloading", mIsDownloading);
        outState.putInt("downloadProgress", mDownloadProgress);
        super.onSaveInstanceState(outState);
    }

 
}
