package dev.baitursinov.caravan.ledger.test;

import dev.baitursinov.caravan.ledger.CaravanLedgerApplication;
import org.joda.money.Money;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = CaravanLedgerApplication.class)
@ActiveProfiles({"local", "test"})
@ExtendWith(SpringExtension.class)
public abstract class AbstractSpringBootTest {

  protected static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
  protected static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  protected static final Money ZERO = Money.zero(DEFAULT_CURRENCY);
  protected static final Money FIVE = usd(5);
  protected static final Money TEN = usd(10);

  protected void awaitEventually(Runnable assertion) {
    await().atMost(POLL_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(assertion::run);
  }

  protected static Money usd(int amount) {
    return Money.of(DEFAULT_CURRENCY, amount);
  }
}
