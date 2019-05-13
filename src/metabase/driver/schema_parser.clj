(ns metabase.driver.schema-parser
  (:require [metabase.driver.hive-parser :as hsp]))


(defn- column->base-type [column-type]
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
    :struct     :type/Dictionary
    :timestamp  :type/DateTime
    :tinyint    :type/Integer
    :varchar    :type/Text} (keyword (re-find #"\w+" column-type))))

(defn- create-nested-fields [schema]
  (set (map (fn [[k v]]
              (let [root {:name (name k)
                          :base-type (cond (map? v) :type/Dictionary
                                           (sequential? v) :type/Array
                                           :else (column->base-type v))
                          :database-type (cond (map? v) "map"
                                               (sequential? v) "array"
                                               :else  v)}]
                (cond
                  (map? v) (assoc root :nested-fields (create-nested-fields v))
                  :else root)))
            schema)))

(defn- remove-root-field [root-field schema]
  (let [root-field-kw (keyword root-field)]
    (cond
      (sequential? schema) schema
      (contains? schema root-field-kw) (schema root-field-kw)
      :else schema)))

(defn- parse-struct-type-field [field-info]
  (let [root-field-name (:name field-info)
        schema (remove-root-field root-field-name (hsp/hive-schema->map (:type field-info)))]
    {:name root-field-name
     :base-type :type/Dictionary
     :database-type "struct"
     :nested-fields (create-nested-fields schema)}))

(defn- parse-array-type-field [field-info]
  (let [root-field-name (:name field-info)
        schema (hsp/hive-schema->map (:type field-info))]
    {:name root-field-name :base-type :type/Array :database-type "array"}))

(defn- is-struct-type-field? [field-info]
  (clojure.string/starts-with? (:type field-info) "struct"))

(defn- is-array-type-field? [field-info]
  (clojure.string/starts-with? (:type field-info) "array"))

(defn parse-schema
  "Parse specific Athena types"
  [field-info]
  (cond
    ; :TODO Should we also validate maps?
    (is-struct-type-field? field-info) (parse-struct-type-field field-info)
    (is-array-type-field? field-info) (parse-array-type-field field-info)
    :else {:name (:name field-info) :base-type (column->base-type (:type field-info)) :database-type (:type field-info)}))
