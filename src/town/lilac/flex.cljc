(ns town.lilac.flex
  (:require
   [clojure.set :as set])
  #?(:cljs (:require-macros [town.lilac.flex]))
  (:refer-clojure :exclude [run!]))

(defprotocol Source
  (-send [src x] "Send a message to be handled by the source")
  (-commit [src tx])
  (-discard [src tx])
  (-rebase [src parent-id child-id]))

(declare send!)

(defprotocol Sink
  (-add-on-error [s f])
  (-run! [sink] "Run the side effect of a sink"))

(defprotocol Signal
  (-propagate [s]))

(defprotocol Reactive
  (-connect [o dep])
  (-disconnect [o dep])
  (-touch [o]))

(defprotocol Ordered
  (-get-order [o])
  (-get-id [o]))

(defprotocol Disposable
  (-dispose [o])
  (-add-on-dispose [o f]))

(defprotocol Debug
  (dump [o]))

(def ^:dynamic *reactive* nil)
(def ^:dynamic *cleanup* nil)

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

(def *tx-id (volatile! 0))
(def *reactive-counter (volatile! 0))
(def ^:dynamic *current-tx* {})
(def sentinel ::init)

(deftype SyncSource [^:volatile-mutable value
                     ^:volatile-mutable dependents
                     ^:volatile-mutable txs
                     ^:volatile-mutable commit]
  Debug
  (dump [_]
    {:value value :dependents dependents :order 0 :txs txs :commit commit})
  Reactive
  (-connect [_ dep]
    (set! dependents (conj dependents dep)))
  (-disconnect [_ dep]
    (set! dependents (disj dependents dep)))
  (-touch [_] value)
  Ordered
  (-get-order [_] 0)
  Source
  (-send [this x]
    (if-let [tx-id (:id *current-tx*)]
      (do
        (when-not (contains? txs tx-id)
          ;; initialize value with either parent tx or current val
          (if-let [parent-id (:parent *current-tx*)]
            (set! txs (assoc txs tx-id (get txs parent-id)))
            (set! txs (assoc txs tx-id value))))
        (if (fn? x) ; TODO handle multimethods
            (set! txs (update txs tx-id x))
            (set! txs (assoc txs tx-id x)))
          (set! *current-tx* (update *current-tx* :dirty conj this))
          tx-id)
      (throw (ex-info "Sent value outside of transaction"
                      {:value x :tx *current-tx*}))))
  (-discard [_ tx-id]
    (set! txs (dissoc txs tx-id)))
  (-commit [_ tx-id]
    (set! value (get txs tx-id))
    (set! txs (dissoc txs tx-id))
    ;; TODO ensure that tx-id is monotonic
    (set! commit tx-id)
    dependents)
  (-rebase [_ parent-id child-id]
    (set! txs (assoc txs parent-id (get txs child-id))))
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [this]
    (when (some? *reactive*)
      (set! *reactive* (conj *reactive* this)))
    (if-let [tx-id (:id *current-tx*)]
      (if (contains? txs tx-id) (get txs tx-id) value)
      value))
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [this x]
    (if (:id *current-tx*)
      (get txs (-send this x))
      (send! this x)))
  #?@(:clj ((applyTo [this args]
                     (when (not= 1 (count args))
                       (throw (ex-info "Invalid arity" {:this this :args args})))
                     (this (first args))))))

(deftype SyncListener [^:volatile-mutable order
                       ^:volatile-mutable on-error-fns
                       id dep f]
  Debug
  (dump [_]
    {:order order :id id :dep dep :f f})
  Signal
  (-propagate [_]
    (f @dep)
    (set! order (inc (-get-order dep)))
    nil)
  Ordered
  (-get-order [_] order)
  (-get-id [_] id)
  Disposable
  (-dispose [this]
    (-disconnect dep this))
  Sink
  (-add-on-error [_ f] (set! on-error-fns (conj on-error-fns f)))
  (-run! [this]
    (try
      (-touch dep)
      (catch #?(:clj Throwable :cljs js/Object) e
        (doseq [on-error on-error-fns]
          (on-error e))))
    (set! order (inc (-get-order dep)))
    (-connect dep this)
    (fn dispose [] (-dispose this)))
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [this] (-run! this))
  #?@(:clj ((applyTo [this args]
                     (when (pos? (count args))
                       (throw (ex-info "Invalid arity" {:args args})))
                     (-run! this)))))

(deftype SyncEffect [^:volatile-mutable dependencies
                     ^:volatile-mutable prev
                     ^:volatile-mutable order
                     ^:volatile-mutable cleanup
                     ^:volatile-mutable on-error-fns
                     id
                     f
                     arity]
  Debug
  (dump [_]
    {:dependencies dependencies :prev prev :order order :id id :f f})
  Signal
  (-propagate [this] (-run! this) nil)
  Ordered
  (-get-order [_] order)
  (-get-id [_] id)
  Disposable
  (-dispose [this]
    (and (fn? prev) (prev))
    (set! prev nil)
    (doseq [dep dependencies]
      (-disconnect dep this))
    (set! dependencies nil)
    (doseq [fx cleanup]
      (-dispose fx)))
  Sink
  (-add-on-error [_ f] (set! on-error-fns (conj on-error-fns f)))
  (-run! [this]
    (when (some? *cleanup*)
      (set! *cleanup* (conj *cleanup* this)))
    (binding [*reactive* #{}
              *cleanup* #{}]
      (try
        (case arity
          0 (set! (.-prev this) (f))
          1 (set! (.-prev this) (f (if (= sentinel prev) nil prev)))
          :multi (set! (.-prev this)
                       (if (= sentinel prev)
                         (f)
                         (f prev))))
        (doseq [dep (set/difference dependencies *reactive*)]
          (-disconnect dep this))
        (doseq [dep (set/difference *reactive* dependencies)]
          (-connect dep this))
        (set! (.-dependencies this) *reactive*)

        ;; child effects
        (doseq [dep (set/difference cleanup *cleanup*)]
          (-dispose dep))
        (set! (.-cleanup this) *cleanup*)
        (catch #?(:clj Throwable :cljs js/Object) e
          (doseq [on-error on-error-fns]
            (on-error e))))
      (set! order (inc (apply max (map #(-get-order %) dependencies))))
      this)))

(deftype SyncSignal [^:volatile-mutable cache
                     ^:volatile-mutable dependents
                     ^:volatile-mutable dependencies
                     ^:volatile-mutable error
                     ^:volatile-mutable on-dispose-fns
                     ^:volatile-mutable order
                     id
                     f]
  Debug
  (dump [_]
    {:cache cache
     :dependents dependents
     :dependencies dependencies
     :error error
     :order order
     :id id
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
        (try
          (set! (.-cache this) (f))
          (set! (.-dependencies this) *reactive*)
          (set! (.-order this) (inc (apply max (map #(-get-order %) *reactive*))))
          (doseq [dep *reactive*]
            (-connect dep this))
          (catch #?(:clj Throwable :cljs js/Object) e
            (set! (.-error e) e)))))
    (if (some? error)
      (throw error)
      cache))
  Ordered
  (-get-order [_] order)
  (-get-id [_] id)
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [this]
    (cond
      (some? *reactive*)
      (do (set! *reactive* (conj *reactive* this))
          (-touch this))
      (some? error) (throw error)
      :else cache))
  Signal
  (-propagate [this]
    (binding [*reactive* #{}]
      (set! error nil)
      (try
        (let [newv (f)]
         (doseq [dep (set/difference dependencies *reactive*)]
           (-disconnect dep this))

         (doseq [dep (set/difference *reactive* dependencies)]
           (-connect dep this))
         (set! dependencies *reactive*)
         (set! (.-order this) (inc (apply max (map #(-get-order %) *reactive*))))
         ;; only return dependents and set cache if value is different
         ;; aka cutoff
         (when (not= cache newv)
           (set! cache newv)
           dependents))
        (catch #?(:clj Throwable :cljs js/Object) e
          (set! error e)
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
  (->SyncSource initial #{} {} nil))

(defn create-signal
  "Creates a reactive signal object. Used by `signal`."
  [f]
  (->SyncSignal sentinel #{} #{} nil [] nil (vswap! *reactive-counter inc) f))

(defn create-effect
  "Creates a reactive effect object. Used by `effect`."
  [f arity]
  (-run! (->SyncEffect
          #{} sentinel nil #{} [] (vswap! *reactive-counter inc) f arity)))

(defn listen
  "Creates a reactive listener meant to do side effects. Given a signal `s`
  and a callback `f`, returns a listener object that when called like a function
  will call `f` anytime `s` changes. Returns a function that when called, stops
  listening."
  [s f]
  (->SyncListener nil [] (vswap! *reactive-counter inc) s f))

(defmacro signal
  "Creates a reactive memoized computation which yields the return value of the
  body when dereferenced inside another reactive expression, e.g. `signal` or
  `effect`.

  Any signals or sources dereferenced inside the body will propagate their
  changes to this signal, which will propagate on to its dependents.

  If the same value (using =) is returned by the body, stops propagation."
  [& body]
  `(create-signal (fn [] ~@body)))

(defmacro effect
  "Returns a reactive effect object, which is meant to do side effects based on
  changes to signals and sources. It immediately executes the body given and
  connects any reactive objects dereferenced in the body, so that they start
  computing and reacting to upstream changes.

  `body` is like a function that can take zero or one arguments. If zero
  arguments are accepted, the body will be called each time any dependencies
  change.

  ```
  (let [fx (effect [] ,,,)]
    ,,,)
  ```

  If one argument is accepted, the body will be called each time any
  dependencies change and the argument will equal the previous value returned by
  the body of the effect.

  ```
  (let [fx (effect [x] ,,,)]
    ,,,)
  ```

  If both zero and one arguments are accepted, the zero arity will be called on
  start and the one arity will be called any time the dependencies change with
  the value previously returned.

  ```
  (let [fx (effect ([] ,,,) ([x] ,,,))]
    ,,,)
  ```

  Calling the `dispose!` function will stop the effect from continuing to
  execute, and cleans up any signals that are solely referenced by the effect
  and any effects that were started inside of it. Call `run!` on it to restart
  it."
  [& body]
  (let [hd (first body)
        arity (cond
                ;; (effect [] ,,,) and (effect [x] ,,,)
                (vector? hd) (count hd)

                ;; (effect ([] ,,,) ([x] ,,,))
                (and (seq? hd) (seq? (second body))) :multi

                ;; (effect ([] ,,,)) and (effect ([x] ,,,))
                (seq? hd) (count (first hd))

                :else (throw (ex-info "effect macro unknown format"
                                       {:body body})))]
    `(create-effect (fn ~@body) ~arity)))

(defn run!
  [fx]
  (-run! fx))

(defn dispose!
  [fx]
  (-dispose fx))

(defn- sync!
  [deps]
  (loop [deps (heap deps)]
    (when-let [[order next-deps] (first deps)]
      (recur (heap (dissoc deps order)
                   (reduce (fn [dependents dep]
                             (into dependents
                                   (try
                                     (-propagate dep)
                                     (catch #?(:clj Throwable :cljs js/Object) e
                                       (throw e)))))
                           #{}
                           (sort-by #(-get-id %) next-deps)))))))

(def ^:dynamic *skip-updates* false)

(defn send!
  "Sends a value to a source. Same as calling it like a function, but is inside
  its own transaction."
  [src x]
  (cond
    (:id *current-tx*) (-send src x)
    *skip-updates* (binding [*current-tx* {:id (vswap! *tx-id inc)
                                           :dirty []}]
                     (-commit src (-send src x))) ; no `sync!`
    :else (sync! (binding [*current-tx* {:id (vswap! *tx-id inc)
                                         :dirty []}]
                   (-commit src (-send src x)))))
  @src)

(defn batch-send!
  "Calls `f` and batches all updates to sources together so that any dependent
  signals/effects are computed only after all changes have been made in.
  If an error occurs at any time during `body`, none of the changes will be
  applied."
  [f]
  (binding [*current-tx* {:id (vswap! *tx-id inc)
                          :parent (:id *current-tx*)
                          :dirty #{}}]
    (try (f)
         (catch #?(:clj Throwable :cljs js/Object) e
           (let [{:keys [id dirty]} *current-tx*]
             (doseq [src dirty]
               (-discard src id)))
           ;; TODO does this fuck with the stacktrace?
           (throw e)))
    (let [{:keys [id dirty parent]} *current-tx*]
      (if parent
        (doseq [src dirty] (-rebase src parent id))
        (sync! (mapcat #(-commit % id) dirty))))
    (:id *current-tx*)))

(defmacro batch
  "Batches all updates to sources together so that any dependent signals/effects
  are computed only after all changes have been made in `body`. If an error
  occurs at any time during `body`, none of the changes will be applied."
  [& body]
  `(batch-send! (fn [] ~@body)))

(defn on-dispose
  "Adds a callback function to a signal to be called when the signal is no
  longer listened to by any other signal or effect and is cleaned up."
  [s f]
  (-add-on-dispose s f)
  s)

(defn on-error
  "Adds a callback function to a signal to be called when the signal throws an
  error during computation."
  [s f]
  (-add-on-error s f)
  s)

(defmacro skip
  "Runs the body. Any updates to reactive sources will not trigger a
  recalculation."
  [& body]
  `(binding [*skip-updates* true]
     ~@body))

(defmacro untrack
  "Runs the body. If run in a reactive context (e.g. inside a `signal`), any
  reactive objects dereferenced will not be listened to and changes to them will
  not trigger updates."
  [& body]
  `(binding [*reactive* nil]
     ~@body))
