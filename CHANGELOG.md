# Changelog

See commit history for latest changes. This file will have summaries of large
changes across multiple commits.

## 2023-03-26

A large change while experimenting with real world cases: `effect` and `listen`
now execute and start listening immediately, no longer waitin for you to call
the returned object like a function. You can call `dispose!` to stop them and
`run!` to resume listening after disposal.

The motivation for the change was after noticing that for UI cases. Decoupling
effect construction from effect run encouraged storing them as global variables,
which encouraged one to treat them as non-singletons. Meanwhile if your effect
needed to close over some function argument or binding, it was awkward to have
to construct it and then start it.

Going forward, `effect` will now run immediately and will listen for changes
until `dispose!` is run on it or a parent effect is `dispose!`ed of. Same for
`listen`, only it doesn't execute its callback on initial run and only listens
for changes.

## 2023-03-25

* Fixed `effect` macro detecting arity inside of other macros

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
