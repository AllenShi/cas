package org.apereo.cas.authentication;

/**
 * Created by tschmidt on 6/6/16.
 */
public class ImpersonateCredentialsMetaDataPopulator implements AuthenticationMetaDataPopulator {

    //@Value("${cas.allowed.to.impersonate:false}")
    //Boolean allowedToImpersonate;

    @Override
    public boolean supports(Credential credential) {
        return credential instanceof ImpUsernamePasswordCredential;
    }

    @Override
    public void populateAttributes(AuthenticationBuilder builder, AuthenticationTransaction transaction) {
        builder.addAttribute("imp", ((ImpUsernamePasswordCredential) transaction.getCredential().get()).getImpname());
    }
}
