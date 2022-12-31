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
      (clojure.lang.IAtom
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
  (#?(:clj removeWatch :cljs -remove-watch) [_ key]
    (set! watchers (dissoc watchers key))
    (when (empty? watchers)
      (dispose)
      (set! dispose nil)))

  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [_]
    (if (instance? #?(:clj SyncSource :cljs flex/SyncSource) s)
      @s
      (flex/-touch s))))

(defn watch
  "Returns a reactive source that watches the atom `iref` and updates its value
  when it changes."
  [iref]
  (let [s (flex/source @iref)
        k (gensym "flex")]
    (add-watch iref k (fn [_ _ _ v] (s v)))
    s))

(defn of
  "Returns a wrapper around a reactive object `s` that implements the Atom
  interface. It lazily constructs an effect on first watch that is disposed when
  the last watcher is removed."
  ([s] (let [updater (when (instance?
                            #?(:clj SyncSource :cljs flex/SyncSource)
                            s)
                       s)]
         (of s updater updater)))
  ([s swapper resetter]
   (->SyncAtomWrapper {} nil swapper resetter s)))
