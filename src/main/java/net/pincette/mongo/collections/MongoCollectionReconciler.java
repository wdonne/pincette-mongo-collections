package net.pincette.mongo.collections;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.compoundIndex;
import static com.mongodb.client.model.Indexes.descending;
import static com.typesafe.config.ConfigFactory.defaultOverrides;
import static io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer.generateNameFor;
import static io.javaoperatorsdk.operator.api.reconciler.UpdateControl.patchStatus;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Logger.getLogger;
import static java.util.stream.Collectors.toList;
import static net.pincette.jes.util.Configuration.loadDefault;
import static net.pincette.json.Jackson.from;
import static net.pincette.json.Jackson.to;
import static net.pincette.json.JsonUtil.createArrayBuilder;
import static net.pincette.json.JsonUtil.createObjectBuilder;
import static net.pincette.json.JsonUtil.createValue;
import static net.pincette.mongo.BsonUtil.fromJsonNew;
import static net.pincette.mongo.collections.MongoCollectionSpec.Collation.defaultCollation;
import static net.pincette.util.Collections.map;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.StreamUtil.stream;
import static net.pincette.util.Util.tryToGetRethrow;
import static net.pincette.util.Util.tryToGetWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ChangeStreamPreAndPostImagesOptions;
import com.mongodb.client.model.ClusteredIndexOptions;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationOptions;
import com.typesafe.config.Config;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.json.JsonObject;
import javax.json.JsonValue;
import net.pincette.mongo.BsonUtil;
import net.pincette.mongo.collections.MongoCollectionSpec.Index;
import net.pincette.mongo.collections.MongoCollectionSpec.Index.Key;
import net.pincette.mongo.collections.MongoCollectionSpec.Index.Options;
import net.pincette.mongo.collections.MongoCollectionSpec.TimeSeries;
import net.pincette.util.ImmutableBuilder;
import net.pincette.util.Pair;
import org.bson.Document;
import org.bson.conversions.Bson;

@io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
@GradualRetry(maxAttempts = MAX_VALUE)
public class MongoCollectionReconciler
    implements Reconciler<MongoCollection>, EventSourceInitializer<MongoCollection> {
  private static final ClusteredIndexOptions CLUSTERED_INDEX_OPTIONS =
      new ClusteredIndexOptions(eq("_id", 1), true);
  private static final String CLUSTERED_NAME = "_id_";
  private static final String DIRECTION = "direction";
  private static final String FIELD = "field";
  private static final String KEY = "key";
  private static final Logger LOGGER = getLogger("net.pincette.mongo.collections");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final TimerEventSource<MongoCollection> timerEventSource = new TimerEventSource<>();

  private static Collation collationOptions(final MongoCollectionSpec.Collation collation) {
    return ImmutableBuilder.create(Collation::builder)
        .update(b -> b.backwards(collation.backwards))
        .update(b -> b.caseLevel(collation.caseLevel))
        .update(b -> b.locale(collation.locale))
        .update(b -> b.normalization(collation.normalization))
        .update(b -> b.numericOrdering(collation.numericOrdering))
        .update(b -> b.collationStrength(CollationStrength.fromInt(collation.strength)))
        .updateIf(
            () -> ofNullable(collation.alternate),
            (b, v) -> b.collationAlternate(CollationAlternate.fromString(v)))
        .updateIf(
            () -> ofNullable(collation.caseFirst),
            (b, v) -> b.collationCaseFirst(CollationCaseFirst.fromString(v)))
        .updateIf(
            () -> ofNullable(collation.maxVariable),
            (b, v) -> b.collationMaxVariable(CollationMaxVariable.fromString(v)))
        .build()
        .build();
  }

  private static com.mongodb.client.MongoCollection<Document> create(
      final String name, final MongoCollectionSpec spec, final MongoDatabase database) {
    LOGGER.info(() -> "Create collection " + name);
    database.createCollection(name, createOptions(spec));

    return database.getCollection(name);
  }

  private static void createIndex(
      final com.mongodb.client.MongoCollection<Document> collection, final Index index) {
    final String name = collection.createIndex(indexes(index.keys), indexOptions(index.options));

    LOGGER.info(() -> "Created index " + name);
  }

  private static JsonValue createKeys(final Document key) {
    return key.entrySet().stream()
        .reduce(
            createArrayBuilder(),
            (b, e) ->
                b.add(
                    createObjectBuilder()
                        .add(FIELD, e.getKey())
                        .add(DIRECTION, createValue(e.getValue()))),
            (b1, b2) -> b1)
        .build();
  }

  private static CreateCollectionOptions createOptions(final MongoCollectionSpec spec) {
    return ImmutableBuilder.create(CreateCollectionOptions::new)
        .updateIf(o -> spec.capped, o -> o.capped(true))
        .updateIf(
            o -> spec.changeStreamPreAndPostImages,
            o ->
                o.changeStreamPreAndPostImagesOptions(
                    new ChangeStreamPreAndPostImagesOptions(true)))
        .updateIf(o -> spec.clustered, o -> o.clusteredIndexOptions(CLUSTERED_INDEX_OPTIONS))
        .updateIf(() -> ofNullable(spec.collation), (o, v) -> o.collation(collationOptions(v)))
        .updateIf(
            () -> ofNullable(spec.encryptedFields),
            (o, v) -> o.encryptedFields(toBson(spec.encryptedFields)))
        .updateIf(
            o -> spec.expireAfterSeconds != -1,
            o -> o.expireAfter(spec.expireAfterSeconds, SECONDS))
        .updateIf(o -> spec.max != -1, o -> o.maxDocuments(spec.max))
        .updateIf(o -> spec.size != -1, o -> o.sizeInBytes(spec.size))
        .updateIf(
            () -> ofNullable(spec.timeSeries), (o, v) -> o.timeSeriesOptions(timeSeriesOptions(v)))
        .updateIf(
            () -> ofNullable(spec.validator),
            (o, v) -> o.validationOptions(validationOptions(spec)))
        .build();
  }

  private static void dropIndex(
      final com.mongodb.client.MongoCollection<Document> collection, final String name) {
    LOGGER.info(() -> "Drop index " + name);
    collection.dropIndex(name);
  }

  static boolean exists(final MongoDatabase database, final String collection) {
    return stream(database.listCollectionNames().iterator()).anyMatch(n -> n.equals(collection));
  }

  private static Index fromBson(final Document bson) {
    return tryToGetRethrow(
            () ->
                MAPPER.treeToValue(
                    from(
                        rearrangeProperties(
                            BsonUtil.fromBson(bson.toBsonDocument()),
                            createKeys(bson.get(KEY, Document.class)))),
                    Index.class))
        .orElse(null);
  }

  private static Pair<String, String> getConfig() {
    final Config config = defaultOverrides().withFallback(loadDefault());

    return pair(config.getString("uri"), config.getString("database"));
  }

  private static IndexOptions indexOptions(final Options index) {
    return ImmutableBuilder.create(IndexOptions::new)
        .updateIf(o -> index.bits != -1, o -> o.bits(index.bits))
        .updateIf(() -> ofNullable(index.collation), (o, v) -> o.collation(collationOptions(v)))
        .updateIf(() -> ofNullable(index.defaultLanguage), IndexOptions::defaultLanguage)
        .updateIf(
            o -> index.expireAfterSeconds != -1,
            o -> o.expireAfter(index.expireAfterSeconds, SECONDS))
        .updateIf(o -> index.hidden, o -> o.hidden(true))
        .updateIf(() -> ofNullable(index.languageOverride), IndexOptions::languageOverride)
        .updateIf(() -> ofNullable(index.max), (o, v) -> o.max(index.max))
        .updateIf(() -> ofNullable(index.min), (o, v) -> o.max(index.min))
        .updateIf(() -> ofNullable(index.name), IndexOptions::name)
        .updateIf(
            () -> ofNullable(index.partialFilterExpression),
            (o, v) -> o.partialFilterExpression(toBson(index.partialFilterExpression)))
        .updateIf(o -> index.sparse, o -> o.sparse(true))
        .updateIf(
            o -> index.sphereIndexVersion != -1, o -> o.sphereVersion(index.sphereIndexVersion))
        .updateIf(o -> index.textIndexVersion != -1, o -> o.textVersion(index.textIndexVersion))
        .updateIf(o -> index.unique, o -> o.unique(true))
        .updateIf(() -> ofNullable(index.weights), (o, v) -> o.weights(toBson(index.weights)))
        .updateIf(
            () -> ofNullable(index.wildcardProjection),
            (o, v) -> o.wildcardProjection(toBson(index.wildcardProjection)))
        .build();
  }

  static List<Index> indexes(
      final com.mongodb.client.MongoCollection<Document> collection, final String locale) {
    return stream(collection.listIndexes().iterator())
        .filter(i -> !CLUSTERED_NAME.equals(i.getString("name")))
        // This index is implicit, not controlled.
        .map(MongoCollectionReconciler::fromBson)
        .map(i -> removeDefaultCollation(i, locale))
        .collect(toList());
  }

  private static Bson indexes(final List<Key> keys) {
    final List<Bson> indexes =
        keys.stream()
            .map(k -> k.direction == 1 ? ascending(k.field) : descending(k.field))
            .collect(toList());

    return indexes.size() == 1 ? indexes.get(0) : compoundIndex(indexes);
  }

  static String locale(final MongoCollectionSpec spec) {
    return ofNullable(spec.collation).map(c -> c.locale).orElse(null);
  }

  private static JsonObject rearrangeProperties(final JsonObject index, final JsonValue keys) {
    return createObjectBuilder()
        .add("keys", keys)
        .add(
            "options",
            index.entrySet().stream()
                .filter(e -> !e.getKey().equals("key"))
                .reduce(
                    createObjectBuilder(),
                    (b, e) -> b.add(e.getKey(), e.getValue()),
                    (b1, b2) -> b1))
        .build();
  }

  private static void reconcile(
      final String name, final MongoCollectionSpec spec, final MongoDatabase database) {
    reconcileIndexes(
        exists(database, name) ? database.getCollection(name) : create(name, spec, database),
        spec.indexes,
        locale(spec));
  }

  private static void reconcileIndexes(
      final com.mongodb.client.MongoCollection<Document> collection,
      final List<Index> indexes,
      final String locale) {
    final List<Index> found = indexes(collection, locale);

    found.stream()
        .filter(i -> !indexes.contains(i))
        .forEach(i -> dropIndex(collection, i.options.name));
    ofNullable(indexes).stream()
        .flatMap(List::stream)
        .map(i -> removeDefaultCollation(i, locale))
        .filter(i -> !found.contains(i))
        .forEach(i -> createIndex(collection, i));
  }

  static List<Index> removeDefaultCollation(final List<Index> indexes, final String locale) {
    indexes.forEach(i -> removeDefaultCollation(i, locale));

    return indexes;
  }

  private static Index removeDefaultCollation(final Index index, final String locale) {
    ofNullable(index.options)
        .map(o -> o.collation)
        .filter(c -> c.equals(defaultCollation(locale)))
        .ifPresent(c -> index.options.collation = null);

    return index;
  }

  private static TimeSeriesOptions timeSeriesOptions(final TimeSeries timeSeries) {
    return ImmutableBuilder.create(() -> new TimeSeriesOptions(timeSeries.timeField))
        .updateIf(
            () -> ofNullable(timeSeries.granularity),
            (t, v) -> t.granularity(TimeSeriesGranularity.valueOf(v.name())))
        .updateIf(() -> ofNullable(timeSeries.metaField), TimeSeriesOptions::metaField)
        .build();
  }

  private static Bson toBson(final Object o) {
    return fromJsonNew(to((ObjectNode) MAPPER.valueToTree(o)));
  }

  private static ValidationOptions validationOptions(final MongoCollectionSpec spec) {
    return ImmutableBuilder.create(ValidationOptions::new)
        .update(o -> o.validator(toBson(spec.validator)))
        .updateIf(
            () -> ofNullable(spec.validationAction),
            (o, v) -> o.validationAction(ValidationAction.fromString(v.name())))
        .updateIf(
            () -> ofNullable(spec.validationLevel),
            (o, v) -> o.validationLevel(com.mongodb.client.model.ValidationLevel.valueOf(v.name())))
        .build();
  }

  private UpdateControl<MongoCollection> error(final MongoCollection resource, final Throwable t) {
    LOGGER.log(SEVERE, t, t::getMessage);
    timerEventSource.scheduleOnce(resource, 5000);
    resource.setStatus(new MongoCollectionStatus(t.getMessage()));

    return patchStatus(resource);
  }

  public Map<String, EventSource> prepareEventSources(
      final EventSourceContext<MongoCollection> context) {
    timerEventSource.start();

    return map(pair(generateNameFor(timerEventSource), timerEventSource));
  }

  public UpdateControl<MongoCollection> reconcile(
      final MongoCollection resource, final Context<MongoCollection> context) {
    final Pair<String, String> config = getConfig();

    return tryToGetWith(
            () -> MongoClients.create(config.first),
            client -> {
              reconcile(
                  resource.getMetadata().getName(),
                  resource.getSpec(),
                  client.getDatabase(config.second));
              timerEventSource.scheduleOnce(resource, 60000);
              resource.setStatus(new MongoCollectionStatus());

              return patchStatus(resource);
            },
            e -> error(resource, e))
        .orElse(null);
  }
}
