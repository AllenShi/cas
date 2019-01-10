package org.apereo.cas.web.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.util.CasVersion;
import org.apereo.cas.util.InetAddressUtils;
import org.apereo.cas.web.BaseCasMvcEndpoint;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Reports overall CAS health based on the observations of the configured {@link HealthEndpoint} instance.
 *
 * @author Marvin S. Addison
 * @since 3.5
 */
@Slf4j
@Endpoint(id = "status")
public class StatusEndpoint extends BaseCasMvcEndpoint {
    private final HealthEndpoint healthEndpoint;

    private static final int PERCENTAGE_VALUE = 100;
    private static final double BYTES_PER_MB = 1048510.0;
    private static final double BYTEST_PER_KB = 1024.0;
    private static Status status;
    private static Health health;

    public StatusEndpoint(final CasConfigurationProperties casProperties, final HealthEndpoint healthEndpoint) {
        super(casProperties);
        this.healthEndpoint = healthEndpoint;
        StatusEndpoint.status = Status.UP;
        checkHealth();
    }

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT15S")
    protected void checkHealth() {
        if ("MAINTENANCE".equals(StatusEndpoint.status.getCode())) {
            LOGGER.debug("Server is in Maintenance and not accepting traffic");
            return;
        }
        LOGGER.debug("Performing health check");
        StatusEndpoint.health = this.healthEndpoint.health();
        StatusEndpoint.status = health.getStatus();
    }

    /**
     * Handle request.
     *
     * @param request  the request
     * @param response the response
     * @throws Exception the exception
     */
    @ReadOperation
    protected String handle() throws Exception {
        /*
        if (request.getParameterMap().containsKey("maintenance") && request.getRemoteAddr().equals("127.0.0.1")) {
            if (request.getParameter("maintenance").equals("on")) {
                StatusEndpoint.status = new Status("MAINTENANCE");
            } else {
                StatusEndpoint.status = Status.UP;
            }
        }
        */

        final StringBuilder sb = new StringBuilder();
        final Status status = StatusEndpoint.status;

        if (status.equals(Status.DOWN) || status.equals(Status.OUT_OF_SERVICE)) {
            //response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }

        sb.append("Health: ").append(status.getCode());

        if (status.getCode().equals("MAINTENANCE")) {
            sb.append("\n");
            //writeResponse(response, sb);
            return sb.toString();
        }

        ObjectMapper mapper = new ObjectMapper();
        final HzStats stats = mapper.convertValue(health.getDetails().get("hazelcast"), HzStats.class);

        sb.append("\n\n\tHazelcast: " + stats.getStatus());
        sb.append("\n\t  Members: " + stats.details.clusterSize);
        sb.append("\n\t  Is Master: " +  stats.details.isMaster());
        for(String key : stats.details.maps.keySet()) {
            sb.append("\n\t  Map: " + key);
            sb.append(" - Entries: " + stats.details.maps.get(key).size + " - Size: " + formatMemory(stats.details.maps.get(key).memory));
            sb.append(" using " + stats.details.maps.get(key).percentFree +"% of heap");
            sb.append("\n\t\tLocal Count: " + stats.details.maps.get(key).localCount);
            sb.append("\n\t\tBackup Count: " + stats.details.maps.get(key).backupCount);
            sb.append("\n\t\tGet Latency: " + stats.details.maps.get(key).getLatency);
            sb.append("\n\t\tPut Latency: " + stats.details.maps.get(key).putLatency);
        }

        SessionStats sess = mapper.convertValue(health.getDetails().get("session"), SessionStats.class);
        sb.append("\n\n\tSessions: " + sess.details.getMessage());
        sb.append(" - " + sess.details.sessionCount + " sessions. ");
        sb.append(sess.details.ticketCount + " service tickets. ");
        sb.append(sess.details.userCount + " unique users.");

        MemoryStats memStats = mapper.convertValue(health.getDetails().get("memory"), MemoryStats.class);
        sb.append("\n\n\tMemory: " + memStats.status);
        sb.append(" - " + formatMemory(memStats.details.freeMemory) + " free, ");
        sb.append(formatMemory(memStats.details.totalMemory) + " total.");

        LdapStats ldapStats = mapper.convertValue(health.getDetails().get("pooledLdapConnectionFactory"), LdapStats.class);
        sb.append("\n\n\tLDAP Pool: " + ldapStats.getDetails());
        sb.append(" - " + ldapStats.details.activeCount + " active, ");
        sb.append(ldapStats.details.idleCount + " idle.");

        sb.append("\n\nHost:\t\t").append(
            StringUtils.isBlank(casProperties.getHost().getName())
                ? InetAddressUtils.getCasServerHostName()
                : casProperties.getHost().getName()
        );
        sb.append("\nServer:\t\t").append(casProperties.getServer().getName());
        sb.append("\nVersion:\t").append(CasVersion.getVersion()).append("\n");
        //writeResponse(response, sb);
        return sb.toString();
    }

    private void writeResponse(final HttpServletResponse response, final StringBuilder sb) throws Exception {
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        try (Writer writer = response.getWriter()) {
            IOUtils.copy(new ByteArrayInputStream(sb.toString().getBytes(response.getCharacterEncoding())),
                    writer,
                    StandardCharsets.UTF_8);
            writer.flush();
        }
    }

    @Getter
    @Setter
    private static class HzStats {
        private String status;
        private Details details;
        @Getter
        @Setter
        private static class Details {
            private boolean master;
            private int clusterSize;
            private String message;
            private Map<String, HzMap> maps;
        }
    }

    @Getter
    @Setter
    private static class HzMap {
        private int size;
        private int percentFree;
        private long evictions;
        private long capacity;
        private long localCount;
        private long backupCount;
        private long getLatency;
        private long putLatency;
        private long memory;
    }

    @Getter
    @Setter
    private static class SessionStats {
        private String status;
        private Details details;
        @Getter
        @Setter
        private static class Details {
            private int sessionCount;
            private int userCount;
            private int ticketCount;
            private String message;
        }
    }

    @Getter
    @Setter
    private static class MemoryStats {
        private String status;
        private Details details;
        @Getter
        @Setter
        private static class Details {
            private long freeMemory;
            private long totalMemory;
        }
    }

    @Getter
    @Setter
    private static class LdapStats {
        private String status;
        private Details details;
        @Getter
        @Setter
        private static class Details {
            private String message;
            private int activeCount;
            private int idleCount;
        }
    }

    public String formatMemory(long mem) {
        if (mem < BYTEST_PER_KB) {
            return String.format(mem+"B");
        }
        if (mem < BYTES_PER_MB) {
            return String.format("%.2fKB", mem / BYTEST_PER_KB);
        }
        return String.format("%.2fMB", mem / BYTES_PER_MB);
    }
}
