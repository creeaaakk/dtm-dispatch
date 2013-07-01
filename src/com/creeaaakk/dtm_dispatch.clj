(ns com.creeaaakk.dtm-dispatch
  (:require [clojure.core.match :refer [match]]))

(defn resolve-handler
  [ns env handler]
  (cond
   (symbol? handler) (ns-resolve ns env handler)
   :else handler))

(defn make-dispatch-fn
  "Given a sequence of the form:

    [match-clause handler]

   Return a function that will, given an event, return the handler
   whose match-clause matches the event."
  [env handlers]
  (let [clauses (mapcat (fn [[m h]] [m (resolve-handler *ns* env h)]) handlers)]
    `(fn [event#] (match event# ~@clauses ~'_ nil))))

(defmacro dispatch-fn
  [& handlers]
  (make-dispatch-fn &env (partition 2 handlers)))


