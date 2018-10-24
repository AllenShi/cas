package org.apereo.cas.web.view;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationAttributeReleasePolicy;
import org.apereo.cas.authentication.ProtocolAttributeEncoder;
import org.apereo.cas.authentication.principal.DefaultPrincipalFactory;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.web.view.AbstractCasView;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.validation.Assertion;
import org.apereo.cas.web.view.Cas10ResponseView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * Custom View to Return the CAS 1.0 Protocol Response. Implemented as a view
 * class rather than a JSP (like CAS 2.0 spec) because of the requirement of the
 * line feeds to be "\n".
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
@Slf4j
public class CasImpersonate10ResponseView extends Cas10ResponseView {

    public CasImpersonate10ResponseView(final boolean successResponse,
                                        final ProtocolAttributeEncoder protocolAttributeEncoder,
                                        final ServicesManager servicesManager,
                                        final String authenticationContextAttribute,
                                        final AuthenticationAttributeReleasePolicy authenticationAttributeReleasePolicy) {
        super(successResponse, protocolAttributeEncoder, servicesManager, authenticationContextAttribute,
                authenticationAttributeReleasePolicy);
    }

    @Override
    protected Principal getPrincipal(Map<String, Object> model) {
        final Assertion assertion = (Assertion) model.get("assertion");
        final Authentication authentication = getPrimaryAuthenticationFrom(model);
        if (assertion.isFromNewLogin() && authentication.getAttributes().containsKey("imp")) {
            final String impName = (String) CollectionUtils.firstElement(authentication.getAttributes().get("imp")).get();
            return new DefaultPrincipalFactory().createPrincipal(impName);
        } else {
            return super.getPrincipal(model);
        }
    }
}
