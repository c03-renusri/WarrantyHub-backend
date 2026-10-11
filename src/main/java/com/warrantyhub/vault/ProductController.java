package com.warrantyhub.vault;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ProductController {
  final ProductRepository products;
  final ReceiptRepository receipts;
  final ServiceRequestRepository requests;
  final Clock clock;

  public ProductController(
      ProductRepository p, ReceiptRepository r, ServiceRequestRepository s, Clock c) {
    products = p;
    receipts = r;
    requests = s;
    clock = c;
  }

  public record Input(
      @NotBlank @Size(max = 120) String productName,
      @NotBlank @Size(max = 100) String brand,
      @NotBlank @Size(max = 80) String category,
      @NotBlank @Size(max = 120) String store,
      @NotNull
          @DecimalMin("0.00")
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal price,
      @NotNull @PastOrPresent LocalDate purchaseDate,
      @NotNull @Min(0) @Max(600) Integer warrantyMonths) {}

  Product owned(Long id, Jwt jwt) {
    return products
        .findByIdAndOwnerId(id, Long.valueOf(jwt.getSubject()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
  }

  Map<String, Object> view(Product p) {
    return view(p, receipts.existsById(p.id));
  }

  Map<String, Object> view(Product p, boolean hasReceipt) {
    var expiry = WarrantyDates.expiry(p.purchaseDate, p.warrantyMonths);
    var today = LocalDate.now(clock);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.id);
    m.put("productName", p.productName);
    m.put("brand", p.brand);
    m.put("category", p.category);
    m.put("store", p.store);
    m.put("price", p.price);
    m.put("purchaseDate", p.purchaseDate);
    m.put("warrantyMonths", p.warrantyMonths);
    m.put("expiryDate", expiry);
    m.put("daysLeft", WarrantyDates.days(expiry, today));
    m.put("status", WarrantyDates.status(expiry, today));
    m.put("hasReceipt", hasReceipt);
    return m;
  }

  @GetMapping("/products")
  public List<Map<String, Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String status) {
    if (!status.isEmpty() && !Set.of("ACTIVE", "EXPIRING", "EXPIRED").contains(status))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid warranty status");
    String query = q.trim().toLowerCase(Locale.ROOT);
    var owned = products.findByOwnerIdOrderByPurchaseDateDesc(Long.valueOf(jwt.getSubject()));
    var receiptIds =
        owned.isEmpty()
            ? Set.<Long>of()
            : receipts.receiptIds(owned.stream().map(p -> p.id).toList());
    return owned.stream()
        .filter(
            p ->
                (p.productName + " " + p.brand + " " + p.category + " " + p.store)
                    .toLowerCase(Locale.ROOT)
                    .contains(query))
        .filter(p -> category.isEmpty() || p.category.equalsIgnoreCase(category))
        .map(p -> view(p, receiptIds.contains(p.id)))
        .filter(p -> status.isEmpty() || p.get("status").equals(status))
        .toList();
  }

  @GetMapping("/products/{id}")
  public Map<String, Object> get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    return view(owned(id, jwt));
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody Input i, @AuthenticationPrincipal Jwt jwt) {
    Product p = new Product();
    p.ownerId = Long.valueOf(jwt.getSubject());
    return save(p, i);
  }

  @PutMapping("/products/{id}")
  public Map<String, Object> update(
      @PathVariable Long id, @Valid @RequestBody Input i, @AuthenticationPrincipal Jwt jwt) {
    return save(owned(id, jwt), i);
  }

  Map<String, Object> save(Product p, Input i) {
    p.productName = i.productName().trim();
    p.brand = i.brand().trim();
    p.category = i.category().trim();
    p.store = i.store().trim();
    p.price = i.price();
    p.purchaseDate = i.purchaseDate();
    p.warrantyMonths = i.warrantyMonths();
    return view(products.saveAndFlush(p));
  }

  @DeleteMapping("/products/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    Product p = owned(id, jwt);
    receipts.deleteById(id);
    requests.deleteAll(requests.findByProductIdOrderByIdDesc(id));
    products.delete(p);
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(@AuthenticationPrincipal Jwt jwt) {
    var all = list(jwt, "", "", "");
    BigDecimal total =
        all.stream().map(p -> (BigDecimal) p.get("price")).reduce(BigDecimal.ZERO, BigDecimal::add);
    return Map.of(
        "categories",
        all.stream()
            .map(p -> (String) p.get("category"))
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList(),
        "totalProducts",
        all.size(),
        "totalSpent",
        total,
        "active",
        all.stream().filter(p -> p.get("status").equals("ACTIVE")).count(),
        "expiring",
        all.stream().filter(p -> p.get("status").equals("EXPIRING")).count(),
        "expired",
        all.stream().filter(p -> p.get("status").equals("EXPIRED")).count(),
        "receiptCount",
        all.stream().filter(p -> (Boolean) p.get("hasReceipt")).count(),
        "upcoming",
        all.stream()
            .filter(p -> (Long) p.get("daysLeft") >= 0)
            .sorted(Comparator.comparing(p -> (LocalDate) p.get("expiryDate")))
            .limit(5)
            .toList());
  }

  @GetMapping("/reminders")
  public List<Map<String, Object>> reminders(@AuthenticationPrincipal Jwt jwt) {
    return list(jwt, "", "", "EXPIRING");
  }
}
