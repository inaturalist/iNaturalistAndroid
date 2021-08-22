package org.inaturalist.android;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Picasso;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
            "Plantae", "Aves", "Insecta", "Amphibia", "Reptilia", "Fungi", "Animalia", "Chromista", "Protozoa", "Actinopterygii", "Mammalia", "Mollusca", "Arachnida", "Unknown"
    };

    private static final int REQUEST_CODE_SEARCH_PROJECTS = 0x1000;
    private static final int REQUEST_CODE_SEARCH_USERS = 0x1001;
    private static final String TAG = "ExploreFiltersActivity";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    @State public ExploreSearchFilters mSearchFilters;

    private Handler mHandler;

    private RecyclerView mTaxonicIcons;
    private Button mApplyFilters;
    private CheckBox mShowMyObservationsCheckbox;
    private CheckBox mHideMyObservationsCheckbox;
    private ViewGroup mShowMyObservationsRow;
    private ViewGroup mHideMyObservationsRow;
    private ImageView mProjectPic;
    private TextView mProjectName;
    private ImageView mClearProject;
    private ImageView mUserPic;
    private TextView mUserName;
    private ImageView mClearUser;
    private CheckBox mResearchGradeCheckbox;
    private View mResearchGradeContainer;
    private CheckBox mNeedsIdCheckbox;
    private View mNeedsIdContainer;
    private CheckBox mCasualGradeCheckbox;
    private View mCasualGradeContainer;
    private CheckBox mHasPhotosCheckbox;
    private View mHasPhotos;
    private CheckBox mHasSoundsCheckbox;
    private View mHasSounds;
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
    private Spinner mSortByProperty;
    private Spinner mSortByOrder;
    private TextView mShowMyObservationsLabel;
    private TextView mHideMyObservationsLabel;
    private TextView mResearchGradeLabel;
    private TextView mNeedsIdLabel;
    private TextView mCasualGradeLabel;
    private TextView mHasPhotosLabel;
    private TextView mHasSoundsLabel;


    @Override
	protected void onStop()
	{
		super.onStop();		
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
        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        DataBindingUtil.setContentView(this, R.layout.explore_filters);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            mSearchFilters = (ExploreSearchFilters) intent.getSerializableExtra(SEARCH_FILTERS);
            mAllAnnotations = (SerializableJSONArray) intent.getSerializableExtra(ALL_ANNOTATIONS);
        }

        mHandler = new Handler();

        mApplyFilters = (Button) findViewById(R.id.apply_filters);
        mTaxonicIcons = (RecyclerView) findViewById(R.id.taxonic_icons);
        mShowMyObservationsCheckbox = findViewById(R.id.show_my_observations_checkbox);
        mShowMyObservationsRow = (ViewGroup) findViewById(R.id.show_my_observations);
        mShowMyObservationsLabel = (TextView) findViewById(R.id.show_my_observations_label);
        mHideMyObservationsCheckbox = findViewById(R.id.hide_my_observations_checkbox);
        mHideMyObservationsRow = (ViewGroup) findViewById(R.id.hide_my_observations);
        mHideMyObservationsLabel = (TextView) findViewById(R.id.hide_my_observations_label);
        mProjectPic = (ImageView) findViewById(R.id.project_pic);
        mProjectName = (TextView) findViewById(R.id.project_name);
        mClearProject = (ImageView) findViewById(R.id.clear_project);
        mUserPic = (ImageView) findViewById(R.id.user_pic);
        mUserName = (TextView) findViewById(R.id.user_name);
        mClearUser = (ImageView) findViewById(R.id.clear_user);
        mResearchGradeCheckbox = findViewById(R.id.research_grade_checkbox);
        mResearchGradeContainer = (View) findViewById(R.id.research_grade);
        mResearchGradeLabel = findViewById(R.id.research_grade_label);
        mNeedsIdCheckbox = findViewById(R.id.needs_id_checkbox);
        mNeedsIdContainer = (View) findViewById(R.id.needs_id);
        mNeedsIdLabel = findViewById(R.id.needs_id_label);
        mCasualGradeCheckbox = findViewById(R.id.casual_grade_checkbox);
        mCasualGradeContainer = (View) findViewById(R.id.casual_grade);
        mCasualGradeLabel = findViewById(R.id.casual_grade_label);
        mHasPhotosCheckbox = findViewById(R.id.has_photos_checkbox);
        mHasPhotos = (View) findViewById(R.id.has_photos);
        mHasPhotosLabel = findViewById(R.id.has_photos_label);
        mHasSoundsCheckbox = findViewById(R.id.has_sounds_checkbox);
        mHasSounds = (View) findViewById(R.id.has_sounds);
        mHasSoundsLabel = findViewById(R.id.has_sounds_label);
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

        mSortByProperty = (Spinner) findViewById(R.id.sort_by_property);
        mSortByOrder = (Spinner) findViewById(R.id.sort_by_order);

        mDateAny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOptionDateAny.performClick();
            }
        });

        mClearUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSearchFilters == null) return;

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
                if (mSearchFilters == null) return;

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


        SharedPreferences prefs = mApp.getPrefs();
        final String currentUsername = prefs.getString("username", null);
        final String currentUserIconUrl = prefs.getString("user_icon_url", null);

        mShowMyObservationsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSearchFilters == null) return;

                if (isChecked) {
                    // Show my observations
                    mHideMyObservationsCheckbox.setChecked(false);
                    mSearchFilters.hideObservationsUserId = null;

                    try {
                        JSONObject myUser = new JSONObject();
                        myUser.put("login", currentUsername);
                        myUser.put("icon_url", currentUserIconUrl);
                        mSearchFilters.user = myUser;
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                    }
                } else {
                    mSearchFilters.user = null;
                }

                refreshViewState();
            }
        });

        mShowMyObservationsLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowMyObservationsCheckbox.performClick();
            }
        });

        mShowMyObservationsRow.setVisibility(currentUsername == null ? View.GONE : View.VISIBLE);


        mHideMyObservationsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSearchFilters == null) return;

                if (isChecked) {
                    // Hide my observations
                    mSearchFilters.hideObservationsUserId = mApp.currentUserId();
                    mSearchFilters.user = null;
                    mShowMyObservationsCheckbox.setChecked(false);
                } else {
                    mSearchFilters.hideObservationsUserId = null;
                }

                refreshViewState();
            }
        });

        mHideMyObservationsLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHideMyObservationsCheckbox.performClick();
            }
        });

        mHideMyObservationsRow.setVisibility(currentUsername == null ? View.GONE : View.VISIBLE);



        View.OnClickListener onDataQualityCheckbox = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchFilters == null) return;

                String qualityGrade;
                CheckBox checkbox;

                if (view == mResearchGradeLabel) {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_RESEARCH;
                    checkbox = mResearchGradeCheckbox;
                } else if (view == mNeedsIdLabel) {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID;
                    checkbox = mNeedsIdCheckbox;
                } else {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_CASUAL;
                    checkbox = mCasualGradeCheckbox;
                }

                checkbox.setChecked(!checkbox.isChecked());

                if (checkbox.isChecked()) {
                    mSearchFilters.qualityGrade.add(qualityGrade);
                } else {
                    mSearchFilters.qualityGrade.remove(qualityGrade);
                }

                refreshViewState();
            }
        };

        mResearchGradeLabel.setOnClickListener(onDataQualityCheckbox);
        mNeedsIdLabel.setOnClickListener(onDataQualityCheckbox);
        mCasualGradeLabel.setOnClickListener(onDataQualityCheckbox);

        CompoundButton.OnCheckedChangeListener onDataQualityCheckboxChange = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton view, boolean isChecked) {
                String qualityGrade;

                if (view == mResearchGradeCheckbox) {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_RESEARCH;
                } else if (view == mNeedsIdCheckbox) {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID;
                } else {
                    qualityGrade = ExploreSearchFilters.QUALITY_GRADE_CASUAL;
                }

                if (isChecked) {
                    mSearchFilters.qualityGrade.add(qualityGrade);
                } else {
                    mSearchFilters.qualityGrade.remove(qualityGrade);
                }
            }
        };
        mResearchGradeCheckbox.setOnCheckedChangeListener(onDataQualityCheckboxChange);
        mNeedsIdCheckbox.setOnCheckedChangeListener(onDataQualityCheckboxChange);
        mCasualGradeCheckbox.setOnCheckedChangeListener(onDataQualityCheckboxChange);

        mHasPhotosLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchFilters == null) return;

                mHasPhotosCheckbox.setChecked(!mHasPhotosCheckbox.isChecked());

                mSearchFilters.hasPhotos = mHasPhotosCheckbox.isChecked();
                refreshViewState();
            }
        });
        mHasPhotosCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSearchFilters.hasPhotos = isChecked;
            }
        });

        mHasSoundsLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchFilters == null) return;

                mHasSoundsCheckbox.setChecked(!mHasSoundsCheckbox.isChecked());

                mSearchFilters.hasSounds = mHasSoundsCheckbox.isChecked();
                refreshViewState();
            }
        });
        mHasSoundsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSearchFilters.hasSounds = isChecked;
            }
        });

        // Show date/calendar picker dialog
        View.OnTouchListener onShowDate = new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
                if (mSearchFilters == null) return false;

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
                        if (mSearchFilters == null) return;

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
                if (mSearchFilters == null) return false;

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
                if (mSearchFilters == null) return;

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
                if (mSearchFilters == null) return false;
                if (mAllAnnotations == null) return false;

                String[] items = new String[mAllAnnotations.getJSONArray().length() + 1];

                items[0] = getString(R.string.none);

                for (int i = 0; i < mAllAnnotations.getJSONArray().length(); i++) {
                    JSONObject item = mAllAnnotations.getJSONArray().optJSONObject(i);
                    String translatedName = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, item.optString("label"), false);
                    items[i + 1] = translatedName;
                }

                mHelper.selection(getString(R.string.annotation_name), items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (mSearchFilters == null) return;

                        if (i == 0) {
                            // None
                            mSearchFilters.annotationNameId = null;
                            mSearchFilters.annotationName = null;
                        } else {
                            // A specific annotation name
                            mSearchFilters.annotationNameId = mAllAnnotations.getJSONArray().optJSONObject(i - 1).optInt("id");
                            String translatedName = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, mAllAnnotations.getJSONArray().optJSONObject(i - 1).optString("label"), false);
                            mSearchFilters.annotationName = translatedName;
                        }

                        mSearchFilters.annotationValueId = null;
                        mSearchFilters.annotationValue = null;

                        refreshViewState();
                    }
                });

                return true;
            }
        });

        mSortByProperty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mSearchFilters == null) return;

                List<String> sortBy = Arrays.asList(getResources().getStringArray(R.array.explore_order_by_values));
                mSearchFilters.orderBy = sortBy.get(position);
                refreshResetFiltersButton();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mSortByOrder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mSearchFilters == null) return;

                List<String> sort = Arrays.asList(getResources().getStringArray(R.array.explore_order_values));
                mSearchFilters.order = sort.get(position);
                refreshResetFiltersButton();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mAnnotationValue.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
                if (mSearchFilters == null) return false;
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
                    String translatedValue = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, value.optString("label"), true);
                    items[i + 1] = translatedValue;
                }

                String translatedName = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, annotation.optString("label"), false);

                mHelper.selection(translatedName, items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (mSearchFilters == null) return;

                        if (i == 0) {
                            // None
                            mSearchFilters.annotationValueId = null;
                            mSearchFilters.annotationValue = null;
                        } else {
                            // A specific annotation value
                            mSearchFilters.annotationValueId = values.optJSONObject(i - 1).optInt("id");
                            String translateValue = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, values.optJSONObject(i - 1).optString("label"), true);
                            mSearchFilters.annotationValue = translateValue;
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

        if (mSearchFilters == null) return;

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
            BindingAdapterUtils.increaseTouch(mClearProject, 80);
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

        if (mSearchFilters.hideObservationsUserId == null) {
            mHideMyObservationsCheckbox.setChecked(false);
        } else {
            mHideMyObservationsCheckbox.setChecked(true);
        }

        if (mSearchFilters.user == null) {
            mClearUser.setVisibility(View.GONE);
            mUserName.setText("");
            mUserPic.setImageResource(R.drawable.ic_account_circle_black_48dp);
            mUserPic.setColorFilter(Color.parseColor("#5D5D5D"));
            mShowMyObservationsCheckbox.setChecked(false);
        } else {
            mClearUser.setVisibility(View.VISIBLE);
            BindingAdapterUtils.increaseTouch(mClearUser, 80);
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
                mShowMyObservationsCheckbox.setChecked(true);
            } else {
                mShowMyObservationsCheckbox.setChecked(false);
            }
        }

        mResearchGradeCheckbox.setChecked(mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_RESEARCH));
        mNeedsIdCheckbox.setChecked(mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID));
        mCasualGradeCheckbox.setChecked(mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_CASUAL));

        mHasPhotosCheckbox.setChecked(mSearchFilters.hasPhotos);
        mHasSoundsCheckbox.setChecked(mSearchFilters.hasSounds);

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

        List<String> sortBy = Arrays.asList(getResources().getStringArray(R.array.explore_order_by_values));
        mSortByProperty.setSelection(sortBy.indexOf(mSearchFilters.orderBy));

        List<String> sortByOrder = Arrays.asList(getResources().getStringArray(R.array.explore_order_values));
        mSortByOrder.setSelection(sortByOrder.indexOf(mSearchFilters.order));

        refreshResetFiltersButton();
    }

    private void refreshResetFiltersButton() {
        if (mSearchFilters == null) return;

        if (mResetFilters != null) {
            mResetFilters.setEnabled(mSearchFilters.isDirty());
        }
    }

    private String monthToString(int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.MONTH, month - 1); // Calendar has zero-based indexing for months

        return new SimpleDateFormat("MMMM").format(cal.getTime());
    }

    private void setSpinnerDate(Spinner spinner, Date date, int defaultValue) {
        if (mSearchFilters == null) return;

        setSpinnerText(spinner, date != null ? mSearchFilters.formatDate(date) : getString(defaultValue));
    }

    private void setSpinnerText(Spinner spinner, String text) {
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{ text }));;
        spinner.setSelection(0);
    }


    private void resetFilters() {
        if (mSearchFilters == null) return;

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

        if (mSearchFilters == null) return;

        if (requestCode == REQUEST_CODE_SEARCH_PROJECTS) {
            if (resultCode == RESULT_OK) {
                try {
                    mSearchFilters.project = new JSONObject(data.getStringExtra(ItemSearchActivity.RESULT));
                    refreshViewState();
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }
        } else if (requestCode == REQUEST_CODE_SEARCH_USERS) {
            if (resultCode == RESULT_OK) {
                try {
                    mSearchFilters.user = new JSONObject(data.getStringExtra(ItemSearchActivity.RESULT));
                    if (mApp.loggedIn()) {
                        if ((mSearchFilters.user != null) && (mSearchFilters.user.optString("login").equals(mApp.currentUserLogin()))) {
                            // User selected his own username
                            mHideMyObservationsCheckbox.setChecked(false);
                            mSearchFilters.hideObservationsUserId = null;
                        }
                    }
                    refreshViewState();
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
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
            if (minDate != null) dialog.getDatePicker().setMinDate(minDate.getTime());
            if (maxDate != null) dialog.getDatePicker().setMaxDate(maxDate.getTime());

            return dialog;
        }

        @Override
        public void onStart() {
            super.onStart();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ((DatePickerDialog) getDialog()).getButton(DatePickerDialog.BUTTON_POSITIVE).setAutoSizeTextTypeUniformWithConfiguration(14, 15, 1, TypedValue.COMPLEX_UNIT_SP);
                ((DatePickerDialog) getDialog()).getButton(DatePickerDialog.BUTTON_NEGATIVE).setAutoSizeTextTypeUniformWithConfiguration(14, 15, 1, TypedValue.COMPLEX_UNIT_SP);
            }
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            if (mOnDateSetListener != null) {
                mOnDateSetListener.onDateSet(view, year, month, day);
            }
        }
    }
}
