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
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LOOLGetTokenWebScript extends DeclarativeWebScript {
    private LOOLService loolService;

    protected Map<String, Object> executeImpl(
            WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        String nodeRefStr = req.getParameter("nodeRef");
        if (nodeRefStr == null) {
            throw new WebScriptException("No 'nodeRef' parameter supplied");
        }
        NodeRef nodeRef = new NodeRef(nodeRefStr);
        String action = req.getParameter("action");
        if (action == null) {
            throw new WebScriptException("No 'action' parameter supplied");
        }
        WOPIAccessTokenInfo tokenInfo = loolService.createAccessToken(loolService.getFileIdForNodeRef(nodeRef));
        String wopiSrcUrl;
        try {
            wopiSrcUrl = loolService.getWopiSrcURL(nodeRef, action);
        } catch (IOException e) {
            status.setCode(Status.STATUS_INTERNAL_SERVER_ERROR, "Failed to " +
                    "get wopiSrcURL");
            return model;
        }
        model.put("access_token", tokenInfo.getAccessToken());
        model.put("access_token_ttl", tokenInfo.getExpiresAt().getTime());
        model.put("wopi_src_url", wopiSrcUrl);
        return model;
    }

    public void setLoolService(LOOLService loolService) {
        this.loolService = loolService;
    }
}