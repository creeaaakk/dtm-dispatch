(ns dev
  (:use [clojure repl pprint]
        [clojure.tools.namespace.repl :only [refresh]]
        criterium.core
        com.creeaaakk.dtm-dispatch)
  (:require [com.creeaaakk.dtm-dispatch.protocols
             [dispatch :as dsp]
             [producer :as p]
             [daemon :as dmn]]
            [com.creeaaakk.cm-dispatch :as cmd])
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def job-count (atom {}))

(defn make-events
  [num]
  (map #(hash-map :tx-data [%])
       (repeatedly num #(rand-nth [{:e 1 :a :user/username     :v 10 :added false}
                                   {:e 1 :a :user/username     :v 10 :added true}
                                   {:e 1 :a :user/phone-number :v 10 :added true}
                                   :foo]))))

#_(def events (make-events 1000000))

(def q (LinkedBlockingQueue.))

(defn new-user-job
  [key]
  (fn [_]
    #(swap! job-count update-in [:new-user-job key] (fnil inc 0))))

(defn removed-user-job
  [key]
  (fn [_]
    #(swap! job-count update-in [:removed-user-job key] (fnil inc 0))))

(defn notify-contacts-job
  [key]
  (fn [_]
    #(swap! job-count update-in [:notify-contacts-job key] (fnil inc 0))))

(defn added-job
  [_]
  #(swap! job-count update-in [:foo-job :added] (fnil inc 0)))

(defn default-foo-job
  [_]
  #(swap! job-count update-in [:foo-job :default] (fnil inc 0)))

(def dsp1 {::new-user-job        {:event {:a :user/username :added true}
                                  :handler (new-user-job :dsp1)}
           ::removed-user-job    {:event {:a :user/username :added false}
                                  :handler (removed-user-job :dsp1)}
           ::notify-contacts-job {:event {:a '(:or :user/phone-number :device/phone-number
                                                   :contact/phone-number :phone-number/number)}
                                  :handler (notify-contacts-job :dsp1)}
           ::default-foo         {:event :foo
                                  :handler default-foo-job}})

(def dsp2 {::new-user-job        {:event {:a :user/username :added true}
                                  :handler (new-user-job :dsp2)}
           ::removed-user-job    {:event {:a :user/username :added false}
                                  :handler (removed-user-job :dsp2)}
           ::notify-contacts-job {:event {:a '(:or :user/phone-number :device/phone-number
                                                   :contact/phone-number :phone-number/number)}
                                  :handler (notify-contacts-job :dsp2)}
           ::default-foo         {:event :foo
                                  :handler default-foo-job}})

(extend-type LinkedBlockingQueue
  p/IProducer
  (start-events ([this] this) ([this _] this))
  (stop-events ([_] nil) ([_ _] nil)))

(def disp (cmd/cm-dispatch dsp1))
(def e (executor disp q))

(defn expected-count
  [events dispatch]
  (->> events
       (mapcat :tx-data)
       (map #(if (keyword? %) % (dissoc % :v :e)))
       (map #(get dispatch %))
       frequencies))

(defn actual-count
  [counts]
  (reduce (fn [acc [k v]]
            (assoc acc k (apply + (vals v))))
          {}
          counts))

(defn foo
  []
  (dsp/set-dispatch-table! disp dsp1)
  (println "Starting:" (java.util.Date.))
  (dmn/start e)
  (future (doseq [e (make-events 1e6)]
            (.put q e)))
  (Thread/sleep 100)
  (dsp/set-dispatch-table! disp dsp2)
  (Thread/sleep 500)
  (dsp/add-dispatch-target! disp ::default-foo {:event :foo :handler added-job})
  (Thread/sleep 1000)
  (dosync
   (dsp/rem-dispatch-target! disp ::default-foo)
   (dsp/add-dispatch-target! disp ::default-foo {:event :foo :handler default-foo-job})))
