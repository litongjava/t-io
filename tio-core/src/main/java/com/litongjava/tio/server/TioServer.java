package com.litongjava.tio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.litongjava.enhance.channel.EnhanceAsynchronousChannelProvider;
import com.litongjava.enhance.channel.EnhanceAsynchronousServerSocketChannel;
import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.utils.Threads;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.StrUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * @author tanyaowu
 */
@Slf4j
public class TioServer {
  private ServerTioConfig serverTioConfig;
  private AsynchronousServerSocketChannel serverSocketChannel;
  private Node serverNode;
  private boolean isWaitingStop = false;
  private static ExecutorService groupExecutor;
  private static AsynchronousChannelGroup channelGroup;

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
      //serverSocketChannel = AsynchronousServerSocketChannel.open();
      EnhanceAsynchronousChannelProvider provider = new EnhanceAsynchronousChannelProvider(false);
      int threads = EnvUtils.getInt(TioCoreConfigKeys.TIO_CORE_THREADS,Runtime.getRuntime().availableProcessors() * 4);
      AtomicInteger threadNumber = new AtomicInteger(1);
      AsynchronousChannelGroup group = provider.openAsynchronousChannelGroup(threads, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          return new Thread(r, "t-io-" + threadNumber.getAndIncrement());
        }
      });

      // 使用提供者创建服务器通道
      serverSocketChannel = (EnhanceAsynchronousServerSocketChannel) provider.openAsynchronousServerSocketChannel(group);
    }

    serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    serverSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);

    InetSocketAddress listenAddress = null;

    if (StrUtil.isBlank(serverIp)) {
      listenAddress = new InetSocketAddress(serverPort);
    } else {
      listenAddress = new InetSocketAddress(serverIp, serverPort);
    }

    serverSocketChannel.bind(listenAddress, serverTioConfig.getBacklog());

    AcceptCompletionHandler acceptCompletionHandler = new AcceptCompletionHandler();
    serverSocketChannel.accept(this, acceptCompletionHandler);

    serverTioConfig.startTime = System.currentTimeMillis();
    Threads.getTioExecutor();
  }

  /**
   * 
   * @return`
   * @author tanyaowu
   */
  public boolean stop() {
    isWaitingStop = true;

    if (channelGroup != null && !channelGroup.isShutdown()) {
      try {
        channelGroup.shutdownNow();
        if (!channelGroup.awaitTermination(5, TimeUnit.SECONDS)) {
          log.warn("channelGroup did not terminate within the timeout");
        }
      } catch (Exception e) {
        log.error("Faild to execute channelGroup.shutdownNow()", e);
      }
    }

    if (groupExecutor != null && !groupExecutor.isShutdown()) {
      try {
        groupExecutor.shutdownNow();
        if (!groupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          log.warn("groupExecutor did not terminate within the timeout");
        }
      } catch (Exception e) {
        log.error("Failed to close groupExecutor", e);
      }
    }

    if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
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
}
