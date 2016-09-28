package dk.magenta.libreoffice.online.service;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LOOLServiceImpl implements LOOLService {
    private static final Log logger = LogFactory.getLog(LOOLServiceImpl.class);

    private static final long ONE_HOUR_MS = 1000 * 60 * 60;

    // TODO: Make configurable
    // 24 hours in milliseconds
    private static final long TOKEN_TTL_MS = ONE_HOUR_MS * 24;
    private static final int DEFAULT_WOPI_PORT = 9980;

    private URL wopiBaseURL;
    private WOPILoader wopiLoader;
    private NodeService nodeService;
    private SysAdminParams sysAdminParams;

    private SecureRandom random = new SecureRandom();

    private Map<String, Map<String, WOPIAccessTokenInfo>> fileIdAccessTokenMap
            = new HashMap<>();

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setWopiBaseURL(URL wopiBaseURL) {
        this.wopiBaseURL = wopiBaseURL;
    }

    @Override
    public WOPIAccessTokenInfo createAccessToken(String fileId) {
        String userName = AuthenticationUtil.getRunAsUser();
        Date now = new Date();
        Date newExpiresAt = new Date(now.getTime() + TOKEN_TTL_MS);
        Map<String, WOPIAccessTokenInfo> tokenInfoMap = fileIdAccessTokenMap.get(fileId);
        WOPIAccessTokenInfo tokenInfo = null;
        if (tokenInfoMap != null) {
            tokenInfo = tokenInfoMap.get(userName);
            if (tokenInfo != null) {
                if (tokenInfo.isValid() &&
                        tokenInfo.getFileId().equals(fileId) &&
                        tokenInfo.getUserName().equals(userName)) {
                    // Renew token for a new time-to-live period.
                    tokenInfo.setExpiresAt(newExpiresAt);
                } else {
                    // Expired or not valid -- remove it.
                    tokenInfoMap.remove(userName);
                }
            }
        }
        if (tokenInfo == null) {
            tokenInfo = new WOPIAccessTokenInfo(generateAccessToken(),
                    now, newExpiresAt, fileId, userName);
            Map<String, WOPIAccessTokenInfo> v = fileIdAccessTokenMap.get
                    (fileId);
            if (v == null) {
                v = fileIdAccessTokenMap.put(fileId, new HashMap<String, WOPIAccessTokenInfo>());
            }
            fileIdAccessTokenMap.get(fileId).put(userName, tokenInfo);
        }
        return tokenInfo;
    }

    @Override
    public String generateAccessToken() {
        return new BigInteger(130, random).toString(32);
    }

    @Override
    public WOPIAccessTokenInfo getAccessToken(String accessToken, String fileId) {
        Map<String, WOPIAccessTokenInfo> tokenInfoMap = fileIdAccessTokenMap.get(fileId);
        if (tokenInfoMap != null) {
            WOPIAccessTokenInfo tokenInfo = null;
            // Find the token in the map values. Note that we don't know the
            // username for the token at this point, so we can't just do a
            // simple key lookup.
            for (WOPIAccessTokenInfo t : tokenInfoMap.values()) {
                if (t.getAccessToken().equals(accessToken)) {
                    tokenInfo = t;
                    break;
                }
            }
            if (tokenInfo != null) {
                // Found the access token for the given file id.
                return tokenInfo;
            } else {
                // Tokens exist for this file id, but given access token is
                // not one of them.
                return null;
            }
        } else {
            // No tokens found for this file id.
            return null;
        }
    }

    @Override
    public NodeRef checkAccessToken(WebScriptRequest req) throws WebScriptException {
        String fileId = req.getServiceMatch().getTemplateVars().get("fileId");
        if (fileId == null) {
            throw new WebScriptException("No 'fileId' parameter supplied");
        }
        String accessToken = req.getParameter("access_token");
        WOPIAccessTokenInfo tokenInfo = getAccessToken(accessToken, fileId);
        // Check access token
        if (accessToken == null || tokenInfo == null || !tokenInfo.isValid()) {
            throw new WebScriptException(Status.STATUS_UNAUTHORIZED, "Access token invalid or expired");
        }

        AuthenticationUtil.setRunAsUser(tokenInfo.getUserName());
        return getNodeRefForFileId(fileId);
    }

    /**
     * Returns the WOPI src URL for a given nodeRef and action.
     *
     * @param nodeRef
     * @param action
     * @return
     */
    @Override
    public String getWopiSrcURL(NodeRef nodeRef, String action) throws IOException {
        ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        return wopiLoader.getSrcURL(contentData.getMimetype(), action);
    }

    @Override
    public String getFileIdForNodeRef(NodeRef nodeRef) {
        return nodeRef.getId();
    }

    @Override
    public NodeRef getNodeRefForFileId(String fileId) {
        // TODO: Implement differently?
        return new NodeRef("workspace", "SpacesStore", fileId);
    }

    public void setSysAdminParams(SysAdminParams sysAdminParams) {
        this.sysAdminParams = sysAdminParams;
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
//            URL discoveryURL = new URL(wopiBaseUrl.getProtocol(),
//                    wopiBaseUrl.getHost(), wopiBaseUrl.getPort(), "/hosting/discovery");
//            HttpURLConnection connection = (HttpURLConnection) discoveryURL.openConnection();
//            return connection.getInputStream();

            // TODO: Remove me! Use the real fetching code (commented out
            // above)
            String xml = "\n" +
                    "<wopi-discovery>\n" +
                    "    <net-zone name=\"external-http\">\n" +
                    "        <app name=\"application/vnd.lotus-wordpro\">\n" +
                    "            <action ext=\"lwp\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"image/svg+xml\">\n" +
                    "            <action ext=\"svg\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.ms-powerpoint\">\n" +
                    "            <action ext=\"pot\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.ms-excel\">\n" +
                    "            <action ext=\"xla\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Writer documents -->\n" +
                    "        <app name=\"application/vnd.sun.xml.writer\">\n" +
                    "            <action ext=\"sxw\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.oasis.opendocument.text\">\n" +
                    "            <action ext=\"odt\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.oasis.opendocument.text-flat-xml\">\n" +
                    "            <action ext=\"fodt\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Calc documents -->\n" +
                    "        <app name=\"application/vnd.sun.xml.calc\">\n" +
                    "            <action ext=\"sxc\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.oasis.opendocument.spreadsheet\">\n" +
                    "            <action ext=\"ods\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.oasis.opendocument.spreadsheet-flat-xml\">\n" +
                    "            <action ext=\"fods\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Impress documents -->\n" +
                    "        <app name=\"application/vnd.sun.xml.impress\">\n" +
                    "            <action ext=\"sxi\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"application/vnd.oasis.opendocument.presentation\">\n" +
                    "            <action ext=\"odp\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.presentation-flat-xml\">\n" +
                    "            <action ext=\"fodp\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Draw documents -->\n" +
                    "        <app name=\"application/vnd.sun.xml.draw\">\n" +
                    "            <action ext=\"sxd\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "        <app name=\"iapplication/vnd.oasis.opendocument.graphics\">\n" +
                    "            <action ext=\"odg\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.graphics-flat-xml\">\n" +
                    "            <action ext=\"fodg\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Chart documents -->\n" +
                    "        <app name=\"application/vnd.oasis.opendocument.chart\">\n" +
                    "            <action ext=\"odc\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Text master documents -->\n" +
                    "        <app name=\"application/vnd.sun.xml.writer.global\">\n" +
                    "            <action ext=\"sxg\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.text-master\">\n" +
                    "            <action ext=\"odm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Math documents -->\n" +
                    "\t<app name=\"application/vnd.sun.xml.math\">\n" +
                    "            <action ext=\"sxm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.formula\">\n" +
                    "            <action ext=\"odf\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Text template documents -->\n" +
                    "\t<app name=\"application/vnd.sun.xml.writer.template\">\n" +
                    "            <action ext=\"stw\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.text-template\">\n" +
                    "            <action ext=\"ott\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Writer master document templates -->\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.text-master-template\">\n" +
                    "            <action ext=\"otm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Spreadsheet template documents -->\n" +
                    "\t<app name=\"application/vnd.sun.xml.calc.template\">\n" +
                    "            <action ext=\"stc\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.spreadsheet-template\">\n" +
                    "            <action ext=\"ots\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Presentation template documents -->\n" +
                    "\t<app name=\"application/vnd.sun.xml.impress.template\">\n" +
                    "            <action ext=\"sti\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.presentation-template\">\n" +
                    "            <action ext=\"otp\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Drawing template documents -->\n" +
                    "\t<app name=\"application/vnd.sun.xml.draw.template\">\n" +
                    "            <action ext=\"std\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.graphics-template\">\n" +
                    "            <action ext=\"otg\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Base documents -->\n" +
                    "\t<app name=\"application/vnd.oasis.opendocument.database\">\n" +
                    "            <action ext=\"odb\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Extensions -->\n" +
                    "\t<app name=\"application/vnd.openofficeorg.extension\">\n" +
                    "            <action ext=\"otx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Microsoft Word Template -->\n" +
                    "\t<app name=\"application/msword\">\n" +
                    "            <action ext=\"dot\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- OOXML wordprocessing -->\n" +
                    "\t<app name=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\">\n" +
                    "            <action ext=\"docx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-word.document.macroEnabled.12\">\n" +
                    "            <action ext=\"docm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.openxmlformats-officedocument.wordprocessingml.template\">\n" +
                    "            <action ext=\"dotx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-word.template.macroEnabled.12\">\n" +
                    "            <action ext=\"dotm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- OOXML spreadsheet -->\n" +
                    "\t<app name=\"application/vnd.openxmlformats-officedocument.spreadsheetml.template\">\n" +
                    "            <action ext=\"xltx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-excel.template.macroEnabled.12\">\n" +
                    "            <action ext=\"xltm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\">\n" +
                    "            <action ext=\"xlsx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-excel.sheet.binary.macroEnabled.12\">\n" +
                    "            <action ext=\"xlsb\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-excel.sheet.macroEnabled.12\">\n" +
                    "            <action ext=\"xlsm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- OOXML presentation -->\n" +
                    "\t<app name=\"application/vnd.openxmlformats-officedocument.presentationml.presentation\">\n" +
                    "            <action ext=\"pptx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-powerpoint.presentation.macroEnabled.12\">\n" +
                    "            <action ext=\"pptm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.openxmlformats-officedocument.presentationml.template\">\n" +
                    "            <action ext=\"potx\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-powerpoint.template.macroEnabled.12\">\n" +
                    "            <action ext=\"potm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\n" +
                    "\t<!-- Others -->\n" +
                    "\t<app name=\"application/vnd.wordperfect\">\n" +
                    "            <action ext=\"wpd\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/x-aportisdoc\">\n" +
                    "            <action ext=\"pdb\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/x-hwp\">\n" +
                    "            <action ext=\"hwp\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.ms-works\">\n" +
                    "            <action ext=\"wps\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/x-mswrite\">\n" +
                    "            <action ext=\"wri\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/x-dif-document\">\n" +
                    "            <action ext=\"dif\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"text/spreadsheet\">\n" +
                    "            <action ext=\"slk\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"text/csv\">\n" +
                    "            <action ext=\"csv\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/x-dbase\">\n" +
                    "            <action ext=\"dbf\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.lotus-1-2-3\">\n" +
                    "            <action ext=\"wk1\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"image/cgm\">\n" +
                    "            <action ext=\"cgm\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"image/vnd.dxf\">\n" +
                    "            <action ext=\"dxf\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"image/x-emf\">\n" +
                    "            <action ext=\"emf\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"image/x-wmf\">\n" +
                    "            <action ext=\"wmf\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/coreldraw\">\n" +
                    "            <action ext=\"cdr\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.visio2013\">\n" +
                    "            <action ext=\"vsd\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/vnd.visio\">\n" +
                    "            <action ext=\"vss\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "\t<app name=\"application/x-mspublisher\">\n" +
                    "            <action ext=\"pub\" name=\"edit\" urlsrc=\"https://lool.magenta.dk:9980/loleaflet/dist/loleaflet.html?\"/>\n" +
                    "        </app>\n" +
                    "    </net-zone>\n" +
                    "</wopi-discovery>";
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void init() {
        if (wopiBaseURL == null) {
            try {
                wopiBaseURL = new URL("https", sysAdminParams.getAlfrescoHost(),
                        DEFAULT_WOPI_PORT, "/");
            } catch (MalformedURLException e) {
                throw new AlfrescoRuntimeException("Invalid WOPI Base URL: "
                        + this.wopiBaseURL, e);
            }
        }
        wopiLoader = new WOPILoader(wopiBaseURL);
    }
}
