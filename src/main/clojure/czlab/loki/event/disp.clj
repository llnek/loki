;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.event.disp

  (:require [czlab.xlib.logging :as log]
            [clojure.core.async
             :as cas
             :refer
             [close!
              go
              chan
              >!
              <!
              go-loop]])

  (:use [czlab.loki.event.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [czlab.loki.event Subr PubSub]
           [czlab.loki.net TcpSender]
           [io.netty.channel Channel]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn tcpSender<>
  ""
  ^TcpSender
  [^Channel ch]
  (reify TcpSender
    (send [_ evt]
      (.writeAndFlush ch (encodeEvent evt)))
    (isReliable [_] true)
    (close [_]
      (log/debug "closing tcp: %s" ch)
      (closeQ ch))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn dispatcher<>
  ""
  ^PubSub
  []
  (let [handlers (atom {})]
    (reify PubSub

      (unsubscribeIfSession [this s]
        (doseq [[^Subr cb _] @handlers
                :let [pss (.session cb)]
                :when (identical? pss s)]
          (.unsubscribe this cb)))

      (publish [_ msg]
        (log/debug "pub message ==== %s" msg)
        (doseq [c (vals @handlers)]
          (cas/go (cas/>! c msg))))

      (unsubscribe [_ cb]
        (when-some [c (@handlers cb)]
          (cas/close! c)
          (swap! handlers dissoc cb)))

      (subscribe [_ cb]
        (let [c (cas/chan 4)]
          (swap! handlers assoc cb c)
          (cas/go-loop []
            (if-some [msg (cas/<! c)]
              (let [^Subr ee cb]
                (log/debug "pubsub: got msg for sub: %s" ee)
                (if (== (.eventType ee)
                        (:type msg))
                  (.receive ee msg))
                (recur))))))

      (close [_]
        (doseq [[_ c] @handlers]
          (cas/close! c))
        (reset! handlers {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

