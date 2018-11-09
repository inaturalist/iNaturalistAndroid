package org.inaturalist.android;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import android.support.v7.app.AppCompatActivity;

public class GuideDetails extends AppCompatActivity implements INaturalistApp.OnDownloadFileProgress {

    private static final String TAG = "GuideDetails";
    private static final double MAX_TAXA = 100; // Max number of taxa to show in the grid

    private INaturalistApp mApp;
	@State public BetterJSONObject mGuide;
    private GuideXML mGuideXml;
    @State public String mGuideXmlFilename;
    @State public String mFilterSearchText;
    @State public ArrayList<String> mFilterTags;
	private ProgressBar mProgress;
	private GridViewExtended mGuideTaxaGrid;
	private TextView mTaxaEmpty;

	private GuideTaxaReceiver mTaxaGuideReceiver;

	private EditText mSearchText;

    private GuideTaxonFilter mFilter;

	private TaxaListAdapter mAdapter;

    private List<GuideTaxonXML> mTaxa;
    private DrawerLayout mDrawerLayout;
    private View mGuideMenu;
    private ListView mGuideMenuList;
    private GuideMenuListAdapter mGuideMenuListAdapter;

    private Handler mHandler;
    private Runnable mTypingTask;
    private TextView mDownloadTitle;
    private TextView mDownloadSubtitle;
    private View mDownloadGuide;
    private View mDownloadingGuide;
    private ProgressBar mDownloadingProgress;
    @State public boolean mIsDownloading;
    private ImageView mDownloadGuideImage;
    private TextView mDescription;
    private TextView mEditorName;
    private TextView mLicense;
    @State public int mDownloadProgress;
    private TextView mDownloadingSubtitle;
    private ActivityHelper mHelper;
    private Button mRecommendedNextStep;
    private List<GuideTaxonXML> mCurrentTaxaResults;
    private List<GuideMenuItem> mSideMenuItems;
    @State public String mRecommendedPrediate;
    private ImageButton mReset;

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

        // Gets the photo place (local/remote) - tries a specific size, and if not found,
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
            String name = item.getDisplayName();
            if ((name == null) || (name.length() == 0)) name = item.getName();
            idName.setText(name);

            ImageView taxonPic = (ImageView) view.findViewById(R.id.taxon_photo);
            
            taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
                    mGuideTaxaGrid.getColumnWidth(),
                    mGuideTaxaGrid.getColumnWidth()
            ));

            List<GuideTaxonPhotoXML> photos = item.getPhotos();
            if (photos.size() == 0) {
                // No photos - use default image
                taxonPic.setImageResource(R.drawable.iconic_taxon_unknown);
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
                            R.drawable.iconic_taxon_unknown,
                            new UrlImageViewCallback() {
                                @Override
                                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url,
                                                     boolean loadedFromCache) {
                                    imageView.setLayoutParams(new RelativeLayout.LayoutParams(
                                            mGuideTaxaGrid.getColumnWidth(),
                                            mGuideTaxaGrid.getColumnWidth()
                                    ));
                                }

                                @Override
                                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                                    // No post-processing of bitmap
                                    return loadedBitmap;
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
                mHelper.confirm(R.string.error, R.string.could_not_retrieve_guide, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        });
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
                String tagTitle = tagName;
                String[] parts = tagName.split("=", 2);
                if (parts.length > 1) {
                    // For display purposes, show only the tag value
                    tagTitle = parts[1];
                }
                sideMenuItems.add(new GuideMenuTag(tagTitle, tagName, tagCounts.get(tagName)));
            }
        }

        mSideMenuItems = sideMenuItems;
        mGuideMenuListAdapter = new GuideMenuListAdapter(GuideDetails.this, mFilter, sideMenuItems);
        mGuideMenuList.setAdapter(mGuideMenuListAdapter);

        refreshGuideSideMenu();
    }


    private void refreshGuideSideMenu() {

        // Prepare the footer of the guide side menu (description, license, etc.)

        mDownloadingGuide.setVisibility(View.GONE);
        mDownloadGuide.setVisibility(View.VISIBLE);

        if (mGuideXml == null) return;

        String description = mGuideXml.getDescription();
        mDescription.setText(Html.fromHtml(description != null ? description : ""));
        mDescription.setMovementMethod(LinkMovementMethod.getInstance());
        mEditorName.setText(mGuideXml.getCompiler());
        mLicense.setText(GuideXML.licenseToText(this, mGuideXml.getLicense()));

        View.OnClickListener showUser = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BetterJSONObject userObj = new BetterJSONObject();
                userObj.put("login", mGuideXml.getCompiler());
                Intent intent = new Intent(GuideDetails.this, UserProfile.class);
                intent.putExtra("user", userObj);
                startActivity(intent);
            }
        };
        mEditorName.setOnClickListener(showUser);


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
            mDownloadGuideImage.setImageResource(R.drawable.ic_fa_check);

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
            mDownloadGuideImage.setImageResource(R.drawable.ic_action_download);
        } else {
            // Download is available
            mDownloadTitle.setText(R.string.download_for_offline_use);
            mDownloadSubtitle.setText(mGuideXml.getNgzFileSize());
            mDownloadGuideImage.setImageResource(R.drawable.ic_action_download);

            mDownloadGuide.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mHelper.confirm(R.string.are_you_sure, String.format(getString(R.string.download_guide_alert), mGuideXml.getNgzFileSize()), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if (!isLoggedIn()) {
                                        // User not logged-in - redirect to onboarding screen
                                        startActivity(new Intent(GuideDetails.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                                        return;
                                    }

                                    downloadGuide();
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                }
                            });
                }
            });
        }

        if ((mGuideXml.getAllTags() == null) || (mGuideXml.getAllTags().size() == 0)) {
            // No tags for this guide - no need to show the reset / next recommended step buttons
            View topBar = (View) findViewById(R.id.top_side_menu_bar);
            topBar.setVisibility(View.GONE);
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
                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_GUIDE_DOWNLOAD_DELETE);
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
                mHelper.alert(R.string.failed_to_download_guide, R.string.internet_connection_seems_to_be_offline);
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

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_GUIDE_DOWNLOAD_START);

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

            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_GUIDE_DOWNLOAD_COMPLETE);

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
        private final String mValue;
        private final int mCount;

        public GuideMenuTag(String title, String value, int count) {
            mTitle = title;
            mCount = count;
            mValue = value;
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

        public String getValue() {
            return mValue;
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
                mApp.setStringResourceForView(view, R.id.quantity, "quantity_all_caps", "quantity");
                title = (TextView) view.findViewById(R.id.title);
                if ((mRecommendedPrediate != null) && (itemText.equals(mRecommendedPrediate))) {
                    // Current predicate is the recommended one
                    view.setBackgroundResource(R.color.inatapptheme_color);
                } else {
                    List<String> tags = mFilter.getAllTags();
                    for (String tag: tags) {
                        String[] values = tag.split("=", 2);

                        if (values[0].equals(itemText)) {
                            // Current predicate has checked on tags - highlight it in green
                            view.setBackgroundColor(Color.parseColor("#FF2D5228"));
                            break;
                        }
                    }
                }
            } else {
                // Tag list item
                GuideMenuTag guideMenuTag = (GuideMenuTag)item;

                view = inflater.inflate(R.layout.guide_menu_tag, parent, false);
                title = (TextView) view.findViewById(R.id.tagName);
                TextView tagCount = (TextView) view.findViewById(R.id.tagCount);
                tagCount.setText(String.valueOf((guideMenuTag.getCount())));

                if (mFilter.hasTag(guideMenuTag.getValue())) {
                    // Tag is checked on
                    view.setBackgroundColor(Color.parseColor("#4C669900"));
                    CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
                    checkbox.setChecked(true);
                }


                ImageView photoIcon = (ImageView) view.findViewById(R.id.tag_photo);
                final String[] values = guideMenuTag.getValue().split("=", 2);
                String predicateName, value;
                if (values.length == 1) {
                    predicateName = GuideXML.PREDICATE_TAGS;
                    value = values[0];
                } else {
                    predicateName = values[0];
                    value = values[1];
                }
                final List<GuideTaxonPhotoXML> photos =  mGuideXml.getTagRepresentativePhoto(predicateName, value);

                if (photos == null) {
                    // No representative photo for the tag value
                    photoIcon.setVisibility(View.INVISIBLE);
                } else {
                    photoIcon.setVisibility(View.VISIBLE);
                    photoIcon.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(GuideDetails.this, TaxonTagPhotosViewer.class);
                            intent.putExtra("guide_id", mGuideXml.getID());
                            intent.putExtra("guide_xml_filename", mGuideXmlFilename);
                            intent.putExtra("tag_name", values[0]);
                            intent.putExtra("tag_value", values[1]);
                            startActivity(intent);
                        }
                    });
                }

            }

            title.setText(itemText);

            view.setTag(item);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    GuideMenuItem item = (GuideMenuItem) view.getTag();
                    if (item == null) return;
                    if (item.isSectionHeader()) return;

                    // A tag was added/removed

                    String tagName = ((GuideMenuTag) item).getValue();

                    if (mFilter.hasTag(tagName)) {
                        mFilter.removeTag(tagName);
                    } else {
                        mFilter.addTag(tagName);
                    }

                    updateTaxaByFilter();
                    mGuideMenuListAdapter.notifyDataSetChanged();
                }
            });

            return view;
        }
    }


    // Refresh the taxa list according to current filter
    private void updateTaxaByFilter() {
        if ((mFilter == null) || (mGuideXml == null) || (mTaxa == null)) return;

        List<GuideTaxonXML> taxa = mGuideXml.getTaxa(mFilter);
        mCurrentTaxaResults = taxa;
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

        String nextPredicate = mGuideXml.getRecommendedPredicate(mFilter, mCurrentTaxaResults);
        if (nextPredicate != null) {
            mRecommendedNextStep.setEnabled(true);
        } else {
            // No recommended next step available
            mRecommendedNextStep.setEnabled(false);
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
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.guide_details_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        setContentView(R.layout.guide_details);

        mHandler = new Handler();
        mHelper = new ActivityHelper(this);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mGuideMenuList = (ListView) findViewById(R.id.guide_menu_list);
        mGuideMenu = findViewById(R.id.guide_menu);

        mFilter = new GuideTaxonFilter();

        mRecommendedNextStep = (Button) findViewById(R.id.recommended_next_step);
        mRecommendedNextStep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String nextPredicate = mGuideXml.getRecommendedPredicate(mFilter, mCurrentTaxaResults);
                mRecommendedPrediate = nextPredicate;

                if (mRecommendedPrediate != null) {
                    // Highlight the appropriate predicate (guide menu header)
                    int position = -1;
                    int i = 0;

                    for (GuideMenuItem item : mSideMenuItems) {
                        if (item instanceof GuideMenuSection) {
                            if (item.getText().equals(mRecommendedPrediate)) {
                                position = i;
                                break;
                            }
                        }
                        i++;
                    }

                    if (i > -1) {
                        // Call setSelection at the end because the ListView scrolling method is sometimes buggy
                        final int finalPosition = position;
                        mGuideMenuList.setOnScrollListener(new AbsListView.OnScrollListener() {
                            int tryCount = 0;

                            @Override
                            public void onScrollStateChanged(final AbsListView view, final int scrollState) {
                                if (scrollState == SCROLL_STATE_IDLE) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Thread.sleep(10);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            if ((tryCount < 2) && (view.getFirstVisiblePosition() != finalPosition)) {
                                                // Fix for scrolling bug
                                                GuideDetails.this.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        view.setSelection(finalPosition);
                                                    }
                                                });
                                                tryCount++;
                                            } else {
                                                view.setOnScrollListener(null);
                                                tryCount = 0;
                                            }
                                        }
                                    }).start();



                                }
                            }

                            @Override
                            public void onScroll(final AbsListView view, final int firstVisibleItem, final int visibleItemCount,
                                                 final int totalItemCount) {
                            }
                        });

                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            mGuideMenuList.smoothScrollToPositionFromTop(position, 0, 300);
                        } else {
                            mGuideMenuList.setSelectionFromTop(position, 0);
                        }


                    }
                }

                mGuideMenuListAdapter.notifyDataSetChanged();
            }
        });

        mReset = (ImageButton) findViewById(R.id.reset_key);
        mReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Reset all tag selections
                mRecommendedPrediate = null;
                mFilter.clearTags();
                mGuideMenuListAdapter.notifyDataSetChanged();
                updateTaxaByFilter();

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    mGuideMenuList.smoothScrollToPositionFromTop(0, 0, 300);
                } else {
                    mGuideMenuList.setSelectionFromTop(0, 0);
                }
            }
        });

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        final Intent intent = getIntent();

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            Object guide = intent.getSerializableExtra("guide");
            if (guide.getClass().equals(String.class)){
                mGuide = new BetterJSONObject((String)guide);
            } else {
                mGuide = (BetterJSONObject) guide;
            }
        } else {
            if (mGuideXmlFilename != null) mGuideXml = new GuideXML(GuideDetails.this, mGuide.getInt("id").toString(), mGuideXmlFilename);

            mFilter.setSearchText(mFilterSearchText);
            mFilter.setTags(mFilterTags);
        }

        if (mGuide == null) {
            finish();
            return;
        }
 
        actionBar.setTitle(mGuide.getString("title"));
        
        
        mTaxaGuideReceiver = new GuideTaxaReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GUIDE_XML_RESULT);
        Log.i(TAG, "Registering ACTION_GUIDE_XML_RESULT");
        BaseFragmentActivity.safeRegisterReceiver(mTaxaGuideReceiver, filter, this);

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
 
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View footerView = inflater.inflate(R.layout.guide_menu_footer, null, false);
        mApp.setStringResourceForView(footerView, R.id.description_title, "description_all_caps", "description");
        mApp.setStringResourceForView(footerView, R.id.about_title, "about_guide_all_caps", "about_guide");
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

        mGuideMenuList.addFooterView(footerView);

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

        BaseFragmentActivity.safeUnregisterReceiver(mTaxaGuideReceiver, this);
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (mGuideXml == null) {
            // Get the guide's XML file
            int guideId = mGuide.getInt("id");
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GUIDE_XML, null, GuideDetails.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.ACTION_GUIDE_ID, guideId);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mFilterSearchText = mFilter.getSearchText();
        mFilterTags = (ArrayList<String>) mFilter.getAllTags();

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    }

}
