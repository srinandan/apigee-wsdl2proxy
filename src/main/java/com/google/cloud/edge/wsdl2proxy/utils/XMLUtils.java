package com.google.cloud.edge.wsdl2proxy.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by mviswanathan on 11/04/17.
 */
public class XMLUtils {
    private DocumentBuilder builder;
    private static final Logger LOGGER = Logger.getLogger(XMLUtils.class.getName());
    private static final ConsoleHandler handler = new ConsoleHandler();

    static {
        LOGGER.setLevel(Level.WARNING);
        // PUBLISH this level
        handler.setLevel(Level.WARNING);
        LOGGER.addHandler(handler);
    }

    private static final Set<String> blacklist = new HashSet<String>(
            Arrays.asList(new String[]{"http://schemas.xmlsoap.org/wsdl/soap/", "http://schemas.xmlsoap.org/wsdl/",
                    "http://schemas.xmlsoap.org/ws/2003/05/partner-link/", "http://www.w3.org/2001/XMLSchema",
                    "http://schemas.xmlsoap.org/soap/encoding/"}));

    private static String elementName = ":{local-name()}";

    public XMLUtils() throws Exception {
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    public boolean isValidXML(String xml) {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setValidating(true);

            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setErrorHandler(new ErrorHandler() {

                @Override
                public void warning(SAXParseException exception) throws SAXException {
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                }
            });
            documentBuilder.parse(new InputSource(new StringReader(xml)));

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Document readXML(String resource) throws Exception {
        try {
            try {
                //first attempt to read as resource; otherwise, it must be a file
                return builder.parse(getClass().getResourceAsStream(resource));
            } catch (IllegalArgumentException npe) {
                return builder.parse(new File(resource));
            }

        } catch (SAXParseException spe) {
            // Error generated by the parser
            LOGGER.severe("\n** Parsing error" + ", line " + spe.getLineNumber() + ", uri " + spe.getSystemId());
            LOGGER.severe("  " + spe.getMessage());
            throw spe;
        } catch (SAXException sxe) {
            LOGGER.severe(sxe.getMessage());
            throw sxe;
        } catch (IOException ioe) {
            LOGGER.severe(ioe.getMessage());
            throw ioe;
        } catch (Exception e) {
            LOGGER.severe(e.getMessage());
            throw e;
        }
    }

    public void writeXML(Document document, String filePath) throws Exception {

        LOGGER.entering(XMLUtils.class.getName(), new Object() {
        }.getClass().getEnclosingMethod().getName());

        try {
            document.setXmlStandalone(true);
            // Use a Transformer for output
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(document);
            File f = new File(filePath);
            FileOutputStream fos = new FileOutputStream(f, false);
            StreamResult result = new StreamResult(fos);
            transformer.transform(source, result);
            System.out.println("Write " + filePath);
            fos.close();
        } catch (IOException ioe) {
            LOGGER.severe(ioe.getMessage());
            throw ioe;
        } catch (TransformerConfigurationException tce) {
            LOGGER.severe("* Transformer Factory error");
            LOGGER.severe(" " + tce.getMessage());
            throw tce;
        } catch (TransformerException te) {
            LOGGER.severe("* Transformation error");
            LOGGER.severe(" " + te.getMessage());
            throw te;
        }
    }

    public Document getXMLFromString(String xml) throws Exception {

        LOGGER.entering(XMLUtils.class.getName(), new Object() {
        }.getClass().getEnclosingMethod().getName());

        try {
            InputSource is = new InputSource(new StringReader(xml));
            Document document = builder.parse(is);
            return cloneDocument(document);
        } catch (SAXException e) {
            LOGGER.severe(e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.severe(e.getMessage());
            throw e;
        }
    }

    private String extractElement(String fullElementName) {
        if (fullElementName.indexOf(":") != -1) {
            String elements[] = fullElementName.split(":");
            return elements[1];
        } else {
            return fullElementName;
        }
    }

    public List<String> getElementList(String xml) throws Exception {

        LOGGER.entering(XMLUtils.class.getName(), new Object() {
        }.getClass().getEnclosingMethod().getName());

        List<String> elementList = new ArrayList<String>();
        try {
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            XPathFactory xpf = XPathFactory.newInstance();
            XPath xp = xpf.newXPath();

            NodeList nodes = (NodeList) xp.evaluate("//@* | //*[not(*)]", doc, XPathConstants.NODESET);

            for (int i = 0, len = nodes.getLength(); i < len; i++) {
                elementList.add(extractElement(nodes.item(i).getNodeName()));
            }
            return elementList;
        } catch (SAXException e) {
            LOGGER.severe(e.getMessage());
            throw e;
        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
            throw e;
        } catch (XPathExpressionException e) {
            LOGGER.severe(e.getMessage());
            throw e;
        }
    }

    public Document cloneDocument(Document doc) throws Exception {
        Document clonedDoc = builder.newDocument();
        clonedDoc.appendChild(clonedDoc.importNode(doc.getDocumentElement(), true));
        return clonedDoc;
    }

    public Element getFirstChildElement(Node node) throws Exception {

        LOGGER.entering(XMLUtils.class.getName(), new Object() {
        }.getClass().getEnclosingMethod().getName());

        node = node.getFirstChild();
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
            node = node.getNextSibling();
        }
        return (Element) node;
    }
}
