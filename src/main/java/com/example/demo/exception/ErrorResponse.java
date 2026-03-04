package com.example.demo.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    public ErrorResponse(int value, String string, long timeMillis) {
		// TODO Auto-generated constructor stub
	}
	private int status;      // error code  (ex: 400, 404, 500)
    private String message;   // error messgae
    private long timestamp;   // error time
}