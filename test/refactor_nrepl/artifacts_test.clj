(ns refactor-nrepl.artifacts-test
  (:require [clojure
             [edn :as edn]
             [test :refer :all]]
            [clojure.java.io :as io]
            [refactor-nrepl.artifacts :as artifacts]))

(def clojure-versions (-> (io/resource "resources/clojure-versions.edn")
                          slurp
                          edn/read-string))
(def sorted-clojure-versions
  (vector "1.7.0-alpha1"
          "1.6.0" "1.6.0-RC1" "1.6.0-beta1" "1.6.0-alpha1"
          "1.5.1" "1.5.0"))
(def clojure-artifacts ["clojure"])
(def clojars-artifacts (-> (io/resource "resources/clojars-artifacts.edn")
                           slurp
                           edn/read-string))

(deftest creates-a-map-of-artifacts
  (reset! artifacts/artifacts {})
  (with-redefs
    [artifacts/get-artifacts-from-clojars! (constantly clojars-artifacts)
     artifacts/get-mvn-artifacts! (constantly clojure-artifacts)
     artifacts/get-versions! (constantly clojure-versions)]

    (is (#'artifacts/stale-cache?))

    (#'artifacts/update-artifact-cache!)

    (is (not (#'artifacts/stale-cache?)))

    (testing "Contains clojure with correct versions"
      (is (contains? @artifacts/artifacts "org.clojure/clojure"))
      (is (= (count (@artifacts/artifacts "org.clojure/clojure"))
             (count clojure-versions))))

    (testing "Contains artifacts from clojars"
      (is (contains? @artifacts/artifacts "alembic"))
      (is (some #{"0.3.1"} (get-in @artifacts/artifacts ["alembic"]))))

    (testing "Sorts all the versions"
      (reset! artifacts/artifacts {"org.clojure/clojure" clojure-versions})
      (is (= sorted-clojure-versions
             (artifacts/artifact-versions {:artifact "org.clojure/clojure"}))))))

(deftest hotload-dependency-throws-exceptions
  (reset! artifacts/artifacts {"prismatic/schema" ["0.1"]})
  (with-redefs
    [artifacts/make-resolve-missing-aware-of-new-deps (fn [& _])
     artifacts/stale-cache? (constantly false)
     artifacts/distill (constantly true)]
    (testing "Throws for non existing version"
      (is (thrown? IllegalArgumentException
                   (artifacts/hotload-dependency
                    {:coordinates "[prismatic/schema \"1.0\"]"}))))
    (testing "Throws for non existing artifact"
      (is (thrown? IllegalArgumentException
                   (artifacts/hotload-dependency
                    {:coordinates "[imaginary \"1.0\"]"}))))
    (testing "No exception when all is OK"
      (is (artifacts/hotload-dependency {:coordinates "[prismatic/schema \"0.1\"]"})))))
