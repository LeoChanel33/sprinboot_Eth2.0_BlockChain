package com.example.demo.eth20.controller; // 注意：點代表進入下一層資料夾

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @GetMapping("/eth/hello")
    public String hello() {
        return "ETH 2.0 Module is Running!";
    }
}