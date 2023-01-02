(ns town.lilac.flex.promise
  (:require
   [town.lilac.flex :as flex]))

(defrecord Resource [state error value loading? fetcher ^:volatile-mutable p]
  IDeref
  (-deref [_] @value)
  IFn
  (-invoke [this]
    (case @state
      (:unresolved :ready :error)
      (set! p (.then (fetcher)
                     (fn [x]
                       (flex/batch
                        (state :ready)
                        (value x)))
                     (fn [e]
                       (flex/batch
                        (state :error)
                        (error e)))))
      nil)
    (case @state
      :unresolved (state :pending)
      (:ready :error) (state :refreshing)
      nil)
    this))

(defn resource
  "Returns a resource record, with the following properties:
  - Calling like a function will call the `fetcher`, a function that returns a
  promise, updating the state of the resource as it proceeds.
  - Derefing the resource acts like a `source` and will produce the last value
    retrieved by `fetcher`

  The resource also contains the following keys:
  `:state` - a source containing one of :unresolved, :pending, :ready,
             :refreshing, :error
  `:error` - a source containing last error from `fetcher`
  `:value` - a source containing last value retrieved by `fetcher`
  `:loading?` - a signal containing true/false whether currently waiting for a
                promise returned by `fetcher` to fulfill
  `:fetcher` - the original `fetcher` function
  `:p` - the last promise returned by `fetcher`"
  [fetcher]
  (let [state (flex/source :unresolved)
        error (flex/source nil)
        value (flex/source nil)
        loading? (flex/signal
                  (case @state
                    (:pending :refreshing) true
                    false))]
    ((->Resource state error value loading? fetcher nil))))
