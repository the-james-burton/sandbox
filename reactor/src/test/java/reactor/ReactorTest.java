package reactor;

import java.lang.invoke.MethodHandles;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Dispatcher;
import reactor.core.processor.RingBufferProcessor;

public class ReactorTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    logger.info(name.getMethodName());
  }

  @Test
  public void testDispatcher() {
    // Initialize context and get default dispatcher
    Environment.initialize();

    // RingBufferDispatcher with 8192 slots by default
    Dispatcher dispatcher = Environment.sharedDispatcher();

    // Dispatch data asynchronously
    dispatcher.dispatch(1234,
        c -> logger.info(c.toString()),
        e -> logger.error(e.getMessage()));

    Environment.terminate();
  }

  @Test
  public void testAsyncProcessor() {
    // standalone async processor
    Processor<Integer, Integer> processor = RingBufferProcessor.create();

    // send data, will be kept safe until a subscriber attaches to the processor
    processor.onNext(1234);
    processor.onNext(5678);

    // consume integer data
    processor.subscribe(new Subscriber<Integer>() {

      public void onSubscribe(Subscription s) {
        // unbounded subscriber
        s.request(Long.MAX_VALUE);
      }

      public void onNext(Integer data) {
        logger.info("onNext: {}", data.toString());
      }

      public void onError(Throwable e) {
        logger.error("onError: {}", e.getMessage());
      }

      public void onComplete() {
        logger.info("onComplete");
      }
    });

    // Shutdown internal thread and call complete
    processor.onComplete();
  }

}
