package org.inaturalist.android;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

import android.util.Log;
import android.util.TypedValue;
import android.widget.DatePicker;
import android.widget.TextView;

import org.tinylog.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {
    private static final String TAG = "DialogFragment";
    private boolean mIsCanceled;
    private int mYear, mMonth, mDay;
    private TextView mDate;
    private OnDateChange mOnDateChange;

    public void setDate(TextView date) {
        mDate = date;
    }
    
    public interface OnDateChange {
        void onDateChange(String date);
    }
    
    public void setOnDateChange(OnDateChange onDateChange) {
        mOnDateChange = onDateChange;
    }
    
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
                Logger.tag(TAG).error(e);
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
            mOnDateChange.onDateChange(mDate.getText().toString());
        }

        dismiss();
    }

    public void onDateSet(DatePicker view, int year, int month, int day) {
        mYear = year;
        mMonth = month;
        mDay = day;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((DatePickerDialog) getDialog()).getButton(DatePickerDialog.BUTTON_POSITIVE).setAutoSizeTextTypeUniformWithConfiguration(14, 15, 1, TypedValue.COMPLEX_UNIT_SP);
            ((DatePickerDialog) getDialog()).getButton(DatePickerDialog.BUTTON_NEGATIVE).setAutoSizeTextTypeUniformWithConfiguration(14, 15, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

}
