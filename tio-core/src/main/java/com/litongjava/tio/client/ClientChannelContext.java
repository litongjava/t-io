package com.litongjava.tio.client;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.TioConfig;

/**
 *
 * @author tanyaowu
 * 2017年4月1日 上午9:31:16
 */
public class ClientChannelContext extends ChannelContext {

  private String bindIp;

  private Integer bindPort;

  private ReconnRunnable reconnRunnable;

  /**
   * 连续重连次数，连接成功后，此值会被重置0
   */
  private AtomicInteger reconnCount = new AtomicInteger();

  /**
   * @param tioConfig
   * @param asynchronousSocketChannel
   *
   * @author tanyaowu
   *
   */
  public ClientChannelContext(TioConfig tioConfig, AsynchronousSocketChannel asynchronousSocketChannel) {
    super(tioConfig, asynchronousSocketChannel);
  }

  /**
   * 创建一个虚拟ChannelContext，主要用来模拟一些操作，真实场景中用得少
   * @param tioConfig
   */
  public ClientChannelContext(TioConfig tioConfig) {
    super(tioConfig);
  }

  /**
   * @return the bindIp
   */
  public String getBindIp() {
    return bindIp;
  }

  /**
   * @return the bindPort
   */
  public Integer getBindPort() {
    return bindPort;
  }

  /**
   * @param bindIp the bindIp to set
   */
  public void setBindIp(String bindIp) {
    this.bindIp = bindIp;
  }

  /**
   * @param bindPort the bindPort to set
   */
  public void setBindPort(Integer bindPort) {
    this.bindPort = bindPort;
  }

  /** 
   * @return
   * @author tanyaowu
   */
  @Override
  public boolean isServer() {
    return false;
  }

  public ReconnRunnable getReconnRunnable() {
    return reconnRunnable;
  }

  public void setReconnRunnable(ReconnRunnable reconnRunnable) {
    this.reconnRunnable = reconnRunnable;
  }

  public AtomicInteger getReconnCount() {
    return reconnCount;
  }

  public void setReconnCount(AtomicInteger reconnCount) {
    this.reconnCount = reconnCount;
  }

}
