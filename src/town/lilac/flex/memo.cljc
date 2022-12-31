(ns town.lilac.flex.memo
  (:require
   [town.lilac.flex :as flex])
  (:refer-clojure :exclude [memoize]))

(defn memoize
  "Takes a function `f` which returns a signal. Returns a new function that when
  called looks up in a cache whether a signal was previously constructed using
  the same args. If it has, and that signal has not been disposed, it will
  return the previous signal.

  When the signal is disposed, it evicts itself from the cache."
  [f]
  ;; TODO LRU
  (let [*cache (volatile! {})]
    (fn [& args]
      (or (get @*cache args)
          (let [s (apply f args)]
            (vswap! *cache assoc args s)
            (flex/on-dispose s (fn [_] (vswap! *cache dissoc args))))))))
