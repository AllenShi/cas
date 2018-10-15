package org.apereo.cas.web.view;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationAttributeReleasePolicy;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.ProtocolAttributeEncoder;
import org.apereo.cas.authentication.principal.DefaultPrincipalFactory;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.validation.Assertion;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Renders and prepares CAS2 views. This view is responsible
 * to simply just prep the base model, and delegates to
 * a the real view to render the final output.
 *
 * @author Misagh Moayyed
 * @since 4.1.0
 */
@Slf4j
public class CasImpersonate20ResponseView extends Cas20ResponseView {

    public CasImpersonate20ResponseView(final boolean successResponse,
                                        final ProtocolAttributeEncoder protocolAttributeEncoder,
                                        final ServicesManager servicesManager,
                                        final String authenticationContextAttribute,
                                        final View view,
                                        final AuthenticationAttributeReleasePolicy authenticationAttributeReleasePolicy,
                                        final AuthenticationServiceSelectionPlan serviceSelectionStrategy) {
        super(successResponse, protocolAttributeEncoder, servicesManager, authenticationContextAttribute, view,
                authenticationAttributeReleasePolicy, serviceSelectionStrategy);
    }


    @Override
    protected void prepareMergedOutputModel(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        super.prepareMergedOutputModel(model, request, response);
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
