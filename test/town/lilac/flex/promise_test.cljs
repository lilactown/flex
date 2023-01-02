(ns town.lilac.flex.promise-test
  (:require
   [clojure.test :as t :refer [async deftest is testing]]
   [town.lilac.flex :as f]
   [town.lilac.flex.async :as a]))

(defn sleep
  [ms]
  (js/Promise. (fn [res _rej]
                 (js/setTimeout
                  (fn [] (res))
                  ms))))

(deftest resource
  (async
   done
   (let [r (a/resource (fn []
                         (-> (sleep 100)
                             (.then (constantly 42)))))
         *calls (atom [])
         s (f/signal (inc @(:value r)))
         fx (f/effect [_] (swap! *calls conj @s))
         dispose (fx)]
     (is (= :unresolved @(:state r)))
     (is (= nil @(:value r)))
     (is (= nil @(:error r)))
     (is (= [1] @*calls))
     (r)
     (is (= :pending @(:state r)))
     (-> (sleep 101)
         (.then (fn []
                  (is (= :ready @(:state r)))
                  (is (= 42 @(:value r)))
                  (is (= [1 43] @*calls))))
         (.then done done)))))

(comment
  (t/run-tests))
