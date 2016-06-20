package reactor;

import java.lang.invoke.MethodHandles;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSubscription implements Subscription {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void request(long n) {
    logger.info("request:{}", n);
  }

  @Override
  public void cancel() {
    logger.info("cancel");
  }

}
