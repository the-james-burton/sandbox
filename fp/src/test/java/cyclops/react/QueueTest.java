package cyclops.react;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.data.async.Queue;
import com.aol.cyclops.types.futurestream.LazyFutureStream;
import com.google.common.collect.Queues;

public class QueueTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Ignore
  @Test
  public void testQueueSingleThreaded() {
    Stream<Integer> stream = Stream.of(1, 2, 3);
    Queue<Integer> q = new Queue<Integer>(Queues.newLinkedBlockingQueue());
    q.fromStream(stream);

    Stream<Integer> dq = q.stream();

    Integer dequeued = q.stream()
        .limit(3)
        .map(i -> i + 2)
        .reduce(0, (acc, next) -> acc + next);

    logger.info("dequeued:{}", dequeued);

  }

  @Ignore
  @Test
  public void testQueueMutliThreaded() {
    final Executor exec = Executors.newFixedThreadPool(5);

    Stream<Integer> stream = ReactiveSeq.range(0, 10);
    Queue<Integer> q = new Queue<Integer>(Queues.newLinkedBlockingQueue());
    q.fromStream(stream);

    // LazyReact.parallelBuilder()
    LazyFutureStream
        .lazyFutureStream(q.stream())
        .withTaskExecutor(exec)
        .async()
        .then(m -> m + 1)
        .peek(m -> logger.info("peek: {}", m.toString()))
        .run(Collectors.toList());
  }

}
