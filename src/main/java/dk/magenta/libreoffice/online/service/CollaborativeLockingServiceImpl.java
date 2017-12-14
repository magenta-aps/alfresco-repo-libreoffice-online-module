package dk.magenta.libreoffice.online.service;

import dk.magenta.collaborative.exceptions.NodeLockedException;
import dk.magenta.collaborative.model.CollaborativeLockModel;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.copy.CopyBehaviourCallback;
import org.alfresco.repo.copy.CopyDetails;
import org.alfresco.repo.copy.CopyServicePolicies;
import org.alfresco.repo.copy.DoNothingCopyBehaviourCallback;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.dictionary.InvalidAspectException;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author lanre.
 */
public class CollaborativeLockingServiceImpl implements CollaborativeLockingService,
        NodeServicePolicies.BeforeAddAspectPolicy, NodeServicePolicies.BeforeDeleteNodePolicy,
        NodeServicePolicies.BeforeMoveNodePolicy, CopyServicePolicies.OnCopyNodePolicy {

    private static final Log logger = LogFactory.getLog(CollaborativeLockingServiceImpl.class);

    private NodeService nodeService;
    private LockService lockService;
    private PolicyComponent policyComponent;

    public void init() {

        PropertyCheck.mandatory(this, "nodeService", nodeService);
        PropertyCheck.mandatory(this, "lockService", lockService);

        //<editor-fold desc="Register behaviour methods">
        this.policyComponent.bindClassBehaviour(
                NodeServicePolicies.BeforeAddAspectPolicy.QNAME,
                CollaborativeLockModel.ASPECT_COLLABORATION_LOCK,
                new JavaBehaviour(this, "beforeAddAspect"));

        this.policyComponent.bindClassBehaviour(
                NodeServicePolicies.BeforeDeleteNodePolicy.QNAME,
                CollaborativeLockModel.ASPECT_COLLABORATION_LOCK,
                new JavaBehaviour(this, "beforeDeleteNode"));

        this.policyComponent.bindClassBehaviour(
                NodeServicePolicies.BeforeMoveNodePolicy.QNAME,
                CollaborativeLockModel.ASPECT_COLLABORATION_LOCK,
                new JavaBehaviour(this, "beforeMoveNode"));

        // Register copy class behaviour
        this.policyComponent.bindClassBehaviour(
                CopyServicePolicies.OnCopyNodePolicy.QNAME,
                CollaborativeLockModel.ASPECT_COLLABORATION_LOCK,
                new JavaBehaviour(this, "getCopyCallback"));
        //</editor-fold>
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
        lockService.checkForLock(documentNode);
        try {
            this.nodeService.addAspect(documentNode, CollaborativeLockModel.ASPECT_COLLABORATION_LOCK, null);
            return true;
        } catch (InvalidNodeRefException | InvalidAspectException inae) {
            logger.error("Unable to lock document for collaborative editing due to the following issue:\n" + inae.getMessage());
            inae.printStackTrace();
            return false;
        }
    }

    /**
     * Removes the collaborative lock aspect. We do not care if one exists or not
     *
     * @param documentNode
     */
    @Override
    public boolean removeCollaborativeLock(NodeRef documentNode) {
        try {
            this.nodeService.removeAspect(documentNode, CollaborativeLockModel.ASPECT_COLLABORATION_LOCK);
            return true;
        } catch (InvalidNodeRefException | InvalidAspectException inae) {
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

    //<editor-fold desc="Bound behavioural methods">

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    /**
     * Called before an <b>aspect</b> is added to a node
     *
     * @param nodeRef         the node to which the aspect will be added
     * @param aspectTypeQName the type of the aspect
     */
    @Override
    public void beforeAddAspect(NodeRef nodeRef, QName aspectTypeQName) {
        if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_LOCKABLE) || nodeService.hasAspect(nodeRef, ContentModel.ASPECT_CHECKED_OUT))
            throw new NodeLockedException(nodeRef, "Can not lock for collaborative editing. Node is either checked out" +
                    "or is locked for single mode editing");
    }

    /**
     * Called before a node is deleted.
     *
     * @param nodeRef the node reference
     */
    @Override
    public void beforeDeleteNode(NodeRef nodeRef) {
        if (nodeService.hasAspect(nodeRef, CollaborativeLockModel.ASPECT_COLLABORATION_LOCK))
            throw new NodeLockedException(nodeRef, "Unable to delete node whilst it is being collaboratively edited");
    }

    /**
     * Called before a node is moved.
     *
     * @param oldChildAssocRef the child association reference prior to the move
     * @param newParentRef     the new parent node reference
     * @since 4.1
     */
    @Override
    public void beforeMoveNode(ChildAssociationRef oldChildAssocRef, NodeRef newParentRef) {
        if (nodeService.hasAspect(oldChildAssocRef.getChildRef(), CollaborativeLockModel.ASPECT_COLLABORATION_LOCK))
            throw new NodeLockedException(oldChildAssocRef.getChildRef(), "Can not to move node whilst it is being collaboratively edited");
    }

    /**
     * @param classRef
     * @param copyDetails
     * @return
     * @link org.alfresco.repo.copy.DoNothingCopyBehaviourCallback
     */
    public CopyBehaviourCallback getCopyCallback(QName classRef, CopyDetails copyDetails) {
        return DoNothingCopyBehaviourCallback.getInstance();
    }
    //</editor-fold>

    //<editor-fold desc="Service Setters">
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setLockService(LockService lockService) {
        this.lockService = lockService;
    }
    //</editor-fold>

}
