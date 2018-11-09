package org.inaturalist.android;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.inaturalist.android.INaturalistService.LoginType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.*;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;

public class GuideTaxonActivity extends AppCompatActivity {
    private static String TAG = "GuideTaxonActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	@State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mTaxon;
	@State public boolean mGuideTaxon;
    @State public String mTaxonId;
    @State public String mGuideXmlFilename;
    private GuideXML mGuideXml;
    private GuideTaxonXML mGuideTaxonXml;
    @State public String mGuideId;
    @State public boolean mShowAdd;
    @State public boolean mDownloadTaxon;
    private TaxonReceiver mTaxonReceiver;

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


    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxonReceiver);
            findViewById(R.id.loading_taxon).setVisibility(View.GONE);
            findViewById(R.id.taxon_details).setVisibility(View.VISIBLE);

            BetterJSONObject taxon = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_RESULT);

            if (taxon == null) {
                return;
            }

            mTaxon = taxon;
            loadTaxon();
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();

        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra("taxon");
        	mGuideTaxon = intent.getBooleanExtra("guide_taxon", true);
            mTaxonId = intent.getStringExtra("taxon_id");
            mGuideId = intent.getStringExtra("guide_id");
            mGuideXmlFilename = intent.getStringExtra("guide_xml_filename");
            mShowAdd = intent.getBooleanExtra("show_add", true);
            mDownloadTaxon = intent.getBooleanExtra("download_taxon", false);
        }

        if ((mGuideTaxon) && (mGuideXmlFilename != null)) {
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
            setContentView(R.layout.taxon);

            if (mDownloadTaxon) {
                findViewById(R.id.loading_taxon).setVisibility(View.VISIBLE);
                findViewById(R.id.taxon_details).setVisibility(View.GONE);
            }
        }

        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);

        String title = "";
        if ((mGuideTaxon) && (mGuideXmlFilename != null) && (mGuideTaxonXml != null)) {
            title = mGuideTaxonXml.getDisplayName();
            if ((title == null) || (title.length() == 0)) title = mGuideTaxonXml.getName();
       } else {

            if (!mDownloadTaxon) {
                loadTaxon();
            }
            title = getString(R.string.about_this_species);
        }

        actionBar.setTitle(title);

    }

    private void loadTaxon() {
        if (mTaxon == null) {
            finish();
            return;
        }

        findViewById(R.id.loading_image).setVisibility(View.VISIBLE);

        TextView displayNameText = (TextView)findViewById(R.id.displayName);
        TextView name = (TextView)findViewById(R.id.name);

        String displayName = null;
        // Get the taxon display name according to configuration of the current iNat network
        if (displayName == null) {
            // Couldn't extract the display name from the taxon names list - use the default one
            try {
                displayName = mTaxon.getJSONObject().getString("unique_name");
            } catch (JSONException e2) {
            }
            try {
                JSONObject defaultName = mTaxon.getJSONObject().getJSONObject("default_name");
                displayName = defaultName.getString("name");
            } catch (JSONException e1) {
                // alas
            }

            if (displayName == null) {
                displayName = mTaxon.getJSONObject().optString("preferred_common_name");
            }
            if ((displayName == null) || (displayName.length() == 0)) {
                displayName = mTaxon.getJSONObject().optString("english_common_name");
            }
        }

        if ((displayName == null) || (displayName.length() == 0)) {
            displayNameText.setText(mTaxon.getJSONObject().optString("name"));
            name.setVisibility(View.GONE);
        } else {
            displayNameText.setText(displayName);
            name.setText(TaxonUtils.getTaxonScientificName(mTaxon.getJSONObject()));
        }

        JSONObject itemJson = mTaxon.getJSONObject();
        ImageView taxonImage = (ImageView) findViewById(R.id.taxon_image);
        String photoUrl = null;
        JSONObject defaultPhoto = itemJson.isNull("default_photo") ? null : itemJson.optJSONObject("default_photo");
        TextView photosAttr = (TextView) findViewById(R.id.attributions_photos);

        if (defaultPhoto != null) {
            photoUrl = defaultPhoto.optString("medium_url");
            photosAttr.setText(Html.fromHtml(String.format(getString(R.string.photo), photoUrl, defaultPhoto.optString("attribution"))));
            photosAttr.setMovementMethod(LinkMovementMethod.getInstance());
            stripUnderlines(photosAttr);
        } else {
            photosAttr.setVisibility(View.GONE);
        }

        if ((photoUrl == null) || (photoUrl.length() == 0)) {
            photoUrl = itemJson.isNull("photo_url") ? null : itemJson.optString("photo_url");
        }
        if (photoUrl != null) {
            findViewById(R.id.loading_image).setVisibility(View.VISIBLE);

            UrlImageViewHelper.setUrlDrawable(taxonImage, photoUrl, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    findViewById(R.id.loading_image).setVisibility(View.GONE);
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    return loadedBitmap;
                }
            });
        } else {
            findViewById(R.id.loading_image).setVisibility(View.GONE);
            taxonImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            taxonImage.setImageResource(TaxonUtils.observationIcon(itemJson));
        }

        TextView description = (TextView) findViewById(R.id.description);
        String descriptionText = itemJson.isNull("wikipedia_summary") ? "" : itemJson.optString("wikipedia_summary", "");
        description.setText(Html.fromHtml(descriptionText));

        ViewGroup viewOnWiki = (ViewGroup) findViewById(R.id.view_on_wikipedia);
        viewOnWiki.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject taxonObj = mTaxon.getJSONObject();
                String obsUrl = null;
                try {
                    String wikiTitle;
                    if ((taxonObj.has("wikipedia_title")) && (!taxonObj.isNull("wikipedia_title")) && (taxonObj.optString("wikipedia_title").length() > 0)) {
                        wikiTitle = taxonObj.optString("wikipedia_title");
                    } else {
                        wikiTitle = taxonObj.optString("name");
                    }
                    wikiTitle = wikiTitle.replace(" ", "_");
                    Locale deviceLocale = getResources().getConfiguration().locale;
                    String deviceLanguage =   deviceLocale.getLanguage();
                    obsUrl = "https://" + deviceLanguage + ".wikipedia.org/wiki/" + URLEncoder.encode(wikiTitle, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(obsUrl));
                startActivity(i);
            }
        });

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        if (mShowAdd) {
            inflater.inflate(R.menu.guide_taxon_menu, menu);
        }
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
    

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
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

         // Gets the photo place (local/remote) - tries a specific size, and if not found,
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


    private class URLSpanNoUnderline extends URLSpan {
        public URLSpanNoUnderline(String url) {
            super(url);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }

    private void stripUnderlines(TextView textView) {
        Spannable s = (Spannable)textView.getText();
        URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
        for (URLSpan span: spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            s.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL());
            s.setSpan(span, start, end, 0);
        }
        textView.setText(s);
    }


    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mDownloadTaxon) {
            // Get the taxon details
            mTaxonReceiver = new TaxonReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_RESULT);
            Log.i(TAG, "Registering ACTION_GET_TAXON_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mTaxonReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxonId != null ? mTaxonId : mTaxon.getInt("id"));
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }
}
