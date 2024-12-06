package com.litongjava.tio.core.task;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.client.ClientChannelContext;
import com.litongjava.tio.client.ClientTioConfig;
import com.litongjava.tio.client.ReconnConf;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.maintain.MaintainUtils;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.queue.FullWaitQueue;
import com.litongjava.tio.utils.queue.TioFullWaitQueue;
import com.litongjava.tio.utils.thread.pool.AbstractQueueRunnable;

/**
 * 
 * @author tanyaowu 
 * 2017年10月19日 上午9:39:59
 */
public class CloseRunnable extends AbstractQueueRunnable<ChannelContext> {

  private static Logger log = LoggerFactory.getLogger(CloseRunnable.class);

  public CloseRunnable(Executor executor) {
    super(executor);
    getMsgQueue();
  }
  // long count = 1;

  @Override
  public void runTask() {
    if (msgQueue.isEmpty()) {
      return;
    }
    ChannelContext channelContext = null;
    while ((channelContext = msgQueue.poll()) != null) {
      close(channelContext);
    }
  }

  public void close(ChannelContext channelContext) {
    try {
      boolean isNeedRemove = channelContext.closeMeta.isNeedRemove;
      String remark = channelContext.closeMeta.remark;
      Throwable throwable = channelContext.closeMeta.throwable;

      channelContext.stat.timeClosed = SystemTimer.currTime;
      if (channelContext.tioConfig.getAioListener() != null) {
        try {
          channelContext.tioConfig.getAioListener().onBeforeClose(channelContext, throwable, remark, isNeedRemove);
        } catch (Throwable e) {
          log.error(e.toString(), e);
        }
      }

      try {
        if (channelContext.isClosed && !isNeedRemove) {
          return;
        }

        if (channelContext.isRemoved) {
          return;
        }

        // 必须先取消任务再清空队列
        channelContext.handlerRunnable.setCanceled(true);
        channelContext.sendRunnable.setCanceled(true);

        channelContext.handlerRunnable.clearMsgQueue();
        channelContext.sendRunnable.clearMsgQueue();

        //log.info("{}, {} 准备关闭连接, isNeedRemove:{}, {}", channelContext.tioConfig, channelContext, isNeedRemove,remark);

        try {
          if (isNeedRemove) {
            MaintainUtils.remove(channelContext);
          } else {
            ClientTioConfig clientTioConfig = (ClientTioConfig) channelContext.tioConfig;
            clientTioConfig.closeds.add(channelContext);
            clientTioConfig.connecteds.remove(channelContext);
            MaintainUtils.close(channelContext);
          }

          channelContext.setRemoved(isNeedRemove);
          if (channelContext.tioConfig.statOn) {
            channelContext.tioConfig.groupStat.closed.incrementAndGet();
          }
          channelContext.stat.timeClosed = SystemTimer.currTime;
          channelContext.setClosed(true);
        } catch (Throwable e) {
          log.error(e.toString(), e);
        } finally {
          if (!isNeedRemove && channelContext.isClosed && !channelContext.isServer()) // 不删除且没有连接上，则加到重连队列中
          {
            ClientChannelContext clientChannelContext = (ClientChannelContext) channelContext;
            ReconnConf.put(clientChannelContext);
          }
        }
      } catch (Throwable e) {
        log.error(throwable.toString(), e);
      }
    } finally {
      channelContext.isWaitingClose = false;
    }
  }

  @Override
  public String logstr() {
    return super.logstr();
  }

  /** The msg queue. */
  private volatile FullWaitQueue<ChannelContext> msgQueue = null;

  @Override
  public FullWaitQueue<ChannelContext> getMsgQueue() {
    if (msgQueue == null) {
      synchronized (this) {
        if (msgQueue == null) {
          msgQueue = new TioFullWaitQueue<ChannelContext>(Integer.getInteger("tio.fullqueue.capacity", null), false);
        }
      }
    }
    return msgQueue;
  }
}
