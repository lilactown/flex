# Ideas

## Remote sources/signals

Have the ability to have a shared graph between client/server.

```clojure
(ns server
  (:require
   [town.lilac.flex.core :as flex]
   [town.lilac.flex.remote.server :as flex.server]))


(def *state (flex/source {:foo "bar"}))

(def *foo (flex/signal (:foo @*state)))

(def server
  (flex.server/serve
   ;; available sources/signals
   {:state *state
    :foo *foo}
   ;; config
   {:http/port 8888
    :protocol :ws/http}))
```

```clojure
(ns client
  (:require
   [town.lilac.flex.core :as flex]
   [town.lilac.flex.remote.client :as flex.client]))

(def client
  (flex.client/client
   {:port 8888
    :protocol :ws/http}))

;; signal to represent remote server state
(def *state (flex.client/signal client :state))

;; can listen to/deref it like a normal signal
(def *fooA (flex/signal (:foo @*state)))

;; or listen to the remote server signal
(def *fooB (flex.client/signal client :foo))
```
