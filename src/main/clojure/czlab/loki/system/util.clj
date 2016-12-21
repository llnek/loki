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

  czlab.loki.system.util

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.convoy.netty.core]
        [czlab.loki.event.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [io.netty.util AttributeKey ReferenceCountUtil]
           [io.netty.handler.codec.http.websocketx
            TextWebSocketFrame
            CloseWebSocketFrame]
           [czlab.loki.core
            Game
            Room
            Player
            Session]
           [io.netty.channel
            Channel
            ChannelHandler
            ChannelHandlerContext]
           [czlab.wabbit.server Container]
           [czlab.convoy.netty InboundAdapter]
           [czlab.loki.event EventError Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defonce ^AttributeKey PLAY_SESSION (akey<> "play-session"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private loki-HANDLER
  (proxy [InboundAdapter][]
    (channelRead [ctx msg]
      (let
        [^ChannelHandlerContext ctx ctx
         ch (.channel ctx)
         ^Session
         ps (getAKey ctx PLAY_SESSION)
         msg
         (condp instance? msg
           TextWebSocketFrame
           (do
             (->> (-> ^TextWebSocketFrame msg
                      (.text)
                      (decodeEvent {:context ps}))
                  (.onMsg (.room ps)))
             msg)
           ;;else
           (do->nil
            (log/debug "got a non-frame msg: %s" msg)
            (.fireChannelRead ctx msg)))]
        (some-> msg
                (ReferenceCountUtil/release msg))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiHandler "" [] loki-HANDLER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn generateUID
  ""
  ^String
  [^Class cz]
  (let [id (juid)]
    (if (nil? cz)
      id
      (str (.getSimpleName cz) "-" id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

