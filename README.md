# flex

flex is a library for building reactive computation graphs in Clojure(Script).
It gets its inspiration from [reagent](https://github.com/reagent-project/reagent)
and its lineage ([reflex](https://github.com/lynaghk/reflex), KnockoutJS).

## Install

Using git deps

```clojure
town.lilac/flex {:git/url "https://github.com/lilactown/flex"
                 :git/sha "2605b53e96b0fa9b1118892074272f66322bfa6c"}
```

## Example

```clojure
(ns my-app.counter
  "A simple reactive counter"
  (:require
   [town.lilac.flex :as flex]))

(def state (flex/source {:count 0}))

(def counter (flex/signal (:count @state)))

(def prn-fx (flex/effect (prn @counter)))

(def dispose (prn-fx))
;; print: 0

(state #(update % :count inc))
;; print: 1

(doseq [_ (range 5)]
  (state #(update % :count inc)))

;; print: 2
;; print: 3
;; print: 4
;; print: 5
;; print: 6

(dispose) ; stop the effect from running and any dependent signals calculating

(state {:count 0}) ; reset state

;; nothing is printed
```

## Features

- [x] JS and JVM platform support
- [x] Reactive sources, signals and effects (`town.lilac.flex`)
- [x] Functional transduce/transform/reduce (`town.lilac.flex.xform`)
- [x] Memoized signal functions (`town.lilac.flex.memo`)
- [x] `add-watch` & `remove-watch` support (`town.lilac.flex.watch`)
- [ ] Transactions
- [ ] Babashka support
- [ ] Multiplexing / multithreaded scheduling on JVM
- [ ] Async support on JS

## License & Copyright

Copyright 2022 Will Acton. Released under the EPL 2.0 license
