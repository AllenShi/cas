package org.apereo.cas.monitor;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.HazelcastInstanceProxy;
import com.hazelcast.memory.MemoryStats;
import com.hazelcast.monitor.LocalMapStats;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.core.monitor.MonitorWarningProperties;
import org.springframework.boot.actuate.health.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This is {@link HazelcastHealthIndicator}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
@ToString
public class HazelcastHealthIndicator extends AbstractCacheHealthIndicator {

    private static int clusterSize;

    /**
     * CAS Hazelcast Instance.
     */
    private final HazelcastInstanceProxy instance;

    public HazelcastHealthIndicator(final CasConfigurationProperties casProperties,
                                    final HazelcastInstance instance) {
        super(casProperties);
        this.instance = (HazelcastInstanceProxy) instance;
    }

    @Override
    protected CacheStatistics[] getStatistics() {
        final List<CacheStatistics> statsList = new ArrayList<>();
        getClusterSize(instance);
        final boolean isMaster = instance.getOriginal().node.isMaster();
        final MemoryStats memoryStats = instance.getOriginal().getMemoryStats();
        instance.getConfig().getMapConfigs().keySet().forEach(key -> {
            final IMap map = instance.getMap(key);
            LOGGER.debug("Starting to collect hazelcast statistics for map [{}] identified by key [{}]...", map, key);
            statsList.add(new HazelcastStatistics(map, clusterSize, isMaster, memoryStats));
        });
        return statsList.toArray(new CacheStatistics[0]);
    }

    private void getClusterSize(final HazelcastInstanceProxy instance) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> HazelcastHealthIndicator.clusterSize =
                instance.getOriginal().node.getClusterService().getSize()
        );
    }

    /**
     * The type Hazelcast statistics.
     */
    public static class HazelcastStatistics implements CacheStatistics {

        private static final int PERCENTAGE_VALUE = 100;
        private static final double BYTES_PER_MB = 1048510.0;
        private static final double BYTEST_PER_KB = 1024.0;

        private final IMap map;

        private final int clusterSize;
        private boolean isMaster;
        private MemoryStats memoryStats;

        protected HazelcastStatistics(final IMap map, final int clusterSize, final boolean isMaster, final MemoryStats memoryStats) {
            this.map = map;
            this.clusterSize = clusterSize;
            this.isMaster = isMaster;
            this.memoryStats = memoryStats;
        }

        @Override
        public long getSize() {
            return this.map.getLocalMapStats().getOwnedEntryCount() + this.map.getLocalMapStats().getBackupEntryCount();
        }

        @Override
        public long getCapacity() {
            return this.memoryStats.getCommittedHeap();
        }

        @Override
        public long getEvictions() {
            if (this.map.getLocalMapStats() != null && this.map.getLocalMapStats().getNearCacheStats() != null) {
                return this.map.getLocalMapStats().getNearCacheStats().getMisses();
            }
            return 0;
        }

        @Override
        public String getName() {
            return this.map.getName();
        }

        /**
         * Hijacked this method to instead return Percent of heap used.
         *
         * @return - Percent of heap.
         */
        @Override
        public long getPercentFree() {
            if (memoryStats.getCommittedHeap() > 0) {
                return map.getLocalMapStats().getHeapCost() * PERCENTAGE_VALUE / memoryStats.getCommittedHeap();
            }
            return -1;
        }

        @Override
        public int getNumberOfMembers() {
            return clusterSize;
        }

        @Override
        public boolean isMaster() {
            return isMaster;
        }

        @Override
        public long getMemoryCost() {
            return map.getLocalMapStats().getHeapCost();
        }

        @Override
        public long getLocalEntryCount() {
            return map.getLocalMapStats().getOwnedEntryCount();
        }

        @Override
        public long getBackupEntryCount() {
            return map.getLocalMapStats().getBackupEntryCount();
        }

        @Override
        public String toString(final StringBuilder builder) {
            final LocalMapStats localMapStats = map.getLocalMapStats();
             builder.append("\n\t  ")
                    .append("Map: "+map.getName())
                    .append(" - ")
                    .append("Size: ")
                    .append(formatMemory(localMapStats.getHeapCost()))
                    .append("\n\t    ")
                    .append("Local entry count: ")
                    .append(localMapStats.getOwnedEntryCount())
                    .append("\t    ")
                    .append("Local entry memory:")
                    .append(formatMemory(localMapStats.getOwnedEntryMemoryCost()))
                    .append("\n\t    ")
                    .append("Backup entry count: ")
                    .append(localMapStats.getBackupEntryCount())
                     .append("\t    ")
                     .append("Backup entry memory: ")
                     .append(formatMemory(localMapStats.getBackupEntryMemoryCost()))
                     .append("\n\t    ")
                    .append("Max get latency: ")
                    .append(localMapStats.getMaxGetLatency())
                     .append("\t\t    ")
                    .append("Max put latency: ")
                    .append(localMapStats.getMaxPutLatency());


            if (localMapStats.getNearCacheStats() != null) {
                builder.append(", Misses: ").append(localMapStats.getNearCacheStats().getMisses());
            }
            return builder.toString();
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            this.toString(builder);
            return builder.toString();
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

    @Override
    protected Status status(CacheStatistics statistics) {
        final MonitorWarningProperties warn = casProperties.getMonitor().getWarn();
        if (statistics.getEvictions() > 0 && statistics.getEvictions() > warn.getEvictionThreshold()) {
            return new Status("WARN");
        }
        return Status.UP;
    }
}
