(ns metabase.driver.query-processor
  (:require [clojure.string :as str]
            [metabase.query-processor
             [store :as qp.store]]
            [metabase.util
             [honeysql-extensions :as hx]]
            [metabase.driver.sql.query-processor :as sql.qp]))

(defn handle-parent-identifier [field-identifier]
  (->
    field-identifier
    (name)
    (str/split #"\.")))

(defn format-field-identifier[field-identifier]
  (->>
    field-identifier
    (apply hx/qualify-and-escape-dots )
    (keyword)))

(defn get-field-identifier [field]
  (let [table            (qp.store/table (:table_id field))
        parent-id        (:parent_id field)]
    (if (nil? parent-id)
      [(:schema table) (:name table) (:name field)]
      (conj (handle-parent-identifier (sql.qp/->honeysql :athena [:field-id parent-id])) (:name field)))))

(defn ->honeysql [driver field]
  (->>
    (get-field-identifier field)
    (format-field-identifier)
    (sql.qp/cast-unix-timestamp-field-if-needed driver field)))