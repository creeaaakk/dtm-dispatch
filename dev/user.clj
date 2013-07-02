(ns user
  (:use [clojure.tools.namespace.repl :only [refresh]]
        clojure.repl)
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def dev #(use 'dev))

