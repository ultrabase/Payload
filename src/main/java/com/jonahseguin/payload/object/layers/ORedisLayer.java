package com.jonahseguin.payload.object.layers;

import com.jonahseguin.payload.common.cache.CacheDatabase;
import com.jonahseguin.payload.common.exception.CachingException;
import com.jonahseguin.payload.object.cache.PayloadObjectCache;
import com.jonahseguin.payload.object.obj.ObjectCacheable;
import com.jonahseguin.payload.object.type.OLayerType;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSONParseException;
import redis.clients.jedis.Jedis;

public class ORedisLayer<X extends ObjectCacheable> extends ObjectCacheLayer<X> {

    private Jedis jedis = null;
    private final Class<X> clazz;

    public ORedisLayer(PayloadObjectCache<X> cache, CacheDatabase database, Class<X> clazz) {
        super(cache, database);
        this.clazz = clazz;
    }

    @Override
    public X provide(String id) {
        if (!cache.getSettings().isUseRedis()) {
            throw new CachingException("Cannot use Redis layer when useRedis is disabled!");
        }
        String json = jedis.get(cache.getSettings().getRedisKey() + id);
        if (json != null) {
            return mapObject(json);
        }
        else {
            return null;
        }
    }

    @Override
    public boolean save(String id, X x) {
        if (!cache.getSettings().isUseRedis()) {
            throw new CachingException("Cannot use Redis layer when useRedis is disabled!");
        }
        if (!x.persist()) return false;
        String json = jsonifyObject(x);
        jedis.set(cache.getSettings().getRedisKey() + id, json);
        return true;
    }

    @Override
    public boolean has(String id) {
        if (!cache.getSettings().isUseRedis()) {
            throw new CachingException("Cannot use Redis layer when useRedis is disabled!");
        }
        return jedis.exists(cache.getSettings().getRedisKey() + id);
    }

    @Override
    public boolean remove(String id) {
        if (!cache.getSettings().isUseRedis()) {
            throw new CachingException("Cannot use Redis layer when useRedis is disabled!");
        }
        jedis.del(cache.getSettings().getRedisKey() + id);
        return true;
    }

    @Override
    public boolean init() {
        if (!cache.getSettings().isUseRedis()) {
            throw new CachingException("Cannot use Redis layer when useRedis is disabled!");
        }
        try {
            jedis = database.getJedis();
            return true;
        }
        catch (Exception ex) {
            error(ex);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        if (!cache.getSettings().isUseRedis()) {
            throw new CachingException("Cannot use Redis layer when useRedis is disabled!");
        }
        if (jedis != null) {
            jedis.close();
            jedis = null;
        }
        return true;
    }

    @Override
    public OLayerType source() {
        return OLayerType.REDIS;
    }

    @Override
    public int cleanup() {
        return 0;
    }

    @Override
    public int clear() {
        return 0;
    }

    private String jsonifyObject(X obj) {
        try {
            BasicDBObject dbObject = (BasicDBObject) database.getMorphia().toDBObject(obj);
            return dbObject.toJson();
        } catch (JSONParseException ex) {
            super.getCache().getDebugger().error(ex, "Could not parse JSON while trying to convert Object to JSON for Redis");
        } catch (Exception ex) {
            super.getCache().getDebugger().error(ex, "Could not convert Object to JSON for Redis");
        }
        return null;
    }

    private X mapObject(String json) {
        try {
            DBObject dbObject = BasicDBObject.parse(json);
            X obj = database.getMorphia().fromDBObject(database.getDatastore(), clazz, dbObject);
            if (obj != null) {
                return obj;
            } else {
                throw new CachingException("Object to map cannot be null");
            }
        } catch (JSONParseException ex) {
            super.getCache().getDebugger().error(ex, "Could not parse JSON to map Object from Redis");
        } catch (Exception ex) {
            super.getCache().getDebugger().error(ex, "Could not map Object from Redis");
        }
        return null;
    }

}
