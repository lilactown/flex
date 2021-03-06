(ns flex.core-benchmark
  (:require
   [clojure.core.async :as a]
   [flex.core :as f]
   [flex.scheduler :as f.s]))

#_(require '[criterium.core :as c])

#_(require '[clj-async-profiler.core :as prof])


(defn run-graph! [times]
  (let [env (f/env
             ;; :scheduler (f.s/->SynchronousSchedulerDoNotUse)
             )
        db (f/input {:limit 0 :chars "a"})
        limit (f/signal (:limit @db))
        chars (f/signal (:chars @db))
        fib (f/signal fib
              ([] (fib [0 1]))
              ([v]
               (let [limit @limit
                     [prev cur] v
                     next (+ prev cur)]
                 (if (< next limit)
                   (f/recur [cur next])
                   [prev cur]))))
        combo (f/signal [(second @fib) @chars])
        ;; combinations (f/collect [] combo)
        ]
    (f/with-env env
      (f/connect! combo))
    #?(:clj (doseq [_ (range times)]
              (f/with-env env
                @(f/send db update :limit inc)
                @(f/send db update :chars str "b")))
       :cljs (-> (js/Promise.all
                  (to-array
                   (for [_ (range times)]
                     (-> (f/with-env env
                           (f/send db update :limit inc))
                         (.then #(f/with-env env
                                   (f/send db update :chars str "b")))))))
                 (.then #(f/with-env env
                           @combo))))
    #?(:clj (f/with-env env @combo))))

#_(do (time (run-graph! 1000)) nil)

;; cljs
#_(let [t (.getTime (js/Date.))
        run (run-graph! 1000)]
    (-> run
        (.then (fn [v]
                 (let [t' (.getTime (js/Date.))]
                   (println (- t' t) "msecs"))))))

#_(c/quick-bench (run-graph! 1000))

#_(prof/profile (run-graph! 1000))

#_(prof/serve-files 8080)


(defn fib*
  ([limit] (fib* limit [0 1]))
  ([limit [prev cur]]
   (let [next (+ prev cur)]
     (if (< next limit)
       (recur limit [cur next])
       [prev cur]))))


(defn run-calc! [times]
  (loop [n times
         limit? true
         db {:limit 0 :chars "a"}
         ;; combinations []
         fib (fib* 0)]
    (if (zero? n)
      #_combinations [(second fib) (:chars db)]
      (let [limit (:limit db)
            chars (:chars db)
            ;; recalculate all fibs every time
            fib (fib* limit)]
        (if limit?
          (recur
           n
           (not limit?)
           (-> db
               (update :limit inc))
           #_(conj combinations (vector (second fib) chars))
           fib)
          (recur
           (dec n)
           (not limit?)
           (-> db
               (update :chars str "b"))
           #_(conj combinations (vector (second fib) chars))
           fib))))))


#_(c/quick-bench (run-calc! 1000))

#_(do (time (run-calc! 1000)) nil)

#_(prof/profile (run-calc! 100000))


(defn run-calc-async! [times]
  (a/go-loop [n times
              limit? true
              db {:limit 0 :chars "a"}
              ;; combinations []
              fib (fib* 0)]
    (if (zero? n)
      #_combinations fib
      (let [limit (:limit db)
            chars (:chars db)
            ;; recalculate all fibs every time
            fib (fib* limit)]
        (if limit?
          (recur
           n
           (not limit?)
           (-> db
               (update :limit inc))
           #_(conj combinations (vector (second fib) chars))
           fib)
          (recur
           (dec n)
           (not limit?)
           (-> db
               (update :chars str "b"))
           #_(conj combinations (vector (second fib) chars))
           fib))))))

#_(c/quick-bench (<!! (run-calc-async! 1000)))

#_(prof/profile (<!! (run-calc-async! 100000)))
