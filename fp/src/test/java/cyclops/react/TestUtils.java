package cyclops.react;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.types.stream.HeadAndTail;

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

  /**
   * Given an integer will return the same integer, but possibly with a delay,
   * to simulate a longer running process
   * @param n
   * @return n
   * @throws InterruptedException
   */
  public static Integer mayBeSlow(Integer n) {
    if (n == 6) {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return n;
  }

  /**
   * recursive function to compute the Sieve of Eratosthenes using streams
   * based on the cyclops-react streams user guide
   * @param s the stream of numbers to sieve
   * @return the numbers sieved
   */
  public static ReactiveSeq<Integer> sieve(ReactiveSeq<Integer> s) {
    HeadAndTail<Integer> ht = s.headAndTail();

    // if empty then return empty...
    // TODO rewrite using native guard support?
    if (!ht.isHeadPresent()) {
      return ReactiveSeq.of();
    }

    // otherwise return the head plus the tail with all divisors of head removed...
    return ReactiveSeq.of(ht.head())
        .appendStream(sieve(ht.tail()
            .filter(n -> n % ht.head() != 0)));
  }

}
