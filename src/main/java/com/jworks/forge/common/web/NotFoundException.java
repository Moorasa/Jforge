package com.jworks.forge.common.web;

/** 리소스 미존재. 핸들러가 404로 변환. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
