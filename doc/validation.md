# Validation

clj-xml-fo validates data with a [malli](https://github.com/metosin/malli)
schema generated from Apache FOP's `fop.xsd`. Validation catches misspelt
attributes, children in the wrong place, and values the XSD's grammar
rejects. These would otherwise be dropped silently by the encoder or reported
late by FOP.

Every `;; =>` on this page is checked by the docs test suite.

```clojure
(require '[malli.core :as m]
         '[malli.error :as me])
```

## `valid?`, `explain`, `validate`

All three take any element `[tag content]` and check it against the schema for
its own tag. That includes a whole `[:root ...]` document or a single
`[:table ...]`.

```clojure
(fo/valid? [:block {:font-size "12pt" :xml/value ["ok"]}])
;; => true

(fo/valid? [:table-row {:table-cell [{:xml/value [[:block {}]]}]}])
;; => true

(fo/valid? [:block {:font-size 12}])
;; => false

(fo/valid? [:blok {}])
;; => false

(fo/valid? 42)
;; => false
```

`explain` returns a malli explanation, or nil. `malli.error/humanize` turns it
into data shaped like the input. An element `[tag content]` humanizes to
`[nil {...}]`, with the errors on the content:

```clojure
(me/humanize (fo/explain [:block {:font-wieght "bold"}]))
;; => [nil {:font-wieght ["disallowed key"]}]

(me/humanize (fo/explain [:root {}]))
;; => [nil {:layout-master-set ["missing required key"], :page-sequence ["missing required key"]}]
```

Errors deep in a document come back at their path:

```clojure
(def doc
  [:root {:layout-master-set {:xml/value [[:simple-page-master {:master-name "p" :region-body {}}]]}
          :page-sequence     [{:master-reference "p"
                               :flow {:flow-name "xsl-region-body"
                                      :xml/value [[:block {:xml/value ["fine"]}]
                                                  [:block {:colour "red" :xml/value ["typo"]}]]}}]}])

(fo/valid? doc)
;; => false

(-> (me/humanize (fo/explain doc)) (get-in [1 :page-sequence 0 :flow :xml/value 1]))
;; => [nil {:colour ["disallowed key"]}]
```

`validate` returns its argument when valid, and throws `ex-info` otherwise.
The ex-data holds `:type`, `:tag`, the humanized `:errors` and the malli
`:explanation`:

```clojure
(try (fo/validate [:block {:color "blurple" :xml/value ["x"]}])
     (catch clojure.lang.ExceptionInfo e
       (select-keys (ex-data e) [:type :tag])))
;; => {:type :clj-xml-fo.core/invalid, :tag :block}
```

## Strict and lenient

By default, attribute values must match `fop.xsd`. The XSD is narrower than
FOP: it has no grammar for property expressions, and some of its patterns are
pickier than the XSL-FO spec. With `{:lenient? true}`, attribute values only need
to be strings. Element structure and attribute names are still checked.

| Value | Strict | Lenient | Strict-valid alternative |
|---|---|---|---|
| `:end-indent "label-end()"` | ✗ | ✓ | a plain length, see [lists.md](lists.md#without-the-functions) |
| `:color "#ff0000"` | ✗ | ✓ | `"#FF0000"`, or a colour name |
| `:color "rgb(255,0,0)"` | ✗ | ✓ | `"#FF0000"` |
| `:space-before "0"` | ✗ | ✓ | `"0pt"` |
| `:border "1pt solid gray"` | ✗ | ✓ | `"solid 1pt gray"` |
| `:border-bottom "1pt solid gray"` | ✗ | ✓ | `"thin solid gray"`, or the longhands |

Each row is checked here:

```clojure
(defn strict+lenient [el] [(fo/valid? el) (fo/valid? el {:lenient? true})])

(strict+lenient [:list-item-label {:end-indent "label-end()" :xml/value [[:block {}]]}])
;; => [false true]
(strict+lenient [:block {:color "#ff0000"}])
;; => [false true]
(strict+lenient [:block {:color "#FF0000"}])
;; => [true true]
(strict+lenient [:block {:color "rgb(255,0,0)"}])
;; => [false true]
(strict+lenient [:block {:space-before "0"}])
;; => [false true]
(strict+lenient [:block {:space-before "0pt"}])
;; => [true true]
(strict+lenient [:block {:border "1pt solid gray"}])
;; => [false true]
(strict+lenient [:block {:border "solid 1pt gray"}])
;; => [true true]
(strict+lenient [:block {:border-bottom "1pt solid gray"}])
;; => [false true]
(strict+lenient [:block {:border-bottom "thin solid gray"}])
;; => [true true]
```

Lenient still rejects unknown attributes, wrong structure and non-strings:

```clojure
(fo/valid? [:block {:fnot-size "12pt"}] {:lenient? true})
;; => false
(fo/valid? [:block {:font-size 12}] {:lenient? true})
;; => false
(fo/valid? [:list-block {:list-item {}}] {:lenient? true})
;; => false
```

## What validation can't see

Mixed content, the `:xml/value` of `block`, `inline` and the like, is hiccup.
The schema only checks that it is a vector of children, so a typo inside it
passes:

```clojure
(fo/valid? [:block {:xml/value [[:inline {:fnot-size "9pt"} "typo inside hiccup"]]}])
;; => true
```

FOP checks the structure of everything it receives, and fails on elements in
the wrong place. One mistake it catches that malli can't is a `[tag content-map]`
element, the form for element content, placed inside hiccup. The map would be
read as attributes. Inside mixed content, write `[:block attrs & children]`.

## Validating while encoding

`encode!`, `xml-string`, `write-xml!` and `render!` take `:validate? true`, plus
`:lenient?`. A fully built document is checked before anything is written, so
FOP never sees invalid input:

```clojure
(try (fo/xml-string [:block {:font-wieght "bold" :xml/value ["x"]}] {:validate? true})
     (catch clojure.lang.ExceptionInfo e (:errors (ex-data e))))
;; => [nil {:font-wieght ["disallowed key"]}]
```

Without it, the encoder writes only the keys the schema knows, so the typo
silently disappears:

```clojure
(fo/xml-string [:block {:font-wieght "bold" :xml/value ["x"]}])
;; => "<?xml version=\"1.0\" encoding=\"UTF-8\"?><fo:block xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">x</fo:block>"
```

For documents that hold streams, validation happens item by item as they are
encoded (see [streaming.md](streaming.md)). Don't call `valid?` on them: malli
would consume the streams.

## Schemas in your own specs

`fo/schema` returns the malli schema for an element, and `fo/registry` the whole
registry. Every element is registered as `:clj-xml-fo.element/<tag>`, so your
own schemas can require FO data:

```clojure
(m/form (fo/schema :table))
;; => :clj-xml-fo.element/table

(def Letter
  [:map
   [:recipient :string]
   [:body [:vector :clj-xml-fo.element/block]]])

(m/validate Letter
            {:recipient "J. Smith"
             :body [[:block {:xml/value ["Dear J. Smith,"]}]
                    [:block {:space-before "6pt" :xml/value ["Thank you for your order."]}]]}
            {:registry (fo/registry)})
;; => true

(m/validate Letter {:recipient "J. Smith" :body [[:blok {}]]} {:registry (fo/registry)})
;; => false
```

`(fo/schema)` with no tag accepts any element. `(fo/registry {:lenient? true})`
is the lenient registry.
