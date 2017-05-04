package org.inaturalist.android;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.Calendar;
import java.util.Date;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

    private boolean mIsCanceled;
    private int mHour, mMinute;
    private TextView mDate;

    public void setDate(TextView date) {
        mDate = date;
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
        }

        dismiss();
    }

    @Override
    public void onTimeSet(TimePicker view, int hour, int minute) {
        mHour = hour;
        mMinute = minute;
    }
}
