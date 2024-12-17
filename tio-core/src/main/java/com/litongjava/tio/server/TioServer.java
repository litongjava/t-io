package com.litongjava.tio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.utils.Threads;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.StrUtil;

/**
 * @author tanyaowu
 *
 */
public class TioServer {
  private static Logger log = LoggerFactory.getLogger(TioServer.class);
  private ServerTioConfig serverTioConfig;
  private AsynchronousServerSocketChannel serverSocketChannel;
  private Node serverNode;
  private boolean isWaitingStop = false;
  private boolean checkLastVersion = true;
  private static ExecutorService groupExecutor;
  private static AsynchronousChannelGroup channelGroup;

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
    serverTioConfig.init();
    serverTioConfig.getCacheFactory().register(TioCoreConfigKeys.REQEUST_PROCESSING, null, null, null);

    this.serverNode = new Node(serverIp, serverPort);
    if (EnvUtils.getBoolean("tio.core.hotswap.reload", false)) {
      groupExecutor = Threads.getGroupExecutor();
      channelGroup = AsynchronousChannelGroup.withThreadPool(groupExecutor);
      serverSocketChannel = AsynchronousServerSocketChannel.open(channelGroup);
    } else {
      serverSocketChannel = AsynchronousServerSocketChannel.open();
    }

    serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    serverSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);

    InetSocketAddress listenAddress = null;

    if (StrUtil.isBlank(serverIp)) {
      listenAddress = new InetSocketAddress(serverPort);
    } else {
      listenAddress = new InetSocketAddress(serverIp, serverPort);
    }

    serverSocketChannel.bind(listenAddress, 0);

    AcceptCompletionHandler acceptCompletionHandler = new AcceptCompletionHandler();
    serverSocketChannel.accept(this, acceptCompletionHandler);

    serverTioConfig.startTime = System.currentTimeMillis();
    Threads.getTioExecutor();
  }

  /**
   * 
   * @return
   * @author tanyaowu
   */
  public boolean stop() {
    isWaitingStop = true;

    if (channelGroup != null) {
      try {
        channelGroup.shutdownNow();
      } catch (Exception e) {
        log.error("Faild to execute channelGroup.shutdownNow()", e);
      }
    }

    if (groupExecutor != null) {
      try {
        groupExecutor.shutdownNow();
      } catch (Exception e) {
        log.error("Failed to close groupExecutor", e);
      }

    }

    if (serverSocketChannel != null) {
      try {
        serverSocketChannel.close();
      } catch (Exception e) {
        log.error("Failed to close serverSocketChannel", e);
      }

    }

    serverTioConfig.setStopped(true);
    boolean ret = Threads.close();
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
