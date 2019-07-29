(ns driver.hive-parser-test
  (:require [clojure.test :refer :all]
            [metabase.driver.hive-parser :refer [hive-schema->map]]))

(deftest parser
  (testing "Parse schema"
    (is (=
         {:customerid "string" :prime "boolean" :productid "int"}
         (hive-schema->map "struct<customerid:string,prime:boolean,productid:int>")))

    (is (=
         {:customerid "string" :m_field [{:key "string" :value "string"}]}
         (hive-schema->map "struct<customerid:string,m_field:map<string,string>>")))

    (is (=
         {:grouperrors "string" :grouperrorsorder [] :fielderrors {:reviewtext {:field "string"} :title {:field "string"}} :fielderrorsorder []}
         (hive-schema->map "struct<grouperrors:string,grouperrorsorder:array<string>,fielderrors:struct<reviewtext:struct<field:string>,title:struct<field:string>>,fielderrorsorder:array<string>>")))))