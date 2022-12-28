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
  "Creates a container with a value inside. `deref` it to get the value, and
  call it like a function to change it.

  (def src (source 0))
  @src ;; => 0
  (src 1)
  @src ;; => 1

  A function can be passed to operate on the current value without dereferencing.

  (src inc)
  @src ;; => 2

  When reactive exprs created with `signal` and `effect` dereference it, changes
  will be propagated to them."
  [initial]
  (->SyncSource initial #{} 0))

(defn create-signal
  "Creates a reactive signal object. Used by `signal`."
  [f]
  (->SyncSignal sentinel #{} #{} [] nil f))

(defn create-effect
  "Creates a reactive effect object. Used by `effect`."
  [f]
  (->SyncEffect #{} nil nil f))

(defmacro signal
  "Creates a reactive signal object which yields the return value of the body
  when dereferenced inside another reactive expression, e.g. `signal` or
  `effect`.

  Any signals or sources dereferenced inside the body will propagate their
  changes to this signal, which will propagate on to its dependents.

  If the same value (using =) is returned by the body, stops propagation."
  [& body]
  `(create-signal (fn [] ~@body)))

(defmacro effect
  "Creates a reactive effect object, which is meant to do side effects based on
  changes to signals and sources.

  Returns a function that when called executes the body and connects any
  reactive objects dereferenced in the body, so that they start computing and
  reacting to upstream changes.

  Calling the function returns \"dispose\" function which when called will stop
  the effect from continuing to execute, and cleans up any signals that are
  solely referenced by the effect."
  [& body]
  `(create-effect (fn ~@body)))

(defn send!
  "Sends a value to a source. Same as calling it like a function."
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
  "Starts an effect. Same as calling it like a function."
  [effect]
  (-run! effect))

(defn on-dispose
  "Adds a callback function to a signal to be called when the signal is no
  longer listened to by any other signal or effect and is cleaned up."
  [s f]
  (-add-on-dispose s f)
  s)
