package com.warrantyhub.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products/{id}/receipt")
public class ReceiptController {
  private final ProductController products;
  private final ReceiptRepository receipts;

  public ReceiptController(ProductController p, ReceiptRepository r) {
    products = p;
    receipts = r;
  }

  @PostMapping
  public void upload(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    products.owned(id, jwt);
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Receipt must be between 1 byte and 5 MB");
    byte[] data = file.getBytes();
    String type = detect(data);
    if (type == null)
      throw new ResponseStatusException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, PNG and JPEG receipts are supported");
    Receipt r = new Receipt();
    r.productId = id;
    r.content = data;
    r.contentType = type;
    String original = file.getOriginalFilename();
    r.filename = (original == null ? "receipt" : original).replaceAll("[^A-Za-z0-9._ -]", "_");
    r.filename = r.filename.substring(0, Math.min(r.filename.length(), 200));
    receipts.saveAndFlush(r);
  }

  static String detect(byte[] d) {
    if (d.length >= 5 && new String(d, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
      return "application/pdf";
    if (d.length >= 8
        && Arrays.equals(Arrays.copyOf(d, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      return "image/png";
    if (d.length >= 3 && d[0] == (byte) 255 && d[1] == (byte) 216 && d[2] == (byte) 255)
      return "image/jpeg";
    return null;
  }

  @GetMapping
  public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    products.owned(id, jwt);
    var r =
        receipts
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(r.contentType))
        .header(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename(r.filename, StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(r.content);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    products.owned(id, jwt);
    receipts.deleteById(id);
  }
}
