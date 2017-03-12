### Monitoring
***
Maxwell exposes certain metrics through either its base logging mechanism, JMX or HTTP. This is configurable through commandline options
or the `config.properties` file. These can provide insight into system health.
At present certain metrics are Kafka-specific - that is, not yet supported other producers.

### Metrics
All metrics are prepended with `MaxwellMetrics.`

metric                         | description
-------------------------------|-------------------------------------
`count.failed`                 | count of messages that failed to send to Kafka
`count.succeeded`              | count of messages that were successfully sent to Kafka
`replication.lag`              | the time elapsed between the database transaction commit and the time it was processed by Maxwell, in milliseconds
`row.count`                    | a count of rows that have been processed from the binlog. note that not every row results in a message being sent to Kafka.
`row.meter`                    | a measure of the rate at which rows arrive to Maxwell from the binlog connector
`time.overall`                 | the time it took to send a given record to Kafka, in milliseconds

### HTTP Endpoints
When the HTTP server is enabled the following endpoints are exposed:

endpoint                       | description
-------------------------------|-------------
`/metrics`                     | return all metrics as JSON
`/healthcheck`                 | run Maxwell's healthcheck(s) and return success or failure based on the result
`/ping`                        | a simple ping test, responds with `pong`

### JMX Configuration
Standard configuration is either via commandline args or the `config.properties` file. However, when exposing JMX metrics
additional configuration is required to allow remote access. In this case Maxwell makes use of the `JAVA_OPTS` environment variable.
To make use of this set `JAVA_OPTS` before starting Maxwell.

The following is an example which allows remote access with no authentication and dinsecure connections.

```
export JAVA_OPTS="-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9010
-Dcom.sun.management.jmxremote.local.only=false
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false"
```



<script>
  jQuery(document).ready(function () {
    jQuery("table").addClass("table table-condensed table-bordered table-hover");
  });
</script>