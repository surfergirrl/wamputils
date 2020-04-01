package net.iqaros.wamp;


import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class SubscriptionTopic {
  private Long subscriptionId;
  private String topic;
  
  public SubscriptionTopic(Long subscriptionId, String topic) {
    this.subscriptionId = subscriptionId;
    this.topic = topic;
  }
  
  public boolean isSameSubscription(Long subId) {
    return this.subscriptionId.equals(subId);
  }
}
