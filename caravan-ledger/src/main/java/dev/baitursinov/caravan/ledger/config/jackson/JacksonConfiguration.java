package dev.baitursinov.caravan.ledger.config.jackson;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jodamoney.JodaMoneyModule;

@Configuration
public class JacksonConfiguration {

  @Bean
  public JsonMapper jsonMapper() {
    return JsonMapper.builder()
        .addModule(new JodaMoneyModule())
        .addMixIn(AccountId.class, StringValueMixIn.class)
        .addMixIn(TransactionId.class, StringValueMixIn.class)
        .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
        .build();
  }
}
