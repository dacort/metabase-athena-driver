(ns metabase.driver.athena-test
  (:require [clojure.test :refer :all]
            [metabase.driver :as driver]
            [metabase.test.data :as data]
            [metabase.test.data
             [datasets :as datasets]]
            [metabase.test.util :as tu]
            [metabase.test.util
             [log :as tu.log]]))


(deftest can-connect-test
  (datasets/test-driver  :athena
     (letfn [(can-connect? [details]
        (driver/can-connect? :athena details))]
           (is (= true
                  (can-connect? (:details (data/db))))
               "can-connect? should return true for normal Athena details")
           (is (= false
                  (tu.log/suppress-output
                    (can-connect? (assoc (:details (data/db)) :db (tu/random-name)))))
               "can-connect? should return false for Athena databases that don't exist"))))