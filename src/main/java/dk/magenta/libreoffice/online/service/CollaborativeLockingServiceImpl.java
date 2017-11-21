package dk.magenta.libreoffice.online.service;

import dk.magenta.collaborative.model.CollaborativeLockModel;
import org.alfresco.service.cmr.dictionary.InvalidAspectException;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.NodeLockedException;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author lanre.
 */
public class CollaborativeLockingServiceImpl implements CollaborativeLockingService {
    private static final Log logger = LogFactory.getLog(CollaborativeLockingServiceImpl.class);

    private NodeService nodeService;
    private LockService lockService;

    public void init() {

        PropertyCheck.mandatory(this, "nodeService", nodeService);
        PropertyCheck.mandatory(this, "lockService", lockService);
    }

    /**
     * Applies collaborative lock aspect to the document. Will check for a normal lock first and log an exception should
     * there be one. boolean result is return so that the user can be made aware of the success of the operation.
     *
     * @param documentNode
     * @param userId
     * @return {true | false}
     */
    @Override
    public boolean applyCollaborativeLock(NodeRef documentNode, String userId) {
        if (lockService.isLocked(documentNode))
            throw new NodeLockedException(documentNode);
        else {
            try {
                this.nodeService.addAspect(documentNode, CollaborativeLockModel.ASPECT_COLLABORATION_LOCK, null);
                return true;
            }
            catch (InvalidNodeRefException | InvalidAspectException inae){
                logger.error("Unable to lock document for collaborative editing due to the following issue:\n" + inae.getMessage());
                inae.printStackTrace();
                return false;
            }
        }
    }

    /**
     * Removes the collaborative lock aspect. We do not care if one exists or not
     *
     * @param documentNode
     */
    @Override
    public boolean removeCollaborativeLock(NodeRef documentNode){
        try{
            this.nodeService.removeAspect(documentNode, CollaborativeLockModel.ASPECT_COLLABORATION_LOCK);
            return true;
        }
        catch (InvalidNodeRefException | InvalidAspectException inae){
            logger.error("Unable to remove collaborative editing lock  due to the following issue:\n" + inae.getMessage());
            inae.printStackTrace();
            return false;
        }
    }

    /**
     * Checks whether there is a collaborative lock aspect on the node
     *
     * @param documentNode
     * @return {true | false}
     */
    @Override
    public boolean isLocked(NodeRef documentNode) {
       return this.nodeService.hasAspect(documentNode, CollaborativeLockModel.ASPECT_COLLABORATION_LOCK);
    }


    //<editor-fold desc="Service Setters">
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setLockService(LockService lockService) {
        this.lockService = lockService;
    }
    //</editor-fold>

}
