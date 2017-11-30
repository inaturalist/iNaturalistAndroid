package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.joda.time.Duration;
import org.json.JSONException;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultipleChoiceAdapter extends ArrayAdapter<String> {

    private List<String> mItems;
    private Set<Integer> mSelectedItems;
	private Context mContext;

	public MultipleChoiceAdapter(Context context, List<String> objects, Set<Integer> selectedItems) {
		super(context, R.layout.multiple_choice_item, objects);

		mItems = objects;
		mContext = context;
        mSelectedItems = new HashSet<>(selectedItems);
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = convertView == null ? inflater.inflate(R.layout.multiple_choice_item, parent, false) : convertView;
		String item = mItems.get(position);

        TextView text = (TextView) view.findViewById(R.id.text);
        final View checkbox = view.findViewById(R.id.checkbox);

        text.setText(item);

        checkbox.setSelected(mSelectedItems.contains(position));

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkbox.setSelected(!checkbox.isSelected());
                if (checkbox.isSelected()) {
                    mSelectedItems.add(position);
                } else {
                    mSelectedItems.remove(position);
                }
            }
        });

		return view;
	}

	public Set<Integer> getSelectedItems() {
	    return mSelectedItems;
    }

}

