package dk.magenta.collaborative.exceptions;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.cmr.repository.NodeRef;

import java.text.MessageFormat;

/**
 * @author DarkStar1.
 */
public class NodeLockedException extends AlfrescoRuntimeException {

    private static final long serialVersionUID = 1436894956977362852L;

    /**
     * Constructor
     *
     * @param msgId the message id
     */
    public NodeLockedException(String msgId) {
        super(msgId);
    }

    public NodeLockedException(NodeRef nodeRef, String msgId) {
        super(MessageFormat.format(msgId, nodeRef.getId()));
    }
}
