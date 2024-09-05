package net.pincette.mongo.collections;

import static java.util.logging.Logger.getLogger;
import static net.pincette.operator.util.Util.watchedNamespaces;
import static net.pincette.util.Util.initLogging;

import io.javaoperatorsdk.operator.Operator;
import java.util.logging.Logger;

public class Application {
  static final Logger LOGGER = getLogger("net.pincette.mongo.collections");
  private static final String VERSION = "1.1.1";

  public static void main(final String[] args) {
    final Operator operator = new Operator();

    initLogging();
    LOGGER.info(() -> "Version " + VERSION);
    operator.register(
        new MongoCollectionReconciler(), config -> config.settingNamespaces(watchedNamespaces()));
    operator.start();
  }
}
