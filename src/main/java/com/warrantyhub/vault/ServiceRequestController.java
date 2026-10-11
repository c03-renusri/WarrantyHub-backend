package com.warrantyhub.vault;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products/{id}/service-requests")
public class ServiceRequestController {
  final ProductController products;
  final ServiceRequestRepository requests;
  final Clock clock;

  public ServiceRequestController(ProductController p, ServiceRequestRepository r, Clock c) {
    products = p;
    requests = r;
    clock = c;
  }

  public record Input(
      @NotBlank @Size(max = 2000) String issueDescription,
      @NotBlank @Pattern(regexp = "Pending|In Progress|Completed") String status) {}

  @GetMapping
  public List<ServiceRequest> list(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    products.owned(id, jwt);
    return requests.findByProductIdOrderByIdDesc(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ServiceRequest create(
      @PathVariable Long id, @Valid @RequestBody Input i, @AuthenticationPrincipal Jwt jwt) {
    products.owned(id, jwt);
    var r = new ServiceRequest();
    r.productId = id;
    r.issueDescription = i.issueDescription();
    r.status = i.status();
    r.requestDate = LocalDate.now(clock);
    return requests.save(r);
  }

  @PutMapping("/{requestId}")
  public ServiceRequest update(
      @PathVariable Long id,
      @PathVariable Long requestId,
      @Valid @RequestBody Input i,
      @AuthenticationPrincipal Jwt jwt) {
    products.owned(id, jwt);
    var r =
        requests
            .findById(requestId)
            .filter(x -> x.productId.equals(id))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found"));
    r.issueDescription = i.issueDescription();
    r.status = i.status();
    return requests.save(r);
  }

  @DeleteMapping("/{requestId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable Long id, @PathVariable Long requestId, @AuthenticationPrincipal Jwt jwt) {
    products.owned(id, jwt);
    var r =
        requests
            .findById(requestId)
            .filter(x -> x.productId.equals(id))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found"));
    requests.delete(r);
  }
}
