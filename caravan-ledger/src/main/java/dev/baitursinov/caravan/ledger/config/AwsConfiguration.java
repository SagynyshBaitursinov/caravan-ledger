package dev.baitursinov.caravan.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@Profile("aws")
public class AwsConfiguration {

  @Bean
  public DynamoDbClient dynamoDbClient() {
    return DynamoDbClient.create();
  }

  @Bean
  public SqsClient sqsClient() {
    return SqsClient.create();
  }
}
