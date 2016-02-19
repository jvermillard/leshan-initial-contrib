/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.server.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.leshan.server.security.NonUniqueSecurityInfoException;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.eclipse.leshan.server.security.SecurityRegistry;
import org.eclipse.leshan.util.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An in-memory security store.
 * <p>
 * This implementation serializes the registry content into a file to be able to re-load the security infos when the
 * server is restarted.
 * </p>
 */
public class SecurityRegistryImpl implements SecurityRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityRegistryImpl.class);

    // by client end-point
    private Map<String, SecurityInfo> securityByEp = new HashMap<>();

    // by PSK identity
    private Map<String, SecurityInfo> securityByIdentity = new HashMap<>();

    // the name of the file used to persist the registry content
    private final String filename;

    private PublicKey serverPublicKey;

    private PrivateKey serverPrivateKey;

    private X509Certificate[] serverX509CertChain;

    private Certificate[] trustedCertificates = null; // TODO retrieve certs from JRE trustStore ?

    // default location for persistence
    private static final String DEFAULT_FILE = "data/security.data";

    public SecurityRegistryImpl() {
        this(DEFAULT_FILE, null, null);
    }

    /**
     * Constructor for RPK
     */
    public SecurityRegistryImpl(PrivateKey serverPrivateKey, PublicKey serverPublicKey) {
        this(DEFAULT_FILE, serverPrivateKey, serverPublicKey);
    }

    /**
     * Constructor for X509 certificates
     */
    public SecurityRegistryImpl(PrivateKey serverPrivateKey, X509Certificate[] serverX509CertChain,
            Certificate[] trustedCertificates) {
        this(DEFAULT_FILE, serverPrivateKey, serverX509CertChain, trustedCertificates);
    }

    /**
     * @param file the file path to persist the registry
     */
    public SecurityRegistryImpl(String file, PrivateKey serverPrivateKey, PublicKey serverPublicKey) {
        Validate.notEmpty(file);

        filename = file;
        this.serverPrivateKey = serverPrivateKey;
        this.serverPublicKey = serverPublicKey;
        loadFromFile();
    }

    /**
     * @param file the file path to persist the registry
     */
    public SecurityRegistryImpl(String file, PrivateKey serverPrivateKey, X509Certificate[] serverX509CertChain,
            Certificate[] trustedCertificates) {
        Validate.notEmpty(file);
        Validate.notEmpty(serverX509CertChain);
        Validate.notEmpty(trustedCertificates);

        filename = file;
        this.serverPrivateKey = serverPrivateKey;
        this.serverX509CertChain = serverX509CertChain;
        this.trustedCertificates = trustedCertificates;
        loadFromFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized SecurityInfo getByEndpoint(String endpoint) {
        return securityByEp.get(endpoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized SecurityInfo getByIdentity(String identity) {
        return securityByIdentity.get(identity);
    }

    @Override
    public synchronized Collection<SecurityInfo> getAll() {
        return Collections.unmodifiableCollection(securityByEp.values());
    }

    private synchronized SecurityInfo addToRegistry(SecurityInfo info) throws NonUniqueSecurityInfoException {
        String identity = info.getIdentity();
        if (identity != null) {
            SecurityInfo infoByIdentity = securityByIdentity.get(info.getIdentity());
            if (infoByIdentity != null && !info.getEndpoint().equals(infoByIdentity.getEndpoint())) {
                throw new NonUniqueSecurityInfoException("PSK Identity " + info.getIdentity() + " is already used");
            }

            securityByIdentity.put(info.getIdentity(), info);
        }

        SecurityInfo previous = securityByEp.put(info.getEndpoint(), info);

        return previous;
    }

    @Override
    public synchronized SecurityInfo add(SecurityInfo info) throws NonUniqueSecurityInfoException {
        SecurityInfo previous = addToRegistry(info);
        saveToFile();

        return previous;
    }

    @Override
    public synchronized SecurityInfo remove(String endpoint) {
        SecurityInfo info = securityByEp.get(endpoint);
        if (info != null) {
            if (info.getIdentity() != null) {
                securityByIdentity.remove(info.getIdentity());
            }
            securityByEp.remove(endpoint);

            saveToFile();
        }
        return info;
    }

    protected void loadFromFile() {
        File file = new File(filename);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));) {
            SecurityInfo[] infos = (SecurityInfo[]) in.readObject();

            if (infos != null) {
                for (SecurityInfo info : infos) {
                    addToRegistry(info);
                }
                if (infos.length > 0) {
                    LOG.info("{} security infos loaded", infos.length);
                }
            }
        } catch (NonUniqueSecurityInfoException e) {
            LOG.error("Non unique security info detected", e);
        } catch (ClassNotFoundException e) {
            LOG.error("Class not found when reading from security info file", e);
        } catch (IOException e) {
            LOG.error("Could not load security infos from file", e);
        }
    }

    protected void saveToFile() {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                file.createNewFile();
            }
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename));) {
                out.writeObject(this.getAll().toArray(new SecurityInfo[0]));
            } catch (IOException e) {
                LOG.error("Could not save security infos to file", e);
            }
        } catch (IOException e) {
            LOG.error("Could not save security infos to file", e);
        }
    }

    @Override
    public PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    @Override
    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }

    @Override
    public X509Certificate[] getServerX509CertChain() {
        return serverX509CertChain;
    }

    @Override
    public Certificate[] getTrustedCertificates() {
        return trustedCertificates;
    }
}
