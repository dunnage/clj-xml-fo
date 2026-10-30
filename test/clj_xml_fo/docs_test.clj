(ns clj-xml-fo.docs-test
  "Every example in doc/*.md runs, validates, renders through FOP without
  errors, and has its PDF and screenshot. See clj-xml-fo.doc-examples."
  (:require [clj-xml-fo.doc-examples :as docs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private all-examples (delay (docs/examples)))

(deftest examples-run-validate-and-render
  (doseq [{:keys [id page line info] :as ex} @all-examples]
    (testing (str page ":" line " " (or id "(invalid example)"))
      (is (nil? (docs/check-valid ex)))
      (when-not (:invalid info)
        (let [{:keys [events pdf]} (docs/render ex)]
          (if (:warns info)
            (is (seq events) "marked warns, but FOP reported nothing")
            (is (empty? events)))
          (is (< 500 (count pdf))))))))

(deftest every-example-has-its-files
  (let [ids (keep :id @all-examples)]
    (is (= (count ids) (count (set ids))) "example ids are unique")
    (doseq [{:keys [id page]} @all-examples
            :when id
            :let [md (slurp (io/file docs/doc-dir page))]]
      (testing id
        (doseq [ext ["pdf" "png"]]
          (is (.exists (io/file docs/examples-dir (str id "." ext)))
              (str "run clojure -X:docs to write examples/" id "." ext))
          (is (str/includes? md (str "(examples/" id "." ext ")"))
              (str page " links examples/" id "." ext)))))))

(deftest element-reference-is-current
  (is (= (docs/element-reference) (slurp docs/element-reference-file))
      "run clojure -X:docs to regenerate doc/element-reference.md"))

(deftest every-page-is-in-the-index
  (let [index (slurp (io/file docs/doc-dir "README.md"))]
    (doseq [^java.io.File page (docs/pages)
            :let [n (.getName page)]
            :when (not= "README.md" n)]
      (is (str/includes? index (str "(" n ")")) (str "README.md links " n)))))
