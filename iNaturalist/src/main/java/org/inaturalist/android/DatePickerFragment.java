package org.inaturalist.android;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
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

    public void setDate(TextView date) {
        mDate = date;
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
        }

        dismiss();
    }

    public void onDateSet(DatePicker view, int year, int month, int day) {
        mYear = year;
        mMonth = month;
        mDay = day;
    }
}
