package org.codeberg.zenxarch.fastnoise.config;

import java.util.List;

public record BooleanConfigEntry(
    String key, String comment, boolean defaultValue, List<String> incompats) {
  public static BooleanConfigEntry of(String key, String comment, boolean defaultValue) {
    return new BooleanConfigEntry(
        key, comment + "\n" + "default: " + defaultValue, defaultValue, List.of());
  }

  public static BooleanConfigEntry mixin(String key, String comment, boolean defaultValue) {
    return of("mixin." + key, comment, defaultValue);
  }

  public BooleanConfigEntry incompatibleWith(String... incompats) {
    return new BooleanConfigEntry(
        key,
        comment + "\n" + "incompatibleWith: " + String.join(",", incompats),
        defaultValue,
        List.of(incompats));
  }

  public boolean isMixin() {
    return this.key.startsWith("mixin.");
  }
}
