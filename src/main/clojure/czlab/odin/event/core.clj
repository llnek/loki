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

  czlab.odin.event.core

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [io.netty.handler.codec.http.websocketx
            WebSocketFrame
            TextWebSocketFrame]
           [clojure.lang APersistentMap]
           [czlab.odin.event Events EventError]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn encodeEvent
  "Turn data into websocket frames"
  ^WebSocketFrame
  [{:keys [type code body]}]
   {:pre [(number? type)]}
   (->> {:body (or body nil)
         :type type
         :code (or code 0)
         :timestamp (now<>)}
        writeJsonStr
        (TextWebSocketFrame. )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn decodeEvent
  "Returns event with socket info attached.
  If error, catch and return it with an invalid type."
  {:tag APersistentMap}
  ([data] (decodeEvent data nil))
  ([data extraBits]
   (log/debug "decoding json: %s" data)
   (try!!
     {:type 0}
     (let [evt (readJsonStrKW data)]
       (when-not (number? (:type evt))
         (trap! EventError
                (format "Event type info: %s" (:type evt))))
       (merge evt extraBits)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn reifyEvent
  ""
  {:tag APersistentMap}
  ([eventType ecode body ctx]
   {:pre [(number? eventType)]}
   (let [base {:timestamp (now<>)
               :type (int eventType)
               :body (or body nil)
               :context (or ctx nil)
               :code (or ecode 0)}] base))

  ([eventType ecode body]
   (reifyEvent eventType ecode body nil))

  ([eventType ecode]
   (reifyEvent eventType ecode nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn reifyGlobalEvent
  "Make a Global Event"
  {:tag APersistentMap}

  ([ecode body]
   (reifyGlobalEvent ecode body true))

  ([ecode body reliable?]
   (-> (reifyEvent Events/GLOBAL ecode body)
       (assoc :reliable (true? reliable?)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn reifyLocalEvent
  "Make a Local Event"
  ^APersistentMap
  [ecode body]
  (reifyEvent Events/LOCAL ecode body))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn reifyUnitEvent
  "Make a Unit Event"
  {:tag APersistentMap}

  ([ecode body]
   (reifyUnitEvent ecode body nil))

  ([ecode body ctx]
   (reifyEvent Events/UNIT ecode body ctx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

