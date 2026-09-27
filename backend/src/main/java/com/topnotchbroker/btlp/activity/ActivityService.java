package com.topnotchbroker.btlp.activity;

import com.topnotchbroker.btlp.web.PagedResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves the operations timeline: audit events in insertion order, scoped to a load or a job. */
@Service
public class ActivityService {

  private final ActivityRepository repository;

  public ActivityService(ActivityRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PagedResponse<ActivityEventResponse> list(UUID loadId, UUID jobId, int page, int size) {
    int offset = page * size;
    List<ActivityEventResponse> content =
        repository.findPage(loadId, jobId, size, offset).stream()
            .map(ActivityEventResponse::from)
            .toList();
    long total = repository.count(loadId, jobId);
    return PagedResponse.of(content, page, size, total);
  }
}
