(ns com.creeaaakk.dtm-dispatch
  (:require [clojure.core.match :refer [clj-form]]
            [datomic.api :as d])
  (:import [java.util.concurrent
            ThreadPoolExecutor ThreadPoolExecutor$DiscardPolicy
            TimeUnit SynchronousQueue BlockingQueue]))

(defprotocol IDaemon
  (start [this])
  (stop [this]))

(defprotocol IProducer
  (start-events [this] [this queue]
    "Returns a queue (or uses the provided queue) that events will be made available on.")
  (stop-events [this] [this queue]
    "Stops appending events onto the queue."))

(defprotocol IDispatch
  (set-dispatch-table! [this table])
  (add-dispatch-target! [this key target])
  (rem-dispatch-target! [this key])
  (dispatch [this args]))

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

(defn dispatch-fn
  "Return a dispatch function of one argument. This function receives an event and
   matches it against its known handlers, returning the first handler to match, or
   nil if there are no matches.

     handlers is a sequence of maps of the form:

        - :event   :: core.match pattern row
        - :handler :: function to be returned if the :event is matched"
  [handlers]
  (let [datom (gensym)
        syms (repeatedly (count handlers) gensym)
        clauses (vec (mapcat #(vector [%1] %2) (map :event handlers) syms))
        sym->handler (apply hash-map (interleave (map keyword syms) (map :handler handlers)))]
    (partial (eval `(fn [{:keys ~(vec syms)} ~datom]
                      ~(clj-form [datom] (concat clauses [:else nil]))))
             sym->handler)))

(deftype DispatchingExecutor
    [dispatch-table dispatcher executor producer ^BlockingQueue txn-queue ^BlockingQueue work-queue stop pumping-thread]

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
    (dosync
     (ref-set dispatch-table table)
     (ref-set dispatcher (dispatch-fn (vals table)))))
  (add-dispatch-target! [_ key target]
    (dosync
     (->> target
          (commute dispatch-table assoc key)
          vals
          dispatch-fn
          (ref-set dispatcher))))
  (rem-dispatch-target! [_ key]
    (dosync
     (->> (alter dispatch-table dissoc key)
          vals
          dispatch-fn
          (ref-set dispatcher))))
  (dispatch [_ args]
    (loop [[datom & datoms] (:tx-data args)]
      (when datom
        (if-let [handler (@dispatcher datom)]
          handler
          (recur datoms))))))

(defn pumping-loop
  [txn-queue dispatch-table stop-sentinel executor]
  (fn [] (loop [txn (.take txn-queue)]
          (when-not (identical? txn stop-sentinel)
            (if-let [handler (dispatch dispatch-table txn)]
              (.execute executor (handler txn)))
            (recur (.take txn-queue))))))

(defn executor
  "Given a sequence of txn handlers and a txn-queue, return a
  ThreadPoolExecutor that will run the appropriate handler for each
  txn in the queue."
  ([txn-queue]
     (executor nil txn-queue))
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
       (->DispatchingExecutor (ref dispatch) (ref (dispatch-fn dispatch))
                              executor producer txn-queue work-queue
                              (Object.) (atom nil)))))

