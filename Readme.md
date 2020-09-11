# Akka Streams Demo 

Akka Streams Demo with error handling to accompany blog post: URL

## Docker setup of environment

### Cassandra Docker setup
Start cassandra with following command:

`docker run --name cassandra -p9042:9042 -d cassandra:latest`

To create necessary table used in this project execute following:

`docker exec -it cassandra bash`

When inside container:

`cqlsh`

```
CREATE KEYSPACE test WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1}  AND durable_writes = true;

CREATE TABLE test.sms (id text,source text,destination text,sms_text text,timestamp timestamp,PRIMARY KEY (id));

```
### Elasticsearch Docker setup

Start Elastic with following command:

`docker run -d --name elastic-7-9 -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:7.9.0`

It's not mandatory, but Kibana can be started also, so data inserted into Elastic can be searched:

`docker run -d --name kibana-7-9 --link elastic-7-9:elasticsearch -p 5601:5601 docker.elastic.co/kibana/kibana:7.9.0`

After Kibana iz up&running, it can be reached on:

`http://localhost:5601`

No additional work is needed, ES index where data is stored will be created automatically.

If Kibana is started, you can check data inserted in ES in Dev Tools console with following command:

```
GET /sms/_search
{
  "query": {
    "match_all": {}
  }
}
```

### Kafka Docker setup
TODO




