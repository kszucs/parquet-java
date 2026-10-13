/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.internal.column.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Mostly ported from Arrow C++'s {@code TestCDC.RollingHashMaskCalculation} and {@code ChunkSizeParameterValidation}. */
public class TestRollingHashMask {

  private static final long MIN_SIZE = 256 * 1024L;
  private static final long MAX_SIZE = 1024 * 1024L;

  @Test
  public void maskMatchesTheReferenceForEachNormalizationLevel() {
    assertThat(RollingHashMask.calculate(MIN_SIZE, MAX_SIZE, 0)).isEqualTo(0xFFFE000000000000L);
    assertThat(RollingHashMask.calculate(MIN_SIZE, MAX_SIZE, 1)).isEqualTo(0xFFFC000000000000L);
    assertThat(RollingHashMask.calculate(MIN_SIZE, MAX_SIZE, 2)).isEqualTo(0xFFF8000000000000L);
    assertThat(RollingHashMask.calculate(MIN_SIZE, MAX_SIZE, 3)).isEqualTo(0xFFF0000000000000L);
    assertThat(RollingHashMask.calculate(MIN_SIZE, MAX_SIZE, -1)).isEqualTo(0xFFFF000000000000L);
  }

  @Test
  public void maskMatchesTheReferenceAtTheEdgesOfItsRange() {
    assertThat(RollingHashMask.calculate(0, 32, 0)).isEqualTo(0x8000000000000000L);
    assertThat(RollingHashMask.calculate(0, 64, 0)).isEqualTo(0xC000000000000000L);
    assertThat(RollingHashMask.calculate(0, 16, -1)).isEqualTo(0x8000000000000000L);
    // A zero target clamps to no bits rather than minus one.
    assertThat(RollingHashMask.calculate(0, 8, -1)).isEqualTo(0x8000000000000000L);
    assertThat(RollingHashMask.calculate(128, 384, -59)).isEqualTo(0xFFFFFFFFFFFFFFFEL);
  }

  @Test
  public void rejectsNegativeMinimum() {
    assertThatThrownBy(() -> RollingHashMask.calculate(-1, 1024, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid content defined chunking minimum chunk size (negative): -1");
  }

  @Test
  public void rejectsRangeThatIsNotAscending() {
    assertThatThrownBy(() -> RollingHashMask.calculate(1024, 512, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid content defined chunking size range: maximum chunk size (512) must be greater "
            + "than minimum chunk size (1024)");
    assertThatThrownBy(() -> RollingHashMask.calculate(32, 32, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid content defined chunking size range: maximum chunk size (32) must be greater "
            + "than minimum chunk size (32)");
  }

  /** The mask needs at least one bit, so the size range must be at least 32, 64 or 128 for level 0, 1 or 2. */
  @ParameterizedTest
  @CsvSource({"0, 16, 0", "32, 48, 0", "1, 33, 1", "0, 123, 2", "128, 384, -60"})
  public void rejectsSizeRangeTooNarrowForTheNormalizationLevel(long min, long max, int normLevel) {
    assertThatThrownBy(() -> RollingHashMask.calculate(min, max, normLevel))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("The content defined chunking mask must be between 1 and 63 bits");
  }

  @ParameterizedTest
  @CsvSource({"0, 32, 0", "32, 64, 0", "1, 65, 1", "0, 128, 2", "1073741824, 2147483648, 0"})
  public void acceptsSizeRangeWideEnoughForTheNormalizationLevel(long min, long max, int normLevel) {
    assertThatCode(() -> RollingHashMask.calculate(min, max, normLevel)).doesNotThrowAnyException();
  }

  /** A deliberate deviation: Arrow C++ and arrow-rs overflow here. */
  @Test
  public void handlesEnvelopeThatWouldOverflowTheAverage() {
    assertThat(RollingHashMask.calculate(256 * 1024L, Long.MAX_VALUE, 0)).isEqualTo(0xFFFFFFFFFFFFFFC0L);
  }
}
