(ns town.lilac.flex-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.flex :as f]))


(deftest linear
  (let [*calls (atom [])
        A (f/source 2)
        B (f/signal (* @A @A))
        Z (f/effect [_] (swap! *calls conj @B))]
    (is (= 2 @A))
    (is (= f/sentinel @B))
    (let [dispose (Z)]
      (is (= 2 @A))
      (is (= 4 @B))
      (is (= [4] @*calls))
      (A 3)
      (is (= 3 @A))
      (is (= 9 @B))
      (is (= [4 9] @*calls))
      (dispose)
      (A 4)
      (is (= 4 @A))
      (is (= f/sentinel @B))
      (is (= [4 9] @*calls)))))

(deftest dirty-diamond
  (let [*calls (atom [])
        A (f/source 2)
        B (f/signal (* @A @A))
        C (f/signal (+ @A 2))
        D (f/signal (* @C @C))
        Z (f/effect [_] (swap! *calls conj [@B @D]))
        dispose (Z)]
    (is (= 2 @A))
    (is (= 4 @B))
    (is (= 4 @C))
    (is (= 16 @D))
    (is (= [[4 16]] @*calls))
    (A 3)
    (is (= 3 @A))
    (is (= 9 @B))
    (is (= 5 @C))
    (is (= 25 @D))
    (is (= [[4 16] [9 25]] @*calls))
    (dispose)
    (A 4)
    (is (= 4 @A))
    (is (= f/sentinel @B))
    (is (= f/sentinel @C))
    (is (= f/sentinel @D))
    (is (= [[4 16] [9 25]] @*calls))))

(deftest effect-cleanup
  (let [*calls (atom [])
        *cleanup-calls (atom 0)
        A (f/source 2)
        Z (f/effect
           [cleanup]
           (swap! *calls conj @A)
           ;; first cleanup is nil
           (and cleanup (cleanup))
           #(swap! *cleanup-calls inc))
        dispose (Z)]
    (is (= [2] @*calls))
    (is (= 0 @*cleanup-calls))
    (A 3)
    (is (= [2 3] @*calls))
    (is (= 1 @*cleanup-calls))
    (dispose)
    (is (= [2 3] @*calls))
    (is (= 2 @*cleanup-calls))))

(deftest on-dispose
  (let [*calls (atom [])
        *disposed (atom 0)
        A (f/source 0)
        B (f/on-dispose (f/signal (* @A @A)) (fn [_] (swap! *disposed inc)))
        Z (f/effect [_] (swap! *calls conj @B))
        dispose (Z)]
    (is (= 0 @*disposed))
    (is (= [0] @*calls))
    (A 2)
    (is (= 0 @*disposed))
    (is (= [0 4] @*calls))
    (dispose)
    (is (= 1 @*disposed))
    (is (= [0 4] @*calls))
    (let [dispose (Z)]
      (is (= 1 @*disposed))
      (is (= [0 4 4] @*calls))
      (dispose)
      (is (= 2 @*disposed))
      (is (= [0 4 4] @*calls)))))

(comment
  (t/run-tests))
