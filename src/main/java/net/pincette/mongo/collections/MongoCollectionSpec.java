package net.pincette.mongo.collections;

import static java.util.Objects.hash;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.generator.annotation.Required;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MongoCollectionSpec {
  @JsonProperty("capped")
  public boolean capped;

  @JsonProperty("changeStreamPreAndPostImages")
  public boolean changeStreamPreAndPostImages;

  @JsonProperty("clustered")
  public boolean clustered;

  @JsonProperty("collation")
  public Collation collation;

  @JsonProperty("encryptedFields")
  public EncryptedFields encryptedFields;

  @JsonProperty("expireAfterSeconds")
  public long expireAfterSeconds = -1;

  @JsonProperty("indexes")
  public List<Index> indexes;

  @JsonProperty("max")
  public long max = -1;

  @JsonProperty("size")
  public long size = -1;

  @JsonProperty("timeSeries")
  public TimeSeries timeSeries;

  @JsonProperty("validator")
  public Map<String, Object> validator;

  @JsonProperty("validationAction")
  public ValidationAction validationAction;

  @JsonProperty("validationLevel")
  public ValidationLevel validationLevel;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Collation {
    @JsonProperty("alternate")
    public String alternate = "non-ignorable";

    @JsonProperty("backwards")
    public boolean backwards;

    @JsonProperty("caseFirst")
    public String caseFirst = "off";

    @JsonProperty("caseLevel")
    public boolean caseLevel;

    @JsonProperty("locale")
    @Required
    public String locale;

    @JsonProperty("maxVariable")
    public String maxVariable = "punct";

    @JsonProperty("normalization")
    public boolean normalization;

    @JsonProperty("numericOrdering")
    public boolean numericOrdering;

    @JsonProperty("strength")
    public int strength = 1;

    static Collation defaultCollation(final String locale) {
      final Collation collation = new Collation();

      collation.locale = locale;

      return collation;
    }

    @Override
    public boolean equals(final Object obj) {
      return ofNullable(obj)
          .filter(Collation.class::isInstance)
          .map(Collation.class::cast)
          .filter(
              c ->
                  this == c
                      || (backwards == c.backwards
                          && caseLevel == c.caseLevel
                          && normalization == c.normalization
                          && numericOrdering == c.numericOrdering
                          && strength == c.strength
                          && Objects.equals(alternate, c.alternate)
                          && Objects.equals(caseFirst, c.caseFirst)
                          && Objects.equals(locale, c.locale)
                          && Objects.equals(maxVariable, c.maxVariable)))
          .isPresent();
    }

    @Override
    public int hashCode() {
      return hash(
          alternate,
          backwards,
          caseFirst,
          caseLevel,
          locale,
          maxVariable,
          normalization,
          numericOrdering,
          strength);
    }
  }

  public static class EncryptedFields {
    @JsonProperty("fields")
    public List<EncryptedField> fields;

    @JsonProperty("queryPatterns")
    public List<Map<String, QueryType>> queryPatterns;

    public static class EncryptedField {
      @JsonProperty("bsonType")
      @Required
      public String bsonType;

      @JsonProperty("keyId")
      @Required
      public String keyId;

      @JsonProperty("path")
      @Required
      public String path;

      @JsonProperty("queries")
      public List<Map<String, QueryType>> queries;
    }
  }

  public static class Index {
    @JsonProperty("keys")
    @Required
    public List<Key> keys;

    @JsonProperty("options")
    public Options options = new Options();

    @Override
    public boolean equals(final Object obj) {
      return ofNullable(obj)
          .filter(Index.class::isInstance)
          .map(Index.class::cast)
          .filter(i -> keys.equals(i.keys) && options.equals(i.options))
          .isPresent();
    }

    @Override
    public int hashCode() {
      return hash(keys, options);
    }

    public static class Key {
      @JsonProperty("direction")
      @Required
      public int direction;

      @JsonProperty("field")
      @Required
      public String field;

      @Override
      public boolean equals(final Object obj) {
        return ofNullable(obj)
            .filter(Key.class::isInstance)
            .map(Key.class::cast)
            .filter(k -> direction == k.direction && Objects.equals(field, k.field))
            .isPresent();
      }

      @Override
      public int hashCode() {
        return hash(direction, field);
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Options {
      @JsonProperty("bits")
      public int bits = -1;

      @JsonProperty("collation")
      public Collation collation;

      @JsonProperty("defaultLanguage")
      public String defaultLanguage;

      @JsonProperty("expireAfterSeconds")
      public long expireAfterSeconds = -1;

      @JsonProperty("hidden")
      public boolean hidden;

      @JsonProperty("languageOverride")
      public String languageOverride;

      @JsonProperty("max")
      public Double max;

      @JsonProperty("min")
      public Double min;

      @JsonProperty("name")
      public String name;

      @JsonProperty("partialFilterExpression")
      public Map<String, Object> partialFilterExpression;

      @JsonProperty("sparse")
      public boolean sparse;

      @JsonProperty("sphereIndexVersion")
      public int sphereIndexVersion = -1;

      @JsonProperty("textIndexVersion")
      public int textIndexVersion = -1;

      @JsonProperty("unique")
      public boolean unique;

      @JsonProperty("weights")
      public Map<String, Integer> weights;

      @JsonProperty("wildcardProjection")
      public Map<String, Integer> wildcardProjection;

      @Override
      public boolean equals(final Object obj) {
        // The name does not play a role because it can be generated by MongoDB.
        return ofNullable(obj)
            .filter(Options.class::isInstance)
            .map(Options.class::cast)
            .filter(
                o ->
                    this == o
                        || (bits == o.bits
                            && Objects.equals(collation, o.collation)
                            && Objects.equals(defaultLanguage, o.defaultLanguage)
                            && expireAfterSeconds == o.expireAfterSeconds
                            && hidden == o.hidden
                            && Objects.equals(languageOverride, o.languageOverride)
                            && Objects.equals(max, o.max)
                            && Objects.equals(min, o.min)
                            && Objects.equals(partialFilterExpression, o.partialFilterExpression)
                            && sparse == o.sparse
                            && sphereIndexVersion == o.sphereIndexVersion
                            && textIndexVersion == o.textIndexVersion
                            && unique == o.unique
                            && Objects.equals(weights, o.weights)
                            && Objects.equals(wildcardProjection, o.wildcardProjection)))
            .isPresent();
      }

      @Override
      public int hashCode() {
        return hash(
            bits,
            collation,
            defaultLanguage,
            expireAfterSeconds,
            hidden,
            languageOverride,
            max,
            min,
            partialFilterExpression,
            sphereIndexVersion,
            sparse,
            textIndexVersion,
            unique,
            weights,
            wildcardProjection);
      }
    }
  }

  public static class TimeSeries {
    @JsonProperty("granularity")
    public Granularity granularity;

    @JsonProperty("metaField")
    public String metaField;

    @JsonProperty("timeField")
    @Required
    public String timeField;
  }
}
