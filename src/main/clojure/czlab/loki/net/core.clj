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

  (:require [czlab.loki.xpis :as loki :refer :all]
            [czlab.basal.logging :as log])

  (:use [czlab.basal.format]
        [czlab.convoy.core]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [clojure.lang APersistentMap]
           [czlab.jasal DataError Sendable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defmacro isPrivate? "" [evt] `(= ::loki/private (:type ~evt)))
(defmacro isPublic? "" [evt] `(= ::loki/public (:type ~evt)))
(defmacro isCode? "" [c evt] `(= ~c (:code ~evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn prettyEvent
  "Pretty print the event"
  ^String [evt] {:pre [(map? evt)]}

  (let [{:keys [type code status]}
        evt
        m {:type (get-enum-str loki-msg-types type)
           :code (get-enum-str loki-msg-codes code)
           :status (get-enum-str loki-status-codes status)}]
    (prn-str (dissoc (merge evt m)
                          :context :socket :session :source))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn encodeEvent
  "Turn data into a json string"
  ^String
  [{:keys [timestamp status
           type code body] :as evt}]
  {:pre [(some? type)(some? code)]}
  (let [msg {:type (get loki-msg-types type)
             :code (get loki-msg-codes code)
             :body (or body {})
             :timestamp (or timestamp (now<>))}]
    (-> (if status
          (assoc msg
                 :status
                 (get loki-status-codes status)) msg)
        writeJsonStr)))

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
           t (and (number? type)
                  (lookup-enum-int loki-msg-types type))
           c (and (number? code)
                  (lookup-enum-int loki-msg-codes code))]
       (cond
         (not t)
         (trap! DataError (format "Event type info: %s" t))
         (not c)
         (trap! DataError (format "Event code info: %s" c))
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
         obj {:status ::loki/error
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
   (let [obj {:status ::loki/ok
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
   (eventObj<> ::loki/private code body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn publicEvent<> ""
  ([code body] (publicEvent<> code body nil))
  ([code body arg]
   (eventObj<> ::loki/public code body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyEvent
  "Reply back a msg" [ch msg]

  (log/debug (str "reply back a msg "
                  "type: %s, code: %s")
             (get-enum-str loki-msg-types (:type msg))
             (get-enum-str loki-msg-codes (:code msg)))
  (do->nil
    (some-> ch
            (send-ws-string (encodeEvent msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyError
  "Reply back an error"
  [ch error msg]

  (replyEvent ch (errorObj<> ::loki/private error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isQuit? "" [evt]
  (and (isPrivate? evt)
       (isCode? ::loki/quit evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isMove? "" [evt]
  (and (isPrivate? evt)
       (isCode? ::loki/play-move evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pokeWait! ""
  ([room body] (pokeWait! room body nil))
  ([room body arg]
   (.send ^Sendable
          room
          (privateEvent<> ::loki/poke-wait body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pokeMove! ""
  ([room body] (pokeMove! room body nil))
  ([room body arg]
   (.send ^Sendable
          room
          (privateEvent<> ::loki/poke-move body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn syncArena! ""
  ([room body] (syncArena! room body nil))
  ([room body arg]
   (->>
     (if arg
       (privateEvent<> ::loki/sync-arena body arg)
       (publicEvent<> ::loki/sync-arena body))
     (.send ^Sendable room ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bcast! ""
  ([room code body] (bcast! room code body nil))
  ([room code body arg]
   (broad-cast room (publicEvent<> code body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn stop! ""
  ([room body] (stop! room body nil))
  ([room body arg] (bcast! room ::loki/stop body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

