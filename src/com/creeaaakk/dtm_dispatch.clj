(ns com.creeaaakk.dtm-dispatch
  (:require [clojure.core.match :refer [match]])
  (:import [java.util.concurrent
            ThreadPoolExecutor ThreadPoolExecutor$DiscardPolicy
            TimeUnit SynchronousQueue BlockingQueue]))

(defprotocol IDispatch
  (set-dispatch-table [this table])
  (dispatch [this args]))

(defprotocol IDaemon
  (start [this])
  (stop [this]))

(declare pumping-loop)

(deftype DispatchingExecutor
    [dispatch-table executor ^BlockingQueue txn-queue ^BlockingQueue work-queue stop? pumping-thread]

  IDaemon
  (start [this]
    (if (nil? @pumping-thread)
      (do (reset! pumping-thread (Thread. (pumping-loop txn-queue this stop? executor) "pumping-thread"))
          (.start @pumping-thread))
      (throw (ex-info "Tried to start non-new pumping-thread." {:thread-state (.getState pumping-thread)}))))
  (stop [_]
    (reset! stop? true)
    (.shutdown executor))
    
  IDispatch
  (set-dispatch-table [_ table]
    (reset! dispatch-table table))
  (dispatch [_ args]
    (@dispatch-table args)))

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

(defn pumping-loop
  [txn-queue dispatch-table stop? executor]
  (fn [] (loop [txn (.poll txn-queue 100 TimeUnit/MILLISECONDS)]
          (when-not @stop?
            (if-let [handler (dispatch dispatch-table txn)]
              (.execute executor handler))
            (recur (.poll txn-queue 100 TimeUnit/MILLISECONDS))))))

(defn executor
  "Given a sequence of txn handlers and a txn-queue, return a
  ThreadPoolExecutor that will run the appropriate handler for each
  txn in the queue."
  ([txn-queue]
     (executor (dispatch-fn)))
  ([dispatch txn-queue]
     (executor dispatch txn-queue (SynchronousQueue.)))
  ([dispatch txn-queue work-queue]
     (let [num-procs (.availableProcessors (Runtime/getRuntime))]
       (executor (ThreadPoolExecutor. num-procs (* 2 num-procs)
                                      500 TimeUnit/MILLISECONDS
                                      work-queue
                                      (ThreadPoolExecutor$DiscardPolicy.))
                 dispatch
                 txn-queue work-queue)))
  ([executor dispatch txn-queue work-queue]
     (->DispatchingExecutor (atom dispatch) executor txn-queue work-queue (atom false) (atom nil))))

