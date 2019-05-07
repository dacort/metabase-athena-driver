# Metabase Athena Driver

💥*Note:* This project is under active development

## Build

I'm not familiar enough with `lein` to know if there is a better way to include a jar from a static URL, so for the time being we download it manually.

1. Download the Athena driver into your local Maven repo
   ```shell
   mkdir -p ~/.m2/repository/athena/athena-jdbc/2.0.7/
   wget -O ~/.m2/repository/athena/athena-jdbc/2.0.7/athena-jdbc-2.0.7.jar https://s3.amazonaws.com/athena-downloads/drivers/JDBC/SimbaAthenaJDBC_2.0.7/AthenaJDBC42_2.0.7.jar 
   ```

2. Clone this repo
   ```shell
   git clone https://github.com/dacort/metabase-athena-driver
   ```

3. Build the jar
   ```shell
   cd metabase-athena-driver/
   DEBUG=1 LEIN_SNAPSHOTS_IN_RELEASE=true lein uberjar
   ```

## Running

With Metabase 0.32, drivers must be stored in a `plugins` directory in the same directory where `metabase.jar` is, or you can specify the directory by setting the env var `MB_PLUGINS_DIR`.

Let's assume we download `metabase.jar` to `~/metabae/` and we built the project above. From the source directory:

```shell
TARGET_DIR=~/metabae
mkdir ${TARGET_DIR}/plugins/
cp target/uberjar/athena.metabase-driver.jar ${TARGET_DIR}/plugins/
cd ${TARGET_DIR}/
java -jar metabase.jar
```

You should see a message on startup similar to:

```
05-07 23:27:32 INFO plugins.lazy-loaded-driver :: Registering lazy loading driver :athena...
05-07 23:27:32 INFO metabase.driver :: Registered driver :athena (parents: #{:sql-jdbc}) 🚚
```

## Configuring

Once you've started up Metabase, go to add a database and select "Athena".

You'll need to provide the AWS region, an access key and secret key, and an S3 bucket and prefix where query results will be written to.

Note that the initial sync can take some time depending on how many databases and tables you have.

## Known Issues

- Cannot specify a single database to sync
- Only native SQL queries are supported
  - Queries must not end with a semi-colon (`;`)
- Sometimes, the initial database verification can time out
  - If this happens, configure a higher timeout value with the `MB_DB_CONNECTION_TIMEOUT_MS` environment variable
