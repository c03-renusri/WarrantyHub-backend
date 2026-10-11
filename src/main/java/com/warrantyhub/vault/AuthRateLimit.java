package com.warrantyhub.vault;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimit extends OncePerRequestFilter {
  private record Bucket(long minute, AtomicInteger count) {}

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final Clock clock;

  public AuthRateLimit(Clock c) {
    clock = c;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return !(r.getMethod().equals("POST") || r.getMethod().equals("PUT"))
        || !(r.getRequestURI().equals("/api/auth/login")
            || r.getRequestURI().equals("/api/auth/register")
            || r.getRequestURI().equals("/api/auth/password"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long minute = clock.instant().getEpochSecond() / 60;
    buckets.entrySet().removeIf(e -> e.getValue().minute() < minute);
    String ip = req.getRemoteAddr();
    if (buckets.size() >= 10000 && !buckets.containsKey(ip)) {
      reject(res);
      return;
    }
    var b =
        buckets.compute(
            ip,
            (k, old) ->
                old == null || old.minute() != minute
                    ? new Bucket(minute, new AtomicInteger())
                    : old);
    if (b.count().incrementAndGet() > 30) {
      reject(res);
      return;
    }
    chain.doFilter(req, res);
  }

  private void reject(HttpServletResponse res) throws IOException {
    res.setStatus(429);
    res.setHeader("Retry-After", "60");
    res.setContentType("application/json");
    res.getWriter().write("{\"message\":\"Too many sign-in attempts. Try again in a minute.\"}");
  }
}
