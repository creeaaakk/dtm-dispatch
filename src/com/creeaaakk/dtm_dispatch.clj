(ns com.creeaaakk.dtm-dispatch
  (:require [clojure.core.match :refer [clj-form]]
            [datomic.api :as d])
  (:import [java.util.concurrent
            ThreadPoolExecutor ThreadPoolExecutor$DiscardPolicy
            TimeUnit SynchronousQueue BlockingQueue]))

(defprotocol IDispatch
  (set-dispatch-table! [this table])
  (dispatch [this args]))

(defprotocol IDaemon
  (start [this])
  (stop [this]))

(defprotocol IProducer
  (start-events [this] [this queue]
    "Returns a queue (or uses the provided queue) that events will be made available on.")
  (stop-events [this] [this queue]
    "Stops appending events onto the queue."))

(extend-type datomic.Connection
  IProducer
  (start-events
    ([connection]
       (d/tx-report-queue connection))
    ([connection _]
       (throw (Error. "(get-events this queue) not implemented for datomic.Connection"))))
  (stop-events
    ([connection]
       (d/remove-tx-report-queue connection))
    ([connection _]
       (d/remove-tx-report-queue connection))))

(declare pumping-loop)

(deftype DispatchingExecutor
    [dispatch-table executor producer ^BlockingQueue txn-queue ^BlockingQueue work-queue stop pumping-thread]

  IDaemon
  (start [this]
    (if (nil? @pumping-thread)
      (do (reset! pumping-thread (Thread. (pumping-loop txn-queue this stop executor) "pumping-thread"))
          (.start @pumping-thread))
      (throw (ex-info "Tried to start non-new pumping-thread." {:thread-state (.getState @pumping-thread)}))))
  (stop [_]
    (stop-events producer)
    (when-not (or (nil? @pumping-thread)
                  (= (.getState @pumping-thread) Thread$State/TERMINATED))
      (.put txn-queue stop)
      (.join @pumping-thread))
    (.shutdown executor)
    :done)
    
  IDispatch
  (set-dispatch-table! [_ table]
    (reset! dispatch-table table))
  (dispatch [_ args]
    (when (and (vector? args) (= (count args) 4))
      (@dispatch-table args))))

(defn dispatch-fn
  [handlers]
  (let [args (vec (repeatedly 4 gensym))]
    (eval (list 'fn [args] (clj-form args (concat handlers [:else nil]))))))

(defn pumping-loop
  [txn-queue dispatch-table stop-sentinel executor]
  (fn [] (loop [txn (.take txn-queue)]
          (when-not (identical? txn stop-sentinel)
            (if-let [handler (dispatch dispatch-table txn)]
              (.execute executor handler))
            (recur (.take txn-queue))))))

(defn executor
  "Given a sequence of txn handlers and a txn-queue, return a
  ThreadPoolExecutor that will run the appropriate handler for each
  txn in the queue."
  ([txn-queue]
     (executor (dispatch-fn '()) txn-queue))
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
  ([executor dispatch producer work-queue]
     (let [txn-queue (start-events producer)]
       (->DispatchingExecutor (atom dispatch) executor producer txn-queue work-queue (Object.) (atom nil)))))

