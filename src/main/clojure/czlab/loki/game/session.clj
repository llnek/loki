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

  czlab.loki.game.session

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.xlib.process]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.loki.system.util]
        [czlab.loki.event.core]
        [czlab.loki.event.disp])

  (:import [io.netty.handler.codec.http.websocketx TextWebSocketFrame]
           [czlab.wabbit.server Container]
           [io.netty.channel Channel]
           [czlab.loki.core
            Game
            Room
            Player
            Session]
           [czlab.xlib Hierarchial]
           [czlab.loki.net MsgSender]
           [czlab.loki.event Events Sender Receiver]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn reifyPlayerSession
  ""
  ^Session
  [^Room room ^Player plyr pnumber]
  (let [impl (muble<> {:status Events/S_NOT_CONNECTED
                       :shutting-down false})
        created (now<>)
        sid (str "session#" (seqint2))]
    (reify Session

      (parent [_] (.getv impl :parent))
      (setParent [_ _])

      (number [_] pnumber)
      (player [_] plyr)
      (room [_] room)

      (sendMsg [this msg]
        (when (and (not (.isShuttingDown this))
                   (.isConnected this))
          (-> ^MsgSender
              (.getv impl :tcp)
              (.sendMsg msg))))

      (onMsg [this evt]
        (trap! Exception "Unexpected onmsg called in PlayerSession."))
        ;;(log/debug "player session " sid " , onmsg called: " evt))

      (isConnected [this] (= Events/S_CONNECTED (.status this)))

      (isShuttingDown [_] (.getv impl :shutting-down))

      (bind [this options]
        (.setv impl :tcp (reifyReliableSender (:socket options)))
        (.setv impl :parent (:source options))
        (.setStatus this Events/S_CONNECTED))

      (id [_] sid)

      (setStatus [_ s] (.setv impl :status s))
      (status [_] (.getv impl :status))

      (close [this]
        (when (.isConnected this)
          (.setv impl :shutting-down true)
          (closeQ (.getv impl :tcp))
          (.unsetv impl :tcp)
          (.setv impl :shutting-down false)
          (.setv impl :status Events/S_NOT_CONNECTED)))

      Object

      (hashCode [_] (.hashCode sid))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.id ^Session obj)
                      (.id this)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

