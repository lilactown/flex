(ns town.lilac.flex.promise-test
  (:require
   [clojure.test :as t :refer [async deftest is]]
   [town.lilac.flex :as f]
   [town.lilac.flex.promise :as p]))

(defn sleep
  [ms]
  (js/Promise. (fn [res _rej]
                 (js/setTimeout
                  (fn [] (res))
                  ms))))

(deftest fetcher-with-noargs-resource
  (async
   done
   (let [r (p/resource (fn []
                         (-> (sleep 100)
                             (.then (constantly 42)))))
         _call-r (r) ; initializing
         *calls (atom [])
         s (f/signal (inc @(:value r)))
         _fx (f/effect [] (swap! *calls conj @s))]
     (is (= :pending @(:state r)))
     ;; (is (= :unresolved @(:state r)))
     (is (= nil @(:value r)))
     (is (= nil @(:error r)))
     (is (= [1] @*calls))
     ;; (r)
     ;; (is (= :pending @(:state r)))
     (-> (sleep 101)
         (.then (fn []
                  (is (= :ready @(:state r)))
                  (is (= 42 @(:value r)))
                  (is (= [1 43] @*calls))))
         (.then done done)))))

(deftest fetcher-with-args-resource
  (async
   done
   (let [r (p/resource (fn [ms]
                         (-> (sleep ms)
                             (.then (constantly 42)))))
         _call-r (r 200) ; initializing
         *calls (atom [])
         s (f/signal (inc @(:value r)))
         _fx (f/effect [] (swap! *calls conj @s))]
     (is (= :pending @(:state r)))
     ;; (is (= :unresolved @(:state r)))
     (is (= nil @(:value r)))
     (is (= nil @(:error r)))
     (is (= [1] @*calls))
     ;; (r)
     ;; (is (= :pending @(:state r)))
     (-> (sleep 101)
         (.then (fn []
                  (is (= :pending @(:state r)))
                  (is (= nil @(:value r)))
                  (is (= [1] @*calls))))
         (.then done done))
     (-> (sleep 201)
         (.then (fn []
                  (is (= :ready @(:state r)))
                  (is (= 42 @(:value r)))
                  (is (= [1 43] @*calls))))
         (.then done done)))))

(comment
  (t/run-tests))
