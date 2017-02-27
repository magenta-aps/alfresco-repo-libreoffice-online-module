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

import dk.magenta.libreoffice.online.service.WOPIAccessTokenInfo;
import dk.magenta.libreoffice.online.service.WOPITokenService;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.PersonService;
import org.springframework.extensions.webscripts.*;

import java.io.IOException;

public class LOOLPutFileWebScript extends AbstractWebScript {
    private WOPITokenService wopiTokenService;
    private NodeService nodeService;
    private ContentService contentService;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        String wopiOverrideHeader = req.getHeader("X-WOPI-Override");
        if (wopiOverrideHeader == null) {
            wopiOverrideHeader = req.getHeader("X-WOPIOverride");
        }
        if (wopiOverrideHeader == null || !wopiOverrideHeader.equals("PUT")) {
            throw new WebScriptException("X-WOPI-Override header must be present and equal to 'PUT'");
        }

        try {
            WOPIAccessTokenInfo tokenInfo = wopiTokenService.getTokenInfo(req);
            NodeRef nodeRef = wopiTokenService.getFileNodeRef(tokenInfo);
            PersonService.PersonInfo person = wopiTokenService.getUserInfoOfToken(tokenInfo);

            ContentWriter writer = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
            writer.putContent(req.getContent().getInputStream());
            writer.guessMimetype((String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME));
            writer.guessEncoding();
            nodeService.setProperty(nodeRef, ContentModel.PROP_MODIFIER, person.getUserName());
        }
        catch(ContentIOException | WebScriptException we){
            we.printStackTrace();
            if (we.getClass() == ContentIOException.class)
                throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Error writing to file");
            else if (we.getClass() == WebScriptException.class)
                throw new WebScriptException(Status.STATUS_UNAUTHORIZED, "Access token invalid or expired");
            else
                throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Unidentified problem writing to file" +
                        "please consult system administrator for help on this issue ");
        }
    }

    public void setWopiTokenService(WOPITokenService wopiTokenService) {
        this.wopiTokenService = wopiTokenService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }
}