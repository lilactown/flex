(ns town.lilac.flex.xform-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.flex :as f]
   [town.lilac.flex.xform :as xf]))

(deftest transform
  (let [*calls (atom [])
        A (f/source 0)
        B (xf/filter even? A)
        C (f/signal (* @B @B))
        D (xf/map inc C)
        Z (f/effect [_] (swap! *calls conj @D))
        dispose (Z)]
    (is (= [1] @*calls))
    (A 1)
    (is (= [1] @*calls))
    (A 2)
    (is (= [1 5] @*calls))
    (A 3)
    (is (= [1 5] @*calls))
    (A 4)
    (is (= [1 5 17] @*calls))
    (dispose)
    (A 5)
    (A 6)
    (is (= [1 5 17] @*calls))))

(comment
  (t/run-tests))
