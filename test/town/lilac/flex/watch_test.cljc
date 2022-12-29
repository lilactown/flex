(ns town.lilac.flex.watch-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.flex :as f]
   [town.lilac.flex.watch :as watch]))


(deftest watcher
  (let [*calls (atom [])
        A (f/source 0)
        W (watch/watcher A)]
    (add-watch W :t (fn [k o p v]
                        (swap! *calls conj [k p v])))
    (A 1)
    (A 2)
    (A 3)
    (remove-watch W :t)
    (A 4)
    (A 5)
    (is (= [[:t 0 1]
            [:t 1 2]
            [:t 2 3]]
           @*calls))))

(comment
  (t/run-tests))
