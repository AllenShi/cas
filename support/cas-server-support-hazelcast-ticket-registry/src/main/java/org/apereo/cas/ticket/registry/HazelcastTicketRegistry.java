package org.apereo.cas.ticket.registry;

import com.hazelcast.core.EntryEvent;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.ticket.Ticket;
import org.apereo.cas.ticket.TicketCatalog;
import org.apereo.cas.ticket.TicketDefinition;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.springframework.beans.factory.DisposableBean;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
@RequiredArgsConstructor
public class HazelcastTicketRegistry extends AbstractTicketRegistry implements AutoCloseable, DisposableBean {
    private final HazelcastInstance hazelcastInstance;
    private final TicketCatalog ticketCatalog;
    private final long pageSize;

    private IMap<String, Ticket> tgts;
    private IMap<String, Set<String>> users;

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        //LOGGER.info("Setting up Hazelcast Ticket Registry instance [{}] with name [{}]", this.hazelcastInstance.getName(), tgts.getName());
        this.tgts = getTicketMapInstance("ticketGrantingTicketsCache");
        this.users = hazelcastInstance.getMap("users");

        /**
         * Add MapListeners to update user map
         */
        tgts.addLocalEntryListener(new EntryRemovedListener<String,Ticket>() {
            @Override
            public void entryRemoved(EntryEvent<String,Ticket> entryEvent) {
                val user = ((TicketGrantingTicket)entryEvent.getOldValue()).getAuthentication().getPrincipal().getId();
                removeTGTfromUser(user,entryEvent.getKey());
            }
        });
        tgts.addLocalEntryListener(new EntryExpiredListener<String,Ticket>() {
            @Override
            public void entryExpired(EntryEvent<String,Ticket> entryEvent) {
                val user = ((TicketGrantingTicket)entryEvent.getOldValue()).getAuthentication().getPrincipal().getId();
                removeTGTfromUser(user,entryEvent.getKey());
            }
        });
        tgts.addLocalEntryListener(new EntryAddedListener<String,Ticket>() {
            @Override
            public void entryAdded(EntryEvent<String, Ticket> entryEvent) {
                val user = ((TicketGrantingTicket)entryEvent.getValue()).getAuthentication().getPrincipal().getId();
                addTGTtoUser(user,entryEvent.getKey());
            }
        });
    }

    @Override
    public Ticket updateTicket(final Ticket ticket) {
        addTicket(ticket);
        return ticket;
    }

    @Override
    public void addTicket(final Ticket ticket) {
        val ttl = ticket.getExpirationPolicy().getTimeToLive();
        if (ttl < 0) {
            throw new IllegalArgumentException("The expiration policy of ticket " + ticket.getId() + "is set to use a negative ttl");
        }

        LOGGER.debug("Adding ticket [{}] with ttl [{}s]", ticket.getId(), ttl);
        val encTicket = encodeTicket(ticket);

        val metadata = this.ticketCatalog.find(ticket);
        val ticketMap = getTicketMapInstanceByMetadata(metadata);

        ticketMap.set(encTicket.getId(), encTicket, ttl, TimeUnit.SECONDS);
        LOGGER.debug("Added ticket [{}] with ttl [{}s]", encTicket.getId(), ttl);
    }

    private IMap<String, Ticket> getTicketMapInstanceByMetadata(final TicketDefinition metadata) {
        val mapName = metadata.getProperties().getStorageName();
        LOGGER.debug("Locating map name [{}] for ticket definition [{}]", mapName, metadata);
        return getTicketMapInstance(mapName);
    }

    @Override
    public Ticket getTicket(final String ticketId, final Predicate<Ticket> predicate) {
        val encTicketId = encodeTicketId(ticketId);
        if (StringUtils.isBlank(encTicketId)) {
            return null;
        }
        val metadata = this.ticketCatalog.find(ticketId);
        if (metadata != null) {
            val map = getTicketMapInstanceByMetadata(metadata);
            val ticket = map.get(encTicketId);
            val result = decodeTicket(ticket);
            if (predicate.test(result)) {
                return result;
            }
            return null;
        }
        LOGGER.warn("No ticket definition could be found in the catalog to match [{}]", ticketId);
        return null;
    }

    @Override
    public boolean deleteSingleTicket(final String ticketIdToDelete) {
        val encTicketId = encodeTicketId(ticketIdToDelete);
        val metadata = this.ticketCatalog.find(ticketIdToDelete);
        val map = getTicketMapInstanceByMetadata(metadata);
        return map.remove(encTicketId) != null;
    }

    @Override
    public long deleteAll() {
        return this.ticketCatalog.findAll().stream()
            .map(this::getTicketMapInstanceByMetadata)
            .filter(Objects::nonNull)
            .mapToInt(instance -> {
                val size = instance.size();
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
    public Collection<? extends Ticket> getTickets() {
        /*
        return this.ticketCatalog.findAll()
            .stream()
            .map(metadata -> getTicketMapInstanceByMetadata(metadata).values())
            .flatMap(tickets -> {
                if (pageSize > 0) {
                    return tickets.stream().limit(pageSize).collect(Collectors.toList()).stream();
                }
                return new ArrayList<>(tickets).stream();
            })
            .map(this::decodeTicket)
            .collect(Collectors.toSet());
        */
        return Collections.EMPTY_LIST;
    }

    /**
     * Make sure we shutdown HazelCast when the context is destroyed.
     */
    public void shutdown() {
        try {
            LOGGER.info("Shutting down Hazelcast instance [{}]", this.hazelcastInstance.getConfig().getInstanceName());
            this.hazelcastInstance.shutdown();
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage());
        }
    }

    @Override
    public void destroy() {
        close();
    }

    @Override
    public void close() {
        shutdown();
    }

    private IMap<String, Ticket> getTicketMapInstance(final String mapName) {
        try {
            val inst = hazelcastInstance.<String, Ticket>getMap(mapName);
            LOGGER.debug("Located Hazelcast map instance [{}]", mapName);
            return inst;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    private void addTGTtoUser(String user, String ticketId) {
        val encodedUser = encodeTicketId(user);
        val encodedTicketId = encodeTicketId(ticketId);
        var tgtSet = this.users.get(encodedUser);
        if (tgtSet == null) {
            tgtSet = new HashSet<String>();
        }
        tgtSet.add(encodedTicketId);
        LOGGER.trace("Added tgt [{}] to user [{}], tgts size now [{}]",ticketId,user,tgtSet.size());
        this.users.set(encodedUser,tgtSet);
    }

    private void removeTGTfromUser(String user, String ticketId) {
        val encodedUser = encodeTicketId(user);
        val encodedTicketId = encodeTicketId(ticketId);
        val tgtSet = this.users.get(encodedUser);
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
                .limit(10)
                .flatMap(h -> h.stream().limit(100))
                .map(s -> getTicket(s))
                .collect(Collectors.toList());
    }

    @Override
    public void setCipherExecutor(CipherExecutor cipherExecutor) {
        super.setCipherExecutor(null);
    }
}
