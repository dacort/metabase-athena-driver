(ns metabase.driver.athena
  (:refer-clojure :exclude [second])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [honeysql
             [core :as hsql]
             [format :as hformat]]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.sql-jdbc
             [connection :as sql-jdbc.conn]
             [execute :as sql-jdbc.execute]
             [sync :as sql-jdbc.sync]]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.util
             [date :as du]
             [honeysql-extensions :as hx]
             [i18n :refer [trs]]])
  (:import [java.sql DatabaseMetaData Timestamp]))

(driver/register! :athena, :parent :sql-jdbc)


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          metabase.driver method impls                                          |
;;; +----------------------------------------------------------------------------------------------------------------+

(defmethod driver/supports? [:athena :foreign-keys] [_ _] false)

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                     metabase.driver.sql-jdbc method impls                                      |
;;; +----------------------------------------------------------------------------------------------------------------+

;;; ---------------------------------------------- sql-jdbc.connection -----------------------------------------------

(defmethod sql-jdbc.conn/connection-details->spec :athena [_ {:keys [region access_key secret_key s3_staging_dir db], :as details}]
  (merge
   {:classname   "com.simba.athena.jdbc.Driver"
    :subprotocol "awsathena"
    :subname     (str "//athena." region ".amazonaws.com:443")
    :user        access_key
    :password    secret_key
    :s3_staging_dir  s3_staging_dir
    ; :LogLevel    6
    }
   (dissoc details :db)))

;;; ------------------------------------------------- sql-jdbc.sync --------------------------------------------------

;; Map of column types -> Field base types
;; https://s3.amazonaws.com/athena-downloads/drivers/JDBC/SimbaAthenaJDBC_2.0.5/docs/Simba+Athena+JDBC+Driver+Install+and+Configuration+Guide.pdf
(defmethod sql-jdbc.sync/database-type->base-type :athena [_ database-type]
  ({:array      :type/Array
    :bigint     :type/BigInteger
    :binary     :type/*
    :varbinary  :type/*
    :boolean    :type/Boolean
    :char       :type/Text
    :date       :type/Date
    :decimal    :type/Decimal
    :double     :type/Float
    :float      :type/Float
    :integer    :type/Integer
    :int        :type/Integer
    :map        :type/*
    :smallint   :type/Integer
    :string     :type/Text
    :struct     :type/*
    :timestamp  :type/DateTime
    :tinyint    :type/Integer
    :varchar    :type/Text} database-type ))

;; keyword function converts database-type variable to a symbol, so we use symbols above to map the types
(defn- database-type->base-type-or-warn
  "Given a `database-type` (e.g. `VARCHAR`) return the mapped Metabase type (e.g. `:type/Text`)."
  [driver database-type]
  (or (sql-jdbc.sync/database-type->base-type driver (keyword database-type))
      (do (log/warn (format "Don't know how to map column type '%s' to a Field base_type, falling back to :type/*."
                            database-type))
          :type/*)))

;; Not all tables in the Data Catalog are guaranted to be compatible with Athena
;; If an exception is thrown, log and throw an error
(defn describe-table-fields
  "Returns a set of column metadata for `schema` and `table-name` using `metadata`. "
  [^DatabaseMetaData metadata, driver, {^String schema :schema, ^String table-name :name}, & [^String db-name-or-nil]]
  (try    
    (with-open [rs (.getColumns metadata db-name-or-nil schema table-name nil)]
        (set
        (for [{database-type :type_name
                column-name   :column_name
                remarks       :remarks} (jdbc/metadata-result rs)]
        (merge
            {:name          column-name
            :database-type database-type
            :base-type     (database-type->base-type-or-warn driver database-type)}
            (when (not (str/blank? remarks))
            {:field-comment remarks})
            ))))
    (catch Throwable e
      (log/error e (trs "Error retreiving fields for DB {0}.{1}" schema table-name))
      (throw e))
  )
)


;; Becuse describe-table-fields might fail, we catch the error here and return an empty set of columns
(defmethod driver/describe-table :athena [driver database table]
  (jdbc/with-db-metadata [metadata (sql-jdbc.conn/db->pooled-connection-spec database)]
    (->> (assoc (select-keys table [:name :schema])
           :fields (try
            (describe-table-fields metadata driver table)
            (catch Throwable e (set nil)))
         ))))


;; EXTERNAL_TABLE is required for Athena
(defn- get-tables [^DatabaseMetaData metadata, ^String schema-or-nil, ^String db-name-or-nil]
  ;; tablePattern "%" = match all tables
  (with-open [rs (.getTables metadata db-name-or-nil schema-or-nil "%"
                             (into-array String ["EXTERNAL_TABLE", "EXTERNAL TABLE" "TABLE", "VIEW", "FOREIGN TABLE", "MATERIALIZED VIEW"]))]
    (vec (jdbc/metadata-result rs))))

;; Required because we're calling our own custom private get-tables method to support Athena
(defn- fast-active-tables [driver, ^DatabaseMetaData metadata, & [db-name-or-nil]]
  (with-open [rs (.getSchemas metadata)]
    (let [all-schemas (set (map :table_schem (jdbc/metadata-result rs)))
          schemas     (set/difference all-schemas (sql-jdbc.sync/excluded-schemas driver))]
      (set (for [schema schemas
                 table  (get-tables metadata schema db-name-or-nil)]
             (let [remarks (:remarks table)]
               {:name        (:table_name table)
                :schema      schema
                :description (when-not (str/blank? remarks)
                               remarks)}))))))

;; You may want to exclude a specific database - this can be done here
; (defmethod sql-jdbc.sync/excluded-schemas :athena [_]
;   #{"database_name"})

; If we want to limit the initial connection to a specific database/schema, I think we'd have to do that here...
(defmethod driver/describe-database :athena [driver database]
  {:tables (jdbc/with-db-metadata [metadata (sql-jdbc.conn/db->pooled-connection-spec database)]
             (fast-active-tables driver metadata))})

; Unsure if this is the right way to approach building the parameterized query...but it works
(defn- prepare-query [driver {:keys [database settings], query :native, :as outer-query}]
    (cond-> outer-query
        (seq (:params query))
        (merge {:native {:params nil
                :query (unprepare/unprepare driver (cons (:query query) (:params query)))}})))

(defmethod driver/execute-query :athena [driver query]
    (sql-jdbc.execute/execute-query driver (prepare-query driver, query)))