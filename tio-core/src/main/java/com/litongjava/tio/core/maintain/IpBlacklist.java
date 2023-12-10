package com.litongjava.tio.core.maintain;

import java.util.Collection;
import java.util.function.Consumer;

import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.server.ServerTioConfig;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.cache.caffeine.CaffeineCache;
import com.litongjava.tio.utils.time.Time;

/**
 *
 * @author tanyaowu
 * 2017年5月22日 下午2:53:47
 */
public class IpBlacklist {
  private String id;

  private final static String CACHE_NAME_PREFIX = "TIO_IP_BLACK_LIST";
  private final static Long TIME_TO_LIVE_SECONDS = Time.DAY_1 * 120;
  private final static Long TIME_TO_IDLE_SECONDS = null;
  private String cacheName = null;
  private CaffeineCache cache = null;
  private ServerTioConfig serverTioConfig;
  public final static IpBlacklist GLOBAL = new IpBlacklist();

  private IpBlacklist() {
    this.id = "__global__";
    this.cacheName = CACHE_NAME_PREFIX + this.id;
    this.cache = CaffeineCache.register(this.cacheName, TIME_TO_LIVE_SECONDS, TIME_TO_IDLE_SECONDS, null);
  }

  public IpBlacklist(String id, ServerTioConfig serverTioConfig) {
    this.id = id;
    this.serverTioConfig = serverTioConfig;
    this.cacheName = CACHE_NAME_PREFIX + this.id;
    this.cache = CaffeineCache.register(this.cacheName, TIME_TO_LIVE_SECONDS, TIME_TO_IDLE_SECONDS, null);
  }

  public boolean add(String ip) {
    // 先添加到黑名单列表
    cache.put(ip, SystemTimer.currTime);

    if (serverTioConfig != null) {
      // 删除相关连接
      Tio.remove(serverTioConfig, ip, "ip[" + ip + "]被加入了黑名单, " + serverTioConfig.getName());
    } else {
      TioConfig.ALL_SERVER_GROUPCONTEXTS.stream().forEach(new Consumer<ServerTioConfig>() {
        @Override
        public void accept(ServerTioConfig tioConfig) {
          Tio.remove(tioConfig, ip, "ip[" + ip + "]被加入了黑名单, " + tioConfig.getName());

        }
      });
    }

    return true;
  }

  public void clear() {
    cache.clear();
  }

  public Collection<String> getAll() {
    return cache.keys();
  }

  /**
   * 是否在黑名单中
   * @param ip
   * @return
   * @author tanyaowu
   */
  public boolean isInBlacklist(String ip) {
    return cache.get(ip) != null;
  }

  /**
   * 从黑名单中删除
   * @param ip
   * @return
   * @author: tanyaowu
   */
  public void remove(String ip) {
    cache.remove(ip);
  }
}
