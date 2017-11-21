package dk.magenta.libreoffice.online.service;

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * @author lanre.
 */
public interface CollaborativeLockingService {
    /**
     * Applies collaborative lock aspect to the document. Will check for a normal lock first and log an exception should
     * there be one. boolean result is return so that the user can be made aware of the success of the operation.
     *
     * @param documentNode
     * @return {true | false}
     */
    boolean applyCollaborativeLock(NodeRef documentNode, String userId);

    /**
     * Removes the collaborative lock aspect. We do not care if one exists or not. but return a boolean to indicate the
     * success of the operation so the user is aware and we do not silently fail the operation.
     *
     * @param documentNode
     * @return {true | false}
     */
    boolean removeCollaborativeLock(NodeRef documentNode);

    /**
     * Checks whether there is a collaborative lock aspect on the node
     *
     * @param documentNode
     * @return {true | false}
     */
    boolean isLocked(NodeRef documentNode);
}
