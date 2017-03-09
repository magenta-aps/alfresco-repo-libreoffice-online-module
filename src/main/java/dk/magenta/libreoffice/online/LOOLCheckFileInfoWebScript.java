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
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.extensions.webscripts.*;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LOOLCheckFileInfoWebScript extends DeclarativeWebScript {
    private static final Logger logger = LoggerFactory.getLogger(LOOLCheckFileInfoWebScript.class);
    private LOOLService loolService;
    private NodeService nodeService;
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
            //Can't shuttle date straight to long so must get it into the date format first.
            Date lastModifiedDate =  (Date) nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED);
            //TODO Some properties are hard coded for now but we should look into making them sysadmin configurable
            model.put("BaseFileName", getBaseFileName(nodeRef));
            //We need to enable this if we want to be able to insert image into the documents
            model.put("DisableCopy", false);
            model.put("DisablePrint", true);
            model.put("DisableExport", true);
            model.put("HideExportOption", true);
            model.put("HideSaveOption", false);
            model.put("HidePrintOption", true);
            model.put("LastModifiedTime", lastModifiedDate.getTime());
            model.put("OwnerId", nodeService.getProperty(nodeRef, ContentModel.PROP_CREATOR).toString());
            model.put("Size", getSize(nodeRef));
            model.put("UserId", AuthenticationUtil.getRunAsUser());
            model.put("UserCanWrite", true);
            model.put("UserFriendlyName", AuthenticationUtil.getRunAsUser());
            model.put("Version",  getDocumentVersion(nodeRef));
            //Host from which token generation request originated
            model.put("PostMessageOrigin",  loolService.getAlfrescoProxyDomain());
            //Search https://www.collaboraoffice.com/category/community-en/ for EnableOwnerTermination
            model.put("EnableOwnerTermination",  false);
        }
        catch(Exception ge){
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "error returning file nodeRef\nReason:\n" + ge.getMessage());
        }
        return model;
    }

    public String getBaseFileName(NodeRef nodeRef) {
        String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        if (name != null) {
            return FilenameUtils.getBaseName(name);
        } else {
            return "";
        }
    }

    public long getSize(NodeRef nodeRef) {
        ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        return contentData.getSize();
    }

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
     * This get the version of a document, if the document hasn't been versioned yet it adds a versioning aspect to it.
     * Important to not that there are no checks as to whether the node is  document, hence the node passed to this
     * method should/must have been sanitized/verified/vetted before hand
     * @param nodeRef
     * @return
     */
    public String getDocumentVersion(NodeRef nodeRef){
        if(! nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE)){
            nodeService.addAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE, null);
        }

        return nodeService.getProperty(nodeRef, ContentModel.PROP_VERSION_LABEL).toString();
    }

    public void setLoolService(LOOLService loolService) {
        this.loolService = loolService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}