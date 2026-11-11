package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminUserResponse;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminUserController(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> list() {
        List<AdminUserResponse> out = userRepository.findAll().stream()
                .map(u -> new AdminUserResponse(u.getId(), u.getName(),
                        u.getEmail(), u.getRole().name(), u.isActive()))
                .toList();
        return ResponseEntity.ok(out);
    }

    // Deactivate a user. Never delete. Never deactivate yourself.
    // Never deactivate another ADMIN — that is the rule in Part 5 above.
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<AdminUserResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (user.getId().equals(admin.getId())) {
            throw new IllegalStateException("You cannot deactivate your own admin account.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalStateException("You cannot deactivate another admin.");
        }
        user.setActive(false);
        userRepository.save(user);
        auditService.log(admin, "USER_DEACTIVATED", "USER", user.getId());
        return ResponseEntity.ok(new AdminUserResponse(user.getId(), user.getName(),
                user.getEmail(), user.getRole().name(), user.isActive()));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<AdminUserResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(true);
        userRepository.save(user);
        auditService.log(admin, "USER_ACTIVATED", "USER", user.getId());
        return ResponseEntity.ok(new AdminUserResponse(user.getId(), user.getName(),
                user.getEmail(), user.getRole().name(), user.isActive()));
    }
}
