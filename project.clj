(defproject metabase/athena-driver "1.0.1-athena-jdbc-2.0.13"
  :min-lein-version "2.5.0"

  :dependencies
  [[athena/athena-jdbc "2.0.13"]]

;   :repositories
;  [["athena" {:url "https://s3.amazonaws.com/athena-downloads/drivers/JDBC/SimbaAthenaJDBC_2.0.13/AthenaJDBC42_2.0.13.jar"}]]
; TODO: Download from source URL
; For now, you have to download the jar above into ~/.m2/repository/athena/athena-jdbc/2.0.13/athena-jdbc-2.0.13.jar

  :aliases
  {"test"       ["with-profile" "+unit_tests" "test"]}

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure "1.10.0"]
     [metabase-core "1.0.0-SNAPSHOT"]]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "athena.metabase-driver.jar"}

   :unit_tests
   {:test-paths     ^:replace ["test_unit"]}})
