package org.apereo.cas.web.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.util.CasVersion;
import org.apereo.cas.util.InetAddressUtils;
import org.apereo.cas.web.BaseCasMvcEndpoint;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;


/**
 * Reports overall CAS health based on the observations of the configured {@link HealthEndpoint} instance.
 *
 * @author Marvin S. Addison
 * @since 3.5
 */
@Slf4j
public class StatusController extends BaseCasMvcEndpoint {
    private final HealthEndpoint healthEndpoint;

    private static final int PERCENTAGE_VALUE = 100;
    private static final double BYTES_PER_MB = 1048510.0;
    private static final double BYTEST_PER_KB = 1024.0;
    private static Status status;
    private static Health health;

    public StatusController(final CasConfigurationProperties casProperties, final HealthEndpoint healthEndpoint) {
        super("status", StringUtils.EMPTY, casProperties.getMonitor().getEndpoints().getStatus(), casProperties);
        this.healthEndpoint = healthEndpoint;
        StatusController.status = Status.UP;
        checkHealth();
    }

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT15S")
    protected void checkHealth() {
        if ("MAINTENANCE".equals(StatusController.status.getCode())) {
            LOGGER.debug("Server is in Maintenance and not accepting traffic");
            return;
        }
        LOGGER.debug("Performing health check");
        StatusController.health = this.healthEndpoint.invoke();
        StatusController.status = health.getStatus();
    }

    /**
     * Handle request.
     *
     * @param request  the request
     * @param response the response
     * @throws Exception the exception
     */
    @GetMapping
    @ResponseBody
    protected void handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        ensureEndpointAccessIsAuthorized(request, response);
        if (request.getParameterMap().containsKey("maintenance") && request.getRemoteAddr().equals("127.0.0.1")) {
            if (request.getParameter("maintenance").equals("on")) {
                StatusController.status = new Status("MAINTENANCE");
            } else {
                StatusController.status = Status.UP;
            }
        }

        final StringBuilder sb = new StringBuilder();
        final Status status = StatusController.status;

        if (status.equals(Status.DOWN) || status.equals(Status.OUT_OF_SERVICE)) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }

        sb.append("Health: ").append(status.getCode());

        if (status.getCode().equals("MAINTENANCE")) {
            sb.append("\n");
            writeResponse(response, sb);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        final HzStats stats = mapper.convertValue(health.getDetails().get("hazelcast"), HzStats.class);

        sb.append("\n\n\tHazelcast: " + stats.getStatus());
        sb.append("\n\t  Members: " + stats.clusterSize);
        sb.append("\n\t  Is Master: " +  stats.isMaster());
        for(String key : stats.maps.keySet()) {
            sb.append("\n\t  Map: " + key);
            sb.append(" - Entries: " + stats.maps.get(key).size + " - Size: " + formatMemory(stats.maps.get(key).memory));
            sb.append(" using " + stats.maps.get(key).percentFree +"% of heap");
            sb.append("\n\t\tLocal Count: " + stats.maps.get(key).localCount);
            sb.append("\n\t\tBackup Count: " + stats.maps.get(key).backupCount);
            sb.append("\n\t\tGet Latency: " + stats.maps.get(key).getLatency);
            sb.append("\n\t\tPut Latency: " + stats.maps.get(key).putLatency);
        }

        SessionStats sess = mapper.convertValue(health.getDetails().get("session"), SessionStats.class);
        sb.append("\n\n\tSessions: " + sess.message);
        sb.append(" - " + sess.sessionCount + " sessions. ");
        sb.append(sess.ticketCount + " service tickets. ");
        sb.append(sess.userCount + " unique users.");

        MemoryStats memStats = mapper.convertValue(health.getDetails().get("memory"), MemoryStats.class);
        sb.append("\n\n\tMemory: " + memStats.status);
        sb.append(" - " + formatMemory(memStats.freeMemory) + " free, ");
        sb.append(formatMemory(memStats.totalMemory) + " total.");

        LdapStats ldapStats = mapper.convertValue(health.getDetails().get("pooledLdapConnectionFactory"), LdapStats.class);
        sb.append("\n\n\tLDAP Pool: " + ldapStats.message);
        sb.append(" - " + ldapStats.activeCount + " active, ");
        sb.append(ldapStats.idleCount + " idle.");

        sb.append("\n\nHost:\t\t").append(
            StringUtils.isBlank(casProperties.getHost().getName())
                ? InetAddressUtils.getCasServerHostName()
                : casProperties.getHost().getName()
        );
        sb.append("\nServer:\t\t").append(casProperties.getServer().getName());
        sb.append("\nVersion:\t").append(CasVersion.getVersion()).append("\n");
        writeResponse(response, sb);
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

    private static class HzStats {
        private String status;
        private boolean master;
        private int clusterSize;
        private Map<String, HzMap> maps;

        public HzStats() {

        }

        public boolean isMaster() {
            return master;
        }

        public void setMaster(boolean master) {
            this.master = master;
        }

        public int getClusterSize() {
            return clusterSize;
        }

        public void setClusterSize(int clusterSize) {
            this.clusterSize = clusterSize;
        }

        public Map<String, HzMap> getMaps() {
            return maps;
        }

        public void setMaps(Map<String, HzMap> maps) {
            this.maps = maps;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

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

        public HzMap() {

        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getPercentFree() {
            return percentFree;
        }

        public void setPercentFree(int percentFree) {
            this.percentFree = percentFree;
        }

        public long getEvictions() {
            return evictions;
        }

        public void setEvictions(int evictions) {
            this.evictions = evictions;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getLocalCount() {
            return localCount;
        }

        public void setLocalCount(long localCount) {
            this.localCount = localCount;
        }

        public long getBackupCount() {
            return backupCount;
        }

        public void setBackupCount(long backupCount) {
            this.backupCount = backupCount;
        }

        public long getGetLatency() {
            return getLatency;
        }

        public void setGetLatency(long getLatency) {
            this.getLatency = getLatency;
        }

        public long getPutLatency() {
            return putLatency;
        }

        public void setPutLatency(long putLatency) {
            this.putLatency = putLatency;
        }

        public long getMemory() {
            return memory;
        }

        public void setMemory(long memory) {
            this.memory = memory;
        }
    }

    private static class SessionStats {
        private String status;
        private int sessionCount;
        private int userCount;
        private int ticketCount;
        private String message;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getSessionCount() {
            return sessionCount;
        }

        public void setSessionCount(int sessionCount) {
            this.sessionCount = sessionCount;
        }

        public int getUserCount() {
            return userCount;
        }

        public void setUserCount(int userCount) {
            this.userCount = userCount;
        }

        public int getTicketCount() {
            return ticketCount;
        }

        public void setTicketCount(int ticketCount) {
            this.ticketCount = ticketCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    private static class MemoryStats {
        private String status;
        private long freeMemory;
        private long totalMemory;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getFreeMemory() {
            return freeMemory;
        }

        public void setFreeMemory(long freeMemory) {
            this.freeMemory = freeMemory;
        }

        public long getTotalMemory() {
            return totalMemory;
        }

        public void setTotalMemory(long totalMemory) {
            this.totalMemory = totalMemory;
        }
    }

    private static class LdapStats {
        private String status;
        private String message;
        private int activeCount;
        private int idleCount;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public void setActiveCount(int activeCount) {
            this.activeCount = activeCount;
        }

        public int getIdleCount() {
            return idleCount;
        }

        public void setIdleCount(int idleCount) {
            this.idleCount = idleCount;
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
