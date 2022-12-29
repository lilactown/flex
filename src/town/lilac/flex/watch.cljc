(ns town.lilac.flex.watch
  (:require
   [town.lilac.flex :as flex]))

(deftype SyncWatcher [^:volatile-mutable watchers
                      ^:volatile-mutable dispose
                      s]
  #?(:clj clojure.lang.IRef :cljs IWatchable)
  (#?(:clj addWatch :cljs -add-watch) [this key f]
    (when (nil? dispose)
      (let [fx (flex/effect
                [prev]
                (doseq [[k f] watchers]
                  (f k this prev @s))
                @s)]
        (set! dispose (fx))))
    (set! watchers (assoc watchers key f)))
  (#?(:clj removeWatch :cljs -remove-watch) [_ key]
    (set! watchers (dissoc watchers key))
    (when (empty? watchers)
      (dispose)
      (set! dispose nil)))

  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref :cljs -deref) [_]
    @s))


(defn watcher
  [s]
  (->SyncWatcher {} nil s))
