(ns com.creeaaakk.dtm-dispatch.protocols.daemon)

(defprotocol IDaemon
  (start [this])
  (stop [this]))
