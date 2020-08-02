(ns metabase.driver.athena-test
  (:require [clojure.test :refer :all]
            [metabase.driver.athena :refer [endpoint-for-region]]))

(deftest endpoint
  (testing "AWS Endpoint URL"
    (is (=
         ".amazonaws.com"
         (endpoint-for-region "us-east-1")))

    (is (=
         ".amazonaws.com"
         (endpoint-for-region "us-west-2")))

    (is (=
         ".amazonaws.com.cn"
         (endpoint-for-region "cn-north-1")))

    (is (=
         ".amazonaws.com.cn"
         (endpoint-for-region "cn-northwest-1")))))