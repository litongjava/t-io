package com.litongjava.tio.core.task;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.PacketHandlerMode;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.intf.Packet;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.cache.AbsCache;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.CollUtil;
import com.litongjava.tio.utils.lock.MapWithLock;
import com.litongjava.tio.utils.queue.FullWaitQueue;
import com.litongjava.tio.utils.queue.TioFullWaitQueue;
import com.litongjava.tio.utils.thread.pool.AbstractQueueRunnable;

/**
 *
 * @author 谭耀武 2012-08-09
 *
 */
public class HandlerRunnable extends AbstractQueueRunnable<Packet> {
  private static final Logger log = LoggerFactory.getLogger(HandlerRunnable.class);

  private ChannelContext channelContext = null;
  private TioConfig tioConfig = null;

  private AtomicLong synFailCount = new AtomicLong();

  public HandlerRunnable(ChannelContext channelContext, Executor executor) {
    super(executor);
    this.channelContext = channelContext;
    tioConfig = channelContext.tioConfig;
    getMsgQueue();
  }

  /**
   * 处理packet
   * 
   * @param packet
   * @return
   *
   * @author tanyaowu
   */
  public void handler(Packet packet) {
    // int ret = 0;

    long start = SystemTimer.currTime;
    try {
      Integer synSeq = packet.getSynSeq();
      if (synSeq != null && synSeq > 0) {
        MapWithLock<Integer, Packet> syns = tioConfig.getWaitingResps();
        Packet initPacket = syns.remove(synSeq);
        if (initPacket != null) {
          synchronized (initPacket) {
            syns.put(synSeq, packet);
            initPacket.notify();
          }
        } else {
          log.error("[{}]同步消息失败, synSeq is {}, 但是同步集合中没有对应key值", synFailCount.incrementAndGet(), synSeq);
        }
      } else {
        Node client = channelContext.getProxyClientNode();
        if (client == null) {
          client = channelContext.getClientNode();
        }

        String ip = client.getIp();
        int port = client.getPort();
        Long id = packet.getId();
        String requestInfo = ip + ":" + port + "_" + id;
        String currentTimeMillis = System.currentTimeMillis() + "";
        if (EnvUtils.getBoolean(TioCoreConfigKeys.TCP_CORE_DIAGNOSTIC, false)) {
          log.info("handle:{},{},{}", ip, port, id);
        }
        AbsCache cache = tioConfig.getCacheFactory().getCache(TioCoreConfigKeys.REQEUST_PROCESSING);
        cache.put(currentTimeMillis.toString(), requestInfo);
        tioConfig.getAioHandler().handler(packet, channelContext);
        cache.remove(currentTimeMillis);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      long end = SystemTimer.currTime;
      long iv = end - start;
      if (tioConfig.statOn) {
        channelContext.stat.handledPackets.incrementAndGet();
        channelContext.stat.handledBytes.addAndGet(packet.getByteCount());
        channelContext.stat.handledPacketCosts.addAndGet(iv);

        tioConfig.groupStat.handledPackets.incrementAndGet();
        tioConfig.groupStat.handledBytes.addAndGet(packet.getByteCount());
        tioConfig.groupStat.handledPacketCosts.addAndGet(iv);
      }

      if (CollUtil.isNotEmpty(tioConfig.ipStats.durationList)) {
        try {
          for (Long v : tioConfig.ipStats.durationList) {
            IpStat ipStat = (IpStat) tioConfig.ipStats.get(v, channelContext);
            ipStat.getHandledPackets().incrementAndGet();
            ipStat.getHandledBytes().addAndGet(packet.getByteCount());
            ipStat.getHandledPacketCosts().addAndGet(iv);
            tioConfig.getIpStatListener().onAfterHandled(channelContext, packet, ipStat, iv);
          }
        } catch (Exception e1) {
          e1.printStackTrace();
        }
      }

      if (tioConfig.getAioListener() != null) {
        try {
          tioConfig.getAioListener().onAfterHandled(channelContext, packet, iv);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

    }
  }

  /**
   * @see org.tio.core.SynRunnable.intf.ISynRunnable#runTask()
   *
   * @author tanyaowu 2016年12月5日 下午3:02:49
   *
   */
  @Override
  public void runTask() {
    Packet packet = null;
    while ((packet = msgQueue.poll()) != null) {
      handler(packet);
    }
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + ":" + channelContext.toString();
  }

  @Override
  public String logstr() {
    return toString();
  }

  /** The msg queue. */
  private FullWaitQueue<Packet> msgQueue = null;

  @Override
  public FullWaitQueue<Packet> getMsgQueue() {
    if (PacketHandlerMode.QUEUE == tioConfig.packetHandlerMode) {
      if (msgQueue == null) {
        synchronized (this) {
          if (msgQueue == null) {
            msgQueue = new TioFullWaitQueue<Packet>(Integer.getInteger("tio.fullqueue.capacity", null), true);
          }
        }
      }
      return msgQueue;
    }
    return null;
  }

}
