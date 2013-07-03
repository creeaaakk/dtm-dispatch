(ns dev
  (:use [clojure repl pprint]
        [clojure.tools.namespace.repl :only [refresh]]
        criterium.core
        com.creeaaakk.dtm-dispatch)
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def job-count (atom {}))

(def q (LinkedBlockingQueue. (repeatedly 100000 #(rand-nth [[1 :user/username 10 false]
                                                            [1 :user/username 10 true]
                                                            [1 :user/phone-number 10 true]
                                                            'foo]))))

(defn new-user-job
  [key]
  #(swap! job-count update-in [:new-user-job key] (fnil inc 0)))

(defn removed-user-job
  [key]
  #(swap! job-count update-in [:removed-user-job key] (fnil inc 0)))

(defn notify-contacts-job
  [key]
  #(swap! job-count update-in [:notify-contacts-job key] (fnil inc 0)))

(def dsp1 (dispatch-fn ['[_ :user/username _ true] `(new-user-job :dsp1)
                        '[_ :user/username _ false] `(removed-user-job :dsp1)
                        '[_ (:or :user/phone-number
                                :device/phone-number
                                :contact/phone-number
                                :phone-number/number) _ _] `(notify-contacts-job :dsp1)]))

(def dsp2 (dispatch-fn ['[_ :user/username _ true] `(new-user-job :dsp2)
                        '[_ :user/username _ false] `(removed-user-job :dsp2)
                        '[_ (:or :user/phone-number
                                :device/phone-number
                                :contact/phone-number
                                :phone-number/number) _ _] `(notify-contacts-job :dsp2)]))

(extend-type LinkedBlockingQueue
  IProducer
  (start-events ([this] this) ([this _] this))
  (stop-events ([_] nil) ([_ _] nil)))

(def e (executor dsp1 q))

(defn foo
  []
  (set-dispatch-table e dsp1)
  (start e)
  (Thread/sleep 100)
  (set-dispatch-table e dsp2))
