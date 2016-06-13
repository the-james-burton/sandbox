package cyclops.react;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Random;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.types.stream.HeadAndTail;

public class TestUtils {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Random random = new Random();

  /** 
   * converts an integer to string, but might throw a RuntimeException...
   * @param <T>
   * @param i integer
   * @return i.toString() or RuntimeException
   */
  public static <T extends Number> String toStringMayThrowError(T i) {
    if (Math.random() < 0.5) {
      throw new RuntimeException("I hate you");
    }
    return i.toString();
  }

  /**
   * either multiplies the given number by 2 or throws RuntimeException...
   * @param i integer
   * @return i * 2 or RuntimeException
   */
  public static <T extends Number> T sometimesThrowException(T i) {
    if (Math.random() < 0.5) {
      logger.info("{}: simulated fail", i.toString());
      throw new RuntimeException("I hate you!");
    }
    logger.info("{}: success", i);
    return (T) new Integer(i.intValue() * 2);
  }

  /**
   * always throws a RuntimeException
   * @param i 
   * @return
   */
  public static <T> T alwaysThrowException(T i) {
    logger.info("{}: failed", i.toString());
    throw new RuntimeException("I hate you!");
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
  public static <T> T sometimesSlowFunction(T n) {
    if (Math.random() < 0.5) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return n;
  }

  /**
   * 
   */
  public static <T> T transform(T t) {
    logger.info("transforming: {}", t.toString());
    return t;
  }

  /**
   * Given an integer will return the same integer, but with a delay
   * to simulate a longer running process
   * @param n
   * @return n
   * @throws InterruptedException
   */
  public static <T> T alwaysSlowFunction(T n) {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
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

  public static Supplier<Integer> supplyMessage() {
    return () -> random.nextInt(10);
  }

}
