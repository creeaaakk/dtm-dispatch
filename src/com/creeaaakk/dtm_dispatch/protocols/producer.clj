(ns com.creeaaakk.dtm-dispatch.protocols.producer)

(defprotocol IProducer
  (start-events [this] [this queue]
    "Returns a queue (or uses the provided queue) that events will be made available on.")
  (stop-events [this] [this queue]
    "Stops appending events onto the queue."))
