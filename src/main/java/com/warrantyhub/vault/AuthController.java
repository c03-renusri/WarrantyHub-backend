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
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  public AuthController(AccountRepository a, PasswordEncoder p, JwtEncoder e) {
    accounts = a;
    passwords = p;
    encoder = e;
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
    var a =
        accounts
            .findByEmail(normalized(r.email()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    if (!passwords.matches(r.password(), a.passwordHash))
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

  private Map<String, Object> profile(Account a) {
    return Map.of("id", a.id, "name", a.name, "email", a.email);
  }

  private Map<String, Object> session(Account a) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("warrantyhub-vault")
            .subject(a.id.toString())
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
