package com.main.accord.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T      data,
        String error
) {
    public static <T> ApiResponse<T> ok(T data)       { return new ApiResponse<>(data, null); }
    public static <T> ApiResponse<T> err(String msg)  { return new ApiResponse<>(null, msg); }
}