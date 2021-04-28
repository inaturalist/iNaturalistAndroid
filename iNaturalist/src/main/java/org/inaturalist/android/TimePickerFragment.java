package org.inaturalist.android;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

import android.util.TypedValue;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.Calendar;
import java.util.Date;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

    private boolean mIsCanceled;
    private int mHour, mMinute;
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
            mOnDateChange.onDateChange(mDate.getText().toString());
        }

        dismiss();
    }

    @Override
    public void onTimeSet(TimePicker view, int hour, int minute) {
        mHour = hour;
        mMinute = minute;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((TimePickerDialog) getDialog()).getButton(DatePickerDialog.BUTTON_POSITIVE).setAutoSizeTextTypeUniformWithConfiguration(14, 15, 1, TypedValue.COMPLEX_UNIT_SP);
            ((TimePickerDialog) getDialog()).getButton(DatePickerDialog.BUTTON_NEGATIVE).setAutoSizeTextTypeUniformWithConfiguration(14, 15, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

}
