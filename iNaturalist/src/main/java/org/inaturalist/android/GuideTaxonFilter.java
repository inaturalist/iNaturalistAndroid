package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a guide taxon filter - text string, tags, etc.
 */
public class GuideTaxonFilter {
    private String mSearchText;
    private ArrayList<String> mTags;

    public GuideTaxonFilter() {
        mTags = new ArrayList<String>();
    }

    public void setSearchText(String searchText) {
        mSearchText = searchText;
    }

    public String getSearchText() {
        return mSearchText;
    }

    public void addTag(String tag) {
        mTags.add(tag);
    }

    public void removeTag(String tag) {
        mTags.remove(tag);
    }

    public void clearTags() {
        mTags.clear();
    }

    public boolean hasTag(String tag) {
        return mTags.contains(tag);
    }

    public void setTags(List<String> tags) {
        mTags.clear();
        mTags.addAll(tags);
    }

    public List<String> getAllTags() {
        return mTags;
    }
}
