package dk.magenta.libreoffice.online;

import dk.magenta.libreoffice.online.service.CollaborativeLockingService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.extensions.webscripts.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lanre.
 */
public class WOPISessionLockRemover extends DeclarativeWebScript {
    private static final Logger logger = LoggerFactory.getLogger(WOPISessionLockRemover.class);

    private NodeService nodeService;
    private CollaborativeLockingService collaborativeLockingService;

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setCollaborativeLockingService(CollaborativeLockingService collaborativeLockingService) {
        this.collaborativeLockingService = collaborativeLockingService;
    }

    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        String fileId = req.getServiceMatch().getTemplateVars().get("fileId");
        if(StringUtils.isBlank(fileId)){
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "File ID parameter missing" );
        }
        NodeRef docNode = new NodeRef("workspace", "SpacesStore", fileId);
        if(!nodeService.exists(docNode)){
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "No session exists for the given Id: "+ fileId);
        }
        logger.info("\nReceived request to remove session for document: "+docNode.toString()+"\n");

        boolean result = this.collaborativeLockingService.removeCollaborativeLock(docNode);

        model.put("removed", result);
        switch (Boolean.toString(result) ){
            case "true":
                model.put("message", "Lock removed for session: " + fileId);
            case "false":
                model.put("message", "Lock not removed for session: " + fileId +
                        ".\nContact your administrator or check server logs for cause.");
        }
        return model;
    }

}
