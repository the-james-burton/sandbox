package reactor;

import java.lang.invoke.MethodHandles;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSubscriber<T> implements Subscriber<T> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void onSubscribe(Subscription s) {
    logger.info("onSubscribe:{}", s.toString());
  }

  @Override
  public void onNext(T t) {
    logger.info("onNext:{}", t.toString());
  }

  @Override
  public void onError(Throwable t) {
    logger.error("onError:{}", t.getMessage());
  }

  @Override
  public void onComplete() {
    logger.error("onComplete");
  }

}
