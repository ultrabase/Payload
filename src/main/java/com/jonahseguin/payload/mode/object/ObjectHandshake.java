/*
 * Copyright (c) 2019 Jonah Seguin.  All rights reserved.  You may not modify, decompile, distribute or use any code/text contained in this document(plugin) without explicit signed permission from Jonah Seguin.
 * www.jonahseguin.com
 */

package com.jonahseguin.payload.mode.object;

import com.google.common.base.Preconditions;
import com.google.inject.Injector;
import com.jonahseguin.payload.base.handshake.Handshake;
import com.jonahseguin.payload.base.handshake.HandshakeData;

import javax.annotation.Nonnull;
import java.util.Optional;

public class ObjectHandshake extends Handshake {

    public static final String KEY_IDENTIFIER = "identifier";
    private final ObjectCache cache;
    private String identifier = null;

    public ObjectHandshake(Injector injector, @Nonnull ObjectCache cache) {
        super(injector);
        Preconditions.checkNotNull(cache);
        this.cache = cache;
    }

    public ObjectHandshake(Injector injector, @Nonnull ObjectCache cache, @Nonnull String identifier) {
        this(injector, cache);
        Preconditions.checkNotNull(identifier);
        this.identifier = identifier;
    }

    @Override
    public ObjectHandshake create() {
        return new ObjectHandshake(injector, cache);
    }

    @Override
    public String channelPublish() {
        return "payload-object-handshake-" + cache.getName();
    }

    @Override
    public String channelReply() {
        return "payload-object-handshake-" + cache.getName() + "-reply";
    }

    @Override
    public void load(@Nonnull HandshakeData data) {
        this.identifier = data.getDocument().getString(KEY_IDENTIFIER);
    }

    @Override
    public void write(@Nonnull HandshakeData data) {
        data.append(KEY_IDENTIFIER, identifier);
    }

    @Override
    public void receive() {
        Optional<PayloadObject> o = cache.getFromCache(identifier);
        if (o.isPresent()) {
            PayloadObject object = o.get();
            object.setHandshakeStartTimestamp(System.currentTimeMillis());
            if (!cache.save(object)) {
                cache.getErrorService().capture("Failed to save during handshake for object " + object.getIdentifier());
            }
        }
    }

    @Override
    public boolean shouldAccept() {
        Optional<PayloadObject> o = cache.getLocalStore().get(identifier);
        if (o.isPresent()) {
            PayloadObject object = o.get();
            Optional<NetworkObject> ono = cache.getNetworked(object);
            if (ono.isPresent()) {
                NetworkObject no = ono.get();
                return no.isThisMostRelevantServer();
            }
        }
        return false;
    }
}
