package com.truckconnect.loadpost;

import com.truckconnect.loadpost.dto.LoadPostResponse;
import com.truckconnect.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/load-posts")
@PreAuthorize("hasRole('OWNER')")
public class OwnerLoadPostController {

    private final LoadPostService loadPostService;

    public OwnerLoadPostController(LoadPostService loadPostService) {
        this.loadPostService = loadPostService;
    }

    /** Open posts at least one of my AVAILABLE trucks can carry. */
    @GetMapping("/matching")
    public ResponseEntity<List<LoadPostResponse>> matching(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(loadPostService.matchingForOwner(owner));
    }
}
