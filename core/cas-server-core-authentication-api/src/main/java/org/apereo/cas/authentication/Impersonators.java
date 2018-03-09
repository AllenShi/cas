package org.apereo.cas.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Created by tschmidt on 6/22/16.
 */
public class Impersonators {

    private HashMap<String,List<String>> map = new HashMap<>();

    private static Thread watcher;
    private static long lastModified;
    private boolean allowed;
    private String impFile;

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public void destroy() {
        if(watcher != null) {
            watcher.interrupt();
        }
    }

    public Impersonators(final boolean allowed, final String impFile) {
        this.allowed = allowed;
        if(allowed) {
            this.impFile = impFile;
            readFile();
            setWatcher();
        }
    }

    public boolean canImpersonate(String user, String service) throws Exception {
         return allowed &&
                !map.isEmpty() &&
                map.containsKey(user) &&
                map.get(user).contains(service);
    }

    private void readFile() {
        try {
            File file = new File(impFile);
            if (lastModified != file.lastModified()) {
                map = new HashMap<>();
                loadMap(getJson(file));
                lastModified = file.lastModified();
            }
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    private JsonNode getJson(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (Stream<String> stream = Files.lines(file.toPath())) {
            stream.forEach(l -> builder.append(l));
        } catch (final Exception e) {
            throw e;
        }
        return MAPPER.readTree(builder.toString());
    }

    private void loadMap(JsonNode json) throws Exception {
        Iterator<JsonNode> it = json.elements();
        while(it.hasNext()) {
            JsonNode entry = it.next();
            ArrayList<String> services = new ArrayList<String>();
            JsonNode jServices = entry.get("services");
            Iterator<JsonNode> sIt = jServices.elements();
            while (sIt.hasNext()) {
                JsonNode service = sIt.next();
                services.add(service.textValue());
            }
            map.put(entry.get("user").textValue(), services);
        }
    }

    private void setWatcher() {
        if (watcher != null) {
            watcher.interrupt();
        }
        watcher = new Thread(new WatcherThread());
        watcher.start();
    }

    private class WatcherThread implements Runnable {

        WatchService watcher;

        @Override
        public void run() {
            try {
                registerWatcher();
                for (; ; ) {
                    WatchKey key = waitForKey();
                    checkEvent(key);
                    key.reset();
                }
            } catch (InterruptedException intE) {
                return;
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void registerWatcher() throws Exception {
            File file = new File(impFile).getParentFile();
            watcher = FileSystems.getDefault().newWatchService();
            file.toPath().register(watcher, ENTRY_MODIFY);
        }

        private WatchKey waitForKey() throws Exception {
            WatchKey key = watcher.take();
            if(Thread.interrupted()) {
                throw new InterruptedException();
            }
            return key;
        }

        private void checkEvent(WatchKey key) throws Exception {
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                if (filename.getFileName().toString().equals("impersonators.json")) {
                    readFile();
                }
            }
        }

    }
}
