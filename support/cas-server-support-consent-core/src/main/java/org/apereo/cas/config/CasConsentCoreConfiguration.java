package org.apereo.cas.config;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.audit.AuditTrailRecordResolutionPlan;
import org.apereo.cas.audit.AuditTrailRecordResolutionPlanConfigurer;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.core.util.EncryptionJwtSigningJwtCryptographyProperties;
import org.apereo.cas.configuration.model.support.consent.ConsentProperties;
import org.apereo.cas.consent.AttributeReleaseConsentCipherExecutor;
import org.apereo.cas.consent.ConsentDecisionBuilder;
import org.apereo.cas.consent.ConsentEngine;
import org.apereo.cas.consent.ConsentRepository;
import org.apereo.cas.consent.DefaultConsentDecisionBuilder;
import org.apereo.cas.consent.DefaultConsentEngine;
import org.apereo.cas.consent.GroovyConsentRepository;
import org.apereo.cas.consent.InMemoryConsentRepository;
import org.apereo.cas.consent.JsonConsentRepository;
import org.apereo.cas.util.cipher.NoOpCipherExecutor;
import org.apereo.inspektr.audit.spi.AuditActionResolver;
import org.apereo.inspektr.audit.spi.AuditResourceResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * This is {@link CasConsentCoreConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Configuration("casConsentCoreConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
public class CasConsentCoreConfiguration implements AuditTrailRecordResolutionPlanConfigurer {

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("authenticationActionResolver")
    private AuditActionResolver authenticationActionResolver;

    @Autowired
    @Qualifier("returnValueResourceResolver")
    private AuditResourceResolver returnValueResourceResolver;

    @ConditionalOnMissingBean(name = "consentEngine")
    @Bean
    @RefreshScope
    public ConsentEngine consentEngine(@Qualifier("consentRepository") final ConsentRepository consentRepository) {
        return new DefaultConsentEngine(consentRepository, consentDecisionBuilder());
    }

    @ConditionalOnMissingBean(name = "consentCipherExecutor")
    @Bean
    @RefreshScope
    public CipherExecutor consentCipherExecutor() {
        final ConsentProperties consent = casProperties.getConsent();
        final EncryptionJwtSigningJwtCryptographyProperties crypto = consent.getCrypto();
        if (crypto.isEnabled()) {
            return new AttributeReleaseConsentCipherExecutor(crypto.getEncryption().getKey(), crypto.getSigning().getKey(), crypto.getAlg());
        }
        LOGGER.debug("Consent attributes stored by CAS are not signed/encrypted.");
        return NoOpCipherExecutor.getInstance();
    }

    @ConditionalOnMissingBean(name = "consentDecisionBuilder")
    @Bean
    @RefreshScope
    public ConsentDecisionBuilder consentDecisionBuilder() {
        return new DefaultConsentDecisionBuilder(consentCipherExecutor());
    }

    @ConditionalOnMissingBean(name = "consentRepository")
    @Bean
    @RefreshScope
    public ConsentRepository consentRepository() {
        final Resource location = casProperties.getConsent().getJson().getLocation();
        if (location != null) {
            LOGGER.warn("Storing consent records in [{}]. This MAY NOT be appropriate in production. "
                + "Consider choosing an alternative repository format for storing consent decisions", location);
            return new JsonConsentRepository(location);
        }

        final Resource groovy = casProperties.getConsent().getGroovy().getLocation();
        if (groovy != null) {
            return new GroovyConsentRepository(groovy);
        }

        LOGGER.warn("Storing consent records in memory. This option is ONLY relevant for demos and testing purposes.");
        return new InMemoryConsentRepository();
    }

    @Override
    public void configureAuditTrailRecordResolutionPlan(final AuditTrailRecordResolutionPlan plan) {
        plan.registerAuditActionResolver("SAVE_CONSENT_ACTION_RESOLVER", this.authenticationActionResolver);
        plan.registerAuditResourceResolver("SAVE_CONSENT_RESOURCE_RESOLVER", this.returnValueResourceResolver);
    }
}
