package com.gamehub.audit.api;

import com.gamehub.audit.application.AuditService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/rooms/{roomId}")
    public List<AuditDtos.AuditEntryResponse> roomAudit(@PathVariable UUID roomId) {
        return auditService.roomAudit(roomId);
    }
}
