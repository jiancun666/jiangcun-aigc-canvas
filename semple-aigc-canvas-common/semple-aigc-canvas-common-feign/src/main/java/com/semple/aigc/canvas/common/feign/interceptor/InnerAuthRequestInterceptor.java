package com.semple.aigc.canvas.common.feign.interceptor;

import com.semple.aigc.canvas.common.core.constant.SecurityConstants;
import com.semple.aigc.canvas.common.core.constant.TraceConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Adds inner-call marker for Feign requests.
 *
 * @author aofaming
 */
public class InnerAuthRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!template.headers().containsKey(SecurityConstants.FROM_SOURCE)) {
            template.header(SecurityConstants.FROM_SOURCE, SecurityConstants.INNER);
        }
        String traceId = MDC.get(TraceConstants.TRACE_ID_MDC_KEY);
        if (StringUtils.hasText(traceId) && !template.headers().containsKey(TraceConstants.TRACE_ID_HEADER)) {
            template.header(TraceConstants.TRACE_ID_HEADER, traceId);
        }
    }
}
