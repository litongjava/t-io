package com.litongjava.tio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.utils.hutool.StrUtil;

/**
 * @author tanyaowu
 *
 */
public class TioServer {
  private static Logger log = LoggerFactory.getLogger(TioServer.class);
  private ServerTioConfig serverTioConfig;
  private AsynchronousServerSocketChannel serverSocketChannel;
  private AsynchronousChannelGroup channelGroup = null;
  private Node serverNode;
  private boolean isWaitingStop = false;
  private boolean checkLastVersion = true;

  /**
   *
   * @param serverTioConfig
   *
   * @author tanyaowu 2017年1月2日 下午5:53:06
   *
   */
  public TioServer(ServerTioConfig serverTioConfig) {
    super();
    this.serverTioConfig = serverTioConfig;
  }

  /**
   * @return the serverTioConfig
   */
  public ServerTioConfig getServerTioConfig() {
    return serverTioConfig;
  }

  /**
   * @return the serverNode
   */
  public Node getServerNode() {
    return serverNode;
  }

  /**
   * @return the serverSocketChannel
   */
  public AsynchronousServerSocketChannel getServerSocketChannel() {
    return serverSocketChannel;
  }

  /**
   * @return the isWaitingStop
   */
  public boolean isWaitingStop() {
    return isWaitingStop;
  }

  /**
   * @param serverTioConfig the serverTioConfig to set
   */
  public void setServerTioConfig(ServerTioConfig serverTioConfig) {
    this.serverTioConfig = serverTioConfig;
  }

  /**
   * @param isWaitingStop the isWaitingStop to set
   */
  public void setWaitingStop(boolean isWaitingStop) {
    this.isWaitingStop = isWaitingStop;
  }

  public void start(String serverIp, int serverPort) throws IOException {
    // long start = System.currentTimeMillis();
    serverTioConfig.getCacheFactory().register(TioCoreConfigKeys.REQEUST_PROCESSING, null, null, null);

    this.serverNode = new Node(serverIp, serverPort);
    // channelGroup = AsynchronousChannelGroup.withThreadPool(serverTioConfig.groupExecutor);
    // serverSocketChannel= AsynchronousServerSocketChannel.open(channelGroup);
    serverSocketChannel = AsynchronousServerSocketChannel.open();
    serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    serverSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);

    InetSocketAddress listenAddress = null;

    if (StrUtil.isBlank(serverIp)) {
      listenAddress = new InetSocketAddress(serverPort);
    } else {
      listenAddress = new InetSocketAddress(serverIp, serverPort);
    }

    serverSocketChannel.bind(listenAddress, 0);

    AcceptCompletionHandler acceptCompletionHandler = serverTioConfig.getAcceptCompletionHandler();
    serverSocketChannel.accept(this, acceptCompletionHandler);

    serverTioConfig.startTime = System.currentTimeMillis();
  }

  /**
   * 
   * @return
   * @author tanyaowu
   */
  public boolean stop() {
    isWaitingStop = true;
    boolean ret = true;

    try {
      channelGroup.shutdownNow();
    } catch (Exception e) {
      log.error("channelGroup.shutdownNow()时报错", e);
    }

    try {
      serverSocketChannel.close();
    } catch (Exception e1) {
      log.error("serverSocketChannel.close()时报错", e1);
    }

    try {
      serverTioConfig.groupExecutor.shutdown();
    } catch (Exception e1) {
      log.error(e1.toString(), e1);
    }
    try {
      serverTioConfig.tioExecutor.shutdown();
    } catch (Exception e1) {
      log.error(e1.toString(), e1);
    }

    serverTioConfig.setStopped(true);
    try {
      ret = ret && serverTioConfig.groupExecutor.awaitTermination(6000, TimeUnit.SECONDS);
      ret = ret && serverTioConfig.tioExecutor.awaitTermination(6000, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.error(e.getLocalizedMessage(), e);
    }

    log.info(this.serverNode + " stopped");
    return ret;
  }

  public boolean isCheckLastVersion() {
    return checkLastVersion;
  }

  public void setCheckLastVersion(boolean checkLastVersion) {
    log.debug("community edition is no longer supported");
    // this.checkLastVersion = checkLastVersion;
  }
}
