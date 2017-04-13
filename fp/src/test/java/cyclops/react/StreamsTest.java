package cyclops.react;

import static java.util.stream.Collectors.*;

import java.io.Closeable;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.URL;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jooq.lambda.Window;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.Reducers;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.StreamUtils;
import com.aol.cyclops.control.Streamable;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.stream.HotStream;
import com.aol.cyclops.types.stream.future.FutureOperations;
import com.aol.cyclops.types.stream.reactive.ReactiveTask;
import com.aol.cyclops.types.stream.reactive.SeqSubscriber;
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

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    logger.info(name.getMethodName());
  }

  /**
   * StreamUtils provides a large range of additional operators for standard Java 8 Streams,
   * these include operators for batching & windowing, error handling and retrying,
   * scheduling, asyncrhonous execution, zipping, controlling emissions by time, appending,
   * deleting and rearranging Streams and more!
   */
  @Test
  public void testStreamUtils() {
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
    List<ListX<Integer>> sliding = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .sliding(2)
        .toList();

    logger.info("sliding : {}", sliding); // [[1,2],[2,3],[3,4],[4,5],[5,6]]

    List<ListX<Integer>> slidingWithIncrement = StreamUtils.sliding(
        Stream.of(1, 2, 3, 4, 5, 6), 3, 2)
        .collect(Collectors.toList());

    logger.info("slidingWithIncrement : {}", slidingWithIncrement); // [[1, 2, 3], [3, 4, 5], [5, 6]]

    List<ListX<Number>> grouped = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .map(n -> TestUtils.sometimesSlowFunction(n))
        .grouped(4)
        .toList();

    logger.info("grouped : {}", grouped); // [[1,2,3,4],[5,6]]

    // Batching returns a List whereas windowing returns a Streamable...
    List<Streamable<Number>> windowBySize = StreamUtils.windowByTime(
        ReactiveSeq.of(1, 2, 3, 4, 5, 6)
            .map(n -> TestUtils.sometimesSlowFunction(n)),
        20, TimeUnit.MICROSECONDS)
        .collect(toList());

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

    List<String> remove = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .remove(2)
        .map(it -> it + "!!")
        .toList();

    logger.info("remove : {}", remove); // [1!!, 3!!, 4!!, 5!!, 6!!]

  }

  /**
   * We can use head and tail to do recursive operations, similar to real
   * FP languages...
   */
  @Test
  public void testHeadAndTail() {
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
    List<String> exceptions = ReactiveSeq.of(1, 2, 3, 4)
        .map(i -> TestUtils.toStringMayThrowError(i))
        .recover(Exception.class, e -> e.getMessage())
        .toList();

    logger.info("exceptions : {}", exceptions);

  }

  @Test
  public void testRetry() {
    List<Integer> retry = ReactiveSeq.of(1, 2, 3)
        .retry(i -> TestUtils.sometimesThrowException(i))
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
  @Test(expected = RuntimeException.class)
  public void testRetryBackoff() {
    List<Integer> retry = ReactiveSeq.of(1, 2, 3, 4, 5, 6, 7, 8)
        .retry(i -> TestUtils.alwaysThrowException(i), 5, 20, TimeUnit.MILLISECONDS)
        .toList();

    logger.info("retry : {}", retry);
  }

  @Test
  public void testSchedule() {
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
    List<Tuple2<Integer, Integer>> zipOne = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .zip(ReactiveSeq.of(100, 200, 300, 400))
        .toList();

    logger.info("zipOne : {}", zipOne); // [(1, 100), (2, 200), (3, 300), (4, 400)]

    List<List<Integer>> zipWith = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .zip(ReactiveSeq.of(100, 200, 300, 400), (a, b) -> Arrays.asList(a, b))
        .toList();

    logger.info("zipWith : {}", zipWith); // [[1, 100], [2, 200], [3, 300], [4, 400]]

  }

  /**
   * The JDK Streams api has operators limit and skip as of Java 8. The naming of
   * these operators is relatively unusual compared with other languages where
   * take / drop is more common. JDK 9 looks set to introduce new operators such
   * as takeWhile & dropWile (maintaining the old limit and skip operators also).
   * cclops-react offers many of these operators already, although we currently
   * extend (like jOOλ) the JDK 8 naming convention and use limitWhile and skipWhile.
   * 
   * This test also shows how to reuse hot streams...
   */
  @Test
  public void testLimitSkip() {
    final Executor exec = Executors.newFixedThreadPool(1);

    HotStream<Integer> hotStream = ReactiveSeq
        .range(1, 50)
        .peek(i -> TestUtils.alwaysSlowFunction(i))
        .peek(i -> logger.info("producer:{}", i.toString()))
        .primedHotStream(exec);

    List<Integer> list = hotStream
        .connect()
        .limit(3)
        .toList();

    logger.info("list : {}", list);

    // can also use .forEach to consume the stream in real time...

    hotStream
        .connect()
        .limit(300, TimeUnit.MILLISECONDS)
        .forEach(i -> logger.info("limit for time:{}", i.toString()));

    hotStream
        .connect()
        .skipUntil(i -> i == 15)
        .limit(3)
        .forEach(i -> logger.info("skipUntil:{}", i.toString()));

  }

  /**
   * Convert to a Stream with the result of a reduction operation repeated
   * specified times.
   * 
   * Monoid is a term from category theory. In Java the signature of Stream
   * reduce is a monoid. In cyclops-react the Monoid class is used to
   * encapsulate the identity value and the accumulating function. There is
   * a Reducers class which has some useful Monoid instances for Integer
   * addition / multiplication, String concatonation etc.
   */
  @Test
  public void testCycle() {
    List<Integer> cycleMonoid = ReactiveSeq.of(1, 2, 2)
        .cycle(Reducers.toCountInt(), 4)
        .toList();

    logger.info("cycleMonoid : {}", cycleMonoid); // [3, 3, 3, 3]
  }

  /**
   * In addition to inhertiting flatMap from Stream, crossJoin, leftOuterJoin and
   * innerJoin from jOOλ, cyclops-react offers a number of additional flatMap methods
   * that accept a Function that returns a value that can be converted (implicitly)
   * to Stream.
   * The flatMapFile operator Streams the content of the returned File as a String. It
   * is syntax sugar for loading the File to a Stream of Strings inside the function 
   *  to the standard Stream flatMap method.
   */
  @Test
  public void testFiles() {
    // TODO - many flapMap* functions are not present on ReactiveSeq...

    Stream<String> stream = Stream.of("words.txt")
        .map(getClass().getClassLoader()::getResource)
        .peek(f -> logger.info(f.toString()))
        .map(URL::getFile);

    List<String> flatMapFile = StreamUtils
        .flatMapFile(stream, File::new)
        .map(w -> w + "!!")
        .collect(Collectors.toList());

    logger.info("flatMapFile : {}", flatMapFile); // []

    // possibly simpler method...

    StreamUtils.flatMapURL(ReactiveSeq.of("words.txt"), getClass().getClassLoader()::getResource)
        .map(w -> w + "##")
        .forEach(logger::info);

  }

  /**
   * crossJoin (inherited from jOOλ) joins two Streams by pairing every possible
   * combination of values from both Streams
   * 
   * The innerJoin operator (inherited from jOOλ) joins two Streams in a similar
   * manner to crossJoin but allows a filtering BiPredicate to be applied.
   * 
   * The leftOuterJoin retains all elements from the host ReactiveSeq and joins
   * them with elements in the supplied Stream where the predicate matches, where the 
   * predicate fails null is used.
   * 
   * The rightOuterJoin retains all elements from the supplied ReactiveSeq and
   * joins them with elements in the host Stream where the predicate matches, where
   * the predicate fails null is used.
   */
  @Test
  public void testJoin() {
    ReactiveSeq<Integer> sa = ReactiveSeq.of(1, 2, 3);
    ReactiveSeq<Integer> sb = ReactiveSeq.of(10, 20, 30);

    List<Tuple2<Integer, Integer>> crossJoin = sb.crossJoin(sa).toList();

    logger.info("crossJoin : {}", crossJoin); // [(10, 1), (10, 2), (10, 3), (20, 1), (20, 2), (20, 3), (30, 1), (30, 2), (30, 3)]

    sa = ReactiveSeq.of(1, 2, 3);
    sb = ReactiveSeq.of(10, 20, 30);

    List<Tuple2<Integer, Integer>> innerJoin = sb.innerJoin(sa, (a, b) -> b > 2).toList();

    logger.info("innerJoin : {}", innerJoin); // [(10, 3), (20, 3), (30, 3)]

    sa = ReactiveSeq.of(1, 2, 3);
    sb = ReactiveSeq.of(10, 20, 30);

    List<Tuple2<Integer, Integer>> leftOuterJoin = sb.leftOuterJoin(sa, (a, b) -> a > 20).toList();

    logger.info("leftOuterJoin : {}", leftOuterJoin); // [(10, null), (20, null), (30, 1), (30, 2), (30, 3)]

    sa = ReactiveSeq.of(1, 2, 3);
    sb = ReactiveSeq.of(10, 20, 30);

    List<Tuple2<Integer, Integer>> rightOuterJoin = sb.rightOuterJoin(sa, (a, b) -> b > 2).toList();

    logger.info("rightOuterJoin : {}", rightOuterJoin); // [(null, 1), (null, 2), (10, 3), (20, 3), (30, 3)]

  }

  /**
   * These are loops within loops.
   */
  // @Test
  // public void testForComprehensions() {

  // List<Integer> forEach2 = ReactiveSeq.of(1, 2, 3)
  // .forEach2(
  // a -> IntStream.of(10, 20, 30),
  // a -> b -> a + b)
  // .toList();
  //
  // logger.info("forEach2 : {}", forEach2); // [11, 21, 31, 12, 22, 32, 13, 23, 33]

  // TODO forEach3 does not appear to compile yet...
  // https://github.com/aol/cyclops-react/blob/master/src/test/java/com/aol/cyclops/streams/ForComprehensionsTest.java

  // ReactiveSeq.of(1, 2, 3)
  // .forEach3(
  // a -> IntStream.of(10, 20, 30),
  // a -> b -> IntStream.of(100, 200, 300),
  // a -> b -> c -> a + b + c)
  // .toList();
  //
  // logger.info("forEach3 : {}", forEach3);

  // }

  @Test
  public void testOnEmpty() {

    List<Integer> onEmptySwitchFalse = ReactiveSeq.of(4, 5, 6)
        .onEmptySwitch(() -> ReactiveSeq.of(1, 2, 3))
        .toList();

    logger.info("onEmptySwitchFalse : {}", onEmptySwitchFalse); // [4, 5, 6]

    List<Integer> onEmptySwitchTrue = ReactiveSeq.fromIntStream(IntStream.empty())
        .onEmptySwitch(() -> ReactiveSeq.of(1, 2, 3))
        .toList();

    logger.info("onEmptySwitchTrue : {}", onEmptySwitchTrue); // [1, 2, 3]

  }

  @Test
  public void testSingle() {

    // note that the ofType function also filters...

    Integer singleFalse = ReactiveSeq.of(2, "a")
        .ofType(Integer.class)
        .singleOptional()
        .orElse(1);

    logger.info("singleFalse : {}", singleFalse); // 1

    Integer singleTrue = ReactiveSeq.of("b")
        .ofType(Integer.class)
        .singleOptional()
        .orElse(1);

    logger.info("singleTrue : {}", singleTrue); // 1

  }

  /**
   * scanLeft performs a non-terminal foldLeft-like operation where the elements
   * in the Stream returned are the intermediate cumulative results. Like reduce
   * and fold the signature of scan matches a Monoid, cyclops-react supports
   * specifying Monoid instances as a parameter (see the Reducers class).
   * 
   * scanLeft starts from the left and applies the supplied function to each
   * value, storing the intermediate cumulative results in the new Stream.
   * 
   * scanRight can take advantage of cyclops-react Efficient Reversability for better performance.
   */
  @Test
  public void testScan() {

    List<Integer> scanLeft = ReactiveSeq.of("a", "ab", "abc")
        .map(str -> str.length())
        .scanLeft(0, (u, t) -> u + t)
        .toList();

    logger.info("scanLeft : {}", scanLeft); // [0, 1, 3, 6]

    List<Integer> scanRight = ReactiveSeq.of("a", "ab", "abc")
        .map(str -> str.length())
        .scanRight(0, (u, t) -> u + t)
        .toList();

    logger.info("scanRight : {}", scanRight); // [0, 3, 5, 6]

  }

  /**
   * In addition to operators on java.util.stream.Stream like anyMatch,
   * allMatch and noneMatch, cyclops-react offers operators such as xMatch,
   * endsWith and startsWith. 
   * 
   * The endsWith operator returns true if the Stream ends with the specified
   * iterable or Stream, otherwise it returns false.
   * 
   * The xMatch operator returns true if the supplied predicate matches
   * the supplied number of times.
   */
  @Test
  public void testAssertions() {

    boolean endsWith = ReactiveSeq.of(1, 2, 3, 4, 5, 6)
        .endsWith(Stream.of(5, 6));

    logger.info("endsWith : {}", endsWith); // true

    boolean xMatch = ReactiveSeq.of(1, 2, 3, 5, 6, 7).xMatch(3, i -> i > 4);

    logger.info("xMatch : {}", xMatch); // true

  }

  /**
   * foldLeft performs a terminal reduction operation, that starts with
   * an identity value and the start of the Stream, applying the identiy
   * value and first value to a user supplied accumulation function, the
   * second value is then applied to the result and so on until the end
   * of the Stream when the acummulated result is returned.
   */
  @Test
  public void testFold() {

    String foldLeft1 = Streamable.of("hello", "world")
        .foldLeft("", (a, b) -> a + ":" + b);

    logger.info("foldLeft1 : {}", foldLeft1); // ":hello:world"

    Integer foldLeft2 = ReactiveSeq.of(1, 2, 3)
        .foldLeft(0, (a, b) -> a + b);

    logger.info("foldLeft2 : {}", foldLeft2); // 6

    Integer foldLeft3 = StreamUtils.foldLeft(
        Stream.of(2, 4, 5),
        Reducers.toTotalInt());

    logger.info("foldLeft3 : {}", foldLeft3); // 11

  }

}
