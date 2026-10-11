package com.warrantyhub.vault;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/me/photo")
public class ProfilePhotoController {
  private final AccountPhotoRepository photos;
  // Bound decoded image memory even when several uploads arrive together.
  private final java.util.concurrent.Semaphore imageSlot = new java.util.concurrent.Semaphore(1);

  public ProfilePhotoController(AccountPhotoRepository photos) {
    this.photos = photos;
  }

  @GetMapping
  public ResponseEntity<byte[]> get(@AuthenticationPrincipal Jwt jwt) {
    var photo =
        photos
            .findById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No profile photo"));
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(photo.content);
  }

  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
      throws IOException {
    if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a PNG or JPEG photo under 2 MB");
    byte[] bytes = file.getBytes();
    boolean png =
        bytes.length >= 8
            && bytes[0] == (byte) 137
            && bytes[1] == 80
            && bytes[2] == 78
            && bytes[3] == 71;
    boolean jpeg =
        bytes.length >= 3
            && bytes[0] == (byte) 255
            && bytes[1] == (byte) 216
            && bytes[2] == (byte) 255;
    if (!png && !jpeg) throw invalid();
    if (!imageSlot.tryAcquire())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Another photo is being processed. Please try again.");
    try {
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try {
          reader.setInput(input, true, true);
          int w = reader.getWidth(0), h = reader.getHeight(0);
          if (w <= 0 || h <= 0 || (long) w * h > 16000000 || w > 8000 || h > 8000)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Photo must be at most 16 megapixels");
          BufferedImage source = reader.read(0);
          int crop = Math.min(w, h), size = Math.min(512, crop);
          BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
          var g = output.createGraphics();
          try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(
                source,
                0,
                0,
                size,
                size,
                (w - crop) / 2,
                (h - crop) / 2,
                (w + crop) / 2,
                (h + crop) / 2,
                null);
          } finally {
            g.dispose();
          }
          var buffer = new ByteArrayOutputStream();
          ImageIO.write(output, "jpeg", buffer);
          var photo = new AccountPhoto();
          photo.accountId = Long.valueOf(jwt.getSubject());
          photo.content = buffer.toByteArray();
          photos.saveAndFlush(photo);
        } finally {
          reader.dispose();
        }
      } catch (IOException | IllegalArgumentException e) {
        throw invalid();
      }
    } finally {
      imageSlot.release();
    }
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt) {
    photos.deleteById(Long.valueOf(jwt.getSubject()));
  }

  private ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Choose a valid PNG or JPEG image");
  }
}
