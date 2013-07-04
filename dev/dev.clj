(ns dev
  (:use [clojure repl pprint]
        [clojure.tools.namespace.repl :only [refresh]]
        criterium.core
        com.creeaaakk.dtm-dispatch)
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def job-count (atom {}))

(def q (LinkedBlockingQueue. (map #(hash-map :tx-data [%])
                                  (repeatedly 1000000 #(rand-nth [{:e 1 :a :user/username :v 10 :added false}
                                                                  {:e 1 :a :user/username :v 10 :added true}
                                                                  {:e 1 :a :user/phone-number :v 10 :added true}
                                                                  :foo])))))

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

(def dsp1 (dispatch-fn [{:event {:a :user/username :added true}
                         :handler (new-user-job :dsp1)
                         :symbol ::new-user-job}
                        {:event {:a :user/username :added false}
                         :handler (removed-user-job :dsp1)
                         :symbol ::removed-user-job}
                        {:event {:a '(:or :user/phone-number :device/phone-number
                                         :contact/phone-number :phone-number/number)}
                         :handler (notify-contacts-job :dsp1)
                         :symbol ::notify-contacts-job}]))

(def dsp2 (dispatch-fn [{:event {:a :user/username :added true}
                         :handler (new-user-job :dsp2)
                         :symbol ::new-user-job}
                        {:event {:a :user/username :added false}
                         :handler (removed-user-job :dsp2)
                         :symbol ::removed-user-job}
                        {:event {:a '(:or :user/phone-number :device/phone-number
                                         :contact/phone-number :phone-number/number)}
                         :handler (notify-contacts-job :dsp2)
                         :symbol ::notify-contacts-job}]))

(extend-type LinkedBlockingQueue
  IProducer
  (start-events ([this] this) ([this _] this))
  (stop-events ([_] nil) ([_ _] nil)))

(def e (executor dsp1 q))

(defn foo
  []
  (set-dispatch-table! e dsp1)
  (println "Starting:" (java.util.Date.))
  (start e)
  (Thread/sleep 100)
  (set-dispatch-table! e dsp2))
