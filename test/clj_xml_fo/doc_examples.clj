(ns clj-xml-fo.doc-examples
  "Runs the code in doc/*.md, so the documentation cannot drift from the library.

  Every ```clojure block on a page is evaluated, in order, in a namespace of its
  own per page (clj-xml-fo.core is aliased as fo). The fence's info string can
  carry flags after the language:

    example=<id>  the block's value is an example: an FO element, a [:root ...]
                  document, or a vector of flow items. It is validated, rendered,
                  and has doc/examples/<id>.pdf and <id>.png
    lenient       validate the example with {:lenient? true}
    streaming     the example holds streams: validate while encoding instead
    invalid       the block's value must be invalid FO; nothing is rendered
    warns         FOP warnings are expected (e.g. content deliberately clipped)
    no-eval       show the code without running it

  A line `;; => <edn>` asserts that the code before it, back to the previous
  assertion, evaluates to that value.

    clojure -X:docs    writes every example's PDF and PNG under doc/examples"
  (:require [clj-xml-fo.core :as fo]
            [clj-xml-fo.impl.encode :as encode]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [malli.error :as me])
  (:import (java.awt Color RenderingHints)
           (java.awt.image BufferedImage)
           (java.io ByteArrayOutputStream File)
           (javax.imageio ImageIO)
           (org.apache.fop.apps FopFactory FOUserAgent)
           (org.apache.fop.events Event EventFormatter EventListener)))

(def doc-dir (io/file "doc"))
(def examples-dir (io/file doc-dir "examples"))

;; ---------------------------------------------------------------------------
;; Reading pages
;; ---------------------------------------------------------------------------

(defn pages
  "The markdown pages under doc/, sorted."
  []
  (sort-by #(.getName ^File %)
           (filter #(str/ends-with? (.getName ^File %) ".md") (.listFiles ^File doc-dir))))

(defn- parse-info [info]
  (let [[lang & flags] (str/split (str/trim info) #"\s+")]
    (reduce (fn [m flag]
              (if-some [[_ id] (re-matches #"example=(.+)" flag)]
                (assoc m :example id)
                (assoc m (keyword flag) true)))
            {:lang lang}
            flags)))

(defn code-blocks
  "Fenced code blocks of a markdown file: [{:info {...} :code s :line n}]."
  [^File file]
  (loop [[line & more] (str/split-lines (slurp file))
         n 1
         open nil
         blocks []]
    (cond
      (nil? line) blocks
      (and (nil? open) (str/starts-with? line "```") (< 3 (count (str/trim line))))
      (recur more (inc n) {:info (parse-info (subs (str/trim line) 3)) :line n :lines []} blocks)
      (and open (= "```" (str/trim line)))
      (recur more (inc n) nil (conj blocks (-> open
                                               (assoc :code (str/join "\n" (:lines open)))
                                               (dissoc :lines))))
      open (recur more (inc n) (update open :lines conj line) blocks)
      :else (recur more (inc n) nil blocks))))

;; ---------------------------------------------------------------------------
;; Evaluating
;; ---------------------------------------------------------------------------

(defn- page-ns [^File page]
  (let [sym (symbol (str "doc-page." (str/replace (.getName page) #"\.md$" "")))]
    (remove-ns sym)
    (let [n (create-ns sym)]
      (binding [*ns* n]
        (refer-clojure)
        (require '[clj-xml-fo.core :as fo]))
      n)))

(defn- eval-code
  "Evaluate code in ns, checking `;; => value` assertions. Returns the value of
  the last form."
  [n code where]
  (binding [*ns* n]
    (loop [[line & more] (str/split-lines code)
           pending []
           value nil]
      (let [run (fn [] (let [s (str/join "\n" pending)]
                         (if (str/blank? s) value
                             (let [v (load-string s)] (if (var? v) @v v)))))]
        (cond
          (nil? line) (run)
          :else
          (if-some [[_ expected] (re-matches #"\s*;; => (.*)" line)]
            (let [v (run)
                  want (edn/read-string expected)]
              (when (not= want v)
                (throw (ex-info (str where ": expected " (pr-str want) ", got " (pr-str v))
                                {:expected want :actual v})))
              (recur more [] v))
            (recur more (conj pending line) value)))))))

(defn examples
  "Run every page; returns the examples found:
  [{:id :page :line :info :value}]. Blocks without example= still run."
  ([] (mapcat examples (pages)))
  ([^File page]
   (let [n (page-ns page)]
     (into []
           (keep (fn [{:keys [info code line]}]
                   (when (and (= "clojure" (:lang info)) (not (:no-eval info)))
                     (let [where (str (.getName page) ":" line)
                           v (try (eval-code n code where)
                                  (catch Throwable t
                                    (throw (ex-info (str where ": " (ex-message t)) {:where where} t))))]
                       (when (or (:example info) (:invalid info))
                         {:id (:example info) :page (.getName page) :line line :info info :value v})))))
           (code-blocks page)))))

;; ---------------------------------------------------------------------------
;; Documents
;; ---------------------------------------------------------------------------

(def ^:private flow-tags
  (delay (:allowed (encode/element-info (fo/schema :flow)))))

(defn flow-items?
  "A vector of flow items rather than one element."
  [v]
  (and (vector? v) (seq v) (every? vector? v)))

(defn document
  "A renderable [:root ...] for an example value: documents as they are, flow
  content placed on an A5 preview page."
  [v]
  (let [items (cond
                (and (vector? v) (= :root (first v))) nil
                (flow-items? v) v
                (and (vector? v) (contains? @flow-tags (first v))) [v]
                :else (throw (ex-info "Example is neither a document nor flow content" {:value v})))]
    (if items
      [:root
       {:layout-master-set {:xml/value [[:simple-page-master {:master-name "preview"
                                                              :page-width  "148mm"
                                                              :page-height "210mm"
                                                              :margin      "10mm"
                                                              :region-body {}}]]}
        :page-sequence     [{:master-reference "preview"
                             :flow             {:flow-name "xsl-region-body" :xml/value items}}]}]
      v)))

(defn check-valid
  "nil when the example's validity is as its flags say, else a message."
  [{:keys [value info]}]
  (let [opts {:lenient? (boolean (:lenient info))}
        els (if (flow-items? value) value [value])]
    (cond
      (:streaming info) nil
      (:invalid info) (when (every? #(fo/valid? % opts) els)
                        "marked invalid but validates")
      :else (some (fn [el]
                    (when-some [e (fo/explain el opts)]
                      (str "invalid " (when (:lenient info) "(lenient) ")
                           (pr-str (me/humanize e)))))
                  els))))

;; ---------------------------------------------------------------------------
;; Rendering
;; ---------------------------------------------------------------------------

(def ^:private fop-factory
  ;; relative URIs in examples (images/...) resolve against doc/
  (delay (FopFactory/newInstance (.toURI doc-dir))))

(def ^:private benign-warning
  ;; Java2D's font setup has no bold Symbol or ZapfDingbats
  #"Font \"(Symbol|ZapfDingbats),.*\" not found")

(defn- user-agent ^FOUserAgent [events]
  (let [ua (.newFOUserAgent ^FopFactory @fop-factory)]
    (.addEventListener (.getEventBroadcaster ua)
                       (reify EventListener
                         (processEvent [_ e]
                           (let [sev (str (.getSeverity ^Event e))
                                 msg (EventFormatter/format e)]
                             (when-not (or (str/includes? sev "INFO")
                                           (re-find benign-warning msg))
                               (swap! events conj (str (last (str/split sev #":")) " " msg)))))))
    ua))

(defn render
  "Render an example's document with FOP. Returns {:events [...] :pdf bytes}."
  [{:keys [value info]}]
  (let [events (atom [])
        out (ByteArrayOutputStream.)]
    (fo/render! (document value) out {:user-agent (user-agent events)
                                      :validate?  (boolean (:streaming info))
                                      :lenient?   (boolean (:lenient info))})
    {:events @events :pdf (.toByteArray out)}))

(defn- page-images
  "Each page of the document as a BufferedImage, via FOP's PNG renderer."
  [doc ^File tmp-dir]
  (let [events (atom [])
        ua (user-agent events)
        first-page (io/file tmp-dir "page.png")]
    (.setTargetResolution ua (float 110))
    (.setOutputFile ua first-page)
    (with-open [o (io/output-stream first-page)]
      (fo/render! doc o {:mime "image/png" :user-agent ua}))
    (into [] (keep (fn [i]
                     (let [f (io/file tmp-dir (if (= 1 i) "page.png" (str "page" i ".png")))]
                       (when (.exists f) (ImageIO/read f)))))
          (range 1 100))))

(defn- content-bottom
  "Row below the lowest non-white pixel."
  [^BufferedImage img]
  (let [w (.getWidth img)]
    (loop [y (dec (.getHeight img))]
      (cond
        (neg? y) 0
        (some (fn [x] (not= 0xFFFFFF (bit-and 0xFFFFFF (.getRGB img x y)))) (range w)) (inc y)
        :else (recur (dec y))))))

(defn- compose
  "Pages side by side on a white canvas, each with a grey border. A single
  fragment page is cropped below its content."
  ^BufferedImage [pages crop?]
  (let [gap 16
        cropped? (and crop? (= 1 (count pages)))
        pages (if cropped?
                (let [^BufferedImage p (first pages)
                      h (min (.getHeight p) (+ (content-bottom p) 28))]
                  [(.getSubimage p 0 0 (.getWidth p) (max h 40))])
                (vec (take 6 pages)))
        w (+ (reduce + (map #(.getWidth ^BufferedImage %) pages)) (* gap (inc (count pages))))
        h (+ (apply max (map #(.getHeight ^BufferedImage %) pages)) (* 2 gap))
        ;; whole pages are shown smaller than cropped fragments
        scale (min 1.0 (/ 1400.0 w) (if cropped? 1.0 (/ 640.0 h)))
        canvas (BufferedImage. (int (* w scale)) (int (* h scale)) BufferedImage/TYPE_INT_RGB)
        g (.createGraphics canvas)]
    (doto g
      (.setRenderingHint RenderingHints/KEY_INTERPOLATION RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.setRenderingHint RenderingHints/KEY_RENDERING RenderingHints/VALUE_RENDER_QUALITY)
      (.scale scale scale)
      (.setColor (Color. 0xF2 0xF2 0xF2))
      (.fillRect 0 0 w h))
    (reduce (fn [x ^BufferedImage p]
              (.drawImage g p (int x) (int gap) nil)
              (.setColor g (Color. 0xB0 0xB0 0xB0))
              (.drawRect g (dec x) (dec gap) (inc (.getWidth p)) (inc (.getHeight p)))
              (+ x (.getWidth p) gap))
            gap
            pages)
    (.dispose g)
    canvas))

(defn- grey?
  "True when every pixel has r = g = b."
  [^BufferedImage img]
  (let [w (.getWidth img) h (.getHeight img)]
    (loop [y 0]
      (cond
        (= y h) true
        (loop [x 0]
          (cond (= x w) true
                (let [rgb (.getRGB img x y)
                      r (bit-and (bit-shift-right rgb 16) 0xFF)
                      g (bit-and (bit-shift-right rgb 8) 0xFF)
                      b (bit-and rgb 0xFF)]
                  (and (= r g) (= g b))) (recur (inc x))
                :else false))
        (recur (inc y))
        :else false))))

(defn- compact
  "Pure black-and-white screenshots are written as greyscale PNGs, a fraction
  of the size."
  ^BufferedImage [^BufferedImage img]
  (if (grey? img)
    (let [g (BufferedImage. (.getWidth img) (.getHeight img) BufferedImage/TYPE_BYTE_GRAY)]
      (doto (.createGraphics g) (.drawImage img 0 0 nil) (.dispose))
      g)
    img))

(defn write-example!
  "doc/examples/<id>.pdf and <id>.png for one example."
  [{:keys [id value] :as ex}]
  (let [{:keys [pdf events]} (render ex)
        tmp (doto (io/file "target" "doc-pages" id) (.mkdirs))]
    (when (seq events)
      (binding [*out* *err*] (println id events)))
    (io/copy pdf (io/file examples-dir (str id ".pdf")))
    (doseq [^File f (.listFiles tmp)] (.delete f))
    (let [doc (document value)
          fragment? (not= :root (first value))]
      (ImageIO/write (compact (compose (page-images doc tmp) fragment?)) "png"
                     (io/file examples-dir (str id ".png"))))))

;; ---------------------------------------------------------------------------
;; Element reference
;; ---------------------------------------------------------------------------

(def element-reference-file (io/file doc-dir "element-reference.md"))

(def ^:private fop-notes
  ;; From Apache FOP's compliance page, https://xmlgraphics.apache.org/fop/compliance.html
  {:float               "partial support: only `float` left or right; `clear` is ignored"
   :inline-container    "partial support: not all properties are implemented"
   :bidi-override       "supported, but `direction` and `unicode-bidi` are not"
   :multi-switch        "partial support, used with FOP's `fox:auto-toggle` extension"
   :multi-case          "partial support"
   :multi-toggle        "not supported by FOP"
   :multi-properties    "not supported by FOP"
   :multi-property-set  "not supported by FOP"
   :table-and-caption   "FOP 2.12 and later only; clj-xml-fo uses FOP 2.11"
   :table-caption       "FOP 2.12 and later only, within table-and-caption"})

(defn- code [x] (str "`" x "`"))

(defn- keys-cell [ks] (if (seq ks) (str/join ", " (map code (sort ks))) "–"))

(defn element-reference
  "Markdown for doc/element-reference.md, generated from the schema."
  []
  (let [infos (into (sorted-map)
                    (map (fn [t] [t (encode/element-info (fo/schema t))]))
                    fo/element-tags)
        kind (fn [{:keys [value children]}]
               (cond (= :hiccup value) :mixed
                     (= :elements value) :elements
                     (seq children) :keyed
                     :else :attributes))
        of-kind (fn [k] (filter (fn [[_ i]] (= k (kind i))) infos))
        name-cell (fn [t] (str (code t) (when (contains? fop-notes t) " ¹")))
        table (fn [header rows] (into [header (str/replace header #"[^|]+" "---")] rows))]
    (str/join
     "\n"
     (concat
      ["# Element reference"
       ""
       "<!-- Generated from the schema by `clojure -X:docs` (clj-xml-fo.doc-examples/element-reference). Do not edit. -->"
       ""
       (str "Every XSL-FO element in `fop.xsd`, grouped by how its content is written. "
            "See [data-model.md](data-model.md) for the rules. \"Required\" lists the keys an "
            "element must have. The attribute count is how many XSL-FO properties `fop.xsd` "
            "allows on it.")
       ""
       "## Mixed content: `:xml/value` is hiccup"
       ""
       "Strings and `[tag attrs? & children]`, in any order."
       ""]
      (table "| Element | Required | Attributes |"
             (for [[t i] (of-kind :mixed)]
               (str "| " (name-cell t) " | " (keys-cell (:required i)) " | " (count (:attrs i)) " |")))
      [""
       "## Element content: `:xml/value` is a vector of elements"
       ""
       "Each item is `[tag content-map]`. \"May contain\" lists the tags the schema allows directly."
       ""]
      (table "| Element | May contain | Required | Attributes |"
             (for [[t i] (of-kind :elements)]
               (str "| " (name-cell t) " | " (str/join ", " (map code (sort (:allowed i))))
                    " | " (keys-cell (:required i)) " | " (count (:attrs i)) " |")))
      [""
       "## Named children: keys"
       ""
       "Each child is a key whose value is the child's content map, or a vector of them where the child repeats."
       ""]
      (table "| Element | Children | Required | Attributes |"
             (for [[t i] (of-kind :keyed)]
               (str "| " (name-cell t) " | "
                    (str/join ", " (map (fn [[k many?]] (str (code k) (if many? " (vector)" " (map)"))) (:children i)))
                    " | " (keys-cell (:required i)) " | " (count (:attrs i)) " |")))
      [""
       "## Attributes only"
       ""
       "`[tag {attrs}]`; use `{}` when there are none."
       ""]
      (table "| Element | Required | Attributes |"
             (for [[t i] (of-kind :attributes)]
               (str "| " (name-cell t) " | " (keys-cell (:required i)) " | " (count (:attrs i)) " |")))
      [""
       "## ¹ FOP support"
       ""
       (str "The schema accepts these elements, but Apache FOP's "
            "[compliance page](https://xmlgraphics.apache.org/fop/compliance.html) limits them:")
       ""]
      (for [[t note] (sort-by key fop-notes)]
        (str "- " (code t) ": " note))
      [""
       (str "XSL 1.1 additions such as `bookmark-tree`, `page-number-citation-last`, "
            "`change-bar-begin` and `retrieve-table-marker` aren't in `fop.xsd`, "
            "so they can't be written as data here.")
       ""]))))

(defn write!
  "Exec fn for -X:docs: render every example's PDF and PNG into doc/examples,
  removing files no example owns, and regenerate element-reference.md."
  [_]
  (spit element-reference-file (element-reference))
  (println "wrote" (.getPath ^File element-reference-file))
  (.mkdirs examples-dir)
  (let [exs (remove #(-> % :info :invalid) (examples))
        ids (set (map :id exs))]
    (doseq [ex exs]
      (when-some [problem (check-valid ex)]
        (throw (ex-info (str (:page ex) ":" (:line ex) " " problem) {:id (:id ex)})))
      (write-example! ex)
      (println "wrote" (:id ex)))
    (doseq [^File f (.listFiles examples-dir)
            :let [id (str/replace (.getName f) #"\.(pdf|png)$" "")]
            :when (not (contains? ids id))]
      (println "removing" (.getName f))
      (.delete f))))
