# Changelog

## 2023-03-18

* Gaurantee stable order of running signals and effects by order of creation
* Allow differentiating between initialization and update of `effect` by using
  arity. `(effect ([] "called on init") ([x] "called on update"))`
* Errors that occur in signals are propagated to dependents.
* Effects and listeners now can attach `on-error` callbacks to them instead.
* Simple clj-kondo config for `effect`

## 2023-01-29

* Effects and listeners started inside of another effect will be disposed of
  when the parent effect is disposed.
