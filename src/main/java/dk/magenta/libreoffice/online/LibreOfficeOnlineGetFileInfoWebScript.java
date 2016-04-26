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

import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LibreOfficeOnlineGetFileInfoWebScript extends DeclarativeWebScript {
    private LOOLService loolService;

    protected Map<String, Object> executeImpl(
            WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        String fileId = req.getServiceMatch().getTemplateVars().get("fileId");
        if (fileId == null) {
            throw new WebScriptException("No 'fileId' parameter supplied");
        }
        String accessToken = req.getParameter("access_token");

        // Check access token
        if (accessToken == null || !loolService.isValidAccessToken(fileId, accessToken)) {
            status.setCode(Status.STATUS_FORBIDDEN, "Access token invalid");
            return model;
        }
        model.put("BaseFileName", loolService.getBaseFileName(fileId));
        model.put("Size", loolService.getSize(fileId));
        return model;
    }

    public void setLoolService(LOOLService loolService) {
        this.loolService = loolService;
    }
}