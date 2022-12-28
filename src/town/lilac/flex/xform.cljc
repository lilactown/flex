(ns town.lilac.flex.xform
  (:require
   [town.lilac.flex :as flex])
  (:refer-clojure :exclude [transduce]))

(deftype SyncSignalTransduction [^:volatile-mutable cache
                                 ^:volatile-mutable dependents
                                 dependency
                                 ^:volatile-mutable on-dispose-fns
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
        dependents))))

(defn transduce
  [xform rf init s]
  (->SyncSignalTransduction flex/sentinel #{} s [] nil init xform rf nil))

(defn transform
  [xform s]
  (transduce xform (fn [_ x] x) nil s))

(defn collect
  [coll xform s]
  (transduce xform (fn [acc x] (conj acc x)) coll s))