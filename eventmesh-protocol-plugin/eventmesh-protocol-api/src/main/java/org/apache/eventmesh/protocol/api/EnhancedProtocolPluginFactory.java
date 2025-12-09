/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced Protocol plugin factory with performance optimizations and lifecycle management.
 *
 * @since 1.3.0
 */
@Slf4j
public class EnhancedProtocolPluginFactory {

    private static final Map<String, ProtocolAdaptor<ProtocolTransportObject>> PROTOCOL_ADAPTOR_MAP =
        new ConcurrentHashMap<>(32);
    
    private static final Map<String, ProtocolMetadata> PROTOCOL_METADATA_MAP = 
        new ConcurrentHashMap<>(32);
    
    private static final ReentrantReadWriteLock REGISTRY_LOCK = new ReentrantReadWriteLock();
    
    private static volatile boolean initialized = false;
    
    static {
        initializePlugins();
    }

    /**
     * Initialize all protocol plugins.
     */
    private static void initializePlugins() {
        if (initialized) {
            return;
        }
        
        REGISTRY_LOCK.writeLock().lock();
        try {
            if (initialized) {
                return;
            }
            
            log.info("Initializing protocol plugins...");
            
            // Protocol adaptors will be registered on-demand when first accessed
            log.debug("Enhanced protocol plugin factory initialized");
            
            initialized = true;
            log.info("Initialized {} protocol plugins", PROTOCOL_ADAPTOR_MAP.size());
            
        } finally {
            REGISTRY_LOCK.writeLock().unlock();
        }
    }

    /**
     * Register a protocol adaptor.
     *
     * @param adaptor protocol adaptor
     */
    @SuppressWarnings("unchecked")
    private static void registerProtocolAdaptor(ProtocolAdaptor adaptor) {
        try {
            String protocolType = adaptor.getProtocolType();
            if (protocolType == null || protocolType.trim().isEmpty()) {
                log.warn("Skip registering protocol adaptor with null or empty protocol type: {}", 
                    adaptor.getClass().getName());
                return;
            }
            
            // Initialize the adaptor
            adaptor.initialize();
            
            // Store adaptor
            PROTOCOL_ADAPTOR_MAP.put(protocolType, adaptor);
            
            // Store metadata
            ProtocolMetadata metadata = new ProtocolMetadata(
                protocolType,
                adaptor.getVersion(),
                adaptor.getPriority(),
                adaptor.supportsBatchProcessing(),
                adaptor.getCapabilities()
            );
            PROTOCOL_METADATA_MAP.put(protocolType, metadata);
            
            log.info("Registered protocol adaptor: {} (version: {}, priority: {})", 
                protocolType, adaptor.getVersion(), adaptor.getPriority());
                
        } catch (Exception e) {
            log.error("Failed to register protocol adaptor: {}", adaptor.getClass().getName(), e);
        }
    }

    /**
     * Get protocol adaptor by type.
     *
     * @param protocolType protocol type
     * @return protocol adaptor
     * @throws IllegalArgumentException if protocol not found
     */
    public static ProtocolAdaptor<ProtocolTransportObject> getProtocolAdaptor(String protocolType) {
        if (protocolType == null || protocolType.trim().isEmpty()) {
            throw new IllegalArgumentException("Protocol type cannot be null or empty");
        }
        
        REGISTRY_LOCK.readLock().lock();
        try {
            ProtocolAdaptor<ProtocolTransportObject> adaptor = PROTOCOL_ADAPTOR_MAP.get(protocolType);
            if (adaptor == null) {
                // Try lazy loading
                adaptor = loadProtocolAdaptor(protocolType);
                if (adaptor == null) {
                    throw new IllegalArgumentException(
                        String.format("Cannot find the Protocol adaptor: %s", protocolType));
                }
            }
            return adaptor;
        } finally {
            REGISTRY_LOCK.readLock().unlock();
        }
    }

    /**
     * Get protocol adaptor with fallback.
     *
     * @param protocolType primary protocol type
     * @param fallbackType fallback protocol type
     * @return protocol adaptor
     */
    public static ProtocolAdaptor<ProtocolTransportObject> getProtocolAdaptorWithFallback(
        String protocolType, String fallbackType) {
        try {
            return getProtocolAdaptor(protocolType);
        } catch (IllegalArgumentException e) {
            log.warn("Primary protocol {} not found, using fallback: {}", protocolType, fallbackType);
            return getProtocolAdaptor(fallbackType);
        }
    }

    /**
     * Get all available protocol types.
     *
     * @return list of protocol types
     */
    public static List<String> getAvailableProtocolTypes() {
        REGISTRY_LOCK.readLock().lock();
        try {
            return PROTOCOL_ADAPTOR_MAP.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
        } finally {
            REGISTRY_LOCK.readLock().unlock();
        }
    }

    /**
     * Get protocol adaptors sorted by priority.
     *
     * @return list of protocol adaptors ordered by priority (descending)
     */
    public static List<ProtocolAdaptor<ProtocolTransportObject>> getProtocolAdaptorsByPriority() {
        REGISTRY_LOCK.readLock().lock();
        try {
            return PROTOCOL_ADAPTOR_MAP.values().stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
        } finally {
            REGISTRY_LOCK.readLock().unlock();
        }
    }

    /**
     * Get protocol metadata.
     *
     * @param protocolType protocol type
     * @return protocol metadata or null if not found
     */
    public static ProtocolMetadata getProtocolMetadata(String protocolType) {
        REGISTRY_LOCK.readLock().lock();
        try {
            return PROTOCOL_METADATA_MAP.get(protocolType);
        } finally {
            REGISTRY_LOCK.readLock().unlock();
        }
    }

    /**
     * Check if protocol type is supported.
     *
     * @param protocolType protocol type
     * @return true if supported
     */
    public static boolean isProtocolSupported(String protocolType) {
        if (protocolType == null || protocolType.trim().isEmpty()) {
            return false;
        }
        
        REGISTRY_LOCK.readLock().lock();
        try {
            return PROTOCOL_ADAPTOR_MAP.containsKey(protocolType);
        } finally {
            REGISTRY_LOCK.readLock().unlock();
        }
    }

    /**
     * Get protocol adaptors by capability.
     *
     * @param capability required capability
     * @return list of protocol adaptors with the capability
     */
    public static List<ProtocolAdaptor<ProtocolTransportObject>> getProtocolAdaptorsByCapability(
        String capability) {
        if (capability == null || capability.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        REGISTRY_LOCK.readLock().lock();
        try {
            return PROTOCOL_ADAPTOR_MAP.values().stream()
                .filter(adaptor -> adaptor.getCapabilities().contains(capability))
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
        } finally {
            REGISTRY_LOCK.readLock().unlock();
        }
    }

    /**
     * Lazy load protocol adaptor.
     */
    @SuppressWarnings("unchecked")
    private static ProtocolAdaptor<ProtocolTransportObject> loadProtocolAdaptor(String protocolType) {
        REGISTRY_LOCK.writeLock().lock();
        try {
            // Double-check pattern
            ProtocolAdaptor<ProtocolTransportObject> adaptor = PROTOCOL_ADAPTOR_MAP.get(protocolType);
            if (adaptor != null) {
                return adaptor;
            }
            
            // Try to load from SPI
            adaptor = EventMeshExtensionFactory.getExtension(ProtocolAdaptor.class, protocolType);
            if (adaptor != null) {
                registerProtocolAdaptor(adaptor);
            }
            
            return adaptor;
        } finally {
            REGISTRY_LOCK.writeLock().unlock();
        }
    }

    /**
     * Shutdown all protocol adaptors.
     */
    public static void shutdown() {
        REGISTRY_LOCK.writeLock().lock();
        try {
            log.info("Shutting down protocol plugins...");
            
            for (ProtocolAdaptor<ProtocolTransportObject> adaptor : PROTOCOL_ADAPTOR_MAP.values()) {
                try {
                    adaptor.destroy();
                } catch (Exception e) {
                    log.warn("Error destroying protocol adaptor: {}", adaptor.getProtocolType(), e);
                }
            }
            
            PROTOCOL_ADAPTOR_MAP.clear();
            PROTOCOL_METADATA_MAP.clear();
            initialized = false;
            
            log.info("Protocol plugins shutdown completed");
        } finally {
            REGISTRY_LOCK.writeLock().unlock();
        }
    }

    /**
     * Protocol metadata holder.
     */
    public static class ProtocolMetadata {
        private final String type;
        private final String version;
        private final int priority;
        private final boolean supportsBatch;
        private final java.util.Set<String> capabilities;

        public ProtocolMetadata(String type, String version, int priority, 
                               boolean supportsBatch, java.util.Set<String> capabilities) {
            this.type = type;
            this.version = version;
            this.priority = priority;
            this.supportsBatch = supportsBatch;
            this.capabilities = capabilities != null ? capabilities : Collections.emptySet();
        }

        public String getType() { 
            return type; 
        }

        public String getVersion() { 
            return version; 
        }

        public int getPriority() { 
            return priority; 
        }

        public boolean supportsBatch() { 
            return supportsBatch; 
        }

        public java.util.Set<String> getCapabilities() { 
            return capabilities; 
        }

        @Override
        public String toString() {
            return String.format("ProtocolMetadata{type='%s', version='%s', priority=%d, batch=%s}", 
                type, version, priority, supportsBatch);
        }
    }
}