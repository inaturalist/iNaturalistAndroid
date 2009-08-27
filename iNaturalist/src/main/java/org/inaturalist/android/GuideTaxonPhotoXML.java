package org.inaturalist.android;

import org.w3c.dom.Node;

import java.io.File;
import java.io.Serializable;

/**
 * Represents a GuideTaxonPhotoXML XML node
 */
public class GuideTaxonPhotoXML extends BaseGuideXMLParser implements Serializable {

    private GuideXML mGuide;

    // The type of guide taxon photo (remote/local)
    public enum PhotoType {
        REMOTE,
        LOCAL
    }

    // The size of the guide taxon photo (thumbnail/small/medium/large)
    public enum PhotoSize {
        THUMBNAIL,
        SMALL,
        MEDIUM,
        LARGE
    }

    /**
     * Initialize the GuideTaxonPhotoXML class with the base guide and root XML node for it
     * @param guide
     * @param root
     */
    public GuideTaxonPhotoXML(GuideXML guide, Node root) {
        setRootNode(root);
        mGuide = guide;
    }


    /**
     * Returns the photo's description
     * @return the photo's description
     */
    public String getDescription() {
        return getValueByXPath("dc:description");
    }

    /**
     * Returns the photo's attribution
     * @return the photo's attribution
     */
    public String getAttribution() {
        return getValueByXPath("attribution");
    }

    /**
     * Returns the photo's rights holder
     * @return the photo's rights holder
     */
    public String getRightsHolder() {
        return getValueByXPath("dcterms:rightsHolder");
    }


    /**
     * Returns the path (URL/local file path) of a guide taxon photo, according to photo type (local/remote)
     * and photo size (thumbnail/small/medium/large).
     * @param photoType
     * @param photoSize
     * @return URL of the photo (if photoType==REMOTE) or file location (if photoType==LOCAL)
     */
    public String getPhotoLocation(PhotoType photoType, PhotoSize photoSize) {
        String typeString, sizeString;
        switch (photoType) {
            case LOCAL:
                typeString = "local";
                break;
            case REMOTE:
            default:
                typeString = "remote";
        }
        switch (photoSize) {
            case THUMBNAIL:
                sizeString = "thumb";
                break;
            case SMALL:
                sizeString = "small";
                break;
            case MEDIUM:
                sizeString = "medium";
                break;
            case LARGE:
            default:
                sizeString = "large";
        }

        String path = getValueByXPath(String.format(
                "descendant::href[@type='%s' and @size='%s']",
                typeString, sizeString));

        if ((photoType == PhotoType.LOCAL) && (path != null)) {
            // Path is local - Need to convert it from a relative path (e.g. "files/guide_photo-1234.jpg") to a full fledged path
            path = mGuide.getOfflineGuidePath() + "/" + path;

            // Make sure file exists
            File photoFile = new File(path);
            if (!photoFile.exists()) {
                return null;
            }
        }

        return path;
    }


    public GuideXML getGuide() {
        return mGuide;
    }

}
