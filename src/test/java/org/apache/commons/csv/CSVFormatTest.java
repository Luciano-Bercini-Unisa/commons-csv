/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.csv;

import static org.apache.commons.csv.CSVFormat.RFC4180;
import static org.apache.commons.csv.Constants.CR;
import static org.apache.commons.csv.Constants.CRLF;
import static org.apache.commons.csv.Constants.LF;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat.Builder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CSVFormat}.
 */
class CSVFormatTest {

    enum EmptyEnum {
        // empty enum.
    }

    enum Header {
        NAME, EMAIL, PHONE
    }

    private static void assertNotEquals(final Object right, final Object left) {
        Assertions.assertNotEquals(right, left);
        Assertions.assertNotEquals(left, right);
    }

    private static CSVFormat copy(final CSVFormat format) {
        return format.builder().setDelimiter(format.getDelimiterString()).get();
    }

    private void assertNotEquals(final String name, final String type, final Object left, final Object right) {
        if (left.equals(right) || right.equals(left)) {
            fail("Objects must not compare equal for " + name + "(" + type + ")");
        }
        if (left.hashCode() == right.hashCode()) {
            fail("Hash code should not be equal for " + name + "(" + type + ")");
        }
    }

    @Test
    void testDelimiterEmptyStringThrowsException1() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setDelimiter(""));
    }

    @Test
    void testDelimiterSameAsCommentStartThrowsException1() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setDelimiter('!').setCommentMarker('!');
        assertThrows(IllegalArgumentException.class, builder::get);
    }

    @Test
    void testDelimiterSameAsEscapeThrowsException1() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setDelimiter('!').setEscape('!');
        assertThrows(IllegalArgumentException.class, builder::get);
    }

    @Test
    void testDelimiterSameAsRecordSeparatorThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> CSVFormat.newFormat(CR));
    }

    @Test
    void testDuplicateHeaderElements() {
        final String[] header = {"A", "A"};
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header).get();
        assertEquals(2, format.getHeader().length);
        assertArrayEquals(header, format.getHeader());
    }


    @Test
    void testDuplicateHeaderElementsFalse() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_EMPTY).setHeader("A", "A");
        assertThrows(IllegalArgumentException.class, builder::get);
    }


    @Test
    void testDuplicateHeaderElementsTrue() {
        // Build a CSVFormat with duplicate header names allowed
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                .setHeader("A", "A")
                .get();

        // Assert that the duplicateHeaderMode is set to ALLOW_ALL.
        assertEquals(DuplicateHeaderMode.ALLOW_ALL, format.getDuplicateHeaderMode(), "Duplicate header mode should be ALLOW_ALL");

        // Assert that the headers are set correctly, even with duplicates.
        assertArrayEquals(new String[]{"A", "A"}, format.getHeader(), "Headers should allow duplicates");
    }


    @Test
    void testDuplicateHeaderElementsTrueContainsEmpty() {
        String[] header = new String[]{"A", "", "B", ""};
        CSVFormat format =
                CSVFormat.DEFAULT.builder().setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_EMPTY).setHeader(header).get();
        // Assert that the header contains the expected elements.
        assertArrayEquals(header, format.getHeader(), "Headers should allow duplicates");
        // Assert that duplicate header mode is ALLOW_EMPTY.
        assertEquals(DuplicateHeaderMode.ALLOW_EMPTY, format.getDuplicateHeaderMode());
    }

    @Test
    void testDuplicateHeaderElementsTrueContainsEmpty2() {
        String[] header = new String[]{"A", "", "B", ""};
        CSVFormat format =
                CSVFormat.DEFAULT.builder().setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_EMPTY).
                        setAllowMissingColumnNames(true).setHeader(header).get();
        // Assert that the header contains the expected elements
        assertArrayEquals(header, format.getHeader(), "Headers should allow duplicates");
        // Assert that duplicate header mode is ALLOW_EMPTY
        assertEquals(DuplicateHeaderMode.ALLOW_EMPTY, format.getDuplicateHeaderMode());
        // Assert that missing column names are allowed
        assertTrue(format.getAllowMissingColumnNames());
    }

    @Test
    void testEquals() {
        final CSVFormat right = CSVFormat.DEFAULT;
        final CSVFormat left = copy(right);
        Assertions.assertNotEquals(null, right);
        Assertions.assertNotEquals("A String Instance", right);
        assertEquals(right, right);
        assertEquals(right, left);
        assertEquals(left, right);
        assertEquals(right.hashCode(), right.hashCode());
        assertEquals(right.hashCode(), left.hashCode());
    }

    @Test
    void testEqualsCommentStart() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setQuote('"').setCommentMarker('#').setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setCommentMarker('!').get();

        assertNotEquals(right, left);
    }

    @Test
    void testEqualsDelimiter() {
        final CSVFormat right = CSVFormat.newFormat('!');
        final CSVFormat left = CSVFormat.newFormat('?');

        assertNotEquals(right, left);
    }

    @Test
    void testEqualsEscape() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setQuote('"').setCommentMarker('#').setEscape('+').setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setEscape('!').get();

        assertNotEquals(right, left);
    }


    @Test
    void testEqualsHash() throws Exception {
        final Method[] methods = CSVFormat.class.getDeclaredMethods();
        for (final Method method : methods) {
            if (Modifier.isPublic(method.getModifiers())) {
                final String name = method.getName();
                if (name.startsWith("with")) {
                    for (final Class<?> cls : method.getParameterTypes()) {
                        final String type = cls.getCanonicalName();
                        switch (type) {
                            case "boolean": {
                                final Object defTrue = method.invoke(CSVFormat.DEFAULT, Boolean.TRUE);
                                final Object defFalse = method.invoke(CSVFormat.DEFAULT, Boolean.FALSE);
                                assertNotEquals(name, type, defTrue, defFalse);
                                break;
                            }
                            case "char": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, 'a');
                                final Object b = method.invoke(CSVFormat.DEFAULT, 'b');
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            case "java.lang.Character": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, new Object[]{null});
                                final Object b = method.invoke(CSVFormat.DEFAULT, Character.valueOf('d'));
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            case "java.lang.String": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, new Object[]{null});
                                final Object b = method.invoke(CSVFormat.DEFAULT, "e");
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            case "java.lang.String[]": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, new Object[]{new String[]{null, null}});
                                final Object b = method.invoke(CSVFormat.DEFAULT, new Object[]{new String[]{"f", "g"}});
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            case "org.apache.commons.csv.QuoteMode": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, QuoteMode.MINIMAL);
                                final Object b = method.invoke(CSVFormat.DEFAULT, QuoteMode.ALL);
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            case "org.apache.commons.csv.DuplicateHeaderMode": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, DuplicateHeaderMode.ALLOW_ALL);
                                final Object b = method.invoke(CSVFormat.DEFAULT, DuplicateHeaderMode.DISALLOW);
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            case "java.lang.Object[]": {
                                final Object a = method.invoke(CSVFormat.DEFAULT, new Object[]{new Object[]{null, null}});
                                final Object b = method.invoke(CSVFormat.DEFAULT, new Object[]{new Object[]{new Object(), new Object()}});
                                assertNotEquals(name, type, a, b);
                                break;
                            }
                            default:
                                if ("withHeader".equals(name)) { // covered above by String[]
                                    // ignored
                                } else {
                                    fail("Unhandled method: " + name + "(" + type + ")");
                                }
                                break;
                        }
                    }
                }
            }
        }
    }

    @Test
    void testEqualsHeader() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setRecordSeparator(CR).setCommentMarker('#').setEscape('+').setHeader("One", "Two", "Three")
                .setIgnoreEmptyLines(true).setIgnoreSurroundingSpaces(true).setQuote('"').setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setHeader("Three", "Two", "One").get();

        assertNotEquals(right, left);
    }

    @Test
    void testEqualsIgnoreEmptyLines() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setCommentMarker('#').setEscape('+').setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true).setQuote('"').setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setIgnoreEmptyLines(false).get();

        assertNotEquals(right, left);
    }


    @Test
    void testEqualsIgnoreSurroundingSpaces() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setCommentMarker('#').setEscape('+').setIgnoreSurroundingSpaces(true).setQuote('"')
                .setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setIgnoreSurroundingSpaces(false).get();

        assertNotEquals(right, left);
    }

    @Test
    void testEqualsLeftNoQuoteRightQuote() {
        final CSVFormat left = CSVFormat.newFormat(',').builder().setQuote(null).get();
        final CSVFormat right = left.builder().setQuote('#').get();

        assertNotEquals(left, right);
    }


    @Test
    void testEqualsNoQuotes() {
        final CSVFormat left = CSVFormat.newFormat(',').builder().setQuote(null).get();
        final CSVFormat right = left.builder().setQuote(null).get();

        assertEquals(left, right);
    }


    @Test
    void testEqualsNullString() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setRecordSeparator(CR).setCommentMarker('#').setEscape('+').setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true).setQuote('"').setQuoteMode(QuoteMode.ALL).setNullString("null").get();
        final CSVFormat left = right.builder().setNullString("---").get();

        assertNotEquals(right, left);
    }


    @Test
    void testEqualsOne() {

        final CSVFormat csvFormatOne = CSVFormat.INFORMIX_UNLOAD;
        final CSVFormat csvFormatTwo = CSVFormat.MYSQL;

        assertEquals('\\', (char) csvFormatOne.getEscapeCharacter());
        assertEquals('\\', csvFormatOne.getEscapeChar());
        assertNull(csvFormatOne.getQuoteMode());

        assertTrue(csvFormatOne.getIgnoreEmptyLines());
        assertFalse(csvFormatOne.getSkipHeaderRecord());

        assertFalse(csvFormatOne.getIgnoreHeaderCase());
        assertNull(csvFormatOne.getCommentMarker());

        assertFalse(csvFormatOne.isCommentMarkerSet());
        assertTrue(csvFormatOne.isQuoteCharacterSet());

        assertEquals("|", csvFormatOne.getDelimiterString());
        assertFalse(csvFormatOne.getAllowMissingColumnNames());

        assertTrue(csvFormatOne.isEscapeCharacterSet());
        assertEquals("\n", csvFormatOne.getRecordSeparator());

        assertEquals('\"', (char) csvFormatOne.getQuoteCharacter());
        assertFalse(csvFormatOne.getTrailingDelimiter());

        assertFalse(csvFormatOne.getTrim());
        assertFalse(csvFormatOne.isNullStringSet());

        assertNull(csvFormatOne.getNullString());
        assertFalse(csvFormatOne.getIgnoreSurroundingSpaces());

        assertTrue(csvFormatTwo.isEscapeCharacterSet());
        assertNull(csvFormatTwo.getQuoteCharacter());

        assertFalse(csvFormatTwo.getAllowMissingColumnNames());
        assertEquals(QuoteMode.ALL_NON_NULL, csvFormatTwo.getQuoteMode());

        assertEquals("\t", csvFormatTwo.getDelimiterString());
        assertArrayEquals(new char[]{'\t'}, csvFormatTwo.getDelimiterCharArray());
        assertEquals("\t", csvFormatTwo.getDelimiterString());
        assertEquals("\n", csvFormatTwo.getRecordSeparator());

        assertFalse(csvFormatTwo.isQuoteCharacterSet());
        assertTrue(csvFormatTwo.isNullStringSet());

        assertEquals('\\', (char) csvFormatTwo.getEscapeCharacter());
        assertFalse(csvFormatTwo.getIgnoreHeaderCase());

        assertFalse(csvFormatTwo.getTrim());
        assertFalse(csvFormatTwo.getIgnoreEmptyLines());

        assertEquals("\\N", csvFormatTwo.getNullString());
        assertFalse(csvFormatTwo.getIgnoreSurroundingSpaces());

        assertFalse(csvFormatTwo.getTrailingDelimiter());
        assertFalse(csvFormatTwo.getSkipHeaderRecord());

        assertNull(csvFormatTwo.getCommentMarker());
        assertFalse(csvFormatTwo.isCommentMarkerSet());

        assertNotSame(csvFormatTwo, csvFormatOne);
        Assertions.assertNotEquals(csvFormatTwo, csvFormatOne);

        assertEquals('\\', (char) csvFormatOne.getEscapeCharacter());
        assertNull(csvFormatOne.getQuoteMode());

        assertTrue(csvFormatOne.getIgnoreEmptyLines());
        assertFalse(csvFormatOne.getSkipHeaderRecord());

        assertFalse(csvFormatOne.getIgnoreHeaderCase());
        assertNull(csvFormatOne.getCommentMarker());

        assertFalse(csvFormatOne.isCommentMarkerSet());
        assertTrue(csvFormatOne.isQuoteCharacterSet());

        assertEquals("|", csvFormatOne.getDelimiterString());
        assertFalse(csvFormatOne.getAllowMissingColumnNames());

        assertTrue(csvFormatOne.isEscapeCharacterSet());
        assertEquals("\n", csvFormatOne.getRecordSeparator());

        assertEquals('\"', (char) csvFormatOne.getQuoteCharacter());
        assertFalse(csvFormatOne.getTrailingDelimiter());

        assertFalse(csvFormatOne.getTrim());
        assertFalse(csvFormatOne.isNullStringSet());

        assertNull(csvFormatOne.getNullString());
        assertFalse(csvFormatOne.getIgnoreSurroundingSpaces());

        assertTrue(csvFormatTwo.isEscapeCharacterSet());
        assertNull(csvFormatTwo.getQuoteCharacter());

        assertFalse(csvFormatTwo.getAllowMissingColumnNames());
        assertEquals(QuoteMode.ALL_NON_NULL, csvFormatTwo.getQuoteMode());

        assertEquals("\t", csvFormatTwo.getDelimiterString());
        assertEquals("\n", csvFormatTwo.getRecordSeparator());

        assertFalse(csvFormatTwo.isQuoteCharacterSet());
        assertTrue(csvFormatTwo.isNullStringSet());

        assertEquals('\\', (char) csvFormatTwo.getEscapeCharacter());
        assertFalse(csvFormatTwo.getIgnoreHeaderCase());

        assertFalse(csvFormatTwo.getTrim());
        assertFalse(csvFormatTwo.getIgnoreEmptyLines());

        assertEquals("\\N", csvFormatTwo.getNullString());
        assertFalse(csvFormatTwo.getIgnoreSurroundingSpaces());

        assertFalse(csvFormatTwo.getTrailingDelimiter());
        assertFalse(csvFormatTwo.getSkipHeaderRecord());

        assertNull(csvFormatTwo.getCommentMarker());
        assertFalse(csvFormatTwo.isCommentMarkerSet());

        assertNotSame(csvFormatOne, csvFormatTwo);
        assertNotSame(csvFormatTwo, csvFormatOne);

        Assertions.assertNotEquals(csvFormatOne, csvFormatTwo);
        Assertions.assertNotEquals(csvFormatTwo, csvFormatOne);

        Assertions.assertNotEquals(csvFormatTwo, csvFormatOne);

    }

    @Test
    void testEqualsQuoteChar() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setQuote('"').get();
        final CSVFormat left = right.builder().setQuote('!').get();

        assertNotEquals(right, left);
    }


    @Test
    void testEqualsQuotePolicy() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setQuote('"').setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setQuoteMode(QuoteMode.MINIMAL).get();

        assertNotEquals(right, left);
    }


    @Test
    void testEqualsRecordSeparator() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setRecordSeparator(CR).setCommentMarker('#').setEscape('+').setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true).setQuote('"').setQuoteMode(QuoteMode.ALL).get();
        final CSVFormat left = right.builder().setRecordSeparator(LF).get();

        assertNotEquals(right, left);
    }


    void testEqualsSkipHeaderRecord() {
        final CSVFormat right = CSVFormat.newFormat('\'').builder().setRecordSeparator(CR).setCommentMarker('#').setEscape('+').setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true).setQuote('"').setQuoteMode(QuoteMode.ALL).setNullString("null").setSkipHeaderRecord(true).get();
        final CSVFormat left = right.builder().setSkipHeaderRecord(false).get();

        assertNotEquals(right, left);
    }


    @Test
    void testEqualsWithNull() {

        final CSVFormat csvFormat = CSVFormat.POSTGRESQL_TEXT;

        assertEquals('\\', (char) csvFormat.getEscapeCharacter());
        assertFalse(csvFormat.getIgnoreSurroundingSpaces());

        assertFalse(csvFormat.getTrailingDelimiter());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isQuoteCharacterSet());
        assertEquals("\\N", csvFormat.getNullString());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertTrue(csvFormat.isEscapeCharacterSet());

        assertFalse(csvFormat.isCommentMarkerSet());
        assertNull(csvFormat.getCommentMarker());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertEquals(QuoteMode.ALL_NON_NULL, csvFormat.getQuoteMode());

        assertEquals("\t", csvFormat.getDelimiterString());
        assertFalse(csvFormat.getSkipHeaderRecord());

        assertEquals("\n", csvFormat.getRecordSeparator());
        assertFalse(csvFormat.getIgnoreEmptyLines());

        assertNull(csvFormat.getQuoteCharacter());
        assertTrue(csvFormat.isNullStringSet());

        assertEquals('\\', (char) csvFormat.getEscapeCharacter());
        assertFalse(csvFormat.getIgnoreSurroundingSpaces());

        assertFalse(csvFormat.getTrailingDelimiter());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isQuoteCharacterSet());
        assertEquals("\\N", csvFormat.getNullString());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertTrue(csvFormat.isEscapeCharacterSet());

        assertFalse(csvFormat.isCommentMarkerSet());
        assertNull(csvFormat.getCommentMarker());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertEquals(QuoteMode.ALL_NON_NULL, csvFormat.getQuoteMode());

        assertEquals("\t", csvFormat.getDelimiterString());
        assertFalse(csvFormat.getSkipHeaderRecord());

        assertEquals("\n", csvFormat.getRecordSeparator());
        assertFalse(csvFormat.getIgnoreEmptyLines());

        assertNull(csvFormat.getQuoteCharacter());
        assertTrue(csvFormat.isNullStringSet());

        Assertions.assertNotEquals(null, csvFormat);

    }

    @Test
    void testEscapeSameAsCommentStartThrowsException() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setEscape('!').setCommentMarker('!');
        assertThrows(IllegalArgumentException.class, builder::get);
    }


    @Test
    void testEscapeSameAsCommentStartThrowsExceptionForWrapperType() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setEscape(Character.valueOf('!')).setCommentMarker(Character.valueOf('!'));
        // Cannot assume that callers won't use different Character objects
        assertThrows(IllegalArgumentException.class, builder::get);
    }


    @Test
    void testFormat() {
        final CSVFormat format = CSVFormat.DEFAULT;

        assertEquals("", format.format());
        assertEquals("a,b,c", format.format("a", "b", "c"));
        assertEquals("\"x,y\",z", format.format("x,y", "z"));
    }

    @Test
        // I assume this to be a defect.
    void testFormatThrowsNullPointerException() {

        final CSVFormat csvFormat = CSVFormat.MYSQL;

        final NullPointerException e = assertThrows(NullPointerException.class, () -> csvFormat.format((Object[]) null));
        assertEquals(Objects.class.getName(), e.getStackTrace()[0].getClassName());
    }

    @Test
    void testFormatToString() {
        // @formatter:off
        final CSVFormat format = CSVFormat.RFC4180
                .builder().setEscape('?').get()
                .builder().setDelimiter(',').get()
                .builder().setQuoteMode(QuoteMode.MINIMAL).get()
                .builder().setRecordSeparator(CRLF).get()
                .builder().setQuote('"').get()
                .builder().setNullString("").get()
                .builder().setIgnoreHeaderCase(true).get()
                .builder().setHeaderComments("This is HeaderComments").get()
                .builder().setHeader("col1", "col2", "col3").get();
        // @formatter:on
        assertEquals(
                "Delimiter=<,> Escape=<?> QuoteChar=<\"> QuoteMode=<MINIMAL> NullString=<> RecordSeparator=<" + CRLF +
                        "> IgnoreHeaderCase:ignored SkipHeaderRecord:false HeaderComments:[This is HeaderComments] Header:[col1, col2, col3]",
                format.toString());
    }

    @Test
    void testGetAllowDuplicateHeaderNames() {
        final Builder builder = CSVFormat.DEFAULT.builder();
        assertSame(builder.get().getDuplicateHeaderMode(), DuplicateHeaderMode.ALLOW_ALL);
        assertSame(builder.setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL).get().getDuplicateHeaderMode(), DuplicateHeaderMode.ALLOW_ALL);
        assertNotSame(builder.setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_EMPTY).get().getDuplicateHeaderMode(), DuplicateHeaderMode.ALLOW_ALL);
        assertNotSame(builder.setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get().getDuplicateHeaderMode(), DuplicateHeaderMode.ALLOW_ALL);
    }

    @Test
    void testGetDuplicateHeaderMode() {
        final Builder builder = CSVFormat.DEFAULT.builder();

        assertEquals(DuplicateHeaderMode.ALLOW_ALL, builder.get().getDuplicateHeaderMode());
        assertEquals(DuplicateHeaderMode.ALLOW_ALL, builder.setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL).get().getDuplicateHeaderMode());
        assertEquals(DuplicateHeaderMode.ALLOW_EMPTY, builder.setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_EMPTY).get().getDuplicateHeaderMode());
        assertEquals(DuplicateHeaderMode.DISALLOW, builder.setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get().getDuplicateHeaderMode());
    }

    @Test
    void testGetHeader() {
        final String[] header = {"one", "two", "three"};
        final CSVFormat formatWithHeader = CSVFormat.DEFAULT.builder().setHeader(header).get();
        // getHeader() makes a copy of the header array.
        final String[] headerCopy = formatWithHeader.getHeader();
        headerCopy[0] = "A";
        headerCopy[1] = "B";
        headerCopy[2] = "C";
        assertFalse(Arrays.equals(formatWithHeader.getHeader(), headerCopy));
        assertNotSame(formatWithHeader.getHeader(), headerCopy);
    }

    @Test
    void testHashCodeAndWithIgnoreHeaderCase() {

        final CSVFormat csvFormat = CSVFormat.INFORMIX_UNLOAD_CSV;
        final CSVFormat csvFormatTwo = csvFormat.builder().setIgnoreHeaderCase(true).get();
        csvFormatTwo.hashCode();

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertTrue(csvFormatTwo.getIgnoreHeaderCase()); // now different
        assertFalse(csvFormatTwo.getTrailingDelimiter());

        Assertions.assertNotEquals(csvFormatTwo, csvFormat); // CSV-244 - should not be equal
        assertFalse(csvFormatTwo.getAllowMissingColumnNames());

        assertFalse(csvFormatTwo.getTrim());

    }

    @Test
    void testJiraCsv236() {
        String[] header = new String[]{"CC", "VV", "VV"};
        CSVFormat format = CSVFormat.DEFAULT.builder().setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL).setHeader(header).get();
        // Assert that the header contains the expected elements
        assertArrayEquals(header, format.getHeader());
        // Assert that duplicate header mode is ALLOW_ALL
        assertEquals(DuplicateHeaderMode.ALLOW_ALL, format.getDuplicateHeaderMode());
    }

    @Test
    void testNewFormat() {

        final CSVFormat csvFormat = CSVFormat.newFormat('X');

        assertFalse(csvFormat.getSkipHeaderRecord());
        assertFalse(csvFormat.isEscapeCharacterSet());

        assertNull(csvFormat.getRecordSeparator());
        assertNull(csvFormat.getQuoteMode());

        assertNull(csvFormat.getCommentMarker());
        assertFalse(csvFormat.getIgnoreHeaderCase());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isNullStringSet());
        assertNull(csvFormat.getEscapeCharacter());

        assertFalse(csvFormat.getIgnoreSurroundingSpaces());
        assertFalse(csvFormat.getTrailingDelimiter());

        assertEquals("X", csvFormat.getDelimiterString());
        assertNull(csvFormat.getNullString());

        assertFalse(csvFormat.isQuoteCharacterSet());
        assertFalse(csvFormat.isCommentMarkerSet());

        assertNull(csvFormat.getQuoteCharacter());
        assertFalse(csvFormat.getIgnoreEmptyLines());

        assertFalse(csvFormat.getSkipHeaderRecord());
        assertFalse(csvFormat.isEscapeCharacterSet());

        assertNull(csvFormat.getRecordSeparator());
        assertNull(csvFormat.getQuoteMode());

        assertNull(csvFormat.getCommentMarker());
        assertFalse(csvFormat.getIgnoreHeaderCase());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isNullStringSet());
        assertNull(csvFormat.getEscapeCharacter());

        assertFalse(csvFormat.getIgnoreSurroundingSpaces());
        assertFalse(csvFormat.getTrailingDelimiter());

        assertEquals("X", csvFormat.getDelimiterString());
        assertNull(csvFormat.getNullString());

        assertFalse(csvFormat.isQuoteCharacterSet());
        assertFalse(csvFormat.isCommentMarkerSet());

        assertNull(csvFormat.getQuoteCharacter());
        assertFalse(csvFormat.getIgnoreEmptyLines());

    }

    @Test
    void testNullRecordSeparatorCsv106() {
        final CSVFormat format = CSVFormat.newFormat(';').builder().setSkipHeaderRecord(true).setHeader("H1", "H2").get();
        final String formatStr = format.format("A", "B");
        assertNotNull(formatStr);
        assertFalse(formatStr.endsWith("null"));
    }


    @Test
    void testPrintRecord() throws IOException {
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180;
        format.printRecord(out, "a", "b", "c");
        assertEquals("a,b,c" + format.getRecordSeparator(), out.toString());
    }

    @Test
    void testPrintRecordEmpty() throws IOException {
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180;
        format.printRecord(out);
        assertEquals(format.getRecordSeparator(), out.toString());
    }

    @Test
    void testPrintWithEscapesEndWithCRLF() throws IOException {
        final Reader in = new StringReader("x,y,x\r\na,?b,c\r\n");
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180.builder().setEscape('?').get().builder().setDelimiter(',').get().builder().setQuote(null).get().builder().setRecordSeparator(CRLF).get();
        format.print(in, out, true);
        assertEquals("x?,y?,x?r?na?,??b?,c?r?n", out.toString());
    }

    @Test
    void testPrintWithEscapesEndWithoutCRLF() throws IOException {
        final Reader in = new StringReader("x,y,x");
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180.builder().setEscape('?').get().builder().setDelimiter(',').get().builder().setQuote(null).get().builder().setRecordSeparator(CRLF).get();
        format.print(in, out, true);
        assertEquals("x?,y?,x", out.toString());
    }

    @Test
    void testPrintWithoutQuotes() throws IOException {
        final Reader in = new StringReader("");
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(',').get().builder().setQuote('"').get().builder().setEscape('?').get().builder().setQuoteMode(QuoteMode.NON_NUMERIC).get();
        format.print(in, out, true);
        assertEquals("\"\"", out.toString());
    }

    @Test
    void testPrintWithQuoteModeIsNONE() throws IOException {
        final Reader in = new StringReader("a,b,c");
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(',').get().builder().setQuote('"').get().builder().setEscape('?').get().builder().setQuoteMode(QuoteMode.NONE).get();
        format.print(in, out, true);
        assertEquals("a?,b?,c", out.toString());
    }

    @Test
    void testPrintWithQuotes() throws IOException {
        final Reader in = new StringReader("\"a,b,c\r\nx,y,z");
        final Appendable out = new StringBuilder();
        final CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(',').get().builder().setQuote('"').get().builder().setEscape('?').get().builder().setQuoteMode(QuoteMode.NON_NUMERIC).get();
        format.print(in, out, true);
        assertEquals("\"\"\"a,b,c\r\nx,y,z\"", out.toString());
    }

    @Test
    void testQuoteCharSameAsCommentStartThrowsException() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setQuote('!').setCommentMarker('!');
        assertThrows(IllegalArgumentException.class, builder::get);
    }

    @Test
    void testQuoteCharSameAsCommentStartThrowsExceptionForWrapperType() {
        // Cannot assume that callers won't use different Character objects
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setQuote(Character.valueOf('!')).setCommentMarker('!');
        assertThrows(IllegalArgumentException.class, builder::get);
    }


    @Test
    void testQuoteCharSameAsDelimiterThrowsException() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setQuote('!').setDelimiter('!');
        assertThrows(IllegalArgumentException.class, builder::get);
    }


    @Test
    void testQuoteModeNoneShouldReturnMeaningfulExceptionMessage() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder().setHeader("Col1", "Col2", "Col3", "Col4").setQuoteMode(QuoteMode.NONE);
        final Exception exception = assertThrows(IllegalArgumentException.class, builder::get);
        final String actualMessage = exception.getMessage();
        final String expectedMessage = "Quote mode set to NONE but no escape character is set";
        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testQuotePolicyNoneWithoutEscapeThrowsException() {
        CSVFormat.Builder builder = CSVFormat.newFormat('!').builder().setQuoteMode(QuoteMode.NONE);
        assertThrows(IllegalArgumentException.class, builder::get);
    }


    @Test
    void testRFC4180() {
        assertNull(RFC4180.getCommentMarker());
        assertEquals(",", RFC4180.getDelimiterString());
        assertNull(RFC4180.getEscapeCharacter());
        assertFalse(RFC4180.getIgnoreEmptyLines());
        assertEquals(Character.valueOf('"'), RFC4180.getQuoteCharacter());
        assertNull(RFC4180.getQuoteMode());
        assertEquals("\r\n", RFC4180.getRecordSeparator());
    }

    @SuppressWarnings("boxing") // no need to worry about boxing here
    @Test
    void testSerialization() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (final ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(CSVFormat.DEFAULT);
            oos.flush();
        }

        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        final CSVFormat format = (CSVFormat) in.readObject();

        assertNotNull(format);
        assertEquals(CSVFormat.DEFAULT.getDelimiterString(), format.getDelimiterString(), "delimiter");
        assertEquals(CSVFormat.DEFAULT.getQuoteCharacter(), format.getQuoteCharacter(), "encapsulator");
        assertEquals(CSVFormat.DEFAULT.getCommentMarker(), format.getCommentMarker(), "comment start");
        assertEquals(CSVFormat.DEFAULT.getRecordSeparator(), format.getRecordSeparator(), "record separator");
        assertEquals(CSVFormat.DEFAULT.getEscapeCharacter(), format.getEscapeCharacter(), "escape");
        assertEquals(CSVFormat.DEFAULT.getIgnoreSurroundingSpaces(), format.getIgnoreSurroundingSpaces(), "trim");
        assertEquals(CSVFormat.DEFAULT.getIgnoreEmptyLines(), format.getIgnoreEmptyLines(), "empty lines");
    }

    @Test
    void testToString() {

        final String string = CSVFormat.INFORMIX_UNLOAD.toString();

        assertEquals("Delimiter=<|> Escape=<\\> QuoteChar=<\"> RecordSeparator=<\n> EmptyLines:ignored SkipHeaderRecord:false", string);

    }

    @Test
    void testToStringAndWithCommentMarkerTakingCharacter() {

        final CSVFormat.Predefined csvFormat_Predefined = CSVFormat.Predefined.DEFAULT;
        final CSVFormat csvFormat = csvFormat_Predefined.getFormat();

        assertNull(csvFormat.getEscapeCharacter());
        assertTrue(csvFormat.isQuoteCharacterSet());

        assertFalse(csvFormat.getTrim());
        assertFalse(csvFormat.getIgnoreSurroundingSpaces());

        assertFalse(csvFormat.getTrailingDelimiter());
        assertEquals(",", csvFormat.getDelimiterString());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertEquals("\r\n", csvFormat.getRecordSeparator());

        assertFalse(csvFormat.isCommentMarkerSet());
        assertNull(csvFormat.getCommentMarker());

        assertFalse(csvFormat.isNullStringSet());
        assertFalse(csvFormat.getAllowMissingColumnNames());

        assertFalse(csvFormat.isEscapeCharacterSet());
        assertFalse(csvFormat.getSkipHeaderRecord());

        assertNull(csvFormat.getNullString());
        assertNull(csvFormat.getQuoteMode());

        assertTrue(csvFormat.getIgnoreEmptyLines());
        assertEquals('\"', (char) csvFormat.getQuoteCharacter());

        final Character character = Character.valueOf('n');

        final CSVFormat csvFormatTwo = csvFormat.builder().setCommentMarker(character).get();

        assertNull(csvFormat.getEscapeCharacter());
        assertTrue(csvFormat.isQuoteCharacterSet());

        assertFalse(csvFormat.getTrim());
        assertFalse(csvFormat.getIgnoreSurroundingSpaces());

        assertFalse(csvFormat.getTrailingDelimiter());
        assertEquals(",", csvFormat.getDelimiterString());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertEquals("\r\n", csvFormat.getRecordSeparator());

        assertFalse(csvFormat.isCommentMarkerSet());
        assertNull(csvFormat.getCommentMarker());

        assertFalse(csvFormat.isNullStringSet());
        assertFalse(csvFormat.getAllowMissingColumnNames());

        assertFalse(csvFormat.isEscapeCharacterSet());
        assertFalse(csvFormat.getSkipHeaderRecord());

        assertNull(csvFormat.getNullString());
        assertNull(csvFormat.getQuoteMode());

        assertTrue(csvFormat.getIgnoreEmptyLines());
        assertEquals('\"', (char) csvFormat.getQuoteCharacter());

        assertFalse(csvFormatTwo.isNullStringSet());
        assertFalse(csvFormatTwo.getAllowMissingColumnNames());

        assertEquals('\"', (char) csvFormatTwo.getQuoteCharacter());
        assertNull(csvFormatTwo.getNullString());

        assertEquals(",", csvFormatTwo.getDelimiterString());
        assertFalse(csvFormatTwo.getTrailingDelimiter());

        assertTrue(csvFormatTwo.isCommentMarkerSet());
        assertFalse(csvFormatTwo.getIgnoreHeaderCase());

        assertFalse(csvFormatTwo.getTrim());
        assertNull(csvFormatTwo.getEscapeCharacter());

        assertTrue(csvFormatTwo.isQuoteCharacterSet());
        assertFalse(csvFormatTwo.getIgnoreSurroundingSpaces());

        assertEquals("\r\n", csvFormatTwo.getRecordSeparator());
        assertNull(csvFormatTwo.getQuoteMode());

        assertEquals('n', (char) csvFormatTwo.getCommentMarker());
        assertFalse(csvFormatTwo.getSkipHeaderRecord());

        assertFalse(csvFormatTwo.isEscapeCharacterSet());
        assertTrue(csvFormatTwo.getIgnoreEmptyLines());

        assertNotSame(csvFormat, csvFormatTwo);
        assertNotSame(csvFormatTwo, csvFormat);

        Assertions.assertNotEquals(csvFormatTwo, csvFormat);

        assertNull(csvFormat.getEscapeCharacter());
        assertTrue(csvFormat.isQuoteCharacterSet());

        assertFalse(csvFormat.getTrim());
        assertFalse(csvFormat.getIgnoreSurroundingSpaces());

        assertFalse(csvFormat.getTrailingDelimiter());
        assertEquals(",", csvFormat.getDelimiterString());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertEquals("\r\n", csvFormat.getRecordSeparator());

        assertFalse(csvFormat.isCommentMarkerSet());
        assertNull(csvFormat.getCommentMarker());

        assertFalse(csvFormat.isNullStringSet());
        assertFalse(csvFormat.getAllowMissingColumnNames());

        assertFalse(csvFormat.isEscapeCharacterSet());
        assertFalse(csvFormat.getSkipHeaderRecord());

        assertNull(csvFormat.getNullString());
        assertNull(csvFormat.getQuoteMode());

        assertTrue(csvFormat.getIgnoreEmptyLines());
        assertEquals('\"', (char) csvFormat.getQuoteCharacter());

        assertFalse(csvFormatTwo.isNullStringSet());
        assertFalse(csvFormatTwo.getAllowMissingColumnNames());

        assertEquals('\"', (char) csvFormatTwo.getQuoteCharacter());
        assertNull(csvFormatTwo.getNullString());

        assertEquals(",", csvFormatTwo.getDelimiterString());
        assertFalse(csvFormatTwo.getTrailingDelimiter());

        assertTrue(csvFormatTwo.isCommentMarkerSet());
        assertFalse(csvFormatTwo.getIgnoreHeaderCase());

        assertFalse(csvFormatTwo.getTrim());
        assertNull(csvFormatTwo.getEscapeCharacter());

        assertTrue(csvFormatTwo.isQuoteCharacterSet());
        assertFalse(csvFormatTwo.getIgnoreSurroundingSpaces());

        assertEquals("\r\n", csvFormatTwo.getRecordSeparator());
        assertNull(csvFormatTwo.getQuoteMode());

        assertEquals('n', (char) csvFormatTwo.getCommentMarker());
        assertFalse(csvFormatTwo.getSkipHeaderRecord());

        assertFalse(csvFormatTwo.isEscapeCharacterSet());
        assertTrue(csvFormatTwo.getIgnoreEmptyLines());

        assertNotSame(csvFormat, csvFormatTwo);
        assertNotSame(csvFormatTwo, csvFormat);

        Assertions.assertNotEquals(csvFormat, csvFormatTwo);

        Assertions.assertNotEquals(csvFormatTwo, csvFormat);
        assertEquals("Delimiter=<,> QuoteChar=<\"> CommentStart=<n> " + "RecordSeparator=<\r\n> EmptyLines:ignored SkipHeaderRecord:false",
                csvFormatTwo.toString());

    }

    @Test
    void testTrim() throws IOException {
        final CSVFormat formatWithTrim = CSVFormat.DEFAULT.builder().setDelimiter(',').get().builder().setTrim(true).get().builder().setQuote(null).get().builder().setRecordSeparator(CRLF).get();

        CharSequence in = "a,b,c";
        final StringBuilder out = new StringBuilder();
        formatWithTrim.print(in, out, true);
        assertEquals("a,b,c", out.toString());

        in = new StringBuilder(" x,y,z");
        out.setLength(0);
        formatWithTrim.print(in, out, true);
        assertEquals("x,y,z", out.toString());

        in = new StringBuilder("");
        out.setLength(0);
        formatWithTrim.print(in, out, true);
        assertEquals("", out.toString());

        in = new StringBuilder("header\r\n");
        out.setLength(0);
        formatWithTrim.print(in, out, true);
        assertEquals("header", out.toString());
    }

    @Test
    void testWithCommentStart() {
        final CSVFormat formatWithCommentStart = CSVFormat.DEFAULT.builder().setCommentMarker('#').get();
        assertEquals(Character.valueOf('#'), formatWithCommentStart.getCommentMarker());
    }

    @Test
    void testWithCommentStartCRThrowsException() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setCommentMarker(CR));
    }

    @Test
    void testWithDelimiter() {
        final CSVFormat formatWithDelimiter = CSVFormat.DEFAULT.builder().setDelimiter('!').get();
        assertEquals("!", formatWithDelimiter.getDelimiterString());
    }

    @Test
    void testWithDelimiterLFThrowsException() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setDelimiter(LF));
    }

    @Test
    void testWithEmptyDuplicates() {
        final CSVFormat formatWithEmptyDuplicates = CSVFormat.DEFAULT.builder().setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_EMPTY).get();

        assertEquals(DuplicateHeaderMode.ALLOW_EMPTY, formatWithEmptyDuplicates.getDuplicateHeaderMode());
        assertNotSame(formatWithEmptyDuplicates.getDuplicateHeaderMode(), DuplicateHeaderMode.ALLOW_ALL);
    }

    @Test
    void testWithEmptyEnum() {
        final CSVFormat formatWithHeader = CSVFormat.DEFAULT.builder().setHeader(EmptyEnum.class).get();
        assertEquals(0, formatWithHeader.getHeader().length);
    }

    @Test
    void testWithEscape() {
        final CSVFormat formatWithEscape = CSVFormat.DEFAULT.builder().setEscape('&').get();
        assertEquals(Character.valueOf('&'), formatWithEscape.getEscapeCharacter());
    }

    @Test
    void testWithEscapeCRThrowsExceptions() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setEscape(CR));
    }

    @Test
    void testWithFirstRecordAsHeader() {
        final CSVFormat formatWithFirstRecordAsHeader = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();
        assertTrue(formatWithFirstRecordAsHeader.getSkipHeaderRecord());
        assertEquals(0, formatWithFirstRecordAsHeader.getHeader().length);
    }

    @Test
    void testWithHeader() {
        final String[] header = {"one", "two", "three"};
        // withHeader() makes a copy of the header array.
        final CSVFormat formatWithHeader = CSVFormat.DEFAULT.builder().setHeader(header).get();
        assertArrayEquals(header, formatWithHeader.getHeader());
        assertNotSame(header, formatWithHeader.getHeader());
    }

    @Test
    void testWithHeaderComments() {

        final CSVFormat csvFormat = CSVFormat.DEFAULT;

        assertEquals('\"', (char) csvFormat.getQuoteCharacter());
        assertFalse(csvFormat.isCommentMarkerSet());

        assertFalse(csvFormat.isEscapeCharacterSet());
        assertTrue(csvFormat.isQuoteCharacterSet());

        assertFalse(csvFormat.getSkipHeaderRecord());
        assertNull(csvFormat.getQuoteMode());

        assertEquals(",", csvFormat.getDelimiterString());
        assertTrue(csvFormat.getIgnoreEmptyLines());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertNull(csvFormat.getCommentMarker());

        assertEquals("\r\n", csvFormat.getRecordSeparator());
        assertFalse(csvFormat.getTrailingDelimiter());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isNullStringSet());
        assertNull(csvFormat.getNullString());

        assertFalse(csvFormat.getIgnoreSurroundingSpaces());
        assertNull(csvFormat.getEscapeCharacter());

        final Object[] objectArray = new Object[8];
        final CSVFormat csvFormatTwo = csvFormat.builder().setHeaderComments(objectArray).get();

        assertEquals('\"', (char) csvFormat.getQuoteCharacter());
        assertFalse(csvFormat.isCommentMarkerSet());

        assertFalse(csvFormat.isEscapeCharacterSet());
        assertTrue(csvFormat.isQuoteCharacterSet());

        assertFalse(csvFormat.getSkipHeaderRecord());
        assertNull(csvFormat.getQuoteMode());

        assertEquals(",", csvFormat.getDelimiterString());
        assertTrue(csvFormat.getIgnoreEmptyLines());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertNull(csvFormat.getCommentMarker());

        assertEquals("\r\n", csvFormat.getRecordSeparator());
        assertFalse(csvFormat.getTrailingDelimiter());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isNullStringSet());
        assertNull(csvFormat.getNullString());

        assertFalse(csvFormat.getIgnoreSurroundingSpaces());
        assertNull(csvFormat.getEscapeCharacter());

        assertFalse(csvFormatTwo.getIgnoreHeaderCase());
        assertNull(csvFormatTwo.getQuoteMode());

        assertTrue(csvFormatTwo.getIgnoreEmptyLines());
        assertFalse(csvFormatTwo.getIgnoreSurroundingSpaces());

        assertNull(csvFormatTwo.getEscapeCharacter());
        assertFalse(csvFormatTwo.getTrim());

        assertFalse(csvFormatTwo.isEscapeCharacterSet());
        assertTrue(csvFormatTwo.isQuoteCharacterSet());

        assertFalse(csvFormatTwo.getSkipHeaderRecord());
        assertEquals('\"', (char) csvFormatTwo.getQuoteCharacter());

        assertFalse(csvFormatTwo.getAllowMissingColumnNames());
        assertNull(csvFormatTwo.getNullString());

        assertFalse(csvFormatTwo.isNullStringSet());
        assertFalse(csvFormatTwo.getTrailingDelimiter());

        assertEquals("\r\n", csvFormatTwo.getRecordSeparator());
        assertEquals(",", csvFormatTwo.getDelimiterString());

        assertNull(csvFormatTwo.getCommentMarker());
        assertFalse(csvFormatTwo.isCommentMarkerSet());

        assertNotSame(csvFormat, csvFormatTwo);
        assertNotSame(csvFormatTwo, csvFormat);

        Assertions.assertNotEquals(csvFormatTwo, csvFormat); // CSV-244 - should not be equal

        final String string = csvFormatTwo.format(objectArray);

        assertEquals('\"', (char) csvFormat.getQuoteCharacter());
        assertFalse(csvFormat.isCommentMarkerSet());

        assertFalse(csvFormat.isEscapeCharacterSet());
        assertTrue(csvFormat.isQuoteCharacterSet());

        assertFalse(csvFormat.getSkipHeaderRecord());
        assertNull(csvFormat.getQuoteMode());

        assertEquals(",", csvFormat.getDelimiterString());
        assertTrue(csvFormat.getIgnoreEmptyLines());

        assertFalse(csvFormat.getIgnoreHeaderCase());
        assertNull(csvFormat.getCommentMarker());

        assertEquals("\r\n", csvFormat.getRecordSeparator());
        assertFalse(csvFormat.getTrailingDelimiter());

        assertFalse(csvFormat.getAllowMissingColumnNames());
        assertFalse(csvFormat.getTrim());

        assertFalse(csvFormat.isNullStringSet());
        assertNull(csvFormat.getNullString());

        assertFalse(csvFormat.getIgnoreSurroundingSpaces());
        assertNull(csvFormat.getEscapeCharacter());

        assertFalse(csvFormatTwo.getIgnoreHeaderCase());
        assertNull(csvFormatTwo.getQuoteMode());

        assertTrue(csvFormatTwo.getIgnoreEmptyLines());
        assertFalse(csvFormatTwo.getIgnoreSurroundingSpaces());

        assertNull(csvFormatTwo.getEscapeCharacter());
        assertFalse(csvFormatTwo.getTrim());

        assertFalse(csvFormatTwo.isEscapeCharacterSet());
        assertTrue(csvFormatTwo.isQuoteCharacterSet());

        assertFalse(csvFormatTwo.getSkipHeaderRecord());
        assertEquals('\"', (char) csvFormatTwo.getQuoteCharacter());

        assertFalse(csvFormatTwo.getAllowMissingColumnNames());
        assertNull(csvFormatTwo.getNullString());

        assertFalse(csvFormatTwo.isNullStringSet());
        assertFalse(csvFormatTwo.getTrailingDelimiter());

        assertEquals("\r\n", csvFormatTwo.getRecordSeparator());
        assertEquals(",", csvFormatTwo.getDelimiterString());

        assertNull(csvFormatTwo.getCommentMarker());
        assertFalse(csvFormatTwo.isCommentMarkerSet());

        assertNotSame(csvFormat, csvFormatTwo);
        assertNotSame(csvFormatTwo, csvFormat);

        assertNotNull(string);
        Assertions.assertNotEquals(csvFormat, csvFormatTwo); // CSV-244 - should not be equal

        Assertions.assertNotEquals(csvFormatTwo, csvFormat); // CSV-244 - should not be equal
        assertEquals(",,,,,,,", string);

    }

    @Test
    void testWithHeaderEnum() {
        final CSVFormat formatWithHeader = CSVFormat.DEFAULT.builder().setHeader(Header.class).get();
        assertArrayEquals(new String[]{"NAME", "EMAIL", "PHONE"}, formatWithHeader.getHeader());
    }

    @Test
    void testWithHeaderEnumNull() {
        final CSVFormat format = CSVFormat.DEFAULT;
        final Class<Enum<?>> simpleName = null;
        CSVFormat updatedFormat = format.builder()
                .setHeader(simpleName)
                .get();
        // Assert that the header is null (as no header is explicitly set)
        assertNull(updatedFormat.getHeader());
    }

    @Test
    void testWithHeaderResultSetNull() throws SQLException {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ResultSet resultSet = null;
        CSVFormat updatedFormat = format.builder()
                .setHeader(resultSet)
                .get();
        // Assert that the header is null (as no header is explicitly set)
        assertNull(updatedFormat.getHeader());
    }

    @Test
    void testWithIgnoreEmptyLines() {
        assertFalse(CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).get().getIgnoreEmptyLines());
        assertTrue(CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).get().getIgnoreEmptyLines());
    }

    @Test
    void testWithIgnoreSurround() {
        assertFalse(CSVFormat.DEFAULT.builder().setIgnoreSurroundingSpaces(false).get().getIgnoreSurroundingSpaces());
        assertTrue(CSVFormat.DEFAULT.builder().setIgnoreSurroundingSpaces(true).get().getIgnoreSurroundingSpaces());
    }

    @Test
    void testWithNullString() {
        final CSVFormat formatWithNullString = CSVFormat.DEFAULT.builder().setNullString("null").get();
        assertEquals("null", formatWithNullString.getNullString());
    }

    @Test
    void testWithQuoteChar() {
        final CSVFormat formatWithQuoteChar = CSVFormat.DEFAULT.builder().setQuote('"').get();
        assertEquals(Character.valueOf('"'), formatWithQuoteChar.getQuoteCharacter());
    }

    @Test
    void testWithQuoteLFThrowsException() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setQuote(LF));
    }

    @Test
    void testWithQuotePolicy() {
        final CSVFormat formatWithQuotePolicy = CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).get();
        assertEquals(QuoteMode.ALL, formatWithQuotePolicy.getQuoteMode());
    }

    @Test
    void testWithRecordSeparatorCR() {
        final CSVFormat formatWithRecordSeparator = CSVFormat.DEFAULT.builder().setRecordSeparator(CR).get();
        assertEquals(String.valueOf(CR), formatWithRecordSeparator.getRecordSeparator());
    }

    @Test
    void testWithRecordSeparatorCRLF() {
        final CSVFormat formatWithRecordSeparator = CSVFormat.DEFAULT.builder().setRecordSeparator(CRLF).get();
        assertEquals(CRLF, formatWithRecordSeparator.getRecordSeparator());
    }

    @Test
    void testWithRecordSeparatorLF() {
        final CSVFormat formatWithRecordSeparator = CSVFormat.DEFAULT.builder().setRecordSeparator(LF).get();
        assertEquals(String.valueOf(LF), formatWithRecordSeparator.getRecordSeparator());
    }

    @Test
    void testWithSystemRecordSeparator() {
        final CSVFormat formatWithRecordSeparator = CSVFormat.DEFAULT.builder().setRecordSeparator(System.lineSeparator()).get();
        assertEquals(System.lineSeparator(), formatWithRecordSeparator.getRecordSeparator());
    }
}
