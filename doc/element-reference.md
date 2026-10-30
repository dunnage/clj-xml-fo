# Element reference

<!-- Generated from the schema by `clojure -X:docs` (clj-xml-fo.doc-examples/element-reference). Do not edit. -->

Every XSL-FO element in `fop.xsd`, grouped by how its content is written. See [data-model.md](data-model.md) for the rules. "Required" lists the keys an element must have. The attribute count is how many XSL-FO properties `fop.xsd` allows on it.

## Mixed content: `:xml/value` is hiccup

Strings and `[tag attrs? & children]`, in any order.

| Element | Required | Attributes |
|---|---|---|
| `:basic-link` | – | 214 |
| `:bidi-override` ¹ | – | 111 |
| `:block` | – | 208 |
| `:inline` | – | 217 |
| `:leader` | – | 209 |
| `:marker` | – | 84 |
| `:multi-case` ¹ | – | 89 |
| `:multi-toggle` ¹ | – | 88 |
| `:title` | – | 193 |
| `:wrapper` | – | 84 |

## Element content: `:xml/value` is a vector of elements

Each item is `[tag content-map]`. "May contain" lists the tags the schema allows directly.

| Element | May contain | Required | Attributes |
|---|---|---|---|
| `:block-container` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 197 |
| `:declarations` | `:color-profile` | `:xml/value` | 83 |
| `:float` ¹ | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 85 |
| `:flow` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:flow-name`, `:xml/value` | 84 |
| `:footnote-body` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 85 |
| `:inline-container` ¹ | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 196 |
| `:layout-master-set` | `:page-sequence-master`, `:simple-page-master` | `:xml/value` | 83 |
| `:list-item-body` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 89 |
| `:list-item-label` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 89 |
| `:page-sequence-master` | `:repeatable-page-master-alternatives`, `:repeatable-page-master-reference`, `:single-page-master-reference` | `:master-name`, `:xml/value` | 84 |
| `:static-content` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:flow-name`, `:xml/value` | 84 |
| `:table-body` | `:table-cell`, `:table-row` | `:xml/value` | 162 |
| `:table-caption` ¹ | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 193 |
| `:table-cell` | `:block`, `:block-container`, `:float`, `:footnote`, `:list-block`, `:marker`, `:multi-properties`, `:multi-switch`, `:retrieve-marker`, `:table`, `:table-and-caption`, `:wrapper` | `:xml/value` | 203 |
| `:table-footer` | `:table-cell`, `:table-row` | `:xml/value` | 162 |
| `:table-header` | `:table-cell`, `:table-row` | `:xml/value` | 162 |

## Named children: keys

Each child is a key whose value is the child's content map, or a vector of them where the child repeats.

| Element | Children | Required | Attributes |
|---|---|---|---|
| `:footnote` | `:inline` (map), `:footnote-body` (map) | `:footnote-body`, `:inline` | 85 |
| `:list-block` | `:list-item` (vector) | `:list-item` | 206 |
| `:list-item` | `:list-item-label` (map), `:list-item-body` (map) | `:list-item-body`, `:list-item-label` | 206 |
| `:multi-properties` ¹ | `:multi-property-set` (vector), `:wrapper` (map) | `:multi-property-set`, `:wrapper` | 86 |
| `:multi-switch` ¹ | `:multi-case` (map) | `:multi-case` | 86 |
| `:page-sequence` | `:title` (map), `:static-content` (vector), `:flow` (map) | `:flow`, `:master-reference` | 91 |
| `:repeatable-page-master-alternatives` | `:conditional-page-master-reference` (vector) | `:conditional-page-master-reference` | 84 |
| `:root` | `:layout-master-set` (map), `:declarations` (map), `:page-sequence` (vector) | `:layout-master-set`, `:page-sequence` | 84 |
| `:simple-page-master` | `:region-body` (map), `:region-before` (map), `:region-after` (map), `:region-start` (map), `:region-end` (map) | `:master-name`, `:region-body` | 102 |
| `:table` | `:table-column` (vector), `:table-header` (map), `:table-footer` (map), `:table-body` (vector) | `:table-body` | 222 |
| `:table-and-caption` ¹ | `:table-caption` (map), `:table` (map) | `:table` | 205 |
| `:table-row` | `:table-cell` (vector) | `:table-cell` | 173 |

## Attributes only

`[tag {attrs}]`; use `{}` when there are none.

| Element | Required | Attributes |
|---|---|---|
| `:character` | – | 211 |
| `:color-profile` | `:color-profile-name`, `:src` | 86 |
| `:conditional-page-master-reference` | `:master-reference` | 87 |
| `:external-graphic` | `:src` | 224 |
| `:initial-property-set` | – | 185 |
| `:instream-foreign-object` | – | 223 |
| `:multi-property-set` ¹ | `:active-state` | 85 |
| `:page-number` | – | 210 |
| `:page-number-citation` | `:ref-id` | 211 |
| `:region-after` | – | 160 |
| `:region-before` | – | 160 |
| `:region-body` | – | 176 |
| `:region-end` | – | 160 |
| `:region-start` | – | 160 |
| `:repeatable-page-master-reference` | `:master-reference` | 85 |
| `:retrieve-marker` | – | 86 |
| `:single-page-master-reference` | `:master-reference` | 84 |
| `:table-column` | – | 138 |

## ¹ FOP support

The schema accepts these elements, but Apache FOP's [compliance page](https://xmlgraphics.apache.org/fop/compliance.html) limits them:

- `:bidi-override`: supported, but `direction` and `unicode-bidi` are not
- `:float`: partial support: only `float` left or right; `clear` is ignored
- `:inline-container`: partial support: not all properties are implemented
- `:multi-case`: partial support
- `:multi-properties`: not supported by FOP
- `:multi-property-set`: not supported by FOP
- `:multi-switch`: partial support, used with FOP's `fox:auto-toggle` extension
- `:multi-toggle`: not supported by FOP
- `:table-and-caption`: FOP 2.12 and later only; clj-xml-fo uses FOP 2.11
- `:table-caption`: FOP 2.12 and later only, within table-and-caption

XSL 1.1 additions such as `bookmark-tree`, `page-number-citation-last`, `change-bar-begin` and `retrieve-table-marker` aren't in `fop.xsd`, so they can't be written as data here.
