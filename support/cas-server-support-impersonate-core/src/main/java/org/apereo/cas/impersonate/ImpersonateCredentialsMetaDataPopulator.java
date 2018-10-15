package org.apereo.cas.impersonate;

import org.apereo.cas.authentication.AuthenticationBuilder;
import org.apereo.cas.authentication.AuthenticationMetaDataPopulator;
import org.apereo.cas.authentication.AuthenticationTransaction;
import org.apereo.cas.authentication.Credential;

/**
 * Created by tschmidt on 6/6/16.
 */
public class ImpersonateCredentialsMetaDataPopulator implements AuthenticationMetaDataPopulator {

    @Override
    public boolean supports(Credential credential) {
        return credential instanceof ImpUsernamePasswordCredential;
    }

    @Override
    public void populateAttributes(AuthenticationBuilder builder, AuthenticationTransaction transaction) {
        builder.addAttribute("imp", ((ImpUsernamePasswordCredential) transaction.getPrimaryCredential().get()).getImpname());
    }
}
