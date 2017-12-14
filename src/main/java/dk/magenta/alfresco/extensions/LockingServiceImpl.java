package dk.magenta.alfresco.extensions;

import dk.magenta.libreoffice.online.service.CollaborativeLockingService;
import org.alfresco.repo.lock.LockServiceImpl;
import org.alfresco.repo.lock.mem.Lifetime;
import org.alfresco.repo.lock.traitextender.LockServiceExtension;
import org.alfresco.repo.lock.traitextender.LockServiceTrait;
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
        PropertyCheck.mandatory(this, "collaborativeLockingService", collaborativeLockingService);
        super.init();
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

}
