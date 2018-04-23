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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_MULTIPART_FORM_DATA;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_TEXT_CSV;
import static com.vmware.xenon.common.Operation.CR_LF;
import static com.vmware.xenon.common.Operation.MEDIA_TYPE_TEXT_PLAIN;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.photon.controller.discovery.common.utils.MultipartFormDataParser.FormData;
import com.vmware.photon.controller.discovery.common.utils.MultipartFormDataParser.FormData.FormDataBuilder;
import com.vmware.xenon.common.Operation;

/**
 * Test class to test the {@link MultipartFormDataParser}. Validates *invalid* headers throw proper
 * exceptions, as well as proper parsing of valid forms.
 */
public class TestMultipartFormDataParser {

    @Test
    public void testMissingMultipartFormDataHeader() {
        Operation op = new Operation()
                .setContentType("invalid");
        try {
            new MultipartFormDataParser(op);
        } catch (IllegalStateException e) {
            assertEquals(String.format("Request does not contain '%s' content-type header.",
                    CONTENT_TYPE_MULTIPART_FORM_DATA), e.getMessage());
        }
    }

    @Test
    public void testInvalidMultipartFormDataHeader() {
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary-invalid=---test", CONTENT_TYPE_MULTIPART_FORM_DATA));
        try {
            new MultipartFormDataParser(op);
        } catch (IllegalStateException e) {
            assertEquals("Illegal content-type header. Missing valid 'boundary' parameter.",
                    e.getMessage());
        }
    }

    @Test
    public void testNoBody() {
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=----test", CONTENT_TYPE_MULTIPART_FORM_DATA));
        try {
            new MultipartFormDataParser(op);
        } catch (IllegalStateException e) {
            assertEquals(null, e.getMessage());
        }
    }

    @Test
    public void testEmptyBody() {
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=----test", CONTENT_TYPE_MULTIPART_FORM_DATA))
                .setBody("".getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (IllegalStateException e) {
            assertEquals("Malformed form content.", e.getMessage());
        }
    }

    @Test
    public void testNoEndBoundary() {
        String boundary = "----test";
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=%s", CONTENT_TYPE_MULTIPART_FORM_DATA, boundary))
                .setBody(String.format("--%s%ssome data", boundary, CR_LF).getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (IllegalStateException e) {
            assertEquals("No end-boundary for this form data.", e.getMessage());
        }
    }

    @Test
    public void testNoContentDispositionHeader() {
        String boundary = "----test";
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=%s", CONTENT_TYPE_MULTIPART_FORM_DATA, boundary))
                .setBody(String.format("--%s%some data%s--%s--%s", boundary, CR_LF, CR_LF, boundary, CR_LF).getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (RuntimeException e) {
            assertEquals("Form header is missing 'Content-Disposition'.",
                    e.getCause().getCause().getMessage());
        }
    }

    @Test
    public void testContentDispositionHeaderNotFormData() {
        String boundary = "----test";
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=%s", CONTENT_TYPE_MULTIPART_FORM_DATA, boundary))
                .setBody(String.format("--%s%sContent-Disposition: not-form-data%s%ssome data%s--%s--%s",
                        boundary, CR_LF, CR_LF, CR_LF, CR_LF, boundary, CR_LF).getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (RuntimeException e) {
            assertEquals("Content-Disposition header does not equal 'form-data'.",
                    e.getCause().getCause().getMessage());
        }
    }

    @Test
    public void testContentDispositionHeaderNoNameParameter() {
        String boundary = "----test";
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=%s", CONTENT_TYPE_MULTIPART_FORM_DATA, boundary))
                .setBody(String.format("--%s%sContent-Disposition: form-data;%s%ssome data%s--%s--%s",
                        boundary, CR_LF, CR_LF, CR_LF, CR_LF, boundary, CR_LF).getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (RuntimeException e) {
            assertEquals("Form header is missing 'name'.",
                    e.getCause().getCause().getMessage());
        }
    }

    @Test
    public void testContentDispositionHeaderEmptyNameParameter() {
        String boundary = "----test";
        Operation op = new Operation()
                .setContentType(String.format("%s; boundary=%s", CONTENT_TYPE_MULTIPART_FORM_DATA, boundary))
                .setBody(String.format("--%s%sContent-Disposition: form-data; name=\"\"%s%ssome data%s--%s--%s",
                        boundary, CR_LF, CR_LF, CR_LF, CR_LF, boundary, CR_LF).getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (RuntimeException e) {
            assertEquals("Form header is missing 'name'.",
                    e.getCause().getCause().getMessage());
        }
    }

    @Test
    public void testBarebones() {
        Operation op = constructMultipartFormDataOperation(
                new FormDataBuilder()
                        .withName("something")
                        .withContent("some data")
                        .build());
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(op);

        FormData formData = multipartFormDataParser.getParameter("something");
        assertEquals("something", formData.name);
        assertEquals(MEDIA_TYPE_TEXT_PLAIN, formData.getContentType());
        assertEquals("form-data", formData.getContentDisposition());
        assertEquals(null, formData.getFilename());
        assertEquals("some data", formData.getContent());

        List<FormData> formDataAll = multipartFormDataParser.getParameterValues("something");
        assertEquals(1, formDataAll.size());
        assertEquals(formDataAll.get(0), formData);
    }

    @Test
    public void testNoContent() {
        Operation op = constructMultipartFormDataOperation(
                new FormDataBuilder()
                        .withName("something")
                        .withContent("")
                        .build());
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(op);

        FormData formData = multipartFormDataParser.getParameter("something");
        assertEquals("something", formData.name);
        assertEquals(MEDIA_TYPE_TEXT_PLAIN, formData.getContentType());
        assertEquals("form-data", formData.getContentDisposition());
        assertEquals(null, formData.getFilename());
        assertEquals("", formData.getContent());
    }

    @Test
    public void testMissingContentDelimiters() {
        Operation op = new Operation();
        String boundary = "----test";
        op.setContentType(String.format("%s; boundary=%s", CONTENT_TYPE_MULTIPART_FORM_DATA, boundary));
        op.setBody(String.format("--%s%sContent-Disposition: form-data; name=\"something\";%s--%s--%s",
                boundary, CR_LF, CR_LF, boundary, CR_LF).getBytes());
        try {
            new MultipartFormDataParser(op);
        } catch (RuntimeException e) {
            assertEquals("Missing delimiters for content.", e.getCause().getCause().getMessage());
        }
    }

    @Test
    public void testContentDispositionMissingContentEOL() {
        Operation op = constructMultipartFormDataOperation(
                new FormDataBuilder()
                        .withName("something")
                        .withContent("")
                        .build());
        try {
            new MultipartFormDataParser(op);
        } catch (RuntimeException e) {
            assertEquals("Missing EOL delimiter.", e.getCause().getCause().getMessage());
        }
    }

    @Test
    public void testGetParametersAndGetParameterValues() {
        Operation op = constructMultipartFormDataOperation(
                new FormDataBuilder()
                        .withName("something")
                        .withContent("some data")
                        .build());
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(op);
        assertNull(multipartFormDataParser.getParameter("invalid"));
        assertNotNull(multipartFormDataParser.getParameter("something"));
        assertNotNull(multipartFormDataParser.getParameterValues("something"));
    }

    @Test
    public void testDifferentContentType() {
        Operation op = constructMultipartFormDataOperation(new FormDataBuilder()
                        .withName("something")
                        .withContentType("text/csv")
                        .withContent("some data")
                        .build());
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(op);

        FormData formData = multipartFormDataParser.getParameter("something");
        assertEquals("form-data", formData.getContentDisposition());
        assertEquals(CONTENT_TYPE_TEXT_CSV, formData.getContentType());
        assertEquals("something", formData.getName());
        assertEquals("some data", formData.getContent());
        assertNull(formData.getFilename());
    }

    @Test
    public void testMultipleSections() {
        FormData firstSection = new FormDataBuilder()
                .withName("section-1")
                .withContent(String.format("Test string!%sNew line!", CR_LF))
                .build();
        FormData secondSection = new FormDataBuilder()
                .withName("section-2")
                .withContentType("some-other-type")
                .withFilename("fake.file")
                .withContent("Test")
                .build();

        Operation op = constructMultipartFormDataOperation(firstSection, secondSection);
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(op);

        // Since the first section didn't specify a content-type, these objects are technically not
        // completely equivalent (as it is auto-set to `text/plain`).
        FormData firstSectionReceived = multipartFormDataParser.getParameter("section-1");
        assertEquals(firstSection.getName(), firstSectionReceived.getName());
        assertEquals(firstSection.getContentDisposition(), firstSectionReceived.getContentDisposition());
        assertEquals(firstSection.getContent(), firstSectionReceived.getContent());
        assertEquals(Operation.MEDIA_TYPE_TEXT_PLAIN, firstSectionReceived.getContentType());
        assertNull(firstSectionReceived.getFilename());

        assertEquals(secondSection, multipartFormDataParser.getParameter("section-2"));
    }

    @Test
    public void testBoundaries() {
        FormData section = new FormDataBuilder()
                .withName("section")
                .withContentType(MEDIA_TYPE_TEXT_PLAIN)
                .withContent(String.format("Test string!%sNew line!", CR_LF))
                .build();

        // Test basic quote
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(
                constructMultipartFormDataOperation("\"quoted-boundary\"", section));
        assertEquals(section, multipartFormDataParser.getParameter("section"));

        // Test more complex boundary
        multipartFormDataParser = new MultipartFormDataParser(
                constructMultipartFormDataOperation("\"gc0pJq0M:08jU534c0p\"", section));
        assertEquals(section, multipartFormDataParser.getParameter("section"));

        // Validate that without quotes the above boundary would fail
        try {
            new MultipartFormDataParser(
                    constructMultipartFormDataOperation("gc0pJq0M:08jU534c0p", section));
        } catch (IllegalStateException e) {
            assertEquals("Illegal content-type header. Missing valid 'boundary' parameter.", e.getMessage());
        }

        // Validate that boundaries cannot be longer than 70 characters
        try {
            new MultipartFormDataParser(
                    constructMultipartFormDataOperation("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz", section));
        } catch (IllegalStateException e) {
            assertEquals("'boundary' must not be greater than 70 characters.", e.getMessage());
        }
    }

    @Test
    public void testSameNameSections() {
        FormData firstSection = new FormDataBuilder()
                .withName("section")
                .withContentType(MEDIA_TYPE_TEXT_PLAIN)
                .withContent(String.format("Test string!%sNew line!", CR_LF))
                .build();
        FormData secondSection = new FormDataBuilder()
                .withName("section")
                .withContentType("some-other-type")
                .withFilename("fake.file")
                .withContent("Test")
                .build();

        Operation op = constructMultipartFormDataOperation(firstSection, secondSection);
        MultipartFormDataParser multipartFormDataParser = new MultipartFormDataParser(op);

        try {
            multipartFormDataParser.getParameter("section");
        } catch (IllegalStateException e) {
            assertEquals("Too many values set for key 'section'.", e.getMessage());
        }

        List<FormData> requests = Arrays.asList(firstSection, secondSection);
        List<FormData> resp = multipartFormDataParser.getParameterValues("section");
        assertEquals(2, resp.size());

        // Validate that the requests were exactly the same as the responses
        resp.removeAll(requests);
        assertEquals(0, resp.size());
    }

    /**
     * Helper method to construct an operation ready for parsing for the
     * {@link MultipartFormDataParser}. Defaults the boundary to a random UUID.
     *
     * @param formObjects The form objects to construct.
     * @return An operation ready to be used in the {@link MultipartFormDataParser}.
     */
    public static Operation constructMultipartFormDataOperation(FormData... formObjects) {
        return constructMultipartFormDataOperation(UUID.randomUUID().toString(), formObjects);
    }

    /**
     * Helper method to construct an operation ready for parsing for the
     * {@link MultipartFormDataParser}.
     *
     * @param boundary The expected boundary to set.
     * @param formObjects The form objects to construct.
     * @return An operation ready to be used in the {@link MultipartFormDataParser}.
     */
    public static Operation constructMultipartFormDataOperation(String boundary, FormData... formObjects) {
        final String bodyBoundary = boundary.replaceAll("^\"|\"$", "");
        return new Operation()
                .setContentType(String.format("%s; boundary=%s",
                        CONTENT_TYPE_MULTIPART_FORM_DATA, boundary))
                .setBody(String.format("%s%s",
                        Arrays.stream(formObjects)
                                .map(formData -> String.format("--%s%s%s%s", bodyBoundary, CR_LF,
                                        formData, CR_LF))
                                .reduce("", String::concat),
                        String.format("--%s--%s", bodyBoundary, CR_LF)).getBytes());
    }
}