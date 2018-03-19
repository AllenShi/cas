package org.apereo.cas.configuration.model.core.standalone;

import java.io.File;
import java.io.Serializable;

/**
 * This is {@link StandaloneConfigurationProperties}. This class is only designed here
 * to allow the configuration binding logic to recognize the settings. In actuality, the fields
 * listed here are not used directly as they are directly accessed and fetched via the runtime
 * environment to bootstrap cas settings in form of a property source locator, etc.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
public class StandaloneConfigurationProperties implements Serializable {
    private static final long serialVersionUID = -7749293768878152908L;
    /**
     * Describes a directory path where CAS configuration may be found.
     */
    private File configurationDirectory;

    /**
     * Describes a file path where that contains the CAS properties in a single file.
     */
    private File configurationFile;

    public File getConfigurationDirectory() {
        return configurationDirectory;
    }

    public void setConfigurationDirectory(File configurationDirectory) {
        this.configurationDirectory = configurationDirectory;
    }

    public File getConfigurationFile() {
        return configurationFile;
    }

    public void setConfigurationFile(File configurationFile) {
        this.configurationFile = configurationFile;
    }

    public StandaloneConfigurationSecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(StandaloneConfigurationSecurityProperties security) {
        this.security = security;
    }

    /**
     * Configuration security settings used to encrypt/decrypt values.
     * Settings are typically expected to be provided via command-line properties
     * or system/environment variables as properties are bootstrapped and fetched.
     * They are placed here to allow CAS to recognize their validity when passed.
     */
    private StandaloneConfigurationSecurityProperties security = new StandaloneConfigurationSecurityProperties();

    public static class StandaloneConfigurationSecurityProperties implements Serializable {
        private static final long serialVersionUID = 8571848605614437022L;

        /**
         * Algorithm to use when deciphering settings.
         */
        private String alg;

        /**
         * Security provider to use when deciphering settings.
         */
        private String provider;

        /**
         * Total number of iterations to use when deciphering settings.
         */
        private long iteration;

        /**
         * Secret key/password to use when deciphering settings.
         */
        private String psw;

        public String getAlg() {
            return alg;
        }

        public void setAlg(String alg) {
            this.alg = alg;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public long getIteration() {
            return iteration;
        }

        public void setIteration(long iteration) {
            this.iteration = iteration;
        }

        public String getPsw() {
            return psw;
        }

        public void setPsw(String psw) {
            this.psw = psw;
        }
    }

}
