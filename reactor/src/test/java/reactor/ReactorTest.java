package reactor;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
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
import reactor.core.reactivestreams.SubscriberFactory;
import reactor.fn.BiConsumer;
import reactor.fn.Consumer;
import reactor.fn.Function;
import reactor.fn.Supplier;
import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import reactor.io.buffer.Buffer;
import reactor.io.codec.json.JsonCodec;
import reactor.rx.BiStreams;
import reactor.rx.Promise;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.action.Control;
import reactor.rx.broadcast.Broadcaster;

public class ReactorTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final ExecutorService exec = Executors.newFixedThreadPool(5);

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    logger.info("start:{}", name.getMethodName());
    Environment.initializeIfEmpty();
  }

  @After
  public void after() throws InterruptedException {
    Thread.sleep(100);
    logger.info("end:{}", name.getMethodName());
  }

  @Test
  public void testDispatcher() {

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
    processor.subscribe(new LoggingSubscriber<Integer>("subscriber") {

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

    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));
    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));
    sub.consume(s -> logger.info("thread:{}, data:{}", Thread.currentThread(), s));

    pub.subscribe(pro);

    // push more data during runtime...
    for (int i = 0; i < 3; i++) {
      pro.onNext(TestUtils.randomInteger() * 10);
    }

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

    // seems to be necessary...
    Thread.sleep(100);

  }

  @Test
  public void testSubscriberRequest() throws Exception {
    Stream<Integer> stream = Streams.just(10, 20, 30, 40);
    LoggingSubscriber<Integer> subscriber = new LoggingSubscriber<Integer>("subscriber");
    stream.subscribe(subscriber);
    subscriber.request(4);
  }

  @Test
  public void testSubscriberFactory() throws Exception {
    Stream<Integer> stream = Streams.just(10, 20, 30, 40);
    Subscriber<Integer> subscriber = SubscriberFactory.unbounded(
        (a, b) -> logger.info("dataConsumer:({},{})", a.toString(), b.toString()),
        e -> logger.info("errorConsumer:{}", e.getMessage()),
        na -> logger.info("completeConsumer:{}"));

    stream.subscribe(subscriber);
  }

  @Test
  public void testLifting() throws Exception {
    Stream<Integer> s1 = Streams.just(10, 20, 30, 40);
    // Stream<Integer> s1 = Streams.from(Arrays.asList(10, 20, 30, 40));
    Stream<String> s2 = s1.map(TestUtils::transformToString);

    LoggingSubscriber<Integer> s1sub = new LoggingSubscriber<Integer>("s1sub");
    LoggingSubscriber<String> s2sub = new LoggingSubscriber<String>("s2sub");

    s1.subscribe(s1sub); // subscriber1 will see the data A unaltered
    s2.subscribe(s2sub); // subscriber2 will see the data B after transformation from A.

    // Note theat these two subscribers will materialize independent stream pipelines, a process we also call lifting
    s1sub.request(4);
    s2sub.request(4);

  }

  @Test
  public void testDispatchOn() throws Exception {
    Stream<String> st = Streams.just("Hello ", "World", "!");

    st.dispatchOn(Environment.cachedDispatcher())
        .map(String::toUpperCase)
        .consume(s -> logger.info("{} greeting = {}", Thread.currentThread(), s));
  }

  @Test
  public void testSubscribeOn() throws Exception {
    Streams
        .range(1, 10)
        .process(RingBufferProcessor.create())
        .subscribeOn(Environment.workDispatcher())
        .capacity(1)
        .consume(m -> logger.info(m.toString()));
  }

  @Test
  public void testBroadcaster() throws Exception {
    // TODO might be gone in 2.5...
    Broadcaster<String> sink = Broadcaster.create(Environment.get());

    sink.map(String::toUpperCase)
        .consume(s -> logger.info("{} greeting = {}", Thread.currentThread(), s));

    sink.onNext("Hello World!");
  }

  @Test
  public void testTwoPipelines() throws Exception {

    Stream<String> stream = Streams.just("a", "b", "c", "d", "e", "f", "g", "h");

    // prepare two unique pipelines
    Stream<String> actionChain1 = stream.map(String::toUpperCase).filter(w -> w.equals("C"));
    Stream<Long> actionChain2 = stream.dispatchOn(Environment.sharedDispatcher()).take(5).count();

    actionChain1.consume(m -> logger.info("chain one:{}", m)); // start chain1
    Control c = actionChain2.consume(m -> logger.info("chain two:{}", m)); // start chain2

    // c.cancel(); // force this consumer to stop receiving data
  }

  @Test
  public void testSharedStream() throws Exception {

    Stream<String> stream = Streams.just("a", "b", "c", "d", "e", "f", "g", "h");

    // prepare a shared pipeline
    Stream<String> sharedStream = stream.observe(m -> logger.info("observe:{}", m)).broadcast();

    // prepare two unique pipelines
    Stream<String> actionChain1 = sharedStream.filter(w -> w.equals("c")).map(String::toUpperCase);
    Stream<Long> actionChain2 = sharedStream.take(5).count();

    actionChain1.consume(m -> logger.info("chain one:{}", m)); // start chain1
    actionChain2.consume(m -> logger.info("chain two:{}", m)); // start chain2

  }

  @Test
  public void testStreamFunctions() throws Exception {

    Streams
        .range(1, 10)
        .map(n -> "#" + n)
        .consume(logger::info);

    Streams
        .range(1, 5)
        .flatMap(n -> Streams.range(1, n).subscribeOn(Environment.workDispatcher()))
        .map(n -> "!" + n)
        .consume(
            logger::info,
            e -> logger.info("exception:{}", e.getMessage()),
            na -> logger.info("complete"));
  }

  @Test
  public void testPromise() throws Exception {

    // NOTE Blocking is considered an anti-pattern in Reactor!
    Promise<List<Long>> result = Streams
        .range(1, 10)
        .subscribeOn(Environment.workDispatcher())
        .toList();

    logger.info("result:{}", result.await());
    result.onSuccess(r -> logger.info("result:{}", r));
  }

  @Test
  public void testBuffer() throws Exception {
    long timeout = 100;
    final int batchsize = 4;
    CountDownLatch latch = new CountDownLatch(1);

    final Broadcaster<Long> streamBatcher = Broadcaster.<Long> create();
    streamBatcher
        .buffer(batchsize, timeout, TimeUnit.MILLISECONDS)
        .consume(i -> {
          logger.info(i.toString());
          latch.countDown();
        });

    Streams.range(1, 10).consume(i -> streamBatcher.onNext(i));
    Thread.sleep(200);
    Streams.range(1, 3).consume(i -> streamBatcher.onNext(i));
    Thread.sleep(200);
    Streams.range(11, 20).consume(i -> streamBatcher.onNext(i));

    latch.await(2, TimeUnit.SECONDS);
  }

  @Test
  public void testWindow() throws Exception {
    // create a list of 1000 numbers and prepare a Stream to read it
    Stream<Long> sensorDataStream = Streams.range(0, 1000);

    // wait for all windows of 100 to finish
    CountDownLatch endLatch = new CountDownLatch(1000 / 100);

    Control controls = sensorDataStream
        .window(100)
        .consume(window -> {
          logger.info("new window");
          window.reduce(Long.MAX_VALUE, (acc, next) -> Math.min(acc, next))
                .finallyDo(o -> endLatch.countDown())
                .consume(i -> logger.info("min:{}", i));
        });

    endLatch.await(10, TimeUnit.SECONDS);
    logger.info(controls.debug().toString());

    assertThat(endLatch.getCount(), is(0l));

  }
  
  @Test
  public void testCapacity() {
    Stream<Long> st = Streams.range(0,  10);

    // TODO capacity doesn't see to make a difference here...
    st.dispatchOn(Environment.sharedDispatcher())
      .capacity(3) 
      .observe(m -> logger.info("observe:{}", m))
      .consume(m -> logger.info("consume:{}", m));
    
    Streams
    .range(1, 10)
    .process(RingBufferProcessor.create()) 
    .subscribeOn(Environment.workDispatcher()) 
    .capacity(3)
    .consume(m -> logger.info("consume:{}", m));
  }
  
  @Test
  public void testImplicitBuffer() throws Exception {
    Streams.just(1,2,3,4,5)
    .buffer(2) 
    //onOverflowBuffer()
    .capacity(4) 
    .consume(m -> logger.info("consume:{}", m));


  Streams.just(1,2,3,4,5)
    .dispatchOn(Environment.cachedDispatcher()) 
    //onOverflowBuffer()
    .dispatchOn(Environment.cachedDispatcher()) 
    .consume(m -> logger.info("consume:{}", m));
  }
  
  @Test
  public void testEvolve1() throws Exception {
    logger.info("get:{}", TestUtils.get("James").await());
    
    TestUtils.allFriends("James")
    .consume(m -> logger.info("allFriends:{}", m));

    TestUtils.filteredFind("James")
    .consume(m -> logger.info("filteredFind:{}", m));
}
  
}
