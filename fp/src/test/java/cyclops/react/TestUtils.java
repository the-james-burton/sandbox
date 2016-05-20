package cyclops.react;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtils {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static String getMethodName() {
    return Thread.currentThread().getStackTrace()[2].getMethodName();
  }

  public static String toStringMayThrowError(Integer i) {
    if (i == 2) {
      throw new RuntimeException("I hate you");
    }
    return i.toString();
  }

  /**
   * Swallows the checked exception and rethrows it as a RuntimeException
   * @param closable
   */
  public static void safeClose(Closeable closable) {
    try {
      closable.close();
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

}
