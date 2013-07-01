(ns user
  (:use [clojure repl pprint]
        [clojure.tools.namespace.repl :only [refresh]]
        criterium.core
        com.creeaaakk.dtm-dispatch)
  (:require dev)
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def job-count (atom {}))

(defn new-user-job
  [key]
  #(swap! job-count update-in [:new-user-job key] (fnil inc 0)))

(defn removed-user-job
  [key]
  #(swap! job-count update-in [:removed-user-job key] (fnil inc 0)))

(defn notify-contacts-job
  [key]
  #(swap! job-count update-in [:notify-contacts-job key] (fnil inc 0)))

(def dsp1 (dispatch-fn [_ :user/username _ true] (bound-fn* (new-user-job :dsp1))
                      [_ :user/username _ false] (bound-fn* (removed-user-job :dsp1))
                      [_ (:or :user/phone-number
                              :device/phone-number
                              :contact/phone-number
                              :phone-number/number) _ _] (bound-fn* (notify-contacts-job :dsp1))))

(def dsp2 (dispatch-fn [_ :user/username _ true] (bound-fn* (new-user-job :dsp2))
                      [_ :user/username _ false] (bound-fn* (removed-user-job :dsp2))
                      [_ (:or :user/phone-number
                              :device/phone-number
                              :contact/phone-number
                              :phone-number/number) _ _] (bound-fn* (notify-contacts-job :dsp2))))

(def q (LinkedBlockingQueue. (repeatedly 100000 #(rand-nth [[1 :user/username 10 false]
                                                           [1 :user/username 10 true]
                                                           [1 :user/phone-number 10 true]
                                                           'foo]))))

(def e (executor dsp1 q))

(defn foo
  []
  (set-dispatch-table e dsp1)
  (start e)
  (Thread/sleep 100)
  (set-dispatch-table e dsp2))
