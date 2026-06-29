package com.comfortableledger.ledger.dto;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }

    public record ApiError(String code, String message) {
    }
}
