package com.litongjava.tio.utils.json;

public class JsonUtils {

  public String toJson(Object object) {
    return Json.getJson().toJson(object);
  }

  public <T> T parse(String jsonString, Class<T> type) {
    return Json.getJson().parse(jsonString, type);
  }
}
