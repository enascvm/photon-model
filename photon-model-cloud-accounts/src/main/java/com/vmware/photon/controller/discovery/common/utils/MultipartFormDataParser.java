/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.discovery.common.utils;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_MULTIPART_FORM_DATA;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.QUOTE;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.SEMICOLON;
import static com.vmware.xenon.common.Operation.CR_LF;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vmware.xenon.common.Operation;

/**
 * This class provides some minimal utility functions to parse a multipart/form-data operation
 * per the specifications set by RFC7578 and RFC2046.
 *
 * Specifically, this parser will seek that an operation's body
 * (with content-type multipart/form-data) conforms to RFC specifications, and then will return a
 * mapping of section names and their parsed bodies in the form of a {@link HashMap}.
 *
 * This parser does not strictly enforce any ordering of requested headers, but does enforce that
 * the "Content-Disposition" header and "name" parameter are set, along with some content (although
 * an empty string is still considered content).
 *
 * Character sets are assumed to be UTF-8 in the context of Discovery, and will be parsed as such.
 *
 * References:
 *
 * {@url https://tools.ietf.org/html/rfc7578}
 * {@url https://tools.ietf.org/html/rfc2046}
 */
public class MultipartFormDataParser {

    private static final Pattern MULTIPART_FORM_PATTERN =
            Pattern.compile("multipart/form-data;\\s+boundary=([-a-zA-Z0-9]*)$");
    private static final Pattern MULTIPART_FORM_PATTERN_QUOTES =
            Pattern.compile("multipart/form-data;\\s+boundary=\"(.*)\"$");
    private static final int MAXIMUM_BOUNDARY_LENGTH = 70;
    private static final String BOUNDARY_PREFIX = "--";

    private Map<String, List<FormData>> form;

    /**
     * Parses a multipart/form-data operation's body and returns a {@link MultipartFormDataParser}
     * of the parsed data sections, where each key is the section names of the form request.
     *
     * @param op The requesting operation
     *
     * @throws IllegalStateException Thrown for not meeting valid header/data requirements.
     * @throws IndexOutOfBoundsException Thrown on an invalid parsing of the operation body.
     */
    public MultipartFormDataParser(Operation op) throws IllegalStateException, IndexOutOfBoundsException {

        // Validate that the operation is actually a multipart/form-data request.
        if (op.getContentType() == null || !op.getContentType().toLowerCase()
                .startsWith(CONTENT_TYPE_MULTIPART_FORM_DATA.toLowerCase())) {
            throw new IllegalStateException(
                    String.format("Request does not contain '%s' content-type header.",
                            CONTENT_TYPE_MULTIPART_FORM_DATA));
        }

        // Per https://tools.ietf.org/html/rfc2046#section-5.1, the boundary must have a preceding
        // "--" for each delimited time it is seen.
        String multipartBoundary = String.format("%s%s", BOUNDARY_PREFIX, getBoundary(op));

        // Split the operation body via the boundary defined in the content-type header.
        String[] form = new String(op.getBody(byte[].class), StandardCharsets.UTF_8)
                .split(String.format("%s%s", multipartBoundary, CR_LF));

        // Validate that at least a body exists, along with the required Content-Disposition header line.
        if (form.length < 2) {
            throw new IllegalStateException("Malformed form content.");
        }

        // By splitting rules, the very last entry should still have an indication of the boundary
        // with some additional values. Validate it exists, and then remove it.
        String endBoundary = String.format("%s%s%s", multipartBoundary, BOUNDARY_PREFIX, CR_LF);
        if (!form[form.length - 1].endsWith(endBoundary)) {
            throw new IllegalStateException("No end-boundary for this form data.");
        }

        form[form.length - 1] = form[form.length - 1].substring(0,
                form[form.length - 1].indexOf(endBoundary));

        // For each set of form-data, create a corresponding FormData object and join into a Map
        // with the form name as the key. Per RFC7578, multiple values may be present under the
        // same section name (i.e. multiple files), so this returns a List of FormData objects
        // instead of individual values.
        try {
            this.form = Arrays.stream(form)
                    .filter(s -> !s.isEmpty())
                    .map(row -> {
                        try {
                            return new FormData(row);
                        } catch (IllegalStateException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toMap(
                            FormData::getName,
                            d -> new ArrayList<>(Collections.singletonList(d)),
                            (a, b) -> {
                                a.addAll(b);
                                return a;
                            }, HashMap::new));
        } catch (RuntimeException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns a single {@link FormData} object from a specific key. If the key holds multiple
     * form data objects, throws an error indicating multiple values exist.
     *
     * @param name The key to search for
     *
     * @return The value held at key (if its the only value present). Null if no values are present.
     * @throws IllegalStateException Thrown if multiple values exist for the given key.
     */
    public FormData getParameter(String name) throws IllegalStateException {
        List<FormData> data = getParameterValues(name);

        if (data == null || data.isEmpty()) {
            return null;
        }

        if (data.size() > 1) {
            throw new IllegalStateException(String.format("Too many values set for key '%s'.", name));
        }

        return data.get(0);
    }

    /**
     * Returns a {@link List} of {@link FormData} objects at the request key.
     *
     * @param name The key to search for
     * @return The values held at key (if present). Otherwise, null.
     */
    public List<FormData> getParameterValues(String name) {
        return this.form.get(name);
    }

    /**
     * Retrieves the boundary from a multipart/form-data content-type header.
     *
     * @param op The operation to check
     *
     * @return The boundary field of a multipart/form-data header.
     *
     * @throws IllegalStateException Throws an exception if the content-type
     * header is not as expected (multipart/form-data; boundary=...)
     */
    private String getBoundary(Operation op) throws IllegalStateException {
        String boundary;
        Matcher defaultMatcher = MULTIPART_FORM_PATTERN.matcher(op.getContentType());
        if (!defaultMatcher.matches()) {
            Matcher quotesMatcher = MULTIPART_FORM_PATTERN_QUOTES.matcher(op.getContentType());
            if (!quotesMatcher.matches()) {
                throw new IllegalStateException(
                        "Illegal content-type header. Missing valid 'boundary' parameter.");
            }

            boundary = quotesMatcher.group(1);
        } else {
            boundary = defaultMatcher.group(1);
        }

        // Per RFC2046, the boundary must not be greater than 70 characters. Reject if larger.
        if (boundary.length() > MAXIMUM_BOUNDARY_LENGTH) {
            throw new IllegalStateException(
                    String.format("'boundary' must not be greater than %d characters.",
                            MAXIMUM_BOUNDARY_LENGTH));
        }

        return boundary;
    }

    /**
     * Helper method to get a field value from a form-data block.
     *
     * @param header The separated header row. This has already been stripped from its boundary
     *               sections.
     * @param fieldName The name of the field.
     * @param expectedStringStart The actual field text to search for.
     * @param endSeparator The separator to denote the end of the field value.
     * @param isRequired Boolean to determine if this field is required or not. If it is and the value
     *                   is not present, then an error is thrown. Otherwise, null will be returned.
     * @return The value of the parsed field, or null if not required (and not found).
     *
     * @throws IllegalStateException Thrown if a field is required and is not present.
     */
    private static String getFormDataInfo(String header, String fieldName, String expectedStringStart,
            String endSeparator, boolean isRequired) throws IllegalStateException {
        return getFormDataInfo(header, fieldName, expectedStringStart, new String[]{endSeparator},
                isRequired);
    }

    /**
     *  Helper method to get a field value from a form-data block.
     *
     * @param header The separated header row. This has already been stripped from its boundary
     *               sections.
     * @param fieldName The name of the field.
     * @param expectedStringStart The actual field text to search for.
     * @param endSeparators The separator(s) to denote the end of the field value. The first one
     *                      found will be chosen. This is for situations like `name`, where it is
     *                      optionally not the last item in its line (i.e. "filename" may follow it).
     * @param isRequired Boolean to determine if this field is required or not. If it is and the value
     *                   is not present, then an error is thrown. Otherwise, null will be returned.
     *
     * @return The value of the parsed field, or null if not required (and not found).
     *
     * @throws IllegalStateException Thrown if a field is required and is not present.
     */
    private static String getFormDataInfo(String header, String fieldName, String expectedStringStart,
            String[] endSeparators, boolean isRequired) throws IllegalStateException {
        String lowerCaseHeader = header.toLowerCase();

        if (!lowerCaseHeader.contains(expectedStringStart)) {
            // If the field was required, then throw an error.
            if (isRequired) {
                throw new IllegalStateException(String.format("Form header is missing '%s'.", fieldName));
            }

            // Otherwise, just return null.
            return null;
        }

        int startingIndex = lowerCaseHeader.indexOf(expectedStringStart) + expectedStringStart.length();
        int separatorIndex = Arrays.stream(endSeparators)
                .map(separator -> header.indexOf(separator, startingIndex))
                .filter(index -> index != -1)
                .min(Integer::compare)
                .orElseGet(header::length);

        return header.substring(startingIndex, separatorIndex).trim();
    }

    /**
     * Representation of the form data within each boundary of a multipart/form-data body as per
     * https://tools.ietf.org/html/rfc7578#section-4.1.
     */
    public static class FormData {

        private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
        private static final String CONTENT_TYPE_HEADER = "Content-Type";
        private static final String CONTENT_DISPOSITION_FORM_DATA = "form-data";
        private static final String CONTENT_DISPOSITION_PARAMETER_NAME = "name";
        private static final String CONTENT_DISPOSITION_PARAMETER_FILENAME = "filename";

        /**
         * The Content-Disposition header. This is required, and must always be "form-data".
         */
        String contentDisposition;

        /**
         * The name header. This is required, and denotes the expected section name.
         */
        String name;

        /**
         * The filename header. This is optionally added if a file is produced, though may still
         * not be present. Recommended to not be used locally.
         */
        String filename;

        /**
         * The content-type header.
         */
        String contentType;

        /**
         * The actual content of the form-data body.
         */
        String content;

        public String getName() {
            return this.name;
        }

        public String getContentDisposition() {
            return this.contentDisposition;
        }

        public String getFilename() {
            return this.filename;
        }

        public String getContentType() {
            return this.contentType;
        }

        public String getContent() {
            return this.content;
        }

        @Override
        public int hashCode() {
            int result = 17;
            if (this.contentDisposition != null) {
                result = 31 * result + this.contentDisposition.hashCode();
            }

            if (this.name != null) {
                result = 31 * result + this.name.hashCode();
            }

            if (this.filename != null) {
                result = 31 * result + this.filename.hashCode();
            }

            if (this.contentType != null) {
                result = 31 * result + this.contentType.hashCode();
            }

            if (this.content != null) {
                result = 31 * result + this.content.hashCode();
            }

            return result;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof FormData)) {
                return false;
            }

            FormData otherFormData = (FormData) other;

            return (this.contentDisposition != null || otherFormData.contentDisposition == null) &&
                    (this.contentDisposition == null || otherFormData.contentDisposition != null) &&
                    (this.contentType != null || otherFormData.contentType == null) &&
                    (this.contentType == null || otherFormData.contentType != null) &&
                    (this.name != null || otherFormData.name == null) &&
                    (this.name == null || otherFormData.name != null) &&
                    (this.filename != null || otherFormData.filename == null) &&
                    (this.filename == null || otherFormData.filename != null) &&
                    (this.content != null || otherFormData.content == null) &&
                    (this.content == null || otherFormData.content != null) &&
                    (this.contentDisposition == null ||
                            this.contentDisposition.equals(otherFormData.contentDisposition)) &&
                    (this.name == null || this.name.equals(otherFormData.name)) &&
                    (this.filename == null || this.filename.equals(otherFormData.filename)) &&
                    (this.contentType == null || this.contentType.equals(otherFormData.contentType)) &&
                    (this.content == null || this.content.equals(otherFormData.content));

        }

        @Override
        public String toString() {
            String formData = String.format("%s: %s; %s=\"%s\"",
                    CONTENT_DISPOSITION_HEADER, this.contentDisposition,
                    CONTENT_DISPOSITION_PARAMETER_NAME, this.name);
            if (this.filename != null) {
                formData += String.format("; %s=\"%s\"", CONTENT_DISPOSITION_PARAMETER_FILENAME,
                        this.filename);
            }
            formData += CR_LF;

            if (this.contentType != null) {
                formData += String.format("%s: %s%s", CONTENT_TYPE_HEADER, this.contentType, CR_LF);
            }

            formData += String.format("%s%s", CR_LF, this.content);
            return formData;
        }

        /**
         * Empty constructor.
         */
        private FormData() {}

        /**
         * Creates a new {@link FormData} object.
         * @param row An already-delimited row to be parsed into a {@link FormData} object.
         * @throws IllegalStateException Thrown if any parsing errors or if there are missing
         * headers.
         */
        FormData(String row) throws IllegalStateException {
            this.contentDisposition = getContentDispositionField(row);
            this.name = getFormDataName(row);
            this.filename = getFormDataFilename(row);
            this.contentType = getFormDataContentType(row);
            this.content = getFormDataContent(row);
        }

        /**
         * Checks for the "Content-Disposition" header. This is a required header, and must equate
         * to "form-data". If not, an exception is thrown.
         *
         * @param row The row to parse for the header.
         * @return The Content-Disposition value.
         * @throws IllegalStateException Thrown if the value is not equal to "form-data", or if not
         * present.
         */
        private String getContentDispositionField(String row) throws IllegalStateException {
            String contentDisposition = getFormDataInfo(row, CONTENT_DISPOSITION_HEADER,
                    String.format("%s:", CONTENT_DISPOSITION_HEADER.toLowerCase()), SEMICOLON, true);
            if (contentDisposition == null ||
                    !contentDisposition.equalsIgnoreCase(CONTENT_DISPOSITION_FORM_DATA)) {
                throw new IllegalStateException(String.format("%s header does not equal '%s'.",
                        CONTENT_DISPOSITION_HEADER, CONTENT_DISPOSITION_FORM_DATA));
            }

            return contentDisposition;
        }

        /**
         * Searches for the "name" field. This is a required parameter (after the
         * Content-Disposition header).
         *
         * @param row The row to parse for the header.
         * @return The data sections name.
         * @throws IllegalStateException Thrown if the value is not present.
         */
        private String getFormDataName(String row) throws IllegalStateException {
            return getFormDataInfo(row, CONTENT_DISPOSITION_PARAMETER_NAME,
                    String.format("%s=%s", CONTENT_DISPOSITION_PARAMETER_NAME, QUOTE), QUOTE, true);
        }

        /**
         * Searches for the "filename" parameter. This is an optional parameter.
         * @param row The row to parse for the header.
         * @return The filename (if present), or null.
         */
        private String getFormDataFilename(String row) {
            return getFormDataInfo(row, CONTENT_DISPOSITION_PARAMETER_FILENAME,
                    String.format("%s=%s", CONTENT_DISPOSITION_PARAMETER_FILENAME, QUOTE), QUOTE, false);
        }

        /**
         * Searches for the "Content-Type" header. This is an optional parameter. If not present,
         * it is defaulted to {@link Operation#MEDIA_TYPE_TEXT_PLAIN}.
         * @param row The row to parse for the header.
         * @return The content-type of the data block.
         */
        private String getFormDataContentType(String row) {
            String contentType = getFormDataInfo(row, Operation.CONTENT_TYPE_HEADER,
                    String.format("%s:", Operation.CONTENT_TYPE_HEADER), new String[]{SEMICOLON, CR_LF}, false);
            return contentType != null ? contentType : Operation.MEDIA_TYPE_TEXT_PLAIN;
        }

        /**
         * Retrieves the actual content of the data form block.
         * @param row The row to parse for the header.
         * @return The content of the form row.
         */
        private String getFormDataContent(String row) {
            String twoLineDelimiter = String.format("%s%s", CR_LF, CR_LF);
            if (!row.contains(twoLineDelimiter)) {
                throw new IllegalStateException("Missing delimiters for content.");
            }

            int startIndex = row.indexOf(twoLineDelimiter) + twoLineDelimiter.length();
            int lastIndex = row.lastIndexOf(CR_LF);
            if (lastIndex < startIndex) {
                throw new IllegalStateException("Missing EOL delimiter.");
            }

            // The headers cannot include double lines per specification, so the first notion of
            // them (remaining until the very last one) are considered the content block.
            return row.substring(row.indexOf(twoLineDelimiter) + twoLineDelimiter.length(),
                    row.lastIndexOf(CR_LF));
        }

        /**
         * Builder class to help construct a {@link FormData} object without parsing a row.
         */
        public static class FormDataBuilder {

            private String name;
            private String filename;
            private String contentType;
            private String content;

            public FormDataBuilder withName(String name) {
                this.name = name;
                return this;
            }

            public FormDataBuilder withContentType(String contentType) {
                this.contentType = contentType;
                return this;
            }

            public FormDataBuilder withFilename(String filename) {
                this.filename = filename;
                return this;
            }

            public FormDataBuilder withContent(String content) {
                this.content = content;
                return this;
            }

            public FormData build() {

                if (this.name == null || this.name.isEmpty()) {
                    throw new IllegalArgumentException("'name' must be set.");
                }

                if (this.content == null) {
                    throw new IllegalArgumentException("'content' must be set.");
                }

                FormData formData = new FormData();
                formData.contentDisposition = CONTENT_DISPOSITION_FORM_DATA;
                formData.name = this.name;
                formData.filename = this.filename;
                formData.contentType = this.contentType;
                formData.content = this.content;
                return formData;
            }
        }
    }
}
