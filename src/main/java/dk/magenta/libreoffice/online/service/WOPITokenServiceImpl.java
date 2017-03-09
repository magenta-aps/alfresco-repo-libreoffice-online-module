package dk.magenta.libreoffice.online.service;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.NoSuchPersonException;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * @author DarkStar1.
 */
public class WOPITokenServiceImpl implements WOPITokenService {
    private static final Log logger = LogFactory.getLog(WOPITokenServiceImpl.class);

    NodeService nodeService;
    PersonService personService;
    LOOLService loolService;

    //<editor-fold desc="Service setters">
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    public void setLoolService(LOOLService loolService) {
        this.loolService = loolService;
    }

    //</editor-fold>

    /**
     * Will return a file nodeRef for the Token in question
     *
     * @param tokenInfo
     * @return
     */
    @Override
    public NodeRef getFileNodeRef(WOPIAccessTokenInfo tokenInfo) {
        NodeRef fileNodeRef = new NodeRef("workspace", "SpacesStore", tokenInfo.getFileId());
        if (nodeService.exists(fileNodeRef))
            return fileNodeRef;
        else return null;
    }

    /**
     * Returns a PersonInfo for the token in question
     *
     * @param tokenInfo
     * @return
     */
    @Override
    public PersonInfo getUserInfoOfToken(WOPIAccessTokenInfo tokenInfo) {
        try{
            NodeRef personNode = personService.getPerson(tokenInfo.getUserName());
            PersonInfo personInfo = new PersonInfo(personService.getPerson(personNode));
            return personInfo;
        }
        catch(NoSuchPersonException | NullPointerException npe){
            npe.printStackTrace();

            if (npe.getClass().equals(NoSuchPersonException.class)) {
                logger.error("Unable to retrieve person from user id [" + tokenInfo.getUserName() + "] specified in token.");
                throw new NoSuchPersonException("Unable to verify that the person exists. Please contact the system administrator");
            }
            if (npe.getClass().equals(NullPointerException.class)) {
                logger.error("Token info is null.");
                throw new NullPointerException("The token should not be null");
            }
            return null;
        }
    }

    /**
     * Gets a token from the request params
     *
     * @param req
     * @return
     */
    @Override
    public WOPIAccessTokenInfo getTokenInfo(WebScriptRequest req) {
        String fileId = req.getServiceMatch().getTemplateVars().get("fileId");
        String accessToken = req.getParameter("access_token");

        return loolService.getAccessToken(accessToken, fileId);
    }
}
