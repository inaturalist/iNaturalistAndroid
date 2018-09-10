package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.co.senab.photoview.HackyViewPager;
import uk.co.senab.photoview.PhotoViewAttacher;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager.LayoutParams;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class TaxonTagPhotosViewer extends AppCompatActivity {
	private static String TAG = "TaxonTagPhotosViewer";
	private INaturalistApp mApp;
	private ActivityHelper mHelper;
	private HackyViewPager mViewPager;
    @State public String mGuideId;
    @State public String mGuideXmlFilename;
    @State public String mTagName;
    @State public String mTagValue;
    private GuideXML mGuideXml;

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
        Bridge.restoreInstanceState(this, savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setIcon(android.R.color.transparent);
		actionBar.setLogo(R.drawable.up_icon);

		mApp = (INaturalistApp) getApplicationContext();
		setContentView(R.layout.observation_photos);

		Intent intent = getIntent();

        if (savedInstanceState == null) {
            mGuideId = intent.getStringExtra("guide_id");
            mGuideXmlFilename = intent.getStringExtra("guide_xml_filename");
            mTagName = intent.getStringExtra("tag_name");
            mTagValue = intent.getStringExtra("tag_value");
            actionBar.setTitle(mTagName + "=" + mTagValue);
        } else {
            actionBar.setTitle(mTagName + "=" + mTagValue);
        }


		mViewPager = (HackyViewPager) findViewById(R.id.id_pic_view_pager);

        if (mGuideXmlFilename != null) {
            // Load guide taxon from XML
            mGuideXml = new GuideXML(this, mGuideId, mGuideXmlFilename);
            mViewPager.setAdapter(new TagPicsPagerAdapter());
        }

	}


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
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Bridge.saveInstanceState(this, outState);
	}


	class TagPicsPagerAdapter extends PagerAdapter {
		int mDefaultTaxonIcon;
		List<String> mImages;

		public TagPicsPagerAdapter() {
			mImages = new ArrayList<String>();

            List<GuideTaxonPhotoXML> photos =  mGuideXml.getTagRepresentativePhoto(mTagName, mTagValue);
			if ((photos != null) && (photos.size() > 0)) {
				// Show the photos
				for (int i = 0; i < photos.size(); i++) {
					GuideTaxonPhotoXML photo = photos.get(i);
                    mImages.add(getPhotoLocation(photo));
				}
			} else {
                // No photos at all
                finish();
			}
		}

		@Override
		public int getCount() {
			return mImages.size();
		}

		@Override
		public View instantiateItem(ViewGroup container, int position) {

			String imageUrl = mImages.get(position);
			View layout = (View) getLayoutInflater().inflate(R.layout.observation_photo, null, false);
			container.addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

            final PhotoViewAttacher attacher = new PhotoViewAttacher(imageView);

            if (mGuideXml.isGuideDownloaded()) {
                // Show offline photo
                Bitmap bitmap = BitmapFactory.decodeFile(imageUrl);
                imageView.setImageBitmap(bitmap);

                loading.setVisibility(View.INVISIBLE);
                imageView.setVisibility(View.VISIBLE);

            } else {
                loading.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.INVISIBLE);

                // Show online photo
                UrlImageViewHelper.setUrlDrawable(imageView, imageUrl, mDefaultTaxonIcon, new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        loading.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                        attacher.update();
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        // No post-processing of bitmap
                        return loadedBitmap;
                    }
                });
            }

			return layout;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			container.removeView((View) object);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}



	}

    private String getPhotoLocation(GuideTaxonPhotoXML photo) {
        final GuideTaxonPhotoXML.PhotoSize[] DEFAULT_SIZES = {
                GuideTaxonPhotoXML.PhotoSize.LARGE,
                GuideTaxonPhotoXML.PhotoSize.MEDIUM,
                GuideTaxonPhotoXML.PhotoSize.SMALL,
                GuideTaxonPhotoXML.PhotoSize.THUMBNAIL
        };

        // Determine if to use offline/online photo
        GuideTaxonPhotoXML.PhotoType photoType =
                (mGuideXml.isGuideDownloaded() ? GuideTaxonPhotoXML.PhotoType.LOCAL : GuideTaxonPhotoXML.PhotoType.REMOTE);

        String photoLocation = null;
        for (GuideTaxonPhotoXML.PhotoSize size : DEFAULT_SIZES) {
            photoLocation = photo.getPhotoLocation(photoType, size);

            // See if we found a photo for current size - if not, try the next best size
            if ((photoLocation != null) && (photoLocation.length() > 0)) break;
        }

        return photoLocation;
    }


}

