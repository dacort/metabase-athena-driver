(ns metabase.driver.athena-test
  (:require [clojure.test :refer :all]
            [metabase.driver :as driver]
            [metabase.test :as mt]
            [metabase.test.data :as data]
            [metabase.test.data
             [datasets :as datasets]]
            [metabase.test.util :as tu]
            [metabase.test.util
             [log :as tu.log]]
            [medley.core :as m]
            [metabase.query-processor :as qp]))

(deftest can-connect-test
  (datasets/test-driver  :athena
                         (letfn [(can-connect? [details]
                                   (driver/can-connect? :athena details))]
                           (is (= true
                                  (can-connect? (:details (data/db))))
                               "can-connect? should return true for normal Athena details")
                           (is (= true
                                  (tu.log/suppress-output
                                   (can-connect? (assoc (:details (data/db)) :db (tu/random-name)))))
                               "can-connect? should still return true for Athena databases that don't exist"))))

(defn long-str [& strings] (clojure.string/join "\n" strings))

(def ^:private ^String relative-date-query
  (long-str "SELECT 0 AS id, 'damon' AS name, timestamp '2006-01-02 15:04:05.123' AS timestamp"
            "UNION ALL"
            "SELECT 1, 'bob', timestamp '2006-01-02 15:04:05.123' - INTERVAL '2' DAY"
            "ORDER BY id ASC"))

(def ^:private ^String datetime-types-query
  (long-str "SELECT"
            "    DATE '2006-01-02' as type_date,"
            "    TIME '15:04:05.000' as type_time,"
            "    TIME '15:04:05.000 America/Los_Angeles' AS type_time_with_timezone,"
            "    TIMESTAMP '2006-01-02 15:04:05.000' AS type_timestamp,"
            "    TIMESTAMP '2006-01-02 15:04:05.000 America/Los_Angeles' AS type_timestamp_with_timezone,"
            "    INTERVAL '3' MONTH AS type_interval_year_to_month,"
            "    INTERVAL '2' DAY AS type_interval_day_to_second"))

(defn- process-native-query [query]
  (driver/with-driver :athena
    (-> (qp/process-query {:native   {:query query}
                           :type     :native
                           :database (data/id)})
        (m/dissoc-in [:data :results_metadata])
        (m/dissoc-in [:data :insights]))))

;; test relative date query - see issue #26
(deftest athena-relative-date-query
  (mt/test-driver :athena
                  (is (= {:row_count         2
                          :status            :completed
                          :data              {:rows        [[0 "damon" "2006-01-02T15:04:05.123Z"]
                                                            [1 "bob"   "2005-12-31T15:04:05.123Z"]]
                                              :cols        [{:name         "id"
                                                             :source       :native
                                                             :display_name "id"
                                                             :field_ref    [:field-literal "id" :type/Integer]
                                                             :base_type     :type/Integer}
                                                            {:name         "name"
                                                             :source       :native
                                                             :display_name "name"
                                                             :field_ref    [:field-literal "name" :type/Text]
                                                             :base_type     :type/Text}
                                                            {:name         "timestamp"
                                                             :source       :native
                                                             :display_name "timestamp"
                                                             :field_ref    [:field-literal "timestamp" :type/DateTime]
                                                             :base_type     :type/DateTime}]
                                              :native_form       {:query relative-date-query}
                                              :results_timezone   "UTC"}}
                         (-> (process-native-query relative-date-query))))))

;; test that all date/datetime fields return values
(deftest athena-datetime-fields
  (mt/test-driver :athena
                  (is (= {:row_count     1
                          :status        :completed
                          :data          {:rows        [["2006-01-02T00:00:00Z" "15:04:05.000" "15:04:05.000 America/Los_Angeles" "2006-01-02T15:04:05Z" "2006-01-02 15:04:05.000 America/Los_Angeles" "0-3" "2 00:00:00.000"]]
                                          :cols        [{:name           "type_date"
                                                         :display_name   "type_date"
                                                         :base_type      :type/Date
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_date" :type/Date]}
                                                        {:name           "type_time"
                                                         :display_name   "type_time"
                                                         :base_type      :type/Text
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_time" :type/Text]}
                                                        {:name           "type_time_with_timezone"
                                                         :display_name   "type_time_with_timezone"
                                                         :base_type      :type/Text
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_time_with_timezone" :type/Text]}
                                                        {:name           "type_timestamp"
                                                         :display_name   "type_timestamp"
                                                         :base_type      :type/DateTime
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_timestamp" :type/DateTime]}
                                                        {:name           "type_timestamp_with_timezone"
                                                         :display_name   "type_timestamp_with_timezone"
                                                         :base_type      :type/Text
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_timestamp_with_timezone" :type/Text]}
                                                        {:name           "type_interval_year_to_month"
                                                         :display_name   "type_interval_year_to_month"
                                                         :base_type      :type/Text
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_interval_year_to_month" :type/Text]}
                                                        {:name           "type_interval_day_to_second"
                                                         :display_name   "type_interval_day_to_second"
                                                         :base_type      :type/Text
                                                         :source         :native
                                                         :field_ref      [:field-literal "type_interval_day_to_second" :type/Text]}]
                                          :native_form {:query datetime-types-query}
                                          :results_timezone    "UTC"}}
                         (-> (process-native-query datetime-types-query))))))

;(deftest start-of-week-test
;  (datasets/test-driver :athena
;                        (is (= [["2015-10-04" 9]]
;                               (druid-query-returning-rows
;                                 {:filter      [:between [:datetime-field $timestamp :day] "2015-10-04" "2015-10-10"]
;                                  :aggregation [[:count $id]]
;                                  :breakout    [[:datetime-field $timestamp :week]]}))
;                            (str "Count the number of events in the given week. Metabase uses Sunday as the start of the week, Druid by "
;                                 "default will use Monday. All of the below events should happen in one week. Using Druid's default "
;                                 "grouping, 3 of the events would have counted for the previous week."))))