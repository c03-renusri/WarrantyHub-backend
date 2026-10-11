package com.warrantyhub.vault;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AccountRepository accounts;
  private final AccountPhotoRepository photos;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final String dummyHash;

  public AuthController(
      AccountRepository a, PasswordEncoder p, JwtEncoder e, AccountPhotoRepository photos) {
    accounts = a;
    this.photos = photos;
    passwords = p;
    encoder = e;
    dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  public record Registration(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 72) String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  private String normalized(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(@Valid @RequestBody Registration r) {
    if (r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 bytes");
    if (accounts.findByEmail(normalized(r.email())).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    Account a = new Account();
    a.name = r.name().trim();
    a.email = normalized(r.email());
    a.passwordHash = passwords.encode(r.password());
    return session(accounts.saveAndFlush(a));
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody Login r) {
    checkPasswordBytes(r.password());
    var a = accounts.findByEmail(normalized(r.email())).orElse(null);
    boolean matches = passwords.matches(r.password(), a == null ? dummyHash : a.passwordHash);
    if (a == null || !matches)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    return session(a);
  }

  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return profile(
        accounts
            .findById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
  }

  public record ProfileInput(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 30) @Pattern(regexp = "[+0-9 ()-]*") String phone,
      @Size(max = 100) String location,
      @Size(max = 500) String bio) {}

  public record PasswordInput(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 10, max = 72) String newPassword) {}

  @PutMapping("/me")
  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> updateProfile(
      @Valid @RequestBody ProfileInput input, @AuthenticationPrincipal Jwt jwt) {
    Account account =
        accounts
            .lockById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    account.name = input.name().trim();
    account.phone = clean(input.phone());
    account.location = clean(input.location());
    account.bio = clean(input.bio());
    return profile(accounts.saveAndFlush(account));
  }

  @PutMapping("/password")
  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> changePassword(
      @Valid @RequestBody PasswordInput input, @AuthenticationPrincipal Jwt jwt) {
    checkPasswordBytes(input.currentPassword());
    checkPasswordBytes(input.newPassword());
    Account account =
        accounts
            .lockById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (!passwords.matches(input.currentPassword(), account.passwordHash))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    if (passwords.matches(input.newPassword(), account.passwordHash))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different new password");
    account.passwordHash = passwords.encode(input.newPassword());
    account.tokenVersion++;
    return session(accounts.saveAndFlush(account));
  }

  private void checkPasswordBytes(String value) {
    if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private Map<String, Object> profile(Account a) {
    return Map.of(
        "id",
        a.id,
        "name",
        a.name,
        "email",
        a.email,
        "phone",
        clean(a.phone),
        "location",
        clean(a.location),
        "bio",
        clean(a.bio),
        "hasPhoto",
        photos.existsById(a.id));
  }

  private Map<String, Object> session(Account a) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("warrantyhub-vault")
            .subject(a.id.toString())
            .claim("version", a.tokenVersion)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return Map.of("token", token, "user", profile(a), "expiresIn", 3600);
  }
}
