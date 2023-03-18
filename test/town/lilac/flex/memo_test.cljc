(ns town.lilac.flex.memo-test
  (:require
   [clojure.test :as t :refer [deftest is]]
   [town.lilac.flex :as f]
   [town.lilac.flex.memo :as memo]))

(deftest memoize-test
  (let [A (f/source 0)
        factory (memo/memoize (fn [x] (f/signal (+ x @A))))
        B (factory 1)
        B' (factory 1)
        C (factory 2)
        Z (f/effect [] (+ @B @B' @C))
        dispose (Z)]
    (is (= B B'))
    (is (= 1 @B @B'))
    (is (not= B C))
    (is (= 2 @C))
    (A 1)
    (is (= 2 @B @B'))
    (is (= 3 @C))
    (dispose)
    (let [B'' (factory 1)
          C' (factory 2)]
      (is (not= B B''))
      (is (not= C C')))))

(comment
  (t/run-tests))
