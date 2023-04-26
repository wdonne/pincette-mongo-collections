# The MongoDB Collections Operator

With this Kubernetes operator you can manage MongoDB collections. The `MongoCollection` custom resource describes a MongoDB collection. It will create the collection if it doesn't exist. The provides properties are used for the creation, but they are not reconciled after that. The indexes are always reconciled, which means indexes may be dropped and recreated when they have been changed in any other way. When a custom resource is deleted, the MongoDB collection will not be deleted. A resource looks like this:

```
apiVersion: pincette.net/v1
kind: MongoCollection
metadata:
  name: test-collection
  namespace: test-mongo-collections
spec:
  name: test-collection
  clustered: true  
  collation:
    locale: "en"
    strength: 1
    caseLevel: true
  indexes:
    - keys:
        field1: 1
        field2: -1
      options:        
        name: myindex1
        expireAfterSeconds: 20
    - keys:
        field3: 1
      options:        
        sparse: true
        unique: true      
```

The collection properties are described at [https://www.mongodb.com/docs/v6.0/reference/method/db.createCollection/](https://www.mongodb.com/docs/v6.0/reference/method/db.createCollection/). The unspoorted properties are `indexOptionDefaults`, `pipeline`, `storageEngine`, `viewOn` and `writeConcern`. The property `clusteredIndex` was change to the boolean property `clustered`.

The collation properties are described at [https://www.mongodb.com/docs/v6.0/reference/collation/#std-label-collation](https://www.mongodb.com/docs/v6.0/reference/collation/#std-label-collation). All properties are supported.

The index properties are described at [https://www.mongodb.com/docs/v6.0/reference/method/db.collection.createIndex/](https://www.mongodb.com/docs/v6.0/reference/method/db.collection.createIndex/). The unsupported options are `storageEngine` and `bucketSize`. The option `2dsphereIndexVersion` was renamed to `sphereIndexVersion`.

Install the operator as follows:

```
kubectl apply -f https://github.com/wdonne/pincette-mongo-collections/raw/main/manifests/install.yaml
```

You need to provide a `Secret` in the `mongo-collections` namespace with the name `config` like this:

```
apiVersion: v1
kind: Secret
metadata:
  namespace: mongo-collections
  name: config
data:
  application.conf: |
    uri = "mongodb://username:password@localhost:27017"
    database = mydatabase    
```

The syntax is [Lightbend Config](https://github.com/lightbend/config). If the URI is private to the cluster and without credentials, then you could kustomize the `Deployment` resource to mount a `ConfigMap` instead. You could also mount both and use an `include` statement to include the secret in the config, for example.

The user should be able to create the database if it doesn't exist yet and create and drop collections and indexes.