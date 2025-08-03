package com.lapxpert.backend.giaoca.controller;

import com.lapxpert.backend.giaoca.entity.LichLamViec;
import com.lapxpert.backend.giaoca.service.LichLamViecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llv")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class LichLamViecController {
    @Autowired
    private LichLamViecService lichLamViecService;
    @GetMapping
    public List<LichLamViec> getLichLamViec() {
        return lichLamViecService.getLLV();
    }
}
