(ns town.lilac.flex.xform
  (:require
   [town.lilac.flex :as flex])
  (:refer-clojure :exclude [transduce reduce eduction]))

(deftype SyncSignalTransduction [^:volatile-mutable cache
                                 ^:volatile-mutable dependents
                                 dependency
                                 ^:volatile-mutable on-dispose-fns
                                 ^:volatile-mutable on-error-fns
                                 ^:volatile-mutable order
                                 init
                                 xform
                                 rf
                                 ^:volatile-mutable rf']
  flex/Debug
  (dump [_]
    {:cache cache
     :dependents dependents
     :dependency dependency
     :order order
     :xform xform
     :rf rf
     :rf' rf'})
  flex/Disposable
  (-add-on-dispose [_ f]
    (set! on-dispose-fns (conj on-dispose-fns f)))
  (-dispose [this]
    ;; completely disconnect, allow GC
    (flex/-disconnect dependency this)
    ;; reset cache and reducer
    (set! cache flex/sentinel)
    (set! rf' nil))
  flex/Reactive
  (-connect [_ dep]
    (set! dependents (conj dependents dep)))
  (-disconnect [this dep]
    (set! dependents (disj dependents dep))
    (when (empty? dependents)
      (flex/-dispose this)))
  (-touch [this]
    (when (= flex/sentinel cache)
      (set! rf' (xform rf))
      (set! cache (rf' init (flex/-touch dependency)))
      (set! order (inc (flex/-get-order dependency)))
      (flex/-connect dependency this))
    cache)
  flex/Ordered
  (-get-order [_] order)
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [this]
    (when (some? flex/*reactive*)
      (set! flex/*reactive* (conj flex/*reactive* this))
      (flex/-touch this))
    cache)
  flex/Signal
  (-propagate [_]
    (let [newv (rf' cache (flex/-touch dependency))]
      (when (not= cache newv)
        (set! cache newv)
        dependents)))
  (-add-on-error [this f]
    (set! on-error-fns (conj on-error-fns f))
    this)
  (-error [_ e]
    (doseq [f on-error-fns]
      (f e))))

(defn transduce
  "Returns a reactive signal which takes each value emitted by reactive obj `s`
  and computes a new value by calling `((xf rf) prev new)`.
  Works with all clojure.core transducers, including stateful ones.
  State is reset on disposal."
  [xform rf init s]
  (->SyncSignalTransduction flex/sentinel #{} s [] [] nil init xform rf nil))

(defn reduce
  "Returns a reactive signal which takes each value emitted by `s` and computes
  a new value by calling `(f prev new)`. State is reset on disposal."
  [f init s]
  (transduce identity f init s))

(defn eduction
  "Returns a reactive signal that uses the transducer `xform` on each value
  produced by reactive obj `s`, emitting the latest value and discarding
  previous. State is reset on disposal."
  [xform s]
  (transduce xform (fn [_ x] x) nil s))

(defn collect
  "Takes an initial `coll`, an `xform` and a reactive obj `s`.
  Returns a reactive signal that `conj`s each value emitted by `s` on to `coll`.
  A transducer may be applied. State is reset on disposal.

  Like `clojure.core/into` for reactive signals."
  ([coll s] (collect coll identity s))
  ([coll xform s]
   (transduce xform conj coll s)))

(defn sliding
  "Takes a max window size `n` and signal `s` and returns a reactive signal that
  produces a sequence of the last `n` values emitted by `s`, with the latest
  being the first value in the sequence. State is reset on disposal."
  [n s]
  (reduce (fn [acc x]
            (conj (take (dec n) acc) x))
          ()
          s))
