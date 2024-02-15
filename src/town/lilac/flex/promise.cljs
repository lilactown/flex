(ns town.lilac.flex.promise
  (:require
   [town.lilac.flex :as flex]))

(defn fetcher-fn [state error value fetcher & args]
  (flex/untrack
   (case @state
     (:unresolved :ready :error) (.then (apply fetcher args)
                                        (fn [x]
                                          (flex/batch
                                           (state :ready)
                                           (value x)))
                                        (fn [e]
                                          (flex/batch
                                           (state :error)
                                           (error e))))
     nil)
   (case @state
     :unresolved (state :pending)
     (:ready :error) (state :refreshing)
     nil)))

(defrecord Resource [state error value loading? fetcher ^:volatile-mutable pr]
  IDeref
  (-deref [_] @value)
  IFn
  (-invoke [this]
    (set! pr (fetcher-fn state error value fetcher)) this)
  (-invoke [this a]
    (set! pr (fetcher-fn state error value fetcher a)) this)
  (-invoke [this a b]
    (set! pr (fetcher-fn state error value fetcher a b)) this)
  (-invoke [this a b c]
    (set! pr (fetcher-fn state error value fetcher a b c)) this)
  (-invoke [this a b c d]
    (set! pr (fetcher-fn state error value fetcher a b c d)) this)
  (-invoke [this a b c d e]
    (set! pr (fetcher-fn state error value fetcher a b c d e)) this)
  (-invoke [this a b c d e f]
    (set! pr (fetcher-fn state error value fetcher a b c d e f)) this)
  (-invoke [this a b c d e f g]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g)) this)
  (-invoke [this a b c d e f g h]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h)) this)
  (-invoke [this a b c d e f g h i]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i)) this)
  (-invoke [this a b c d e f g h i j]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j)) this)
  (-invoke [this a b c d e f g h i j k]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k)) this)
  (-invoke [this a b c d e f g h i j k l]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l)) this)
  (-invoke [this a b c d e f g h i j k l m]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m)) this)
  (-invoke [this a b c d e f g h i j k l m n]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n)) this)
  (-invoke [this a b c d e f g h i j k l m n o]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n o)) this)
  (-invoke [this a b c d e f g h i j k l m n o p]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n o p)) this)
  (-invoke [this a b c d e f g h i j k l m n o p q]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n o p q)) this)
  (-invoke [this a b c d e f g h i j k l m n o p q r]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n o p q r)) this)
  (-invoke [this a b c d e f g h i j k l m n o p q r s]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n o p q r s)) this)
  (-invoke [this a b c d e f g h i j k l m n o p q r s t]
    (set! pr (fetcher-fn state error value fetcher a b c d e f g h i j k l m n o p q r s t)) this))

(defn resource
  "Returns a flex source that updates its state based on a promise-returning
  function. Calling the source like a function will execute the `fetcher`,
  a function that returns a promise, updating the state of the resource as it
  proceeds. Derefing will return the last value retrieved by `fetcher`.

  The return value is also an associative containing the following keys:
  `:state` - a source containing one of :unresolved, :pending, :ready,
             :refreshing, :error
  `:error` - a source containing last error from `fetcher`
  `:value` - a source containing last value retrieved by `fetcher`
  `:loading?` - a signal containing true/false whether currently waiting for a
                promise returned by `fetcher` to fulfill
  `:fetcher` - the original `fetcher` function
  `:pr` - the last promise returned by `fetcher`"
  [fetcher]
  (let [state (flex/source :unresolved)
        error (flex/source nil)
        value (flex/source nil)
        loading? (flex/signal
                  (case @state
                    (:pending :refreshing) true
                    false))]
    (->Resource state error value loading? fetcher nil)))
