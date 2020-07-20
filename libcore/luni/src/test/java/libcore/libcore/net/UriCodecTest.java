/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package libcore.libcore.net;

import junit.framework.TestCase;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import libcore.net.UriCodec;

/**
 * Tests for {@link UriCodec}
 */
public class UriCodecTest extends TestCase {
    private static final UriCodec CODEC = new UriCodec() {
        @Override
        protected boolean isRetained(char c) {
            return c == '$';
        }
    };

    private static final String VALID_ENCODED_STRING = "a0b$CD%01a%23b%45c%67%89%abd%cd%efq";

    public void testValidate_stringOK_passes() throws Exception {
        assertEquals(
                VALID_ENCODED_STRING,
                CODEC.validate(
                        VALID_ENCODED_STRING, 0, VALID_ENCODED_STRING.length(), "test OK string"));
    }

    // Hex codes in upper case are valid as well.
    public void testValidate_stringUppercaseOK_passes() throws Exception {
        String stringOKUpperCase = VALID_ENCODED_STRING.toUpperCase();
        CODEC.validate(stringOKUpperCase, 0, stringOKUpperCase.length(), "test OK UC string");
    }

    // Characters before the start index are ignored.
    public void testValidate_wrongCharsBeforeStart_passes() throws Exception {
        assertEquals(VALID_ENCODED_STRING, CODEC.validate(
                "%p" + VALID_ENCODED_STRING,
                2,
                VALID_ENCODED_STRING.length() + 2,
                "test string"));
    }

    // Fails with character 'p', invalid after '%'
    public void testValidate_wrongCharsAtStart_fails() throws Exception {
        try {
            CODEC.validate(
                    "%p" + VALID_ENCODED_STRING,
                    0,
                    VALID_ENCODED_STRING.length() + 2,
                    "test string");
            fail("Expected URISyntaxException");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    // Fails with character 'p', invalid after '%'
    public void testValidate_wrongCharsBeyondEnd_passes() throws Exception {
        assertEquals(VALID_ENCODED_STRING, CODEC.validate(
                VALID_ENCODED_STRING + "%p",
                0,
                VALID_ENCODED_STRING.length(),
                "test string"));
    }

    // Fails with character 'p', invalid after '%'
    public void testValidate_wrongCharsAtEnd_fails() throws Exception {
        try {
            CODEC.validate(
                    VALID_ENCODED_STRING + "%p",
                    0,
                    VALID_ENCODED_STRING.length() + 2,
                    "test string");
            fail("Expected URISyntaxException");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    public void testValidate_secondDigitWrong_fails() throws Exception {
        try {
            CODEC.validate(
                    VALID_ENCODED_STRING + "%1p",
                    0,
                    VALID_ENCODED_STRING.length() + 2,
                    "test string");
            fail("Expected URISyntaxException");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    public void testValidate_emptyString_passes() throws Exception {
        assertEquals("", CODEC.validate("", 0, 0, "empty string"));
    }

    public void testValidate_stringEndingWithPercent_fails() throws Exception {
        try {
            CODEC.validate("a%", 0, 0, "a% string");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    public void testValidate_stringEndingWithPercentAndSingleDigit_fails() throws Exception {
        try {
            CODEC.validate("a%1", 0, 0, "a%1 string");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    public void testValidateSimple_stringOK_passes() throws Exception {
        UriCodec.validateSimple(VALID_ENCODED_STRING, "$%");
    }

    // Hex codes in upper case are valid as well.
    public void testValidateSimple_stringUppercaseOK_passes() throws Exception {
        UriCodec.validateSimple(VALID_ENCODED_STRING.toUpperCase(), "$%");
    }

    // Fails with character 'p', invalid after '%'
    public void testValidateSimple_wrongCharsAtStart_fails() throws Exception {
        try {
            UriCodec.validateSimple("%/" + VALID_ENCODED_STRING, "$%");
            fail("Expected URISyntaxException");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    // Fails with character 'p', invalid after '%'
    public void testValidateSimple_wrongCharsAtEnd_fails() throws Exception {
        try {
            UriCodec.validateSimple(VALID_ENCODED_STRING + "%/", "$%");
            fail("Expected URISyntaxException");
        } catch (URISyntaxException expected) {
            // Expected.
        }
    }

    public void testValidateSimple_emptyString_passes() throws Exception {
        UriCodec.validateSimple("", "$%");
    }

    public void testValidateSimple_stringEndingWithPercent_passes() throws Exception {
        UriCodec.validateSimple("a%", "$%");
    }

    public void testValidateSimple_stringEndingWithPercentAndSingleDigit_passes() throws Exception {
        UriCodec.validateSimple("a%1", "$%");
    }

    public void testEncode_emptyString_returnsEmptyString() {
        assertEquals("", CODEC.encode("", StandardCharsets.UTF_8));
    }

    public void testEncode() {
        assertEquals("ab%2F$%C4%82%2512", CODEC.encode("ab/$\u0102%12", StandardCharsets.UTF_8));
    }

    public void testEncode_convertWhitespace() {
        // Whitespace is not retained, output %20.
        assertEquals("ab%2F$%C4%82%2512%20",
                CODEC.encode("ab/$\u0102%12 ", StandardCharsets.UTF_8));

        UriCodec withWhitespaceRetained = new UriCodec() {
            @Override
            protected boolean isRetained(char c) {
                return c == '$' || c == ' ';
            }
        };
        // Whitespace is retained, convert to plus.
        assertEquals("ab%2F$%C4%82%2512+",
                withWhitespaceRetained.encode("ab/$\u0102%12 ", StandardCharsets.UTF_8));
    }

    /** Confirm that '%' can be retained, disabling '%' encoding. http://b/24806835 */
    public void testEncode_percentRetained() {
        UriCodec withPercentRetained = new UriCodec() {
            @Override
            protected boolean isRetained(char c) {
                return c == '%';
            }
        };
        // Percent is retained
        assertEquals("ab%34%20", withPercentRetained.encode("ab%34 ", StandardCharsets.UTF_8));
    }

    public void testEncode_partially_returnsPercentUnchanged() {
        StringBuilder stringBuilder = new StringBuilder();
        // Check it's really appending instead of returning a new builder.
        stringBuilder.append("pp");
        CODEC.appendPartiallyEncoded(stringBuilder, "ab/$\u0102%");
        // Returns % at the end instead of %25.
        assertEquals("ppab%2F$%C4%82%", stringBuilder.toString());
    }

    public void testEncode_partially_returnsCharactersAfterPercentEncoded() {
        StringBuilder stringBuilder = new StringBuilder();
        // Check it's really appending instead of returning a new builder.
        stringBuilder.append("pp");
        CODEC.appendPartiallyEncoded(stringBuilder, "ab/$\u0102%\u0102");
        // Returns %C4%82 at the end.
        assertEquals("ppab%2F$%C4%82%%C4%82", stringBuilder.toString());
    }

    public void testEncode_partially_returnsDigitsAfterPercentUnchanged() {
        StringBuilder stringBuilder = new StringBuilder();
        // Check it's really appending instead of returning a new builder.
        stringBuilder.append("pp");
        CODEC.appendPartiallyEncoded(stringBuilder, "ab/$\u0102%38");
        // Returns %38 at the end.
        assertEquals("ppab%2F$%C4%82%38", stringBuilder.toString());
    }

    // Last character needs encoding (make sure we are flushing the buffer with chars to encode).
    public void testEncode_lastCharacter() {
        assertEquals("ab%2F$%C4%82%25%E0%A1%80",
                CODEC.encode("ab/$\u0102%\u0840", StandardCharsets.UTF_8));
    }

    // Last character needs encoding (make sure we are flushing the buffer with chars to encode).
    public void testEncode_flushBufferBeforePlusFromSpace() {
        UriCodec withSpaceRetained = new UriCodec() {
            @Override
            protected boolean isRetained(char c) {
                return c == ' ';
            }
        };
        assertEquals("%2F+",
                withSpaceRetained.encode("/ ", StandardCharsets.UTF_8));
    }

    public void testDecode_emptyString_returnsEmptyString() {
        assertEquals("", UriCodec.decode(""));
    }

    public void testDecode_wrongHexDigit_fails() {
        try {
            // %p in the end.
            UriCodec.decode("ab%2f$%C4%82%25%e0%a1%80%p");
            fail("Expected URISyntaxException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public void testDecode_secondHexDigitWrong_fails() {
        try {
            // %1p in the end.
            UriCodec.decode("ab%2f$%c4%82%25%e0%a1%80%1p");
            fail("Expected URISyntaxException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public void testDecode_endsWithPercent_fails() {
        try {
            // % in the end.
            UriCodec.decode("ab%2f$%c4%82%25%e0%a1%80%");
            fail("Expected URISyntaxException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public void testDecode_dontThrowException_appendsUnknownCharacter() {
        assertEquals("ab/$\u0102%\u0840\ufffd",
                UriCodec.decode("ab%2f$%c4%82%25%e0%a1%80%",
                        false /* convertPlus */,
                        StandardCharsets.UTF_8,
                        false /* throwOnFailure */));
    }

    public void testDecode_convertPlus() {
        assertEquals("ab/$\u0102% \u0840",
                UriCodec.decode("ab%2f$%c4%82%25+%e0%a1%80",
                        true /* convertPlus */,
                        StandardCharsets.UTF_8,
                        false /* throwOnFailure */));
    }

    // Last character needs decoding (make sure we are flushing the buffer with chars to decode).
    public void testDecode_lastCharacter() {
        assertEquals("ab/$\u0102%\u0840",
                UriCodec.decode("ab%2f$%c4%82%25%e0%a1%80"));
    }

    // Check that a second row of encoded characters is decoded properly (internal buffers are
    // reset properly).
    public void testDecode_secondRowOfEncoded() {
        assertEquals("ab/$\u0102%\u0840aa\u0840",
                UriCodec.decode("ab%2f$%c4%82%25%e0%a1%80aa%e0%a1%80"));
    }
}
