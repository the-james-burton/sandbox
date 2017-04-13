package mutation;

public class MutationTarget {

  public boolean lessThan(int foo, int bar) {
    return foo < bar;
  }

  public boolean equalTo(int foo, int bar) {
    return foo == bar;
  }

  public int plus(int foo, int bar) {
    return foo + bar;
  }

  public int increment(int foo) {
    return ++foo;
  }

  public int negate(int foo) {
    return -foo;
  }

  public int plusOne(int foo) {
    int bar = 1;
    return foo + bar;
  }

  public Object foo() {
    return new Object();
  }

  private int woo = 0;

  private void setWooToTwo() {
    woo = 2;
  }

  public int plusWoo(int foo) {
    setWooToTwo();
    return foo + woo;
  }

  private int three() {
    return 3;
  }

  public int plusThree(int foo) {
    int wee = three();
    return foo + wee;
  }

}
