package com.rewardflow.app.service;

public final class SceneNormalizer {

  private SceneNormalizer() {
  }

  public static String normalize(String scene) {
    if (scene == null) {
      return null;
    }
    String s = scene.trim();
    if (s.isEmpty()) {
      return s;
    }
    return s.replace(':', '_');
  }
}
