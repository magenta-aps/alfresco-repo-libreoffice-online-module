package dk.magenta.libreoffice.online;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;

public class LOOLService {
    private static final Log logger = LogFactory.getLog(LOOLService.class);

    private URL wopiBaseURL;
    private WOPILoader wopiLoader;
    private NodeService nodeService;

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setWopiBaseURL(URL wopiBaseURL) {
        this.wopiBaseURL = wopiBaseURL;
    }

    public String getAccessToken(NodeRef nodeRef) {
        // TODO: Generate and store an access token only valid for the current
        // user/nodeRef combination.
        return AuthenticationUtil.getFullyAuthenticatedUser() + "-" +
                getFileIdForNodeRef(nodeRef);
    }

    /**
     * Returns the WOPI src URL for a given nodeRef and action.
     *
     * @param nodeRef
     * @param action
     * @return
     */
    public String getWopiSrcURL(NodeRef nodeRef, String action) throws IOException {
        ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        return wopiLoader.getSrcURL(contentData.getMimetype(), action);
    }

    private String getFileIdForNodeRef(NodeRef nodeRef) {
        return nodeRef.getId();
    }

    public NodeRef getNodeRefForFileId(String fileId) {
        // TODO: Implement differently?
        return new NodeRef("workspace", "SpacesStore", fileId);
    }

    public String getBaseFileName(String fileId) {
        NodeRef nodeRef = getNodeRefForFileId(fileId);
        String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        if (name != null) {
            return FilenameUtils.getBaseName(name);
        } else {
            return "";
        }
    }

    public long getSize(String fileId) {
        NodeRef nodeRef = getNodeRefForFileId(fileId);
        ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        return contentData.getSize();
    }

    public boolean isValidAccessToken(String fileId, String accessToken) {
        // TODO: Implement for real.
        return accessToken.equals(getAccessToken(getNodeRefForFileId(fileId)));
    }

    public class WOPILoader {
        private Document discoveryDoc;
        private URL wopiBaseUrl;

        public WOPILoader(URL wopiBaseUrl) {
            this.wopiBaseUrl = wopiBaseUrl;
        }

        /**
         * Return the srcurl for a given mimetype.
         *
         * @param mimeType
         * @return
         */
        public String getSrcURL(String mimeType, String action) throws IOException {
            // Attempt to reload discovery.xml from host if it isn't already
            // loaded.
            if (discoveryDoc == null) {
                try {
                    loadDiscoveryXML();
                } catch (IOException e) {
                    logger.warn("Failed to fetch discovery.xml file from WOPI server", e);
                    throw e;
                }
            }
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xPath = xPathFactory.newXPath();
            String xPathExpr =
                    ("/wopi-discovery/net-zone/app[@name='${mimeType}']/action[@name='${action}']/@urlsrc")
                            .replace("${mimeType}", mimeType)
                            .replace("${action}", action);
            try {
                return xPath.evaluate(xPathExpr, discoveryDoc);
            } catch (XPathExpressionException e) {
                e.printStackTrace();
                return null;
            }
        }

        private void loadDiscoveryXML() throws IOException {
            InputStream inputStream = fetchDiscoveryXML();
            discoveryDoc = parse(inputStream);
        }

        /**
         * Parse a discovery.xml file input stream.
         *
         * @param discoveryInputStream
         * @return
         */
        private Document parse(InputStream discoveryInputStream) {
            DocumentBuilderFactory builderFactory =
                    DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                return builder.parse(discoveryInputStream);
            } catch (ParserConfigurationException | IOException | SAXException e) {
                e.printStackTrace();
            }
            return null;
        }

        private InputStream fetchDiscoveryXML() throws IOException {
            URL discoveryURL = new URL(wopiBaseUrl.getProtocol(),
                    wopiBaseUrl.getHost(), wopiBaseUrl.getPort(), "/hosting/discovery");
            HttpURLConnection connection = (HttpURLConnection) discoveryURL.openConnection();
            return connection.getInputStream();
        }
    }

    public void init() {
        wopiLoader = new WOPILoader(wopiBaseURL);
        System.out.println("LOOLService!");
    }
}
