package com.gamehub.common.api;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice
public class ResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger logger = LoggerFactory.getLogger(ResponseWrapperAdvice.class);

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> paramType = returnType.getParameterType();
        // Only wrap responses from our application's controllers to avoid
        // interfering with framework endpoints (e.g. springdoc OpenAPI JSON,
        // static resources, byte[] payloads, etc.).
        Package pkg = returnType.getDeclaringClass().getPackage();
        if (pkg == null || !pkg.getName().startsWith("com.gamehub")) return false;

        // Exclude already-wrapped types and raw payloads that must remain unchanged.
        if (ApiResponse.class.isAssignableFrom(paramType)) return false;
        if (ErrorResponse.class.isAssignableFrom(paramType)) return false;
        if (String.class.isAssignableFrom(paramType)) return false;
        if (byte[].class.isAssignableFrom(paramType)) return false;
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (body == null) {
            return new ApiResponse<>(
                    Instant.now(),
                    HttpStatus.OK.value(),
                    "OK",
                    null,
                    List.of(),
                    getRequestPath(request)
            );
        }

        if (body instanceof ApiResponse<?> || body instanceof ErrorResponse) {
            return body;
        }

        int status = HttpStatus.OK.value();
        String message = "OK";

        return new ApiResponse<>(
                Instant.now(),
                status,
                message,
                body,
                List.of(),
                getRequestPath(request)
        );
    }

    private String getRequestPath(ServerHttpRequest request) {
        return request.getURI().getPath();
    }
}
