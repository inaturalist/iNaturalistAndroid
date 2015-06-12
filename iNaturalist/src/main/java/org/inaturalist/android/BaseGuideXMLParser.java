package org.inaturalist.android;

import android.util.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Base class for all XML-parsing classes (contains utility methods for parsing the XML)
 */
public abstract class BaseGuideXMLParser {
    private XPath mXpath;
    private Node mRootNode;
    // Cache that is used for storing past results of XPath expressions (much faster this way)
    // Key = xpath expression; value = either single result (string) or multiple results (list of
    // Nodes/Strings)
    private Map<String, Object> mCache;

    /**
     * Initialize the BaseGuideXMLParser class
     */
    public BaseGuideXMLParser() {
        mXpath = XPathFactory.newInstance().newXPath();

        // Set the namespaces used by the Guide XML code
        NamespaceContext context = new NamespaceContextMap(
            "dc", "http://purl.org/dc/elements/1.1/",
            "dcterms", "http://purl.org/dc/terms/",
            "eol", "http://www.eol.org/transfer/content/1.0");
        mXpath.setNamespaceContext(context);
        mCache = new HashMap<String, Object>();
    }


    /**
     * Sets the root node
     * @param rootNode
     */
    protected void setRootNode(Node rootNode) {
        mRootNode = rootNode;
    }

    /**
     * Returns the root node
     * @return
     */
    protected Node getRootNode() {
        return mRootNode;
    }


    /**
     * Returns a single value according to the given XPath expression
     * @param xpath
     * @return
     */
    protected String getValueByXPath(String xpath) {
        if (mCache.containsKey(xpath)) {
            // This Xpath was previously evaluated - return cached result
            return (String) mCache.get(xpath);
        }

        try {
            String result = mXpath.evaluate(xpath, mRootNode);
            mCache.put(xpath, result); // Save result to cache
            return result;
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a list of values according to the given  XPath expression
     * @param xpath
     * @return
     */
    protected ArrayList<String> getValuesByXPath(String xpath) {
        ArrayList<Node> nodes = getNodesByXPath(xpath);
        ArrayList<String> results = new ArrayList<String>();

        for (Node node: nodes) {
            results.add(node.getTextContent());
        }

        return results;
    }


    /**
     * Returns a list of XML nodes according to the given  XPath expression
     * @param xpath
     * @return
     */
    protected ArrayList<Node> getNodesByXPath(String xpath) {
        if (mCache.containsKey(xpath)) {
            // This Xpath was previously evaluated - return cached results
            return (ArrayList<Node>) mCache.get(xpath);
        }

        NodeList nodes = null;
       try {
           nodes = (NodeList)mXpath.evaluate(xpath, mRootNode, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            return null;
        }

        ArrayList<Node> results = new ArrayList<Node>();

        for (int i = 0; i < nodes.getLength(); i++){
            Node node = nodes.item(i);
            results.add(node);
        }

        mCache.put(xpath, results); // Save result to cache

        return results;
    }


    /**
     * Utility method for retrieving value of a specific property value
     * @param node
     * @param name
     * @return
     */
    protected String getAttribute(Node node, String name) {
        NamedNodeMap attributes = node.getAttributes();

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (attribute.getNodeName().equals(name)) {
                return attribute.getNodeValue();
            }
        }

        return null;

    }

}
