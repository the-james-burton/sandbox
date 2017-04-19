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

import static mutation.pojo.TickerTheoryTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import javaslang.collection.CharSeq;

public class TickerTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ObjectMapper json = new ObjectMapper();

  @Test
  public void testExchangeCompareTo() {
    // make sure the ordering is correct in our enum...
    assertThat(ExchangeEnum.ASX).isLessThan(ExchangeEnum.LSE);
  }

  @Test
  public void testConstructorProperties() {
    // check that the constructor correctly sets all properties...
    assertThat(tickerAAA).hasFieldOrPropertyWithValue("ric", CharSeq.of(String.format("%s.%s", TICK1, AX.getExtension())));
    assertThat(tickerAAA).hasFieldOrPropertyWithValue("symbol", TICK1);
    assertThat(tickerAAA).hasFieldOrPropertyWithValue("exchange", AX);
  }

  @Test
  public void testNonJsonConstructor() {
    Ticker ticker = new Ticker(tickerAAA.getRic(), tickerAAA.getName());
    assertThat(ticker.getExchange()).isEqualTo(tickerAAA.getExchange());
    assertThat(ticker.getExchangeAsString()).isEqualTo(tickerAAA.getExchange().toString());
    assertThat(ticker.getNameAsString()).isEqualTo(tickerAAA.getName().toString());
    assertThat(ticker.getRicAsString()).isEqualTo(tickerAAA.getRic().toString());
    assertThat(ticker.getSymbolAsString()).isEqualTo(tickerAAA.getSymbol().toString());

    // assertj can't do CharSeq...
    assertTrue(ticker.getName().equals(tickerAAA.getName()));
    assertTrue(ticker.getRic().equals(tickerAAA.getRic()));
    assertTrue(ticker.getSymbol().equals(tickerAAA.getSymbol()));

  }

  @Test
  public void testHashCode() {
    // check that the hash code is consistent across objects...
    assertThat(tickerAAA.hashCode()).isEqualTo(new Ticker(TICK1, AX, NAME1).hashCode());
    assertThat(new Ticker(TICK1, AX, NAME1).hashCode()).isEqualTo(tickerAAA.hashCode());

    // check that all fields are part of the hash code...
    assertThat(tickerAAA.hashCode()).isNotEqualTo(tickerAAB.hashCode());
    assertThat(tickerAAA.hashCode()).isNotEqualTo(tickerABA.hashCode());
    assertThat(tickerAAA.hashCode()).isNotEqualTo(tickerBAA.hashCode());
  }

  @Test
  public void testEquals() {
    // reflexive...
    assertThat(tickerAAA).isEqualTo(tickerAAA);

    // symmetric...
    assertThat(tickerAAA).isEqualTo(new Ticker(TICK1, AX, NAME1));
    assertThat(new Ticker(TICK1, AX, NAME1)).isEqualTo(tickerAAA);

    // consistent (same checks again)...
    assertThat(tickerAAA).isEqualTo(new Ticker(TICK1, AX, NAME1));
    assertThat(new Ticker(TICK1, AX, NAME1)).isEqualTo(tickerAAA);

    // transitive...
    Ticker ABC_AX2 = new Ticker(TICK1, AX, NAME1);
    Ticker ABC_AX3 = new Ticker(TICK1, AX, NAME1);

    assertThat(tickerAAA).isEqualTo(ABC_AX2);
    assertThat(ABC_AX2).isEqualTo(ABC_AX3);
    assertThat(tickerAAA).isEqualTo(ABC_AX3);

    // check not equal to null...
    assertThat(tickerAAA).isNotEqualTo(null);

    // check all properties are included in equals...
    assertThat(tickerAAA).isNotEqualTo(tickerAAB);
    assertThat(tickerAAA).isNotEqualTo(tickerABA);
    assertThat(tickerAAA).isNotEqualTo(tickerBAA);
  }

  @Test
  public void testCompareTo() {

    // check that all fields participate in compareTo...
    assertThat(tickerAAA).isLessThan(tickerAAB);
    assertThat(tickerAAA).isLessThan(tickerABA);
    assertThat(tickerAAA).isLessThan(tickerBAA);

    // check that the reverse is also true...
    assertThat(tickerAAB).isGreaterThan(tickerAAA);
    assertThat(tickerABA).isGreaterThan(tickerAAA);
    assertThat(tickerBAA).isGreaterThan(tickerAAA);
  }

  @Test
  public void testJsonSerialise() throws IOException {
    String expected = json.writeValueAsString(tickerAAA);
    logger.info(expected);
    Ticker ticker = json.readValue(expected, Ticker.class);
    assertThat(ticker.toString()).isEqualTo(expected);
  }

}
