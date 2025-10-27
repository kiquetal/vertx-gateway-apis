package com.example.gateway.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A very lightweight lock-free token bucket for per-service rate limiting.
 */
public class TokenBucket {
  private final long capacity;
  private final long refillPerSecond;
  private final AtomicLong tokens;
  private volatile long lastRefillNanos;

  public TokenBucket(long capacity, long refillPerSecond) {
    this.capacity = Math.max(1, capacity);
    this.refillPerSecond = Math.max(0, refillPerSecond);
    this.tokens = new AtomicLong(this.capacity);
    this.lastRefillNanos = System.nanoTime();
  }

  public boolean tryConsume() {
    refillIfNeeded();
    while (true) {
      long current = tokens.get();
      if (current <= 0) return false;
      if (tokens.compareAndSet(current, current - 1)) return true;
    }
  }

  private void refillIfNeeded() {
    long now = System.nanoTime();
    long elapsedNanos = now - lastRefillNanos;
    if (elapsedNanos <= 0) return;
    long toAdd = (elapsedNanos * refillPerSecond) / 1_000_000_000L;
    if (toAdd <= 0) return;
    lastRefillNanos = now;
    long prev;
    long next;
    do {
      prev = tokens.get();
      next = Math.min(capacity, prev + toAdd);
    } while (!tokens.compareAndSet(prev, next));
  }

  public long getCapacity() { return capacity; }
  public long getRefillPerSecond() { return refillPerSecond; }
  public long getTokens() { return tokens.get(); }
}
