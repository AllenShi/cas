package org.apereo.cas.web.flow;


import org.apereo.cas.authentication.DefaultAuthentication;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.core.collection.MutableAttributeMap;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import java.util.Map;
import java.util.Set;

public class JaasCheck extends AbstractAction {

    private final boolean jaasCheck;

    public JaasCheck(final boolean jaasCheck) {
        this.jaasCheck = jaasCheck;
    }

    @Override
    protected Event doExecute(RequestContext requestContext) throws Exception {
        if (!jaasCheck) {
            return no();
        }
        final MutableAttributeMap<Object> conversationScope = requestContext.getConversationScope();
        final DefaultAuthentication authentication = (DefaultAuthentication) conversationScope.get("authentication");
        final Object authMethod = authentication.getAttributes().get("authenticationMethod");
        boolean jaasUsed;
        if (authMethod instanceof Set) {
            jaasUsed = ((Set)authMethod).contains("JaasAuthenticationHandler");
        } else {
            jaasUsed = ((String)authMethod).equals("JaasAuthenticationHandler");
        }
        if (jaasCheck && jaasUsed
            && authentication.getFailures().size() > 0
            && !authentication.getFailures().get("LdapAuthenticationHandler").getClass().getName().equals("org.apereo.cas.authentication.PreventedException")) {
            return yes();
        }
        return no();
    }
}
