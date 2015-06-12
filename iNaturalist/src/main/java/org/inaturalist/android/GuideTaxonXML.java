package org.inaturalist.android;

import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GuideTaxon XML node
 */
public class GuideTaxonXML extends BaseGuideXMLParser {

    private GuideXML mGuide;

    /**
     * Initialize the GuideTaxonXML class with the parent guide and root XML node for it
     * @param guide
     * @param root
     */
    public GuideTaxonXML(GuideXML guide, Node root) {
        mGuide = guide;
        setRootNode(root);
    }


    /**
     * Returns the taxon's name
     * @return the taxon's name
     */
    public String getName() {
        return getValueByXPath("descendant::name");
    }

    /**
     * Returns the taxon's display name
     * @return the taxon's display name
     */
    public String getDisplayName() {
        return getValueByXPath("descendant::displayName");
    }

    /**
     * Returns the taxon's id
     * @return the taxon's id
     */
    public String getTaxonId() {
        return getValueByXPath("descendant::taxonID");
    }


    /**
     * Returns the list of sections (e.g. summary section)
     * @return list of GuideTaxonSectionXML instances
     */
    public List<GuideTaxonSectionXML> getSections() {
        List<Node> nodes = getNodesByXPath("descendant::GuideSection");
        ArrayList<GuideTaxonSectionXML> sections = new ArrayList<GuideTaxonSectionXML>();

        for (Node node: nodes) {
            sections.add(new GuideTaxonSectionXML(node));
        }

        return sections;
    }

    /**
     * Returns the list of taxon photos
     * @return list of GuideTaxonPhotoXML instances
     */
    public List<GuideTaxonPhotoXML> getPhotos() {
        List<Node> nodes = getNodesByXPath("descendant::GuidePhoto");
        ArrayList<GuideTaxonPhotoXML> photos = new ArrayList<GuideTaxonPhotoXML>();

        for (Node node: nodes) {
            photos.add(new GuideTaxonPhotoXML(mGuide, node));
        }

        return photos;
    }

}
