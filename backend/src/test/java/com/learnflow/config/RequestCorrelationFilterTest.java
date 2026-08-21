package com.learnflow.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesBoundedSafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "edge-request_1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertEquals(
                "edge-request_1234",
                org.slf4j.MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)
        );

        filter.doFilter(request, response, chain);

        assertEquals("edge-request_1234", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }

    @Test
    void replacesUnsafeClientControlledValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "bad\nvalue with secrets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotEquals("bad\nvalue with secrets", generated);
        assertTrue(generated != null && generated.matches("[0-9a-f-]{36}"));
    }
}
