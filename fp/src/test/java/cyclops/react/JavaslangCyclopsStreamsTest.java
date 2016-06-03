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
    logger.info(TestUtils.getMethodName());

    javaslang.collection.List<Integer> jsSeq = javaslang.collection.List.of(1, 2, 3, 4, 5, 6);
    ReactiveSeq<Integer> traversable = Javaslang.traversable(jsSeq).stream();

    List<String> deleteBetween = StreamUtils.deleteBetween(traversable, 2, 4)
        .map(it -> it + "!!")
        .collect(Collectors.toList());

    logger.info("deleteBetween", deleteBetween);
  }

  @Test
  public void testFlatMap() throws Exception {
    logger.info(TestUtils.getMethodName());

    List<Integer> flatMapAnyM = ReactiveSeq.of(1, 2, 3)
        .flatMapAnyM(i -> Javaslang.traversable(javaslang.collection.List.of(i + 1, i + 2, i + 3)))
        .collect(Collectors.toList());

    logger.info("flatMapAnyM: {}", flatMapAnyM); // List[2,3,4,3,4,5,4,5,6]
  }
}
