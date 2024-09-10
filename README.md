# The MongoDB Collections Operator

With this Kubernetes operator you can manage MongoDB collections. The `MongoCollection` custom resource describes a MongoDB collection. It will create the collection if it doesn't exist. The provided properties are used for the creation, but they are not reconciled after that. The indexes are always reconciled, which means indexes may be dropped and recreated when they have been changed in any other way. When a custom resource is deleted, the MongoDB collection will not be deleted. A resource looks like this:

```yaml
apiVersion: pincette.net/v1
kind: MongoCollection
metadata:
  name: test-collection
  namespace: test-mongo-collections
spec:
  clustered: true  
  collation:
    locale: "en"
    strength: 1
    caseLevel: true
  indexes:
    - keys:
        - field: field1
          direction: 1
        - field: field2
          direction: -1
      options:        
        name: myindex1
        expireAfterSeconds: 20
    - keys:
        - field: field3
          direction: 1
      options:        
        sparse: true
        unique: true      
```

The `spec` field has no mandatory fields.

The collection properties are described at [https://www.mongodb.com/docs/v6.
0/reference/method/db.createCollection/](https://www.mongodb.com/docs/v6.0/reference/method/db.createCollection/). The unsupported properties are `indexOptionDefaults`, `pipeline`, 
`storageEngine`, `viewOn` and `writeConcern`. The property `clusteredIndex` was changed to the 
boolean property `clustered`.

The collation properties are described at [https://www.mongodb.com/docs/v6.0/reference/collation/#std-label-collation](https://www.mongodb.com/docs/v6.0/reference/collation/#std-label-collation). All properties are supported.

The index properties are described at [https://www.mongodb.com/docs/v6.0/reference/method/db.collection.createIndex/](https://www.mongodb.com/docs/v6.0/reference/method/db.collection.createIndex/). The unsupported options are `storageEngine` and `bucketSize`. The option `2dsphereIndexVersion` was renamed to `sphereIndexVersion`.

Install the operator as follows:

```bash
helm repo add pincette https://pincette.net/charts
helm repo update
helm install mongo-collections pincette/mongo-collections --namespace mongo-collections --create-namespace
```

The default chart values expect you to provide a `Secret` in the `mongo-collections` namespace (or the one you have chosen) with the name `config` like this:

```yaml
apiVersion: v1
kind: Secret
metadata:
  namespace: mongo-collections
  name: config
stringData:
  application.conf: |
    uri = "mongodb://username:password@localhost:27017"
    database = mydatabase    
```

The syntax is [Lightbend Config](https://github.com/lightbend/config). If your configuration has partly secret information and partly non-secret information, then you can load both a secret and a config map. Then you can include one in the other with a Lightbend include statement. The default command in the container image expects to find the result in `/conf/application.conf`, but you can change this in the values file.

The user should be able to create the database if it doesn't exist yet and create and drop collections and indexes.
