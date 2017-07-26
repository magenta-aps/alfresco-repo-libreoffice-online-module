/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package dk.magenta.libreoffice.online;

import dk.magenta.libreoffice.online.service.LOOLService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.extensions.webscripts.*;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LOOLCheckFileInfoWebScript extends DeclarativeWebScript {
    private static final Logger logger = LoggerFactory.getLogger(LOOLCheckFileInfoWebScript.class);
    private LOOLService loolService;
    private NodeService nodeService;
    private VersionService versionService;

    /**
     * https://msdn.microsoft.com/en-us/library/hh622920(v=office.12).aspx search for  "optional": false
     * to see mandatory parameters. (As of 29/11/2016 when this was modified, SHA is no longer needed)
     * Also return all values defined here: https://github.com/LibreOffice/online/blob/3ce8c3158a6b9375d4b8ca862ea5b50490af4c35/wsd/Storage.cpp#L403
     * because LOOL uses them internally to determine permission on rendering of certain elements.
     * Well I assume given the variable name(s), one should be able to semantically derive their relevance
     * @param req
     * @param status
     * @param cache
     * @return
     */
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        try {
            NodeRef nodeRef = loolService.checkAccessToken(req);
            Date lastModifiedDate =  (Date) nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED);
            //Convert lastModifiedTime to ISO 8601 according to:
            // https://github.com/LibreOffice/online/blob/master/wsd/Storage.cpp#L464 or look in the
            // std::unique_ptr<WopiStorage::WOPIFileInfo> WopiStorage::getWOPIFileInfo method
            String dte = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)
                         .format(Instant.ofEpochMilli(lastModifiedDate.getTime()));
            //TODO Some properties are hard coded for now but we should look into making them sysadmin configurable
            model.put("BaseFileName", getBaseFileName(nodeRef));
            //We need to enable this if we want to be able to insert image into the documents
            model.put("DisableCopy", false);
            model.put("DisablePrint", true);
            model.put("DisableExport", true);
            model.put("HideExportOption", true);
            model.put("HideSaveOption", false);
            model.put("HidePrintOption", true);
            model.put("LastModifiedTime", dte);
            model.put("OwnerId", nodeService.getProperty(nodeRef, ContentModel.PROP_CREATOR).toString());
            model.put("Size", getSize(nodeRef));
            model.put("UserId", AuthenticationUtil.getRunAsUser());
            model.put("UserCanWrite", true);
            model.put("UserExtraInfo", "");
            model.put("UserFriendlyName", AuthenticationUtil.getRunAsUser());
            model.put("Version",  getDocumentVersion(nodeRef));
            //Host from which token generation request originated
            model.put("PostMessageOrigin", loolService.getAlfExternalHost().toString());
            //Search https://www.collaboraoffice.com/category/community-en/ for EnableOwnerTermination
            // last found here: https://www.collaboraoffice.com/community-en/code-2-0-updates-2/
            model.put("EnableOwnerTermination",  false);
        }
        catch(Exception ge){
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Error returning file nodeRef\nReason:\n"
                    + ge.getMessage());
        }
        return model;
    }

    /**
     * Returns the actual file of the file itself (the cm:name property)
     * @param nodeRef
     * @return
     */
    public String getBaseFileName(NodeRef nodeRef) {
        String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        if (name != null) {
            return FilenameUtils.getName(name);
        } else {
            return "";
        }
    }

    /**
     * Returns the size of the file
     * @param nodeRef
     * @return
     */
    public long getSize(NodeRef nodeRef) {
        ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        return contentData.getSize();
    }

    /**
     * Previously, it was mandatory that the SHA 256 of the file be returned as part of the WOPI protocol.
     * It's no longer necessary but we'll leave this here just in case LOOL requires it in the future
     * @param nodeRef
     * @return
     * @throws IOException
     */
    protected String getSHAhash(NodeRef nodeRef)throws IOException{
        ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(contentData.getContentUrl().getBytes());
            byte [] aMessageDigest = md.digest();

            return Base64.getEncoder().encodeToString(aMessageDigest);
        }
        catch (NoSuchAlgorithmException nsae){
            logger.error("Unable to find encoding algorithm");
            throw new IOException ("Unable to generate a hash for the requested file");
        }
    }

    /**
     * This gets the version of a document. If the document hasn't been versioned yet, it adds a versioning aspect to it.
     * Important to note that there are no checks as to whether the node is a document, hence the node passed to this
     * method should/must have been sanitized/verified/vetted beforehand.
     * @param nodeRef
     * @return
     */
    public String getDocumentVersion(NodeRef nodeRef){
        if(! nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE)){
            Map<QName, Serializable> initialVersionProps = new HashMap<QName, Serializable>(1, 1.0f);
            versionService.ensureVersioningEnabled(nodeRef, initialVersionProps);
        }
        return nodeService.getProperty(nodeRef, ContentModel.PROP_VERSION_LABEL).toString();
    }

    //<editor-fold desc="Bean setters">
    public void setLoolService(LOOLService loolService) {
        this.loolService = loolService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }
    //</editor-fold>
}
