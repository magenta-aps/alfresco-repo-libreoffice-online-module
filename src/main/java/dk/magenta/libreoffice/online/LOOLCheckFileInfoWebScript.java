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
import dk.magenta.libreoffice.online.service.WOPIAccessTokenInfo;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.extensions.webscripts.*;

import java.util.HashMap;
import java.util.Map;

public class LOOLCheckFileInfoWebScript extends DeclarativeWebScript {
    private LOOLService loolService;
    private NodeService nodeService;

    protected Map<String, Object> executeImpl(
            WebScriptRequest req, Status status, Cache cache) {
        NodeRef nodeRef = loolService.checkAccessToken(req);
        Map<String, Object> model = new HashMap<>();
        model.put("BaseFileName", getBaseFileName(nodeRef));
        model.put("Size", getSize(nodeRef));
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

    public void setLoolService(LOOLService loolService) {
        this.loolService = loolService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}