package com.cloud_technological.aura_pos.utils;

import org.springframework.http.HttpStatus;

public class GlobalException extends RuntimeException {
    private HttpStatus status;

    public GlobalException(HttpStatus status, String s) {
        super(s);
        this.status = status;
    }

    public GlobalException(HttpStatus status) {
        super();
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }
}