package com.litongjava.tio.core.maintain;

import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.utils.lock.MapWithLock;

/**
 *一对一  (ip:port <--> ChannelContext)<br>
 * @author tanyaowu
 * 2017年4月1日 上午9:35:20
 */
public class ClientNodes {
  private static final Logger log = LoggerFactory.getLogger(ClientNodes.class);

  /** remoteAndChannelContext key: "ip:port" value: ChannelContext. */
  private MapWithLock<Node, ChannelContext> mapWithLock = new MapWithLock<>();

  /**
   *
   * @param key
   * @return
   * @author tanyaowu
   */
  public ChannelContext find(Node key) {
    Lock lock = mapWithLock.readLock();
    lock.lock();
    try {
      Map<Node, ChannelContext> m = mapWithLock.getObj();
      return m.get(key);
    } finally {
      lock.unlock();
    }
  }

  /**
   *
   * @param ip
   * @param port
   * @return
   * @author tanyaowu
   */
  public ChannelContext find(String ip, int port) {
    return find(new Node(ip, port));
  }

  /**
   *
   * @return
   * @author tanyaowu
   */
  public MapWithLock<Node, ChannelContext> getObjWithLock() {
    return mapWithLock;
  }

  /**
   * 添加映射
   * @param channelContext
   * @author tanyaowu
   */
  public void put(ChannelContext channelContext) {
    if (channelContext.tioConfig.isShortConnection) {
      return;
    }
    try {
      Node clientNode = channelContext.getClientNode();
      mapWithLock.put(clientNode, channelContext);
    } catch (Exception e) {
      log.error("Excpetion:{}", e);
    }
  }

  /**
   * Removes映射
   * @param channelContext
   * @author tanyaowu
   */
  public void remove(ChannelContext channelContext) {
    if (channelContext.tioConfig.isShortConnection) {
      return;
    }
    try {
      Node node = channelContext.getClientNode();
      mapWithLock.remove(node);
    } catch (Throwable e) {
      log.error(e.toString(), e);
    }
  }
}
