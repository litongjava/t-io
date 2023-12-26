package com.litongjava.tio.utils.cache.j2cache;

import com.litongjava.tio.utils.cache.AbsCache;
import com.litongjava.tio.utils.cache.CacheFactory;
import com.litongjava.tio.utils.cache.RemovalListenerWrapper;

public class J2CacheFactory implements CacheFactory{

  @Override
  public AbsCache register(String cacheName, Long timeToLiveSeconds, Long timeToIdleSeconds) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <T> AbsCache register(String cacheName, Long timeToLiveSeconds, Long timeToIdleSeconds,
      RemovalListenerWrapper<T> removalListenerWrapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AbsCache getCache(String cacheName, boolean skipNull) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AbsCache getCache(String cacheName) {
    // TODO Auto-generated method stub
    return null;
  }

}
