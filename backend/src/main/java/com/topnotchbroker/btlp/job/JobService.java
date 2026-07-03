package com.topnotchbroker.btlp.job;

import com.topnotchbroker.btlp.load.LoadRepository;
import com.topnotchbroker.btlp.web.PagedResponse;
import com.topnotchbroker.btlp.web.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application logic for job create/read/update, linked to a parent load. */
@Service
public class JobService {

  private final JobRepository repository;
  private final LoadRepository loadRepository;

  public JobService(JobRepository repository, LoadRepository loadRepository) {
    this.repository = repository;
    this.loadRepository = loadRepository;
  }

  @Transactional
  public JobResponse create(JobCreateRequest request) {
    if (!loadRepository.existsById(request.loadId())) {
      throw new ResourceNotFoundException("Load not found: " + request.loadId());
    }
    int sequence =
        request.sequence() != null ? request.sequence() : repository.nextSequence(request.loadId());
    Job toInsert =
        new Job(
            null,
            request.loadId(),
            request.jobType(),
            sequence,
            JobStatus.UNASSIGNED,
            request.scheduledAt(),
            null,
            null);
    return JobResponse.from(repository.insert(toInsert));
  }

  @Transactional(readOnly = true)
  public JobResponse getById(UUID id) {
    return JobResponse.from(repository.findById(id).orElseThrow(() -> notFound(id)));
  }

  @Transactional(readOnly = true)
  public PagedResponse<JobResponse> list(UUID loadId, int page, int size) {
    int offset = page * size;
    List<JobResponse> content;
    long total;
    if (loadId != null) {
      content =
          repository.findByLoad(loadId, size, offset).stream().map(JobResponse::from).toList();
      total = repository.countByLoad(loadId);
    } else {
      content = repository.findPage(size, offset).stream().map(JobResponse::from).toList();
      total = repository.count();
    }
    return PagedResponse.of(content, page, size, total);
  }

  @Transactional
  public JobResponse update(UUID id, JobUpdateRequest request) {
    Job values =
        new Job(
            id,
            null,
            request.jobType(),
            request.sequence(),
            null,
            request.scheduledAt(),
            null,
            null);
    return JobResponse.from(repository.update(id, values).orElseThrow(() -> notFound(id)));
  }

  private static ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Job not found: " + id);
  }
}
