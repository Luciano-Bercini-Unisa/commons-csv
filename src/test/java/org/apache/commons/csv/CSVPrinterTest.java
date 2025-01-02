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

import static org.apache.commons.csv.Constants.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Vector;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.h2.tools.SimpleResultSet;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CSVPrinter}.
 */
class CSVPrinterTest {

    private static final int TABLE_RECORD_COUNT = 2;
    private static final char DQUOTE_CHAR = '"';
    private static final char EURO_CH = '\u20AC';
    private static final int ITERATIONS_FOR_RANDOM_TEST = 50000;
    private static final char QUOTE_CH = '\'';

    private static String printable(final String s) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            if (ch <= ' ' || ch >= 128) {
                sb.append("(").append((int) ch).append(")");
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String longText2;

    private final String recordSeparator = CSVFormat.DEFAULT.getRecordSeparator();

    private void assertInitialState(final CSVPrinter printer) {
        assertEquals(0, printer.getRecordCount());
    }

    private File createTempFile() throws IOException {
        return createTempPath().toFile();
    }

    private Path createTempPath() throws IOException {
        return Files.createTempFile(getClass().getName(), ".csv");
    }

    private void doOneRandom(final CSVFormat format) throws Exception {
        final Random r = new Random();

        final int nLines = r.nextInt(4) + 1;
        final int nCol = r.nextInt(3) + 1;
        final String[][] lines = generateLines(nLines, nCol);

        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {

            for (int i = 0; i < nLines; i++) {
                printer.printRecord((Object[]) lines[i]);
            }

            printer.flush();
        }
        final String result = sw.toString();

        try (final CSVParser parser = CSVParser.parse(result, format)) {
            final List<CSVRecord> parseResult = parser.getRecords();

            final String[][] expected = lines.clone();
            for (int i = 0; i < expected.length; i++) {
                expected[i] = expectNulls(expected[i], format);
            }
            Utils.compare("Printer output :" + printable(result), expected, parseResult);
        }
    }

    private void doRandom(final CSVFormat format, final int iter) throws Exception {
        for (int i = 0; i < iter; i++) {
            doOneRandom(format);
        }
    }

    /**
     * Converts an input CSV array into expected output values WRT NULLs. NULL strings are converted to null values because the parser will convert these
     * strings to null.
     */
    private <T> T[] expectNulls(final T[] original, final CSVFormat csvFormat) {
        final T[] fixed = original.clone();
        for (int i = 0; i < fixed.length; i++) {
            if (Objects.equals(csvFormat.getNullString(), fixed[i])) {
                fixed[i] = null;
            }
        }
        return fixed;
    }

    private String[][] generateLines(final int nLines, final int nCol) {
        final String[][] lines = new String[nLines][];
        for (int i = 0; i < nLines; i++) {
            final String[] line = new String[nCol];
            lines[i] = line;
            for (int j = 0; j < nCol; j++) {
                line[j] = randStr();
            }
        }
        return lines;
    }

    private Connection getH2Connection() throws SQLException, ClassNotFoundException {
        Class.forName("org.h2.Driver");
        return DriverManager.getConnection("jdbc:h2:mem:my_test;", "sa", "");
    }

    private CSVPrinter printWithHeaderComments(final StringWriter sw, final Date now, final CSVFormat baseFormat) throws IOException {
        // Use withHeaderComments first to test CSV-145
        // @formatter:off
        final CSVFormat format = baseFormat.builder()
                .setHeaderComments("Generated by Apache Commons CSV 1.1", now)
                .setCommentMarker('#')
                .setHeader("Col1", "Col2")
                .get();
        // @formatter:on
        final CSVPrinter printer = format.print(sw);
        printer.printRecord("A", "B");
        printer.printRecord("C", "D");
        printer.close();
        return printer;
    }

    private String randStr() {
        final Random r = new Random();
        final int sz = r.nextInt(20);
        final char[] buf = new char[sz];
        for (int i = 0; i < sz; i++) {
            // stick in special chars with greater frequency
            final char ch;
            final int what = r.nextInt(20);
            switch (what) {
                case 0:
                    ch = '\r';
                    break;
                case 1:
                    ch = '\n';
                    break;
                case 2:
                    ch = '\t';
                    break;
                case 3:
                    ch = '\f';
                    break;
                case 4:
                    ch = ' ';
                    break;
                case 5:
                    ch = ',';
                    break;
                case 6:
                    ch = DQUOTE_CHAR;
                    break;
                case 7:
                    ch = '\'';
                    break;
                case 8:
                    ch = BACKSLASH;
                    break;
                default:
                    ch = (char) r.nextInt(300);
                    break;
            }
            buf[i] = ch;
        }
        return new String(buf);
    }

    private void setUpTable(final Connection connection) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255), TEXT CLOB, BIN_DATA BLOB)");
            statement.execute("insert into TEST values(1, 'r1', 'long text 1', 'binary data 1')");
            longText2 = StringUtils.repeat('a', IOUtils.DEFAULT_BUFFER_SIZE - 4);
            longText2 += "\"\r\n\"b\"";
            longText2 += StringUtils.repeat('c', IOUtils.DEFAULT_BUFFER_SIZE - 1);
            statement.execute("insert into TEST values(2, 'r2', '" + longText2 + "', 'binary data 2')");
            longText2 = longText2.replace("\"", "\"\"");
        }
    }

    @Test
    void testCloseBackwardCompatibility() throws IOException {
        try (final Writer writer = mock(Writer.class)) {
            final CSVFormat csvFormat = CSVFormat.DEFAULT;
            try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                assertInitialState(printer);
            }
            verify(writer, never()).flush();
            verify(writer, times(1)).close();
        }
    }

    @Test
    void testCloseWithCsvFormatAutoFlushOff() throws IOException {
        try (final Writer writer = mock(Writer.class)) {
            final CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setAutoFlush(false).get();
            try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                assertInitialState(printer);
            }
            verify(writer, never()).flush();
            verify(writer, times(1)).close();
        }
    }

    @Test
    void testCloseWithCsvFormatAutoFlushOn() throws IOException {
        try (final Writer writer = mock(Writer.class)) {
            final CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setAutoFlush(true).get();
            try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                assertInitialState(printer);
            }
            verify(writer, times(1)).flush();
            verify(writer, times(1)).close();
        }
    }

    @Test
    void testCloseWithFlushOff() throws IOException {
        try (final Writer writer = mock(Writer.class)) {
            final CSVFormat csvFormat = CSVFormat.DEFAULT;
            @SuppressWarnings("resource") final CSVPrinter printer = new CSVPrinter(writer, csvFormat);
            assertInitialState(printer);
            printer.close(false);
            assertEquals(0, printer.getRecordCount());
            verify(writer, never()).flush();
            verify(writer, times(1)).close();
        }
    }

    @Test
    void testCloseWithFlushOn() throws IOException {
        try (final Writer writer = mock(Writer.class)) {
            @SuppressWarnings("resource") final CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT);
            assertInitialState(printer);
            printer.close(true);
            assertEquals(0, printer.getRecordCount());
            verify(writer, times(1)).flush();
        }
    }

    @Test
    void testCRComment() throws IOException {
        final StringWriter sw = new StringWriter();
        final Object value = "abc";
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setCommentMarker('#').get())) {
            assertInitialState(printer);
            printer.print(value);
            assertEquals(0, printer.getRecordCount());
            printer.printComment("This is a comment\r\non multiple lines\rthis is next comment\r");
            assertEquals("abc" + recordSeparator + "# This is a comment" + recordSeparator + "# on multiple lines" + recordSeparator +
                    "# this is next comment" + recordSeparator + "# " + recordSeparator, sw.toString());
            assertEquals(0, printer.getRecordCount());
        }
    }

    @Test
    void testCSV135() throws IOException {
        final List<String> list = new LinkedList<>();
        list.add("\"\""); // ""
        list.add("\\\\"); // \\
        list.add("\\\"\\"); // \"\
        //
        // "",\\,\"\ (unchanged)
        tryFormat(list, null, null, "\"\",\\\\,\\\"\\");
        //
        // """""",\\,"\""\" (quoted, and embedded DQ doubled)
        tryFormat(list, '"', null, "\"\"\"\"\"\",\\\\,\"\\\"\"\\\"");
        //
        // "",\\\\,\\"\\ (escapes escaped, not quoted)
        tryFormat(list, null, '\\', "\"\",\\\\\\\\,\\\\\"\\\\");
        //
        // "\"\"","\\\\","\\\"\\" (quoted, and embedded DQ & escape escaped)
        tryFormat(list, '"', '\\', "\"\\\"\\\"\",\"\\\\\\\\\",\"\\\\\\\"\\\\\"");
        //
        // """""",\\,"\""\" (quoted, embedded DQ escaped)
        tryFormat(list, '"', '"', "\"\"\"\"\"\",\\\\,\"\\\"\"\\\"");
    }

    @Test
    void testCSV259() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final Reader reader = new FileReader("src/test/resources/org/apache/commons/csv/CSV-259/sample.txt");
             final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setEscape('!').get().builder().setQuote(null).get())) {
            assertInitialState(printer);
            printer.print(reader);
            assertEquals("x!,y!,z", sw.toString());
        }
    }

    @Test
    void testDelimeterQuoted() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote('\'').get())) {
            assertInitialState(printer);
            printer.print("a,b,c");
            printer.print("xyz");
            assertEquals("'a,b,c',xyz", sw.toString());
        }
    }

    @Test
    void testDelimeterQuoteNone() throws IOException {
        final StringWriter sw = new StringWriter();
        final CSVFormat format = CSVFormat.DEFAULT.builder().setEscape('!').get().builder().setQuoteMode(QuoteMode.NONE).get();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {
            assertInitialState(printer);
            printer.print("a,b,c");
            printer.print("xyz");
            assertEquals("a!,b!,c,xyz", sw.toString());
        }
    }

    @Test
    void testDelimeterStringQuoted() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setDelimiter("[|]").setQuote('\'').get())) {
            assertInitialState(printer);
            printer.print("a[|]b[|]c");
            printer.print("xyz");
            assertEquals("'a[|]b[|]c'[|]xyz", sw.toString());
        }
    }

    @Test
    void testDelimeterStringQuoteNone() throws IOException {
        final StringWriter sw = new StringWriter();
        final CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter("[|]").setEscape('!').setQuoteMode(QuoteMode.NONE).get();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {
            assertInitialState(printer);
            printer.print("a[|]b[|]c");
            printer.print("xyz");
            printer.print("a[xy]bc[]");
            assertEquals("a![!|!]b![!|!]c[|]xyz[|]a[xy]bc[]", sw.toString());
        }
    }

    @Test
    void testDelimiterEscaped() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setEscape('!').get().builder().setQuote(null).get())) {
            assertInitialState(printer);
            printer.print("a,b,c");
            printer.print("xyz");
            assertEquals("a!,b!,c,xyz", sw.toString());
        }
    }

    @Test
    void testDelimiterPlain() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get())) {
            assertInitialState(printer);
            printer.print("a,b,c");
            printer.print("xyz");
            assertEquals("a,b,c,xyz", sw.toString());
        }
    }

    @Test
    void testDelimiterStringEscaped() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setDelimiter("|||").setEscape('!').setQuote(null).get())) {
            assertInitialState(printer);
            printer.print("a|||b|||c");
            printer.print("xyz");
            assertEquals("a!|!|!|b!|!|!|c|||xyz", sw.toString());
        }
    }

    @Test
    void testDisabledComment() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printComment("This is a comment");
            assertEquals("", sw.toString());
            assertEquals(0, printer.getRecordCount());
        }
    }

    @Test
    void testDontQuoteEuroFirstChar() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.RFC4180)) {
            assertInitialState(printer);
            printer.printRecord(EURO_CH, "Deux");
            assertEquals(EURO_CH + ",Deux" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testEolEscaped() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get().builder().setEscape('!').get())) {
            assertInitialState(printer);
            printer.print("a\rb\nc");
            printer.print("x\fy\bz");
            assertEquals("a!rb!nc,x\fy\bz", sw.toString());
        }
    }

    @Test
    void testEolPlain() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get())) {
            assertInitialState(printer);
            printer.print("a\rb\nc");
            printer.print("x\fy\bz");
            assertEquals("a\rb\nc,x\fy\bz", sw.toString());
        }
    }

    @Test
    void testEolQuoted() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote('\'').get())) {
            assertInitialState(printer);
            printer.print("a\rb\nc");
            printer.print("x\by\fz");
            assertEquals("'a\rb\nc',x\by\fz", sw.toString());
        }
    }

    @Test
    void testEscapeBackslash1() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(QUOTE_CH).get())) {
            assertInitialState(printer);
            printer.print("\\");
        }
        assertEquals("\\", sw.toString());
    }

    @Test
    void testEscapeBackslash2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(QUOTE_CH).get())) {
            assertInitialState(printer);
            printer.print("\\\r");
        }
        assertEquals("'\\\r'", sw.toString());
    }

    @Test
    void testEscapeBackslash3() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(QUOTE_CH).get())) {
            assertInitialState(printer);
            printer.print("X\\\r");
        }
        assertEquals("'X\\\r'", sw.toString());
    }

    @Test
    void testEscapeBackslash4() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(QUOTE_CH).get())) {
            assertInitialState(printer);
            printer.print("\\\\");
        }
        assertEquals("\\\\", sw.toString());
    }

    @Test
    void testEscapeNull1() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setEscape(null).get())) {
            assertInitialState(printer);
            printer.print("\\");
        }
        assertEquals("\\", sw.toString());
    }

    @Test
    void testEscapeNull2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setEscape(null).get())) {
            assertInitialState(printer);
            printer.print("\\\r");
        }
        assertEquals("\"\\\r\"", sw.toString());
    }

    @Test
    void testEscapeNull3() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setEscape(null).get())) {
            assertInitialState(printer);
            printer.print("X\\\r");
        }
        assertEquals("\"X\\\r\"", sw.toString());
    }

    @Test
    void testEscapeNull4() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setEscape(null).get())) {
            assertInitialState(printer);
            printer.print("\\\\");
        }
        assertEquals("\\\\", sw.toString());
    }

    @Test
    void testExcelPrintAllArrayOfArrays() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords((Object[]) new String[][]{{"r1c1", "r1c2"}, {"r2c1", "r2c2"}});
            assertEquals("r1c1,r1c2" + recordSeparator + "r2c1,r2c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllArrayOfArraysWithFirstEmptyValue2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords((Object[]) new String[][]{{""}});
            assertEquals("\"\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllArrayOfArraysWithFirstSpaceValue1() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords((Object[]) new String[][]{{" ", "r1c2"}});
            assertEquals("\" \",r1c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllArrayOfArraysWithFirstTabValue1() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords((Object[]) new String[][]{{"\t", "r1c2"}});
            assertEquals("\"\t\",r1c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllArrayOfLists() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords((Object[]) new List[]{Arrays.asList("r1c1", "r1c2"), Arrays.asList("r2c1", "r2c2")});
            assertEquals("r1c1,r1c2" + recordSeparator + "r2c1,r2c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllArrayOfListsWithFirstEmptyValue2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords((Object[]) new List[]{Arrays.asList("")});
            assertEquals("\"\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllIterableOfArrays() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords(Arrays.asList(new String[][]{{"r1c1", "r1c2"}, {"r2c1", "r2c2"}}));
            assertEquals("r1c1,r1c2" + recordSeparator + "r2c1,r2c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllIterableOfArraysWithFirstEmptyValue2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords(Arrays.asList(new String[][]{{""}}));
            assertEquals("\"\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllIterableOfLists() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords(Arrays.asList(Arrays.asList("r1c1", "r1c2"), Arrays.asList("r2c1", "r2c2")));
            assertEquals("r1c1,r1c2" + recordSeparator + "r2c1,r2c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrintAllStreamOfArrays() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecords(Stream.of(new String[][]{{"r1c1", "r1c2"}, {"r2c1", "r2c2"}}));
            assertEquals("r1c1,r1c2" + recordSeparator + "r2c1,r2c2" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrinter1() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecord("a", "b");
            assertEquals("a,b" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testExcelPrinter2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.EXCEL)) {
            assertInitialState(printer);
            printer.printRecord("a,b", "b");
            assertEquals("\"a,b\",b" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testHeader() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get().builder().setHeader("C1", "C2", "C3").get())) {
            assertEquals(1, printer.getRecordCount());
            printer.printRecord("a", "b", "c");
            printer.printRecord("x", "y", "z");
            assertEquals("C1,C2,C3\r\na,b,c\r\nx,y,z\r\n", sw.toString());
        }
    }

    @Test
    void testHeaderCommentExcel() throws IOException {
        final StringWriter sw = new StringWriter();
        final Date now = new Date();
        final CSVFormat format = CSVFormat.EXCEL;
        try (final CSVPrinter csvPrinter = printWithHeaderComments(sw, now, format)) {
            assertEquals("# Generated by Apache Commons CSV 1.1\r\n# " + now + "\r\nCol1,Col2\r\nA,B\r\nC,D\r\n", sw.toString());
        }
    }

    @Test
    void testHeaderCommentTdf() throws IOException {
        final StringWriter sw = new StringWriter();
        final Date now = new Date();
        final CSVFormat format = CSVFormat.TDF;
        try (final CSVPrinter csvPrinter = printWithHeaderComments(sw, now, format)) {
            assertEquals("# Generated by Apache Commons CSV 1.1\r\n# " + now + "\r\nCol1\tCol2\r\nA\tB\r\nC\tD\r\n", sw.toString());
        }
    }

    @Test
    void testHeaderNotSet() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get())) {
            assertInitialState(printer);
            printer.printRecord("a", "b", "c");
            printer.printRecord("x", "y", "z");
            assertEquals("a,b,c\r\nx,y,z\r\n", sw.toString());
        }
    }

    @Test
    void testInvalidFormat() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setDelimiter(CR));
    }

    @Test
    void testJdbcPrinter() throws IOException, ClassNotFoundException, SQLException {
        final StringWriter sw = new StringWriter();
        final CSVFormat csvFormat = CSVFormat.DEFAULT;
        try (final Connection connection = getH2Connection()) {
            setUpTable(connection);
            try (final Statement stmt = connection.createStatement();
                 final CSVPrinter printer = new CSVPrinter(sw, csvFormat);
                 final ResultSet resultSet = stmt.executeQuery("select ID, NAME, TEXT, BIN_DATA from TEST");) {
                assertInitialState(printer);
                printer.printRecords(resultSet);
                assertEquals(TABLE_RECORD_COUNT, printer.getRecordCount());
            }
        }
        final String csv = sw.toString();
        assertEquals("1,r1,\"long text 1\",\"YmluYXJ5IGRhdGEgMQ==\r\n\"" + recordSeparator + "2,r2,\"" + longText2 + "\",\"YmluYXJ5IGRhdGEgMg==\r\n\"" +
                recordSeparator, csv);
        // Round trip the data
        try (StringReader reader = new StringReader(csv);
             final CSVParser csvParser = csvFormat.parse(reader)) {
            // Row 1
            CSVRecord myRecord = csvParser.nextRecord();
            assertEquals("1", myRecord.get(0));
            assertEquals("r1", myRecord.get(1));
            assertEquals("long text 1", myRecord.get(2));
            assertEquals("YmluYXJ5IGRhdGEgMQ==\r\n", myRecord.get(3));
            // Row 2
            myRecord = csvParser.nextRecord();
            assertEquals("2", myRecord.get(0));
            assertEquals("r2", myRecord.get(1));
            assertEquals("YmluYXJ5IGRhdGEgMg==\r\n", myRecord.get(3));
        }
    }

    @Test
    void testJdbcPrinterWithFirstEmptyValue2() throws IOException, ClassNotFoundException, SQLException {
        final StringWriter sw = new StringWriter();
        try (final Connection connection = getH2Connection()) {
            try (final Statement stmt = connection.createStatement();
                 final ResultSet resultSet = stmt.executeQuery("select '' AS EMPTYVALUE from DUAL");
                 final CSVPrinter printer = CSVFormat.DEFAULT.builder().setHeader(resultSet).get().print(sw)) {
                printer.printRecords(resultSet);
            }
        }
        assertEquals("EMPTYVALUE" + recordSeparator + "\"\"" + recordSeparator, sw.toString());
    }

    @Test
    void testJdbcPrinterWithResultSet() throws IOException, ClassNotFoundException, SQLException {
        final StringWriter sw = new StringWriter();
        try (final Connection connection = getH2Connection()) {
            setUpTable(connection);
            try (final Statement stmt = connection.createStatement();
                 final ResultSet resultSet = stmt.executeQuery("select ID, NAME, TEXT from TEST");
                 final CSVPrinter printer = CSVFormat.DEFAULT.builder().setHeader(resultSet).get().print(sw)) {
                printer.printRecords(resultSet);
            }
        }
        assertEquals("ID,NAME,TEXT" + recordSeparator + "1,r1,\"long text 1\"" + recordSeparator + "2,r2,\"" + longText2 + "\"" + recordSeparator,
                sw.toString());
    }

    @Test
    void testJdbcPrinterWithResultSetHeader() throws IOException, ClassNotFoundException, SQLException {
        final StringWriter sw = new StringWriter();
        try (final Connection connection = getH2Connection()) {
            setUpTable(connection);
            try (final Statement stmt = connection.createStatement();
                 final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);) {
                try (final ResultSet resultSet = stmt.executeQuery("select ID, NAME from TEST")) {
                    printer.printRecords(resultSet, true);
                    assertEquals(TABLE_RECORD_COUNT, printer.getRecordCount());
                    assertEquals("ID,NAME" + recordSeparator + "1,r1" + recordSeparator + "2,r2" + recordSeparator, sw.toString());
                }
                try (final ResultSet resultSet = stmt.executeQuery("select ID, NAME from TEST")) {
                    printer.printRecords(resultSet, false);
                    assertEquals(TABLE_RECORD_COUNT * 2, printer.getRecordCount());
                    assertNotEquals("ID,NAME" + recordSeparator + "1,r1" + recordSeparator + "2,r2" + recordSeparator, sw.toString());
                }
            }
        }
    }

    @Test
    void testJdbcPrinterWithResultSetMetaData() throws IOException, ClassNotFoundException, SQLException {
        final StringWriter sw = new StringWriter();
        try (final Connection connection = getH2Connection()) {
            setUpTable(connection);
            try (final Statement stmt = connection.createStatement();
                 final ResultSet resultSet = stmt.executeQuery("select ID, NAME, TEXT from TEST");
                 final CSVPrinter printer = CSVFormat.DEFAULT.builder().setHeader(resultSet.getMetaData()).get().print(sw)) {
                // The header is the first record.
                assertEquals(1, printer.getRecordCount());
                printer.printRecords(resultSet);
                assertEquals(3, printer.getRecordCount());
                assertEquals("ID,NAME,TEXT" + recordSeparator + "1,r1,\"long text 1\"" + recordSeparator + "2,r2,\"" + longText2 + "\"" + recordSeparator,
                        sw.toString());
            }
        }
    }
    
    @Test
    void testJira135_part1() throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator('\n').get().builder().setQuote(DQUOTE_CHAR).get().builder().setEscape(BACKSLASH).get();
        final StringWriter sw = new StringWriter();
        final List<String> list = new LinkedList<>();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {
            list.add("\"");
            printer.printRecord(list);
        }
        final String expected = "\"\\\"\"" + format.getRecordSeparator();
        assertEquals(expected, sw.toString());
        final String[] record0 = toFirstRecordValues(expected, format);
        assertArrayEquals(expectNulls(list.toArray(), format), record0);
    }
    
    
    @Test
    void testJira135_part2() throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator('\n').get().builder().setQuote(DQUOTE_CHAR).get().builder().setEscape(BACKSLASH).get();
        final StringWriter sw = new StringWriter();
        final List<String> list = new LinkedList<>();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {
            list.add("\\");
            printer.printRecord(list);
        }
        final String expected = "\"\\\\\"" + format.getRecordSeparator();
        assertEquals(expected, sw.toString());
        final String[] record0 = toFirstRecordValues(expected, format);
        assertArrayEquals(expectNulls(list.toArray(), format), record0);
    }

    @Test
    @Disabled
    void testJira135All() throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator('\n').get().builder().setQuote(DQUOTE_CHAR).get().builder().setEscape(BACKSLASH).get();
        final StringWriter sw = new StringWriter();
        final List<String> list = new LinkedList<>();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {
            list.add("\"");
            list.add("\n");
            list.add("\\");
            printer.printRecord(list);
        }
        final String expected = "\"\\\"\",\"\\n\",\"\\\"" + format.getRecordSeparator();
        assertEquals(expected, sw.toString());
        final String[] record0 = toFirstRecordValues(expected, format);
        assertArrayEquals(expectNulls(list.toArray(), format), record0);
    }

    @Test
    void testMongoDbCsvBasic() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_CSV)) {
            printer.printRecord("a", "b");
            assertEquals("a,b" + recordSeparator, sw.toString());
            assertEquals(1, printer.getRecordCount());
        }
    }

    @Test
    void testMongoDbCsvCommaInValue() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_CSV)) {
            printer.printRecord("a,b", "c");
            assertEquals("\"a,b\",c" + recordSeparator, sw.toString());
            assertEquals(1, printer.getRecordCount());
        }
    }

    @Test
    void testMongoDbCsvDoubleQuoteInValue() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_CSV)) {
            printer.printRecord("a \"c\" b", "d");
            assertEquals("\"a \"\"c\"\" b\",d" + recordSeparator, sw.toString());
            assertEquals(1, printer.getRecordCount());
        }
    }

    @Test
    void testMongoDbCsvTabInValue() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_CSV)) {
            printer.printRecord("a\tb", "c");
            assertEquals("a\tb,c" + recordSeparator, sw.toString());
            assertEquals(1, printer.getRecordCount());
        }
    }

    @Test
    void testMongoDbTsvBasic() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_TSV)) {
            printer.printRecord("a", "b");
            assertEquals("a\tb" + recordSeparator, sw.toString());
            assertEquals(1, printer.getRecordCount());
        }
    }

    @Test
    void testMongoDbTsvCommaInValue() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_TSV)) {
            printer.printRecord("a,b", "c");
            assertEquals("a,b\tc" + recordSeparator, sw.toString());
            assertEquals(1, printer.getRecordCount());
        }
    }

    @Test
    void testMongoDbTsvTabInValue() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.MONGODB_TSV)) {
            printer.printRecord("a\tb", "c");
            assertEquals("\"a\tb\"\tc" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testMultiLineComment() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setCommentMarker('#').get())) {
            printer.printComment("This is a comment\non multiple lines");
            assertEquals("# This is a comment" + recordSeparator + "# on multiple lines" + recordSeparator, sw.toString());
            assertEquals(0, printer.getRecordCount());
        }
    }

    @Test
    void testMySqlNullOutput() throws IOException {
        assertRecord(new String[]{"\\N", null},
                "\\\\N\t\\N\n", CSVFormat.MYSQL.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"\\N", "A"},
                "\\\\N\tA\n", CSVFormat.MYSQL.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"\n", "A"},
                "\\n\tA\n", CSVFormat.MYSQL.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"", null},
                "\tNULL\n", CSVFormat.MYSQL.builder()
                        .setNullString("NULL")
                        .get());

        assertRecord(new String[]{"", null},
                "\t\\N\n", CSVFormat.MYSQL);

        assertRecord(new String[]{"\\N", "", "\u000e,\\\r"},
                "\\\\N\t\t\u000e,\\\\\\r\n", CSVFormat.MYSQL);

        assertRecord(new String[]{"NULL", "\\\r"},
                "NULL\t\\\\\\r\n", CSVFormat.MYSQL);

        assertRecord(new String[]{"\\\r"},
                "\\\\\\r\n", CSVFormat.MYSQL);
    }

    @Test
    void testMySqlNullStringDefault() {
        assertEquals("\\N", CSVFormat.MYSQL.getNullString());
    }

    @Test
    void testNewCsvPrinterAppendableNullFormat() {
        StringWriter stringWriter = new StringWriter();
        assertThrows(NullPointerException.class, () -> new CSVPrinter(stringWriter, null));
    }

    @Test
    void testNewCsvPrinterNullAppendableFormat() {
        assertThrows(NullPointerException.class, () -> new CSVPrinter(null, CSVFormat.DEFAULT));
    }

    @Test
    void testNotFlushable() throws IOException {
        final Appendable out = new StringBuilder();
        try (final CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            printer.printRecord("a", "b", "c");
            assertEquals("a,b,c" + recordSeparator, out.toString());
            printer.flush();
        }
    }

    @Test
    void testParseCustomNullValues() throws IOException {
        final StringWriter sw = new StringWriter();
        final CSVFormat format = CSVFormat.DEFAULT.builder().setNullString("NULL").get();
        try (final CSVPrinter printer = new CSVPrinter(sw, format)) {
            printer.printRecord("a", null, "b");
        }
        final String csvString = sw.toString();
        assertEquals("a,NULL,b" + recordSeparator, csvString);
        try (final CSVParser iterable = format.parse(new StringReader(csvString))) {
            final Iterator<CSVRecord> iterator = iterable.iterator();
            final CSVRecord myRecord = iterator.next();
            assertEquals("a", myRecord.get(0));
            assertNull(myRecord.get(1));
            assertEquals("b", myRecord.get(2));
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    void testPlainEscaped() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get().builder().setEscape('!').get())) {
            printer.print("abc");
            printer.print("xyz");
            assertEquals("abc,xyz", sw.toString());
        }
    }

    @Test
    void testPlainPlain() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get())) {
            printer.print("abc");
            printer.print("xyz");
            assertEquals("abc,xyz", sw.toString());
        }
    }

    @Test
    void testPlainQuoted() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote('\'').get())) {
            printer.print("abc");
            assertEquals("abc", sw.toString());
        }
    }

    @Test
    @Disabled
    void testPostgreSqlCsvNullOutput() throws IOException {
        assertRecord(new String[]{"NULL", null},
                "\"NULL\",NULL\n", CSVFormat.POSTGRESQL_CSV.builder()
                        .setQuote(DQUOTE_CHAR)
                        .setNullString("NULL")
                        .setQuoteMode(QuoteMode.ALL_NON_NULL)
                        .get());

        assertRecord(new String[]{"\\N", null},
                "\\\\N\t\\N\n", CSVFormat.POSTGRESQL_CSV.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"\\N", "A"},
                "\\\\N\tA\n", CSVFormat.POSTGRESQL_CSV.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"\n", "A"},
                "\\n\tA\n", CSVFormat.POSTGRESQL_CSV.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"", null},
                "\tNULL\n", CSVFormat.POSTGRESQL_CSV.builder()
                        .setNullString("NULL")
                        .get());

        assertRecord(new String[]{"", null},
                "\t\\N\n", CSVFormat.POSTGRESQL_CSV);

        assertRecord(new String[]{"\\N", "", "\u000e,\\\r"},
                "\\\\N\t\t\u000e,\\\\\\r\n", CSVFormat.POSTGRESQL_CSV);

        assertRecord(new String[]{"NULL", "\\\r"},
                "NULL\t\\\\\\r\n", CSVFormat.POSTGRESQL_CSV);

        assertRecord(new String[]{"\\\r"},
                "\\\\\\r\n", CSVFormat.POSTGRESQL_CSV);
    }
    
    @Test
    @Disabled
    void testPostgreSqlCsvTextOutput() throws IOException {
        assertRecord(new String[]{"NULL", null},
                "\"NULL\"\tNULL\n", CSVFormat.POSTGRESQL_TEXT.builder()
                        .setQuote(DQUOTE_CHAR)
                        .setNullString("NULL")
                        .setQuoteMode(QuoteMode.ALL_NON_NULL)
                        .get());

        assertRecord(new String[]{"\\N", null},
                "\\\\N\t\\N\n", CSVFormat.POSTGRESQL_TEXT.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"\\N", "A"},
                "\\\\N\tA\n", CSVFormat.POSTGRESQL_TEXT.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"\n", "A"},
                "\\n\tA\n", CSVFormat.POSTGRESQL_TEXT.builder()
                        .setNullString("\\N")
                        .get());

        assertRecord(new String[]{"", null},
                "\tNULL\n", CSVFormat.POSTGRESQL_TEXT.builder()
                        .setNullString("NULL")
                        .get());

        assertRecord(new String[]{"", null},
                "\t\\N\n", CSVFormat.POSTGRESQL_TEXT);

        assertRecord(new String[]{"\\N", "", "\u000e,\\\r"},
                "\\\\N\t\t\u000e,\\\\\\r\n", CSVFormat.POSTGRESQL_TEXT);

        assertRecord(new String[]{"NULL", "\\\r"},
                "NULL\t\\\\\\r\n", CSVFormat.POSTGRESQL_TEXT);

        assertRecord(new String[]{"\\\r"},
                "\\\\\\r\n", CSVFormat.POSTGRESQL_TEXT);
    }

    private void assertRecord(String[] input, String expectedOutput, CSVFormat format) throws IOException {
        StringWriter writer = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(writer, format)) {
            printer.printRecord((Object[]) input);
        }
        assertEquals(expectedOutput, writer.toString());
        String[] record0 = toFirstRecordValues(expectedOutput, format);
        assertArrayEquals(expectNulls(input, format), record0);
    }
    
    @Test
    void testPostgreSqlNullStringDefaultCsv() {
        assertEquals("", CSVFormat.POSTGRESQL_CSV.getNullString());
    }

    @Test
    void testPostgreSqlNullStringDefaultText() {
        assertEquals("\\N", CSVFormat.POSTGRESQL_TEXT.getNullString());
    }

    @Test
    void testPrint() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = CSVFormat.DEFAULT.print(sw)) {
            assertInitialState(printer);
            printer.printRecord("a", "b\\c");
            assertEquals("a,b\\c" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrintCSVParser() throws IOException {
        // @formatter:off
        final String code = "a1,b1\n" + // 1)
                "a2,b2\n" + // 2)
                "a3,b3\n" + // 3)
                "a4,b4\n";  // 4)
        // @formatter:on
        final String[][] res = {{"a1", "b1"}, {"a2", "b2"}, {"a3", "b3"}, {"a4", "b4"}};
        final CSVFormat format = CSVFormat.DEFAULT;
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = format.print(sw);
             final CSVParser parser = CSVParser.parse(code, format)) {
            assertInitialState(printer);
            printer.printRecords(parser);
        }
        try (final CSVParser parser = CSVParser.parse(sw.toString(), format)) {
            final List<CSVRecord> records = parser.getRecords();
            assertFalse(records.isEmpty());
            Utils.compare("Fail", res, records);
        }
    }

    @Test
    void testPrintCSVRecord() throws IOException {
        // @formatter:off
        final String code = "a1,b1\n" + // 1)
                "a2,b2\n" +  // 2)
                "a3,b3\n" +  // 3)
                "a4,b4\n";   // 4)
        // @formatter:on
        final String[][] res = {{"a1", "b1"}, {"a2", "b2"}, {"a3", "b3"}, {"a4", "b4"}};
        final CSVFormat format = CSVFormat.DEFAULT;
        final StringWriter sw = new StringWriter();
        int row = 0;
        try (final CSVPrinter printer = format.print(sw);
             final CSVParser parser = CSVParser.parse(code, format)) {
            assertInitialState(printer);
            for (final CSVRecord myRecord : parser) {
                printer.printRecord(myRecord);
                assertEquals(++row, printer.getRecordCount());
            }
            assertEquals(row, printer.getRecordCount());
        }
        try (final CSVParser parser = CSVParser.parse(sw.toString(), format)) {
            final List<CSVRecord> records = parser.getRecords();
            assertFalse(records.isEmpty());
            Utils.compare("Fail", res, records);
        }
    }

    @Test
    void testPrintCSVRecords() throws IOException {
        // @formatter:off
        final String code = "a1,b1\n" + // 1)
                "a2,b2\n" + // 2)
                "a3,b3\n" + // 3)
                "a4,b4\n";  // 4)
        // @formatter:on
        final String[][] res = {{"a1", "b1"}, {"a2", "b2"}, {"a3", "b3"}, {"a4", "b4"}};
        final CSVFormat format = CSVFormat.DEFAULT;
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = format.print(sw);
             final CSVParser parser = CSVParser.parse(code, format)) {
            assertInitialState(printer);
            printer.printRecords(parser.getRecords());
        }
        try (final CSVParser parser = CSVParser.parse(sw.toString(), format)) {
            final List<CSVRecord> records = parser.getRecords();
            assertFalse(records.isEmpty());
            Utils.compare("Fail", res, records);
        }
    }

    @Test
    void testPrintCustomNullValues() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setNullString("NULL").get())) {
            assertInitialState(printer);
            printer.printRecord("a", null, "b");
            assertEquals("a,NULL,b" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter1() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a", "b");
            assertEquals(1, printer.getRecordCount());
            assertEquals("a,b" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter2() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a,b", "b");
            assertEquals("\"a,b\",b" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter3() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a, b", "b ");
            assertEquals("\"a, b\",\"b \"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter4() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a", "b\"c");
            assertEquals("a,\"b\"\"c\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter5() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a", "b\nc");
            assertEquals("a,\"b\nc\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter6() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a", "b\r\nc");
            assertEquals("a,\"b\r\nc\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrinter7() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a", "b\\c");
            assertEquals("a,b\\c" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrintNullValues() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            assertInitialState(printer);
            printer.printRecord("a", null, "b");
            assertEquals("a,,b" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testPrintOnePositiveInteger() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.MINIMAL).get())) {
            assertInitialState(printer);
            printer.print(Integer.MAX_VALUE);
            assertEquals(String.valueOf(Integer.MAX_VALUE), sw.toString());
        }
    }

    /**
     * Test to target the use of {@link IOUtils#copy(java.io.Reader, Appendable)} which directly buffers the value from the Reader to the Appendable.
     *
     * <p>
     * Requires the format to have no quote or escape character, value to be a {@link Reader Reader} and the output <em>MUST NOT</em> be a {@link Writer Writer}
     * but some other Appendable.
     * </p>
     *
     * @throws IOException Not expected to happen
     */
    @Test
    void testPrintReaderWithoutQuoteToAppendable() throws IOException {
        final StringBuilder sb = new StringBuilder();
        final String content = "testValue";
        try (final CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT.builder().setQuote(null).get())) {
            assertInitialState(printer);
            final StringReader value = new StringReader(content);
            printer.print(value);
        }
        assertEquals(content, sb.toString());
    }

    /**
     * Test to target the use of {@link IOUtils#copyLarge(java.io.Reader, Writer)} which directly buffers the value from the Reader to the Writer.
     *
     * <p>
     * Requires the format to have no quote or escape character, value to be a {@link Reader Reader} and the output <em>MUST</em> be a {@link Writer Writer}.
     * </p>
     *
     * @throws IOException Not expected to happen
     */
    @Test
    void testPrintReaderWithoutQuoteToWriter() throws IOException {
        final StringWriter sw = new StringWriter();
        final String content = "testValue";
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get())) {
            final StringReader value = new StringReader(content);
            printer.print(value);
        }
        assertEquals(content, sw.toString());
    }

    @Test
    void testPrintRecordStream() throws IOException {
        // @formatter:off
        final String code = "a1,b1\n" + // 1)
                "a2,b2\n" + // 2)
                "a3,b3\n" + // 3)
                "a4,b4\n";  // 4)
        // @formatter:on
        final String[][] res = {{"a1", "b1"}, {"a2", "b2"}, {"a3", "b3"}, {"a4", "b4"}};
        final CSVFormat format = CSVFormat.DEFAULT;
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = format.print(sw);
             final CSVParser parser = CSVParser.parse(code, format)) {
            long count = 0;
            for (final CSVRecord myRecord : parser) {
                printer.printRecord(myRecord.stream());
                assertEquals(++count, printer.getRecordCount());
            }
        }
        try (final CSVParser parser = CSVParser.parse(sw.toString(), format)) {
            final List<CSVRecord> records = parser.getRecords();
            assertFalse(records.isEmpty());
            Utils.compare("Fail", res, records);
        }
    }

    @Test
    void testPrintRecordsWithCSVRecord() throws IOException {
        final String[] values = {"A", "B", "C"};
        final String rowData = StringUtils.join(values, ',');
        final CharArrayWriter charArrayWriter = new CharArrayWriter(0);
        try (final CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(rowData));
             final CSVPrinter printer = CSVFormat.INFORMIX_UNLOAD.print(charArrayWriter)) {
            long count = 0;
            for (final CSVRecord myRecord : parser) {
                printer.printRecord(myRecord);
                assertEquals(++count, printer.getRecordCount());
            }
        }
        assertEquals(6, charArrayWriter.size());
        assertEquals("A|B|C" + CSVFormat.INFORMIX_UNLOAD.getRecordSeparator(), charArrayWriter.toString());
    }

    @Test
    void testPrintRecordsWithEmptyVector() throws IOException {
        final PrintStream out = System.out;
        try {
            System.setOut(new PrintStream(NullOutputStream.INSTANCE));
            try (CSVPrinter printer = CSVFormat.POSTGRESQL_TEXT.printer()) {
                final Vector<CSVFormatTest.EmptyEnum> vector = new Vector<>();
                final int expectedCapacity = 23;
                vector.setSize(expectedCapacity);
                printer.printRecords(vector);
                assertEquals(expectedCapacity, vector.capacity());
                assertEquals(expectedCapacity, printer.getRecordCount());
            }
        } finally {
            System.setOut(out);
        }
    }

    @Test
    void testPrintRecordsWithObjectArray() throws IOException {
        final CharArrayWriter charArrayWriter = new CharArrayWriter(0);
        final Object[] objectArray = new Object[6];
        try (CSVPrinter printer = CSVFormat.INFORMIX_UNLOAD.print(charArrayWriter)) {
            final HashSet<BatchUpdateException> hashSet = new HashSet<>();
            objectArray[3] = hashSet;
            printer.printRecords(objectArray);
            assertEquals(objectArray.length, printer.getRecordCount());
        }
        assertEquals(6, charArrayWriter.size());
        assertEquals("\n\n\n\n\n\n", charArrayWriter.toString());
    }

    @Test
    void testPrintRecordsWithResultSetOneRow() throws IOException, SQLException {
        try (CSVPrinter printer = CSVFormat.MYSQL.printer()) {
            try (ResultSet resultSet = new SimpleResultSet()) {
                assertInitialState(printer);
                printer.printRecords(resultSet);
                assertInitialState(printer);
                assertEquals(0, resultSet.getRow());
            }
        }
    }

    @Test
    void testPrintToFileWithCharsetUtf16Be() throws IOException {
        final File file = createTempFile();
        try (final CSVPrinter printer = CSVFormat.DEFAULT.print(file, StandardCharsets.UTF_16BE)) {
            printer.printRecord("a", "b\\c");
        }
        assertEquals("a,b\\c" + recordSeparator, FileUtils.readFileToString(file, StandardCharsets.UTF_16BE));
    }

    @Test
    void testPrintToFileWithDefaultCharset() throws IOException {
        final File file = createTempFile();
        try (final CSVPrinter printer = CSVFormat.DEFAULT.print(file, Charset.defaultCharset())) {
            printer.printRecord("a", "b\\c");
        }
        assertEquals("a,b\\c" + recordSeparator, FileUtils.readFileToString(file, Charset.defaultCharset()));
    }

    @Test
    void testPrintToPathWithDefaultCharset() throws IOException {
        final Path file = createTempPath();
        try (final CSVPrinter printer = CSVFormat.DEFAULT.print(file, Charset.defaultCharset())) {
            printer.printRecord("a", "b\\c");
        }
        assertEquals("a,b\\c" + recordSeparator, new String(Files.readAllBytes(file), Charset.defaultCharset()));
    }

    @Test
    void testQuoteAll() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).get())) {
            printer.printRecord("a", "b\nc", "d");
            assertEquals("\"a\",\"b\nc\",\"d\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testQuoteCommaFirstChar() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.RFC4180)) {
            printer.printRecord(",");
            assertEquals("\",\"" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testQuoteNonNumeric() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.NON_NUMERIC).get())) {
            printer.printRecord("a", "b\nc", Integer.valueOf(1));
            assertEquals("\"a\",\"b\nc\",1" + recordSeparator, sw.toString());
        }
    }

    @Test
    void testRandomDefault() throws Exception {
        doRandom(CSVFormat.DEFAULT, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    void testRandomExcel() throws Exception {
        doRandom(CSVFormat.EXCEL, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    @Disabled
    void testRandomMongoDbCsv() throws Exception {
        doRandom(CSVFormat.MONGODB_CSV, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    void testRandomMySql() throws Exception {
        doRandom(CSVFormat.MYSQL, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    @Disabled
    void testRandomOracle() throws Exception {
        doRandom(CSVFormat.ORACLE, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    @Disabled
    void testRandomPostgreSqlCsv() throws Exception {
        doRandom(CSVFormat.POSTGRESQL_CSV, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    void testRandomPostgreSqlText() throws Exception {
        doRandom(CSVFormat.POSTGRESQL_TEXT, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    void testRandomRfc4180() throws Exception {
        doRandom(CSVFormat.RFC4180, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    void testRandomTdf() throws Exception {
        doRandom(CSVFormat.TDF, ITERATIONS_FOR_RANDOM_TEST);
    }

    @Test
    void testSingleLineComment() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setCommentMarker('#').get())) {
            printer.printComment("This is a comment");
            assertEquals("# This is a comment" + recordSeparator, sw.toString());
            assertEquals(0, printer.getRecordCount());
        }
    }

    @Test
    void testSingleQuoteQuoted() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote('\'').get())) {
            printer.print("a'b'c");
            printer.print("xyz");
            assertEquals("'a''b''c',xyz", sw.toString());
        }
    }

    @Test
    void testSkipHeaderRecordFalse() throws IOException {
        // functionally identical to testHeader, used to test CSV-153
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get().builder().setHeader("C1", "C2", "C3").get().builder().setSkipHeaderRecord(false).get())) {
            printer.printRecord("a", "b", "c");
            printer.printRecord("x", "y", "z");
            assertEquals("C1,C2,C3\r\na,b,c\r\nx,y,z\r\n", sw.toString());
        }
    }

    @Test
    void testSkipHeaderRecordTrue() throws IOException {
        // functionally identical to testHeaderNotSet, used to test CSV-153
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setQuote(null).get().builder().setHeader("C1", "C2", "C3").get().builder().setSkipHeaderRecord(true).get())) {
            printer.printRecord("a", "b", "c");
            printer.printRecord("x", "y", "z");
            assertEquals("a,b,c\r\nx,y,z\r\n", sw.toString());
        }
    }

    @Test
    void testTrailingDelimiterOnTwoColumns() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setTrailingDelimiter(true).get())) {
            printer.printRecord("A", "B");
            assertEquals("A,B,\r\n", sw.toString());
        }
    }

    @Test
    void testTrimOffOneColumn() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setTrim(false).get())) {
            printer.print(" A ");
            assertEquals("\" A \"", sw.toString());
        }
    }

    @Test
    void testTrimOnOneColumn() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setTrim(true).get())) {
            printer.print(" A ");
            assertEquals("A", sw.toString());
        }
    }

    @Test
    void testTrimOnTwoColumns() throws IOException {
        final StringWriter sw = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setTrim(true).get())) {
            printer.print(" A ");
            printer.print(" B ");
            assertEquals("A,B", sw.toString());
        }
    }

    private String[] toFirstRecordValues(final String expected, final CSVFormat format) throws IOException {
        try (final CSVParser parser = CSVParser.parse(expected, format)) {
            return parser.getRecords().get(0).values();
        }
    }

    private void tryFormat(final List<String> list, final Character quote, final Character escape, final String expected) throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setQuote(quote).get().builder().setEscape(escape).get().builder().setRecordSeparator(null).get();
        final Appendable out = new StringBuilder();
        try (final CSVPrinter printer = new CSVPrinter(out, format)) {
            printer.printRecord(list);
        }
        assertEquals(expected, out.toString());
    }

}
