package org.inaturalist.android;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class ObservationListActivity extends ListActivity {
	public static String TAG = "INAT";
  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.observation_list);
        
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(Observation.CONTENT_URI);
        }
        
        // Inform the list we provide context menus for items
        getListView().setOnCreateContextMenuListener(this);
        
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);
        
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        String conditions = "_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        
        Cursor cursor = managedQuery(getIntent().getData(), Observation.PROJECTION, 
        		conditions, null, Observation.DEFAULT_SORT_ORDER);

        // Used to map notes entries from the database to views
        ObservationCursorAdapter adapter = new ObservationCursorAdapter(
                this, R.layout.list_item, cursor,
                new String[] { Observation.SPECIES_GUESS, Observation.DESCRIPTION }, 
                new int[] { R.id.speciesGuess, R.id.subContent });
        setListAdapter(adapter);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.observations_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.observations_menu_add:
            // Launch activity to insert a new item
        	startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), this, ObservationEditor.class));
            return true;
        case R.id.observations_menu_sync:
            Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, this, INaturalistService.class);
            startService(serviceIntent);
            return true;
        case R.id.observations_menu_menu:
            startActivity(new Intent(this, MenuActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user.  The have clicked on one, so return it now.
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, uri, this, ObservationEditor.class));
        }
    }
    
    private class ObservationCursorAdapter extends SimpleCursorAdapter {
        private HashMap<Long, String[]> mPhotoInfo = new HashMap<Long, String[]>();
        
        public ObservationCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
            getPhotoInfo();
        }
        
        /**
         * Retrieves photo ids and orientations for photos associated with the listed observations.
         */
        public void getPhotoInfo() {
            Cursor c = getCursor();
            if (c.getCount() == 0) return;
            c.moveToFirst();
            ArrayList<Long> obsIds = new ArrayList<Long>();
            ArrayList<Long> photoIds = new ArrayList<Long>();
            while (!c.isAfterLast()) {
                obsIds.add(c.getLong(c.getColumnIndexOrThrow(Observation._ID)));
                c.moveToNext();
            }
            Cursor opc = managedQuery(ObservationPhoto.CONTENT_URI, 
                    new String[]{ObservationPhoto._ID, ObservationPhoto._OBSERVATION_ID, ObservationPhoto._PHOTO_ID}, 
                    "_observation_id IN ("+StringUtils.join(obsIds, ',')+")", 
                    null, 
                    ObservationPhoto._ID);
            if (opc.getCount() == 0) return;
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                photoIds.add(opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID)));
                opc.moveToNext();
            }
            
            Cursor pc = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.Images.ImageColumns.ORIENTATION}, 
                    "_ID IN ("+StringUtils.join(photoIds, ',')+")", 
                    null, 
                    null);
            if (pc.getCount() == 0) return;
            HashMap<Long,String> orientationsByPhotoId = new HashMap<Long,String>();
            pc.moveToFirst();
            while (!pc.isAfterLast()) {
                orientationsByPhotoId.put(
                        pc.getLong(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID)), 
                        pc.getString(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION)));
                pc.moveToNext();
            }
            
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                Long obsId = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
                Long photoId = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
                if (!mPhotoInfo.containsKey(obsId)) {
                    mPhotoInfo.put(
                            obsId,
                            new String[] {
                                photoId.toString(),
                                orientationsByPhotoId.get(photoId)
                            });
                }
                opc.moveToNext();
            }
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            Cursor c = this.getCursor();
            if (c.getCount() == 0) {
                return view;
            }
            ImageView image = (ImageView) view.findViewById(R.id.image);
            c.moveToPosition(position);
            Long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
            
            String[] photoInfo = mPhotoInfo.get(obsId);
            if (photoInfo != null) {
                if (photoInfo[0] == null || photoInfo[0].equals("null")) return view;
                Long photoId = Long.parseLong(photoInfo[0]);
                Integer orientation;
                if (photoInfo[1] == null || photoInfo[1].equals("null")) {
                    orientation = 0;
                } else {
                    orientation = Integer.parseInt(photoInfo[1]);
                }
                Bitmap bitmapImage = MediaStore.Images.Thumbnails.getThumbnail(
                        getContentResolver(), 
                        photoId, 
                        MediaStore.Images.Thumbnails.MICRO_KIND, 
                        (BitmapFactory.Options) null);
                if (orientation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
                    bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
                }
                image.setImageBitmap(bitmapImage);
                return view;
            }
            
            String iconicTaxonName = c.getString(c.getColumnIndexOrThrow(Observation.ICONIC_TAXON_NAME));
            if (iconicTaxonName == null) {
                image.setImageResource(R.drawable.iconic_taxon_unknown);
            } else if (iconicTaxonName.equals("Animalia")) {
                image.setImageResource(R.drawable.iconic_taxon_animalia);
            } else if (iconicTaxonName.equals("Plantae")) {
                image.setImageResource(R.drawable.iconic_taxon_plantae);
            } else if (iconicTaxonName.equals("Chromista")) {
                image.setImageResource(R.drawable.iconic_taxon_chromista);
            } else if (iconicTaxonName.equals("Fungi")) {
                image.setImageResource(R.drawable.iconic_taxon_fungi);
            } else if (iconicTaxonName.equals("Protozoa")) {
                image.setImageResource(R.drawable.iconic_taxon_protozoa);
            } else if (iconicTaxonName.equals("Actinopterygii")) {
                image.setImageResource(R.drawable.iconic_taxon_actinopterygii);
            } else if (iconicTaxonName.equals("Amphibia")) {
                image.setImageResource(R.drawable.iconic_taxon_amphibia);
            } else if (iconicTaxonName.equals("Reptilia")) {
                image.setImageResource(R.drawable.iconic_taxon_reptilia);
            } else if (iconicTaxonName.equals("Aves")) {
                image.setImageResource(R.drawable.iconic_taxon_aves);
            } else if (iconicTaxonName.equals("Mammalia")) {
                image.setImageResource(R.drawable.iconic_taxon_mammalia);
            } else if (iconicTaxonName.equals("Mollusca")) {
                image.setImageResource(R.drawable.iconic_taxon_mollusca);
            } else if (iconicTaxonName.equals("Insecta")) {
                image.setImageResource(R.drawable.iconic_taxon_insecta);
            } else if (iconicTaxonName.equals("Arachnida")) {
                image.setImageResource(R.drawable.iconic_taxon_arachnida);
            } else {
                image.setImageResource(R.drawable.iconic_taxon_unknown);
            }
            return view;
        }
        
    }
}