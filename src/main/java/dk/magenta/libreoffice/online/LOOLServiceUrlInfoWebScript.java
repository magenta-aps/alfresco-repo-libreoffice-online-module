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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.extensions.webscripts.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class LOOLServiceUrlInfoWebScript extends DeclarativeWebScript {
    private static final Logger logger = LoggerFactory.getLogger(LOOLServiceUrlInfoWebScript.class);
    private String loolServiceUrl;

    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        try {
                URL loolHost = new URL(loolServiceUrl);

            logger.debug("\n\n------- The service url for WOPI is: ---------\n ("+loolServiceUrl+")\n\n");
            model.put("lool_host_url", loolServiceUrl);

        }
        catch(MalformedURLException ge){
            logger.error("=== Error ===\nInvalid service URL format. Did you make a typo somewhere??");
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "The WOPI Service URL is invalid");
        }
        return model;
    }

    public void setLoolServiceUrl(String loolServiceUrl) {
        this.loolServiceUrl = loolServiceUrl;
    }
}