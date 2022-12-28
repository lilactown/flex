(ns town.lilac.flex
  (:require
   [clojure.set :as set])
  #?(:cljs (:require-macros [town.lilac.flex]))
  (:refer-clojure :exclude [run!]))

(defprotocol Source
  (-send [src x] "Send a message to be handled by the source"))

(declare send!)

(defprotocol Sink
  (-run! [sink] "Run the side effect of a sink"))

(defprotocol Signal
  (-propagate [s]))

(defprotocol Reactive
  (-connect [o dep])
  (-disconnect [o dep])
  (-touch [o]))

(defprotocol Ordered
  (-get-order [o]))

(defprotocol Disposable
  (-dispose [o])
  (-add-on-dispose [o f]))

(defprotocol Debug
  (dump [o]))

(def ^:dynamic *reactive* nil)

(defn- heap
  ([xs]
   (heap (sorted-map) xs))
  ([heap xs]
   (reduce
    (fn [m x]
      (update
       m
       (-get-order x)
       (fnil conj #{})
       x))
    heap
    xs)))

(deftype SyncSource [^:volatile-mutable value
                     ^:volatile-mutable dependents
                     order]
  Debug
  (dump [_]
    {:value value :dependents dependents})
  Reactive
  (-connect [_ dep]
    (set! dependents (conj dependents dep)))
  (-disconnect [_ dep]
    (set! dependents (disj dependents dep)))
  (-touch [_] value)
  Ordered
  (-get-order [_] order)
  Source
  (-send [_ x]
    (if (fn? x) ; TODO handle multimethods
      (set! value (x value))
      (set! value x))
    dependents)
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [this]
    (when (some? *reactive*)
      (set! *reactive* (conj *reactive* this)))
    value)
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [this x] (send! this x))
  #?@(:clj ((applyTo [this args]
                     (when (not= 1 (count args))
                       (throw (ex-info "Invalid arity" {:args args})))
                     (send! this (first args))))))

(deftype SyncEffect [^:volatile-mutable dependencies
                     ^:volatile-mutable cleanup
                     ^:volatile-mutable order
                     f]
  Debug
  (dump [_]
    {:dependencies dependencies :cleanup cleanup :order order :f f})
  Signal
  (-propagate [this] (-run! this) nil)
  Ordered
  (-get-order [_] order)
  Disposable
  (-dispose [this]
    (and (fn? cleanup) (cleanup))
    (set! (.-cleanup this) nil)
    (doseq [dep dependencies]
      (-disconnect dep this))
    (set! (.-dependencies this) nil))
  Sink
  (-run! [this]
    (binding [*reactive* #{}]
      (set! cleanup (f cleanup))
      (doseq [dep (set/difference dependencies *reactive*)]
        (-disconnect dep this))
      (doseq [dep (set/difference *reactive* dependencies)]
        (-connect dep this))
      (set! dependencies *reactive*)
      (set! order (inc (apply max (map #(-get-order %) *reactive*))))
      (fn dispose [] (-dispose this))))
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [this] (-run! this))
  #?@(:clj ((applyTo [this args]
                     (when (pos? (count args))
                       (throw (ex-info "Invalid arity" {:args args})))
                     (-run! this)))))

(def sentinel ::init)

(deftype SyncSignal [^:volatile-mutable cache
                     ^:volatile-mutable dependents
                     ^:volatile-mutable dependencies
                     ^:volatile-mutable on-dispose-fns
                     ^:volatile-mutable order
                     f]
  Debug
  (dump [_]
    {:cache cache
     :dependents dependents
     :dependencies dependencies
     :order order
     :f f})
  Reactive
  (-connect [_ dep]
    (set! dependents (conj dependents dep)))
  (-disconnect [this dep]
    (set! dependents (disj dependents dep))
    (when (empty? dependents)
      (-dispose this)))
  (-touch [this]
    (when (= sentinel cache)
      ;; track dependencies
      (binding [*reactive* #{}]
        ;; https://clojure.atlassian.net/browse/CLJ-2743
        (set! (.-cache this) (f))
        (set! (.-dependencies this) *reactive*)
        (set! (.-order this) (inc (apply max (map #(-get-order %) *reactive*))))
        (doseq [dep *reactive*]
          (-connect dep this))))
    cache)
  Ordered
  (-get-order [_] order)
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [this]
    (if (some? *reactive*)
      (do (set! *reactive* (conj *reactive* this))
          (-touch this))
      cache))
  Signal
  (-propagate [this]
    (binding [*reactive* #{}]
      (let [newv (f)]
        (doseq [dep (set/difference dependencies *reactive*)]
          (-disconnect dep this))

        (doseq [dep (set/difference *reactive* dependencies)]
          (-connect dep this))
        (set! dependencies *reactive*)
        ;; only return dependents and set cache if value is different
        ;; aka cutoff
        (when (not= cache newv)
          (set! cache newv)
          dependents))))
  Disposable
  (-dispose [this]
    (doseq [f on-dispose-fns]
      (f cache))
    ;; completely disconnect, allow GC
    (doseq [deps dependencies]
      (-disconnect deps this))
    (set! dependencies nil)
    (set! cache sentinel))
  (-add-on-dispose [_ f]
    (set! on-dispose-fns (conj on-dispose-fns f))))

(defn source
  [initial]
  (->SyncSource initial #{} 0))

(defn create-signal
  [f]
  (->SyncSignal sentinel #{} #{} [] nil f))

(defn create-effect
  [f]
  (->SyncEffect #{} nil nil f))

(defmacro signal
  [& body]
  `(create-signal (fn [] ~@body)))

(defmacro effect
  [& body]
  `(create-effect (fn ~@body)))

(defn send!
  [src x]
  (loop [deps (heap (-send src x))]
    (when-let [[order next-deps] (first deps)]
      ;; TODO handle recursive
      ;; guaranteed to always have a bigger order in deps
      (recur (heap (dissoc deps order)
                   (reduce (fn [dependents dep]
                             (into dependents (-propagate dep)))
                           #{}
                           next-deps))))))

(defn run!
  [effect]
  (-run! effect))

(defn on-dispose
  [s f]
  (-add-on-dispose s f)
  s)
