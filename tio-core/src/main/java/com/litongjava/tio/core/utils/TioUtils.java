package com.litongjava.tio.core.utils;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.Tio;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author tanyaowu 
 * 2017年10月19日 上午9:40:54
 */
@Slf4j
public class TioUtils {

  public static boolean checkBeforeIO(ChannelContext channelContext) {
    if (channelContext.isWaitingClose) {
      return false;
    }

    Boolean isopen = null;
    if (channelContext.asynchronousSocketChannel != null) {
      isopen = channelContext.asynchronousSocketChannel.isOpen();

      if (channelContext.isClosed || channelContext.isRemoved) {
        if (isopen) {
          try {
            Tio.close(channelContext, "asynchronousSocketChannel is open, but channelContext isClosed: " + channelContext.isClosed + ", isRemoved: " + channelContext.isRemoved,
                CloseCode.CHANNEL_NOT_OPEN);
          } catch (Throwable e) {
            log.error(e.toString(), e);
          }
        }
        return false;
      }
    } else {
      log.error("{}, Plese check, asynchronousSocketChannel is null, isClosed:{}, isRemoved:{}, {} ", channelContext, channelContext.isClosed, channelContext.isRemoved);
      return false;
    }

    if (!isopen) {
      log.info("connection might close by peer,{}, isOpen:{}, isClosed:{}, isRemoved:{}", channelContext, isopen, channelContext.isClosed, channelContext.isRemoved);
      Tio.close(channelContext, "asynchronousSocketChannel is not open, connection might close by peer", CloseCode.CHANNEL_NOT_OPEN);
      return false;
    }
    return true;
  }

}
