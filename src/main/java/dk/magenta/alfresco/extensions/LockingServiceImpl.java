package dk.magenta.alfresco.extensions;

import dk.magenta.collaborative.exceptions.NodeLockedException;
import dk.magenta.libreoffice.online.service.CollaborativeLockingService;
import org.alfresco.repo.lock.LockServiceImpl;
import org.alfresco.repo.lock.mem.Lifetime;
import org.alfresco.repo.lock.traitextender.LockServiceExtension;
import org.alfresco.repo.lock.traitextender.LockServiceTrait;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.lock.LockType;
import org.alfresco.service.cmr.lock.UnableToAquireLockException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.traitextender.Extend;
import org.alfresco.util.PropertyCheck;

/**
 * @author DarkStar1.
 */
public class LockingServiceImpl extends LockServiceImpl {

    private CollaborativeLockingService collaborativeLockingService;

    public void setCollaborativeLockingService(CollaborativeLockingService collaborativeLockingService) {
        this.collaborativeLockingService = collaborativeLockingService;
    }

    public LockingServiceImpl() {
        super();
    }

    @Override
    public void init() {
        super.init();
        PropertyCheck.mandatory(this, "collaborativeLockingService", collaborativeLockingService);
    }


    @Override
    @Extend(traitAPI = LockServiceTrait.class, extensionAPI = LockServiceExtension.class)
    public void lock(NodeRef nodeRef, LockType lockType, int timeToExpire, Lifetime lifetime, String additionalInfo) {
        if (collaborativeLockingService.isLocked(nodeRef)) {
            // Error since we are trying to lock a locked node
            throw new UnableToAquireLockException(nodeRef);
        }
        super.lock(nodeRef, lockType, timeToExpire, lifetime, additionalInfo);
    }

    @Override
    @Extend(traitAPI = LockServiceTrait.class, extensionAPI = LockServiceExtension.class)
    public boolean isLocked(NodeRef nodeRef) {
        LockStatus lockStatus = getLockStatus(nodeRef);
        switch (lockStatus) {
            case LOCKED:
            case LOCK_OWNER:
                return true;
            default:
                return collaborativeLockingService.isLocked(nodeRef);
        }
    }

    @Override
    public void beforeUpdateNode(NodeRef nodeRef) {
        //Check if the document is collaboratively locked before the running the normal checks
        if (!collaborativeLockingService.isLocked(nodeRef))
            checkForLock(nodeRef);
    }

    @Override
    public void beforeDeleteNode(NodeRef nodeRef) {
        if (collaborativeLockingService.isLocked(nodeRef) ){
            throw new NodeLockedException(nodeRef, "Cannot delete document whilst it is being collaboratively edited.");
        }
        else
            super.beforeDeleteNode(nodeRef);
    }


}
