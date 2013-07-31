(ns com.creeaaakk.dtm-dispatch.protocols.dispatch)

(defprotocol IDispatch
  (set-dispatch-table! [this table])
  (add-dispatch-target! [this key target])
  (rem-dispatch-target! [this key])
  (dispatch [this args]))
