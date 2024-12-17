package com.litongjava.tio.core.task;

import java.util.concurrent.atomic.AtomicLong;

import com.litongjava.aio.Packet;
import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.cache.AbsCache;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.CollUtil;
import com.litongjava.tio.utils.lock.MapWithLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandlePacketTask {

  private AtomicLong synFailCount = new AtomicLong();
  
  /**
   * 处理packet
   * 
   * @param packet
   * @return
   *
   * @author tanyaowu
   */
  public void handler(ChannelContext channelContext, Packet packet) {
    // int ret = 0;
    TioConfig tioConfig = channelContext.tioConfig;

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
          log.error("[{}] Failed to synchronize message, synSeq is {}, but there is no corresponding key value in the synchronization collection", synFailCount.incrementAndGet(), synSeq);
        }
      } else {
        Node client = channelContext.getProxyClientNode();
        if (client == null) {
          client = channelContext.getClientNode();
        }

        if (EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC, false)) {
          String ip = client.getIp();
          int port = client.getPort();
          Long id = packet.getId();
          String requestInfo = ip + ":" + port + "_" + id;
          log.info("handle:{},{},{}", ip, port, id);
          AbsCache cache = tioConfig.getCacheFactory().getCache(TioCoreConfigKeys.REQEUST_PROCESSING);
          cache.put(SystemTimer.currTime + "", requestInfo);
          tioConfig.getAioHandler().handler(packet, channelContext);
          cache.remove(SystemTimer.currTime + "");
        } else {
          tioConfig.getAioHandler().handler(packet, channelContext);
        }
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

}