package org.apereo.cas.web.flow;


import org.apereo.cas.authentication.DefaultAuthentication;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.core.collection.MutableAttributeMap;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import java.util.Set;

public class TlsCheck extends AbstractAction {

    private final boolean tlsCheck;

    public TlsCheck(final boolean tlsCheck) {
        this.tlsCheck = tlsCheck;
    }

    @Override
    protected Event doExecute(RequestContext requestContext) throws Exception {
        if (!tlsCheck) {
            return no();
        }
        final String service = requestContext.getRequestParameters().get("service");
        if (service.startsWith("https")) {
            return no();
        }
        return yes();
    }
}
