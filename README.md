# flex

flex is a library for building reactive computation graphs in Clojure(Script).
It gets its inspiration from [reagent](https://github.com/reagent-project/reagent)
and its lineage ([reflex](https://github.com/lynaghk/reflex), KnockoutJS).

## Install

Using git deps

```clojure
town.lilac/flex {:git/url "https://github.com/lilactown/flex"
                 :git/sha "c0709c301f8855d138de451d81d453e53b3072b5"
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
(def prn-fx (flex/listen counter-sq prn))

;; start the listener
(def dispose (prn-fx))

(counter 1)
;; print: 1

(doseq [_ (range 5)]
  (counter inc))

;; print: 4
;; print: 9
;; print: 16
;; print: 25
;; print: 36

;; batch updates in transaction to avoid computing signals/effects until the end
(flex/batch
 (counter inc)
 (counter inc)
 (counter inc)
 (counter inc))

;; print: 100

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
- [x] Batching
- [x] Error handling
- [x] Async support on JS
- [ ] Multiplexing / multithreaded scheduling on JVM
- [ ] Babashka support

### Differences from reagent

#### Platform support

flex supports Clojure on the JVM. Reagent is ClojureScript (JS) only

#### Eager vs lazy

flex computes "live" signals eagerly after a source has changed, and uses a
topological ordering to ensure that calculations are only done once and avoid
glitches. Reagent propagates changes up the dependency chain and only
re-calculates when dereferenced, which can avoid unnecessary work in some
instances but can also lead to glitches.

#### Batching and errors

When inside of a batched transaction, flex does not compute any dependent
signals until the transaction has ended, even if the signal is explicitly
dereferenced. When reagent is batching updates, it will calculate the result of
a reaction with the latest value if it is dereferenced.

If an error occurs inside of a flex transaction, all changes are rolled back and
no signals are updated. If you are updating a group of ratoms in reagent, if any
error occurs in between updates then you can end up in a state where some of the
ratoms are up to date and others are not.

#### Scheduling

flex does all changes, computations and effects synchronously by default.
Reagent schedules some effects asynchronously using `requestAnimationFrame`.

#### Scope

flex only handles reactive computation graphs and has no external dependencies.
Reagent bundles together its reactive computations with additional functionality
to build web apps in the browser, and depends on React.js for this.

## License & Copyright

Copyright 2022 Will Acton. Released under the EPL 2.0 license
