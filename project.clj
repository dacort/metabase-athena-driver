(defproject metabase/athena-driver "1.0.0-athena-jdbc-2.0.13"
  :min-lein-version "2.5.0"

  ; Run `make download-jar` first, to initialize in-project maven repo and download third-party jar.
  :repositories [["athena" {:url "file:maven_repository"}]]

  :dependencies
  [[athena/athena-jdbc "2.0.13"]]

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