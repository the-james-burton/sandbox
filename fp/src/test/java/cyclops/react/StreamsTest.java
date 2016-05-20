package cyclops.react;

import java.io.Closeable;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.types.stream.reactive.SeqSubscriber;
import com.aol.cyclops.util.stream.StreamUtils;
import com.aol.cyclops.util.stream.Streamable;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.io.Files;

/**
 * Working demonstrations of cyclops-react streams as JUnit tests...
 * file:///home/jimsey/Development/projects/cyclops-react/user-guide/streams.adoc
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
            // TODO can't seem to do this with Try..?
            // Try.run(() -> resource.close()));
            () -> TestUtils.safeClose(resource));
    logger.info("result : {}", result);
    logger.info("errors : {}", errors);

  }

  // -------------------------------------------------

}
