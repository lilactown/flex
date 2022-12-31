(ns town.lilac.flex.atom-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.flex :as f]
   [town.lilac.flex.atom :as atom]))


(deftest of
  (testing "source"
    (let [*calls (atom [])
          A (f/source 0)
          W (atom/of A)]
      (add-watch W :t (fn [k o p v]
                        (swap! *calls conj [k p v])))
      (A 1)
      (is (= 1 @W))
      (A 2)
      (reset! W 3)
      (remove-watch W :t)
      (A 4)
      (A 5)
      (is (= [[:t 0 1]
              [:t 1 2]
              [:t 2 3]]
             @*calls))))
  (testing "signal"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (inc @A))
          W (atom/of B)]
      (add-watch W :t (fn [k o p v]
                        (swap! *calls conj [k p v])))
      (A 1)
      (is (= 2 @W))
      (A 2)
      (is (thrown? #?(:clj NullPointerException :cljs js/Error) (reset! W 3)))
      (A 3)
      (remove-watch W :t)
      (A 4)
      (A 5)
      (is (= [[:t 1 2]
              [:t 2 3]
              [:t 3 4]]
             @*calls)))))

(deftest watch
  (let [*calls (atom [])
        *source (atom 0)
        A (atom/watch *source)
        B (f/signal (inc @A))
        Z (f/effect [_] (swap! *calls conj @B))
        dispose (Z)]
    (is (= 0 @A))
    (is (= [1] @*calls))
    (swap! *source inc)
    (is (= 1 @A))
    (is (= [1 2] @*calls))))

(comment
  (t/run-tests))
