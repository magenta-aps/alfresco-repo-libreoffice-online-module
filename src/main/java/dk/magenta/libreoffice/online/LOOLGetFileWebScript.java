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
import dk.magenta.libreoffice.online.service.LOOLServiceImpl;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.*;
import org.apache.chemistry.opencmis.commons.impl.IOUtils;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LOOLGetFileWebScript extends AbstractWebScript {
    private LOOLService loolService;
    private NodeService nodeService;
    private ContentService contentService;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        NodeRef nodeRef = loolService.checkAccessToken(req);
        ContentData contentProp = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        res.setContentType(contentProp.getMimetype());
        res.setContentEncoding(contentProp.getEncoding());

        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = reader.getContentInputStream();
            outputStream = res.getOutputStream();
            IOUtils.copy(inputStream, outputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }


    public void setLoolService(LOOLServiceImpl loolService) {
        this.loolService = loolService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }
}