package mutation.example;

/**
 * example from
 * http://www.codeaffine.com/2015/10/05/what-the-heck-is-mutation-testing/
 */
public class Timeline {

  static final int DEFAULT_FETCH_COUNT = 10;
  private int fetchCount;

  public Timeline() {
    fetchCount = DEFAULT_FETCH_COUNT;
  }

  public void setFetchCount(int fetchCount) {
    if (fetchCount <= 0) {
      String msg = "Argument 'fetchCount' must be a positive value.";
      throw new IllegalArgumentException(msg);
    }
    this.fetchCount = fetchCount;
  }

  public int getFetchCount() {
    return fetchCount;
  }

}
