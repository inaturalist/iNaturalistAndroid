package org.inaturalist.android;

import android.content.Context;
import android.util.Log;

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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        try {
            // Read root node so we won't re-parse the XML file every time we evaluate an XPath
            XPath xpath = XPathFactory.newInstance().newXPath();
            setRootNode((Node) xpath.evaluate("/", inputSource, XPathConstants.NODE));
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        // Parse all taxon tags
        parseTags();
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
     * Utility method that parses out all of the guide's taxon tags
     */
    private void parseTags() {
        ArrayList<Node> nodes = getNodesByXPath("//GuideTaxon/tag");
        Map<String, Set<String>> predicates = new HashMap<String, Set<String>>();
        Map<String, Integer> tagCounts = new HashMap<String, Integer>();

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
     * Returns the list of guide taxa according to the filter
     * @return
     */
    public List<GuideTaxonXML> getTaxa(GuideTaxonFilter filter) {
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

