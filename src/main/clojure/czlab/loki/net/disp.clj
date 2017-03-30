;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.net.disp

  (:require [czlab.basal.logging :as log]
            [clojure.core.async
             :as cas
             :refer
             [close!
              go
              chan
              >!
              <!
              go-loop]])

  (:use [czlab.loki.net.core]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str])

  (:import [czlab.loki.net PubSub]
           [czlab.jasal Receivable]
           [io.netty.channel Channel]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Subr
  Object
  (hashCode [me] (.hashCode (id?? me)))
  (equals [me obj] (objEQ? me obj))
  (toString [me] (id?? me))
  Idable
  (id [_] (:id @data))
  Receivable
  (receive [me evt]
    (when (= (:type @data)
             (:type evt))
      (log/debug "[%s]: recv'ed msg: %s" me evt)
      (send! (:session @data) evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro defsubr "" [session]
  (let [id (str "subr#" (seqint2))]
    `(entity<> Subr {:type Events/PUBLIC
                     :session ~session
                     :id ~id})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sendMsg "" [socket msg]
  (some-> socket
          (.writeAndFlush (encodeEvent msg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Dispatcher
  PubSub
    (unsubscribeIfSession [_ s]
      (doseq [[cb _] (:handlers @data)
              :let [pss (:session @cb)]
              :when (identical? pss s)]
        (.unsubscribe me cb)))
    (publish [_ msg]
      (log/debug "pub msg = %s" (:code msg))
      (doseq [[_ c] (:handlers @data)]
        (cas/go (cas/>! c msg))))
    (unsubscribe [_ cb]
      (when-some [c (get (:handlers @data) cb)]
        (cas/close! c)
        (swap! data
               update-in [:handlers] dissoc cb)))
    (subscribe [_ cb]
      (let [c (cas/chan 4)]
        (swap! data update-in [:handlers] assoc cb c)
        (cas/go-loop []
          (when-some [msg (cas/<! c)]
            (if (= (:type @cb)
                   (:type msg))
              (.receive ^Receivable cb msg))
            (recur)))))
    Closeable
    (close [_]
      (doseq [[_ c] (:handlers @data)]
        (cas/close! c))
      (swap! data assoc :handlers {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro defdispatcher "" []
  `(entity<> Dispatcher {:handlers {}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

