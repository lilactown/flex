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

;; a state container that can be changed manually
(def counter (flex/source 0))

;; a computation that updates when its dependencies change
(def counter-sq (flex/signal (* @state @state)))

;; an effect that runs side effects when its dependencies change
(def prn-fx (flex/effect (prn @counter-sq)))

(def dispose (prn-fx))
;; print: 0

(counter 1)
;; print: 1

(doseq [_ (range 5)]
  (counter inc))

;; print: 4
;; print: 9
;; print: 16
;; print: 25
;; print: 36

(dispose) ; stop the effect from running and any dependent signals calculating

(counter inc)

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
