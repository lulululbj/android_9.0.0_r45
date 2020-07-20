/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package libcore.libcore.util;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import libcore.io.BufferIterator;
import libcore.tzdata.testing.ZoneInfoTestHelper;
import libcore.util.ZoneInfo;
import libcore.util.ZoneInfoDB;

/**
 * Tests for {@link ZoneInfo}
 */
public class ZoneInfoTest extends TestCase {

  /**
   * Checks that a {@link ZoneInfo} cannot be created without any types.
   */
  public void testMakeTimeZone_NoTypes() throws Exception {
    int[][] transitions = {};
    int[][] types = {};
    try {
      createZoneInfo(transitions, types);
      fail();
    } catch (IOException expected) {
    }
  }

  /**
   * Checks that a {@link ZoneInfo} can be created with one type and no transitions.
   */
  public void testMakeTimeZone_OneType_NoTransitions() throws Exception {
    int[][] transitions = {};
    int[][] types = {
        { 4800, 0 }
    };
    ZoneInfo zoneInfo = createZoneInfo(transitions, types);

    // If there are no transitions then the offset should be constant irrespective of the time.
    assertEquals(secondsInMillis(4800), zoneInfo.getOffset(Long.MIN_VALUE));
    assertEquals(secondsInMillis(4800), zoneInfo.getOffset(0));
    assertEquals(secondsInMillis(4800), zoneInfo.getOffset(Long.MAX_VALUE));

    // No transitions means no DST.
    assertFalse("Doesn't use DST", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());

    // The raw offset should be the offset of the first type.
    assertEquals(secondsInMillis(4800), zoneInfo.getRawOffset());
  }

  /**
   * Checks that a {@link ZoneInfo} can be created with one non-DST transition.
   */
  public void testReadTimeZone_OneNonDstTransition() throws Exception {
    int[][] transitions = {
        { 0, 0 }
    };
    int[][] types = {
        { 3600, 0 }
    };
    ZoneInfo zoneInfo = createZoneInfo(transitions, types);

    // Any time before the first transition is assumed to use the first standard transition.
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(secondsInMillis(-2)));
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(0));
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(secondsInMillis(2)));

    // No transitions means no DST.
    assertFalse("Doesn't use DST", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());

    // The raw offset should be the offset of the first type.
    assertEquals(secondsInMillis(3600), zoneInfo.getRawOffset());
  }

  /**
   * Checks that a {@link ZoneInfo} cannot be created with one DST but no non-DSTs transitions.
   */
  public void testReadTimeZone_OneDstTransition() throws Exception {
    int[][] transitions = {
        { 0, 0 }
    };
    int[][] types = {
        { 3600, 1 }
    };
    try {
      createZoneInfo(transitions, types);
      fail("Did not detect no non-DST transitions");
    } catch (IllegalStateException expected) {
    }
  }

  /**
   * Checks to make sure that rounding the time from milliseconds to seconds does not cause issues
   * around the boundary of negative transitions.
   */
  public void testReadTimeZone_NegativeTransition() throws Exception {
    int[][] transitions = {
        { -2000, 0 },
        { -5, 1 },
        { 0, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 1 },
        { 5400, 0 }
    };
    ZoneInfo zoneInfo = createZoneInfo(transitions, types);

    // Even a millisecond before a transition means that the transition is not active.
    assertEquals(1800000, zoneInfo.getOffset(secondsInMillis(-5) - 1));
    assertFalse(zoneInfo.inDaylightTime(new Date(secondsInMillis(-5) - 1)));

    // A time equal to the transition point activates the transition.
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(secondsInMillis(-5)));
    assertTrue(zoneInfo.inDaylightTime(new Date(secondsInMillis(-5))));

    // A time after the transition point but before the next activates the transition.
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(secondsInMillis(-5) + 1));
    assertTrue(zoneInfo.inDaylightTime(new Date(secondsInMillis(-5) + 1)));

    assertFalse("Doesn't use DST", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());
    assertEquals(secondsInMillis(5400), zoneInfo.getRawOffset());
  }

  /**
   * Checks to make sure that rounding the time from milliseconds to seconds does not cause issues
   * around the boundary of positive transitions.
   */
  public void testReadTimeZone_PositiveTransition() throws Exception {
    int[][] transitions = {
        { 0, 0 },
        { 5, 1 },
        { 2000, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 1 },
        { 5400, 0 }
    };
    ZoneInfo zoneInfo = createZoneInfo(transitions, types);

    // Even a millisecond before a transition means that the transition is not active.
    assertEquals(secondsInMillis(1800), zoneInfo.getOffset(secondsInMillis(5) - 1));
    assertFalse(zoneInfo.inDaylightTime(new Date(secondsInMillis(5) - 1)));

    // A time equal to the transition point activates the transition.
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(secondsInMillis(5)));
    assertTrue(zoneInfo.inDaylightTime(new Date(secondsInMillis(5))));

    // A time after the transition point but before the next activates the transition.
    assertEquals(secondsInMillis(3600), zoneInfo.getOffset(secondsInMillis(5) + 1));
    assertTrue(zoneInfo.inDaylightTime(new Date(secondsInMillis(5) + 1)));

    assertFalse("Doesn't use DST", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());
    assertEquals(secondsInMillis(5400), zoneInfo.getRawOffset());
  }

  /**
   * Checks that creating a {@link ZoneInfo} with future DST transitions but no past DST
   * transitions where the transition times are negative is not affected by rounding issues.
   */
  public void testReadTimeZone_HasFutureDST_NoPastDST_NegativeTransitions() throws Exception {
    int[][] transitions = {
        { -2000, 0 },
        { -500, 1 },
        { -100, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 0 },
        { 5400, 1 }
    };
    // The expected DST savings is the difference between the DST offset (which includes the
    // raw offset) and the preceding non-DST offset (which should just be the raw offset).
    // Or in other words (5400 - 3600) * 1000
    int expectedDSTSavings = secondsInMillis(5400 - 3600);

    ZoneInfo zoneInfo = createZoneInfo(transitions, types, secondsInMillis(-700));

    assertTrue("Should use DST but doesn't", zoneInfo.useDaylightTime());
    assertEquals(expectedDSTSavings, zoneInfo.getDSTSavings());

    // Now create one a few milliseconds before the DST transition to make sure that rounding
    // errors don't cause a problem.
    zoneInfo = createZoneInfo(transitions, types, secondsInMillis(-100) - 5);

    assertTrue("Should use DST but doesn't", zoneInfo.useDaylightTime());
    assertEquals(expectedDSTSavings, zoneInfo.getDSTSavings());
  }

  /**
   * Checks that creating a {@link ZoneInfo} with future DST transitions but no past DST
   * transitions where the transition times are positive is not affected by rounding issues.
   */
  public void testReadTimeZone_HasFutureDST_NoPastDST_PositiveTransitions() throws Exception {
    int[][] transitions = {
        { 4000, 0 },
        { 5500, 1 },
        { 6000, 2 },
    };
    int[][] types = {
        { 1800, 0 },
        { 3600, 0 },
        { 7200, 1 }
    };
    // The expected DST savings is the difference between the DST offset (which includes the
    // raw offset) and the preceding non-DST offset (which should just be the raw offset).
    // Or in other words (7200 - 3600) * 1000
    int expectedDSTSavings = secondsInMillis(7200 - 3600);

    ZoneInfo zoneInfo = createZoneInfo(
            transitions, types, secondsInMillis(4500) /* currentTimeMillis */);

    assertTrue("Should use DST but doesn't", zoneInfo.useDaylightTime());
    assertEquals(expectedDSTSavings, zoneInfo.getDSTSavings());

    // Now create one a few milliseconds before the DST transition to make sure that rounding
    // errors don't cause a problem.
    zoneInfo = createZoneInfo(transitions, types, secondsInMillis(6000) - 5);

    assertTrue("Should use DST but doesn't", zoneInfo.useDaylightTime());
    assertEquals(expectedDSTSavings, zoneInfo.getDSTSavings());
  }

  /**
   * Checks that creating a {@link ZoneInfo} with past DST transitions but no future DST
   * transitions where the transition times are negative is not affected by rounding issues.
   */
  public void testReadTimeZone_HasPastDST_NoFutureDST_NegativeTransitions() throws Exception {
    int[][] transitions = {
        { -5000, 0 },
        { -2000, 1 },
        { -500, 0 },
        { 0, 2 },
    };
    int[][] types = {
        { 3600, 0 },
        { 1800, 1 },
        { 5400, 0 }
    };
    ZoneInfo zoneInfo = createZoneInfo(transitions, types,
            secondsInMillis(-1) /* currentTimeMillis */);

    assertFalse("Shouldn't use DST but does", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());

    // Now create one a few milliseconds after the DST transition to make sure that rounding
    // errors don't cause a problem.
    zoneInfo = createZoneInfo(transitions, types, secondsInMillis(-2000) + 5);

    assertFalse("Shouldn't use DST but does", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());
  }

  /**
   * Checks that creating a {@link ZoneInfo} with past DST transitions but no future DST
   * transitions where the transition times are positive is not affected by rounding issues.
   */
  public void testReadTimeZone_HasPastDST_NoFutureDST_PositiveTransitions() throws Exception {
    int[][] transitions = {
        { 1000, 0 },
        { 4000, 1 },
        { 5500, 0 },
        { 6000, 2 },
    };
    int[][] types = {
        { 3600, 0 },
        { 1800, 1 },
        { 5400, 0 }
    };
    ZoneInfo zoneInfo = createZoneInfo(transitions, types, secondsInMillis(4700));

    assertFalse("Shouldn't use DST but does", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());

    // Now create one a few milliseconds after the DST transition to make sure that rounding
    // errors don't cause a problem.
    zoneInfo = createZoneInfo(transitions, types, secondsInMillis(4000) + 5);

    assertFalse("Shouldn't use DST but does", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());
  }

  /**
   * Checks to make sure that it can handle up to 256 types.
   */
  public void testReadTimeZone_LotsOfTypes() throws Exception {
    int[][] transitions = {
        { -2000, 255 },
    };
    int[][] types = new int[256][];
    Arrays.fill(types, new int[2]);
    types[255] = new int[] { 3600, 0 };

    ZoneInfo zoneInfo = createZoneInfo(getName(), transitions, types, (long) Integer.MIN_VALUE);

    assertFalse("Shouldn't use DST but does", zoneInfo.useDaylightTime());
    assertEquals(0, zoneInfo.getDSTSavings());

    // Make sure that WallTime works properly with a ZoneInfo with 256 types.
    ZoneInfo.WallTime wallTime = new ZoneInfo.WallTime();
    wallTime.localtime(0, zoneInfo);
    wallTime.mktime(zoneInfo);
  }

  /**
   * Create an instance for every available time zone for which we have data to ensure that they
   * can all be handled correctly.
   *
   * <p>This is to ensure that ZoneInfo can read all time zone data without failing, it doesn't
   * check that it reads it correctly or that the data itself is correct. This is a sanity test
   * to ensure that any additional checks added to the code that reads the data source and
   * creates the {@link ZoneInfo} instances does not prevent any of the time zones being loaded.
   */
  public void testReadTimeZone_All() throws Exception {
    ZoneInfoDB.TzData instance = ZoneInfoDB.getInstance();
    String[] availableIDs = instance.getAvailableIDs();
    Arrays.sort(availableIDs);
    for (String id : availableIDs) {
      BufferIterator bufferIterator = instance.getBufferIterator(id);

      // Create a ZoneInfo at the earliest possible time to allow us to use the
      // useDaylightTime() method to check whether it ever has or ever will support daylight
      // savings time.
      ZoneInfo zoneInfo = ZoneInfo.readTimeZone(id, bufferIterator, Long.MIN_VALUE);
      assertNotNull("TimeZone " + id + " was not created", zoneInfo);
      assertEquals(id, zoneInfo.getID());
    }
  }

  public void testReadTimeZone_valid() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid();
    assertNotNull(createZoneInfo(getName(), System.currentTimeMillis(), builder.build()));
  }

  public void testReadTimeZone_badMagic() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setMagic(0xdeadbeef); // Bad magic.
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), builder.build());
      fail();
    } catch (IOException expected) {}
  }

  /**
   * Checks to make sure that ZoneInfo rejects more than 256 types.
   */
  public void testReadTimeZone_TooManyTypes() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTypeCountOverride(257);
    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail("Did not detect too many types");
    } catch (IOException expected) {
    }
  }

  /**
   * Checks to make sure that ZoneInfo rejects more than 2000 transitions.
   */
  public void testReadTimeZone_TooManyTransitions() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionCountOverride(2001);
    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail("Did not detect too many transitions");
    } catch (IOException expected) {
    }
  }

  /**
   * Checks to make sure that ZoneInfo rejects a negative type count.
   */
  public void testReadTimeZone_NegativeTypes() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTypeCountOverride(-1);
    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  /**
   * Checks to make sure that ZoneInfo rejects a negative transition count.
   */
  public void testReadTimeZone_NegativeTransitions() throws Exception {
    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionCountOverride(-1);
    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  public void testReadTimeZone_TransitionsNotSorted() throws Exception {
    int[][] transitions = {
            { 1000, 0 },
            { 3000, 1 }, // Out of transition order.
            { 2000, 0 },
    };
    int[][] types = {
            { 3600, 0 },
            { 1800, 1 },
    };

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);

    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  public void testReadTimeZone_InvalidTypeIndex() throws Exception {
    int[][] transitions = {
            { 1000, 0 },
            { 2000, 2 }, // Invalid type index - only 0 and 1 defined below.
            { 3000, 0 },
    };
    int[][] types = {
            { 3600, 0 },
            { 1800, 1 },
    };

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);

    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  public void testReadTimeZone_InvalidIsDst() throws Exception {
    int[][] transitions = {
            { 1000, 0 },
            { 2000, 1 },
            { 3000, 0 },
    };
    int[][] types = {
            { 3600, 0 },
            { 1800, 2 }, // Invalid isDst - must be 0 or 1
    };

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .initializeToValid()
                    .setTransitionsAndTypes(transitions, types);

    byte[] bytes = builder.build();
    try {
      createZoneInfo(getName(), System.currentTimeMillis(), bytes);
      fail();
    } catch (IOException expected) {
    }
  }

  /**
   * Checks that we can read the serialized form of a {@link ZoneInfo} created in pre-OpenJDK
   * AOSP.
   *
   * <p>One minor difference is that in pre-OpenJDK {@link ZoneInfo#mDstSavings} can be non-zero
   * even if {@link ZoneInfo#mUseDst} was false. That was not visible externally (except through
   * the {@link ZoneInfo#toString()} method) as the {@link ZoneInfo#getDSTSavings()} would check
   * {@link ZoneInfo#mUseDst} and if it was false then would return 0. This checks to make sure
   * that is handled properly. See {@link ZoneInfo#readObject(ObjectInputStream)}.
   */
  public void testReadSerialized() throws Exception {
    ZoneInfo zoneInfoRead;
    try (InputStream is = getClass().getResourceAsStream("ZoneInfoTest_ZoneInfo.golden.ser");
         ObjectInputStream ois = new ObjectInputStream(is)) {
      Object object = ois.readObject();
      assertTrue("Not a ZoneInfo instance", object instanceof ZoneInfo);
      zoneInfoRead = (ZoneInfo) object;
    }

    int[][] transitions = {
        { -5000, 0 },
        { -2000, 1 },
        { -500, 0 },
        { 0, 2 },
    };
    int[][] types = {
        { 3600, 0 },
        { 1800, 1 },
        { 5400, 0 }
    };
    ZoneInfo zoneInfoCreated = createZoneInfo("test", transitions, types, secondsInMillis(-1));

    assertEquals("Read ZoneInfo does not match created one", zoneInfoCreated, zoneInfoRead);
    assertEquals("useDaylightTime() mismatch",
        zoneInfoCreated.useDaylightTime(), zoneInfoRead.useDaylightTime());
    assertEquals("getDSTSavings() mismatch",
        zoneInfoCreated.getDSTSavings(), zoneInfoRead.getDSTSavings());
  }

  private static int secondsInMillis(int seconds) {
    return seconds * 1000;
  }

  private ZoneInfo createZoneInfo(int[][] transitions, int[][] types)
      throws Exception {
    return createZoneInfo(getName(), transitions, types, System.currentTimeMillis());
  }

  private ZoneInfo createZoneInfo(int[][] transitions, int[][] types,
      long currentTimeMillis) throws Exception {
    return createZoneInfo(getName(), transitions, types, currentTimeMillis);
  }

  private ZoneInfo createZoneInfo(String name, int[][] transitions, int[][] types,
      long currentTimeMillis) throws Exception {

    ZoneInfoTestHelper.ZicDataBuilder builder =
            new ZoneInfoTestHelper.ZicDataBuilder()
                    .setTransitionsAndTypes(transitions, types);
    return createZoneInfo(name, currentTimeMillis, builder.build());
  }

  private ZoneInfo createZoneInfo(String name, long currentTimeMillis, byte[] bytes)
          throws IOException {
    ByteBufferIterator bufferIterator = new ByteBufferIterator(ByteBuffer.wrap(bytes));
    return ZoneInfo.readTimeZone("TimeZone for '" + name + "'", bufferIterator, currentTimeMillis);
  }

  /**
   * A {@link BufferIterator} that wraps a {@link ByteBuffer}.
   */
  private static class ByteBufferIterator extends BufferIterator {

    private final ByteBuffer buffer;

    public ByteBufferIterator(ByteBuffer buffer) {
      this.buffer = buffer;
    }

    @Override
    public void seek(int offset) {
      buffer.position(offset);
    }

    @Override
    public void skip(int byteCount) {
      buffer.position(buffer.position() + byteCount);
    }

    @Override
    public int pos() {
      return buffer.position();
    }

    @Override
    public void readByteArray(byte[] dst, int dstOffset, int byteCount) {
      buffer.get(dst, dstOffset, byteCount);
    }

    @Override
    public byte readByte() {
      return buffer.get();
    }

    @Override
    public int readInt() {
      int value = buffer.asIntBuffer().get();
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(4);
      return value;
    }

    @Override
    public void readIntArray(int[] dst, int dstOffset, int intCount) {
      buffer.asIntBuffer().get(dst, dstOffset, intCount);
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(4 * intCount);
    }

    @Override
    public short readShort() {
      short value = buffer.asShortBuffer().get();
      // Using a separate view does not update the position of this buffer so do it
      // explicitly.
      skip(2);
      return value;
    }
  }
}
