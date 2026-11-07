package dev.baitursinov.caravan.ledger.config.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

abstract class StringValueMixIn {

  @SuppressWarnings("unused")
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  StringValueMixIn(String value) {
  }

  @JsonValue
  abstract String value();
}
