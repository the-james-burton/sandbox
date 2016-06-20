package reactor;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import reactor.core.Dispatcher;
import reactor.core.processor.RingBufferProcessor;
import reactor.core.processor.RingBufferWorkProcessor;
import reactor.fn.BiConsumer;
import reactor.fn.Consumer;
import reactor.fn.Function;
import reactor.fn.Supplier;
import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import reactor.io.buffer.Buffer;
import reactor.io.codec.json.JsonCodec;
import reactor.rx.BiStreams;
import reactor.rx.Stream;
import reactor.rx.Streams;

public class ReactorTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final ExecutorService exec = Executors.newFixedThreadPool(5);

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
  public void testAsyncProcessor() throws InterruptedException {

    // standalone async processor
    Processor<Integer, Integer> processor = RingBufferProcessor.create();

    // send data, will be kept safe until a subscriber attaches to the processor
    for (int i = 0; i < 3; i++) {
      processor.onNext(TestUtils.randomInteger());
    }

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

    Thread.sleep(100);

    // Shutdown internal thread and call complete
    // WARNING - this will kill the thread...
    // processor.onComplete();

  }

  @Test
  public void testBiConsumer() throws Exception {

    // Now in Java 8 style for brievety
    Function<Integer, String> transformation = integer -> "BiConsumer-" + integer;

    Supplier<Integer> supplier = () -> TestUtils.randomInteger();

    BiConsumer<Consumer<String>, String> biConsumer = (callback, value) -> {
      for (int i = 0; i < 3; i++) {
        // lazy evaluate the final logic to run
        callback.accept(value);
      }
    };

    // note how the execution flows from supplier to biconsumer
    biConsumer.accept(
        m -> logger.info("{}", m.toString()),
        transformation.apply(supplier.get()));

  }

  @Test
  public void testTuple2() throws Exception {
    Function<Integer, String> transformation = integer -> "Tuple2-" + integer;

    Supplier<Integer> supplier = () -> TestUtils.randomInteger();

    Consumer<Tuple2<Consumer<String>, String>> biConsumer = tuple -> {
      for (int i = 0; i < 3; i++) {
        // Correct typing, compiler happy
        tuple.getT1().accept(tuple.getT2());
      }
    };

    biConsumer.accept(
        Tuple.of(
            m -> logger.info("{}", m.toString()),
            transformation.apply(supplier.get())));

  }

  @Test
  public void testRingBufferProcessor() throws Exception {
    Processor<Integer, Integer> pro = RingBufferProcessor.create();
    Stream<Integer> sub = Streams.wrap(pro);

    // initial data in the stream...
    Stream<Integer> pub = Streams.from(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

    // pro.onSubscribe(new LoggingSubscription());

    // for (int i = 0; i < 3; i++) {
    // pro.onNext(TestUtils.randomInteger());
    // }

    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));
    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));
    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));

    pub.subscribe(pro);

    // push more data during runtime...
    for (int i = 0; i < 3; i++) {
      pro.onNext(TestUtils.randomInteger() * 10);
    }

    Thread.sleep(1000);

  }

  @Test
  public void testRingBufferWorkProcessor() throws Exception {
    Processor<Integer, Integer> pro = RingBufferWorkProcessor.create();
    Stream<Integer> sub = Streams.wrap(pro);
    Stream<Integer> pub = Streams.from(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));
    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));
    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));

    pub.subscribe(pro);

    Thread.sleep(1000);

  }

  @Test
  public void testCodec() throws Exception {

    // given: 'A JSON codec'
    JsonCodec<Map<String, Object>, Object> codec = new JsonCodec(Map.class);
    CountDownLatch latch = new CountDownLatch(1);

    // when: 'The decoder is passed some JSON'
    final Map<String, Object> asyncDecoded = Maps.newHashMap();

    Consumer<Map<String, Object>> codecConsumer = new Consumer<Map<String, Object>>() {
      @Override
      public void accept(Map<String, Object> t) {
        logger.info("codecConsumer:{}", t);
        asyncDecoded.putAll(t);
        latch.countDown();
      }
    };

    Function<Buffer, Map<String, Object>> asyncDecoder = codec.decoder(codecConsumer);
    Function<Buffer, Map<String, Object>> syncDecoder = codec.decoder();

    Map<String, Object> async = asyncDecoder.apply(Buffer.wrap("{\"a\": \"alpha\"}"));
    Map<String, Object> sync = syncDecoder.apply(Buffer.wrap("{\"a\": \"beta\"}"));

    // then: 'The decoded maps have the expected entries'
    latch.await();

    logger.info("sync:{}", sync);
    logger.info("asyncDecoded:{}", asyncDecoded);

  }

  @Test
  public void testStreams() throws Exception {
    Environment.initialize();

    // find the top 10 words used in a list of Strings
    Streams.from(SandboxConstants.passage
        .toLowerCase()
        .replace(",", " ")
        .replace(".", " ")
        .replace(";", " ")
        .replace("-", " ")
        .split(" "))
        .dispatchOn(Environment.sharedDispatcher())
        .flatMap(sentence -> Streams
            .from(sentence.split(" "))
            .dispatchOn(Environment.cachedDispatcher())
            .filter(word -> !word.trim().isEmpty())
            .observe(word -> logger.info("word:{}", word)))
        .map(word -> Tuple.of(word, 1))
        .window(1, TimeUnit.SECONDS)
        .flatMap(words -> BiStreams.reduceByKey(words, (prev, next) -> prev + next)
            .sort((wordWithCountA, wordWithCountB) -> -wordWithCountA.t2.compareTo(wordWithCountB.t2))
            .take(10)
            .finallyDo(event -> logger.info("---- window complete! ----")))
        .consume(
            wordWithCount -> logger.info(wordWithCount.t1 + ": " + wordWithCount.t2),
            error -> logger.error("", error));

    // TODO how to wait for it to complete?
    Thread.sleep(1000);
  }

}
