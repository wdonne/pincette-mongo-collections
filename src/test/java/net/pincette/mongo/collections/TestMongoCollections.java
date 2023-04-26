package net.pincette.mongo.collections;

import static com.mongodb.client.model.Filters.eq;
import static io.fabric8.kubernetes.client.Config.autoConfigure;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static net.pincette.jes.util.Configuration.loadDefault;
import static net.pincette.mongo.collections.MongoCollectionReconciler.exists;
import static net.pincette.mongo.collections.MongoCollectionReconciler.indexes;
import static net.pincette.mongo.collections.MongoCollectionReconciler.locale;
import static net.pincette.mongo.collections.MongoCollectionReconciler.removeDefaultCollation;
import static net.pincette.operator.testutil.Util.createNamespace;
import static net.pincette.operator.testutil.Util.createOrReplaceAndWait;
import static net.pincette.operator.testutil.Util.deleteNamespace;
import static net.pincette.util.Collections.concat;
import static net.pincette.util.Collections.list;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.StreamUtil.stream;
import static net.pincette.util.Util.waitFor;
import static net.pincette.util.Util.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.typesafe.config.Config;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.pincette.mongo.collections.MongoCollectionSpec.Collation;
import net.pincette.mongo.collections.MongoCollectionSpec.Index;
import net.pincette.mongo.collections.MongoCollectionSpec.Index.Key;
import net.pincette.mongo.collections.MongoCollectionSpec.Index.Options;
import net.pincette.operator.testutil.Util;
import net.pincette.util.Pair;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestMongoCollections {
  private static final KubernetesClient CLIENT =
      new KubernetesClientBuilder().withConfig(autoConfigure("minikube")).build();
  private static final String COLLECTION = "test-collection";
  private static final Config CONFIG = loadDefault();
  private static final MongoDatabase DATABASE =
      MongoClients.create(CONFIG.getString("uri")).getDatabase(CONFIG.getString("database"));
  private static final Duration INTERVAL = ofSeconds(1);
  private static final String NAMESPACE = "test-mongo-collections";

  private static void assertCollection() {
    assertEquals(
        list("v1", "v2"),
        stream(DATABASE.getCollection(COLLECTION).find().iterator())
            .map(d -> d.get("test"))
            .map(Object::toString)
            .sorted()
            .collect(toList()));
  }

  private static void create(final int strength) {
    create(createCollation(strength));
  }

  private static void create(final Collation collation) {
    final MongoCollectionSpec spec = createCollectionSpec(collation);

    createCollection(createCollection(spec));
    insertData();
    assertTrue(waitForCollection(spec));
    assertCollection();
  }

  private static Collation createCollation(final int strength) {
    final Collation collation = new Collation();

    collation.backwards = false;
    collation.locale = "nl";
    collation.strength = strength;

    return collation;
  }

  private static MongoCollection createCollection(final MongoCollectionSpec spec) {
    final MongoCollection collection = new MongoCollection();

    collection.setMetadata(
        new ObjectMetaBuilder().withName(COLLECTION).withNamespace(NAMESPACE).build());
    collection.setSpec(spec);

    return collection;
  }

  private static void createCollection(final MongoCollection collection) {
    createOrReplaceAndWait(CLIENT.resources(MongoCollection.class).resource(collection));
  }

  private static MongoCollectionSpec createCollectionSpec(final Collation collation) {
    final Index index = new Index();
    final MongoCollectionSpec spec = new MongoCollectionSpec();

    spec.capped = false;
    spec.clustered = true;
    spec.expireAfterSeconds = 200000;
    spec.collation = collation;

    index.options = new Options();
    index.options.name = "test";
    index.keys = keys(list(pair("test", 1)));
    index.options.collation = collation;
    index.options.sparse = false;
    index.options.hidden = false;
    index.options.unique = true;
    spec.indexes = list(index);

    return spec;
  }

  @AfterAll
  public static void deleteAll() {
    deleteCustomResource();
    deleteNamespace(CLIENT, NAMESPACE);
  }

  private static void deleteCustomResource() {
    Util.deleteCustomResource(CLIENT, "mongocollections.pincette.net");
  }

  private static void dropCollection() {
    DATABASE.getCollection(COLLECTION).drop();
    waitFor(waitForCondition(() -> completedFuture(!exists(DATABASE, COLLECTION))), INTERVAL)
        .toCompletableFuture()
        .join();
  }

  private static void insertData() {
    DATABASE
        .getCollection(COLLECTION)
        .insertMany(list(new Document("test", "v1"), new Document("test", "v2")));
  }

  private static List<Key> keys(final List<Pair<String, Integer>> pairs) {
    return pairs.stream()
        .map(
            p -> {
              final Key key = new Key();

              key.field = p.first;
              key.direction = p.second;

              return key;
            })
        .collect(toList());
  }

  private static void loadCustomResource() {
    Util.loadCustomResource(
        CLIENT,
        MongoCollection.class.getResourceAsStream(
            "/META-INF/fabric8/mongocollections.pincette.net-v1" + ".yml"));
  }

  @BeforeAll
  public static void prepare() {
    loadCustomResource();
    createNamespace(CLIENT, NAMESPACE);
    startOperator();
  }

  private static void startOperator() {
    final Operator operator = new Operator(CLIENT);

    operator.register(new MongoCollectionReconciler());
    operator.start();
  }

  private static boolean waitForCollection(final MongoCollectionSpec spec) {
    final String locale = locale(spec);
    final Set<Index> indexes = new HashSet<>(removeDefaultCollation(spec.indexes, locale));

    return waitFor(
            waitForCondition(
                () ->
                    completedFuture(
                        indexes.equals(
                            new HashSet<>(indexes(DATABASE.getCollection(COLLECTION), locale))))),
            INTERVAL)
        .toCompletableFuture()
        .join();
  }

  @AfterEach
  void after() {
    dropCollection();
  }

  void addAndRemoveIndex(final List<Pair<String, Integer>> keys) {
    final MongoCollectionSpec spec = createCollectionSpec(createCollation(1));

    createCollection(createCollection(spec));
    assertTrue(waitForCollection(spec));

    final List<Index> currentIndexes = spec.indexes;
    final Index index = new Index();

    index.keys = keys(keys);
    spec.indexes = concat(currentIndexes, list(index));
    createCollection(createCollection(spec));
    assertTrue(waitForCollection(spec));
    spec.indexes = currentIndexes;
    createCollection(createCollection(spec));
    assertTrue(waitForCollection(spec));
  }

  @Test
  @DisplayName("add and remove index one key")
  void addAndRemoveIndex1() {
    addAndRemoveIndex(list(pair("test2", -1)));
  }

  @Test
  @DisplayName("add and remove index two keys")
  void addAndRemoveIndex2() {
    addAndRemoveIndex(list(pair("test2", -1), pair("test3", 1)));
  }

  @BeforeEach
  void before() {
    dropCollection();
  }

  @Test
  @DisplayName("create with default collation")
  void create1() {
    create(1);
  }

  @Test
  @DisplayName("create with non-default collation")
  void create2() {
    create(2);
  }

  @Test
  @DisplayName("create without collation")
  void create3() {
    create(null);
  }

  @Test
  @DisplayName("illegal update")
  void illegalUpdate() {
    create(1);
    DATABASE.getCollection(COLLECTION).dropIndex("test");
    DATABASE.getCollection(COLLECTION).createIndex(eq("test", -1), new IndexOptions().name("test"));
    waitForCollection(createCollectionSpec(createCollation(1)));
  }
}
