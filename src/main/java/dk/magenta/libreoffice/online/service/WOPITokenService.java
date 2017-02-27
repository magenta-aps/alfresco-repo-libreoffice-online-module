package dk.magenta.libreoffice.online.service;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.PersonService;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * Contains just two methods for now but more maybe added later as we may want to perform several actions from the
 * token context as opposed from the LOOLService context
 *
 * @author DarkStar1.
 */
public interface WOPITokenService {

    /**
     * Will return a file nodeRef for the Token in question
     * @param tokenInfo
     * @return
     */
    NodeRef getFileNodeRef(WOPIAccessTokenInfo tokenInfo);

    /**
     * Returns a PersonInfo for the token in question
     * @param tokenInfo
     * @return
     */
    PersonService.PersonInfo getUserInfoOfToken(WOPIAccessTokenInfo tokenInfo);

    /**
     * Gets a token from the request params
     * @param req
     * @return
     */
    WOPIAccessTokenInfo getTokenInfo(WebScriptRequest req);

}
