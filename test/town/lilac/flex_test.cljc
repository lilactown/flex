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

(deftest conditional
  (testing "conditional sources"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/source 10)
          C (f/source 100)
          D (f/signal (if (even? @A)
                        (inc @B)
                        (inc @C)))
          Z (f/effect [_] (swap! *calls conj @D))
          dispose (Z)]
      (is (= [11] @*calls))
      (B 20)
      (C 200)
      (is (= [11 21] @*calls))
      (A 1)
      (is (= [11 21 201] @*calls))
      (B 30)
      (C 300)
      (is (= [11 21 201 301] @*calls))
      (A 2)
      (B 40) (C 400)
      (is (= [11 21 201 301 31 41] @*calls))
      (dispose)
      (A 3)
      (B 50) (C 500)
      (is (= [11 21 201 301 31 41] @*calls))))
  (testing "conditional signals"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (* 10 @A))
          C (f/signal (* 100 @A))
          D (f/signal (if (even? @A)
                        (inc @B)
                        (inc @C)))
          Z (f/effect [_] (swap! *calls conj @D))
          dispose (Z)]
      (is (= [1] @*calls))
      (is (= f/sentinel @C))
      (A 1)
      (is (= [1 101] @*calls))
      (is (= f/sentinel @B))
      (A 2)
      (is (= [1 101 21] @*calls))
      (dispose)
      (is (= f/sentinel @B))
      (is (= f/sentinel @C))
      (is (= f/sentinel @D))
      (A 3)
      (is (= [1 101 21] @*calls))))
  (testing "order"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (+ @A 10))
          C (f/signal (- @B 10))
          D (f/signal (let [a @A]
                        (if (> a 0)
                          (let [c @C]
                            (+ a c))
                          a)))
          Z (f/effect [_] (swap! *calls conj @D))
          dispose (Z)]
      (is (= [0] @*calls))
      (A 1)
      (A 2)
      (is (= [0 2 4] @*calls)))))

(deftest transaction
  (let [*calls (atom [])
        A (f/source 0)
        B (f/source 0)
        Z (f/effect [_] (swap! *calls conj [@A @B]))
        dispose (Z)]
    (is (= [[0 0]] @*calls))
    (f/transact! (fn []
                   (A 1)
                   (is (= 1 @A))
                   (is (= 0 @B))
                   (B 1)
                   (is (= 1 @B))
                   (is (= 1 @A))
                   (A 2)
                   (is (= [[0 0]] @*calls))))
    (is (= [[0 0] [2 1]] @*calls)))
  (testing "exceptions"
    (let [*calls (atom [])
          A (f/source 1)
          B (f/source 1)
          C (f/signal (/ @A @B))
          Z (f/effect [_] (swap! *calls conj @C))
          dispose (Z)]
      (is (= [1] @*calls))
      (is (thrown? ArithmeticException
                   (f/transact! (fn []
                                  (A 2)
                                  (B 0)))))
      (is (= [1] @*calls))
      (f/transact! (fn []
                     (A 4)
                     (B 2)))
      (is (= [1 2] @*calls)))))

(comment
  (t/run-tests))
