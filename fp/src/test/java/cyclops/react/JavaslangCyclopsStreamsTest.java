package cyclops.react;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.javaslang.Javaslang;
import com.aol.cyclops.util.stream.StreamUtils;

public class JavaslangCyclopsStreamsTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void testDeleteBetween() {
    javaslang.collection.List<Integer> jsSeq = javaslang.collection.List.of(1, 2, 3, 4, 5, 6);
    ReactiveSeq<Integer> traversable = Javaslang.traversable(jsSeq).stream();

    List<String> result = StreamUtils.deleteBetween(traversable, 2, 4)
        .map(it -> it + "!!")
        .collect(Collectors.toList());
    logger.info("result: {}", result);
  }

}
