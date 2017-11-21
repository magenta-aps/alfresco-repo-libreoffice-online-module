package dk.magenta.collaborative.model;

import org.alfresco.service.namespace.QName;

/**
 * @author DarkStar1.
 */
public interface CollaborativeLockModel {

    String COLLAB_EDIT_MODEL_ = "http://magenta.dk/model/collaborative_edit/1.0";
    String COLLAB_EDIT_MODEL_PREFIX = "mca";

    /**
     * Aspects
     */
    QName ASPECT_COLLABORATION_LOCK = QName.createQName(COLLAB_EDIT_MODEL_, "lockable");

    /*
     * Properties. Until there is a need for this, we'll disable for the moment
     *
    QName PROP_COLLABORATION_IS_ACTIVE = QName.createQName(COLLAB_EDIT_MODEL_, "active");
    QName PROP_COLLABORATIO_PARTICIPANTS = QName.createQName(COLLAB_EDIT_MODEL_, "sessionParticipants");
    */
}
