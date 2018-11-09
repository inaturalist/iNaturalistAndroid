package org.inaturalist.android;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.ptashek.widgets.datetimepicker.DateTimePicker;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

public class ProjectFieldViewer {
    private static final String TAG = "ProjectFieldViewer";
    public static final int PROJECT_FIELD_TAXON_SEARCH_REQUEST_CODE = 301;
    private final boolean mIsConfirmation;
    private AppCompatActivity mContext;
    private ProjectField mField;
    private ProjectFieldValue mFieldValue;

    private int mTaxonId = -1;

    // UI elements
    private TextView mFieldName;
    private EditText mEditText;
    private Spinner mSpinner;
    private RelativeLayout mDateContainer;
    private ImageView mSetDate;
    private TextView mDate;
    private RelativeLayout mTaxonContainer;
    private ImageView mTaxonPic;
    private TextView mIdName;
    private TextView mIdTaxonName;
    private ArrayAdapter<String> mSpinnerAdapter;
    private TaxonReceiver mTaxonReceiver;
    private TextView mFieldDescription;
    private FocusedListener mFocusedListener;
    private boolean mIsFocusing;

    public void unregisterReceivers() {
        BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, mContext);
    }

    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, mContext);

            BetterJSONObject taxon = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_RESULT);

            if (taxon == null) {
                return;
            }

            int taxonId = taxon.getInt("id");

            if (taxonId != mTaxonId) {
                // Result was not from out taxon
                return;
            }

            UrlImageViewHelper.setUrlDrawable(mTaxonPic, taxon.getString("image_url"));
            mIdName.setText(taxon.getString("unique_name"));
            mIdTaxonName.setText(TaxonUtils.getTaxonScientificName(taxon.getJSONObject()));
            if (taxon.getJSONObject().optInt("rank_level", 0) <= 20) {
                mIdTaxonName.setTypeface(null, Typeface.ITALIC);
            } else {
                mIdTaxonName.setTypeface(null, Typeface.NORMAL);
            }
            String idNameString = getTaxonName(taxon.getJSONObject());
            if (idNameString != null) {
                mIdName.setText(idNameString);
                mIdTaxonName.setText(taxon.getJSONObject().optString("name", ""));
            } else {
                mIdName.setText(taxon.getJSONObject().optString("name", mContext.getResources().getString(R.string.unknown)));
                mIdTaxonName.setText("");
                mIdName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            }
        }
    }

    // Utility function for retrieving the Taxon's name
    private String getTaxonName(JSONObject item) {
        JSONObject defaultName;
        String displayName = null;


        // Get the taxon display name according to configuration of the current iNat network
        String inatNetwork = ((INaturalistApp)mContext.getApplication()).getInaturalistNetworkMember();
        String networkLexicon = ((INaturalistApp)mContext.getApplication()).getStringResourceByName("inat_lexicon_" + inatNetwork);
        try {
            JSONArray taxonNames = item.getJSONArray("taxon_names");
            for (int i = 0; i < taxonNames.length(); i++) {
                JSONObject taxonName = taxonNames.getJSONObject(i);
                String lexicon = taxonName.getString("lexicon");
                if (lexicon.equals(networkLexicon)) {
                    // Found the appropriate lexicon for the taxon
                    displayName = taxonName.getString("name");
                    break;
                }
            }
        } catch (JSONException e3) {
            e3.printStackTrace();
        }

        if (displayName == null) {
            // Couldn't extract the display name from the taxon names list - use the default one
            try {
                displayName = item.getString("unique_name");
            } catch (JSONException e2) {
                displayName = null;
            }
            try {
                defaultName = item.getJSONObject("default_name");
                displayName = defaultName.getString("name");
            } catch (JSONException e1) {
                // alas
                JSONObject commonName = item.optJSONObject("common_name");
                if (commonName != null) {
                    displayName = commonName.optString("name");
                } else {
                    displayName = item.optString("name");
                }
            }
        }

        return displayName;

    }


    public ProjectFieldViewer(AppCompatActivity context, ProjectField field, ProjectFieldValue fieldValue, boolean isConfirmation) {
        mField = field;
        mFieldValue = fieldValue;
        mContext = context;
        mIsConfirmation = isConfirmation;

        if (mFieldValue == null) {
            mFieldValue = new ProjectFieldValue();
        }

        if (mFieldValue.value == null) {
            mFieldValue.value = "";
        }
    }

    public ProjectField getField() {
        return mField;
    }

    public String getValue() {
        if ((mField.data_type.equals("text")) && (mField.allowed_values != null) && (!mField.allowed_values.equals(""))) {
            return (String) mSpinner.getSelectedItem();
        } else if (mField.data_type.equals("text")) {
            return mEditText.getText().toString();
        } else if (mField.data_type.equals("numeric")) {
            String val = mEditText.getText().toString();
            return (val.equals("") ? null : val);
        } else if (mField.data_type.equals("date")) {
            String dateString = mDate.getText().toString();
            if (dateString.equals("")) return null;
            return dateString;
        } else if (mField.data_type.equals("time")) {
            String dateString = mDate.getText().toString();
            if (dateString.equals("")) return null;
            return dateString;
        } else if (mField.data_type.equals("datetime")) {
            String dateString = mDate.getText().toString();
            if (dateString.equals("")) return null;
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(dateString);

                // Return a ISO8601 date string
                String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date);
                return formatted.substring(0, 22) + ":" + formatted.substring(22);

            } catch (ParseException e) {
                e.printStackTrace();
                return null;
            }
        } else if (mField.data_type.equals("taxon")) {
            if (mTaxonId == -1) return null;
            return String.valueOf(mTaxonId);
        } else {
            return null;
        }

    }

    public Boolean isValid() {
        if (mField.is_required) {
            String value = getValue();
            if (value == null || value.equals("")) {
                // Mandatory field
                return false;
            }
        }

        if ((mField.data_type.equals("numeric")) && (!mEditText.getText().toString().equals(""))) {
            try {
                float value = Float.valueOf(mEditText.getText().toString());
            } catch (Exception exc) {
                // Invalid number
                return false;
            }
        }


        return true;
    }

    public void onTaxonSearchResult(Intent data) {
        int taxonId = data.getIntExtra(TaxonSearchActivity.TAXON_ID, 0);
        String taxonName = data.getStringExtra(TaxonSearchActivity.TAXON_NAME);
        String idName = data.getStringExtra(TaxonSearchActivity.ID_NAME);
        String idImageUrl = data.getStringExtra(TaxonSearchActivity.ID_PIC_URL);
        int fieldId = data.getIntExtra(TaxonSearchActivity.FIELD_ID, 0);

        if ((fieldId != mField.field_id.intValue()) || (!mField.data_type.equals("taxon"))) {
            // Not our field
            return;
        }

        mTaxonId = taxonId;
        UrlImageViewHelper.setUrlDrawable(mTaxonPic, idImageUrl);
        mIdName.setText(idName);
        mIdTaxonName.setText(taxonName);
        mIdTaxonName.setTypeface(null, Typeface.ITALIC);
    }

    public void setFocus() {
        if (mEditText.getVisibility() == View.VISIBLE) {
            mIsFocusing = true;
            mEditText.requestFocus();
            mEditText.setSelection(mEditText.getText().length());
        }
    }
    
    public interface FocusedListener {
        void onFocused();
    }
    
    public void setOnFocusedListener(FocusedListener listener) {
        mFocusedListener = listener;
    }

    public View getView() {
        ViewGroup row = (ViewGroup) LayoutInflater.from(mContext).inflate(mIsConfirmation ? R.layout.project_field_confirmation : R.layout.project_field, null);
        row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        mFieldName = (TextView) row.findViewById(R.id.field_name);
        mFieldDescription = (TextView) row.findViewById(R.id.field_description);
        mEditText = (EditText) row.findViewById(R.id.edit_text);
        mEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    if (mFocusedListener != null) {
                        if (mIsFocusing) {
                            mIsFocusing = false;
                        } else {
                            mFocusedListener.onFocused();
                        }
                    }
                }
            }
        });

        mSpinner = (Spinner) row.findViewById(R.id.spinner);
        mDateContainer = (RelativeLayout) row.findViewById(R.id.date_container);
        mSetDate = (ImageView) row.findViewById(R.id.set_date);
        mDate = (TextView) row.findViewById(R.id.date);
        mTaxonContainer = (RelativeLayout) row.findViewById(R.id.taxon_container);
        mTaxonPic = (ImageView) row.findViewById(R.id.taxon_photo);
        mIdName = (TextView) row.findViewById(R.id.id_name);
        mIdTaxonName = (TextView) row.findViewById(R.id.id_taxon_name);
        mIdTaxonName.setTypeface(null, Typeface.ITALIC);

        mFieldName.setText(mField.name);
        mFieldDescription.setText(mField.description);

        if ((mField.description == null) || (mField.description.length() == 0)) {
            mFieldDescription.setVisibility(View.GONE);
        } else {
            mFieldDescription.setVisibility(View.VISIBLE);
        }

        if (mField.is_required) {
            mFieldName.setTypeface(null, Typeface.BOLD);

            if (mIsConfirmation) {
                mFieldName.setText(mFieldName.getText() + " *");
            }
        }

        if ((mField.data_type.equals("text")) && (mField.allowed_values != null) && (!mField.allowed_values.equals(""))) {
            mSpinner.setVisibility(View.VISIBLE);
            String[] allowedValues = mField.allowed_values.split("\\|");
            mSpinnerAdapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, android.R.id.text1, allowedValues);
            mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mSpinner.setAdapter(mSpinnerAdapter);

            int position = mSpinnerAdapter.getPosition(mFieldValue.value);
            if (position != -1) {
                mSpinner.setSelection(position);
            }
        } else if (mField.data_type.equals("text")) {
            mEditText.setVisibility(View.VISIBLE);
            mEditText.setText(mFieldValue.value);
        } else if (mField.data_type.equals("numeric")) {
            mEditText.setVisibility(View.VISIBLE);
            mEditText.setText(mFieldValue.value);
            mEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_NUMBER);
        } else if (mField.data_type.equals("date")) {
            mDateContainer.setVisibility(View.VISIBLE);

            if (!mFieldValue.value.equals("")) {
                mDate.setText(formatDate(mFieldValue.value));
            } else {
                mDate.setText("");
            }
            mDateContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DatePickerFragment newFragment = new DatePickerFragment();
                    newFragment.setDate(mDate);
                    newFragment.show(mContext.getSupportFragmentManager(), "datePicker");
                }
            });

        } else if (mField.data_type.equals("time")) {
            mDateContainer.setVisibility(View.VISIBLE);
            mDate.setText(mFieldValue.value);
            mSetDate.setImageResource(R.drawable.ic_action_time);
            mDateContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TimePickerFragment newFragment = new TimePickerFragment();
                    newFragment.setDate(mDate);
                    newFragment.show(mContext.getSupportFragmentManager(), "timePicker");
                }
            });


        } else if (mField.data_type.equals("datetime")) {
            mDateContainer.setVisibility(View.VISIBLE);
            // date time = 2013-11-14T13:23:37+00:00
            if (!mFieldValue.value.equals("")) {
                mDate.setText(formatDateTime(mFieldValue.value));
            } else {
                mDate.setText("");
            }
            mDateContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDateTimeDialog();
                }
            });

        } else if (mField.data_type.equals("taxon")) {
            mTaxonContainer.setVisibility(View.VISIBLE);

            if (mTaxonId == -1) {
                mTaxonId = (mFieldValue.value.equals("") ? -1 : Integer.valueOf(mFieldValue.value));
            }

            if (mTaxonId != -1) {
                // Get the taxon details
                mTaxonReceiver = new TaxonReceiver();
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_RESULT);
                Log.i(TAG, "Registering ACTION_GET_TAXON_RESULT");
                BaseFragmentActivity.safeRegisterReceiver(mTaxonReceiver, filter, mContext);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON, null, mContext, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxonId);
                ContextCompat.startForegroundService(mContext, serviceIntent);
            } else {
                mIdName.setText("");
                mIdTaxonName.setText("");
            }
            mTaxonContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, TaxonSearchActivity.class);
                    intent.putExtra(TaxonSearchActivity.FIELD_ID, mField.field_id);
                    mContext.startActivityForResult(intent, PROJECT_FIELD_TAXON_SEARCH_REQUEST_CODE);
                }
            });

        }

        return row;
    }

    private void showDateTimeDialog() {
        // Create the dialog
        final Dialog mDateTimeDialog = new Dialog(mContext);
        // Inflate the root layout
        final RelativeLayout mDateTimeDialogView = (RelativeLayout) mContext.getLayoutInflater().inflate(R.layout.date_time_dialog, null);
        // Grab widget instance
        final DateTimePicker mDateTimePicker = (DateTimePicker) mDateTimeDialogView.findViewById(R.id.DateTimePicker);

        // When the "OK" button is clicked
        ((Button) mDateTimeDialogView.findViewById(R.id.SetDateTime)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mDateTimePicker.clearFocus();

                Date selectedTime = mDateTimePicker.getDate();
                String formatted = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(selectedTime);
                mDate.setText(formatted);

                mDateTimeDialog.dismiss();
            }
        });

        // Cancel the dialog when the "Cancel" button is clicked
        ((Button) mDateTimeDialogView.findViewById(R.id.CancelDialog)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mDateTimeDialog.cancel();
            }
        });

        // Reset Date and Time pickers when the "Reset" button is clicked
        ((Button) mDateTimeDialogView.findViewById(R.id.ResetDateTime)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mDateTimePicker.reset();
            }
        });


        // Setup TimePicker
        mDateTimePicker.setIs24HourView(true);
        // No title on the dialog window
        mDateTimeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Set the dialog content view
        mDateTimeDialog.setContentView(mDateTimeDialogView);

        String dateValue = mDate.getText().toString();

        if (!dateValue.equals("")) {
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(dateValue);
                mDateTimePicker.updateDate(date.getYear() + 1900, date.getMonth(), date.getDate());
                mDateTimePicker.updateTime(date.getHours(), date.getMinutes());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }


        // Display the dialog
        mDateTimeDialog.show();
    }

    private Date valueToDateTime(String value) {
        Calendar calendar = GregorianCalendar.getInstance();
        String s = value.replace("Z", "+00:00");
        try {
            s = s.substring(0, 22) + s.substring(23);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }
        Date date;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        calendar.setTime(date);
        return calendar.getTime();
    }

    private Date valueToDate(String value) {
        Calendar calendar = GregorianCalendar.getInstance();
        Date date;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        calendar.setTime(date);

        return calendar.getTime();
    }


    private String formatDate(String value) {
        Date date = valueToDate(value);
        String formatted = new SimpleDateFormat("yyyy-MM-dd").format(date);
        return formatted;
    }

    private String formatDateTime(String value) {
        Date date = valueToDateTime(value);
        String formatted = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
        return formatted;
    }


    //
    // Utility methods to be used by several activies (for displaying and retrieving project fields)
    //


    // Returns all fields and field values for a specific observation

    public interface ProjectFieldsResults {
        void onProjectFieldsResults(ArrayList projectFields, HashMap<Integer, ProjectFieldValue> projectValues);
    }

    public static void getProjectFields(Context context, List<Integer> projectIds, int obsId, ProjectFieldsResults resultsCallback) {
        ArrayList projectFields = new ArrayList();
        HashMap<Integer, ProjectFieldValue> projectFieldValues = new HashMap<>();

        // Get project fields
        for (int projectId : projectIds) {
            Cursor c = context.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                    "(project_id = " + projectId + ")",
                    null, ProjectField.DEFAULT_SORT_ORDER);

            c.moveToFirst();

            while (c.isAfterLast() == false) {
                ProjectField projectField = new ProjectField(c);
                projectFields.add(projectField);
                c.moveToNext();
            }
            c.close();

        }

        // Get project field values

        Cursor c = context.getContentResolver().query(ProjectFieldValue.CONTENT_URI, ProjectFieldValue.PROJECTION,
                "(observation_id = " + obsId + ")",
                null, ProjectFieldValue.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ProjectFieldValue fieldValue = new ProjectFieldValue(c);
            projectFieldValues.put(fieldValue.field_id, fieldValue);
            c.moveToNext();
        }
        c.close();

        resultsCallback.onProjectFieldsResults(projectFields, projectFieldValues);
    }

    // Returns a sorted field list for a specific project
    public static List<ProjectField> sortProjectFields(final int projectId, ArrayList projectFields) {
        // Filter by project ID
        CollectionUtils.filter(projectFields, new Predicate<ProjectField>() {
            @Override
            public boolean evaluate(ProjectField field) {
                return (field.project_id != null) && (field.project_id == projectId);
            }
        });

        // Then sort by position
        Collections.sort(projectFields, new Comparator<ProjectField>() {
            @Override
            public int compare(ProjectField field1, ProjectField field2) {
                Integer projectId1 = (field1.project_id != null ? field1.project_id : Integer.valueOf(-1));
                Integer projectId2 = (field2.project_id != null ? field2.project_id : Integer.valueOf(-1));

                if (projectId1.equals(projectId2)) {
                    // Same project - sort by position
                    Integer position1 = (field1.position != null ? field1.position : Integer.valueOf(0));
                    Integer position2 = (field2.position != null ? field2.position : Integer.valueOf(0));

                    return position1.compareTo(position2);
                } else {
                    // Group fields together in the same project
                    return projectId1.compareTo(projectId2);
                }
            }
        });
        return projectFields;
    }

}
