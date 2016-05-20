package cyclops.react;

public class TestUtils {

  public static String getMethodName() {
    return Thread.currentThread().getStackTrace()[2].getMethodName();
  }

}
