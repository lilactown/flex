(ns town.lilac.flex.atom
  (:require
   [town.lilac.flex :as flex])
  #?(:clj (:import [town.lilac.flex SyncSource])))

(deftype SyncAtomWrapper [^:volatile-mutable watchers
                          ^:volatile-mutable dispose
                          swapper
                          resetter
                          s]
  #?@(:clj
      (Object
       (finalize
        [_]
        (and dispose (dispose)))
       clojure.lang.IAtom
       (swap [_ f] (swapper f))
       (swap [_ f a] (swapper #(f % a)))
       (swap [_ f a b] (swapper #(f % a b)))
       (swap [_ f a b args] (swapper #(apply f % a b args)))
       (reset [_ x] (resetter x))
       (compareAndSet [_ o n] (resetter n) (= o n)))
      :cljs
      (IAtom
       ISwap
       (-swap! [_ f] (swapper f))
       (-swap! [_ f a] (swapper #(f % a)))
       (-swap! [_ f a b] (swapper #(f % a b)))
       (-swap! [_ f a b args] (swapper #(apply f % a b args)))
       IReset
       (-reset! [_ x] (resetter x))))
  flex/Disposable
  (-add-on-dispose [_ f] nil)
  (-dispose [_]
    (set! watchers nil)
    (and dispose (dispose)))
  #?(:clj clojure.lang.IRef :cljs IWatchable)
  (#?(:clj addWatch :cljs -add-watch) [this key f]
    (when (nil? dispose)
      (let [fx (flex/effect
                [prev]
                (doseq [[k f] (.-watchers this)]
                  (f k this prev @s))
                @s)]
        (set! dispose (fx))))
    (set! watchers (assoc watchers key f))
    this)
  (#?(:clj removeWatch :cljs -remove-watch) [this key]
    (set! watchers (dissoc watchers key))
    this)
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [this]
    (when (nil? dispose)
      (let [fx (flex/effect
                [prev]
                (doseq [[k f] (.-watchers this)]
                  (f k this prev @s))
                @s)]
        (set! dispose (fx))))
    @s))

(defn watch
  "Returns a reactive source that watches the atom `iref` and updates its value
  when it changes."
  [iref]
  (let [s (flex/source @iref)
        k (gensym "flex")]
    (add-watch iref k (fn [_ _ _ v] (s v)))
    s))

;; TODO add tests for GC
(defn of
  "Returns a wrapper around a reactive object `s` that implements the Atom
  interface. On first deref/add-watch, will construct an effect that will update
  the atom until the atom is GC'd.

  Optionally takes a map with the following:
  `:swap` - function to call when `swap!` is called to update upstream state
  `:reset` - function to call when `reset!` is called to update upstream state"
  ([s] (of s nil))
  ([s {:keys [swap reset]}]
   (let [updater (when (instance?
                        #?(:clj SyncSource :cljs flex/SyncSource)
                        s)
                   s)
         wrapper (->SyncAtomWrapper
                  {} nil (or swap updater) (or reset updater) s)]
     wrapper)))
