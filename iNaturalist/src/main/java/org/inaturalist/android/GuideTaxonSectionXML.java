package org.inaturalist.android;

import org.w3c.dom.Node;

/**
 * Represents a GuideTaxonSection XML node
 */
public class GuideTaxonSectionXML extends BaseGuideXMLParser {

    /**
     * Initialize the GuideTaxonSectionXML class with the root XML node for it
     * @param root
     */
    public GuideTaxonSectionXML(Node root) {
        setRootNode(root);
    }


    /**
     * Returns the section's title
     * @return the section's title
     */
    public String getTitle() {
        return getValueByXPath("dc:title");
    }

    /**
     * Returns the section's body
     * @return the section's body
     */
    public String getBody() {
        return getValueByXPath("dc:body");
    }

    /**
     * Returns the section's attribution
     * @return the section's attribution
     */
    public String getAttribution() {
        return getValueByXPath("attribution");
    }

    /**
     * Returns the section's rights holder
     * @return the section's rights holder
     */
    public String getRightsHolder() {
        return getValueByXPath("dcterms:rightsHolder");
    }

}
