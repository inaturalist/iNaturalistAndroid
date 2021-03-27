package org.inaturalist.android;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AboutLicensesAdapter extends RecyclerView.Adapter<AboutLicensesAdapter.ViewHolder> {
    private static final String TAG = "AboutLicensesAdapter";
    private final ActivityHelper mHelper;
    private List<License> mLicenses;
    private Context mContext;

    public AboutLicensesAdapter(Context context, List<License> licenses) {
        mLicenses = licenses;
        mContext = context;
        mHelper = new ActivityHelper(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.about_license_list_item, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        License license = mLicenses.get(position);

        if (license.logoResource == null) {
            holder.icon.setVisibility(View.GONE);
        } else {
            holder.icon.setVisibility(View.VISIBLE);
            holder.icon.setImageResource(license.logoResource);
        }

        holder.licenseName.setText(license.name);
        holder.licenseDescription.setText(license.description);
        holder.gbif.setVisibility(license.gbifCompatible ? View.VISIBLE : View.GONE);
        holder.wikimedia.setVisibility(license.wikimediaCompatible ? View.VISIBLE : View.GONE);
        holder.licenseLink.setVisibility(license.url != null ? View.VISIBLE : View.GONE);

        if (license.url != null) {
            holder.licenseLink.setOnClickListener(v -> mHelper.openUrlInBrowser(license.url));
        }
    }

    @Override
    public int getItemCount() {
        return mLicenses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView icon;
        public TextView licenseName;
        public TextView gbif;
        public TextView wikimedia;
        public TextView licenseDescription;
        public TextView licenseLink;
        public View rootView;

        public ViewHolder(@NonNull View view) {
            super(view);

            icon = view.findViewById(R.id.license_icon);
            licenseName = view.findViewById(R.id.license_name);
            gbif = view.findViewById(R.id.gbif);
            wikimedia = view.findViewById(R.id.wikimedia);
            licenseDescription = view.findViewById(R.id.license_description);
            licenseLink = view.findViewById(R.id.license_link);
            rootView = view;
        }
    }
}
