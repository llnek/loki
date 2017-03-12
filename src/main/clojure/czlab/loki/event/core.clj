;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.event.core

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.str])

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
  [{:keys [timestamp status
           type code body] :as evt}]
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
  ^WebSocketFrame [evt] {:pre [(map? evt)]}
  (->> (encodeEventAsJson evt) (TextWebSocketFrame. )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn decodeEvent
  "Returns event with socket info attached.
  If error, catch and return it with an invalid type."
  {:tag APersistentMap}
  ([data] (decodeEvent data nil))
  ([data extras]
   (log/debug "decoding json: %s" data)
   (try!
     (let [evt (readJsonStrKW data)]
       (when-not (number? (:type evt))
         (trap! EventError
                (format "Event type info: %d" (:type evt))))
       (merge evt extras)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn errorObj<>
  "" {:tag APersistentMap}

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
  "" {:tag APersistentMap}

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
  "Reply back a msg" [^Channel ch msg]

  (log/debug (str "reply back a msg "
                  "type: %d, code: %d")
             (:type msg) (:code msg))
  (do->nil
    (if (some? ch)
        (->> (encodeEvent msg)
             (.writeAndFlush ch)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyError
  "Reply back an error"
  [^Channel ch error msg]

  (replyEvent ch (errorObj<> Events/UNIT error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

