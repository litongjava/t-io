package com.litongjava.tio.utils.json;

import com.google.gson.Gson;

public class GsonJson extends Json {

  private Gson gson = new Gson();

  @Override
  public String toJson(Object object) {
    return gson.toJson(object);
  }

  @Override
  public <T> T parse(String jsonString, Class<T> type) {
    return gson.fromJson(jsonString, type);
  }
}
