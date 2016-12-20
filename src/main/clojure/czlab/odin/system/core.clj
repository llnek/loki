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

  czlab.odin.system.core

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.flux.wflow.core]
        [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str]
        [czlab.odin.event.core]
        [czlab.odin.game.msgreq])

  (:import [io.netty.handler.codec.http.websocketx TextWebSocketFrame]
           [czlab.flux.wflow WorkStream Job]
           [czlab.wabbit.io WSockEvent]
           [czlab.odin.event Events]
           [czlab.xlib XData]
           [java.io File]
           [io.netty.channel Channel]
           [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- odinOnEvent
  ""
  [^WSockEvent ws]

  (let [req (->> {:socket (.socket ws)
                  :source (.source ws)}
                 (decodeEvent (.. ws
                                  body
                                  stringify)))]
    (condp = (:type req)

      Events/PLAYGAME_REQ
      (doPlayReq req)

      Events/JOINGAME_REQ
      (doJoinReq req)

      (log/warn "unhandled event! %s" req))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn odinInit
  "One time init from the MainApp"
  [^Container ctr]
  ;;TODO: loading in Odin config file. do something with it?
  (let [{:keys [odin]}
        (.podConfig ctr)]
    (log/info "Odin config= %s" odin)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn odinHandler
  ""
  ^WorkStream
  []
  (workStream<>
    (script<>
      #(odinOnEvent (.event ^Job %2)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


