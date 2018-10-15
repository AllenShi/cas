package org.apereo.cas.config;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.authentication.AuthenticationAttributeReleasePolicy;
import org.apereo.cas.authentication.AuthenticationEventExecutionPlanConfigurer;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.ProtocolAttributeEncoder;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.impersonate.ImpersonateCredentialsMetaDataPopulator;
import org.apereo.cas.impersonate.ImpersonateLoginWebflowConfigurer;
import org.apereo.cas.impersonate.Impersonators;
import org.apereo.cas.web.flow.CasFlowHandlerAdapter;
import org.apereo.cas.web.flow.CasWebflowConfigurer;
import org.apereo.cas.web.flow.actions.CasDefaultFlowUrlHandler;
import org.apereo.cas.web.flow.executor.WebflowExecutorFactory;
import org.apereo.cas.web.view.CasImpersonate10ResponseView;
import org.apereo.cas.web.view.CasImpersonate20ResponseView;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.web.view.Cas30ResponseView;
import org.apereo.cas.web.view.attributes.DefaultCas30ProtocolAttributesRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.View;
import org.springframework.webflow.config.FlowDefinitionRegistryBuilder;
import org.springframework.webflow.context.servlet.FlowUrlHandler;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;
import org.springframework.webflow.execution.FlowExecutionListener;
import org.springframework.webflow.executor.FlowExecutor;
import org.springframework.webflow.mvc.servlet.FlowHandlerMapping;

@Configuration("casImpersonateConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
public class CasImpersonateConfiguration {

    private static final String BASE_CLASSPATH_WEBFLOW = "classpath*:/webflow";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("casAttributeEncoder")
    private ProtocolAttributeEncoder protocolAttributeEncoder;

    @Autowired
    @Qualifier("servicesManager")
    private ServicesManager servicesManager;

    @Autowired
    @Qualifier("authenticationAttributeReleasePolicy")
    private AuthenticationAttributeReleasePolicy authenticationAttributeReleasePolicy;

    @Autowired
    @Qualifier("cas2SuccessView")
    private View cas2SuccessView;

    @Autowired
    @Qualifier("cas3SuccessView")
    private View cas3SuccessView;

    @Autowired
    @Qualifier("authenticationServiceSelectionPlan")
    private ObjectProvider<AuthenticationServiceSelectionPlan> authenticationServiceSelectionPlan;

    @Autowired
    @Qualifier("builder")
    private FlowBuilderServices builder;

    @Autowired
    @Qualifier("webflowCipherExecutor")
    private CipherExecutor webflowCipherExecutor;


    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public View cas1ServiceSuccessView() {
        return new CasImpersonate10ResponseView(true,
                protocolAttributeEncoder,
                servicesManager,
                casProperties.getAuthn().getMfa().getAuthenticationContextAttribute(),
                authenticationAttributeReleasePolicy);
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public View cas2ServiceSuccessView() {
        return new CasImpersonate20ResponseView(true, protocolAttributeEncoder,
                servicesManager,
                casProperties.getAuthn().getMfa().getAuthenticationContextAttribute(),
                cas2SuccessView,
                authenticationAttributeReleasePolicy,
                authenticationServiceSelectionPlan.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public View cas3ServiceSuccessView() {
        final String authenticationContextAttribute = casProperties.getAuthn().getMfa().getAuthenticationContextAttribute();
        final boolean isReleaseProtocolAttributes = casProperties.getAuthn().isReleaseProtocolAttributes();
        return new Cas30ResponseView(true,
                protocolAttributeEncoder,
                servicesManager,
                authenticationContextAttribute,
                cas3SuccessView,
                isReleaseProtocolAttributes,
                authenticationAttributeReleasePolicy,
                authenticationServiceSelectionPlan.getIfAvailable(),
                new DefaultCas30ProtocolAttributesRenderer());
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public Impersonators impersonators() {
        return new Impersonators(casProperties.getServer().isImpersonate(),
                casProperties.getServer().getImpersonateFile());
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public ImpersonateCredentialsMetaDataPopulator impersonateCredentialsMetaDataPopulator() {
        return new ImpersonateCredentialsMetaDataPopulator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public AuthenticationEventExecutionPlanConfigurer casImpersonateMetadataAuthenticationEventExecutionPlanConfigurer() {
        return plan -> {
            plan.registerMetadataPopulator(impersonateCredentialsMetaDataPopulator());
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public FlowUrlHandler impersonateFlowUrlHandler() {
        return new CasDefaultFlowUrlHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public FlowDefinitionRegistry impersonateFlowRegistry() {
        final FlowDefinitionRegistryBuilder flowBuilder = new FlowDefinitionRegistryBuilder(this.applicationContext, builder);
        flowBuilder.setBasePath(BASE_CLASSPATH_WEBFLOW);
        flowBuilder.addFlowLocationPattern("/impersonate/*-webflow.xml");
        return flowBuilder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public FlowExecutor impersonateFlowExecutor() {
        final WebflowExecutorFactory factory = new WebflowExecutorFactory(casProperties.getWebflow(),
                impersonateFlowRegistry(), this.webflowCipherExecutor,
                new FlowExecutionListener[0]);

        return factory.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public HandlerAdapter impersonateHandlerAdapter() {
        final CasFlowHandlerAdapter handler = new CasFlowHandlerAdapter("impersonate");
        handler.setFlowExecutor(impersonateFlowExecutor());
        handler.setFlowUrlHandler(impersonateFlowUrlHandler());
        return handler;
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public HandlerMapping impersonateFlowHandlerMapping() {
        final FlowHandlerMapping handler = new FlowHandlerMapping();
        handler.setOrder(1);
        handler.setFlowRegistry(impersonateFlowRegistry());
        return handler;
    }

    @Bean
    @ConditionalOnProperty(prefix = "cas.server", name = "impersonate", havingValue = "true")
    public CasWebflowConfigurer impersonateFlowConfigurer() {
        final ImpersonateLoginWebflowConfigurer c = new ImpersonateLoginWebflowConfigurer(builder, impersonateFlowRegistry(),
                applicationContext, casProperties);
        c.setOrder(Ordered.HIGHEST_PRECEDENCE);
        c.doInitialize();
        return c;
    }
}
