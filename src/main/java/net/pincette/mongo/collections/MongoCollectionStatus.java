package net.pincette.mongo.collections;

import static net.pincette.mongo.collections.Phase.Pending;
import static net.pincette.mongo.collections.Phase.Ready;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.generator.annotation.Required;

public class MongoCollectionStatus {
  @JsonProperty("error")
  public final String error;

  @JsonProperty("phase")
  @Required
  public final Phase phase;

  @JsonCreator
  public MongoCollectionStatus() {
    this(Ready, null);
  }

  MongoCollectionStatus(final String error) {
    this(Pending, error);
  }

  private MongoCollectionStatus(final Phase phase, final String error) {
    this.phase = phase;
    this.error = error;
  }
}
