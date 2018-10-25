package org.apereo.cas.impersonate;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.flow.configurer.DefaultLoginWebflowConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.Flow;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;

import java.util.Arrays;

@Slf4j
public class ImpersonateLoginWebflowConfigurer extends DefaultLoginWebflowConfigurer {


    /**
     * Instantiates a new Default webflow configurer.
     *
     * @param flowBuilderServices    the flow builder services
     * @param flowDefinitionRegistry the flow definition registry
     * @param applicationContext     the application context
     * @param casProperties          the cas properties
     */
    public ImpersonateLoginWebflowConfigurer(final FlowBuilderServices flowBuilderServices,
                                         final FlowDefinitionRegistry flowDefinitionRegistry,
                                         final ApplicationContext applicationContext,
                                         final CasConfigurationProperties casProperties) {

        super(flowBuilderServices, flowDefinitionRegistry, applicationContext, casProperties);
    }

    @Override
    public void doInitialize() {
        final Flow flow = getLoginFlow();

        if (flow != null) {
            createInitialFlowActions(flow);
            createDefaultGlobalExceptionHandlers(flow);
            createDefaultEndStates(flow);
            createDefaultDecisionStates(flow);
            createDefaultActionStates(flow);
            createDefaultViewStates(flow);
            createFlowVariable(flow, CasWebflowConstants.VAR_ID_CREDENTIAL, ImpUsernamePasswordCredential.class);

            setStartState(flow, CasWebflowConstants.STATE_ID_INITIAL_AUTHN_REQUEST_VALIDATION_CHECK);
        }

    }

    @Override
    public Flow getLoginFlow() {
        if (this.loginFlowDefinitionRegistry == null) {
            LOGGER.error("Login flow registry is not configured and/or initialized correctly.");
            return null;
        }
        final boolean found = Arrays.stream(this.loginFlowDefinitionRegistry.getFlowDefinitionIds()).anyMatch(f -> f.equals("impersonate"));
        if (found) {
            return (Flow) this.loginFlowDefinitionRegistry.getFlowDefinition("impersonate");
        }
        LOGGER.error("Could not find flow definition [{}]. Available flow definition ids are [{}]", "impersonate", this.loginFlowDefinitionRegistry.getFlowDefinitionIds());
        return null;
    }
}
