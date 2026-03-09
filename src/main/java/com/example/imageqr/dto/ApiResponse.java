package com.example.imageqr.dto;

import java.util.List;

public class ApiResponse<T> {

    private String code;
    private String message;
    private List<T> data;

    public ApiResponse(String code, String message, List<T> data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public List<T> getData() { return data; }

}
