;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

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

  (:import [czlab.loki.event EventSub PubSub]
           [czlab.loki.net TCPSender]
           [io.netty.channel Channel]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn tcpSender<>
  ""
  ^TCPSender
  [^Channel ch]
  (reify TCPSender
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
        (doseq [[^EventSub cb _] @handlers
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
              (let [^EventSub ee cb]
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

