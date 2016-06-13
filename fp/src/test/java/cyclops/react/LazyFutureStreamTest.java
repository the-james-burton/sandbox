package cyclops.react;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.types.futurestream.LazyFutureStream;

public class LazyFutureStreamTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void testGenerate() {

    final Executor exec = Executors.newFixedThreadPool(5);

    // TODO why does .run() not work unless a collector is provided?

    LazyFutureStream
        .generate(TestUtils.supplyMessage())
        // .of(1, 2, 3, 4, 5)
        .withTaskExecutor(exec)
        .map(m -> m + 1)
        .map(m -> TestUtils.transform(m))
        .peek(m -> logger.info("trying: {}", m.toString()))
        .capture(e -> logger.error("error: {}", e.getMessage()))
        // .consume(r -> logger.info("{}", r.toString()))
        .limit(10)
        .run(Collectors.toList());
    // .run();
    // .forEach(m -> logger.info("forEach: {}", m.toString()));
  }

}
