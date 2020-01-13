(ns metabase.test.data.athena
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [honeysql
             [core :as hsql]
             [format :as hformat]]
            [metabase.test.data
             [interface :as tx]
             [sql :as sql.tx]
             [sql-jdbc :as sql-jdbc.tx]]
            [metabase
             [config :as config]
             [driver :as driver]]
            [metabase.driver.athena :as athena]
            [metabase.driver.sql-jdbc
             [connection :as sql-jdbc.conn]
             [sync :as sql-jdbc.sync]]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.test.data.sql-jdbc
             [execute :as execute]
             [load-data :as load-data]]
            [metabase.test.data.sql.ddl :as ddl]
            [metabase.util
             [date :as du]
             [honeysql-extensions :as hx]])
  (:import java.util.Date
           java.sql.Time))

(sql-jdbc.tx/add-test-extensions! :athena)

;; during unit tests don't treat athena as having FK support
(defmethod driver/supports? [:athena :foreign-keys] [_ _] (not config/is-test?))

;; Override unprepare for date types as Athena doesn't support inserting "timestamp with time zone" values,
;; which is what the from_iso8601_timestamp() function returns.
(defmethod unprepare/unprepare-value [:athena Date] [_ value]
  (format "from_unixtime(to_unixtime(%s))"
          (hformat/to-sql
            (hsql/call :from_iso8601_timestamp (hx/literal (du/date->iso-8601 value))))
          ))

;; Override unprepare for Time types as this was causing errors in INSERTs - we may need to fix this in the main driver
;; TODO: Fix the from/to_unixtime shenanigans - they were used while testing how to implement this
(defmethod unprepare/unprepare-value [:athena Time] [_ value]
  (format "from_unixtime(to_unixtime(%s))"
          (hformat/to-sql
            (hsql/call :from_iso8601_timestamp (hx/literal (du/date->iso-8601 value))))
          ))

;;; ----------------------------------------------- Connection Details -----------------------------------------------

(defmethod tx/dbdef->connection-details :athena [_ context {:keys [database-name]}]
            (println "Damon Region: " (tx/db-test-env-var-or-throw :athena :region))
           (merge
             {:region   (tx/db-test-env-var-or-throw :athena :region)
              :access_key      (tx/db-test-env-var-or-throw :athena :access-key)
              :secret_key  (tx/db-test-env-var-or-throw :athena :secret-key)
              :s3_staging_dir (tx/db-test-env-var-or-throw :athena :s3-staging-dir)
              :workgroup "primary"}))

;; TODO: Implement this because the tests try to add a table with a hyphen and Athena doesn't support that
;(defmethod tx/format-name :athena
;  [_ s]
;  (format "`%s`" s))

;; TODO: We need a better way to have an isolated test environment for Athena
;; If other tables exist, the tests start to query them for some reason,
;; so we exclude them via an environment variable
(defmethod sql-jdbc.sync/excluded-schemas :athena [_]
  (println "Excluding schemas: " (tx/db-test-env-var-or-throw :athena :ignore-dbs "") #",")
  (str/split (tx/db-test-env-var-or-throw :athena :ignore-dbs "") #","))

;; Athena requires you identify an object with db-name.table-name
(defmethod sql.tx/qualified-name-components :athena
  ([_ db-name]                       [db-name])
  ([_ db-name table-name]            [db-name table-name])
  ([_ db-name table-name field-name] [db-name table-name field-name]))

; Athena requires backtick-escaped database name for some queries
;; TODO: Handle non-empty database
;; Error executing SQL: DROP DATABASE IF EXISTS `test-data`
(defmethod sql.tx/drop-db-if-exists-sql :athena
  [driver {:keys [database-name]}]
  (format "DROP DATABASE IF EXISTS `%s`" database-name))

(defmethod sql.tx/drop-table-if-exists-sql :athena
  [driver {:keys [database-name]} {:keys [table-name]}]
  (format "DROP TABLE IF EXISTS `%s`.`%s`" database-name table-name))

(defmethod sql.tx/create-db-sql :athena
  [driver {:keys [database-name]}]
  (format "CREATE DATABASE `%s`;" database-name))

;; Customize the create table table to include the S3 location
;; TODO: Specify a unique location each time
(defmethod sql.tx/create-table-sql :athena
  [driver {:keys [database-name]} {:keys [table-name], :as tabledef}]
  (let [field-definitions (conj (:field-definitions tabledef) {:field-name "id", :base-type  :type/Integer})]
  (format "CREATE EXTERNAL TABLE `%s`.`%s` (%s) LOCATION '%stest-data/%s/'"
          database-name
          table-name
          (->> field-definitions
               (map (fn [{:keys [field-name base-type]}]
                      (format "%s %s" field-name (if (map? base-type)
                                                                (:native base-type)
                                                                (sql.tx/field-base-type->sql-type driver base-type)))))
               (interpose ", ")
               (apply str))
          (tx/db-test-env-var-or-throw :athena :s3-staging-dir)
          table-name)))

;; The Athena JDBC driver doesn't support parameterized queries.
;; So go ahead and deparameterize all the statements for now.
(defmethod ddl/insert-rows-ddl-statements :athena
  [driver table-identifier row-or-rows]
  (for [sql+args ((get-method ddl/insert-rows-ddl-statements :sql-jdbc/test-extensions) driver table-identifier row-or-rows)]
    (unprepare/unprepare driver sql+args)))

(doseq [[base-type sql-type] {:type/BigInteger     "BIGINT"
                              :type/Boolean        "BOOLEAN"
                              :type/Date           "TIMESTAMP"
                              :type/DateTime       "TIMESTAMP"
                              :type/DateTimeWithTZ "TIMESTAMP"
                              :type/Decimal        "DECIMAL"
                              :type/Float          "DOUBLE"
                              :type/Integer        "INT"
                              :type/Text           "STRING"
                              :type/Time           "TIMESTAMP"}]
  (defmethod sql.tx/field-base-type->sql-type [:athena base-type] [_ _] sql-type))

;; I'm not sure why `driver/supports?` above doesn't rectify this, but make `add-fk-sql a noop
(defmethod sql.tx/add-fk-sql :athena [& _] nil)

;; Athena can only execute one statement at a time
(defmethod execute/execute-sql! :athena [& args]
  (apply execute/sequentially-execute-sql! args))

;; Might have to figure out autoincrement settings
(defmethod sql.tx/pk-sql-type :athena [_] "INTEGER")

;; Add IDs to the sample data
(defmethod load-data/load-data! :athena [& args]
  (apply load-data/load-data-add-ids! args))
