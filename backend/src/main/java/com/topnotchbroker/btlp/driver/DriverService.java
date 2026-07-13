package com.topnotchbroker.btlp.driver;

import com.topnotchbroker.btlp.web.PagedResponse;
import com.topnotchbroker.btlp.web.ResourceNotFoundException;
import com.topnotchbroker.btlp.web.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application logic for the driver directory, availability, and assignment eligibility. */
@Service
public class DriverService {

  private final DriverRepository repository;

  public DriverService(DriverRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public DriverResponse create(DriverCreateRequest request) {
    DriverStatus status = request.status() != null ? request.status() : DriverStatus.ACTIVE;
    Driver created =
        repository.insert(request.name(), request.phone(), request.licenseNumber(), status);
    return DriverResponse.from(created);
  }

  @Transactional(readOnly = true)
  public DriverResponse getById(UUID id) {
    return DriverResponse.from(repository.findById(id).orElseThrow(() -> notFound(id)));
  }

  @Transactional(readOnly = true)
  public PagedResponse<DriverResponse> list(
      DriverAvailability availability, DriverStatus status, int page, int size) {
    int offset = page * size;
    List<DriverResponse> content =
        repository.findPage(availability, status, size, offset).stream()
            .map(DriverResponse::from)
            .toList();
    long total = repository.count(availability, status);
    return PagedResponse.of(content, page, size, total);
  }

  @Transactional(readOnly = true)
  public PagedResponse<DriverResponse> listEligible(int page, int size) {
    int offset = page * size;
    List<DriverResponse> content =
        repository.findEligible(size, offset).stream().map(DriverResponse::from).toList();
    long total = repository.countEligible();
    return PagedResponse.of(content, page, size, total);
  }

  @Transactional
  public DriverResponse setAvailability(UUID id, DriverAvailability availability) {
    if (availability == DriverAvailability.ON_TRIP) {
      throw new ValidationException("availability must be AVAILABLE or UNAVAILABLE");
    }
    return DriverResponse.from(
        repository.updateAvailability(id, availability).orElseThrow(() -> notFound(id)));
  }

  @Transactional
  public DriverResponse setStatus(UUID id, DriverStatus status) {
    return DriverResponse.from(repository.updateStatus(id, status).orElseThrow(() -> notFound(id)));
  }

  private static ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Driver not found: " + id);
  }
}
