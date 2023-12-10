package com.litongjava.tio.client.intf;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.intf.AioHandler;
import com.litongjava.tio.core.intf.Packet;

/**
 *
 * @author tanyaowu
 * 2017年4月1日 上午9:14:24
 */
public interface ClientAioHandler extends AioHandler {
  /**
   * 创建心跳包
   * @return
   * @author tanyaowu
   */
  Packet heartbeatPacket(ChannelContext channelContext);
}
