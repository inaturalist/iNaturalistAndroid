package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TimePicker;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.ptashek.widgets.datetimepicker.DateTimePicker;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ProjectFieldViewer {
    private static final String TAG = "ProjectFieldViewer";
    public static final int PROJECT_FIELD_TAXON_SEARCH_REQUEST_CODE = 301;
    private final boolean mIsConfirmation;
    private SherlockFragmentActivity mContext;
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

    @SuppressLint("ValidFragment")
    private class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

        private boolean mIsCanceled;
        private int mHour, mMinute;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String currentHour = mDate.getText().toString();
            Calendar c;

            mIsCanceled = false;

            if (currentHour.equals("")) {
                // Use the current hour as the default hour in the picker
                c = Calendar.getInstance();
            } else {
                Date date;
                c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, Integer.valueOf(currentHour.split(":")[0]));
                c.set(Calendar.MINUTE, Integer.valueOf(currentHour.split(":")[1]));
            }

            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            // Create a new instance of TimePickerDialog and return it
            TimePickerDialog dialog = new TimePickerDialog(getActivity(), this, hour, minute, true);
            dialog.setOnCancelListener(this);
            dialog.setOnDismissListener(this);

            return dialog;
        }

        public void onCancel(DialogInterface dialog) {
            mIsCanceled = true;
        }

        public void onDismiss(DialogInterface dialog) {
            if (!mIsCanceled) {
                mDate.setText(String.format("%02d:%02d", mHour, mMinute));
            }

            dismiss();
        }

        @Override
        public void onTimeSet(TimePicker view, int hour, int minute) {
            mHour = hour;
            mMinute = minute;
        }
    }


    @SuppressLint("ValidFragment")
    private class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

        private boolean mIsCanceled;
        private int mYear, mMonth, mDay;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String currentDate = mDate.getText().toString();
            Calendar c;

            mIsCanceled = false;

            if (currentDate.equals("")) {
                // Use the current date as the default date in the picker
                c = Calendar.getInstance();
            } else {
                Date date;
                c = Calendar.getInstance();
                try {
                    date = new SimpleDateFormat("yyyy-MM-dd").parse(currentDate);
                    c.setTime(date);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            DatePickerDialog dialog = new DatePickerDialog(getActivity(), this, year, month, day);
            dialog.setOnCancelListener(this);
            dialog.setOnDismissListener(this);

            return dialog;
        }

        public void onCancel(DialogInterface dialog) {
            mIsCanceled = true;
        }

        public void onDismiss(DialogInterface dialog) {
            if (!mIsCanceled) {
                mDate.setText(String.format("%d-%02d-%02d", mYear, mMonth + 1, mDay));
            }

            dismiss();
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            mYear = year;
            mMonth = month;
            mDay = day;
        }
    }


    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext.unregisterReceiver(mTaxonReceiver);

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
            mIdTaxonName.setText(taxon.getString("name"));
            mIdTaxonName.setTypeface(null, Typeface.ITALIC);
        }
    }


    public ProjectFieldViewer(SherlockFragmentActivity context, ProjectField field, ProjectFieldValue fieldValue, boolean isConfirmation) {
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

    public View getView() {
        ViewGroup row = (ViewGroup) LayoutInflater.from(mContext).inflate(mIsConfirmation ? R.layout.project_field_confirmation : R.layout.project_field, null);
        row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        mFieldName = (TextView) row.findViewById(R.id.field_name);
        mFieldDescription = (TextView) row.findViewById(R.id.field_description);
        mEditText = (EditText) row.findViewById(R.id.edit_text);
        mSpinner = (Spinner) row.findViewById(R.id.spinner);
        mDateContainer = (RelativeLayout) row.findViewById(R.id.date_container);
        mSetDate = (ImageView) row.findViewById(R.id.set_date);
        mDate = (TextView) row.findViewById(R.id.date);
        mTaxonContainer = (RelativeLayout) row.findViewById(R.id.taxon_container);
        mTaxonPic = (ImageView) row.findViewById(R.id.taxon_pic);
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
                    DialogFragment newFragment = new DatePickerFragment();
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
                    DialogFragment newFragment = new TimePickerFragment();
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
                mContext.registerReceiver(mTaxonReceiver, filter);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON, null, mContext, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxonId);
                mContext.startService(serviceIntent);
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
        void onProjectFieldsResults(Hashtable<Integer, ProjectField> projectFields, HashMap<Integer, ProjectFieldValue> projectValues);
    }

    public static void getProjectFields(Context context, List<Integer> projectIds, int obsId, ProjectFieldsResults resultsCallback) {
        Hashtable<Integer, ProjectField> projectFields = new Hashtable<Integer, ProjectField>();
        HashMap<Integer, ProjectFieldValue> projectFieldValues = new HashMap<Integer, ProjectFieldValue>();

        // Get project fields
        for (int projectId : projectIds) {
            Cursor c = context.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                    "(project_id = " + projectId + ")",
                    null, ProjectField.DEFAULT_SORT_ORDER);

            c.moveToFirst();

            while (c.isAfterLast() == false) {
                ProjectField projectField = new ProjectField(c);
                projectFields.put(projectField.field_id, projectField);
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

            if (!projectFields.containsKey(fieldValue.field_id)) {
                // It's a custom non-project field
                Cursor c2 = context.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                        "(field_id = " + fieldValue.field_id + ")", null, ProjectField.DEFAULT_SORT_ORDER);
                c2.moveToFirst();
                if (!c2.isAfterLast()) {
                    ProjectField field = new ProjectField(c2);
                    projectFields.put(fieldValue.field_id, field);
                }
                c2.close();
            }

            c.moveToNext();
        }
        c.close();

        resultsCallback.onProjectFieldsResults(projectFields, projectFieldValues);
    }

    // Returns a sorted field list for a specific project
    public static List<ProjectField> sortProjectFields(final int projectId, Hashtable<Integer, ProjectField> projectFields) {
        ArrayList<Map.Entry<Integer, ProjectField>> fields = new ArrayList(projectFields.entrySet());

        // Filter by project ID
        CollectionUtils.filter(fields, new Predicate<Map.Entry<Integer, ProjectField>>() {
            @Override
            public boolean evaluate(Map.Entry<Integer, ProjectField> object) {
                ProjectField field = object.getValue();
                return (field.project_id != null) && (field.project_id == projectId);
            }
        });

        // Then sort by position
        Collections.sort(fields, new Comparator<Map.Entry<Integer, ProjectField>>() {
            @Override
            public int compare(Map.Entry<Integer, ProjectField> lhs, Map.Entry<Integer, ProjectField> rhs) {
                ProjectField field1 = lhs.getValue();
                ProjectField field2 = rhs.getValue();

                Integer projectId1 = (field1.project_id != null ? field1.project_id : Integer.valueOf(-1));
                Integer projectId2 = (field2.project_id != null ? field2.project_id : Integer.valueOf(-1));

                if (projectId1 == projectId2) {
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

        Iterator<Map.Entry<Integer, ProjectField>> iterator = fields.iterator();
        List<ProjectField> resultProjectFields = new ArrayList<ProjectField>();

        while (iterator.hasNext()) {
            ProjectField field = iterator.next().getValue();
            resultProjectFields.add(field);
        }
        return resultProjectFields;
    }

}
