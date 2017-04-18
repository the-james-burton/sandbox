package mutation.simple;

import static org.assertj.core.api.Assertions.*;

import org.junit.Test;

import mutation.simple.MutationTarget;

public class MutationTargetTest {

  private MutationTarget target = new MutationTarget();

  @Test
  public void testLessThan() {
    assertThat(target.lessThan(4, 8)).isTrue();
    assertThat(target.lessThan(8, 4)).isFalse();
    assertThat(target.lessThan(8, 8)).isFalse();
  }

  @Test
  public void testEqualTo() {
    assertThat(target.equalTo(8, 8)).isTrue();
    assertThat(target.equalTo(8, 4)).isFalse();
  }

  @Test
  public void testPlus() {
    assertThat(target.plus(4, 8)).isEqualTo(12);
  }

  @Test
  public void testIncrement() {
    assertThat(target.increment(7)).isEqualTo(8);
  }

  @Test
  public void testNegate() {
    assertThat(target.negate(8)).isEqualTo(-8);
  }

  @Test
  public void testPlusOne() {
    assertThat(target.plusOne(7)).isEqualTo(8);
  }

  @Test
  public void testFoo() {
    Object foo = target.foo();
    assertThat(foo).isInstanceOf(Object.class);
  }

  @Test
  public void testPlusWoo() {
    assertThat(target.plusWoo(6)).isEqualTo(8);
  }

  @Test
  public void testPlusThree() {
    assertThat(target.plusThree(5)).isEqualTo(8);
  }

}
