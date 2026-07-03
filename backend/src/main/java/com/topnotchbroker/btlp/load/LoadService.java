package com.topnotchbroker.btlp.load;

import com.topnotchbroker.btlp.web.PagedResponse;
import com.topnotchbroker.btlp.web.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application logic for load create/read/update, including server-side audit stamping. */
@Service
public class LoadService {

  private static final String DEFAULT_CURRENCY = "USD";

  private final LoadRepository repository;

  public LoadService(LoadRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public LoadResponse create(LoadCreateRequest request, String actor) {
    Load toInsert =
        new Load(
            null,
            request.customerId(),
            request.origin(),
            request.destination(),
            request.pickupWindowStart(),
            request.pickupWindowEnd(),
            request.dropoffWindowStart(),
            request.dropoffWindowEnd(),
            request.rateAmount(),
            currencyOrDefault(request.rateCurrency()),
            request.notes(),
            LoadStatus.PLANNED,
            actor,
            actor,
            null,
            null);
    return LoadResponse.from(repository.insert(toInsert));
  }

  @Transactional(readOnly = true)
  public LoadResponse getById(UUID id) {
    return LoadResponse.from(repository.findById(id).orElseThrow(() -> notFound(id)));
  }

  @Transactional(readOnly = true)
  public PagedResponse<LoadResponse> list(int page, int size) {
    int offset = page * size;
    List<LoadResponse> content =
        repository.findPage(size, offset).stream().map(LoadResponse::from).toList();
    long total = repository.count();
    return PagedResponse.of(content, page, size, total);
  }

  @Transactional
  public LoadResponse update(UUID id, LoadUpdateRequest request, String actor) {
    Load values =
        new Load(
            id,
            null,
            request.origin(),
            request.destination(),
            request.pickupWindowStart(),
            request.pickupWindowEnd(),
            request.dropoffWindowStart(),
            request.dropoffWindowEnd(),
            request.rateAmount(),
            currencyOrDefault(request.rateCurrency()),
            request.notes(),
            null,
            null,
            actor,
            null,
            null);
    return LoadResponse.from(repository.update(id, values).orElseThrow(() -> notFound(id)));
  }

  private static String currencyOrDefault(String currency) {
    return currency != null ? currency : DEFAULT_CURRENCY;
  }

  private static ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Load not found: " + id);
  }
}
