package com.warrantyhub.vault;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
      "spring.datasource.username=${TEST_DB_USER:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
      "app.jwt-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@AutoConfigureMockMvc
class VaultIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  String register() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    return json.readTree(
            mvc.perform(
                    post("/api/auth/register")
                        .with(
                            req -> {
                              req.setRemoteAddr(UUID.randomUUID().toString());
                              return req;
                            })
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "name",
                                    "Test user",
                                    "email",
                                    email,
                                    "password",
                                    "long-test-password"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("token")
        .asText();
  }

  Map<String, Object> product() {
    return new HashMap<>(
        Map.of(
            "productName",
            "Samsung washer",
            "brand",
            "Samsung",
            "category",
            "Appliances",
            "store",
            "Local store",
            "price",
            28000.50,
            "purchaseDate",
            "2020-03-15",
            "warrantyMonths",
            24));
  }

  long create(String token) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(product())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiryDate").value("2022-03-15"))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  @Test
  void unauthenticatedAccessDenied() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidJwtDenied() throws Exception {
    mvc.perform(get("/api/products").header("Authorization", "Bearer forged"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registrationAndLogin() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    var body =
        json.writeValueAsString(
            Map.of("name", "Alice", "email", email, "password", "long-test-password"));
    mvc.perform(
            post("/api/auth/register")
                .with(
                    req -> {
                      req.setRemoteAddr(UUID.randomUUID().toString());
                      return req;
                    })
                .contentType("application/json")
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    mvc.perform(
            post("/api/auth/register")
                .with(
                    req -> {
                      req.setRemoteAddr(UUID.randomUUID().toString());
                      return req;
                    })
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email.toUpperCase(Locale.ROOT),
                            "password",
                            "long-test-password"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", "incorrect"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void weakPasswordDenied() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .with(
                    req -> {
                      req.setRemoteAddr(UUID.randomUUID().toString());
                      return req;
                    })
                .contentType("application/json")
                .content("{\"name\":\"Alice\",\"email\":\"a@example.com\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ownershipIsolation() throws Exception {
    String owner = register(), other = register();
    long id = create(owner);
    mvc.perform(get("/api/products").header("Authorization", "Bearer " + other))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    mvc.perform(get("/api/products/" + id).header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/products/" + id)
                .header("Authorization", "Bearer " + other)
                .contentType("application/json")
                .content(json.writeValueAsString(product())))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/products/" + id + "/receipt").header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateFilterDashboardAndDelete() throws Exception {
    String t = register();
    long id = create(t);
    var p = product();
    p.put("productName", "Laptop");
    p.put("price", 31000.25);
    mvc.perform(
            put("/api/products/" + id)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(p)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productName").value("Laptop"));
    mvc.perform(
            get("/api/products?q=laptop&status=EXPIRED&category=Appliances")
                .header("Authorization", "Bearer " + t))
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/products?q=missing").header("Authorization", "Bearer " + t))
        .andExpect(content().json("[]"));
    mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + t))
        .andExpect(jsonPath("$.totalSpent").value(31000.25))
        .andExpect(jsonPath("$.expired").value(1));
    mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + t))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/products/" + id).header("Authorization", "Bearer " + t))
        .andExpect(status().isNotFound());
  }

  @Test
  void validationRejectsFutureDatesAndNegativePrices() throws Exception {
    String t = register();
    var p = product();
    p.put("price", -1);
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(p)))
        .andExpect(status().isBadRequest());
    p.put("price", 1);
    p.put("purchaseDate", LocalDate.now().plusYears(1).toString());
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(p)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void receiptsArePrivateValidatedAndReplaceable() throws Exception {
    String t = register(), other = register();
    long id = create(t);
    byte[] pdf = "%PDF-1.4 test receipt".getBytes();
    mvc.perform(
            multipart("/api/products/" + id + "/receipt")
                .file(new MockMultipartFile("file", "bill.pdf", "application/pdf", pdf))
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isOk());
    mvc.perform(get("/api/products/" + id + "/receipt").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(content().bytes(pdf))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            multipart("/api/products/" + id + "/receipt")
                .file(new MockMultipartFile("file", "bill.pdf", "application/pdf", pdf))
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
    mvc.perform(
            multipart("/api/products/" + id + "/receipt")
                .file(
                    new MockMultipartFile(
                        "file", "fake.pdf", "application/pdf", "<script>".getBytes()))
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            multipart("/api/products/" + id + "/receipt")
                .file(
                    new MockMultipartFile(
                        "file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]))
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/products/" + id + "/receipt").header("Authorization", "Bearer " + t))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/products/" + id + "/receipt").header("Authorization", "Bearer " + t))
        .andExpect(status().isNotFound());
  }

  @Test
  void serviceRequestsAreOwnedAndCascade() throws Exception {
    String t = register(), other = register();
    long id = create(t);
    String body = "{\"issueDescription\":\"Motor stopped\",\"status\":\"Pending\"}";
    long requestId =
        json.readTree(
                mvc.perform(
                        post("/api/products/" + id + "/service-requests")
                            .header("Authorization", "Bearer " + t)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    mvc.perform(
            get("/api/products/" + id + "/service-requests")
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/products/" + id + "/service-requests/" + requestId)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(body.replace("Pending", "Completed")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Completed"));
    mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + t))
        .andExpect(status().isNoContent());
  }

  @Test
  void remindersReflectExpiry() throws Exception {
    String t = register();
    var p = product();
    p.put("purchaseDate", LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
    p.put("warrantyMonths", 0);
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(p)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.daysLeft").value(0));
    mvc.perform(get("/api/reminders").header("Authorization", "Bearer " + t))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void healthAndCors() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(
            options("/api/products")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    mvc.perform(
            options("/api/products")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void authRateLimiting() throws Exception {
    String ip = UUID.randomUUID().toString();
    for (int i = 0; i < 30; i++)
      mvc.perform(
              post("/api/auth/login")
                  .with(
                      req -> {
                        req.setRemoteAddr(ip);
                        return req;
                      })
                  .contentType("application/json")
                  .content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
          .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .with(
                    req -> {
                      req.setRemoteAddr(ip);
                      return req;
                    })
                .contentType("application/json")
                .content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
  }

  @Test
  void profileAndPasswordChangeRevokesOldSessions() throws Exception {
    String token = register();
    var profile =
        json.readTree(
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String email = profile.get("email").asText();
    mvc.perform(
            put("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Updated name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated name"));
    mvc.perform(
            put("/api/auth/password")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "currentPassword",
                            "wrong-password",
                            "newPassword",
                            "new-long-password"))))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    long id = create(token);
    String fresh =
        json.readTree(
                mvc.perform(
                        put("/api/auth/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content(
                                json.writeValueAsString(
                                    Map.of(
                                        "currentPassword",
                                        "long-test-password",
                                        "newPassword",
                                        "new-long-password"))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("token")
            .asText();
    mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/products/" + id).header("Authorization", "Bearer " + fresh))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("email", email, "password", "long-test-password"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("email", email, "password", "new-long-password"))))
        .andExpect(status().isOk());
  }

  @Test
  void longUnicodePasswordsAndMalformedDatesReturnBadRequest() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("email", "test@example.com", "password", "é".repeat(40)))))
        .andExpect(status().isBadRequest());
    String token = register();
    var input = product();
    input.put("purchaseDate", "invalid-date");
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(input)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void databaseUpgradePreservesExistingData() throws Exception {
    String url =
        "jdbc:h2:mem:upgrade"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    org.flywaydb.core.Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
    try (var c = java.sql.DriverManager.getConnection(url, "sa", "");
        var statement = c.createStatement()) {
      statement.execute(
          "INSERT INTO accounts (name,email,password_hash) VALUES ('Existing"
              + " user','existing@example.com','hash')");
      statement.execute(
          "INSERT INTO products"
              + " (owner_id,product_name,brand,category,store,price,purchase_date,warranty_months)"
              + " VALUES (1,'Existing product','Brand','Category','Store',12.50,'2020-01-01',12)");
    }
    org.flywaydb.core.Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (var c = java.sql.DriverManager.getConnection(url, "sa", "");
        var statement = c.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT a.token_version, p.product_name FROM accounts a JOIN products p ON"
                    + " p.owner_id=a.id")) {
      assertTrue(rows.next());
      assertEquals(0, rows.getLong(1));
      assertEquals("Existing product", rows.getString(2));
    }
  }

  @Test
  void leapYearsAndInclusiveExpiry() {
    assertEquals(LocalDate.of(2025, 2, 28), WarrantyDates.expiry(LocalDate.of(2024, 2, 29), 12));
    assertEquals(LocalDate.of(2026, 2, 28), WarrantyDates.expiry(LocalDate.of(2026, 1, 31), 1));
    LocalDate today = LocalDate.of(2026, 10, 11);
    assertEquals("EXPIRED", WarrantyDates.status(today.minusDays(1), today));
    assertEquals("EXPIRING", WarrantyDates.status(today, today));
    assertEquals("EXPIRING", WarrantyDates.status(today.plusDays(30), today));
    assertEquals("ACTIVE", WarrantyDates.status(today.plusDays(31), today));
  }
}
