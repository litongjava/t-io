package com.litongjava.tio.server;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ReadCompletionHandler;
import com.litongjava.tio.core.Tio.IpBlacklist;
import com.litongjava.tio.core.ssl.SslUtils;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.CollUtil;

/**
 *
 * @author tanyaowu 2017年4月4日 上午9:27:45
 */
public class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, TioServer> {

  private static Logger log = LoggerFactory.getLogger(AcceptCompletionHandler.class);

  public AcceptCompletionHandler() {
  }

  /**
   *
   * @param asynchronousSocketChannel
   * @param tioServer
   * @author tanyaowu
   */
  @Override
  public void completed(AsynchronousSocketChannel asynchronousSocketChannel, TioServer tioServer) {
    if (tioServer.isWaitingStop()) {
      log.info("The server will be shut down and no new requests will be accepted:{}", tioServer.getServerNode());
    } else {
      AsynchronousServerSocketChannel serverSocketChannel = tioServer.getServerSocketChannel();
      serverSocketChannel.accept(tioServer, this);
    }

    ServerTioConfig serverTioConfig = tioServer.getServerTioConfig();
    try {

      InetSocketAddress inetSocketAddress = (InetSocketAddress) asynchronousSocketChannel.getRemoteAddress();
      if (EnvUtils.getBoolean(TioCoreConfigKeys.TCP_CORE_DIAGNOSTIC, false)) {
        log.info("new connection:{},{}", inetSocketAddress.getHostString(), inetSocketAddress.getPort());
      }

      String clientIp = inetSocketAddress.getHostString();
      // serverTioConfig.ips.get(clientIp).getRequestCount().incrementAndGet();

      // CaffeineCache[] caches = serverTioConfig.ips.getCaches();
      // for (CaffeineCache guavaCache : caches) {
      // IpStat ipStat = (IpStat) guavaCache.get(clientIp);
      // ipStat.getRequestCount().incrementAndGet();
      // }

      if (IpBlacklist.isInBlacklist(serverTioConfig, clientIp)) {
        log.info("{} on the blacklist, {}", clientIp, serverTioConfig.getName());
        asynchronousSocketChannel.close();
        return;
      }

      if (serverTioConfig.statOn) {
        ((ServerGroupStat) serverTioConfig.groupStat).accepted.incrementAndGet();
      }

      // channelContext.getIpStat().getActivatedCount().incrementAndGet();
      // for (CaffeineCache guavaCache : caches) {
      // IpStat ipStat = (IpStat) guavaCache.get(clientIp);
      // ipStat.getActivatedCount().incrementAndGet();
      // }
      // for (Long v : durationList) {
      // IpStat ipStat = (IpStat) serverTioConfig.ips.get(v, clientIp);
      // IpStat.getActivatedCount().incrementAndGet();
      // }
      // IpStat.getActivatedCount(clientIp, true).incrementAndGet();
      asynchronousSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      asynchronousSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);
      asynchronousSocketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 64 * 1024);
      asynchronousSocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

      ServerChannelContext channelContext = new ServerChannelContext(serverTioConfig, asynchronousSocketChannel);
      channelContext.setClosed(false);
      channelContext.stat.setTimeFirstConnected(SystemTimer.currTime);
      channelContext.setServerNode(tioServer.getServerNode());

      // channelContext.traceClient(ChannelAction.CONNECT, null, null);

      // serverTioConfig.connecteds.add(channelContext);
      serverTioConfig.ips.bind(channelContext);

      boolean isConnected = true;
      boolean isReconnect = false;
      if (serverTioConfig.getServerAioListener() != null) {
        if (!SslUtils.isSsl(channelContext.tioConfig)) {
          try {
            serverTioConfig.getServerAioListener().onAfterConnected(channelContext, isConnected, isReconnect);
          } catch (Throwable e) {
            log.error(e.toString(), e);
          }
        }
      }

      if (CollUtil.isNotEmpty(serverTioConfig.ipStats.durationList)) {
        try {
          for (Long v : serverTioConfig.ipStats.durationList) {
            IpStat ipStat = (IpStat) serverTioConfig.ipStats.get(v, channelContext);
            ipStat.getRequestCount().incrementAndGet();
            serverTioConfig.getIpStatListener().onAfterConnected(channelContext, isConnected, isReconnect, ipStat);
          }
        } catch (Exception e) {
          log.error(e.toString(), e);
        }
      }

      if (!tioServer.isWaitingStop()) {
        ReadCompletionHandler readCompletionHandler = channelContext.getReadCompletionHandler();
        ByteBuffer readByteBuffer = readCompletionHandler.getReadByteBuffer();// ByteBuffer.allocateDirect(channelContext.tioConfig.getReadBufferSize());
        readByteBuffer.position(0);
        readByteBuffer.limit(readByteBuffer.capacity());
        asynchronousSocketChannel.read(readByteBuffer, readByteBuffer, readCompletionHandler);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   *
   * @param exc
   * @param tioServer
   * @author tanyaowu
   */
  @Override
  public void failed(Throwable exc, TioServer tioServer) {
    AsynchronousServerSocketChannel serverSocketChannel = tioServer.getServerSocketChannel();
    serverSocketChannel.accept(tioServer, this);

    log.error("[" + tioServer.getServerNode() + "]监听出现异常", exc);

  }

}
