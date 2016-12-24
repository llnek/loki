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

  czlab.loki.event.core

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [io.netty.handler.codec.http.websocketx
            WebSocketFrame
            TextWebSocketFrame]
           [io.netty.channel Channel]
           [clojure.lang APersistentMap]
           [czlab.loki.event Events EventError]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn encodeEventAsJson
  "Turn data into a json string"
  ^String
  [{:keys [timestamp status type code body] :as evt}]
  {:pre [(number? status)
         (number? type)(number? code)]}
  (let [m {:status status
           :type type
           :code code
           :body (or body {})
           :timestamp (or timestamp (now<>))}]
    (writeJsonStr m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn encodeEvent
  "Turn data into a websocket frame"
  ^WebSocketFrame
  [evt]
  {:pre [(map? evt)]}
  (->> (encodeEventAsJson evt)
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
   (try!
     (let [evt (readJsonStrKW data)]
       (when-not (number? (:type evt))
         (trap! EventError
                (format "Event type info: %s" (:type evt))))
       (merge evt extraBits)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn errorObj<>
  ""
  {:tag APersistentMap}

  ([etype ecode body arg]
   {:pre [(number? etype)
          (number? ecode)]}
   (let [body (if (string? body) {:message body} body)]
     (merge {:timestamp (now<>)
             :status Events/ERROR
             :type etype
             :code ecode
             :body (or body {})} arg)))

  ([etype ecode body]
   (errorObj<> etype ecode body nil))

  ([etype ecode]
   (errorObj<> etype ecode nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn eventObj<>
  ""
  {:tag APersistentMap}

  ([etype ecode body arg]
   {:pre [(number? etype)
          (number? ecode)
          (or (nil? body)
              (map? body))]}
   (merge {:timestamp (now<>)
           :status Events/OK
           :type etype
           :code ecode
           :body (or body {})} arg))

  ([etype ecode body]
   (eventObj<> etype ecode body nil))

  ([etype ecode]
   (eventObj<> etype ecode nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyEvent
  "Reply back a message"
  [^Channel ch msg]
  {:pre [(some? ch)(map? msg)]}
  (log/debug (str "reply back a msg "
                  "type: %d, code: %d")
             (:type msg) (:code msg))
  (do->nil
    (->> (encodeEvent msg)
         (.writeAndFlush ch))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyError
  "Reply back an error"
  [^Channel ch error msg]
  {:pre [(some? ch)(number? error)]}
  (replyEvent ch
              (errorObj<> Events/UNIT error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

