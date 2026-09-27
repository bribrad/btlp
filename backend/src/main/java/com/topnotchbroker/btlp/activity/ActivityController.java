package com.topnotchbroker.btlp.activity;

import com.topnotchbroker.btlp.web.PagedResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only operations timeline for the dispatcher portal. Secured to {@code DISPATCHER} and {@code
 * ADMIN} via the {@code /api/v1/activity/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ActivityService activityService;

  public ActivityController(ActivityService activityService) {
    this.activityService = activityService;
  }

  /**
   * Dispatch and status events, newest first. {@code loadId} widens to every job and assignment on
   * that load; {@code jobId} narrows to a single leg. Supplying both intersects them.
   */
  @GetMapping
  public PagedResponse<ActivityEventResponse> list(
      @RequestParam(required = false) UUID loadId,
      @RequestParam(required = false) UUID jobId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return activityService.list(
        loadId, jobId, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
  }
}
