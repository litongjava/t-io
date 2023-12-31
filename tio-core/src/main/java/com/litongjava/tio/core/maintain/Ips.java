package com.litongjava.tio.core.maintain;

import java.util.HashMap;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.lock.LockUtils;
import com.litongjava.tio.utils.lock.MapWithLock;
import com.litongjava.tio.utils.lock.SetWithLock;

/**
 * 一对多  (ip <--> ChannelContext)<br>
 * 一个ip有哪些客户端，该维护只在Server侧有<br>
 * @author tanyaowu 
 * 2017年10月19日 上午9:40:27
 */
public class Ips {

  /** The log. */
  private static Logger log = LoggerFactory.getLogger(Ips.class);

  /** 一个IP有哪些客户端
   * key: ip
   * value: SetWithLock<ChannelContext>
   */
  private MapWithLock<String, SetWithLock<ChannelContext>> ipmap = new MapWithLock<>(
      new HashMap<String, SetWithLock<ChannelContext>>());
  private String rwKey = "_tio_ips__";

  /**
   * 和ip绑定
   * @param ip
   * @param channelContext
   * @author tanyaowu
   */
  public void bind(ChannelContext channelContext) {
    if (channelContext == null) {
      return;
    }

    if (channelContext.tioConfig.isShortConnection) {
      return;
    }

    try {
      String ip = channelContext.getClientNode().getIp();
      if (ChannelContext.UNKNOWN_ADDRESS_IP.equals(ip)) {
        return;
      }

      if (StrUtil.isBlank(ip)) {
        return;
      }

      SetWithLock<ChannelContext> channelSet = ipmap.get(ip);
      if (channelSet == null) {
        LockUtils.runWriteOrWaitRead(rwKey + ip, this, () -> {
//					@Override
//					public void read() {
//					}

//					@Override
//					public void write() {
//						SetWithLock<ChannelContext> channelSet = ipmap.get(ip);
          if (ipmap.get(ip) == null) {
//							channelSet = new SetWithLock<>(new HashSet<ChannelContext>());
            ipmap.put(ip, new SetWithLock<>(new HashSet<ChannelContext>()));
          }
//					}
        });
        channelSet = ipmap.get(ip);
      }
      channelSet.add(channelContext);
    } catch (Exception e) {
      log.error(e.toString(), e);
    }
  }

  /**
   * 一个ip有哪些客户端，有可能返回null
   * @param tioConfig
   * @param ip
   * @return
   * @author tanyaowu
   */
  public SetWithLock<ChannelContext> clients(TioConfig tioConfig, String ip) {
    if (tioConfig.isShortConnection) {
      return null;
    }

    if (StrUtil.isBlank(ip)) {
      return null;
    }
    return ipmap.get(ip);
  }

  /**
   * @return the ipmap
   */
  public MapWithLock<String, SetWithLock<ChannelContext>> getIpmap() {
    return ipmap;
  }

  /**
   * 与指定ip解除绑定
   * @param ip
   * @param channelContext
   * @author tanyaowu
   */
  public void unbind(ChannelContext channelContext) {
    if (channelContext == null) {
      return;
    }

    if (channelContext.tioConfig.isShortConnection) {
      return;
    }

    try {
      String ip = channelContext.getClientNode().getIp();
      if (StrUtil.isBlank(ip)) {
        return;
      }
      if (ChannelContext.UNKNOWN_ADDRESS_IP.equals(ip)) {
        return;
      }

      SetWithLock<ChannelContext> channelSet = ipmap.get(ip);
      if (channelSet != null) {
        channelSet.remove(channelContext);
        if (channelSet.size() == 0) {
          ipmap.remove(ip);
        }
      } else {
        log.info("{}, ip【{}】 找不到对应的SetWithLock", channelContext.tioConfig.getName(), ip);
      }
    } catch (Exception e) {
      log.error(e.toString(), e);
    }
  }
}
