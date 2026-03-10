package com.cloud_technological.aura_pos.utils;


import io.micrometer.common.lang.Nullable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;
    private boolean error;

    public ApiResponse(int status, String message, boolean error, @Nullable T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.error = error;
    }
}
