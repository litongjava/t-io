package com.litongjava.tio.core.task;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.aio.Packet;
import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.exception.AioDecodeException;
import com.litongjava.tio.core.stat.ChannelStat;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.core.utils.ByteBufferUtils;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.CollUtil;
import com.litongjava.tio.utils.queue.FullWaitQueue;
import com.litongjava.tio.utils.queue.TioFullWaitQueue;
import com.litongjava.tio.utils.thread.pool.AbstractQueueRunnable;

/**
 * 解码任务对象，一个连接对应一个本对象
 *
 * @author 谭耀武 2012-08-09
 */
@SuppressWarnings("deprecation")
public class DecodeRunnable extends AbstractQueueRunnable<ByteBuffer> {
  private static final Logger log = LoggerFactory.getLogger(DecodeRunnable.class);
  private ChannelContext channelContext = null;
  private TioConfig tioConfig = null;

  /**
   * 上一次解码剩下的数据
   */
  private ByteBuffer lastByteBuffer = null;
  /**
   * 新收到的数据
   */
  private ByteBuffer newReceivedByteBuffer = null;
  /**
   * 上次解码进度百分比
   */
  private int lastPercentage = 0;

  /**
   *
   * @param packet
   * @param byteCount
   * @author tanyaowu
   */
  public void handler(Packet packet, int byteCount) {
    switch (tioConfig.packetHandlerMode) {
    case SINGLE_THREAD:
      channelContext.handlerRunnable.handler(packet);
      break;
    case QUEUE:
      channelContext.handlerRunnable.addMsg(packet);
      channelContext.handlerRunnable.execute();
      break;
    default:
      channelContext.handlerRunnable.handler(packet);
      break;
    }
  }

  /**
   *
   */
  public DecodeRunnable(ChannelContext channelContext, Executor executor) {
    super(executor);
    this.channelContext = channelContext;
    this.tioConfig = channelContext.tioConfig;
    getMsgQueue();
  }

  /**
   * 清空处理的队列消息
   */
  public void clearMsgQueue() {
    super.clearMsgQueue();
    lastByteBuffer = null;
    newReceivedByteBuffer = null;
  }

  @Override
  public void runTask() {
    while ((newReceivedByteBuffer = msgQueue.poll()) != null) {
      decode();
    }
  }

  /**
   * @see java.lang.Runnable#run()
   *
   * @author tanyaowu 2017年3月21日 下午4:26:39
   *
   */
  public void decode() {
    if (EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC, false)) {
      log.info("decode:{}", channelContext.getClientNode());
    }
    ByteBuffer byteBuffer = newReceivedByteBuffer;
    if (lastByteBuffer != null) {
      byteBuffer = ByteBufferUtils.composite(lastByteBuffer, byteBuffer);
      lastByteBuffer = null;
    }
    label_2: while (true) {
      try {
        int initPosition = byteBuffer.position();
        int limit = byteBuffer.limit();
        int readableLength = limit - initPosition;
        Packet packet = null;

        if (channelContext.packetNeededLength != null) {
          if (log.isDebugEnabled()) {
            log.debug("{}, Length required for decoding:{}", channelContext, channelContext.packetNeededLength);
          }
          if (readableLength >= channelContext.packetNeededLength) {
            packet = tioConfig.getAioHandler().decode(byteBuffer, limit, initPosition, readableLength, channelContext);
          } else {
            int percentage = (int) (((double) readableLength / channelContext.packetNeededLength) * 100);
            if (percentage != lastPercentage) {
              lastPercentage = percentage;
              if (tioConfig.disgnostic) {
                log.info("Receiving large packet: received {}% of {} bytes.", percentage, channelContext.packetNeededLength);
              }
            }
            lastByteBuffer = ByteBufferUtils.copy(byteBuffer, initPosition, limit);
            return;
          }
        } else {
          try {
            packet = tioConfig.getAioHandler().decode(byteBuffer, limit, initPosition, readableLength, channelContext);
          } catch (BufferUnderflowException e) {
            // 数据不够读
            e.printStackTrace();

          }
        }

        if (packet == null)// 数据不够，解不了码
        {
          if (tioConfig.useQueueDecode || (byteBuffer != newReceivedByteBuffer)) {
            byteBuffer.position(initPosition);
            byteBuffer.limit(limit);
            lastByteBuffer = byteBuffer;
          } else {
            lastByteBuffer = ByteBufferUtils.copy(byteBuffer, initPosition, limit);
          }
          ChannelStat channelStat = channelContext.stat;
          channelStat.decodeFailCount++;
          if (log.isInfoEnabled()) {
            log.info("{} Failed to decode this time, has failed to decode for {} consecutive times, the length of data involved in decoding is {} bytes.", channelContext, channelStat.decodeFailCount,
                readableLength);
          }
          if (channelStat.decodeFailCount > 5) {
            if (channelContext.packetNeededLength == null) {
              if (log.isInfoEnabled()) {
                log.info("{} Failed to decode this time, has failed to decode for {} consecutive times, the length of data involved in decoding is {} bytes.", channelContext,
                    channelStat.decodeFailCount, readableLength);
              }
            }

            // 检查慢包攻击
            if (channelStat.decodeFailCount > 10) {
              // int capacity = lastByteBuffer.capacity();
              int per = readableLength / channelStat.decodeFailCount;
              if (per < Math.min(channelContext.getReadBufferSize() / 2, 256)) {
                String str = "Failed to decode continuously " + channelStat.decodeFailCount + " times unsuccessfully, and the average data received each time is " + per
                    + " bytes, which suggests the possibility of a slow attack";
                throw new AioDecodeException(str);
              }
            }
          }
          return;
        } else // 解码成功
        {
          channelContext.setPacketNeededLength(null);
          channelContext.stat.latestTimeOfReceivedPacket = SystemTimer.currTime;
          channelContext.stat.decodeFailCount = 0;

          int packetSize = byteBuffer.position() - initPosition;
          packet.setByteCount(packetSize);

          if (tioConfig.statOn) {
            tioConfig.groupStat.receivedPackets.incrementAndGet();
            channelContext.stat.receivedPackets.incrementAndGet();
          }

          if (CollUtil.isNotEmpty(tioConfig.ipStats.durationList)) {
            try {
              for (Long v : tioConfig.ipStats.durationList) {
                IpStat ipStat = tioConfig.ipStats.get(v, channelContext);
                ipStat.getReceivedPackets().incrementAndGet();
                tioConfig.getIpStatListener().onAfterDecoded(channelContext, packet, packetSize, ipStat);
              }
            } catch (Exception e1) {
              log.error(packet.logstr(), e1);
            }
          }

          if (tioConfig.getAioListener() != null) {
            try {
              tioConfig.getAioListener().onAfterDecoded(channelContext, packet, packetSize);
            } catch (Throwable e) {
              log.error(e.toString(), e);
            }
          }

          if (log.isDebugEnabled()) {
            log.debug("{}, Unpacking to get a packet:{}", channelContext, packet.logstr());
          }

          handler(packet, packetSize);

          if (byteBuffer.hasRemaining())// 组包后，还剩有数据
          {
            if (log.isDebugEnabled()) {
              log.debug("{},After grouping packets, there is still data left:{}", channelContext, byteBuffer.remaining());
            }
            continue label_2;
          } else// 组包后，数据刚好用完
          {
            lastByteBuffer = null;
            if (log.isDebugEnabled()) {
              log.debug("{},After grouping the packets, the data just ran out", channelContext);
            }
            return;
          }
        }
      } catch (Throwable e) {
        if (channelContext.logWhenDecodeError) {
          log.error("Encountered an exception while decoding", e);
        }

        channelContext.setPacketNeededLength(null);

        if (e instanceof AioDecodeException) {
          List<Long> list = tioConfig.ipStats.durationList;
          if (list != null && list.size() > 0) {
            try {
              for (Long v : list) {
                IpStat ipStat = tioConfig.ipStats.get(v, channelContext);
                ipStat.getDecodeErrorCount().incrementAndGet();
                tioConfig.getIpStatListener().onDecodeError(channelContext, ipStat);
              }
            } catch (Exception e1) {
              log.error(e1.toString(), e1);
            }
          }
        }

        Tio.close(channelContext, e, "Decode exception:" + e.getMessage(), CloseCode.DECODE_ERROR);
        return;
      }
    }
  }

  /**
   * 
   * @param newReceivedByteBuffer
   */
  public void setNewReceivedByteBuffer(ByteBuffer newReceivedByteBuffer) {
    this.newReceivedByteBuffer = newReceivedByteBuffer;
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
  private FullWaitQueue<ByteBuffer> msgQueue = null;

  @Override
  public FullWaitQueue<ByteBuffer> getMsgQueue() {
    if (tioConfig.useQueueDecode) {
      if (msgQueue == null) {
        synchronized (this) {
          if (msgQueue == null) {
            msgQueue = new TioFullWaitQueue<ByteBuffer>(Integer.getInteger("tio.fullqueue.capacity", null), true);
          }
        }
      }
      return msgQueue;
    }
    return null;
  }

}
