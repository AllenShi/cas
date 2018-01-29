package org.apereo.cas.ticket.registry;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.ticket.Ticket;
import org.apereo.cas.ticket.TicketCatalog;
import org.apereo.cas.ticket.TicketDefinition;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Hazelcast-based implementation of a {@link TicketRegistry}.
 * <p>This implementation just wraps the Hazelcast's {@link IMap}
 * which is an extension of the standard Java's {@code ConcurrentMap}.</p>
 * <p>The heavy lifting of distributed data partitioning, network cluster discovery and
 * join, data replication, etc. is done by Hazelcast's Map implementation.</p>
 *
 * @author Dmitriy Kopylenko
 * @author Jonathan Johnson
 * @since 4.1.0
 */
@Slf4j
@AllArgsConstructor
public class HazelcastTicketRegistry extends AbstractTicketRegistry implements Closeable {
    private final HazelcastInstance hazelcastInstance;
    private final TicketCatalog ticketCatalog;
    private final long pageSize;
    private IMap<String, Ticket> tgts;
    private IMap<String, Set<String>> users;

    /**
     * Instantiates a new Hazelcast ticket ticketGrantingTicketsRegistry.
     *
     * @param hz       An instance of {@code HazelcastInstance}
     * @param plan     the plan
     * @param pageSize the page size
     */
    public HazelcastTicketRegistry(final HazelcastInstance hz, final TicketCatalog plan, final long pageSize) {
        this.hazelcastInstance = hz;
        this.pageSize = pageSize;
        this.ticketCatalog = plan;

        this.tgts = getTicketMapInstance("ticketGrantingTicketsCache");
        this.users = hazelcastInstance.getMap("users");

        /**
         * Add MapListeners to update user map
         */
        tgts.addLocalEntryListener(new EntryRemovedListener<String,Ticket>() {
            @Override
            public void entryRemoved(EntryEvent<String,Ticket> entryEvent) {
                String user = ((TicketGrantingTicket)entryEvent.getOldValue()).getAuthentication().getPrincipal().getId();
                removeTGTfromUser(user,entryEvent.getKey());
            }
        });
        tgts.addLocalEntryListener(new EntryExpiredListener<String,Ticket>() {
            @Override
            public void entryExpired(EntryEvent<String,Ticket> entryEvent) {
                String user = ((TicketGrantingTicket)entryEvent.getOldValue()).getAuthentication().getPrincipal().getId();
                removeTGTfromUser(user,entryEvent.getKey());
            }
        });
        tgts.addLocalEntryListener(new EntryAddedListener<String,Ticket>() {
            @Override
            public void entryAdded(EntryEvent<String, Ticket> entryEvent) {
                String user = ((TicketGrantingTicket)entryEvent.getValue()).getAuthentication().getPrincipal().getId();
                addTGTtoUser(user,entryEvent.getKey());
            }
        });

    }

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        LOGGER.info("Setting up Hazelcast Ticket Registry instance [{}] with name [{}]", this.hazelcastInstance.getName(), tgts.getName());

    }

    @Override
    public Ticket updateTicket(final Ticket ticket) {
        addTicket(ticket);
        return ticket;
    }

    @Override
    public void addTicket(final Ticket ticket) {
        final long ttl = ticket.getExpirationPolicy().getTimeToLive();
        if (ttl < 0) {
            throw new IllegalArgumentException("The expiration policy of ticket " + ticket.getId() + "is set to use a negative ttl");
        }

        LOGGER.debug("Adding ticket [{}] with ttl [{}s]", ticket.getId(), ttl);
        final Ticket encTicket = encodeTicket(ticket);

        final TicketDefinition metadata = this.ticketCatalog.find(ticket);
        final IMap<String, Ticket> ticketMap = getTicketMapInstanceByMetadata(metadata);

        ticketMap.set(encTicket.getId(), encTicket, ttl, TimeUnit.SECONDS);
        LOGGER.debug("Added ticket [{}] with ttl [{}s]", encTicket.getId(), ttl);
    }

    private IMap<String, Ticket> getTicketMapInstanceByMetadata(final TicketDefinition metadata) {
        final String mapName = metadata.getProperties().getStorageName();
        LOGGER.debug("Locating map name [{}] for ticket definition [{}]", mapName, metadata);
        return getTicketMapInstance(mapName);
    }

    @Override
    public Ticket getTicket(final String ticketId) {
        final String encTicketId = encodeTicketId(ticketId);
        if (StringUtils.isBlank(encTicketId)) {
            return null;
        }
        final TicketDefinition metadata = this.ticketCatalog.find(ticketId);
        if (metadata != null) {
            final IMap<String, Ticket> map = getTicketMapInstanceByMetadata(metadata);
            final Ticket ticket = map.get(encTicketId);
            final Ticket result = decodeTicket(ticket);
            if (result != null && result.isExpired()) {
                LOGGER.debug("Ticket [{}] has expired and is now removed from the cache", result.getId());
                map.remove(encTicketId);
                return null;
            }
            return result;
        }
        LOGGER.warn("No ticket definition could be found in the catalog to match [{}]", ticketId);
        return null;
    }

    @Override
    public boolean deleteSingleTicket(final String ticketIdToDelete) {
        final String encTicketId = encodeTicketId(ticketIdToDelete);
        final TicketDefinition metadata = this.ticketCatalog.find(ticketIdToDelete);
        final IMap<String, Ticket> map = getTicketMapInstanceByMetadata(metadata);
        return map.remove(encTicketId) != null;
    }

    @Override
    public long deleteAll() {
        return this.ticketCatalog.findAll().stream()
            .map(this::getTicketMapInstanceByMetadata)
            .filter(Objects::nonNull)
            .mapToInt(instance -> {
                final int size = instance.size();
                instance.evictAll();
                instance.clear();
                return size;
            })
            .sum();
    }

    @Override
    public long sessionCount() {
        return getTicketMapInstance("ticketGrantingTicketsCache").size();
    }

    @Override
    public long serviceTicketCount() {
        return getTicketMapInstance("serviceTicketsCache").size();
    }

    @Override
    public long userCount() {
        return this.users.size();
    }


    @Override
    public Collection<Ticket> getTickets() {
        /*
        final Collection<Ticket> tickets = new HashSet<>();
        try {
            final Collection<TicketDefinition> metadata = this.ticketCatalog.findAll();
            metadata.forEach(t -> {
                final IMap<String, Ticket> map = getTicketMapInstanceByMetadata(t);
                tickets.addAll(map.values().stream().limit(this.pageSize).collect(Collectors.toList()));
            });
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return decodeTickets(tickets);
        */
        return Collections.EMPTY_LIST;
    }

    /**
     * Make sure we shutdown HazelCast when the context is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        try {
            LOGGER.info("Shutting down Hazelcast instance [{}]", this.hazelcastInstance.getConfig().getInstanceName());
            this.hazelcastInstance.shutdown();
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage());
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private IMap<String, Ticket> getTicketMapInstance(final String mapName) {
        try {
            final IMap<String, Ticket> inst = hazelcastInstance.getMap(mapName);
            LOGGER.debug("Located Hazelcast map instance [{}]", mapName);
            return inst;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    private void addTGTtoUser(String user, String ticketId) {
        String encodedUser = encodeTicketId(user);
        String encodedTicketId = encodeTicketId(ticketId);
        Set<String> tgtSet = this.users.get(encodedUser);
        if (tgtSet == null) {
            tgtSet = new HashSet<>();
        }
        tgtSet.add(encodedTicketId);
        LOGGER.trace("Added tgt [{}] to user [{}], tgts size now [{}]",ticketId,user,tgtSet.size());
        this.users.set(encodedUser,tgtSet);
    }

    private void removeTGTfromUser(String user, String ticketId) {
        String encodedUser = encodeTicketId(user);
        String encodedTicketId = encodeTicketId(ticketId);
        Set<String> tgtSet = this.users.get(encodedUser);
        if(tgtSet != null && !tgtSet.isEmpty()) {
            tgtSet.remove(encodedTicketId);
            LOGGER.trace("Removed tgt [{}] from user [{}], tgt size now [{}]",ticketId,user,tgtSet.size());
            if (tgtSet.isEmpty()) {
                this.users.remove(encodedUser);
            } else {
                this.users.set(encodedUser,tgtSet);
            }
        }
    }

    @Override
    public Collection<Ticket> getTicketsByUser(String user){
        return users.values((k) -> ((String)k.getKey()).matches(user))
                .stream()
                .flatMap(h -> h.stream())
                .map(s -> getTicket(s))
                .collect(Collectors.toList());
    }
}
