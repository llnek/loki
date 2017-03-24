;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.net.core

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [io.netty.handler.codec.http.websocketx
            WebSocketFrame
            TextWebSocketFrame]
           [io.netty.channel Channel]
           [czlab.loki.sys Room]
           [clojure.lang APersistentMap]
           [czlab.loki.net Events EventError]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defmacro isPrivate? "" [evt] `(= Events/PRIVATE (:type ~evt)))
(defmacro isPublic? "" [evt] `(= Events/PUBLIC (:type ~evt)))
(defmacro isCode? "" [c evt] `(= ~c (:code ~evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn prettyEvent
  "Pretty print the event"
  ^String [evt] {:pre [(map? evt)]}

  (let [{:keys [type code status]}
        evt
        m {:type (str type)
           :code (str code)
           :status (str status)}]
    (writeJsonStr (merge evt m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn encodeEventAsJson
  "Turn data into a json string"
  ^String
  [{:keys [timestamp status
           type code body] :as evt}]
  {:pre [(some? type)(some? code)]}
  (let [msg {:type (.value ^Events type)
             :code (.value ^Events code)
             :body (or body {})
             :timestamp (or timestamp (now<>))}]
    (-> (if status
          (assoc msg
                 :status
                 (.value ^Events status)) msg)
        writeJsonStr)))

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
     (let [{:keys [type code] :as evt}
           (readJsonStrKW data)
           t (and (number? type) (Events/get type))
           c (and (number? code) (Events/get code))]
       (cond
         (or (false? t) (nil? t))
         (trap! EventError (format "Event type info: %d" t))
         (or (false? c) (nil? c))
         (trap! EventError (format "Event code info: %d" c))
         :else
         (merge evt
                {:type t :code c} extras))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn errorObj<>
  "" {:tag APersistentMap}

  ([etype ecode body arg]
   {:pre [(some? etype)
          (some? ecode)]}
   (let [body (if (string? body) {:message body} body)
         obj {:status Events/ERROR
              :timestamp (now<>)
              :type etype
              :code ecode
              :body (or body {})}]
     (cond
       (map? arg)
       (merge obj arg)
       (some? arg)
       (assoc obj :context arg)
       :else obj)))

  ([etype ecode body]
   (errorObj<> etype ecode body nil))

  ([etype ecode]
   (errorObj<> etype ecode nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn eventObj<>
  "" {:tag APersistentMap}

  ([etype ecode body arg]
   {:pre [(some? etype)
          (some? ecode)
          (or (nil? body)
              (map? body))]}
   (let [obj {:status Events/OK
              :timestamp (now<>)
              :type etype
              :code ecode
              :body (or body {})}]
     (cond
       (map? arg)
       (merge obj arg)
       (some? arg)
       (assoc obj :context arg)
       :else obj)))

  ([etype ecode body]
   (eventObj<> etype ecode body nil))

  ([etype ecode]
   (eventObj<> etype ecode nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn privateEvent<> ""
  ([code body] (privateEvent<> code body nil))
  ([code body arg]
   (eventObj<> Events/PRIVATE code body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn publicEvent<> ""
  ([code body] (publicEvent<> code body nil))
  ([code body arg]
   (eventObj<> Events/PUBLIC code body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyEvent
  "Reply back a msg" [^Channel ch msg]

  (log/debug (str "reply back a msg "
                  "type: %s, code: %s")
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

  (replyEvent ch (errorObj<> Events/PRIVATE error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isMove? "" [evt]
  (and (isPrivate? evt)
       (isCode? Events/PLAY_MOVE evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pokeWait! ""
  ([room body] (pokeWait! room body nil))
  ([room body arg]
   (.send ^Room room (privateEvent<> Events/POKE_WAIT body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pokeMove! ""
  ([room body] (pokeMove! room body nil))
  ([room body arg]
   (.send ^Room room  (privateEvent<> Events/POKE_MOVE body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn syncArena! ""
  ([room body] (pokeMove! room body nil))
  ([^Room room body arg]
   (->>
     (if arg
       (privateEvent<> Events/SYNC_ARENA body arg)
       (publicEvent<> Events/SYNC_ARENA body))
     (.send room))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bcast! ""
  ([room code body] (bcast! room code body nil))
  ([room code body arg]
   (. ^Room room broadcast (publicEvent<> code body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn stop! ""
  ([room body] (stop! room body nil))
  ([room body arg] (bcast! room Events/STOP body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

