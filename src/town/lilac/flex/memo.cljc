(ns town.lilac.flex.memo
  (:require
   [town.lilac.flex :as flex])
  (:refer-clojure :exclude [memoize]))

(defn memoize
  [f]
  (let [*cache (volatile! {})]
    (fn [& args]
      (or (get @*cache args)
          (let [s (apply f args)]
            (vswap! *cache assoc args s)
            (flex/on-dispose s (fn [_] (vswap! *cache dissoc args))))))))
