# Metabase Athena Driver

💥*Note:* This project is under active development

[![CircleCI](https://img.shields.io/circleci/build/github/dacort/metabase-athena-driver)](https://circleci.com/gh/dacort/metabase-athena-driver)
[![Latest Release](https://img.shields.io/github/v/release/dacort/metabase-athena-driver.svg?label=latest%20release&include_prereleases)](https://github.com/dacort/metabase-athena-driver/releases)
![Tested with Metabase v0.43.1](https://img.shields.io/badge/metabase-v0.43.1-blue?)
[![GitHub license](https://img.shields.io/github/license/dacort/metabase-athena-driver)](https://raw.githubusercontent.com/dacort/metabase-athena-driver/master/LICENSE)

## Installation

Beginning with Metabase 0.32, drivers must be stored in a `plugins` directory in the same directory where `metabase.jar` is, or you can specify the directory by setting the environment variable `MB_PLUGINS_DIR`. There are a few options to get up and running with a custom driver.

### Docker

This repository has an example [`Dockerfile`](./Dockerfile) you can use to build the Amazon Athena Metabase driver and run the most recent supported version of Metabase:

```shell
git clone https://github.com/dacort/metabase-athena-driver.git
cd metabase-athena-driver
docker build -t metabase/athena .
docker run --name metabase-athena -p 3000:3000 metabase/athena
```

Then open http://localhost:3000 and skip down to [Configuring](#Configuring)

### Download Metabase Jar and Run

1. Download a fairly recent Metabase binary release (jar file) from the [Metabase distribution page](https://metabase.com/start/jar.html).
2. Download the Athena driver jar from this repository's ["Releases"](https://github.com/dacort/metabase-athena-driver/releases) page
3. Create a directory and copy the `metabase.jar` to it.
4. In that directory create a sub-directory called `plugins`.
5. Copy the Athena driver jar to the `plugins` directory.
6. Make sure you are the in the directory where your `metabase.jar` lives.
7. Run `java -jar metabase.jar`.

In either case, you should see a message on startup similar to:

```
04-15 06:14:08 DEBUG plugins.lazy-loaded-driver :: Registering lazy loading driver :athena...
04-15 06:14:08 INFO driver.impl :: Registered driver :athena (parents: [:sql-jdbc]) 🚚
```

## Configuring

Once you've started up Metabase, go to add a database and select "Amazon Athena".

You'll need to provide the AWS region, an access key and secret key, and an S3 bucket and prefix where query results will be written to.

Please note:

- The provided bucket must be in the same region you specify.
- If you do _not_ provide an access key, the [default credentials chain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) will be used.
- The initial sync can take some time depending on how many databases and tables you have.

If you need an example IAM policy for providing read-only access to your customer-base, check out the [Example IAM Policy](#example-iam-policy) below.

You can provide additional options if necessary. For example, to disable result set streaming and enable `TRACE`-level debugging, use `UseResultsetStreaming=0;LogLevel=6`.

Result set streaming is a performance optimization that streams results from Athena rather than using pagination logic, however it requries outbound access to TCP port 444 and not all organizations allow that.

Other options can be found in the "Driver Configuration Options" section of the [Athena JDBC Driver Installation and Configuration
Guide](https://s3.amazonaws.com/athena-downloads/drivers/JDBC/SimbaAthenaJDBC_2.0.13/docs/Simba+Athena+JDBC+Driver+Install+and+Configuration+Guide.pdf).

## Contributing

### Prerequisites

- [Leiningen](https://leiningen.org/)
- [Install metabase-core](https://github.com/metabase/metabase/wiki/Writing-a-Driver:-Packaging-a-Driver-&-Metabase-Plugin-Basics#installing-metabase-core-locally)

### Build from source

The entire jar can now be built from the included Dockerfile.

1. Build the project and copy the jar from the export stage

```shell
docker build --output jars --target stg_export .
```

You should now have a `athena.metabase-driver.jar` file in the `jars/` directory.

2. Download a fairly recent Metabase binary release (jar file) from the [Metabase distribution page](https://metabase.com/start/jar.html).

3. Let's assume we download `metabase.jar` to `~/metabae/` and we built the project above. Copy the built jar to the Metabase plugins directly and run Metabase from there!
   ```shell
   TARGET_DIR=~/metabae
   mkdir ${TARGET_DIR}/plugins/
   cp jars/athena.metabase-driver.jar ${TARGET_DIR}/plugins/
   cd ${TARGET_DIR}/
   java -jar metabase.jar
   ```

You should see a message on startup similar to:

```
2019-05-07 23:27:32 INFO plugins.lazy-loaded-driver :: Registering lazy loading driver :athena...
2019-05-07 23:27:32 INFO metabase.driver :: Registered driver :athena (parents: #{:sql-jdbc}) 🚚
```

### Testing

There are two different sets of tests in the project.

1. Unit tests, located in the `test_unit/` directory
2. Integration tests, located in the standard `test/` directory

The reason they're split out is because the integration tests require us to [link the driver](https://github.com/metabase/metabase/wiki/Writing-a-Driver:-Adding-Test-Extensions,-Tests,-and-Setting-up-CI#file-organization) into the core Metabase code and run the full suite of tests there. I wanted to be able to have some lightweight unit tests that could be run without that overhead, so those are split out into the `test_unit/` directory.

Running the tests requires you to have the metabase source relevant to the version you're building against. To make this easier, you can also run tests from the Dockerfile.

```shell
docker build -t metabase/athena-test --target stg_test .
docker run --rm --name mb-test metabase/athena-test
```

## Resources

### Example IAM Policy

This policy provides read-only access. Note you need to specify any buckets you want the user to be able to query from _as well as_ the S3 bucket provided as part of the configuration where results are written to.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Athena",
      "Effect": "Allow",
      "Action": [
        "athena:BatchGetNamedQuery",
        "athena:BatchGetQueryExecution",
        "athena:GetNamedQuery",
        "athena:GetQueryExecution",
        "athena:GetQueryResults",
        "athena:GetQueryResultsStream",
        "athena:GetWorkGroup",
        "athena:ListDatabases",
        "athena:ListDataCatalogs",
        "athena:ListNamedQueries",
        "athena:ListQueryExecutions",
        "athena:ListTagsForResource",
        "athena:ListWorkGroups",
        "athena:ListTableMetadata",
        "athena:StartQueryExecution",
        "athena:StopQueryExecution",
        "athena:CreatePreparedStatement",
        "athena:DeletePreparedStatement",
        "athena:GetPreparedStatement"
      ],
      "Resource": "*"
    },
    {
      "Sid": "Glue",
      "Effect": "Allow",
      "Action": [
        "glue:BatchGetPartition",
        "glue:GetDatabase",
        "glue:GetDatabases",
        "glue:GetPartition",
        "glue:GetPartitions",
        "glue:GetTable",
        "glue:GetTables",
        "glue:GetTableVersion",
        "glue:GetTableVersions"
      ],
      "Resource": "*"
    },
    {
      "Sid": "S3ReadAccess",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:ListBucket", "s3:GetBucketLocation"],
      "Resource": [
        "arn:aws:s3:::bucket1",
        "arn:aws:s3:::bucket1/*",
        "arn:aws:s3:::bucket2",
        "arn:aws:s3:::bucket2/*"
      ]
    },
    {
      "Sid": "AthenaResultsBucket",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:AbortMultipartUpload",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": ["arn:aws:s3:::bucket2", "arn:aws:s3:::bucket2/*"]
    }
  ]
}
```

If your customer-base needs access to create tables for whatever reason, they will need additional AWS Glue permissions. Here is an example policy granting that. Note that the Resource is `*` so this will give Delete/Update permissions to any table.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "VisualEditor0",
      "Effect": "Allow",
      "Action": [
        "glue:BatchCreatePartition",
        "glue:UpdateDatabase",
        "glue:DeleteDatabase",
        "glue:CreateTable",
        "glue:CreateDatabase",
        "glue:UpdateTable",
        "glue:BatchDeletePartition",
        "glue:BatchDeleteTable",
        "glue:DeleteTable",
        "glue:CreatePartition",
        "glue:DeletePartition",
        "glue:UpdatePartition"
      ],
      "Resource": "*"
    }
  ]
}
```

## Known Issues

- Cannot specify a single database to sync
- ~~Only native SQL queries are supported~~
  - Native SQL Queries must not end with a semi-colon (`;`)
  - Basic aggregations seem to work in the query builder
  - ~~Parameterized queries are not supported~~
- Sometimes, the initial database verification can time out
  - If this happens, configure a higher timeout value with the `MB_DB_CONNECTION_TIMEOUT_MS` environment variable
- ~~Heavily nested fields can result in a `StackOverflowError`~~
  - ~~If this happens, increase the `-Xss` JVM parameter~~

## Updated Dockerfile

### Test image

```shell
docker build -t metabase/athena-test --target stg_test .
docker run -it --rm --name mb-test metabase/athena-test
```

### Copy jars

```shell
docker build --output jars --target stg_export .
```

### Run Metabase

```shell
docker build -t metabase/athena .
docker run --rm --name metabase-athena -p 3000:3000 metabase/athena
```
