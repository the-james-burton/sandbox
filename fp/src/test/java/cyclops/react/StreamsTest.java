package cyclops.react;

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
import com.aol.cyclops.util.stream.StreamUtils;
import com.aol.cyclops.util.stream.Streamable;

/**
 * My own learning from...
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

    Executor exec = Executors.newFixedThreadPool(1);
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

    Executor exec = Executors.newFixedThreadPool(1);

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
   * TODO - I don't think this works, raise issue with cyclops-react
   */
  @Test
  public void testHotStreamsBackPressure() {

    Executor exec = Executors.newFixedThreadPool(1);
    ArrayBlockingQueue<String> blockingQueue = new ArrayBlockingQueue<String>(3);

    ReactiveSeq.range(0, Integer.MAX_VALUE)
        .limit(500)
        .map(i -> i.toString())
        .peek(logger::info)
        .hotStream(exec)
        .connect(blockingQueue)
        .onePer(1, TimeUnit.SECONDS)
        .forEach(next -> logger.info(next));
  }

}
