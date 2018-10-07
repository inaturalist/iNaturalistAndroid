package org.inaturalist.android;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Picasso;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ExploreFiltersActivity extends AppCompatActivity {
    public static final String SEARCH_FILTERS = "search_filters";
    public static final String ALL_ANNOTATIONS = "all_annotations";

    private static final String[] ICONIC_TAXA = {
            "Plantae", "Aves", "Insecta", "Amphibia", "Reptilia", "Fungi", "Animalia", "Chromista", "Protozoa", "Actinopterygii", "Mammalia", "Mollusca", "Arachnida"
    };

    private static final int REQUEST_CODE_SEARCH_PROJECTS = 0x1000;
    private static final int REQUEST_CODE_SEARCH_USERS = 0x1001;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    @State public ExploreSearchFilters mSearchFilters;

    private Handler mHandler;

    private RecyclerView mTaxonicIcons;
    private Button mApplyFilters;
    private View mShowMyObservationsCheckbox;
    private ViewGroup mShowMyObservationsRow;
    private ImageView mProjectPic;
    private TextView mProjectName;
    private ImageView mClearProject;
    private ImageView mUserPic;
    private TextView mUserName;
    private ImageView mClearUser;
    private View mResearchGradeCheckbox;
    private View mNeedsIdCheckbox;
    private View mCasualGradeCheckbox;
    private RadioButton mOptionDateAny;
    private RadioButton mOptionDateExact;
    private Spinner mDateExact;
    private RadioButton mOptionDateMinMax;
    private RadioButton mOptionDateMonths;
    private TextView mDateAny;
    private Spinner mDateMin;
    private Spinner mDateMax;
    private Spinner mDateMonths;
    private Spinner mAnnotationName;
    private TextView mAnnotationEqual;
    private Spinner mAnnotationValue;

    private MenuItem mResetFilters;
    @State public SerializableJSONArray mAllAnnotations;


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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.explore_filters_menu, menu);
        mResetFilters = menu.findItem(R.id.reset_filters);
        refreshResetFiltersButton();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;

            case R.id.reset_filters:
                resetFilters();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setElevation(0);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.filters);

        mHelper = new ActivityHelper(this);

        setContentView(R.layout.explore_filters);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            mSearchFilters = (ExploreSearchFilters) intent.getSerializableExtra(SEARCH_FILTERS);
            mAllAnnotations = (SerializableJSONArray) intent.getSerializableExtra(ALL_ANNOTATIONS);
        }

        mHandler = new Handler();

        mApplyFilters = (Button) findViewById(R.id.apply_filters);
        mTaxonicIcons = (RecyclerView) findViewById(R.id.taxonic_icons);
        mShowMyObservationsCheckbox = (View) findViewById(R.id.show_my_observations_checkbox);
        mShowMyObservationsRow = (ViewGroup) findViewById(R.id.show_my_observations);
        mProjectPic = (ImageView) findViewById(R.id.project_pic);
        mProjectName = (TextView) findViewById(R.id.project_name);
        mClearProject = (ImageView) findViewById(R.id.clear_project);
        mUserPic = (ImageView) findViewById(R.id.user_pic);
        mUserName = (TextView) findViewById(R.id.user_name);
        mClearUser = (ImageView) findViewById(R.id.clear_user);
        mResearchGradeCheckbox = (View) findViewById(R.id.research_grade_checkbox);
        mNeedsIdCheckbox = (View) findViewById(R.id.needs_id_checkbox);
        mCasualGradeCheckbox = (View) findViewById(R.id.casual_grade_checkbox);
        mOptionDateAny = (RadioButton) findViewById(R.id.option_date_any);
        mOptionDateExact = (RadioButton) findViewById(R.id.option_date_exact);
        mOptionDateMinMax = (RadioButton) findViewById(R.id.option_date_min_max);
        mOptionDateMonths = (RadioButton) findViewById(R.id.option_date_months);
        mDateAny = (TextView) findViewById(R.id.date_any);
        mDateExact = (Spinner) findViewById(R.id.date_exact);
        mDateMin = (Spinner) findViewById(R.id.date_min);
        mDateMax = (Spinner) findViewById(R.id.date_max);
        mDateMonths = (Spinner) findViewById(R.id.date_months);
        mAnnotationName = (Spinner) findViewById(R.id.annotation_name);
        mAnnotationEqual = (TextView) findViewById(R.id.annotation_equal);
        mAnnotationValue = (Spinner) findViewById(R.id.annotation_value);

        mDateAny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOptionDateAny.performClick();
            }
        });

        mClearUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchFilters.user = null;
                refreshViewState();
            }
        });

        mUserName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String searchUrl = INaturalistService.API_HOST + "/users/autocomplete";
                Intent intent = new Intent(ExploreFiltersActivity.this, ItemSearchActivity.class);
                intent.putExtra(ItemSearchActivity.RETURN_RESULT, true);
                intent.putExtra(ItemSearchActivity.SEARCH_HINT_TEXT, getString(R.string.search_users));
                intent.putExtra(ItemSearchActivity.SEARCH_URL, searchUrl);
                intent.putExtra(ItemSearchActivity.IS_USER, true);
                startActivityForResult(intent, REQUEST_CODE_SEARCH_USERS);
            }
        });

        mClearProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchFilters.project = null;
                refreshViewState();
            }
        });

        mProjectName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ExploreFiltersActivity.this, ItemSearchActivity.class);
                intent.putExtra(ItemSearchActivity.RETURN_RESULT, true);
                intent.putExtra(ItemSearchActivity.SEARCH_HINT_TEXT, getString(R.string.search_projects));
                intent.putExtra(ItemSearchActivity.SEARCH_URL, INaturalistService.API_HOST + "/projects/autocomplete");
                startActivityForResult(intent, REQUEST_CODE_SEARCH_PROJECTS);
            }
        });


        mApp = (INaturalistApp) getApplicationContext();
        SharedPreferences prefs = mApp.getPrefs();
        final String currentUsername = prefs.getString("username", null);
        final String currentUserIconUrl = prefs.getString("user_icon_url", null);

        mShowMyObservationsCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setSelected(!view.isSelected());

                if (view.isSelected()) {
                    // Show my observations
                    try {
                        JSONObject myUser = new JSONObject();
                        myUser.put("login", currentUsername);
                        myUser.put("icon_url", currentUserIconUrl);
                        mSearchFilters.user = myUser;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    mSearchFilters.user = null;
                }

                refreshViewState();
            }
        });

        mShowMyObservationsRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowMyObservationsCheckbox.performClick();
            }
        });

        mShowMyObservationsRow.setVisibility(currentUsername == null ? View.GONE : View.VISIBLE);


        View.OnClickListener onDataQualityCheckbox = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String qualityGrade;

                if (view == mResearchGradeCheckbox) {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_RESEARCH;
                } else if (view == mNeedsIdCheckbox) {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID;
                } else {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_CASUAL;
                }

                view.setSelected(!view.isSelected());

                if (view.isSelected()) {
                    mSearchFilters.qualityGrade.add(qualityGrade);
                } else {
                    mSearchFilters.qualityGrade.remove(qualityGrade);
                }

                refreshViewState();
            }
        };

        mResearchGradeCheckbox.setOnClickListener(onDataQualityCheckbox);
        mNeedsIdCheckbox.setOnClickListener(onDataQualityCheckbox);
        mCasualGradeCheckbox.setOnClickListener(onDataQualityCheckbox);


        // Show date/calendar picker dialog
        View.OnTouchListener onShowDate = new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                Bundle args = new Bundle();
                Date initialDate = null, minDate = null, maxDate = new Date();

                // Set the initial dates for the calendar view
                if (view == mDateExact) initialDate = mSearchFilters.observedOn;
                if (view == mDateMin) {
                    initialDate = mSearchFilters.observedOnMinDate;
                    maxDate = mSearchFilters.observedOnMaxDate != null ? mSearchFilters.observedOnMaxDate : new Date();
                }
                if (view == mDateMax) {
                    initialDate = mSearchFilters.observedOnMaxDate;
                    minDate = mSearchFilters.observedOnMinDate;
                    maxDate = new Date();
                }

                args.putSerializable("date", initialDate != null ? initialDate : new Date());
                if (minDate != null) args.putSerializable("min_date", minDate);
                if (maxDate != null) args.putSerializable("max_date", maxDate);

                DatePickerFragment newFragment = new DatePickerFragment();
                newFragment.setArguments(args);
                newFragment.setOnDateSetListener(new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker dp, int year, int month, int dayOfMonth) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(year, month, dayOfMonth);
                        Date date = calendar.getTime();

                        // Update the date
                        if (view == mDateExact) {
                            mSearchFilters.observedOn = date;
                            mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_EXACT_DATE;
                        } else if (view == mDateMin) {
                            mSearchFilters.observedOnMinDate = date;
                            mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_MIN_MAX_DATE;
                        } else if (view == mDateMax) {
                            mSearchFilters.observedOnMaxDate = date;
                            mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_MIN_MAX_DATE;
                        }

                        refreshViewState();
                    }
                });

                newFragment.show(getSupportFragmentManager(), "datePicker");
                return true;
            }
        };

        mDateExact.setOnTouchListener(onShowDate);
        mDateMin.setOnTouchListener(onShowDate);
        mDateMax.setOnTouchListener(onShowDate);
        mDateMonths.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                List<String> monthsNames = new ArrayList<>();
                for (int i = 1; i <= 12; i++) {
                    monthsNames.add(monthToString(i));
                }
                Set<Integer> selectedPositions = new HashSet<>();
                for (Integer month : mSearchFilters.observedOnMonths) {
                    selectedPositions.add(month - 1);
                }
                mHelper.multipleChoiceSelection(getString(R.string.choose_months), monthsNames, selectedPositions, new ActivityHelper.OnMultipleChoices() {
                    @Override
                    public void onMultipleChoices(Set<Integer> selectedPositions) {
                        mSearchFilters.observedOnMonths.clear();
                        for (Integer position : selectedPositions) {
                            mSearchFilters.observedOnMonths.add(position + 1);
                        }
                        mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_MONTHS;
                        refreshViewState();
                    }
                });
                return true;
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mTaxonicIcons.setLayoutManager(layoutManager);

        mApplyFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Return the search filters
                if (!mSearchFilters.iconicTaxa.isEmpty()) {
                    // Iconic taxa have been chosen - clear out the taxon filter
                    mSearchFilters.taxon = null;
                }

                Intent data = new Intent();
                data.putExtra(SEARCH_FILTERS, mSearchFilters);
                setResult(RESULT_OK, data);
                finish();
            }
        });


        mAnnotationName.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
                if (mAllAnnotations == null) return false;

                String[] items = new String[mAllAnnotations.getJSONArray().length() + 1];

                items[0] = getString(R.string.none);

                for (int i = 0; i < mAllAnnotations.getJSONArray().length(); i++) {
                    JSONObject item = mAllAnnotations.getJSONArray().optJSONObject(i);
                    items[i + 1] = item.optString("label");
                }

                mHelper.selection(getString(R.string.annotation_name), items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 0) {
                            // None
                            mSearchFilters.annotationNameId = null;
                            mSearchFilters.annotationName = null;
                        } else {
                            // A specific annotation name
                            mSearchFilters.annotationNameId = mAllAnnotations.getJSONArray().optJSONObject(i - 1).optInt("id");
                            mSearchFilters.annotationName = mAllAnnotations.getJSONArray().optJSONObject(i - 1).optString("label");
                        }

                        mSearchFilters.annotationValueId = null;
                        mSearchFilters.annotationValue = null;

                        refreshViewState();
                    }
                });

                return true;
            }
        });

        mAnnotationValue.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
                if (mAllAnnotations == null) return false;
                if (mSearchFilters.annotationNameId == null) return false;


                JSONObject annotation = null;
                for (int i = 0; i < mAllAnnotations.getJSONArray().length(); i++) {
                    annotation = mAllAnnotations.getJSONArray().optJSONObject(i);

                    if (annotation.optInt("id") == mSearchFilters.annotationNameId) {
                        break;
                    }
                }

                if (annotation == null) return false;

                final JSONArray values = annotation.optJSONArray("values");

                String[] items = new String[values.length() + 1];
                items[0] = getString(R.string.none);

                for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i);
                    items[i + 1] = value.optString("label");
                }

                mHelper.selection(annotation.optString("label"), items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 0) {
                            // None
                            mSearchFilters.annotationValueId = null;
                            mSearchFilters.annotationValue = null;
                        } else {
                            // A specific annotation value
                            mSearchFilters.annotationValueId = values.optJSONObject(i - 1).optInt("id");
                            mSearchFilters.annotationValue = values.optJSONObject(i - 1).optString("label");
                        }

                        refreshViewState();
                    }
                });

                return true;
            }
        });

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        refreshViewState();
    }

    private void refreshViewState() {
        List<Pair<String, Boolean>> iconicTaxa = new ArrayList<>();

        for (int i = 0; i < ICONIC_TAXA.length; i++) {
            iconicTaxa.add(new Pair<>(ICONIC_TAXA[i], mSearchFilters.iconicTaxa.contains(ICONIC_TAXA[i])));
        }

        mTaxonicIcons.setAdapter(new IconicTaxonAdapter(this, iconicTaxa, new IconicTaxonAdapter.OnIconicTaxonSelected() {
            @Override
            public void onIconicITaxonSelected(String iconicTaxa, boolean selected) {
                if (selected) {
                    mSearchFilters.iconicTaxa.add(iconicTaxa);
                } else {
                    mSearchFilters.iconicTaxa.remove(iconicTaxa);
                }

                mTaxonicIcons.getAdapter().notifyDataSetChanged();
                refreshResetFiltersButton();
            }
        }));

        if (mSearchFilters.project == null) {
            mClearProject.setVisibility(View.GONE);
            mProjectName.setText("");
            mProjectPic.setImageResource(R.drawable.ic_work_black_24dp);
            mProjectPic.setColorFilter(Color.parseColor("#5D5D5D"));
        } else {
            mClearProject.setVisibility(View.VISIBLE);
            mProjectName.setText(mSearchFilters.project.optString("title"));
            mProjectPic.setColorFilter(null);

            String iconUrl = mSearchFilters.project.has("icon") ? mSearchFilters.project.optString("icon") : mSearchFilters.project.optString("icon_url");
            if (iconUrl == null) {
                mProjectPic.setImageResource(R.drawable.ic_work_black_24dp);
            } else {
                Picasso.with(this).
                        load(iconUrl).
                        placeholder(R.drawable.ic_work_black_24dp).
                        into(mProjectPic);
            }
        }


        if (mSearchFilters.user == null) {
            mClearUser.setVisibility(View.GONE);
            mUserName.setText("");
            mUserPic.setImageResource(R.drawable.ic_account_circle_black_48dp);
            mUserPic.setColorFilter(Color.parseColor("#5D5D5D"));
            mShowMyObservationsCheckbox.setSelected(false);
        } else {
            mClearUser.setVisibility(View.VISIBLE);
            mUserName.setText(mSearchFilters.user.optString("login"));
            mUserPic.setColorFilter(null);

            String iconUrl = mSearchFilters.user.has("icon") ? mSearchFilters.user.optString("icon") : mSearchFilters.user.optString("icon_url");
            if ((iconUrl == null) || (iconUrl.length() == 0)) {
                mUserPic.setImageResource(R.drawable.ic_account_circle_black_48dp);
            } else {
                Picasso.with(this).
                        load(iconUrl).
                        placeholder(R.drawable.ic_account_circle_black_48dp).
                        fit().
                        centerCrop().
                        transform(new UserActivitiesAdapter.CircleTransform()).
                        into(mUserPic);
            }

            String filterUser = mSearchFilters.user.optString("login");
            if ((mApp.currentUserLogin() != null) && (filterUser.equals(mApp.currentUserLogin()))) {
                mShowMyObservationsCheckbox.setSelected(true);
            } else {
                mShowMyObservationsCheckbox.setSelected(false);
            }
        }

        mResearchGradeCheckbox.setSelected(mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_RESEARCH));
        mNeedsIdCheckbox.setSelected(mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID));
        mCasualGradeCheckbox.setSelected(mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_CASUAL));

        mOptionDateAny.setChecked(false);
        mOptionDateExact.setChecked(false);
        mOptionDateMinMax.setChecked(false);
        mOptionDateMonths.setChecked(false);

        switch (mSearchFilters.dateFilterType) {
            case ExploreSearchFilters.DATE_TYPE_ANY:
                mOptionDateAny.setChecked(true);
                break;
            case ExploreSearchFilters.DATE_TYPE_EXACT_DATE:
                mOptionDateExact.setChecked(true);
                break;
             case ExploreSearchFilters.DATE_TYPE_MIN_MAX_DATE:
                mOptionDateMinMax.setChecked(true);
                break;
             case ExploreSearchFilters.DATE_TYPE_MONTHS:
                mOptionDateMonths.setChecked(true);
                break;
        }

        View.OnClickListener onDateOptionSelected = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == mOptionDateAny) mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_ANY;
                if (v == mOptionDateExact) mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_EXACT_DATE;
                if (v == mOptionDateMinMax) mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_MIN_MAX_DATE;
                if (v == mOptionDateMonths) mSearchFilters.dateFilterType = ExploreSearchFilters.DATE_TYPE_MONTHS;

                refreshViewState();
            }
        };

        mOptionDateAny.setOnClickListener(onDateOptionSelected);
        mOptionDateExact.setOnClickListener(onDateOptionSelected);
        mOptionDateMinMax.setOnClickListener(onDateOptionSelected);
        mOptionDateMonths.setOnClickListener(onDateOptionSelected);

        setSpinnerDate(mDateExact, mSearchFilters.observedOn, R.string.exact_date);
        setSpinnerDate(mDateMin, mSearchFilters.observedOnMinDate, R.string.start);
        setSpinnerDate(mDateMax, mSearchFilters.observedOnMaxDate, R.string.end);

        SortedSet<Integer> sortedMonths = new TreeSet<>(mSearchFilters.observedOnMonths);
        List<String> months = new ArrayList<>();
        for (int month : sortedMonths) {
            months.add(monthToString(month));
        }

        if (months.size() == 0) {
            setSpinnerText(mDateMonths, getString(R.string.months));
        } else {
            setSpinnerText(mDateMonths, StringUtils.join(months, ", "));
        }

        if ((mSearchFilters.annotationNameId == null) || (mAllAnnotations == null)) {
            // No annotation name selected
            setSpinnerText(mAnnotationName, getString(R.string.none));
            mAnnotationEqual.setVisibility(View.GONE);
            mAnnotationValue.setVisibility(View.GONE);
        } else {
            // Show a specific annotation name with an option to select annotation value
            if (mSearchFilters.annotationName != null) {
                setSpinnerText(mAnnotationName, mSearchFilters.annotationName);
                mAnnotationEqual.setVisibility(View.VISIBLE);
                mAnnotationValue.setVisibility(View.VISIBLE);

                if (mSearchFilters.annotationValueId != null) {
                    if (mSearchFilters.annotationValue != null) {
                        setSpinnerText(mAnnotationValue, mSearchFilters.annotationValue);
                    }

                } else {
                    setSpinnerText(mAnnotationValue, getString(R.string.none));
                }
            }
        }

        refreshResetFiltersButton();
    }

    private void refreshResetFiltersButton() {
        if (mResetFilters != null) {
            mResetFilters.setEnabled(mSearchFilters.isDirty());
        }
    }

    private String monthToString(int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, month - 1); // Calendar has zero-based indexing for months

        return new SimpleDateFormat("MMMM").format(cal.getTime());
    }

    private void setSpinnerDate(Spinner spinner, Date date, int defaultValue) {
        setSpinnerText(spinner, date != null ? mSearchFilters.formatDate(date) : getString(defaultValue));
    }

    private void setSpinnerText(Spinner spinner, String text) {
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{ text }));;
        spinner.setSelection(0);
    }


    private void resetFilters() {
        mSearchFilters.resetToDefault();
        refreshViewState();
    }


    private static class IconicTaxonAdapter extends RecyclerView.Adapter<IconicTaxonAdapter.IconicTaxonViewHolder> {
        private final Context mContext;
        private final OnIconicTaxonSelected mOnSelected;
        private List<Pair<String, Boolean>> mItems;
        
        public interface OnIconicTaxonSelected {
            public void onIconicITaxonSelected(String iconicTaxa, boolean selected);
        }

        public IconicTaxonAdapter(Context context, List<Pair<String, Boolean>> objects, OnIconicTaxonSelected onSelected) {
            mContext = context;
            mItems = objects;
            mOnSelected = onSelected;
        }

        @Override
        public IconicTaxonViewHolder onCreateViewHolder(ViewGroup viewGroup, int position) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.iconic_taxon_item, viewGroup, false);
            IconicTaxonViewHolder viewHolder = new IconicTaxonViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(IconicTaxonViewHolder viewHolder, final int position) {
            Pair<String, Boolean> pair = mItems.get(position);
            final String iconicTaxonName = pair.first;
            final boolean isSelected = pair.second;

            Picasso.with(mContext)
                    .load(TaxonUtils.taxonicIconNameToResource(iconicTaxonName))
                    .fit()
                    .into(viewHolder.imageView);

            viewHolder.background.setBackgroundResource(isSelected ? R.drawable.selected_light_green_with_border : 0);

            viewHolder.background.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Update the selected value for this iconic taxon
                    Pair<String, Boolean> newPair = new Pair<>(iconicTaxonName, !isSelected);
                    mItems.set(position, newPair);

                    mOnSelected.onIconicITaxonSelected(iconicTaxonName, !isSelected);
                }
            });
        }

        @Override
        public int getItemCount() {
            return (mItems != null ? mItems.size() : 0);
        }

        class IconicTaxonViewHolder extends RecyclerView.ViewHolder {
            protected ImageView imageView;
            protected ViewGroup background;

            public IconicTaxonViewHolder(View v) {
                super(v);

                imageView = (ImageView) v.findViewById(R.id.iconic_taxon);
                background = (ViewGroup) v.findViewById(R.id.background);
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SEARCH_PROJECTS) {
            if (resultCode == RESULT_OK) {
                try {
                    mSearchFilters.project = new JSONObject(data.getStringExtra(ItemSearchActivity.RESULT));
                    refreshViewState();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_CODE_SEARCH_USERS) {
            if (resultCode == RESULT_OK) {
                try {
                    mSearchFilters.user = new JSONObject(data.getStringExtra(ItemSearchActivity.RESULT));
                    refreshViewState();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
	}


    public static class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

        private DatePickerDialog.OnDateSetListener mOnDateSetListener = null;

        public void setOnDateSetListener(DatePickerDialog.OnDateSetListener listener) {
            mOnDateSetListener = listener;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Date initialDate;
            Date minDate = null, maxDate = null;

            Bundle args = getArguments();
            if (args == null) {
                Calendar c = Calendar.getInstance();
                initialDate = c.getTime();
            } else {
                initialDate = (Date) args.getSerializable("date");
                minDate = (Date) args.getSerializable("min_date");
                maxDate = (Date) args.getSerializable("max_date");
            }

            // Use the initial date as the default date in the picker
            Calendar c = new GregorianCalendar();
            c.setTime(initialDate);
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            DatePickerDialog dialog = new DatePickerDialog(getActivity(), this, year, month, day);
            if (maxDate != null) dialog.getDatePicker().setMaxDate(maxDate.getTime());
            if (minDate != null) dialog.getDatePicker().setMinDate(minDate.getTime());

            return dialog;
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            if (mOnDateSetListener != null) {
                mOnDateSetListener.onDateSet(view, year, month, day);
            }
        }
    }
}
