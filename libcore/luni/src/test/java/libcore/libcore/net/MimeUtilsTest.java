/*
 * Copyright (C) 2014 The Android Open Source Project
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

package libcore.libcore.net;

import junit.framework.TestCase;

import libcore.net.MimeUtils;

public class MimeUtilsTest extends TestCase {
  public void test_15715370() {
    assertEquals("audio/flac", MimeUtils.guessMimeTypeFromExtension("flac"));
    assertEquals("flac", MimeUtils.guessExtensionFromMimeType("audio/flac"));
    assertEquals("flac", MimeUtils.guessExtensionFromMimeType("application/x-flac"));
  }

  // https://code.google.com/p/android/issues/detail?id=78909
  public void test_78909() {
    assertEquals("mka", MimeUtils.guessExtensionFromMimeType("audio/x-matroska"));
    assertEquals("mkv", MimeUtils.guessExtensionFromMimeType("video/x-matroska"));
  }

  public void test_16978217() {
    assertEquals("image/x-ms-bmp", MimeUtils.guessMimeTypeFromExtension("bmp"));
    assertEquals("image/x-icon", MimeUtils.guessMimeTypeFromExtension("ico"));
    assertEquals("video/mp2ts", MimeUtils.guessMimeTypeFromExtension("ts"));
  }

  public void testCommon() {
    assertEquals("audio/mpeg", MimeUtils.guessMimeTypeFromExtension("mp3"));
    assertEquals("image/png", MimeUtils.guessMimeTypeFromExtension("png"));
    assertEquals("application/zip", MimeUtils.guessMimeTypeFromExtension("zip"));

    assertEquals("mp3", MimeUtils.guessExtensionFromMimeType("audio/mpeg"));
    assertEquals("png", MimeUtils.guessExtensionFromMimeType("image/png"));
    assertEquals("zip", MimeUtils.guessExtensionFromMimeType("application/zip"));
  }

  public void test_18390752() {
    assertEquals("jpg", MimeUtils.guessExtensionFromMimeType("image/jpeg"));
  }

  public void test_30207891() {
    assertTrue(MimeUtils.hasMimeType("IMAGE/PNG"));
    assertTrue(MimeUtils.hasMimeType("IMAGE/png"));
    assertFalse(MimeUtils.hasMimeType(""));
    assertEquals("png", MimeUtils.guessExtensionFromMimeType("IMAGE/PNG"));
    assertEquals("png", MimeUtils.guessExtensionFromMimeType("IMAGE/png"));
    assertNull(MimeUtils.guessMimeTypeFromExtension(""));
    assertNull(MimeUtils.guessMimeTypeFromExtension("doesnotexist"));
    assertTrue(MimeUtils.hasExtension("PNG"));
    assertTrue(MimeUtils.hasExtension("PnG"));
    assertFalse(MimeUtils.hasExtension(""));
    assertFalse(MimeUtils.hasExtension(".png"));
    assertEquals("image/png", MimeUtils.guessMimeTypeFromExtension("PNG"));
    assertEquals("image/png", MimeUtils.guessMimeTypeFromExtension("PnG"));
    assertNull(MimeUtils.guessMimeTypeFromExtension(".png"));
    assertNull(MimeUtils.guessMimeTypeFromExtension(""));
    assertNull(MimeUtils.guessExtensionFromMimeType("doesnotexist"));
  }

  public void test_30793548() {
    assertEquals("video/3gpp", MimeUtils.guessMimeTypeFromExtension("3gpp"));
    assertEquals("video/3gpp", MimeUtils.guessMimeTypeFromExtension("3gp"));
    assertEquals("video/3gpp2", MimeUtils.guessMimeTypeFromExtension("3gpp2"));
    assertEquals("video/3gpp2", MimeUtils.guessMimeTypeFromExtension("3g2"));
  }

  public void test_37167977() {
    // https://tools.ietf.org/html/rfc5334#section-10.1
    assertEquals("audio/ogg", MimeUtils.guessMimeTypeFromExtension("ogg"));
    assertEquals("audio/ogg", MimeUtils.guessMimeTypeFromExtension("oga"));
    assertEquals("audio/ogg", MimeUtils.guessMimeTypeFromExtension("spx"));
    assertEquals("video/ogg", MimeUtils.guessMimeTypeFromExtension("ogv"));
  }

  public void test_70851634() {
    assertEquals("application/vnd.youtube.yt", MimeUtils.guessMimeTypeFromExtension("yt"));
  }
}
