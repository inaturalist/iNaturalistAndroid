package org.inaturalist.android;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import org.inaturalist.android.INaturalistService.LoginType;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.*;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;

public class GuideTaxonActivity extends SherlockActivity {
    private static String TAG = "GuideTaxonActivity";
    private static String GUIDE_TAXON_URL = "http://%s/guide_taxa/%d.xml";
    private static String TAXON_URL = "http://%s/taxa/%d";
    private WebView mWebView;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private BetterJSONObject mTaxon;
	private boolean mGuideTaxon;
    private String mTaxonId;
    private String mGuideXmlFilename;
    private GuideXML mGuideXml;
    private GuideTaxonXML mGuideTaxonXml;
    private String mGuideId;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        
        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra("taxon");
        	mGuideTaxon = intent.getBooleanExtra("guide_taxon", true);
            mTaxonId = intent.getStringExtra("taxon_id");
            mGuideId = intent.getStringExtra("guide_id");
            mGuideXmlFilename = intent.getStringExtra("guide_xml_filename");
        } else {
        	mTaxon = (BetterJSONObject) savedInstanceState.getSerializable("taxon");
        	mGuideTaxon = savedInstanceState.getBoolean("guide_taxon", true);
            mGuideId = savedInstanceState.getString("guide_id");
            mTaxonId = savedInstanceState.getString("taxon_id");
            mGuideXmlFilename = savedInstanceState.getString("guide_xml_filename");
        }

        if (mGuideTaxon) {
            // Load guide taxon from XML
            mGuideXml = new GuideXML(this, mGuideId, mGuideXmlFilename);
            mGuideTaxonXml = mGuideXml.getTaxonById(mTaxonId);

            setContentView(R.layout.guide_taxon);

            TextView displayName = (TextView)findViewById(R.id.displayName);
            TextView name = (TextView)findViewById(R.id.name);

            displayName.setText(mGuideTaxonXml.getDisplayName());
            name.setText(mGuideTaxonXml.getName());

            // Prepare the sections

            List<GuideTaxonSectionXML> sections = mGuideTaxonXml.getSections();
            StringBuilder photosAttributions = new StringBuilder();
            StringBuilder textAttributions = new StringBuilder();

            ViewGroup sectionsRoot = (ViewGroup)findViewById(R.id.sections);
            for (GuideTaxonSectionXML section : sections) {
                ViewGroup layout = (ViewGroup) getLayoutInflater().inflate(R.layout.guide_taxon_section, null, false);
                TextView title = (TextView) layout.findViewById(R.id.title);
                TextView body = (TextView) layout.findViewById(R.id.body);

                title.setText(section.getTitle());
                body.setText(Html.fromHtml(section.getBody()));
                body.setMovementMethod(LinkMovementMethod.getInstance());

                sectionsRoot.addView(layout);

                if (textAttributions.length() > 0) textAttributions.append(", ");
                textAttributions.append('"' + section.getTitle() + '"' + " ");
                textAttributions.append(section.getAttribution());
            }

            // Prepare attributions
            TextView textAttr = (TextView) findViewById(R.id.attributions_text);
            if (sections.size() == 0) {
                textAttr.setVisibility(View.GONE);
            }

            textAttr.setText(Html.fromHtml(String.format(getResources().getString(R.string.text), textAttributions)));
            textAttr.setMovementMethod(LinkMovementMethod.getInstance());

            List<GuideTaxonPhotoXML> photos = mGuideTaxonXml.getPhotos();
            for (GuideTaxonPhotoXML photo : photos) {
                if (photosAttributions.length() > 0) photosAttributions.append(", ");
                photosAttributions.append(photo.getAttribution());
            }

            TextView photosAttr = (TextView) findViewById(R.id.attributions_photos);


            photosAttr.setText(Html.fromHtml(String.format(getResources().getString(R.string.photos), photosAttributions)));
            photosAttr.setMovementMethod(LinkMovementMethod.getInstance());


            if ((photos.size() == 0) && (sections.size() == 0)) {
                // Don't show the sources and attribution section at all
                View sourcesSection = findViewById(R.id.sources_and_attrs_section);
                sourcesSection.setVisibility(View.GONE);
            }

            // Prepare photos gallery
            Gallery gallery = (Gallery) findViewById(R.id.gallery);
            gallery.setAdapter(new GalleryPhotoAdapter(this, photos));

            if (photos.size() == 0) {
                photosAttr.setVisibility(View.GONE);
                gallery.setVisibility(View.GONE);
            }


            gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView parent, View view, int position, long id) {
                    Gallery g = (Gallery) parent;
                    Uri uri = ((GalleryPhotoAdapter) g.getAdapter()).getItemUri(position);
                    Intent intent;

                    if (mGuideXml.isGuideDownloaded()) {
                        // Offline photo
                        intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "image/*");
                    } else {
                        // Online photo
                        intent = new Intent(Intent.ACTION_VIEW, uri);
                    }

                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Failed to view photo: " + e);
                    }
                }
            });


        } else {
            setContentView(R.layout.web);
            mWebView = (WebView) findViewById(R.id.webview);
        }


        String title = "";
        if (mGuideTaxon) {
            title = mGuideTaxonXml.getDisplayName();
            if ((title == null) || (title.length() == 0)) title = mGuideTaxonXml.getName();
        } else {
            if (mTaxon.has("display_name") && !mTaxon.getJSONObject().isNull("display_name")) {
                title = mTaxon.getString("display_name");
            } else {
                if (mTaxon.has("common_name") && !mTaxon.getJSONObject().isNull("common_name")) {
                    title = mTaxon.getJSONObject("common_name").optString("name", "");
                } else {
                    title = mTaxon.getJSONObject().optString("name", "");
                }
            }
            mWebView.getSettings().setJavaScriptEnabled(true);

            final Activity activity = this;
            mWebView.setWebViewClient(new WebViewClient() {
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    mHelper.loading();
                }
                public void onPageFinished(WebView view, String url) {
                    mHelper.stopLoading();
                }
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    mHelper.stopLoading();
                    mHelper.alert(String.format(getString(R.string.oh_no), description));
                }
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (!mApp.loggedIn()) {
                        return false;
                    }
                    mWebView.loadUrl(url, getAuthHeaders());
                    return true;
                }
            });

        }
        actionBar.setTitle(title);

        if (!mGuideTaxon) {
            loadTaxonPage(mTaxon.getInt("id"));
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.guide_taxon_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.add_taxon:
        	// Add a new observation with the specified taxon
        	Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, this, ObservationEditor.class);
            if (mGuideTaxon) {
                intent.putExtra(ObservationEditor.SPECIES_GUESS, String.format("%s (%s)", mGuideTaxonXml.getDisplayName(), mGuideTaxonXml.getName()));
            } else {
                intent.putExtra(ObservationEditor.SPECIES_GUESS, String.format("%s (%s)", mTaxon.has("display_name") ? mTaxon.getString("display_name") : mTaxon.getJSONObject("common_name").optString("name"), mTaxon.getString("name")));
            }
        	startActivity(intent);

        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public HashMap<String,String> getAuthHeaders() {
        HashMap<String,String> headers = new HashMap<String,String>();
        if (!mApp.loggedIn()) {
            return headers;
        }
        if (mApp.getLoginType() == LoginType.PASSWORD) {
            headers.put("Authorization", "Basic " + mApp.getPrefs().getString("credentials", null));
        } else {
            headers.put("Authorization", "Bearer " + mApp.getPrefs().getString("credentials", null));
        }
        return headers;
    }
    
    public void loadTaxonPage(Integer taxonId) {
    	mWebView.getSettings().setUserAgentString(INaturalistService.USER_AGENT);

    	String url;
    	
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
    	
        url = String.format(TAXON_URL, inatHost, taxonId);
    	mWebView.loadUrl(url, getAuthHeaders());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("taxon", mTaxon);
        outState.putBoolean("guide_taxon", mGuideTaxon);
        outState.putString("taxon_id", mTaxonId);
        outState.putString("guide_id", mGuideId);
        outState.putString("guide_xml_filename", mGuideXmlFilename);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN){
            switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if ((!mGuideTaxon) && (mWebView.canGoBack() == true)) {
                    mWebView.goBack();
                } else {
                    finish();
                }
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }


     public class GalleryPhotoAdapter extends BaseAdapter {
         private Context mContext;
         private HashMap<Integer, ImageView> mViews;
         private List<GuideTaxonPhotoXML> mPhotos;

        public GalleryPhotoAdapter(Context c, List<GuideTaxonPhotoXML> photos) {
             mContext = c;
             mPhotos = photos;
             mViews = new HashMap<Integer, ImageView>();
         }

         public int getCount() {
             return mPhotos.size();
         }

         public Object getItem(int position) {
             return mPhotos.get(position);
         }

         public Uri getItemUri(int position) {
             GuideTaxonPhotoXML photo = mPhotos.get(position);
             boolean isOffline = photo.getGuide().isGuideDownloaded();
             GuideTaxonPhotoXML.PhotoType photoType;

             if (isOffline) {
                 // Offline version of the photo is available
                 photoType = GuideTaxonPhotoXML.PhotoType.LOCAL;
             } else {
                 // Use online version of the photo
                 photoType = GuideTaxonPhotoXML.PhotoType.REMOTE;
             }

             String photoPath = this.getPhotoLocation(photo, photoType);

             if (isOffline) {
                 return Uri.fromFile(new File(photoPath));
             } else {
                 return Uri.parse(photoPath);
             }
         }

         public long getItemId(int position) {
             return position;
         }

         // Gets the photo location (local/remote) - tries a specific size, and if not found,
         // tries the next best size until something is found
         private String getPhotoLocation(GuideTaxonPhotoXML photo, GuideTaxonPhotoXML.PhotoType photoType) {
             final GuideTaxonPhotoXML.PhotoSize[] DEFAULT_SIZES = {
                     GuideTaxonPhotoXML.PhotoSize.MEDIUM,
                     GuideTaxonPhotoXML.PhotoSize.LARGE,
                     GuideTaxonPhotoXML.PhotoSize.SMALL,
                     GuideTaxonPhotoXML.PhotoSize.THUMBNAIL
             };

             String photoLocation = null;
             for (GuideTaxonPhotoXML.PhotoSize size : DEFAULT_SIZES) {
                 photoLocation = photo.getPhotoLocation(photoType, size);

                 // See if we found a photo for current size - if not, try the next best size
                 if ((photoLocation != null) && (photoLocation.length() > 0)) break;
             }

             return photoLocation;
         }

         public View getView(int position, View convertView, ViewGroup parent) {
             if (mViews.containsKey(position)) {
                 return (ImageView) mViews.get(position);
             }

             ImageView imageView = new ImageView(mContext);
             imageView.setLayoutParams(new Gallery.LayoutParams(Gallery.LayoutParams.FILL_PARENT, Gallery.LayoutParams.FILL_PARENT));
             imageView.setScaleType(ImageView.ScaleType.FIT_START);

             GuideTaxonPhotoXML photo = mPhotos.get(position);
             GuideTaxonPhotoXML.PhotoType photoType;
             boolean isOffline = photo.getGuide().isGuideDownloaded();

             if (isOffline) {
                 // Offline version of the photo is available
                 photoType = GuideTaxonPhotoXML.PhotoType.LOCAL;
             } else {
                 // Use online version of the photo
                 photoType = GuideTaxonPhotoXML.PhotoType.REMOTE;
             }

             String photoPath = this.getPhotoLocation(photo, photoType);

             if (isOffline) {
                 Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
                 imageView.setImageBitmap(bitmap);
             } else {
                 UrlImageViewHelper.setUrlDrawable(imageView, photoPath);
             }

             mViews.put(position, imageView);
             return imageView;
         }
     }
 
}
