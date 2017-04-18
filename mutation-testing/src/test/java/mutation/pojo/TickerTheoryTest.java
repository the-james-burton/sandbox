/**
 * The MIT License
 * Copyright (c) 2015 the-james-burton
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package mutation.pojo;

import org.junit.experimental.theories.DataPoint;

import javaslang.collection.CharSeq;
import utils.ObjectTheories;

public class TickerTheoryTest extends ObjectTheories {

  public static final CharSeq TICK1 = CharSeq.of("TICK1");

  public static final CharSeq TICK2 = CharSeq.of("TICK2");

  public static final CharSeq NAME1 = CharSeq.of("NAME1");

  public static final CharSeq NAME2 = CharSeq.of("NAME2");

  public static final ExchangeEnum AX = ExchangeEnum.ASX;

  public static final ExchangeEnum L = ExchangeEnum.LSE;

  @DataPoint
  public static final Ticker tickAAA = Ticker.of(TICK1, AX, NAME1);

  @DataPoint
  public static final Ticker tickAAB = Ticker.of(TICK1, AX, NAME2);

  @DataPoint
  public static final Ticker tickABA = Ticker.of(TICK1, L, NAME1);

  @DataPoint
  public static final Ticker tickBAA = Ticker.of(TICK2, AX, NAME1);

}
