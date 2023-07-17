package net.pincette.mongo.collections;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import net.pincette.operator.util.Status;

@Group("pincette.net")
@Version("v1")
public class MongoCollection extends CustomResource<MongoCollectionSpec, Status>
    implements Namespaced {}
