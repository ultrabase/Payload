package com.jonahseguin.payload.mode.profile.pubsub;

import com.jonahseguin.payload.PayloadAPI;
import com.jonahseguin.payload.mode.profile.PayloadProfile;
import com.jonahseguin.payload.mode.profile.PayloadProfileController;
import com.jonahseguin.payload.mode.profile.ProfileCache;
import org.bson.Document;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class HandshakeListener<X extends PayloadProfile> extends JedisPubSub {

    private final ProfileCache<X> cache;

    public HandshakeListener(ProfileCache<X> cache) {
        this.cache = cache;
    }

    @Override
    public void onMessage(String channel, String message) {
        HandshakeEvent event = fromChannel(channel);
        if (event != null) {
            // Confirmed it's a handshake event, make assumption the message is JSON (Document)
            // Message is json
            Document data;
            try {
                data = Document.parse(message);
            } catch (Exception ex) {
                this.cache.getErrorHandler().exception(this.cache, ex, "Failed to parse data for Redis Handshake event: " + ex.getMessage());
                return;
            }

            if (data != null) {
                final String sourcePayloadId = data.getString(HandshakeManager.SOURCE_PAYLOAD_ID);
                final String cacheName = data.getString(HandshakeManager.CACHE_NAME);
                final String uniqueId = data.getString(HandshakeManager.PLAYER_UUID); // Payload Profile's UUID
                final String targetPayloadID = data.getString(HandshakeManager.TARGET_PAYLOAD_ID);

                if (sourcePayloadId == null) {
                    this.cache.getErrorHandler().error(this.cache, "Missing field in Payload handshake: '" + HandshakeManager.SOURCE_PAYLOAD_ID + "'");
                    return;
                } else if (cacheName == null) {
                    this.cache.getErrorHandler().error(this.cache, "Missing field in Payload handshake: '" + HandshakeManager.CACHE_NAME + "'");
                    return;
                } else if (uniqueId == null) {
                    this.cache.getErrorHandler().error(this.cache, "Missing field in Payload handshake: '" + HandshakeManager.PLAYER_UUID + "'");
                    return;
                } else if (targetPayloadID == null) {
                    this.cache.getErrorHandler().error(this.cache, "Missing field in Payload handshake: '" + HandshakeManager.TARGET_PAYLOAD_ID + "'");
                    return;
                }

                if (cacheName.equalsIgnoreCase(this.cache.getName())) {
                    // If the cache is this cache
                    if (targetPayloadID.equalsIgnoreCase(PayloadAPI.get().getPayloadID())) {
                        // If the target is this server

                        // Convert our UUID to uuid object
                        final UUID uuid;
                        try {
                            uuid = UUID.fromString(uniqueId);
                        } catch (IllegalArgumentException ex) {
                            this.cache.getErrorHandler().exception(this.cache, ex, "Failed to parse UUID '" + uniqueId + "' for Redis Handshake event: " + ex.getMessage());
                            return;
                        }

                        // Process events..

                        final Document dataOut = new Document();
                        dataOut.append(HandshakeManager.SOURCE_PAYLOAD_ID, PayloadAPI.get().getPayloadID());
                        dataOut.append(HandshakeManager.CACHE_NAME, this.cache.getName());
                        dataOut.append(HandshakeManager.TARGET_PAYLOAD_ID, sourcePayloadId);
                        dataOut.append(HandshakeManager.PLAYER_UUID, uniqueId);

                        final String dataOutJson = dataOut.toJson();

                        if (event.equals(HandshakeEvent.REQUEST_PAYLOAD_SAVE)) {

                            // The sourcePayloadId server is requesting that this server save a profile for the uuid profile
                            // Check if we have them cached/if they are online
                            X target = null;
                            if (this.cache.getLocalLayer().has(uuid)) {
                                target = this.cache.getLocalLayer().get(uuid);
                            }

                            if (target != null && target.getPlayer() != null && target.getPlayer().isOnline()) {
                                // Save

                                final X finalTarget = target;
                                finalTarget.setSwitchingServers(true);
                                this.cache.getPublisherJedis().publish(HandshakeEvent.SAVING_PAYLOAD.getName(), dataOutJson);
                                this.cache.getPool().submit(() -> {
                                    this.cache.save(finalTarget);
                                    this.cache.getPublisherJedis().publish(HandshakeEvent.SAVED_PAYLOAD.getName(), dataOutJson);
                                });
                            } else {
                                // Don't have them, emit an event to let the server know
                                this.cache.getPublisherJedis().publish(HandshakeEvent.PAYLOAD_NOT_CACHED_CONTINUE.getName(), dataOut.toJson());
                            }

                        } else if (event.equals(HandshakeEvent.SAVING_PAYLOAD)) {

                            // The sourcePayloadId server is saving our payload that we requested to be saved.
                            // Stop the timeout
                            PayloadProfileController<X> controller = this.cache.getController(uuid);
                            if (controller != null) {
                                controller.setServerFound(true);
                                controller.setLoadFromServer(sourcePayloadId);
                            } else {
                                this.cache.getErrorHandler().debug(this.cache, "Controller was null during SAVING_PAYLOAD for uuid " + uniqueId);
                            }

                        } else if (event.equals(HandshakeEvent.SAVED_PAYLOAD)) {

                            // The sourcePayloadId server has finished saving our payload that we requested to be saved
                            // Continue with our caching process
                            PayloadProfileController<X> controller = this.cache.getController(uuid);
                            if (controller != null) {
                                controller.onHandshakeComplete();
                            } else {
                                this.cache.getErrorHandler().debug(this.cache, "Controller was null during SAVED_PAYLOAD for uuid " + uniqueId);
                            }

                        } else if (event.equals(HandshakeEvent.PAYLOAD_NOT_CACHED_CONTINUE)) {

                            // The sourcePayloadId server doesn't have the profile we requested they save
                            // Continue with our caching process
                            // instead of re-loading the object from the database like we would in the SAVED_PAYLOAD event handler,
                            // just use the object we already loaded to get the lastSeenServer field
                            PayloadProfileController<X> controller = this.cache.getController(uuid);
                            if (controller != null) {
                                controller.setAbortHandshakeNotCached(true);
                                controller.onHandshakeComplete();
                            } else {
                                this.cache.getErrorHandler().debug(this.cache, "Controller was null during SAVING_PAYLOAD for uuid " + uniqueId);
                            }

                        }
                    }
                }
            }
        }
    }

    private HandshakeEvent fromChannel(String channel) {
        return HandshakeEvent.fromString(channel);
    }

}