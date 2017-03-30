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

  (:import [czlab.jasal Sendable Receivable]
           [czlab.loki.net PubSub]
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
      (. ^Sendable (:session @data) send evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro defsubr "" [session]
  (let [id (toKW "subr#" (seqint2))]
    `(entity<> Subr {:type Events/PUBLIC
                     :id ~id
                     :session ~session})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Dispatcher
  PubSub
  (unsubscribeIfSession [me s]
    (doseq [[cb _] (:handlers @data)
            :let [pss (:session @cb)]
            :when (objEQ? pss s)]
      (.unsubscribe me cb)))
  (publish [_ msg]
    (log/debug "pub msg = %s" (:code msg))
    (doseq [[_ c] (:handlers @data)]
      (cas/go (cas/>! c msg))))
  (unsubscribe [_ s]
    (when-some [c ((:handlers @data) s)]
      (cas/close! c)
      (swap! data
             update-in [:handlers] dissoc s)))
  (subscribe [_ s]
    (let [c (cas/chan 4)]
      (swap! data update-in [:handlers] assoc s c)
      (cas/go-loop []
        (when-some [msg (cas/<! c)]
          (if (= (:type @s)
                 (:type msg))
            (. ^Receivable s receive msg))
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

