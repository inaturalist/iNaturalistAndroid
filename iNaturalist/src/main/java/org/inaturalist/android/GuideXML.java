package org.inaturalist.android;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Represents a Guide's XML file - parses it and supports downloading an NGZ file locally
 */
public class GuideXML extends BaseGuideXMLParser {

    // The default predicate - tags
    public final static String PREDICATE_TAGS = "TAGS";

    // The directory where all offline guides are saved to
    private final static String OFFLINE_GUIDE_PATH = "/offline_guides/";

    private String mGuideId;
    private Context mContext;
    private Map<String, Integer> mTagCounts;
    private Map<String, Set<String>> mTags;

    // Representative photos of the tag values (e.g. what photo should we display for "number of legs=4")
    private Map<Pair<String, String>, List<GuideTaxonPhotoXML>> mReprTagPhotos;

    /**
     * Initialize the GuideXML class with the offline (downloaded) version of it
     * @param context the app context
     * @param guideId the guide identifier
     */
    public GuideXML(Context context, String guideId) {
        // Initialize the class with the offline guide downloaded XML file
        this(context, guideId,
                context.getExternalCacheDir() + OFFLINE_GUIDE_PATH + guideId + "/" + guideId + ".xml");
    }

    /**
     * Initialize the GuideXML class with a local file XML path
     * @param context the app context
     * @param guideId the guide identifier
     * @param path the local file name of the guide XML file
     */
    public GuideXML(Context context, String guideId, String path) {
        mContext = context;
        mGuideId = guideId;

        FileReader fr = null;
        try {
            fr = new FileReader(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        InputSource inputSource = new InputSource(fr);

        mTagCounts = new HashMap<String, Integer>();
        mTags = new HashMap<String, Set<String>>();

        try {
            // Read root node so we won't re-parse the XML file every time we evaluate an XPath
            XPath xpath = XPathFactory.newInstance().newXPath();
            setRootNode((Node) xpath.evaluate("/", inputSource, XPathConstants.NODE));

            // Parse all taxon tags
            parseTags();
            // Parse the TaxonImage tags (so we'll know what are the representative image for each tag value)
            parseImageTags();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

    }

    /**
     * Creates the root directory that will hold all offline guides
     * @param context
     */
    public static void createOfflineGuidesDirectory(Context context) {
        File offlineGuidesDir = new File(context.getExternalCacheDir() + OFFLINE_GUIDE_PATH);
        offlineGuidesDir.mkdirs();
    }


    /**
     * Returns all offline (downloaded) guides available
     * @param context
     * @return
     */
    public static List<GuideXML> getAllOfflineGuides(Context context) {
        ArrayList<GuideXML> guides = new ArrayList<GuideXML>();
        File rootDir = new File(context.getExternalCacheDir() + OFFLINE_GUIDE_PATH);
        File files[] = rootDir.listFiles();

        if (files == null) return guides;

        // Iterate over all directories in the offline guides root directory, and create
        // a new GuideXML instance for each.

        for (int i = 0; i < files.length; i++) {
            if (!files[i].isDirectory()) {
                continue;
            }
            String guideId = files[i].getName();

            guides.add(new GuideXML(context, guideId));
        }

        return guides;
    }


    /**
     * Checks whether or not an offline version of the guide is available (was downloaded)
     * @return
     */
    public boolean isGuideDownloaded() {
        String path = getOfflineGuidePath();
        File dir = new File(path);

        return dir.exists();
    }


    /**
     * Returns the base path for the downloaded offline guide
     * @return
     */
    public String getOfflineGuidePath() {
        return mContext.getExternalCacheDir() + OFFLINE_GUIDE_PATH + mGuideId;
    }

    /**
     * Returns the path for the downloaded offline guide XML
     * @return
     */
    public String getOfflineGuideXmlFilePath() {
        return getOfflineGuidePath() + "/" + mGuideId + ".xml";
    }

    /**
     * Returns the date/time the guide was downloaded at.
     * @return
     */
    public Date getDownloadedGuideDate() {
        String path = getOfflineGuideXmlFilePath();
        File xmlFile = new File(path);

        return new Date(xmlFile.lastModified());
    }


    /**
     * Deletes the directory to which the offline guide was downloaded/extract to
     * @return true/false status
     */
    public boolean deleteOfflineGuide() {
        // Delete all files inside directory
        deleteFiles(mContext.getExternalCacheDir() + OFFLINE_GUIDE_PATH + mGuideId);

        return true;
    }

    /**
     * Recursively delete files and folders
     * @param uri
     */
    private void deleteFiles(String uri) {
        File currentFile = new File(uri);
        File files[] = currentFile.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteFiles(files[i].toString());
                }
                files[i].delete();
            }
        }

        currentFile.delete();
    }

    /**
     * Extracts a downloaded NGZ file into the offline guide directory
     * @param ngzFilename the NGZ file path
     * @return true/false status
     */
    public boolean extractOfflineGuide(String ngzFilename) {
        // First, create the offline guide directory, if it doesn't exist
        File offlineGuidesDir = new File(mContext.getExternalCacheDir() + OFFLINE_GUIDE_PATH + mGuideId);
        offlineGuidesDir.mkdirs();

        // Next, extract the NGZ file into that directory
        String basePath = offlineGuidesDir.getPath();
        InputStream is;
        ZipInputStream zis;
        try {
            String filename;
            is = new FileInputStream(ngzFilename);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            // Extract all files in the zip file - one by one
            while ((ze = zis.getNextEntry()) != null) {
                // Get current filename
                filename = ze.getName();

                // Need to create directories if doesn't exist, or it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(basePath + "/" + filename);
                    fmd.mkdirs();
                    continue;
                }

                FileOutputStream fout = new FileOutputStream(basePath + "/" + filename);

                // Extract current file
                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        }
        catch(IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Returns the guide's ID
     * @return the guide's ID
     */
    public String getID() {
        return mGuideId;
    }


    /**
     * Returns the guide's title
     * @return the guide's title
     */
    public String getTitle() {
        return getValueByXPath("//INatGuide/dc:title");
    }

    /**
     * Returns the guide's description
     * @return the guide's description
     */
    public String getDescription() {
        return getValueByXPath("//INatGuide/dc:description");
    }


    /**
     * Returns the guide's compiler (user who created this guide)
     * @return the guide's compiler
     */
    public String getCompiler() {
        return getValueByXPath("//INatGuide/eol:agent[@role='compiler']");
    }

    /**
     * Returns the guide's license
     * @return the guide's license
     */
    public String getLicense() {
        return getValueByXPath("//INatGuide/dc:license");
    }

    /**
     * Utility method for converting a license URL to textual representation
     * @param context
     * @param license
     * @return
     */
    public static String licenseToText(Context context, String license) {
        if ((license == null) || (license.length() == 0)) {
            // No license
            return context.getResources().getString(R.string.license_none);
        }

        String[] parts = license.split("/");
        if (parts.length > 2) {
            return String.format("CC %s", parts[parts.length - 2].toUpperCase());
        } else {
            return license;
        }
    }

    /**
     * Checks whether or not an offline guide is available for download
     * @return
     */
    public boolean isOfflineGuideAvailable() {
        String ngzUrl = getNgzURL();
        return ((ngzUrl!= null) && (ngzUrl.length() > 0));
    }

    /**
     * Returns the guide's NGZ file URL
     * @return the guide's NGZ URL
     */
    public String getNgzURL() {
        return getValueByXPath("//ngz/href");
    }

    /**
     * Returns the guide's NGZ file size (e.g. 1.71 MB)
     * @return the guide's NGZ file size
     */
    public String getNgzFileSize() {
        return getValueByXPath("//ngz/size");
    }

    /**
     * Utility method that parses out all of the guide photos's taxon tags
     */
    private void parseImageTags() {
        ArrayList<Node> nodes = getNodesByXPath("//GuideTaxon/GuidePhoto/tag");

        if (nodes == null) {
            return;
        }

        mReprTagPhotos = new HashMap<Pair<String, String>, List<GuideTaxonPhotoXML>>();

        for (Node node: nodes) {
            String predicateName = getAttribute(node, "predicate");
            if ((predicateName == null) || (predicateName.length() == 0)) {
                predicateName = PREDICATE_TAGS;
            }
            String value = getAttribute(node, "value");
            Pair<String, String> key = new Pair<String, String>(predicateName, value);
            GuideTaxonPhotoXML photo = new GuideTaxonPhotoXML(this, node.getParentNode());

            if (!mReprTagPhotos.containsKey(key)) {
                mReprTagPhotos.put(key, new ArrayList<GuideTaxonPhotoXML>());
            }

            List<GuideTaxonPhotoXML> photos = mReprTagPhotos.get(key);
            photos.add(photo);
        }
    }

    /**
     * Returns the representative photos of a specific tag name + value combo.
     *
     * @param tagName the tag name (e.g. number of legs)
     * @param tagValue the tag value (e.g. 3)
     * @return the representative photos for that combo (or null if non existent)
     */
    public List<GuideTaxonPhotoXML> getTagRepresentativePhoto(String tagName, String tagValue) {
        Pair<String, String> key = new Pair<String, String>(tagName, tagValue);

        if (!mReprTagPhotos.containsKey(key)) {
            return null;
        }

        return mReprTagPhotos.get(key);
    }

    /**
     * Utility method that parses out all of the guide's taxon tags
     */
    private void parseTags() {
        ArrayList<Node> nodes = getNodesByXPath("//GuideTaxon/tag");
        Map<String, Set<String>> predicates = new HashMap<String, Set<String>>();
        Map<String, Integer> tagCounts = new HashMap<String, Integer>();

        if (nodes == null) {
            return;
        }

        for (Node node: nodes) {
            String predicateName = getAttribute(node, "predicate");
            String tagName = node.getTextContent();
            if ((predicateName == null) || (predicateName.equalsIgnoreCase(PREDICATE_TAGS)) || (predicateName.length() == 0)) {
                predicateName = PREDICATE_TAGS;
            }
            if (!predicates.containsKey(predicateName)) {
                predicates.put(predicateName, new HashSet<String>());
            }
            if (!tagCounts.containsKey(tagName)) {
                tagCounts.put(tagName, Integer.valueOf(0));
            }
            HashSet<String> tags = (HashSet<String>)predicates.get(predicateName);
            Integer tagCount = tagCounts.get(tagName);
            tagCounts.put(tagName, tagCount + 1);

            tags.add(tagName);
        }
        
        mTagCounts = tagCounts;
        mTags = predicates;
    }

    /**
     * Returns a map of tag name -> count (how many taxa with that tags are found in the guide)
     * @return
     */
    public Map<String, Integer> getTagCounts() {
        return mTagCounts;
    }

    /**
     * Returns all the tags set for the guide taxa
     * @return a map of predicates and its set of tags
     */
    public Map<String, Set<String>> getAllTags() {
        return mTags;
    }

    /**
     * Returns a guide taxon by ID
     * @param taxonId
     * @return
     */
    public GuideTaxonXML getTaxonById(String taxonId) {
        ArrayList<Node> nodes = getNodesByXPath(String.format("//GuideTaxon/taxonID/text()[contains(.,'%s')]/ancestor::*[self::GuideTaxon]", taxonId));

        if (nodes.size() == 0) return null;

        return new GuideTaxonXML(this, nodes.get(0));
    }

    /**
     * Used by getRecommendedPredicate
     * @param data
     * @return
     */
    private Double getMean(List<Integer> data) {
        double sum = 0.0;
        for (Integer a : data) {
            sum += a;
        }
        return sum / data.size();
    }

    /**
     * Used by getRecommendedPredicate
     * @param data
     * @return
     */
    private Double getVariance(Double mean, List<Integer> data) {
        if (data.size() <= 1) {
            // Undefined variance when there is one or less item
            return null;
        }

        double temp = 0;
        for(Integer a : data) {
            temp += (mean - a) * (mean - a);
        }
        return temp / data.size();
    }

    /**
     * Returns the next recommended predicate name, according to current filter tags and results - find out
     * the tag name that is the most "useful" one for filtering results. For example, a tag that will
     * not eliminate the results by much will not be considered a good candidate.
     * @param filter current filter tags
     * @param currentResults current search results
     * @return the next recommended tag name.
     */
    public String getRecommendedPredicate(GuideTaxonFilter filter, List<GuideTaxonXML> currentResults) {
        Map<String, Integer> currentTagCounts = new HashMap<String, Integer>();
        List<String> selectedPredicates = new ArrayList<String>();

        // Build the list of already-selected predicate names
        for (String tagName : filter.getAllTags()) {
            String predicateName = tagName.split("=")[0];
            selectedPredicates.add(predicateName);
        }

        // Calculate the tag counts from the remaining taxa results
        for (GuideTaxonXML taxon : currentResults) {
            ArrayList<Node> nodes = taxon.getNodesByXPath("tag");

            for (Node node : nodes) {
                String tagName = node.getTextContent();

                if (!currentTagCounts.containsKey(tagName)) {
                    currentTagCounts.put(tagName, 0);
                }
                int currentCount = currentTagCounts.get(tagName);
                currentTagCounts.put(tagName, currentCount + 1);
            }
        }

        // Next, create a list of tag counts for each predicate
        Map<String, List<Integer>> predicateTagCounts = new HashMap<String, List<Integer>>();

        for (String tagName : currentTagCounts.keySet()) {
            int tagCount = currentTagCounts.get(tagName);
            String predicateName = tagName.split("=")[0];

            if (!predicateTagCounts.containsKey(predicateName)) {
                predicateTagCounts.put(predicateName, new ArrayList<Integer>());
            }
            predicateTagCounts.get(predicateName).add(tagCount);
        }


        // Calculate the mean and variance for each predicate tag count list
        // (Key = predicate name; values = mean, variance
        SortedMap<String, Pair<Double, Double>> predicatesMeanAndVariance = new TreeMap<String, Pair<Double, Double>>();

        for (String predicateName : predicateTagCounts.keySet()) {
            List<Integer> predicateTagCount = predicateTagCounts.get(predicateName);

            if (selectedPredicates.contains(predicateName)) {
                // Predicate was already selected - skip it
                continue;
            }

            // Calculate mean and variance
            Double mean = getMean(predicateTagCount);
            Double variance = getVariance(mean, predicateTagCount);

            predicatesMeanAndVariance.put(predicateName, new Pair<Double, Double>(mean, variance));

            Log.e("AAA", predicateName + " = [" + StringUtils.join(predicateTagCount, ",") + "] = " + mean + ", " + variance);
        }

        // Finally, decide which of the predicates is the next recommended one

         // This comparator is the heart of our algorithm for choosing the next recommended predicate:
        // We choose the one with the max mean; then, in case of identical means, we choose the one
        // with the minimum variance (that is not null).
        Comparator<Map.Entry> predicateComparator = new Comparator<Map.Entry>() {
            @Override
            public int compare(Map.Entry entry1, Map.Entry entry2) {
                Pair<Double, Double> predicate1 = (Pair<Double, Double>) entry1.getValue();
                Pair<Double, Double> predicate2 = (Pair<Double, Double>) entry2.getValue();

                // First, compare by mean
                if (predicate1.first > predicate2.first) {
                    return -1;
                } else if (predicate2.first > predicate1.first) {
                    return 1;
                } else {
                    // Means are identical - compare by variance
                    if ((predicate1.second == null) && (predicate2.second != null)) {
                        return 1;
                    } else if ((predicate2.second == null) && (predicate1.second != null)) {
                        return -1;
                    } else if ((predicate2.second == null) && (predicate1.second == null)) {
                        return 0;
                    } else {
                        return (int)(predicate1.second - predicate2.second);
                    }
                }
            }
        };


        // Sort the predicate mean/variance list
        List<Map.Entry> list = new LinkedList(predicatesMeanAndVariance.entrySet());

        if (list.size() > 1) {
            Collections.sort(list, predicateComparator);
        }

        for (Map.Entry item : list) {
            Log.e("AAA", item.getKey() + " = " + ((Pair<Double,Double>)item.getValue()).first + ", " + ((Pair<Double,Double>)item.getValue()).second);
        }

        if (list.size() > 0) {
            return (String) list.get(0).getKey();
        } else {
            return null;
        }
    }


    /**
     * Utility method for building an xpath string by a taxon search filter
     * @param filter
     * @return
     */
    private String buildFilterXpath(GuideTaxonFilter filter) {
        String searchText = filter.getSearchText();
        String xPathExpression;

        if ((searchText == null) || (searchText.length() == 0)) {
            // No search text
            xPathExpression = "//GuideTaxon";
        } else {
            // Filter only guide taxa that fit the search text
            xPathExpression = String.format("//GuideTaxon/*/text()[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'%s')]/ancestor::*[self::GuideTaxon]",
                    searchText.toLowerCase());
        }

        List<String> tags = filter.getAllTags();
        if (tags.size() > 0) {
            // Filter by specific tags as well
            ArrayList<String> tagExpressions = new ArrayList<String>();
            for (String tag: tags) {
                tagExpressions.add(String.format("descendant::tag[text() = '%s']", tag));
            }
            xPathExpression = String.format("%s[%s]", xPathExpression, StringUtils.join(tagExpressions, " and "));
        }

        return xPathExpression;
    }


    /**
     * Returns the list of guide taxa according to the filter
     * @return
     */
    public List<GuideTaxonXML> getTaxa(GuideTaxonFilter filter) {
        String xPathExpression = buildFilterXpath(filter);

        // Get the list of all GuideTaxon nodes that fit the filter
        ArrayList<Node> nodes = getNodesByXPath(xPathExpression);
        ArrayList<GuideTaxonXML> taxa = new ArrayList<GuideTaxonXML>();

        // Initialize each node into a GuideTaxonXML instance
        for (Node node: nodes) {
            taxa.add(new GuideTaxonXML(this, node));
        }

        return taxa;
    }

}

