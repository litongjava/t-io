package com.litongjava.tio.core.maintain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.lock.LockUtils;
import com.litongjava.tio.utils.lock.MapWithLock;
import com.litongjava.tio.utils.lock.SetWithLock;

/**
 * 一对多  (userid <--> ChannelContext)<br>
 * @author tanyaowu 
 * 2017年10月19日 上午9:40:40
 */
public class Users {
  private static Logger log = LoggerFactory.getLogger(Users.class);
  /**
   * key: userid
   * value: ChannelContext
   */
  private MapWithLock<String, SetWithLock<ChannelContext>> mapWithLock = new MapWithLock<>(
      new HashMap<String, SetWithLock<ChannelContext>>());

  /**
   * 绑定userid.
   *
   * @param userid the userid
   * @param channelContext the channel context
   * @author tanyaowu
   */
  public void bind(String userid, ChannelContext channelContext) {
    if (channelContext.tioConfig.isShortConnection) {
      return;
    }

    if (StrUtil.isBlank(userid)) {
      return;
    }

    try {
      SetWithLock<ChannelContext> setWithLock = mapWithLock.get(userid);
      if (setWithLock == null) {
        LockUtils.runWriteOrWaitRead("_tio_users_bind__" + userid, this, () -> {
//					@Override
//					public void read() {
//					}

//					@Override
//					public void write() {
//						SetWithLock<ChannelContext> setWithLock = mapWithLock.get(userid);
          if (mapWithLock.get(userid) == null) {
//							setWithLock = new SetWithLock<>(new HashSet<ChannelContext>());
            mapWithLock.put(userid, new SetWithLock<>(new HashSet<ChannelContext>()));
          }
//					}
        });
        setWithLock = mapWithLock.get(userid);
      }
      setWithLock.add(channelContext);

      channelContext.setUserid(userid);
    } catch (Throwable e) {
      log.error("", e);
    }

  }

  /**
   * Find.
   *
   * @param userid the userid
   * @return the channel context
   */
  public SetWithLock<ChannelContext> find(TioConfig tioConfig, String userid) {
    if (tioConfig.isShortConnection) {
      return null;
    }

    if (StrUtil.isBlank(userid)) {
      return null;
    }

    return mapWithLock.get(userid);
  }

  /**
   * @return the mapWithLock
   */
  public MapWithLock<String, SetWithLock<ChannelContext>> getMap() {
    return mapWithLock;
  }

  /**
   * 解除channelContext绑定的userid
   *
   * @param channelContext the channel context
   */
  public void unbind(ChannelContext channelContext) {
    if (channelContext.tioConfig.isShortConnection) {
      return;
    }

    String userid = channelContext.userid;
    if (StrUtil.isBlank(userid)) {
      log.debug("{}, {}, unbind user", channelContext.tioConfig.getName(), channelContext.toString());
      return;
    }

    try {
      SetWithLock<ChannelContext> setWithLock = mapWithLock.get(userid);
      if (setWithLock == null) {
        log.warn("{}, {}, userid:{}, can't find SetWithLock", channelContext.tioConfig.getName(), channelContext.toString(),
            userid);
        return;
      }

      setWithLock.remove(channelContext);

      if (setWithLock.size() == 0) {
        mapWithLock.remove(userid);
      }

      channelContext.setUserid(null);
    } catch (Throwable e) {
      log.error(e.toString(), e);
    }
  }

  /**
   * 解除tioConfig范围内所有ChannelContext的 userid绑定
   *
   * @param userid the userid
   * @author tanyaowu
   */
  public void unbind(TioConfig tioConfig, String userid) {
    if (tioConfig.isShortConnection) {
      return;
    }
    if (StrUtil.isBlank(userid)) {
      return;
    }

    try {
      Lock lock = mapWithLock.writeLock();
      lock.lock();
      try {
        Map<String, SetWithLock<ChannelContext>> m = mapWithLock.getObj();
        SetWithLock<ChannelContext> setWithLock = m.get(userid);
        if (setWithLock == null) {
          return;
        }

        WriteLock writeLock = setWithLock.writeLock();
        writeLock.lock();
        try {
          Set<ChannelContext> set = setWithLock.getObj();
          if (set.size() > 0) {
            for (ChannelContext channelContext : set) {
              channelContext.setUserid(null);
            }
            set.clear();
          }

          m.remove(userid);
        } catch (Throwable e) {
          log.error(e.getMessage(), e);
        } finally {
          writeLock.unlock();
        }

      } catch (Throwable e) {
        throw e;
      } finally {
        lock.unlock();
      }
    } catch (Throwable e) {
      log.error(e.toString(), e);
    }
  }
}
