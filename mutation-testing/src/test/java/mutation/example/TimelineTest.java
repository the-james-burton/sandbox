package mutation.example;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * example from
 * http://www.codeaffine.com/2015/10/05/what-the-heck-is-mutation-testing/
 */
public class TimelineTest {

  private Timeline timeline;

  @Before
  public void setUp() {
    timeline = new Timeline();
  }

  @Test
  public void setFetchCount() {
    int expected = 5;
    timeline.setFetchCount(expected);
    int actual = timeline.getFetchCount();
    assertEquals(expected, actual);
  }

  @Test(expected = IllegalArgumentException.class)
  public void setFetchCountWithNonPositiveValue() {
    timeline.setFetchCount(0);
  }

  @Test
  public void initialState() {
    assertEquals(Timeline.DEFAULT_FETCH_COUNT, timeline.getFetchCount());
  }

}
