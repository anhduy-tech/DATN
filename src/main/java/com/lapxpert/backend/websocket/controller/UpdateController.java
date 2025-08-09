package com.lapxpert.backend.websocket.controller;

import com.lapxpert.backend.websocket.service.MessageRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class UpdateController {

    private final MessageRoutingService messageRoutingService;

    @PostMapping("/pos-app/send")
    public ResponseEntity<?> sendToPosApp(
            @RequestParam int roomId,
            @RequestBody Map<String, Object> data
    ) {
        messageRoutingService.sendPosAppUpdate(roomId, data);
        return ResponseEntity.ok("Sent POS update to room " + roomId);
    }
}
