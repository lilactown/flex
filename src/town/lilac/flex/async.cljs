(ns town.lilac.flex.async
  (:require
   [town.lilac.flex :as flex]))

(defrecord Resource [state error value loading? fetcher ^:volatile-mutable p]
  IFn
  (-invoke [this]
    (case @state
      (:unresolved :ready :error)
      (set! p (.then (fetcher)
                     (fn [x]
                       (flex/dosync
                        (state :ready)
                        (value x)))
                     (fn [e]
                       (flex/dosync
                        (state :error)
                        (error e)))))
      nil)
    (case @state
      :unresolved (state :pending)
      (:ready :error) (state :refreshing)
      nil)
    this))

(defn resource
  [fetcher]
  (let [state (flex/source :unresolved)
        error (flex/source nil)
        value (flex/source nil)
        loading? (flex/signal
                  (case @state
                    (:pending :refreshing) true
                    false))]
    (->Resource state error value loading? fetcher nil)))
