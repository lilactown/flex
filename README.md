# flex

flex is a library for building computations using signals in Clojure(Script).
It gets its inspiration from [reagent](https://github.com/reagent-project/reagent)
and its lineage ([reflex](https://github.com/lynaghk/reflex), KnockoutJS,
[S.js](https://github.com/adamhaile/S)) as well as [SolidJS](https://www.solidjs.com/).

## Install

Using git deps

```clojure
town.lilac/flex {:git/url "https://github.com/lilactown/flex"
                 :git/sha "a17120b49f9b5efcc519f7ae2cc775527c2ece57"
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
(def counter-sq (flex/signal (* @counter @counter)))

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
- [x] `add-watch` & `remove-watch` support (`town.lilac.flex.atom`)
- [x] Batching
- [x] Error propagation to consumers
- [x] Async support on JS (`town.lilac.flex.promise`)
- [ ] Multiplexing / multithreaded scheduling on JVM

### Differences from reagent

#### Platform support

flex supports Clojure on the JVM. Reagent is ClojureScript (JS) only

#### Eager vs lazy

flex computes "live" signals eagerly after a source has changed, and uses a
topological ordering to ensure that calculations are only done once and avoid
glitches. Reagent propagates changes up the dependency chain and only
re-calculates when dereferenced, which can avoid unnecessary work in some
instances but can also lead to glitches.

#### Batching

When inside of a batched transaction, flex does not compute any dependent
signals until the transaction has ended, even if the signal is explicitly
dereferenced. When reagent is batching updates, it will calculate the result of
a reaction with the latest value if it is dereferenced.

#### Errors

If an error occurs inside of a flex transaction, all changes are rolled back and
no signals are updated. If you are updating a group of ratoms in reagent, if any
error occurs in between updates then you can end up in a state where some of the
ratoms are up to date and others are not.

If an error occurs in a flex signal, the error is propagated to consumers so
that it can be handled by an effect that has an `on-error` callback attached.
Reagent reactions do not propagate errors and simply fail silently, leaving your
app in a broken state if an exception is thrown.

#### Scheduling

flex does all changes, computations and effects synchronously by default.
Reagent schedules some effects asynchronously using `requestAnimationFrame`.

#### Nested effects

Effects can be nested within each other, and when the outer effect is disposed it
will dispose of all inner effects.

#### Scope

flex only handles reactive computations and has no external dependencies.
Reagent bundles together its reactive computations with additional functionality
to build web apps in the browser, and depends on React.js for this.

## License & Copyright

Copyright 2022 Will Acton. Released under the EPL 2.0 license
