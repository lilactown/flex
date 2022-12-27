(ns town.lilac.strange)

(defprotocol IFoo
  (bar [foo]))

(deftype Foo [^:volatile-mutable baz]
  IFoo
  (bar [_]
    (set! baz 42)
    baz))

(def ^:dynamic *dynamic*)

(deftype DynamicFoo [^:volatile-mutable baz]
  IFoo
  (bar [_]
    (binding [*dynamic* 84]
      (set! baz 42))
    baz))
