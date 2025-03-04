package com.litongjava.tio.server;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import com.litongjava.model.sys.SysConst;
import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.intf.AioHandler;
import com.litongjava.tio.core.intf.AioListener;
import com.litongjava.tio.core.maintain.GlobalIpBlacklist;
import com.litongjava.tio.core.ssl.SslConfig;
import com.litongjava.tio.server.intf.ServerAioHandler;
import com.litongjava.tio.server.intf.ServerAioListener;
import com.litongjava.tio.utils.AppendJsonConverter;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.hutool.CollUtil;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.lock.SetWithLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author tanyaowu 2016年10月10日 下午5:51:56
 */
@Slf4j
public class ServerTioConfig extends TioConfig {

  private ServerAioHandler serverAioHandler = null;
  private ServerAioListener serverAioListener = null;
  private Thread checkHeartbeatThread = null;
  private boolean needCheckHeartbeat = true;
  private boolean isShared = false;
  /**
   * 服务端backlog
   */
  private int backlog = 1000;

  public ServerTioConfig(String name) {
    super(name);
  }

  /**
   * 
   * @param keyStoreFile   如果是以"classpath:"开头，则从classpath中查找，否则视为普通的文件路径
   * @param trustStoreFile 如果是以"classpath:"开头，则从classpath中查找，否则视为普通的文件路径
   * @param keyStorePwd
   * @throws FileNotFoundException
   */
  public void useSsl(String keyStoreFile, String trustStoreFile, String keyStorePwd) throws Exception {
    if (StrUtil.isNotBlank(keyStoreFile) && StrUtil.isNotBlank(trustStoreFile)) {
      SslConfig sslConfig = SslConfig.forServer(keyStoreFile, trustStoreFile, keyStorePwd);
      this.setSslConfig(sslConfig);
    }
  }

  /**
   * 
   * @param keyStoreInputStream
   * @param trustStoreInputStream
   * @param passwd
   * @throws Exception
   * @author tanyaowu
   */
  public void useSsl(InputStream keyStoreInputStream, InputStream trustStoreInputStream, String passwd) throws Exception {
    SslConfig sslConfig = SslConfig.forServer(keyStoreInputStream, trustStoreInputStream, passwd);
    this.setSslConfig(sslConfig);
  }

  /**
   * @see org.tio.core.TioConfig#getAioHandler()
   *
   * @return
   * @author tanyaowu 2016年12月20日 上午11:34:37
   *
   */
  @Override
  public AioHandler getAioHandler() {
    return this.getServerAioHandler();
  }

  /**
   * @see org.tio.core.TioConfig#getAioListener()
   *
   * @return
   * @author tanyaowu 2016年12月20日 上午11:34:37
   *
   */
  @Override
  public AioListener getAioListener() {
    return getServerAioListener();
  }

  /**
   * @return the serverAioHandler
   */
  public ServerAioHandler getServerAioHandler() {
    return serverAioHandler;
  }

  /**
   * @return the serverAioListener
   */
  public ServerAioListener getServerAioListener() {
    return serverAioListener;
  }

  public void setServerAioListener(ServerAioListener serverAioListener) {
    this.serverAioListener = serverAioListener;
  }

  /**
   * @return
   * @author tanyaowu
   */
  @Override
  public boolean isServer() {
    return true;
  }

  @Override
  public String toString() {
    return "ServerTioConfig [name=" + name + "]";
  }

  public void share(ServerTioConfig tioConfig) {
    synchronized (ServerTioConfig.class) {
      if (tioConfig == this) {
        return;
      }
      this.clientNodes = tioConfig.clientNodes;
      this.connections = tioConfig.connections;
      this.groups = tioConfig.groups;
      this.users = tioConfig.users;
      this.tokens = tioConfig.tokens;
      this.ids = tioConfig.ids;
      this.bsIds = tioConfig.bsIds;
      this.ipBlacklist = tioConfig.ipBlacklist;
      this.ips = tioConfig.ips;

      if (!tioConfig.isShared && !this.isShared) {
        this.needCheckHeartbeat = false;
      }
      if (tioConfig.isShared && !this.isShared) {
        this.needCheckHeartbeat = false;
      }
      if (!tioConfig.isShared && this.isShared) {
        tioConfig.needCheckHeartbeat = false;
      }

      // 下面这两行代码要放到前面if的后面
      tioConfig.isShared = true;
      this.isShared = true;
    }
  }

  public void setServerAioHandler(ServerAioHandler serverAioHandler) {
    this.serverAioHandler = serverAioHandler;
  }

  public int getBacklog() {
    return backlog;
  }

  public void setBacklog(int backlog) {
    this.backlog = backlog;
  }

  public void init() {
    super.init();
    this.groupStat = new ServerGroupStat();
    GlobalIpBlacklist.INSTANCE.init(this);
    Runnable check = new Runnable() {
      @Override
      public void run() {
        // 第一次先休息一下
        try {
          Thread.sleep(1000 * 10);
        } catch (InterruptedException e1) {
          log.error(e1.toString(), e1);
        }

        while (needCheckHeartbeat && !isStopped()) {
          // long sleeptime = heartbeatTimeout;
          if (heartbeatTimeout <= 0) {
            break;
          }
          try {
            Thread.sleep(heartbeatTimeout);
          } catch (InterruptedException e1) {
            log.error(e1.toString(), e1);
          }
          long start = SystemTimer.currTime;
          SetWithLock<ChannelContext> setWithLock = connections;
          Set<ChannelContext> set = null;
          long start1 = 0;
          int count = 0;
          ReadLock readLock = setWithLock.readLock();
          readLock.lock();
          try {
            start1 = SystemTimer.currTime;
            set = setWithLock.getObj();

            for (ChannelContext channelContext : set) {
              count++;
              long compareTime = Math.max(channelContext.stat.latestTimeOfReceivedByte, channelContext.stat.latestTimeOfSentPacket);
              long currtime = SystemTimer.currTime;
              long interval = currtime - compareTime;

              boolean needRemove = false;
              if (channelContext.heartbeatTimeout != null && channelContext.heartbeatTimeout > 0) {
                needRemove = interval > channelContext.heartbeatTimeout;
              } else {
                needRemove = interval > heartbeatTimeout;
              }

              if (needRemove) {
                if (!ServerTioConfig.this.serverAioListener.onHeartbeatTimeout(channelContext, interval, channelContext.stat.heartbeatTimeoutCount.incrementAndGet())) {
                  log.info("{}, {} ms or not send and receive message", channelContext, interval);
                  channelContext.setCloseCode(CloseCode.HEARTBEAT_TIMEOUT);
                  Tio.remove(channelContext, interval + " ms not send and receive message");
                }
              }
            }
          } catch (Throwable e) {
            log.error("", e);
          } finally {
            try {
              readLock.unlock();
              if (debug) {
                diagnostic(start, set, start1, count);
              }
            } catch (Throwable e) {
              log.error("", e);
            }
          }
        }
      }
    };

    checkHeartbeatThread = new Thread(check, "tio-timer-checkheartbeat-" + id + "-" + name);
    checkHeartbeatThread.setDaemon(true);
    checkHeartbeatThread.setPriority(Thread.MIN_PRIORITY);
    checkHeartbeatThread.start();
  }

  private void diagnostic(long start, Set<ChannelContext> set, long start1, int count) {
    StringBuilder builder = new StringBuilder();
    builder.append(SysConst.CRLF).append(name);
    builder.append("\r\n ├ 当前时间:").append(SystemTimer.currTime);
    builder.append("\r\n ├ 连接统计");
    builder.append("\r\n │ \t ├ 共接受过连接数 :").append(((ServerGroupStat) groupStat).accepted.get());
    builder.append("\r\n │ \t ├ 当前连接数 :").append(set.size());
    // builder.append("\r\n │ \t ├ 当前群组数 :").append(groups);
    builder.append("\r\n │ \t ├ 异IP连接数 :").append(this.ips.getIpmap().getObj().size());
    builder.append("\r\n │ \t └ 关闭过的连接数 :").append(groupStat.closed.get());

    builder.append("\r\n ├ 消息统计");
    builder.append("\r\n │ \t ├ 已处理消息 :").append(groupStat.handledPackets.get());
    builder.append("\r\n │ \t ├ 已接收消息(packet/byte) :").append(groupStat.receivedPackets.get()).append("/").append(groupStat.receivedBytes.get());
    builder.append("\r\n │ \t ├ 已发送消息(packet/byte) :").append(groupStat.sentPackets.get()).append("/").append(groupStat.sentBytes.get()).append("b");
    builder.append("\r\n │ \t ├ 平均每次TCP包接收的字节数 :").append(groupStat.getBytesPerTcpReceive());
    builder.append("\r\n │ \t └ 平均每次TCP包接收的业务包 :").append(groupStat.getPacketsPerTcpReceive());
    builder.append("\r\n └ IP统计时段 ");

    if (CollUtil.isNotEmpty(ipStats.durationList)) {
      builder.append("\r\n   \t └ ").append(AppendJsonConverter.convertListLongToJson(this.ipStats.durationList));
    } else {
      builder.append("\r\n   \t └ ").append("没有设置ip统计时间");
    }

    builder.append("\r\n ├ 节点统计");
    builder.append("\r\n │ \t ├ clientNodes :").append(this.clientNodes.getObjWithLock().getObj().size());
    builder.append("\r\n │ \t ├ 所有连接 :").append(this.connections.getObj().size());
    builder.append("\r\n │ \t ├ 绑定user数 :").append(this.users.getMap().getObj().size());
    builder.append("\r\n │ \t ├ 绑定token数 :").append(this.tokens.getMap().getObj().size());
    builder.append("\r\n │ \t └ 等待同步消息响应 :").append(this.waitingResps.getObj().size());

    builder.append("\r\n ├ 群组");
    builder.append("\r\n │ \t └ groupmap:").append(this.groups.getGroupmap().getObj().size());
    builder.append("\r\n └ 拉黑IP ");
    if (this.ipBlacklist != null) {
      builder.append("\r\n   \t └ ").append(AppendJsonConverter.convertCollectionStringToJson(this.ipBlacklist.getAll()));
    }
    builder.append("\r\n └ 正在处理的请求数量: ").append(getCacheFactory().getCache(TioCoreConfigKeys.REQEUST_PROCESSING).keysCollection().size());
    // builder.append("\r\n └ 线程池信息: \n").append(Threads.status());
    log.warn(builder.toString());
    long end = SystemTimer.currTime;
    long iv1 = start1 - start;
    long iv = end - start1;
    log.warn("{}, 检查心跳, 共{}个连接, 取锁耗时{}ms, 循环耗时{}ms, 心跳超时时间:{}ms", name, count, iv1, iv, heartbeatTimeout);
  }
}