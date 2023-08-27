package com.rpl.mastodon;

import com.rpl.mastodon.data.*;
import com.rpl.rama.RamaModule;
import com.rpl.rama.ops.*;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.jupiter.api.TestInfo;

import java.util.*;

public class TestHelpers {
  public static Set asSet(Object... os) {
    Set ret = new HashSet();
    for(Object o: os) {
      ret.add(o);
    }
    return ret;
  }

  public static List asList(Object... os) {
    List ret = new ArrayList();
    for(Object o: os) {
      ret.add(o);
    }
    return ret;
  }

  public static Map asMap(Object... kvs) {
    Map ret = new HashMap();
    for(int i=0; i<kvs.length; i+=2) {
      ret.put(kvs[i], kvs[i+ 1]);
    }
    return ret;
  }

  public static List<Long> getStatusIds(List<StatusResultWithId> statuses) {
    List<Long> res = new ArrayList<>();
    for (StatusResultWithId status : statuses) {
      res.add(status.statusId);
    }
    return res;
  }

  public static StatusContent normalStatusContent(String text, StatusVisibility visibility) {
    return StatusContent.normal(new NormalStatusContent(text, visibility));
  }

  public static StatusContent replyStatusContent(String text, StatusVisibility visibility, long parentAuthorId, long parentStatusId) {
    return StatusContent.reply(new ReplyStatusContent(text, visibility, new StatusPointer(parentAuthorId, parentStatusId)));
  }

  public static StatusContent boostStatusContent(long authorId, long statusId) {
    return StatusContent.boost(new BoostStatusContent(new StatusPointer(authorId, statusId)));
  }

  public static void attainCondition(RamaFunction0<Boolean> fn) {
    attainConditionPred(() -> null, (Object q) -> fn.invoke());
  }

  public static <T> void attainConditionPred(RamaFunction0<T> queryFn, RamaFunction1<T, Boolean> predFn) {
    long start = System.nanoTime();
    while(true) {
      T q = queryFn.invoke();
      if(predFn.invoke(q)) {
        break;
      } else if(System.nanoTime() - start >= 45000000000L) { // 45 seconds
        throw new RuntimeException("Failed to attain condition, last query: " + q);
      } else {
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  public static void attainStableCondition(RamaFunction0<Boolean> fn) {
    attainStableConditionPred(() -> null, (Object q) -> fn.invoke());
  }

  public static <T> void attainStableConditionPred(RamaFunction0<T> queryFn, RamaFunction1<T, Boolean> predFn) {
    long start = System.nanoTime();
    int reachedCount = 0;
    try {
      while(true) {
        T q = queryFn.invoke();
        if(predFn.invoke(q)) {
          reachedCount++;
          if(reachedCount > 25) break;
          Thread.sleep(2);
        } else if(reachedCount > 0) {
          throw new RuntimeException("Condition not stable, last query: " + q);
        } else if(System.nanoTime() - start >= 45000000000L) { // 45 seconds
          throw new RuntimeException("Failed to attain condition, last query: " + q);
        } else {
          Thread.sleep(2);
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static void launchModule(InProcessCluster ipc, RamaModule module, TestInfo testInfo) {
    int numTasks = 1 << new Random().nextInt(3);
    int numThreads = new Random().nextInt(Math.min(2, numTasks)) + 1;
    // to exercise serialization
    if(numTasks > 1) numThreads = Math.max(numThreads, 2);
    System.out.printf(
      "Launching %s module in %s.%s with %d tasks and %d threads\n",
      module.getClass().getSimpleName(),
      testInfo.getTestClass().isPresent() ? testInfo.getTestClass().get().getSimpleName() : "ClassNotFound",
      testInfo.getTestMethod().isPresent() ? testInfo.getTestMethod().get().getName() : "methodNotFound",
      numTasks,
      numThreads);
    LaunchConfig config = new LaunchConfig(numTasks, numThreads);
    if(numThreads > 1) config.numWorkers(2);
    ipc.launchModule(module, config);
  }
}
