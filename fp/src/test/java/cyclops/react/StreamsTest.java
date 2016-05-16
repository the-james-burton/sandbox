package cyclops.react;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.util.stream.StreamUtils;

public class StreamsTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void testStreams() {
    List<String> result = StreamUtils.deleteBetween(Stream.of(1, 2, 3, 4, 5, 6), 2, 4)
        .map(it -> it + "!!")
        .collect(Collectors.toList());
    logger.info("result: {}", result);
  }

}
