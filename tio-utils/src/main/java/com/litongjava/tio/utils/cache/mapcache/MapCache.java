package com.litongjava.tio.utils.cache.mapcache;

import java.io.Serializable;

import com.litongjava.tio.utils.cache.AbsCache;

public class MapCache extends AbsCache {

  public MapCache(String cacheName) {
    super(cacheName);
  }

  @Override
  public void clear() {

  }

  @Override
  public Iterable<String> keys() {
    return null;
  }

  @Override
  public void put(String key, Serializable value) {
    // TODO Auto-generated method stub

  }

  @Override
  public void remove(String key) {

  }

  @Override
  public void putTemporary(String key, Serializable value) {

  }

  @Override
  public long ttl(String key) {
    return 0;
  }

  @Override
  public Serializable _get(String key) {
    return null;
  }

  public static AbsCache register(String cacheName, Long timeToLiveSeconds, Long timeToIdleSeconds, Object object) {
    return null;
  }
}
