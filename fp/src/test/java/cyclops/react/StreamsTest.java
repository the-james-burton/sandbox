package cyclops.react;

import java.io.Closeable;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.Window;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.stream.HotStream;
import com.aol.cyclops.types.stream.future.FutureOperations;
import com.aol.cyclops.types.stream.reactive.ReactiveTask;
import com.aol.cyclops.types.stream.reactive.SeqSubscriber;
import com.aol.cyclops.util.stream.StreamUtils;
import com.aol.cyclops.util.stream.Streamable;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.io.Files;

/**
 * Working demonstrations of cyclops-react streams as JUnit tests...
 * https://github.com/aol/cyclops-react/blob/master/user-guide/streams.adoc
 *
 * @author the-james-burton
 */
public class StreamsTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * StreamUtils provides a large range of additional operators for standard Java 8 Streams,
   * these include operators for batching & windowing, error handling and retrying,
   * scheduling, asyncrhonous execution, zipping, controlling emissions by time, appending,
   * deleting and rearranging Streams and more!
   */
  @Test
  public void testStreamUtils() {
    logger.info(TestUtils.getMethodName());

    List<String> result = StreamUtils
        .deleteBetween(Stream.of(1, 2, 3, 4, 5, 6), 2, 4)
        .map(it -> it + "!!")
        .collect(Collectors.toList());
    logger.info("result: {}", result);
  }

  /**
   * Streamable is a class that represents something that can be Streamed repeatedly.
   */
  @Test
  public void testStreamable() {
    logger.info(TestUtils.getMethodName());

    Optional<Integer> result = Streamable
        .of(1, 2, 3)
        .map(i -> i + 1)
        .reduce((a, b) -> a + b);
    logger.info("result : {}", result.orElse(-1));
  }

  /**
   * HotStreams are streams that are actively flowing. They can be created via the
   * hotstream method on ReactiveSeq or in StreamUtils. They execute on a
   * single thread on a provided executor.
   */
  @Test
  public void testHotStreams() {
    logger.info(TestUtils.getMethodName());

    final Executor exec = Executors.newFixedThreadPool(1);
    ReactiveSeq.range(0, Integer.MAX_VALUE)
        .limit(5)
        .map(i -> i.toString())
        .peek(logger::info)
        .hotStream(exec);
  }

  /**
   * In the example below 5 entries will be written out on the HotStreams executing thread,
   * and 2 of those will also be written out on the current thread.
   */
  @Test
  public void testHotStreamsConnect() {
    logger.info(TestUtils.getMethodName());

    final Executor exec = Executors.newFixedThreadPool(1);

    ReactiveSeq.range(0, Integer.MAX_VALUE)
        .limit(5)
        .map(i -> i.toString())
        .peek(logger::info)
        .hotStream(exec)
        .connect()
        .limit(2)
        .forEach(next -> logger.info(next));
  }

  /**
   * In the example below 5,000 entries will be written out on the HotStreams
   * executing thread, the consuming thread will only emit one per second.
   * We connect and use a BlockingStream as a transfer queue, the producing
   * Stream will ultimately be slowed to the same rate as the consuming Stream.
   * 
   * TODO - https://github.com/aol/cyclops/issues/198
   */
  @Test
  public void testHotStreamsBackPressure() {
    logger.info(TestUtils.getMethodName());

    final Executor exec = Executors.newFixedThreadPool(1);
    final ArrayBlockingQueue<String> blockingQueue = Queues.newArrayBlockingQueue(3);

    ReactiveSeq.range(0, Integer.MAX_VALUE)
        .limit(5)
        .map(i -> i.toString())
        .peek(logger::info)
        .hotStream(exec)
        .connect(blockingQueue)
        .onePer(1, TimeUnit.SECONDS)
        .forEach(next -> logger.info(next));
  }

  /**
   * ReactiveSeq has a static subscriber method that returns a cyclops-react
   * reactive-streams Subscriber. That is a class that can subscribe to any
   * reactive-streams publisher (e.g. an RxJava Observable, Pivotal REACTOR Stream,
   * akka-stream etc).
   * 
   * ReactiveSeq extenads java.util.stream.Stream - so this also a standard, sequential Java 8 Stream.
   * 
   * TODO - I think the user guide is a little off here... maybe do a PR.
   */
  @Test
  public void testPublishSubscribe() {
    logger.info(TestUtils.getMethodName());

    final SeqSubscriber<Integer> subscriber = ReactiveSeq.subscriber();
    final ReactiveSeq<Integer> queue = ReactiveSeq.of(1, 2, 3, 4);

    queue.subscribe(subscriber);
    // note that this stream is a cyclops 'reactiveSeq'...
    Optional<Integer> result = subscriber.stream()
        .map(i -> i + 1)
        .reduce((a, b) -> a + b);
    logger.info("result : {}", result.orElse(-1));
  }

  /**
   * The forEachWithErrors operator allows users to iterate over a Stream providing
   * a consumer for the elements for the Stream a lá Stream.forEach, and a consumer
   * for the errors produced while processing the Stream.
   */
  @Test
  public void testForEachWithError() {
    logger.info(TestUtils.getMethodName());

    final List<String> result = Lists.newArrayList();
    final List<Throwable> errors = Lists.newArrayList();
    ReactiveSeq.of(1, 2, 3, 4)
        .map(i -> TestUtils.toStringMayThrowError(i))
        .forEachWithError(
            // TODO - possible/sensible to use PCollections here..?
            i -> result.add(i),
            e -> errors.add(e));
    logger.info("result : {}", result);
    logger.info("errors : {}", errors);
  }

  /**
   * The forEachEvent operator is similar to forEachWithErrors but also accepts
   * a Runnable that is run when the Stream has been completely consumed.
   */
  @Test
  public void testForEachEvent() throws Exception {
    logger.info(TestUtils.getMethodName());

    final File file = File.createTempFile("test", "txt");
    final Closeable resource = Files.newWriter(file, Charsets.UTF_8);
    final List<String> result = Lists.newArrayList();
    final List<Throwable> errors = Lists.newArrayList();

    ReactiveSeq.of(1, 2, 3, 4)
        .map(i -> TestUtils.toStringMayThrowError(i))
        .forEachEvent(
            i -> result.add(i),
            e -> errors.add(e),
            // TODO can't seem to do this with Try or ExceptionSoftener..?
            // Try.run(() -> resource.close()));
            () -> TestUtils.safeClose(resource));
    logger.info("result : {}", result);
    logger.info("errors : {}", errors);

  }

  /**
   * The reactive-streams based terminal operations can also be launched asynchronously,
   * first by using the futureOperations operator to provide an Executor that will
   * process the Stream. The futureOperations operator opens up a world of
   * asynchronously executed terminal operations. A large range of terminal operations
   * are provided and for each one a CompletbableFuture is returned.
   */
  @Test
  public void testFutureOperations() {
    logger.info(TestUtils.getMethodName());

    Executor exec = Executors.newFixedThreadPool(1);
    FutureOperations<Integer> terminalOps = StreamUtils.futureOperations(Stream.of(1, 2, 3), exec);

    // execute the collection & Stream evaluation on the provided executor
    CompletableFuture<List<Integer>> list = terminalOps.collect(Collectors.toList());

    List<Integer> result = list.join();

    logger.info("result : {}", result);

  }

  /**
   * Each of the async Future Operations for reactive-streams (forEachX,
   * forEachEvent etc), return a ReactiveTask object. This allows users
   * to check the status of Stream processing, to cancel it, to request
   * more elements to be processed from the Stream either synchronously
   * or asynchronously.
   */
  @Test
  public void testReactiveTask() {
    logger.info(TestUtils.getMethodName());

    Executor exec = Executors.newFixedThreadPool(1);
    List<Integer> result = Lists.newArrayList();
    ReactiveTask s = ReactiveSeq.of(1, 2, 3, 4)
        .futureOperations(exec)
        .forEachX(2, i -> {
          logger.info("computing {}", i);
          result.add(i);
        });

    logger.info("result : {}", result); // []
    s.block(); // wait until first 2 elements are processed
    logger.info("result : {}", result); // [1, 2]
    s.requestAll(); // trigger the remainder of the Stream processing asynchronously
    logger.info("result : {}", result); // [1, 2, 3, 4]

  }

  /**
   * Sliding produces a sliding view over a Stream, there are two sliding
   * operators - one that takes just the window size and another that takes
   * window size and the increment to be applied.
   * 
   * Batch / Window by size allows elements to be grouped as they flow through
   * the Stream into Lists or Streamables of the specified size.
   * 
   * Batch / Window by time group elements into either a List (Batch) or
   * Streamable (Window) based on the time bucket they pass through the Stream.
   * 
   * Much like batchBySize groups elements into Lists based on the specified
   * list size, and windowBySize organises streaming elements into Streamables
   * by time bucket- batchBySizeAndTime / windowBySizeAndTime populates Lists
   * (or Streamables) based on which ever criteria is met first. Should the
   * max size be reached the List / Streamable is ready to move down stream,
   * should the max time elaspe - ditto.
   */
  @Test
  public void testGroupingSlidingWindowing() {
    logger.info(TestUtils.getMethodName());

    List<ListX<Integer>> sliding = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .sliding(2)
        .toList();

    logger.info("sliding : {}", sliding); // [[1,2],[2,3],[3,4],[4,5],[5,6]]

    List<ListX<Integer>> slidingWithIncrement = StreamUtils.sliding(
        Stream.of(1, 2, 3, 4, 5, 6), 3, 2)
        .collect(Collectors.toList());

    logger.info("slidingWithIncrement : {}", slidingWithIncrement); // [[1, 2, 3], [3, 4, 5], [5, 6]]

    List<ListX<Integer>> grouped = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .map(n -> TestUtils.mayBeSlow(n))
        .grouped(4)
        .toList();

    logger.info("grouped : {}", grouped); // [[1,2,3,4],[5,6]]

    // Batching returns a List whereas windowing returns a Streamable...
    List<Streamable<Integer>> windowBySize = StreamUtils.windowByTime(
        ReactiveSeq.of(1, 2, 3, 4, 5, 6)
            .map(n -> TestUtils.mayBeSlow(n)),
        20, TimeUnit.MICROSECONDS)
        .collect(Collectors.toList());

    logger.info("windowBySize : {}", windowBySize); // [results will vary]

    // TODO function windowBySizeAndTime does not exist!

  }

  /**
   * jOOλ based windowing implements SQL windowing operations for Streams.
   * The jOOλ functions are exceptionally powerful and flexible, but also consume
   * the Stream. This means they will not perform as well as the simpler (but
   * less powerful) batchBy, windowBy and sliding functions in cyclops-react.
   * They are also not suitable for use in infinitely large Streams.
   */
  @Test
  public void testJOOlWindowing() {
    logger.info(TestUtils.getMethodName());

    String windowBySizeFormat = ReactiveSeq.of(1, 2, 4, 2, 3)
        .window(i -> i % 2)
        .map(w -> Tuple.tuple(
            w.value(),
            w.value() % 2,
            w.rowNumber(),
            w.rank(),
            w.denseRank(),
            w.toString()))
        .format();

    logger.info("windowBySizeFormat\n{}", windowBySizeFormat); // min: [1, 2, 2, 2, 1] max : [1, 2, 4, 2, 3]

    List<Optional<Integer>> windowBySize = ReactiveSeq.of(1, 2, 4, 2, 3)
        .window(i -> i % 2, Comparator.naturalOrder())
        .peek(i -> logger.info(i.toString()))
        .map(Window::max)
        .toList();

    // TODO why does the naturalOrder() seem to make this crazy? Surely 1,2,4,4,3 is correct?

    logger.info("windowBySize : {}", windowBySize); // min: [1, 2, 2, 2, 1] max : [1, 2, 4, 2, 3]

    String windowTuple = ReactiveSeq.of("a", "a", "a", "b", "c", "c", "d", "e")
        .window(Comparator.naturalOrder())
        .map(w -> Tuple.tuple(
            w.value(), // v0
            w.count(), // v1
            w.median(), // v2
            w.lead(), // v3
            w.lag(), // v4
            w.toString() // v5
        ))
        .format();

    logger.info("windowTuple:\n{}", windowTuple);
  }

  /**
   * self-explanatory stream modification functions...
   */
  @Test
  public void testStreamModification() {
    logger.info(TestUtils.getMethodName());

    List<String> prepend = StreamUtils.prepend(Stream.of(1, 2, 3), 100, 200, 300)
        .map(it -> it + "!!")
        .collect(Collectors.toList());

    logger.info("prepend : {}", prepend); // [100!!, 200!!, 300!!, 1!!, 2!!, 3!!]

    List<String> append = ReactiveSeq.of(1, 2, 3)
        .append(100, 200, 300)
        .map(it -> it + "!!")
        .toList();

    logger.info("append : {}", append); // [1!!, 2!!, 3!!, 100!!, 200!!, 300!!]

    List<String> insertAt = ReactiveSeq.of(1, 2, 3)
        .insertAt(1, 100, 200, 300)
        .map(it -> it + "!!")
        .collect(Collectors.toList());

    logger.info("insertAt : {}", insertAt); // [1!!, 100!!, 200!!, 300!!, 2!!, 3!!]

    List<String> deleteBetween = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .deleteBetween(2, 4)
        .map(it -> it + "!!")
        .toList();

    logger.info("deleteBetween : {}", deleteBetween); // ["1!!","2!!","5!!","6!!"]

  }

  /**
   * We can use head and tail to do recursive operations, similar to real
   * FP languages...
   */
  @Test
  public void testHeadAndTail() {
    logger.info(TestUtils.getMethodName());

    ReactiveSeq<Integer> s = ReactiveSeq.range(2, 100);
    List<Integer> testHeadAndTail = null;
    testHeadAndTail = TestUtils.sieve(s).toList();

    // [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97]
    logger.info("testHeadAndTail : {}", testHeadAndTail);

  }

  /**
   * 
   */
  @Test
  public void testExceptions() {
    logger.info(TestUtils.getMethodName());

    List<String> exceptions = ReactiveSeq.of(1, 2, 3, 4)
        .map(i -> TestUtils.toStringMayThrowError(i))
        .recover(Exception.class, e -> e.getMessage())
        .toList();

    logger.info("exceptions : {}", exceptions);

  }

  @Test
  public void testRetry() {
    logger.info(TestUtils.getMethodName());

    List<Integer> retry = ReactiveSeq.of(1, 2, 3, 4, 5, 6, 7, 8)
        .retry(i -> TestUtils.randomFails(i))
        .toList();

    logger.info("retry : {}", retry);
  }

  /**
   * Retry allows a function to be retried. By default retry occurs up
   * to 5 times with an exponential backoff.
   * 
   * Note: simple-react users should note that the implementation in LazyFutureStream
   * is a significantly more advanced asynchronous retry (making use of
   * Tomasz Nurkiewicz async retry library).
  
   * https://github.com/aol/cyclops-react/issues/220
   */
  @Test
  public void testRetryBackoff() {
    logger.info(TestUtils.getMethodName());

    List<Integer> retry = ReactiveSeq.of(1, 2, 3, 4, 5, 6, 7, 8)
        .retry(i -> TestUtils.alwaysThrowException(i), 5, 100, TimeUnit.MILLISECONDS)
        .toList();

    logger.info("retry : {}", retry);
  }

  @Test
  public void testSchedule() {
    logger.info(TestUtils.getMethodName());

    ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

    HotStream<Integer> stream = ReactiveSeq.of(1, 2, 3, 4)
        .peek(i -> logger.info("schedule: {}", i.toString()))
        .schedule("* * * * * ?", exec);

    List<Integer> scheduled = stream.connect()
        // .debounce(1, TimeUnit.SECONDS)
        .peek(i -> logger.info("hot: {}", i.toString()))
        .toList();

    logger.info("scheduled : {}", scheduled);

  }

  /**
   * onePer ensures that only one element is emitted per time period,
   * data is not lost, but rather queued and will be emitted when the
   * next time gate opens. For an operator that drops data see debounce.
   * 
   * simple-react’s LazyFutureStream is a parallel implementation of ReactiveSeq
   * 
   * Tip : The xPer operator works in a similar fashion but allows only
   * a specified number of elements through per time period. The elements
   * will be emtted as soon as they are available, which may cause the
   * emissions to bunch at the start of the time period.
   */
  @Test
  public void testOnePer() {
    logger.info(TestUtils.getMethodName());

    ReactiveSeq.iterate(0, it -> it + 1)
        .limit(10)
        .onePer(10, TimeUnit.MILLISECONDS)
        .map(i -> i + "!!")
        .peek(i -> logger.info(i.toString()))
        .toList();

  }

  /**
   * A number of the cyclops-react zip operators allow a custom zipper
   * to be supplied (typically a BiFunction that allows users to determine 
   * ow the Streams should be merged).
   */
  @Test
  public void testZip() {
    logger.info(TestUtils.getMethodName());

    List<Tuple2<Integer, Integer>> zipOne = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .zip(ReactiveSeq.of(100, 200, 300, 400))
        .toList();

    logger.info("zipOne : {}", zipOne); // [(1, 100), (2, 200), (3, 300), (4, 400)]

    List<List<Integer>> zipWith = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .zip(ReactiveSeq.of(100, 200, 300, 400), (a, b) -> Arrays.asList(a, b))
        .toList();

    logger.info("zipWith : {}", zipWith); // [[1, 100], [2, 200], [3, 300], [4, 400]]

  }

}
